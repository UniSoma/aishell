# Phase 33: Attach Command - Research

**Researched:** 2026-01-31
**Domain:** Docker exec with tmux, CLI subcommand implementation, container validation
**Confidence:** HIGH

## Summary

Phase 33 implements the `aishell attach` command to reconnect users to running containers' tmux sessions. Building on Phase 32's tmux auto-start (all containers start in `tmux` session "main") and Phase 30's container naming, this phase provides the final piece of the multi-container workflow: the ability to attach to running harnesses for observation and co-debugging.

The core technical decision is **`docker exec` with `tmux attach`** (not `docker attach`). This approach:
1. Creates a new login session via `docker exec -it`, ensuring proper TTY and environment setup
2. Uses the same user-switching path (`gosu`) as container start, avoiding socket permission issues
3. Executes `tmux attach-session -t <session>` to join the existing tmux session
4. Allows multiple simultaneous attachments (tmux multi-client support)

Key findings:
- **docker exec vs docker attach**: exec creates new process tree with proper entrypoint execution (gosu, env setup), attach connects directly to PID 1 stdin/stdout (no user switching, breaks with gosu)
- **tmux socket permissions**: Must use same UID/GID as container start or socket access fails ("permission denied")
- **Session validation**: Use `tmux has-session -t <session>` to validate before attach, show available sessions on error
- **Non-existent container handling**: `docker inspect` exit code is most reliable check for existence, `docker ps --filter` for running state
- **TTY requirement**: `docker exec -it` requires interactive terminal, must validate stdin is TTY before execution

**Primary recommendation:** Create `attach` subcommand in CLI that validates container state, constructs `docker exec -it <container> tmux attach-session -t <session>` command with proper error handling, and provides helpful feedback when sessions don't exist or container is stopped.

## Standard Stack

The established libraries/tools for this domain:

### Core
| Component | Version | Purpose | Why Standard |
|-----------|---------|---------|--------------|
| `docker exec -it` | Docker CLI | Execute command in running container with TTY | Standard Docker pattern, creates new process with full environment |
| `tmux attach-session -t` | tmux 3.3a+ | Attach to existing named session | Full command form, version-stable, works across tmux versions |
| `tmux has-session -t` | tmux 3.3a+ | Validate session exists | Exit code 0 if exists, 1 if not - scriptable check |
| `tmux list-sessions` | tmux 3.3a+ | Enumerate available sessions | Discovery when specified session doesn't exist |
| `babashka.cli` | Built into Babashka | CLI subcommand parsing | Already in use for all commands |
| `babashka.process` | Built into Babashka | Docker/tmux command execution | Existing pattern, p/exec for attach (terminal takeover) |

### Supporting
| Component | Version | Purpose | When to Use |
|-----------|---------|---------|-------------|
| Phase 30 `naming/container-name` | v2.6.0 | Generate full container name | Compute aishell-{hash}-{name} from project dir + user name |
| Phase 30 `naming/container-running?` | v2.6.0 | Validate container is running | Pre-flight check before exec |
| `System/console` | JVM built-in | Check if stdin is TTY | Validation - attach requires interactive terminal |

### Alternatives Considered
| Instead of | Could Use | Tradeoff |
|------------|-----------|----------|
| `docker attach` | `docker exec` with tmux | docker attach connects to PID 1 (no user switching), breaks with gosu; exec works correctly |
| Short tmux command `tmux a` | Full form `tmux attach-session -t` | Short forms not stable across tmux versions, full form unambiguous |
| Parsing `tmux ls` output | `tmux has-session -t` | has-session provides exit code, simpler and more reliable |
| `docker exec bash -c "tmux attach"` | `docker exec tmux attach` | sh -c wrapper breaks TTY allocation, direct exec works |

**Installation:**
No additional dependencies required. All functionality available in current stack.

## Architecture Patterns

### Recommended Code Structure
```
src/aishell/
├── cli.clj              # Add attach subcommand to dispatch table
├── attach.clj           # NEW: Attach command implementation
├── docker/
│   ├── naming.clj       # Existing: container-name, container-running?
│   └── ...
```

