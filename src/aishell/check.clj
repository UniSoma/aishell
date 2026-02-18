(ns aishell.check
  "Pre-flight validation checks without container execution.
   Runs the same checks that happen before harness/shell launch,
   reporting status for each without starting any container."
  (:require [clojure.string :as str]
            [babashka.fs :as fs]
            [aishell.docker :as docker]
            [aishell.docker.hash :as hash]
            [aishell.docker.templates :as templates]
            [aishell.docker.extension :as ext]
            [aishell.config :as config]
            [aishell.state :as state]
            [aishell.validation :as validation]
            [aishell.detection.core :as detection]
            [aishell.detection.formatters :as formatters]
            [aishell.gitleaks.scan-state :as scan-state]
            [aishell.output :as output]
            [aishell.util :as util]))

(def GREEN (if (output/colors-enabled?) "\u001b[0;32m" ""))

(defn- check-mark [] (str GREEN (if (output/utf8-output?) "✓" "+") output/NC))
(defn- cross-mark [] (str output/RED (if (output/utf8-output?) "✗" "x") output/NC))
(defn- warn-mark [] (str output/YELLOW "!" output/NC))

(defn- print-status
  "Print a status line: ✓/✗/! label"
  [status label]
  (let [mark (case status
               :ok   (check-mark)
               :fail (cross-mark)
               :warn (warn-mark))]
    (println (str "  " mark " " label))))

(defn- check-docker
  "Check Docker availability and daemon status."
  []
  (cond
    (not (docker/docker-available?))
    (do (print-status :fail "Docker is not installed")
        :fail)

    (not (docker/docker-running?))
    (do (print-status :fail "Docker daemon is not running")
        :fail)

    :else
    (do (print-status :ok "Docker is available")
        :ok)))

(defn- check-setup-state
  "Check if setup state exists. Returns state map or nil."
  []
  (if-let [s (state/read-state)]
    (do (print-status :ok (str "Setup state found (built: "
                                (or (:build-time s) "unknown") ")"))
        s)
    (do (print-status :fail (str "No setup found. Run: " output/CYAN "aishell setup" output/NC))
        nil)))

(defn- check-base-image
  "Check if base Docker image exists."
  [state]
  (let [tag (or (:image-tag state) "aishell:base")]
    (if (docker/image-exists? tag)
      (do (print-status :ok (str "Base image exists: " tag))
          tag)
      (do (print-status :fail (str "Base image not found: " tag))
          nil))))

(defn- check-dockerfile-staleness
  "Check if embedded Dockerfile changed since build."
  [state]
  (if-let [stored-hash (:dockerfile-hash state)]
    (let [current-hash (hash/compute-hash templates/base-dockerfile)]
      (if (= stored-hash current-hash)
        (print-status :ok "Base Dockerfile is up to date")
        (print-status :warn (str "Image may be stale. Run " output/CYAN "aishell update" output/NC " to rebuild"))))
    (print-status :warn "No Dockerfile hash stored (old build format)")))

(defn- check-harnesses
  "Report which harnesses are installed."
  [state]
  (let [harnesses [["Claude Code" :with-claude :claude-version]
                   ["OpenCode" :with-opencode :opencode-version]
                   ["Codex CLI" :with-codex :codex-version]
                   ["Gemini CLI" :with-gemini :gemini-version]
                   ["Pi" :with-pi :pi-version]
                   ["Gitleaks" :with-gitleaks nil]]]
    (doseq [[name key version-key] harnesses]
      (if (get state key)
        (let [ver (when version-key (get state version-key))]
          (print-status :ok (str name " installed" (when ver (str " (" ver ")")))))
        (print-status :warn (str name " not installed"))))))

(defn- check-extension
  "Check project extension Dockerfile status."
  [project-dir base-tag]
  (if-let [_dockerfile (ext/project-dockerfile project-dir)]
    (let [extended-tag (ext/compute-extended-tag project-dir)]
      ;; Validate base tag before checking rebuild status
      (ext/validate-base-tag project-dir)
      (if (ext/needs-extended-rebuild? extended-tag base-tag project-dir)
        (print-status :warn "Project extension needs rebuild")
        (print-status :ok (str "Project extension is up to date: " extended-tag))))
    (print-status :ok "No project extension (.aishell/Dockerfile)")))

(defn- check-config
  "Validate configuration files. Returns loaded config."
  [project-dir]
  (let [project-path (config/project-config-path project-dir)
        global-path (config/global-config-path)
        project-exists? (fs/exists? project-path)
        global-exists? (fs/exists? global-path)]
    (when global-exists?
      (print-status :ok (str "Global config: " global-path)))
    (when-not global-exists?
      (print-status :ok "No global config (optional)"))
    (when project-exists?
      (print-status :ok (str "Project config: " project-path)))
    (when-not project-exists?
      (print-status :ok "No project config (optional)"))
    ;; Load config (this validates YAML and warns on unknown keys)
    (config/load-config project-dir)))

