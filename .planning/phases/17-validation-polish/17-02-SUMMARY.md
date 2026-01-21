---
phase: 17-validation-polish
plan: 02
subsystem: validation
tags: [security, docker, warning, stale-detection]

# Dependency graph
requires:
  - phase: 17-01
    provides: dockerfile-hash in state.edn, update command
  - phase: 14-02
    provides: hash/compute-hash function
  - phase: 14-03
    provides: templates/base-dockerfile embedded content
provides:
  - Security validation module for dangerous docker_args patterns
  - Stale image detection comparing dockerfile hash
  - Advisory warnings without blocking execution
affects: [18-production-release]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "Advisory warnings (warn but continue execution)"
    - "Regex patterns for flexible matching"

key-files:
  created:
    - src/aishell/validation.clj
  modified:
    - src/aishell/run.clj

key-decisions:
  - "Both string and regex patterns for flexibility in dangerous-patterns"
  - "Advisory warnings only - never block execution"

patterns-established:
  - "validation module: separate module for security checks"
  - "check- function returns data, warn- function displays it"

# Metrics
duration: 2min
completed: 2026-01-21
---

# Phase 17 Plan 02: Security Warnings Summary

**Dangerous docker_args pattern detection and stale image warnings with advisory-only output**

## Performance

- **Duration:** 2 min
- **Started:** 2026-01-21T01:52:39Z
- **Completed:** 2026-01-21T01:54:29Z
- **Tasks:** 2
- **Files modified:** 2

## Accomplishments
- Created validation.clj module with dangerous pattern checking (--privileged, docker.sock, --cap-add, apparmor/seccomp)
- Added stale image detection comparing stored vs current Dockerfile hash
- Integrated both checks into run.clj before container execution
- All warnings are advisory only - execution continues after warnings

## Task Commits

Each task was committed atomically:

1. **Task 1: Create validation.clj with dangerous pattern checking** - `c899c44` (feat)
2. **Task 2: Add stale image detection and security warnings to run.clj** - `1df73b9` (feat)

## Files Created/Modified
- `src/aishell/validation.clj` - Security validation module with check-dangerous-args and warn-dangerous-args
- `src/aishell/run.clj` - Added stale image check and dangerous args warning integration

## Decisions Made
- Used both string patterns (simple contains) and regex patterns (for OR cases like SYS_ADMIN|ALL)
- check-dangerous-args returns seq for testability, warn-dangerous-args handles display
- Stale check runs before security warnings (stale is more immediately actionable)

## Deviations from Plan

None - plan executed exactly as written.

## Issues Encountered

None - all implementations worked as expected.

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness
- Security validation foundation complete
- Ready for Phase 18 production release
- All v2.0 Babashka functionality complete

---
*Phase: 17-validation-polish*
*Completed: 2026-01-21*