### Pattern 1: Attach Command with Default Session
**What:** Attach to running container's default "main" tmux session
**When to use:** User runs `aishell attach <name>` without --session flag
**Example:**
```clojure
;; Source: Phase 32 tmux auto-start (session "main") + Docker exec pattern
(ns aishell.attach
  (:require [babashka.process :as p]
            [aishell.docker.naming :as naming]
            [aishell.output :as output]))

(defn attach-to-session
  "Attach to tmux session in running container.

   Arguments:
   - name: User-provided container name (e.g., 'claude', 'experiment')
   - session: tmux session name (default: 'main')

   Validates container running, then executes docker exec with tmux attach.
   Uses p/exec to replace current process (terminal takeover)."
  ([name] (attach-to-session name "main"))
  ([name session]
   (let [project-dir (System/getProperty "user.dir")
         container-name (naming/container-name project-dir name)]

     ;; Validate container is running
     (when-not (naming/container-running? container-name)
       (output/error (str "Container '" name "' is not running.\n"
                         "\nUse 'aishell ps' to list running containers.")))

     ;; Validate stdin is TTY (attach requires interactive terminal)
     (when-not (some? (System/console))
       (output/error "Attach requires an interactive terminal.\n"
                     "Cannot attach from non-interactive contexts (scripts, pipes, CI)."))

     ;; Execute docker exec with tmux attach (replaces current process)
     ;; Uses -it for interactive TTY allocation
     ;; Full path "attach-session -t" for version stability
     (p/exec "docker" "exec" "-it" container-name
             "tmux" "attach-session" "-t" session))))
```

### Pattern 2: Session Validation with Helpful Errors
**What:** Check if session exists before attach, show available sessions if not
**When to use:** Before tmux attach command, to provide helpful error messages
**Example:**
```clojure
;; Source: tmux has-session exit code + list-sessions for discovery
(defn validate-session-exists
  "Check if tmux session exists in container.
   If not, show available sessions and error.

   Returns nil if valid, calls output/error if invalid."
  [container-name session-name]
  (let [{:keys [exit]}
        (p/shell {:out :string :err :string :continue true}
                 "docker" "exec" container-name
                 "tmux" "has-session" "-t" session-name)]
    (when-not (zero? exit)
      ;; Session doesn't exist - show available sessions
      (let [{:keys [out]}
            (p/shell {:out :string :err :string :continue true}
                     "docker" "exec" container-name "tmux" "list-sessions")]
        (if (str/blank? out)
          ;; No sessions at all
          (output/error (str "No tmux sessions found in container.\n"
                            "Did you start the container in detached mode?"))
          ;; Show available sessions
          (output/error (str "Session '" session-name "' not found.\n"
                            "\nAvailable sessions:\n" out)))))))

;; Use before attach:
(defn attach-to-session [name session]
  (let [container-name (naming/container-name project-dir name)]
    (validate-session-exists container-name session)
    (p/exec "docker" "exec" "-it" container-name
            "tmux" "attach-session" "-t" session)))
```

### Pattern 3: CLI Subcommand Integration
**What:** Add attach subcommand to CLI dispatch table with --name and --session flags
**When to use:** Wire attach function into CLI routing
**Example:**
```clojure
;; Source: Existing cli.clj dispatch pattern (build, update, check commands)
;; Location: src/aishell/cli.clj

(def attach-spec
  {:name    {:desc "Container name to attach to (e.g., 'claude', 'reviewer')"}
   :session {:desc "Tmux session name (default: main)"}
   :help    {:alias :h :coerce :boolean :desc "Show attach help"}})

(defn print-attach-help []
  (println (str output/BOLD "Usage:" output/NC " aishell attach --name <name> [OPTIONS]"))
  (println)
  (println "Attach to a running container's tmux session.")
  (println)
  (println (str output/BOLD "Options:" output/NC))
  (println (cli/format-opts {:spec attach-spec
                             :order [:name :session :help]}))
  (println)
  (println (str output/BOLD "Examples:" output/NC))
  (println (str "  " output/CYAN "aishell attach --name claude" output/NC))
  (println (str "      Attach to the 'claude' container's main session"))
  (println)
  (println (str "  " output/CYAN "aishell attach --name experiment --session debug" output/NC))
  (println (str "      Attach to specific session in 'experiment' container"))
  (println)
  (println "Press Ctrl+B then D to detach without stopping the container."))

(defn handle-attach [{:keys [opts]}]
  (if (:help opts)
    (print-attach-help)
    (let [name (:name opts)]
      (when-not name
        (output/error "Container name required.\nUsage: aishell attach --name <name>"))
      (attach/attach-to-session name (or (:session opts) "main")))))

;; Add to dispatch table:
(def dispatch-table
  [{:cmds ["build"] :fn handle-build :spec build-spec :restrict true}
   {:cmds ["update"] :fn handle-update :spec update-spec :restrict true}
   {:cmds ["check"] :fn check/run-check}
   {:cmds ["attach"] :fn handle-attach :spec attach-spec :restrict true}  ;; NEW
   {:cmds [] :spec global-spec :fn handle-default}])
```

