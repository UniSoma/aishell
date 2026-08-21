(ns aishell.harness-test
  (:require [clojure.test :refer [deftest is testing]]
            [aishell.harness :as harness]))

;; ---------------------------------------------------------------------------
;; Registry membership and order
;; ---------------------------------------------------------------------------

(deftest registry-holds-six-descriptors-in-display-order
  (testing "the registry is the closed, ordered set of harnesses"
    (is (= [:claude :opencode :codex :gemini :pi :gitleaks]
           (mapv :id harness/registry))))
  (testing "descriptors are pure data — no functions in any row"
    (is (empty? (for [d harness/registry
                      v (tree-seq coll? seq d)
                      :when (fn? v)]
                  v)))))

(deftest descriptors-are-addressable-by-id
  (testing "lookup by identity keyword"
    (is (= :codex (:id (harness/descriptor :codex)))))
  (testing "an unknown id has no descriptor"
    (is (nil? (harness/descriptor :openspec)))))

(deftest canonical-long-labels
  (testing "one canonical label per harness"
    (is (= {:claude "Claude Code"
            :opencode "OpenCode"
            :codex "Codex CLI"
            :gemini "Gemini CLI"
            :pi "Pi coding agent"
            :gitleaks "Gitleaks"}
           (into {} (map (juxt :id :label)) harness/registry)))))

;; ---------------------------------------------------------------------------
;; Table-driven completeness
;;
;; The rules below are written independently of the registry: a descriptor that
;; drops a field its declared capabilities require must fail this test.
;; ---------------------------------------------------------------------------

(def ^:private always-required
  [:id :label :subcommand :state-key
   :interactive? :pre-start? :accepts-config-defaults? :volume-participant?
   :install])

(def ^:private conditional-rules
  [{:name "harness volume participants carry a version key"
    :when :volume-participant?
    :require [:version-key]}
   {:name "npm installs name their package"
    :when #(= :npm (get-in % [:install :kind]))
    :require [[:install :package]]}
   {:name "binary-tarball installs carry both URL shapes and a target dir"
    :when #(= :binary-tarball (get-in % [:install :kind]))
    :require [[:install :latest-url] [:install :versioned-url-template]
              [:install :install-dir]]}
   {:name "image-baked installs pin the version built into the image"
    :when #(= :image-baked (get-in % [:install :kind]))
    :require [[:install :version]]}
   {:name "alias emitters say whether they emit unconditionally"
    :when :alias
    :require [[:alias :always?]]}])

(defn- missing-path? [descriptor path]
  (nil? (get-in descriptor (if (vector? path) path [path]))))

(defn- descriptor-violations
  "Independent completeness checker: returns a seq of [id rule-name path]."
  [descriptor]
  (concat
   (for [k always-required
         :when (missing-path? descriptor k)]
     [(:id descriptor) "always required" k])
   (for [{:keys [name] rule-when :when required :require} conditional-rules
         :when (rule-when descriptor)
         path required
         :when (missing-path? descriptor path)]
     [(:id descriptor) name path])))

(deftest every-descriptor-carries-the-fields-its-capabilities-require
  (testing "no descriptor in the registry violates a completeness rule"
    (is (= [] (vec (mapcat descriptor-violations harness/registry))))))

(deftest completeness-checker-rejects-an-incomplete-descriptor
  (testing "a descriptor missing an unconditionally required field is caught"
    (is (seq (descriptor-violations (dissoc (harness/descriptor :pi) :label)))))
  (testing "a volume participant missing its version key is caught"
    (is (seq (descriptor-violations (dissoc (harness/descriptor :gemini) :version-key)))))
  (testing "an npm install missing its package is caught"
    (is (seq (descriptor-violations (update (harness/descriptor :claude) :install dissoc :package)))))
  (testing "an alias emitter missing :always? is caught"
    (is (seq (descriptor-violations (update (harness/descriptor :codex) :alias dissoc :always?))))))

