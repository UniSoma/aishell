(ns aishell.docker.extension
  "Project extension and Extended image builds.

   A project extends the Base image with a Project extension at
   <active-dir>/Dockerfile, where the active dir is .aishell or its .sandbox
   alias. The Extended image rebuilds when the Base image ID or the Project
   extension's content changes; see needs-extended-rebuild?."
  (:require [babashka.process :as p]
            [babashka.fs :as fs]
            [clojure.string :as str]
            [aishell.docker :as docker]
            [aishell.docker.hash :as hash]
            [aishell.docker.spinner :as spinner]
            [aishell.output :as output]
            [aishell.util :as util]))

;; Labels for tracking rebuild dependencies
(def base-image-id-label "aishell.base.id")
(def extension-hash-label "aishell.extension.hash")

(defn project-dockerfile
  "Get path to the Project extension if it exists.

   Arguments:
   - project-dir: Path to project directory

   Returns: String path to <active-dir>/Dockerfile if exists, else nil"
  [project-dir]
  (let [path (fs/path project-dir (util/resolve-project-config-dir project-dir) "Dockerfile")]
    (when (fs/exists? path)
      (str path))))

(defn- miscased-dockerfile
  "On Windows, return the actual filename if <active-dir>/<name> matches
   'dockerfile' case-insensitively but isn't exactly 'Dockerfile'.
   Returns nil otherwise (including on non-Windows platforms).

   NTFS is case-insensitive so the file resolves locally, but Docker
   Desktop's Linux daemon is case-sensitive — BuildKit fails with a
   cryptic 'open Dockerfile: no such file or directory' error."
  [project-dir]
  (when (fs/windows?)
    (let [config-dir (fs/path project-dir (util/resolve-project-config-dir project-dir))]
      (when (fs/exists? config-dir)
        (->> (fs/list-dir config-dir)
             (map #(str (fs/file-name %)))
             (some #(when (and (= (str/lower-case %) "dockerfile")
                               (not= % "Dockerfile"))
                      %)))))))

(defn compute-extended-tag
  "Compute the Extended image's Docker tag from the project path.

   Arguments:
   - project-dir: Path to project directory

   Returns: Tag string like \"aishell:ext-abc123def456\""
  [project-dir]
  (str "aishell:ext-" (hash/compute-hash project-dir)))

(defn get-extension-dockerfile-hash
  "Compute hash of the Project extension's content.

   Arguments:
   - project-dir: Path to project directory

   Returns: 12-char hash string, or nil if there is no Project extension"
  [project-dir]
  (when-let [dockerfile-path (project-dockerfile project-dir)]
    (hash/compute-hash (slurp dockerfile-path))))

(defn needs-extended-rebuild?
  "Return true when the Extended image needs rebuilding, false otherwise.

   Rebuild is needed when:
   1. The Extended image doesn't exist
   2. The Base image ID has changed (the Base image picks up Foundation image changes)
   3. The Project extension's content has changed (only checked when
      project-dir is given and has a Project extension)

   Arguments:
   - extended-tag: Tag of the Extended image
   - base-tag: Tag of the Base image (aishell:base)
   - project-dir: Path to project directory (optional)"
  ([extended-tag base-tag]
   (needs-extended-rebuild? extended-tag base-tag nil))
  ([extended-tag base-tag project-dir]
   (cond
     (not (docker/image-exists? extended-tag))
     true

     :else
     (let [stored-base-id (docker/get-image-label extended-tag base-image-id-label)
           current-base-id (docker/image-id base-tag)]
       (cond
         ;; While the Base image exists, an Extended image with no Base image
         ;; ID label also counts as changed and rebuilds.
         (not= stored-base-id current-base-id)
         true

         (and project-dir (project-dockerfile project-dir))
         (let [stored-hash (docker/get-image-label extended-tag extension-hash-label)
               current-hash (get-extension-dockerfile-hash project-dir)]
           (not= stored-hash current-hash))

         :else false)))))

(defn build-failure-message
  "Return the error text for a failed Extended image build. detail is the
   captured build output, nil when it already streamed to the terminal."
  [config-dir detail]
  (str "Extension build failed" (when detail (str ":\n" detail))
       "\nTo start without the Project extension and fix " config-dir "/Dockerfile from inside,"
       " run: aishell --base-image <harness>"))

(defn build-extended-image
  "Build the Extended image from the Project extension.

   Arguments (map):
   - project-dir: Path to project directory
   - base-tag: Tag of the Base image (aishell:base)
   - extended-tag: Tag for the Extended image
   - force: Force rebuild (--no-cache)
   - verbose: Show detailed build output

   Warns on stderr when the Dockerfile's filename casing is wrong (Windows).
   Returns: {:success true :image extended-tag} or nil if no Project
            extension; exits on error with build-failure-message"
  [{:keys [project-dir base-tag extended-tag force verbose]}]
  (when-let [dockerfile-path (project-dockerfile project-dir)]
    (let [config-dir (util/resolve-project-config-dir project-dir)]
      (when-let [actual (miscased-dockerfile project-dir)]
        (output/warn
         (str config-dir "/" actual " has non-canonical casing. "
              "Docker Desktop's Linux daemon is case-sensitive and will fail "
              "to find this file during build. Rename it to " config-dir "/Dockerfile.")))
      (let [base-id (docker/image-id base-tag)
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
          (let [{:keys [exit]}
                (apply p/shell {:continue true :dir project-dir}
                       "docker" "build" (conj build-args project-dir))]
            (if (zero? exit)
              {:success true :image extended-tag}
              (output/error (build-failure-message config-dir nil))))
          (let [result (spinner/with-spinner "Building Extended image" build-fn)]
            (if (:success result)
              result
              (output/error (build-failure-message config-dir (:error result))))))))))
