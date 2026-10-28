(ns aishell.run
  "Run command orchestration.
   Launches an interactive shell, or any harness the registry knows, in a
   container."
  (:require [babashka.process :as p]
            [babashka.fs :as fs]
            [clojure.string :as str]
            [aishell.docker :as docker]
            [aishell.docker.base :as base]
            [aishell.docker.build :as build]
            [aishell.docker.naming :as naming]
            [aishell.docker.run :as docker-run]
            [aishell.docker.extension :as ext]
            [aishell.docker.volume :as vol]
            [aishell.config :as config]
            [aishell.harness :as harness]
            [aishell.state :as state]
            [aishell.output :as output]
            [aishell.terminal :as terminal]
            [aishell.validation :as validation]
            [aishell.detection.core :as detection]
            [aishell.gitleaks.warnings :as gitleaks-warnings]
            [aishell.gitleaks.scan-state :as scan-state]
            [aishell.pi :as pi]))

(defn- harness-for
  "The registry descriptor for a run command name, or nil in shell mode."
  [cmd]
  (harness/for-subcommand cmd))

(defn- verify-harness-available
  "Check that the harness was included in setup. Exit with error if not."
  [{:keys [label subcommand state-key]} state]
  (when-not (get state state-key)
    (output/error
     (str label
          " not installed. Run: aishell setup --with-"
          subcommand))))

(defn- report-harness-defaults!
  "Report the `harness_args` defaults configured for `cmd`.

   Defaults that reach the launch argv are a verbose note. A harness that
   cannot receive them — one whose descriptor sets :accepts-config-defaults?
   false, gitleaks today — warns instead, so the config mistake is visible
   rather than silently dropped."
  [cmd default-args]
  (when (seq default-args)
    (let [descriptor (harness-for cmd)
          joined (str/join " " default-args)]
      (if (and descriptor (not (:accepts-config-defaults? descriptor)))
        (output/warn (str "Ignoring harness_args for " cmd ": " (:label descriptor)
                          " does not accept configured defaults (" joined ")"))
        (output/verbose (str "Applying " cmd " defaults: " joined))))))

(defn- container-command
  "Launch argv for `cmd` inside the container. Harness commands derive their
   argv from the registry's interpreter; anything else opens a shell."
  [cmd default-args cli-args skip-perms?]
  (if-let [descriptor (harness-for cmd)]
    (harness/launch-argv descriptor {:skip-permissions? skip-perms?
                                     :default-args default-args
                                     :cli-args cli-args})
    ["/bin/bash"]))

(defn- warn-if-foundation-stale
  "Warn when the Foundation image is stale: the hash setup or update stored
   in `state` differs from the hash of the embedded content (the Dockerfile
   and every file it COPYs from the build context). Silent when `state`
   holds no stored hash. Advisory only: it does not block execution."
  [state]
  (when-let [stored-hash (:dockerfile-hash state)]
    (when (build/foundation-stale? stored-hash)
      (output/warn "Image may be stale. Run 'aishell update' to rebuild."))))

