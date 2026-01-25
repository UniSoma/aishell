# Phase 29: Exec Command - Research

**Researched:** 2026-01-25
**Domain:** CLI subprocess management, Docker one-off commands, TTY detection
**Confidence:** HIGH

## Summary

This phase adds an `aishell exec <command>` subcommand that runs one-off commands in the container without entering an interactive shell. The implementation closely parallels the existing `run-container` function but uses `docker run` for one-off execution rather than interactive sessions.

The key technical challenge is **TTY auto-detection**: when stdin is a terminal, allocate a pseudo-TTY (`-t` flag); when stdin is piped or redirected, omit TTY allocation to allow proper piping. This is a well-understood problem with established solutions in Babashka.

The existing codebase architecture is well-suited for this feature. The `build-docker-args` function in `docker/run.clj` constructs Docker arguments, and can be extended with a parameter to conditionally include/exclude `-it` flags. The dispatch table in `cli.clj` can easily accommodate a new `exec` subcommand.

**Primary recommendation:** Extend `build-docker-args` with a `:tty?` parameter (auto-detected from `(some? (System/console))`), create a new `handle-exec` function in `cli.clj` that validates build state and delegates to a new execution path.

## Standard Stack

The established libraries/tools for this domain:

### Core
| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| babashka.process | Bundled | Subprocess execution | Native to Babashka, handles stdin/stdout |
| babashka.cli | Bundled | Command parsing | Already used for dispatch table |

### Supporting
| Library | Version | Purpose | When to Use |
|---------|---------|---------|-------------|
| System/console | JVM | TTY detection | Check if stdin is terminal |

### Alternatives Considered
| Instead of | Could Use | Tradeoff |
|------------|-----------|----------|
| System/console for TTY | `test -t 0` subprocess | System/console is simpler, already used in codebase |
| Single build-docker-args | Separate exec-docker-args | DRY - extend existing function instead |

**No new dependencies required.** All functionality is available in existing stack.

## Architecture Patterns

### Recommended Project Structure
```
src/aishell/
├── cli.clj              # Add handle-exec, exec-spec, dispatch entry
├── run.clj              # Add run-exec function (parallel to run-container)
└── docker/
    └── run.clj          # Extend build-docker-args with :tty? parameter
```

### Pattern 1: TTY Auto-Detection
**What:** Detect if stdin is connected to a terminal to decide TTY allocation
**When to use:** Any command that may be run interactively OR in scripts/pipes
**Example:**
```clojure
;; Source: Existing codebase pattern (detection/core.clj, output.clj)
(defn- tty?
  "Check if stdin is connected to a terminal."
  []
  (some? (System/console)))

;; Alternative using subprocess (from babashka examples)
(defn- tty-subprocess?
  "Check if stdin is TTY using test command."
  []
  (->> ["test" "-t" "0"]
       (p/process {:in :inherit :env {}})
       deref
       :exit
       (= 0)))
```

### Pattern 2: Conditional Docker Flags
**What:** Build docker arguments with optional TTY flags
**When to use:** When same base needs both interactive and non-interactive modes
**Example:**
```clojure
;; Extend existing build-docker-args signature
(defn build-docker-args
  [{:keys [project-dir image-tag config git-identity skip-pre-start tty?]}]
  (let [tty-flags (if tty? ["-it"] ["-i"])]  ; -i for stdin, -t only if TTY
    (-> ["docker" "run" "--rm" "--init"]
        (into tty-flags)
        ;; ... rest of existing logic
        )))
```

### Pattern 3: Error Before Execution
**What:** Validate prerequisites (build exists, image available) before running
**When to use:** Commands that depend on prior build step
**Example:**
```clojure
;; Source: Existing pattern in run.clj
(defn- require-build!
  "Exit with clear error if no build exists."
  []
  (when-not (state/read-state)
    (output/error "No image built. Run: aishell build")))

(defn- require-image!
  "Exit with clear error if image doesn't exist."
  [image-tag]
  (when-not (docker/image-exists? image-tag)
    (output/error (str "Image not found: " image-tag
                       "\nRun: aishell build"))))
```

### Anti-Patterns to Avoid
- **Hardcoding -it:** Always allocate TTY. Breaks piping: `echo "test" | aishell exec cat`
- **Duplicating docker args construction:** Creates maintenance burden. Extend existing function.
- **Skipping build verification:** User confusion when image missing. Verify first.
- **Using p/exec for piped commands:** p/exec replaces the process, can't capture exit codes. Use p/shell with :inherit for exec-style behavior.

## Don't Hand-Roll

Problems that look simple but have existing solutions:

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| TTY detection | Read /dev/tty | `System/console` | Already used in codebase, portable |
| Argument parsing | Manual string parsing | babashka.cli dispatch | Handles edge cases |
| Docker arg construction | New function | Extend build-docker-args | DRY, tested code |
| Error messages | Ad-hoc strings | output/error function | Consistent formatting, exit codes |

**Key insight:** The exec command shares 95% of infrastructure with run commands. Extend, don't duplicate.

## Common Pitfalls

