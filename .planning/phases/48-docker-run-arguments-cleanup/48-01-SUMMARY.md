---
phase: 48-docker-run-arguments-cleanup
plan: 01
subsystem: docker
tags: [docker, clojure, babashka, tmux-removal, v3.0.0]

# Dependency graph
requires:
  - phase: 47-state-config-schema-cleanup
    provides: "Removed tmux from CLI, state, and config schemas"
provides:
  - "Docker run arguments without tmux-related functions or environment variables"
  - "skip-interactive parameter (renamed from skip-tmux) controlling interactive features"
affects: [49-docker-templates-cleanup, docker-operations, entrypoint]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "skip-interactive parameter controls all interactive features (harness aliases)"

key-files:
  created: []
  modified:
    - src/aishell/docker/run.clj
    - src/aishell/run.clj

key-decisions:
  - "Renamed skip-tmux to skip-interactive to reflect broader purpose (controls harness aliases)"
  - "Removed WITH_TMUX env var, tmux config mounts, and resurrect mounts from container runtime"

patterns-established:
  - "Docker run argument construction no longer references tmux infrastructure"

# Metrics
duration: 2min
completed: 2026-02-06
---

# Phase 48 Plan 01: Docker Run Arguments Cleanup Summary

**Removed tmux functions and environment variables from Docker run argument construction, renamed skip-tmux to skip-interactive**

## Performance

- **Duration:** 2 min
- **Started:** 2026-02-06T12:39:17Z
- **Completed:** 2026-02-06T12:41:17Z
- **Tasks:** 2
- **Files modified:** 2

## Accomplishments
- Deleted 4 tmux-related functions: `user-mounted-tmux-config?`, `build-tmux-config-mount`, `build-resurrect-mount`, `build-resurrect-env-args`
- Removed 4 tmux cond-> blocks from `build-docker-args-internal` (config mount, resurrect mount, WITH_TMUX env var, resurrect env args)
- Preserved harness aliases cond-> block with skip-interactive rename
- Renamed skip-tmux parameter to skip-interactive throughout docker/run.clj and run.clj
- All namespaces load cleanly in Babashka after cleanup

## Task Commits

Each task was committed atomically:

1. **Task 1: Delete tmux functions and cond-> blocks from docker/run.clj** - `37d1ff6` (refactor)
2. **Task 2: Update skip-tmux to skip-interactive in caller (run.clj)** - `db06d65` (refactor)

## Files Created/Modified
- `src/aishell/docker/run.clj` - Removed tmux functions and cond-> blocks, renamed skip-tmux to skip-interactive
- `src/aishell/run.clj` - Updated build-docker-args call to use skip-interactive parameter

## Decisions Made
- Renamed skip-tmux to skip-interactive to better reflect its purpose: controlling interactive features (harness aliases) rather than just tmux
- Kept only the harness aliases cond-> block (lines 273-275 after edits), which skips aliases when skip-interactive is true

## Deviations from Plan

None - plan executed exactly as written.

## Issues Encountered

None

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness

Ready for Phase 48 Plan 02 (Docker templates cleanup):
- Docker run arguments no longer pass WITH_TMUX env var
- Entrypoint should handle missing WITH_TMUX gracefully (Phase 49)
- Tmux config mounts and resurrect mounts eliminated from container runtime
- skip-interactive parameter now controls harness aliases

---
*Phase: 48-docker-run-arguments-cleanup*
*Completed: 2026-02-06*

## Self-Check: PASSED

All files and commits verified:
- src/aishell/docker/run.clj: FOUND
- src/aishell/run.clj: FOUND
- Commit 37d1ff6: FOUND
- Commit db06d65: FOUND
