---
phase: 17-validation-polish
plan: 01
subsystem: cli
tags: [babashka, cli, docker, state-management, version-validation]

# Dependency graph
requires:
  - phase: 15-build-command
    provides: build command, state persistence, version validation
  - phase: 14-docker-integration
    provides: build.clj, hash.clj, templates.clj
provides:
  - --force flag for build command (bypass Docker cache)
  - update command (force rebuild with preserved configuration)
  - dockerfile-hash in state.edn for stale image detection
affects: [18-testing, documentation]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "Update command = force rebuild, NOT 'check for updates'"
    - "State preservation during rebuild operations"

key-files:
  created: []
  modified:
    - src/aishell/cli.clj
    - src/aishell/docker/build.clj
    - src/aishell/state.clj

key-decisions:
  - "update command always uses force=true (--no-cache)"
  - "dockerfile-hash stored in state for future stale image detection"
  - "force parameter passed through all run-build call sites"

patterns-established:
  - "Preserved state pattern: read existing state, rebuild with same flags + force"
  - "Version validation: dangerous-chars check then semver format check"

# Metrics
duration: 8min
completed: 2026-01-21
---

# Phase 17 Plan 01: Build Force and Update Summary

**Build --force flag for Docker cache bypass, update command for rebuilding with preserved configuration, and dockerfile-hash storage in state.edn**

## Performance

- **Duration:** 8 min
- **Started:** 2026-01-21T01:43:00Z
- **Completed:** 2026-01-21T01:51:00Z
- **Tasks:** 4 (1 verification, 3 implementation)
- **Files modified:** 3

## Accomplishments
- Verified VAL-01 requirement: existing version validation rejects shell metacharacters and invalid semver
- Added --force flag to build command with --no-cache Docker support
- Implemented update command that rebuilds with preserved configuration (always force)
- Stored dockerfile-hash in state.edn after each build for future stale detection

## Task Commits

Each task was committed atomically:

1. **Task 1: Verify version validation (VAL-01)** - (verification only, no commit)
2. **Task 2: Add --force flag to build** - `10f3268` (feat)
3. **Task 3: Store dockerfile-hash in state.edn** - `1dff39f` (feat)
4. **Task 4: Implement update command** - `2b558f2` (feat)

## Files Created/Modified
- `src/aishell/cli.clj` - Added --force to build-spec, handle-update function, update command dispatch
- `src/aishell/docker/build.clj` - run-build now accepts force? parameter, adds --no-cache when true
- `src/aishell/state.clj` - Updated schema docstring to include :dockerfile-hash

## Decisions Made
- **update = force rebuild:** Per 17-CONTEXT.md, "update" means force rebuild with preserved state, NOT a "check for updates" command
- **Always force for update:** Update command always uses force=true regardless of user opts
- **State preservation:** Update reads existing state and passes same --with-claude/--with-opencode flags

## Deviations from Plan

None - plan executed exactly as written.

## Issues Encountered
- Shell parsing of special characters (`;`, `&`) happens before CLI - tested version validation directly with babashka REPL to confirm dangerous-chars pattern works correctly

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness
- build --force ready for user testing
- update command ready for user testing
- dockerfile-hash stored for future stale image detection (not yet implemented)
- All success criteria met

---
*Phase: 17-validation-polish*
*Completed: 2026-01-21*
