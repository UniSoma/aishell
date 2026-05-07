(ns aishell.attach.parse-test
  (:require [clojure.test :refer [deftest is testing]]
            [aishell.attach.parse :as parse]))

(deftest parse-attach-args-name-only
  (testing "single name with no -- yields nil command-argv"
    (is (= {:name "session" :command-argv nil}
           (parse/parse-attach-args ["session"])))))

(deftest parse-attach-args-name-and-command
  (testing "name then -- then command yields command-argv vector"
    (is (= {:name "session" :command-argv ["btm"]}
           (parse/parse-attach-args ["session" "--" "btm"])))))

(deftest parse-attach-args-multi-arg-command
  (testing "multi-arg command preserves argv boundaries verbatim"
    (is (= {:name "session" :command-argv ["vim" "my notes.md"]}
           (parse/parse-attach-args ["session" "--" "vim" "my notes.md"])))))

(deftest parse-attach-args-multiple-double-dash
  (testing "only the first -- is consumed; later -- pass through to command"
    (is (= {:name "session" :command-argv ["bash" "-c" "--" "foo"]}
           (parse/parse-attach-args ["session" "--" "bash" "-c" "--" "foo"])))))

(deftest parse-attach-args-empty-command
  (testing "-- with no command yields a specific error"
    (is (= {:error "attach: '--' given but no command followed"}
           (parse/parse-attach-args ["session" "--"])))))

(deftest parse-attach-args-no-name-with-dash-dash
  (testing "-- as first token yields the existing 'name required' error"
    (is (= {:error "Container name required.\n\nUsage: aishell attach <name>\n\nUse 'aishell ps' to list running containers."}
           (parse/parse-attach-args ["--" "btm"])))))

(deftest parse-attach-args-no-args
  (testing "empty args yields the same 'name required' error"
    (is (= {:error "Container name required.\n\nUsage: aishell attach <name>\n\nUse 'aishell ps' to list running containers."}
           (parse/parse-attach-args [])))))

(deftest parse-attach-args-multiple-names
  (testing "more than one token before -- is an error"
    (is (= {:error "attach: only one container name allowed before '--'"}
           (parse/parse-attach-args ["a" "b" "--" "btm"])))))