### Pattern 4: Container State Validation
**What:** Check container existence and running state before attempting attach
**When to use:** Pre-flight validation in attach command
**Example:**
```clojure
;; Source: Phase 30 naming.clj patterns + Docker inspect
(defn validate-container-state
  "Validate container exists and is running.
   Provides helpful error messages for common failure modes."
  [container-name short-name]
  ;; Check if container exists (any state)
  (when-not (naming/container-exists? container-name)
    (output/error (str "Container '" short-name "' not found.\n"
                      "\nAvailable containers:"
                      "\n  aishell ps"
                      "\n\nTo start:"
                      "\n  aishell " short-name " --detach")))

  ;; Check if container is running (not just stopped)
  (when-not (naming/container-running? container-name)
    (output/error (str "Container '" short-name "' is not running.\n"
                      "\nCurrent state: stopped"
                      "\n\nTo start:"
                      "\n  aishell " short-name " --detach"
                      "\n\nOr remove stopped container:"
                      "\n  docker rm " container-name))))
```

### Anti-Patterns to Avoid
- **Using `docker attach` instead of exec**: docker attach connects to PID 1's stdin/stdout directly, bypassing entrypoint's gosu user switching; socket ownership mismatch causes "permission denied"
- **Using short tmux command forms** (`tmux a`, `tmux at`): Not stable across tmux versions; some versions don't recognize short forms
- **Wrapping tmux in sh -c**: `docker exec bash -c "tmux attach"` breaks TTY allocation; use direct exec
- **Skipping TTY validation**: Non-interactive contexts (scripts, CI) cause "not a tty" errors; validate System/console first
- **Assuming session exists**: If session missing, tmux attach fails with cryptic error; use has-session check first

## Don't Hand-Roll

Problems that look simple but have existing solutions:

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| Checking if session exists | Parsing `tmux ls` output | `tmux has-session -t <session>` | Exit code-based check, simpler and more reliable |
| Enumerating sessions | Custom Docker exec + grep | `tmux list-sessions` | Built-in tmux command, proper formatting |
| Container name generation | String concatenation with project path | Phase 30 `naming/container-name` | Already handles hashing, validation, canonicalization |
| Container running check | Parsing `docker ps` | Phase 30 `naming/container-running?` | Already handles exact name matching, exit codes |
| TTY detection | Platform-specific checks | `System/console` | JVM built-in, cross-platform, returns nil if no TTY |

**Key insight:** All infrastructure pieces exist. Phase 30 provides naming and validation, Phase 32 ensures tmux is running. Just wire them together with proper error handling.

## Common Pitfalls

### Pitfall 1: Docker Attach vs Docker Exec (Critical)
**What goes wrong:** Using `docker attach` to connect to tmux session fails with "permission denied" on socket

**Why it happens:**
- docker attach connects directly to PID 1's stdin/stdout (tini)
- This bypasses entrypoint's user switching (gosu)
- tmux socket is owned by the `developer` user (UID from host)
- docker attach runs as root context, can't access user's socket
- Error: "error connecting to /tmp/tmux-1000/default (Permission denied)"

**How to avoid:** Always use `docker exec -it <container> tmux attach-session`, never `docker attach <container>`. Docker exec goes through entrypoint → gosu → tmux attach, using correct user context.

**Warning signs:**
- Error: "error connecting to /tmp/tmux-{uid}/default (Permission denied)"
- Socket exists (`ls /tmp/tmux-*`) but attach fails
- Works with `docker exec bash` but not with `docker attach`

**Prevention code:**
```clojure
;; RIGHT: Use docker exec (goes through entrypoint + gosu)
(p/exec "docker" "exec" "-it" container-name
        "tmux" "attach-session" "-t" "main")

;; WRONG: Use docker attach (bypasses gosu, socket permission error)
(p/exec "docker" "attach" container-name)
```