(defn- check-mounts
  "Check if configured mount sources exist."
  [cfg]
  (when-let [mounts (:mounts cfg)]
    (doseq [mount mounts]
      (let [mount-str (str mount)
            ;; Smart colon parsing: detect drive letter to avoid splitting on it
            source (if (re-matches #"^[A-Za-z]:[/\\].*" mount-str)
                     ;; Windows absolute path — extract source (before second colon, or entire string)
                     (if-let [idx (str/index-of mount-str ":" 2)]
                       (subs mount-str 0 idx)
                       mount-str)
                     ;; Unix path — extract before first colon
                     (first (str/split mount-str #":" 2)))
            source (util/expand-path source)]
        (if (fs/exists? source)
          (print-status :ok (str "Mount exists: " source))
          (print-status :warn (str "Mount not found: " source)))))))

(defn- check-security
  "Check for dangerous docker args and mount paths."
  [cfg]
  (let [has-warnings? (atom false)]
    (when-let [docker-args (:docker_args cfg)]
      (when-let [warnings (validation/check-dangerous-args docker-args)]
        (reset! has-warnings? true)
        (doseq [msg warnings]
          (print-status :warn msg))))
    (when-let [mounts (:mounts cfg)]
      (when-let [warnings (validation/check-dangerous-mounts mounts)]
        (reset! has-warnings? true)
        (doseq [msg warnings]
          (print-status :warn msg))))
    (when-not @has-warnings?
      (print-status :ok "No dangerous Docker options or mount paths"))))

(defn- check-sensitive-files
  "Scan for sensitive files in project directory."
  [project-dir cfg]
  (let [detection-config (get cfg :detection {})
        enabled? (get detection-config :enabled true)]
    (if-not enabled?
      (print-status :ok "Sensitive file detection disabled via config")
      (let [allowlist (:allowlist detection-config [])
            findings (detection/scan-project project-dir detection-config)
            filtered (detection/filter-allowlisted findings allowlist project-dir)
            high-count (count (filter #(= :high (:severity %)) filtered))
            medium-count (count (filter #(= :medium (:severity %)) filtered))
            low-count (count (filter #(= :low (:severity %)) filtered))
            total (count filtered)]
        (if (zero? total)
          (print-status :ok "No sensitive files detected")
          (do
            (print-status (if (pos? high-count) :warn :ok)
                          (str total " sensitive file(s) detected"
                               " (" high-count " high, " medium-count " medium, " low-count " low)"))
            ;; Show details
            (let [sorted-groups (detection/group-by-severity filtered)]
              (doseq [[_severity items] sorted-groups]
                (doseq [finding items]
                  (println (str "      " (formatters/format-finding-line finding))))))))))))

(defn- check-gitleaks-freshness
  "Check gitleaks scan freshness."
  [project-dir cfg]
  (when (not= false (:gitleaks_freshness_check cfg))
    (let [days (scan-state/days-since-scan project-dir)
          threshold (or (:gitleaks_freshness_threshold cfg) 7)]
      (cond
        (nil? days)
        (print-status :warn (str "Gitleaks scan never run. Run: " output/CYAN "aishell gitleaks" output/NC))

        (>= days threshold)
        (print-status :warn (str "Gitleaks scan is stale (" days " days old). Run: " output/CYAN "aishell gitleaks" output/NC))

        :else
        (print-status :ok (str "Gitleaks scan is fresh (" days " day(s) old)"))))))

(defn run-check
  "Run all pre-flight checks and report status.
   Returns exit code: 0 if all critical checks pass, 1 if any fail."
  []
  (let [project-dir (System/getProperty "user.dir")
        critical-fail? (atom false)]

    (println)
    (println (str output/BOLD "Pre-flight checks" output/NC))
    (println (apply str (repeat 40 "-")))
    (println)

    ;; Docker
    (println (str output/BOLD "Docker" output/NC))
    (when (= :fail (check-docker))
      (reset! critical-fail? true))
    (println)

    ;; Build state
    (println (str output/BOLD "Setup" output/NC))
    (let [state (check-setup-state)]
      (if state
        (do
          ;; Base image
          (let [base-tag (check-base-image state)]
            (when-not base-tag
              (reset! critical-fail? true))

            ;; Dockerfile staleness
            (check-dockerfile-staleness state)

            ;; Extension
            (when base-tag
              (check-extension project-dir base-tag)))

          (println)

          ;; Harnesses
          (println (str output/BOLD "Harnesses" output/NC))
          (check-harnesses state))
        (reset! critical-fail? true)))
    (println)

    ;; Configuration
    (println (str output/BOLD "Configuration" output/NC))
    (let [cfg (check-config project-dir)]
      (println)

      ;; Mounts
      (println (str output/BOLD "Mounts" output/NC))
      (if (seq (:mounts cfg))
        (check-mounts cfg)
        (print-status :ok "No mounts configured"))
      (println)

      ;; Security
      (println (str output/BOLD "Security" output/NC))
      (check-security cfg)
      (check-sensitive-files project-dir cfg)
      (check-gitleaks-freshness project-dir cfg))

    (println)

    ;; Tools
    (println (str output/BOLD "Tools" output/NC))
    (if (fs/which "code")
      (do
        (print-status :ok "VSCode 'code' CLI available (aishell vscode)")
        (print-status :ok (str "VSCode imageConfigs: " (util/vscode-imageconfigs-dir))))
      (print-status :warn "VSCode 'code' CLI not found (aishell vscode won't work)"))
    (if (fs/which "git")
      (print-status :ok "Git available")
      (print-status :warn "Git not found (git identity won't be forwarded to container)"))

    (println)
    (println (apply str (repeat 40 "-")))
    (if @critical-fail?
      (do
        (println (str (cross-mark) " Some critical checks failed"))
        (System/exit 1))
      (println (str (check-mark) " All critical checks passed")))))
