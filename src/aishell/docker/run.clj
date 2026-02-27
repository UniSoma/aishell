(ns aishell.docker.run
  "Docker run argument construction.
   Builds the full docker run command vector from config and runtime info."
  (:require [babashka.process :as p]
            [babashka.fs :as fs]
            [clojure.string :as str]
            [aishell.util :as util]
            [aishell.output :as output]
            [aishell.docker.naming :as naming]
            [aishell.config :as cfg]))

(defn read-git-identity
  "Read git identity from host configuration.
   Returns {:name \"...\" :email \"...\"} with nil values if not set."
  [project-dir]
  (letfn [(git-config [key]
            (try
              (let [{:keys [exit out]}
                    (p/shell {:out :string :err :string :continue true :dir project-dir}
                             "git" "config" key)]
                (when (zero? exit)
                  (let [val (str/trim out)]
                    (when-not (str/blank? val) val))))
              (catch Exception _ nil)))]
    {:name (git-config "user.name")
     :email (git-config "user.email")}))

(defn- detect-worktree-git-dir
  "Detect if project-dir is inside a git worktree.
   Returns the git common directory path that needs mounting, or nil
   if not a worktree, not a git repository, or on Windows.

   In a worktree, .git is a file pointing to <main>/.git/worktrees/<name>.
   Git needs access to the main repo's .git directory for objects, refs, etc.
   We use 'git rev-parse --git-common-dir' to reliably find the shared .git path."
  [project-dir]
  ;; Windows worktrees in Linux containers have fundamental path incompatibilities
  (when-not (fs/windows?)
    (try
      (let [{:keys [exit out]}
            (p/shell {:out :string :err :string :continue true :dir project-dir}
                     "git" "rev-parse" "--git-common-dir")]
        (when (zero? exit)
          (let [common-dir (str/trim out)
                ;; Make absolute without resolving symlinks (for mounting)
                common-dir-abs (str (fs/normalize
                                      (if (fs/relative? common-dir)
                                        (fs/path project-dir common-dir)
                                        common-dir)))
                ;; Canonicalize both for accurate comparison only
                common-dir-real (str (fs/canonicalize common-dir-abs))
                project-dir-real (str (fs/canonicalize project-dir))]
            ;; Path-segment-aware check: is the git common dir outside the project mount?
            ;; fs/starts-with? delegates to java.nio.file.Path#startsWith (segment-aware),
            ;; so /repo2 does NOT match /repo — unlike string prefix checks.
            (when-not (fs/starts-with? common-dir-real project-dir-real)
              ;; Return the non-canonical absolute path for mounting.
              ;; This preserves symlinks so git's gitdir pointer resolves correctly.
              common-dir-abs))))
      (catch Exception _ nil))))

(defn- get-uid []
  (if (fs/windows?)
    "1000"
    (-> (p/shell {:out :string} "id" "-u") :out str/trim)))

(defn- get-gid []
  (if (fs/windows?)
    "1000"
    (-> (p/shell {:out :string} "id" "-g") :out str/trim)))

(defn- parse-mount-string
  "Parse mount string 'source' or 'source:dest'.
   Smart colon parsing: detect Windows drive letter (X:/) to avoid splitting on it.
   Returns [source dest] where dest is nil for source-only mounts."
  [mount-str]
  (if (re-matches #"^[A-Za-z]:[/\\].*" mount-str)
    ;; Windows absolute path with drive letter — find colon AFTER drive letter
    (if-let [idx (str/index-of mount-str ":" 2)]
      [(subs mount-str 0 idx) (subs mount-str (inc idx))]
      [mount-str nil])
    ;; Unix path or relative path — split on first colon
    (if-let [idx (str/index-of mount-str ":")]
      [(subs mount-str 0 idx) (subs mount-str (inc idx))]
      [mount-str nil])))

(defn- normalize-mount-source
  "Normalize mount source path for Docker Desktop.
   On Windows: converts backslashes to forward slashes.
   On Unix: no-op."
  [source-path]
  (if (fs/windows?)
    (fs/unixify source-path)
    source-path))

(defn- build-mount-args
  "Build -v flags from mounts config.
   Supports:
   - source-only: ~/.ssh (same path on Unix, /home/developer/<name> on Windows)
   - source:dest: /host/path:/container/path (trust user's dest)
   Expands ~ and $HOME in source paths. Warns if source doesn't exist.
   Normalizes source paths for Docker Desktop on Windows."
  [mounts]
  (when (seq mounts)
    (->> mounts
         (mapcat
           (fn [mount]
             (let [mount-str (str mount)
                   [source dest] (parse-mount-string mount-str)
                   source (util/expand-path source)
                   dest (if dest
                          dest  ; Explicit dest: trust user (container path)
                          (if (fs/windows?)
                            ;; Windows source-only: map under container home
                            (str "/home/developer/" (fs/file-name source))
                            ;; Unix source-only: same path
                            source))]
               (if (fs/exists? source)
                 ["-v" (str (normalize-mount-source source) ":" dest)]
                 (do
                   (output/warn (str "Mount source does not exist: " source))
                   []))))))))

(defn- parse-env-string
  "Parse env string 'KEY=value' or 'KEY' (passthrough).
   Returns [key value] where value is nil for passthrough."
  [s]
  (let [s (str s)]
    (if-let [idx (str/index-of s "=")]
      [(subs s 0 idx) (subs s (inc idx))]
      [s nil])))

(defn- build-env-args
  "Build -e flags from env config.

   Supports two YAML formats:
   1. Map format:
      env:
        FOO: bar        # literal
        BAR:            # passthrough from host
   2. Array format:
      env:
        - FOO=bar       # literal
        - BAR           # passthrough from host

   Skips passthrough vars not set on host with warning."
  [env]
  (when (seq env)
    (let [entries (if (map? env)
                    ;; Map format: {:FOO "bar" :BAR nil}
                    (map (fn [[k v]] [(name k) v]) env)
                    ;; Array format: ["FOO=bar" "BAR"]
                    (map parse-env-string env))]
      (->> entries
           (mapcat
             (fn [[key-name value]]
               (if (nil? value)
                 ;; Passthrough: only add if set on host
                 (if-let [host-val (System/getenv key-name)]
                   ["-e" key-name]
                   (do
                     (output/warn (str "Skipping unset host variable: " key-name))
                     []))
                 ;; Literal value
                 ["-e" (str key-name "=" value)])))))))

(def port-pattern
  "Valid port format: [IP:]HOST:CONTAINER[/PROTOCOL]"
  #"^((\d{1,3}\.){3}\d{1,3}:)?\d+:\d+(/[a-z]+)?$")

(defn- build-port-args
  "Build -p flags from ports config.

   Validates format: HOST:CONTAINER or IP:HOST:CONTAINER
   Examples: 8080:80, 127.0.0.1:8080:80, 8080:80/udp"
  [ports]
  (when (seq ports)
    (->> ports
         (mapcat
           (fn [port]
             (let [port-str (str port)]
               (if (re-matches port-pattern port-str)
                 ["-p" port-str]
                 (output/error (str "Invalid port mapping: " port-str
                                   "\nExpected format: HOST_PORT:CONTAINER_PORT or IP:HOST_PORT:CONTAINER_PORT"
                                   "\nExamples: 8080:80, 127.0.0.1:8080:80, 8080:80/udp")))))))))

(defn- tokenize-docker-args
  "Tokenize docker_args into individual args.
   Accepts string (splits on whitespace) or vector (returns as-is).
   Complex quoting in strings not supported (documented limitation)."
  [docker-args]
  (cond
    (sequential? docker-args) (vec docker-args)
    (and docker-args (not (str/blank? docker-args)))
    (str/split (str/trim docker-args) #"\s+")
    :else nil))

(defn- build-harness-volume-args
  "Build -v flag for harness volume mount.
   Mounts volume read-only at /tools (immutable toolchain) unless pi_packages
   are configured, in which case RW is needed for pi's runtime package manager.
   Returns empty vector if volume-name is nil."
  [volume-name config]
  (if volume-name
    (let [has-pi-packages? (seq (:pi_packages config))
          suffix (if has-pi-packages? "" ":ro")]
      ["-v" (str volume-name ":/tools" suffix)])
    []))

(defn- build-harness-env-args
  "Build -e flags for harness tool PATH/NODE_PATH configuration.
   These are always added when a harness volume is mounted.
   The entrypoint also handles PATH setup, but these ensure
   the environment is correct even for non-bash entry points."
  [volume-name]
  (if volume-name
    ["-e" "HARNESS_VOLUME=true"
     "-e" "NPM_CONFIG_PREFIX=/tools/npm"]
    []))

(defn- build-harness-alias-env-args
  "Build -e flags for harness aliases inside the container.
   Passes full command strings so the entrypoint can create shell aliases."
  [config state]
  (let [harness-args (get config :harness_args {})
        known [["claude"   :with-claude   true]
               ["opencode" :with-opencode false]
               ["codex"    :with-codex    true]
               ["gemini"   :with-gemini   false]
               ["pi"       :with-pi       false]]]
    (->> known
         (keep (fn [[name state-key always?]]
                 (when (get state state-key)
                   (let [args (get harness-args (keyword name) [])
                         skip-perms? (not= "false" (System/getenv "AISHELL_SKIP_PERMISSIONS"))
                         full-args (cond
                                     (and (= name "claude") skip-perms?)
                                     (into ["--dangerously-skip-permissions"] args)

                                     (= name "codex")
                                     (into ["-c" "check_for_update_on_startup=false"] args)

                                     :else
                                     (vec args))]
                     (when (or always? (seq full-args))
                       ["-e" (str "HARNESS_ALIAS_" (str/upper-case name)
                                  "=" name " " (str/join " " full-args))])))))
         (apply concat)
         vec)))

(def harness-config-dirs
  "Config directories required by each harness.
   Only directories for enabled harnesses are mounted."
  {:with-claude   [[".claude"] [".claude.json"]]
   :with-opencode [[".config" "opencode"] [".local" "share" "opencode"]]
   :with-codex    [[".codex"]]
   :with-gemini   [[".gemini"]]
   :with-pi       [[".pi"]]})

(def ^:private harness-config-files
  "Entries in harness-config-dirs that are files, not directories.
   These are not auto-created on the host."
  #{[".claude.json"]})

(defn- ensure-harness-config-dirs!
  "Create harness config directories on host if they don't exist.
   Ensures bind mounts work for first-time harness users.
   Skips file entries (e.g. .claude.json) — only creates directories."
  [config-entries home]
  (doseq [components config-entries]
    (when-not (harness-config-files components)
      (util/ensure-dir (str (apply fs/path home components))))))

(defn- build-harness-config-mounts
  "Build mount args for harness configuration directories.
   Only mounts directories for enabled harnesses that exist on host.
   On Windows: maps destinations under /home/developer (container home).
   On Unix: mounts at same path as source."
  [state]
  (let [home (util/get-home)
        container-home "/home/developer"
        config-entries (->> harness-config-dirs
                            (filter (fn [[state-key _]] (get state state-key)))
                            (mapcat val)
                            distinct)]
    (ensure-harness-config-dirs! config-entries home)
    (->> config-entries
         (map (fn [components]
                (let [src (str (apply fs/path home components))
                      dst (if (fs/windows?)
                            (str (apply fs/path container-home components))
                            src)]
                  [src dst])))
         (filter (fn [[src _]] (fs/exists? src)))
         (mapcat (fn [[src dst]] ["-v" (str (normalize-mount-source src) ":" dst)])))))

(def harness-api-keys
  "API key environment variables required by each harness.
   Only keys for enabled harnesses are passed into the container.
   Cross-cutting keys (GITHUB_TOKEN, AWS_*) must be added explicitly
   via config.yaml env: section."
  {:with-claude   ["ANTHROPIC_API_KEY"]
   :with-opencode ["OPENAI_API_KEY" "ANTHROPIC_API_KEY" "GROQ_API_KEY"
                    "OPENCODE_API_KEY" "AZURE_OPENAI_API_KEY" "AZURE_OPENAI_ENDPOINT"]
   :with-codex    ["OPENAI_API_KEY" "CODEX_API_KEY"]
   :with-gemini   ["GEMINI_API_KEY" "GOOGLE_API_KEY" "GOOGLE_CLOUD_PROJECT"
                    "GOOGLE_CLOUD_LOCATION" "GOOGLE_APPLICATION_CREDENTIALS"]
   :with-pi       ["PI_CODING_AGENT_DIR" "PI_SKIP_VERSION_CHECK"]})

(defn- build-api-env-args
  "Build -e flags for API keys required by enabled harnesses."
  [state]
  (let [enabled-keys (->> harness-api-keys
                          (filter (fn [[state-key _]] (get state state-key)))
                          (mapcat val)
                          distinct)]
    (->> enabled-keys
         (filter #(System/getenv %))
         (mapcat (fn [var] ["-e" (str var "=" (System/getenv var))])))))

(defn build-gcp-credentials-mount
  "Mount GCP service account credentials file if GOOGLE_APPLICATION_CREDENTIALS is set
   and the Gemini harness is enabled.
   The env var is passed through separately; this mounts the file it references."
  [state]
  (when (get state :with-gemini)
    (when-let [creds-path (System/getenv "GOOGLE_APPLICATION_CREDENTIALS")]
      (when (fs/exists? creds-path)
        ["-v" (str creds-path ":" creds-path ":ro")]))))

(defn- build-docker-args-internal
  "Internal helper to build docker run arguments.
   Shared by both build-docker-args and build-docker-args-for-exec."
  [{:keys [project-dir image-tag config state git-identity skip-pre-start skip-interactive container-name tty-flags harness-volume-name]}]
  (let [uid (get-uid)
        gid (get-gid)
        home (util/get-home)]
    (-> ["docker" "run" "--rm" "--init"]
        (into tty-flags)
        (cond-> container-name (into ["--name" container-name]))
        (into (let [mount-source (normalize-mount-source project-dir)
                    mount-dest (if (fs/windows?) "/workspace" project-dir)
                    container-home (if (fs/windows?) "/home/developer" home)]
                [;; Project mount
                 "-v" (str mount-source ":" mount-dest)
                 "-w" mount-dest
                 ;; User identity for entrypoint
                 "-e" (str "LOCAL_UID=" uid)
                 "-e" (str "LOCAL_GID=" gid)
                 "-e" (str "LOCAL_HOME=" container-home)
                 ;; Terminal settings
                 "-e" (str "TERM=" (or (System/getenv "TERM") "xterm-256color"))
                 "-e" (str "COLORTERM=" (or (System/getenv "COLORTERM") "truecolor"))]))

        ;; Git worktree support: mount the shared .git directory if in a worktree
        ;; Without this, git inside the container can't follow the gitdir pointer
        (into (if-let [git-common-dir (detect-worktree-git-dir project-dir)]
                ["-v" (str (normalize-mount-source git-common-dir) ":" git-common-dir)]
                []))

        ;; Git identity
        (cond-> (:name git-identity)
          (into ["-e" (str "GIT_AUTHOR_NAME=" (:name git-identity))
                 "-e" (str "GIT_COMMITTER_NAME=" (:name git-identity))]))
        (cond-> (:email git-identity)
          (into ["-e" (str "GIT_AUTHOR_EMAIL=" (:email git-identity))
                 "-e" (str "GIT_COMMITTER_EMAIL=" (:email git-identity))]))

        ;; Harness config mounts (only enabled harnesses)
        (into (build-harness-config-mounts state))

        ;; GCP credentials file mount (only when Gemini enabled)
        (into (or (build-gcp-credentials-mount state) []))

        ;; API keys (only enabled harnesses)
        (into (build-api-env-args state))

        ;; Disable autoupdater in container
        (into ["-e" "DISABLE_AUTOUPDATER=1"])

        ;; Harness volume mount (volume-mounted harness tools)
        (into (build-harness-volume-args harness-volume-name config))
        (into (build-harness-env-args harness-volume-name))

        ;; Harness aliases for interactive shell use
        ;; Skip when skip-interactive is true (non-interactive commands like exec/gitleaks)
        (cond-> (not skip-interactive)
          (into (build-harness-alias-env-args config state)))

        ;; Config: mounts
        (cond-> (:mounts config)
          (into (build-mount-args (:mounts config))))

        ;; Config: env
        (cond-> (:env config)
          (into (build-env-args (:env config))))

        ;; Config: ports
        (cond-> (:ports config)
          (into (build-port-args (:ports config))))

        ;; Config: pre_start (passed to entrypoint via env var)
        ;; Entrypoint handles execution: sh -c "$PRE_START" > /tmp/pre-start.log 2>&1 &
        ;; Skip pre_start if skip-pre-start flag is true (for gitleaks command)
        (cond-> (and (:pre_start config) (not skip-pre-start))
          (into ["-e" (str "PRE_START=" (:pre_start config))]))

        ;; Unset PRE_START if skip-pre-start is true
        (cond-> skip-pre-start
          (into ["-e" "PRE_START="]))

        ;; Config: docker_args (must be before image)
        (cond-> (:docker_args config)
          (into (tokenize-docker-args (:docker_args config))))

        ;; Image tag (must be last before command)
        (conj image-tag))))

(defn build-docker-args
  "Build complete docker run argument vector.

   Arguments:
   - project-dir: Absolute path to project
   - image-tag: Docker image to run
   - config: Parsed config map from config.clj (or nil)
   - git-identity: {:name \"...\" :email \"...\"} from read-git-identity
   - skip-pre-start: When true, disable pre_start hooks (for gitleaks command)
   - skip-interactive: When true, skip interactive features (harness aliases)

   Returns vector starting with [\"docker\" \"run\" ...] ready for p/exec.

   Note: PRE_START is passed as -e PRE_START=command. The entrypoint script
   (from Phase 14) handles execution: runs in background, logs to /tmp/pre-start.log."
  [{:keys [project-dir image-tag config state git-identity skip-pre-start skip-interactive container-name harness-volume-name]}]
  (build-docker-args-internal
    {:project-dir project-dir
     :image-tag image-tag
     :config config
     :state state
     :git-identity git-identity
     :skip-pre-start skip-pre-start
     :skip-interactive skip-interactive
     :container-name container-name
     :harness-volume-name harness-volume-name
     :tty-flags ["-it"]}))

(defn build-docker-args-for-exec
  "Build docker run arguments for one-off command execution.

   Key differences from build-docker-args:
   - Conditionally allocates TTY based on :tty? parameter
   - Always includes -i for stdin (required for piping)
   - Skips pre_start hooks (one-off commands shouldn't start sidecars)
   - Skips interactive features (harness aliases)

   Arguments:
   - project-dir: Absolute path to project
   - image-tag: Docker image to run
   - config: Parsed config map from config.clj (or nil)
   - git-identity: {:name \"...\" :email \"...\"} from read-git-identity
   - tty?: When true, allocate TTY (-it); when false, stdin only (-i)

   Returns vector starting with [\"docker\" \"run\" ...] ready for p/shell."
  [{:keys [project-dir image-tag config state git-identity tty? harness-volume-name]}]
  (build-docker-args-internal
    {:project-dir project-dir
     :image-tag image-tag
     :config config
     :state state
     :git-identity git-identity
     :skip-pre-start true  ; Always skip pre_start for exec
     :skip-interactive true  ; Skip interactive features for exec
     :harness-volume-name harness-volume-name
     :tty-flags (if tty? ["-it"] ["-i"])}))
