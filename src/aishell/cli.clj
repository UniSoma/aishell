(ns aishell.cli
  (:require [babashka.cli :as cli]
            [clojure.string :as str]
            [clojure.pprint :as pp]
            [aishell.docker :as docker]
            [aishell.docker.build :as build]
            [aishell.docker.hash :as hash]
            [aishell.docker.naming :as naming]
            [aishell.docker.templates :as templates]
            [aishell.output :as output]
            [aishell.run :as run]
            [aishell.check :as check]
            [aishell.state :as state]
            [aishell.util :as util]
            [aishell.attach :as attach]))

(def version "2.7.0")

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

;; Build subcommand spec
;; Note: with-claude/with-opencode don't use :coerce because babashka.cli
;; returns boolean true for flags without values, which can't be coerced to string.
;; parse-with-flag handles both boolean true and string values.
(def build-spec
  {:with-claude   {:desc "Include Claude Code (optional: =VERSION)"}
   :with-opencode {:desc "Include OpenCode (optional: =VERSION)"}
   :with-codex    {:desc "Include Codex CLI (optional: =VERSION)"}
   :with-gemini   {:desc "Include Gemini CLI (optional: =VERSION)"}
   :without-gitleaks {:coerce :boolean :desc "Skip Gitleaks installation"}
   :force         {:coerce :boolean :desc "Force rebuild (bypass Docker cache)"}
   :verbose       {:alias :v :coerce :boolean :desc "Show full Docker build output"}
   :help          {:alias :h :coerce :boolean :desc "Show build help"}})

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
  (println "Build and run ephemeral containers for AI harnesses.")
  (println)
  (println (str output/BOLD "Commands:" output/NC))
  (println (str "  " output/CYAN "build" output/NC "      Build the container image"))
  (println (str "  " output/CYAN "update" output/NC "     Rebuild with latest versions"))
  (println (str "  " output/CYAN "check" output/NC "      Validate setup and configuration"))
  (println (str "  " output/CYAN "exec" output/NC "       Run one-off command in container"))
  (println (str "  " output/CYAN "ps" output/NC "         List project containers"))
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
  (println (str "  " output/CYAN "aishell build --with-claude" output/NC "     Build with Claude Code"))
  (println (str "  " output/CYAN "aishell check" output/NC "                    Validate setup"))
  (println (str "  " output/CYAN "aishell exec ls -la" output/NC "             Run command in container"))
  (println (str "  " output/CYAN "aishell ps" output/NC "                       List containers"))
  (println (str "  " output/CYAN "aishell claude" output/NC "                  Run Claude Code"))
  (println (str "  " output/CYAN "aishell codex" output/NC "                   Run Codex CLI"))
  (println (str "  " output/CYAN "aishell" output/NC "                         Enter shell")))

(defn print-build-help []
  (println (str output/BOLD "Usage:" output/NC " aishell build [OPTIONS]"))
  (println)
  (println "Build the container image with optional harness installations.")
  (println)
  (println (str output/BOLD "Options:" output/NC))
  (println (cli/format-opts {:spec build-spec
                             :order [:with-claude :with-opencode :with-codex :with-gemini :without-gitleaks :force :verbose :help]}))
  (println)
  (println (str output/BOLD "Examples:" output/NC))
  (println (str "  " output/CYAN "aishell build" output/NC "                      Build base image"))
  (println (str "  " output/CYAN "aishell build --with-claude" output/NC "        Include Claude Code (latest)"))
  (println (str "  " output/CYAN "aishell build --with-claude=2.0.22" output/NC " Pin Claude Code version"))
  (println (str "  " output/CYAN "aishell build --with-claude --with-opencode" output/NC " Include both"))
  (println (str "  " output/CYAN "aishell build --with-codex --with-gemini" output/NC " Include Codex and Gemini"))
  (println (str "  " output/CYAN "aishell build --without-gitleaks" output/NC "   Skip Gitleaks"))
  (println (str "  " output/CYAN "aishell build --force" output/NC "               Force rebuild")))

