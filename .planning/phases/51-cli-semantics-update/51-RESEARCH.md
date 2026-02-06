# Phase 51: CLI Semantics Update - Research

**Researched:** 2026-02-06
**Domain:** Babashka CLI argument parsing, container naming, Docker run semantics
**Confidence:** HIGH

## Summary

Phase 51 updates aishell's CLI semantics to complete the v3.0.0 migration from tmux-based session management to docker-native attach workflows. The phase removes the `--detach`/`-d` flag (always-attached model) and changes default container naming so harness names become container names directly (e.g., `aishell claude` creates a container named `claude`, not a random or session-based name).

The current implementation already has the foundations:
1. Container naming via `aishell.docker.naming/container-name` generates deterministic names: `aishell-{8-char-hash}-{name}`
2. `--name` flag extraction already exists in `cli.clj` dispatch for harness commands
3. Detach mode exists but will be removed (lines 484-486, 492-500 in cli.clj)
4. `ensure-name-available!` already enforces no-duplicate-running-containers (naming.clj:90-106)

**Primary recommendation:** This is primarily a CLI argument routing change, not architectural. Remove detach flag extraction, update default container name resolution from `(or (:container-name opts) cmd "shell")` to use cmd/harness-name directly, and update help text to reflect always-attached semantics.

## Standard Stack

### Core
| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| babashka.cli | 0.7.52+ | CLI argument parsing | Official Babashka CLI library, handles dispatch, spec validation, positional args |
| babashka.process | Latest | Process execution | Built-in, used for docker exec/run invocations |

### Supporting
| Library | Version | Purpose | When to Use |
|---------|---------|---------|-------------|
| clojure.string | Built-in | String manipulation | Parsing container names, formatting output |

**Installation:**
No new dependencies. babashka.cli already in use (see src/aishell/cli.clj line 2).

## Architecture Patterns

### Current Container Naming Flow

```
User command: `aishell claude --name bar`
                    ↓
cli.clj dispatch (line 490-500):
  - Extracts --name flag → container-name-override = "bar"
  - Removes --name and value from args
  - Passes to run/run-container with {:container-name "bar"}
                    ↓
run.clj (line 115-116):
  name-part = (or (:container-name opts) cmd "shell")
  container-name-str = (naming/container-name project-dir name-part)
                    ↓
naming.clj (line 41-47):
  Validates name against Docker naming rules
  Returns "aishell-{hash}-bar"
                    ↓
ensure-name-available! (line 90-106):
  Checks if container running → error
  Checks if container stopped → auto-remove
  Proceeds if available
```

### Recommended Pattern: Always-Attached Model

With detach removed, containers are ALWAYS foreground-attached:

**Creation commands** (aishell, aishell claude, aishell --name X):
- Run `docker run -it` (foreground, TTY allocated)
- Process replaced via `p/exec` (terminal takeover)
- Container stops when user exits shell/harness

**Attach commands** (aishell attach X):
- Run `docker exec -it bash` to existing container
- Process replaced via `p/exec`
- Container continues running after detach (Ctrl+D or exit)

**Key insight:** Without detach mode, there's no "background container creation". Users who want persistent containers must:
1. Start container in one terminal: `aishell claude` (foreground)
2. Attach from another terminal: `aishell attach claude`

This is the standard pattern for interactive Docker workflows (see Docker best practices).

### Pattern: Container Name Resolution

**Current (v2.10.0):**
```clojure
;; Default name is cmd (harness) or "shell"
name-part = (or (:container-name opts) cmd "shell")

;; Examples:
;; aishell → "shell"
;; aishell claude → "claude"
;; aishell --name foo → "foo"
;; aishell claude --name bar → "bar"
```

**Required (v3.0.0):**
No change needed! Current logic already satisfies CLI-01 through CLI-04. The name resolution is correct.

**Verification:**
- CLI-01: `aishell` (no args) → cmd=nil → name="shell" ✓
- CLI-02: `aishell claude` → cmd="claude" → name="claude" ✓
- CLI-03: `aishell --name foo` → cmd=nil, override="foo" → name="foo" ✓
- CLI-04: `aishell claude --name bar` → cmd="claude", override="bar" → name="bar" ✓

### Pattern: Detach Flag Removal

**Current extraction (cli.clj:484-500):**
```clojure
;; Extract --detach/-d flag before pass-through
detach? (boolean (some #{"-d" "--detach"} clean-args))
clean-args (vec (remove #{"-d" "--detach"} clean-args))

;; Pass to run-container
(run/run-container "claude" args {:detach detach?})
```

**Required change:**
1. Remove detach extraction (lines 484-486)
2. Remove detach from run/run-container calls (lines 536-548)
3. Remove detach handling in run.clj (lines 202, 236, 240-258)
4. Remove detach flag from docker/run.clj build-docker-args (line 236, 321, 330)

