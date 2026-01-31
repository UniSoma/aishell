---
phase: 32-detached-mode-conflict-detection
plan: 02
subsystem: infra
tags: [docker, clojure, babashka, detached-mode, container-lifecycle]

# Dependency graph
requires:
  - phase: 30-container-naming
    provides: "naming/container-name and naming/ensure-name-available! functions"
provides:
  - "--detach/-d flag support in CLI for background container execution"
  - "Named containers for all modes (foreground and detached) via --name flag"
  - "Pre-flight conflict detection before every docker run"
  - "User-friendly feedback on detached launch (attach/shell/stop commands)"
affects: [33-attach-command, 34-ps-command, detached-workflow]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "CLI flag extraction pattern: extract flag → remove from args → pass to run-container"
    - "Detached execution uses p/shell (to capture container ID) vs foreground p/exec"
    - "Conflict detection gate pattern: ensure-name-available! before docker run"

key-files:
  created: []
  modified:
    - src/aishell/cli.clj
    - src/aishell/run.clj
    - src/aishell/docker/run.clj

key-decisions:
  - "--detach and -d short form safe (no conflicts with harness flags)"
  - "All containers get --name flag (unified naming across foreground and detached)"
  - "Detached feedback prints attach/shell/stop commands for user workflow"
  - "--rm works with --detach in modern Docker (auto-cleanup when stopped)"

patterns-established:
  - "Flag extraction in cli.clj: extract → remove → pass to run-container"
  - "Conflict detection before docker run: naming/ensure-name-available!"
  - "Detached execution pattern: p/shell with :out :string to capture container ID"

# Metrics
duration: 3min
completed: 2026-01-31
---

# Phase 32 Plan 02: Detach Flag & Conflict Detection Wiring Summary

**CLI --detach flag wired end-to-end with named containers and pre-flight conflict detection for all container modes**

## Performance

- **Duration:** 3 min
- **Started:** 2026-01-31T14:25:40Z
- **Completed:** 2026-01-31T14:29:06Z
- **Tasks:** 2
- **Files modified:** 3

## Accomplishments
- --detach/-d flag extracted in CLI and passed through all run-container calls
- All containers (foreground and detached) get named containers via --name flag
- Conflict detection runs before every docker run (error if running, auto-remove if stopped)
- Detached mode prints user-friendly attach/shell/stop commands after launch
- Foreground mode unchanged (backwards compatible, still uses p/exec)

## Task Commits

Each task was committed atomically:

1. **Task 1: Add --detach flag extraction to CLI dispatch** - `bdd6a61` (feat)
2. **Task 2: Wire named containers, conflict detection, and detach into run flow** - `e665392` (feat)

**Plan metadata:** [pending - will be committed after this summary]

## Files Created/Modified
- `src/aishell/cli.clj` - Added --detach/-d flag extraction and pass-through to all run-container calls
- `src/aishell/run.clj` - Added ensure-name-available! call, :detach/:container-name parameter passing, detached execution with feedback
- `src/aishell/docker/run.clj` - Added --name and --detach flags to docker args construction

## Decisions Made

**1. -d short form is safe to use**
- Claude uses `--dangerously-skip-permissions` (long form only, no short `-d`)
- OpenCode, Codex, Gemini don't use `-d` flag
- Safe to claim `-d` as aishell shorthand for `--detach`

**2. All containers get named containers**
- Per CONTEXT.md override: shell mode also gets named containers (name='shell')
- Unified behavior across all modes (foreground and detached)
- Enables consistent attach/ps/stop workflow

**3. Detached feedback format**
- Print container name confirmation
- Show attach command with user-friendly --name value (not SHA hash)
- Show docker exec shell command (bypass for power users)
- Show stop command
- Show ps command for listing containers

**4. --rm + --detach is valid**
- Modern Docker supports --rm with --detach (auto-cleanup when container stops)
- Container removed when it exits, not when detached

## Deviations from Plan

None - plan executed exactly as written.

## Issues Encountered

None - implementation straightforward, all verifications passed.

## Next Phase Readiness

**Ready for attach command (Phase 33):**
- --detach launches containers successfully
- Container names are predictable and user-friendly
- Detached feedback provides correct attach command

**Ready for ps command (Phase 34):**
- All containers have consistent naming format (aishell-{hash}-{name})
- Container metadata available for listing

**Blockers/Concerns:**
- Signal handling validation still needed (verify --init/tini handles SIGTERM correctly with tmux)
- Socket permissions (tmux must start after gosu to avoid root-owned sockets)

**Testing notes for verification:**
- `aishell claude` should run in foreground with named container (backwards compatible)
- `aishell claude --detach` should print feedback and exit immediately
- Duplicate running container should error with attach hint
- Stopped duplicate container should auto-remove and launch new container

---
*Phase: 32-detached-mode-conflict-detection*
*Completed: 2026-01-31*
