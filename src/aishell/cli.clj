(ns aishell.cli
  (:require [babashka.cli :as cli]
            [babashka.fs :as fs]
            [cheshire.core :as json]
            [clojure.string :as str]
            [clojure.pprint :as pp]
            [aishell.docker :as docker]
            [aishell.docker.base :as base]
            [aishell.docker.bootstrap :as bootstrap]
            [aishell.docker.build :as build]
            [aishell.docker.hash :as hash]
            [aishell.docker.naming :as naming]
            [aishell.docker.templates :as templates]
            [aishell.docker.volume :as vol]
            [aishell.output :as output]
            [aishell.run :as run]
            [aishell.check :as check]
            [aishell.state :as state]
            [aishell.config :as config]
            [aishell.attach :as attach]
            [aishell.attach.parse :as attach-parse]
            [aishell.vscode :as vscode]
            [aishell.upgrade :as upgrade]
            [aishell.info :as info]
            [aishell.pi :as pi]
            [aishell.util :as util]
            [aishell.update-check :as update-check]))

(def version "3.21.0")

(defn print-version []
  (println (str "aishell " version)))

(def global-spec
  {:help    {:alias :h :coerce :boolean :desc "Show help"}
   :version {:alias :v :coerce :boolean :desc "Show version"}
   :json    {:coerce :boolean
             :desc "Emit JSON for: ps, --version"}})

