(ns aishell.migration
  "One-time migration warnings for version upgrades."
  (:require [babashka.fs :as fs]
            [aishell.state :as state]
            [aishell.util :as util]
            [aishell.output :as output]))

(defn- migration-marker-file
  "Path to migration warning marker file."
  []
  (str (fs/path (util/config-dir) ".migration-v2.9-warned")))

(defn- needs-migration-warning?
  "Returns true if user needs to see v2.9.0 migration warning.
   Criteria:
   - State file exists (not a fresh install)
   - State lacks :harness-volume-hash key (pre-v2.9.0 state schema)
   - Marker file does not exist (warning not yet shown)"
  []
  (and
    ;; Not a fresh install
    (some? (state/read-state))
    ;; Pre-v2.9.0 state (lacks new schema field)
    (nil? (:harness-volume-hash (state/read-state)))
    ;; Warning not yet shown
    (not (fs/exists? (migration-marker-file)))))

(defn show-v2.9-migration-warning!
  "Display one-time migration warning for v2.9.0 breaking changes.
   Creates marker file to prevent repeat warnings."
  []
  (when (needs-migration-warning?)
    (println)
    (output/warn "aishell v2.9.0: Breaking changes to tmux behavior")
    (binding [*out* *err*]
      (println)
      (println "tmux is now OPT-IN (was always-enabled in v2.7-2.8):")
      (println "  - Your existing containers: tmux remains enabled (no action needed)")
      (println "  - New builds after v2.9.0: tmux disabled by default")
      (println "  - To enable tmux: aishell build --with-tmux")
      (println)
      (println "Session name changed:")
      (println "  - Old default: main")
      (println "  - New default: harness")
      (println)
      (println "This affects:")
      (println "  - 'aishell attach' command (requires tmux)")
      (println "  - Session persistence (tmux-resurrect)")
      (println "  - Multiple windows/panes in container")
      (println)
      (println "For details: docs/ARCHITECTURE.md")
      (println "This message shows once.")
      (println))
    ;; Create marker file
    (util/ensure-dir (util/config-dir))
    (spit (migration-marker-file) (str "Warned: " (java.time.Instant/now)))))
