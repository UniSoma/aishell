# Technology Stack: tmux Integration for v2.6.0

**Project:** Agentic Harness Sandbox
**Researched:** 2026-01-31
**Confidence:** HIGH

## Executive Summary

Adding tmux to the existing Docker-based sandbox requires minimal stack additions. The primary changes are:
1. Add tmux package to Debian base image (tmux 3.3a-3 from bookworm)
2. Implement Docker container naming with project hash isolation
3. Use `docker exec` with tmux client commands for attach functionality
4. Leverage existing Babashka CLI for orchestration

**No fundamental architecture changes needed.** The existing ephemeral container model, gosu user switching, and Babashka CLI infrastructure all remain unchanged.

## Required Stack Additions

### 1. tmux Package

| Component | Version | Source | Purpose |
|-----------|---------|--------|---------|
| tmux | 3.3a-3 | debian:bookworm-slim apt | Terminal multiplexer for session management |

**Installation in Dockerfile:**
```dockerfile
RUN apt-get update && apt-get install -y --no-install-recommends \
    # ... existing packages ...
    tmux \
    && rm -rf /var/lib/apt/lists/*
```

**Why tmux 3.3a-3:**
- Native to Debian bookworm (no backports needed)
- Stable, well-tested version
- Includes all features needed: named sessions, detach/attach, multiple windows
- Dependencies already satisfied by base image (libc6, libevent-core-2.1-7, libtinfo6, libutempter0)
- ~1MB package size (negligible overhead)

**What NOT to add:**
- tmux plugin manager (tpm) - Unnecessary complexity for ephemeral containers
- Custom tmux.conf - Users can mount their own via config.yaml if desired
- tmux 3.5a from backports - 3.3a has all required features, avoid unnecessary version churn

