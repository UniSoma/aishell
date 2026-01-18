---
phase: 04-project-customization
plan: 01
subsystem: cli
tags: [docker, dockerfile, extension, caching, bash]

# Dependency graph
requires:
  - phase: 03-harness-integration
    provides: Base image with harnesses installed
provides:
  - Project extension support via .aishell/Dockerfile
  - Automatic extended image building and caching
  - --rebuild and --build-arg CLI flags
affects: [05-distribution, project-specific-tooling]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - Hash-based image tagging for project isolation
    - Label-based base image ID tracking for cache invalidation

key-files:
  created: []
  modified:
    - aishell

key-decisions:
  - "Hash project path for extended image tag (aishell:ext-{hash12})"
  - "Use docker labels (aishell.base.id) to track base image version"
  - "Always run docker build - let Docker handle caching"
  - "Project directory as build context (enables COPY from project)"

patterns-established:
  - "Extension detection: check for .aishell/Dockerfile before container run"
  - "Cache invalidation: rebuild when base image ID changes"

# Metrics
duration: 8min
completed: 2026-01-18
---

# Phase 4 Plan 1: Project Extension Support Summary

**Project extension via .aishell/Dockerfile with automatic building, hash-based tagging, and base image change detection**

## Performance

- **Duration:** 8 min (across checkpoint interaction)
- **Started:** 2026-01-17
- **Completed:** 2026-01-18T00:41:12Z
- **Tasks:** 2 (1 auto + 1 checkpoint verification)
- **Files modified:** 1

## Accomplishments

- Projects can now extend base aishell image with custom dependencies
- Extended images are cached and tagged by project path hash
- Base image changes automatically trigger extended image rebuilds
- New --rebuild flag forces fresh builds
- New --build-arg flag passes arguments to project Dockerfiles

## Task Commits

Each task was committed atomically:

1. **Task 1: Add project extension support to aishell** - `612fcd2` (feat)
2. **Task 2: Verify project extension workflow** - checkpoint (human verification approved)

**Plan metadata:** (this commit)

## Files Created/Modified

- `aishell` - Added extension detection, building, caching logic, and new CLI flags

## Decisions Made

1. **Hash-based image tags** - Extended images tagged as `aishell:ext-{hash12}` where hash is first 12 chars of sha256 of project path. Provides isolation between projects while keeping tags deterministic.

2. **Label-based cache tracking** - Store `aishell.base.id` label on extended images to detect when base image has changed. This triggers automatic rebuilds when base is updated.

3. **Always run docker build** - Rather than complex cache logic, always invoke `docker build` and let Docker's layer cache handle efficiency. Only pass `--no-cache` when force rebuild or base changed.

4. **Project directory as context** - Build context is the project directory (not .aishell/), enabling COPY commands to reference project files in extension Dockerfiles.

## Deviations from Plan

None - plan executed exactly as written.

## Issues Encountered

None.

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness

- Extension mechanism complete and verified
- Ready for Phase 5: Distribution
- No blockers or concerns

---
*Phase: 04-project-customization*
*Completed: 2026-01-18*