### Pitfall 1: Forgetting -i Without -t
**What goes wrong:** Piped input is ignored, command hangs or fails
**Why it happens:** Removing -t also removes -i, stdin is not connected
**How to avoid:** Always include `-i` flag, only `-t` is conditional
**Warning signs:** `echo "test" | aishell exec cat` produces no output

### Pitfall 2: TTY Error Messages in CI/Scripts
**What goes wrong:** Docker error: "the input device is not a TTY"
**Why it happens:** `-t` flag with non-TTY stdin
**How to avoid:** Auto-detect TTY with `System/console`, only add `-t` when true
**Warning signs:** Works in terminal, fails in scripts/CI

### Pitfall 3: Exit Code Loss
**What goes wrong:** Script can't detect if exec'd command failed
**Why it happens:** Using p/exec instead of p/shell for non-interactive
**How to avoid:** For non-TTY mode, use p/shell with :inherit true, propagate exit code
**Warning signs:** `aishell exec false; echo $?` returns 0 instead of 1

### Pitfall 4: Missing Build Check
**What goes wrong:** Confusing Docker error about missing image
**Why it happens:** User runs exec before build
**How to avoid:** Check state/read-state first, provide clear "run aishell build" message
**Warning signs:** Docker errors instead of aishell guidance

### Pitfall 5: Sensitive File Detection on Exec
**What goes wrong:** Detection warnings appear for quick one-off commands
**Why it happens:** Reusing full run-container flow
**How to avoid:** Skip detection for exec (or make it opt-in)
**Warning signs:** Running `aishell exec ls` triggers long detection scan

## Code Examples

Verified patterns from existing codebase:

### TTY Detection (from detection/core.clj)
```clojure
;; Source: src/aishell/detection/core.clj:130-133
(defn- interactive?
  "Check if running in an interactive terminal (not CI/piped)."
  []
  (some? (System/console)))
```

### Build State Verification (from run.clj)
```clojure
;; Source: src/aishell/run.clj:70-82
(let [state (state/read-state)]
  (when-not state
    (output/error-no-build))
  ;; ... continue with validated state
  )
```

### CLI Dispatch Entry (from cli.clj)
```clojure
;; Source: src/aishell/cli.clj:249-252 - pattern for new commands
(def dispatch-table
  [{:cmds ["build"] :fn handle-build :spec build-spec :restrict true}
   {:cmds ["update"] :fn handle-update :spec update-spec :restrict true}
   ;; Add exec here:
   {:cmds ["exec"] :fn handle-exec :spec exec-spec :restrict true}
   {:cmds [] :spec global-spec :fn handle-default}])
```

### Process Execution with Exit Code (from run.clj)
```clojure
;; Source: src/aishell/run.clj:176-187 - gitleaks pattern
(let [result (apply p/shell {:inherit true :continue true}
                    (concat docker-args container-cmd))]
  (System/exit (:exit result)))
```

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| Always allocate TTY | Auto-detect TTY | Standard practice | Enables piping, scripts |
| docker exec into running container | docker run --rm for one-off | Design choice | Ephemeral by design |

**Design decision:** This codebase uses `docker run --rm` for all execution, not `docker exec`. This maintains the "ephemeral container" philosophy where each command gets a fresh container. The exec command follows this pattern - it's "exec a command in A container" not "exec into THE container."

**Deprecated/outdated:**
- None - this is new functionality

## Open Questions

Things that couldn't be fully resolved:

1. **Detection warnings for exec**
   - What we know: Run commands trigger sensitive file detection
   - What's unclear: Should exec also trigger detection? Adds latency for quick commands
   - Recommendation: Skip detection for exec by default (fast path). User can run `aishell gitleaks` separately if needed.

2. **Pre-start hooks for exec**
   - What we know: Run commands can trigger pre_start from config
   - What's unclear: Should exec run pre_start? May be unexpected for one-off commands
   - Recommendation: Skip pre_start for exec. One-off commands shouldn't start sidecars.

## Sources

### Primary (HIGH confidence)
- Existing codebase: `src/aishell/cli.clj`, `src/aishell/run.clj`, `src/aishell/docker/run.clj`
- Existing TTY detection: `src/aishell/detection/core.clj:130-133`, `src/aishell/output.clj:6-7`
- [Babashka process library](https://github.com/babashka/process) - subprocess handling
- [Babashka TTY detection example](https://github.com/babashka/babashka/blob/master/examples/is_tty.clj) - is_tty.clj

### Secondary (MEDIUM confidence)
- [Docker exec documentation](https://docs.docker.com/reference/cli/docker/container/exec/) - TTY flags
- [Docker run vs exec best practices](https://labs.iximiuz.com/tutorials/docker-run-vs-attach-vs-exec) - iximiuz Labs
- [Baeldung Docker -i -t options](https://www.baeldung.com/linux/docker-run-interactive-tty-options) - flag semantics

### Tertiary (LOW confidence)
- None - all findings verified with primary sources

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH - using existing codebase patterns, no new dependencies
- Architecture: HIGH - clear extension points, existing patterns to follow
- Pitfalls: HIGH - well-documented TTY issues, verified with existing code

**Research date:** 2026-01-25
**Valid until:** 60 days (stable domain, no rapid changes expected)
