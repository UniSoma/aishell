(ns aishell.docker.volume-test
  (:require [clojure.test :refer [deftest is testing]]
            [aishell.docker.volume :as vol]))

(def ^:private claude-only
  {:with-claude true :claude-version "2.0.22"})

(deftest harness-hash-ignores-removed-openspec
  (testing "a stale OpenSpec flag no longer contributes to the hash"
    (is (= (vol/compute-harness-hash claude-only)
           (vol/compute-harness-hash (assoc claude-only
                                            :with-openspec true
                                            :openspec-version "1.2.3"))))))

(deftest harness-hash-is-stable-for-unaffected-users
  (testing "hashes for OpenSpec-free configs are unchanged by the removal"
    (is (= "65b8e9d41105" (vol/compute-harness-hash claude-only)))))

(deftest install-commands-omit-removed-openspec
  (testing "a stale OpenSpec flag installs no npm package"
    (is (not (re-find #"openspec"
                      (vol/build-install-commands
                       (assoc claude-only :with-openspec true)))))))
