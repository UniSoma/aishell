(ns aishell.docker.volume
  "Deterministic harness hash computation and volume naming.

   Harness volumes are keyed by hash of enabled harnesses + versions.
   This ensures projects with identical harness combinations share the
   same volume, reducing disk usage and improving performance."
  (:require [aishell.docker.hash :as hash]))

(def ^:private harness-keys
  "Ordered list of harness names for deterministic sorting."
  [:claude :codex :gemini :opencode])

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
