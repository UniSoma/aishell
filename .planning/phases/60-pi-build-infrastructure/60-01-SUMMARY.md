---
phase: 60-pi-build-infrastructure
plan: 01
subsystem: infra
tags: [docker, fd-find, npm, pi-coding-agent, harness-volume]

# Dependency graph
requires: []
provides:
  - fd-find package in foundation Docker image with fd symlink
  - Pi coding agent registered as npm harness in volume infrastructure
affects: [60-02-PLAN]

# Tech tracking
tech-stack:
  added: [fd-find, "@mariozechner/pi-coding-agent"]
  patterns: [harness-keys-driven iteration for npm packages]

key-files:
  created: []
  modified:
    - src/aishell/docker/templates.clj
    - src/aishell/docker/volume.clj

key-decisions:
  - "fd symlink created via ln -s because Debian packages fd-find as fdfind but pi expects fd"
  - "Pi registered in harness-npm-packages map following same pattern as Claude/Codex/Gemini"

patterns-established:
  - "New harness registration: add key to harness-keys vector + package to harness-npm-packages map"

requirements-completed: [FOUND-01, FOUND-02, HARNESS-05]

# Metrics
duration: 1min
completed: 2026-02-18
---

# Phase 60 Plan 01: Foundation Image fd-find and Pi Harness Registration Summary

**fd-find added to foundation Dockerfile with fd symlink, pi coding agent registered as npm harness in volume infrastructure**

## Performance

- **Duration:** 1 min
- **Started:** 2026-02-18T01:53:20Z
- **Completed:** 2026-02-18T01:54:35Z
- **Tasks:** 1
- **Files modified:** 2

## Accomplishments
- Added fd-find to foundation Docker image apt-get install list (alphabetical order)
- Created /usr/bin/fd symlink to /usr/bin/fdfind for pi compatibility
- Registered :pi in harness-keys vector for deterministic hash computation
- Added @mariozechner/pi-coding-agent to harness-npm-packages map

## Task Commits

Each task was committed atomically:

1. **Task 1: Add fd-find to foundation image and register pi in volume infrastructure** - `91d905e` (feat)

## Files Created/Modified
- `src/aishell/docker/templates.clj` - Added fd-find package and fd symlink RUN command to base Dockerfile
- `src/aishell/docker/volume.clj` - Added :pi to harness-keys and @mariozechner/pi-coding-agent to harness-npm-packages

## Decisions Made
- Used `ln -s /usr/bin/fdfind /usr/bin/fd` because Debian packages fd-find as fdfind but pi expects the `fd` command name
- Pi follows same npm harness pattern as Claude/Codex/Gemini -- no special installation logic needed

## Deviations from Plan

None - plan executed exactly as written.

Note: Verification step 7 referenced a non-existent `check-dockerfile-stale` function. The stale detection mechanism works inherently through template string hashing -- changing the Dockerfile template string means the hash differs from previously built images.

## Issues Encountered
None.

## User Setup Required
None - no external service configuration required.

## Next Phase Readiness
- Foundation image template updated, ready for Plan 02 (pi config flags, launch commands, entrypoint alias)
- Volume infrastructure knows how to install pi, hash computation includes :pi when enabled
- `aishell update --force` will rebuild foundation image with fd-find on next run

## Self-Check: PASSED

- FOUND: src/aishell/docker/templates.clj
- FOUND: src/aishell/docker/volume.clj
- FOUND: 60-01-SUMMARY.md
- FOUND: commit 91d905e

---
*Phase: 60-pi-build-infrastructure*
*Completed: 2026-02-18*
