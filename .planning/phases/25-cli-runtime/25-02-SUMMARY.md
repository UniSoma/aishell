---
phase: 25-cli-runtime
plan: 02
subsystem: cli
tags: [clojure, babashka, cli, dispatch, harness]

# Dependency graph
requires:
  - phase: 24-dockerfile-build-infrastructure
    provides: Codex and Gemini Dockerfile build support
provides:
  - CLI dispatch for codex and gemini commands
  - Harness verification for codex and gemini
  - Container command construction for codex and gemini
  - Config validation for harness_args.codex and harness_args.gemini
  - Typo suggestions for codex and gemini commands
affects:
  - phase-26-testing

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "Harness dispatch pattern extended for new harnesses"

key-files:
  created: []
  modified:
    - src/aishell/cli.clj
    - src/aishell/run.clj
    - src/aishell/config.clj
    - src/aishell/output.clj

key-decisions:
  - "Codex and Gemini use simple pass-through like opencode (no special flags)"

patterns-established:
  - "New harness addition pattern: cli.clj dispatch, run.clj verify+cmd, config.clj known-harnesses, output.clj known-commands"

# Metrics
duration: 2min
completed: 2026-01-25
---

# Phase 25 Plan 02: CLI Dispatch & Runtime Summary

**CLI dispatch and runtime support for Codex and Gemini with harness verification and argument pass-through**

## Performance

- **Duration:** 2 min
- **Started:** 2026-01-25T08:30:00Z
- **Completed:** 2026-01-25T08:32:00Z
- **Tasks:** 3
- **Files modified:** 4

## Accomplishments
- Added codex and gemini commands to CLI dispatch
- Implemented harness verification with correct error messages
- Updated help text to show codex and gemini commands
- Added config validation for harness_args.codex and harness_args.gemini

## Task Commits

Each task was committed atomically:

1. **Task 1: Add codex and gemini to CLI dispatch and help** - `85246fb` (feat)
2. **Task 2: Add codex and gemini harness verification and commands** - `435b19e` (feat)
3. **Task 3: Add codex and gemini to known-harnesses and known-commands** - `7cb5d58` (feat)

## Files Created/Modified
- `src/aishell/cli.clj` - CLI dispatch routes and help text for codex/gemini
- `src/aishell/run.clj` - Harness verification and container command construction
- `src/aishell/config.clj` - known-harnesses set includes codex/gemini
- `src/aishell/output.clj` - known-commands set includes codex/gemini

## Decisions Made
- Codex and Gemini use simple pass-through like opencode (no special flags like Claude's --dangerously-skip-permissions)

## Deviations from Plan

None - plan executed exactly as written.

## Issues Encountered

None.

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness
- CLI and runtime support complete for all four harnesses
- Ready for Phase 26 Testing

---
*Phase: 25-cli-runtime*
*Completed: 2026-01-25*
