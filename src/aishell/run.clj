(ns aishell.run
  "Run command orchestration.
   Handles shell, claude, and opencode execution in containers."
  (:require [babashka.process :as p]
            [aishell.docker :as docker]
            [aishell.docker.run :as docker-run]
            [aishell.docker.hash :as hash]
            [aishell.docker.templates :as templates]
            [aishell.config :as config]
            [aishell.state :as state]
            [aishell.output :as output]
            [aishell.validation :as validation]))

(defn- verify-harness-available
  "Check that harness was included in build. Exit with error if not."
  [harness-name state-key state]
  (when-not (get state state-key)
    (output/error
      (str (case harness-name
             "claude" "Claude Code"
             "opencode" "OpenCode")
           " not installed. Run: aishell build --with-"
           harness-name))))

(defn- check-dockerfile-stale
  "Check if embedded Dockerfile changed since build, warn if so.
   Advisory only - does not block execution."
  [state]
  (when-let [stored-hash (:dockerfile-hash state)]
    (let [current-hash (hash/compute-hash templates/base-dockerfile)]
      (when (not= stored-hash current-hash)
        (output/warn "Image may be stale. Run 'aishell update' to rebuild.")))))

(defn run-container
  "Run docker container for shell or harness.

   Arguments:
   - cmd: nil (shell), \"claude\", or \"opencode\"
   - harness-args: Extra arguments to pass to harness (vector)"
  [cmd harness-args]
  ;; Check Docker available
  (docker/check-docker!)

  ;; Read state (contains build info)
  (let [state (state/read-state)]
    ;; Verify build exists
    (when-not state
      (output/error-no-build))

    ;; Verify image exists
    (let [image-tag (or (:image-tag state) "aishell:base")]
      (when-not (docker/image-exists? image-tag)
        (output/error (str "Image not found: " image-tag
                          "\nRun: aishell build")))

      ;; Verify harness if requested
      (case cmd
        "claude" (verify-harness-available "claude" :with-claude state)
        "opencode" (verify-harness-available "opencode" :with-opencode state)
        nil)

      ;; Load config
      (let [project-dir (System/getProperty "user.dir")
            cfg (config/load-config project-dir)
            git-id (docker-run/read-git-identity project-dir)

            ;; Verbose output (when we add --verbose support)
            _ (when cfg
                (output/verbose (str "Loaded config from: "
                                    (name (config/config-source project-dir)))))
            _ (when (and (:name git-id) (:email git-id))
                (output/verbose (str "Git identity: " (:name git-id)
                                    " <" (:email git-id) ">")))

            ;; Check for stale image (advisory warning)
            _ (check-dockerfile-stale state)

            ;; Warn about dangerous docker_args (advisory warning)
            _ (when-let [docker-args (:docker_args cfg)]
                (validation/warn-dangerous-args docker-args))

            ;; Build docker args
            docker-args (docker-run/build-docker-args
                          {:project-dir project-dir
                           :image-tag image-tag
                           :config cfg
                           :git-identity git-id})

            ;; Determine command to run in container
            container-cmd (case cmd
                            "claude"
                            (into ["claude" "--dangerously-skip-permissions"]
                                  harness-args)

                            "opencode"
                            (into ["opencode"] harness-args)

                            ;; Default: bash shell
                            ["/bin/bash"])]

        ;; Execute - replaces current process
        (apply p/exec (concat docker-args container-cmd))))))
