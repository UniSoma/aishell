(ns aishell.state
  "State persistence for build configuration.
   Stores build flags in ~/.aishell/state.edn so `aishell claude`
   knows what was built."
  (:require [babashka.fs :as fs]
            [clojure.edn :as edn]
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

(def removed-keys
  "State keys for harnesses aishell no longer supports.
   Dropped on every state write; presence in a saved state triggers a
   one-time removal warning."
  [:with-openspec :openspec-version])

(defn strip-removed-keys
  "Remove keys for no-longer-supported harnesses from a state map."
  [state]
  (when state
    (apply dissoc state removed-keys)))

(defn removed-harness-warning
  "Return a removal warning for a saved state that had a removed harness
   enabled, or nil when there is nothing to warn about.

   Keyed on the flag being truthy, not merely present: every setup since
   v3.7.0 wrote :with-openspec false into state, so presence would warn
   users who never enabled it."
  [state]
  (when (or (:with-openspec state) (:openspec-version state))
    (str "OpenSpec support was removed from aishell. "
         "Its setup flag and state are gone, and the harness volume "
         "repopulates without it on the next setup or update.")))

(defn write-state
  "Write state to file, creating directory if needed.

   State schema (v3.7.0):
   {:with-claude true            ; boolean
    :with-opencode false         ; boolean
    :with-codex false            ; boolean
    :with-gemini false           ; boolean
    :with-pi false               ; boolean
    :with-gitleaks false         ; boolean (whether Gitleaks installed, default false, opt-in)
    :unisoma false               ; boolean (UniSoma OpenCode provider whitelist, default false)
    :claude-version \"2.0.22\"   ; string or nil
    :opencode-version nil        ; string or nil
    :codex-version \"0.89.0\"    ; string or nil
    :gemini-version nil          ; string or nil
    :pi-version nil              ; string or nil
    :image-tag \"aishell:foundation\"  ; string
    :build-time \"2026-01-20...\" ; ISO-8601 string
    :foundation-hash \"abc123def456\"  ; 12-char SHA-256 of foundation Dockerfile template
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