(deftest install-kinds-are-explicit
  (testing "each harness declares how it is installed"
    (is (= {:claude :npm :opencode :binary-tarball :codex :npm
            :gemini :npm :pi :npm :gitleaks :image-baked}
           (into {} (map (juxt :id #(get-in % [:install :kind]))) harness/registry)))))

;; ---------------------------------------------------------------------------
;; Volume-hash sufficiency
;;
;; The hash derivation itself lives in the volume namespace. What the registry
;; owes it: the id as a keyword, the exact state/version key spellings, and
;; participation as a capability.
;; ---------------------------------------------------------------------------

(deftest state-and-version-key-spellings-match-the-hash-contract
  (testing "state key is :with-<id> and version key is :<id>-version"
    (doseq [d harness/registry]
      (is (= (keyword (str "with-" (name (:id d)))) (:state-key d)))
      (when (:version-key d)
        (is (= (keyword (str (name (:id d)) "-version")) (:version-key d)))))))

(deftest volume-participants-exclude-gitleaks
  (testing "the five installable harnesses participate in the harness volume"
    (is (= [:claude :opencode :codex :gemini :pi]
           (mapv :id (harness/volume-participants)))))
  (testing "sorted participant ids are the hash membership set"
    (is (= [:claude :codex :gemini :opencode :pi]
           (sort (map :id (harness/volume-participants))))))
  (testing "gitleaks is baked into the image, not the volume"
    (is (false? (:volume-participant? (harness/descriptor :gitleaks))))))

;; ---------------------------------------------------------------------------
;; Capability filters
;; ---------------------------------------------------------------------------

(deftest subcommand-set-covers-every-harness
  (testing "every harness is reachable as a pass-through subcommand"
    (is (= ["claude" "opencode" "codex" "gemini" "pi" "gitleaks"]
           (harness/subcommands)))))

(deftest suggestion-contributions-include-pi
  (testing "typo suggestions draw on every harness subcommand"
    (is (= #{"claude" "opencode" "codex" "gemini" "pi" "gitleaks"}
           (set (harness/suggestion-terms))))))

(deftest alias-emitters-and-their-unconditional-flag
  (testing "gitleaks emits no shell alias"
    (is (= [:claude :opencode :codex :gemini :pi]
           (mapv :id (harness/alias-emitters)))))
  (testing "claude and codex always emit; the rest only when they carry args"
    (is (= {:claude true :opencode false :codex true :gemini false :pi false}
           (into {} (map (juxt :id #(get-in % [:alias :always?]))) (harness/alias-emitters))))))

(deftest launch-shape-capabilities
  (testing "gitleaks runs non-interactively, skips pre_start and drops config defaults"
    (let [d (harness/descriptor :gitleaks)]
      (is (false? (:interactive? d)))
      (is (false? (:pre-start? d)))
      (is (false? (:accepts-config-defaults? d)))))
  (testing "every other harness is interactive, runs pre_start and takes config defaults"
    (doseq [d harness/registry
            :when (not= :gitleaks (:id d))]
      (is (true? (:interactive? d)))
      (is (true? (:pre-start? d)))
      (is (true? (:accepts-config-defaults? d))))))

(deftest config-paths-and-env-passthrough-are-carried
  (testing "claude's config paths distinguish the directory from the seed file"
    (is (= [{:path [".claude"] :type :dir}
            {:path [".claude.json"] :type :file}]
           (:config-paths (harness/descriptor :claude)))))
  (testing "env passthrough keeps its declared order (duplicates dedupe downstream)"
    (is (= ["OPENAI_API_KEY" "CODEX_API_KEY"]
           (:env-passthrough (harness/descriptor :codex))))
    (is (= ["ANTHROPIC_API_KEY"]
           (:env-passthrough (harness/descriptor :claude)))))
  (testing "gitleaks has neither config paths nor passthrough env vars"
    (is (nil? (:config-paths (harness/descriptor :gitleaks))))
    (is (nil? (:env-passthrough (harness/descriptor :gitleaks))))))

;; ---------------------------------------------------------------------------
;; The skip-permissions override
;; ---------------------------------------------------------------------------

(deftest skip-permissions-override-has-one-spelling
  (testing "the override is read from AISHELL_SKIP_PERMISSIONS"
    (is (= "AISHELL_SKIP_PERMISSIONS" harness/skip-permissions-env-var)))
  (testing "default is on; only the literal string \"false\" turns it off"
    (is (true? (harness/skip-permissions? nil)))
    (is (true? (harness/skip-permissions? "")))
    (is (true? (harness/skip-permissions? "true")))
    (is (true? (harness/skip-permissions? "0")))
    (is (false? (harness/skip-permissions? "false")))))

;; ---------------------------------------------------------------------------
;; The argv interpreter
;; ---------------------------------------------------------------------------

(defn- argv [id inputs]
  (harness/launch-argv (harness/descriptor id) inputs))

(deftest argv-bare-launch-for-every-harness
  (testing "no config defaults, no CLI args, skip-permissions on"
    (is (= ["claude" "--dangerously-skip-permissions"] (argv :claude {:skip-permissions? true})))
    (is (= ["opencode"] (argv :opencode {:skip-permissions? true})))
    (is (= ["codex" "-c" "check_for_update_on_startup=false"] (argv :codex {:skip-permissions? true})))
    (is (= ["gemini"] (argv :gemini {:skip-permissions? true})))
    (is (= ["pi"] (argv :pi {:skip-permissions? true})))
    (is (= ["gitleaks"] (argv :gitleaks {:skip-permissions? true}))))
  (testing "skip-permissions off only affects claude"
    (is (= ["claude"] (argv :claude {:skip-permissions? false})))
    (is (= ["opencode"] (argv :opencode {:skip-permissions? false})))
    (is (= ["codex" "-c" "check_for_update_on_startup=false"] (argv :codex {:skip-permissions? false})))
    (is (= ["gemini"] (argv :gemini {:skip-permissions? false})))
    (is (= ["pi"] (argv :pi {:skip-permissions? false})))
    (is (= ["gitleaks"] (argv :gitleaks {:skip-permissions? false}))))
  (testing "omitted skip-permissions is treated as off, not as the env default"
    (is (= ["claude"] (argv :claude {})))
    (is (= ["claude"] (argv :claude nil)))))

(deftest argv-config-defaults-precede-cli-args
  (testing "defaults come first so CLI args win by position"
    (is (= ["opencode" "--model" "sonnet" "--print"]
           (argv :opencode {:default-args ["--model" "sonnet"] :cli-args ["--print"]}))))
  (testing "claude's skip-permissions flag precedes both"
    (is (= ["claude" "--dangerously-skip-permissions" "--model" "opus" "-p" "hi"]
           (argv :claude {:skip-permissions? true
                          :default-args ["--model" "opus"]
                          :cli-args ["-p" "hi"]}))))
  (testing "codex's update-check flag precedes both"
    (is (= ["codex" "-c" "check_for_update_on_startup=false" "--full-auto" "exec"]
           (argv :codex {:default-args ["--full-auto"] :cli-args ["exec"]}))))
  (testing "gemini and pi simply append"
    (is (= ["gemini" "-d" "--yolo"] (argv :gemini {:default-args ["-d"] :cli-args ["--yolo"]})))
    (is (= ["pi" "-d" "--yolo"] (argv :pi {:default-args ["-d"] :cli-args ["--yolo"]})))))

(deftest argv-cli-args-only
  (testing "CLI args alone, no config defaults"
    (is (= ["claude" "--dangerously-skip-permissions" "-p" "x"]
           (argv :claude {:skip-permissions? true :cli-args ["-p" "x"]})))
    (is (= ["gemini" "chat"] (argv :gemini {:cli-args ["chat"]})))))

(deftest argv-gitleaks-non-interactive-shape
  (testing "gitleaks takes CLI args only — config defaults do not reach it"
    (is (= ["gitleaks" "dir" "."]
           (argv :gitleaks {:default-args ["--redact"] :cli-args ["dir" "."]}))))
  (testing "config defaults alone produce a bare gitleaks argv"
    (is (= ["gitleaks"] (argv :gitleaks {:default-args ["--redact"]}))))
  (testing "skip-permissions never leaks into gitleaks"
    (is (= ["gitleaks" "git"] (argv :gitleaks {:skip-permissions? true :cli-args ["git"]})))))

(deftest argv-returns-a-vector-of-strings
  (testing "callers hand the result straight to process invocation"
    (doseq [d harness/registry]
      (let [v (harness/launch-argv d {:skip-permissions? true
                                      :default-args ["--a"]
                                      :cli-args ["--b"]})]
        (is (vector? v))
        (is (every? string? v))
        (is (= (:subcommand d) (first v)))))))
