# Phase 26: Documentation - Context

**Gathered:** 2026-01-25
**Status:** Ready for planning

<domain>
## Phase Boundary

Update README with Codex and Gemini commands, authentication methods, and environment variables. This is focused README updates — comprehensive docs (architecture, troubleshooting, development guide) belong to Phase 27.

</domain>

<decisions>
## Implementation Decisions

### Content depth
- Claude's discretion on detail level (match existing README style)
- Claude's discretion on including tips/gotchas
- Claude's discretion on config.yaml harness_args examples
- Claude's discretion on build flag examples

### Structure/organization
- Claude's discretion on section organization (separate vs unified)
- Claude's discretion on harness ordering
- Claude's discretion on summary table inclusion
- Claude's discretion on env var placement (per-harness vs centralized)

### Authentication presentation
- Present API key and OAuth/interactive auth equally — don't favor one over other
- **Research required:** Investigate each harness's auth flows before documenting
  - Claude Code: Has copy/paste URL flow that works in container
  - Codex: Need to research if similar interactive flow exists
  - Gemini: Need to research if similar interactive flow exists
- Consider that users with existing accounts may prefer OAuth to avoid API key costs
- Claude's discretion on documenting "auth on host, use in container" fallback

### Environment variables format
- Claude's discretion on table vs list format
- Claude's discretion on grouping (by harness vs by purpose)
- Claude's discretion on including example values
- Claude's discretion on including export command examples

### Claude's Discretion
Most formatting and structural decisions deferred to Claude based on existing README style and what provides clearest documentation. The key constraint is research before auth documentation.

</decisions>

<specifics>
## Specific Ideas

- Auth consideration: "For people who already have a harness account (Claude, Codex, Gemini), it is more expensive (or not recommended) to use API key and have extra costs"
- Fallback pattern: "User could install and perform the auth flow on the host and, as the configs are shared, this auth would be valid for the sandbox as well"
- Claude Code reference: Has interactive OAuth flow (copy/paste URL) that works from within container

</specifics>

<deferred>
## Deferred Ideas

None — discussion stayed within phase scope

</deferred>

---

*Phase: 26-documentation*
*Context gathered: 2026-01-25*
