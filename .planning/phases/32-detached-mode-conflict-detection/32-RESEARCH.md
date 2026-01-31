# Phase 32: Detached Mode & Conflict Detection - Research

**Researched:** 2026-01-31
**Domain:** Docker detached mode, tmux auto-start, container lifecycle, signal handling
**Confidence:** HIGH

## Summary

Phase 32 implements detached container mode with tmux auto-start, building on the named container foundation from Phase 30 and tmux installation from Phase 31. The core challenge is enabling two distinct access patterns: (1) attaching to the running harness inside its tmux session, and (2) getting a separate shell in the same container for inspection.

Key findings:
- `--detach` flag runs containers as background processes, compatible with `--rm` for automatic cleanup
- Docker sends SIGTERM (exit 143) for graceful shutdown, escalates to SIGKILL (exit 137) after 10s timeout
- Tini (already in use via `--init` flag) correctly forwards SIGTERM to child processes
- `tmux new-session -A -s main` provides idempotent session creation/attachment
- `docker attach` connects to PID 1 (entrypoint), `docker exec` creates new process
- Auto-starting tmux requires wrapping final `exec` command in entrypoint script
- Name conflicts require pre-flight checks: error if running, auto-remove if stopped

**Primary recommendation:** Add `--detach` flag support to CLI, modify entrypoint to conditionally auto-start tmux using `exec tmux new-session -A -s main -c "$PWD" "$@"`, implement pre-flight name conflict detection using Phase 30's `ensure-name-available!` function, and add `aishell attach` command using `docker attach`.

## Standard Stack

The established libraries/tools for this domain:

### Core
| Component | Version | Purpose | Why Standard |
|-----------|---------|---------|--------------|
| `docker run --detach` | Docker CLI | Background container execution | Official Docker feature, container remains running after terminal exits |
| `docker run --rm` | Docker CLI | Automatic cleanup on exit | Compatible with `--detach`, removes container when stopped |
| `docker attach` | Docker CLI | Connect to container's PID 1 stdin/stdout | Official way to attach to main process |
| `docker exec` | Docker CLI | Run new process in running container | Standard for getting additional shells |
| `tmux new-session -A` | tmux 3.3a+ | Idempotent session create/attach | Built-in flag, simpler than manual checking |
| Tini (via `--init`) | Built into Docker 1.13+ | Signal forwarding and zombie reaping | Already in use, handles SIGTERM correctly |

### Supporting
| Component | Version | Purpose | When to Use |
|-----------|---------|---------|-------------|
| `docker stop` | Docker CLI | Graceful container shutdown | Sends SIGTERM, waits 10s, then SIGKILL |
| `babashka.cli` | Built into Babashka | CLI flag parsing | Add `--detach` flag support |

### Alternatives Considered
| Instead of | Could Use | Tradeoff |
|------------|-----------|----------|
| `docker attach` for shell access | `docker exec bash` | exec creates new process, doesn't connect to harness; both needed for different use cases |
| Manual tmux session check | `tmux new-session -A` | `-A` flag eliminates need for conditional logic |
| Custom signal handling | Tini as PID 1 | Already in use via `--init`, proven solution |
| Pre-create tmux in Dockerfile | Auto-start in entrypoint | Dockerfile build fails (no terminal), entrypoint works correctly |

**Installation:**
No additional dependencies required. All functionality available in current stack.

## Architecture Patterns

### Recommended Code Structure
```
src/aishell/
├── cli.clj              # Add --detach flag parsing, attach subcommand
├── run.clj              # Add --detach to docker run args
├── docker/
│   ├── naming.clj       # Already has ensure-name-available! (Phase 30)
│   ├── run.clj          # Modify docker args builder
│   └── templates.clj    # Modify entrypoint to auto-start tmux
```

