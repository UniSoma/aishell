---
phase: 35-foundation-image-split
plan: 01
subsystem: infra
tags: [docker, clojure, babashka, foundation-image, build-system]

# Dependency graph
requires:
  - phase: 35-research
    provides: Architecture investigation showing 2-tier volume-based injection approach
provides:
  - Foundation Dockerfile template stripped of harness installations
  - aishell:foundation image tag replacing aishell:base
  - Simplified build orchestration without harness version tracking
  - Stable foundation layer that only rebuilds on system dependency changes
affects: [35-02-volume-injection, 35-03-extension-mount-update, extension-caching]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "Foundation/extension separation pattern for Docker images"
    - "Build-time vs runtime separation for tool installation"

key-files:
  created: []
  modified:
    - src/aishell/docker/templates.clj
    - src/aishell/docker/build.clj
    - src/aishell/cli.clj

key-decisions:
  - "Clean break from aishell:base to aishell:foundation (maintained backward compat alias)"
  - "Keep gitleaks in foundation layer (security infrastructure, not harness)"
  - "Preserve harness flags in state persistence for Phase 36 volume logic"
  - "Remove version-changed? function - foundation has no version dependencies"

patterns-established:
  - "Foundation image contains only system dependencies: Debian, Node.js, babashka, gosu, gitleaks, tmux"
  - "Image tag naming: foundation-image-tag with backward compat alias"
  - "Build functions renamed with -foundation suffix, old names aliased"

# Metrics
duration: 4min
completed: 2026-01-31
---

# Phase 35 Plan 01: Strip Harness from Foundation Summary

**Foundation image now builds with zero harness installations, tagged aishell:foundation, containing only stable system dependencies (Debian, Node.js, babashka, gosu, gitleaks, tmux)**

## Performance

- **Duration:** 4 minutes
- **Started:** 2026-01-31T22:29:41Z
- **Completed:** 2026-01-31T22:33:37Z
- **Tasks:** 2
- **Files modified:** 3

## Accomplishments
- Removed all harness ARGs and installation blocks from Dockerfile template
- Changed image tag from aishell:base to aishell:foundation
- Eliminated harness version tracking from build cache logic
- Simplified build orchestration to only handle system-level options
- Foundation image now only rebuilds when Dockerfile template changes, not when harness versions change

## Task Commits

Each task was committed atomically:

1. **Task 1: Strip harness installations from Dockerfile template** - `ef4a6df` (refactor)
2. **Task 2: Update build orchestration for foundation image** - `fdbddca` (feat)

## Files Created/Modified
- `src/aishell/docker/templates.clj` - Removed harness ARG declarations and installation blocks, changed header to "Foundation Image"
- `src/aishell/docker/build.clj` - Renamed tag to foundation-image-tag, removed version-changed? function, simplified build-docker-args, renamed build-base-image to build-foundation-image
- `src/aishell/cli.clj` - Updated handle-build and handle-update to call build-foundation-image with minimal opts (gitleaks, verbose, force only)

## Decisions Made

**1. Keep gitleaks in foundation layer**
- Rationale: Gitleaks is security infrastructure (secret scanning), not an AI harness tool. It belongs in the stable foundation layer.

**2. Maintain backward compatibility alias**
- Rationale: Phase 36 and other code may reference base-image-tag. Alias prevents breaking changes during transition.

**3. Preserve harness flags in state persistence**
- Rationale: Phase 36 volume injection logic will need harness configuration. State file still tracks which harnesses to install, just not in the foundation image.

**4. Remove version-changed? function entirely**
- Rationale: Foundation has no harness versions to track. Cache invalidation now only checks Dockerfile template hash and force flag.

## Deviations from Plan

None - plan executed exactly as written.

## Issues Encountered

**Parenthesis mismatch during build-foundation-image refactor**
- Issue: Initial edit had unbalanced parentheses in if-not/let/try nesting
- Resolution: Corrected closing parentheses count (needed 4 total for if-not and let blocks)
- Verification: `bb -cp src -e` syntax check passed

## Next Phase Readiness

Foundation image is ready for volume-based harness injection (Phase 36):
- Template stripped of harness content
- Tag changed to aishell:foundation
- Build orchestration simplified
- State persistence still includes harness flags for volume logic
- No blockers

**Next:** Phase 35-02 will implement volume creation and population with harness tools, mounting them at runtime instead of baking into the image.

---
*Phase: 35-foundation-image-split*
*Completed: 2026-01-31*
