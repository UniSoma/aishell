(ns aishell.docker.run
  "Docker run argument construction.
   Builds the full docker run command vector from config and runtime info."
  (:require [babashka.process :as p]
            [babashka.fs :as fs]
            [clojure.string :as str]
            [aishell.util :as util]
            [aishell.output :as output]))

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

(defn- get-uid []
  (-> (p/shell {:out :string} "id" "-u") :out str/trim))

(defn- get-gid []
  (-> (p/shell {:out :string} "id" "-g") :out str/trim))

(defn- build-mount-args
  "Build -v flags from mounts config.

   Supports:
   - source-only: ~/.ssh (mounts at same path)
   - source:dest: /host/path:/container/path

   Expands ~ and $HOME in paths. Warns if source doesn't exist."
  [mounts]
  (when (seq mounts)
    (->> mounts
         (mapcat
           (fn [mount]
             (let [mount-str (str mount)
                   [source dest] (if (str/includes? mount-str ":")
                                   (str/split mount-str #":" 2)
                                   [mount-str mount-str])
                   source (util/expand-path source)
                   dest (util/expand-path dest)]
               (if (fs/exists? source)
                 ["-v" (str source ":" dest)]
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

(defn- build-harness-config-mounts
  "Build mount args for harness configuration directories.
   Only mounts directories that exist on host."
  []
  (let [home (util/get-home)
        config-paths [[(str home "/.claude") (str home "/.claude")]
                      [(str home "/.claude.json") (str home "/.claude.json")]
                      [(str home "/.config/opencode") (str home "/.config/opencode")]
                      [(str home "/.local/share/opencode") (str home "/.local/share/opencode")]
                      ;; Codex CLI - uses ~/.codex/ for auth.json and config.toml
                      [(str home "/.codex") (str home "/.codex")]
                      ;; Gemini CLI - uses ~/.gemini/ for settings.json
                      [(str home "/.gemini") (str home "/.gemini")]]]
    (->> config-paths
         (filter (fn [[src _]] (fs/exists? src)))
         (mapcat (fn [[src dst]] ["-v" (str src ":" dst)])))))

(def api-key-vars
  "Environment variables to pass through for API access."
  ["ANTHROPIC_API_KEY"
   "OPENAI_API_KEY"
   "CODEX_API_KEY"
   "GEMINI_API_KEY"
   "GOOGLE_API_KEY"
   "GROQ_API_KEY"
   "GITHUB_TOKEN"
   "AWS_ACCESS_KEY_ID"
   "AWS_SECRET_ACCESS_KEY"
   "AWS_REGION"
   "AWS_PROFILE"
   "AZURE_OPENAI_API_KEY"
   "AZURE_OPENAI_ENDPOINT"
   "GOOGLE_CLOUD_PROJECT"
   "GOOGLE_CLOUD_LOCATION"
   "GOOGLE_APPLICATION_CREDENTIALS"])

(defn- build-api-env-args
  "Build -e flags for API keys that are set on host."
  []
  (->> api-key-vars
       (filter #(System/getenv %))
       (mapcat (fn [var] ["-e" (str var "=" (System/getenv var))]))))

(defn build-gcp-credentials-mount
  "Mount GCP service account credentials file if GOOGLE_APPLICATION_CREDENTIALS is set.
   The env var is passed through separately; this mounts the file it references."
  []
  (when-let [creds-path (System/getenv "GOOGLE_APPLICATION_CREDENTIALS")]
    (when (fs/exists? creds-path)
      ["-v" (str creds-path ":" creds-path ":ro")])))

(defn build-docker-args
  "Build complete docker run argument vector.

   Arguments:
   - project-dir: Absolute path to project
   - image-tag: Docker image to run
   - config: Parsed config map from config.clj (or nil)
   - git-identity: {:name \"...\" :email \"...\"} from read-git-identity
   - skip-pre-start: When true, disable pre_start hooks (for gitleaks command)

   Returns vector starting with [\"docker\" \"run\" ...] ready for p/exec.

   Note: PRE_START is passed as -e PRE_START=command. The entrypoint script
   (from Phase 14) handles execution: runs in background, logs to /tmp/pre-start.log."
  [{:keys [project-dir image-tag config git-identity skip-pre-start]}]
  (let [uid (get-uid)
        gid (get-gid)
        home (util/get-home)]
    (-> ["docker" "run"
         "--rm" "-it" "--init"
         ;; Project mount at same path
         "-v" (str project-dir ":" project-dir)
         "-w" project-dir
         ;; User identity for entrypoint
         "-e" (str "LOCAL_UID=" uid)
         "-e" (str "LOCAL_GID=" gid)
         "-e" (str "LOCAL_HOME=" home)
         ;; Terminal settings
         "-e" (str "TERM=" (or (System/getenv "TERM") "xterm-256color"))
         "-e" (str "COLORTERM=" (or (System/getenv "COLORTERM") "truecolor"))]

        ;; Git identity
        (cond-> (:name git-identity)
          (into ["-e" (str "GIT_AUTHOR_NAME=" (:name git-identity))
                 "-e" (str "GIT_COMMITTER_NAME=" (:name git-identity))]))
        (cond-> (:email git-identity)
          (into ["-e" (str "GIT_AUTHOR_EMAIL=" (:email git-identity))
                 "-e" (str "GIT_COMMITTER_EMAIL=" (:email git-identity))]))

        ;; Harness config mounts (Claude, OpenCode, Codex, Gemini configs)
        (into (build-harness-config-mounts))

        ;; GCP credentials file mount (for Vertex AI authentication)
        (into (or (build-gcp-credentials-mount) []))

        ;; API keys
        (into (build-api-env-args))

        ;; Disable autoupdater in container
        (into ["-e" "DISABLE_AUTOUPDATER=1"])

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
