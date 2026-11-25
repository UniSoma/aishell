(ns aishell.state
  "State persistence for build configuration.
   Stores build flags in ~/.aishell/state.edn so `aishell claude`
   knows what was built."
  (:require [babashka.fs :as fs]
            [clojure.edn :as edn]
            [clojure.string :as str]
            [aishell.output :as output]
            [aishell.util :as util]))

(defn state-file
  "Path to global state file: ~/.aishell/state.edn"
  []
  (str (fs/path (util/config-dir) "state.edn")))

(defn read-state
  "Read state from file.
   Returns nil if file doesn't exist (not an error)."
  []
  (let [path (state-file)]
    (when (fs/exists? path)
      (edn/read-string (slurp path)))))

(def ^:private removed-harnesses
  "Harnesses aishell no longer supports: their state keys and the warning a
   saved state that still enables one gets."
  [{:keys [:with-openspec :openspec-version]
    :message (str "OpenSpec support was removed from aishell. "
                  "Its setup flag and state are gone, and the harness volume "
                  "repopulates without it on the next setup or update.")}
   {:keys [:with-gitleaks]
    :message (str "Gitleaks support was removed from aishell. "
                  "The Foundation image rebuilds without it on the next setup or update; "
                  "docs/CONFIGURATION.md (Secret scanning) shows where gitleaks goes now.")}])

(def removed-keys
  "State keys for harnesses aishell no longer supports.
   Dropped on every state write; presence in a saved state triggers a
   one-time removal warning."
  (into [] (mapcat :keys) removed-harnesses))

(defn strip-removed-keys
  "Remove keys for no-longer-supported harnesses from a state map."
  [state]
  (when state
    (apply dissoc state removed-keys)))

(defn removed-harness-warning
  "Return one string holding the removal warning of every removed harness
   the saved state enables, or nil when it enables none.

   Keyed on the flag being truthy, not merely present: setup wrote
   :with-openspec (since v3.7.0) and :with-gitleaks as false into state when
   they were not enabled, so presence would warn users who never enabled
   them."
  [state]
  (some->> removed-harnesses
           (filter (fn [{:keys [keys]}] (some #(get state %) keys)))
           (map :message)
           seq
           (str/join " ")))

(defn foundation-hash
  "Return the Foundation content hash recorded in `state`: `:foundation-hash`,
   or `:dockerfile-hash` for state written before v2.8.0. Nil when neither
   is present."
  [state]
  (or (:foundation-hash state) (:dockerfile-hash state)))

(defn assoc-foundation-hash
  "Record `content-hash` in `state` under both `:dockerfile-hash`, the only
   key older aishell versions read, and `:foundation-hash`, the key
   `foundation-hash` reads first."
  [state content-hash]
  (assoc state
         :dockerfile-hash content-hash
         :foundation-hash content-hash))

(defn write-state
  "Write state to file, creating directory if needed.

   State schema (v3.7.0):
   {:with-claude true            ; boolean
    :with-opencode false         ; boolean
    :with-codex false            ; boolean
    :with-copilot false          ; boolean
    :with-gemini false           ; boolean
    :with-pi false               ; boolean
    :with-omp false              ; boolean
    :unisoma false               ; boolean (UniSoma OpenCode provider whitelist, default false)
    :claude-version \"2.0.22\"   ; string or nil
    :opencode-version nil        ; string or nil
    :codex-version \"0.89.0\"    ; string or nil
    :copilot-version nil         ; string or nil
    :gemini-version nil          ; string or nil
    :pi-version nil              ; string or nil
    :omp-version nil             ; string or nil
    :build-time \"2026-01-20...\" ; ISO-8601 string
    :foundation-hash \"abc123def456\"  ; 12-char SHA-256 of the Foundation image's content; see foundation-hash
    :dockerfile-hash \"abc123def456\"  ; same hash under its pre-v2.8.0 name, kept for older aishell versions
    :harness-volume-hash \"def789ghi012\" ; 12-char SHA-256 of enabled harnesses+versions
    :harness-volume-name \"aishell-harness-def789ghi012\"} ; Docker volume name for runtime mounting"
  [state]
  (let [path (state-file)]
    (util/ensure-dir (util/config-dir))
    (spit path (pr-str (strip-removed-keys state)))))

(defn warn-removed-harnesses!
  "Warn once about harnesses that aishell no longer supports.

   When the saved state still carries removed-harness keys, print the
   warning and immediately persist the stripped state, so the next
   command is silent."
  []
  (let [state (read-state)]
    (when-let [msg (removed-harness-warning state)]
      (output/warn msg)
      (write-state state))))
