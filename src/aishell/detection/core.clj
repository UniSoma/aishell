(ns aishell.detection.core
  "Core detection framework for scanning and warning about sensitive files.
   Provides the infrastructure for file detection; specific patterns are added
   in later phases (20-22)."
  (:require [babashka.fs :as fs]
            [clojure.string :as str]
            [aishell.output :as output]
            [aishell.detection.formatters :as formatters]
            [aishell.detection.patterns :as patterns]))

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

(defn scan-project
  "Scan project directory for sensitive files.
   Returns vector of findings: [{:path :type :severity :reason}]"
  [project-dir]
  (let [all-findings (concat
                       (patterns/detect-env-files project-dir excluded-dirs)
                       (patterns/detect-ssh-keys project-dir excluded-dirs)
                       (patterns/detect-key-containers project-dir excluded-dirs)
                       (patterns/detect-pem-key-files project-dir excluded-dirs))]
    (patterns/group-findings all-findings)))

(defn group-by-severity
  "Group findings by severity and sort (high first, then medium, then low)."
  [findings]
  (->> findings
       (group-by :severity)
       (sort-by #(get severity-order (key %) 99))))

(defn display-warnings
  "Display warnings block to stderr, grouped by severity.
   Follows the existing validation.clj warning pattern."
  [findings]
  (when (seq findings)
    (let [sorted-groups (group-by-severity findings)]
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
