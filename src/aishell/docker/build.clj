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
(def base-image-id-label "aishell.base.id")
(def base-image-tag "aishell:base")

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

(defn version-changed?
  "Check if requested harness versions differ from stored state.
   Returns true if rebuild needed due to version change."
  [opts state]
  (or
    ;; Claude version changed
    (and (:with-claude opts)
         (not= (:claude-version opts) (:claude-version state)))
    ;; OpenCode version changed
    (and (:with-opencode opts)
         (not= (:opencode-version opts) (:opencode-version state)))
    ;; Codex version changed
    (and (:with-codex opts)
         (not= (:codex-version opts) (:codex-version state)))
    ;; Gemini version changed
    (and (:with-gemini opts)
         (not= (:gemini-version opts) (:gemini-version state)))
    ;; Harness added that wasn't in previous build
    (and (:with-claude opts) (not (:with-claude state)))
    (and (:with-opencode opts) (not (:with-opencode state)))
    (and (:with-codex opts) (not (:with-codex state)))
    (and (:with-gemini opts) (not (:with-gemini state)))
    ;; Gitleaks flag changed (default true for backwards compat with old state)
    (not= (:with-gitleaks opts) (:with-gitleaks state true))))

(defn write-build-files
  "Write embedded template files to build directory."
  [build-dir]
  (spit (str (fs/path build-dir "Dockerfile")) templates/base-dockerfile)
  (spit (str (fs/path build-dir "entrypoint.sh")) templates/entrypoint-script)
  (spit (str (fs/path build-dir "bashrc.aishell")) templates/bashrc-content))

(defn- build-docker-args
  "Construct docker build argument vector."
  [{:keys [with-claude with-opencode with-codex with-gemini with-gitleaks
           claude-version opencode-version codex-version gemini-version]} dockerfile-hash]
  (cond-> []
    with-claude (conj "--build-arg" "WITH_CLAUDE=true")
    with-opencode (conj "--build-arg" "WITH_OPENCODE=true")
    with-codex (conj "--build-arg" "WITH_CODEX=true")
    with-gemini (conj "--build-arg" "WITH_GEMINI=true")
    claude-version (conj "--build-arg" (str "CLAUDE_VERSION=" claude-version))
    opencode-version (conj "--build-arg" (str "OPENCODE_VERSION=" opencode-version))
    codex-version (conj "--build-arg" (str "CODEX_VERSION=" codex-version))
    gemini-version (conj "--build-arg" (str "GEMINI_VERSION=" gemini-version))
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

(defn- format-harness-line
  "Format harness version line for build summary output."
  [name version]
  (str "  " name ": " (or version "latest")))

(defn- run-build
  "Execute docker build command. Returns true on success."
  [build-dir tag args verbose? force?]
  (let [cmd (vec (concat ["docker" "build" "-t" tag]
                         (when force? ["--no-cache"])
                         (when verbose? ["--progress=plain"])
                         args
                         ["."]))]
    (if verbose?
      ;; Verbose: inherit output streams
      (let [{:keys [exit]} (apply p/process {:dir (str build-dir)
                                             :out :inherit
                                             :err :inherit}
                                            cmd)]
        (zero? @exit))
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

(defn build-base-image
  "Build the base Docker image with embedded templates.

   Options:
   - :with-claude - Include Claude Code
   - :with-opencode - Include OpenCode
   - :with-codex - Include Codex CLI
   - :with-gemini - Include Gemini CLI
   - :with-gitleaks - Include Gitleaks (default true)
   - :claude-version - Specific Claude version
   - :opencode-version - Specific OpenCode version
   - :codex-version - Specific Codex version
   - :gemini-version - Specific Gemini version
   - :force - Bypass cache check
   - :verbose - Show full build output
   - :quiet - Suppress all output except errors

   Returns {:success true :image tag} on success, exits on failure."
  [{:keys [force verbose quiet] :as opts}]
  ;; Verify Docker is available
  (docker/check-docker!)

  ;; Check cache - early return if no rebuild needed
  ;; Read state to check for version changes (dynamic require to avoid circular deps)
  (let [state ((requiring-resolve 'aishell.state/read-state))]
    (if-not (or (needs-rebuild? base-image-tag force)
                (version-changed? opts state))
      (do
        (when-not quiet
          (println (str "Image " base-image-tag " is up to date (use --force to rebuild)")))
        {:success true :image base-image-tag :cached true})

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
                         (run-build build-dir base-image-tag docker-args true force)

                         quiet
                         (run-build build-dir base-image-tag docker-args false force)

                         :else
                         (spinner/with-spinner "Building image"
                                               #(run-build build-dir base-image-tag docker-args false force)))]
          (if success?
            (let [duration (- (System/currentTimeMillis) start-time)
                  size (docker/get-image-size base-image-tag)]
              (when-not quiet
                (println (str "Built " base-image-tag
                              " (" (format-duration duration)
                              (when size (str ", " size)) ")"))
                (when (:with-claude opts)
                  (println (format-harness-line "Claude Code" (:claude-version opts))))
                (when (:with-opencode opts)
                  (println (format-harness-line "OpenCode" (:opencode-version opts))))
                (when (:with-codex opts)
                  (println (format-harness-line "Codex" (:codex-version opts))))
                (when (:with-gemini opts)
                  (println (format-harness-line "Gemini" (:gemini-version opts)))))
              {:success true
               :image base-image-tag
               :duration duration
               :size size
               :with-claude (:with-claude opts)
               :with-opencode (:with-opencode opts)
               :with-codex (:with-codex opts)
               :with-gemini (:with-gemini opts)
               :claude-version (:claude-version opts)
               :opencode-version (:opencode-version opts)
               :codex-version (:codex-version opts)
               :gemini-version (:gemini-version opts)})
            (output/error "Build failed")))
        (finally
          ;; Cleanup temp directory
          (fs/delete-tree build-dir)))))))
