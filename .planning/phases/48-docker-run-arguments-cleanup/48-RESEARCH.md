# Phase 48: Docker Run Arguments Cleanup - Research

**Researched:** 2026-02-06
**Domain:** Clojure function deletion in Babashka/SCI environment, Docker container runtime configuration
**Confidence:** HIGH

## Summary

Phase 48 removes all tmux-related runtime configuration from Docker run arguments. This is pure deletion work removing mount builders, environment variables, and dead helper functions that were stubbed in Phase 47. The critical challenge is Babashka's SCI interpreter which resolves symbols at analysis time - even dead code paths that reference deleted functions will crash the tool during file load.

The standard approach for this phase is exhaustive grep-based analysis to find ALL callers of functions before deletion, then atomic commits that delete functions and update all callers simultaneously. This prevents the "symbol not found at analysis time" errors that break Babashka tools.

**Primary recommendation:** Delete tmux mount builders, env builders, and conditional blocks from `run.clj` and `volume.clj` in atomic commits. Update ALL callers found via grep before deleting each function. Remove tmux state from volume hash calculation to prevent volume thrashing.

## Standard Stack

Phase 48 uses existing project tools - no new libraries required.

### Core
| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| Babashka/SCI | 1.12.214 | Clojure interpreter | Project runtime (NOT JVM Clojure) |
| babashka.fs | Built-in | Filesystem operations | Babashka standard library |
| babashka.process | Built-in | Shell command execution | Babashka standard library |

### Supporting
None required - pure deletion work.

## Architecture Patterns

### Pattern 1: Babashka/SCI Symbol Resolution (CRITICAL)

**What:** SCI (Small Clojure Interpreter) resolves symbols at analysis time when loading files, not at runtime. This means dead code paths that reference deleted symbols will crash during namespace loading.

**When to use:** ALWAYS when deleting functions in Babashka projects.

**Critical workflow:**
```clojure
;; WRONG: Delete function first, then update callers
;; This breaks SCI - namespace load fails with "symbol not found"

;; RIGHT: Find ALL callers first, then atomic commit
;; 1. Exhaustive grep for function name
grep -rn "build-tmux-config-mount" src/

;; 2. Check every result (even dead code paths!)
;; 3. Single commit: delete function + update all callers
```

**Example from project memory:**
Phase 47 deleted `parse-resurrect-config` from `config.clj` but missed 2 callers in `docker/run.clj` (lines 264, 332). This broke `aishell --help` because SCI failed to load the namespace during analysis.

**Source:** /home/jonasrodrigues/.claude/projects/-home-jonasrodrigues-projects-harness/memory/MEMORY.md

### Pattern 2: Threaded Conditional Deletion

**What:** Remove `cond->` threading blocks that conditionally add Docker arguments based on tmux state.

**Structure in run.clj:**
```clojure
(-> ["docker" "run" ...]
    ;; ... other args ...

    ;; DELETE THIS BLOCK - tmux config mount
    (cond-> (not skip-tmux)
      (into (build-tmux-config-mount state config)))

    ;; DELETE THIS BLOCK - resurrect mount
    (cond-> (not skip-tmux)
      (into (build-resurrect-mount state config project-dir)))

    ;; DELETE THIS BLOCK - WITH_TMUX env var
    (cond-> (and (get state :with-tmux) (not skip-tmux))
      (into ["-e" "WITH_TMUX=true"]))

    ;; ... continue with valid args ...)
```

**How to delete:**
1. Remove entire `cond->` expression
2. Remove the function call it references (after verifying no other callers)
3. Verify thread continues cleanly to next valid expression

### Pattern 3: Volume Hash Calculation Cleanup

**What:** Remove tmux state from harness volume hash to prevent volume thrashing.

**Current implementation (volume.clj lines 52-55):**
```clojure
(let [harness-pairs (... harness normalization ...)
      tmux-state (when (:with-tmux state)
                   [:tmux {:plugins (vec (sort (or (:tmux-plugins state) [])))}])]
  (cond-> harness-pairs
    tmux-state (conj tmux-state)))
```

