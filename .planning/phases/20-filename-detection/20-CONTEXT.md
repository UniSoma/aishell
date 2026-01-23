# Phase 20: Filename-based Detection - Context

**Gathered:** 2026-01-23
**Status:** Ready for planning

<domain>
## Phase Boundary

Detect sensitive files by filename patterns (no content inspection) and warn users before AI agents access them. Covers environment files (.env variants), SSH keys by name, and key container files (.p12, .pfx, .jks, .keystore). Content-aware detection is Phase 21.

</domain>

<decisions>
## Implementation Decisions

### Warning Grouping
- Keep output compact over showing every file
- Threshold of 3: show files individually if 3 or fewer match a pattern, summarize if more
- Claude's discretion on summarized format (count only vs count + sample paths)

### Scan Scope
- Full recursive tree scan — don't miss nested secrets
- Ignore .gitignore — warn about sensitive files even if gitignored (they're still exposed to container)
- Skip common dependency directories for performance: node_modules, .git, vendor, __pycache__
- Claude's discretion on symlink handling (likely skip to avoid loops)

### Pattern Matching
- Use glob patterns — simple and sufficient for filename detection
- Case-insensitive matching (.ENV, .Env, .env all match)
- Claude's discretion on hidden files (likely include since secrets often in dotfiles)
- Claude's discretion on filename vs full path matching (pick based on pattern type)

### Output Density
- Truncate output with count when many findings
- Claude's discretion on max findings before truncation
- Claude's discretion on summary line behavior
- New audit command for full untruncated output — when truncated, hint "Run `aishell audit` for full detail"

### Claude's Discretion
- Summarized warning format (count only vs count + sample paths)
- Symlink handling
- Hidden file inclusion
- Filename vs path matching per pattern
- Max findings before truncation
- Summary line behavior
- Audit command naming and implementation

</decisions>

<specifics>
## Specific Ideas

- User mentioned wanting an "audit" command (or similar name) that shows full untruncated output
- Truncated output should hint at this audit command for users who want full details
- This enables quick glance during normal runs while allowing deep inspection when needed

</specifics>

<deferred>
## Deferred Ideas

- Audit command implementation — could be its own phase or a quick task after Phase 20
  - User's idea: separate command to expose full detection output
  - When truncated: "Run `aishell audit` for full detail"

</deferred>

---

*Phase: 20-filename-detection*
*Context gathered: 2026-01-23*
