---
phase: 07-nodejs-and-clojure-tooling
plan: 01
subsystem: infra
tags: [nodejs, npm, npx, babashka, clojure, dockerfile, multi-stage-build]

# Dependency graph
requires:
  - phase: 01-core-container-foundation
    provides: base Dockerfile heredoc structure in aishell
provides:
  - Node.js 24 LTS runtime with npm and npx
  - Babashka fast Clojure scripting runtime
  - Multi-stage build pattern for efficient binary copying
affects: [future-tooling-additions, aishell-base-image]

# Tech tracking
tech-stack:
  added: [node.js-24-lts, npm, npx, babashka-1.12.214]
  patterns: [multi-stage-dockerfile-builds, static-binary-downloads]

key-files:
  created: []
  modified: [aishell]

key-decisions:
  - "Multi-stage build from node:24-bookworm-slim for Node.js binaries"
  - "Babashka static binary for container compatibility (no glibc dependencies)"
  - "Removed bbin after user feedback (requires Java runtime)"

patterns-established:
  - "Multi-stage builds: Use official images as build stage sources for binaries"
  - "Static binaries: Prefer *-static variants for container tools"

# Metrics
duration: 8min
completed: 2026-01-18
---

# Phase 7 Plan 01: Node.js and Babashka Base Image Summary

**Node.js 24 LTS and Babashka 1.12.214 installed via multi-stage Dockerfile build for JavaScript and Clojure scripting in containers**

## Performance

- **Duration:** 8 min
- **Started:** 2026-01-18T10:00:00Z
- **Completed:** 2026-01-18T10:08:00Z
- **Tasks:** 2/2
- **Files modified:** 1

## Accomplishments

- Node.js 24 LTS available via multi-stage copy from official node image
- npm and npx symlinked for package management and script execution
- Babashka 1.12.214 static binary for fast Clojure scripting
- All tools accessible to non-root container user after gosu switch
- --rebuild flag properly forces base image rebuild with --no-cache

## Task Commits

Each task was committed atomically:

1. **Task 1: Update Dockerfile heredoc** - `3b2901b` (feat) - Initial Node.js, Babashka, bbin installation
2. **Task 1: Fix --rebuild** - `c503c22` (fix) - Ensure --rebuild forces base image rebuild with --no-cache
3. **Task 1: Skip bbin version** - `2bd6da0` (fix) - Skip bbin version check during build (requires Java)
4. **Task 1: Remove bbin** - `64e56de` (refactor) - Remove bbin from base image after user feedback

**Checkpoint approved:** User verified Node.js and Babashka work correctly

## Files Created/Modified

- `aishell` - Updated write_dockerfile() with multi-stage build, Node.js copy, and Babashka installation

## Decisions Made

- **Multi-stage build from node:24-bookworm-slim:** Most efficient way to get Node.js binaries without installing from source or using apt
- **Babashka static binary:** The *-static variant has no glibc dependencies, more reliable in containers
- **Removed bbin:** Originally planned but removed after user feedback - bbin requires Java runtime which adds significant image size
- **ARG placement:** BABASHKA_VERSION defined early with other ARGs for consistency

## Deviations from Plan

### Changes from Original Plan

**1. Removed bbin installation**
- **Reason:** User feedback indicated bbin requires Java runtime
- **Impact:** Reduces image size, removes Java dependency
- **Resolution:** Babashka (bb) alone provides sufficient scripting capability
- **Committed in:** 64e56de

**2. Fixed --rebuild flag behavior**
- **Found during:** Testing
- **Issue:** --rebuild wasn't forcing fresh build with --no-cache
- **Fix:** Added docker build --no-cache when --rebuild flag passed
- **Committed in:** c503c22

---

**Total deviations:** 2 (1 user-requested removal, 1 bug fix)
**Impact on plan:** bbin removal reduces scope but improves image; --rebuild fix ensures proper testing workflow

## Issues Encountered

- bbin version check failed during build (requires Java) - resolved by removing bbin entirely per user feedback
- --rebuild flag needed --no-cache to properly test Dockerfile changes - fixed

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness

- Node.js and Babashka ready for use in containers
- Future tooling additions can follow multi-stage build pattern
- Phase 7 complete (single plan)

---
*Phase: 07-nodejs-and-clojure-tooling*
*Completed: 2026-01-18*
