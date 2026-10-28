(ns aishell.docker.base
  "Base image layer.

   Manages `aishell:base`, the image between the Foundation image and
   Project extensions. When `~/.aishell/Dockerfile` exists, builds the
   Base image from it. Otherwise, tags the Foundation image as the Base image (alias)."
  (:require [babashka.process :as p]
            [babashka.fs :as fs]
            [aishell.docker :as docker]
            [aishell.docker.build :as build]
            [aishell.docker.hash :as hash]
            [aishell.docker.spinner :as spinner]
            [aishell.output :as output]
            [aishell.util :as util]))

(def base-image-tag "aishell:base")

;; Labels for tracking rebuild dependencies
(def base-dockerfile-hash-label "aishell.base.dockerfile.hash")
(def base-foundation-id-label "aishell.base.foundation.id")

(defn global-dockerfile-path
  "Get path to the Base image Dockerfile (~/.aishell/Dockerfile)."
  []
  (str (fs/path (util/config-dir) "Dockerfile")))

(defn global-dockerfile-exists?
  "Check if the Base image Dockerfile exists at ~/.aishell/Dockerfile."
  []
  (fs/exists? (global-dockerfile-path)))

(defn global-dockerfile-hash
  "Compute 12-char SHA-256 hash of ~/.aishell/Dockerfile content.

   Returns: Hash string, or nil if ~/.aishell/Dockerfile doesn't exist."
  []
  (when (global-dockerfile-exists?)
    (hash/compute-hash (slurp (global-dockerfile-path)))))

(defn tag-foundation-as-base
  "Tag the Foundation image as the Base image, the alias used when
   ~/.aishell/Dockerfile does not exist, so `aishell:base` always exists for
   runs and Extended image builds.

   Returns: {:success true :image \"aishell:base\" :alias true}, without
            checking whether `docker tag` succeeded."
  []
  (p/shell {:out :string :err :string :continue true}
           "docker" "tag" build/foundation-image-tag base-image-tag)
  {:success true :image base-image-tag :alias true})

(defn- has-custom-base-label?
  "Check if the current Base image was custom-built (has dockerfile hash label)."
  []
  (some? (docker/get-image-label base-image-tag base-dockerfile-hash-label)))

(defn- base-matches-foundation?
  "Return true when the Base image has the Foundation image's Docker image ID,
   meaning the alias is current. Returns nil when either image ID can't be read."
  []
  (let [base-id (docker/image-id base-image-tag)
        foundation-id (docker/image-id build/foundation-image-tag)]
    (and base-id foundation-id (= base-id foundation-id))))

(defn needs-base-rebuild?
  "Return true when the Base image needs rebuilding from ~/.aishell/Dockerfile.

   Rebuild is needed when:
   - the `aishell:base` image doesn't exist
   - its stored Dockerfile hash differs from the current ~/.aishell/Dockerfile hash
   - its stored Foundation image ID differs from the current Foundation image ID"
  []
  (cond
    (not (docker/image-exists? base-image-tag))
    true

    (not= (docker/get-image-label base-image-tag base-dockerfile-hash-label)
          (global-dockerfile-hash))
    true

    (not= (docker/get-image-label base-image-tag base-foundation-id-label)
          (docker/image-id build/foundation-image-tag))
    true

    :else false))

(defn build-base-image
  "Build the Base image from ~/.aishell/Dockerfile, labelled with the
   Dockerfile hash and the Foundation image ID that needs-base-rebuild? compares.

   Options:
   - :force - Force rebuild (--no-cache)
   - :verbose - Show full Docker build output

   Returns: {:success true :image \"aishell:base\"} on success.
            Exits on failure rather than falling back to the Foundation image
            alias: the user wrote this Dockerfile, so its failure must show.
            The error includes the build output unless :verbose streamed it."
  [{:keys [force verbose]}]
  (let [dockerfile-path (global-dockerfile-path)
        foundation-id (docker/image-id build/foundation-image-tag)
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
      (let [{:keys [exit]}
            (apply p/shell {:continue true :dir build-dir}
                   "docker" "build" (conj build-args build-dir))]
        (if (zero? exit)
          (do
            (println "Base image built")
            {:success true :image base-image-tag})
          (output/error "Base image build failed")))
      (let [result (spinner/with-spinner "Building Base image"
                                         build-fn)]
        (if (:success result)
          (do
            (println "Base image built")
            result)
          (output/error (str "Base image build failed:\n" (:error result))))))))

(defn ensure-base-image
  "Ensure `aishell:base` image exists and is up to date.

   Called by setup, update and before each run.

   When ~/.aishell/Dockerfile exists:
   - If it needs a rebuild or :force -> build the Base image
   - Else: the Base image is up to date

   When ~/.aishell/Dockerfile does NOT exist:
   - If the Base image is missing, was custom-built, or its image ID differs
     from the Foundation image's or can't be read -> tag the Foundation image
     as the Base image
   - Else: the Base image already is the Foundation image, nothing to do

   Options:
   - :force - Force rebuild
   - :verbose - Show full Docker build output
   - :quiet - Suppress informational messages

   Returns: the result map of the path taken, never nil: :cached true when a
            built Base image is current, :alias true on the alias path.
            Exits when the build fails."
  [{:keys [force verbose quiet]}]
  (if (global-dockerfile-exists?)
    ;; Custom Dockerfile path
    (if (or force (needs-base-rebuild?))
      (build-base-image {:force force :verbose verbose})
      (do
        (when-not quiet
          (println "Base image aishell:base is up to date"))
        {:success true :image base-image-tag :cached true}))
    ;; No Dockerfile: alias path
    (if (or (not (docker/image-exists? base-image-tag))
            (has-custom-base-label?)
            (not (base-matches-foundation?)))
      (tag-foundation-as-base)
      {:success true :image base-image-tag :alias true})))
