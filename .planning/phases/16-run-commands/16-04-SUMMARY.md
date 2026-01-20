---
phase: 16-run-commands
plan: 04
subsystem: cli
tags: [babashka, yaml, config, env, passthrough]

# Dependency graph
requires:
  - phase: 16-01
    provides: YAML config parsing for run commands
  - phase: 16-03
    provides: Run command dispatch (claude, opencode, shell)
provides:
  - Array format support for env config
  - Pass-through args for harness commands
affects: [17-validation, uat]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "Dual format support (map vs array) for config sections"
    - "Per-command :restrict override in babashka.cli dispatch"

key-files:
  created: []
  modified:
    - src/aishell/docker/run.clj
    - src/aishell/cli.clj

key-decisions:
  - "parse-env-string helper for KEY=value parsing"
  - "map? check to detect format and route accordingly"
  - ":restrict false per-command overrides global :restrict true"

patterns-established:
  - "Dual format config: detect type, normalize to common structure"
  - "Pass-through commands: :restrict false allows unknown flags"

# Metrics
duration: 4min
completed: 2026-01-20
---

# Phase 16 Plan 04: Fix config and CLI issues Summary

**Array format support for env config and pass-through args for claude/opencode commands**

## Performance

- **Duration:** 4 min
- **Started:** 2026-01-20T22:38:15Z
- **Completed:** 2026-01-20T22:42:24Z
- **Tasks:** 2
- **Files modified:** 2

## Accomplishments
- build-env-args now handles both map format `{FOO: bar}` and array format `[FOO=bar]`
- parse-env-string helper extracts KEY=value or KEY (passthrough) from array entries
- claude and opencode commands now pass-through all args to the harness

## Task Commits

Each task was committed atomically:

1. **Task 1: Update build-env-args to handle both formats** - `1b737a2` (fix)
2. **Task 2: Allow pass-through args for claude/opencode** - `21d1564` (fix)

## Files Created/Modified
- `src/aishell/docker/run.clj` - Added parse-env-string, updated build-env-args for dual format
- `src/aishell/cli.clj` - Added :restrict false to claude/opencode dispatch entries

## Decisions Made
- **parse-env-string helper:** Simple string parsing with str/index-of for "=" separator
- **map? check for format detection:** Clean branching - map format uses (name k), array format uses parse-env-string
- **:restrict false per-command:** Overrides global :restrict true, collects unknown flags in :opts

## Deviations from Plan

None - plan executed exactly as written.

## Issues Encountered

None.

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness
- Phase 16 gap closure complete
- All UAT issues resolved
- Ready for Phase 17 validation

---
*Phase: 16-run-commands*
*Completed: 2026-01-20*
