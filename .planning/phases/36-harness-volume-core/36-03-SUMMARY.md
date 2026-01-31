---
phase: 36-harness-volume-core
plan: 03
subsystem: infra
tags: [docker, volume, path, entrypoint, clojure]

# Dependency graph
requires:
  - phase: 36-01
    provides: Harness hash computation for volume naming
provides:
  - Harness volume mount in docker run arguments (-v volume:/tools:ro)
  - PATH/NODE_PATH configuration in entrypoint script
  - Conditional volume mounting (only when harness-volume-name provided)
affects: [37-harness-lazy-population]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "Volume-based tool injection with read-only mounts"
    - "Directory existence checks for conditional PATH configuration"

key-files:
  created: []
  modified:
    - src/aishell/docker/run.clj
    - src/aishell/docker/templates.clj

key-decisions:
  - "Mount harness volume read-only at /tools for security"
  - "Use HARNESS_VOLUME env var as signal rather than setting PATH via -e"
  - "Directory existence check (-d /tools/npm/bin) for safe PATH activation"
  - "Prepend /tools/npm/bin to PATH so harness tools take precedence"

patterns-established:
  - "Volume mount arguments added after autoupdater config, before user mounts"
  - "PATH order: harness tools -> user local -> system"

# Metrics
duration: 2min
completed: 2026-01-31
---

# Phase 36 Plan 03: Volume Runtime Wiring Summary

**Harness volume mounted read-only at /tools with PATH/NODE_PATH configured for volume-mounted tool execution**

## Performance

- **Duration:** 2 min 4 sec
- **Started:** 2026-01-31T23:51:58Z
- **Completed:** 2026-01-31T23:54:02Z
- **Tasks:** 2
- **Files modified:** 2

## Accomplishments
- Harness volume mount support in docker run arguments with backward compatibility
- PATH configuration prepends /tools/npm/bin when harness volume present
- NODE_PATH set for Node.js module resolution from harness volume
- Both build-docker-args and build-docker-args-for-exec support harness volumes
- Conditional activation via directory existence check (safe when no volume)

## Task Commits

Each task was committed atomically:

1. **Task 1: Add harness volume mount to docker run arguments** - `cbe996f` (feat)
2. **Task 2: Add PATH/NODE_PATH configuration to entrypoint script** - `e2616c2` (feat)

## Files Created/Modified
- `src/aishell/docker/run.clj` - Added build-harness-volume-args and build-harness-env-args; updated build-docker-args-internal, build-docker-args, and build-docker-args-for-exec to accept :harness-volume-name parameter
- `src/aishell/docker/templates.clj` - Added PATH/NODE_PATH configuration block in entrypoint script with directory existence check

## Decisions Made

**1. Read-only volume mount**
- Mount harness volume as read-only (`:/tools:ro`) for security
- Prevents accidental modification of shared harness tools

**2. HARNESS_VOLUME signal vs PATH in -e flag**
- Set HARNESS_VOLUME=true env var as signal to entrypoint
- Actual PATH modification in entrypoint script (not via -e flag)
- Rationale: `-e PATH="/tools/npm/bin:$PATH"` doesn't expand $PATH correctly in docker run

**3. Directory existence check for activation**
- Use `if [ -d "/tools/npm/bin" ]` to conditionally configure PATH
- Safe when no harness volume is mounted (directory won't exist)
- <1ms overhead (single stat call), robust approach

**4. PATH precedence order**
- `/tools/npm/bin` prepended to PATH (harness tools found first)
- PATH order: harness tools -> user local -> system
- Ensures harness tool versions take precedence over system versions

## Deviations from Plan

None - plan executed exactly as written.

## Issues Encountered

None

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness

Ready for Phase 37 (Harness Lazy Population):
- Volume mount infrastructure in place
- PATH configuration ready for harness binaries
- Backward compatible (callers don't need changes yet)

Blockers: None

Note: Callers (run.clj orchestration) will be updated in Phase 37 when lazy population is wired in. For now, :harness-volume-name parameter is optional and defaults to nil (no volume mount, backward compatible).

---
*Phase: 36-harness-volume-core*
*Completed: 2026-01-31*
