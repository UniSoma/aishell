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

;; Known commands for suggestion matching
(def known-commands #{"build" "update" "check" "exec" "attach" "ps" "volumes"
                      "claude" "opencode" "codex" "gemini" "gitleaks"})

(defn- levenshtein-distance
  "Calculate edit distance between two strings."
  [s1 s2]
  (cond
    (empty? s1) (count s2)
    (empty? s2) (count s1)
    :else
    (let [s1 (vec s1)
          s2 (vec s2)
          n (count s1)
          m (count s2)
          ;; Iterative approach using atoms
          d (atom (vec (range (inc m))))]
      (doseq [i (range 1 (inc n))]
        (let [prev @d
              curr (atom [(inc i)])]
          (doseq [j (range 1 (inc m))]
            (let [cost (if (= (s1 (dec i)) (s2 (dec j))) 0 1)]
              (swap! curr conj
                     (min (inc (prev j))
                          (inc (last @curr))
                          (+ (prev (dec j)) cost)))))
          (reset! d @curr)))
      (last @d))))

(defn suggest-command
  "Suggest a similar command based on input."
  [input]
  (let [input (str/lower-case input)
        candidates (->> known-commands
                        (map (fn [cmd] [cmd (levenshtein-distance input cmd)]))
                        (filter (fn [[_ dist]] (<= dist 3)))  ;; Max 3 edits
                        (sort-by second))]
    (when (seq candidates)
      (first (first candidates)))))

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
    (when-let [suggestion (suggest-command cmd)]
      (println (str "Did you mean: " CYAN suggestion NC "?")))
    (println (str "Try: " CYAN "aishell --help" NC)))
  (System/exit 1))

(defn error-no-build
  "Print error when no image has been built for the project"
  []
  (binding [*out* *err*]
    (println (str RED "Error:" NC " No image built. Run: " CYAN "aishell build" NC)))
  (System/exit 1))
