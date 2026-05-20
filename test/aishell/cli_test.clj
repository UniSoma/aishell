(ns aishell.cli-test
  (:require [clojure.test :refer [deftest is testing]]
            [aishell.cli :as cli]))

(deftest resolve-setup-state-plain-setup-is-declarative
  (testing "plain setup does not inherit omitted flags from saved state"
    (is (= {:reuse-config? false
            :state-map {:with-claude true
                        :with-opencode false
                        :with-codex false
                        :with-gemini false
                        :with-pi false
                        :with-openspec false
                        :with-gitleaks false
                        :unisoma false
                        :claude-version "2.0.22"
                        :opencode-version nil
                        :codex-version nil
                        :gemini-version nil
                        :pi-version nil
                        :openspec-version nil}}
           (cli/resolve-setup-state {:with-claude "2.0.22"}
                                    {:with-opencode true
                                     :opencode-version "1.2.3"
                                     :with-gitleaks true
                                     :unisoma true})))))

(deftest resolve-setup-state-reuse-config-merges-and-overrides
  (testing "reuse mode inherits omitted options and bare flags reset versions to latest"
    (is (= {:with-claude true
            :with-opencode true
            :with-codex false
            :with-gemini false
            :with-pi true
            :with-openspec false
            :with-gitleaks true
            :unisoma true
            :claude-version "2.0.22"
            :opencode-version nil
            :codex-version nil
            :gemini-version nil
            :pi-version "1.0.0"
            :openspec-version nil}
           (:state-map (cli/resolve-setup-state {:reuse-config true
                                                 :with-opencode true
                                                 :with-pi "1.0.0"}
                                                {:with-claude true
                                                 :claude-version "2.0.22"
                                                 :with-opencode true
                                                 :opencode-version "0.9.0"
                                                 :with-gitleaks true
                                                 :unisoma true}))))))

(deftest resolve-setup-state-reuse-config-requires-saved-setup
  (testing "reuse mode fails clearly when no saved setup exists"
    (is (= {:reuse-config? true
            :error "--reuse-config requires an existing saved setup. Run plain 'aishell setup --with-...' to write a new configuration."}
           (cli/resolve-setup-state {:reuse-config true} nil)))))

(deftest setup-validation-error-validates-merged-effective-config
  (testing "inherited OpenCode satisfies --unisoma in reuse mode"
    (is (nil? (cli/setup-validation-error
               (cli/resolve-setup-state {:reuse-config true :unisoma true}
                                        {:with-opencode true
                                         :opencode-version "1.2.3"})))))
  (testing "invalid saved config includes a recovery hint"
    (is (= "Saved setup config is invalid: --unisoma requires --with-opencode.\nRun plain 'aishell setup --with-...' to write a new configuration."
           (cli/setup-validation-error
            {:reuse-config? true
             :state-map {:with-opencode false
                         :unisoma true}})))))

(deftest format-ps-data-empty
  (testing "no containers yields an empty vector"
    (is (= [] (cli/format-ps-data [])))))

(deftest classify-json-command-supported
  (testing "wired-today subcommands and --version are :supported"
    (is (= :supported (cli/classify-json-command ["ps"])))
    (is (= :supported (cli/classify-json-command ["--version"])))
    (is (= :supported (cli/classify-json-command ["-v"])))))

