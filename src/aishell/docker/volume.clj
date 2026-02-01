(ns aishell.docker.volume
  "Deterministic harness hash computation and volume naming.

   Harness volumes are keyed by hash of enabled harnesses + versions.
   This ensures projects with identical harness combinations share the
   same volume, reducing disk usage and improving performance."
  (:require [aishell.docker.hash :as hash]
            [aishell.docker.build :as build]
            [aishell.docker.spinner :as spinner]
            [aishell.output :as output]
            [babashka.process :as p]
            [clojure.string :as str]))

(def ^:private harness-keys
  "Ordered list of harness names for deterministic sorting."
  [:claude :codex :gemini :opencode])

(def ^:private harness-npm-packages
  "Map of harness key to npm package name.
   OpenCode is excluded as it's a Go binary, not an npm package."
  {:claude "@anthropic-ai/claude-code"
   :codex "@codex-ai/codex"
   :gemini "@google/generative-ai-cli"})

(defn normalize-harness-config
  "Extract and normalize harness configuration for deterministic hashing.

   Arguments:
   - state: State map from state/read-state with flat structure:
            {:with-claude true, :claude-version \"2.0.22\",
             :with-opencode false, :opencode-version nil, ...}

   Returns: Sorted vector of [harness-keyword version-string] pairs.
            Only includes harnesses where :with-{name} is truthy.
            Nil versions become \"latest\".
            Example: [[:claude \"2.0.22\"] [:codex \"0.89.0\"]]

   Guarantees:
   - Disabled harnesses excluded from output
   - Map iteration order doesn't affect result (explicit sorting)
   - Same configuration always produces same canonical form
   - nil versions consistently normalized to \"latest\""
  [state]
  (->> harness-keys
       (filter #(get state (keyword (str "with-" (name %)))))
       (map (fn [harness-kw]
              (let [version-key (keyword (str (name harness-kw) "-version"))
                    version (get state version-key)]
                [harness-kw (or version "latest")])))
       (sort-by first)
       vec))

(defn compute-harness-hash
  "Compute deterministic hash from harness configuration.

   Arguments:
   - state: State map from state/read-state

   Returns: 12-character hex string (SHA-256 hash prefix)

   Process:
   1. Normalize config to canonical sorted form
   2. Serialize with pr-str
   3. Hash with SHA-256, take first 12 chars

   Guarantees:
   - Same harness flags/versions always produce same hash
   - Different configurations produce different hashes
   - Order-independent (map key order doesn't matter)
   - Disabled harnesses don't affect hash"
  [state]
  (-> state
      normalize-harness-config
      pr-str
      hash/compute-hash))

(defn volume-name
  "Generate volume name from harness hash.

   Arguments:
   - hash: 12-character hex hash from compute-harness-hash

   Returns: Volume name string following aishell-harness-{hash} pattern

   Example: \"aishell-harness-abc123def456\""
  [hash]
  (str "aishell-harness-" hash))

(defn volume-exists?
  "Check if Docker volume exists by name.

   Arguments:
   - name: Volume name string

   Returns: true if volume exists, false otherwise

   Implementation:
   - Runs `docker volume inspect {name}`
   - Returns true if exit code is 0 (volume exists)
   - Returns false if exit code is non-zero or exception occurs"
  [name]
  (try
    (let [{:keys [exit]} (p/shell {:continue true
                                   :out :string
                                   :err :string}
                                  "docker" "volume" "inspect" name)]
      (zero? exit))
    (catch Exception _
      false)))

(defn get-volume-label
  "Get a specific label value from a Docker volume.

   Arguments:
   - volume-name: Volume name string
   - label-key: Label key string (e.g., \"aishell.harness.hash\")

   Returns: Trimmed label value string if volume exists and has label, nil otherwise

   Implementation:
   - Runs `docker volume inspect --format '{{index .Labels \"label-key\"}}' volume-name`
   - Returns trimmed string value if exit code is 0
   - Returns nil if volume doesn't exist or label is missing
   - Returns nil if exception occurs"
  [volume-name label-key]
  (try
    (let [format-str (str "{{index .Labels \"" label-key "\"}}")
          {:keys [exit out]} (p/shell {:continue true
                                       :out :string
                                       :err :string}
                                      "docker" "volume" "inspect"
                                      "--format" format-str
                                      volume-name)]
      (when (zero? exit)
        (str/trim out)))
    (catch Exception _
      nil)))

(defn create-volume
  "Create Docker volume with metadata labels.

   Arguments:
   - volume-name: Volume name string
   - labels: Map of label keys to values (e.g., {\"aishell.harness.hash\" \"abc123\"})

   Returns: volume-name on success

   Throws: Exception on failure

   Implementation:
   - Builds command: `docker volume create --label key=value ... volume-name`
   - Expands labels map into --label flags
   - Throws if docker command fails"
  [volume-name labels]
  (let [label-args (mapcat (fn [[k v]]
                             ["--label" (str k "=" v)])
                           labels)
        cmd (concat ["docker" "volume" "create"] label-args [volume-name])]
    (apply p/shell cmd)
    volume-name))

(defn remove-volume
  "Remove Docker volume by name. Silently ignores errors (volume may not exist)."
  [volume-name]
  (try
    (p/shell {:out :string :err :string :continue true}
             "docker" "volume" "rm" volume-name)
    (catch Exception _ nil)))

(defn build-install-commands
  "Build shell command string for installing harness tools into volume.

   Arguments:
   - state: State map with harness flags and versions
            {:with-claude true, :claude-version \"2.0.22\", ...}

   Returns: Shell command string that:
            1. Sets NPM_CONFIG_PREFIX to /tools/npm
            2. Installs enabled npm-based harnesses
            3. Sets world-readable permissions with chmod

   Example output:
   \"export NPM_CONFIG_PREFIX=/tools/npm && npm install -g @anthropic-ai/claude-code@2.0.22 @codex-ai/codex@0.89.0 && chmod -R a+rX /tools\"

   Notes:
   - OpenCode is excluded (Go binary, not npm package)
   - Nil versions become \"latest\"
   - Only enabled harnesses are included"
  [state]
  (let [;; Build list of package@version strings for enabled harnesses
        packages (keep (fn [[harness-kw package-name]]
                         (when (get state (keyword (str "with-" (name harness-kw))))
                           (let [version-key (keyword (str (name harness-kw) "-version"))
                                 version (or (get state version-key) "latest")]
                             (str package-name "@" version))))
                       harness-npm-packages)
        ;; Join packages into npm install command
        npm-install (when (seq packages)
                      (str "npm install -g " (str/join " " packages)))]
    ;; Build complete command string
    (str "export NPM_CONFIG_PREFIX=/tools/npm"
         (when npm-install (str " && " npm-install))
         " && chmod -R a+rX /tools")))

(defn populate-volume
  "Install harness tools into Docker volume via temporary container.

   Arguments:
   - volume-name: Volume name string
   - state: State map with harness flags and versions
   - opts: Optional map with :verbose key for output control

   Returns: {:success true :volume volume-name} on success

   Process:
   1. Generate npm install commands based on enabled harnesses
   2. Run temporary container mounting volume at /tools
   3. Execute npm install commands inside container
   4. Set world-readable permissions for non-root execution
   5. Container is automatically removed (--rm flag)

   Output handling:
   - :verbose true -> Show all npm output
   - :verbose false -> Show spinner, suppress output

   Notes:
   - Uses foundation image from build/foundation-image-tag
   - OpenCode excluded (Go binary, not npm package)
   - Volume must exist before calling this function"
  [volume-name state & [opts]]
  (let [install-commands (build-install-commands state)
        verbose? (:verbose opts)
        cmd ["docker" "run" "--rm"
             "-v" (str volume-name ":/tools")
             "--entrypoint" ""
             build/foundation-image-tag
             "sh" "-c" install-commands]]
    (try
      (if verbose?
        ;; Verbose: inherit output streams
        (let [{:keys [exit]} (apply p/process {:out :inherit
                                               :err :inherit}
                                    cmd)]
          {:success (zero? @exit) :volume volume-name})
        ;; Silent: wrap in spinner
        (let [result (spinner/with-spinner "Populating harness volume"
                                           #(let [{:keys [exit err]} (apply p/shell {:out :string
                                                                                     :err :string
                                                                                     :continue true}
                                                                            cmd)]
                                              (when-not (zero? exit)
                                                (binding [*out* *err*]
                                                  (println err)))
                                              (zero? exit)))]
          {:success result :volume volume-name}))
      (catch Exception e
        (binding [*out* *err*]
          (println (str "Exception during volume population: " (.getMessage e))))
        {:success false :volume volume-name}))))
