(ns aishell.info
  "Display information about the aishell image stack.

   Extracts info from static sources (templates, state) rather than
   running Docker commands, so `aishell info` works even when Docker
   isn't running."
  (:require [clojure.string :as str]
            [aishell.docker.templates :as templates]
            [aishell.docker.base :as base]
            [aishell.docker.extension :as ext]
            [aishell.output :as output]
            [aishell.state :as state]))

(defn- parse-packages
  "Extract apt-get install packages from the Dockerfile template."
  [dockerfile]
  (when-let [block (second (re-find #"apt-get install.*?\\([\s\S]*?)&&\s*rm" dockerfile))]
    (->> (str/split block #"\\\n|\n")
         (map str/trim)
         (remove str/blank?)
         (map #(str/replace % #"\\\\$" ""))
         (map str/trim)
         (remove str/blank?)
         sort
         vec)))

(defn- parse-node-version
  "Extract Node.js major version from FROM node:XX-bookworm-slim."
  [dockerfile]
  (second (re-find #"FROM node:(\d+)-bookworm-slim" dockerfile)))

(defn- parse-babashka-version
  "Extract Babashka version from ARG BABASHKA_VERSION=X.Y.Z."
  [dockerfile]
  (second (re-find #"ARG BABASHKA_VERSION=(\S+)" dockerfile)))

(defn- parse-gitleaks-version
  "Extract Gitleaks version from ARG GITLEAKS_VERSION=X.Y.Z."
  [dockerfile]
  (second (re-find #"ARG GITLEAKS_VERSION=(\S+)" dockerfile)))

(defn- wrap-packages
  "Format package list into comma-separated lines wrapped at ~70 chars."
  [packages]
  (loop [pkgs packages
         line "    "
         lines []]
    (if (empty? pkgs)
      (if (= line "    ")
        lines
        (conj lines line))
      (let [pkg (first pkgs)
            entry (if (= line "    ") pkg (str ", " pkg))
            new-line (str line entry)]
        (if (> (count new-line) 70)
          (recur (rest pkgs)
                 (str "    " pkg)
                 (conj lines (str line ",")))
          (recur (rest pkgs) new-line lines))))))

(defn- format-harnesses
  "Format enabled harnesses from state map."
  [state]
  (let [harness-defs [[:with-claude "Claude Code" :claude-version]
                      [:with-opencode "OpenCode" :opencode-version]
                      [:with-codex "Codex CLI" :codex-version]
                      [:with-gemini "Gemini CLI" :gemini-version]
                      [:with-pi "Pi" :pi-version]
                      [:with-openspec "OpenSpec" :openspec-version]]
        enabled (filter (fn [[flag _ _]] (get state flag)) harness-defs)]
    (if (empty? enabled)
      ["  None"]
      (mapv (fn [[_ name version-key]]
              (let [version (get state version-key)]
                (str "  " name ": " (or version "latest"))))
            enabled))))

(defn run-info
  "Print structured summary of the aishell image stack."
  [args]
  (when (some #{"-h" "--help"} args)
    (println (str output/BOLD "Usage:" output/NC " aishell info"))
    (println)
    (println "Display information about the aishell image stack.")
    (println)
    (println "Shows foundation contents, base customization status,")
    (println "project extension status, and installed harnesses.")
    (System/exit 0))

  (let [dockerfile templates/base-dockerfile
        packages (parse-packages dockerfile)
        node-version (parse-node-version dockerfile)
        bb-version (parse-babashka-version dockerfile)
        gitleaks-version (parse-gitleaks-version dockerfile)
        state (state/read-state)
        project-dir (System/getProperty "user.dir")]

    ;; Foundation section
    (println (str output/BOLD "Foundation Image" output/NC " (aishell:foundation)"))
    (println "--------------------------------------")
    (println "  Base: debian:bookworm-slim")
    (println)
    (println "  System Packages:")
    (doseq [line (wrap-packages packages)]
      (println line))
    (println)
    (println "  Runtimes:")
    (when node-version
      (println (str "    Node.js " node-version " (from node:" node-version "-bookworm-slim)")))
    (when bb-version
      (println (str "    Babashka " bb-version)))
    (println "    gosu 1.19")
    (println)
    (println (str "  Gitleaks: "
                  (if (and state (:with-gitleaks state))
                    (str "installed (" gitleaks-version ")")
                    "not installed")))
    (println)

    ;; Base Image section
    (println (str output/BOLD "Base Image" output/NC " (aishell:base)"))
    (println "--------------------------------------")
    (if (base/global-dockerfile-exists?)
      (println "  Custom (~/.aishell/Dockerfile)")
      (println "  Default (foundation alias)"))
    (println)

    ;; Project Extension section
    (println (str output/BOLD "Project Extension" output/NC))
    (println "--------------------------------------")
    (if (ext/project-dockerfile project-dir)
      (println "  Configured (.aishell/Dockerfile)")
      (println "  Not configured"))
    (println)

    ;; Harnesses section
    (println (str output/BOLD "Harnesses" output/NC " (volume-mounted)"))
    (println "--------------------------------------")
    (if state
      (doseq [line (format-harnesses state)]
        (println line))
      (println "  No setup found"))))