(defn handle-build [{:keys [opts]}]
  (if (:help opts)
    (print-build-help)
    (let [;; Parse flags
          claude-config (parse-with-flag (:with-claude opts))
          opencode-config (parse-with-flag (:with-opencode opts))
          codex-config (parse-with-flag (:with-codex opts))
          gemini-config (parse-with-flag (:with-gemini opts))
          with-gitleaks (not (:without-gitleaks opts))  ; invert flag for positive tracking

          ;; Validate versions before build
          _ (validate-version (:version claude-config) "Claude Code")
          _ (validate-version (:version opencode-config) "OpenCode")
          _ (validate-version (:version codex-config) "Codex")
          _ (validate-version (:version gemini-config) "Gemini")

          ;; Show replacement message if image exists
          _ (when (docker/image-exists? build/base-image-tag)
              (println "Replacing existing image..."))

          ;; Build with force if specified (--force bypasses Docker cache)
          result (build/build-base-image
                   {:with-claude (:enabled? claude-config)
                    :with-opencode (:enabled? opencode-config)
                    :with-codex (:enabled? codex-config)
                    :with-gemini (:enabled? gemini-config)
                    :with-gitleaks with-gitleaks
                    :claude-version (:version claude-config)
                    :opencode-version (:version opencode-config)
                    :codex-version (:version codex-config)
                    :gemini-version (:version gemini-config)
                    :verbose (:verbose opts)
                    :force (:force opts)})]

      ;; Persist state (always, even on failure this won't run due to error exit)
      (state/write-state
        {:with-claude (:enabled? claude-config)
         :with-opencode (:enabled? opencode-config)
         :with-codex (:enabled? codex-config)
         :with-gemini (:enabled? gemini-config)
         :with-gitleaks with-gitleaks
         :claude-version (:version claude-config)
         :opencode-version (:version opencode-config)
         :codex-version (:version codex-config)
         :gemini-version (:version gemini-config)
         :image-tag (:image result)
         :build-time (str (java.time.Instant/now))
         :dockerfile-hash (hash/compute-hash templates/base-dockerfile)}))))

(defn handle-update
  "Update command: force rebuild with preserved configuration.
   Reads existing state and rebuilds with same flags + force (--no-cache).
   This is NOT a 'check for updates' command - it always rebuilds."
  [{:keys [opts]}]
  (let [state (state/read-state)]
    ;; Must have prior build
    (when-not state
      (output/error "No previous build found. Run: aishell build"))

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

    ;; Rebuild with same config + force (always --no-cache for update)
    (let [result (build/build-base-image
                   {:with-claude (:with-claude state)
                    :with-opencode (:with-opencode state)
                    :with-codex (:with-codex state)
                    :with-gemini (:with-gemini state)
                    :with-gitleaks (:with-gitleaks state true)  ; default true for backwards compat
                    :claude-version (:claude-version state)
                    :opencode-version (:opencode-version state)
                    :codex-version (:codex-version state)
                    :gemini-version (:gemini-version state)
                    :verbose (:verbose opts)
                    :force true})]  ; Always force for update

      ;; Update state with new build-time and hash
      (state/write-state
        (assoc state
               :image-tag (:image result)
               :build-time (str (java.time.Instant/now))
               :dockerfile-hash (hash/compute-hash templates/base-dockerfile))))))

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
  {:verbose {:alias :v :coerce :boolean :desc "Show full Docker build output"}})

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
  [{:cmds ["build"] :fn handle-build :spec build-spec :restrict true}
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
      "attach" (let [rest-args (vec (rest clean-args))]
                 (cond
                   (some #{"-h" "--help"} rest-args)
                   (do
                     (println (str output/BOLD "Usage:" output/NC " aishell attach --name <name> [OPTIONS]"))
                     (println)
                     (println "Attach to a running container's tmux session.")
                     (println)
                     (println (str output/BOLD "Options:" output/NC))
                     (println "  --name <name>       Container name to attach to")
                     (println "  --session <session>  Tmux session name (default: main)")
                     (println "  -h, --help           Show this help")
                     (println)
                     (println (str output/BOLD "Examples:" output/NC))
                     (println (str "  " output/CYAN "aishell attach --name claude" output/NC))
                     (println (str "      Attach to the 'claude' container's main session"))
                     (println)
                     (println (str "  " output/CYAN "aishell attach --name experiment --session debug" output/NC))
                     (println (str "      Attach to specific session in 'experiment' container"))
                     (println)
                     (println (str output/BOLD "Notes:" output/NC))
                     (println "  Press Ctrl+B then D to detach without stopping the container.")
                     (println "  Multiple users can attach to the same container simultaneously."))

                   (empty? rest-args)
                   (output/error "Container name required.\n\nUsage: aishell attach --name <name>\n\nUse 'aishell ps' to list running containers.")

                   :else
                   (let [opts (cli/parse-opts rest-args {:spec {:name {} :session {}}})]
                     (if-not (:name opts)
                       (output/error "Container name required.\n\nUsage: aishell attach --name <name>\n\nUse 'aishell ps' to list running containers.")
                       (attach/attach-to-session (:name opts) (or (:session opts) "main"))))))
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
      ;; Standard dispatch for other commands (build, update, help)
      (if unsafe?
        ;; --unsafe with no harness command -> shell mode with unsafe
        (run/run-container nil [] {:unsafe true :container-name container-name-override :detach detach?})
        ;; Normal dispatch
        (cli/dispatch dispatch-table args {:error-fn handle-error
                                           :restrict true})))))