(defn- ensure-harness-volume
  "Ensure harness volume exists and is up-to-date.
   Populates lazily if missing or stale (hash mismatch).
   Returns volume name for docker run mounting, or nil if no harnesses enabled."
  [state config]
  (when (harness/volume-harnesses-enabled? state)
    (let [expected-hash (vol/compute-harness-hash state)
          volume-name (or (:harness-volume-name state)
                          (vol/volume-name expected-hash))]
      (cond
        ;; Volume missing - create and populate
        (not (vol/volume-exists? volume-name))
        (do
          (vol/create-volume volume-name {"aishell.harness.hash" expected-hash
                                          "aishell.harness.version" vol/volume-schema-version})
          (let [result (vol/populate-volume volume-name state {:config config
                                                               :verbose output/*verbose*})]
            (when-not (:success result)
              ;; Remove empty volume so next run retries population
              (vol/remove-volume volume-name)
              (output/error "Failed to populate harness volume"))))

        ;; Volume exists but stale (hash mismatch or missing label)
        (not= (vol/get-volume-label volume-name "aishell.harness.hash")
              expected-hash)
        (let [result (vol/populate-volume volume-name state {:config config
                                                             :verbose output/*verbose*})]
          (when-not (:success result)
            (output/error "Failed to populate harness volume"))))
      ;; Return volume name regardless
      volume-name)))

(defn skip-pre-start?
  "Return true when a launch with these run-container opts skips the
   pre_start hook. --base-image skips it: pre_start is what most likely
   needs the Project extension's tools."
  [opts]
  (boolean (or (:skip-pre-start opts) (:base-image opts))))

(defn resolve-image-tag
  "Return the image tag to run: the Extended image if the project has a
   Project extension (<active-dir>/Dockerfile), else the Base image.
   Builds or re-tags the Base image first when it needs it, and builds the
   Extended image when it needs a rebuild.
   With :base-image in opts, returns the Base image without building the
   Extended image, and prints to stderr (not in JSON mode) the Project
   extension it skipped, or that the project has none.
   Builds stream their docker output when output/*verbose* is bound (--verbose);
   otherwise they run behind a spinner."
  [project-dir force? & [{:keys [base-image]}]]
  (base/ensure-base-image {:quiet (not output/*verbose*) :verbose output/*verbose*})
  (let [extension-path (ext/project-extension-path project-dir)]
    (cond
      base-image
      (do
        (when-not output/*json-output*
          (binding [*out* *err*]
            (println
             (if extension-path
               (str "Starting on the Base image: Project extension "
                    (fs/relativize project-dir extension-path)
                    " skipped, and pre_start with it.")
               "Starting on the Base image: no Project extension to skip; pre_start skipped."))))
        base/base-image-tag)

      extension-path
      (let [extended-tag (ext/compute-extended-tag project-dir)]
        (when (ext/needs-extended-rebuild? extended-tag base/base-image-tag project-dir)
          (ext/build-extended-image
           {:project-dir project-dir
            :base-tag base/base-image-tag
            :extended-tag extended-tag
            :force force?
            :verbose output/*verbose*}))
        extended-tag)

      :else
      base/base-image-tag)))

(defn run-container
  "Run docker container for shell or harness.

   Arguments:
   - cmd: nil for a shell, else a harness subcommand from the registry
   - harness-args: Extra arguments to pass to harness (vector)
   - opts: Optional map with :unsafe (skip detection warnings),
     :container-name (overrides the name part), :skip-pre-start and
     :base-image (start on the Base image, without the Project extension
     or pre_start)

   Does not return: exits with the container's exit code, or on Unix
   replaces the process."
  [cmd harness-args & [opts]]
  ;; Refuse a home-containing root before anything walks the tree
  (validation/check-project-dir! (System/getProperty "user.dir"))

  (docker/check-docker!)

  ;; Announce removed harnesses once, before state is read
  (state/warn-removed-harnesses!)

  (let [state (state/read-state)]
    (when-not state
      (output/error-no-setup))

    (let [project-dir (System/getProperty "user.dir")
          cfg (config/load-config project-dir)

          container-name-str (let [name-part (or (:container-name opts) cmd "shell")]
                               (naming/container-name project-dir name-part))

          ;; Pre-flight conflict check: error if running, auto-remove if stopped
          _ (naming/ensure-name-available! container-name-str (or (:container-name opts) cmd "shell"))

          _ (output/verbose (str "Container name: " container-name-str))

          harness-volume-name (ensure-harness-volume state cfg)

          ;; Pi packages come from the global config only
          _ (pi/ensure-pi-packages! cfg state harness-volume-name)]

      (when-let [descriptor (harness-for cmd)]
        (verify-harness-available descriptor state))

      (let [descriptor (harness-for cmd)
            ;; A harness that is itself a secret scanner stands in for aishell's
            ;; own scanning: no sensitive-file scan, no scan-freshness nag.
            secret-scanner? (boolean (:secret-scanner? descriptor))
            image-tag (resolve-image-tag project-dir false opts)
            git-id (docker-run/read-git-identity project-dir)

            defaults (when (and cfg cmd)
                       (get-in cfg [:harness_args (keyword cmd)] []))
            defaults-vec (vec (or defaults []))

            _ (when cfg
                (output/verbose (str "Loaded config from: "
                                     (name (config/config-source project-dir)))))
            _ (when (and (:name git-id) (:email git-id))
                (output/verbose (str "Git identity: " (:name git-id)
                                     " <" (:email git-id) ">")))
            _ (report-harness-defaults! cmd defaults-vec)

            _ (warn-if-foundation-stale state)
            _ (build/warn-if-foundation-out-of-date! image-tag)

            _ (when-let [docker-args (:docker_args cfg)]
                (validation/warn-dangerous-args docker-args))

            _ (when-let [mounts (:mounts cfg)]
                (validation/warn-dangerous-mounts mounts))

            _ (when-not (or (:unsafe opts) secret-scanner?)
                (let [detection-config (get cfg :detection {})
                      allowlist (:allowlist detection-config [])
                      ;; scan-project checks :enabled and uses :custom_patterns
                      findings (detection/scan-project project-dir detection-config)
                      filtered-findings (detection/filter-allowlisted findings allowlist project-dir)]
                  (when (seq filtered-findings)
                    (detection/display-warnings project-dir filtered-findings)
                    (detection/confirm-if-needed filtered-findings))))

            _ (when (and (:with-gitleaks state) (not secret-scanner?))
                (gitleaks-warnings/display-freshness-warning project-dir cfg))

            docker-args (docker-run/build-docker-args
                         {:project-dir project-dir
                          :image-tag image-tag
                          :config cfg
                          :state state
                          :git-identity git-id
                          :skip-pre-start (skip-pre-start? opts)
                          :skip-interactive (boolean (and descriptor
                                                          (not (:interactive? descriptor))))
                          :container-name container-name-str
                          :harness-volume-name harness-volume-name})

            skip-perms? (harness/skip-permissions?
                         (System/getenv harness/skip-permissions-env-var))

            container-cmd (container-command cmd defaults-vec harness-args skip-perms?)]

        ;; For a scan, use shell instead of exec so we can update the timestamp after
        ;; :continue true prevents p/shell from throwing on non-zero exit
        (if secret-scanner?
          (let [result (apply p/shell {:inherit true :continue true} (concat docker-args container-cmd))
                ;; Only update timestamp for actual scan subcommands, not help/version
                scan-subcommands #{"dir" "git" "detect" "protect"}
                first-arg (first harness-args)
                is-scan? (contains? scan-subcommands first-arg)
                ;; Gitleaks exit codes: 0=no leaks, 1=leaks found (both are successful scans)
                scan-completed? (contains? #{0 1} (:exit result))]
            (when (and scan-completed? is-scan?)
              (scan-state/write-scan-timestamp project-dir))
            (System/exit (:exit result)))
          ;; Foreground mode: set window title, then exec (transfer terminal control)
          (let [project-name (.getName (java.io.File. project-dir))]
            (print (str "\033]2;[aishell] " project-name "\007"))
            (flush)
            (if (fs/windows?)
              ;; Windows: spawn child process with inherited I/O, wait, propagate exit.
              ;; No stty equivalent here, so only the escapes are replayed.
              (let [result @(apply p/process {:inherit true}
                                   (concat docker-args container-cmd))]
                (print terminal/restore-sequence)
                (flush)
                (System/exit (:exit result)))
              ;; Unix: replace process (cleaner process tree). Wrapped so the
              ;; terminal is restored if the harness is killed rather than
              ;; exiting — `docker kill`, or the daemon going away.
              (apply p/exec (terminal/wrap-with-restore
                             (vec (concat docker-args container-cmd)))))))))))

(defn run-exec
  "Run one-off command in container.

   Arguments:
   - cmd-args: Vector of command + arguments (e.g., [\"ls\" \"-la\"])
   - opts: Optional map with :base-image (start on the Base image,
     without the Project extension)

   Auto-detects TTY. Uses all standard mounts/env from config.
   Skips detection warnings and pre_start hooks for fast execution."
  [cmd-args & [opts]]
  ;; Refuse a home-containing root before anything walks the tree
  (validation/check-project-dir! (System/getProperty "user.dir"))

  (docker/check-docker!)

  (let [state (state/read-state)]
    (when-not state
      (output/error-no-setup))

    (let [project-dir (System/getProperty "user.dir")
          image-tag (resolve-image-tag project-dir false opts)
          _ (build/warn-if-foundation-out-of-date! image-tag)
          cfg (config/load-config project-dir)
          git-id (docker-run/read-git-identity project-dir)

          harness-volume-name (ensure-harness-volume state cfg)

          ;; False when piped or scripted
          tty? (output/tty?)

          ;; Skips pre_start
          docker-args (docker-run/build-docker-args-for-exec
                       {:project-dir project-dir
                        :image-tag image-tag
                        :config cfg
                        :state state
                        :git-identity git-id
                        :tty? tty?
                        :harness-volume-name harness-volume-name})

          container-cmd cmd-args

          ;; :continue true prevents exception on non-zero exit
          result (apply p/shell {:inherit true :continue true}
                        (concat docker-args container-cmd))]

      (System/exit (:exit result)))))
