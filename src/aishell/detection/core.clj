(ns aishell.detection.core
  "Core detection framework for scanning and warning about sensitive files.
   Provides the infrastructure for file detection; specific patterns are added
   in later phases (20-22)."
  (:require [babashka.fs :as fs]
            [clojure.string :as str]
            [aishell.output :as output]
            [aishell.detection.formatters :as formatters]
            [aishell.detection.patterns :as patterns]
            [aishell.detection.gitignore :as gitignore]
            [aishell.config :as config]))

;; Directories to skip during scanning (performance optimization)
(def excluded-dirs
  "Directories excluded from scanning for performance.
   These typically contain build artifacts, dependencies, or version control."
  #{".git" "node_modules" "vendor" "target" "build" "dist"
    "__pycache__" ".venv" "venv" ".bundle"})

(def severity-order
  "Sort order for severities (lower number = higher priority)."
  {:high 0 :medium 1 :low 2})

(defn- in-excluded-dir?
  "Check if a path is within an excluded directory."
  [path]
  (let [path-str (str path)]
    (some #(str/includes? path-str (str "/" % "/")) excluded-dirs)))

(defn- file-allowlisted?
  "Check if file path matches any allowlist entry.
   Supports exact paths and glob patterns.
   Allowlist entries must be maps with :path key (and :reason for documentation).
   Returns false for nil paths (summary findings should never be allowlisted)."
  [file-path allowlist project-dir]
  (when (and file-path (seq allowlist))  ;; Guard against nil file-path
    (let [rel-path (str (fs/relativize project-dir (fs/absolutize file-path)))]
      (some (fn [entry]
              (let [pattern (:path entry)]
                (when pattern  ;; Guard against entries without :path
                  (or (= pattern rel-path)
                      (= pattern (fs/file-name file-path))  ;; Allow filename-only matching
                      (seq (fs/match rel-path (str "glob:" pattern)))))))  ;; fs/match returns collection, use seq
            allowlist))))

(defn filter-allowlisted
  "Remove findings that match allowlist entries.
   Allowlisted files are completely hidden (no 'allowed' status)."
  [findings allowlist project-dir]
  (if (seq allowlist)
    (remove #(file-allowlisted? (:path %) allowlist project-dir) findings)
    findings))

(defn scan-project
  "Scan project directory for sensitive files.
   Returns vector of findings, filtered by allowlist."
  [project-dir & [detection-config]]
  ;; Check if detection is disabled via config
  (let [enabled? (get detection-config :enabled true)]
    (if-not enabled?
      []  ;; Detection disabled, return empty findings
      (let [custom-patterns (get detection-config :custom_patterns {})
            all-findings (concat
                           ;; Phase 20 detectors
                           (patterns/detect-env-files project-dir excluded-dirs)
                           (patterns/detect-ssh-keys project-dir excluded-dirs)
                           (patterns/detect-key-containers project-dir excluded-dirs)
                           (patterns/detect-pem-key-files project-dir excluded-dirs)
                           ;; Phase 21-01 cloud credential detectors
                           (patterns/detect-gcp-credentials project-dir excluded-dirs)
                           (patterns/detect-terraform-state project-dir excluded-dirs)
                           (patterns/detect-kubeconfig project-dir excluded-dirs)
                           ;; Phase 21-02 package manager, app secrets, database detectors
                           (patterns/detect-package-manager-credentials project-dir excluded-dirs)
                           (patterns/detect-tool-configs project-dir excluded-dirs)
                           (patterns/detect-rails-secrets project-dir excluded-dirs)
                           (patterns/detect-secret-pattern-files project-dir excluded-dirs)
                           (patterns/detect-database-credentials project-dir excluded-dirs)
                           ;; Phase 23-02 custom patterns
                           (patterns/detect-custom-patterns project-dir excluded-dirs custom-patterns))]
        (patterns/group-findings all-findings)))))

(defn group-by-severity
  "Group findings by severity and sort (high first, then medium, then low)."
  [findings]
  (->> findings
       (group-by :severity)
       (sort-by #(get severity-order (key %) 99))))

(defn- annotate-with-gitignore-status
  "Annotate high-severity findings with gitignore status.

   For high-severity findings:
   - If NOT in .gitignore (gitignored? returns false): append ' (risk: may be committed)' to reason
   - If in .gitignore or unknown (true/nil): leave reason unchanged

   For medium/low severity: return unchanged (no gitignore annotation)."
  [project-dir findings]
  (mapv (fn [finding]
          (if (= :high (:severity finding))
            (let [ignored? (gitignore/gitignored? project-dir (:path finding))]
              (if (false? ignored?)  ; Explicitly not ignored (at risk)
                (update finding :reason str " (risk: may be committed)")
                finding))
            finding))
        findings))

(defn display-warnings
  "Display warnings block to stderr, grouped by severity.
   Follows the existing validation.clj warning pattern.

   Arguments:
   - project-dir: Project directory for gitignore checking
   - findings: Vector of findings to display"
  [project-dir findings]
  (when (seq findings)
    (let [annotated-findings (annotate-with-gitignore-status project-dir findings)
          sorted-groups (group-by-severity annotated-findings)]
      (println)  ; Blank line before warning block
      (binding [*out* *err*]
        (println (str output/BOLD "Sensitive files detected in project directory" output/NC))
        (println (apply str (repeat 50 "-")))
        (doseq [[_severity items] sorted-groups]
          (doseq [finding items]
            (println (formatters/format-finding-line finding))))
        (println)
        (println "AI tools will have access to these files inside the container.")
        (println)))))

(defn- interactive?
  "Check if running in an interactive terminal (not CI/piped)."
  []
  (some? (System/console)))

(defn- prompt-yn
  "Prompt user for y/n confirmation. Returns true if user enters 'y'."
  [message]
  (print (str message " (y/n): "))
  (flush)
  (let [response (str/lower-case (or (read-line) ""))]
    (= response "y")))

(defn confirm-if-needed
  "Request confirmation for high-severity findings.
   - High-severity + interactive: prompt y/n
   - High-severity + non-interactive: error requiring --unsafe (exit 1)
   - Medium/low only: auto-proceed
   Returns true to proceed, exits for abort."
  [findings]
  (let [high-count (count (filter #(= :high (:severity %)) findings))]
    (cond
      ;; No high-severity: auto-proceed
      (zero? high-count)
      true

      ;; Non-interactive (CI): require --unsafe
      (not (interactive?))
      (do
        (binding [*out* *err*]
          (println)
          (println (str output/RED "Error:" output/NC
                        " High-severity findings detected in non-interactive mode."))
          (println "Use --unsafe flag to proceed in CI/automation."))
        (System/exit 1))

      ;; Interactive: prompt
      :else
      (if (prompt-yn (str output/YELLOW "Proceed with " high-count
                          " high-severity finding(s)?" output/NC))
        true
        (do
          (println "Aborted.")
          (System/exit 0))))))
