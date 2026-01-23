# Phase 22: Gitleaks Integration - Context

**Gathered:** 2026-01-23
**Status:** Ready for planning

<domain>
## Phase Boundary

Content-based secret scanning inside the container. Users can run `aishell gitleaks` for deep scanning, and see freshness warnings when scan is stale. This phase adds gitleaks to the base image, creates the command, and implements staleness tracking/warning.

</domain>

<decisions>
## Implementation Decisions

### Scan output & feedback
- Show progress while scanning (helpful for large repos)
- Default to working tree scan, `--history` flag for full git history scan
- Claude's discretion: output format (direct terminal vs summary+file) and exit code behavior

### Command interface
- Full passthrough of flags to underlying gitleaks binary — power user flexibility
- Auto-detect `.gitleaks.toml` if present in project root, otherwise use gitleaks defaults
- Claude's discretion: whether to auto-detect `.gitleaks-baseline.json` for baseline support

### Freshness warning UX
- Warning includes actionable command: "Run `aishell gitleaks` to scan"
- Default threshold: 7 days before warning appears
- User can disable freshness warning via `.aishell/config.yaml`
- Claude's discretion: warning prominence level (consistent with existing warning hierarchy)

### State persistence
- Store last-scan timestamp in XDG state directory (`~/.local/state/aishell/`) keyed by project path
- Only update timestamp on completed scan (ran to completion, regardless of findings)
- No repo pollution — state is personal, not project config

### Claude's Discretion
- Output format choice (terminal streaming vs file report)
- Exit code semantics (non-zero on findings or always zero)
- Warning prominence relative to file detection warnings
- Whether to auto-detect baseline file

</decisions>

<specifics>
## Specific Ideas

- Scan state is personal, not project config — fresh clone should prompt "hey, scan this"
- Pattern follows existing CLI tools: config in repo (.aishell/), state in XDG
- Gitleaks runs inside container without executing pre_start hooks (per success criteria)

</specifics>

<deferred>
## Deferred Ideas

None — discussion stayed within phase scope

</deferred>

---

*Phase: 22-gitleaks-integration*
*Context gathered: 2026-01-23*
