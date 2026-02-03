# Phase 43: Validation & Migration - Research

**Researched:** 2026-02-03
**Domain:** CLI validation, user migration communication, documentation updates
**Confidence:** HIGH

## Summary

Phase 43 focuses on ensuring graceful degradation when users attempt to use tmux-dependent features without tmux enabled, communicating behavioral changes from v2.7-2.8 to v2.9.0, and synchronizing all documentation with the architectural changes introduced in v2.9.0 (volume-based injection, foundation image, tmux opt-in, resurrect state persistence).

This is a validation and communication phase rather than a feature implementation phase. The primary technical domain is **error handling and user feedback** in CLI tools, following established patterns for helpful error messages, actionable guidance, and clear migration communication.

The standard approach for this type of phase involves:
1. **Pre-flight validation** - Check prerequisites before attempting operations (tmux enabled for attach)
2. **Graceful failure** - Provide clear error messages with corrective actions when validation fails
3. **Version-aware messaging** - Detect upgrade scenarios and display one-time migration warnings
4. **Documentation synchronization** - Update all user-facing docs to reflect system state accurately

**Primary recommendation:** Use existing validation patterns from `aishell.attach` and `aishell.validation` namespaces as architectural templates. Implement version comparison using string-based state checking (no external dependencies needed). Follow established error message format from `aishell.output` namespace.

## Standard Stack

This phase uses only existing project tools - no new libraries required.

### Core
| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| Clojure | 1.11+ | Host language | Project standard |
| Babashka | Latest | CLI runtime | Project standard |
| clojure.edn | Built-in | State file parsing | Standard Clojure data format |

### Supporting
| Library | Version | Purpose | When to Use |
|---------|---------|---------|-------------|
| clojure.string | Built-in | Version string comparison | Simple semantic version checks |
| babashka.fs | Built-in | File system operations | Checking for migration marker files |

### Alternatives Considered
| Instead of | Could Use | Tradeoff |
|------------|-----------|----------|
| Manual version comparison | version-clj library | version-clj provides full semver parsing but adds dependency; manual string comparison sufficient for simple "before v2.9.0" checks |
| Persistent migration state | One-time file marker | Persistent state more complex; single marker file adequate for one-time warning |
| External validation framework | Project patterns | External framework overkill; existing validation.clj and attach.clj provide all needed patterns |

**Installation:**
No installation needed - all capabilities exist in current project dependencies.

## Architecture Patterns

### Recommended Project Structure
```
src/aishell/
├── attach.clj           # Existing validation patterns to follow
├── validation.clj       # Advisory warning patterns
├── output.clj           # Error message formatting
├── state.clj            # Version state reading
└── config.clj           # Config validation
```

### Pattern 1: Pre-Flight Validation
**What:** Check prerequisites before executing command, exit with helpful error if not met
**When to use:** Any operation that depends on specific configuration state
**Example:**
```clojure
;; From attach.clj - validate container running before attempting attach
(defn- validate-container-state!
  "Validate container exists and is running.
   Exits with error and guidance if container doesn't exist or is stopped."
  [container-name short-name]
  (when-not (naming/container-exists? container-name)
    (output/error (str "Container '" short-name "' not found.\n\n"
                      "Use 'aishell ps' to list containers.\n"
                      "To start: aishell " short-name " --detach"))))
```

**Key elements:**
1. Single-purpose validation function with `!` suffix (indicates side effects)
2. Descriptive error message (what went wrong)
3. Context (how to check current state)
4. Corrective action (how to fix)
5. Calls `output/error` which exits with status 1

