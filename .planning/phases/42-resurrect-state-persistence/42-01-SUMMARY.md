---
phase: 42-resurrect-state-persistence
plan: 01
subsystem: tmux-integration
tags: [tmux, resurrect, state-persistence, config-parsing, docker-volumes]

# Dependency graph
requires:
  - phase: 40-tmux-plugin-management
    provides: TPM runtime infrastructure and volume mounting
  - phase: 41-tpm-initialization
    provides: TPM conditional startup in entrypoint
provides:
  - Config parsing for tmux.resurrect (boolean sugar and map forms)
  - Host directory mount for resurrect state persistence
  - Normalized resurrect config with :enabled and :restore_processes keys
affects: [42-02-entrypoint-plugin-declarations, resurrect-future-phases]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "Boolean sugar config pattern (true -> normalized map)"
    - "Scalar merge for tmux config (project replaces global)"
    - "Per-project state directory isolation via project hash"

key-files:
  created: []
  modified:
    - src/aishell/config.clj
    - src/aishell/docker/run.clj

key-decisions:
  - "resurrect: true -> {:enabled true :restore_processes false} (sensible default)"
  - "resurrect: {:enabled false} returns nil (disabled state)"
  - "State directory: ~/.aishell/resurrect/{project-hash}/ for isolation"
  - "Mount silently skipped when tmux disabled (resurrect config ignored)"

patterns-established:
  - "Parse-normalize pattern: accept multiple input forms, return single normalized shape"
  - "Validation warnings for invalid config types, not errors"
  - "Auto-create host directories for state persistence mounts"

# Metrics
duration: 1min
completed: 2026-02-02
---

# Phase 42 Plan 01: Resurrect Config Parsing and State Mounting Summary

**Config parsing for tmux.resurrect (boolean + map forms) with auto-mounted per-project state directory at ~/.aishell/resurrect/{project-hash}/**

## Performance

- **Duration:** 1 min
- **Started:** 2026-02-02T22:15:41Z
- **Completed:** 2026-02-02T22:17:01Z
- **Tasks:** 2
- **Files modified:** 2

## Accomplishments
- Parse resurrect config in all forms: true, false, map with enabled, map with restore_processes
- Mount resurrect state directory from host when both tmux and resurrect enabled
- Auto-create host state directory with project hash isolation
- Silent skip when tmux disabled (resurrect config ignored, no warnings)

## Task Commits

Each task was committed atomically:

1. **Task 1: Parse resurrect config in config.clj** - `c195669` (feat)
2. **Task 2: Mount resurrect state directory in docker run** - `a9227d1` (feat)

## Files Created/Modified
- `src/aishell/config.clj` - Added parse-resurrect-config for boolean sugar and map normalization, added resurrect validation in validate-tmux-config
- `src/aishell/docker/run.clj` - Added build-resurrect-mount function, integrated into build-docker-args-internal with auto-directory creation

## Decisions Made
- **resurrect: true sugar form**: Maps to `{:enabled true :restore_processes false}` as sensible default (most users want session persistence, not process restoration)
- **State directory structure**: Use `~/.aishell/resurrect/{project-hash}/` for per-project isolation, consistent with existing project-hash pattern
- **Mount target**: Container path at `~/.tmux/resurrect/` (tmux-resurrect default location)
- **Silent skip behavior**: When tmux disabled, resurrect config silently ignored (no warnings) - consistent with principle that tmux config only matters when tmux active

## Deviations from Plan

None - plan executed exactly as written.

## Issues Encountered

None.

## Next Phase Readiness

Ready for Phase 42-02 (entrypoint plugin declarations). The config parsing and state directory mounting provide the foundation for:
- Entrypoint to read parsed resurrect config and inject plugin declarations
- tmux-resurrect plugin to find state files in mounted directory
- Session state persistence across container restarts

No blockers or concerns.

---
*Phase: 42-resurrect-state-persistence*
*Completed: 2026-02-02*
