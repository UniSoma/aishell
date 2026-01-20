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

(defn- build-env-args
  "Build -e flags from env config.

   In YAML, env is a map:
   - key with nil value: passthrough from host (VAR:)
   - key with value: literal (VAR: value)

   Skips passthrough vars not set on host with warning."
  [env-map]
  (when (seq env-map)
    (->> env-map
         (mapcat
           (fn [[k v]]
             (let [key-name (name k)]
               (if (nil? v)
                 ;; Passthrough: only add if set on host
                 (if-let [host-val (System/getenv key-name)]
                   ["-e" key-name]
                   (do
                     (output/warn (str "Skipping unset host variable: " key-name))
                     []))
                 ;; Literal value
                 ["-e" (str key-name "=" v)])))))))

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
  "Split docker_args string into individual args.
   Simple whitespace split - complex quoting not supported (documented limitation)."
  [docker-args-str]
  (when (and docker-args-str (not (str/blank? docker-args-str)))
    (str/split (str/trim docker-args-str) #"\s+")))

(defn- build-harness-config-mounts
  "Build mount args for harness configuration directories.
   Only mounts directories that exist on host."
  []
  (let [home (util/get-home)
        config-paths [[(str home "/.claude") (str home "/.claude")]
                      [(str home "/.claude.json") (str home "/.claude.json")]
                      [(str home "/.config/opencode") (str home "/.config/opencode")]
                      [(str home "/.local/share/opencode") (str home "/.local/share/opencode")]]]
    (->> config-paths
         (filter (fn [[src _]] (fs/exists? src)))
         (mapcat (fn [[src dst]] ["-v" (str src ":" dst)])))))

(def api-key-vars
  "Environment variables to pass through for API access."
  ["ANTHROPIC_API_KEY"
   "OPENAI_API_KEY"
   "GEMINI_API_KEY"
   "GROQ_API_KEY"
   "GITHUB_TOKEN"
   "AWS_ACCESS_KEY_ID"
   "AWS_SECRET_ACCESS_KEY"
   "AWS_REGION"
   "AWS_PROFILE"
   "AZURE_OPENAI_API_KEY"
   "AZURE_OPENAI_ENDPOINT"
   "GOOGLE_CLOUD_PROJECT"
   "GOOGLE_APPLICATION_CREDENTIALS"])

(defn- build-api-env-args
  "Build -e flags for API keys that are set on host."
  []
  (->> api-key-vars
       (filter #(System/getenv %))
       (mapcat (fn [var] ["-e" (str var "=" (System/getenv var))]))))
