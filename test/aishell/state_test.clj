(ns aishell.state-test
  (:require [clojure.test :refer [deftest is testing]]
            [aishell.state :as state]))

(deftest strip-removed-keys-drops-openspec
  (testing "removed OpenSpec keys are dropped"
    (is (= {:with-claude true :claude-version "2.0.22"}
           (state/strip-removed-keys {:with-claude true
                                      :claude-version "2.0.22"
                                      :with-openspec true
                                      :openspec-version "1.2.3"}))))
  (testing "state without removed keys passes through untouched"
    (let [s {:with-claude true :claude-version nil}]
      (is (= s (state/strip-removed-keys s)))))
  (testing "nil state stays nil"
    (is (nil? (state/strip-removed-keys nil)))))

(deftest removed-harness-warning-detects-enabled-openspec
  (testing "warns when OpenSpec was enabled"
    (is (some? (state/removed-harness-warning {:with-openspec true})))
    (is (some? (state/removed-harness-warning {:openspec-version "1.2.3"}))))
  (testing "silent for a user who never enabled OpenSpec"
    ;; Every setup since v3.7.0 wrote :with-openspec false, so the disabled
    ;; flag must not warn.
    (is (nil? (state/removed-harness-warning {:with-claude true
                                              :with-openspec false
                                              :openspec-version nil})))
    (is (nil? (state/removed-harness-warning {:with-claude true})))
    (is (nil? (state/removed-harness-warning nil))))
  (testing "the message names OpenSpec and says it was removed"
    (let [msg (state/removed-harness-warning {:with-openspec true})]
      (is (re-find #"OpenSpec" msg))
      (is (re-find #"removed" msg)))))
