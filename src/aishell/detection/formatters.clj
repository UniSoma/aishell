(ns aishell.detection.formatters
  "Severity-specific terminal output formatting for detection findings."
  (:require [aishell.output :as output]
            [babashka.fs :as fs]
            [clojure.string :as str]))

;; Extended ANSI code for low-severity (dim/muted)
(def ^:private DIM (if (some? (System/console)) "\u001b[2m" ""))

(def severity-config
  "Configuration for each severity level: label, color, bold emphasis."
  {:high   {:label "HIGH"   :color output/RED    :bold? true}
   :medium {:label "MEDIUM" :color output/YELLOW :bold? false}
   :low    {:label "LOW"    :color DIM           :bold? false}})

(defn format-severity-label
  "Format a severity keyword into a colored label string."
  [severity]
  (let [{:keys [label color bold?]} (get severity-config severity
                                         {:label "UNKNOWN" :color "" :bold? false})]
    (str (when bold? output/BOLD)
         color
         label
         output/NC)))

;; Multimethod for extensible finding formatting
;; Dispatch on :type allows later phases to add custom formatters
(defmulti format-finding
  "Format a finding for display. Dispatches on :type."
  :type)

;; Default formatter for any finding type
(defmethod format-finding :default
  [{:keys [severity path reason summary? sample-paths]}]
  (if summary?
    ;; Summary format: "  MEDIUM 15 files detected (e.g., .env, .env.local)"
    (str "  "
         (format-severity-label severity)
         " "
         reason
         (when (seq sample-paths)
           (let [names (map (fn [p] (str (fs/file-name p))) sample-paths)]
             (str " (e.g., " (str/join ", " names) ")"))))
    ;; Individual format: "  MEDIUM path - reason"
    (str "  "
         (format-severity-label severity)
         " "
         path
         (when reason (str " - " reason)))))

;; Convenience alias for the multimethod
(def format-finding-line format-finding)
