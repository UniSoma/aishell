(ns aishell.cli
  (:require [babashka.cli :as cli]
            [clojure.string :as str]
            [aishell.core :as core]
            [aishell.docker :as docker]
            [aishell.docker.build :as build]
            [aishell.output :as output]
            [aishell.run :as run]
            [aishell.state :as state]
            [aishell.util :as util]))

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
  (println (str "  " output/CYAN "(none)" output/NC "     Enter interactive shell"))
  (println)
  (println (str output/BOLD "Global Options:" output/NC))
  (println (cli/format-opts {:spec global-spec
                             :order [:help :version :json]}))
  (println)
  (println (str output/BOLD "Examples:" output/NC))
  (println (str "  " output/CYAN "aishell build --with-claude" output/NC "     Build with Claude Code"))
  (println (str "  " output/CYAN "aishell claude" output/NC "                  Run Claude Code"))
  (println (str "  " output/CYAN "aishell" output/NC "                         Enter shell")))

(defn print-build-help []
  (println (str output/BOLD "Usage:" output/NC " aishell build [OPTIONS]"))
  (println)
  (println "Build the container image with optional harness installations.")
  (println)
  (println (str output/BOLD "Options:" output/NC))
  (println (cli/format-opts {:spec build-spec
                             :order [:with-claude :with-opencode :verbose :help]}))
  (println)
  (println (str output/BOLD "Examples:" output/NC))
  (println (str "  " output/CYAN "aishell build" output/NC "                      Build base image"))
  (println (str "  " output/CYAN "aishell build --with-claude" output/NC "        Include Claude Code (latest)"))
  (println (str "  " output/CYAN "aishell build --with-claude=2.0.22" output/NC " Pin Claude Code version"))
  (println (str "  " output/CYAN "aishell build --with-claude --with-opencode" output/NC " Include both")))

(defn handle-build [{:keys [opts]}]
  (if (:help opts)
    (print-build-help)
    (let [;; Parse flags
          claude-config (parse-with-flag (:with-claude opts))
          opencode-config (parse-with-flag (:with-opencode opts))

          ;; Validate versions before build
          _ (validate-version (:version claude-config) "Claude Code")
          _ (validate-version (:version opencode-config) "OpenCode")

          ;; Show replacement message if image exists
          _ (when (docker/image-exists? build/base-image-tag)
              (println "Replacing existing image..."))

          ;; Build with explicit force (no aishell caching per CONTEXT.md)
          result (build/build-base-image
                   {:with-claude (:enabled? claude-config)
                    :with-opencode (:enabled? opencode-config)
                    :claude-version (:version claude-config)
                    :opencode-version (:version opencode-config)
                    :verbose (:verbose opts)
                    :force true})]

      ;; Persist state (always, even on failure this won't run due to error exit)
      (state/write-state
        {:with-claude (:enabled? claude-config)
         :with-opencode (:enabled? opencode-config)
         :claude-version (:version claude-config)
         :opencode-version (:version opencode-config)
         :image-tag (:image result)
         :build-time (str (java.time.Instant/now))}))))

(defn handle-run
  "Handle run commands: claude, opencode, or shell (default)."
  [{:keys [opts args]} cmd]
  (if (:help opts)
    (case cmd
      "claude" (do
                 (println (str output/BOLD "Usage:" output/NC " aishell claude [ARGS...]"))
                 (println)
                 (println "Run Claude Code in container.")
                 (println)
                 (println "All arguments are passed to Claude Code.")
                 (println)
                 (println (str output/BOLD "Examples:" output/NC))
                 (println (str "  " output/CYAN "aishell claude" output/NC "                  Start Claude Code"))
                 (println (str "  " output/CYAN "aishell claude --model opus" output/NC "     Use specific model")))
      "opencode" (do
                   (println (str output/BOLD "Usage:" output/NC " aishell opencode [ARGS...]"))
                   (println)
                   (println "Run OpenCode in container.")
                   (println)
                   (println "All arguments are passed to OpenCode.")))
    ;; Run the command
    (run/run-container cmd (vec args))))

(defn handle-default [{:keys [opts args]}]
  (cond
    (:version opts)
    (if (:json opts)
      (core/print-version-json)
      (core/print-version))

    (:help opts)
    (print-help)

    ;; Unknown command - check for typos
    (seq args)
    (output/error-unknown-command (first args))

    ;; No command, no flags - run shell
    :else
    (run/run-container nil [])))

(def dispatch-table
  [{:cmds ["build"] :fn handle-build :spec build-spec :restrict true}
   {:cmds ["claude"] :fn #(handle-run % "claude") :spec {:help {:alias :h :coerce :boolean}}}
   {:cmds ["opencode"] :fn #(handle-run % "opencode") :spec {:help {:alias :h :coerce :boolean}}}
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
  (cli/dispatch dispatch-table args {:error-fn handle-error
                                      :restrict true}))
