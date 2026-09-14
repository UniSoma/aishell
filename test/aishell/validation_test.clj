(ns aishell.validation-test
  (:require [clojure.test :refer [deftest is testing]]
            [babashka.fs :as fs]
            [clojure.string :as str]
            [aishell.output :as output]
            [aishell.util :as util]
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

(defn- refuse
  "Run check-project-dir! with HOME redirected to `home`. Returns the
   captured stderr message when it refuses, or nil when it lets the
   directory through."
  [project-dir home]
  (let [err (java.io.StringWriter.)]
    (binding [*err* err]
      (with-redefs [util/get-home (constantly home)
                    output/exit! (fn [_] (throw (ex-info "refused" {})))]
        (try
          (validation/check-project-dir! project-dir)
          nil
          (catch clojure.lang.ExceptionInfo _
            (str err)))))))

(deftest home-dir-conflict-refuses-home-and-its-ancestors
  (let [tmp (str (fs/create-temp-dir))
        home (str (fs/create-dirs (fs/path tmp "users" "me")))
        project (str (fs/create-dirs (fs/path home "src" "proj")))]
    (try
      (testing "the home directory itself"
        (is (re-find #"is your home directory" (validation/home-dir-conflict home home))))
      (testing "a trailing slash does not change the answer"
        (is (some? (validation/home-dir-conflict (str home "/") home))))
      (testing "every ancestor of home, up to the filesystem root"
        (doseq [dir [(str (fs/path tmp "users")) tmp "/"]]
          (let [reason (validation/home-dir-conflict dir home)]
            (is (some? reason) (str dir " should be refused"))
            (is (re-find #"contains your home directory" reason)))))
      (testing "a symlink to home resolves to home"
        (let [link (str (fs/path tmp "home-link"))]
          (fs/create-sym-link link home)
          (is (re-find #"is your home directory" (validation/home-dir-conflict link home)))
          (is (some? (validation/home-dir-conflict home link)))))
      (testing "a sibling whose name merely prefixes home's is not an ancestor"
        (let [sibling (str (fs/create-dirs (fs/path tmp "users" "me2")))]
          (is (nil? (validation/home-dir-conflict sibling home)))))
      (testing "a project inside home passes"
        (is (nil? (validation/home-dir-conflict project home))))
      (finally
        (fs/delete-tree tmp)))))

(deftest check-project-dir-exits-with-a-named-reason
  (let [tmp (str (fs/create-temp-dir))
        home (str (fs/create-dirs (fs/path tmp "me")))]
    (try
      (testing "from home: names the directory, the reason, and the --unsafe limit"
        (let [msg (refuse home home)]
          (is (some? msg))
          (is (str/includes? msg home))
          (is (str/includes? msg "home directory"))
          (is (str/includes? msg "--unsafe does not override"))))
      (testing "from an ancestor of home"
        (is (str/includes? (refuse tmp home) "contains your home directory")))
      (testing "a normal project directory passes silently"
        (is (nil? (refuse (str (fs/create-dirs (fs/path home "proj"))) home))))
      (finally
        (fs/delete-tree tmp)))))