**Source:** Phase 32 research (PITFALLS-tmux.md) Pitfall 2 (tmux Socket Permissions with Gosu)

---

### Pitfall 2: TTY Allocation Failures in Non-Interactive Contexts
**What goes wrong:** `docker exec -it` fails with "the input device is not a tty" when run from scripts, pipes, or CI

**Why it happens:**
- `-it` flag requires stdin to be a TTY (interactive terminal)
- Scripts, pipes, cron jobs don't have TTY allocated
- Docker refuses to allocate TTY when stdin is not a terminal
- Error: "the input device is not a tty"

**How to avoid:**
1. Validate stdin is TTY before docker exec
2. Use `System/console` in JVM/Babashka (returns nil if no TTY)
3. Provide clear error message when attach used non-interactively
4. Document that attach requires interactive terminal

**Warning signs:**
- Command works in terminal but fails in scripts
- Error mentions "not a tty"
- Works with `docker exec` (no -t flag) but fails with `-it`

**Prevention code:**
```clojure
;; Validate TTY before attempting attach
(defn attach-to-session [name session]
  ;; System/console is nil when stdin is not TTY
  (when-not (some? (System/console))
    (output/error "Attach requires an interactive terminal.\n"
                  "Cannot attach from non-interactive contexts (scripts, pipes, CI)."
                  "\n\nFor inspection from scripts, use:"
                  "\n  docker exec <container> tmux capture-pane -p"))

  ;; Safe to use -it flag now
  (p/exec "docker" "exec" "-it" container-name "tmux" "attach-session" "-t" session))
```

**Source:** Phase 32 research (PITFALLS-tmux.md) Pitfall 3 (TTY Allocation for docker exec + tmux attach)

---

### Pitfall 3: Tmux Session Doesn't Exist (User Experience)
**What goes wrong:** User tries to attach but session name is wrong or container has no tmux sessions, gets cryptic tmux error

**Why it happens:**
- Container might be running but not in tmux (started with exec, not run)
- User typos session name
- Container crashed and restarted (new tmux server, sessions lost)
- tmux error: "session not found: <name>" is not helpful

**How to avoid:**
1. Pre-flight check with `tmux has-session -t <session>`
2. Show available sessions if specified session missing
3. Special case: no sessions at all (suggest container restart)
4. Provide actionable guidance (correct session names, how to list)

**Warning signs:**
- Error: "session not found: debug" (but user expected "main")
- Attach works sometimes but not consistently
- User reports "attach worked before but now fails"

**Prevention code:**
```clojure
(defn show-available-sessions
  "Query container for available tmux sessions, show to user."
  [container-name]
  (let [{:keys [exit out]}
        (p/shell {:out :string :err :string :continue true}
                 "docker" "exec" container-name "tmux" "list-sessions")]
    (if (and (zero? exit) (not (str/blank? out)))
      ;; Sessions exist, show them
      (str "\nAvailable sessions:\n" out
           "\n\nUsage: aishell attach --name <name> --session <session>")
      ;; No sessions
      "\nNo tmux sessions found in container.\n"
      "The container may have been started without tmux or crashed.\n"
      "\nTo restart with tmux:\n"
      "  docker stop <container>\n"
      "  aishell <harness> --detach --name <name>")))

(defn attach-to-session [name session]
  (let [container-name (naming/container-name project-dir name)
        {:keys [exit]}
        (p/shell {:out :string :err :string :continue true}
                 "docker" "exec" container-name
                 "tmux" "has-session" "-t" session)]
    (when-not (zero? exit)
      ;; Session doesn't exist - show helpful error
      (output/error (str "Session '" session "' not found in container '" name "'."
                        (show-available-sessions container-name))))))
```

**Source:** Phase 32 research (FEATURES-tmux.md) Session discovery on attach error

---

### Pitfall 4: Wrapper Shell Breaking TTY (sh -c Trap)
**What goes wrong:** Using `docker exec bash -c "tmux attach"` fails with "open terminal failed: not a tty"

**Why it happens:**
- `sh -c` creates shell as TTY owner, not tmux
- tmux tries to allocate its own pseudo-terminal
- Nested TTY allocation fails
- Error: "open terminal failed: not a tty"

**How to avoid:** Pass tmux command directly to docker exec, don't wrap in shell

