# Requirements: aishell v2.6.0

**Defined:** 2026-01-31
**Core Value:** Run agentic AI harnesses in isolated, reproducible environments without polluting the host system.

## v2.6.0 Requirements

Requirements for tmux Integration & Named Containers milestone.

### Base Image

- [x] **IMG-01**: tmux is installed in the base Docker image (Debian bookworm tmux package)
- [x] **IMG-02**: Image build succeeds with tmux and `tmux -V` reports version inside container

### Container Naming

- [x] **NAME-01**: Harness commands produce a Docker container named `aishell-{project-hash}-{name}` where project-hash is first 8 chars of SHA-256 of project directory path
- [x] **NAME-02**: Default container name equals the harness name (e.g., `claude`, `opencode`, `codex`, `gemini`)
- [x] **NAME-03**: User can override container name with `--name <name>` flag (e.g., `aishell claude --name reviewer`)
- [x] **NAME-04**: ~~Shell mode does not use named containers~~ OVERRIDDEN: All modes get named containers (user decision in CONTEXT.md)

### tmux Integration

- [x] **TMUX-01**: Harness commands auto-start inside a tmux session named `main`
- [x] **TMUX-02**: ~~Shell mode does NOT auto-start tmux~~ OVERRIDDEN: All modes auto-start in tmux (user decision in CONTEXT.md)
- [x] **TMUX-03**: Container remains running in detached mode after harness starts (user can attach/detach)

### Attach

- [x] **ATTCH-01**: `aishell attach <name>` connects to a running container's tmux session via `docker exec -it`
- [x] **ATTCH-02**: `aishell attach <name> --session <session>` connects to a specific tmux session inside the container
- [x] **ATTCH-03**: Attaching to a non-existent or stopped container produces a clear error message
- [x] **ATTCH-04**: User can detach from tmux (Ctrl+B D) without stopping the container

### Discovery

- [ ] **DISC-01**: `aishell ps` lists running containers for the current project
- [ ] **DISC-02**: Output includes container name, status, and creation time

### Conflict Detection

- [x] **CONF-01**: Starting a container with a name already in use by a running container produces a clear error with guidance
- [x] **CONF-02**: Starting a container with a name used by a stopped container auto-removes the stopped container and proceeds

### Lifecycle

- [x] **LIFE-01**: Containers use `--rm` flag (ephemeral, destroyed on exit)
- [x] **LIFE-02**: Existing shell mode and exec mode behavior is unchanged

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
| IMG-01 | Phase 31 | Complete |
| IMG-02 | Phase 31 | Complete |
| NAME-01 | Phase 30 | Complete |
| NAME-02 | Phase 30 | Complete |
| NAME-03 | Phase 30 | Complete |
| NAME-04 | Phase 32 | Complete (overridden) |
| TMUX-01 | Phase 32 | Complete |
| TMUX-02 | Phase 32 | Complete (overridden) |
| TMUX-03 | Phase 32 | Complete |
| ATTCH-01 | Phase 33 | Complete |
| ATTCH-02 | Phase 33 | Complete |
| ATTCH-03 | Phase 33 | Complete |
| ATTCH-04 | Phase 33 | Complete |
| DISC-01 | Phase 34 | Pending |
| DISC-02 | Phase 34 | Pending |
| CONF-01 | Phase 32 | Complete |
| CONF-02 | Phase 32 | Complete |
| LIFE-01 | Phase 32 | Complete |
| LIFE-02 | Phase 32 | Complete |

**Coverage:**
- v2.6.0 requirements: 19 total
- Mapped to phases: 19
- Unmapped: 0

---
*Requirements defined: 2026-01-31*
*Last updated: 2026-01-31 after research synthesis*
