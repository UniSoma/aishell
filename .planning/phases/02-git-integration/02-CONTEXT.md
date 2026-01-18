# Phase 2: Git Integration - Context

**Gathered:** 2026-01-17
**Status:** Ready for planning

<domain>
## Phase Boundary

Git identity and safe directory configuration work seamlessly inside the container. Users can make git commits with their identity and without ownership warnings.

</domain>

<decisions>
## Implementation Decisions

### Identity source
- Pass git identity as environment variables (GIT_AUTHOR_NAME, GIT_AUTHOR_EMAIL, GIT_COMMITTER_NAME, GIT_COMMITTER_EMAIL)
- Read effective config from project directory (respects local .git/config overrides)
- Always try to read git config, even if project isn't a git repo (fall back to global)

### Safe directory handling
- Always configure safe.directory for the project path (belt-and-suspenders, zero downside)

### Failure behavior
- If git identity not found on host: warn and continue (user can configure inside)
- Simple warning message, no instructions
- Warning shows in normal mode (not just verbose)

### Claude's Discretion
- Which additional git config values to propagate (start with name/email)
- Where to configure safe.directory (entrypoint vs bashrc)
- How to handle "git not installed on host" case

</decisions>

<specifics>
## Specific Ideas

- Environment variables should have the same values as if running git on the host directory
- Effective config means: if project has local .git/config override, use that; otherwise fall back to global

</specifics>

<deferred>
## Deferred Ideas

None — discussion stayed within phase scope

</deferred>

---

*Phase: 02-git-integration*
*Context gathered: 2026-01-17*
