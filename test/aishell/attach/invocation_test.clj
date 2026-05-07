(ns aishell.attach.invocation-test
  (:require [clojure.test :refer [deftest is testing]]
            [aishell.attach.invocation :as inv]))

(deftest build-docker-exec-argv-no-command
  (testing "no command-argv reproduces today's plain bash --login form"
    (is (= ["docker" "exec" "-it" "-u" "developer"
            "-e" "TERM=xterm-256color"
            "-e" "COLORTERM=truecolor"
            "-e" "LANG=C.UTF-8"
            "-e" "LC_ALL=C.UTF-8"
            "session-abc"
            "/bin/bash" "--login"]
           (inv/build-docker-exec-argv {:container "session-abc"
                                        :term "xterm-256color"
                                        :colorterm "truecolor"
                                        :command-argv nil})))))

(deftest build-docker-exec-argv-with-command
  (testing "command-argv produces a bash wrapper that runs the command then execs a login shell"
    (is (= ["docker" "exec" "-it" "-u" "developer"
            "-e" "TERM=xterm-256color"
            "-e" "COLORTERM=truecolor"
            "-e" "LANG=C.UTF-8"
            "-e" "LC_ALL=C.UTF-8"
            "session-abc"
            "/bin/bash" "--login" "-c" "\"$@\"; exec /bin/bash --login" "_" "btm"]
           (inv/build-docker-exec-argv {:container "session-abc"
                                        :term "xterm-256color"
                                        :colorterm "truecolor"
                                        :command-argv ["btm"]})))))

(deftest build-docker-exec-argv-multi-arg-command
  (testing "multi-arg command-argv flows through after the wrapper sentinel"
    (is (= ["docker" "exec" "-it" "-u" "developer"
            "-e" "TERM=xterm-256color"
            "-e" "COLORTERM=truecolor"
            "-e" "LANG=C.UTF-8"
            "-e" "LC_ALL=C.UTF-8"
            "session-abc"
            "/bin/bash" "--login" "-c" "\"$@\"; exec /bin/bash --login" "_"
            "vim" "my notes.md"]
           (inv/build-docker-exec-argv {:container "session-abc"
                                        :term "xterm-256color"
                                        :colorterm "truecolor"
                                        :command-argv ["vim" "my notes.md"]})))))

(deftest build-docker-exec-argv-empty-command-argv-treated-as-no-command
  (testing "empty command-argv falls back to plain bash --login"
    (is (= ["docker" "exec" "-it" "-u" "developer"
            "-e" "TERM=xterm-256color"
            "-e" "COLORTERM=truecolor"
            "-e" "LANG=C.UTF-8"
            "-e" "LC_ALL=C.UTF-8"
            "session-abc"
            "/bin/bash" "--login"]
           (inv/build-docker-exec-argv {:container "session-abc"
                                        :term "xterm-256color"
                                        :colorterm "truecolor"
                                        :command-argv []})))))
