(ns aishell.cli
  (:require [babashka.cli :as cli]
            [clojure.string :as str]
            [clojure.pprint :as pp]
            [aishell.docker :as docker]
            [aishell.docker.build :as build]
            [aishell.docker.hash :as hash]
            [aishell.docker.naming :as naming]
            [aishell.docker.templates :as templates]
            [aishell.docker.volume :as vol]
            [aishell.output :as output]
            [aishell.run :as run]
            [aishell.check :as check]
            [aishell.state :as state]
            [aishell.config :as config]
            [aishell.util :as util]
            [aishell.attach :as attach]
            [aishell.migration :as migration]))

(def version "2.9.1")

(defn print-version []
  (println (str "aishell " version)))

(defn print-version-json []
  (println (str "{\"name\":\"aishell\",\"version\":\"" version "\"}")))

(def global-spec
  {:help    {:alias :h :coerce :boolean :desc "Show help"}
   :version {:alias :v :coerce :boolean :desc "Show version"}
   :json    {:coerce :boolean :desc "Output in JSON format"}})

;; Version validation patterns
(def semver-pattern
  #"^\d+\.\d+\.\d+(-[a-zA-Z0-9.-]+)?(\+[a-zA-Z0-9.-]+)?$")

(def dangerous-chars #"[;&|`$(){}\[\]<>!\\]")

(defn validate-version
  "Validate version string. Returns nil on success, exits with error on failure."
  [version harness-name]
  (when (and version (not= version "true") (not= version "latest"))
    (cond
      (re-find dangerous-chars version)
      (output/error (str "Invalid " harness-name " version: contains shell metacharacters"))

      (not (re-matches semver-pattern version))
      (output/error (str "Invalid " harness-name " version format: " version
                        "\nExpected: X.Y.Z or X.Y.Z-prerelease (e.g., 2.0.22, 1.0.0-beta.1)")))))

(defn parse-with-flag
  "Parse --with-X flag value.
   nil -> {:enabled? false}
   true or 'true' -> {:enabled? true} (flag without value)
   'latest' -> {:enabled? true}
   version -> {:enabled? true :version version}"
  [value]
  (cond
    (nil? value) {:enabled? false}
    (true? value) {:enabled? true}  ; boolean true from CLI
    (= value "true") {:enabled? true}
    (= value "latest") {:enabled? true}
    :else {:enabled? true :version (str value)}))

;; Setup subcommand spec
;; Note: with-claude/with-opencode don't use :coerce because babashka.cli
;; returns boolean true for flags without values, which can't be coerced to string.
;; parse-with-flag handles both boolean true and string values.
(def setup-spec
  {:with-claude   {:desc "Include Claude Code (optional: =VERSION)"}
   :with-opencode {:desc "Include OpenCode (optional: =VERSION)"}
   :with-codex    {:desc "Include Codex CLI (optional: =VERSION)"}
   :with-gemini   {:desc "Include Gemini CLI (optional: =VERSION)"}
   :without-gitleaks {:coerce :boolean :desc "Skip Gitleaks installation"}
   :with-tmux     {:coerce :boolean :desc "Enable tmux multiplexer in container"}
   :force         {:coerce :boolean :desc "Force rebuild (bypass Docker cache)"}
   :verbose       {:alias :v :coerce :boolean :desc "Show full Docker build output"}
   :help          {:alias :h :coerce :boolean :desc "Show setup help"}})

(defn installed-harnesses
  "Return set of installed harness names based on state.
   If no state file exists (no build yet), returns all harnesses
   to aid discoverability."
  []
  (if-let [state (state/read-state)]
    (cond-> #{}
      (:with-claude state) (conj "claude")
      (:with-opencode state) (conj "opencode")
      (:with-codex state) (conj "codex")
      (:with-gemini state) (conj "gemini")
      (:with-gitleaks state true) (conj "gitleaks"))
    ;; No state = no build yet, show all for discoverability
    #{"claude" "opencode" "codex" "gemini" "gitleaks"}))

(defn print-help []
  (println (str output/BOLD "Usage:" output/NC " aishell [OPTIONS] COMMAND [ARGS...]"))
  (println)
  (println "Set up and run ephemeral containers for AI harnesses.")
  (println)
  (println (str output/BOLD "Commands:" output/NC))
  (println (str "  " output/CYAN "setup" output/NC "      Set up the container image and select harnesses"))
  (println (str "  " output/CYAN "update" output/NC "     Refresh harness tools to latest versions"))
  (println (str "  " output/CYAN "check" output/NC "      Validate setup and configuration"))
  (println (str "  " output/CYAN "exec" output/NC "       Run one-off command in container"))
  (println (str "  " output/CYAN "ps" output/NC "         List project containers"))
  (println (str "  " output/CYAN "volumes" output/NC "    Manage harness volumes"))
  (println (str "  " output/CYAN "attach" output/NC "     Attach to running container"))
  ;; Conditionally show harness commands based on installation
  (let [installed (installed-harnesses)]
    (when (contains? installed "claude")
      (println (str "  " output/CYAN "claude" output/NC "     Run Claude Code")))
    (when (contains? installed "opencode")
      (println (str "  " output/CYAN "opencode" output/NC "   Run OpenCode")))
    (when (contains? installed "codex")
      (println (str "  " output/CYAN "codex" output/NC "      Run Codex CLI")))
    (when (contains? installed "gemini")
      (println (str "  " output/CYAN "gemini" output/NC "     Run Gemini CLI")))
    (when (contains? installed "gitleaks")
      (println (str "  " output/CYAN "gitleaks" output/NC "   Run Gitleaks secret scanner"))))
  (println (str "  " output/CYAN "(none)" output/NC "     Enter interactive shell"))
  (println)
  (println (str output/BOLD "Global Options:" output/NC))
  (println (cli/format-opts {:spec global-spec
                             :order [:help :version :json]}))
  (println)
  (println (str output/BOLD "Examples:" output/NC))
  (println (str "  " output/CYAN "aishell setup --with-claude" output/NC "     Set up with Claude Code"))
  (println (str "  " output/CYAN "aishell check" output/NC "                    Validate setup"))
  (println (str "  " output/CYAN "aishell exec ls -la" output/NC "             Run command in container"))
  (println (str "  " output/CYAN "aishell ps" output/NC "                       List containers"))
  (println (str "  " output/CYAN "aishell claude" output/NC "                  Run Claude Code"))
  (println (str "  " output/CYAN "aishell codex" output/NC "                   Run Codex CLI"))
  (println (str "  " output/CYAN "aishell" output/NC "                         Enter shell")))

(defn print-setup-help []
  (println (str output/BOLD "Usage:" output/NC " aishell setup [OPTIONS]"))
  (println)
  (println "Set up the container image and select harnesses.")
  (println)
  (println (str output/BOLD "Options:" output/NC))
  (println (cli/format-opts {:spec setup-spec
                             :order [:with-claude :with-opencode :with-codex :with-gemini :with-tmux :without-gitleaks :force :verbose :help]}))
  (println)
  (println (str output/BOLD "Examples:" output/NC))
  (println (str "  " output/CYAN "aishell setup" output/NC "                      Set up base image"))
  (println (str "  " output/CYAN "aishell setup --with-claude" output/NC "        Include Claude Code (latest)"))
  (println (str "  " output/CYAN "aishell setup --with-claude=2.0.22" output/NC " Pin Claude Code version"))
  (println (str "  " output/CYAN "aishell setup --with-claude --with-opencode" output/NC " Include both"))
  (println (str "  " output/CYAN "aishell setup --with-codex --with-gemini" output/NC " Include Codex and Gemini"))
  (println (str "  " output/CYAN "aishell setup --with-claude --with-tmux" output/NC " Include Claude + tmux"))
  (println (str "  " output/CYAN "aishell setup --without-gitleaks" output/NC "   Skip Gitleaks"))
  (println (str "  " output/CYAN "aishell setup --force" output/NC "               Force rebuild")))

(defn handle-setup [{:keys [opts]}]
  ;; Show migration warning on first touch for upgraders
  (migration/show-v2.9-migration-warning!)
  (if (:help opts)
    (print-setup-help)
    (let [;; Parse flags
          claude-config (parse-with-flag (:with-claude opts))
          opencode-config (parse-with-flag (:with-opencode opts))
          codex-config (parse-with-flag (:with-codex opts))
          gemini-config (parse-with-flag (:with-gemini opts))
          with-gitleaks (not (:without-gitleaks opts))  ; invert flag for positive tracking
          with-tmux (boolean (:with-tmux opts))

          ;; Validate versions before build
          _ (validate-version (:version claude-config) "Claude Code")
          _ (validate-version (:version opencode-config) "OpenCode")
          _ (validate-version (:version codex-config) "Codex")
          _ (validate-version (:version gemini-config) "Gemini")

          ;; Show replacement message if image exists
          _ (when (docker/image-exists? build/foundation-image-tag)
              (println "Replacing existing image..."))

          ;; Build foundation image (harness tools will be volume-mounted in Phase 36)
          result (build/build-foundation-image
                   {:with-gitleaks with-gitleaks
                    :verbose (:verbose opts)
                    :force (:force opts)})

          ;; Step 2: Compute harness volume hash
          project-dir (System/getProperty "user.dir")
          cfg (config/load-config project-dir)
          state-map {:with-claude (:enabled? claude-config)
                     :with-opencode (:enabled? opencode-config)
                     :with-codex (:enabled? codex-config)
                     :with-gemini (:enabled? gemini-config)
                     :with-gitleaks with-gitleaks
                     :with-tmux with-tmux
                     :tmux-plugins (when with-tmux
                                     (let [plugins (vec (or (get-in cfg [:tmux :plugins]) []))
                                           resurrect-val (get-in cfg [:tmux :resurrect])
                                           resurrect-cfg (config/parse-resurrect-config resurrect-val)
                                           needs-resurrect? (:enabled resurrect-cfg)
                                           has-resurrect? (some #(= % "tmux-plugins/tmux-resurrect") plugins)]
                                       (if (and needs-resurrect? (not has-resurrect?))
                                         (conj plugins "tmux-plugins/tmux-resurrect")
                                         plugins)))
                     :claude-version (:version claude-config)
                     :opencode-version (:version opencode-config)
                     :codex-version (:version codex-config)
                     :gemini-version (:version gemini-config)
                     :resurrect-config (when with-tmux
                                         (config/parse-resurrect-config (get-in cfg [:tmux :resurrect])))}

          harness-hash (vol/compute-harness-hash state-map)
          volume-name (vol/volume-name harness-hash)

          ;; Step 3: Populate volume if needed (only if missing or stale)
          _ (when (some #(get state-map %) [:with-claude :with-opencode :with-codex :with-gemini :with-tmux])
              (let [vol-missing? (not (vol/volume-exists? volume-name))
                    vol-stale? (and (not vol-missing?)
                                   (not= (vol/get-volume-label volume-name "aishell.harness.hash")
                                         harness-hash))
                    ;; Compute harness list for label
                    harness-list (str/join "," (keep (fn [[k v]]
                                                       (when v (name k)))
                                                     {:claude (:with-claude state-map)
                                                      :opencode (:with-opencode state-map)
                                                      :codex (:with-codex state-map)
                                                      :gemini (:with-gemini state-map)}))]
                (when (or vol-missing? vol-stale?)
                  (when vol-missing?
                    (vol/create-volume volume-name {"aishell.harness.hash" harness-hash
                                                    "aishell.harness.version" "2.8.0"
                                                    "aishell.harnesses" harness-list}))
                  (let [pop-result (vol/populate-volume volume-name state-map {:verbose (:verbose opts) :config cfg})]
                    (when-not (:success pop-result)
                      (when vol-missing? (vol/remove-volume volume-name))
                      (output/error "Failed to populate harness volume"))))))]

      ;; Persist state (always, even on failure this won't run due to error exit)
      (state/write-state
        (assoc state-map
               :image-tag (:image result)
               :build-time (str (java.time.Instant/now))
               :dockerfile-hash (hash/compute-hash templates/base-dockerfile)  ; Kept for v2.7.0 compat
               :foundation-hash (hash/compute-hash templates/base-dockerfile)  ; NEW: same as dockerfile-hash for now
               :harness-volume-hash harness-hash                               ; NEW
               :harness-volume-name volume-name)))))                           ; NEW

(defn handle-default [{:keys [opts args]}]
  (cond
    (:version opts)
    (if (:json opts)
      (print-version-json)
      (print-version))

    (:help opts)
    (print-help)

    ;; Unknown command - check for typos
    (seq args)
    (output/error-unknown-command (first args))

    ;; No command, no flags - run shell
    :else
    (run/run-container nil [] {})))

(def update-spec
  {:force   {:coerce :boolean :desc "Also rebuild foundation image (--no-cache)"}
   :verbose {:alias :v :coerce :boolean :desc "Show full build and install output"}
   :help    {:alias :h :coerce :boolean :desc "Show update help"}})

(def volumes-prune-spec
  {:yes {:alias :y :coerce :boolean :desc "Skip confirmation prompt"}})

(defn print-update-help []
  (println (str output/BOLD "Usage:" output/NC " aishell update [OPTIONS]"))
  (println)
  (println "Refresh harness tools to latest versions using existing configuration.")
  (println)
  (println (str output/BOLD "Options:" output/NC))
  (println (cli/format-opts {:spec update-spec
                             :order [:force :verbose :help]}))
  (println)
  (println (str output/BOLD "Notes:" output/NC))
  (println "  - Refreshes harnesses from last build configuration")
  (println "  - To change which harnesses are installed, use 'aishell setup'")
  (println "  - Volume is deleted and recreated for a clean slate")
  (println "  - Foundation image is NOT rebuilt (unless --force is used)")
  (println)
  (println (str output/BOLD "Examples:" output/NC))
  (println (str "  " output/CYAN "aishell update" output/NC "          Refresh harness tools only"))
  (println (str "  " output/CYAN "aishell update --force" output/NC "  Also rebuild foundation image")))

(defn handle-update
  "Update command: refresh harness tools to latest versions.
   Default behavior: repopulates volume (delete + recreate) without rebuilding foundation.
   With --force: also rebuilds foundation image using --no-cache."
  [{:keys [opts]}]
  (if (:help opts)
    (print-update-help)
    (let [state (state/read-state)]
      ;; Must have prior build
      (when-not state
        (output/error "No previous setup found. Run: aishell setup"))

      ;; Show what we're updating
      (println "Updating with preserved configuration...")
      (when (:with-claude state)
        (println (str "  Claude Code: " (or (:claude-version state) "latest"))))
      (when (:with-opencode state)
        (println (str "  OpenCode: " (or (:opencode-version state) "latest"))))
      (when (:with-codex state)
        (println (str "  Codex: " (or (:codex-version state) "latest"))))
      (when (:with-gemini state)
        (println (str "  Gemini: " (or (:gemini-version state) "latest"))))
      (when (:with-tmux state)
        (println "  tmux: enabled"))

      ;; Conditionally rebuild foundation image (only with --force)
      (let [project-dir (System/getProperty "user.dir")
            cfg (config/load-config project-dir)
            result (when (:force opts)
                     (build/build-foundation-image
                       {:with-gitleaks (:with-gitleaks state true)
                        :verbose (:verbose opts)
                        :force true}))

            ;; Volume repopulation (unconditional delete + recreate)
            harness-hash (vol/compute-harness-hash state)
            volume-name (or (:harness-volume-name state)
                           (vol/volume-name harness-hash))

            ;; Check if any harness is enabled
            harnesses-enabled? (some #(get state %) [:with-claude :with-opencode :with-codex :with-gemini :with-tmux])

            _ (if harnesses-enabled?
                ;; Repopulate volume (delete + recreate)
                (let [;; Compute harness list for label
                      harness-list (str/join "," (keep (fn [[k v]]
                                                         (when v (name k)))
                                                       {:claude (:with-claude state)
                                                        :opencode (:with-opencode state)
                                                        :codex (:with-codex state)
                                                        :gemini (:with-gemini state)}))]
                  (println "Repopulating harness volume...")
                  (vol/remove-volume volume-name)
                  (vol/create-volume volume-name {"aishell.harness.hash" harness-hash
                                                  "aishell.harness.version" "2.8.0"
                                                  "aishell.harnesses" harness-list})
                  (let [pop-result (vol/populate-volume volume-name state {:verbose (:verbose opts) :config cfg})]
                    (when-not (:success pop-result)
                      (vol/remove-volume volume-name)
                      (output/error "Failed to populate harness volume"))))
                ;; No harnesses enabled
                (println "No harnesses enabled. Nothing to update."))]

        ;; Update state with new build-time (and foundation-hash if --force was used)
        (state/write-state
          (cond-> state
            true (assoc :build-time (str (java.time.Instant/now)))
            result (assoc :image-tag (:image result)
                         :dockerfile-hash (hash/compute-hash templates/base-dockerfile)
                         :foundation-hash (hash/compute-hash templates/base-dockerfile))))))))

(defn- prompt-yn
  "Prompt user for yes/no confirmation."
  [message]
  (print (str message " (y/n): "))
  (flush)
  (= "y" (str/trim (read-line))))

(defn handle-volumes-list
  "List all harness volumes with active/orphaned status."
  []
  (let [state (state/read-state)
        volumes (vol/list-harness-volumes)
        current-vol (:harness-volume-name state)]
    (if (empty? volumes)
      (println "No harness volumes found.\n\nVolumes are created automatically during 'aishell setup' when harnesses are enabled.")
      (do
        (pp/print-table [:NAME :STATUS :SIZE :HARNESSES]
                       (map (fn [{:keys [name harnesses]}]
                              {:NAME name
                               :STATUS (if (= name current-vol) "active" "orphaned")
                               :SIZE (vol/get-volume-size name)
                               :HARNESSES (or harnesses "unknown")})
                            volumes))
        (println "\nTo remove orphaned volumes: aishell volumes prune")))))

(defn handle-volumes-prune
  "Remove orphaned volumes with confirmation prompt."
  [opts]
  (let [state (state/read-state)
        current-vol (:harness-volume-name state)
        all-volumes (vol/list-harness-volumes)
        orphaned (filter #(and (not= (:name %) current-vol)
                              (str/starts-with? (:name %) "aishell-harness-"))
                        all-volumes)]
    (if (empty? orphaned)
      (println "No orphaned volumes to prune.")
      (do
        (println "The following volumes will be removed:")
        (doseq [{:keys [name]} orphaned]
          (println (str "  - " name)))
        (when (or (:yes opts) (prompt-yn "Remove these volumes?"))
          (doseq [{:keys [name]} orphaned]
            (if (vol/volume-in-use? name)
              (println (str "Skipping " name " (in use by container)"))
              (do
                (vol/remove-volume name)
                (println (str "Removed " name)))))
          (println "Prune complete."))))))

(defn print-volumes-help
  "Print help for volumes command."
  []
  (println (str output/BOLD "Usage:" output/NC " aishell volumes [SUBCOMMAND]"))
  (println)
  (println "Manage harness volumes.")
  (println)
  (println (str output/BOLD "Subcommands:" output/NC))
  (println "  list     List all harness volumes (default)")
  (println "  prune    Remove orphaned volumes")
  (println)
  (println (str output/BOLD "Prune Options:" output/NC))
  (println (cli/format-opts {:spec volumes-prune-spec
                             :order [:yes]}))
  (println)
  (println (str output/BOLD "Examples:" output/NC))
  (println (str "  " output/CYAN "aishell volumes" output/NC "              List volumes"))
  (println (str "  " output/CYAN "aishell volumes list" output/NC "        List volumes"))
  (println (str "  " output/CYAN "aishell volumes prune" output/NC "       Remove orphaned volumes"))
  (println (str "  " output/CYAN "aishell volumes prune --yes" output/NC " Skip confirmation")))

(defn handle-volumes
  "Dispatcher for volumes subcommands."
  [args]
  (let [subcommand (first args)]
    (cond
      (or (= subcommand "--help") (= subcommand "-h"))
      (print-volumes-help)

      (or (nil? subcommand) (= subcommand "list"))
      (handle-volumes-list)

      (= subcommand "prune")
      (let [opts (cli/parse-opts (vec (rest args)) {:spec volumes-prune-spec})]
        (handle-volumes-prune opts))

      :else
      (output/error (str "Unknown volumes subcommand: " subcommand
                        "\n\nValid subcommands: list, prune"
                        "\n\nTry: aishell volumes --help")))))

(defn- extract-short-name
  "Extract user-friendly name from full container name.
   Example: 'aishell-a1b2c3d4-claude' -> 'claude'"
  [container-name]
  (last (str/split container-name #"-" 3)))

(defn- format-container
  "Format container map for display with uppercase column headers."
  [c]
  {:NAME (extract-short-name (:name c))
   :STATUS (:status c)
   :CREATED (:created c)})

(defn handle-ps
  "List all containers for the current project."
  [_]
  (let [project-dir (System/getProperty "user.dir")
        containers (naming/list-project-containers project-dir)]
    (if (empty? containers)
      (println "No containers found for this project.\n\nTo start a container:\n  aishell claude --detach\n  aishell opencode --detach --name my-session\n\nContainers are project-specific (based on current directory).")
      (do
        (println "Containers for this project:\n")
        (pp/print-table [:NAME :STATUS :CREATED] (map format-container containers))
        (println "\nTo attach: aishell attach --name <name>")))))

(def dispatch-table
  [{:cmds ["setup"] :fn handle-setup :spec setup-spec :restrict true}
   {:cmds ["update"] :fn handle-update :spec update-spec :restrict true}
   {:cmds [] :spec global-spec :fn handle-default}])

(defn handle-error
  "Handle CLI parsing errors with helpful messages."
  [{:keys [cause option msg]}]
  (case cause
    :restrict
    (do
      (binding [*out* *err*]
        (println (str output/RED "Error:" output/NC " Unknown option: " option))
        (println (str "Try: " output/CYAN "aishell --help" output/NC)))
      (System/exit 1))

    ;; Default
    (output/error msg)))

(defn dispatch [args]
  ;; Show migration warning on run commands for upgraders
  (migration/show-v2.9-migration-warning!)
  ;; Extract --unsafe flag before pass-through (used by detection framework)
  (let [unsafe? (boolean (some #{"--unsafe"} args))
        clean-args (vec (remove #{"--unsafe"} args))

        ;; Extract --detach/-d flag before pass-through
        detach? (boolean (some #{"-d" "--detach"} clean-args))
        clean-args (vec (remove #{"-d" "--detach"} clean-args))

        ;; Extract --name flag (--name VALUE format) for harness commands only
        ;; attach and other commands parse their own --name flag
        harness-commands #{"claude" "opencode" "codex" "gemini" "gitleaks"}
        should-extract-name? (contains? harness-commands (first clean-args))
        container-name-override (when should-extract-name?
                                  (let [idx (.indexOf (vec clean-args) "--name")]
                                    (when (and (>= idx 0) (< (inc idx) (count clean-args)))
                                      (nth clean-args (inc idx)))))
        clean-args (if container-name-override
                     (let [idx (.indexOf (vec clean-args) "--name")]
                       (into (subvec (vec clean-args) 0 idx)
                             (subvec (vec clean-args) (+ idx 2))))
                     clean-args)]
    ;; Handle pass-through commands before standard dispatch
    ;; This ensures all args (including --help, --version) go to the harness
    (case (first clean-args)
      "check" (check/run-check)
      "exec" (run/run-exec (vec (rest clean-args)))
      "ps" (handle-ps nil)
      "volumes" (handle-volumes (vec (rest clean-args)))
      "attach" (let [rest-args (vec (rest clean-args))]
                 (cond
                   (some #{"-h" "--help"} rest-args)
                   (do
                     (println (str output/BOLD "Usage:" output/NC " aishell attach --name <name> [OPTIONS]"))
                     (println)
                     (println "Attach to a running container's tmux session.")
                     (println)
                     (println (str output/BOLD "Options:" output/NC))
                     (println "  --name <name>        Container name to attach to")
                     (println "  --session <session>  Tmux session name (default: harness)")
                     (println "  --shell              Open a bash shell (creates tmux session 'shell')")
                     (println "  -h, --help           Show this help")
                     (println)
                     (println (str output/BOLD "Examples:" output/NC))
                     (println (str "  " output/CYAN "aishell attach --name claude" output/NC))
                     (println (str "      Attach to the 'claude' container's harness session"))
                     (println)
                     (println (str "  " output/CYAN "aishell attach --name claude --shell" output/NC))
                     (println (str "      Open a bash shell in the 'claude' container"))
                     (println)
                     (println (str "  " output/CYAN "aishell attach --name experiment --session debug" output/NC))
                     (println (str "      Attach to specific session in 'experiment' container"))
                     (println)
                     (println (str output/BOLD "Notes:" output/NC))
                     (println "  --shell and --session are mutually exclusive.")
                     (println "  Press Ctrl+B then D to detach without stopping the container.")
                     (println "  Multiple users can attach to the same container simultaneously."))

                   (empty? rest-args)
                   (output/error "Container name required.\n\nUsage: aishell attach --name <name>\n\nUse 'aishell ps' to list running containers.")

                   :else
                   (let [opts (cli/parse-opts rest-args {:spec {:name {} :session {} :shell {:coerce :boolean}}})]
                     (cond
                       (not (:name opts))
                       (output/error "Container name required.\n\nUsage: aishell attach --name <name>\n\nUse 'aishell ps' to list running containers.")

                       (and (:shell opts) (:session opts))
                       (output/error "--shell and --session are mutually exclusive.\n\n--shell creates a bash session named 'shell'.\n--session attaches to a specific existing tmux session.")

                       (:shell opts)
                       (attach/attach-shell (:name opts))

                       :else
                       (attach/attach-to-session (:name opts) (or (:session opts) "harness"))))))
      "claude" (run/run-container "claude" (vec (rest clean-args))
                 {:unsafe unsafe? :container-name container-name-override :detach detach?})
      "opencode" (run/run-container "opencode" (vec (rest clean-args))
                   {:unsafe unsafe? :container-name container-name-override :detach detach?})
      "codex" (run/run-container "codex" (vec (rest clean-args))
               {:unsafe unsafe? :container-name container-name-override :detach detach?})
      "gemini" (run/run-container "gemini" (vec (rest clean-args))
                {:unsafe unsafe? :container-name container-name-override :detach detach?})
      "gitleaks" (run/run-container "gitleaks" (vec (rest clean-args))
                   {:unsafe unsafe? :container-name container-name-override :skip-pre-start true :detach detach?})
      ;; Standard dispatch for other commands (setup, update, help)
      (if unsafe?
        ;; --unsafe with no harness command -> shell mode with unsafe
        (run/run-container nil [] {:unsafe true :container-name container-name-override :detach detach?})
        ;; Normal dispatch
        (cli/dispatch dispatch-table args {:error-fn handle-error
                                           :restrict true})))))
