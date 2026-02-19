---
phase: 66-global-base-image-customization
plan: 01
subsystem: docker
tags: [docker, base-image, three-tier, customization, dockerfile]

# Dependency graph
requires:
  - phase: 36-volume-mounted-harness-tools
    provides: Foundation image build, volume-mounted harness tools
provides:
  - "aishell:base intermediate image layer module (docker/base.clj)"
  - "Global Dockerfile detection and staleness check"
  - "Setup and update integration with base image ensure"
affects: [66-02, 66-03, 66-04]

# Tech tracking
tech-stack:
  added: []
  patterns: [three-tier-image-chain, tag-alias-pattern, label-based-staleness]

key-files:
  created: [src/aishell/docker/base.clj]
  modified: [src/aishell/cli.clj]

key-decisions:
  - "Base image uses label-based staleness detection (same pattern as extension images)"
  - "Tag-alias path (docker tag) used when no global Dockerfile exists"
  - "Hard-stop on build failure via output/error (user explicitly created Dockerfile)"

patterns-established:
  - "Three-tier image chain: aishell:foundation -> aishell:base -> aishell:ext-{hash}"
  - "Tag-alias pattern: docker tag for image aliasing when no customization needed"

requirements-completed: [BASE-01, BASE-02, BASE-03, BASE-04, BASE-06]

# Metrics
duration: 2min
completed: 2026-02-18
---

# Phase 66 Plan 01: Core Base Image Module Summary

**Three-tier image chain module with global Dockerfile detection, custom build, tag-alias fallback, and setup/update integration**

## Performance

- **Duration:** 2 min
- **Started:** 2026-02-18T23:45:58Z
- **Completed:** 2026-02-18T23:48:14Z
- **Tasks:** 2
- **Files modified:** 2

## Accomplishments
- Created `docker/base.clj` module with 9 functions: base-image-tag, global-dockerfile-path, global-dockerfile-exists?, global-dockerfile-hash, tag-foundation-as-base, needs-base-rebuild?, build-base-image, ensure-base-image, and has-custom-base-label?
- Integrated base image ensure into both `handle-setup` (after foundation build) and `handle-update` (force or quiet mode)
- All namespaces load cleanly under Babashka with no SCI resolution errors

## Task Commits

Each task was committed atomically:

1. **Task 1: Create docker/base.clj module** - `10504d1` (feat)
2. **Task 2: Integrate base image into setup and update commands** - `25d8995` (feat)

## Files Created/Modified
- `src/aishell/docker/base.clj` - Global base image customization layer: detection, build, tag-alias, staleness check
- `src/aishell/cli.clj` - Added base require and ensure-base-image calls in setup/update handlers

## Decisions Made
- Base image uses same label-based staleness pattern as extension images (dockerfile hash + foundation ID labels)
- Tag-alias via `docker tag` when no global Dockerfile exists (ensures `aishell:base` always available)
- Build uses spinner for non-verbose mode, matching existing extension build UX
- Hard-stop on build failure (output/error) since user explicitly created the Dockerfile

## Deviations from Plan

None - plan executed exactly as written.

## Issues Encountered
None

## User Setup Required
None - no external service configuration required.

## Next Phase Readiness
- `docker/base.clj` module ready for run-path integration (Plan 66-02)
- `ensure-base-image` entry point available for lazy build on container run
- Extension module `validate-base-tag` removal and `FROM aishell:base` acceptance ready for Plan 66-03

## Self-Check: PASSED

- FOUND: src/aishell/docker/base.clj
- FOUND: 66-01-SUMMARY.md
- FOUND: commit 10504d1
- FOUND: commit 25d8995

---
*Phase: 66-global-base-image-customization*
*Completed: 2026-02-18*
