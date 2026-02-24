(ns aishell.migration
  "One-time migration warnings for version upgrades.")

(defn show-v2.9-migration-warning!
  "No-op. The v2.9.0 tmux migration warning was removed in v3.0.0
   when tmux support was dropped entirely. Kept as no-op because
   cli.clj calls this at two dispatch points."
  []
  nil)