### Pattern 1: Detached Mode Flag Handling
**What:** Add `--detach` flag to CLI, pass through to docker run
**When to use:** All harness commands and shell mode
**Example:**
```clojure
;; In cli.clj dispatch function
;; Extract --detach flag before pass-through
(let [detach? (boolean (some #{"-d" "--detach"} args))
      clean-args (vec (remove #{"-d" "--detach"} args))]
  (run/run-container "claude" (vec (rest clean-args))
    {:unsafe unsafe?
     :container-name container-name-override
     :detach detach?}))

;; In docker/run.clj
(defn build-docker-args [{:keys [detach ...]}]
  (-> ["docker" "run" "--rm" "--init"]
      (cond-> detach (conj "--detach"))
      (into ["-it"])  ; Always allocate TTY
      ...))
```

### Pattern 2: Tmux Auto-Start in Entrypoint
**What:** Wrap final exec command in tmux session using `-A` flag
**When to use:** All container starts (harness and shell mode)
**Example:**
```bash
# Source: Phase 32 requirement + tmux -A pattern research
# In entrypoint.sh (modified from templates.clj)

# ... existing user creation and setup ...

# Final exec: wrap command in tmux session
# -A: attach if exists, create if not
# -s main: session named "main"
# -c "$PWD": start in working directory
# "$@": the actual command (bash or harness)
exec gosu "$USER_ID:$GROUP_ID" tmux new-session -A -s main -c "$PWD" "$@"
```

**Why this works:**
- `new-session -A` is idempotent: first call creates, subsequent calls attach
- `-c "$PWD"` ensures tmux starts in project directory (set by `docker run -w`)
- `exec` replaces shell with tmux, making tmux PID 1's child (receives signals)
- Works for both foreground (`docker attach` connects) and background (session persists)

### Pattern 3: Attach Command Implementation
**What:** Validate container running, then `docker attach` to connect to tmux
**When to use:** User wants to reattach to running harness
**Example:**
```clojure
;; Source: Existing naming.clj + docker attach documentation
(defn handle-attach [{:keys [opts]}]
  (let [project-dir (System/getProperty "user.dir")
        name (or (:name opts)
                 (output/error "Container name required. Use: aishell attach --name <name>"))
        container-name (naming/container-name project-dir name)]

    ;; Validate container is running
    (when-not (naming/container-running? container-name)
      (output/error (str "Container not running: " container-name
                        "\nAvailable: aishell ps")))

    ;; Attach to container (connects to PID 1 stdin/stdout)
    ;; User will attach to the tmux session started by entrypoint
    (p/exec "docker" "attach" container-name)))
```

### Pattern 4: Shell Access via Docker Exec
**What:** Start new bash process in running container for inspection
**When to use:** User wants shell access without disrupting running harness
**Example:**
```clojure
;; NEW: shell subcommand (different from shell mode)
(defn handle-shell [{:keys [opts]}]
  (let [project-dir (System/getProperty "user.dir")
        name (or (:name opts)
                 (output/error "Container name required. Use: aishell shell --name <name>"))
        container-name (naming/container-name project-dir name)]

    ;; Validate container exists
    (when-not (naming/container-exists? container-name)
      (output/error (str "Container not found: " container-name)))

    ;; Exec new bash in container (separate from harness)
    (p/exec "docker" "exec" "-it" container-name "/bin/bash")))
```

### Pattern 5: Pre-Flight Conflict Detection
**What:** Check for name conflicts before `docker run --name`, handle appropriately
**When to use:** Every container start (foreground and detached)
**Example:**
```clojure
;; Source: Phase 30 naming.clj L90-106 (ensure-name-available!)
;; Already implemented, just need to call it

;; In run.clj run-container function, before docker run:
(let [container-name-str (naming/container-name project-dir name-part)]
  ;; Pre-flight check: error if running, auto-remove if stopped
  (naming/ensure-name-available! container-name-str name-part)

  ;; Now safe to docker run --name
  (apply p/exec (concat docker-args ["--name" container-name-str] container-cmd)))
```

### Anti-Patterns to Avoid
- **Starting tmux in Dockerfile:** Fails with "no terminal" error during build
- **Using `tmux attach` without checking:** Fails if session doesn't exist; use `-A` instead
- **Assuming --rm incompatible with --detach:** Modern Docker supports this combination
- **Using `docker attach` for shell access:** Attaches to PID 1 (harness), not a separate shell
- **Skipping conflict detection on foreground mode:** Both foreground and detached need checks

