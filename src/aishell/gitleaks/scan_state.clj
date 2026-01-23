(ns aishell.gitleaks.scan-state
  "Per-project gitleaks scan timestamp persistence.
   Stores timestamps in XDG state directory, keyed by absolute project path."
  (:require [babashka.fs :as fs]
            [clojure.edn :as edn]
            [aishell.util :as util]))

;; State file location: ~/.local/state/aishell/gitleaks-scans.edn
;; Format: {"/path/to/project" {:last-scan "2026-01-23T10:00:00Z"} ...}

(defn scan-state-file []
  (str (fs/path (util/state-dir) "gitleaks-scans.edn")))

(defn read-scan-state []
  (let [path (scan-state-file)]
    (if (fs/exists? path)
      (try
        (edn/read-string (slurp path))
        (catch Exception _ {}))
      {})))

(defn write-scan-timestamp [project-dir]
  (let [abs-path (str (fs/absolutize project-dir))
        current-state (read-scan-state)
        new-state (assoc current-state abs-path {:last-scan (str (java.time.Instant/now))})]
    (util/ensure-dir (util/state-dir))
    (spit (scan-state-file) (pr-str new-state))))

(defn get-last-scan [project-dir]
  (let [abs-path (str (fs/absolutize project-dir))
        state (read-scan-state)
        timestamp-str (get-in state [abs-path :last-scan])]
    (when timestamp-str
      (java.time.Instant/parse timestamp-str))))

(defn days-since-scan [project-dir]
  (when-let [last-scan (get-last-scan project-dir)]
    (let [now (java.time.Instant/now)
          duration (java.time.Duration/between last-scan now)]
      (.toDays duration))))

(defn stale? [project-dir threshold-days]
  (let [days (days-since-scan project-dir)]
    (cond
      (nil? days) true  ; Never scanned = stale
      (>= days threshold-days) true
      :else false)))
