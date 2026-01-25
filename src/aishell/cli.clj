(ns aishell.cli
  (:require [babashka.cli :as cli]
            [clojure.string :as str]
            [aishell.docker :as docker]
            [aishell.docker.build :as build]
            [aishell.docker.hash :as hash]
            [aishell.docker.templates :as templates]
            [aishell.output :as output]
            [aishell.run :as run]
            [aishell.state :as state]
            [aishell.util :as util]))

(def version "2.3.0")

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
   :force         {:coerce :boolean :desc "Force rebuild (bypass Docker cache)"}
   :verbose       {:alias :v :coerce :boolean :desc "Show full Docker build output"}
   :help          {:alias :h :coerce :boolean :desc "Show build help"}})

(defn print-help []
  (println (str output/BOLD "Usage:" output/NC " aishell [OPTIONS] COMMAND [ARGS...]"))
  (println)
  (println "Build and run ephemeral containers for AI harnesses.")
  (println)
  (println (str output/BOLD "Commands:" output/NC))
  (println (str "  " output/CYAN "build" output/NC "      Build the container image"))
  (println (str "  " output/CYAN "update" output/NC "     Rebuild with latest versions"))
  (println (str "  " output/CYAN "claude" output/NC "     Run Claude Code"))
  (println (str "  " output/CYAN "opencode" output/NC "   Run OpenCode"))
  (println (str "  " output/CYAN "codex" output/NC "      Run Codex CLI"))
  (println (str "  " output/CYAN "gemini" output/NC "     Run Gemini CLI"))
  (println (str "  " output/CYAN "gitleaks" output/NC "   Run Gitleaks secret scanner"))
  (println (str "  " output/CYAN "(none)" output/NC "     Enter interactive shell"))
  (println)
  (println (str output/BOLD "Global Options:" output/NC))
  (println (cli/format-opts {:spec global-spec
                             :order [:help :version :json]}))
  (println)
  (println (str output/BOLD "Examples:" output/NC))
  (println (str "  " output/CYAN "aishell build --with-claude" output/NC "     Build with Claude Code"))
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
                             :order [:with-claude :with-opencode :with-codex :with-gemini :force :verbose :help]}))
  (println)
  (println (str output/BOLD "Examples:" output/NC))
  (println (str "  " output/CYAN "aishell build" output/NC "                      Build base image"))
  (println (str "  " output/CYAN "aishell build --with-claude" output/NC "        Include Claude Code (latest)"))
  (println (str "  " output/CYAN "aishell build --with-claude=2.0.22" output/NC " Pin Claude Code version"))
  (println (str "  " output/CYAN "aishell build --with-claude --with-opencode" output/NC " Include both"))
  (println (str "  " output/CYAN "aishell build --with-codex --with-gemini" output/NC " Include Codex and Gemini"))
  (println (str "  " output/CYAN "aishell build --force" output/NC "               Force rebuild")))

(defn handle-build [{:keys [opts]}]
  (if (:help opts)
    (print-build-help)
    (let [;; Parse flags
          claude-config (parse-with-flag (:with-claude opts))
          opencode-config (parse-with-flag (:with-opencode opts))
          codex-config (parse-with-flag (:with-codex opts))
          gemini-config (parse-with-flag (:with-gemini opts))

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
        clean-args (vec (remove #{"--unsafe"} args))]
    ;; Handle pass-through commands before standard dispatch
    ;; This ensures all args (including --help, --version) go to the harness
    (case (first clean-args)
      "claude" (run/run-container "claude" (vec (rest clean-args)) {:unsafe unsafe?})
      "opencode" (run/run-container "opencode" (vec (rest clean-args)) {:unsafe unsafe?})
      "codex" (run/run-container "codex" (vec (rest clean-args)) {:unsafe unsafe?})
      "gemini" (run/run-container "gemini" (vec (rest clean-args)) {:unsafe unsafe?})
      "gitleaks" (run/run-container "gitleaks" (vec (rest clean-args)) {:unsafe unsafe? :skip-pre-start true})
      ;; Standard dispatch for other commands (build, update, help)
      (if unsafe?
        ;; --unsafe with no harness command -> shell mode with unsafe
        (run/run-container nil [] {:unsafe true})
        ;; Normal dispatch
        (cli/dispatch dispatch-table args {:error-fn handle-error
                                           :restrict true})))))
