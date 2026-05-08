(ns aishell.docker.bootstrap-test
  (:require [clojure.test :refer [deftest is testing]]
            [aishell.docker.bootstrap :as bootstrap]))

(deftest derive-state-none-when-pre-start-unset
  (testing "pre-start-configured? false → :none, regardless of sentinels"
    (is (= :none (bootstrap/derive-state {:pre-start-configured? false
                                          :done? false
                                          :failed? false})))
    (is (= :none (bootstrap/derive-state {:pre-start-configured? false
                                          :done? true
                                          :failed? true})))))

(deftest derive-state-pending-when-no-sentinels
  (testing "pre_start configured but no sentinels yet → :pending"
    (is (= :pending (bootstrap/derive-state {:pre-start-configured? true
                                             :done? false
                                             :failed? false})))))

(deftest derive-state-ready-when-done-only
  (testing "done sentinel present (no failed) → :ready"
    (is (= :ready (bootstrap/derive-state {:pre-start-configured? true
                                           :done? true
                                           :failed? false})))))

(deftest derive-state-failed
  (testing "failed sentinel only → :failed"
    (is (= :failed (bootstrap/derive-state {:pre-start-configured? true
                                            :done? false
                                            :failed? true}))))
  (testing "both sentinels race → :failed (failure takes precedence)"
    (is (= :failed (bootstrap/derive-state {:pre-start-configured? true
                                            :done? true
                                            :failed? true})))))

(deftest merge-bootstrap-running-uses-state-map
  (testing "running container picks up its bootstrap from the state map"
    (is (= [{:name "aishell-h-claude" :status "Up 3 minutes"
             :created "x" :bootstrap :ready}]
           (bootstrap/merge-bootstrap
            [{:name "aishell-h-claude" :status "Up 3 minutes" :created "x"}]
            {"aishell-h-claude" :ready})))))

(deftest merge-bootstrap-non-running-short-circuits-to-none
  (testing "stopped container reports :none even when state map says otherwise"
    (is (= [{:name "aishell-h-shell" :status "Exited (0) 2 hours ago"
             :created "x" :bootstrap :none}]
           (bootstrap/merge-bootstrap
            [{:name "aishell-h-shell" :status "Exited (0) 2 hours ago" :created "x"}]
            {"aishell-h-shell" :ready}))))
  (testing "Created/Restarting also short-circuit to :none"
    (is (= [{:name "a" :status "Created" :bootstrap :none}
            {:name "b" :status "Restarting (1) 3 seconds ago" :bootstrap :none}]
           (bootstrap/merge-bootstrap
            [{:name "a" :status "Created"}
             {:name "b" :status "Restarting (1) 3 seconds ago"}]
            {"a" :ready "b" :failed})))))

(deftest merge-bootstrap-running-missing-from-map-defaults-to-none
  (testing "running container missing from the state map → :none (defensive)"
    (is (= [{:name "aishell-h-orphan" :status "Up 1 minute"
             :created "x" :bootstrap :none}]
           (bootstrap/merge-bootstrap
            [{:name "aishell-h-orphan" :status "Up 1 minute" :created "x"}]
            {})))))

(deftest merge-bootstrap-mixed-rows-and-all-enum-values
  (testing "each enum value flows through for running rows; full taxonomy"
    (is (= [{:name "n1" :status "Up 1m" :bootstrap :none}
            {:name "n2" :status "Up 1m" :bootstrap :pending}
            {:name "n3" :status "Up 1m" :bootstrap :ready}
            {:name "n4" :status "Up 1m" :bootstrap :failed}]
           (bootstrap/merge-bootstrap
            [{:name "n1" :status "Up 1m"}
             {:name "n2" :status "Up 1m"}
             {:name "n3" :status "Up 1m"}
             {:name "n4" :status "Up 1m"}]
            {"n1" :none "n2" :pending "n3" :ready "n4" :failed})))))