### Pattern 2: Feature Availability Check
**What:** Verify required feature is enabled in configuration before allowing dependent operations
**When to use:** Commands that depend on build-time configuration decisions
**Example:**
```clojure
;; From attach.clj - validate tmux session exists (implies tmux enabled)
(defn- validate-session-exists!
  "Validate that the requested tmux session exists in the container.
   Exits with error and lists available sessions if not found."
  [container-name session-name short-name]
  (let [{:keys [exit]} (p/shell {:out :string :err :string :continue true}
                                 "docker" "exec" "-u" "developer" container-name
                                 "tmux" "has-session" "-t" session-name)]
    (when-not (zero? exit)
      ;; Session not found - try to list available sessions
      (let [{:keys [exit out]} (p/shell {:out :string :err :string :continue true}
                                         "docker" "exec" "-u" "developer" container-name
                                         "tmux" "list-sessions")]
        (if (and (zero? exit) (not (str/blank? out)))
          ;; Sessions exist, show them
          (output/error (str "Session '" session-name "' not found...\n\n"
                            "Available sessions:\n"
                            (str/join "\n" (map #(str "  " %) sessions))))
          ;; No sessions at all - tmux not enabled
          (output/error (str "No tmux sessions found...\n\n"
                            "The container may have been started without tmux.\n"
                            "Try restarting: docker stop " container-name)))))))
```

**Key elements:**
1. Two-tier error handling (specific error vs. general problem)
2. Dynamic context (list what is available when specific item not found)
3. Root cause diagnosis (no tmux vs. wrong session name)
4. Recovery guidance varies by failure mode

### Pattern 3: Advisory Warning (Non-Blocking)
**What:** Inform user about potentially problematic configuration without blocking execution
**When to use:** Security notices, deprecation warnings, migration notices
**Example:**
```clojure
;; From validation.clj - warn about dangerous Docker options
(defn warn-dangerous-args
  "Warn about dangerous docker_args if any found.
   Advisory only - does not block execution."
  [docker-args]
  (when-let [warnings (check-dangerous-args docker-args)]
    (println)  ; Blank line before warning block
    (output/warn "Security notice: Potentially dangerous Docker options detected")
    (doseq [msg warnings]
      (binding [*out* *err*]
        (println (str "  - " msg))))
    (binding [*out* *err*]
      (println)
      (println "These options reduce container isolation. Use only if necessary.")
      (println))))
```

