(ns aishell.docker.extension
  "Per-project Dockerfile extension support.

   Projects can extend the base image with .aishell/Dockerfile.
   The extension is auto-rebuilt when base image or extension Dockerfile changes."
  (:require [babashka.process :as p]
            [babashka.fs :as fs]
            [clojure.string :as str]
            [aishell.docker :as docker]
            [aishell.docker.hash :as hash]
            [aishell.docker.spinner :as spinner]
            [aishell.output :as output]))

;; Labels for tracking rebuild dependencies
(def base-image-id-label "aishell.base.id")
(def extension-hash-label "aishell.extension.hash")

(defn project-dockerfile
  "Get path to project extension Dockerfile if it exists.

   Arguments:
   - project-dir: Path to project directory

   Returns: String path to .aishell/Dockerfile if exists, else nil"
  [project-dir]
  (let [path (fs/path project-dir ".aishell" "Dockerfile")]
    (when (fs/exists? path)
      (str path))))

(defn get-base-image-id
  "Get Docker image ID for a given image tag.

   Arguments:
   - image-tag: Docker image tag (e.g., \"aishell:base\")

   Returns: Image ID string or nil on error"
  [image-tag]
  (try
    (let [{:keys [exit out]}
          (p/shell {:out :string :err :string :continue true}
                   "docker" "inspect" "--format={{.Id}}" image-tag)]
      (when (zero? exit)
        (str/trim out)))
    (catch Exception _ nil)))

(defn compute-extended-tag
  "Compute Docker tag for extended image based on project path.

   Arguments:
   - project-dir: Path to project directory

   Returns: Tag string like \"aishell:ext-abc123def456\""
  [project-dir]
  (str "aishell:ext-" (hash/compute-hash project-dir)))

(defn get-extension-dockerfile-hash
  "Compute hash of project extension Dockerfile content.

   Arguments:
   - project-dir: Path to project directory

   Returns: 12-char hash string, or nil if no extension Dockerfile"
  [project-dir]
  (when-let [dockerfile-path (project-dockerfile project-dir)]
    (hash/compute-hash (slurp dockerfile-path))))

(defn needs-extended-rebuild?
  "Check if extended image needs to be rebuilt.

   Rebuild is needed when:
   1. Extended image doesn't exist
   2. Base image ID has changed
   3. Extension Dockerfile content has changed

   Arguments:
   - extended-tag: Tag for extended image
   - base-image-tag: Tag for base image
   - project-dir: Path to project directory (optional, for Dockerfile hash check)

   Returns: true if rebuild needed, false otherwise"
  ([extended-tag base-image-tag]
   (needs-extended-rebuild? extended-tag base-image-tag nil))
  ([extended-tag base-image-tag project-dir]
   (cond
     ;; Extended image doesn't exist
     (not (docker/image-exists? extended-tag))
     true

     ;; Check base image ID
     :else
     (let [stored-base-id (docker/get-image-label extended-tag base-image-id-label)
           current-base-id (get-base-image-id base-image-tag)]
       (cond
         ;; Base image changed
         (not= stored-base-id current-base-id)
         true

         ;; Check extension Dockerfile hash if project-dir provided
         (and project-dir (project-dockerfile project-dir))
         (let [stored-hash (docker/get-image-label extended-tag extension-hash-label)
               current-hash (get-extension-dockerfile-hash project-dir)]
           (not= stored-hash current-hash))

         ;; No changes detected
         :else false)))))

(defn build-extended-image
  "Build extended image from project Dockerfile.

   Arguments (map):
   - project-dir: Path to project directory
   - base-tag: Tag for base image
   - extended-tag: Tag for extended image
   - force: Force rebuild (--no-cache)
   - verbose: Show detailed build output

   Returns: {:success true :image extended-tag} or nil if no extension,
            exits on error"
  [{:keys [project-dir base-tag extended-tag force verbose]}]
  (when-let [dockerfile-path (project-dockerfile project-dir)]
    (let [base-id (get-base-image-id base-tag)
          extension-hash (get-extension-dockerfile-hash project-dir)
          build-args (cond-> ["-f" dockerfile-path
                              "-t" extended-tag
                              (str "--label=" base-image-id-label "=" base-id)
                              (str "--label=" extension-hash-label "=" extension-hash)]
                       force (conj "--no-cache")
                       verbose (conj "--progress=plain"))
          build-fn (fn []
                     (let [{:keys [exit out err]}
                           (apply p/shell {:out :string :err :string :continue true :dir project-dir}
                                  "docker" "build" (conj build-args project-dir))]
                       (if (zero? exit)
                         {:success true :image extended-tag}
                         {:success false :error (str out "\n" err)})))]
      (if verbose
        ;; Verbose: show output directly
        (let [{:keys [exit]}
              (apply p/shell {:continue true :dir project-dir}
                     "docker" "build" (conj build-args project-dir))]
          (if (zero? exit)
            {:success true :image extended-tag}
            (output/error "Extension build failed")))
        ;; Non-verbose: use spinner
        (let [result (spinner/with-spinner "Building project extension"
                                           build-fn)]
          (if (:success result)
            result
            (output/error (str "Extension build failed:\n" (:error result)))))))))
