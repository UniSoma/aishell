---
phase: 66-global-base-image-customization
plan: 02
subsystem: docker
tags: [docker, base-image, three-tier, extension, run-path, check, volumes]

# Dependency graph
requires:
  - phase: 66-global-base-image-customization
    provides: Core base image module (docker/base.clj)
provides:
  - "Extension builds FROM aishell:base (three-tier chain complete)"
  - "Lazy base image build on container run via ensure-base-image"
  - "Base image status in aishell check output"
  - "Orphaned base image cleanup via volumes prune"
  - "validate-base-tag fully removed (FROM aishell:base now accepted)"
affects: [66-03, 66-04]

# Tech tracking
tech-stack:
  added: []
  patterns: [lazy-base-build-on-run, orphaned-base-cleanup]

key-files:
  created: []
  modified: [src/aishell/docker/extension.clj, src/aishell/run.clj, src/aishell/check.clj, src/aishell/cli.clj]

key-decisions:
  - "Avoided circular dependency between base.clj and extension.clj by passing base tag as parameter"
  - "Extension rebuild tracking switched from foundation ID to base image ID label"
  - "Orphaned base image detection uses presence of dockerfile hash label without global Dockerfile"

patterns-established:
  - "Callers pass base/base-image-tag to extension functions (no circular require)"
  - "ensure-base-image called at multiple entry points for defense-in-depth lazy build"

requirements-completed: [BASE-04, BASE-05, BASE-07, BASE-08]

# Metrics
duration: 4min
completed: 2026-02-18
---

# Phase 66 Plan 02: Base Image Integration Summary

**Three-tier chain wired into run path, extension builds, check command, and volumes prune with lazy base build and orphan cleanup**

## Performance

- **Duration:** 4 min
- **Started:** 2026-02-18T23:50:27Z
- **Completed:** 2026-02-18T23:54:16Z
- **Tasks:** 2
- **Files modified:** 4

## Accomplishments
- Wired extension images to build FROM `aishell:base` instead of `aishell:foundation`, completing the three-tier chain
- Removed `validate-base-tag` function and all 3 call sites (definition + 2 callers), enabling `FROM aishell:base` in project Dockerfiles
- Added lazy `ensure-base-image` calls in run-container, run-exec, and resolve-image-tag for defense-in-depth base image builds
- Added `check-base-image-custom` to `aishell check` showing custom vs default base image status
- Added orphaned custom base image detection to `volumes prune` (re-tags foundation when global Dockerfile removed)

## Task Commits

Each task was committed atomically:

1. **Task 1: Update extension.clj and run.clj for base image layer** - `2fef394` (feat)
2. **Task 2: Update check command and volumes prune for base image** - `ff75309` (feat)

## Files Created/Modified
- `src/aishell/docker/extension.clj` - Removed validate-base-tag, switched to base image ID tracking, updated docstrings
- `src/aishell/run.clj` - Added base require, lazy ensure-base-image calls, use base/base-image-tag throughout
- `src/aishell/check.clj` - Added base require, check-base-image-custom function, removed validate-base-tag call
- `src/aishell/cli.clj` - Added orphaned custom base image detection to handle-volumes-prune

## Decisions Made
- Avoided circular dependency (base.clj requires extension.clj for get-foundation-image-id) by keeping extension.clj free of base.clj require; callers pass base-image-tag as parameter instead
- Extension images now track `aishell.base.id` label instead of `aishell.foundation.id` so they auto-rebuild when base image changes
- ensure-base-image called at 3 points in run path (run-container, run-exec, resolve-image-tag) for defense-in-depth

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] Avoided circular dependency between base.clj and extension.clj**
- **Found during:** Task 1 (extension.clj changes)
- **Issue:** Plan specified adding `[aishell.docker.base :as base]` require to extension.clj, but base.clj already requires extension.clj (for get-foundation-image-id), creating a circular dependency that would crash SCI
- **Fix:** Instead of requiring base.clj in extension.clj, used the `base-tag` parameter already passed to `needs-extended-rebuild?` and `build-extended-image` for the base image ID lookup. Callers in run.clj pass `base/base-image-tag`.
- **Files modified:** src/aishell/docker/extension.clj
- **Verification:** `bb -e "(require '[aishell.docker.extension]) (println 'OK')"` succeeds
- **Committed in:** 2fef394 (Task 1 commit)

---

**Total deviations:** 1 auto-fixed (1 blocking)
**Impact on plan:** Essential fix to prevent SCI circular dependency crash. No scope creep.

## Issues Encountered
None beyond the circular dependency addressed above.

## User Setup Required
None - no external service configuration required.

## Next Phase Readiness
- Three-tier chain fully wired: foundation -> base -> ext-{hash}
- Ready for Plan 66-03 (documentation) and Plan 66-04 (version bump / CHANGELOG)
- All integration points working: setup, update, run, check, volumes prune

## Self-Check: PASSED

- FOUND: src/aishell/docker/extension.clj
- FOUND: src/aishell/run.clj
- FOUND: src/aishell/check.clj
- FOUND: src/aishell/cli.clj
- FOUND: commit 2fef394
- FOUND: commit ff75309

---
*Phase: 66-global-base-image-customization*
*Completed: 2026-02-18*