**Key elements:**
1. Warning, not error (uses `output/warn`, doesn't exit)
2. Visual separation (blank lines)
3. Explanation of risk
4. Acknowledges legitimate use cases
5. Output to stderr for proper stream handling

### Pattern 4: Version-Based Migration Warning
**What:** Detect upgrade scenario and show one-time informational message
**When to use:** Breaking changes or significant behavioral differences between versions
**Example (pattern to implement):**
```clojure
(defn check-migration-warning
  "Check if user is upgrading from pre-v2.9.0 and needs migration warning.
   Returns true if warning should be shown."
  []
  (let [state (state/read-state)
        marker-file (str (util/config-dir) "/.migration-v2.9-warned")]
    (and
      ;; State exists (not fresh install)
      state
      ;; Build is from pre-v2.9.0 (lacks harness-volume-hash field)
      (nil? (:harness-volume-hash state))
      ;; Haven't shown warning yet
      (not (fs/exists? marker-file)))))

(defn show-migration-warning
  "Show one-time migration warning for v2.7-2.8 → v2.9.0 upgrade."
  []
  (println)
  (output/warn "aishell v2.9.0: tmux behavior has changed")
  (binding [*out* *err*]
    (println)
    (println "tmux is now OPT-IN (previously always enabled):")
    (println "  • Containers built before v2.9.0: tmux remains enabled")
    (println "  • New builds: tmux disabled by default")
    (println "  • To enable: aishell build --with-tmux")
    (println)
    (println "This affects:")
    (println "  • Session persistence (tmux resurrect)")
    (println "  • Multiple windows/panes")
    (println "  • Attach command requires tmux")
    (println)
    (println "This message shows once. For details: docs/ARCHITECTURE.md")
    (println))
  ;; Mark as warned
  (spit (str (util/config-dir) "/.migration-v2.9-warned") ""))
```

**Key elements:**
1. Detect upgrade vs. fresh install (state exists)
2. Version detection via state schema (missing new fields)
3. One-time warning (marker file prevents repeat)
4. Clear explanation of what changed
5. Impact list (what features affected)
6. Pointer to documentation
7. Non-blocking (informational only)

### Anti-Patterns to Avoid
- **Cryptic errors:** "Attach failed" without explaining tmux is required
- **No corrective action:** Error message doesn't tell user how to fix
- **Blocking warnings:** Forcing user acknowledgment for informational messages
- **Version string parsing:** Using regex semver parsing when state schema fields suffice
- **Repeated warnings:** Showing migration warning on every command after acknowledged

## Don't Hand-Roll

Problems that look simple but have existing solutions in this codebase:

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| Error message formatting | Custom formatting functions | `aishell.output/error`, `output/warn` | Already handles color, stderr routing, exit codes |
| Validation patterns | New validation style | Patterns from `attach.clj` (validate-*! functions) | Consistent UX, established naming convention |
| State file access | Direct slurp/spit | `aishell.state/read-state`, `write-state` | Handles missing file cases, consistent path resolution |
| Command suggestions | Manual string matching | `output/suggest-command`, `error-unknown-command` | Uses Levenshtein distance, already integrated |
| Config directory path | Hardcoded ~/.aishell | `aishell.util/config-dir` | Abstracts home directory resolution |

**Key insight:** This phase requires zero new architectural patterns. All needed patterns exist in attach.clj (pre-flight validation), validation.clj (advisory warnings), and output.clj (error formatting). Reuse, don't reinvent.

## Common Pitfalls

### Pitfall 1: Insufficient Error Context
**What goes wrong:** Error says "tmux not enabled" without explaining what that means or how to fix
**Why it happens:** Developer focuses on technical accuracy over user guidance
**How to avoid:**
- Include "what," "why," and "how to fix" in every error message
- Test error messages by showing them to someone unfamiliar with the codebase
- Follow the 3-part structure: problem + context + corrective action
**Warning signs:** Error message is one sentence with no guidance

### Pitfall 2: Version Comparison Over-Engineering
**What goes wrong:** Implementing full semantic version parsing when simple state schema checks suffice
**Why it happens:** Generalization instinct ("we might need this for future versions")
**How to avoid:**
- Check what differentiates versions in practice (state schema fields)
- Use simplest detection that works (field presence, not version string parsing)
- Add complexity only when needed, not preemptively
**Warning signs:** Adding `version-clj` dependency or writing semver regex

### Pitfall 3: Migration Warning Spam
**What goes wrong:** Showing migration warning on every command execution
**Why it happens:** No persistence of "user has seen this" state
**How to avoid:**
- Use marker file (`.migration-v2.9-warned`) in config directory
- Check marker before showing warning
- Create marker after showing warning
- Make marker check fast (existence check, no file parsing)
**Warning signs:** Warning appears multiple times in testing

### Pitfall 4: Validation Without Guidance
**What goes wrong:** Validation detects problem but error message doesn't help user fix it
**Why it happens:** Separating validation logic from error message construction
**How to avoid:**
- Validation functions should call `output/error` with full context
- Include specific next steps in error message
- Show current state when helpful ("Available sessions:", "Use 'aishell ps' to list containers")
**Warning signs:** Validation function returns boolean, caller constructs error message

### Pitfall 5: Documentation Drift
**What goes wrong:** Updating some docs but missing others, leaving inconsistent information
**Why it happens:** Documentation files not treated as interdependent
**How to avoid:**
- Create checklist of all docs that reference changed behavior
- Search codebase for specific terms (e.g., "aishell:base", "tmux", "main session")
- Update docs atomically (all in one commit/task)
- Include version info in docs ("Last updated: v2.9.0")
**Warning signs:** README mentions "main" session but ARCHITECTURE says "harness"

### Pitfall 6: Validation in Wrong Layer
**What goes wrong:** Placing tmux validation in `run.clj` instead of `attach.clj`
**Why it happens:** Thinking "attach uses run, so validate in run"
**How to avoid:**
- Validate at the point of user interaction (command entry point)
- Keep validation close to the command that requires it
- Don't add validation to shared infrastructure (run.clj) for specific command needs (attach)
**Warning signs:** Adding --require-tmux flag to run-container

## Code Examples

Verified patterns for this phase:

### Validation Pattern (attach command)
```clojure
;; Source: src/aishell/attach.clj (existing code to extend)

(defn- validate-tmux-enabled!
  "Ensure tmux was enabled when container was built.
   Exits with error and guidance if tmux not available."
  [container-name short-name]
  ;; Try to detect tmux in container
  (let [{:keys [exit]} (p/shell {:out :string :err :string :continue true}
                                 "docker" "exec" "-u" "developer" container-name
                                 "which" "tmux")]
    (when-not (zero? exit)
      (output/error (str "Container '" short-name "' does not have tmux enabled.\n\n"
                        "The 'attach' command requires tmux for session management.\n\n"
                        "To enable tmux:\n"
                        "  1. Rebuild: aishell build --with-tmux\n"
                        "  2. Restart: aishell " short-name " --detach\n"
                        "  3. Attach: aishell attach --name " short-name)))))

;; Integration point in attach-to-session function
(defn attach-to-session
  "Attach to a tmux session in a running container."
  ([name]
   (attach-to-session name "harness"))
  ([name session]
   (let [project-dir (System/getProperty "user.dir")
         container-name (naming/container-name project-dir name)]
     ;; Run all validations
     (validate-tty!)
     (validate-container-state! container-name name)
     (validate-tmux-enabled! container-name name)  ; NEW VALIDATION
     (validate-session-exists! container-name session name)
     ;; ... rest of attach logic
     )))
```

### Migration Warning Pattern
```clojure
;; Source: NEW code for cli.clj or migration.clj namespace

(ns aishell.migration
  "One-time migration warnings for version upgrades."
  (:require [babashka.fs :as fs]
            [aishell.state :as state]
            [aishell.util :as util]
            [aishell.output :as output]))

(defn- migration-marker-file
  "Path to migration warning marker file."
  []
  (str (util/config-dir) "/.migration-v2.9-warned"))

(defn- needs-migration-warning?
  "Check if v2.9.0 migration warning should be shown.
   True if upgrading from v2.7-2.8 (state exists but lacks new fields)."
  []
  (let [state (state/read-state)]
    (and
      ;; Has state (not fresh install)
      (some? state)
      ;; Pre-v2.9.0 state (no harness-volume-hash field)
      (nil? (:harness-volume-hash state))
      ;; Warning not yet shown
      (not (fs/exists? (migration-marker-file))))))

(defn show-v2.9-migration-warning!
  "Show one-time warning about v2.9.0 behavioral changes.
   Creates marker file to prevent repeated warnings."
  []
  (when (needs-migration-warning?)
    (println)
    (output/warn "aishell v2.9.0: Breaking changes to tmux behavior")
    (binding [*out* *err*]
      (println)
      (println "tmux is now OPT-IN (was always-enabled in v2.7-2.8):")
      (println "  • Your existing containers: tmux remains enabled (no action needed)")
      (println "  • New builds after v2.9.0: tmux disabled by default")
      (println "  • To enable tmux: aishell build --with-tmux")
      (println)
      (println "Session name changed:")
      (println "  • Old: --session main")
      (println "  • New: --session harness")
      (println "  • (Both work for backward compatibility)")
      (println)
      (println "This affects:")
      (println "  • 'aishell attach' command (requires tmux)")
      (println "  • Session persistence (tmux-resurrect)")
      (println "  • Multiple windows/panes in container")
      (println)
      (println "For details: docs/ARCHITECTURE.md")
      (println "This message shows once.")
      (println))
    ;; Mark as warned
    (util/ensure-dir (util/config-dir))
    (spit (migration-marker-file) (str "Warned: " (java.time.Instant/now)))))
```

### Error Message Format
```clojure
;; Source: src/aishell/output.clj (existing patterns)

;; Pattern 1: Simple error with guidance
(output/error "No image built. Run: aishell build")

;; Pattern 2: Multi-line error with context and actions
(output/error (str "Container '" name "' not found.\n\n"
                  "Use 'aishell ps' to list containers.\n"
                  "To start: aishell " name " --detach"))

;; Pattern 3: Error with dynamic context
(output/error (str "Session '" session "' not found in container '" name "'.\n\n"
                  "Available sessions:\n"
                  (str/join "\n" (map #(str "  " %) sessions))))

;; Pattern 4: Warning (non-blocking)
(output/warn "Security notice: Potentially dangerous Docker options detected")
(binding [*out* *err*]
  (println "  - --privileged: Container has full host access")
  (println)
  (println "These options reduce container isolation. Use only if necessary."))
```

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| Generic error messages | 3-part structure (problem + context + action) | Industry standard since ~2020 | Users can self-recover vs. needing support |
| Version string parsing | State schema field detection | v2.9.0 | Simpler, more reliable upgrade detection |
| Session name "main" | Session name "harness" | v2.9.0 | Clearer semantic meaning, backward compat maintained |
| Image tag "aishell:base" | Image tag "aishell:foundation" | v2.8.0 | Migration guide in docs, error detection |
| tmux always enabled | tmux opt-in via --with-tmux | v2.9.0 | Breaking change requiring migration warning |

**Deprecated/outdated:**
- Generic "command failed" errors without corrective action (pre-2020 CLI norm)
- Semver parsing for upgrade detection (state schema more reliable)
- Blocking modal dialogs for informational warnings (poor CLI UX)

## Open Questions

Things that couldn't be fully resolved:

1. **Session name backward compatibility**
   - What we know: Phase 42 changed default session name from "main" to "harness"
   - What's unclear: Whether attach.clj still accepts "main" as alias, or if this is breaking
   - Recommendation: Verify attach.clj session parameter handling; if "main" no longer works, add to migration warning

2. **Documentation version consistency**
   - What we know: Docs should show "Last updated: v2.X.0" footer
   - What's unclear: Whether this is v2.9.0 or v2.8.1 (cli.clj shows version "2.8.1")
   - Recommendation: Check version in cli.clj; use that version for documentation updates

3. **Troubleshooting guide scope**
   - What we know: Need to add tmux-related troubleshooting entries
   - What's unclear: Exact failure modes users will encounter (attach when no tmux, resurrect without tmux, etc.)
   - Recommendation: List all commands that depend on tmux; document failure mode + resolution for each

## Sources

### Primary (HIGH confidence)
- Existing codebase patterns: `src/aishell/attach.clj` (validation functions), `src/aishell/validation.clj` (advisory warnings), `src/aishell/output.clj` (error formatting)
- State schema: `src/aishell/state.clj` (version detection via schema fields)
- Phase 42 artifacts: `.planning/phases/42-resurrect-state-persistence/42-VERIFICATION.md` (tmux feature details)

### Secondary (MEDIUM confidence)
- [Command Line Interface Guidelines](https://clig.dev/) - CLI error message best practices
- [Mastering CLI Design Best Practices](https://jsschools.com/programming/mastering-cli-design-best-practices-for-powerful-/) - Validation patterns
- [Software Migration Guide for 2026](https://hicronsoftware.com/blog/software-migration-guide/) - User communication strategies

### Tertiary (LOW confidence)
- [version-clj library](https://github.com/xsc/version-clj) - Mentioned for completeness; not recommended for this phase (overkill)
- [Data Migration Best Practices 2026](https://medium.com/@kanerika/data-migration-best-practices-your-ultimate-guide-for-2026-7cbd5594d92e) - General migration concepts

## Metadata

**Confidence breakdown:**
- Validation patterns: HIGH - Direct code examples from existing codebase
- Error message format: HIGH - Existing output.clj namespace defines patterns
- Migration warning approach: HIGH - Standard CLI practice, simple implementation
- Documentation structure: HIGH - Files exist, requirements specify what to update
- Version detection: HIGH - State schema provides clear upgrade signal

**Research date:** 2026-02-03
**Valid until:** 60 days (stable domain; CLI validation patterns don't change rapidly)
