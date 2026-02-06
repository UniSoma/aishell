# Phase 47: State & Config Schema Cleanup - Research

**Researched:** 2026-02-06
**Domain:** Clojure schema cleanup, CLI argument removal, state file migration
**Confidence:** HIGH

## Summary

Phase 47 removes all tmux-related configuration from aishell's CLI, state persistence, and config schema as the second phase of v3.0.0 milestone. Phase 46 already removed the tmux binary from the foundation image, making all tmux-related configuration effectively dead code. This phase focuses on three interconnected cleanup operations: (1) removing `--with-tmux` from the CLI build command spec, (2) removing `:with-tmux` and `:tmux-plugins` from the state.edn schema, and (3) removing the `tmux:` section from config.yaml validation and merge logic.

The research confirms this is a straightforward schema cleanup task with well-established Clojure patterns. The codebase uses babashka.cli for CLI argument parsing with declarative specs (maps defining flags and their types), plain EDN maps for state persistence (no formal schema library), and clj-yaml for config validation with manual key checking. All three systems use simple `dissoc` operations to remove keys, with no complex migration logic required since the features are being permanently removed rather than transformed.

The key insight is that this phase can be completed with pure deletion—no state migration needed. Users who have `:with-tmux true` in their state.edn will simply get that flag ignored after Phase 47. The next `aishell setup` command will overwrite state.edn without tmux keys. Config files with `tmux:` sections will trigger unknown-key warnings (existing validation pattern) but won't break functionality. This graceful degradation approach aligns with the project's v2.9.0 migration strategy (warning-based, not error-based).

**Primary recommendation:** Remove tmux entries from three schema locations (cli.clj setup-spec, state.clj write-state docstring, config.clj known-keys and validate-tmux-config). Remove tmux from help text order arrays. Remove conditional tmux logic from setup/update handlers. Verify with manual testing that setup/update/check commands work and help output shows no tmux options.

## Standard Stack

This phase uses existing libraries and patterns already present in the codebase:

### Core Technologies
| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| babashka.cli | ~0.7.x | CLI argument parsing | Declarative spec-based CLI, used throughout aishell for all commands |
| clojure.edn | core | State file serialization | Standard Clojure data format, human-readable and editable |
| clj-yaml.core | ~1.0.x | YAML config parsing | Standard YAML library for Clojure, used for config.yaml loading |

### Supporting Patterns
| Pattern | Location | Purpose | When to Use |
|---------|----------|---------|-------------|
| Dissoc for key removal | config.clj:283,320,325 | Remove keys from maps | Deleting schema keys, cleaning up internal state |
| Manual schema validation | config.clj:171-187 | Validate known keys | Config validation without heavyweight schema library |
| Docstring schemas | state.clj:25-41 | Document state shape | Lightweight schema documentation in comments |
| Migration warnings | migration.clj:28-56 | One-time version upgrade messages | Major breaking changes that need user awareness |

**No new dependencies required.** This phase only removes existing schema definitions and validation logic.

## Architecture Patterns

### Pattern 1: CLI Spec Removal
**What:** Remove tmux-related keys from babashka.cli spec map and help order array
**When to use:** Removing command-line flags from CLI interface
**Example:**
```clojure
;; In src/aishell/cli.clj

;; BEFORE (lines 69-78):
(def setup-spec
  {:with-claude   {:desc "Include Claude Code (optional: =VERSION)"}
   :with-opencode {:desc "Include OpenCode (optional: =VERSION)"}
   :with-codex    {:desc "Include Codex CLI (optional: =VERSION)"}
   :with-gemini   {:desc "Include Gemini CLI (optional: =VERSION)"}
   :with-gitleaks {:coerce :boolean :desc "Include Gitleaks secret scanner"}
   :with-tmux     {:coerce :boolean :desc "Enable tmux multiplexer in container"}  ;; REMOVE
   :force         {:coerce :boolean :desc "Force rebuild (bypass Docker cache)"}
   :verbose       {:alias :v :coerce :boolean :desc "Show full Docker build output"}
   :help          {:alias :h :coerce :boolean :desc "Show setup help"}})

;; AFTER:
(def setup-spec
  {:with-claude   {:desc "Include Claude Code (optional: =VERSION)"}
   :with-opencode {:desc "Include OpenCode (optional: =VERSION)"}
   :with-codex    {:desc "Include Codex CLI (optional: =VERSION)"}
   :with-gemini   {:desc "Include Gemini CLI (optional: =VERSION)"}
   :with-gitleaks {:coerce :boolean :desc "Include Gitleaks secret scanner"}
   :force         {:coerce :boolean :desc "Force rebuild (bypass Docker cache)"}
   :verbose       {:alias :v :coerce :boolean :desc "Show full Docker build output"}
   :help          {:alias :h :coerce :boolean :desc "Show setup help"}})

;; ALSO REMOVE from help order (line 142):
;; BEFORE:
:order [:with-claude :with-opencode :with-codex :with-gemini :with-tmux :with-gitleaks :force :verbose :help]

;; AFTER:
:order [:with-claude :with-opencode :with-codex :with-gemini :with-gitleaks :force :verbose :help]
```