**After Phase 47:** State no longer has `:with-tmux` or `:tmux-plugins` keys, so `tmux-state` is always nil. This dead code can be removed.

**Impact:** Volume hash becomes purely harness-based (Claude, OpenCode, Codex, Gemini versions). Existing volumes remain valid - hash only changes if harness configuration changes.

### Pattern 4: Stub Function Deletion

**What:** Phase 47 created stub functions that return empty vectors. Phase 48 deletes these stubs and their callers.

**Stubs to delete:**
```clojure
;; docker/run.clj line 224
(defn- build-resurrect-mount
  "Stub — tmux removed in v3.0.0. Full cleanup in Phase 48."
  [_state _config _project-dir]
  [])

;; docker/run.clj line 263
(defn- build-resurrect-env-args
  "Stub — tmux removed in v3.0.0. Full cleanup in Phase 48."
  [_state _config]
  [])
```

**Pattern:** Find callers with grep, verify they're in dead conditional blocks, delete function + caller atomically.

## Don't Hand-Roll

Problems that look simple but have existing solutions:

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| Finding all function callers | Manual search, IDE navigation | `grep -rn "function-name" src/` | Grep finds ALL textual references including dead code paths that IDE "find usages" might skip |
| Verifying SCI compatibility | Manual testing | Load namespace in bb REPL: `bb -e "(require 'aishell.docker.run)"` | Catches analysis-time symbol resolution errors before runtime |
| Volume hash calculation | Custom hash logic | Existing `normalize-harness-config` + `pr-str` + `hash/compute-hash` pattern | Already deterministic, collision-resistant, and tested |

**Key insight:** Babashka's SCI requires more conservative deletion than JVM Clojure because analysis-time failures are harder to debug. Grep-based verification is mandatory, not optional.

## Common Pitfalls

### Pitfall 1: Deleting Functions Before Finding All Callers

**What goes wrong:** Namespace fails to load with "Unable to resolve symbol" error during analysis phase.

**Why it happens:** SCI resolves symbols when loading the file, not when executing code. Even dead code paths cause load failures.

