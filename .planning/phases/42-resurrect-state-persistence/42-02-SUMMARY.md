---
phase: 42-resurrect-state-persistence
plan: 02
subsystem: infra
tags: [tmux, tmux-resurrect, tpm, state-persistence, container-runtime]

# Dependency graph
requires:
  - phase: 42-01
    provides: Resurrect config parsing and state directory mounting
provides:
  - Auto-injection of tmux-resurrect plugin in build pipeline
  - Resurrect config injection in entrypoint (resurrect-dir, process restoration)
  - Auto-restore execution on tmux start via run-shell
affects: [tmux-integration, session-management, container-lifecycle]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - Plugin auto-injection with deduplication in build pipeline
    - Environment variable-based config passing to entrypoint
    - Runtime config injection via bash in entrypoint script

key-files:
  created: []
  modified:
    - src/aishell/cli.clj
    - src/aishell/docker/volume.clj
    - src/aishell/docker/run.clj
    - src/aishell/docker/templates.clj

key-decisions:
  - "Auto-inject tmux-resurrect plugin when resurrect enabled (no manual declaration needed)"
  - "Deduplicate resurrect plugin if user already declared it (silent, no warnings)"
  - "Process restoration defaults to 'false' for safety, explicit restore_processes: true enables ':all:' mode"
  - "Auto-restore runs via run-shell after TPM initialization in config file"
  - "Pass resurrect config as RESURRECT_ENABLED and RESURRECT_RESTORE_PROCESSES env vars to entrypoint"

patterns-established:
  - "Plugin auto-injection pattern: check resurrect config, conditionally append plugin to list, deduplicate if present"
  - "Entrypoint config injection: read env vars, append tmux config directives to runtime config file"
  - "Auto-restore pattern: run-shell executes restore.sh r after TPM loads plugins"

# Metrics
duration: 2min
completed: 2026-02-02
---

# Phase 42 Plan 02: Resurrect Plugin Injection Summary

**Auto-inject tmux-resurrect plugin in build pipeline and configure auto-restore with process restoration controls in entrypoint**

## Performance

- **Duration:** 2 min
- **Started:** 2026-02-02T22:19:33Z
- **Completed:** 2026-02-02T22:21:48Z
- **Tasks:** 2
- **Files modified:** 4

## Accomplishments
- tmux-resurrect plugin auto-added to plugin list when resurrect enabled in config
- Plugin deduplication prevents double entries when user manually declares resurrect
- Resurrect configuration injected into runtime tmux config (resurrect-dir, process restoration)
- Auto-restore script runs on tmux start to restore last saved session
- Process restoration controllable via restore_processes config option (defaults to false)

## Task Commits

Each task was committed atomically:

1. **Task 1: Auto-inject tmux-resurrect plugin in build pipeline** - `4e2d42e` (feat)
2. **Task 2: Inject resurrect config and auto-restore in entrypoint** - `8f0aa8a` (feat)

## Files Created/Modified
- `src/aishell/cli.clj` - Auto-inject resurrect plugin in build command, pass resurrect-config to state
- `src/aishell/docker/volume.clj` - Add inject-resurrect-plugin helper, apply auto-injection in lazy volume population
- `src/aishell/docker/run.clj` - Add build-resurrect-env-args helper, pass RESURRECT_ENABLED/RESURRECT_RESTORE_PROCESSES env vars
- `src/aishell/docker/templates.clj` - Inject resurrect config block in entrypoint after TPM initialization

## Decisions Made
- Auto-injection ensures resurrect plugin is installed without manual declaration
- Deduplication pattern handles user-declared resurrect plugin gracefully
- Process restoration defaults to 'false' for safety (explicit opt-in for ':all:' mode)
- Auto-restore positioned after TPM initialization so plugin is loaded before restore script runs
- Environment variables used to pass resurrect config to entrypoint (simpler than state file mounting)

## Deviations from Plan

None - plan executed exactly as written.

## Issues Encountered

None

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness

Phase 42 complete. tmux-resurrect feature fully operational:
- Plugin auto-installed when resurrect enabled
- State directory mounted per-project for persistence
- Config injected with customizable process restoration
- Auto-restore runs on container start

Ready for testing and v2.9.0 milestone completion.

---
*Phase: 42-resurrect-state-persistence*
*Completed: 2026-02-02*
