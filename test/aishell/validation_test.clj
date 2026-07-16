(ns aishell.validation-test
  (:require [clojure.test :refer [deftest is testing]]
            [aishell.output :as output]
            [aishell.validation :as validation]))

(defn- reject
  "Run check-claude-shared-paths, treating output/error as a hard reject.
   Returns the captured stderr message when the run rejects, or nil when it
   passes without calling output/error."
  [paths]
  (let [err (java.io.StringWriter.)]
    (binding [*err* err]
      (with-redefs [output/exit! (fn [_] (throw (ex-info "rejected" {})))]
        (try
          (validation/check-claude-shared-paths paths)
          nil
          (catch clojure.lang.ExceptionInfo _
            (str err)))))))

(deftest check-claude-shared-paths-allows-safe-entries
  (testing "safe relative entries do not reject"
    (is (nil? (reject ["output-styles" "my-scripts" "notes/todo.md" "bin/run.sh"])))
    (is (nil? (reject nil)))
    (is (nil? (reject [])))))

(deftest check-claude-shared-paths-rejects-machine-state
  (testing "machine-state collisions reject with the offending entry named"
    (doseq [entry ["daemon.lock" "daemon" "daemon/roster.json" "roster.json"
                   "jobs" "tasks" "sessions" "session-env"
                   "shell-snapshots" "file-history" "__store.db"]]
      (let [msg (reject [entry])]
        (is (some? msg) (str entry " should be rejected"))
        (is (re-find (re-pattern (java.util.regex.Pattern/quote entry)) msg)
            (str "message should name " entry))
        (is (re-find #"(?i)machine state" msg))))))

(deftest check-claude-shared-paths-rejects-absolute
  (testing "absolute paths reject"
    (is (some? (reject ["/etc/passwd"])))
    (is (some? (reject ["~/secrets"])))
    (is (re-find #"(?i)absolute" (reject ["/etc/passwd"])))))

(deftest check-claude-shared-paths-rejects-escapes
  (testing "'..' escapes above ~/.claude reject"
    (is (some? (reject ["../outside"])))
    (is (some? (reject ["a/../../outside"])))
    (is (re-find #"escapes" (reject ["../outside"]))))
  (testing "'..' that stays within ~/.claude is allowed"
    (is (nil? (reject ["a/../b"])))))
