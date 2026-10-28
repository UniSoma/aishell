(ns aishell.docker.build
  "Docker image building with caching and progress display.

   Builds images from embedded templates, with cache invalidation based
   on the embedded content hash. Supports verbose, quiet, and spinner modes.
   Also warns when a Foundation image is out of date; see
   `warn-if-foundation-out-of-date!`."
  (:require [babashka.process :as p]
            [babashka.fs :as fs]
            [aishell.docker :as docker]
            [aishell.docker.hash :as hash]
            [aishell.docker.spinner :as spinner]
            [aishell.docker.templates :as templates]
            [aishell.output :as output])
  (:import [java.time Duration Instant]))

(def dockerfile-hash-label "aishell.dockerfile.hash")
(def foundation-image-tag "aishell:foundation")
(def build-date-label "aishell.build.timestamp")

(def ^:private out-of-date-after-days
  "Age in days past which a Foundation image is out of date: Docker's layer
   cache keeps its OS packages as they were at its build date."
  30)

(defn- foundation-out-of-date-warning
  "Return the out-of-date warning for a Foundation image whose
   `build-date-label` value is `built-at`, an ISO-8601 instant, or nil when
   it is at most `out-of-date-after-days` old at `now`. A missing label (the image
   predates the label) or an unparseable one counts as out of date."
  [built-at now]
  (let [built (try (Instant/parse built-at) (catch Exception _ nil))
        age-days (when built (.toDays (Duration/between built now)))
        remedy " Run 'aishell update --force' to refresh its OS packages."]
    (cond
      (nil? built)
      (str "Foundation image has no build date." remedy)

      (> age-days out-of-date-after-days)
      (str "Foundation image is " age-days " days old." remedy))))

(defn warn-if-foundation-out-of-date!
  "Warn on stderr when the Foundation image under `image-tag` is out of date:
   older than `out-of-date-after-days`, or with no build date. The label is
   inherited through FROM, so the Base and Extended images carry it too.
   Advisory only: never stops the command."
  [image-tag]
  (when-let [msg (foundation-out-of-date-warning
                  (docker/get-image-label image-tag build-date-label)
                  (Instant/now))]
    (output/warn msg)))

(defn foundation-build-date
  "Return the `build-date-label` value for a Foundation image build, or nil for
   none. A forced build (`--no-cache`) and a first build are stamped `now`.
   A cached rebuild may reuse the old apt layer, so it keeps the previous
   stamp, or stays unstamped when the image it replaces had none."
  [{:keys [force? image-exists? previous now]}]
  (cond
    force? (str now)
    image-exists? previous
    :else (str now)))

(defn get-dockerfile-hash
  "Compute the hash of the Foundation image's embedded content: the
   Dockerfile and every file it COPYs from the build context (entrypoint.sh,
   bashrc.aishell, profile.d-aishell.sh, etc-profile). The image label
   keeps the name `aishell.dockerfile.hash` for backwards compatibility,
   but an entrypoint-only edit still changes the hash."
  []
  (hash/compute-hash templates/foundation-content))

(defn foundation-stale?
  "Return true when `recorded-hash`, read from state or an image label,
   differs from the hash of the embedded content. A nil hash is stale."
  [recorded-hash]
  (not= recorded-hash (get-dockerfile-hash)))

(defn needs-rebuild?
  "Return true when `image-tag` needs a rebuild: `force?` is set, the
   image is missing, or its hash label differs from the embedded content
   hash."
  [image-tag force?]
  (or force?
      (not (docker/image-exists? image-tag))
      (foundation-stale? (docker/get-image-label image-tag dockerfile-hash-label))))

(defn write-build-files
  "Write embedded template files to build directory."
  [build-dir]
  (spit (str (fs/path build-dir "Dockerfile")) templates/foundation-dockerfile)
  (spit (str (fs/path build-dir "entrypoint.sh")) templates/entrypoint-script)
  (spit (str (fs/path build-dir "bashrc.aishell")) templates/bashrc-content)
  (spit (str (fs/path build-dir "profile.d-aishell.sh")) templates/profile-d-script)
  (spit (str (fs/path build-dir "etc-profile")) templates/etc-profile-content))

(defn- build-docker-args
  "Return the docker build argument vector."
  [{:keys [with-gitleaks]} dockerfile-hash built-at]
  (cond-> []
    ;; Passed even when false so the build cache sees the same args every time
    true (conj "--build-arg" (str "WITH_GITLEAKS=" (if with-gitleaks "true" "false")))
    true (conj "--label" (str dockerfile-hash-label "=" dockerfile-hash))
    built-at (conj "--label" (str build-date-label "=" built-at))))

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
      (let [{:keys [exit err]} (apply p/shell {:dir (str build-dir)
                                               :out :string
                                               :err :string
                                               :continue true}
                                      cmd)]
        (when-not (zero? exit)
          (binding [*out* *err*]
            (println err)))
        (zero? exit)))))

(defn build-foundation-image
  "Build the Foundation image from the embedded templates, unless its
   content hash is unchanged and `:force` is unset.

   The Foundation image holds OS packages, runtimes and shared tooling;
   volume-installed harnesses live in the harness volume instead.

   Options:
   - :with-gitleaks - Include Gitleaks (default false, opt-in)
   - :force - Rebuild with `--no-cache` and stamp a fresh build date
   - :verbose - Show full build output
   - :quiet - Suppress all output except errors

   Returns {:success true :image tag}, plus `:cached true` when no build
   ran. Exits the process when the build fails."
  [{:keys [force verbose quiet] :as opts}]
  (docker/check-docker!)

  (if-not (needs-rebuild? foundation-image-tag force)
    (do
      (when-not quiet
        (println (str "Image " foundation-image-tag " matches its embedded Dockerfile (use --force to rebuild)")))
      {:success true :image foundation-image-tag :cached true})

    (let [build-dir (fs/create-temp-dir {:prefix "aishell-build-"})
          dockerfile-hash (get-dockerfile-hash)
          image-exists? (docker/image-exists? foundation-image-tag)
          built-at (foundation-build-date
                    {:force? force
                     :image-exists? image-exists?
                     :previous (when image-exists?
                                 (docker/get-image-label foundation-image-tag
                                                         build-date-label))
                     :now (Instant/now)})
          docker-args (build-docker-args opts dockerfile-hash built-at)
          start-time (System/currentTimeMillis)]
      (try
        (write-build-files build-dir)

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
          (fs/delete-tree build-dir))))))

