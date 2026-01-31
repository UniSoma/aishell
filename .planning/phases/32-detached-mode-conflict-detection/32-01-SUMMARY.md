---
phase: 32-detached-mode-conflict-detection
plan: 01
subsystem: infra
tags: [docker, tmux, entrypoint, session-management]

# Dependency graph
requires:
  - phase: 31-dockerfile-image-build
    provides: Base Dockerfile with tmux package installed
provides:
  - Entrypoint script auto-starts all container commands in tmux session "main"
  - Foundation for attach/detach functionality (Phase 33)
affects: [33-attach-detach-implementation]

# Tech tracking
tech-stack:
  added: []
  patterns: ["All container modes (shell, harness, foreground, detached) run inside tmux by default"]

key-files:
  created: []
  modified: ["src/aishell/docker/templates.clj"]

key-decisions:
  - "All container modes auto-start inside tmux (override of ROADMAP criterion for shell mode)"
  - "gosu runs before tmux to ensure user-owned socket (avoid permission errors)"
  - "Session named 'main' for consistent attach command"

patterns-established:
  - "Entrypoint wraps final exec with: exec gosu USER:GROUP tmux new-session -A -s main -c PWD COMMAND"
  - "Idempotent session creation with -A flag (attach if exists, create if not)"

# Metrics
duration: 1min
completed: 2026-01-31
---

# Phase 32 Plan 01: Tmux Auto-Start Summary

**All container commands now auto-start inside tmux session 'main', enabling future attach/detach functionality**

## Performance

- **Duration:** 1 min
- **Started:** 2026-01-31T14:24:23Z
- **Completed:** 2026-01-31T14:26:09Z
- **Tasks:** 1
- **Files modified:** 1

## Accomplishments
- Modified entrypoint script to wrap all commands in tmux session
- gosu precedes tmux in exec chain (ensures user-owned socket)
- Applies consistently to all modes: shell, harness, foreground, detached
- Foundation in place for Phase 33 attach/detach commands

## Task Commits

Each task was committed atomically:

1. **Task 1: Modify entrypoint to auto-start tmux session** - `7660414` (feat)

## Files Created/Modified
- `src/aishell/docker/templates.clj` - Modified entrypoint-script to wrap final exec with tmux new-session

## Decisions Made

**1. Override ROADMAP criterion for shell mode**
- ROADMAP stated "Shell mode does NOT auto-start tmux" (criterion #2)
- CONTEXT.md decision: "ALL modes auto-start inside tmux (harness, shell, foreground, detached)"
- Rationale: Consistency across all modes, simplifies user mental model
- This was an explicit user decision during phase discussion

**2. gosu before tmux in exec chain**
- Pattern: `exec gosu "$USER_ID:$GROUP_ID" tmux new-session ...`
- Ensures tmux socket is owned by the user, not root
- Avoids permission errors on attach operations

**3. Session name 'main'**
- Consistent naming for attach command (Phase 33)
- -A flag makes creation idempotent (attach if exists, create if not)

## Deviations from Plan

None - plan executed exactly as written.

## Issues Encountered

**Initial syntax error in Clojure string escaping**
- Issue: Unescaped double quotes in comments broke string literal
- Resolution: Escaped quotes in comment text with backslashes
- Comments like `session named "main"` became `session named \"main\"`

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness

**Ready for Phase 33 (Attach/Detach Implementation):**
- tmux session auto-start complete
- Session consistently named 'main' for attach commands
- User-owned socket ensures permission correctness

**Validation needed:**
- Signal handling with existing --init flag (tini as PID 1)
- Verify docker stop doesn't timeout (SIGTERM handling through tmux)
- Test socket permissions with `ls -la /tmp/tmux-*`

---
*Phase: 32-detached-mode-conflict-detection*
*Completed: 2026-01-31*
