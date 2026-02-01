(ns aishell.docker.extension
  "Per-project Dockerfile extension support with legacy tag validation.

   Projects can extend the foundation image with .aishell/Dockerfile.
   The extension is auto-rebuilt when foundation image or extension Dockerfile changes."
  (:require [babashka.process :as p]
            [babashka.fs :as fs]
            [clojure.string :as str]
            [aishell.docker :as docker]
            [aishell.docker.hash :as hash]
            [aishell.docker.spinner :as spinner]
            [aishell.output :as output]))

;; Labels for tracking rebuild dependencies
(def foundation-image-id-label "aishell.foundation.id")
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

(defn validate-base-tag
  "Validate that project Dockerfile doesn't use legacy 'aishell:base' tag.

   Arguments:
   - project-dir: Path to project directory

   Returns: nil if validation passes or no Dockerfile exists.
            Exits with error if legacy FROM aishell:base is found."
  [project-dir]
  (when-let [dockerfile-path (project-dockerfile project-dir)]
    (let [content (slurp dockerfile-path)
          legacy-pattern #"(?i)FROM\s+aishell:base\b"]
      (when (re-find legacy-pattern content)
        (output/error
          "Legacy base image tag in .aishell/Dockerfile

Found:    FROM aishell:base
Expected: FROM aishell:foundation

Why: v2.8.0 splits the base image into a stable foundation layer and
     volume-mounted harness tools to prevent cascade rebuilds.

Fix: Edit .aishell/Dockerfile and change:
     FROM aishell:base  ->  FROM aishell:foundation")))))

(defn get-foundation-image-id
  "Get Docker image ID for foundation image.

   Arguments:
   - image-tag: Docker image tag (e.g., \"aishell:foundation\")

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
   2. Foundation image ID has changed
   3. Extension Dockerfile content has changed

   Arguments:
   - extended-tag: Tag for extended image
   - foundation-image-tag: Tag for foundation image
   - project-dir: Path to project directory (optional, for Dockerfile hash check)

   Returns: true if rebuild needed, false otherwise"
  ([extended-tag foundation-image-tag]
   (needs-extended-rebuild? extended-tag foundation-image-tag nil))
  ([extended-tag foundation-image-tag project-dir]
   (cond
     ;; Extended image doesn't exist
     (not (docker/image-exists? extended-tag))
     true

     ;; Check foundation image ID
     :else
     (let [stored-foundation-id (docker/get-image-label extended-tag foundation-image-id-label)
           current-foundation-id (get-foundation-image-id foundation-image-tag)]
       (cond
         ;; Foundation image changed (or extension has no foundation label - triggers rebuild for migration)
         (not= stored-foundation-id current-foundation-id)
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
   - foundation-tag: Tag for foundation image
   - extended-tag: Tag for extended image
   - force: Force rebuild (--no-cache)
   - verbose: Show detailed build output

   Returns: {:success true :image extended-tag} or nil if no extension,
            exits on error"
  [{:keys [project-dir foundation-tag extended-tag force verbose]}]
  (when-let [dockerfile-path (project-dockerfile project-dir)]
    (let [foundation-id (get-foundation-image-id foundation-tag)
          extension-hash (get-extension-dockerfile-hash project-dir)
          build-args (cond-> ["-f" dockerfile-path
                              "-t" extended-tag
                              (str "--label=" foundation-image-id-label "=" foundation-id)
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
