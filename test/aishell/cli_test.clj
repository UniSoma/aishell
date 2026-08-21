(ns aishell.cli-test
  (:require [clojure.test :refer [deftest is testing]]
            [babashka.fs :as fs]
            [clojure.set :as set]
            [clojure.string :as str]
            [aishell.harness :as harness]
            [aishell.output :as output]
            [aishell.state :as state]
            [aishell.cli :as cli]))

(deftest resolve-setup-state-plain-setup-is-declarative
  (testing "plain setup does not inherit omitted flags from saved state"
    (is (= {:reuse-config? false
            :state-map {:with-claude true
                        :with-opencode false
                        :with-codex false
                        :with-gemini false
                        :with-pi false
                        :with-gitleaks false
                        :unisoma false
                        :claude-version "2.0.22"
                        :opencode-version nil
                        :codex-version nil
                        :gemini-version nil
                        :pi-version nil}}
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
            :with-gitleaks true
            :unisoma true
            :claude-version "2.0.22"
            :opencode-version nil
            :codex-version nil
            :gemini-version nil
            :pi-version "1.0.0"}
           (:state-map (cli/resolve-setup-state {:reuse-config true
                                                 :with-opencode true
                                                 :with-pi "1.0.0"}
                                                {:with-claude true
                                                 :claude-version "2.0.22"
                                                 :with-opencode true
                                                 :opencode-version "0.9.0"
                                                 :with-gitleaks true
                                                 :unisoma true}))))))

(deftest resolve-setup-state-drops-removed-openspec-keys
  (testing "reuse mode ignores OpenSpec left over in a saved setup"
    (let [state-map (:state-map (cli/resolve-setup-state
                                 {:reuse-config true}
                                 {:with-claude true
                                  :claude-version "2.0.22"
                                  :with-openspec true
                                  :openspec-version "1.2.3"}))]
      (is (not (contains? state-map :with-openspec)))
      (is (not (contains? state-map :openspec-version)))
      (is (true? (:with-claude state-map))))))

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

(deftest normalize-config-dir-name-accepts-valid
  (testing "literal names, with or without leading dot"
    (is (= ".aishell" (cli/normalize-config-dir-name ".aishell")))
    (is (= ".sandbox" (cli/normalize-config-dir-name ".sandbox")))
    (is (= ".aishell" (cli/normalize-config-dir-name "aishell")))
    (is (= ".sandbox" (cli/normalize-config-dir-name "sandbox")))))

(deftest normalize-config-dir-name-rejects-invalid
  (testing "anything else is nil"
    (is (nil? (cli/normalize-config-dir-name ".secrets")))
    (is (nil? (cli/normalize-config-dir-name "foo")))
    (is (nil? (cli/normalize-config-dir-name "")))
    (is (nil? (cli/normalize-config-dir-name nil)))))

(deftest scaffold-config-dir-creates-sandbox
  (testing "scaffolds .sandbox/config.yaml into an empty project"
    (let [dir (str (fs/create-temp-dir {:prefix "aishell-scaffold-test"}))]
      (try
        (cli/scaffold-config-dir! ".sandbox" dir)
        (is (fs/exists? (fs/path dir ".sandbox" "config.yaml")))
        (finally
          (fs/delete-tree dir))))))

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

;; --- command surface --------------------------------------------------------

(deftest every-harness-is-a-recognised-subcommand
  (testing "each registry harness dispatches as its own subcommand"
    (is (= (set (harness/subcommands))
           (set/intersection cli/known-subcommands (set (harness/subcommands))))))
  (testing "recognised subcommands and the suggestion vocabulary are one surface"
    (is (= output/known-commands cli/known-subcommands))))

(deftest every-harness-passes-its-argv-through
  (testing "pass-through is exactly the harness subcommands"
    (is (= (set (harness/subcommands)) cli/pass-through-harnesses))))

;; --- setup flags and labels -------------------------------------------------

(deftest setup-spec-carries-one-flag-per-harness
  (testing "every harness has a --with-<id> flag"
    (is (= #{:with-claude :with-opencode :with-codex :with-gemini :with-pi :with-gitleaks}
           (set/intersection (set (keys cli/setup-spec))
                             (set (map :state-key harness/registry))))))
  (testing "versioned flags take an optional =VERSION and so are not coerced"
    (is (= {:desc "Include Claude Code (optional: =VERSION)"}
           (:with-claude cli/setup-spec)))
    (is (= {:desc "Include Pi coding agent (optional: =VERSION)"}
           (:with-pi cli/setup-spec)))
    (is (= {:desc "Include Codex CLI (optional: =VERSION)"}
           (:with-codex cli/setup-spec))))
  (testing "the version-less gitleaks flag is a plain boolean"
    (is (= {:coerce :boolean :desc "Include Gitleaks secret scanner"}
           (:with-gitleaks cli/setup-spec))))
  (testing "non-harness options are untouched"
    (is (= {:coerce :boolean :desc "Force rebuild (bypass Docker cache)"}
           (:force cli/setup-spec)))))

