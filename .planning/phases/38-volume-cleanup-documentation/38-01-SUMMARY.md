---
phase: 38-volume-cleanup-documentation
plan: 01
subsystem: cli
tags: [clojure, docker, volume, update-command]

# Dependency graph
requires:
  - phase: 37-build-integration-migration
    provides: Volume lifecycle functions (vol/remove-volume, vol/create-volume, vol/populate-volume)
  - phase: 36-volume-population
    provides: Volume-based harness injection architecture
provides:
  - Update command focused on harness volume refresh (not foundation rebuild)
  - --force flag for opt-in foundation image rebuild
  - Unconditional volume delete + recreate strategy
affects: [38-02, 38-03, documentation]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "Update command follows volume-first workflow (refresh harness tools, foundation rebuild opt-in)"
    - "Delete + recreate for clean slate (no stale state)"

key-files:
  created: []
  modified:
    - src/aishell/cli.clj
    - src/aishell/output.clj

key-decisions:
  - "Update command default behavior: volume refresh only (no foundation rebuild)"
  - "Foundation rebuild is opt-in via --force flag"
  - "Volume repopulation uses unconditional delete + recreate (not check-if-stale)"
  - "No harnesses enabled results in informational message, not error"

patterns-established:
  - "Update workflow: delete volume → create volume → populate volume"
  - "State updates preserve harness config, only update build-time (unless --force adds foundation-hash)"

# Metrics
duration: 4min
completed: 2026-02-01
---

# Phase 38 Plan 01: Redesign Update Command Summary

**Update command redesigned for volume-based architecture: default refreshes harness tools only, --force rebuilds foundation image**

## Performance

- **Duration:** 4 min
- **Started:** 2026-02-01T20:04:08Z
- **Completed:** 2026-02-01T20:07:47Z
- **Tasks:** 1
- **Files modified:** 2

## Accomplishments
- Redesigned `aishell update` to focus on harness volume refresh (delete + recreate)
- Foundation image rebuild moved to opt-in via --force flag
- Added comprehensive help text with print-update-help function
- Shows target harness versions during update
- Handles edge case of no harnesses enabled gracefully

## Task Commits

Each task was committed atomically:

1. **Task 1: Redesign handle-update and update-spec in cli.clj** - `ec54bc5` (feat)

## Files Created/Modified
- `src/aishell/cli.clj` - Redesigned handle-update function with volume-first workflow, added print-update-help, updated update-spec with :force and :help flags
- `src/aishell/output.clj` - Added "volumes" to known-commands set for typo suggestions

## Decisions Made

**Volume repopulation strategy:**
- Chose unconditional delete + recreate over check-if-stale approach
- Rationale: Simpler logic, guarantees clean slate, eliminates edge cases with stale volume state

**Foundation rebuild opt-in:**
- Default behavior: volume refresh only (faster, most common use case)
- --force flag: rebuild foundation AND refresh volume (occasional use for system updates)
- Rationale: Aligns with volume-based architecture where harness updates are independent of foundation

**No-harnesses-enabled handling:**
- Informational message instead of error
- Rationale: Valid state (user may have disabled all harnesses), not a failure condition

**Help text design:**
- Explicit notes about when to use build vs update
- Examples showing both default and --force usage
- Rationale: Clear user guidance on separation of concerns (build = configure, update = refresh)

## Deviations from Plan

None - plan executed exactly as written.

## Issues Encountered

**Symbol resolution ordering:**
- Initial implementation had handle-update defined before print-update-help, causing compilation error
- Resolution: Moved handle-update to after print-update-help (after update-spec definition)
- Impact: None - reorganization was straightforward

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness

- Update command ready for testing and documentation
- Volume lifecycle functions (remove/create/populate) proven in new workflow
- Pattern established for future volume management commands
- Ready for volumes subcommand implementation (38-02)

**Blockers:** None

**Concerns:** None - update command behavior is well-defined and tested via compilation

---
*Phase: 38-volume-cleanup-documentation*
*Completed: 2026-02-01*