## Don't Hand-Roll

Problems that look simple but have existing solutions:

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| Checking if tmux session exists | `tmux has-session && tmux attach \|\| tmux new-session` | `tmux new-session -A -s main` | `-A` flag handles both cases atomically |
| Signal forwarding to child process | Custom trap handlers in entrypoint | Tini via `--init` flag | Already in use, handles all signals correctly |
| Container name uniqueness validation | Custom Docker API queries | Phase 30's `ensure-name-available!` | Already implemented, handles all edge cases |
| Checking container state | Parsing `docker ps` output | `docker inspect` exit code or `docker ps --filter --format` | More reliable, machine-readable |
| Detached container lifecycle | Custom daemon management | `--detach` + `--rm` flags | Built into Docker, automatic cleanup |

**Key insight:** The pieces already exist. Phase 30 provides naming and conflict detection, Phase 31 provides tmux, existing entrypoint handles user creation. Just wire them together with `--detach` flag and tmux auto-start.

## Common Pitfalls

### Pitfall 1: --rm + --detach Incompatibility Assumptions
**What goes wrong:** Developer assumes `--rm` doesn't work with `--detach` based on outdated information

**Why it happens:** Old Docker versions (pre-2020) had issues with this combination, and outdated articles persist

**How to avoid:** Modern Docker (1.13+) supports `--rm` with `--detach`. Use both flags. Container is removed when it exits OR when daemon exits.

**Warning signs:** Manual container cleanup needed, accumulating stopped containers

**Verification:**
```bash
# Works correctly in modern Docker
docker run --rm --detach --name test alpine sleep 30
docker stop test  # Container auto-removed after stop
docker ps -a | grep test  # Should show nothing
```

### Pitfall 2: Tmux Session Persistence Across Attach/Detach
**What goes wrong:** User detaches from tmux session (Ctrl+B D) and container exits

**Why it happens:** Tmux detach is NOT the same as Docker detach. If tmux is the only process and all sessions close, container exits.

**How to avoid:** Use `tmux new-session -A -s main` in entrypoint so the session is ALWAYS created at container start. User can detach from tmux session and Docker attach will reconnect.

**Warning signs:** Container exits when user presses Ctrl+B D, container missing from `docker ps`

**Prevention code:**
```bash
# In entrypoint.sh - session created at container start, not user attach
exec gosu "$USER_ID:$GROUP_ID" tmux new-session -A -s main -c "$PWD" "$@"
```

### Pitfall 3: Docker Attach vs Exec Confusion
**What goes wrong:** User runs `docker exec -it <container> bash` expecting to attach to running harness, gets separate shell instead

**Why it happens:** `docker exec` creates NEW process (new PID), doesn't connect to PID 1 (the harness)

**How to avoid:**
- Use `docker attach` to connect to running harness (joins tmux session)
- Use `docker exec bash` to get separate shell for inspection
- Document both patterns clearly

**Warning signs:** User can't see harness output, harness keeps running when shell exits

**Solution:**
```bash
# Attach to running harness (connects to tmux "main" session)
docker attach aishell-XXXXXXXX-claude

# Get separate shell for inspection (new bash process)
docker exec -it aishell-XXXXXXXX-claude /bin/bash
```

### Pitfall 4: Exit Code Confusion (137 vs 143 vs 0)
**What goes wrong:** Developer treats exit code 137 (SIGKILL) as acceptable, misses timeout issues

**Why it happens:** Exit 137 can indicate legitimate shutdown OR timeout. Context determines acceptability.

**How to avoid:**
- 0 = clean exit (harness quit normally)
- 143 = SIGTERM handled (docker stop, graceful shutdown)
- 137 = SIGKILL (timeout after 10s OR OOM)
- Aim for 0 or 143, investigate 137

**Warning signs:** Containers always exit with 137, shutdown takes full 10+ seconds

**Prevention:**
```bash
# Test graceful shutdown
docker run --rm --name test aishell:base sleep infinity
docker stop test  # Should exit with 143 in <3s (not 137)
```