**How to avoid:**
1. Exhaustive grep for function name across entire `src/` directory
2. Verify EVERY result (don't skip "dead" paths)
3. Delete function and update all callers in single atomic commit

**Warning signs:**
- `aishell --help` crashes (namespace load failure during CLI initialization)
- Error mentions "symbol" or "resolve" but occurs in unrelated commands
- Stack trace shows file being loaded, not executed

**Example:** Phase 47 deleted `parse-resurrect-config` but missed callers in `docker/run.clj` lines 264, 332. Both were in conditional blocks but still caused analysis-time failure.

### Pitfall 2: Assuming Conditional Blocks Are Safe to Leave

**What goes wrong:** Dead conditional blocks remain in codebase, creating confusion and technical debt.

**Why it happens:** Assumption that `(cond-> false (into [expr]))` is harmless because condition is never true.

**How to avoid:** Delete entire conditional block when condition can never be true:
```clojure
;; WRONG: Leave dead conditional
(cond-> (get state :with-tmux)  ; Phase 47 removed :with-tmux from state
  (into (build-tmux-config-mount state config)))  ; This never executes but is confusing

;; RIGHT: Delete entire block
;; (Entire cond-> expression removed)
```

**Warning signs:**
- Comments saying "dead code" or "never executes"
- Conditional on state keys that no longer exist
- Calls to stub functions that return empty vectors

### Pitfall 3: Missing Volume Hash Impact

**What goes wrong:** Removing tmux from volume hash calculation without understanding impact could invalidate existing volumes, forcing reinstallation.

**Why it happens:** Volume names are generated from hash of harness configuration. Changing hash algorithm changes volume names.

**How to avoid:**
1. Understand current hash includes tmux state (lines 52-55 in volume.clj)
2. Since Phase 47 removed `:with-tmux` from state, tmux-state is always nil
3. Removing dead tmux-state code doesn't change hash output (nil was already excluded)
4. Document that existing volumes remain valid

**Warning signs:**
- `aishell update` recreates volumes unnecessarily
- Volume names change between runs with identical harness configuration
- TPM installation runs when it shouldn't

### Pitfall 4: Incomplete Grep Search

**What goes wrong:** Function appears unused but is actually called from unexpected location.

**Why it happens:** Searching only obvious files (same directory) instead of entire codebase.

**How to avoid:**
```bash
# WRONG: Search only docker directory
grep -rn "build-tmux-config-mount" src/aishell/docker/

# RIGHT: Search entire src directory
grep -rn "build-tmux-config-mount" src/

# EVEN BETTER: Check for variations
grep -rn "tmux-config-mount" src/  # Catches references without 'build-' prefix
```

**Warning signs:**
- Function deletion succeeds but tests fail
- Unrelated commands break
- Errors mention function name in stack trace

## Code Examples

Verified deletion patterns from existing codebase:

### Deleting Conditional Mount Block

```clojure
// Source: src/aishell/docker/run.clj lines 314-317
// DELETE THIS:
        ;; Tmux config mount (read-only, if enabled and file exists)
        ;; Skip when skip-tmux is true (non-interactive commands like exec/gitleaks)
        (cond-> (not skip-tmux)
          (into (build-tmux-config-mount state config)))

// Result: Thread continues cleanly to next valid block
```

### Deleting Conditional Env Var Block

```clojure
// Source: src/aishell/docker/run.clj lines 324-327
// DELETE THIS:
        ;; Pass WITH_TMUX flag to entrypoint for conditional tmux startup
        ;; Skip when skip-tmux is true (non-interactive commands like exec/gitleaks)
        (cond-> (and (get state :with-tmux) (not skip-tmux))
          (into ["-e" "WITH_TMUX=true"]))

// Note: state no longer has :with-tmux key (removed in Phase 47)
// Condition is always false, entire block is dead code
```

### Removing Tmux from Volume Hash

```clojure
// Source: src/aishell/docker/volume.clj lines 44-55
// BEFORE:
(defn normalize-harness-config
  [state]
  (let [harness-pairs (->> harness-keys
                           (filter #(get state (keyword (str "with-" (name %)))))
                           (map (fn [harness-kw] ...))
                           (sort-by first)
                           vec)
        tmux-state (when (:with-tmux state)
                     [:tmux {:plugins (vec (sort (or (:tmux-plugins state) [])))}])]
    (cond-> harness-pairs
      tmux-state (conj tmux-state))))

// AFTER:
(defn normalize-harness-config
  [state]
  (->> harness-keys
       (filter #(get state (keyword (str "with-" (name %)))))
       (map (fn [harness-kw] ...))
       (sort-by first)
       vec))

// Simplified: Remove let binding, remove cond->, return harness-pairs directly
// Impact: Hash output unchanged (tmux-state was already nil after Phase 47)
```

### Deleting Helper Function with Stub Pattern

```clojure
// Source: src/aishell/docker/run.clj lines 192-199
// DELETE THIS FUNCTION:
(defn- user-mounted-tmux-config?
  "Check if user explicitly mounted tmux config in their config mounts.
   Prevents duplicate mount when auto-mount would also add it."
  [config]
  (let [mounts (get config :mounts [])]
    (some #(or (str/includes? (str %) ".tmux.conf")
              (str/includes? (str %) "tmux/tmux.conf"))
          mounts)))

// Only called from build-tmux-config-mount (line 211)
// When build-tmux-config-mount is deleted, this helper becomes unused
// DELETE both in same commit
```

### Removing TPM Installation from Volume Population

```clojure
// Source: src/aishell/docker/volume.clj lines 334-338
// DELETE THIS BLOCK:
        tmux-plugins (when (:with-tmux state)
                       (:tmux-plugins state))
        tmux-install (build-tpm-install-command tmux-plugins)
        full-commands (str install-commands
                          (when tmux-install (str " && " tmux-install)))

// REPLACE WITH:
        full-commands install-commands

// Note: state no longer has :with-tmux or :tmux-plugins (Phase 47)
// tmux-plugins is always nil, tmux-install is always nil
```

### Deleting inject-resurrect-plugin Helper

```clojure
// Source: src/aishell/docker/volume.clj lines 196-203
// DELETE THIS FUNCTION:
(defn inject-resurrect-plugin
  "Auto-add tmux-resurrect to plugin list when resurrect is enabled.
   Deduplicates if user already declared it."
  [plugins resurrect-enabled?]
  (if (and resurrect-enabled?
           (not (some #(= % "tmux-plugins/tmux-resurrect") plugins)))
    (conj (vec plugins) "tmux-plugins/tmux-resurrect")
    (vec (or plugins []))))

// Function is no longer called (resurrect config removed in Phase 47)
// Verify with: grep -rn "inject-resurrect-plugin" src/
```

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| Mount tmux config at ~/.tmux.conf | No tmux config mounting | Phase 48 (v3.0.0) | Container starts faster, no config file staging |
| WITH_TMUX env var to entrypoint | Direct shell execution | Phase 48-49 (v3.0.0) | Simpler entrypoint, no conditional logic |
| Volume hash includes tmux plugins | Volume hash is harness-only | Phase 48 (v3.0.0) | Volumes stable across non-harness changes |
| Resurrect state directory mounting | No session persistence | Phase 48 (v3.0.0) | Stateless containers, no hidden state directories |
| TPM installation in volumes | npm packages only | Phase 48 (v3.0.0) | Faster volume population, smaller volumes |

**Deprecated/outdated:**
- `build-tmux-config-mount` function: Removed in Phase 48, tmux config no longer mounted
- `build-resurrect-mount` function: Stubbed in Phase 47, deleted in Phase 48
- `build-resurrect-env-args` function: Stubbed in Phase 47, deleted in Phase 48
- `user-mounted-tmux-config?` helper: Deleted in Phase 48, no longer needed
- `inject-resurrect-plugin` function: Deleted in Phase 48, resurrect config removed
- `build-tpm-install-command` function: Deleted in Phase 48, TPM no longer installed
- `WITH_TMUX` environment variable: Removed in Phase 48, entrypoint simplified in Phase 49
- `skip-tmux` parameter: Removed in Phase 48, all commands skip tmux now

## Open Questions

None - Phase 48 is pure deletion work with clear scope from requirements and prior phases.

## Sources

### Primary (HIGH confidence)
- Project codebase analysis: src/aishell/docker/run.clj, src/aishell/docker/volume.clj, src/aishell/config.clj
- Project memory: /home/jonasrodrigues/.claude/projects/-home-jonasrodrigues-projects-harness/memory/MEMORY.md (Babashka SCI symbol resolution lesson)
- Phase 47 verification: .planning/phases/47-state-config-schema-cleanup/47-VERIFICATION.md (confirmed state schema changes)
- Phase 46 summary: .planning/phases/46-foundation-image-cleanup/46-01-SUMMARY.md (confirmed tmux binary removal)
- Requirements: .planning/REQUIREMENTS.md (TMUX-04 through TMUX-09 requirements)

### Secondary (MEDIUM confidence)
- Phase 41 research: .planning/phases/41-tpm-initialization-in-entrypoint/41-RESEARCH.md (WITH_TMUX pattern documentation)
- Phase 42 plans: .planning/phases/42-resurrect-state-persistence/ (resurrect mount pattern)

### Tertiary (LOW confidence)
None - all findings verified with codebase inspection.

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH - No new libraries, pure deletion work
- Architecture: HIGH - Babashka SCI behavior documented in project memory, verified with codebase analysis
- Pitfalls: HIGH - Based on actual incident (Phase 47 parse-resurrect-config deletion)

**Research date:** 2026-02-06
**Valid until:** 60 days (stable APIs, no external dependencies)
