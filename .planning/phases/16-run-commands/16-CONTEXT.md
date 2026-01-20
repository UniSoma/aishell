# Phase 16: Run Commands - Context

**Gathered:** 2026-01-20
**Status:** Ready for planning

<domain>
## Phase Boundary

Let users enter an interactive shell or launch harnesses (claude, opencode) inside ephemeral Docker containers with project mounting and configuration support. Users can run `./aishell` for shell, `./aishell claude` for Claude Code, or `./aishell opencode` for OpenCode. Project is mounted at same path as host, git identity is available, and containers are destroyed on exit.

</domain>

<decisions>
## Implementation Decisions

### Container lifecycle
- Auto-remove always — container destroyed immediately on exit, no cleanup needed
- Ctrl+C passes to process inside container (standard terminal behavior)
- Multiple containers can run in parallel for different projects (no orphan detection)
- Let Docker generate container names (no project-based naming)

### Config loading
- Project-first lookup: `.aishell/config.yaml` in project directory
- Global fallback: `~/.aishell/config.yaml` if project config missing
- No config: Just use base image if neither exists
- Fail fast on invalid syntax or unknown keys — show error, don't run container
- Validate mount paths exist on host before starting container
- PRE_START behavior matches bash impl: runs inside container in background via entrypoint, output to `/tmp/pre-start.log`

### Harness launching
- Docker-compose style env vars: `VAR` (passthrough from host) or `VAR=value` (literal)
- All extra arguments pass through to harness (e.g., `./aishell claude --model opus`)
- Clear error message if harness not installed: "Claude Code not installed. Run: aishell build --with-claude"

### Shell experience
- Default shell: bash (simple, always present)
- Working directory: same path as host (e.g., /home/user/myproject inside container)
- Custom prompt: `[aishell] $` to indicate you're in aishell

### Claude's Discretion
- Harness mismatch detection (check state.edn vs let container fail)
- Exact entrypoint script structure
- TTY detection and handling
- Signal forwarding details

</decisions>

<specifics>
## Specific Ideas

- "Docker-compose way of specifying env vars" — user prefers VAR (passthrough) or VAR=value (literal) style over hardcoded lists
- PRE_START must match bash implementation exactly — background execution, logged to /tmp/pre-start.log

</specifics>

<deferred>
## Deferred Ideas

None — discussion stayed within phase scope

</deferred>

---

*Phase: 16-run-commands*
*Context gathered: 2026-01-20*