(deftest classify-json-command-unsupported
  (testing "known subcommands without a wired JSON path are :unsupported"
    (is (= :unsupported (cli/classify-json-command ["setup"])))
    (is (= :unsupported (cli/classify-json-command ["update"])))
    (is (= :unsupported (cli/classify-json-command ["exec" "ls"])))
    (is (= :unsupported (cli/classify-json-command ["attach" "claude"])))
    (is (= :unsupported (cli/classify-json-command ["a" "claude"])))
    (is (= :unsupported (cli/classify-json-command ["claude"])))
    (is (= :unsupported (cli/classify-json-command ["opencode"])))
    (is (= :unsupported (cli/classify-json-command ["codex"])))
    (is (= :unsupported (cli/classify-json-command ["gemini"])))
    (is (= :unsupported (cli/classify-json-command ["pi"])))
    (is (= :unsupported (cli/classify-json-command ["gitleaks"])))
    (is (= :unsupported (cli/classify-json-command ["vscode"])))
    (is (= :unsupported (cli/classify-json-command ["upgrade"]))))
  (testing "Group-A-eventually subcommands without a wired JSON path are :unsupported"
    (is (= :unsupported (cli/classify-json-command ["volumes"])))
    (is (= :unsupported (cli/classify-json-command ["volumes" "list"])))
    (is (= :unsupported (cli/classify-json-command ["info"])))
    (is (= :unsupported (cli/classify-json-command ["check"]))))
  (testing "no subcommand (bare --json) is :unsupported"
    (is (= :unsupported (cli/classify-json-command [])))))

(deftest classify-json-command-unknown
  (testing "unknown command beats unsupported_json"
    (is (= :unknown (cli/classify-json-command ["foobar"])))
    (is (= :unknown (cli/classify-json-command ["nope" "extra" "args"])))))

(deftest classify-json-command-help
  (testing "--help wins over --json regardless of position or subcommand"
    (is (= :help (cli/classify-json-command ["--help"])))
    (is (= :help (cli/classify-json-command ["-h"])))
    (is (= :help (cli/classify-json-command ["ps" "--help"])))
    (is (= :help (cli/classify-json-command ["setup" "-h"])))
    (is (= :help (cli/classify-json-command ["foobar" "--help"])))))

(deftest format-ps-data-extracts-short-name-and-keys
  (testing "each container produces {:name :fullName :status :created :bootstrap :ready}"
    (is (= [{:name "claude"
             :fullName "aishell-a1b2c3d4-claude"
             :status "Up 3 minutes"
             :created "2026-05-07 14:00:00 +0000 UTC"
             :bootstrap :ready
             :ready true}
            {:name "shell"
             :fullName "aishell-a1b2c3d4-shell"
             :status "Exited (0) 2 hours ago"
             :created "2026-05-07 12:00:00 +0000 UTC"
             :bootstrap :none
             :ready false}
            {:name "boot"
             :fullName "aishell-a1b2c3d4-boot"
             :status "Up 5 seconds"
             :created "2026-05-07 14:05:00 +0000 UTC"
             :bootstrap :pending
             :ready false}
            {:name "broken"
             :fullName "aishell-a1b2c3d4-broken"
             :status "Up 1 minute"
             :created "2026-05-07 14:04:00 +0000 UTC"
             :bootstrap :failed
             :ready false}
            {:name "bare"
             :fullName "aishell-a1b2c3d4-bare"
             :status "Up 2 minutes"
             :created "2026-05-07 14:03:00 +0000 UTC"
             :bootstrap :none
             :ready true}]
           (cli/format-ps-data
            [{:name "aishell-a1b2c3d4-claude"
              :status "Up 3 minutes"
              :created "2026-05-07 14:00:00 +0000 UTC"
              :bootstrap :ready}
             {:name "aishell-a1b2c3d4-shell"
              :status "Exited (0) 2 hours ago"
              :created "2026-05-07 12:00:00 +0000 UTC"
              :bootstrap :none}
             {:name "aishell-a1b2c3d4-boot"
              :status "Up 5 seconds"
              :created "2026-05-07 14:05:00 +0000 UTC"
              :bootstrap :pending}
             {:name "aishell-a1b2c3d4-broken"
              :status "Up 1 minute"
              :created "2026-05-07 14:04:00 +0000 UTC"
              :bootstrap :failed}
             {:name "aishell-a1b2c3d4-bare"
              :status "Up 2 minutes"
              :created "2026-05-07 14:03:00 +0000 UTC"
              :bootstrap :none}])))))
