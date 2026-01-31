---
phase: 35-foundation-image-split
plan: 02
subsystem: infra
tags: [docker, clojure, babashka, validation, migration, foundation-image]

# Dependency graph
requires:
  - phase: 35-01
    provides: Foundation image stripped of harness installations
provides:
  - validate-base-tag function that detects legacy FROM aishell:base usage
  - Clear migration error message with instructions to change to FROM aishell:foundation
  - Validation integrated into extension build and check paths
affects: [35-03-extension-mount-update, user-migrations]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "Migration validation pattern for breaking changes"
    - "Early validation before build attempts"

key-files:
  created: []
  modified:
    - src/aishell/docker/extension.clj
    - src/aishell/run.clj
    - src/aishell/check.clj

key-decisions:
  - "Case-insensitive regex with word boundary to match FROM aishell:base only"
  - "Exit with error (not warning) to prevent silent breakage"
  - "Validate before build attempt, not during build failure"

patterns-established:
  - "Validation functions in extension.clj return nil on success, call output/error on failure"
  - "Early validation in resolve-image-tag and check-extension before rebuild logic"

# Metrics
duration: 2min
completed: 2026-01-31
---

# Phase 35 Plan 02: Legacy Base Tag Validation Summary

**Validation prevents silent breakage when users upgrade to v2.8.0 with existing .aishell/Dockerfile files that reference FROM aishell:base, providing clear migration path to FROM aishell:foundation**

## Performance

- **Duration:** 2 minutes
- **Started:** 2026-01-31T22:36:36Z
- **Completed:** 2026-01-31T22:38:03Z
- **Tasks:** 2
- **Files modified:** 3

## Accomplishments
- Created validate-base-tag function with case-insensitive regex detection
- Clear error message explains the v2.8.0 architecture change and exact fix
- Validation integrated before extension build attempts in both run and check paths
- Projects without .aishell/Dockerfile unaffected (no validation overhead)

## Task Commits

Each task was committed atomically:

1. **Task 1: Add validate-base-tag function to extension.clj** - `88cd07f` (feat)
2. **Task 2: Wire validation into extension build and run paths** - `660d308` (feat)

## Files Created/Modified
- `src/aishell/docker/extension.clj` - Added validate-base-tag function with regex detection and migration error message
- `src/aishell/run.clj` - Added validation call in resolve-image-tag before extension rebuild
- `src/aishell/check.clj` - Added validation call in check-extension before rebuild status check

## Decisions Made

**1. Use word boundary in regex pattern**
- Rationale: `\b` ensures we only match `FROM aishell:base` exactly, not `FROM aishell:base-something`. Prevents false positives.

**2. Exit with error instead of warning**
- Rationale: Silent build failure would be worse than clear stoppage. Users need to fix before continuing.

**3. Validate before build attempt, not on build failure**
- Rationale: Clearer user experience. Validation happens immediately with helpful message, rather than cryptic Docker build error.

## Deviations from Plan

None - plan executed exactly as written.

## Issues Encountered

None

## Next Phase Readiness

Legacy tag validation is complete and ready for v2.8.0 release:
- Users with existing .aishell/Dockerfile files will see clear migration instructions
- FROM aishell:foundation works without any validation overhead
- No blockers for next plan

**Next:** Phase 35-03 will update extension mounting to use foundation volumes.

---
*Phase: 35-foundation-image-split*
*Completed: 2026-01-31*
