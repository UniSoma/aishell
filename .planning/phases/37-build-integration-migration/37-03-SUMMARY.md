---
phase: 37-build-integration-migration
plan: 03
subsystem: build
tags: [docker, cache-invalidation, migration, labels, foundation-image]

# Dependency graph
requires:
  - phase: 36-harness-volume-core
    provides: "Volume-based harness injection architecture"
  - phase: 35-foundation-rename
    provides: "Clean break from aishell:base to aishell:foundation"
provides:
  - "Extension cache invalidation using foundation image labels"
  - "Automatic extension rebuild for pre-upgrade extensions (migration support)"
  - "foundation-image-id-label constant in build.clj"
affects: [38-deprecation-cleanup]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "Extension cache keyed by foundation image ID (aishell.foundation.id)"
    - "Migration handled via nil label detection (old extensions have no aishell.foundation.id)"

key-files:
  created: []
  modified:
    - src/aishell/docker/build.clj
    - src/aishell/docker/extension.clj
    - src/aishell/run.clj

key-decisions:
  - "Use nil foundation ID check for migration (stored-foundation-id nil triggers rebuild)"
  - "Keep backward-compat alias base-image-id-label in build.clj"
  - "Rename function get-base-image-id to get-foundation-image-id for clarity"

patterns-established:
  - "Extension cache validation via foundation-image-id-label"
  - "Natural migration behavior: missing label != current ID = rebuild"

# Metrics
duration: 2min
completed: 2026-02-01
---

# Phase 37 Plan 03: Extension Cache Migration Summary

**Extension cache invalidation migrated to foundation image labels with automatic rebuild for pre-upgrade extensions**

## Performance

- **Duration:** 2 min
- **Started:** 2026-02-01T01:30:04Z
- **Completed:** 2026-02-01T01:31:55Z
- **Tasks:** 1
- **Files modified:** 3

## Accomplishments
- Extension cache invalidation now references aishell.foundation.id instead of aishell.base.id
- Old extensions (pre-upgrade) automatically rebuild because stored foundation ID is nil
- New extension builds label with aishell.foundation.id for future cache validation
- Backward compatibility maintained with base-image-id-label alias

## Task Commits

Each task was committed atomically:

1. **Task 1: Update label constants and cache invalidation logic** - `5ffe159` (refactor)

**Plan metadata:** (pending - to be committed with STATE.md update)

## Files Created/Modified
- `src/aishell/docker/build.clj` - Renamed base-image-id-label to foundation-image-id-label with backward-compat alias
- `src/aishell/docker/extension.clj` - Updated cache invalidation to use foundation-image-id-label, renamed get-base-image-id to get-foundation-image-id
- `src/aishell/run.clj` - Updated build-extended-image call to use :foundation-tag parameter

## Decisions Made

**Migration strategy via nil label check:**
- Existing extensions have no aishell.foundation.id label (they have aishell.base.id)
- Cache validation: `(not= stored-foundation-id current-foundation-id)`
- When stored-foundation-id is nil (old extension), comparison with current foundation ID (non-nil) returns true
- This triggers automatic rebuild, satisfying MIGR-02 requirement
- No special migration code needed - natural consequence of label change

**Backward compatibility:**
- Kept base-image-id-label as alias to foundation-image-id-label in build.clj
- Prevents breakage if other code references old constant name
- Clean migration path without hard cutover

## Deviations from Plan

None - plan executed exactly as written.

## Issues Encountered

None

## Next Phase Readiness

- Extension cache validation aligned with foundation image architecture
- Migration behavior validated: old extensions trigger rebuild on first use after upgrade
- Ready for phase 38 (deprecation cleanup)

**Readiness for 38:**
- Can safely deprecate old base-image-id-label alias after validation period
- Can remove backward-compat shims once migration confirmed working

---
*Phase: 37-build-integration-migration*
*Completed: 2026-02-01*
