(ns aishell.info
  "Display information about the aishell image stack.

   Extracts info from static sources (templates, state) rather than
   running Docker commands, so `aishell info` works even when Docker
   isn't running."
  (:require [clojure.string :as str]
            [babashka.fs :as fs]
            [aishell.docker.templates :as templates]
            [aishell.docker.base :as base]
            [aishell.docker.extension :as ext]
            [aishell.docker.naming :as naming]
            [aishell.docker.run :as run]
            [aishell.output :as output]
            [aishell.state :as state]
            [aishell.util :as util]))

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

(defn- parse-bbin-version
  "Extract bbin version from ARG BBIN_VERSION=X.Y.Z."
  [dockerfile]
  (second (re-find #"ARG BBIN_VERSION=(\S+)" dockerfile)))

(defn- parse-cue-version
  "Extract CUE version from ARG CUE_VERSION=X.Y.Z."
  [dockerfile]
  (second (re-find #"ARG CUE_VERSION=(\S+)" dockerfile)))

(defn- parse-uv-version
  "Extract uv version from ARG UV_VERSION=X.Y.Z."
  [dockerfile]
  (second (re-find #"ARG UV_VERSION=(\S+)" dockerfile)))

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

(def ^:private harness-labels
  "Map harness state keys to display names."
  {:with-claude   "Claude Code"
   :with-opencode "OpenCode"
   :with-codex    "Codex CLI"
   :with-gemini   "Gemini CLI"
   :with-openspec "OpenSpec"
   :with-pi       "Pi"})

(defn- format-config-paths
  "Format host config paths for enabled harnesses."
  [state]
  (let [home (util/get-home)
        enabled (->> run/harness-config-dirs
                     (filter (fn [[state-key _]] (get state state-key)))
                     (sort-by first))]
    (if (empty? enabled)
      ["  None"]
      (mapcat (fn [[state-key paths]]
                (let [label (get harness-labels state-key (name state-key))]
                  (into [(str "  " label ":")]
                        (map (fn [components]
                               (str "    " (str (apply fs/path home components))))
                             paths))))
              enabled))))

(defn- project-image-tag
  "Compute the resolved image tag for a project without calling Docker.

   Returns the extended tag (aishell:ext-XXXXXXXXXXXX) when a project
   extension Dockerfile exists, otherwise the base tag (aishell:base)."
  [project-dir]
  (if (ext/project-dockerfile project-dir)
    (ext/compute-extended-tag project-dir)
    base/base-image-tag))

(defn- format-project-section
  "Format the Project section body as a vector of indented label/value lines.

   Labels are padded so values align in a single column (longest label is
   `Container prefix:`)."
  [project-dir]
  (let [directory (str (fs/canonicalize project-dir))
        hash (naming/project-hash project-dir)
        prefix (str "aishell-" hash "-")]
    [(str "  Directory:        " directory)
     (str "  Hash:             " hash)
     (str "  Container prefix: " prefix)]))

(defn run-info
  "Print structured summary of the aishell image stack."
  [args]
  (when (some #{"-h" "--help"} args)
    (println (str output/BOLD "Usage:" output/NC " aishell info [--foundation]"))
    (println)
    (println "Display information about the aishell image stack.")
    (println)
    (println "Options:")
    (println "  --foundation  Print the embedded foundation Dockerfile")
    (println)
    (println "Shows foundation contents, base customization status,")
    (println "project extension status, and installed harnesses.")
    (System/exit 0))

  (when (some #{"--foundation"} args)
    (print templates/base-dockerfile)
    (flush)
    (System/exit 0))

  (let [dockerfile templates/base-dockerfile
        packages (parse-packages dockerfile)
        node-version (parse-node-version dockerfile)
        bb-version (parse-babashka-version dockerfile)
        bbin-version (parse-bbin-version dockerfile)
        cue-version (parse-cue-version dockerfile)
        uv-version (parse-uv-version dockerfile)
        gitleaks-version (parse-gitleaks-version dockerfile)
        state (state/read-state)
        project-dir (System/getProperty "user.dir")]

    ;; Project section
    (println (str output/BOLD "Project" output/NC " (" (project-image-tag project-dir) ")"))
    (println "--------------------------------------")
    (doseq [line (format-project-section project-dir)]
      (println line))
    (println)

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
    (when bbin-version
      (println (str "    bbin " bbin-version)))
    (when cue-version
      (println (str "    CUE " cue-version)))
    (when uv-version
      (println (str "    uv " uv-version " (uv + uvx, Python toolchain)")))
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
      (println (str "  Configured (" (util/resolve-project-config-dir project-dir) "/Dockerfile)"))
      (println "  Not configured"))
    (println)

    ;; Harnesses section
    (println (str output/BOLD "Harnesses" output/NC " (volume-mounted)"))
    (println "--------------------------------------")
    (if state
      (do
        (doseq [line (format-harnesses state)]
          (println line))
        (when (:unisoma state)
          (println)
          (println "  UniSoma: enabled (OpenCode model whitelist)")))
      (println "  No setup found"))

    ;; Host Config Paths section (only when harnesses are enabled)
    (when state
      (let [enabled-harnesses (some (fn [[k _]] (get state k)) run/harness-config-dirs)]
        (when enabled-harnesses
          (println)
          (println (str output/BOLD "Host Config Paths" output/NC " (mounted to container)"))
          (println "--------------------------------------")
          (doseq [line (format-config-paths state)]
            (println line)))))))
