(ns aishell.docker.build
  "Docker image building with caching and progress display.

   Builds images from embedded templates, with cache invalidation based
   on Dockerfile content hash. Supports verbose, quiet, and spinner modes."
  (:require [babashka.process :as p]
            [babashka.fs :as fs]
            [clojure.string :as str]
            [aishell.docker :as docker]
            [aishell.docker.hash :as hash]
            [aishell.docker.spinner :as spinner]
            [aishell.docker.templates :as templates]
            [aishell.output :as output]))

;; Label keys for cache tracking
(def dockerfile-hash-label "aishell.dockerfile.hash")
(def foundation-image-id-label "aishell.foundation.id")
(def base-image-id-label foundation-image-id-label) ;; Backward compatibility alias
(def foundation-image-tag "aishell:foundation")
(def base-image-tag foundation-image-tag)

(defn get-dockerfile-hash
  "Compute hash of embedded Dockerfile content for cache comparison."
  []
  (hash/compute-hash templates/base-dockerfile))

(defn needs-rebuild?
  "Check if image needs rebuilding.

   Returns true if:
   - force? is true
   - Image doesn't exist
   - Dockerfile hash changed since build"
  [image-tag force?]
  (or force?
      (not (docker/image-exists? image-tag))
      (not= (get-dockerfile-hash)
            (docker/get-image-label image-tag dockerfile-hash-label))))


(defn write-build-files
  "Write embedded template files to build directory."
  [build-dir]
  (spit (str (fs/path build-dir "Dockerfile")) templates/base-dockerfile)
  (spit (str (fs/path build-dir "entrypoint.sh")) templates/entrypoint-script)
  (spit (str (fs/path build-dir "bashrc.aishell")) templates/bashrc-content)
  (spit (str (fs/path build-dir "profile.d-aishell.sh")) templates/profile-d-script))

(defn- build-docker-args
  "Construct docker build argument vector."
  [{:keys [with-gitleaks]} dockerfile-hash]
  (cond-> []
    ;; Gitleaks is opt-out, so we always pass the arg (true or false)
    true (conj "--build-arg" (str "WITH_GITLEAKS=" (if with-gitleaks "true" "false")))
    true (conj "--label" (str dockerfile-hash-label "=" dockerfile-hash))))

(defn- format-duration
  "Format milliseconds as human-readable duration."
  [ms]
  (let [secs (/ ms 1000.0)]
    (if (< secs 60)
      (format "%.1fs" secs)
      (let [mins (int (/ secs 60))
            remaining-secs (mod secs 60)]
        (format "%dm %.0fs" mins remaining-secs)))))


(defn- run-build
  "Execute docker build command. Returns true on success."
  [build-dir tag args verbose? force?]
  (let [cmd (vec (concat ["docker" "build" "-t" tag]
                         (when force? ["--no-cache"])
                         args
                         ["."]))]
    (if verbose?
      ;; Verbose: inherit output streams (blocking)
      (let [{:keys [exit]} (apply p/shell {:dir (str build-dir)
                                           :out :inherit
                                           :err :inherit
                                           :continue true}
                                          cmd)]
        (zero? exit))
      ;; Silent: capture output
      (let [{:keys [exit out err]} (apply p/shell {:dir (str build-dir)
                                                   :out :string
                                                   :err :string
                                                   :continue true}
                                                  cmd)]
        (when-not (zero? exit)
          (binding [*out* *err*]
            (println err)))
        (zero? exit)))))

(defn build-foundation-image
  "Build the foundation Docker image with embedded templates.

   Foundation image contains only system dependencies (Debian, Node.js, babashka, gosu, gitleaks).
   Harness tools are installed separately via volume injection (Phase 36).

   Options:
   - :with-gitleaks - Include Gitleaks (default false, opt-in)
   - :force - Bypass cache check
   - :verbose - Show full build output
   - :quiet - Suppress all output except errors

   Returns {:success true :image tag} on success, exits on failure."
  [{:keys [force verbose quiet with-gitleaks] :as opts}]
  ;; Verify Docker is available
  (docker/check-docker!)

  ;; Check cache - early return if no rebuild needed
  (if-not (needs-rebuild? foundation-image-tag force)
    (do
      (when-not quiet
        (println (str "Image " foundation-image-tag " is up to date (use --force to rebuild)")))
      {:success true :image foundation-image-tag :cached true})

    ;; Create temp build directory and build
    (let [build-dir (fs/create-temp-dir {:prefix "aishell-build-"})
          dockerfile-hash (get-dockerfile-hash)
          docker-args (build-docker-args opts dockerfile-hash)
          start-time (System/currentTimeMillis)]
      (try
        ;; Write template files
        (write-build-files build-dir)

        ;; Build with appropriate output mode
        (let [success? (cond
                         verbose
                         (run-build build-dir foundation-image-tag docker-args true force)

                         quiet
                         (run-build build-dir foundation-image-tag docker-args false force)

                         :else
                         (spinner/with-spinner "Building image"
                                               #(run-build build-dir foundation-image-tag docker-args false force)))]
          (if success?
            (let [duration (- (System/currentTimeMillis) start-time)
                  size (docker/get-image-size foundation-image-tag)]
              (when-not quiet
                (println (str "Built " foundation-image-tag
                              " (" (format-duration duration)
                              (when size (str ", " size)) ")")))
              {:success true
               :image foundation-image-tag
               :duration duration
               :size size})
            (output/error "Build failed")))
        (finally
          ;; Cleanup temp directory
          (fs/delete-tree build-dir))))))

;; Backward compatibility alias
(def build-base-image build-foundation-image)
