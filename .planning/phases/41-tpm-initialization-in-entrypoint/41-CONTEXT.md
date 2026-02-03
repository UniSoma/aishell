# Phase 41: TPM Initialization in Entrypoint - Context

**Gathered:** 2026-02-02
**Status:** Ready for planning

<domain>
## Phase Boundary

Make installed tmux plugins discoverable at runtime. Bridge the gap between volume-installed plugins (Phase 40) and a working tmux session inside the container. Ensure shell mode and harness commands work correctly when tmux is disabled.

</domain>

<decisions>
## Implementation Decisions

### Plugin symlink strategy
- Override any existing ~/.tmux/plugins — volume-managed plugins are authoritative inside the container
- Claude's discretion: symlink vs TMUX_PLUGIN_MANAGER_PATH vs bind mount approach
- Claude's discretion: idempotent-every-start vs only-if-missing timing
- Claude's discretion: TPM path handling (likely covered by same symlink since TPM lives in volume)

### Config injection approach
- Copy read-only mounted ~/.tmux.conf to a writable location, append TPM `run` line, start tmux with that copy
- If no ~/.tmux.conf exists on host, create a minimal config with just the TPM initialization line
- Claude's discretion: writable copy path (e.g., same path overwriting mount, or /tmp, or elsewhere)
- Claude's discretion: whether to set TMUX_PLUGIN_MANAGER_PATH in config as well as env var

### Startup sequence & tmux-disabled mode
- Single entrypoint script with conditional blocks — shared setup runs always, tmux steps guarded by :with-tmux flag
- When tmux disabled: entrypoint execs directly into shell, no tmux code runs
- Harness commands (aishell exec) work identically regardless of tmux state — always exec directly in container
- Fixed tmux session name: 'harness' — container isolation (Phase 34) handles project separation

### Error handling at runtime
- TPM init failure: warn to stderr and continue — plugins are nice-to-have, not required
- tmux itself fails to start: hard fail (exit container) — user explicitly asked for tmux
- Quick sanity check on plugin symlink before starting tmux (single test -d) — warn if broken, continue anyway
- All runtime warnings go to stderr only — container is ephemeral, no log file needed

### Claude's Discretion
- Exact symlink vs TMUX_PLUGIN_MANAGER_PATH implementation
- Writable config copy location
- Entrypoint script structure and ordering details
- Sanity check specifics and warning message text

</decisions>

<specifics>
## Specific Ideas

- Warning-only approach for plugin issues is consistent with Phase 40's validation framework
- Container-first philosophy: volume-managed plugins always take precedence over any mounted dotfiles
- The "copy + append" pattern for read-only config was derived from Phase 39's read-only mount decision

</specifics>

<deferred>
## Deferred Ideas

None — discussion stayed within phase scope

</deferred>

---

*Phase: 41-tpm-initialization-in-entrypoint*
*Context gathered: 2026-02-02*
