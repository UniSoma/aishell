# Phase 17: Validation & Polish - Context

**Gathered:** 2026-01-21
**Status:** Ready for planning

<domain>
## Phase Boundary

Security validations, warnings, and update awareness for the container/image. This matches v1.2 hardening features. The `update` command updates the image/container (fetches latest harness versions, forces rebuild), NOT the tool itself.

</domain>

<decisions>
## Implementation Decisions

### Update command
- Force rebuild by default (no check-then-prompt)
- Preserve existing build flags from state.edn (--with-claude, --with-opencode versions)
- Same output as build command (spinner, then success/failure)
- No flags — just `aishell update`

### Version validation
- Already implemented in Phase 15 (cli.clj:23-33)
- Validates semver format (X.Y.Z or X.Y.Z-prerelease) plus "latest"
- Rejects shell metacharacters
- Validation happens at parse time (fail fast)
- No network check for version existence

### Docker security warnings
- Advisory only (warn, don't block)
- Check dangerous patterns in docker_args from config.yaml

### Dockerfile change detection
- Hash comparison: store Dockerfile hash in state.edn, compare on run
- Warn on every run command (shell, claude, opencode)
- Simple message: "Image may be stale. Run 'aishell update' to rebuild."

### Claude's Discretion
- Which docker_args patterns trigger warnings (research v1.2 bash impl + best practices)
- Warning verbosity (one-liner vs with context)
- When to check warnings (config load vs run time)
- Whether Dockerfile change warning is suppressible

</decisions>

<specifics>
## Specific Ideas

- Update = force rebuild with preserved state (not a "check for updates" command)
- Version validation already done — just verify it works in context of this phase

</specifics>

<deferred>
## Deferred Ideas

None — discussion stayed within phase scope

</deferred>

---

*Phase: 17-validation-polish*
*Context gathered: 2026-01-21*
