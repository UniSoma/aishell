---
phase: quick
plan: 004
subsystem: config
tags: [babashka, yaml, config-merge, inheritance]

# Dependency graph
requires:
  - phase: 13-babashka-rewrite
    provides: Config loading system with YAML support
provides:
  - Flexible config inheritance with extends key
  - Merge strategy for global and project configs
  - List concatenation, map merging, and scalar replacement
affects: [config, user-configuration, project-setup]

# Tech tracking
tech-stack:
  added: []
  patterns: [config-inheritance, merge-strategies]

key-files:
  created: []
  modified:
    - src/aishell/config.clj
    - .aishell/config.yaml

key-decisions:
  - "Default extends behavior is 'global' (merge) for backward compatibility"
  - "Lists concatenate (global first, project appends)"
  - "Maps shallow merge (project values override global)"
  - "Scalars replace (project wins)"
  - "extends key is removed from final config (internal-only)"

patterns-established:
  - "Config merge strategy: list concatenation, map merge, scalar replacement"
  - "extends: global (default) merges, extends: none replaces"

# Metrics
duration: 2min
completed: 2026-01-22
---

# Quick Task 004: Add extends Key for Config Merge Strategy

**Flexible config inheritance with extends key supporting merge (global) or replace (none) strategies**

## Performance

- **Duration:** 2 min
- **Started:** 2026-01-22T14:43:25Z
- **Completed:** 2026-01-22T14:45:24Z
- **Tasks:** 2
- **Files modified:** 2

## Accomplishments
- Config merge system with extends key for flexible inheritance
- Default merge behavior (extends: global) for backward compatibility
- Project configs can opt out of merging with extends: none
- Documented merge rules in config template

## Task Commits

Each task was committed atomically:

1. **Task 1: Implement config merge strategy in config.clj** - `d8e12ba` (feat)
2. **Task 2: Document extends key in config.yaml template** - `636d39c` (docs)

## Files Created/Modified
- `src/aishell/config.clj` - Config merge logic with extends key support
- `.aishell/config.yaml` - Template documentation for extends key

## Decisions Made
- **Default extends behavior is "global"**: When extends key is missing, configs merge (maintains backward compatibility)
- **List concatenation order**: Global values first, then project values append
- **Map merge strategy**: Shallow merge with project values overriding global
- **Scalar replacement**: Project values completely replace global values
- **extends key is internal**: Removed from final config after processing
- **config-source returns :merged**: When both configs used with extends: global

## Deviations from Plan

None - plan executed exactly as written.

## Issues Encountered

None.

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness

Config inheritance system complete and ready for use. Users can now:
- Use extends: global (or omit extends) to merge project config with global
- Use extends: none to fully replace global config with project config
- Leverage global defaults while overriding specific values per project

No blockers or concerns.

---
*Phase: quick*
*Completed: 2026-01-22*
