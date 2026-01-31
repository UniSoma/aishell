# Requirements: aishell v2.6.0

**Defined:** 2026-01-31
**Core Value:** Run agentic AI harnesses in isolated, reproducible environments without polluting the host system.

## v2.6.0 Requirements

Requirements for tmux Integration & Named Containers milestone.

### Base Image

- [ ] **IMG-01**: tmux is installed in the base Docker image (Debian bookworm tmux package)
- [ ] **IMG-02**: Image build succeeds with tmux and `tmux -V` reports version inside container

### Container Naming

- [ ] **NAME-01**: Harness commands produce a Docker container named `aishell-{project-hash}-{name}` where project-hash is first 8 chars of SHA-256 of project directory path
- [ ] **NAME-02**: Default container name equals the harness name (e.g., `claude`, `opencode`, `codex`, `gemini`)
- [ ] **NAME-03**: User can override container name with `--name <name>` flag (e.g., `aishell claude --name reviewer`)
- [ ] **NAME-04**: Shell mode (`aishell`) does not use named containers (preserves current ephemeral anonymous behavior)

### tmux Integration

- [ ] **TMUX-01**: Harness commands auto-start inside a tmux session named `main`
- [ ] **TMUX-02**: Shell mode (`aishell`) has tmux available but does NOT auto-start a tmux session
- [ ] **TMUX-03**: Container remains running in detached mode after harness starts (user can attach/detach)

### Attach

- [ ] **ATTCH-01**: `aishell attach <name>` connects to a running container's tmux session via `docker exec -it`
- [ ] **ATTCH-02**: `aishell attach <name> --session <session>` connects to a specific tmux session inside the container
- [ ] **ATTCH-03**: Attaching to a non-existent or stopped container produces a clear error message
- [ ] **ATTCH-04**: User can detach from tmux (Ctrl+B D) without stopping the container

### Discovery

- [ ] **DISC-01**: `aishell ps` lists running containers for the current project
- [ ] **DISC-02**: Output includes container name, status, and creation time

### Conflict Detection

- [ ] **CONF-01**: Starting a container with a name already in use by a running container produces a clear error with guidance
- [ ] **CONF-02**: Starting a container with a name used by a stopped container auto-removes the stopped container and proceeds

### Lifecycle

- [ ] **LIFE-01**: Containers use `--rm` flag (ephemeral, destroyed on exit)
- [ ] **LIFE-02**: Existing shell mode and exec mode behavior is unchanged

## Deferred

Tracked but not in v2.6.0 roadmap.

- **ATTCH-05**: Session discovery on attach error (show `tmux ls` output if session not found)
- **DISC-03**: Visual indicators in ps output for containers with active tmux clients
- **CLEAN-01**: `aishell clean` command to remove stopped aishell containers
- **TMUX-04**: Per-harness session naming (session name = harness name instead of "main")

## Out of Scope

| Feature | Reason |
|---------|--------|
| Persistent tmux sessions across container restarts | Violates ephemeral design principle |
| tmux plugin installation in base image | Version drift, slow builds, conflicts with ephemeral model |
| Built-in tmux configuration management | Scope creep; users can mount their own .tmux.conf |
| Session sharing between containers | Complex, unclear use case |
| Background harness execution (no tmux) | Defeats observability purpose |
| Auto-cleanup command | Nice-to-have, not core to v2.6.0 value |

## Traceability

| Requirement | Phase | Status |
|-------------|-------|--------|
| IMG-01 | Phase 31 | Pending |
| IMG-02 | Phase 31 | Pending |
| NAME-01 | Phase 30 | Pending |
| NAME-02 | Phase 30 | Pending |
| NAME-03 | Phase 30 | Pending |
| NAME-04 | Phase 32 | Pending |
| TMUX-01 | Phase 32 | Pending |
| TMUX-02 | Phase 32 | Pending |
| TMUX-03 | Phase 32 | Pending |
| ATTCH-01 | Phase 33 | Pending |
| ATTCH-02 | Phase 33 | Pending |
| ATTCH-03 | Phase 33 | Pending |
| ATTCH-04 | Phase 33 | Pending |
| DISC-01 | Phase 34 | Pending |
| DISC-02 | Phase 34 | Pending |
| CONF-01 | Phase 32 | Pending |
| CONF-02 | Phase 32 | Pending |
| LIFE-01 | Phase 32 | Pending |
| LIFE-02 | Phase 32 | Pending |

**Coverage:**
- v2.6.0 requirements: 19 total
- Mapped to phases: 19
- Unmapped: 0

---
*Requirements defined: 2026-01-31*
*Last updated: 2026-01-31 after research synthesis*
