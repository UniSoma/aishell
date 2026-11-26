(ns aishell.docker.run-test
  (:require [clojure.test :refer [deftest is testing]]
            [babashka.fs :as fs]
            [clojure.edn :as edn]
            [clojure.string :as str]
            [aishell.util :as util]
            [aishell.docker.naming :as naming]
            [aishell.docker.run :as run]
            [aishell.harness :as harness]
            [aishell.output :as output]
            [aishell.run]))

(def ^:private build-mounts #'run/build-harness-config-mounts)

(defn- seed-home
  "Create a temp HOME with a .claude dir containing the given relative entries
   (dirs and files), plus a .claude.json file. Returns the home path."
  [entries]
  (let [home (str (fs/create-temp-dir))
        claude (fs/path home ".claude")]
    (fs/create-dirs claude)
    (spit (str (fs/path home ".claude.json")) "{}\n")
    (doseq [{:keys [rel dir?]} entries]
      (let [p (apply fs/path claude rel)]
        (if dir?
          (fs/create-dirs p)
          (do (fs/create-dirs (fs/parent p))
              (spit (str p) "")))))
    home))

(defn- mount-strings
  "Extract the src:dst strings from a flat [-v s -v s ...] arg vector."
  [args]
  (->> args (partition 2) (map second)))

(deftest shared-mode-byte-identical
  (let [home (seed-home [])
        tmp-state (str (fs/create-temp-dir))]
    (try
      (with-redefs [util/get-home (fn [] home)
                    util/state-dir (fn [] tmp-state)]
        (let [state {:with-claude true}]
          (testing "shared mode (default cfg) mounts host ~/.claude and ~/.claude.json wholesale"
            (let [result (vec (build-mounts state home {}))]
              (is (= ["-v" (str (fs/path home ".claude") ":" (fs/path home ".claude"))
                      "-v" (str (fs/path home ".claude.json") ":" (fs/path home ".claude.json"))]
                     result))))
          (testing "explicit shared cfg matches default"
            (is (= (vec (build-mounts state home {}))
                   (vec (build-mounts state home {:claude_isolation "shared"})))))))
      (finally
        (fs/delete-tree home)
        (fs/delete-tree tmp-state)))))

(deftest project-mode-inverts-claude-mount
  (let [home (seed-home [{:rel ["skills"] :dir? true}
                         {:rel ["projects"] :dir? true}
                         {:rel ["settings.json"] :dir? false}
                         {:rel ["history.jsonl"] :dir? false}])
        tmp-state (str (fs/create-temp-dir))]
    (try
      (with-redefs [util/get-home (fn [] home)
                    util/state-dir (fn [] tmp-state)]
        (let [state {:with-claude true}
              cfg {:claude_isolation "project"}
              result (vec (build-mounts state home cfg))
              strs (mount-strings result)
              dot-claude (naming/claude-dot-claude-dir home)
              claude (str (fs/path home ".claude"))]
          (testing "base mount maps per-project dot-claude to container ~/.claude"
            (is (some #{(str dot-claude ":" claude)} strs)))
          (testing "no wholesale host ~/.claude mount"
            (is (not (some #{(str claude ":" claude)} strs))))
          (testing "~/.claude.json mount is unchanged"
            (is (some #{(str (fs/path home ".claude.json") ":" (fs/path home ".claude.json"))} strs)))
          (testing "overlays present only for allowlist sources that exist on host"
            (is (some #{(str (fs/path claude "skills") ":" (fs/path claude "skills"))} strs))
            (is (some #{(str (fs/path claude "projects") ":" (fs/path claude "projects"))} strs))
            (is (some #{(str (fs/path claude "settings.json") ":" (fs/path claude "settings.json"))} strs))
            (is (some #{(str (fs/path claude "history.jsonl") ":" (fs/path claude "history.jsonl"))} strs))
            (testing "missing allowlist dirs are pre-created on host and mounted (bootstrapping)"
              (is (some #{(str (fs/path claude "agents") ":" (fs/path claude "agents"))} strs)))
            (testing "credentials never seeded, skipped when absent on host"
              (is (not (some #{(str (fs/path claude ".credentials.json") ":" (fs/path claude ".credentials.json"))} strs)))))
          (testing "base mount precedes its overlay children"
            (let [idx (fn [s] (.indexOf (vec strs) s))]
              (is (< (idx (str dot-claude ":" claude))
                     (idx (str (fs/path claude "skills") ":" (fs/path claude "skills")))))))))
      (finally
        (fs/delete-tree home)
        (fs/delete-tree tmp-state)))))

(deftest project-mode-mounts-user-shared-paths
  ;; A user-listed root-level script (present on host) must mount like a
  ;; built-in, so it is callable from settings hooks inside the sandbox.
  (let [home (seed-home [{:rel ["run-hook.sh"] :dir? false}
                         {:rel ["team-skills"] :dir? true}])
        tmp-state (str (fs/create-temp-dir))]
    (try
      (with-redefs [util/get-home (fn [] home)
                    util/state-dir (fn [] tmp-state)]
        (let [state {:with-claude true}
              cfg {:claude_isolation "project"
                   :claude_shared_paths ["run-hook.sh" "team-skills"]}
              claude (str (fs/path home ".claude"))
              strs (mount-strings (vec (build-mounts state home cfg)))]
          (testing "user file entry mounted as a single-file overlay"
            (is (some #{(str (fs/path claude "run-hook.sh") ":" (fs/path claude "run-hook.sh"))} strs)))
          (testing "user dir entry mounted as a dir overlay"
            (is (some #{(str (fs/path claude "team-skills") ":" (fs/path claude "team-skills"))} strs)))))
      (finally
        (fs/delete-tree home)
        (fs/delete-tree tmp-state)))))

(deftest project-mode-precreates-missing-user-shared-sources
  ;; Missing user sources are pre-created per bootstrapping conventions:
  ;; a file entry becomes a file, a dir entry becomes a directory.
  (let [home (seed-home [])
        tmp-state (str (fs/create-temp-dir))]
    (try
      (with-redefs [util/get-home (fn [] home)
                    util/state-dir (fn [] tmp-state)]
        (let [state {:with-claude true}
              cfg {:claude_isolation "project"
                   :claude_shared_paths ["hooks-dir" "startup.sh"]}
              claude (fs/path home ".claude")
              strs (mount-strings (vec (build-mounts state home cfg)))]
          (testing "missing dir entry pre-created as a directory"
            (is (fs/directory? (fs/path claude "hooks-dir"))))
          (testing "missing file entry pre-created as a file, not a directory"
            (is (fs/regular-file? (fs/path claude "startup.sh"))))
          (testing "both mounted"
            (is (some #{(str (fs/path claude "hooks-dir") ":" (fs/path claude "hooks-dir"))} strs))
            (is (some #{(str (fs/path claude "startup.sh") ":" (fs/path claude "startup.sh"))} strs)))))
      (finally
        (fs/delete-tree home)
        (fs/delete-tree tmp-state)))))

(deftest project-mode-user-entry-covered-by-builtin-warns-and-no-dupes
  (let [home (seed-home [{:rel ["skills"] :dir? true}])
        tmp-state (str (fs/create-temp-dir))]
    (try
      (with-redefs [util/get-home (fn [] home)
                    util/state-dir (fn [] tmp-state)]
        (let [state {:with-claude true}
              cfg {:claude_isolation "project"
                   :claude_shared_paths ["skills"]}
              claude (fs/path home ".claude")
              sw (java.io.StringWriter.)
              strs (binding [*err* sw]
                     (mount-strings (vec (build-mounts state home cfg))))
              skills-mount (str (fs/path claude "skills") ":" (fs/path claude "skills"))]
          (testing "warning emitted for a built-in-covered entry"
            (is (str/includes? (str sw) "already shared by default")))
          (testing "no duplicate mount for the built-in entry"
            (is (= 1 (count (filter #{skills-mount} strs)))))))
      (finally
        (fs/delete-tree home)
        (fs/delete-tree tmp-state)))))

(deftest project-mode-first-run-creates-state-and-meta
  (let [home (seed-home [{:rel ["skills"] :dir? true}])
        tmp-state (str (fs/create-temp-dir))]
    (try
      (with-redefs [util/get-home (fn [] home)
                    util/state-dir (fn [] tmp-state)]
        (let [state {:with-claude true}
              cfg {:claude_isolation "project"}]
          (build-mounts state home cfg)
          (let [dot-claude (naming/claude-dot-claude-dir home)
                meta-file (naming/claude-meta-file home)]
            (testing "dot-claude dir created"
              (is (fs/exists? dot-claude)))
            (testing "meta.edn written with canonicalized project path and created-at"
              (is (fs/exists? meta-file))
              (let [m (edn/read-string (slurp meta-file))]
                (is (= (str (fs/canonicalize home)) (:project-path m)))
                (is (string? (:created-at m)))))
            (testing "second run does not rewrite meta.edn"
              (let [before (slurp meta-file)]
                (build-mounts state home cfg)
                (is (= before (slurp meta-file))))))))
      (finally
        (fs/delete-tree home)
        (fs/delete-tree tmp-state)))))

(deftest project-mode-distinct-projects-independent-state
  (let [home (seed-home [{:rel ["skills"] :dir? true}])
        tmp-state (str (fs/create-temp-dir))
        proj-a (str (fs/create-temp-dir))
        proj-b (str (fs/create-temp-dir))]
    (try
      (with-redefs [util/get-home (fn [] home)
                    util/state-dir (fn [] tmp-state)]
        (let [state {:with-claude true}
              cfg {:claude_isolation "project"}
              base (fn [proj]
                     (str (naming/claude-dot-claude-dir proj) ":" (fs/path home ".claude")))]
          (build-mounts state proj-a cfg)
          (build-mounts state proj-b cfg)
          (testing "each project gets its own dot-claude base mount"
            (is (not= (base proj-a) (base proj-b)))
            (is (some #{(base proj-a)} (mount-strings (vec (build-mounts state proj-a cfg)))))
            (is (some #{(base proj-b)} (mount-strings (vec (build-mounts state proj-b cfg))))))))
      (finally
        (fs/delete-tree home)
        (fs/delete-tree tmp-state)
        (fs/delete-tree proj-a)
        (fs/delete-tree proj-b)))))

(def ^:private allowlist-dirs
  ["skills" "agents" "commands" "hooks" "plugins" "projects"])

(deftest project-mode-bootstraps-missing-sources
  ;; Fresh HOME: only .claude/ + .claude.json exist, no allowlist entries.
  (let [home (seed-home [])
        tmp-state (str (fs/create-temp-dir))]
    (try
      (with-redefs [util/get-home (fn [] home)
                    util/state-dir (fn [] tmp-state)]
        (let [state {:with-claude true}
              cfg {:claude_isolation "project"}
              claude (fs/path home ".claude")]
          (build-mounts state home cfg)
          (testing "missing allowlist dirs pre-created on host"
            (doseq [d allowlist-dirs]
              (is (fs/directory? (fs/path claude d)) (str d " should be a directory"))))
          (testing "settings.json seeded with an empty JSON object"
            (let [f (str (fs/path claude "settings.json"))]
              (is (fs/regular-file? f))
              (is (str/includes? (slurp f) "{}"))))
          (testing "CLAUDE.md seeded empty"
            (let [f (str (fs/path claude "CLAUDE.md"))]
              (is (fs/regular-file? f))
              (is (= "" (slurp f)))))
          (testing ".credentials.json never seeded and never manufactured as a dir"
            (is (not (fs/exists? (fs/path claude ".credentials.json")))))
          (testing "history.jsonl (:never) not seeded when absent"
            (is (not (fs/exists? (fs/path claude "history.jsonl")))))))
      (finally
        (fs/delete-tree home)
        (fs/delete-tree tmp-state)))))

(deftest project-mode-present-sources-mounted-and-preserved
  ;; Fresh HOME with every allowlist source already present.
  (let [home (seed-home [{:rel ["skills"] :dir? true}
                         {:rel ["agents"] :dir? true}
                         {:rel ["commands"] :dir? true}
                         {:rel ["hooks"] :dir? true}
                         {:rel ["plugins"] :dir? true}
                         {:rel ["projects"] :dir? true}
                         {:rel ["settings.json"] :dir? false}
                         {:rel ["CLAUDE.md"] :dir? false}
                         {:rel ["history.jsonl"] :dir? false}
                         {:rel [".credentials.json"] :dir? false}])
        tmp-state (str (fs/create-temp-dir))]
    (try
      (with-redefs [util/get-home (fn [] home)
                    util/state-dir (fn [] tmp-state)]
        (let [state {:with-claude true}
              cfg {:claude_isolation "project"}
              claude (fs/path home ".claude")]
          ;; sentinel content proves seeding does not overwrite existing files
          (spit (str (fs/path claude "settings.json")) "{\"a\":1}")
          (spit (str (fs/path claude "CLAUDE.md")) "keep me")
          (let [strs (mount-strings (vec (build-mounts state home cfg)))]
            (testing "all allowlist sources mounted"
              (doseq [rel (concat allowlist-dirs
                                  ["settings.json" "CLAUDE.md" "history.jsonl" ".credentials.json"])]
                (is (some #{(str (fs/path claude rel) ":" (fs/path claude rel))} strs)
                    (str rel " should be mounted")))))
          (testing "existing config files are not overwritten"
            (is (= "{\"a\":1}" (slurp (str (fs/path claude "settings.json")))))
            (is (= "keep me" (slurp (str (fs/path claude "CLAUDE.md"))))))))
      (finally
        (fs/delete-tree home)
        (fs/delete-tree tmp-state)))))

(deftest project-mode-no-credentials-notice
  (let [home (seed-home [])
        tmp-state (str (fs/create-temp-dir))]
    (try
      (with-redefs [util/get-home (fn [] home)
                    util/state-dir (fn [] tmp-state)]
        (let [state {:with-claude true}
              cfg {:claude_isolation "project"}
              claude (fs/path home ".claude")
              sw (java.io.StringWriter.)
              strs (binding [*err* sw]
                     (mount-strings (vec (build-mounts state home cfg))))]
          (testing "visible notice emitted on stderr"
            (is (str/includes? (str sw) "credential")))
          (testing "no credentials mount emitted when absent"
            (is (not (some #{(str (fs/path claude ".credentials.json") ":"
                                  (fs/path claude ".credentials.json"))} strs))))))
      (finally
        (fs/delete-tree home)
        (fs/delete-tree tmp-state)))))

(deftest project-mode-promotes-project-local-credentials
  (let [home (seed-home [])
        tmp-state (str (fs/create-temp-dir))]
    (try
      (with-redefs [util/get-home (fn [] home)
                    util/state-dir (fn [] tmp-state)]
        (let [state {:with-claude true}
              cfg {:claude_isolation "project"}
              dot-claude (naming/claude-dot-claude-dir home)
              proj-creds (str (fs/path dot-claude ".credentials.json"))
              host-creds (str (fs/path home ".claude" ".credentials.json"))]
          ;; project state has a login; host does not
          (fs/create-dirs dot-claude)
          (spit proj-creds "{\"token\":\"x\"}")
          (is (not (fs/exists? host-creds)))
          (let [strs (mount-strings (vec (build-mounts state home cfg)))]
            (testing "creds promoted up to host before mounting"
              (is (fs/exists? host-creds))
              (is (= "{\"token\":\"x\"}" (slurp host-creds))))
            (testing "creds mount now present"
              (is (some #{(str host-creds ":" host-creds)} strs))))
          (testing "promotion is idempotent on a second run"
            (let [strs2 (mount-strings (vec (build-mounts state home cfg)))]
              (is (= "{\"token\":\"x\"}" (slurp host-creds)))
              (is (some #{(str host-creds ":" host-creds)} strs2))))))
      (finally
        (fs/delete-tree home)
        (fs/delete-tree tmp-state)))))

;; ---------------------------------------------------------------------------
;; Harness alias env args — the in-sandbox shell aliases
;;
;; Criterion: the alias argv and the container launch argv come from the same
;; interpreter and are byte-identical for the same inputs. Both sides below are
;; production code paths (docker.run's alias builder vs aishell.run's launch
;; argv), not two calls into the interpreter.
;; ---------------------------------------------------------------------------

(def ^:private alias-env-args #'run/build-harness-alias-env-args)
(def ^:private container-command #'aishell.run/container-command)

(def ^:private all-enabled
  {:with-claude true :with-opencode true :with-codex true
   :with-copilot true :with-gemini true :with-pi true :with-omp true})

(defn- alias-value
  "The value of HARNESS_ALIAS_<NAME> in a -e flag vector, or nil when absent."
  [env-args harness-name]
  (let [prefix (str "HARNESS_ALIAS_" (str/upper-case harness-name) "=")]
    (some #(when (str/starts-with? % prefix) (subs % (count prefix))) env-args)))

(defn- alias-names [env-args]
  (->> env-args
       (keep #(second (re-find #"^HARNESS_ALIAS_([A-Z]+)=" %)))
       (mapv str/lower-case)))

(def ^:private every-harness-args
  {:claude ["--model" "opus"]
   :opencode ["-m" "gpt"]
   :codex ["--full-auto"]
   :copilot ["--model" "gpt-5"]
   :gemini ["-d"]
   :pi ["-y"]
   :omp ["--model" "sonnet"]})

(deftest alias-argv-is-byte-identical-to-container-launch-argv
  (doseq [skip? [true false]
          [label harness-args] [["no config defaults" {}]
                                ["config defaults for every harness" every-harness-args]]]
    (testing (str "skip-permissions " skip? " with " label)
      (with-redefs [harness/skip-permissions? (constantly skip?)]
        (let [env-args (alias-env-args {:harness_args harness-args} all-enabled)
              emitted (alias-names env-args)]
          (is (seq emitted))
          (doseq [harness-name emitted]
            (is (= (str/join " " (container-command harness-name
                                                    (get harness-args (keyword harness-name) [])
                                                    []
                                                    skip?))
                   (alias-value env-args harness-name))
                (str harness-name " alias must match its launch argv"))))))))

(deftest alias-emission-rules-come-from-descriptor-capabilities
  (with-redefs [harness/skip-permissions? (constantly true)]
    (testing "claude, codex and copilot always emit; the rest only when they carry args"
      (is (= ["claude" "codex" "copilot"] (alias-names (alias-env-args {} all-enabled)))))
    (testing "config defaults make the remaining harnesses emit"
      (is (= ["claude" "opencode" "codex" "copilot" "gemini" "pi" "omp"]
             (alias-names (alias-env-args {:harness_args every-harness-args} all-enabled)))))
    (testing "a disabled harness emits nothing"
      (is (empty? (alias-names (alias-env-args {:harness_args every-harness-args} {})))))))

(deftest copilot-alias-never-carries-a-permissions-bypass
  (testing "the in-Sandbox alias is bare even with skip-permissions on"
    (with-redefs [harness/skip-permissions? (constantly true)]
      (let [value (alias-value (alias-env-args {} all-enabled) "copilot")]
        (is (= "copilot" value))
        (is (not-any? #{"--allow-all" "--yolo"} (str/split value #" ")))))))

(deftest skip-permissions-affects-both-paths-identically
  (testing "claude's alias carries the flag exactly when the launch argv does"
    (doseq [skip? [true false]]
      (with-redefs [harness/skip-permissions? (constantly skip?)]
        (let [v (alias-value (alias-env-args {} all-enabled) "claude")]
          (is (= skip? (str/includes? v "--dangerously-skip-permissions")))
          (is (= (str/join " " (container-command "claude" [] [] skip?)) v)))))))

(deftest shell-mode-has-no-harness-descriptor
  (testing "no harness command falls back to bash"
    (is (= ["/bin/bash"] (container-command nil [] [] true)))
    (is (= ["/bin/bash"] (container-command "bash" [] [] true)))))

;; ---------------------------------------------------------------------------
;; Harness config-path and env-passthrough tables
;;
;; These tables are what the mount and env builders consume, and what
;; `aishell info` prints. The literals below are the pre-registry values: they
;; pin that deriving them from descriptors changed nothing a caller can see.
;; ---------------------------------------------------------------------------

(def ^:private config-files @#'run/harness-config-files)
(def ^:private api-env-args #'run/build-api-env-args)
(def ^:private runtime-env-args #'run/build-harness-runtime-env-args)

(deftest harness-config-dirs-table
  (testing "home-relative config paths per harness state key, in registry order"
    (is (= {:with-claude   [[".claude"] [".claude.json"]]
            :with-opencode [[".config" "opencode"] [".local" "share" "opencode"]]
            :with-codex    [[".codex"]]
            :with-copilot  [[".copilot"]]
            :with-gemini   [[".gemini"]]
            :with-pi       [[".pi"]]
            :with-omp      [[".omp"]]}
           run/harness-config-dirs))))

(def ^:private synthetic-ids
  "Ten harness ids: enough that a map keyed by their state keys is a hash-map,
   whose iteration order no longer follows insertion order."
  (mapv #(str "h" %) (range 10)))

(def ^:private synthetic-registry
  (mapv (fn [id]
          {:id id
           :state-key (keyword (str "with-" id))
           :config-paths [{:path [(str "." id)] :type :dir}]
           :env-passthrough [(str (str/upper-case id) "_KEY")]})
        synthetic-ids))

(def ^:private synthetic-state
  (into {} (map (fn [{:keys [state-key]}] [state-key true])) synthetic-registry))

(deftest config-mounts-follow-registry-order
  (let [home (str (fs/create-temp-dir))]
    (doseq [id synthetic-ids] (fs/create-dirs (fs/path home (str "." id))))
    (try
      (with-redefs [harness/registry synthetic-registry
                    util/get-home (fn [] home)]
        (let [expected (map #(let [p (str (fs/path home (str "." %)))] (str p ":" p))
                            synthetic-ids)]
          (is (= expected (mount-strings (build-mounts synthetic-state home {}))))))
      (finally
        (fs/delete-tree home)))))

(deftest api-key-args-follow-registry-order
  (with-redefs [harness/registry synthetic-registry
                run/host-env (fn [var] (str var "-value"))]
    (is (= (mapcat #(let [var (str (str/upper-case %) "_KEY")]
                      ["-e" (str var "=" var "-value")])
                   synthetic-ids)
           (api-env-args synthetic-state)))))

(deftest harness-config-files-are-seeded-not-created-as-dirs
  (testing "only .claude.json is a file entry"
    (is (= #{[".claude.json"]} config-files))))

(deftest harness-api-keys-table
  (testing "passthrough env vars per harness state key"
    (is (= {:with-claude   ["ANTHROPIC_API_KEY"]
            :with-opencode ["OPENAI_API_KEY" "ANTHROPIC_API_KEY" "GROQ_API_KEY"
                            "OPENCODE_API_KEY" "AZURE_OPENAI_API_KEY" "AZURE_OPENAI_ENDPOINT"]
            :with-codex    ["OPENAI_API_KEY" "CODEX_API_KEY"]
            :with-copilot  ["COPILOT_GITHUB_TOKEN" "COPILOT_GH_HOST"]
            :with-gemini   ["GEMINI_API_KEY" "GOOGLE_API_KEY" "GOOGLE_CLOUD_PROJECT"
                            "GOOGLE_CLOUD_LOCATION" "GOOGLE_APPLICATION_CREDENTIALS"]
            :with-pi       ["PI_CODING_AGENT_DIR"]
            :with-omp      ["OMP_PROFILE" "ANTHROPIC_API_KEY" "OPENAI_API_KEY"
                            "GEMINI_API_KEY" "OPENROUTER_API_KEY" "GROQ_API_KEY"
                            "XAI_API_KEY" "MISTRAL_API_KEY" "DEEPSEEK_API_KEY"]}
           run/harness-api-keys))))

(deftest copilot-does-not-forward-broad-github-or-cache-vars
  (testing "GH_TOKEN, GITHUB_TOKEN, COPILOT_HOME, and COPILOT_CACHE_HOME are not declared for Copilot"
    (is (not-any? #{"GH_TOKEN" "GITHUB_TOKEN" "COPILOT_HOME" "COPILOT_CACHE_HOME"}
                  (get run/harness-api-keys :with-copilot))))
  (testing "even when all of them are set on the host, only the two declared vars reach the -e args"
    (let [host {"GH_TOKEN" "gh" "GITHUB_TOKEN" "github"
                "COPILOT_HOME" "/elsewhere" "COPILOT_CACHE_HOME" "/cache"
                "COPILOT_GITHUB_TOKEN" "scoped" "COPILOT_GH_HOST" "ghe.example.com"}]
      (with-redefs [run/host-env host]
        (is (= ["-e" "COPILOT_GITHUB_TOKEN=scoped" "-e" "COPILOT_GH_HOST=ghe.example.com"]
               (api-env-args {:with-copilot true})))))))

(deftest omp-alone-leaves-pi-directory-vars-on-the-host
  (let [host {"PI_CODING_AGENT_DIR" "/host/pi" "PI_CONFIG_DIR" ".pi-alt"
              "OMP_PROFILE" "work" "OPENROUTER_API_KEY" "or"}]
    (with-redefs [run/host-env host]
      (testing "with only omp enabled, pi's directory variables stay out of the Sandbox"
        (is (= ["-e" "OMP_PROFILE=work" "-e" "OPENROUTER_API_KEY=or"]
               (api-env-args {:with-omp true}))))
      (testing "enabling pi as well brings PI_CODING_AGENT_DIR in for the whole Sandbox"
        (is (some #{"PI_CODING_AGENT_DIR=/host/pi"}
                  (api-env-args {:with-omp true :with-pi true})))))))

(deftest api-env-args-only-cover-enabled-harnesses
  (testing "a disabled harness contributes no -e flag even when the var is set"
    (with-redefs [run/host-env {"GEMINI_API_KEY" "g" "ANTHROPIC_API_KEY" "a"}]
      (is (= ["-e" "ANTHROPIC_API_KEY=a"] (api-env-args {:with-claude true})))))
  (testing "no harness enabled means no API env args at all"
    (is (empty? (api-env-args {}))))
  (testing "unset vars are never forwarded as empty values"
    (is (not-any? #(str/ends-with? (str %) "=") (api-env-args {:with-claude true
                                                               :with-opencode true
                                                               :with-codex true
                                                               :with-gemini true
                                                               :with-pi true})))))

(defn- sandbox-args
  "The `docker run` argv for a sandbox built from `config` and setup `state`,
   against a throwaway HOME. The rest of the launch inputs are fixed: these
   tests read the argv for one variable, not for the container it describes."
  [{:keys [config state]}]
  (let [home (str (fs/create-temp-dir))
        project (str (fs/create-temp-dir))]
    (try
      (with-redefs [util/get-home (constantly home)]
        (run/build-docker-args
         {:project-dir project
          :image-tag "aishell:test"
          :config config
          :state state
          :git-identity {}
          :skip-pre-start false}))
      (finally
        (fs/delete-tree home)
        (fs/delete-tree project)))))

(defn- exec-args
  "The `docker run` argv for a one-off `exec` built from `config` and setup
   `state`, against a throwaway HOME."
  [{:keys [config state]}]
  (let [home (str (fs/create-temp-dir))
        project (str (fs/create-temp-dir))]
    (try
      (with-redefs [util/get-home (constantly home)]
        (run/build-docker-args-for-exec
         {:project-dir project
          :image-tag "aishell:test"
          :config config
          :state state
          :git-identity {}
          :tty? false}))
      (finally
        (fs/delete-tree home)
        (fs/delete-tree project)))))

(deftest exec-skips-pre-start-and-harness-aliases-a-launch-keeps
  (let [inputs {:config {:pre_start "echo hi"} :state {:with-claude true}}
        aliases (fn [argv] (filter #(str/starts-with? (str %) "HARNESS_ALIAS_") argv))]
    (testing "a launch passes pre_start and the harness aliases"
      (let [argv (sandbox-args inputs)]
        (is (some #{"PRE_START=echo hi"} argv))
        (is (seq (aliases argv)))))
    (testing "exec clears pre_start and passes no harness alias"
      (let [argv (exec-args inputs)]
        (is (some #{"PRE_START="} argv))
        (is (not-any? #{"PRE_START=echo hi"} argv))
        (is (empty? (aliases argv)))))))

(deftest vscode-server-volume-follows-the-vscode-option
  (let [mounts-volume? (fn [argv]
                         (some #(re-matches #"aishell-vscode-server:.*/\.vscode-server" (str %)) argv))]
    (testing "with VSCode enabled, a Sandbox mounts the volume at the VSCode server path"
      (is (mounts-volume? (sandbox-args {:state {:with-vscode true}}))))
    (testing "with VSCode disabled, it does not"
      (is (not (mounts-volume? (sandbox-args {:state {:with-claude true}})))))
    (testing "a one-off exec is not a Sandbox and does not mount it"
      (is (not (mounts-volume? (exec-args {:state {:with-vscode true}})))))))

(def ^:private runtime-env-descriptors
  "Every descriptor declaring fixed runtime environment policy. The behavioral
   tests below iterate over this rather than naming one harness, so a descriptor
   that gains or loses `:runtime-env` is exercised without editing them. The
   inventory test still names what the registry holds today."
  (filterv :runtime-env harness/registry))

(deftest runtime-env-is-declared-by-claude-copilot-and-pi
  (testing "the harnesses aishell owns updates for declare it on their descriptors"
    (is (= {:claude  {"DISABLE_AUTOUPDATER" "1"}
            :copilot {"COPILOT_AUTO_UPDATE" "false"}
            :pi      {"PI_SKIP_VERSION_CHECK" "true"}}
           (into {} (map (juxt :id :runtime-env)) runtime-env-descriptors)))))

(deftest runtime-env-args-are-scoped-to-enabled-harnesses
  (testing "no harness enabled means no fixed runtime env at all"
    (is (= [] (runtime-env-args {})))
    (is (= [] (runtime-env-args (into {} (map (juxt :state-key (constantly false)))
                                      harness/registry)))))
  (doseq [{:keys [id state-key runtime-env]} runtime-env-descriptors]
    (testing (str "only enabled " id " contributes its fixed policy")
      (is (= (into [] (mapcat (fn [[var value]] ["-e" (str var "=" value)])) runtime-env)
             (runtime-env-args {state-key true}))))))

(deftest runtime-env-beats-config-env-and-loses-to-docker-args
  (doseq [{:keys [id state-key runtime-env]} runtime-env-descriptors
          [var value] runtime-env]
    (testing (str "for " id ": ordinary config env comes first, fixed policy next, docker_args last")
      (let [args (sandbox-args {:config {:env [(str var "=config")]
                                         :docker_args ["-e" (str var "=escape")]}
                                :state {state-key true}})
            index (fn [v] (.indexOf args v))]
        (is (< (index (str var "=config"))
               (index (str var "=" value))
               (index (str var "=escape"))
               (index "aishell:test")))))))

(deftest disable-autoupdater-is-claude-scoped
  (testing "the variable rides on Claude's descriptor, not on every sandbox"
    (is (not (some #{"DISABLE_AUTOUPDATER=1"}
                   (sandbox-args {:config {} :state {:with-codex true}}))))
    (is (some #{"DISABLE_AUTOUPDATER=1"}
              (sandbox-args {:config {} :state {:with-claude true}})))))

(deftest copilot-config-mount-is-scoped-to-enabled-state
  (let [home (str (fs/create-temp-dir))]
    (try
      (with-redefs [util/get-home (constantly home)]
        (is (empty? (build-mounts {} home {})))
        (is (= ["-v" (str (fs/path home ".copilot") ":" (fs/path home ".copilot"))]
               (vec (build-mounts {:with-copilot true} home {}))))
        (is (fs/directory? (fs/path home ".copilot"))))
      (finally
        (fs/delete-tree home)))))

(deftest credentials-file-mount-follows-the-gemini-descriptor
  (testing "the mount is offered only when Gemini is enabled"
    (is (nil? (run/build-gcp-credentials-mount {:with-claude true}))))
  (testing "Gemini declares which env var names its credentials file"
    (is (= "GOOGLE_APPLICATION_CREDENTIALS"
           (:credentials-file-env (harness/descriptor :gemini)))))
  (testing "no other harness declares one"
    (is (= [:gemini]
           (mapv :id (filter :credentials-file-env harness/registry))))))


(def ^:private port-args #'run/build-port-args)

(defn- ports-outcome
  "Run build-port-args against an env map. Returns {:args [...]} or, when it
   calls output/error, {:error <stderr text>}."
  [ports env]
  (let [err (java.io.StringWriter.)]
    (binding [*err* err]
      (with-redefs [output/exit! (fn [_] (throw (ex-info "exit" {})))]
        (try
          {:args (vec (port-args ports env))}
          (catch clojure.lang.ExceptionInfo _
            {:error (str err)}))))))

(deftest ports-expand-host-environment-variables
  (testing "a set variable supplies the host port"
    (is (= {:args ["-p" "127.0.0.1:9999:7888"]}
           (ports-outcome ["127.0.0.1:${WEAVE_NREPL_PORT:-7888}:7888"]
                          {"WEAVE_NREPL_PORT" "9999"}))))
  (testing "an unset variable falls back to its default"
    (is (= {:args ["-p" "127.0.0.1:7888:7888"]}
           (ports-outcome ["127.0.0.1:${WEAVE_NREPL_PORT:-7888}:7888"] {}))))
  (testing "an empty variable falls back to its default"
    (is (= {:args ["-p" "127.0.0.1:7888:7888"]}
           (ports-outcome ["127.0.0.1:${WEAVE_NREPL_PORT:-7888}:7888"]
                          {"WEAVE_NREPL_PORT" ""})))))

(deftest ports-refuse-an-unresolvable-variable
  (testing "unset with no default names the variable and the entry"
    (let [{:keys [error]} (ports-outcome ["127.0.0.1:${WEAVE_NREPL_PORT}:7888"] {})]
      (is (str/includes? error "${WEAVE_NREPL_PORT}"))
      (is (str/includes? error "\"127.0.0.1:${WEAVE_NREPL_PORT}:7888\""))
      (is (str/includes? error "not set"))))
  (testing "set but empty with no default is refused too"
    (let [{:keys [error]} (ports-outcome ["${P}:7888"] {"P" ""})]
      (is (str/includes? error "${P}"))
      (is (str/includes? error "empty"))))
  (testing "an empty default is refused"
    (let [{:keys [error]} (ports-outcome ["${P:-}:7888"] {})]
      (is (str/includes? error "${P:-}"))
      (is (str/includes? error "empty default")))))

(deftest ports-check-the-format-after-substitution
  (let [{:keys [error]} (ports-outcome ["127.0.0.1:${WEAVE_NREPL_PORT:-7888}:7888"]
                                       {"WEAVE_NREPL_PORT" "abc"})]
    (is (str/includes? error "Invalid port mapping: 127.0.0.1:abc:7888"))
    (is (str/includes? error "(from \"127.0.0.1:${WEAVE_NREPL_PORT:-7888}:7888\")"))
    (is (str/includes? error "Expected format:"))))

(deftest ports-without-variables-behave-as-before
  (testing "a valid plain entry passes through unchanged"
    (is (= {:args ["-p" "8080:80" "-p" "127.0.0.1:5432:5432" "-p" "9090:9090/udp"]}
           (ports-outcome ["8080:80" "127.0.0.1:5432:5432" "9090:9090/udp"] {}))))
  (testing "an invalid plain entry gets today's message, with no (from ...) line"
    (let [{:keys [error]} (ports-outcome ["abc:80"] {})]
      (is (str/includes? error "Invalid port mapping: abc:80\nExpected format:"))
      (is (not (str/includes? error "(from"))))))


;; Resource caps (resources.memory, resources.cpus)

(defn- resources-outcome
  "Build the argv for both a launch and an exec from `resources`. Returns
   {:sandbox argv :exec argv} or, when the build calls output/error,
   {:error <stderr text>}."
  [resources]
  (let [err (java.io.StringWriter.)]
    (binding [*err* err]
      (with-redefs [output/exit! (fn [_] (throw (ex-info "exit" {})))]
        (try
          {:sandbox (sandbox-args {:config {:resources resources} :state {}})
           :exec (exec-args {:config {:resources resources} :state {}})}
          (catch clojure.lang.ExceptionInfo _
            {:error (str err)}))))))

(defn- flag-value [argv flag]
  (second (drop-while #(not= flag %) argv)))

(deftest resources-reach-both-launch-paths
  (doseq [path [:sandbox :exec]]
    (testing (str "memory and cpus become flags on the " (name path) " argv")
      (let [argv (path (resources-outcome {:memory "8g" :cpus "4"}))]
        (is (= "8g" (flag-value argv "--memory")))
        (is (= "4" (flag-value argv "--cpus")))))
    (testing (str "absent keys add no flag on the " (name path) " argv")
      (doseq [resources [nil {}]]
        (let [argv (path (resources-outcome resources))]
          (is (not-any? #{"--memory" "--cpus"} argv))))
      (let [argv (path (resources-outcome {:cpus 2}))]
        (is (= "2" (flag-value argv "--cpus")))
        (is (not-any? #{"--memory"} argv))))))

(deftest resources-keep-the-value-as-written
  (testing "accepted memory forms pass through unchanged"
    (doseq [v ["512m" "8g" "1.5G" "8GiB" "8gb" "8K" "1073741824" "512 MB" "100b" "1.5"]]
      (is (= v (flag-value (:sandbox (resources-outcome {:memory v})) "--memory")) v)))
  (testing "a YAML integer memory value is read as bytes and stringified"
    (is (= "1073741824" (flag-value (:sandbox (resources-outcome {:memory 1073741824})) "--memory"))))
  (testing "accepted cpus forms pass through, numbers stringified"
    (doseq [[v expected] [["4" "4"] [4 "4"] [1.5 "1.5"] ["0.5" "0.5"] ["1.000000001" "1.000000001"]]]
      (is (= expected (flag-value (:sandbox (resources-outcome {:cpus v})) "--cpus")) (pr-str v)))))

(deftest resources-come-before-docker-args
  (let [argv (sandbox-args {:config {:resources {:memory "8g"} :docker_args ["--memory=2g"]}
                            :state {}})
        idx (fn [s] (.indexOf ^java.util.List argv s))]
    (is (< (idx "--memory") (idx "--memory=2g")))))

(deftest invalid-resources-exit-naming-the-key
  (testing "refused memory values name resources.memory and the value"
    (doseq [v ["8x" "0" "0g" "-1" "abc" "" 0 -1 "8i" "8Gi" "8bb" "8bi" "8gbb" "0.5" "0.9b"]]
      (let [{:keys [error]} (resources-outcome {:memory v})]
        (is (some? error) (pr-str v))
        (is (str/includes? (str error) "resources.memory") (pr-str v))
        (is (str/includes? (str error) (str v)) (pr-str v)))))
  (testing "refused cpus values name resources.cpus and the value"
    (doseq [v ["8x" "0" 0 "-1" -1 "abc" "1.0000000001" 0.0]]
      (let [{:keys [error]} (resources-outcome {:cpus v})]
        (is (some? error) (pr-str v))
        (is (str/includes? (str error) "resources.cpus") (pr-str v))
        (is (str/includes? (str error) (str v)) (pr-str v))))))
