(ns aishell.config
  "Per-project configuration loading from YAML.
   Supports .aishell/config.yaml in project dir with global fallback."
  (:require [clj-yaml.core :as yaml]
            [babashka.fs :as fs]
            [aishell.util :as util]
            [aishell.output :as output]))

(def known-keys
  "Valid config keys. Unknown keys trigger warning."
  #{:mounts :env :ports :docker_args :pre_start :extends})

(defn project-config-path
  "Path to project config: PROJECT_DIR/.aishell/config.yaml"
  [project-dir]
  (str (fs/path project-dir ".aishell" "config.yaml")))

(defn global-config-path
  "Path to global config: ~/.aishell/config.yaml"
  []
  (str (fs/path (util/get-home) ".aishell" "config.yaml")))

(defn validate-config
  "Validate config map. Warns on unknown keys. Returns config unchanged."
  [config source-path]
  (when config
    (let [config-keys (set (keys config))
          unknown (clojure.set/difference config-keys known-keys)]
      (when (seq unknown)
        (output/warn (str "Unknown config keys in " source-path ": "
                         (clojure.string/join ", " (map name unknown))
                         "\nValid keys: mounts, env, ports, docker_args, pre_start, extends")))))
  config)

(defn merge-configs
  "Merge global-config and project-config with defined strategy.
   - Lists (mounts, ports, docker_args): concatenate (global + project)
   - Maps (env): shallow merge (project overrides global)
   - Scalars (pre_start): project replaces global
   - Removes :extends key from result (internal-only)"
  [global-config project-config]
  (let [list-keys #{:mounts :ports :docker_args}
        map-keys #{:env}
        scalar-keys #{:pre_start}
        merged (reduce
                (fn [acc k]
                  (cond
                    ;; List keys - concatenate
                    (contains? list-keys k)
                    (let [global-val (get global-config k)
                          project-val (get project-config k)]
                      (if (or global-val project-val)
                        (assoc acc k (vec (concat (or global-val []) (or project-val []))))
                        acc))

                    ;; Map keys - shallow merge
                    (contains? map-keys k)
                    (let [global-val (get global-config k)
                          project-val (get project-config k)]
                      (if (or global-val project-val)
                        (assoc acc k (merge (or global-val {}) (or project-val {})))
                        acc))

                    ;; Scalar keys - project wins
                    (contains? scalar-keys k)
                    (if-let [project-val (get project-config k)]
                      (assoc acc k project-val)
                      (if-let [global-val (get global-config k)]
                        (assoc acc k global-val)
                        acc))

                    :else acc))
                {}
                (clojure.set/union (set (keys global-config)) (set (keys project-config))))]
    ;; Remove :extends key from result
    (dissoc merged :extends)))

(defn load-yaml-config
  "Load and parse YAML config from path. Returns parsed config or nil on error."
  [path]
  (when (fs/exists? path)
    (try
      (-> (slurp path)
          yaml/parse-string
          (validate-config path))
      (catch Exception e
        (output/error (str "Invalid YAML in " path ": " (.getMessage e)))))))

(defn load-config
  "Load config.yaml with merge strategy based on extends key.

   Merge strategy (when project config exists):
   - extends: 'global' (default): Merge project with global config
     - Lists concatenate (global + project)
     - Maps shallow merge (project overrides global)
     - Scalars replace (project wins)
   - extends: 'none': Project config fully replaces global (no merging)

   Fallback: Uses global config if no project config exists.
   Returns nil if neither config exists."
  [project-dir]
  (let [project-path (project-config-path project-dir)
        global-path (global-config-path)
        project-config (load-yaml-config project-path)
        global-config (load-yaml-config global-path)]
    (cond
      ;; Project config exists - check extends strategy
      project-config
      (let [extends-val (get project-config :extends "global")]
        (if (= extends-val "none")
          ;; extends: none - return project config as-is (remove extends key)
          (dissoc project-config :extends)
          ;; extends: global (or missing) - merge configs
          (if global-config
            (merge-configs global-config project-config)
            ;; No global config to merge with - return project config without extends key
            (dissoc project-config :extends))))

      ;; No project config - fall back to global
      global-config global-config

      ;; No config at all
      :else nil)))

(defn config-source
  "Return which config was loaded: :project, :global, :merged, or nil"
  [project-dir]
  (let [project-path (project-config-path project-dir)
        global-path (global-config-path)
        project-exists? (fs/exists? project-path)
        global-exists? (fs/exists? global-path)]
    (cond
      ;; Project config exists - check extends strategy
      project-exists?
      (let [project-config (load-yaml-config project-path)
            extends-val (get project-config :extends "global")]
        (if (and (= extends-val "global") global-exists?)
          :merged
          :project))

      ;; No project config - check global
      global-exists? :global

      ;; No config at all
      :else nil)))
