(ns aishell.run
  "Run command orchestration.
   Handles shell, claude, and opencode execution in containers."
  (:require [babashka.process :as p]
            [babashka.fs :as fs]
            [aishell.docker :as docker]
            [aishell.docker.base :as base]
            [aishell.docker.naming :as naming]
            [aishell.docker.run :as docker-run]
            [aishell.docker.hash :as hash]
            [aishell.docker.templates :as templates]
            [aishell.docker.extension :as ext]
            [aishell.docker.volume :as vol]
            [aishell.config :as config]
            [aishell.state :as state]
            [aishell.output :as output]
            [aishell.validation :as validation]
            [aishell.detection.core :as detection]
            [aishell.gitleaks.warnings :as gitleaks-warnings]
            [aishell.gitleaks.scan-state :as scan-state]))

(defn- verify-harness-available
  "Check that harness was included in setup. Exit with error if not."
  [harness-name state-key state]
  (when-not (get state state-key)
    (output/error
      (str (case harness-name
             "claude" "Claude Code"
             "opencode" "OpenCode"
             "codex" "Codex CLI"
             "gemini" "Gemini CLI"
             "pi" "Pi coding agent")
           " not installed. Run: aishell setup --with-"
           harness-name))))

(defn- check-dockerfile-stale
  "Check if embedded Dockerfile changed since build, warn if so.
   Advisory only - does not block execution."
  [state]
  (when-let [stored-hash (:dockerfile-hash state)]
    (let [current-hash (hash/compute-hash templates/base-dockerfile)]
      (when (not= stored-hash current-hash)
        (output/warn "Image may be stale. Run 'aishell update' to rebuild.")))))

