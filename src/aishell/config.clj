(ns aishell.config
  "Per-project configuration loading from YAML.
   Supports .aishell/config.yaml in project dir with global fallback."
  (:require [clj-yaml.core :as yaml]
            [babashka.fs :as fs]
            [aishell.util :as util]
            [aishell.output :as output]))

(def known-keys
  "Valid config keys. Unknown keys trigger warning."
  #{:mounts :env :ports :docker_args :pre_start})

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
                         "\nValid keys: mounts, env, ports, docker_args, pre_start")))))
  config)

(defn load-config
  "Load config.yaml with project-first, global-fallback strategy.

   Lookup order:
   1. PROJECT_DIR/.aishell/config.yaml (if exists)
   2. ~/.aishell/config.yaml (if project config missing)
   3. nil (if neither exists)

   Exits with error on invalid YAML syntax.
   Warns on unknown keys but continues."
  [project-dir]
  (let [project-path (project-config-path project-dir)
        global-path (global-config-path)]
    (cond
      ;; Project config exists - use it
      (fs/exists? project-path)
      (try
        (-> (slurp project-path)
            yaml/parse-string
            (validate-config project-path))
        (catch Exception e
          (output/error (str "Invalid YAML in " project-path ": " (.getMessage e)))))

      ;; Fall back to global config
      (fs/exists? global-path)
      (try
        (-> (slurp global-path)
            yaml/parse-string
            (validate-config global-path))
        (catch Exception e
          (output/error (str "Invalid YAML in " global-path ": " (.getMessage e)))))

      ;; No config - return nil
      :else nil)))

(defn config-source
  "Return which config was loaded: :project, :global, or nil"
  [project-dir]
  (let [project-path (project-config-path project-dir)
        global-path (global-config-path)]
    (cond
      (fs/exists? project-path) :project
      (fs/exists? global-path) :global
      :else nil)))