**Source:** [Debian Packages - tmux in bookworm](https://packages.debian.org/bookworm/tmux)

### 2. Docker Container Naming Strategy

| Component | Purpose | Pattern |
|-----------|---------|---------|
| Project hash | Cross-project isolation | First 8 chars of SHA-256(project-dir) |
| Container name | User-visible identifier | `--name` flag or harness name default |
| Docker name | Full container name | `aishell-{project-hash}-{name}` |

**Implementation points:**
- Reuse existing `aishell.docker.hash/compute-hash` (already returns 12 chars, use first 8)
- Hash based on absolute project directory path (deterministic per project)
- Pattern: `aishell-abc12345-claude` where `abc12345` is project hash, `claude` is name
- Enables running `aishell claude --name dev` and `aishell claude --name prod` in same project
- Prevents conflicts when same harness runs in different projects

**Why this pattern:**
- **Prefix `aishell-`**: Namespace separation from other Docker containers
- **Project hash**: Enables multiple projects to use same container name without conflicts
- **User-provided name**: Human-readable identifier for `aishell attach <name>`
- **Lowercase + hyphens**: Docker naming best practices (no underscores, dots only for registry)
- **63 char limit**: `aishell-` (8) + hash (8) + `-` (1) + name (46 max) = 63 chars max

**What NOT to do:**
- Generic names like "web" or "database" (not descriptive enough)
- Environment prefixes like "prod-" (user can include in --name if desired)
- Timestamps or random suffixes (breaks attach by name)
- Underscores (Docker allows but hyphens are more standard)

**Sources:**
- [Docker Container Naming Best Practices](https://devtodevops.com/blog/docker-container-naming-convention/)
- [3 Tips for Naming Docker Containers](https://mangohost.net/blog/3-tips-for-naming-docker-containers/)

### 3. Docker Label System for `aishell ps`

| Label Key | Value | Purpose |
|-----------|-------|---------|
| `aishell.project-hash` | 8-char hash | Filter containers for current project |
| `aishell.project-dir` | Absolute path | Display project location in ps output |
| `aishell.container-name` | User-provided name | Display user-friendly name |

**Implementation:**
```clojure
;; In build-docker-args, add labels:
["-l" (str "aishell.project-hash=" project-hash)
 "-l" (str "aishell.project-dir=" project-dir)
 "-l" (str "aishell.container-name=" container-name)]
```

**Querying for ps command:**
```bash
# List containers for current project
docker ps --filter "label=aishell.project-hash=abc12345" \
  --format "table {{.Names}}\t{{.Status}}\t{{.Label \"aishell.container-name\"}}"
```

**Why labels over parsing names:**
- Labels are first-class Docker primitives for metadata
- Filtering by label is efficient (indexed by Docker)
- Enables rich metadata (project dir, container name, future additions)
- Avoids fragile string parsing of container names

**Sources:**
- [Docker Object Labels Documentation](https://docs.docker.com/engine/manage-resources/labels/)
- [Docker ps Filter by Label](https://docs.docker.com/reference/cli/docker/container/ls/)

## Integration with Existing Stack

### 4. tmux Session Lifecycle

**Auto-start for harness commands:**
```bash
# In Babashka CLI, wrap harness execution:
tmux new-session -s main -d "claude --dangerously-skip-permissions"
tmux attach-session -t main
```

**Key command options:**
- `new-session -s <name>` - Create named session
- `-d` - Start detached (create session without attaching)
- `-A` - Attach if exists, create if not (useful for idempotency)
- `attach-session -t <session>` - Attach to named session

**Shell mode (no auto-start):**
- `aishell` enters bash directly (tmux available but not auto-started)
- User can run `tmux` manually if desired
- Preserves existing UX for shell exploration

**Why this approach:**
- Harnesses run inside tmux automatically (transparent to user)
- Shell mode remains lightweight (no forced tmux)
- Consistent with "tmux available everywhere" goal
- Detach-on-destroy not needed (ephemeral containers exit on process end)

**Sources:**
- [tmux new-session Manual](https://man7.org/linux/man-pages/man1/tmux.1.html)
- [Getting Started with tmux](https://github.com/tmux/tmux/wiki/Getting-Started)

### 5. Attach Implementation via docker exec

**Attach to default session:**
```bash
docker exec -it aishell-abc12345-claude tmux attach-session -t main
```

**Attach to specific session:**
```bash
docker exec -it aishell-abc12345-claude tmux attach-session -t <session>
```

**Why `docker exec` not `docker attach`:**
- `docker attach` connects to PID 1 (entrypoint.sh + gosu + bash)
  - Exiting kills the container (not desired for ephemeral but running containers)
  - All attached terminals see same output (not desired for independent tmux clients)
- `docker exec` creates new process (tmux client)
  - Exiting tmux client leaves container running
  - Multiple users can attach independently
  - Proper tmux multi-client behavior

**Terminal flags:**
- `-it` required for interactive tmux session
- `-i` keeps STDIN open for tmux input
- `-t` allocates pseudo-TTY for tmux rendering

**Error handling:**
- Container not found: Clear error "Container '{name}' not found for current project"
- tmux session not found: `tmux attach` returns error, user sees it directly
- No tmux installed: Won't happen (always in base image)

**Sources:**
- [Docker Exec vs Attach](https://www.baeldung.com/ops/docker-exec-attach-difference)
- [Docker Attach vs Exec - When to Use What](https://yasoob.me/posts/docker-attach-vs-exec-when-to-use-what/)

### 6. Container Lifecycle Changes

**Current behavior (v2.5.0):**
- `docker run --rm` - Container deleted immediately on exit
- No container naming (Docker assigns random names like `quirky_einstein`)
- No persistent containers

**New behavior (v2.6.0):**
- `docker run --rm --name aishell-{hash}-{name}` - Named but still ephemeral
- Container runs until harness exits OR user stops manually
- `aishell attach` while container running, NOT after exit
- Conflict detection: If name exists, error with clear message

**Why still --rm:**
- Ephemeral is core design principle
- Named containers enable attach-while-running
- `--rm` ensures cleanup after exit (no stale containers)
- User can run multiple named containers concurrently

**Conflict handling:**
```bash
# Check if name exists before docker run:
docker ps -a --filter "name=aishell-abc12345-claude" --format "{{.Names}}"
# If exists: Error "Container 'claude' already running. Use 'aishell attach claude' or stop it first."
```

**Sources:**
- [Docker run --rm Guide](https://thelinuxcode.com/docker-run-rm-an-expert-teachers-guide-to-effectively-using-and-best-practices/)
- [Docker Running Containers Documentation](https://docs.docker.com/engine/containers/run/)

## Babashka Integration Points

### 7. CLI Command Structure

**New commands (all in `src/aishell/cli.clj`):**

| Command | Purpose | Implementation |
|---------|---------|----------------|
| `aishell attach <name>` | Attach to container's tmux session | `docker exec -it` + `tmux attach` |
| `aishell attach <name> --session <s>` | Attach to specific tmux session | `docker exec -it` + `tmux attach -t <s>` |
| `aishell ps` | List running containers | `docker ps --filter label` + format |

**Existing commands (modified):**

| Command | Change | Reason |
|---------|--------|--------|
| `aishell claude` | Add `--name` flag | Allow named containers |
| `aishell opencode` | Add `--name` flag | Allow named containers |
| `aishell codex` | Add `--name` flag | Allow named containers |
| `aishell gemini` | Add `--name` flag | Allow named containers |
| `aishell` (shell) | No auto-start tmux | Preserve current UX |

**Harness wrapper logic (pseudo-code):**
```clojure
(defn run-harness-in-tmux [harness-name container-name]
  (let [session "main"
        cmd (str "tmux new-session -s " session " -d '" harness-name " --dangerously-skip-permissions' && "
                 "tmux attach-session -t " session)]
    (docker-run-with-name container-name cmd)))
```

**Why in Babashka, not shell script:**
- Existing CLI infrastructure (flags, error handling, state management)
- Clojure data structures for parsing/validation
- Cross-platform (Linux, macOS)
- Consistent with v2.0+ architecture

### 8. State Management

**No new state file fields needed:**
- Container names are runtime, not build-time
- Project hash computed on-demand from project-dir
- tmux always installed (no build flag like `--with-tmux`)

**Potential future state (out of scope for v2.6.0):**
```edn
;; If we add default container name preference:
{:default-container-name "dev"}  ; User-specific default for --name
```

## What NOT to Add

### Anti-Patterns for tmux Integration

| What | Why Not |
|------|---------|
| tmux-resurrect / tmux-continuum | Session persistence conflicts with ephemeral container model |
| Custom tmux.conf in image | User preferences vary; let them mount ~/.tmux.conf if desired |
| tmuxinator / tmuxp | Over-engineering for simple "start harness in main session" use case |
| tmux server on host with docker exec | Container must own tmux server for proper isolation |
| Docker volume for /tmp/tmux-* | Ephemeral model - sessions die with container |

### Out of Scope Features

| Feature | Reason to Defer |
|---------|----------------|
| Shared tmux sessions across containers | Complex, unclear use case |
| tmux session snapshots | Conflicts with ephemeral model |
| Auto-reconnect on container restart | Containers are --rm (no restart) |
| tmux-based monitoring/logging | Pre_start handles sidecars, tmux is for user sessions |

## Configuration Impact

### User-facing YAML additions

**New optional config in `.aishell/config.yaml`:**
```yaml
# Optional: Mount user's tmux config
mounts:
  - ~/.tmux.conf

# Optional: tmux-specific environment
env:
  TMUX_TMPDIR: /tmp/tmux-custom  # Custom socket directory
```

**No breaking changes:**
- Existing configs work unchanged
- tmux auto-start only for harness commands
- Shell mode (`aishell`) unaffected

## Performance Considerations

| Aspect | Impact | Mitigation |
|--------|--------|------------|
| Image size | +1MB for tmux package | Negligible (current ~500MB with Node.js) |
| Container startup | +50ms for tmux server init | Only when harness starts, not shell mode |
| Memory per session | +2-5MB per tmux session | Acceptable for desktop/CI environments |
| docker exec latency | +10-20ms vs direct attach | Acceptable for user-initiated attach |

**Benchmark expectations:**
- `aishell claude`: +50ms (tmux new-session overhead)
- `aishell attach claude`: +20ms (docker exec + tmux client)
- `aishell ps`: +100ms (docker ps + label filtering)

**Why these are acceptable:**
- User-initiated commands (not hot path)
- Benefits (multi-session, attach/detach) outweigh minimal latency
- Desktop/CI use case (not latency-sensitive production serving)

## Security Considerations

### tmux Socket Permissions

**Default tmux behavior:**
- Creates socket at `/tmp/tmux-{UID}/default` (per-user isolation)
- Socket owned by container user (UID from LOCAL_UID)
- gosu ensures proper ownership

**No additional hardening needed:**
- Container is already ephemeral and single-user
- No multi-tenant tmux sharing
- Host cannot access container tmux sockets (container filesystem isolation)

### Container Naming Security

**Risk: Container name collision attacks**
- User A in project X runs `aishell claude --name prod`
- User B in project Y runs `aishell claude --name prod`
- Without project hash: collision
- With project hash: `aishell-{X-hash}-prod` vs `aishell-{Y-hash}-prod` (isolated)

**Mitigation:**
- Project hash in name prevents cross-project collisions
- Conflict detection prevents same-project collisions
- Labels not user-controlled (set by CLI, not user input)

## Verification Checklist

Before shipping v2.6.0:

- [ ] tmux 3.3a-3 installs correctly in Debian bookworm-slim
- [ ] `tmux new-session -s main -d "echo test"` works in container
- [ ] `docker exec -it <container> tmux attach -t main` attaches successfully
- [ ] Container names follow `aishell-{8-char-hash}-{name}` pattern
- [ ] `docker ps --filter label=aishell.project-hash=<hash>` filters correctly
- [ ] Conflict detection prevents duplicate container names
- [ ] Shell mode (`aishell`) does NOT auto-start tmux
- [ ] Multiple named containers run concurrently in same project
- [ ] Exiting tmux session in harness mode exits container (--rm cleanup)

## Migration Notes

**From v2.5.0 to v2.6.0:**

**Breaking changes:** None

**New features:**
- `--name` flag for harness commands (optional, defaults to harness name)
- `aishell attach <name>` command
- `aishell ps` command
- Harnesses auto-start in tmux session named "main"

**User education needed:**
- Document tmux keybindings (especially `Ctrl+b d` to detach)
- Explain difference between detach (container keeps running) and exit (container stops)
- Show `aishell ps` for discovering running containers

**Backward compatibility:**
- Existing `aishell claude` works (uses default name "claude")
- No config.yaml changes required
- Docker image rebuilds with tmux (one-time `aishell build --update`)

## Sources

- [Debian Packages - tmux in bookworm](https://packages.debian.org/bookworm/tmux)
- [Docker Container Naming Best Practices](https://devtodevops.com/blog/docker-container-naming-convention/)
- [3 Tips for Naming Docker Containers](https://mangohost.net/blog/3-tips-for-naming-docker-containers/)
- [Docker Object Labels Documentation](https://docs.docker.com/engine/manage-resources/labels/)
- [tmux Manual Page](https://man7.org/linux/man-pages/man1/tmux.1.html)
- [Docker Exec vs Attach Comparison](https://www.baeldung.com/ops/docker-exec-attach-difference)
- [Docker --rm Best Practices](https://thelinuxcode.com/docker-run-rm-an-expert-teachers-guide-to-effectively-using-and-best-practices/)
- [Getting Started with tmux](https://github.com/tmux/tmux/wiki/Getting-Started)
- [How to Use tmux in 2026](https://www.hostinger.com/tutorials/how-to-use-tmux)

---
*Research complete: Ready for roadmap creation*
