---
phase: 08-explicit-build-update-commands
plan: 02
subsystem: cli
tags: [bash, docker, state-management, subcommands]

# Dependency graph
requires:
  - phase: 08-01
    provides: state management infrastructure (read/write state files)
provides:
  - build subcommand with state persistence
  - update subcommand with state merging
  - error handling for missing builds
affects: [08-03, 08-04]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "Explicit build before run pattern"
    - "State-driven update with flag merging"
    - "errexit-compatible return handling"

key-files:
  created: []
  modified:
    - aishell

key-decisions:
  - "Build subcommand always runs docker build (no caching logic, uses Docker cache)"
  - "Update merges new flags with existing (additive, not replacement)"
  - "Update always uses --no-cache (semantically means get latest)"
  - "Stop spinner only clears line when spinner was active"

patterns-established:
  - "errexit-compatible pattern: cmd || result=$?"
  - "Subcommand-specific arg parsing within function"

# Metrics
duration: 5min
completed: 2026-01-18
---

# Phase 8 Plan 2: Build Command Implementation Summary

**Explicit build and update subcommands with state persistence, removing auto-build from run commands**

## Performance

- **Duration:** 5 min
- **Started:** 2026-01-18T18:24:09Z
- **Completed:** 2026-01-18T18:28:52Z
- **Tasks:** 3
- **Files modified:** 1

## Accomplishments

- Implemented `aishell build` subcommand with local arg parsing and state persistence
- Refactored `do_update` to load state, merge flags, and always use --no-cache
- Removed auto-build behavior from run commands (shell, claude, opencode)
- Added helpful error messages when build is missing or image was deleted

## Task Commits

Each task was committed atomically:

1. **Task 1: Implement do_build subcommand** - `14859ca` (feat)
2. **Task 2: Refactor do_update to use state file** - `bd3c188` (feat)
3. **Task 3: Remove auto-build from run commands** - `5d2d2dd` (feat)

## Files Created/Modified

- `aishell` - Added do_build(), refactored do_update(), added verify_build_exists(), handle_extension(), removed ensure_image() and ensure_image_with_extension()

## Decisions Made

1. **Build always runs docker build** - No internal caching logic; rely on Docker's layer cache. User can pass --no-cache if needed.

2. **Update merges flags additively** - Running `aishell update --with-opencode` when Claude was already in build adds OpenCode, doesn't remove Claude.

3. **Update always uses --no-cache** - The semantic of "update" is "get latest versions", so no-cache is always appropriate.

4. **Stop spinner conditional clear** - Only clear terminal line when a spinner was actually running, preventing output from being cleared on error exit.

5. **errexit-compatible return handling** - Use `cmd || result=$?` pattern instead of `cmd; result=$?` to prevent `set -e` from exiting on non-zero returns.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] Stop spinner clearing output on error**
- **Found during:** Task 3 verification
- **Issue:** EXIT trap ran stop_spinner which cleared terminal line, hiding error messages
- **Fix:** Changed stop_spinner to only printf when spinner_pid is set
- **Files modified:** aishell
- **Verification:** Error messages now display correctly
- **Committed in:** 5d2d2dd (Task 3 commit)

**2. [Rule 3 - Blocking] errexit causing premature exit**
- **Found during:** Task 3 verification
- **Issue:** `verify_build_exists` returning 1 caused script to exit due to `set -e`
- **Fix:** Changed to `verify_build_exists "$project_dir" || verify_result=$?` pattern
- **Files modified:** aishell
- **Verification:** Script now properly handles non-zero returns
- **Committed in:** 5d2d2dd (Task 3 commit)

---

**Total deviations:** 2 auto-fixed (1 bug, 1 blocking)
**Impact on plan:** Both fixes were necessary for correct operation. No scope creep.

## Issues Encountered

None beyond the auto-fixed issues above.

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness

- Build and update commands fully functional
- State persistence working with merge semantics
- Ready for Plan 3: harness verification (checking if requested harness was in build)
- Ready for Plan 4: full integration testing

---
*Phase: 08-explicit-build-update-commands*
*Completed: 2026-01-18*
