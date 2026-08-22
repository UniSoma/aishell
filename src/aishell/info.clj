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
            [aishell.harness :as harness]
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

(defn- parse-distro
  "Extract the distro image from the final stage's FROM line.

   Takes the last FROM that carries no `AS` alias: builder stages are named
   and the main stage is not, so a builder on a different distro can never be
   reported as the foundation's own."
  [dockerfile]
  (second (last (re-seq #"(?im)^FROM[ \t]+(\S+)[ \t]*$" dockerfile))))

(defn- parse-node-tag
  "Extract the Node.js image tag and major version from the node-source stage.

   Returns [tag major], e.g. [\"24-trixie-slim\" \"24\"]. Deliberately not
   anchored on a Debian suite name: a distro bump changes the tag and should
   not need a change here."
  [dockerfile]
  (let [[_ tag major] (re-find #"(?i)FROM[ \t]+node:((\d+)-\S+)\s+AS\s+node-source" dockerfile)]
    (when tag [tag major])))

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

(defn- parse-sqlite-version
  "Extract SQLite version from ARG SQLITE_VERSION=X.Y.Z."
  [dockerfile]
  (second (re-find #"ARG SQLITE_VERSION=(\S+)" dockerfile)))

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
  (let [enabled (filter #(get state (:state-key %)) (harness/versioned))]
    (if (empty? enabled)
      ["  None"]
      (mapv (fn [{:keys [label version-key]}]
              (str "  " label ": " (or (get state version-key) "latest")))
            enabled))))

(def ^:private harness-labels
  "Map harness state keys to their canonical display labels."
  (into {} (map (juxt :state-key :label)) harness/registry))

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
                               (str "    " (apply fs/path home components)))
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
        distro (parse-distro dockerfile)
        [node-tag node-version] (parse-node-tag dockerfile)
        bb-version (parse-babashka-version dockerfile)
        bbin-version (parse-bbin-version dockerfile)
        cue-version (parse-cue-version dockerfile)
        uv-version (parse-uv-version dockerfile)
        sqlite-version (parse-sqlite-version dockerfile)
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
    (when distro
      (println (str "  Distro: " distro)))
    (println)
    (println "  System Packages:")
    (doseq [line (wrap-packages packages)]
      (println line))
    (println)
    (println "  Runtimes:")
    (when node-version
      (println (str "    Node.js " node-version " (from node:" node-tag ")")))
    (when bb-version
      (println (str "    Babashka " bb-version)))
    (when bbin-version
      (println (str "    bbin " bbin-version)))
    (when cue-version
      (println (str "    CUE " cue-version)))
    (when uv-version
      (println (str "    uv " uv-version " (uv + uvx, Python toolchain)")))
    (when sqlite-version
      (println (str "    SQLite " sqlite-version
                    " (sqlite3, sqldiff, sqlite3_rsync"
                    " + libsqlite3/headers, built from source)")))
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
