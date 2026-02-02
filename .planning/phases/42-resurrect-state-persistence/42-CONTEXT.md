# Phase 42: Resurrect State Persistence - Context

**Gathered:** 2026-02-02
**Status:** Ready for planning

<domain>
## Phase Boundary

Enable optional tmux session state persistence via tmux-resurrect. Users can configure resurrect in config.yaml to have their tmux layout (and optionally processes) survive container restarts. Resurrect state is stored on the host and mounted into the container.

</domain>

<decisions>
## Implementation Decisions

### Config shape
- `tmux.resurrect` accepts boolean or map
- `resurrect: true` is shorthand for `{enabled: true}` (boolean sugar)
- `resurrect: {restore_processes: true}` enables map form with overrides
- If resurrect is a map without `enabled` key, assume enabled: true
- `enabled: false` in map form explicitly disables while preserving config

### Storage location
- Resurrect state stored in central home dir: `~/.aishell/resurrect/<project-hash>/`
- Reuse same project hash used for named containers
- Directory auto-created on first enable when container starts
- No CLI command for clearing state — user deletes directory manually

### Restoration scope
- Layout restoration active by default (tmux-resurrect default behavior)
- Process restoration disabled by default
- When process restoration enabled via `restore_processes: true`, restore all processes (`:all:` mode)
- Manual save only — user triggers with prefix + Ctrl-s (no auto-save timer, no tmux-continuum)
- Auto-restore on tmux start — if state exists, restore automatically when container starts

### Activation flow
- Resurrect requires `--with-tmux` to be set — it's a tmux feature
- If resurrect configured but tmux not enabled: silently ignore (no warning, no error)
- tmux-resurrect plugin auto-added to plugin list when resurrect is enabled — user doesn't declare it
- If user also manually lists tmux-resurrect in plugins: deduplicate silently

### Claude's Discretion
- Exact resurrect config injection approach (tmux set-options vs config file append)
- How auto-restore is triggered at container startup
- Volume mount strategy for the resurrect state directory
- Integration with existing entrypoint plugin bridging from Phase 41

</decisions>

<specifics>
## Specific Ideas

No specific requirements — open to standard approaches

</specifics>

<deferred>
## Deferred Ideas

None — discussion stayed within phase scope

</deferred>

---

*Phase: 42-resurrect-state-persistence*
*Context gathered: 2026-02-02*
