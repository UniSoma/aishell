---
phase: 14-docker-integration
plan: 05
subsystem: cli
tags: [cli, docker-integration, error-handling, user-feedback]

# Dependency graph
requires:
  - phase: 14-01
    provides: Docker availability checks (check-docker!)
  - phase: 14-03
    provides: Build module ready for CLI wiring
  - phase: 14-04
    provides: Extension module ready for CLI wiring
provides:
  - CLI integrated with Docker checks at entry point
  - User-facing error message for missing image (error-no-build)
  - Complete Phase 14 foundation for build command in Phase 15
affects: [15-build-command, run-command]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "Docker check at CLI entry point before any operation"
    - "Staged error flow: Docker check -> image check -> proceed"

key-files:
  created: []
  modified:
    - src/aishell/cli.clj
    - src/aishell/output.clj

key-decisions:
  - "Docker check before image check in default handler"
  - "Always show no-build error for now (image state management in Phase 15)"

patterns-established:
  - "Default CLI action checks prerequisites before proceeding"
  - "error-* pattern for different failure modes with hints"

# Metrics
duration: 1min
completed: 2026-01-20
---

# Phase 14 Plan 05: CLI Docker Integration Summary

**Docker daemon check and "no image built" error integrated into CLI default handler for user-facing feedback**

## Performance

- **Duration:** 1 min
- **Started:** 2026-01-20T18:20:00Z
- **Completed:** 2026-01-20T18:21:00Z
- **Tasks:** 2 (+ 1 verification checkpoint)
- **Files modified:** 2

## Accomplishments

- Added `error-no-build` function to output module with colored message and hint
- Integrated Docker availability check into CLI default handler
- CLI now requires docker module and calls `check-docker!` before proceeding
- Clear error flow: Docker check -> No image error -> (future: shell entry)

## Task Commits

Each task was committed atomically:

1. **Task 1: Add error-no-build to output module** - `9bd81d5` (feat)
2. **Task 2: Update CLI to integrate Docker checks** - `f9b798d` (feat)
3. **Task 3: Verification checkpoint** - User approved all tests

## Files Created/Modified

- `src/aishell/output.clj` - Added `error-no-build` function
  - Outputs colored error to stderr
  - Includes hint: "Run: aishell build"
  - Exits with code 1
- `src/aishell/cli.clj` - Integrated Docker checks
  - Added require for `aishell.docker`
  - Default handler calls `docker/check-docker!` before `output/error-no-build`

## Decisions Made

| Decision | Rationale |
|----------|-----------|
| Docker check before image check | Fail fast on Docker unavailable before checking image state |
| Always show no-build error for Phase 14 | State management for actual image check comes in Phase 15 |

## Deviations from Plan

None - plan executed exactly as written.

## Issues Encountered

None.

## User Setup Required

None - no external service configuration required.

## Phase 14 Completion Summary

This plan completes Phase 14: Docker Integration. All success criteria met:

| Criterion | Implementation |
|-----------|----------------|
| "Docker not running" error when daemon unavailable | `check-docker!` in docker.clj (14-01) |
| "No image built. Run: aishell build" when no image | `error-no-build` in output.clj (14-05) |
| Build from embedded Dockerfile template | templates.clj + build.clj (14-03) |
| Cache builds via hash | hash.clj + needs-rebuild? in build.clj (14-02, 14-03) |
| Per-project Dockerfile extension | extension.clj (14-04) |

## Module Structure

Phase 14 created the Docker infrastructure:

```
src/aishell/
  docker.clj          # Core: availability, image inspection (14-01)
  docker/
    spinner.clj       # Progress indicator (14-02)
    hash.clj          # SHA-256 for cache (14-02)
    templates.clj     # Embedded Dockerfile content (14-03)
    build.clj         # Build orchestration (14-03)
    extension.clj     # Per-project extensions (14-04)
```

## Next Phase Readiness

Ready for Phase 15 (Build Command):
- All Docker utilities in place
- CLI structure ready for `build` subcommand
- Cache invalidation logic ready
- Extension support ready
- Error messages established

---
*Phase: 14-docker-integration*
*Plan: 05*
*Completed: 2026-01-20*