### Anti-Patterns to Avoid

**Don't: Validate container name twice**
- Naming.clj already validates in `validate-container-name!` (line 23-39)
- Don't add duplicate validation in cli.clj

**Don't: Parse --name in multiple places**
- Currently extracted once in cli.clj dispatch (line 490-500)
- Attach command parses its own positional arg (correct - different semantics)
- Keep this separation

**Don't: Add "no-detach" flag or deprecation warning**
- This is a breaking change in v3.0.0
- Users upgrading will see error "unknown option: --detach"
- Clean break, no gradual migration

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| CLI argument parsing | Manual sys/argv parsing | babashka.cli/dispatch | Handles specs, validation, error messages, restrict mode |
| Container name validation | Custom regex | Docker naming rules (already in naming.clj) | Docker has strict rules, already implemented |
| Duplicate detection | Manual docker ps parsing | naming/ensure-name-available! | Already handles running vs stopped, auto-removes stopped |

**Key insight:** The infrastructure already exists. This phase is primarily **removal** (detach flag) and **verification** (naming already correct), not new features.

## Common Pitfalls

### Pitfall 1: Breaking Change Communication
**What goes wrong:** Users upgrading to v3.0.0 run `aishell claude --detach` and get cryptic error
**Why it happens:** babashka.cli restrict mode rejects unknown options
**How to avoid:**
- Ensure error message is clear: "Error: Unknown option: --detach"
- Add to CHANGELOG with migration guidance
- Consider adding to migration.clj warning (like v2.9 migration warning)
**Warning signs:** User confusion in issues/support requests

### Pitfall 2: Forgetting Detach Mode Side Effects
**What goes wrong:** Remove flag but leave detach-mode code paths (p/shell with output capture, "Container started" messages)
**Why it happens:** Detach mode has multiple code paths (cli.clj extraction, run.clj routing, docker/run.clj args)
**How to avoid:**
- grep -rn "detach" src/ to find all references
- Remove all detach-related code, not just flag extraction
- Test that foreground mode (p/exec) is always used
**Warning signs:** Dead code paths, unreachable println statements

### Pitfall 3: Help Text Inconsistency
**What goes wrong:** Help shows examples with --detach after flag removed
**Why it happens:** Help text not updated in cli.clj print-help, print-ps help
**How to avoid:**
- Search for "detach" in help text (cli.clj lines 94-132)
- Update ps command help (currently says "aishell claude --detach")
- Update attach help (currently references detached containers)
**Warning signs:** User confusion from help examples that don't work

### Pitfall 4: State File Confusion
**What goes wrong:** Old state files have detach-related fields, unclear if they need migration
**Why it happens:** State evolution across versions
**How to avoid:**
- Check state.clj and state/read-state for any detach-related fields
- Grep revealed: No state fields for detach (container naming already project-scoped)
- No migration needed for state
**Warning signs:** N/A - no detach state exists

## Code Examples

Verified patterns from current codebase:

### Container Name Generation (naming.clj:41-47)
```clojure
;; Source: src/aishell/docker/naming.clj
(defn container-name
  "Generate deterministic container name from project directory and name.
   Format: aishell-{8-char-hash}-{name}
   Validates name before generating."
  [project-dir name]
  (validate-container-name! name)
  (str "aishell-" (project-hash project-dir) "-" name))

;; Example outputs:
;; (container-name "/home/user/project" "claude")
;; => "aishell-a1b2c3d4-claude"
```

### Container Name Validation (naming.clj:23-39)
```clojure
;; Source: src/aishell/docker/naming.clj
(defn validate-container-name!
  "Validate user-provided name portion against Docker naming rules.
   Must start with alphanumeric, can contain alphanumeric, underscore, period, hyphen.
   Full container name (aishell-XXXXXXXX-{name}) must not exceed 63 characters.
   Exits with error if validation fails."
  [name]
  (when (empty? name)
    (output/error "Container name cannot be empty"))
  (when-not (re-matches #"^[a-zA-Z0-9][a-zA-Z0-9_.-]*$" name)
    (output/error (str "Invalid container name: " name
                      "\nMust start with alphanumeric, can contain alphanumeric, underscore, period, hyphen")))
  ;; Full name format: "aishell-" (8 chars) + hash (8 chars) + "-" (1 char) + name
  ;; = 17 chars + name length. Max Docker name length is 63 chars.
  ;; So name portion max is 46 chars.
  (when (> (count name) 46)
    (output/error (str "Container name too long: " name
                      "\nMaximum length: 46 characters (full container name must not exceed 63)"))))
```

