---
phase: 29-exec-command
plan: 01
subsystem: cli
tags: [clojure, babashka, docker, tty-detection, subprocess-management]

# Dependency graph
requires:
  - phase: 28-dynamic-help
    provides: CLI dispatch patterns and help infrastructure
  - phase: 13-babashka-rewrite
    provides: Babashka process execution patterns
provides:
  - One-off command execution via `aishell exec <command>`
  - TTY auto-detection for piping support
  - build-docker-args-for-exec function with conditional TTY
  - run-exec function for fast command execution
affects: [future-cli-commands, scripting-automation]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - TTY auto-detection using System/console
    - Conditional docker flags based on stdin type
    - Fast path execution (skip detection/warnings for one-off commands)

key-files:
  created: []
  modified:
    - src/aishell/docker/run.clj
    - src/aishell/run.clj
    - src/aishell/cli.clj
    - src/aishell/output.clj

key-decisions:
  - "Use System/console for TTY detection (same pattern as existing codebase)"
  - "Skip detection/warnings for exec (fast path for one-off commands)"
  - "Always skip pre_start hooks for exec (one-off shouldn't start sidecars)"
  - "Use p/shell with :inherit true for exec (not p/exec, to capture exit codes)"

patterns-established:
  - "TTY-conditional docker args: [-it] for terminal, [-i] for pipes"
  - "Always include -i flag (stdin) even without TTY allocation"
  - "Refactor docker arg building via internal helper for DRY"

# Metrics
duration: 3min
completed: 2026-01-26
---

# Phase 29 Plan 01: Exec Command Summary

**One-off command execution with automatic TTY detection enables piping and scripting without TTY errors**

## Performance

- **Duration:** 3 min
- **Started:** 2026-01-26T11:25:38Z
- **Completed:** 2026-01-26T11:28:47Z
- **Tasks:** 3
- **Files modified:** 4

## Accomplishments
- Users can run single commands in containers via `aishell exec <command>`
- TTY auto-detected: works in terminals AND in pipes/scripts without errors
- Exit codes propagate correctly from container to host
- Fast path: skips detection warnings and pre_start hooks for quick execution

## Task Commits

Each task was committed atomically:

1. **Task 1: Add TTY-conditional docker args builder** - `666647d` (feat)
   - Refactored build-docker-args into shared internal helper
   - Created build-docker-args-for-exec with :tty? parameter
   - Conditional flags: [-it] when tty?, [-i] when not

2. **Task 2: Add run-exec function** - `c27df64` (feat)
   - Created run-exec for one-off command execution
   - Auto-detect TTY using (some? (System/console))
   - Skip detection warnings and gitleaks freshness warnings
   - Use p/shell with :inherit true :continue true
   - Propagate exit code via System/exit

3. **Task 3: Add exec command to CLI dispatch** - `602a761` (feat)
   - Added exec case to dispatch function
   - Updated help output with exec command
   - Added exec to known-commands for typo suggestions

## Files Created/Modified
- `src/aishell/docker/run.clj` - Added build-docker-args-for-exec with TTY-conditional flags, refactored into internal helper
- `src/aishell/run.clj` - Added run-exec function for one-off command execution with TTY auto-detection
- `src/aishell/cli.clj` - Added exec dispatch case and help output
- `src/aishell/output.clj` - Added exec to known-commands set for typo suggestions

## Decisions Made

**TTY detection method:**
- Used `(some? (System/console))` for TTY detection
- Rationale: Already used in existing codebase (detection/core.clj, output.clj), portable, simple

**Skip detection for exec:**
- Skip sensitive file detection and gitleaks warnings for exec
- Rationale: Fast path for quick commands. Users can run `aishell gitleaks` separately if needed.

**Skip pre_start for exec:**
- Always set skip-pre-start true in build-docker-args-for-exec
- Rationale: One-off commands shouldn't start sidecars. Only interactive sessions need pre_start.

**Always include -i flag:**
- Even when not allocating TTY, include -i flag for stdin
- Rationale: Without -i, piped input fails silently. -i is required for `echo test | aishell exec cat`

**Use p/shell not p/exec:**
- run-exec uses p/shell with :inherit true :continue true
- Rationale: Need to capture exit code for propagation. p/exec replaces process.

## Deviations from Plan

None - plan executed exactly as written.

## Issues Encountered

None

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness

Exec command complete and working. Ready for:
- Documentation updates if needed
- User testing for feedback
- Phase complete - v2.5.0 polish milestone ready

---
*Phase: 29-exec-command*
*Completed: 2026-01-26*