(defn- ensure-harness-volume
  "Ensure harness volume exists and is up-to-date.
   Populates lazily if missing or stale (hash mismatch).
   Returns volume name for docker run mounting, or nil if no harnesses enabled."
  [state config]
  (when (some #(get state %) [:with-claude :with-opencode :with-codex :with-gemini :with-pi :with-openspec])
    (let [expected-hash (vol/compute-harness-hash state)
          volume-name (or (:harness-volume-name state)
                          (vol/volume-name expected-hash))]
      (cond
        ;; Volume missing - create and populate
        (not (vol/volume-exists? volume-name))
        (do
          (vol/create-volume volume-name {"aishell.harness.hash" expected-hash
                                          "aishell.harness.version" "3.1.0"})
          (let [result (vol/populate-volume volume-name state {:config config})]
            (when-not (:success result)
              ;; Remove empty volume so next run retries population
              (vol/remove-volume volume-name)
              (output/error "Failed to populate harness volume"))))

        ;; Volume exists but stale (hash mismatch or missing label)
        (not= (vol/get-volume-label volume-name "aishell.harness.hash")
              expected-hash)
        (let [result (vol/populate-volume volume-name state {:config config})]
          (when-not (:success result)
            (output/error "Failed to populate harness volume"))))
      ;; Return volume name regardless
      volume-name)))

(defn resolve-image-tag
  "Determine which image to use: extended if project has .aishell/Dockerfile, else base.
   Ensures base image is up to date before extension resolution.
   Auto-builds extension if needed (matches bash behavior)."
  [_base-tag project-dir force?]
  ;; Ensure base image is up to date (lazy build from ~/.aishell/Dockerfile)
  (base/ensure-base-image {})
  (if-let [_dockerfile (ext/project-dockerfile project-dir)]
    ;; Project has extension — builds FROM aishell:base
    (let [extended-tag (ext/compute-extended-tag project-dir)]
      (when (ext/needs-extended-rebuild? extended-tag base/base-image-tag project-dir)
        (ext/build-extended-image
          {:project-dir project-dir
           :foundation-tag base/base-image-tag
           :extended-tag extended-tag
           :force force?
           :verbose false}))
      extended-tag)
    ;; No extension, use base
    base/base-image-tag))

(defn run-container
  "Run docker container for shell or harness.

   Arguments:
   - cmd: nil (shell), \"claude\", or \"opencode\"
   - harness-args: Extra arguments to pass to harness (vector)
   - opts: Optional map with :unsafe (skip detection warnings)"
  [cmd harness-args & [opts]]
  ;; Check Docker available
  (docker/check-docker!)

  ;; Read state (contains build info)
  (let [state (state/read-state)]
    ;; Verify build exists
    (when-not state
      (output/error-no-setup))

    ;; Get project-dir FIRST (needed for extension resolution)
    (let [project-dir (System/getProperty "user.dir")
          base-tag base/base-image-tag
          cfg (config/load-config project-dir)

          ;; Ensure base image is up to date (lazy build from ~/.aishell/Dockerfile)
          _ (base/ensure-base-image {})

          ;; Resolve container name: --name override or harness name (or "shell" for shell mode)
          container-name-str (let [name-part (or (:container-name opts) cmd "shell")]
                               (naming/container-name project-dir name-part))

          ;; Pre-flight conflict check: error if running, auto-remove if stopped
          _ (naming/ensure-name-available! container-name-str (or (:container-name opts) cmd "shell"))

          ;; Log container name for verification
          _ (output/verbose (str "Container name: " container-name-str))

          ;; Ensure harness volume ready (lazy population)
          harness-volume-name (ensure-harness-volume state cfg)]

      ;; Verify BASE image exists (required before extension can build)
      (when-not (docker/image-exists? base-tag)
        (output/error (str "Image not found: " base-tag
                          "\nRun: aishell setup")))

      ;; Verify harness if requested
      (case cmd
        "claude" (verify-harness-available "claude" :with-claude state)
        "opencode" (verify-harness-available "opencode" :with-opencode state)
        "codex" (verify-harness-available "codex" :with-codex state)
        "gemini" (verify-harness-available "gemini" :with-gemini state)
        "pi" (verify-harness-available "pi" :with-pi state)
        nil)

      ;; Resolve final image (may auto-build extension)
      (let [image-tag (resolve-image-tag base-tag project-dir false)
            git-id (docker-run/read-git-identity project-dir)

            ;; Extract defaults for this harness (if any)
            defaults (when (and cfg cmd)
                       (get-in cfg [:harness_args (keyword cmd)] []))

            ;; Ensure defaults is a vector
            defaults-vec (vec (or defaults []))

            ;; Merge: defaults first, then CLI args (CLI can override by position)
            merged-args (vec (concat defaults-vec harness-args))

            ;; Verbose output (when we add --verbose support)
            _ (when cfg
                (output/verbose (str "Loaded config from: "
                                    (name (config/config-source project-dir)))))
            _ (when (and (:name git-id) (:email git-id))
                (output/verbose (str "Git identity: " (:name git-id)
                                    " <" (:email git-id) ">")))
            _ (when (seq defaults-vec)
                (output/verbose (str "Applying " cmd " defaults: "
                                    (clojure.string/join " " defaults-vec))))

            ;; Check for stale image (advisory warning)
            _ (check-dockerfile-stale state)

            ;; Warn about dangerous docker_args (advisory warning)
            _ (when-let [docker-args (:docker_args cfg)]
                (validation/warn-dangerous-args docker-args))

            ;; Warn about dangerous mount paths (advisory warning)
            _ (when-let [mounts (:mounts cfg)]
                (validation/warn-dangerous-mounts mounts))

            ;; Scan for sensitive files (unless --unsafe or gitleaks command)
            ;; Uses project-dir already bound at line 71
            _ (when-not (or (:unsafe opts) (= cmd "gitleaks"))
                (let [detection-config (get cfg :detection {})
                      allowlist (:allowlist detection-config [])
                      ;; scan-project checks :enabled and uses :custom_patterns
                      findings (detection/scan-project project-dir detection-config)
                      ;; filter out allowlisted files
                      filtered-findings (detection/filter-allowlisted findings allowlist project-dir)]
                  (when (seq filtered-findings)
                    (detection/display-warnings project-dir filtered-findings)
                    (detection/confirm-if-needed filtered-findings))))

            ;; Display gitleaks freshness warning only if gitleaks is installed
            _ (when (and (:with-gitleaks state) (not= cmd "gitleaks"))
                (gitleaks-warnings/display-freshness-warning project-dir cfg))

            ;; Build docker args
            docker-args (docker-run/build-docker-args
                          {:project-dir project-dir
                           :image-tag image-tag
                           :config cfg
                           :state state
                           :git-identity git-id
                           :skip-pre-start (:skip-pre-start opts)
                           :skip-interactive (= cmd "gitleaks")
                           :container-name container-name-str
                           :harness-volume-name harness-volume-name})

            ;; Determine command to run in container
            container-cmd (case cmd
                            "claude"
                            (into ["claude" "--dangerously-skip-permissions"]
                                  merged-args)

                            "opencode"
                            (into ["opencode"] merged-args)

                            "codex"
                            (into ["codex"] merged-args)

                            "gemini"
                            (into ["gemini"] merged-args)

                            "pi"
                            (into ["pi"] merged-args)

                            "gitleaks"
                            (into ["gitleaks"] harness-args)

                            ;; Default: bash shell
                            ["/bin/bash"])]

        ;; For gitleaks, use shell instead of exec so we can update timestamp after
        ;; :continue true prevents p/shell from throwing on non-zero exit
        (if (= cmd "gitleaks")
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
              ;; Windows: spawn child process with inherited I/O, wait, propagate exit
              (let [result @(apply p/process {:inherit true}
                                   (concat docker-args container-cmd))]
                (System/exit (:exit result)))
              ;; Unix: replace process (cleaner process tree)
              (apply p/exec (concat docker-args container-cmd)))))))))

