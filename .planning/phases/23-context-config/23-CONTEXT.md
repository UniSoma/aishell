# Phase 23: Context & Configuration - Context

**Gathered:** 2026-01-23
**Status:** Ready for planning

<domain>
## Phase Boundary

Customize filename detection behavior and highlight unprotected sensitive files. Users can add custom patterns, allowlist false positives, and see extra warnings when high-severity files are not in .gitignore. Does NOT add new detection types or change core detection logic.

</domain>

<decisions>
## Implementation Decisions

### Gitignore emphasis
- Check gitignore status for high-severity files only (not medium/low)
- Append "(risk: may be committed)" to warnings for files not in .gitignore
- If project has no .gitignore file, treat all files as unprotected
- Severity stays the same (high remains high) — just add contextual text

### Custom pattern format
- Support both simple filenames and glob patterns
- User specifies severity per-pattern (high/medium/low)
- Map format in config: `pattern: {severity: high, description: 'API keys'}`
- Custom patterns extend default patterns (never replace)

### Allowlist behavior
- Support both exact file paths and glob patterns
- Reason/comment is required for each allowlist entry (audit trail)
- File-level only — cannot allowlist entire categories
- Allowlisted files are completely hidden from output (no "allowed" status shown)

### Config structure
- Both global (~/.aishell/config.yaml) and project-level supported
- Project config extends global (allowlist entries merge, patterns merge)
- Global kill switch: `enabled: true/false` to disable all filename detection

### Claude's Discretion
- Exact key naming within config (detection vs security namespace)
- Whether description is optional for custom patterns (pragmatic: make it optional)
- Gitignore parsing implementation details

</decisions>

<specifics>
## Specific Ideas

- "(risk: may be committed)" wording chosen for its focus on consequence rather than just observation
- Required reasons for allowlist entries creates audit trail — good for teams and future self

</specifics>

<deferred>
## Deferred Ideas

None — discussion stayed within phase scope

</deferred>

---

*Phase: 23-context-config*
*Context gathered: 2026-01-23*
