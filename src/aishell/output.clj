(ns aishell.output
  (:require [babashka.fs :as fs]
            [cheshire.core :as json]
            [clojure.string :as str]))

(def ^:dynamic *verbose* false)
(def ^:dynamic *json-output* false)

(defn error-json-payload
  "Build the error envelope used by `--json` mode.
   Returns {:error {:message msg :code code}}."
  [msg code]
  {:error {:message msg :code code}})

(defn emit-json
  "Print `data` as compact JSON to *out* with a trailing newline."
  [data]
  (println (json/generate-string data)))

(defn exit!
  "Exit the process. Wrapped so tests can stub it via with-redefs."
  [code]
  (System/exit code))

(defn emit-error-json
  "Write a JSON error envelope to *err* and exit 1.
   Envelope shape: {\"error\":{\"message\":msg,\"code\":code}}."
  [msg code]
  (binding [*out* *err*]
    (println (json/generate-string (error-json-payload msg code))))
  (exit! 1))

(defn colors-enabled?
  "Detect if ANSI colors should be used in output.
   Priority: NO_COLOR (opt-out) > FORCE_COLOR (opt-in) > auto-detection.
   Auto-detection checks System/console, COLORTERM, WT_SESSION (Windows Terminal),
   ConEmuANSI (ConEmu), and TERM (Unix)."
  []
  (let [no-color (System/getenv "NO_COLOR")
        force-color (System/getenv "FORCE_COLOR")]
    (cond
      ;; User opt-out (highest priority, non-empty string per spec)
      (and no-color (not= "" no-color))
      false

      ;; User opt-in (overrides auto-detection)
      (and force-color (not= "" force-color))
      true

      ;; Auto-detection fallback
      :else
      (and
        ;; Must be interactive terminal (null when piped/redirected)
       (some? (System/console))
        ;; Terminal must support colors
       (or
          ;; Explicit color capability (modern terminals)
        (some? (System/getenv "COLORTERM"))
          ;; Windows Terminal (ANSI-capable)
        (and (fs/windows?)
             (some? (System/getenv "WT_SESSION")))
          ;; ConEmu with ANSI enabled
        (and (fs/windows?)
             (= "ON" (System/getenv "ConEmuANSI")))
          ;; Unix with standard TERM variable (not dumb)
        (and (not (fs/windows?))
             (let [term (System/getenv "TERM")]
               (and term (not= "dumb" term)))))))))

(defn utf8-output?
  "Detect if the console can display UTF-8 characters.
   On Windows, checks for Windows Terminal (WT_SESSION) or chcp 65001.
   On Unix, assumes UTF-8 (standard on modern systems)."
  []
  (if (fs/windows?)
    (or
      ;; Windows Terminal always supports UTF-8
     (some? (System/getenv "WT_SESSION"))
      ;; Check if console codepage is UTF-8 (65001)
     (let [encoding (str (.getEncoding (java.io.OutputStreamWriter. System/out)))]
       (str/includes? (str/lower-case encoding) "utf")))
    ;; Unix/macOS: UTF-8 is standard
    true))

(def ^:dynamic RED (if (colors-enabled?) "\u001b[0;31m" ""))
(def ^:dynamic YELLOW (if (colors-enabled?) "\u001b[0;33m" ""))
(def ^:dynamic CYAN (if (colors-enabled?) "\u001b[0;36m" ""))
(def ^:dynamic BOLD (if (colors-enabled?) "\u001b[1m" ""))
(def ^:dynamic NC (if (colors-enabled?) "\u001b[0m" ""))

;; Known commands for suggestion matching
(def known-commands #{"setup" "update" "check" "exec" "attach" "ps" "volumes"
                      "claude" "opencode" "codex" "gemini" "gitleaks" "vscode"
                      "upgrade" "info"})

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
  "Print error message to stderr and exit with code 1.
   In JSON mode, emits a JSON error envelope with code `internal_error`."
  [msg]
  (if *json-output*
    (emit-error-json msg "internal_error")
    (do
      (binding [*out* *err*]
        (println (str RED "Error:" NC " " msg)))
      (exit! 1))))

(defn warn
  "Print warning message to stderr. Silent in JSON mode."
  [msg]
  (when-not *json-output*
    (binding [*out* *err*]
      (println (str YELLOW "Warning:" NC " " msg)))))

(defn verbose
  "Print message to stderr if verbose mode enabled. Silent in JSON mode."
  [msg]
  (when (and *verbose* (not *json-output*))
    (binding [*out* *err*]
      (println msg))))

(defn error-unknown-command
  "Print error for unknown command with suggestion.
   In JSON mode, emits a JSON error envelope with code `unknown_command`."
  [cmd]
  (if *json-output*
    (emit-error-json (str "Unknown command: " cmd) "unknown_command")
    (binding [*out* *err*]
      (println (str RED "Error:" NC " Unknown command: " cmd))
      (when-let [suggestion (suggest-command cmd)]
        (println (str "Did you mean: " CYAN suggestion NC "?")))
      (println (str "Try: " CYAN "aishell --help" NC))))
  (when-not *json-output*
    (exit! 1)))

(defn error-no-setup
  "Print error when no setup has been run for the project.
   In JSON mode, emits a JSON error envelope with code `no_setup`."
  []
  (if *json-output*
    (emit-error-json "No setup found. Run: aishell setup" "no_setup")
    (do
      (binding [*out* *err*]
        (println (str RED "Error:" NC " No setup found. Run: " CYAN "aishell setup" NC)))
      (exit! 1))))
