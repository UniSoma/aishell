(ns aishell.docker.base
  "Global base image customization layer.

   Manages the `aishell:base` intermediate image between `aishell:foundation`
   and project extensions. When `~/.aishell/Dockerfile` exists, builds a custom
   base image from it. Otherwise, tags foundation as base (alias)."
  (:require [babashka.process :as p]
            [babashka.fs :as fs]
            [clojure.string :as str]
            [aishell.docker :as docker]
            [aishell.docker.hash :as hash]
            [aishell.docker.extension :as ext]
            [aishell.docker.spinner :as spinner]
            [aishell.output :as output]
            [aishell.util :as util]))

;; Image tag for the base layer
(def base-image-tag "aishell:base")

;; Labels for tracking rebuild dependencies
(def base-dockerfile-hash-label "aishell.base.dockerfile.hash")
(def base-foundation-id-label "aishell.base.foundation.id")

(defn global-dockerfile-path
  "Get path to global base Dockerfile (~/.aishell/Dockerfile)."
  []
  (str (fs/path (util/config-dir) "Dockerfile")))

(defn global-dockerfile-exists?
  "Check if global base Dockerfile exists at ~/.aishell/Dockerfile."
  []
  (fs/exists? (global-dockerfile-path)))

(defn global-dockerfile-hash
  "Compute 12-char SHA-256 hash of global Dockerfile content.

   Returns: Hash string, or nil if no global Dockerfile exists."
  []
  (when (global-dockerfile-exists?)
    (hash/compute-hash (slurp (global-dockerfile-path)))))

(defn tag-foundation-as-base
  "Tag foundation image as base (alias path).

   Used when no global Dockerfile exists — ensures `aishell:base` always
   exists as a valid image tag.

   Returns: {:success true :image \"aishell:base\" :alias true}"
  []
  (p/shell {:out :string :err :string :continue true}
           "docker" "tag" "aishell:foundation" base-image-tag)
  {:success true :image base-image-tag :alias true})

(defn- has-custom-base-label?
  "Check if current base image was custom-built (has dockerfile hash label)."
  []
  (some? (docker/get-image-label base-image-tag base-dockerfile-hash-label)))

(defn needs-base-rebuild?
  "Check if custom base image needs rebuilding.

   Returns true if:
   - `aishell:base` image doesn't exist
   - Stored Dockerfile hash differs from current global Dockerfile hash
   - Stored foundation image ID differs from current foundation image ID"
  []
  (cond
    ;; Base image doesn't exist
    (not (docker/image-exists? base-image-tag))
    true

    ;; Dockerfile hash changed
    (not= (docker/get-image-label base-image-tag base-dockerfile-hash-label)
          (global-dockerfile-hash))
    true

    ;; Foundation image changed
    (not= (docker/get-image-label base-image-tag base-foundation-id-label)
          (ext/get-foundation-image-id "aishell:foundation"))
    true

    :else false))

(defn build-base-image
  "Build custom base image from ~/.aishell/Dockerfile.

   Options:
   - :force - Force rebuild (--no-cache)
   - :verbose - Show full Docker build output

   Returns: {:success true :image \"aishell:base\"} on success.
            Exits on failure (hard-stop per decision)."
  [{:keys [force verbose]}]
  (let [dockerfile-path (global-dockerfile-path)
        foundation-id (ext/get-foundation-image-id "aishell:foundation")
        df-hash (global-dockerfile-hash)
        build-dir (str (fs/parent dockerfile-path))
        build-args (cond-> ["-f" dockerfile-path
                            "-t" base-image-tag
                            (str "--label=" base-dockerfile-hash-label "=" df-hash)
                            (str "--label=" base-foundation-id-label "=" foundation-id)]
                     force (conj "--no-cache")
                     verbose (conj "--progress=plain"))
        build-fn (fn []
                   (let [{:keys [exit out err]}
                         (apply p/shell {:out :string :err :string :continue true :dir build-dir}
                                "docker" "build" (conj build-args build-dir))]
                     (if (zero? exit)
                       {:success true :image base-image-tag}
                       {:success false :error (str out "\n" err)})))]
    (if verbose
      ;; Verbose: show output directly
      (let [{:keys [exit]}
            (apply p/shell {:continue true :dir build-dir}
                   "docker" "build" (conj build-args build-dir))]
        (if (zero? exit)
          (do
            (println "Global base image built")
            {:success true :image base-image-tag})
          (output/error "Global base image build failed")))
      ;; Non-verbose: use spinner
      (let [result (spinner/with-spinner "Building global base image"
                                         build-fn)]
        (if (:success result)
          (do
            (println "Global base image built")
            result)
          (output/error (str "Global base image build failed:\n" (:error result))))))))

(defn ensure-base-image
  "Ensure `aishell:base` image exists and is up to date.

   Main entry point called from setup and update paths.

   When global Dockerfile exists:
   - If needs rebuild or :force -> build custom base image
   - Else: base is up to date

   When global Dockerfile does NOT exist:
   - If base image missing or was custom-built -> re-tag foundation as base
   - Else: base tag already points to foundation, nothing to do

   Options:
   - :force - Force rebuild
   - :verbose - Show full Docker build output
   - :quiet - Suppress informational messages

   Returns: Result map from whichever path executed, or nil if no action needed."
  [{:keys [force verbose quiet]}]
  (if (global-dockerfile-exists?)
    ;; Custom Dockerfile path
    (if (or force (needs-base-rebuild?))
      (build-base-image {:force force :verbose verbose})
      (do
        (when-not quiet
          (println "Base image aishell:base is up to date"))
        {:success true :image base-image-tag :cached true}))
    ;; No Dockerfile — alias path
    (if (or (not (docker/image-exists? base-image-tag))
            (has-custom-base-label?))
      (tag-foundation-as-base)
      {:success true :image base-image-tag :alias true})))
