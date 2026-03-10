(ns aishell.update-check
  "Time-based update check. Warns users when a newer version is available.
   State persisted in XDG state dir, configurable via ~/.aishell/config.yaml."
  (:require [babashka.fs :as fs]
            [clojure.edn :as edn]
            [clj-yaml.core :as yaml]
            [aishell.util :as util]
            [aishell.upgrade :as upgrade]
            [aishell.output :as output]))

;; Default settings (overridden by config.yaml update_check section)
(def default-interval-days 1)

;; Max time (ms) to wait for the GitHub version check before giving up
(def check-timeout-ms 5000)

(defn state-file []
  (str (fs/path (util/state-dir) "update-check.edn")))

(defn read-check-state []
  (let [path (state-file)]
    (if (fs/exists? path)
      (try
        (edn/read-string (slurp path))
        (catch Exception _ {}))
      {})))

(defn write-check-state
  "Write check timestamp and optional latest version to state file."
  [latest-version]
  (let [state (cond-> {:last-check (str (java.time.Instant/now))}
                latest-version (assoc :latest-version latest-version))]
    (util/ensure-dir (util/state-dir))
    (spit (state-file) (pr-str state))))

(defn hours-since-check []
  (let [state (read-check-state)
        timestamp-str (:last-check state)]
    (when timestamp-str
      (try
        (let [last-check (java.time.Instant/parse timestamp-str)
              now (java.time.Instant/now)
              duration (java.time.Duration/between last-check now)]
          (/ (.toMinutes duration) 60.0))
        (catch Exception _ nil)))))

(defn check-due?
  "Returns true if enough time has elapsed since the last check."
  [interval-days]
  (let [hours (hours-since-check)]
    (or (nil? hours)
        (>= hours (* interval-days 24)))))

(defn- load-global-update-check-config
  "Safely load only the update_check section from global config.
   Handles update_check: false as a shorthand for disabling.
   Returns {:enabled bool, :interval_days int}. Never throws."
  []
  (try
    (let [global-path (str (fs/path (util/get-home) ".aishell" "config.yaml"))]
      (if (fs/exists? global-path)
        (let [parsed (yaml/parse-string (slurp global-path))
              uc (:update_check parsed)]
          (cond
            ;; update_check: false — shorthand to disable
            (false? uc)
            {:enabled false :interval_days default-interval-days}

            ;; update_check: { enabled: ..., interval_days: ... }
            (map? uc)
            {:enabled (get uc :enabled true)
             :interval_days (get uc :interval_days default-interval-days)}

            ;; Missing or any other value — defaults
            :else
            {:enabled true :interval_days default-interval-days}))
        {:enabled true :interval_days default-interval-days}))
    (catch Exception _
      {:enabled true :interval_days default-interval-days})))

(defn- fetch-latest-version-with-timeout
  "Fetch latest version from GitHub, bounded by check-timeout-ms.
   Returns version string, or nil if fetch fails, times out, or no downloader."
  []
  (when-let [downloader (upgrade/find-downloader)]
    (let [f (future (upgrade/fetch-latest-version downloader))
          result (deref f check-timeout-ms ::timeout)]
      (when-not (= result ::timeout)
        result))))

(defn maybe-check-for-update
  "Check for updates if interval has elapsed. Warning printed to stderr.
   When a check is due, may block for up to check-timeout-ms (5s) for the
   network call; skips instantly on all other invocations.
   Loads its own config safely — never depends on config/load-yaml-config.
   Returns nil. Never throws."
  [current-version]
  (try
    (let [{:keys [enabled interval_days]} (load-global-update-check-config)]
      (when (and enabled (check-due? interval_days))
        (let [latest (fetch-latest-version-with-timeout)]
          ;; Always advance the timestamp, even on failure, so offline users
          ;; don't pay the network penalty on every invocation.
          (write-check-state latest)
          ;; Only warn if we got a version and it's newer
          (when (and latest (pos? (upgrade/version-compare latest current-version)))
            (binding [*out* *err*]
              (println (str output/YELLOW "A new version of aishell is available: v" latest
                            " (current: v" current-version ")"
                            "\nRun " output/CYAN "aishell upgrade" output/YELLOW
                            " to update." output/NC)))))))
    (catch Exception _
      ;; Silently ignore all errors — never break user workflow
      nil)))
