(ns aishell.config
  "Per-project configuration loading from YAML.
   Supports .aishell/config.yaml in project dir with global fallback."
  (:require [clj-yaml.core :as yaml]
            [babashka.fs :as fs]
            [clojure.string :as cstr]
            [aishell.util :as util]
            [aishell.output :as output]))

(def known-keys
  "Valid config keys. Unknown keys trigger warning."
  #{:mounts :env :ports :docker_args :pre_start :extends :harness_args :gitleaks_freshness_check :gitleaks_freshness_threshold :detection :pi_packages})

(def known-harnesses
  "Valid harness names for harness_args validation."
  #{"claude" "opencode" "codex" "gemini" "vscode" "pi" "openspec"})

(defn project-config-path
  "Path to project config: PROJECT_DIR/.aishell/config.yaml"
  [project-dir]
  (str (fs/path project-dir ".aishell" "config.yaml")))

(defn global-config-path
  "Path to global config: ~/.aishell/config.yaml"
  []
  (str (fs/path (util/get-home) ".aishell" "config.yaml")))

(defn normalize-pre-start
  "Normalize pre_start: string passes through, list joins with ' && '.
   Filters empty items from lists. Returns string or nil."
  [pre-start-value]
  (cond
    (nil? pre-start-value) nil
    (string? pre-start-value) pre-start-value
    (sequential? pre-start-value)
    (let [filtered (filter (comp not clojure.string/blank?) (map str pre-start-value))]
      (when (seq filtered)
        (clojure.string/join " && " filtered)))
    :else nil))

(defn normalize-docker-args
  "Normalize docker_args to a vector at load time.
   String splits on whitespace, vector passes through, nil stays nil."
  [docker-args]
  (cond
    (nil? docker-args) nil
    (sequential? docker-args) (vec docker-args)
    (string? docker-args) (when-not (cstr/blank? docker-args)
                            (cstr/split (cstr/trim docker-args) #"\s+"))
    :else nil))

(defn normalize-harness-arg
  "Convert string to single-element list, pass lists through."
  [arg-val]
  (cond
    (nil? arg-val) []
    (string? arg-val) [arg-val]
    (sequential? arg-val) (vec arg-val)
    :else []))

(defn normalize-harness-args
  "Normalize harness_args map: convert string values to single-element lists."
  [harness-args-map]
  (when harness-args-map
    (into {}
          (map (fn [[k v]] [k (normalize-harness-arg v)])
               harness-args-map))))

(defn validate-harness-names
  "Warn if harness_args contains unknown harness names."
  [harness-args-map source-path]
  (when harness-args-map
    (let [config-harnesses (set (map name (keys harness-args-map)))
          unknown (clojure.set/difference config-harnesses known-harnesses)]
      (when (seq unknown)
        (output/warn (str "Unknown harness names in " source-path
                         " harness_args: "
                         (clojure.string/join ", " unknown)))))))

(defn validate-detection-config
  "Validate detection config. Warns on invalid severity and missing reason.
   Returns config unchanged."
  [detection-config source-path]
  (when detection-config
    ;; Validate custom_patterns severities
    (when-let [patterns (:custom_patterns detection-config)]
      (doseq [[pattern opts] patterns]
        (when-let [severity (:severity opts)]
          (when-not (contains? #{"high" "medium" "low"} severity)
            (output/warn (str "Invalid severity in " source-path
                             " custom pattern '" pattern "': " severity
                             "\nValid values: high, medium, low"))))))
    ;; Validate allowlist entries must be maps with :path and :reason
    (when-let [allowlist (:allowlist detection-config)]
      (doseq [entry allowlist]
        (cond
          (not (map? entry))
          (output/warn (str "Invalid allowlist entry in " source-path
                           ": must be a map with 'path' and 'reason' keys, got: " (pr-str entry)))
          (not (:path entry))
          (output/warn (str "Missing 'path' in " source-path " allowlist entry: " (pr-str entry)))
          (not (:reason entry))
          (output/warn (str "Missing 'reason' in " source-path
                           " allowlist entry for path: " (:path entry)))))))
  detection-config)

(defn validate-pi-packages
  "Validate pi_packages config. Warns if in project config (global-only).
   Validates entries are non-empty strings. Returns config unchanged."
  [pi-packages source-path]
  (when pi-packages
    ;; Warn if in project config (not global)
    (when (not= source-path (global-config-path))
      (output/warn (str "pi_packages in " source-path " will be ignored.\n"
                        "pi_packages is a global-only setting. Move it to: "
                        (global-config-path))))
    ;; Validate: must be sequential
    (when-not (sequential? pi-packages)
      (output/warn (str "pi_packages in " source-path " must be a list of strings")))
    ;; Validate entries are non-empty strings
    (when (sequential? pi-packages)
      (doseq [entry pi-packages]
        (when (or (not (string? entry)) (clojure.string/blank? entry))
          (output/warn (str "Invalid pi_packages entry in " source-path
                           ": must be a non-empty string, got: " (pr-str entry)))))))
  pi-packages)

(defn validate-config
  "Validate config map. Warns on unknown keys. Returns config unchanged."
  [config source-path]
  (when config
    (let [config-keys (set (keys config))
          unknown (clojure.set/difference config-keys known-keys)]
      (when (seq unknown)
        (output/warn (str "Unknown config keys in " source-path ": "
                         (clojure.string/join ", " (map name unknown))
                         "\nValid keys: mounts, env, ports, docker_args, pre_start, extends, harness_args, gitleaks_freshness_check, gitleaks_freshness_threshold, detection, pi_packages"))))
    (when-let [harness-args (:harness_args config)]
      (validate-harness-names harness-args source-path))
    (when-let [detection (:detection config)]
      (validate-detection-config detection source-path))
    (when-let [pi-packages (:pi_packages config)]
      (validate-pi-packages pi-packages source-path)))
  config)

(defn merge-harness-args
  "Merge harness_args maps: merge keys, concatenate per-harness lists.
   Global defaults come first (can be overridden by position)."
  [global-args project-args]
  (let [global-normalized (normalize-harness-args global-args)
        project-normalized (normalize-harness-args project-args)]
    (merge-with (fn [global-list project-list]
                  (vec (concat (or global-list [])
                              (or project-list []))))
                global-normalized
                project-normalized)))

(defn merge-detection
  "Custom merge for detection config.
   - enabled: project wins (scalar, use contains? for false values)
   - custom_patterns: map merge (project overrides global per-pattern)
   - allowlist: concatenate (both apply, list merge)"
  [global-detection project-detection]
  (let [enabled (cond
                  (contains? project-detection :enabled) (:enabled project-detection)
                  (contains? global-detection :enabled) (:enabled global-detection)
                  :else nil)
        patterns (merge (:custom_patterns global-detection)
                        (:custom_patterns project-detection))
        allowlist (vec (concat (:allowlist global-detection [])
                              (:allowlist project-detection [])))]
    (cond-> {}
      (some? enabled) (assoc :enabled enabled)
      (seq patterns) (assoc :custom_patterns patterns)
      (seq allowlist) (assoc :allowlist allowlist))))

(defn- normalize-env-to-map
  "Normalize env config to map format for merging.
   Array format [\"FOO=bar\" \"BAR\"] becomes {:FOO \"bar\" :BAR nil}.
   Map format passes through unchanged."
  [env]
  (if (map? env)
    env
    (into {}
      (map (fn [entry]
             (let [s (str entry)
                   idx (cstr/index-of s "=")]
               (if idx
                 [(keyword (subs s 0 idx)) (subs s (inc idx))]
                 [(keyword s) nil])))
           env))))

(defn merge-configs
  "Merge global-config and project-config with defined strategy.
   - Lists (mounts, ports, docker_args): concatenate (global + project)
   - Maps (env): shallow merge (project overrides global)
   - Map-of-lists (harness_args): merge keys, concatenate per-key lists
   - Scalars (pre_start): project replaces global
   - Detection: custom merge (enabled scalar, patterns map merge, allowlist concat)
   - Removes :extends key from result (internal-only)"
  [global-config project-config]
  (let [list-keys #{:mounts :ports :docker_args :pi_packages}
        map-keys #{:env}
        map-of-lists-keys #{:harness_args}
        scalar-keys #{:pre_start :gitleaks_freshness_check}
        ;; Extract detection config before reduce
        global-detection (get global-config :detection)
        project-detection (get project-config :detection)
        merged (reduce
                (fn [acc k]
                  (cond
                    ;; Skip :detection - handled separately after reduce
                    (= k :detection) acc

                    ;; List keys - concatenate
                    (contains? list-keys k)
                    (let [global-val (get global-config k)
                          project-val (get project-config k)]
                      (if (or global-val project-val)
                        (assoc acc k (vec (concat (or global-val []) (or project-val []))))
                        acc))

                    ;; Map keys - normalize to map then shallow merge
                    (contains? map-keys k)
                    (let [global-val (get global-config k)
                          project-val (get project-config k)]
                      (if (or global-val project-val)
                        (assoc acc k (merge (normalize-env-to-map (or global-val {}))
                                            (normalize-env-to-map (or project-val {}))))
                        acc))

                    ;; Map-of-lists keys - merge keys, concatenate lists
                    (contains? map-of-lists-keys k)
                    (let [global-val (get global-config k)
                          project-val (get project-config k)]
                      (if (or global-val project-val)
                        (assoc acc k (merge-harness-args global-val project-val))
                        acc))

                    ;; Scalar keys - project wins (use contains? to handle false values)
                    (contains? scalar-keys k)
                    (cond
                      (contains? project-config k) (assoc acc k (get project-config k))
                      (contains? global-config k) (assoc acc k (get global-config k))
                      :else acc)

                    :else acc))
                {}
                (clojure.set/union (set (keys global-config)) (set (keys project-config))))
        ;; Merge detection config using custom strategy
        merged-with-detection (if (or global-detection project-detection)
                                (assoc merged :detection
                                       (merge-detection (or global-detection {})
                                                       (or project-detection {})))
                                merged)]
    ;; Remove :extends key from result
    (dissoc merged-with-detection :extends)))

(defn load-yaml-config
  "Load and parse YAML config from path. Returns parsed config or nil on error."
  [path]
  (when (fs/exists? path)
    (try
      (let [parsed (-> (slurp path)
                       yaml/parse-string
                       (validate-config path))
            ;; Global-only keys must not come from project config.
            parsed (if (= path (global-config-path))
                     parsed
                     (dissoc parsed :pi_packages))]
        (cond-> parsed
          (contains? parsed :pre_start) (update :pre_start normalize-pre-start)
          (contains? parsed :docker_args) (update :docker_args normalize-docker-args)))
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
