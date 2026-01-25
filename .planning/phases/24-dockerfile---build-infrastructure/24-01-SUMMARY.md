---
phase: 24-dockerfile---build-infrastructure
plan: 01
subsystem: infra
tags: [docker, cli, codex, gemini, npm, build-args]

# Dependency graph
requires:
  - phase: 13-bb-rewrite
    provides: CLI build-spec pattern and Babashka implementation
  - phase: 14-dynamic-harness-config
    provides: Harness configuration approach
provides:
  - Codex CLI installation option (--with-codex flag)
  - Gemini CLI installation option (--with-gemini flag)
  - Version pinning for Codex/Gemini harnesses
  - Build infrastructure for npm-based harnesses
affects: [25-harness-runtime, future-harness-additions]

# Tech tracking
tech-stack:
  added: []
  patterns: ["npm global install pattern for CLI harnesses"]

key-files:
  created: []
  modified:
    - src/aishell/docker/templates.clj
    - src/aishell/cli.clj
    - src/aishell/docker/build.clj

key-decisions:
  - "Codex and Gemini follow npm global install pattern (same as Claude)"
  - "ARG naming convention: WITH_{HARNESS} and {HARNESS}_VERSION"

patterns-established:
  - "New harness integration pattern: ARG in Dockerfile + CLI flag + build.clj support + state persistence"

# Metrics
duration: 3min
completed: 2026-01-25
---

# Phase 24 Plan 01: Codex & Gemini Build Infrastructure Summary

**Codex and Gemini CLI harnesses with npm global install, version pinning, and CLI flag support**

## Performance

- **Duration:** 3 min
- **Started:** 2026-01-25T01:25:50Z
- **Completed:** 2026-01-25T01:28:23Z
- **Tasks:** 2
- **Files modified:** 3

## Accomplishments
- Added Codex and Gemini as installable harness options
- Enabled version pinning for both new harnesses
- Integrated new flags into CLI help and build workflow
- Extended state persistence to track new harness configuration

## Task Commits

Each task was committed atomically:

1. **Task 1: Add Codex and Gemini installation blocks to Dockerfile template** - `a50b77f` (feat)
2. **Task 2: Add --with-codex and --with-gemini CLI flags** - `9bf079f` (feat)

**Auto-fix (build.clj support):** `af59177` (feat)

## Files Created/Modified
- `src/aishell/docker/templates.clj` - Added ARGs and RUN blocks for Codex/Gemini npm installs
- `src/aishell/cli.clj` - Added CLI flags, version validation, state persistence for new harnesses
- `src/aishell/docker/build.clj` - Added build-arg construction and version change detection

## Decisions Made
- Codex and Gemini use npm global install pattern (consistent with Claude Code)
- ARG naming follows existing convention: WITH_{HARNESS}=false and {HARNESS}_VERSION=""
- Version validation reuses existing semver-pattern validation

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 2 - Missing Critical] Added Codex/Gemini support to build.clj**
- **Found during:** Task 2 completion verification
- **Issue:** build.clj lacked parameter handling for new harnesses - would fail when CLI passed :with-codex/:with-gemini
- **Fix:** Extended version-changed?, build-docker-args, and build output to handle Codex/Gemini
- **Files modified:** src/aishell/docker/build.clj
- **Verification:** All parameters flow from CLI → build.clj → Docker build args
- **Committed in:** af59177

---

**Total deviations:** 1 auto-fixed (1 missing critical)
**Impact on plan:** Auto-fix was essential for end-to-end functionality. Plan correctly identified CLI and Dockerfile changes but build.clj is the integration point that connects them. No scope creep.

## Issues Encountered
None

## User Setup Required
None - no external service configuration required.

## Next Phase Readiness
- Build infrastructure complete for Codex and Gemini harnesses
- Ready for runtime integration (harness-specific run commands)
- Ready for testing with actual Codex/Gemini CLI packages

---
*Phase: 24-dockerfile---build-infrastructure*
*Completed: 2026-01-25*
