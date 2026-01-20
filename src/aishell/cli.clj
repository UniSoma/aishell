(ns aishell.cli
  (:require [babashka.cli :as cli]
            [aishell.core :as core]
            [aishell.output :as output]))

(def global-spec
  {:help    {:alias :h :coerce :boolean :desc "Show help"}
   :version {:alias :v :coerce :boolean :desc "Show version"}
   :json    {:coerce :boolean :desc "Output in JSON format"}})

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

(defn handle-default [{:keys [opts args]}]
  (cond
    (:version opts)
    (if (:json opts)
      (core/print-version-json)
      (core/print-version))

    (:help opts)
    (print-help)

    (seq args)
    (output/error-unknown-command (first args))

    :else
    (output/error "No image built. Run: aishell build --with-claude")))

(def dispatch-table
  [{:cmds [] :spec global-spec :fn handle-default}])

(defn dispatch [args]
  (cli/dispatch dispatch-table args))
