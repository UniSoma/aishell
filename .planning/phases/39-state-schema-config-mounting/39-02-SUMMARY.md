---
phase: 39-state-schema-config-mounting
plan: 02
subsystem: infra
tags: [docker, config-mounting, tmux, state-management, babashka]

# Dependency graph
requires:
  - phase: 39-01
    provides: :with-tmux state persistence and tmux config schema
provides:
  - Conditional read-only mount of ~/.tmux.conf when tmux enabled
  - Collision detection to prevent duplicate mounts
  - Graceful handling of missing ~/.tmux.conf
affects:
  - 39-03 (needs tmux installation logic to complete runtime integration)
  - 40-tmux-bootstrap (benefits from mounted user tmux config)

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "Conditional docker mount based on state flags"
    - "Read-only config file mounting for safety"
    - "Collision detection for user-explicit vs auto-mounts"
    - "Graceful file existence checking with fs/exists?"

key-files:
  created: []
  modified:
    - src/aishell/docker/run.clj
    - src/aishell/run.clj

key-decisions:
  - "Mount ~/.tmux.conf read-only to prevent container modification"
  - "Auto-mount skipped if user has explicit .tmux.conf in config mounts"
  - "Missing ~/.tmux.conf returns empty mount vector (no error/warning)"
  - "State threaded through build-docker-args for tmux mount decision"

patterns-established:
  - "Helper function for collision detection (user-mounted-tmux-config?)"
  - "Build-mount functions return empty vector when condition not met"
  - "State parameter added to docker args internal functions"

# Metrics
duration: 1.5min
completed: 2026-02-02
---

# Phase 39 Plan 02: Conditional Tmux Config Mount Summary

**Conditional read-only mounting of ~/.tmux.conf based on :with-tmux state flag with collision detection**

## Performance

- **Duration:** 1.5 min
- **Started:** 2026-02-02T00:37:32Z
- **Completed:** 2026-02-02T00:39:04Z
- **Tasks:** 2
- **Files modified:** 2

## Accomplishments
- user-mounted-tmux-config? helper detects explicit .tmux.conf mounts in config
- build-tmux-config-mount function mounts ~/.tmux.conf:ro when conditions met
- Mount only occurs when :with-tmux true AND file exists AND not user-mounted
- Returns empty vector when conditions not met (no error on missing file)
- State threaded from run.clj through docker/run.clj for tmux mount decision
- Placement before config mounts allows user overrides

## Task Commits

Each task was committed atomically:

1. **Task 1: Add conditional tmux config mount to docker/run.clj** - `bc67423` (feat)
2. **Task 2: Thread state through callers for tmux mount** - `a11d10d` (feat)

## Files Created/Modified
- `src/aishell/docker/run.clj` - Added user-mounted-tmux-config?, build-tmux-config-mount, :state parameter
- `src/aishell/run.clj` - Threaded :state to build-docker-args and build-docker-args-for-exec calls

## Decisions Made

**Read-only mount for safety**
- Host ~/.tmux.conf mounted with :ro flag
- Prevents container from accidentally modifying host config
- User can still modify via host editor, changes visible on next container start
- Rationale: Config files should flow host → container, not reverse

**Collision detection prevents duplicate mounts**
- user-mounted-tmux-config? checks config :mounts for ".tmux.conf" substring
- Auto-mount skipped if user has explicit mount
- Prevents docker error: "duplicate mount point"
- Rationale: User-explicit mounts take precedence

**Graceful handling of missing file**
- fs/exists? check before adding mount
- Returns empty vector if file missing (no error, no warning)
- Container starts successfully without tmux config
- Rationale: Not all users have ~/.tmux.conf, should not block execution

**State threading architecture**
- State passed through build-docker-args → build-docker-args-internal
- Required for conditional mount decision based on :with-tmux flag
- Also passed to build-docker-args-for-exec for consistency
- Rationale: Mount behavior should be consistent across run modes

## Deviations from Plan

None - plan executed exactly as written.

## Issues Encountered

None.

## User Setup Required

None - feature is opt-in via --with-tmux build flag from Plan 01.

## Next Phase Readiness

Ready for Phase 39-03 (tmux installation in Dockerfile).

**Blocked by:** None

**Concerns:** None

**Delivered:**
- CONF-01 implemented: User tmux config mounted when opt-in flag set
- CONF-02 implemented: Missing ~/.tmux.conf handled gracefully
- CONF-03 implemented: Collision detection prevents duplicate mounts
- Mount is read-only for safety
- State-based conditional mounting architecture established

---
*Phase: 39-state-schema-config-mounting*
*Completed: 2026-02-02*