**Rationale:** babashka.cli uses declarative specs where each key in the spec map becomes a command-line flag. Removing the `:with-tmux` key makes `--with-tmux` flag unavailable. The `:order` array controls help output display order and must also be updated to remove tmux.

**Source:** Existing pattern in src/aishell/cli.clj (lines 69-78, 141-142), [babashka.cli format-opts documentation](https://github.com/babashka/cli/blob/main/API.md)

### Pattern 2: State Schema Cleanup
**What:** Remove tmux references from state write-state docstring and handler logic
**When to use:** Removing keys from persisted state without backward-incompatible migration
**Example:**
```clojure
;; In src/aishell/state.clj

;; BEFORE (lines 25-41):
(defn write-state
  "Write state to file, creating directory if needed.

   State schema (v2.8.0):
   {:with-claude true            ; boolean
    :with-opencode false         ; boolean
    :with-codex false            ; boolean
    :with-gemini false           ; boolean
    :with-gitleaks false         ; boolean (whether Gitleaks installed, default false, opt-in)
    :with-tmux false             ; boolean (whether tmux enabled, default false)  ;; REMOVE
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
  ...)

;; AFTER:
(defn write-state
  "Write state to file, creating directory if needed.

   State schema (v3.0.0):
   {:with-claude true            ; boolean
    :with-opencode false         ; boolean
    :with-codex false            ; boolean
    :with-gemini false           ; boolean
    :with-gitleaks false         ; boolean (whether Gitleaks installed, default false, opt-in)
    :claude-version \"2.0.22\"   ; string or nil
    :opencode-version nil        ; string or nil
    :codex-version \"0.89.0\"    ; string or nil
    :gemini-version nil          ; string or nil
    :image-tag \"aishell:foundation\"  ; string
    :build-time \"2026-01-20...\" ; ISO-8601 string
    :foundation-hash \"abc123def456\"  ; 12-char SHA-256 of foundation Dockerfile template
    :harness-volume-hash \"def789ghi012\" ; 12-char SHA-256 of enabled harnesses+versions
    :harness-volume-name \"aishell-harness-def789ghi012\"} ; Docker volume name for runtime mounting"
  [state]
  ...)
```

**Rationale:** State file uses plain EDN serialization with no formal schema validation. The docstring serves as documentation. Old state files with `:with-tmux` will be ignored (Clojure maps tolerate extra keys). New builds will never write `:with-tmux` or `:tmux-plugins` keys. This is a soft migration—no code to migrate old state files, they just get overwritten on next setup.

**Source:** Existing pattern in src/aishell/state.clj (lines 14-45), Clojure EDN read/write semantics

### Pattern 3: Config Schema Validation Removal
**What:** Remove `tmux:` from known-keys set and delete validate-tmux-config function
**When to use:** Removing entire config section from YAML validation
**Example:**
```clojure
;; In src/aishell/config.clj

;; BEFORE (line 11):
(def known-keys
  "Valid config keys. Unknown keys trigger warning."
  #{:mounts :env :ports :docker_args :pre_start :extends :harness_args :gitleaks_freshness_check :detection :tmux})

;; AFTER:
(def known-keys
  "Valid config keys. Unknown keys trigger warning."
  #{:mounts :env :ports :docker_args :pre_start :extends :harness_args :gitleaks_freshness_check :detection})

;; REMOVE entire function (lines 138-169):
(defn validate-tmux-config
  "Validate tmux config structure. Warns on invalid format.
   Expected: map with optional keys like :plugins, :resurrect.
   Returns config unchanged."
  [tmux-config source-path]
  ...)

;; REMOVE from validate-config (lines 185-186):
(defn validate-config
  "Validate config map. Warns on unknown keys. Returns config unchanged."
  [config source-path]
  (when config
    (let [config-keys (set (keys config))
          unknown (clojure.set/difference config-keys known-keys)]
      (when (seq unknown)
        (output/warn (str "Unknown config keys in " source-path ": "
                         (clojure.string/join ", " (map name unknown))
                         "\nValid keys: mounts, env, ports, docker_args, pre_start, extends, harness_args, detection"))))  ;; UPDATE: remove "tmux" from list
    (when-let [harness-args (:harness_args config)]
      (validate-harness-names harness-args source-path))
    (when-let [detection (:detection config)]
      (validate-detection-config detection source-path))
    ;; REMOVE these two lines:
    (when-let [tmux (:tmux config)]
      (validate-tmux-config tmux source-path)))
  config)

;; REMOVE from merge-configs scalar-keys (line 232):
;; BEFORE:
scalar-keys #{:pre_start :gitleaks_freshness_check :tmux}

;; AFTER:
scalar-keys #{:pre_start :gitleaks_freshness_check}
```

**Rationale:** Config validation uses manual key checking against a known-keys set. Removing `:tmux` from the set causes config.yaml files with `tmux:` sections to trigger unknown-key warnings (non-fatal). The validate-tmux-config function becomes dead code and should be deleted. Config merge logic treats tmux as a scalar (project config replaces global) but this entire branch becomes unreachable once `:tmux` is removed from known-keys.

**Source:** Existing validation pattern in src/aishell/config.clj (lines 9-187), similar to gitleaks and detection config handling

### Pattern 4: Help Text Update
**What:** Remove tmux-related examples and explanations from print-setup-help
**When to use:** Cleaning up user-facing documentation after removing features
**Example:**
```clojure
;; In src/aishell/cli.clj

;; BEFORE (lines 135-152):
(defn print-setup-help []
  (println (str output/BOLD "Usage:" output/NC " aishell setup [OPTIONS]"))
  (println)
  (println "Set up the container image and select harnesses.")
  (println)
  (println (str output/BOLD "Options:" output/NC))
  (println (cli/format-opts {:spec setup-spec
                             :order [:with-claude :with-opencode :with-codex :with-gemini :with-tmux :with-gitleaks :force :verbose :help]}))
  (println)
  (println (str output/BOLD "Examples:" output/NC))
  (println (str "  " output/CYAN "aishell setup" output/NC "                      Set up base image"))
  (println (str "  " output/CYAN "aishell setup --with-claude" output/NC "        Include Claude Code (latest)"))
  (println (str "  " output/CYAN "aishell setup --with-claude=2.0.22" output/NC " Pin Claude Code version"))
  (println (str "  " output/CYAN "aishell setup --with-claude --with-opencode" output/NC " Include both"))
  (println (str "  " output/CYAN "aishell setup --with-codex --with-gemini" output/NC " Include Codex and Gemini"))
  (println (str "  " output/CYAN "aishell setup --with-claude --with-tmux" output/NC "  Include Claude + tmux"))  ;; REMOVE
  (println (str "  " output/CYAN "aishell setup --with-gitleaks" output/NC "          Include Gitleaks scanner"))
  (println (str "  " output/CYAN "aishell setup --force" output/NC "                  Force rebuild")))

;; AFTER (remove tmux example and update :order):
(defn print-setup-help []
  (println (str output/BOLD "Usage:" output/NC " aishell setup [OPTIONS]"))
  (println)
  (println "Set up the container image and select harnesses.")
  (println)
  (println (str output/BOLD "Options:" output/NC))
  (println (cli/format-opts {:spec setup-spec
                             :order [:with-claude :with-opencode :with-codex :with-gemini :with-gitleaks :force :verbose :help]}))
  (println)
  (println (str output/BOLD "Examples:" output/NC))
  (println (str "  " output/CYAN "aishell setup" output/NC "                      Set up base image"))
  (println (str "  " output/CYAN "aishell setup --with-claude" output/NC "        Include Claude Code (latest)"))
  (println (str "  " output/CYAN "aishell setup --with-claude=2.0.22" output/NC " Pin Claude Code version"))
  (println (str "  " output/CYAN "aishell setup --with-claude --with-opencode" output/NC " Include both"))
  (println (str "  " output/CYAN "aishell setup --with-codex --with-gemini" output/NC " Include Codex and Gemini"))
  (println (str "  " output/CYAN "aishell setup --with-gitleaks" output/NC "          Include Gitleaks scanner"))
  (println (str "  " output/CYAN "aishell setup --force" output/NC "                  Force rebuild")))
```

**Rationale:** Help text includes both auto-generated option descriptions (via format-opts) and manual examples. Removing `:with-tmux` from the spec automatically removes it from format-opts output. The example showing `--with-claude --with-tmux` must be manually deleted to complete the cleanup.

**Source:** Existing help text pattern in src/aishell/cli.clj (lines 135-152), [babashka.cli format-opts](https://github.com/babashka/cli)

### Pattern 5: Handler Logic Cleanup
**What:** Remove tmux-related parsing, validation, and state-building in handle-setup and handle-update
**When to use:** Removing conditional feature logic after deprecating a feature
**Example:**
```clojure
;; In src/aishell/cli.clj handle-setup (lines 154-242)

;; REMOVE these lines:
;; Line 165:
with-tmux (boolean (:with-tmux opts))

;; Lines 191-206 (tmux-plugins calculation in state-map):
:with-tmux with-tmux
:tmux-plugins (when with-tmux
                (let [plugins (vec (or (get-in cfg [:tmux :plugins]) []))
                      resurrect-val (get-in cfg [:tmux :resurrect])
                      resurrect-cfg (config/parse-resurrect-config resurrect-val)
                      needs-resurrect? (:enabled resurrect-cfg)
                      has-resurrect? (some #(= % "tmux-plugins/tmux-resurrect") plugins)]
                  (if (and needs-resurrect? (not has-resurrect?))
                    (conj plugins "tmux-plugins/tmux-resurrect")
                    plugins)))

;; Lines 205-206 (resurrect-config in state-map):
:resurrect-config (when with-tmux
                    (config/parse-resurrect-config (get-in cfg [:tmux :resurrect])))

;; Line 212 (tmux check in harness-enabled? predicate):
;; BEFORE:
_ (when (some #(get state-map %) [:with-claude :with-opencode :with-codex :with-gemini :with-tmux])

;; AFTER:
_ (when (some #(get state-map %) [:with-claude :with-opencode :with-codex :with-gemini])

;; In handle-update (lines 289-358):

;; REMOVE these lines (311-312):
(when (:with-tmux state)
  (println "  tmux: enabled"))

;; Line 329:
;; BEFORE:
harnesses-enabled? (some #(get state %) [:with-claude :with-opencode :with-codex :with-gemini :with-tmux])

;; AFTER:
harnesses-enabled? (some #(get state %) [:with-claude :with-opencode :with-codex :with-gemini])
```

**Rationale:** The handle-setup function parses `--with-tmux` opt, reads `tmux:` config section, and builds state-map with `:with-tmux`, `:tmux-plugins`, and `:resurrect-config` keys. All this logic becomes dead code after removing the CLI flag and config section. The harness-enabled? check includes `:with-tmux` to trigger volume population, but tmux should not trigger volume creation (it's not a harness). Removing these lines completes the cleanup. Similarly, handle-update displays "tmux: enabled" status and checks `:with-tmux` for harnesses-enabled? logic—both must be removed.

**Source:** Existing handler logic in src/aishell/cli.clj (lines 154-242, 289-358)

### Anti-Patterns to Avoid

- **Writing state migration code:** Don't create a migration function to remove `:with-tmux` from existing state files. Old state files will be overwritten on next `aishell setup`. Extra keys are ignored by Clojure's EDN reader. Migration adds complexity with no benefit.

- **Leaving parse-resurrect-config function:** The config/parse-resurrect-config function (config.clj:108-136) is ONLY used for tmux resurrect config parsing. After removing `:tmux` from known-keys and the validate-tmux-config call, this function becomes dead code. Delete it to prevent future confusion.

- **Forgetting help text order arrays:** babashka.cli format-opts uses an `:order` vector to control option display order. Removing `:with-tmux` from setup-spec but forgetting to remove it from the :order vector will cause a runtime warning or incorrect help output. Grep for `:with-tmux` in :order arrays.

- **Incomplete handler cleanup:** The handle-setup and handle-update functions have multiple references to `:with-tmux` (opt parsing, state-map building, harness-enabled? checks). Removing only the opt parsing but leaving state-map building will cause the state file to still contain `:with-tmux false` entries. Remove ALL references systematically.

## Don't Hand-Roll

Problems with existing Clojure/CLI solutions:

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| State file migration | Custom migration logic to remove keys | No migration—let overwrite handle it | EDN readers ignore unknown keys, next setup overwrites state cleanly |
| Schema validation | Heavyweight schema library (plumatic/schema, malli) | Manual known-keys set validation | Existing pattern works well, no complex validation needs |
| CLI help generation | Custom string building for options | babashka.cli format-opts | Already integrated, auto-generates from spec, maintains consistency |
| Config file parsing | Custom YAML validation | clj-yaml with manual key checking | Existing pattern handles validation, warnings non-fatal |

**Key insight:** The existing codebase uses lightweight schema patterns (docstrings, known-keys sets, manual validation) that work well for aishell's needs. Phase 47 cleanup requires only simple deletions—no new infrastructure or migration code needed.

## Common Pitfalls

### Pitfall 1: Breaking Existing State Files
**What goes wrong:** Users with `:with-tmux true` in state.edn get errors when running commands after Phase 47.

**Why it happens:** Code might try to access `:with-tmux` or `:tmux-plugins` keys that exist in old state files but are no longer handled by new code.

**How to avoid:**
1. Search all code for `(:with-tmux state)` and `(:tmux-plugins state)` references
2. Use `(get state :with-tmux false)` pattern with default values (already used in codebase)
3. Test with a state file containing `:with-tmux true` to ensure graceful handling
4. Document in MIGRATION.md that old state files are harmless and will be overwritten

**Warning signs:**
- NullPointerException or nil errors when reading state
- Unexpected behavior when `:with-tmux` key exists in state
- State file validation errors

**Verification:**
```bash
# Create test state with tmux keys
echo '{:with-tmux true :tmux-plugins ["tmux-plugins/tmux-sensible"]}' > ~/.aishell/state.edn

# Run commands and verify no errors
aishell check
aishell setup --with-claude
```

### Pitfall 2: Orphaned Function References
**What goes wrong:** Removing validate-tmux-config and parse-resurrect-config functions but leaving code that calls them causes compilation errors.

**Why it happens:** config.clj has multiple call sites for tmux validation functions. Missing one reference breaks the namespace.

**How to avoid:**
1. Grep for function names before deletion: `grep -n "parse-resurrect-config\|validate-tmux-config" src/aishell/**/*.clj`
2. Remove all call sites first, then delete function definitions
3. Use Clojure REPL to load namespace and catch undefined function errors early
4. Run aishell commands end-to-end to verify runtime behavior

**Warning signs:**
- Compilation errors: "Unable to resolve symbol: parse-resurrect-config"
- Runtime errors when loading config.yaml with tmux: section
- ClojureScript analyzer warnings about undefined vars

**Call sites to check:**
- config/validate-config calls validate-tmux-config (line 185-186)
- cli/handle-setup calls parse-resurrect-config (lines 194, 206)
- docker.volume/compute-harness-hash and populate-volume reference `:tmux-plugins` and `:resurrect-config` state keys

### Pitfall 3: Help Text Inconsistency
**What goes wrong:** Help output still mentions tmux in examples or descriptions after removing the flag.

**Why it happens:** Help text has both auto-generated sections (format-opts) and manual examples. Removing from spec fixes format-opts but not hand-written examples.

**How to avoid:**
1. Remove `:with-tmux` from setup-spec (auto-removes from format-opts)
2. Remove `:with-tmux` from :order array in print-setup-help
3. Remove manual tmux example: `"aishell setup --with-claude --with-tmux"`
4. Grep help text for "tmux" to find other mentions
5. Test: `aishell setup --help` should show zero tmux references

**Warning signs:**
- Help output mentions tmux but flag doesn't work
- Examples show `--with-tmux` but spec doesn't include it
- Users report "documentation shows tmux option but it's not available"

**Verification:**
```bash
aishell setup --help | grep -i tmux
# Should return nothing after Phase 47
```

### Pitfall 4: Volume Hash Still Depends on Tmux State
**What goes wrong:** docker.volume/compute-harness-hash still includes `:with-tmux` and `:tmux-plugins` in hash calculation, causing unnecessary volume rebuilds.

**Why it happens:** Volume hash is computed from state map to detect when harness configuration changes. If tmux keys remain in the hash input, changing them (or their absence) triggers volume recreation.

**How to avoid:**
1. Check docker/volume.clj compute-harness-hash function (line ~40-60)
2. Remove `:with-tmux` from harness-state extraction
3. Remove `:tmux-plugins` from hash input
4. Document that v3.0.0 volume hashes are incompatible with v2.9.0 (expected)

**Warning signs:**
- Volume gets recreated on every build despite no harness changes
- Hash values differ between identical configs (only difference is tmux keys)
- populate-volume called unnecessarily

**Source:** Similar issue documented in Phase 42 research (resurrect state persistence), requirement TMUX-07 explicitly calls out volume hash dependency removal.

### Pitfall 5: Config Merge Logic Broken
**What goes wrong:** Config merge-configs function references `:tmux` in scalar-keys set, causing global/project merge to fail or behave unexpectedly when tmux key is removed.

**Why it happens:** merge-configs categorizes config keys (lists, maps, scalars) to control merge behavior. Removing `:tmux` from known-keys but not from scalar-keys creates inconsistency.

**How to avoid:**
1. Remove `:tmux` from known-keys set (line 11)
2. Remove `:tmux` from scalar-keys in merge-configs (line 232)
3. Test with global config having `tmux:` section and project config without it
4. Verify merge doesn't crash or produce unexpected results

**Warning signs:**
- Config merge errors or exceptions
- Config validation warnings about tmux in global but not in merged result
- Unexpected config values when global/project both define tmux

**Source:** Existing merge pattern in config.clj:220-283, similar to :pre_start and :gitleaks_freshness_check scalar handling

## Code Examples

Verified patterns from the existing codebase:

### Complete CLI Spec Cleanup
```clojure
;; File: src/aishell/cli.clj

;; Step 1: Remove from setup-spec (lines 69-78)
(def setup-spec
  {:with-claude   {:desc "Include Claude Code (optional: =VERSION)"}
   :with-opencode {:desc "Include OpenCode (optional: =VERSION)"}
   :with-codex    {:desc "Include Codex CLI (optional: =VERSION)"}
   :with-gemini   {:desc "Include Gemini CLI (optional: =VERSION)"}
   :with-gitleaks {:coerce :boolean :desc "Include Gitleaks secret scanner"}
   ;; REMOVED: :with-tmux     {:coerce :boolean :desc "Enable tmux multiplexer in container"}
   :force         {:coerce :boolean :desc "Force rebuild (bypass Docker cache)"}
   :verbose       {:alias :v :coerce :boolean :desc "Show full Docker build output"}
   :help          {:alias :h :coerce :boolean :desc "Show setup help"}})

;; Step 2: Update help order array (line 142)
(cli/format-opts {:spec setup-spec
                  :order [:with-claude :with-opencode :with-codex :with-gemini
                          ;; REMOVED: :with-tmux
                          :with-gitleaks :force :verbose :help]})

;; Step 3: Remove manual example (line 150)
;; REMOVED: (println (str "  " output/CYAN "aishell setup --with-claude --with-tmux" output/NC "  Include Claude + tmux"))
```
**Source:** src/aishell/cli.clj (lines 69-152)

### Complete State Schema Cleanup
```clojure
;; File: src/aishell/state.clj

;; Update docstring (lines 25-41):
(defn write-state
  "Write state to file, creating directory if needed.

   State schema (v3.0.0):
   {:with-claude true            ; boolean
    :with-opencode false         ; boolean
    :with-codex false            ; boolean
    :with-gemini false           ; boolean
    :with-gitleaks false         ; boolean (whether Gitleaks installed, default false, opt-in)
    ;; REMOVED: :with-tmux false             ; boolean (whether tmux enabled, default false)
    :claude-version \"2.0.22\"   ; string or nil
    :opencode-version nil        ; string or nil
    :codex-version \"0.89.0\"    ; string or nil
    :gemini-version nil          ; string or nil
    :image-tag \"aishell:foundation\"  ; string
    :build-time \"2026-01-20...\" ; ISO-8601 string
    :foundation-hash \"abc123def456\"  ; 12-char SHA-256 of foundation Dockerfile template
    :harness-volume-hash \"def789ghi012\" ; 12-char SHA-256 of enabled harnesses+versions
    :harness-volume-name \"aishell-harness-def789ghi012\"} ; Docker volume name for runtime mounting"
  [state]
  (let [path (state-file)]
    (util/ensure-dir (util/config-dir))
    (spit path (pr-str state))))

;; No migration needed—old state files with :with-tmux will be overwritten on next setup
```
**Source:** src/aishell/state.clj (lines 22-45)

### Complete Config Schema Cleanup
```clojure
;; File: src/aishell/config.clj

;; Step 1: Remove from known-keys (line 11)
(def known-keys
  "Valid config keys. Unknown keys trigger warning."
  #{:mounts :env :ports :docker_args :pre_start :extends :harness_args :gitleaks_freshness_check :detection})
  ;; REMOVED: :tmux

;; Step 2: Delete entire validate-tmux-config function (lines 138-169)
;; REMOVED: (defn validate-tmux-config ...)

;; Step 3: Delete parse-resurrect-config function (lines 108-136)
;; REMOVED: (defn parse-resurrect-config ...)

;; Step 4: Remove call from validate-config (lines 185-186)
(defn validate-config
  "Validate config map. Warns on unknown keys. Returns config unchanged."
  [config source-path]
  (when config
    (let [config-keys (set (keys config))
          unknown (clojure.set/difference config-keys known-keys)]
      (when (seq unknown)
        (output/warn (str "Unknown config keys in " source-path ": "
                         (clojure.string/join ", " (map name unknown))
                         "\nValid keys: mounts, env, ports, docker_args, pre_start, extends, harness_args, detection"))))
    (when-let [harness-args (:harness_args config)]
      (validate-harness-names harness-args source-path))
    (when-let [detection (:detection config)]
      (validate-detection-config detection source-path)))
    ;; REMOVED: (when-let [tmux (:tmux config)]
    ;;            (validate-tmux-config tmux source-path))
  config)

;; Step 5: Remove from scalar-keys in merge-configs (line 232)
(defn merge-configs
  "Merge global-config and project-config with defined strategy..."
  [global-config project-config]
  (let [list-keys #{:mounts :ports :docker_args}
        map-keys #{:env}
        map-of-lists-keys #{:harness_args}
        scalar-keys #{:pre_start :gitleaks_freshness_check}  ;; REMOVED: :tmux
        ...]
    ...))
```
**Source:** src/aishell/config.clj (lines 9-283)

### Complete Handler Cleanup
```clojure
;; File: src/aishell/cli.clj

;; In handle-setup (lines 154-242):
(defn handle-setup [{:keys [opts]}]
  (if (:help opts)
    (print-setup-help)
    (let [claude-config (parse-with-flag (:with-claude opts))
          opencode-config (parse-with-flag (:with-opencode opts))
          codex-config (parse-with-flag (:with-codex opts))
          gemini-config (parse-with-flag (:with-gemini opts))
          with-gitleaks (boolean (:with-gitleaks opts))
          ;; REMOVED: with-tmux (boolean (:with-tmux opts))

          _ (validate-version (:version claude-config) "Claude Code")
          _ (validate-version (:version opencode-config) "OpenCode")
          _ (validate-version (:version codex-config) "Codex")
          _ (validate-version (:version gemini-config) "Gemini")

          result (build/build-foundation-image
                   {:with-gitleaks with-gitleaks
                    :verbose (:verbose opts)
                    :force (:force opts)})

          project-dir (System/getProperty "user.dir")
          cfg (config/load-config project-dir)
          state-map {:with-claude (:enabled? claude-config)
                     :with-opencode (:enabled? opencode-config)
                     :with-codex (:enabled? codex-config)
                     :with-gemini (:enabled? gemini-config)
                     :with-gitleaks with-gitleaks
                     ;; REMOVED: :with-tmux with-tmux
                     ;; REMOVED: :tmux-plugins (when with-tmux ...)
                     :claude-version (:version claude-config)
                     :opencode-version (:version opencode-config)
                     :codex-version (:version codex-config)
                     :gemini-version (:version gemini-config)}
                     ;; REMOVED: :resurrect-config (when with-tmux ...)

          harness-hash (vol/compute-harness-hash state-map)
          volume-name (vol/volume-name harness-hash)

          _ (when (some #(get state-map %) [:with-claude :with-opencode :with-codex :with-gemini])  ;; REMOVED: :with-tmux
              ...)]

      (state/write-state
        (assoc state-map
               :image-tag (:image result)
               :build-time (str (java.time.Instant/now))
               :foundation-hash (hash/compute-hash templates/base-dockerfile)
               :harness-volume-hash harness-hash
               :harness-volume-name volume-name)))))

;; In handle-update (lines 289-358):
(defn handle-update [{:keys [opts]}]
  (if (:help opts)
    (print-update-help)
    (let [state (state/read-state)]
      (when-not state
        (output/error "No previous setup found. Run: aishell setup"))

      (println "Updating with preserved configuration...")
      (when (:with-claude state)
        (println (str "  Claude Code: " (or (:claude-version state) "latest"))))
      (when (:with-opencode state)
        (println (str "  OpenCode: " (or (:opencode-version state) "latest"))))
      (when (:with-codex state)
        (println (str "  Codex: " (or (:codex-version state) "latest"))))
      (when (:with-gemini state)
        (println (str "  Gemini: " (or (:gemini-version state) "latest"))))
      ;; REMOVED: (when (:with-tmux state) (println "  tmux: enabled"))

      (let [project-dir (System/getProperty "user.dir")
            cfg (config/load-config project-dir)
            result (when (:force opts)
                     (build/build-foundation-image
                       {:with-gitleaks (:with-gitleaks state false)
                        :verbose (:verbose opts)
                        :force true}))

            harness-hash (vol/compute-harness-hash state)
            volume-name (or (:harness-volume-name state)
                           (vol/volume-name harness-hash))

            harnesses-enabled? (some #(get state %) [:with-claude :with-opencode :with-codex :with-gemini])  ;; REMOVED: :with-tmux
            ...]
        ...))))
```
**Source:** src/aishell/cli.clj (lines 154-358)

### Verification Commands
```bash
# After Phase 47 implementation, verify cleanup:

# 1. Help text should show no tmux options
aishell setup --help | grep -i tmux
# Expected: (no output)

# 2. Flag should be unrecognized
aishell setup --with-tmux 2>&1 | grep "Unknown option"
# Expected: Error: Unknown option: --with-tmux

# 3. State file should have no tmux keys after rebuild
aishell setup --with-claude
grep -E "with-tmux|tmux-plugins" ~/.aishell/state.edn
# Expected: (no output)

# 4. Config with tmux: section should trigger warning but not error
echo "tmux:\n  plugins:\n    - tmux-plugins/tmux-sensible" > .aishell/config.yaml
aishell check 2>&1 | grep "Unknown config keys"
# Expected: Warning: Unknown config keys in .aishell/config.yaml: tmux

# 5. Old state file with tmux keys should not break commands
echo '{:with-tmux true :tmux-plugins ["tmux-plugins/tmux-sensible"]}' > ~/.aishell/state.edn
aishell check
# Expected: (no errors, check completes successfully)
```
**Source:** Manual verification pattern based on requirements TMUX-02, TMUX-03, TMUX-08

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| tmux always installed | tmux opt-in via --with-tmux flag | v2.9.0 (Phase 39) | Made tmux conditional, reduced default image size |
| tmux opt-in | tmux completely removed | v3.0.0 (Phase 46-47) | No tmux in any configuration, --with-tmux flag removed |
| State migration with code | Soft migration via overwrite | v3.0.0 (Phase 47) | No migration logic needed, old state ignored and overwritten |
| Complex schema validation | Manual known-keys checking | v2.0 baseline | Lightweight, sufficient for aishell's needs |

**Deprecated/outdated:**
- **--with-tmux build flag:** Removed in Phase 47. No longer possible to enable tmux in containers. Window management moved to host terminal multiplexers.
- **:with-tmux state key:** Removed from state schema in Phase 47. Old state files may contain this key but it's ignored. Next setup overwrites cleanly.
- **:tmux-plugins state key:** Removed from state schema in Phase 47. Was used to track installed tmux plugins for volume hash calculation.
- **tmux: config section:** Removed from config.yaml schema in Phase 47. Config files with this section trigger unknown-key warnings.
- **config/parse-resurrect-config function:** Removed in Phase 47. Was used to parse tmux.resurrect config (boolean sugar or map with options).
- **config/validate-tmux-config function:** Removed in Phase 47. Was used to validate tmux: section format (plugins list, resurrect config).

## Open Questions

### 1. Should Phase 47 add a v3.0.0 migration warning?
**What we know:**
- migration.clj has a pattern for one-time warnings (v2.9.0 example lines 28-56)
- v2.9.0 warning detects pre-v2.9.0 state by checking for missing :harness-volume-hash key
- v3.0.0 removes tmux but doesn't add new required state keys
- Users with :with-tmux in state won't get errors, just ignored keys

**What's unclear:**
- Should there be a user-facing warning that tmux is no longer supported?
- How to detect "needs v3.0.0 warning" condition (presence of :with-tmux key?)
- Or defer all user communication to Phase 52 documentation updates?

**Recommendation:** Add a lightweight migration warning in migration.clj similar to v2.9.0 pattern. Detection: state file contains `:with-tmux` key. Warning: "aishell v3.0.0: tmux removed - window management now handled by host terminal multiplexer. Your existing containers will continue to work, but new builds will not include tmux. For details: docs/ARCHITECTURE.md". This provides user awareness during transition period without breaking changes.

### 2. What happens to docker/volume.clj references to :tmux-plugins and :resurrect-config?
**What we know:**
- docker/volume.clj compute-harness-hash function includes tmux state (lines 52-53)
- docker/volume.clj populate-volume has TPM installation logic (lines 318-340)
- Phase 47 removes these keys from state.clj schema
- Phase 48 is supposed to remove volume population logic (requirement TMUX-06)

**What's unclear:**
- Should Phase 47 also clean up docker/volume.clj references?
- Or strictly separate: Phase 47 = schema only, Phase 48 = volume logic?
- If volume.clj still references removed state keys, will it break?

**Recommendation:** Phase 47 should remove `:tmux-plugins` and `:resurrect-config` references from docker/volume.clj compute-harness-hash and populate-volume. Rationale: These functions directly read state keys that Phase 47 removes from the schema. Leaving dead references causes confusion and potential nil errors. Phase 48 can then focus on WITH_TMUX env var and mount logic without dealing with state keys. Requirement TMUX-06 (TPM installation removed) and TMUX-07 (volume hash no longer depends on tmux) both fit naturally in Phase 47 scope.

### 3. How to handle attach.clj validation that requires tmux?
**What we know:**
- attach.clj validate-tmux-enabled! checks if tmux binary exists (lines 44-57)
- attach.clj functions depend on tmux being in container (tmux attach-session, tmux new-session)
- Phase 46 removed tmux binary from foundation image
- Attach command will fail with "tmux not found" after Phase 46

**What's unclear:**
- Should Phase 47 update attach.clj error messages?
- Should attach command be disabled entirely until Phase 50 rewrites it?
- Or leave as-is and let Phase 50 handle the rewrite comprehensively?

**Recommendation:** Leave attach.clj unchanged in Phase 47. Rationale: Requirement ATTCH-01 through ATTCH-04 define new attach semantics (docker exec -it bash, no tmux dependency). These are Phase 50 scope, which will completely rewrite attach.clj. Phase 47 focus is CLI/state/config schema cleanup only. Users who try `aishell attach` after Phase 46 will get the existing error message "tmux not enabled" which is technically accurate. Phase 50 will simplify to direct docker exec.

## Sources

### Primary (HIGH confidence)
- src/aishell/cli.clj - CLI spec and handler logic with tmux references (lines 69-358)
- src/aishell/state.clj - State schema documentation and persistence (lines 14-45)
- src/aishell/config.clj - Config validation and merge logic with tmux handling (lines 9-332)
- src/aishell/migration.clj - Migration warning pattern for version upgrades (lines 1-56)
- src/aishell/docker/volume.clj - Volume hash computation and population with tmux state (lines 40-340)
- .planning/REQUIREMENTS.md - v3.0.0 requirements TMUX-02, TMUX-03, TMUX-08 (lines 1-50)
- .planning/ROADMAP.md - Phase 47 definition and success criteria (Phase 47 section)
- [babashka.cli API documentation](https://github.com/babashka/cli/blob/main/API.md) - format-opts and spec usage

### Secondary (MEDIUM confidence)
- [Clojure dissoc function](https://clojuredocs.org/clojure.core/dissoc) - Standard pattern for removing map keys
- [babashka.cli blog post](https://blog.michielborkent.nl/babashka-cli.html) - CLI spec design patterns
- [Clojure EDN format](https://clojure.org/guides/learn/hashed_colls) - Map handling and key tolerance

### Tertiary (LOW confidence)
- [Plumatic Schema](https://github.com/plumatic/schema) - Not used in aishell, but common Clojure schema library for context
- [schema-tools](https://github.com/metosin/schema-tools) - Schema-aware map operations, not applicable to this codebase

## Metadata

**Confidence breakdown:**
- CLI spec removal: HIGH - Straightforward spec map key deletion, well-documented babashka.cli pattern
- State schema cleanup: HIGH - Simple docstring update, no formal schema validation to update
- Config validation removal: HIGH - Existing known-keys pattern clear, validation function removal safe
- Handler logic cleanup: HIGH - Clear code paths to remove, grep-able references
- Migration strategy: MEDIUM - Soft migration (overwrite) is untested in this specific case but matches project patterns

**Research date:** 2026-02-06
**Valid until:** 2026-04-06 (60 days - Clojure core patterns stable, CLI library stable, internal codebase well-understood)

**Key technical constraints:**
- Phase 47 removes ONLY schema definitions, not runtime container logic (Phase 48-49)
- No state migration code needed—soft migration via overwrite on next setup
- Config files with tmux: sections generate warnings but remain non-fatal
- Volume hash computation must stop depending on tmux state (overlaps with Phase 48 scope)
- Attach command left unchanged (Phase 50 will rewrite comprehensively)
