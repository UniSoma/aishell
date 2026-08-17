(ns aishell.attach.resolve-test
  (:require [clojure.test :refer [deftest is testing]]
            [aishell.attach.resolve :as resolve]))

(defn- container
  [short-name status]
  {:name (str "aishell-a1b2c3d4-" short-name)
   :status status
   :created "2026-08-17 10:00:00 +0000 UTC"})

(deftest resolve-single-running
  (testing "exactly one running container yields its short name"
    (is (= {:name "claude"}
           (resolve/resolve-attach-target [(container "claude" "Up 3 minutes")])))))

(deftest resolve-single-running-among-stopped
  (testing "stopped siblings do not block the single running pick"
    (is (= {:name "claude"}
           (resolve/resolve-attach-target
            [(container "claude" "Up 3 minutes")
             (container "shell" "Exited (0) 2 hours ago")
             (container "old" "Created")])))))

(deftest resolve-none-running-with-stopped
  (testing "no running containers names the stopped ones and hints at Docker"
    (is (= {:error (str "No running containers in this project.\n\n"
                        "To start one:  aishell claude\n\n"
                        "Stopped: claude, shell\n"
                        "If this is unexpected, check Docker: aishell check")}
           (resolve/resolve-attach-target
            [(container "claude" "Exited (0) 2 hours ago")
             (container "shell" "Created")])))))

(deftest resolve-empty-vector
  (testing "an empty listing omits the Stopped line"
    (is (= {:error (str "No running containers in this project.\n\n"
                        "To start one:  aishell claude\n\n"
                        "If this is unexpected, check Docker: aishell check")}
           (resolve/resolve-attach-target [])))))

(deftest resolve-multiple-running
  (testing "several running containers list each candidate and a copyable command"
    (is (= {:error (str "Multiple running containers — name one:\n\n"
                        "  claude    Up 3 minutes\n"
                        "  shell     Up 20 minutes\n\n"
                        "  aishell attach claude")}
           (resolve/resolve-attach-target
            [(container "claude" "Up 3 minutes")
             (container "shell" "Up 20 minutes")])))))

(deftest resolve-multiple-running-ignores-stopped
  (testing "stopped containers are not listed as candidates"
    (is (= {:error (str "Multiple running containers — name one:\n\n"
                        "  a     Up 1 second\n"
                        "  bb    Up 2 seconds\n\n"
                        "  aishell attach a")}
           (resolve/resolve-attach-target
            [(container "a" "Up 1 second")
             (container "gone" "Exited (137) 1 minute ago")
             (container "bb" "Up 2 seconds")])))))
