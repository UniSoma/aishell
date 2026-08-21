(ns aishell.harness
  "The harness registry: the ordered, closed collection of harness descriptors
   from which every harness-dependent behavior in aishell derives.

   A descriptor is pure data — no functions. It records one Harness's identity,
   canonical label, state and version keys, capabilities, install source,
   config paths and passthrough environment variables. Capabilities are
   presence-based where that reads naturally (no `:alias` key means no shell
   alias, no `:config-paths` means nothing to mount) and explicit booleans
   where absence would be ambiguous (`:interactive?`, `:pre-start?`,
   `:volume-participant?`, `:accepts-config-defaults?`).

   Everything else in this namespace is a derivation over the registry:
   capability filters, and the single argv interpreter that both the container
   launch path and the shell-alias builder call.")

;; ---------------------------------------------------------------------------
;; Descriptor schema
;;
;;   :id                       identity keyword (`:claude`) — also the hash key
;;   :label                    canonical long label, used in all user-facing output
;;   :subcommand               `aishell <subcommand>` and the launch command name
;;   :state-key                setup-state boolean, always `:with-<id>`
;;   :version-key              setup-state version pin, always `:<id>-version`;
;;                             absent when the harness carries no version
;;   :interactive?             runs in an interactive shell/TTY session
;;   :pre-start?               the project's pre_start hook runs before it
;;   :accepts-config-defaults? `harness_args` defaults from config reach its argv
;;   :volume-participant?      installed into the shared harness volume (and so
;;                             part of the volume hash)
;;   :fixed-args               argv flags always inserted before user args
;;   :skip-permissions-flag    flag added when skip-permissions is in effect
;;   :alias                    presence = emits a shell alias in the sandbox;
;;                             {:always? bool} — false means "only when it has args"
;;   :install                  {:kind :npm|:binary-tarball|:image-baked, …}
;;   :config-paths             [{:path [".claude"] :type :dir|:file} …], home-relative
;;   :credentials-file-env     env var naming a host credentials file to mount
;;                             read-only; absent when the harness has none
;;   :env-passthrough          host env vars forwarded when set, in declared order
;; ---------------------------------------------------------------------------

(def registry
  "The six harnesses, in user-facing display order."
  [{:id :claude
    :label "Claude Code"
    :subcommand "claude"
    :state-key :with-claude
    :version-key :claude-version
    :interactive? true
    :pre-start? true
    :accepts-config-defaults? true
    :volume-participant? true
    :skip-permissions-flag "--dangerously-skip-permissions"
    :alias {:always? true}
    :install {:kind :npm :package "@anthropic-ai/claude-code"}
    :config-paths [{:path [".claude"] :type :dir}
                   {:path [".claude.json"] :type :file}]
    :env-passthrough ["ANTHROPIC_API_KEY"]}

   {:id :opencode
    :label "OpenCode"
    :subcommand "opencode"
    :state-key :with-opencode
    :version-key :opencode-version
    :interactive? true
    :pre-start? true
    :accepts-config-defaults? true
    :volume-participant? true
    :alias {:always? false}
    :install {:kind :binary-tarball
              :latest-url "https://github.com/anomalyco/opencode/releases/latest/download/opencode-linux-x64.tar.gz"
              :versioned-url-template "https://github.com/anomalyco/opencode/releases/download/v%s/opencode-linux-x64.tar.gz"
              :install-dir "/tools/bin"}
    :config-paths [{:path [".config" "opencode"] :type :dir}
                   {:path [".local" "share" "opencode"] :type :dir}]
    :env-passthrough ["OPENAI_API_KEY" "ANTHROPIC_API_KEY" "GROQ_API_KEY"
                      "OPENCODE_API_KEY" "AZURE_OPENAI_API_KEY" "AZURE_OPENAI_ENDPOINT"]}

   {:id :codex
    :label "Codex CLI"
    :subcommand "codex"
    :state-key :with-codex
    :version-key :codex-version
    :interactive? true
    :pre-start? true
    :accepts-config-defaults? true
    :volume-participant? true
    :fixed-args ["-c" "check_for_update_on_startup=false"]
    :alias {:always? true}
    :install {:kind :npm :package "@openai/codex"}
    :config-paths [{:path [".codex"] :type :dir}]
    :env-passthrough ["OPENAI_API_KEY" "CODEX_API_KEY"]}

   {:id :gemini
    :label "Gemini CLI"
    :subcommand "gemini"
    :state-key :with-gemini
    :version-key :gemini-version
    :interactive? true
    :pre-start? true
    :accepts-config-defaults? true
    :volume-participant? true
    :alias {:always? false}
    :install {:kind :npm :package "@google/gemini-cli"}
    :config-paths [{:path [".gemini"] :type :dir}]
    :credentials-file-env "GOOGLE_APPLICATION_CREDENTIALS"
    :env-passthrough ["GEMINI_API_KEY" "GOOGLE_API_KEY" "GOOGLE_CLOUD_PROJECT"
                      "GOOGLE_CLOUD_LOCATION" "GOOGLE_APPLICATION_CREDENTIALS"]}

   {:id :pi
    :label "Pi coding agent"
    :subcommand "pi"
    :state-key :with-pi
    :version-key :pi-version
    :interactive? true
    :pre-start? true
    :accepts-config-defaults? true
    :volume-participant? true
    :alias {:always? false}
    :install {:kind :npm :package "@earendil-works/pi-coding-agent"}
    :config-paths [{:path [".pi"] :type :dir}]
    :env-passthrough ["PI_CODING_AGENT_DIR" "PI_SKIP_VERSION_CHECK"]}

   {:id :gitleaks
    :label "Gitleaks"
    :subcommand "gitleaks"
    :state-key :with-gitleaks
    :interactive? false
    :pre-start? false
    :accepts-config-defaults? false
    :volume-participant? false
    :install {:kind :image-baked :version "8.30.1"}}])

(defn descriptor
  "The descriptor for `id`, or nil when the registry has no such harness."
  [id]
  (first (filter #(= id (:id %)) registry)))

;; ---------------------------------------------------------------------------
;; Capability filters
;; ---------------------------------------------------------------------------

(defn subcommands
  "Every harness subcommand, in display order."
  []
  (mapv :subcommand registry))

(defn suggestion-terms
  "Harness contributions to the typo-suggestion vocabulary."
  []
  (mapv :subcommand registry))

(defn volume-participants
  "Descriptors installed into the shared harness volume — the harnesses whose
   enabled state and pinned version feed the volume hash."
  []
  (filterv :volume-participant? registry))

(defn volume-harnesses-enabled?
  "Whether `state` enables any harness that lives in the shared harness volume.
   Gates volume creation, population and repopulation."
  [state]
  (boolean (some #(get state (:state-key %)) (volume-participants))))

(defn volume-config
  "Canonical, hash-bearing description of the harness volume `state` asks for:
   a vector of `[id version]` pairs for every enabled volume participant,
   sorted by id so registry (display) order can never affect it. An unpinned
   harness carries the version \"latest\"."
  [state]
  (->> (volume-participants)
       (filter #(get state (:state-key %)))
       (map (fn [{:keys [id version-key]}]
              [id (or (get state version-key) "latest")]))
       (sort-by first)
       vec))

(defn alias-emitters
  "Descriptors that get a shell alias inside the sandbox. Those with
   `[:alias :always?]` false emit only when they carry arguments."
  []
  (filterv :alias registry))

;; ---------------------------------------------------------------------------
;; Skip-permissions override
;; ---------------------------------------------------------------------------

(def skip-permissions-env-var
  "Host env var that overrides the skip-permissions default."
  "AISHELL_SKIP_PERMISSIONS")

(defn skip-permissions?
  "Whether skip-permissions is in effect, given the raw value of
   AISHELL_SKIP_PERMISSIONS. On by default; only the literal \"false\" opts out."
  [env-value]
  (not= "false" env-value))

;; ---------------------------------------------------------------------------
;; The argv interpreter
;; ---------------------------------------------------------------------------

(defn launch-argv
  "Final launch argv for `descriptor` under the given runtime inputs:

     :skip-permissions?  whether the skip-permissions override is in effect
     :default-args       `harness_args` defaults from config
     :cli-args           arguments the user passed on the command line

   Config defaults precede CLI args so that CLI args win by position. A harness
   with `:accepts-config-defaults?` false (gitleaks) takes CLI args only."
  [descriptor {:keys [skip-permissions? default-args cli-args]}]
  (let [{:keys [subcommand fixed-args skip-permissions-flag accepts-config-defaults?]} descriptor
        prefix (cond-> [subcommand]
                 (and skip-permissions? skip-permissions-flag) (conj skip-permissions-flag)
                 (seq fixed-args) (into fixed-args))
        user-args (if accepts-config-defaults?
                    (concat default-args cli-args)
                    cli-args)]
    (into prefix user-args)))
