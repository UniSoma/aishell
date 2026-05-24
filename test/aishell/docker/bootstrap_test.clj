(ns aishell.docker.bootstrap-test
  (:require [clojure.test :refer [deftest is testing]]
            [aishell.docker.bootstrap :as bootstrap]))

(deftest derive-state-pending-when-entrypoint-not-done
  (testing "entrypoint-done? false → :pending regardless of all other state"
    (is (= :pending (bootstrap/derive-state {:entrypoint-done? false
                                             :pre-start-configured? false
                                             :done? false
                                             :failed? false})))
    (is (= :pending (bootstrap/derive-state {:entrypoint-done? false
                                             :pre-start-configured? true
                                             :done? true
                                             :failed? false})))
    (is (= :pending (bootstrap/derive-state {:entrypoint-done? false
                                             :pre-start-configured? true
                                             :done? false
                                             :failed? true})))))

(deftest derive-state-none-when-pre-start-unset
  (testing "entrypoint-done + pre-start-configured? false → :none"
    (is (= :none (bootstrap/derive-state {:entrypoint-done? true
                                          :pre-start-configured? false
                                          :done? false
                                          :failed? false})))
    (is (= :none (bootstrap/derive-state {:entrypoint-done? true
                                          :pre-start-configured? false
                                          :done? true
                                          :failed? true})))))

(deftest derive-state-pending-when-no-sentinels
  (testing "entrypoint-done, pre_start configured, no pre-start sentinels yet → :pending"
    (is (= :pending (bootstrap/derive-state {:entrypoint-done? true
                                             :pre-start-configured? true
                                             :done? false
                                             :failed? false})))))

(deftest derive-state-ready-when-done-only
  (testing "entrypoint-done + pre-start done sentinel (no failed) → :ready"
    (is (= :ready (bootstrap/derive-state {:entrypoint-done? true
                                           :pre-start-configured? true
                                           :done? true
                                           :failed? false})))))

(deftest derive-state-failed
  (testing "entrypoint-done + failed sentinel only → :failed"
    (is (= :failed (bootstrap/derive-state {:entrypoint-done? true
                                            :pre-start-configured? true
                                            :done? false
                                            :failed? true}))))
  (testing "both sentinels race → :failed (failure takes precedence)"
    (is (= :failed (bootstrap/derive-state {:entrypoint-done? true
                                            :pre-start-configured? true
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