### Duplicate Container Prevention (naming.clj:90-106)
```clojure
;; Source: src/aishell/docker/naming.clj
(defn ensure-name-available!
  "Ensure container name is available for use.
   If a container with this name is running, exit with error showing attach options.
   If a stopped container exists, remove it and proceed.
   If no container exists, proceed silently."
  [container-name harness-name]
  (case (remove-container-if-stopped! container-name)
    :running
    (output/error (str "Container '" container-name "' is already running.\n"
                      "To attach: aishell attach " harness-name "\n"
                      "To force stop: docker stop " container-name))

    :removed
    (println (str "Removed stopped container: " container-name))

    :not-found
    nil))
```

### Always-Foreground Execution (run.clj:259-263)
```clojure
;; Source: src/aishell/run.clj (foreground mode, current)
;; This is the ONLY execution mode in v3.0.0 (detach removed)
(let [project-name (.getName (java.io.File. project-dir))]
  (print (str "\033]2;[aishell] " project-name "\007"))  ; Set terminal title
  (flush)
  (apply p/exec (concat docker-args container-cmd)))  ; Replace process - terminal takeover
```

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| tmux-based background sessions | Docker exec attach to foreground containers | v3.0.0 (Phase 46-51) | Window management on host, not in container |
| `--detach` flag for background mode | Always-attached (foreground only) | v3.0.0 (Phase 51) | Simplified model: one container = one terminal |
| Random/session-based container names | Harness name = container name | v3.0.0 (Phase 51) | Predictable: `aishell claude` → container "claude" |

**Deprecated/outdated:**
- `--detach`/`-d` flag: Removed in v3.0.0, use host terminal multiplexing instead
- tmux inside containers: Removed in v3.0.0, use `tmux`/`screen` on host
- Complex attach --session management: Removed in Phase 50, now simple `aishell attach <name>`

## Open Questions

Things that couldn't be fully resolved:

1. **Migration strategy for existing detached containers**
   - What we know: v3.0.0 removes --detach, but doesn't address existing containers created with v2.x --detach
   - What's unclear: Should migration.clj detect and warn about running v2.x detached containers?
   - Recommendation: Document in CHANGELOG that users should stop v2.x detached containers before upgrading. No automatic migration (clean break).

2. **Help text examples post-detach**
   - What we know: ps command help currently shows "aishell claude --detach" (cli.clj:452)
   - What's unclear: What's the new recommended workflow for persistent containers?
   - Recommendation: Help should show two-terminal workflow: "Terminal 1: aishell claude" + "Terminal 2: aishell attach claude"

3. **Error message clarity for removed flag**
   - What we know: babashka.cli restrict mode will reject --detach with "Unknown option: --detach"
   - What's unclear: Is this clear enough, or should we add custom error handling?
   - Recommendation: Standard babashka.cli error is sufficient. Add to CHANGELOG and docs.

## Sources

### Primary (HIGH confidence)
- Codebase analysis:
  - `/home/jonasrodrigues/projects/harness/src/aishell/cli.clj` - CLI dispatch, flag extraction
  - `/home/jonasrodrigues/projects/harness/src/aishell/run.clj` - Container creation, name resolution
  - `/home/jonasrodrigues/projects/harness/src/aishell/docker/naming.clj` - Name validation, duplicate prevention
  - `/home/jonasrodrigues/projects/harness/src/aishell/attach.clj` - Attach command (Phase 50 rewrite)
  - `/home/jonasrodrigues/projects/harness/.planning/REQUIREMENTS.md` - CLI-01 through CLI-06 requirements
  - `/home/jonasrodrigues/projects/harness/.planning/ROADMAP.md` - Phase 51 description and success criteria

### Secondary (MEDIUM confidence)
- [GitHub - babashka/cli](https://github.com/babashka/cli) - Official babashka.cli repository with dispatch, spec, positional args documentation
- [Babashka CLI API Documentation](https://github.com/babashka/cli/blob/main/API.md) - parse-opts, parse-args, dispatch semantics
- [org.babashka/cli 0.7.52 Readme](https://cljdoc.org/d/org.babashka/cli/0.7.52/doc/readme) - Current version documentation with subcommands and positional arguments
- [Docker Docs: Running containers](https://docs.docker.com/engine/containers/run/) - Foreground vs detached mode semantics, best practices

### Tertiary (LOW confidence)
- [Medium: Docker Running Modes](https://medium.com/@shettysandesh.ss1996/exploring-docker-running-modes-foreground-vs-detached-64435943078f) - Community explanation of foreground/detached modes (general context, not authoritative for design decisions)

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH - babashka.cli already in use, no new dependencies
- Architecture: HIGH - Code analysis reveals existing infrastructure, minimal changes needed
- Pitfalls: HIGH - Based on code grep analysis and past phase patterns (migration warnings, help text)

**Research date:** 2026-02-06
**Valid until:** 2026-03-06 (30 days - stable domain, babashka.cli mature)