### Pitfall 5: Container Name Already in Use (Stopped Container)
**What goes wrong:** `docker run --name X` fails with "name already in use" even though `docker ps` shows nothing

**Why it happens:** Stopped containers still own their names. `--rm` only removes on exit, not on forced stop.

**How to avoid:** Pre-flight check with `ensure-name-available!` (Phase 30). Auto-remove stopped containers, error if running.

**Warning signs:** Error "Conflict. The container name is already in use", `docker ps -a` shows stopped container

**Prevention:** Already implemented in Phase 30's `naming/ensure-name-available!`

### Pitfall 6: Tmux Socket Permissions with Gosu
**What goes wrong:** Tmux socket created by root before gosu, user can't access socket

**Why it happens:** Entrypoint runs as root, creates tmux socket, then gosu switches user. Socket owned by root.

**How to avoid:** ALWAYS use `exec gosu ... tmux ...` so tmux runs as user, creating socket with correct ownership.

**Warning signs:** Permission denied errors when attaching, `ls -la /tmp/tmux-*` shows root ownership

**Prevention code:**
```bash
# WRONG: tmux starts as root, gosu happens after
tmux new-session -d -s main
exec gosu "$USER_ID:$GROUP_ID" "$@"

# RIGHT: gosu first, then tmux runs as user
exec gosu "$USER_ID:$GROUP_ID" tmux new-session -A -s main -c "$PWD" "$@"
```

## Code Examples

Verified patterns from official sources and existing codebase:

### Entrypoint Modification for Tmux Auto-Start
```bash
# Source: templates.clj entrypoint-script + tmux -A pattern research
# Location: src/aishell/docker/templates.clj, line 151-231

# Modified final exec section (replaces line 229-230):

# Execute command as the user via gosu, auto-start in tmux session
# -A: attach if session exists, create if not (idempotent)
# -s main: session name (consistent with requirements)
# -c "$PWD": start in working directory (set by docker run -w)
# "$@": the actual command (bash, claude, opencode, etc.)
exec gosu "$USER_ID:$GROUP_ID" tmux new-session -A -s main -c "$PWD" "$@"
```

### CLI Flag Parsing for --detach
```clojure
;; Source: Existing cli.clj pattern (L275-310) + Docker --detach flag
;; Location: src/aishell/cli.clj dispatch function

(defn dispatch [args]
  ;; Extract --detach flag before pass-through
  (let [detach? (boolean (some #{"-d" "--detach"} args))
        clean-args (vec (remove #{"-d" "--detach"} args))

        ;; ... existing --unsafe and --name extraction ...
        ]

    ;; Pass detach flag to run-container
    (case (first clean-args)
      "claude" (run/run-container "claude" (vec (rest clean-args))
                 {:unsafe unsafe?
                  :container-name container-name-override
                  :detach detach?})
      ;; ... similar for other harnesses ...
      )))
```

### Docker Args Construction with Detach
```clojure
;; Source: Existing docker/run.clj L252-273 + Docker detach flag docs
;; Location: src/aishell/docker/run.clj

(defn build-docker-args
  "Build complete docker run argument vector.

   New argument:
   - detach: When true, add --detach flag (container runs in background)"
  [{:keys [project-dir image-tag config git-identity skip-pre-start detach]}]
  (build-docker-args-internal
    {:project-dir project-dir
     :image-tag image-tag
     :config config
     :git-identity git-identity
     :skip-pre-start skip-pre-start
     :detach detach  ; Pass through to internal builder
     :tty-flags ["-it"]}))  ; Always allocate TTY

(defn build-docker-args-internal [{:keys [detach tty-flags ...]}]
  (-> ["docker" "run" "--rm" "--init"]
      (into tty-flags)  ; -it for interactive
      (cond-> detach (conj "--detach"))  ; Add --detach if requested
      (into ["-v" (str project-dir ":" project-dir)
             "-w" project-dir
             ;; ... rest of args ...
             ])))
```

