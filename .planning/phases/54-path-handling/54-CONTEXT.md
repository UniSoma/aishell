# Phase 54: Path Handling - Context

**Gathered:** 2026-02-11
**Status:** Ready for planning

<domain>
## Phase Boundary

Cross-platform path handling for home directories, state/config locations, and Docker volume mounts. All path resolution and construction must work correctly on both Windows and Unix. Docker volume mount paths must be translated for Windows hosts targeting Linux containers.

</domain>

<decisions>
## Implementation Decisions

### Home directory resolution
- On Windows: `USERPROFILE > HOME > fs/home` priority order
- On Unix: keep current `HOME > fs/home` order
- Silent fallback to `fs/home` if all env vars are unset (no warning, no error)
- `$HOME` in config files is abstract shorthand for "home directory" — `get-home` resolves it correctly on both platforms, no `$USERPROFILE` expansion needed
- No `%USERPROFILE%` (Windows cmd.exe syntax) expansion — `~` is the universal shorthand

### Path separator strategy
- Keep OS-native separators internally (let `babashka.fs` do its thing)
- Normalize to forward slashes only when constructing Docker commands
- Enforce all path construction through `fs/path` — audit and eliminate string concatenation with hardcoded `/` separators
- `expand-path` output normalized through `fs/path` to prevent mixed-separator paths
- Trust `fs/path` normalization for path comparison — no separate `path-equals?` helper needed

### Config/state locations
- Config: `~/.aishell/` on all platforms (same location everywhere, follows Docker convention)
- State on Windows: `LOCALAPPDATA/aishell` (platform-native for invisible state data)
- State on Unix: keep current XDG (`$XDG_STATE_HOME/aishell` or `~/.local/state/aishell`)
- If `LOCALAPPDATA` unset on Windows: fall back to `~/.local/state/aishell` (Unix default)
- All platform-specific path resolution centralized through `util.clj` functions

### Docker mount path conversion
- Smart colon parsing: detect drive letter (`X:`) to avoid splitting on it in mount strings
- Normalize mount source paths to forward slashes before passing to Docker
- Project mount destination: `/workspace` on Windows only, keep same-path mount on Unix
- `LOCAL_HOME` set to `/home/developer` on Windows (Windows home path meaningless in Linux container)
- Source-only mounts (e.g., `~/.ssh`) on Windows: map destination under container home (`/home/developer/.ssh`)
- Explicit `source:dest` mounts: trust user's dest path (already targeting container, should be Unix)

### Claude's Discretion
- Exact regex/logic for drive letter detection in mount parsing
- How `build-harness-config-mounts` translates known config directories on Windows
- Whether to extract Docker path utilities into a separate namespace or keep in `run.clj`
- Implementation details of `fs/path` enforcement audit

</decisions>

<specifics>
## Specific Ideas

- Docker Desktop for Windows (WSL2 backend) accepts Windows paths in `-v` flags, so source paths with forward slashes work
- The `/workspace` convention for Windows project mounts should be Windows-only; Unix keeps the current same-path-as-host behavior
- `expand-path` should run its result through `(str (fs/path ...))` to normalize separators after expansion

</specifics>

<deferred>
## Deferred Ideas

None — discussion stayed within phase scope

</deferred>

---

*Phase: 54-path-handling*
*Context gathered: 2026-02-11*