**Warning signs:**
- Direct `docker exec tmux attach` works but scripted version fails
- Error mentions "not a tty" but stdin IS a tty
- Works without -c wrapper

**Prevention code:**
```clojure
;; WRONG: Wrap in sh -c (breaks TTY)
(p/exec "docker" "exec" "-it" container-name
        "sh" "-c" "tmux attach-session -t main")

;; RIGHT: Direct exec (TTY allocation works)
(p/exec "docker" "exec" "-it" container-name
        "tmux" "attach-session" "-t" "main")
```

**Source:** Phase 32 research (PITFALLS-tmux.md) Pitfall 12 (Shell Form vs Exec Form)

---

### Pitfall 5: Attaching to Stopped Container
**What goes wrong:** Container exists but stopped, attach command hangs or fails with non-obvious error

**Why it happens:**
- `docker ps -a` shows stopped container
- User tries to attach, docker exec fails (can't exec into stopped container)
- Error varies by Docker version (timeout, "container not running", etc.)

**How to avoid:** Pre-flight check for container running state, not just existence

**Warning signs:**
- Attach hangs indefinitely
- Error: "Error: container is not running"
- `docker ps` shows nothing but attach seems to try anyway

**Prevention code:**
```clojure
;; Check existence (any state)
(when-not (naming/container-exists? container-name)
  (output/error "Container not found: " name))

;; Check running state specifically
(when-not (naming/container-running? container-name)
  (output/error (str "Container '" name "' is not running.\n"
                    "\nTo start:"
                    "\n  aishell " name " --detach")))
```

**Source:** Phase 32 requirements ATTCH-03 (clear error for stopped containers)

## Code Examples

Verified patterns from official sources and existing codebase:

### Complete Attach Command Implementation
```clojure
;; Source: Phase 32 tmux setup + Phase 30 naming + Docker exec docs
;; NEW FILE: src/aishell/attach.clj
(ns aishell.attach
  (:require [babashka.process :as p]
            [clojure.string :as str]
            [aishell.docker.naming :as naming]
            [aishell.output :as output]))

(defn- validate-tty!
  "Ensure stdin is a TTY. Attach requires interactive terminal.
   Exits with error if not a TTY."
  []
  (when-not (some? (System/console))
    (output/error "Attach requires an interactive terminal.\n"
                  "Cannot attach from non-interactive contexts (scripts, pipes, CI)."
                  "\n\nFor inspection from scripts, use:"
                  "\n  docker exec <container> tmux capture-pane -p")))

(defn- validate-container-state!
  "Ensure container exists and is running.
   Provides helpful errors for common failure modes."
  [container-name short-name]
  ;; Check existence
  (when-not (naming/container-exists? container-name)
    (output/error (str "Container '" short-name "' not found.\n"
                      "\nAvailable containers:"
                      "\n  aishell ps"
                      "\n\nTo start:"
                      "\n  aishell " short-name " --detach")))

  ;; Check running state
  (when-not (naming/container-running? container-name)
    (output/error (str "Container '" short-name "' is not running.\n"
                      "\nTo start:"
                      "\n  aishell " short-name " --detach"
                      "\n\nOr remove stopped container:"
                      "\n  docker rm " container-name))))

(defn- validate-session-exists!
  "Check if tmux session exists in container.
   Shows available sessions if specified session not found."
  [container-name session-name short-name]
  (let [{:keys [exit]}
        (p/shell {:out :string :err :string :continue true}
                 "docker" "exec" container-name
                 "tmux" "has-session" "-t" session-name)]
    (when-not (zero? exit)
      ;; Session doesn't exist - show available sessions
      (let [{:keys [exit out]}
            (p/shell {:out :string :err :string :continue true}
                     "docker" "exec" container-name "tmux" "list-sessions")]
        (if (and (zero? exit) (not (str/blank? out)))
          ;; Show available sessions
          (output/error (str "Session '" session-name "' not found.\n"
                            "\nAvailable sessions:\n" out
                            "\n\nUsage: aishell attach --name " short-name " --session <session>"))
          ;; No sessions at all
          (output/error (str "No tmux sessions found in container '" short-name "'.\n"
                            "The container may not be running a harness in tmux.\n"
                            "\nTo restart:"
                            "\n  docker stop " container-name
                            "\n  aishell " short-name " --detach")))))))

(defn attach-to-session
  "Attach to tmux session in running container.

   Arguments:
   - name: User-provided container name (e.g., 'claude', 'experiment')
   - session: tmux session name (default: 'main')

   Validates container state and session existence, then attaches.
   Uses p/exec to replace current process (terminal takeover).

   User can detach with Ctrl+B D without stopping container."
  ([name] (attach-to-session name "main"))
  ([name session]
   (let [project-dir (System/getProperty "user.dir")
         container-name (naming/container-name project-dir name)]

     ;; Pre-flight validations
     (validate-tty!)
     (validate-container-state! container-name name)
     (validate-session-exists! container-name session name)

     ;; All validations passed - attach to tmux session
     ;; Uses docker exec (goes through entrypoint + gosu for correct user)
     ;; Full command form "attach-session -t" for version stability
     (p/exec "docker" "exec" "-it" container-name
             "tmux" "attach-session" "-t" session))))
```

### CLI Integration
```clojure
;; Source: Existing cli.clj patterns (check, build, update commands)
;; MODIFIED FILE: src/aishell/cli.clj
(require '[aishell.attach :as attach])

(def attach-spec
  {:name    {:desc "Container name to attach to"}
   :session {:desc "Tmux session name (default: main)"}
   :help    {:alias :h :coerce :boolean :desc "Show attach help"}})

(defn print-attach-help []
  (println (str output/BOLD "Usage:" output/NC " aishell attach --name <name> [OPTIONS]"))
  (println)
  (println "Attach to a running container's tmux session.")
  (println)
  (println (str output/BOLD "Options:" output/NC))
  (println (cli/format-opts {:spec attach-spec
                             :order [:name :session :help]}))
  (println)
  (println (str output/BOLD "Examples:" output/NC))
  (println (str "  " output/CYAN "aishell attach --name claude" output/NC))
  (println (str "      Attach to the 'claude' container's main session"))
  (println)
  (println (str "  " output/CYAN "aishell attach --name experiment --session debug" output/NC))
  (println (str "      Attach to specific session in 'experiment' container"))
  (println)
  (println (str output/BOLD "Notes:" output/NC))
  (println "  - Press Ctrl+B then D to detach without stopping the container")
  (println "  - Multiple users can attach to the same container simultaneously")
  (println "  - Use 'aishell ps' to list running containers"))

(defn handle-attach [{:keys [opts]}]
  (if (:help opts)
    (print-attach-help)
    (let [name (:name opts)]
      (when-not name
        (output/error "Container name required.\n\nUsage: aishell attach --name <name>"))
      (attach/attach-to-session name (or (:session opts) "main")))))

;; Add to dispatch table
(def dispatch-table
  [{:cmds ["build"] :fn handle-build :spec build-spec :restrict true}
   {:cmds ["update"] :fn handle-update :spec update-spec :restrict true}
   {:cmds ["check"] :fn check/run-check}
   {:cmds ["attach"] :fn handle-attach :spec attach-spec :restrict true}  ;; NEW
   {:cmds [] :spec global-spec :fn handle-default}])
```

### Session Discovery Helper
```clojure
;; Utility function for listing available sessions
(defn list-sessions
  "List all tmux sessions in a running container.
   Returns vector of session names, or empty vector on error."
  [container-name]
  (try
    (let [{:keys [exit out]}
          (p/shell {:out :string :err :string :continue true}
                   "docker" "exec" container-name "tmux" "list-sessions"
                   "-F" "#{session_name}")]
      (if (zero? exit)
        (vec (remove str/blank? (str/split-lines out)))
        []))
    (catch Exception _ [])))

;; Usage in error messages:
(let [sessions (list-sessions container-name)]
  (if (seq sessions)
    (str "Available sessions: " (str/join ", " sessions))
    "No tmux sessions found"))
```

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| `docker attach` for reconnecting | `docker exec tmux attach` | Phase 33 (v2.6.0) | Works with gosu user switching, correct socket permissions |
| Manual tmux session checking | `tmux has-session -t` exit code | Standard practice 2020+ | Simpler, scriptable, no output parsing |
| No session validation | Pre-flight session check | Phase 33 (v2.6.0) | Better error messages, session discovery |
| Short tmux commands (`tmux a`) | Full command form (`attach-session -t`) | Best practice 2018+ | Version-stable, works across tmux versions |
| No TTY validation | `System/console` check | Phase 33 (v2.6.0) | Clear errors for non-interactive use |

**Deprecated/outdated:**
- Using `docker attach` to reconnect to harness: Bypasses entrypoint user switching, socket permission errors with gosu
- Parsing `tmux list-sessions` output for validation: `has-session` provides exit code, simpler and more reliable
- Assuming attach works in all contexts: Must validate TTY exists (scripts, CI environments fail)

## Open Questions

1. **Shorthand --name Syntax**
   - What we know: User must type `--name <name>` (verbose)
   - What's unclear: Whether positional argument syntax is desired (`aishell attach <name>`)
   - Recommendation: Keep explicit `--name` for v1 consistency with `--detach --name` pattern. Consider positional in future.

2. **Default Session Name**
   - What we know: Phase 32 uses "main" as default session
   - What's unclear: Whether per-harness session names would be better (claude → "claude" session)
   - Recommendation: Use "main" for v1 (matches Phase 32 decision). Re-evaluate in Phase 34 if users create multiple sessions per container.

3. **Multi-Client Attach Feedback**
   - What we know: tmux supports multiple clients attached to same session
   - What's unclear: Whether to show indicator when joining shared session
   - Recommendation: No special indicator for v1. tmux native "client attached" message is sufficient.

## Sources

### Primary (HIGH confidence)
- **Docker exec documentation** - [Docker Docs CLI Reference](https://docs.docker.com/reference/cli/docker/container/exec/) - TTY allocation, command execution in running containers
- **tmux manual** - [tmux.1 man page](https://man7.org/linux/man-pages/man1/tmux.1.html) - attach-session command, has-session validation, list-sessions enumeration
- **Existing codebase**:
  - Phase 30 `src/aishell/docker/naming.clj` (L41-68, L90-106) - container-name, container-running?, ensure-name-available!
  - Phase 32 `src/aishell/docker/templates.clj` (L240-245) - tmux auto-start with session "main"
  - Existing `src/aishell/cli.clj` (L89-123, L255-258) - CLI dispatch pattern, help formatting
- **Java System.console()** - [JDK javadoc](https://docs.oracle.com/javase/8/docs/api/java/lang/System.html#console--) - TTY detection method

### Secondary (MEDIUM confidence)
- **Docker attach vs exec comparison** - [iximiuz Labs Tutorial](https://labs.iximiuz.com/tutorials/docker-run-vs-attach-vs-exec) - Detailed comparison of attach vs exec behavior
- **tmux multi-client support** - [Tmux Book](https://leanpub.com/the-tao-of-tmux/read#leanpub-auto-multiple-clients-and-sessions) - Multiple users attaching to same session
- **Docker TTY allocation issues** - [Docker GitHub issue #8755](https://github.com/moby/moby/issues/8755) - "not a tty" error causes and solutions
- **Phase 32 research** - `.planning/phases/32-*/32-RESEARCH.md` - Detached mode, tmux auto-start, signal handling context
- **Phase 30 research** - `.planning/phases/30-*/30-RESEARCH.md` - Container naming, hash collision probability, Docker query patterns

### Tertiary (LOW confidence)
- **Tmux in Docker guide** - [Gist by ptrj](https://gist.github.com/ptrj/b54b8cdd34f632028e669a1e71bc03b8) - Community example of tmux with Docker (informational context only)

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH - All components already in use (docker exec, tmux, Phase 30 naming)
- Architecture: HIGH - Builds directly on proven Phase 30 and 32 foundations, minimal new code
- Pitfalls: HIGH - Validated against tmux research (PITFALLS-tmux.md) and Docker documentation
- TTY handling: HIGH - System/console is JVM built-in, cross-platform, well-documented

**Research date:** 2026-01-31
**Valid until:** 60 days (stable domain - Docker CLI and tmux features rarely change)

**Critical dependencies for planning:**
- Phase 30 provides: `container-name`, `container-running?`, `container-exists?`
- Phase 32 provides: tmux auto-start with session "main", entrypoint user switching (gosu)
- Current codebase: CLI dispatch pattern, error handling conventions (output/error)
- JVM built-in: System/console for TTY detection

**Implementation complexity:** LOW
- Reuses 100% existing infrastructure (naming, Docker queries, tmux)
- New code: ~150 lines (attach.clj function + CLI integration + help text)
- No new dependencies, no entrypoint changes, no Docker image rebuild

---
*Phase: 33-attach-command*
*Research completed: 2026-01-31*