### Attach Command Implementation
```clojure
;; Source: Docker attach documentation + existing naming patterns
;; NEW function in run.clj or dedicated attach.clj

(defn attach-container
  "Attach to running container by name.

   Arguments:
   - name: User-provided name (e.g., 'claude', 'reviewer')

   Validates container is running, then attaches to its PID 1 (tmux session)."
  [name]
  (let [project-dir (System/getProperty "user.dir")
        container-name (naming/container-name project-dir name)]

    ;; Validate container is running
    (when-not (naming/container-running? container-name)
      (output/error (str "Container not running: " name
                        "\n\nAvailable containers:"
                        "\n  aishell ps"
                        "\n\nTo start:"
                        "\n  aishell claude --detach --name " name)))

    ;; Attach to container's PID 1 (connects to tmux session)
    (p/exec "docker" "attach" container-name)))

;; In cli.clj dispatch table:
;; {:cmds ["attach"] :fn handle-attach :spec attach-spec}
(defn handle-attach [{:keys [opts]}]
  (when-not (:name opts)
    (output/error "Container name required.\nUsage: aishell attach --name <name>"))
  (attach-container (:name opts)))
```

### Shell Access Command
```clojure
;; Source: Docker exec documentation + existing patterns
;; NEW function for getting separate shell in running container

(defn shell-into-container
  "Execute new bash shell in running container.
   Different from attach: creates new process, doesn't connect to harness."
  [name]
  (let [project-dir (System/getProperty "user.dir")
        container-name (naming/container-name project-dir name)]

    ;; Validate container exists (can be running or paused)
    (when-not (naming/container-exists? container-name)
      (output/error (str "Container not found: " name
                        "\n\nAvailable: aishell ps")))

    ;; Exec new bash in container (separate from harness)
    (p/exec "docker" "exec" "-it" container-name "/bin/bash")))
```

### Pre-Flight Conflict Check Integration
```clojure
;; Source: Existing naming.clj L90-106 (ensure-name-available!)
;; Location: src/aishell/run.clj run-container function

;; BEFORE docker run, AFTER container name generation:
(let [container-name-str (naming/container-name project-dir name-part)]

  ;; Pre-flight check (already implemented in Phase 30)
  ;; - Errors if container running (shows attach hint)
  ;; - Auto-removes if stopped (logs removal)
  ;; - Proceeds silently if name available
  (naming/ensure-name-available! container-name-str name-part)

  ;; Build docker args (now includes --name)
  (let [docker-args (docker-run/build-docker-args
                      {:project-dir project-dir
                       :image-tag image-tag
                       :config cfg
                       :git-identity git-id
                       :skip-pre-start (:skip-pre-start opts)
                       :detach (:detach opts)})

        ;; Add --name to docker args
        docker-args-with-name (into docker-args ["--name" container-name-str])

        ;; Determine command to run
        container-cmd (case cmd
                        "claude" ["claude" "--dangerously-skip-permissions" ...]
                        ["/bin/bash"])]

    ;; Execute (replaces current process)
    (apply p/exec (concat docker-args-with-name container-cmd))))
```

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| Anonymous containers | Named containers with project hash | Phase 30 (v2.6.0) | Enables container discovery and reattachment |
| `--rm` incompatible with `--detach` | Full support for both flags | Docker 1.13+ (2017) | Background containers can auto-cleanup |
| Manual tmux session checking | `tmux new-session -A` flag | tmux 1.9+ (2014) | Idempotent session creation, simpler scripts |
| Shell signal handling | Tini as PID 1 | Docker 1.13+ (2017) | Correct signal forwarding without custom code |
| `docker attach` shows nothing | Works with tmux-wrapped entrypoint | Current design | PID 1 is tmux, attach connects to session |

**Deprecated/outdated:**
- Separate RUN tmux in Dockerfile: Fails at build time (no terminal), must be in entrypoint
- Custom signal trap handlers: Tini handles this better, already in use via `--init`
- Checking tmux session exists before attach: `tmux new-session -A` makes this unnecessary

## Open Questions

Things that couldn't be fully resolved:

