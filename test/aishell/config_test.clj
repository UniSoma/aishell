(ns aishell.config-test
  (:require [clojure.test :refer [deftest is testing]]
            [aishell.config :as config]))

(deftest resolve-claude-isolation-defaults-to-shared
  (testing "absent key defaults to :shared"
    (is (= :shared (config/resolve-claude-isolation {})))
    (is (= :shared (config/resolve-claude-isolation nil))))
  (testing "nil value defaults to :shared"
    (is (= :shared (config/resolve-claude-isolation {:claude_isolation nil}))))
  (testing "explicit shared is :shared"
    (is (= :shared (config/resolve-claude-isolation {:claude_isolation "shared"}))))
  (testing "unknown value falls back to :shared"
    (is (= :shared (config/resolve-claude-isolation {:claude_isolation "bogus"})))))

(deftest resolve-claude-isolation-project
  (testing "project value resolves to :project"
    (is (= :project (config/resolve-claude-isolation {:claude_isolation "project"}))))
  (testing "keyword value also resolves (coerced via name)"
    (is (= :project (config/resolve-claude-isolation {:claude_isolation :project})))))

(defn- captured-warnings [f]
  (let [err (java.io.StringWriter.)]
    (binding [*err* err]
      (f))
    (str err)))

(deftest validate-claude-isolation-warns-on-invalid
  (testing "invalid value produces a warning naming the value and valid options"
    (let [out (captured-warnings
               #(config/validate-claude-isolation "bogus" "/tmp/config.yaml"))]
      (is (re-find #"Invalid claude_isolation" out))
      (is (re-find #"bogus" out))
      (is (re-find #"shared, project" out))))
  (testing "valid values do not warn"
    (is (= "" (captured-warnings #(config/validate-claude-isolation "shared" "/x"))))
    (is (= "" (captured-warnings #(config/validate-claude-isolation "project" "/x"))))))

(deftest merge-configs-claude-isolation-project-overrides-global
  (testing "project claude_isolation overrides global (scalar override) and survives merge"
    (let [merged (config/merge-configs {:claude_isolation "shared"}
                                       {:claude_isolation "project"})]
      (is (= "project" (:claude_isolation merged)))
      (is (= :project (config/resolve-claude-isolation merged)))))
  (testing "global-only value survives merge when project omits it"
    (let [merged (config/merge-configs {:claude_isolation "project"} {})]
      (is (= "project" (:claude_isolation merged)))))
  (testing "guards against the :else-drop trap: value is not silently dropped"
    (let [merged (config/merge-configs {} {:claude_isolation "project"})]
      (is (contains? merged :claude_isolation)))))

(deftest merge-configs-claude-shared-paths-additive
  (testing "global and project claude_shared_paths concatenate (list-key merge)"
    (let [merged (config/merge-configs {:claude_shared_paths ["a" "b"]}
                                       {:claude_shared_paths ["c"]})]
      (is (= ["a" "b" "c"] (:claude_shared_paths merged)))))
  (testing "project-only claude_shared_paths survives the merge (not :else-dropped)"
    (let [merged (config/merge-configs {} {:claude_shared_paths ["c"]})]
      (is (= ["c"] (:claude_shared_paths merged)))))
  (testing "global-only claude_shared_paths survives the merge"
    (let [merged (config/merge-configs {:claude_shared_paths ["a"]} {})]
      (is (= ["a"] (:claude_shared_paths merged))))))

(deftest validate-claude-shared-paths-warns-on-bad-entries
  (testing "non-list value warns"
    (is (re-find #"must be a list"
                 (captured-warnings
                  #(config/validate-claude-shared-paths "nope" "/x")))))
  (testing "blank/non-string entries warn"
    (is (re-find #"non-empty string"
                 (captured-warnings
                  #(config/validate-claude-shared-paths ["ok" "" 5] "/x")))))
  (testing "valid list of strings does not warn"
    (is (= "" (captured-warnings
               #(config/validate-claude-shared-paths ["skills" "notes/x.md"] "/x"))))))