(deftest setup-help-lists-harness-flags-in-registry-order
  (testing "harness flags come first, in display order, then the rest"
    (let [out (with-out-str (cli/print-setup-help))
          index (fn [flag] (str/index-of out (str "--" flag)))]
      (is (apply < (map index ["with-claude" "with-opencode" "with-codex"
                               "with-gemini" "with-pi" "with-gitleaks"
                               "unisoma" "dir"]))))))

(deftest setup-validation-uses-the-canonical-label
  (testing "a bad version names the harness by its canonical label"
    (is (str/starts-with? (cli/setup-validation-error
                           {:reuse-config? false
                            :state-map {:with-codex true :codex-version "1.2"}})
                          "Invalid Codex CLI version format"))
    (is (str/starts-with? (cli/setup-validation-error
                           {:reuse-config? false
                            :state-map {:with-pi true :pi-version "1.2"}})
                          "Invalid Pi coding agent version format"))
    (is (str/starts-with? (cli/setup-validation-error
                           {:reuse-config? false
                            :state-map {:with-gemini true :gemini-version "1.2"}})
                          "Invalid Gemini CLI version format"))))

(deftest empty-setup-state-covers-every-harness-key
  (testing "a boolean per harness, a version per versioned harness, plus unisoma"
    (is (= {:with-claude false :with-opencode false :with-codex false
            :with-gemini false :with-pi false :with-gitleaks false
            :unisoma false
            :claude-version nil :opencode-version nil :codex-version nil
            :gemini-version nil :pi-version nil}
           cli/empty-setup-state))))

(deftest explicit-setup-state-reads-every-harness-flag
  (testing "flags with versions, bare flags, and the boolean-only harness"
    (is (= (assoc cli/empty-setup-state
                  :with-pi true :pi-version "1.2.3"
                  :with-gitleaks true
                  :with-gemini true)
           (cli/explicit-setup-state {:with-pi "1.2.3"
                                      :with-gemini true
                                      :with-gitleaks true})))))

(deftest reused-setup-summary-uses-canonical-labels
  (testing "each enabled harness prints its canonical label and version"
    (let [out (with-out-str
                (cli/print-effective-reused-setup
                 (assoc cli/empty-setup-state
                        :with-codex true :codex-version "1.2.3"
                        :with-pi true
                        :with-gitleaks true
                        :unisoma true)))]
      (is (str/includes? out "  Codex CLI: 1.2.3"))
      (is (str/includes? out "  Pi coding agent: latest"))
      (is (str/includes? out "  Gitleaks: enabled"))
      (is (str/includes? out "  UniSoma: enabled"))))
  (testing "an empty configuration says so"
    (is (str/includes? (with-out-str (cli/print-effective-reused-setup cli/empty-setup-state))
                       "No harnesses or optional tools enabled"))))

(deftest enabled-harness-list-names-volume-participants
  (testing "gitleaks lives outside the volume and is not listed"
    (is (= "claude,pi"
           (cli/enabled-harness-list (assoc cli/empty-setup-state
                                            :with-claude true
                                            :with-pi true
                                            :with-gitleaks true))))))

(deftest help-lists-every-harness-command-with-its-label
  (testing "with no saved state every harness is listed, for discoverability"
    (let [out (with-redefs [state/read-state (fn [] nil)]
                (binding [output/CYAN "" output/NC "" output/BOLD ""]
                  (with-out-str (cli/print-help))))]
      (is (str/includes? out "claude     Run Claude Code"))
      (is (str/includes? out "opencode   Run OpenCode"))
      (is (str/includes? out "codex      Run Codex CLI"))
      (is (str/includes? out "gemini     Run Gemini CLI"))
      (is (str/includes? out "pi         Run Pi coding agent"))
      (is (str/includes? out "gitleaks   Run Gitleaks"))))
  (testing "only the harnesses in the saved state are listed"
    (let [out (with-redefs [state/read-state (fn [] {:with-pi true})]
                (binding [output/CYAN "" output/NC "" output/BOLD ""]
                  (with-out-str (cli/print-help))))]
      (is (str/includes? out "pi         Run Pi coding agent"))
      (is (not (str/includes? out "  claude     Run Claude Code"))))))
