---
phase: 26-documentation
plan: 01
subsystem: documentation
tags: [readme, authentication, environment-variables, codex, gemini]

# Dependency graph
requires:
  - phase: 25-cli-runtime
    provides: "Codex CLI and Gemini CLI harness implementations"
provides:
  - "README.md with complete multi-harness documentation"
  - "Authentication documentation for all 4 harnesses (Claude Code, OpenCode, Codex CLI, Gemini CLI)"
  - "Environment variables table organized by category (harness-specific, cloud provider, other)"
affects: [27-comprehensive-docs, user-onboarding]

# Tech tracking
tech-stack:
  added: []
  patterns: []

key-files:
  created: []
  modified: [README.md]

key-decisions:
  - "Present OAuth and API key options equally - don't favor one over other per RESEARCH.md"
  - "Organize environment variables by category: Harness-Specific Keys, Cloud Provider Credentials, Other"
  - "Document harness-specific authentication nuances (device auth, container limitations)"

patterns-established:
  - "Authentication section structure: Option 1 (OAuth/Interactive) then Option 2 (API Key)"
  - "Environment variables organized by purpose rather than alphabetically"

# Metrics
duration: 3min
completed: 2026-01-25
---

# Phase 26 Plan 01: Documentation Summary

**README.md updated with Codex CLI and Gemini CLI commands, authentication methods for all harnesses, and comprehensive environment variables table**

## Performance

- **Duration:** 3 min
- **Started:** 2026-01-25T12:34:18Z
- **Completed:** 2026-01-25T12:36:25Z
- **Tasks:** 3
- **Files modified:** 1

## Accomplishments
- Added `aishell codex` and `aishell gemini` commands to README with build and run examples
- Documented authentication methods for all 4 harnesses with balanced OAuth/API key presentation
- Created comprehensive environment variables table with all 16 passthrough variables from src/aishell/docker/run.clj

## Task Commits

Each task was committed atomically:

1. **Task 1: Update build and run examples with codex/gemini commands** - `b0b0cb6` (docs)
2. **Task 2: Add authentication section documenting auth methods per harness** - `fa271ab` (docs)
3. **Task 3: Update Environment Variables section with complete harness-specific table** - `65ddcf8` (docs)

## Files Created/Modified
- `README.md` - Added Codex CLI and Gemini CLI documentation across Usage, Authentication, and Environment Variables sections

## Decisions Made

**Authentication presentation approach:**
- Presented OAuth and API key options equally per RESEARCH.md guidance
- Documented harness-specific nuances:
  - Claude Code: Copy-paste URL OAuth works in container
  - Codex: Device auth flag for headless environments
  - Gemini: No device code flow, requires auth on host or API key
- Emphasized config directory mounting for credential persistence

**Environment variables organization:**
- Organized by category (Harness-Specific Keys, Cloud Provider Credentials, Other) rather than alphabetically
- Referenced authoritative list from src/aishell/docker/run.clj (lines 149-166)
- Included usage notes for each variable (which harnesses, special requirements)

**Documentation structure:**
- Added Authentication section before Environment Variables
- Maintained existing README formatting (code blocks, table structure, comment style)

## Deviations from Plan

None - plan executed exactly as written.

## Issues Encountered

None.

## Next Phase Readiness

README.md is now complete for v2.4.0 Multi-Harness Support milestone. Users can discover and use all 4 harnesses (Claude Code, OpenCode, Codex CLI, Gemini CLI).

Next phase (27-comprehensive-docs) can build on this foundation with:
- Architecture documentation
- Troubleshooting guides
- Development documentation
- Migration guides

No blockers or concerns.

---
*Phase: 26-documentation*
*Completed: 2026-01-25*
