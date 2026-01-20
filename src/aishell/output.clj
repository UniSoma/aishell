(ns aishell.output
  (:require [clojure.string :as str]))

(def ^:dynamic *verbose* false)

(defn- colors-enabled? []
  (and (some? (System/console))
       (nil? (System/getenv "NO_COLOR"))
       (or (some? (System/getenv "FORCE_COLOR"))
           (not= "dumb" (System/getenv "TERM")))))

(def RED (if (colors-enabled?) "\u001b[0;31m" ""))
(def YELLOW (if (colors-enabled?) "\u001b[0;33m" ""))
(def CYAN (if (colors-enabled?) "\u001b[0;36m" ""))
(def BOLD (if (colors-enabled?) "\u001b[1m" ""))
(def NC (if (colors-enabled?) "\u001b[0m" ""))

(defn error
  "Print error message to stderr and exit with code 1"
  [msg]
  (binding [*out* *err*]
    (println (str RED "Error:" NC " " msg)))
  (System/exit 1))

(defn warn
  "Print warning message to stderr"
  [msg]
  (binding [*out* *err*]
    (println (str YELLOW "Warning:" NC " " msg))))

(defn verbose
  "Print message to stderr if verbose mode enabled"
  [msg]
  (when *verbose*
    (binding [*out* *err*]
      (println msg))))

(defn error-unknown-command
  "Print error for unknown command with suggestion"
  [cmd]
  (binding [*out* *err*]
    (println (str RED "Error:" NC " Unknown command: " cmd))
    (println (str "Try: " CYAN "aishell --help" NC)))
  (System/exit 1))
