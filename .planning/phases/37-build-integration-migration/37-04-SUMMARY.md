---
phase: 37-build-integration-migration
plan: 04
subsystem: build
tags: [docker, volume, state-management, build-system, clojure]

# Dependency graph
requires:
  - phase: 37-01
    provides: "State schema v2.8.0 migration infrastructure"
  - phase: 36-01
    provides: "Harness hash computation and volume naming primitives"
  - phase: 36-02
    provides: "Volume creation and population primitives"
provides:
  - "Unified build command that transparently handles foundation image + harness volume"
  - "v2.8.0 state persistence with :foundation-hash, :harness-volume-hash, :harness-volume-name"
  - "Lazy volume population (only when missing or stale)"
affects: [37-05-volume-runtime-wiring, testing, run-command]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "Lazy volume population in build flow (check hash, create if missing/stale)"
    - "Unified state-map construction used for both hash computation and state writing"

key-files:
  created: []
  modified:
    - "src/aishell/cli.clj"

key-decisions:
  - "Volume population only happens when at least one harness is enabled (some check on harness flags)"
  - "Volume is only populated if missing OR hash mismatch (lazy, not every build)"
  - "Both :dockerfile-hash (deprecated) and :foundation-hash (new) are written for backward compatibility"
  - ":harness-volume-name is stored so run.clj can use it without recomputing"

patterns-established:
  - "state-map pattern: Build map with harness flags/versions, use for hash computation AND state writing via assoc"
  - "Volume existence check + label verification pattern for staleness detection"

# Metrics
duration: 1min
completed: 2026-02-01
---

# Phase 37 Plan 04: Build Integration & Migration Summary

**Foundation build and harness volume transparently wired into unified build command with v2.8.0 state persistence**

## Performance

- **Duration:** 1 min
- **Started:** 2026-02-01T01:34:36Z
- **Completed:** 2026-02-01T01:35:44Z
- **Tasks:** 1
- **Files modified:** 1

## Accomplishments
- `aishell build --with-claude` now transparently handles foundation image build AND harness volume population in one command
- State file after build contains complete v2.8.0 schema: :foundation-hash, :harness-volume-hash, :harness-volume-name
- Volume is only populated when needed (missing or hash mismatch), not on every build
- Both `handle-build` and `handle-update` updated with consistent volume population logic

## Task Commits

Each task was committed atomically:

1. **Task 1: Add volume population to handle-build and update state writing** - `0e78f60` (feat)

## Files Created/Modified
- `src/aishell/cli.clj` - Added volume.clj require, wired volume population into build flow, updated state writing to v2.8.0 schema

## Decisions Made
- Volume population only happens when at least one harness is enabled (some check on :with-claude/:with-opencode/:with-codex/:with-gemini)
- Volume is only populated if missing OR hash label mismatch (lazy, not every build)
- Both :dockerfile-hash (deprecated) and :foundation-hash (new) are written for backward compatibility
- :harness-volume-name is stored in state so run.clj can mount it without recomputing hash

## Deviations from Plan

None - plan executed exactly as written.

## Issues Encountered

None

## Next Phase Readiness

Ready for Plan 37-05 (volume runtime wiring). The build command now:
- Computes harness hash from state-map
- Creates/populates volume if needed
- Writes complete v2.8.0 state with volume fields

Run command can now read :harness-volume-name from state and mount it at runtime.

No blockers.

---
*Phase: 37-build-integration-migration*
*Completed: 2026-02-01*