1. **Foreground vs Detached Launch Feedback**
   - What we know: Detached mode should show container name and attach hint
   - What's unclear: Exact format user prefers (marked as Claude's discretion in CONTEXT)
   - Recommendation: Show container name, attach command, ps hint. Example:
     ```
     Container started in background: aishell-a1b2c3d4-claude

     To attach:  aishell attach --name claude
     To list:    aishell ps
     ```

2. **--force Flag for Replacing Running Containers**
   - What we know: User might want to force-replace running container
   - What's unclear: Whether `--force` flag is desired (marked as discretion)
   - Recommendation: Skip `--force` flag for v1. Error message already shows `docker stop` command. Advanced users can stop manually.

3. **Exit Code Acceptance Policy**
   - What we know: 0 = clean, 143 = SIGTERM (good), 137 = SIGKILL (timeout or OOM)
   - What's unclear: Whether 137 is acceptable (marked as discretion)
   - Recommendation: Aim for <3s shutdown with 0 or 143. Log warning if 137, but don't error. Investigate if reproducible.

## Sources

### Primary (HIGH confidence)
- [Docker container run reference](https://docs.docker.com/reference/cli/docker/container/run/) - Official documentation for --detach, --rm, --name flags and interactions
- [Tini GitHub README](https://github.com/krallin/tini) - Signal handling behavior, PID 1 requirements, zombie reaping
- [tmux man page](https://man7.org/linux/man-pages/man1/tmux.1.html) - Documentation for `new-session -A` flag behavior
- Existing codebase:
  - `src/aishell/docker/naming.clj` (Phase 30) - Container naming, conflict detection
  - `src/aishell/docker/run.clj` - Docker args construction pattern
  - `src/aishell/docker/templates.clj` - Current entrypoint script (lines 151-231)

### Secondary (MEDIUM confidence)
- [Docker Detached Mode Explained - FreeCodeCamp](https://www.freecodecamp.org/news/docker-detached-mode-explained/) - Detached container lifecycle and behavior
- [Exit Codes in Docker - Komodor](https://komodor.com/learn/exit-codes-in-containers-and-kubernetes-the-complete-guide/) - Explanation of exit codes 0, 137, 143 and their meanings
- [How to Set Default Shell to Launch tmux - Baeldung](https://www.baeldung.com/linux/tmux-startup-default-shell) - Auto-starting tmux in shell initialization
- [Docker Run, Attach, and Exec - iximiuz Labs](https://labs.iximiuz.com/tutorials/docker-run-vs-attach-vs-exec) - Detailed comparison of attach vs exec behavior
- [Gracefully Shutdown Docker Container - Kakashi's Blog](https://kkc.github.io/2018/06/06/gracefully-shutdown-docker-container/) - Signal handling and graceful termination patterns
- [Docker name conflict fixes - Baeldung](https://www.baeldung.com/ops/docker-name-already-in-use) - Handling "name already in use" errors

### Tertiary (LOW confidence)
- [Tmux in demonized docker container - GitHub Gist](https://gist.github.com/ptrj/b54b8cdd34f632028e669a1e71bc03b8) - Community example of tmux with Docker
- [Docker compose name conflicts - Docker Forums](https://forums.docker.com/t/docker-compose-confilct-default-container-name-already-in-use/138556) - User discussions on name conflicts

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH - All components already in use or official Docker features
- Architecture: HIGH - Built on proven Phase 30 and 31 foundations, minimal new code
- Pitfalls: HIGH - Verified against Docker official docs and existing codebase issues
- Signal handling: HIGH - Tini already in use, behavior documented and tested

**Research date:** 2026-01-31
**Valid until:** 60 days (stable domain - Docker CLI and tmux features rarely change)

**Critical dependencies for planning:**
- Phase 30 provides: `container-name`, `ensure-name-available!`, `container-running?`
- Phase 31 provides: tmux installation in base image
- Current entrypoint: User creation, gosu, pre_start handling
- Current docker args: `--rm`, `--init`, `-it` already present

---
*Phase: 32-detached-mode-conflict-detection*
*Research completed: 2026-01-31*
