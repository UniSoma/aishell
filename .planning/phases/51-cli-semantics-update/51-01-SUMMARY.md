---
phase: 51-cli-semantics-update
plan: 01
subsystem: cli
tags: [cli, docker, babashka, clojure]

# Dependency graph
requires:
  - phase: 50-attach-command-rewrite
    provides: Attach command using docker exec bash (no tmux)
provides:
  - Always-foreground container model (no detached mode)
  - --name flag works in shell mode for named containers
  - Simplified CLI semantics without detach complexity
affects: [52-final-cleanup]

# Tech tracking
tech-stack:
  added: []
  patterns: []

key-files:
  created: []
  modified:
    - src/aishell/cli.clj
    - src/aishell/run.clj
    - src/aishell/docker/run.clj
    - src/aishell/attach.clj

key-decisions:
  - "Removed --detach/-d flag completely (containers always foreground-attached)"
  - "Extended --name extraction to shell mode for CLI-03 compliance"

patterns-established: []

# Metrics
duration: 3min
completed: 2026-02-06
---

# Phase 51 Plan 01: CLI Semantics Update Summary

**Removed --detach flag and extended --name to shell mode, completing always-attached container model**

## Performance

- **Duration:** 3 min
- **Started:** 2026-02-06T14:09:50Z
- **Completed:** 2026-02-06T14:13:09Z
- **Tasks:** 2
- **Files modified:** 4

## Accomplishments
- Removed all --detach/-d flag handling from CLI and execution paths
- Extended --name extraction to work for shell mode (not just harness commands)
- Wired --name to shell mode dispatch so `aishell --name foo` creates container "foo"
- Updated all help text and error messages to remove --detach references
- Simplified docker run logic by removing detached vs foreground branching

## Task Commits

Each task was committed atomically:

1. **Task 1: Remove detach flag and extend --name extraction** - `9ef5cca` (feat)
2. **Task 2: Update attach.clj error messages** - `c5a4216` (fix)

## Files Created/Modified
- `src/aishell/cli.clj` - Removed --detach extraction, extended --name to shell mode, updated help text
- `src/aishell/run.clj` - Removed detached mode execution branch, simplified to foreground-only
- `src/aishell/docker/run.clj` - Removed detach parameter from build-docker-args functions
- `src/aishell/attach.clj` - Removed --detach from error message guidance

## Decisions Made

**Extended --name extraction logic:**
- Changed from harness-command-only extraction to run-mode extraction
- New logic: extract --name for all commands EXCEPT known subcommands (setup, update, check, exec, ps, volumes, attach)
- This allows `aishell --name foo` to create container named "foo" in shell mode (CLI-03)

**Shell mode dispatch wiring:**
- Changed conditional from `if unsafe?` to `if (or unsafe? container-name-override)`
- Ensures `aishell --name foo` dispatches to run/run-container instead of falling through to cli/dispatch
- Satisfies CLI-03: `aishell --name foo` → container named "foo" running bash

## Deviations from Plan

None - plan executed exactly as written.

## Issues Encountered

**Parenthesis count error after detach removal:**
- Issue: Removed detached if branch but kept same closing paren count, causing SCI parse error
- Cause: Detached mode was nested inside the gitleaks if as an else branch with extra indentation
- Fix: Removed one closing paren to account for removed if nesting level
- Verification: `bb -cp src -e "(require '[aishell.cli]) :ok"` passed after fix

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness

CLI semantics now match v3.0.0 model:
- All 6 CLI success criteria satisfied (CLI-01 through CLI-06)
- No detach references anywhere in codebase
- --name works for both harness commands and shell mode
- Container creation errors on name conflicts
- Ready for Phase 52 (final cleanup and documentation)

---
*Phase: 51-cli-semantics-update*
*Completed: 2026-02-06*

## Self-Check: PASSED

All files and commits verified.
