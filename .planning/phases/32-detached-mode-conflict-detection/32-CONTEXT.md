# Phase 32: Detached Mode & Conflict Detection - Context

**Gathered:** 2026-01-31
**Status:** Ready for planning

<domain>
## Phase Boundary

Enable harness commands to run in named, detached containers with tmux auto-start while preserving existing foreground mode behavior. Handle container name collisions gracefully. Both foreground and detached modes use named containers and conflict detection.

</domain>

<decisions>
## Implementation Decisions

### Detach behavior
- `--detach` flag triggers detached mode (explicit opt-in, backwards compatible)
- Default remains foreground (current behavior preserved)
- Both harness commands AND shell mode support `--detach`
- All containers (foreground and detached) always get named containers (`aishell-{hash}-{name}`)

### Conflict resolution
- Conflict detection applies to BOTH foreground and detached modes
- Running container with same name → error with attach hint: "Container already running. Use: aishell attach <name>"
- Stopped container with same name → Claude's discretion on handling (see below)
- `--force` flag behavior → Claude's discretion (see below)
- Foreground containers use `--rm` (auto-removed on exit)
- Detached containers also use `--rm` (auto-removed on stop, no orphans)

### Tmux session setup
- ALL modes auto-start inside tmux (harness, shell, foreground, detached)
- This overrides the original roadmap assumption that shell mode would NOT auto-start tmux
- Tmux session always named "main" — container name carries the harness identity
- Users can create additional tmux sessions on demand
- Must support both: attaching to the running harness AND shelling into the container

### Graceful shutdown
- Priority is speed (1-2s shutdown)
- When harness exits naturally (user quits), container stops automatically (and --rm cleans it up)
- Clean lifecycle: launch → run → exit → removed

### Claude's Discretion
- Launch feedback format for detached mode (what the user sees after `aishell claude --detach`)
- Stopped container cleanup approach (auto-remove silently vs with notice)
- Whether to include `--force` flag for replacing running containers
- Signal handling approach (tini vs explicit trap) — decide based on testing
- Acceptable exit code policy (137 vs 0/143) — decide based on reliability
- How to implement "attach to harness" vs "shell into container" (pre-create sessions, docker exec, etc.)

</decisions>

<specifics>
## Specific Ideas

- User explicitly wants to be able to both attach to a running harness AND get a shell inside the container — two distinct access patterns
- Consistency is valued: tmux everywhere, named containers everywhere, conflict detection everywhere
- `--rm` on all containers — ephemeral is the philosophy, no accumulation of stopped containers

</specifics>

<deferred>
## Deferred Ideas

None — discussion stayed within phase scope

</deferred>

---

*Phase: 32-detached-mode-conflict-detection*
*Context gathered: 2026-01-31*
