# Phase 8: Explicit Build/Update Commands - Context

**Gathered:** 2026-01-18
**Status:** Ready for planning

<domain>
## Phase Boundary

Separate build and run concerns with explicit subcommands. Add `aishell build` for image creation, `aishell update` for rebuilding with saved flags, and persist build configuration for the update command. Running harnesses requires prior build with appropriate flags.

</domain>

<decisions>
## Implementation Decisions

### State Persistence
- Location: `~/.local/state/aishell/` (XDG standard for runtime state)
- Works consistently on Linux and WSL2
- Per-project files keyed by project identifier
- **Research needed:** Best practices for project identification (path hash vs alternatives)

### Error Messaging
- Colored output respecting NO_COLOR environment variable
- Smart suggestions: when harness is missing, suggest rebuild command or update
- Example: "Claude not in current build. Run: aishell build --with-claude"

### Build Behavior
- Cache by default, `--no-cache` flag for fresh builds
- Progress spinner + summary by default
- Full Docker output with `--verbose` flag
- Empty build allowed: `aishell build` with no flags creates base image only (shell access without harnesses)

### Update Workflow
- Missing state file: Error with guidance ("No previous build found. Run 'aishell build --with-claude' first.")
- Show what's being rebuilt before proceeding: "Rebuilding with: Claude Code, OpenCode..."
- Always uses --no-cache (update means "get latest")
- Accepts additional flags that merge with saved state: `aishell update --with-opencode` adds OpenCode to existing build

### Claude's Discretion
- State file format (shell variables vs JSON)
- Exit code strategy (distinct codes vs simple 0/1)
- Build preview before starting (show what's being installed or not)
- Project identification scheme (pending research confirmation)

</decisions>

<specifics>
## Specific Ideas

- Update semantically means "get latest" — should feel different from build
- User expects `update` to actually fetch newer versions, hence no-cache default
- Merging flags on update allows incremental capability addition without losing existing config

</specifics>

<deferred>
## Deferred Ideas

None — discussion stayed within phase scope

</deferred>

---

*Phase: 08-explicit-build-update-commands*
*Context gathered: 2026-01-18*
