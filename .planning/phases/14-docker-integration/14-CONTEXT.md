# Phase 14: Docker Integration - Context

**Gathered:** 2026-01-20
**Status:** Ready for planning

<domain>
## Phase Boundary

Provide Docker operations that build and run commands depend on: daemon availability checks, image building with caching, and per-project Dockerfile extension. The build command workflow and run commands are separate phases.

</domain>

<decisions>
## Implementation Decisions

### Error messaging
- Minimal errors for Docker daemon unavailable: "Docker not running" (no platform-specific hints)
- When no image built: error with hint "No image built. Run: aishell build"
- Use colors for errors (red) and warnings (yellow) when terminal supports it, matching Phase 13 color detection

### Build output
- Default: progress indicator only (spinner), then completion stats
- Completion message includes stats: "Built aishell:abc123 (45s, 1.2GB)"
- --verbose flag: show full Docker output including layer info
- --quiet flag: suppress all output except errors (for CI/scripting)

### Cache behavior
- Cache invalidation based on Dockerfile content hash
- --force flag to bypass cache and force rebuild
- Cache hash storage location: defer to research (state file vs image label)
- Cache hit feedback: defer to research (silent vs brief message)

### Dockerfile extension
- Keep current bash version behavior: project `.aishell/Dockerfile` uses `FROM aishell:base` and is built with project dir as context
- Silent when no project Dockerfile (just use base image)
- Auto-rebuild extended image when base image changes (track base image ID in label)

### Claude's Discretion
- Docker error output handling (pass through vs summarize)
- Verbose mode layer caching detail level
- Project Dockerfile syntax error presentation

</decisions>

<specifics>
## Specific Ideas

- Match current bash version behavior for Dockerfile extension (multi-stage FROM pattern)
- Exit codes should work in CI pipelines (non-zero on failure)

</specifics>

<deferred>
## Deferred Ideas

None — discussion stayed within phase scope

</deferred>

---

*Phase: 14-docker-integration*
*Context gathered: 2026-01-20*
