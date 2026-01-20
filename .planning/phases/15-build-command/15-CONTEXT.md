# Phase 15: Build Command - Context

**Gathered:** 2026-01-20
**Status:** Ready for planning

<domain>
## Phase Boundary

Users can build their sandbox environment with harness version pinning. The build command creates Docker images with optional harness installations (Claude Code, OpenCode). State persistence allows subsequent commands to know what was built.

</domain>

<decisions>
## Implementation Decisions

### Build output & feedback
- Spinner while building, then summary on success
- Success summary shows: image name, size, duration, installed harness versions
- `--verbose` flag shows full docker build output
- On error: show last N lines of docker output + suggest running with --verbose for full output

### Flag persistence behavior
- Flags persisted in `~/.aishell/state.edn` (global, not per-project)
- `aishell build [flags]` always resets state — flags passed are what gets persisted
- `aishell build` with no flags = base image only, clears any previous flags
- `aishell update` rebuilds using previously persisted flags (separate command, Phase 17)

### Version pinning UX
- Single flag design: `--with-claude` (latest) or `--with-claude=1.0.0` (pinned)
- Same pattern for `--with-opencode`
- No separate `--claude-version` flag needed
- Accept `--with-claude=latest` as explicit "latest" (equivalent to `--with-claude`)
- Strict semver validation at parse time (reject invalid formats before build starts)
- Security: reject version strings with shell metacharacters

### Rebuild triggers
- `aishell build` always rebuilds (no aishell-level caching)
- No `--force` flag needed (would be redundant)
- Show "Replacing existing image..." when image already exists

### Claude's Discretion
- Whether to warn when `aishell build` with no flags clears previously persisted flags
- Whether to use Docker's layer cache (`--no-cache` or not)
- Exact number of lines to show on error (10-20 lines)
- Loading skeleton / progress indicator design details

</decisions>

<specifics>
## Specific Ideas

- Mental model: `build` is explicit (what you pass is what you get), `update` is implicit (reapply existing)
- Feel like a focused CLI tool — not chatty, but informative when something happens

</specifics>

<deferred>
## Deferred Ideas

None — discussion stayed within phase scope

</deferred>

---

*Phase: 15-build-command*
*Context gathered: 2026-01-20*
