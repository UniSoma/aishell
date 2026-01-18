# Phase 1: Core Container Foundation - Context

**Gathered:** 2026-01-17
**Status:** Ready for planning

<domain>
## Phase Boundary

Working container with project mounted at host path, correct permissions, basic tools. Users can enter an ephemeral container with their project mounted at the exact host path, correct file ownership, and basic CLI tools available.

</domain>

<decisions>
## Implementation Decisions

### CLI invocation
- Command name: `aishell`
- Project detection: Use current working directory (not git root detection)
- Verbosity: Quiet by default, use `-v` for details
- Works on any directory, not just git repositories

### Container startup feedback
- Image build: Show progress indicator (spinner/progress bar) during build
- No "ready" signal — just drop directly into shell prompt
- Quiet operation matches the quiet-by-default CLI philosophy

### Error scenarios
- Simple, clear error messages (not verbose guidance)
- Use ANSI colors when terminal supports it (red for errors, yellow for warnings)
- Directory errors: Clear "Directory not found" or "Permission denied" and exit
- Docker not running: Simple message like "Docker is not running. Please start Docker and try again."

### In-container experience
- Custom shell prompt to indicate user is in container (e.g., `[aishell] ~/project $`)
- Working directory: Start in the mounted project directory (same as host pwd)
- Shell: Bash
- Container auto-exits when shell exits — ephemeral by design

### Claude's Discretion
- Whether to allow docker flag passthrough (--docker-args)
- Slow startup feedback timing and indicator style
- Verbose mode (-v) output content and detail level
- Docker permission denied error handling approach

</decisions>

<specifics>
## Specific Ideas

- Name `aishell` chosen to avoid conflicts with common tools (harness, sandbox, devbox are overloaded)
- Ephemeral containers — no state persists between runs
- Project mounted at exact host path, not /workspace

</specifics>

<deferred>
## Deferred Ideas

None — discussion stayed within phase scope

</deferred>

---

*Phase: 01-core-container-foundation*
*Context gathered: 2026-01-17*
