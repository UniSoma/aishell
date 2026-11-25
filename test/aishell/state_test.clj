(ns aishell.state-test
  (:require [clojure.test :refer [deftest is testing]]
            [babashka.fs :as fs]
            [aishell.state :as state]
            [aishell.util :as util]))

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

(deftest strip-removed-keys-drops-gitleaks
  (is (= {:with-claude true}
         (state/strip-removed-keys {:with-claude true :with-gitleaks true}))))

(deftest removed-harness-warning-detects-enabled-gitleaks
  (testing "warns when Gitleaks was enabled, naming it and where it went"
    (let [msg (state/removed-harness-warning {:with-gitleaks true})]
      (is (re-find #"Gitleaks" msg))
      (is (re-find #"removed" msg))
      (is (re-find #"CONFIGURATION\.md" msg))))
  (testing "silent when the saved flag is false"
    (is (nil? (state/removed-harness-warning {:with-claude true :with-gitleaks false}))))
  (testing "both removed harnesses enabled: one warning names both"
    (let [msg (state/removed-harness-warning {:with-openspec true :with-gitleaks true})]
      (is (re-find #"OpenSpec" msg))
      (is (re-find #"Gitleaks" msg)))))

(deftest warn-removed-harnesses-warns-once-for-gitleaks
  (let [dir (str (fs/create-temp-dir))
        err (java.io.StringWriter.)]
    (try
      (with-redefs [util/config-dir (constantly dir)]
        (state/write-state {:with-claude true})
        ;; write-state strips removed keys, so plant the old state directly
        (spit (state/state-file) (pr-str {:with-claude true :with-gitleaks true}))
        (binding [*err* err *out* err]
          (state/warn-removed-harnesses!))
        (is (re-find #"Gitleaks" (str err)) "the first command warns")
        (is (= {:with-claude true} (state/read-state)) "the key is gone from saved state")
        (let [second-run (java.io.StringWriter.)]
          (binding [*err* second-run *out* second-run]
            (state/warn-removed-harnesses!))
          (is (= "" (str second-run)) "the next command is silent")))
      (finally (fs/delete-tree dir)))))

(deftest foundation-hash-reads-foundation-hash-then-dockerfile-hash
  (testing "state with both keys reads :foundation-hash"
    (is (= "new" (state/foundation-hash {:foundation-hash "new" :dockerfile-hash "old"}))))
  (testing "state written before v2.8.0 falls back to :dockerfile-hash"
    (is (= "old" (state/foundation-hash {:dockerfile-hash "old"}))))
  (testing "state without either key has no hash"
    (is (nil? (state/foundation-hash {})))))

(deftest assoc-foundation-hash-writes-both-keys
  (is (= {:build-time "t" :dockerfile-hash "h" :foundation-hash "h"}
         (state/assoc-foundation-hash {:build-time "t"} "h"))))
