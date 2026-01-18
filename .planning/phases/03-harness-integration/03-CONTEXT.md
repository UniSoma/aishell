# Phase 3: Harness Integration - Context

**Gathered:** 2026-01-17
**Status:** Ready for planning

<domain>
## Phase Boundary

Enable running Claude Code and OpenCode harnesses inside the container with their configurations mounted. Users invoke harnesses via subcommands (`aishell claude`, `aishell opencode`) or enter a shell with bare `aishell`. Harnesses read existing configs from mounted home directories.

</domain>

<decisions>
## Implementation Decisions

### Invocation interface
- Subcommand pattern: `aishell claude`, `aishell opencode`, `aishell` (shell)
- Arguments after harness name pass through to the harness (e.g., `aishell claude --help` runs `claude --help` inside)
- Bare `aishell` enters shell (implicit, no explicit `aishell shell` required)

### Config mounting
- Research which directories each harness needs for smooth experience (Claude's discretion)
- Mount directories read-write so harnesses can modify their configs
- Both mounted config files AND environment variables available for API keys
- Pass common env vars (ANTHROPIC_API_KEY, OPENAI_API_KEY, etc.) from host to container

### Installation approach
- Harnesses pre-installed in Docker image (faster startup)
- Latest versions at image build time by default
- Version override flags available: `--claude-version`, `--opencode-version` when building
- `aishell update` command to rebuild image with latest harness versions
- Node.js LTS for Claude Code runtime

### Tool availability
- Both harnesses optional (neither included by default)
- User opts in via build-time flags: `--with-claude`, `--with-opencode`
- Minimal base image; harnesses added when explicitly requested

### Claude's Discretion
- Help interface design (help subcommand vs --help flag vs both)
- Exact directories to mount for each harness (research what they need)
- Error UX when user runs unavailable harness (error message vs prompt to install)
- Which environment variables to pass through for each harness

</decisions>

<specifics>
## Specific Ideas

- User wants ability to override harness versions at build time for reproducibility
- Update command should make getting new harness versions easy without manual Docker commands

</specifics>

<deferred>
## Deferred Ideas

None — discussion stayed within phase scope

</deferred>

---

*Phase: 03-harness-integration*
*Context gathered: 2026-01-17*