;; Version validation patterns
(def semver-pattern
  #"^\d+\.\d+\.\d+(-[a-zA-Z0-9.-]+)?(\+[a-zA-Z0-9.-]+)?$")

(def dangerous-chars #"[;&|`$(){}\[\]<>!\\]")

(defn version-error-message
  "Return a version validation error string, or nil when valid."
  [version harness-name]
  (when (and version (not= version "true") (not= version "latest"))
    (cond
      (re-find dangerous-chars version)
      (str "Invalid " harness-name " version: contains shell metacharacters")

      (not (re-matches semver-pattern version))
      (str "Invalid " harness-name " version format: " version
           "\nExpected: X.Y.Z or X.Y.Z-prerelease (e.g., 2.0.22, 1.0.0-beta.1)"))))

(defn validate-version
  "Validate version string. Returns nil on success, exits with error on failure."
  [version harness-name]
  (when-let [msg (version-error-message version harness-name)]
    (output/error msg)))

(defn parse-with-flag
  "Parse --with-X flag value.
   nil -> {:enabled? false}
   true or 'true' -> {:enabled? true} (flag without value)
   'latest' -> {:enabled? true}
   version -> {:enabled? true :version version}"
  [value]
  (cond
    (nil? value) {:enabled? false}
    (true? value) {:enabled? true}  ; boolean true from CLI
    (= value "true") {:enabled? true}
    (= value "latest") {:enabled? true}
    :else {:enabled? true :version (str value)}))

(def unisoma-models
  "Hard-coded whitelist of model names for UniSoma OpenCode provider."
  ["gpt-5.2"
   "gpt-5.3-codex"
   "gpt-5.4"
   "claude-haiku-4-5"
   "claude-sonnet-4-6"
   "claude-opus-4-6"
   "gemini-3-flash"
   "gemini-3.1-pro"])

(defn- opencode-config-path
  "Path to OpenCode config: ~/.config/opencode/opencode.json"
  []
  (str (fs/path (fs/home) ".config" "opencode" "opencode.json")))

(defn- manage-opencode-whitelist!
  "Manage the OpenCode model whitelist for UniSoma users.
   - If :unisoma is true in new-state: upsert provider.opencode.whitelist
   - If :unisoma was true in prev-state but false in new-state: remove whitelist
   - Otherwise: no-op"
  [new-state prev-state]
  (let [new-unisoma? (:unisoma new-state false)
        prev-unisoma? (:unisoma prev-state false)
        config-path (opencode-config-path)]
    (cond
      ;; Upsert whitelist
      new-unisoma?
      (let [config-dir (str (fs/parent config-path))
            existing (when (fs/exists? config-path)
                       (json/parse-string (slurp config-path)))
            config (assoc-in (or existing {"$schema" "https://opencode.ai/config.json"})
                             ["provider" "opencode" "whitelist"] unisoma-models)]
        (fs/create-dirs config-dir)
        (spit config-path (json/generate-string config {:pretty true})))

      ;; Remove whitelist (was UniSoma, now isn't)
      (and prev-unisoma? (not new-unisoma?))
      (when (fs/exists? config-path)
        (let [config (json/parse-string (slurp config-path))
              config (update-in config ["provider" "opencode"] dissoc "whitelist")]
          (spit config-path (json/generate-string config {:pretty true})))))))

;; Setup subcommand spec
;; Note: with-claude/with-opencode don't use :coerce because babashka.cli
;; returns boolean true for flags without values, which can't be coerced to string.
;; parse-with-flag handles both boolean true and string values.
(def setup-spec
  {:with-claude   {:desc "Include Claude Code (optional: =VERSION)"}
   :with-opencode {:desc "Include OpenCode (optional: =VERSION)"}
   :with-codex    {:desc "Include Codex CLI (optional: =VERSION)"}
   :with-gemini   {:desc "Include Gemini CLI (optional: =VERSION)"}
   :with-pi       {:desc "Include Pi coding agent (optional: =VERSION)"}
   :with-openspec {:desc "Include OpenSpec (optional: =VERSION)"}
   :with-gitleaks {:coerce :boolean :desc "Include Gitleaks secret scanner"}
   :unisoma       {:coerce :boolean :desc "Enable UniSoma OpenCode model whitelist (requires --with-opencode)"}
   :dir           {:coerce :string :desc "Scaffold project config dir: .aishell (default) or .sandbox"}
   :reuse-config  {:coerce :boolean :desc "Seed omitted options from the saved setup config"}
   :force         {:coerce :boolean :desc "Force rebuild (bypass Docker cache)"}
   :verbose       {:alias :v :coerce :boolean :desc "Show full Docker build output"}
   :help          {:alias :h :coerce :boolean :desc "Show setup help"}})

(def setup-harness-options
  [{:opt :with-claude :state :with-claude :version :claude-version :label "Claude Code"}
   {:opt :with-opencode :state :with-opencode :version :opencode-version :label "OpenCode"}
   {:opt :with-codex :state :with-codex :version :codex-version :label "Codex"}
   {:opt :with-gemini :state :with-gemini :version :gemini-version :label "Gemini"}
   {:opt :with-pi :state :with-pi :version :pi-version :label "Pi"}
   {:opt :with-openspec :state :with-openspec :version :openspec-version :label "OpenSpec"}])

(def setup-boolean-options
  [{:opt :with-gitleaks :state :with-gitleaks}
   {:opt :unisoma :state :unisoma}])

(def empty-setup-state
  {:with-claude false
   :with-opencode false
   :with-codex false
   :with-gemini false
   :with-pi false
   :with-openspec false
   :with-gitleaks false
   :unisoma false
   :claude-version nil
   :opencode-version nil
   :codex-version nil
   :gemini-version nil
   :pi-version nil
   :openspec-version nil})

(defn explicit-setup-state
  "Build declarative setup intent from CLI opts only. Omitted flags stay disabled."
  [opts]
  (let [parsed (into {}
                     (map (fn [{:keys [opt]}]
                            [opt (parse-with-flag (get opts opt))])
                          setup-harness-options))]
    (assoc empty-setup-state
           :with-claude (get-in parsed [:with-claude :enabled?])
           :with-opencode (get-in parsed [:with-opencode :enabled?])
           :with-codex (get-in parsed [:with-codex :enabled?])
           :with-gemini (get-in parsed [:with-gemini :enabled?])
           :with-pi (get-in parsed [:with-pi :enabled?])
           :with-openspec (get-in parsed [:with-openspec :enabled?])
           :with-gitleaks (boolean (:with-gitleaks opts))
           :unisoma (boolean (:unisoma opts))
           :claude-version (get-in parsed [:with-claude :version])
           :opencode-version (get-in parsed [:with-opencode :version])
           :codex-version (get-in parsed [:with-codex :version])
           :gemini-version (get-in parsed [:with-gemini :version])
           :pi-version (get-in parsed [:with-pi :version])
           :openspec-version (get-in parsed [:with-openspec :version]))))

(defn saved-setup-state
  "Extract the persisted setup intent, excluding derived build metadata."
  [saved-state]
  (merge empty-setup-state
         (select-keys saved-state (keys empty-setup-state))))

(defn resolve-setup-state
  "Resolve effective setup intent.

   Plain setup is fully declarative: omitted flags stay disabled.
   With --reuse-config, omitted flags inherit from the saved setup intent,
   while explicit CLI flags override the inherited values."
  [opts saved-state]
  (let [reuse-config? (boolean (:reuse-config opts))]
    (cond
      (not reuse-config?)
      {:reuse-config? false
       :state-map (explicit-setup-state opts)}

      (nil? saved-state)
      {:reuse-config? true
       :error "--reuse-config requires an existing saved setup. Run plain 'aishell setup --with-...' to write a new configuration."}

      :else
      (let [state-map (saved-setup-state saved-state)
            state-map (reduce (fn [state-map {:keys [opt state version]}]
                                (if (contains? opts opt)
                                  (let [parsed (parse-with-flag (get opts opt))]
                                    (assoc state-map
                                           state (:enabled? parsed)
                                           version (:version parsed)))
                                  state-map))
                              state-map
                              setup-harness-options)
            state-map (reduce (fn [state-map {:keys [opt state]}]
                                (if (contains? opts opt)
                                  (assoc state-map state (boolean (get opts opt)))
                                  state-map))
                              state-map
                              setup-boolean-options)]
        {:reuse-config? true
         :state-map state-map}))))

(defn setup-validation-error
  "Return setup validation error string, or nil when the effective config is valid."
  [{:keys [state-map reuse-config?]}]
  (let [recovery-hint "Run plain 'aishell setup --with-...' to write a new configuration."]
    (cond
      (and (:unisoma state-map) (not (:with-opencode state-map)))
      (if reuse-config?
        (str "Saved setup config is invalid: --unisoma requires --with-opencode.\n" recovery-hint)
        "--unisoma requires --with-opencode")

      :else
      (some (fn [{:keys [version label]}]
              (when-let [msg (version-error-message (get state-map version) label)]
                (if reuse-config?
                  (str "Saved setup config is invalid: " msg "\n" recovery-hint)
                  msg)))
            setup-harness-options))))

(defn validate-setup-state
  "Exit with an error if the effective setup state is invalid."
  [resolution]
  (when-let [msg (setup-validation-error resolution)]
    (output/error msg)))

(defn enabled-harness-list
  "Return enabled harness names in label order for volume metadata."
  [state-map]
  (->> [{:name "claude" :enabled? (:with-claude state-map)}
        {:name "opencode" :enabled? (:with-opencode state-map)}
        {:name "codex" :enabled? (:with-codex state-map)}
        {:name "gemini" :enabled? (:with-gemini state-map)}
        {:name "openspec" :enabled? (:with-openspec state-map)}
        {:name "pi" :enabled? (:with-pi state-map)}]
       (keep (fn [{:keys [name enabled?]}]
               (when enabled? name)))
       (str/join ",")))

(defn print-effective-reused-setup
  "Print the merged effective config when --reuse-config is active."
  [state-map]
  (println "Reusing saved setup configuration:")
  (when (:with-claude state-map)
    (println (str "  Claude Code: " (or (:claude-version state-map) "latest"))))
  (when (:with-opencode state-map)
    (println (str "  OpenCode: " (or (:opencode-version state-map) "latest"))))
  (when (:unisoma state-map)
    (println "  UniSoma: enabled"))
  (when (:with-codex state-map)
    (println (str "  Codex: " (or (:codex-version state-map) "latest"))))
  (when (:with-gemini state-map)
    (println (str "  Gemini: " (or (:gemini-version state-map) "latest"))))
  (when (:with-pi state-map)
    (println (str "  Pi: " (or (:pi-version state-map) "latest"))))
  (when (:with-openspec state-map)
    (println (str "  OpenSpec: " (or (:openspec-version state-map) "latest"))))
  (when (:with-gitleaks state-map)
    (println "  Gitleaks: enabled"))
  (when-not (or (:with-claude state-map)
                (:with-opencode state-map)
                (:with-codex state-map)
                (:with-gemini state-map)
                (:with-pi state-map)
                (:with-openspec state-map)
                (:with-gitleaks state-map)
                (:unisoma state-map))
    (println "  No harnesses or optional tools enabled")))

(defn installed-harnesses
  "Return set of installed harness names based on state.
   If no state file exists (no build yet), returns all harnesses
   to aid discoverability."
  []
  (if-let [state (state/read-state)]
    (cond-> #{}
      (:with-claude state) (conj "claude")
      (:with-opencode state) (conj "opencode")
      (:with-codex state) (conj "codex")
      (:with-gemini state) (conj "gemini")
      (:with-pi state) (conj "pi")
      (:with-openspec state) (conj "openspec")
      (:with-gitleaks state false) (conj "gitleaks"))
    ;; No state = no build yet, show all for discoverability
    #{"claude" "opencode" "codex" "gemini" "pi" "gitleaks"}))

(defn print-help []
  (println (str output/BOLD "Usage:" output/NC " aishell [OPTIONS] COMMAND [ARGS...]"))
  (println)
  (println "Set up and run ephemeral containers for AI harnesses.")
  (println)
  (println (str output/BOLD "Commands:" output/NC))
  (println (str "  " output/CYAN "setup" output/NC "      Set up the container image and select harnesses"))
  (println (str "  " output/CYAN "update" output/NC "     Refresh harness tools to latest versions"))
  (println (str "  " output/CYAN "check" output/NC "      Validate setup and configuration"))
  (println (str "  " output/CYAN "exec" output/NC "       Run one-off command in container"))
  (println (str "  " output/CYAN "ps" output/NC "         List project containers"))
  (println (str "  " output/CYAN "volumes" output/NC "    Manage harness volumes"))
  (println (str "  " output/CYAN "attach, a" output/NC "  Attach to running container"))
  (println (str "  " output/CYAN "vscode" output/NC "     Open VSCode attached to container"))
  (println (str "  " output/CYAN "info" output/NC "       Show image stack and installed tools"))
  (println (str "  " output/CYAN "upgrade" output/NC "    Upgrade aishell to latest version"))
  ;; Conditionally show harness commands based on installation
  (let [installed (installed-harnesses)]
    (when (contains? installed "claude")
      (println (str "  " output/CYAN "claude" output/NC "     Run Claude Code")))
    (when (contains? installed "opencode")
      (println (str "  " output/CYAN "opencode" output/NC "   Run OpenCode")))
    (when (contains? installed "codex")
      (println (str "  " output/CYAN "codex" output/NC "      Run Codex CLI")))
    (when (contains? installed "gemini")
      (println (str "  " output/CYAN "gemini" output/NC "     Run Gemini CLI")))
    (when (contains? installed "pi")
      (println (str "  " output/CYAN "pi" output/NC "         Run Pi coding agent")))
    (when (contains? installed "gitleaks")
      (println (str "  " output/CYAN "gitleaks" output/NC "   Run Gitleaks secret scanner"))))
  (println (str "  " output/CYAN "(none)" output/NC "     Enter interactive shell"))
  (println)
  (println (str output/BOLD "Global Options:" output/NC))
  (println (cli/format-opts {:spec global-spec
                             :order [:help :version :json]}))
  (println)
  (println (str output/BOLD "Examples:" output/NC))
  (println (str "  " output/CYAN "aishell setup --with-claude" output/NC "     Set up with Claude Code"))
  (println (str "  " output/CYAN "aishell check" output/NC "                    Validate setup"))
  (println (str "  " output/CYAN "aishell exec ls -la" output/NC "             Run command in container"))
  (println (str "  " output/CYAN "aishell ps" output/NC "                       List containers"))
  (println (str "  " output/CYAN "aishell claude" output/NC "                  Run Claude Code"))
  (println (str "  " output/CYAN "aishell codex" output/NC "                   Run Codex CLI"))
  (println (str "  " output/CYAN "aishell" output/NC "                         Enter shell")))

(defn print-setup-help []
  (println (str output/BOLD "Usage:" output/NC " aishell setup [OPTIONS]"))
  (println)
  (println "Set up the container image and select harnesses.")
  (println)
  (println (str output/BOLD "Options:" output/NC))
  (println (cli/format-opts {:spec setup-spec
                             :order [:with-claude :with-opencode :with-codex :with-gemini :with-pi :with-openspec :with-gitleaks :unisoma :dir :reuse-config :force :verbose :help]}))
  (println)
  (println (str output/BOLD "Examples:" output/NC))
  (println (str "  " output/CYAN "aishell setup" output/NC "                      Set up base image"))
  (println (str "  " output/CYAN "aishell setup --with-claude" output/NC "        Include Claude Code (latest)"))
  (println (str "  " output/CYAN "aishell setup --with-claude=2.0.22" output/NC " Pin Claude Code version"))
  (println (str "  " output/CYAN "aishell setup --with-claude --with-opencode" output/NC " Include both"))
  (println (str "  " output/CYAN "aishell setup --with-codex --with-gemini" output/NC " Include Codex and Gemini"))
  (println (str "  " output/CYAN "aishell setup --with-pi" output/NC "               Include Pi coding agent"))
  (println (str "  " output/CYAN "aishell setup --with-openspec" output/NC "          Include OpenSpec"))
  (println (str "  " output/CYAN "aishell setup --with-gitleaks" output/NC "          Include Gitleaks scanner"))
  (println (str "  " output/CYAN "aishell setup --with-opencode --unisoma" output/NC " OpenCode with UniSoma whitelist"))
  (println (str "  " output/CYAN "aishell setup --dir .sandbox" output/NC "           Scaffold a .sandbox/ project config dir"))
  (println (str "  " output/CYAN "aishell setup --reuse-config" output/NC "           Reuse saved setup defaults"))
  (println (str "  " output/CYAN "aishell setup --reuse-config --with-opencode" output/NC " Reuse config, reset OpenCode to latest"))
  (println (str "  " output/CYAN "aishell setup --force" output/NC "                  Force rebuild"))
  (println)
  (println (str output/BOLD "Reuse mode:" output/NC))
  (println "  - --reuse-config starts from the last saved setup intent")
  (println "  - Omitted setup flags inherit from the saved config")
  (println "  - A bare --with-... flag resets that tool to latest")
  (println "  - To disable saved tools or write a brand-new config, use plain 'aishell setup'"))

(def starter-config-yaml
  "# aishell project configuration.
# Inherit from global ~/.aishell/config.yaml (default), or 'none' to disable.
# extends: global
#
# mounts:
#   - /path/to/data
# env:
#   MY_VAR: literal_value
# ports:
#   - \"3000:3000\"
")

(defn normalize-config-dir-name
  "Normalize a `--dir` value to a canonical project config dir name
   (.aishell or .sandbox), or nil when invalid. Accepts the literal
   names with or without the leading dot."
  [v]
  (when (string? v)
    (let [name (if (str/starts-with? v ".") v (str "." v))]
      (when (some #{name} util/project-config-dir-names)
        name))))

(defn scaffold-config-dir!
  "Scaffold the chosen project config dir under project-dir.
   Exits with an error on an invalid `--dir` value or when the other
   alias dir already exists. Creates the dir and a starter config.yaml
   when none is present."
  [dir-value project-dir]
  (let [dir-name (normalize-config-dir-name dir-value)]
    (when-not dir-name
      (output/error (str "Invalid --dir value: " (pr-str dir-value)
                         ". Use .aishell or .sandbox.")))
    (let [other (first (remove #{dir-name} util/project-config-dir-names))]
      (when (fs/exists? (fs/path project-dir other))
        (output/error (str "Cannot scaffold " dir-name "/: " other
                           "/ already exists. Use only one project config directory."))))
    (let [dir-path (fs/path project-dir dir-name)
          config-path (fs/path dir-path "config.yaml")]
      (fs/create-dirs dir-path)
      (if (fs/exists? config-path)
        (println (str "Project config dir already present: " dir-name "/"))
        (do
          (spit (str config-path) starter-config-yaml)
          (println (str "Scaffolded " dir-name "/config.yaml")))))))

(defn handle-setup [{:keys [opts]}]
  (if (:help opts)
    (print-setup-help)
    (let [_ (when (contains? opts :dir)
              (scaffold-config-dir! (:dir opts) (System/getProperty "user.dir")))
          prev-state (state/read-state)
          resolution (resolve-setup-state opts prev-state)
          _ (when-let [msg (:error resolution)]
              (output/error msg))
          _ (validate-setup-state resolution)
          state-map (:state-map resolution)
          _ (when (:reuse-config? resolution)
              (print-effective-reused-setup state-map))

          ;; Show replacement message if image exists
          _ (when (docker/image-exists? build/foundation-image-tag)
              (println "Replacing existing image..."))

          ;; Build foundation image (harness tools will be volume-mounted in Phase 36)
          result (build/build-foundation-image
                  {:with-gitleaks (:with-gitleaks state-map)
                   :verbose (:verbose opts)
                   :force (:force opts)})

          ;; Ensure aishell:base exists (custom build from ~/.aishell/Dockerfile or alias)
          _ (base/ensure-base-image {:force (:force opts) :verbose (:verbose opts)})

          ;; Step 2: Compute harness volume hash
          project-dir (System/getProperty "user.dir")
          cfg (config/load-config project-dir)
          harness-hash (vol/compute-harness-hash state-map)
          volume-name (vol/volume-name harness-hash)

          ;; Step 3: Populate volume if needed (only if missing or stale)
          _ (when (some #(get state-map %) [:with-claude :with-opencode :with-codex :with-gemini :with-pi :with-openspec])
              (let [vol-missing? (not (vol/volume-exists? volume-name))
                    vol-stale? (and (not vol-missing?)
                                    (not= (vol/get-volume-label volume-name "aishell.harness.hash")
                                          harness-hash))
                    harness-list (enabled-harness-list state-map)]
                (when (or vol-missing? vol-stale?)
                  (when vol-missing?
                    (vol/create-volume volume-name {"aishell.harness.hash" harness-hash
                                                    "aishell.harness.version" "3.1.0"
                                                    "aishell.harnesses" harness-list}))
                  (let [pop-result (vol/populate-volume volume-name state-map {:verbose (:verbose opts) :config cfg})]
                    (when-not (:success pop-result)
                      (when vol-missing? (vol/remove-volume volume-name))
                      (output/error "Failed to populate harness volume"))))))]

      ;; Manage OpenCode whitelist for UniSoma
      (manage-opencode-whitelist! state-map prev-state)

      ;; Persist state (always, even on failure this won't run due to error exit)
      (state/write-state
       (assoc state-map
              :image-tag (:image result)
              :build-time (str (java.time.Instant/now))
              :dockerfile-hash (hash/compute-hash templates/foundation-content)  ; Kept for v2.7.0 compat
              :foundation-hash (hash/compute-hash templates/foundation-content)  ; NEW: same as dockerfile-hash for now
              :harness-volume-hash harness-hash                               ; NEW
              :harness-volume-name volume-name)))))                           ; NEW

(defn handle-default [{:keys [opts args]}]
  (cond
    (:version opts)
    (if output/*json-output*
      (output/emit-json {:name "aishell" :version version})
      (print-version))

    (:help opts)
    (print-help)

    ;; Unknown command - check for typos
    (seq args)
    (output/error-unknown-command (first args))

    ;; No command, no flags - run shell
    :else
    (run/run-container nil [] {})))

(def update-spec
  {:force   {:coerce :boolean :desc "Also rebuild foundation image (--no-cache)"}
   :verbose {:alias :v :coerce :boolean :desc "Show full build and install output"}
   :help    {:alias :h :coerce :boolean :desc "Show update help"}})

(def volumes-prune-spec
  {:yes {:alias :y :coerce :boolean :desc "Skip confirmation prompt"}})

(defn print-update-help []
  (println (str output/BOLD "Usage:" output/NC " aishell update [OPTIONS]"))
  (println)
  (println "Refresh harness tools to latest versions using existing configuration.")
  (println)
  (println (str output/BOLD "Options:" output/NC))
  (println (cli/format-opts {:spec update-spec
                             :order [:force :verbose :help]}))
  (println)
  (println (str output/BOLD "Notes:" output/NC))
  (println "  - Refreshes harnesses from last build configuration")
  (println "  - Deletes and recreates the harness volume for a clean slate")
  (println "  - Foundation image is NOT rebuilt unless --force is used")
  (println "  - Use 'aishell setup --reuse-config' when you want setup semantics")
  (println "    (inherit saved config, override selected flags, avoid unconditional")
  (println "    harness-volume repopulation when the effective config is unchanged)")
  (println)
  (println (str output/BOLD "Examples:" output/NC))
  (println (str "  " output/CYAN "aishell update" output/NC "                       Refresh harness tools only"))
  (println (str "  " output/CYAN "aishell update --force" output/NC "               Also rebuild foundation image"))
  (println (str "  " output/CYAN "aishell setup --reuse-config --force" output/NC " Rebuild setup layers from saved config")))

(defn handle-update
  "Update command: refresh harness tools to latest versions.
   Default behavior: repopulates volume (delete + recreate) without rebuilding foundation.
   With --force: also rebuilds foundation image using --no-cache."
  [{:keys [opts]}]
  (if (:help opts)
    (print-update-help)
    (let [state (state/read-state)]
      ;; Must have prior build
      (when-not state
        (output/error "No previous setup found. Run: aishell setup"))

      ;; Show what we're updating
      (println "Updating with preserved configuration...")
      (when (:with-claude state)
        (println (str "  Claude Code: " (or (:claude-version state) "latest"))))
      (when (:with-opencode state)
        (println (str "  OpenCode: " (or (:opencode-version state) "latest"))))
      (when (:unisoma state)
        (println "  UniSoma: enabled"))
      (when (:with-codex state)
        (println (str "  Codex: " (or (:codex-version state) "latest"))))
      (when (:with-gemini state)
        (println (str "  Gemini: " (or (:gemini-version state) "latest"))))
      (when (:with-pi state)
        (println (str "  Pi: " (or (:pi-version state) "latest"))))
      (when (:with-openspec state)
        (println (str "  OpenSpec: " (or (:openspec-version state) "latest"))))

      ;; Rebuild foundation image if stale (always check; --force bypasses cache)
      (let [project-dir (System/getProperty "user.dir")
            cfg (config/load-config project-dir)
            result (build/build-foundation-image
                    {:with-gitleaks (:with-gitleaks state false)
                     :verbose (:verbose opts)
                     :force (:force opts)
                     :quiet (not (:force opts))})

            ;; Ensure aishell:base is up to date
            _ (if (:force opts)
                (base/ensure-base-image {:force true :verbose (:verbose opts)})
                (base/ensure-base-image {:verbose (:verbose opts) :quiet true}))

            ;; Volume repopulation (unconditional delete + recreate)
            harness-hash (vol/compute-harness-hash state)
            volume-name (or (:harness-volume-name state)
                            (vol/volume-name harness-hash))

            ;; Check if any harness is enabled
            harnesses-enabled? (some #(get state %) [:with-claude :with-opencode :with-codex :with-gemini :with-pi :with-openspec])

            _ (if harnesses-enabled?
                ;; Repopulate volume (delete + recreate)
                (let [;; Compute harness list for label
                      harness-list (str/join "," (keep (fn [[k v]]
                                                         (when v (name k)))
                                                       {:claude (:with-claude state)
                                                        :opencode (:with-opencode state)
                                                        :codex (:with-codex state)
                                                        :gemini (:with-gemini state)
                                                        :openspec (:with-openspec state)
                                                        :pi (:with-pi state)}))]
                  (println "Repopulating harness volume...")
                  (let [rm-result (vol/remove-volume volume-name)]
                    (when-not (:removed? rm-result)
                      (output/error
                       (case (:reason rm-result)
                         :in-use (str "Cannot repopulate harness volume " volume-name
                                      " — it is in use by container(s): "
                                      (str/join ", " (vol/containers-using-volume volume-name))
                                      ". Stop and remove them, then re-run 'aishell update'.")
                         (str "Failed to remove harness volume " volume-name
                              ": " (:stderr rm-result))))))
                  (vol/create-volume volume-name {"aishell.harness.hash" harness-hash
                                                  "aishell.harness.version" "3.1.0"
                                                  "aishell.harnesses" harness-list})
                  (let [pop-result (vol/populate-volume volume-name state {:verbose (:verbose opts) :config cfg})]
                    (when-not (:success pop-result)
                      (vol/remove-volume volume-name)
                      (output/error "Failed to populate harness volume")))
                  ;; Clear Pi packages hash so they get reinstalled on next run
                  (pi/clear-hash!))
                ;; No harnesses enabled
                (println "No harnesses enabled. Nothing to update."))]

        ;; Refresh OpenCode whitelist for UniSoma (upsert only on update)
        (manage-opencode-whitelist! state state)

        ;; Update state with new build-time and current hashes
        (state/write-state
         (cond-> state
           true (assoc :build-time (str (java.time.Instant/now))
                       :dockerfile-hash (hash/compute-hash templates/foundation-content)
                       :foundation-hash (hash/compute-hash templates/foundation-content))
           result (assoc :image-tag (:image result))))))))

(defn- prompt-yn
  "Prompt user for yes/no confirmation."
  [message]
  (print (str message " (y/n): "))
  (flush)
  (= "y" (str/trim (read-line))))

(defn handle-volumes-list
  "List all harness volumes with active/orphaned status."
  []
  (let [state (state/read-state)
        volumes (vol/list-harness-volumes)
        current-vol (:harness-volume-name state)]
    (if (empty? volumes)
      (println "No harness volumes found.\n\nVolumes are created automatically during 'aishell setup' when harnesses are enabled.")
      (do
        (pp/print-table [:NAME :STATUS :SIZE :HARNESSES]
                        (map (fn [{:keys [name harnesses]}]
                               {:NAME name
                                :STATUS (if (= name current-vol) "active" "orphaned")
                                :SIZE (vol/get-volume-size name)
                                :HARNESSES (or harnesses "unknown")})
                             volumes))
        (println "\nTo remove orphaned volumes: aishell volumes prune")))))

(defn handle-volumes-prune
  "Remove orphaned volumes and orphaned base images with confirmation prompt."
  [opts]
  (let [state (state/read-state)
        current-vol (:harness-volume-name state)
        all-volumes (vol/list-harness-volumes)
        orphaned (filter #(and (not= (:name %) current-vol)
                               (str/starts-with? (:name %) "aishell-harness-"))
                         all-volumes)]
    (if (empty? orphaned)
      (println "No orphaned volumes to prune.")
      (do
        (println "The following volumes will be removed:")
        (doseq [{:keys [name]} orphaned]
          (println (str "  - " name)))
        (when (or (:yes opts) (prompt-yn "Remove these volumes?"))
          (doseq [{:keys [name]} orphaned]
            (if (vol/volume-in-use? name)
              (println (str "Skipping " name " (in use by container)"))
              (do
                (vol/remove-volume name)
                (println (str "Removed " name)))))
          (println "Prune complete."))))

    ;; Check for orphaned custom base image:
    ;; If aishell:base has custom Dockerfile label but ~/.aishell/Dockerfile no longer exists,
    ;; the custom base image is orphaned — re-tag foundation as base to reset.
    (when (and (docker/image-exists? base/base-image-tag)
               (docker/get-image-label base/base-image-tag "aishell.base.dockerfile.hash")
               (not (base/global-dockerfile-exists?)))
      (println "Orphaned custom base image found — resetting to foundation alias.")
      (base/tag-foundation-as-base)
      (println "Base image reset to foundation alias."))))

(defn print-volumes-help
  "Print help for volumes command."
  []
  (println (str output/BOLD "Usage:" output/NC " aishell volumes [SUBCOMMAND]"))
  (println)
  (println "Manage harness volumes.")
  (println)
  (println (str output/BOLD "Subcommands:" output/NC))
  (println "  list     List all harness volumes (default)")
  (println "  prune    Remove orphaned volumes")
  (println)
  (println (str output/BOLD "Prune Options:" output/NC))
  (println (cli/format-opts {:spec volumes-prune-spec
                             :order [:yes]}))
  (println)
  (println (str output/BOLD "Examples:" output/NC))
  (println (str "  " output/CYAN "aishell volumes" output/NC "              List volumes"))
  (println (str "  " output/CYAN "aishell volumes list" output/NC "        List volumes"))
  (println (str "  " output/CYAN "aishell volumes prune" output/NC "       Remove orphaned volumes"))
  (println (str "  " output/CYAN "aishell volumes prune --yes" output/NC " Skip confirmation")))

(defn handle-volumes
  "Dispatcher for volumes subcommands."
  [args]
  (let [subcommand (first args)]
    (cond
      (or (= subcommand "--help") (= subcommand "-h"))
      (print-volumes-help)

      (or (nil? subcommand) (= subcommand "list"))
      (handle-volumes-list)

      (= subcommand "prune")
      (let [opts (cli/parse-opts (vec (rest args)) {:spec volumes-prune-spec})]
        (handle-volumes-prune opts))

      :else
      (output/error (str "Unknown volumes subcommand: " subcommand
                         "\n\nValid subcommands: list, prune"
                         "\n\nTry: aishell volumes --help")))))

(defn- extract-short-name
  "Extract user-friendly name from full container name.
   Example: 'aishell-a1b2c3d4-claude' -> 'claude'"
  [container-name]
  (last (str/split container-name #"-" 3)))

(defn- ps-row
  "Pure: build the canonical row for one docker container.
   Used by both the JSON path (`format-ps-data`) and the human table
   (`format-container`) so the two stay in lockstep.

   `:bootstrap` is attached upstream by `bootstrap/attach-bootstrap!`
   (one of `:none :pending :ready :failed`); a missing key surfaces as
   `:none` so the JSON shape stays stable when the upstream attach is
   skipped.

   `:ready` is the derived single-signal answer to \"is this container
   running and ready to use?\" — true iff the container is `Up` and
   either has no pre_start (`:none`) or finalized it (`:ready`). The
   `:none`/`:ready` collapse is intentional: from the caller's view,
   both mean \"no readiness work left.\""
  [c]
  (let [status (:status c)
        bootstrap (or (:bootstrap c) :none)]
    {:name (extract-short-name (:name c))
     :fullName (:name c)
     :status status
     :created (:created c)
     :bootstrap bootstrap
     :ready (and (bootstrap/running? status)
                 (contains? #{:none :ready} bootstrap))}))

(defn- bootstrap-cell
  "Render the BOOTSTRAP cell for one human-table row.
   Bare `name` for `:none|:pending|:ready`; capitalized FAILED for emphasis.
   No ANSI color (would break clojure.pprint/print-table width math)."
  [bootstrap]
  (case bootstrap
    :failed "FAILED"
    (name (or bootstrap :none))))

(defn- format-container
  "Format a container row for the human table (uppercase column headers)."
  [c]
  (let [{:keys [name status created bootstrap]} (ps-row c)]
    {:NAME name :STATUS status :CREATED created
     :BOOTSTRAP (bootstrap-cell bootstrap)}))

(defn format-ps-data
  "Build the JSON-shaped data for `aishell ps`.
   Pure: takes the docker container list (vector of maps with
   :name/:status/:created/:bootstrap) and returns a vector of maps
   with :name/:fullName/:status/:created/:bootstrap/:ready keys.
   Empty input yields []."
  [containers]
  (mapv ps-row containers))

(defn handle-ps
  "List all containers for the current project.
   In JSON mode, emits a compact JSON array of
   {name,fullName,status,created,bootstrap,ready}."
  [_]
  (let [project-dir (System/getProperty "user.dir")
        containers (-> (naming/list-project-containers project-dir)
                       bootstrap/attach-bootstrap!)]
    (if output/*json-output*
      (output/emit-json (format-ps-data containers))
      (if (empty? containers)
        (println "No containers found for this project.\n\nTo start a container:\n  aishell claude\n  aishell opencode --name my-session\n\nContainers are project-specific (based on current directory).")
        (do
          (println "Containers for this project:\n")
          (pp/print-table [:NAME :STATUS :CREATED :BOOTSTRAP]
                          (map format-container containers))
          (when (some #(= :failed (:bootstrap %)) containers)
            (println "\nOne or more containers failed pre_start. Run aishell exec <name> cat /tmp/pre-start.log for details."))
          (println "\nTo attach: aishell attach <name>"))))))

(def json-supported-subcommands
  "Subcommands whose JSON path is wired today. Add to this set as
   `volumes list`, `info`, and `check` JSON paths land in their
   respective follow-up tickets (aix-01kr1qpwqxg9, aix-01kr1qqhakrv,
   aix-01kr1qr59100), and update the --json description in `global-spec`
   in lockstep."
  #{"ps"})

(def known-subcommands
  "All recognised aishell subcommands. Used for unknown_command detection."
  #{"setup" "update" "check" "exec" "ps" "volumes" "attach" "a"
    "vscode" "upgrade" "info" "claude" "opencode" "codex" "gemini"
    "pi" "gitleaks"})

(defn classify-json-command
  "Classify the cleaned argv (after --json stripping) for JSON-mode dispatch.

   Returns one of:
     :help        — args contain --help/-h (human help wins over --json)
     :supported   — first token is in Group A, or args request --version
     :unsupported — recognised but non-Group-A command, or no subcommand
     :unknown     — first token is not a known subcommand or flag

   :unknown wins over :unsupported when both could apply."
  [args]
  (let [args (vec args)
        first-arg (first args)]
    (cond
      (some #{"--help" "-h"} args) :help
      (some #{"--version" "-v"} args) :supported
      (nil? first-arg) :unsupported
      (contains? json-supported-subcommands first-arg) :supported
      (contains? known-subcommands first-arg) :unsupported
      :else :unknown)))

(def dispatch-table
  [{:cmds ["setup"] :fn handle-setup :spec setup-spec :restrict true}
   {:cmds ["update"] :fn handle-update :spec update-spec :restrict true}
   {:cmds [] :spec global-spec :fn handle-default}])

(defn handle-error
  "Handle CLI parsing errors with helpful messages."
  [{:keys [cause option msg]}]
  (case cause
    :restrict
    (do
      (binding [*out* *err*]
        (println (str output/RED "Error:" output/NC " Unknown option: " option))
        (println (str "Try: " output/CYAN "aishell --help" output/NC)))
      (System/exit 1))

    ;; Default
    (output/error msg)))

(def pass-through-harnesses
  "Harness subcommands whose argv (including --json) is forwarded verbatim."
  #{"claude" "opencode" "codex" "gemini" "pi" "gitleaks"})

(defn- json-error-message [args]
  (if (empty? args)
    "--json requires a supported command (ps, volumes list, info, check, --version)"
    (str "--json is not supported for: " (first args))))

(defn- do-dispatch [args]
  ;; Extract --unsafe flag before pass-through (used by detection framework)
  (let [unsafe? (boolean (some #{"--unsafe"} args))
        clean-args (vec (remove #{"--unsafe"} args))

        ;; Extract --name flag (--name VALUE format) for run-mode commands
        ;; attach and other commands parse their own --name flag
        known-subcommands #{"setup" "update" "check" "exec" "ps" "volumes" "attach" "a" "vscode" "upgrade" "info"}
        should-extract-name? (not (contains? known-subcommands (first clean-args)))
        container-name-override (when should-extract-name?
                                  (let [idx (.indexOf (vec clean-args) "--name")]
                                    (when (and (>= idx 0) (< (inc idx) (count clean-args)))
                                      (nth clean-args (inc idx)))))
        clean-args (if container-name-override
                     (let [idx (.indexOf (vec clean-args) "--name")]
                       (into (subvec (vec clean-args) 0 idx)
                             (subvec (vec clean-args) (+ idx 2))))
                     clean-args)]
    ;; Check for newer version (skip for help/version/upgrade to avoid startup delay).
    ;; -v/--version is ambiguous: top-level it means version, but on subcommands
    ;; (setup -v, update -v) it means verbose. Only skip the update check when
    ;; there's no subcommand — i.e. the args will route to handle-default.
    (let [first-arg (first clean-args)
          has-subcommand? (contains? known-subcommands first-arg)
          skip? (or (some #{"--help" "-h"} clean-args)
                    (and (not has-subcommand?)
                         (some #{"-v" "--version"} clean-args))
                    (= "upgrade" first-arg))]
      (when-not (or skip? output/*json-output*)
        (update-check/maybe-check-for-update version)))
    ;; Handle pass-through commands before standard dispatch
    ;; This ensures all args (including --help, --version) go to the harness
    (case (first clean-args)
      "info" (info/run-info (vec (rest clean-args)))
      "check" (check/run-check)
      "exec" (run/run-exec (vec (rest clean-args)))
      "ps" (handle-ps nil)
      "volumes" (handle-volumes (vec (rest clean-args)))
      ("attach" "a") (let [rest-args (vec (rest clean-args))]
                       (cond
                         (some #{"-h" "--help"} rest-args)
                         (do
                           (println (str output/BOLD "Usage:" output/NC " aishell attach <name> [-- <command> [args...]]"))
                           (println)
                           (println "Attach to a running container (opens bash shell).")
                           (println "If a command follows '--', it runs first; on exit you drop into the container shell.")
                           (println)
                           (println (str output/BOLD "Options:" output/NC))
                           (println "  -h, --help    Show this help")
                           (println)
                           (println (str output/BOLD "Examples:" output/NC))
                           (println (str "  " output/CYAN "aishell attach claude" output/NC))
                           (println "      Open bash shell in the 'claude' container")
                           (println)
                           (println (str "  " output/CYAN "aishell attach shell" output/NC))
                           (println "      Open bash shell in the 'shell' container")
                           (println)
                           (println (str "  " output/CYAN "aishell attach session -- btm" output/NC))
                           (println "      Run 'btm' first; on exit, drop into the container shell")
                           (println)
                           (println (str "  " output/CYAN "aishell attach session -- bash -c \"btm | tee log\"" output/NC))
                           (println "      Use bash -c to opt into shell pipelines")
                           (println)
                           (println (str output/BOLD "Notes:" output/NC))
                           (println "  Use 'aishell ps' to list running containers.")
                           (println "  The container must be running. Start one in another terminal: aishell <harness>"))

                         :else
                         (let [parsed (attach-parse/parse-attach-args rest-args)]
                           (if-let [err (:error parsed)]
                             (output/error err)
                             (attach/attach-to-container (:name parsed)
                                                         :command-argv (:command-argv parsed))))))
      "vscode" (let [rest-args (vec (rest clean-args))
                     own-flags #{"--detach" "--stop" "-h" "--help"}
                     code-args (vec (remove own-flags rest-args))]
                 (cond
                   (some #{"-h" "--help"} rest-args)
                   (do
                     (println (str output/BOLD "Usage:" output/NC " aishell vscode [OPTIONS] [-- CODE_ARGS...]"))
                     (println)
                     (println "Open VSCode attached to the aishell container as the developer user.")
                     (println (str output/YELLOW "(Experimental — API subject to change)" output/NC))
                     (println)
                     (println (str output/BOLD "Options:" output/NC))
                     (println "  -h, --help      Show this help")
                     (println "      --detach    Run in background (don't wait for VSCode to close)")
                     (println "      --stop      Stop a detached vscode container")
                     (println)
                     (println (str output/BOLD "Extra arguments:" output/NC))
                     (println "  Any arguments not listed above are passed through to the 'code' CLI.")
                     (println (str "  Default args can be set via harness_args.vscode in "
                                   (util/resolve-project-config-dir (System/getProperty "user.dir"))
                                   "/config.yaml."))
                     (println)
                     (println (str output/BOLD "What this does:" output/NC))
                     (println "  1. Syncs host VSCode extensions to container config")
                     (println "  2. Starts the container if not already running")
                     (println "  3. Opens VSCode attached to the container")
                     (println "  4. Waits for VSCode to close, then stops the container")
                     (println)
                     (println (str output/BOLD "Note:" output/NC))
                     (println "  Your locally installed VSCode extensions are automatically")
                     (println "  made available inside the container.")
                     (println)
                     (println (str output/BOLD "Prerequisites:" output/NC))
                     (println "  - VSCode with 'code' CLI on PATH")
                     (println "  - Dev Containers extension installed in VSCode")
                     (println "  - aishell setup completed")
                     (println)
                     (println (str output/BOLD "Examples:" output/NC))
                     (println (str "  " output/CYAN "aishell vscode" output/NC "                         Open VSCode (blocks until closed)"))
                     (println (str "  " output/CYAN "aishell vscode --detach" output/NC "                Run in background"))
                     (println (str "  " output/CYAN "aishell vscode --stop" output/NC "                  Stop a detached container"))
                     (println (str "  " output/CYAN "aishell vscode --disable-gpu" output/NC "           Pass --disable-gpu to code"))
                     (println (str "  " output/CYAN "aishell vscode --detach --profile Work" output/NC " Detach with a code profile")))

                   (some #{"--stop"} rest-args)
                   (vscode/stop-vscode)

                   :else
                   (let [detach? (some #{"--detach"} rest-args)]
                     (vscode/open-vscode {:detach? (boolean detach?)
                                          :code-args code-args}))))
      "upgrade" (let [rest-args (vec (rest clean-args))]
                  (cond
                    (some #{"-h" "--help"} rest-args)
                    (do
                      (println (str output/BOLD "Usage:" output/NC " aishell upgrade [VERSION]"))
                      (println)
                      (println "Upgrade aishell to the latest version (or a specific version).")
                      (println)
                      (println (str output/BOLD "Arguments:" output/NC))
                      (println "  VERSION    Target version (e.g., 3.4.0). Defaults to latest.")
                      (println)
                      (println (str output/BOLD "Options:" output/NC))
                      (println "  -h, --help    Show this help")
                      (println)
                      (println (str output/BOLD "Examples:" output/NC))
                      (println (str "  " output/CYAN "aishell upgrade" output/NC "          Upgrade to latest version"))
                      (println (str "  " output/CYAN "aishell upgrade 3.4.0" output/NC "   Upgrade to specific version"))
                      (println (str "  " output/CYAN "aishell upgrade 3.2.0" output/NC "   Downgrade to older version")))

                    :else
                    (let [target-version (first rest-args)]
                      (when target-version
                        (validate-version target-version "aishell"))
                      (upgrade/do-upgrade version target-version))))
      "claude" (run/run-container "claude" (vec (rest clean-args))
                                  {:unsafe unsafe? :container-name container-name-override})
      "opencode" (run/run-container "opencode" (vec (rest clean-args))
                                    {:unsafe unsafe? :container-name container-name-override})
      "codex" (run/run-container "codex" (vec (rest clean-args))
                                 {:unsafe unsafe? :container-name container-name-override})
      "gemini" (run/run-container "gemini" (vec (rest clean-args))
                                  {:unsafe unsafe? :container-name container-name-override})
      "pi" (run/run-container "pi" (vec (rest clean-args))
                              {:unsafe unsafe? :container-name container-name-override})
      "gitleaks" (run/run-container "gitleaks" (vec (rest clean-args))
                                    {:unsafe unsafe? :container-name container-name-override :skip-pre-start true})
      ;; Standard dispatch for other commands (setup, update, help)
      (if (or unsafe? container-name-override)
        ;; --unsafe or --name with no harness command -> shell mode
        (run/run-container nil [] {:unsafe (boolean unsafe?) :container-name container-name-override})
        ;; Normal dispatch
        (cli/dispatch dispatch-table args {:error-fn handle-error
                                           :restrict true})))))

(defn- subcommand-of
  "Return the first non-flag token in args, or nil. Used to decide whether
   --json should be consumed at the top level or forwarded to a harness."
  [args]
  (first (remove (fn [a] (str/starts-with? a "-")) args)))

(defn dispatch
  "Public dispatch entry. Handles --json gating before delegating to do-dispatch.

   --json semantics:
   - For pass-through harnesses (claude, opencode, etc.) the flag is forwarded
     verbatim, never consumed.
   - Otherwise --json is stripped here, and the command is classified:
       :unknown     -> emit unknown_command error and exit 1
       :unsupported -> emit unsupported_json error and exit 1
       :help        -> normal flow with colors (--help wins)
       :supported   -> bind *json-output* and zero out ANSI vars, normal flow"
  [args]
  (let [args (vec args)
        sub (subcommand-of args)
        json-pass-through? (contains? pass-through-harnesses sub)
        ;; When --json is pre-position and the subcommand is a pass-through harness,
        ;; hoist the harness to the front so the case-dispatch in do-dispatch keys
        ;; on the harness name rather than "--json". This preserves the
        ;; "claude --json forwards to Claude" contract regardless of flag order.
        args (if (and json-pass-through? (not= sub (first args)))
               (into [sub] (remove #{sub} args))
               args)
        json-mode? (and (not json-pass-through?) (boolean (some #{"--json"} args)))
        args (if json-mode? (vec (remove #{"--json"} args)) args)]
    (if json-mode?
      (case (classify-json-command args)
        :unknown     (output/emit-error-json
                      (str "Unknown command: " (first args))
                      "unknown_command")
        :unsupported (output/emit-error-json
                      (json-error-message args)
                      "unsupported_json")
        :help        (do-dispatch args)
        :supported   (binding [output/*json-output* true
                               output/RED ""
                               output/YELLOW ""
                               output/CYAN ""
                               output/BOLD ""
                               output/NC ""]
                       (do-dispatch args)))
      (do-dispatch args))))
