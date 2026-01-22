# Phase 19: Core Detection Framework - Context

**Gathered:** 2026-01-22
**Status:** Ready for planning

<domain>
## Phase Boundary

Framework that warns users about sensitive files with severity tiers (high/medium/low) when running aishell commands. Warnings are advisory — user can always proceed. Specific file patterns (env files, SSH keys, etc.) are implemented in later phases.

</domain>

<decisions>
## Implementation Decisions

### Warning display
- Group warnings by severity: high first, then medium, then low
- Use colors: red for high, yellow for medium, blue/dim for low
- Compact format: one line per file with severity + path + brief reason
- Clear header banner: "⚠️ Sensitive files detected" with visual separator

### Severity presentation
- Emoji + text labels: 🔴 HIGH, 🟡 MEDIUM, 🟢 LOW
- High-severity warnings get extra visual emphasis (bold or spacing)
- Low-severity items visually subdued (dimmer/gray) — informational, not alarming
- User-friendly reasons: "May contain passwords", "Private key file", etc.

### User flow
- For high-severity warnings: require explicit y/n confirmation before proceeding
- For medium/low only: display and continue automatically
- `--unsafe` flag suppresses all warnings (for CI/automation that knows the risks)
- User always proceeds on 'y' — never blocked, just informed

### Edge cases
- No sensitive files: silent — proceed directly without message
- Many files (20+): truncate list, show "...and N more" with option to expand
- Show spinner/dots indicator while scanning
- Always display by default, `--unsafe` is explicit opt-out

### Claude's Discretion
- Exact timing of when warnings appear in command flow
- Handling of unreadable files (permissions, broken symlinks)
- Exact truncation threshold and expansion mechanism
- Specific visual styling details (spacing, borders, etc.)

</decisions>

<specifics>
## Specific Ideas

- Flag named `--unsafe` rather than `--no-warn` — clearly signals bypassing safety
- High-severity confirmation uses y/n choice, not just "press any key"
- Follows existing aishell security warning visual pattern

</specifics>

<deferred>
## Deferred Ideas

None — discussion stayed within phase scope

</deferred>

---

*Phase: 19-core-framework*
*Context gathered: 2026-01-22*
