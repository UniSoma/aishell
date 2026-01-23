(ns aishell.gitleaks.warnings
  "Freshness warning display for gitleaks scans.
   Shown before container launch when scan is stale (default: 7 days)."
  (:require [aishell.output :as output]
            [aishell.gitleaks.scan-state :as scan-state]))

(def default-threshold-days 7)

(defn display-freshness-warning
  "Display warning if gitleaks scan is stale (older than threshold).
   Called before container launch. Advisory only - does not block.

   Arguments:
   - project-dir: Project directory path
   - config: Loaded config map (to check gitleaks_freshness_check toggle)
   - threshold-days: Optional threshold override (default: 7)"
  [project-dir config & [threshold-days]]
  ;; Check if freshness check is disabled in config
  (when (not= false (:gitleaks_freshness_check config))
    (let [threshold (or threshold-days default-threshold-days)]
      (when (scan-state/stale? project-dir threshold)
        (let [days (scan-state/days-since-scan project-dir)
              never-scanned? (nil? days)]
          (println)
          (binding [*out* *err*]
            (println (str output/YELLOW "Warning:" output/NC " Gitleaks scan is "
                         (if never-scanned?
                           "missing"
                           (str "stale (" days " days old)"))))
            (println (str "Run: " output/CYAN "aishell gitleaks" output/NC " to scan for secrets"))
            (println)))))))
