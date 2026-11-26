(ns aishell.config-test
  (:require [clojure.test :refer [deftest is testing]]
            [babashka.fs :as fs]
            [aishell.config :as config]
            [aishell.harness :as harness]))

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

(deftest harness-args-accepts-every-harness
  (testing "each registry harness is accepted"
    (doseq [{:keys [subcommand]} harness/registry]
      (is (= "" (captured-warnings
                 #(config/validate-harness-names {(keyword subcommand) ["--x"]}
                                                 "config.yaml")))
          (str subcommand " should be a known harness name")))))

(deftest harness-args-accepts-vscode
  (testing "vscode has no descriptor but its harness_args are consumed, so it stays known"
    (is (= "" (captured-warnings
               #(config/validate-harness-names {:vscode ["--verbose"]} "config.yaml"))))))

(deftest harness-args-warns-about-unknown-harness-names
  (testing "a leftover gitleaks entry is an unknown harness name"
    (let [out (captured-warnings
               #(config/validate-harness-names {:gitleaks ["--redact"]} "config.yaml"))]
      (is (re-find #"Unknown harness names" out))
      (is (re-find #"gitleaks" out))))
  (testing "a name no harness answers to is still reported as unknown"
    (let [out (captured-warnings
               #(config/validate-harness-names {:openspec ["--x"]} "config.yaml"))]
      (is (re-find #"Unknown harness names" out)))))

(deftest harness-args-warns-for-a-name-no-harness-owns
  (testing "a genuine typo still warns"
    (is (re-find #"opnecode"
                 (captured-warnings
                  #(config/validate-harness-names {:opnecode ["--x"]} "config.yaml"))))))

(deftest merge-configs-resources-shallow-merge
  (testing "a project key overrides the global key; other global keys are kept"
    (is (= {:memory "16g" :cpus "4"}
           (:resources (config/merge-configs {:resources {:memory "8g" :cpus "4"}}
                                             {:resources {:memory "16g"}})))))
  (testing "global-only and project-only maps survive the merge"
    (is (= {:cpus "2"} (:resources (config/merge-configs {:resources {:cpus "2"}} {}))))
    (is (= {:cpus "2"} (:resources (config/merge-configs {} {:resources {:cpus "2"}})))))
  (testing "no resources on either side adds none"
    (is (not (contains? (config/merge-configs {:env {:A "1"}} {}) :resources)))))

(deftest validate-config-knows-resources
  (testing "resources is a known key"
    (is (= "" (captured-warnings
               #(config/validate-config {:resources {:memory "8g" :cpus "2"}} "/x")))))
  (testing "an unknown key under resources warns without exiting"
    (let [out (captured-warnings
               #(config/validate-config {:resources {:swap "1g"}} "/x"))]
      (is (re-find #"resources" out))
      (is (re-find #"swap" out)))))

(deftest load-config-resources-follow-extends
  (let [dir (fs/create-temp-dir)
        global (str (fs/path dir "global.yaml"))
        project (str (fs/path dir "project.yaml"))
        load (fn [project-yaml]
               (spit global "resources:\n  memory: 8g\n  cpus: 4\n")
               (spit project project-yaml)
               (with-redefs [config/global-config-path (constantly global)
                             config/project-config-path (constantly project)]
                 (:resources (config/load-config (str dir)))))]
    (try
      (testing "extends: global merges the project key over the global map"
        (is (= {:memory "16g" :cpus 4} (load "resources:\n  memory: 16g\n"))))
      (testing "extends: none drops the global map"
        (is (= {:memory "16g"} (load "extends: none\nresources:\n  memory: 16g\n"))))
      (finally
        (fs/delete-tree dir)))))
