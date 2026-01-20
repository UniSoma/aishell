---
phase: 16-run-commands
plan: 02
subsystem: docker
tags: [docker, run, mounts, env, ports, git-identity]

# Dependency graph
requires:
  - phase: 14-docker-integration
    provides: entrypoint template with PRE_START handling
  - phase: 16-run-commands/16-01
    provides: config module for YAML parsing
provides:
  - Docker run argument builder (build-docker-args)
  - Git identity reading from host
  - Mount/env/port argument construction
  - API key passthrough
affects: [16-run-commands/16-03, 16-run-commands/16-04]

# Tech tracking
tech-stack:
  added: []
  patterns: [cond-> threading for optional args, mapcat for arg pairs]

key-files:
  created: [src/aishell/docker/run.clj]
  modified: []

key-decisions:
  - "cond-> threading for conditional arg inclusion (cleaner than nested ifs)"
  - "mapcat for building -flag value pairs from sequences"
  - "PRE_START passed as env var, entrypoint handles execution"

patterns-established:
  - "cond-> for optional docker args: (cond-> (:key config) (into ...))"
  - "mapcat for arg pairs: (->> items (mapcat (fn [x] [\"-flag\" x])))"

# Metrics
duration: 3min
completed: 2026-01-20
---

# Phase 16 Plan 02: Docker Run Argument Builder Summary

**Docker run argument builder with git identity, mount/env/port handling, and PRE_START passthrough**

## Performance

- **Duration:** 3 min
- **Started:** 2026-01-20
- **Completed:** 2026-01-20
- **Tasks:** 2
- **Files modified:** 1

## Accomplishments
- Created docker/run.clj with full docker run argument construction
- Implemented git identity reading from host (user.name, user.email)
- Built mount/env/port argument builders with validation
- Integrated harness config mounts (Claude, OpenCode) and API key passthrough

## Task Commits

Each task was committed atomically:

1. **Task 1: Create docker/run.clj with core argument building** - `37745df` (feat)
2. **Task 2: Add main build-docker-args function** - `06b03a1` (feat)

## Files Created/Modified
- `src/aishell/docker/run.clj` - Docker run argument construction module

## Decisions Made
- **cond-> threading for optional args:** Cleaner pattern than nested ifs for conditionally adding docker args
- **mapcat for arg pairs:** Elegant way to build [-flag value -flag value] sequences from configs
- **PRE_START as env var:** Passed via -e PRE_START=command, entrypoint (Phase 14) handles background execution

## Deviations from Plan

None - plan executed exactly as written.

## Issues Encountered

None.

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness
- build-docker-args ready for use by run command implementation
- Git identity reading ready for container execution
- Config processing (mounts, env, ports, docker_args, pre_start) complete
- Ready for Plan 16-03: run command implementation

---
*Phase: 16-run-commands*
*Completed: 2026-01-20*
