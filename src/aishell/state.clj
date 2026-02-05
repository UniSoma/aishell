(ns aishell.state
  "State persistence for build configuration.
   Stores build flags in ~/.aishell/state.edn so `aishell claude`
   knows what was built."
  (:require [babashka.fs :as fs]
            [clojure.edn :as edn]
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

(defn write-state
  "Write state to file, creating directory if needed.

   State schema (v2.8.0):
   {:with-claude true            ; boolean
    :with-opencode false         ; boolean
    :with-codex false            ; boolean
    :with-gemini false           ; boolean
    :with-gitleaks false         ; boolean (whether Gitleaks installed, default false, opt-in)
    :with-tmux false             ; boolean (whether tmux enabled, default false)
    :claude-version \"2.0.22\"   ; string or nil
    :opencode-version nil        ; string or nil
    :codex-version \"0.89.0\"    ; string or nil
    :gemini-version nil          ; string or nil
    :image-tag \"aishell:foundation\"  ; string
    :build-time \"2026-01-20...\" ; ISO-8601 string
    :dockerfile-hash \"abc123def456\" ; DEPRECATED: Use :foundation-hash
    :foundation-hash \"abc123def456\"  ; 12-char SHA-256 of foundation Dockerfile template
    :harness-volume-hash \"def789ghi012\" ; 12-char SHA-256 of enabled harnesses+versions
    :harness-volume-name \"aishell-harness-def789ghi012\"} ; Docker volume name for runtime mounting"
  [state]
  (let [path (state-file)]
    (util/ensure-dir (util/config-dir))
    (spit path (pr-str state))))