(defn run-exec
  "Run one-off command in container.

   Arguments:
   - cmd-args: Vector of command + arguments (e.g., [\"ls\" \"-la\"])

   Auto-detects TTY. Uses all standard mounts/env from config.
   Skips detection warnings and pre_start hooks for fast execution."
  [cmd-args]
  ;; Check Docker available
  (docker/check-docker!)

  ;; Read state (contains build info)
  (let [state (state/read-state)]
    ;; Verify build exists
    (when-not state
      (output/error-no-setup))

    ;; Get project-dir FIRST (needed for extension resolution)
    (let [project-dir (System/getProperty "user.dir")
          base-tag base/base-image-tag

          ;; Ensure base image is up to date (lazy build from ~/.aishell/Dockerfile)
          _ (base/ensure-base-image {})]

      ;; Verify BASE image exists (required before extension can build)
      (when-not (docker/image-exists? base-tag)
        (output/error (str "Image not found: " base-tag
                          "\nRun: aishell setup")))

      ;; Resolve final image (may auto-build extension)
      (let [image-tag (resolve-image-tag base-tag project-dir false)
            cfg (config/load-config project-dir)
            git-id (docker-run/read-git-identity project-dir)

            ;; Ensure harness volume ready (lazy population)
            harness-volume-name (ensure-harness-volume state cfg)

            ;; Auto-detect TTY: true if running in terminal, false if piped/scripted
            tty? (some? (System/console))

            ;; Build docker args for exec (conditional TTY, skip pre_start)
            docker-args (docker-run/build-docker-args-for-exec
                          {:project-dir project-dir
                           :image-tag image-tag
                           :config cfg
                           :state state
                           :git-identity git-id
                           :tty? tty?
                           :harness-volume-name harness-volume-name})

            ;; Command to run in container (user's command)
            container-cmd cmd-args]

        ;; Execute command with inherited stdin/stdout/stderr
        ;; :continue true prevents exception on non-zero exit
        (let [result (apply p/shell {:inherit true :continue true}
                            (concat docker-args container-cmd))]
          ;; Propagate exit code to caller
          (System/exit (:exit result)))))))
