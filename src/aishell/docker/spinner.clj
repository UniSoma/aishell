(ns aishell.docker.spinner
  "Progress indicator for long-running Docker builds.

   Shows animated spinner in TTY environments, static message in CI.")

(def ^:private spinner-chars [\| \/ \- \\])

(defn should-animate?
  "Check if we should show animated output.
   Returns true only when:
   - Running in a terminal (System/console not nil)
   - Not in CI environment (CI env var not set)"
  []
  (and (some? (System/console))
       (nil? (System/getenv "CI"))))

(defn with-spinner
  "Execute function while showing spinner, returns function result.

   In TTY environments: shows animated spinner that cycles through | / - \\
   In non-TTY/CI: prints static message to stderr

   Arguments:
   - message: The message to display alongside spinner
   - f: Zero-argument function to execute

   Returns: The result of calling f"
  [message f]
  (if-not (should-animate?)
    ;; No animation: just print message and run
    (do
      (binding [*out* *err*]
        (println message))
      (f))
    ;; Animated spinner
    (let [running (atom true)
          spinner-thread (future
                           (loop [i 0]
                             (when @running
                               (binding [*out* *err*]
                                 (print (str "\r" (nth spinner-chars (mod i 4)) " " message " "))
                                 (flush))
                               (Thread/sleep 100)
                               (recur (inc i)))))]
      (try
        (let [result (f)]
          ;; Clear spinner line: print \r + spaces + \r
          (binding [*out* *err*]
            (print "\r")
            (print (apply str (repeat (+ 3 (count message)) " ")))
            (print "\r")
            (flush))
          result)
        (finally
          (reset! running false))))))
