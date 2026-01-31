# Domain Pitfalls: tmux Integration in Docker Containers

**Domain:** Adding tmux to Docker-based sandbox with named containers and attach support
**Researched:** 2026-01-31
**Context:** Milestone v2.6.0 - Adding tmux integration to existing aishell Docker sandbox

## Executive Summary

Adding tmux to Docker containers introduces complexity in three critical areas:

1. **Signal handling** - tmux as PID 1 or with --init creates signal propagation issues
2. **User switching** - tmux + gosu interaction affects socket permissions and session ownership
3. **Attach semantics** - docker exec vs docker attach have different TTY allocation behavior with tmux

The most dangerous pitfalls are around signal handling (containers won't stop gracefully) and socket permissions (attach commands fail with "permission denied"). Both are subtle, manifest inconsistently, and require deep understanding of Docker/tmux interactions to debug.

## Critical Pitfalls

Mistakes that cause rewrites or major issues.

### Pitfall 1: tmux Signal Handling with PID 1

**What goes wrong:**

When tmux runs as PID 1 in a Docker container, `docker stop` sends SIGTERM to tmux, but the kernel treats PID 1 specially - it ignores signals unless the process explicitly handles them. Without signal handling, `docker stop` waits 10 seconds for SIGTERM, then forcibly kills with SIGKILL. This means:
- Sessions never save gracefully
- Background processes get killed mid-operation
- No cleanup happens
- Users lose unsaved work

**Why it happens:**

The aishell entrypoint uses `gosu` for user switching, which means the actual process tree is `--init` (PID 1) → entrypoint script → `gosu` → `tmux`. But if the entrypoint directly `exec`s into tmux without an init system, tmux becomes PID 1. Linux kernel ignores SIGTERM/SIGINT to PID 1 unless the process has explicit signal handlers, which tmux does not register by default when it's PID 1.

**Consequences:**

- Containers take 10+ seconds to stop (waiting for SIGTERM timeout)
- Named containers can't be restarted cleanly (previous instance still shutting down)
- Work in tmux sessions is lost on container shutdown
- `aishell attach` may connect to a dying container
- CI/CD pipelines timeout waiting for clean shutdown

**Prevention:**

```bash
# In entrypoint script, BEFORE exec-ing into tmux:
trap "tmux kill-server; exit 0" TERM INT

# Then start tmux, but don't exec (so trap handler remains active):
gosu dev tmux new-session -s harness -d
gosu dev tmux attach-session -t harness
```

**Alternative: Let --init handle signals**

The container already uses `--init` flag (line 190 in run.clj), which means Docker's tini init system is PID 1. This correctly propagates signals. The entrypoint should NOT use `exec` before tmux, allowing the shell to receive signals and forward them:

```bash
# DON'T: exec gosu dev tmux ...  (shell exits, signal handler lost)
# DO:    gosu dev tmux ...       (shell remains, can trap signals)
```

**Detection:**

- `docker stop` takes exactly 10 seconds (SIGTERM timeout)
- `docker logs` shows no shutdown messages from tmux
- Stopped containers have exit code 137 (SIGKILL) not 0 or 143 (SIGTERM)
- `docker inspect` shows "OOMKilled": false but container was force-killed

**Sources:**
- [PID 1 Signal Handling in Docker](https://petermalmgren.com/signal-handling-docker/)
- [Docker Signal Demo with tmux](https://github.com/DrPsychick/docker-signal-demo)
- [Why Your Docker Container Won't Stop Gracefully (2026)](https://medium.com/@fernando.harsha2016/why-your-docker-container-wont-stop-gracefully-understanding-pid-1-and-process-management-569c44dce004)
- [How to gracefully shutdown a long-running Docker container](https://labex.io/tutorials/docker-how-to-gracefully-shut-down-a-long-running-docker-container-417742)

---

### Pitfall 2: tmux Socket Permissions After User Switching

**What goes wrong:**

The tmux server creates a socket at `/tmp/tmux-{uid}/default`. If the entrypoint starts tmux as root (before gosu), the socket is owned by root. When `aishell attach` runs `docker exec -it ... gosu dev tmux attach`, it tries to connect as the dev user but gets "permission denied" because the socket is owned by root.

Conversely, if tmux starts as dev user but the entrypoint runs as root, the socket directory `/tmp/tmux-{dev-uid}` might not exist or have wrong permissions.

**Why it happens:**

gosu is designed for ENTRYPOINT user downgrade (root → non-root). The aishell entrypoint runs as root (to configure git safe.directory, create /home/dev, etc.), then uses gosu to drop privileges. If tmux is started in the root section (before gosu), it creates sockets owned by root. If started after gosu, the socket is owned by dev, but subsequent `docker exec` commands that don't use the same user context fail to attach.

**Consequences:**

- `aishell attach` fails with "no server running on /tmp/tmux-1000/default"
- Or "permission denied" when socket exists but wrong owner
- Users can start sessions but can't attach to them
- Multiple sessions created instead of attaching to existing one
- Debugging is hard because socket path varies by UID

**Prevention:**

1. **Start tmux AFTER gosu** (tmux must run as target user)
2. **Use consistent UID** (already done - entrypoint sets LOCAL_UID from host)
3. **Verify socket ownership** in entrypoint:

```bash
# After starting tmux as dev user
if [ -e "/tmp/tmux-${LOCAL_UID}/default" ]; then
  chown ${LOCAL_UID}:${LOCAL_GID} "/tmp/tmux-${LOCAL_UID}/default"
fi
```

4. **For attach command**: Always use same user context:

```bash
# In aishell attach implementation:
docker exec -it -e LOCAL_UID=${UID} -e LOCAL_GID=${GID} ${container} \
  /bin/bash -c 'gosu dev tmux attach-session -t harness'
```

**Detection:**

- `ls -la /tmp/tmux-*/` inside container shows root-owned sockets
- `docker exec ... tmux ls` says "no server running" but tmux IS running
- `ps aux | grep tmux` shows tmux process exists
- Error message: "error connecting to /tmp/tmux-{uid}/default (Permission denied)"

**Sources:**
- [tmux socket permission problem](https://github.com/tmuxinator/tmuxinator/issues/377)
- [getting tmux to use existing domain socket without changing permissions](https://github.com/tmux/tmux/issues/2110)
- [can't create socket: Permission denied](https://github.com/tmux/tmux/issues/1215)
- [User Switching Tool in Containers: gosu](https://docsaid.org/en/blog/gosu-usage/)

---

### Pitfall 3: TTY Allocation for docker exec + tmux attach

**What goes wrong:**

Running `docker exec -it container tmux attach` can fail with "open terminal failed: not a tty" or result in a "not a tty" error when checking with the `tty` command inside the container. tmux requires a proper TTY, but the way `docker exec` allocates TTYs interacts poorly with tmux's expectations, especially when:
- The host terminal is not a TTY (CI environment)
- The exec command is wrapped in scripts
- Using `sh -c` to chain commands before tmux attach

**Why it happens:**

`docker exec -it` allocates a pseudo-TTY, but there's a known difference between `docker exec` and `docker attach` in how TTYs are handled. When you use `docker exec -it IMAGE_NAME /bin/bash`, it creates a new TTY allocation that differs from the original container's TTY. tmux then tries to create its own pseudo-terminal, and the nested TTY allocation can fail.

Additionally, if you wrap the tmux attach command in `sh -c`, the shell becomes the TTY owner, not tmux, leading to "not a tty" errors.

**Consequences:**

- `aishell attach` command fails intermittently
- Works on developer machines, fails in CI/SSH sessions
- Error messages are cryptic: "open terminal failed: not a tty"
- tmux refuses to attach even though session exists
- Pane layout breaks: "create pane failed: pane too small"

**Prevention:**

1. **Always use -it flag** for interactive tmux attach:
   ```bash
   docker exec -it container tmux attach-session -t harness
   ```

2. **Don't wrap in sh -c** for simple commands:
   ```bash
   # BAD:  docker exec -it container sh -c "tmux attach -t harness"
   # GOOD: docker exec -it container tmux attach-session -t harness
   ```

3. **For complex commands, use script wrapper**:
   ```bash
   docker exec -it container script -q -c "tmux attach -t harness" /dev/null
   ```

4. **Set TERM variable explicitly**:
   ```bash
   docker exec -it -e TERM=screen-256color container tmux attach -t harness
   ```

5. **Validate TTY before attach**:
   ```bash
   # In aishell attach command, check if stdin is a TTY
   if ! tty -s; then
     echo "Error: aishell attach requires interactive terminal"
     exit 1
   fi
   ```

6. **Document non-interactive limitation**:
   ```
   Note: `aishell attach` requires an interactive terminal.
   It will not work in non-interactive contexts (scripts, CI, piped input).
   ```

**Detection:**

- Error: "open terminal failed: not a tty"
- Error: "create pane failed: pane too small"
- `tty` command inside exec'd shell returns "not a tty"
- Works with `docker attach` but fails with `docker exec`
- tmux session exists (`tmux ls` works) but attach fails

**Sources:**
- [Interactive Shell. tty: Not a tty. Unable to use screen or tmux](https://github.com/moby/moby/issues/728)
- [Docker tty is not a tty with docker exec](https://github.com/moby/moby/issues/8755)
- [Tmux and Screen - Docker documentation](https://dockerdocs.org/multiplexers/)
- [Fixing "The input device is not a TTY" Error With Docker Run](https://www.magetop.com/blog/fixing-the-input-device-is-not-a-tty-error-with-docker-run/)

---

### Pitfall 4: Named Container + --rm Flag Conflict

**What goes wrong:**

The aishell currently uses `--rm` flag (line 190 in run.clj) to auto-remove containers on exit. This is perfect for ephemeral containers. But v2.6.0 adds named containers with `--name aishell-{project-hash}-{name}`. The conflict:

1. Container starts with `--name aishell-abc-claude --rm`
2. Container crashes or is killed (SIGKILL)
3. Docker tries to remove container but fails (rare race condition)
4. Stopped container remains, name is taken
5. Next `aishell claude` fails: "container name already in use"

Worse: if the container is still running (user backgrounded it), and they try to start another with same name, it fails immediately.

**Why it happens:**

`--rm` only removes the container when it exits cleanly. If Docker daemon is interrupted, the container killed forcibly, or the removal process fails, the stopped container persists. With auto-generated names (ephemeral containers), this was never a problem - each container got a unique random name. With user-specified names, name collisions block subsequent runs.

**Consequences:**

- "Container name already in use" errors
- Users must manually `docker rm` before each run
- Race conditions during cleanup cause intermittent failures
- Multiple containers per project (the feature goal) is blocked by name collisions
- `aishell ps` shows stopped containers users thought were removed

**Prevention:**

1. **Pre-flight check for name collision**:
   ```bash
   # Before docker run, check if name exists
   if docker ps -a --filter "name=^aishell-{hash}-{name}$" --format "{{.Names}}" | grep -q .; then
     echo "Container with name already exists. Remove it? (y/n)"
     # Or auto-remove if stopped: docker rm -f aishell-{hash}-{name}
   fi
   ```

2. **Keep --rm but document limitation**:
   ```
   Note: Named containers use --rm for auto-cleanup. If cleanup fails,
   manually remove with: docker rm -f aishell-{hash}-{name}
   ```

3. **Remove --rm for named containers, add cleanup command**:
   ```bash
   # Don't use --rm when --name is provided
   docker run --name aishell-{hash}-{name} ...  # no --rm

   # Add explicit cleanup command:
   aishell cleanup  # removes stopped aishell-* containers
   ```

4. **Use unique suffixes for true ephemeral names**:
   ```bash
   # If user doesn't provide --name, append timestamp
   --name aishell-{hash}-{harness}-$(date +%s)
   ```

**Detection:**

- Error: "Conflict. The container name "/aishell-..." is already in use"
- `docker ps -a` shows stopped containers with aishell-* names
- Containers removed manually reappear after crashes
- `aishell ps` count doesn't match running container count

**Sources:**
- [Easy Container Cleanup in Cron + Docker Environments](https://www.cloudbees.com/blog/easy-container-cleanup-in-cron-docker-environments)
- [How to resolve container naming conflicts](https://labex.io/tutorials/docker/how-to-resolve-container-naming-conflicts-418051)
- [10 Docker Container Naming Convention Best Practices](https://devtodevops.com/blog/docker-container-naming-convention/)
- [Fix Docker Container Name Conflict Errors](https://github.com/oneuptime/blog/tree/master/posts/2026-01-25-fix-docker-container-name-conflict-errors)

---

### Pitfall 5: tmux Session Persistence Expectations

**What goes wrong:**

Users expect tmux sessions to persist across container restarts (like they do on the host). But Docker containers are ephemeral - when they stop, all in-memory state is lost, including tmux sessions. Users run:

1. `aishell claude` - starts container, tmux session, works on code
2. `docker stop aishell-abc-claude` - container stops
3. `docker start aishell-abc-claude` - container starts with fresh state
4. `aishell attach aishell-abc-claude` - tmux session doesn't exist

This violates user expectations and causes confusion about what "named containers" means.

**Why it happens:**

tmux sessions are stored in memory and the tmux server socket at `/tmp/tmux-{uid}/`. When a container stops, all processes exit, memory is cleared, and `/tmp` is ephemeral (not persisted to volumes). Even if you `docker start` the same container, it runs the entrypoint again, which creates a NEW tmux server and NEW sessions - it doesn't restore the old ones.

This is fundamentally different from host tmux (where sessions persist until `tmux kill-server` or system reboot) or from tools like tmux-resurrect (which save/restore session layout and processes).

**Consequences:**

- Users lose work when containers restart
- Confusion about "attach" semantics - attach to what, if session is gone?
- Feature appears broken: "I attached but my session is empty"
- Muscle memory from host tmux doesn't transfer
- Documentation needs to explicitly explain ephemeral session model

**Prevention:**

1. **Document ephemeral session model clearly**:
   ```
   Important: tmux sessions exist only while the container is running.
   When you stop a container, all sessions are lost. Use `aishell attach`
   to attach to a RUNNING container's session, not to "resume" a stopped one.

   To preserve work:
   - Don't stop containers, just detach (Ctrl-B D)
   - Use `aishell ps` to see running containers
   - Use `docker stop` only when done with the session
   ```

2. **Clarify "named containers" != "persistent sessions"**:
   ```
   Named containers (--name) allow multiple concurrent harness instances,
   not session persistence. Each container is still ephemeral.
   ```

3. **Consider tmux-resurrect for power users** (optional, complex):
   - Add tmux-resurrect plugin to base image
   - Save sessions to mounted volume (not /tmp)
   - Entrypoint checks for saved session and restores
   - Document manual save/restore workflow

4. **Fail gracefully on attach to stopped container**:
   ```bash
   # In aishell attach command
   if ! docker ps --filter "name=${container}" | grep -q .; then
     echo "Error: Container ${container} is not running"
     echo "Start it with: aishell claude --name ${name}"
     exit 1
   fi
   ```

**Detection:**

- User reports: "I attached but my session is empty"
- User reports: "Where did my tmux windows go?"
- `aishell attach` succeeds but shows fresh shell (no previous work)
- Confusion between `docker start` (restarts container) vs `aishell attach` (joins session)

**Sources:**
- [tmux session persistence across restarts](https://github.com/tmux-plugins/tmux-resurrect)
- [Save And Restore Tmux Environments Across Reboots](https://ostechnix.com/save-and-restore-tmux-environment/)
- [tmux in demonized docker container](https://gist.github.com/ptrj/b54b8cdd34f632028e669a1e71bc03b8)

## Moderate Pitfalls

Mistakes that cause delays or technical debt.

### Pitfall 6: TERM Variable Mismatch

**What goes wrong:**

tmux sessions display incorrectly (broken colors, formatting issues, pane layout errors) when the TERM environment variable is mismatched. Common symptoms:
- Colors appear wrong (16 colors instead of 256)
- Pane borders don't render
- Status bar garbled
- Error: "create pane failed: pane too small"
- Error: "can't find pane 1"

**Why it happens:**

The TERM variable must be set correctly in THREE places:
1. Host terminal (before docker exec)
2. Docker container (inside the container environment)
3. Inside tmux (tmux overrides TERM to "screen" or "tmux-256color")

The aishell run.clj sets TERM to "xterm-256color" (line 200), which is correct for the container. But tmux requires TERM to be "screen", "tmux", or "tmux-256color" INSIDE tmux sessions. If the host has TERM=xterm and doesn't pass -e TERM, or if tmux is configured incorrectly, color support breaks.

**Prevention:**

1. **Set TERM in container** (already done in run.clj line 200):
   ```clojure
   "-e" (str "TERM=" (or (System/getenv "TERM") "xterm-256color"))
   ```

2. **Configure tmux for 256 colors** in base image's .tmux.conf:
   ```bash
   set -g default-terminal "tmux-256color"
   # Or for older tmux versions:
   set -g default-terminal "screen-256color"
   ```

3. **Pass TERM through docker exec** in attach command:
   ```bash
   docker exec -it -e TERM=screen-256color container tmux attach
   ```

4. **Document tmux color config** for users extending base image

**Detection:**

- Colors look wrong (8-color instead of 256-color)
- `echo $TERM` inside tmux shows "xterm" (should be "screen" or "tmux-256color")
- Pane errors related to terminal size
- Status bar formatting broken

**Sources:**
- [tmux FAQ - 256 colors](https://github.com/tmux/tmux/wiki/FAQ)
- [Wrong colors inside Docker container](https://forums.docker.com/t/wrong-colors-inside-docker-container/68322)
- [Docker does not pass right terminal environment variable](https://github.com/moby/moby/issues/33910)
- [Fixing 256-color Support with tmux](https://www.forshee.me/fixing-256-color-support-in-tmux-with-terminator/)

---

### Pitfall 7: tmux Socket Location and TMPDIR

**What goes wrong:**

tmux socket location can vary based on TMPDIR, TMUX_TMPDIR, or XDG_RUNTIME_DIR environment variables. If the entrypoint sets these inconsistently between container start and `docker exec` attach, the attach command looks for the socket in the wrong place and fails with "no server running".

**Why it happens:**

tmux stores its socket using this precedence:
1. `$TMUX_TMPDIR/tmux-{uid}/`
2. `$TMPDIR/tmux-{uid}/` (if TMUX_TMPDIR unset)
3. `/tmp/tmux-{uid}/` (if both unset)

If the entrypoint or user's shell profile sets TMPDIR differently between initial run and exec, tmux creates/looks for sockets in different locations. This is especially common in HPC/cluster environments where TMPDIR points to /scratch or similar.

**Prevention:**

1. **Explicitly set TMUX_TMPDIR** in entrypoint:
   ```bash
   export TMUX_TMPDIR=/tmp
   ```

2. **Unset conflicting variables**:
   ```bash
   unset XDG_RUNTIME_DIR  # can cause conflicts
   ```

3. **Use consistent env vars in docker exec**:
   ```bash
   # In aishell attach
   docker exec -it -e TMUX_TMPDIR=/tmp container tmux attach
   ```

4. **Document socket location** for debugging:
   ```
   tmux sockets are located at /tmp/tmux-{uid}/default
   If attach fails, check: ls -la /tmp/tmux-*/
   ```

**Detection:**

- Error: "no server running on /tmp/tmux-1000/default"
- `ls /tmp/tmux-*` shows socket but tmux can't find it
- Works with `tmux -S /path/to/socket attach` but not `tmux attach`
- Socket exists in non-standard location (not /tmp)

**Sources:**
- [tmux can't auto create socket; why?](https://bbs.archlinux.org/viewtopic.php?id=206593)
- [TMUX_TMPDIR environment variable](https://www.mail-archive.com/tmux-users@lists.sourceforge.net/msg04204.html)
- [Tmux cant reconnect to server; TMUX_TMPDIR bug](https://hpc-discourse.usc.edu/t/tmux-cant-reconnect-to-server-tmux-tmpdir-bug/329)

---

### Pitfall 8: Zombie Processes with tmux + --init

**What goes wrong:**

Shell processes become zombies (`<defunct>`) that aren't reaped until tmux server terminates. This is especially common when:
- Running multiple shells in tmux panes
- Launching background processes from within tmux
- Using tmux with --enable-utempter build flag

Zombies accumulate, consume process table entries, and can eventually exhaust PID limits (though rare in practice).

**Why it happens:**

The aishell uses `--init` flag (Docker's tini), which acts as PID 1 and reaps zombie processes. But tmux server can create a process hierarchy where shells exit but tmux doesn't notify tini immediately. If tmux is built with `--enable-utempter` (for session logging), it creates additional processes that can become zombies.

The interaction: tini (PID 1) → entrypoint → gosu → tmux server → shells. When a shell exits, tmux should reap it, but if tmux is busy or the shell exited abnormally, it becomes a zombie waiting for tmux to call `wait()`. tini can only reap direct children, not grandchildren.

**Consequences:**

- `ps aux` shows increasing `<defunct>` processes
- System logs warn about process table exhaustion (rare)
- Long-running containers accumulate zombies
- Performance degradation over time (minimal, but visible in `ps` output)

**Prevention:**

1. **--init flag already handles most cases** (already in run.clj line 190)

2. **Avoid --enable-utempter** when building tmux in base image:
   ```dockerfile
   # When installing tmux from source
   ./configure --disable-utempter
   ```

3. **Periodic zombie cleanup** (if needed):
   ```bash
   # In tmux.conf, periodically respawn panes
   # Or in entrypoint, add trap handler:
   trap 'kill $(jobs -p)' EXIT
   ```

4. **Monitor for zombies** in health checks:
   ```bash
   # In aishell check command, warn if zombies exist
   zombie_count=$(ps aux | grep defunct | wc -l)
   if [ $zombie_count -gt 10 ]; then
     echo "Warning: $zombie_count zombie processes detected"
   fi
   ```

**Detection:**

- `ps aux | grep defunct` shows tmux or bash processes
- Process count increases over time without new work
- `[tmux] <defunct>` or `[bash] <defunct>` in ps output
- Container runs for days/weeks and accumulates zombies

**Sources:**
- [zombie alert! - supervisord issue](https://github.com/ochinchina/supervisord/issues/60)
- [tmux -D keeps zombies around with --enable-utempter](https://github.com/tmux/tmux/issues/4559)
- [WSL 2: tmux zombies not reaped without shell parent](https://github.com/microsoft/WSL/issues/4138)

---

### Pitfall 9: Container Name Length Limits (DNS)

**What goes wrong:**

Docker container names are used in DNS resolution for container networking. DNS labels have a 63-character limit. The aishell naming scheme is:

```
aishell-{project-hash}-{harness}
aishell-{project-hash}-{user-name}
```

If project path is deeply nested, the hash can be long. If user provides a long custom name, the total exceeds 63 characters and Docker rejects it or DNS resolution fails.

**Why it happens:**

DNS RFC 1123 limits labels to 63 characters. Docker enforces this for container names. The aishell naming scheme concatenates:
- "aishell-" (8 chars)
- project hash (variable, typically 8-12 chars)
- harness/name (variable, could be 50+ chars)

Example failure:
```
aishell-a1b2c3d4e5-my-very-long-custom-container-name-for-testing-purposes
```

**Consequences:**

- Container creation fails: "invalid container name"
- DNS resolution fails in multi-container setups
- Users must manually shorten names
- Error messages are cryptic (may not mention 63-char limit)

**Prevention:**

1. **Truncate project hash** to fixed length (8 chars):
   ```clojure
   (subs project-hash 0 8)  ; in hash.clj
   ```

2. **Validate total name length** before docker run:
   ```clojure
   (when (> (count container-name) 63)
     (output/error "Container name exceeds 63 characters (DNS limit)"))
   ```

3. **Truncate user-provided names** with warning:
   ```clojure
   (let [max-name-len (- 63 8 (count project-hash) 1)  ; "aishell-" + hash + "-"
         truncated-name (subs user-name 0 max-name-len)]
     (when (not= user-name truncated-name)
       (output/warn (str "Name truncated to " max-name-len " chars: " truncated-name)))
     truncated-name)
   ```

4. **Document limit** in help text:
   ```
   --name NAME    Custom container name (max 50 characters recommended)
   ```

**Detection:**

- Error: "Invalid container name"
- Error creating container with long name
- `docker ps` shows truncated names
- DNS resolution fails for named containers

**Sources:**
- [10 Docker Container Naming Convention Best Practices](https://devtodevops.com/blog/docker-container-naming-convention/) (mentions 63-char DNS limit)
- [3 Tips for Naming Docker Containers](https://mangohost.net/blog/3-tips-for-naming-docker-containers/)

## Minor Pitfalls

Mistakes that cause annoyance but are fixable.

### Pitfall 10: tmux Config Location and User Switching

**What goes wrong:**

tmux looks for configuration at `~/.tmux.conf` or `$XDG_CONFIG_HOME/tmux/tmux.conf`. The aishell uses gosu to switch from root to dev user, which changes `$HOME` from `/root` to `/home/dev`. If tmux config is installed in the wrong location during image build, users can't customize their tmux setup or base config isn't loaded.

**Prevention:**

- Install default tmux.conf to `/home/dev/.tmux.conf` in Dockerfile
- Or use global config at `/etc/tmux.conf`
- Document tmux customization path for project-specific `.aishell/Dockerfile` overrides

**Detection:**

- tmux starts with default settings (no custom key bindings, colors)
- `.tmux.conf` exists but isn't loaded
- `tmux show-options` doesn't show custom config values

---

### Pitfall 11: Multiple tmux Servers in Same Container

**What goes wrong:**

If the entrypoint starts a tmux server, then the harness command (claude, opencode) ALSO starts tmux, you get multiple tmux servers. Users attach to the wrong one and can't find their work.

**Prevention:**

- Entrypoint creates tmux session: `tmux new-session -d -s harness`
- All harness commands attach to existing session, don't create new servers
- Use `tmux has-session -t harness` to check before creating

**Detection:**

- `tmux ls` shows multiple sessions or servers
- Attach command shows empty session
- `ps aux | grep tmux` shows multiple tmux processes

---

### Pitfall 12: Shell Form vs Exec Form in Entrypoint

**What goes wrong:**

If the entrypoint uses shell form for tmux command (`sh -c "tmux..."`), the shell becomes PID 2 (under tini), not tmux. This breaks signal handling and adds unnecessary process layer.

**Prevention:**

Use exec form in entrypoint:
```bash
# BAD:  exec sh -c "gosu dev tmux attach"
# GOOD: exec gosu dev tmux attach-session -t harness
```

**Detection:**

- `ps aux` shows extra /bin/sh processes
- Signal handling issues despite using --init

---

### Pitfall 13: Attach Command Must Be Exact

**What goes wrong:**

tmux attach commands have many variants:
- `tmux attach`
- `tmux attach-session`
- `tmux a`
- `tmux at`

Some work with certain tmux versions, others don't. Old scripts using short forms (`tmux a`) may fail with "unknown command" on newer tmux.

**Prevention:**

Always use full command form:
```bash
tmux attach-session -t harness  # unambiguous, version-stable
```

**Detection:**

- Error: "unknown command: a"
- Attach works locally but fails in container (different tmux version)

## Phase-Specific Warnings

| Phase Topic | Likely Pitfall | Mitigation |
|-------------|---------------|------------|
| Entrypoint modification | Pitfall 1 (Signal handling) | Add signal traps BEFORE exec-ing tmux, test with `docker stop` |
| Named container implementation | Pitfall 4 (--rm conflict) | Add pre-flight name collision check, document cleanup |
| Attach command | Pitfall 3 (TTY allocation) | Use `docker exec -it`, validate stdin is TTY, handle errors |
| tmux session creation | Pitfall 2 (Socket permissions) | Start tmux AFTER gosu, verify socket ownership |
| User documentation | Pitfall 5 (Persistence expectations) | Document ephemeral session model, clarify attach vs resume |
| tmux configuration | Pitfall 6 (TERM variable) | Set TERM in .tmux.conf, pass through in exec |
| Multi-container support | Pitfall 9 (Name length) | Truncate hash and name, validate total length |
| Base image Dockerfile | Pitfall 8 (Zombies) | Build tmux without --enable-utempter |

## Integration-Specific Concerns

### tmux + gosu + --init Interaction

The three-way interaction between tmux (session management), gosu (user switching), and --init (PID 1 zombie reaping) creates edge cases:

1. **Process tree must be**: tini (PID 1) → entrypoint script → gosu → tmux server
2. **Signal flow**: SIGTERM → tini → entrypoint trap handler → tmux kill-server
3. **Socket ownership**: Must match gosu target user, not entrypoint user

Test this specific interaction during implementation:
```bash
# Start container
docker run --name test-tmux ...

# In another terminal, send SIGTERM
docker stop -t 3 test-tmux

# Verify:
# - Container stops in <3 seconds (not 10)
# - Exit code is 0 or 143 (SIGTERM), not 137 (SIGKILL)
# - No "forcefully killing" in docker logs
```

### Attach vs Exec Semantics

The implementation has two attach-like operations:
1. **Initial harness start**: `docker run -it` → entrypoint → tmux new + attach
2. **Subsequent attach**: `docker exec -it` → gosu → tmux attach

These must use identical user context, TERM variables, and socket paths, or they'll diverge (user attached to session A can't see session B).

## Testing Checklist for Implementation

- [ ] Container stops gracefully in <3 seconds with `docker stop`
- [ ] Exit code is 0 or 143 after stop, not 137
- [ ] `aishell attach` succeeds when container is running
- [ ] `aishell attach` fails gracefully when container is stopped
- [ ] Socket permissions allow attach as dev user
- [ ] TERM variable supports 256 colors in tmux
- [ ] Named containers can be started multiple times (no name collision)
- [ ] `aishell ps` shows running containers correctly
- [ ] `docker ps -a` doesn't accumulate stopped containers (--rm works)
- [ ] No zombie processes after running for 1 hour
- [ ] Container name length validated (<63 chars)
- [ ] Works in non-TTY context (fails gracefully with clear error)

## Sources Summary

**Signal Handling:**
- [PID 1 Signal Handling in Docker](https://petermalmgren.com/signal-handling-docker/)
- [Docker Signal Demo with tmux](https://github.com/DrPsychick/docker-signal-demo)
- [Why Docker Containers Won't Stop Gracefully (2026)](https://medium.com/@fernando.harsha2016/why-your-docker-container-wont-stop-gracefully-understanding-pid-1-and-process-management-569c44dce004)

**Socket Permissions:**
- [User Switching with gosu](https://docsaid.org/en/blog/gosu-usage/)
- [tmux socket permission issues](https://github.com/tmux/tmux/issues/2110)

**TTY Allocation:**
- [Docker exec TTY issues](https://github.com/moby/moby/issues/8755)
- [tmux and Docker multiplexers guide](https://dockerdocs.org/multiplexers/)

**Container Naming:**
- [Fix Docker Name Conflicts (2026)](https://github.com/oneuptime/blog/tree/master/posts/2026-01-25-fix-docker-container-name-conflict-errors)
- [Docker Naming Best Practices](https://devtodevops.com/blog/docker-container-naming-convention/)

**Session Persistence:**
- [tmux-resurrect for session persistence](https://github.com/tmux-plugins/tmux-resurrect)
- [Save and Restore tmux Environments](https://ostechnix.com/save-and-restore-tmux-environment/)

**TERM Variables:**
- [tmux 256 color FAQ](https://github.com/tmux/tmux/wiki/FAQ)
- [Docker TERM variable handling](https://github.com/moby/moby/issues/33910)

All research conducted 2026-01-31, sources verified for currency and relevance.
