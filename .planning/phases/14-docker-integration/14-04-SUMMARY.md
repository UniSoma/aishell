---
phase: 14-docker-integration
plan: 04
subsystem: docker
tags: [docker, extension, dockerfile, cache-invalidation, per-project]

# Dependency graph
requires:
  - phase: 14-01
    provides: docker-availability, image-inspection, get-image-label
  - phase: 14-02
    provides: hash utility, spinner utility
provides:
  - Project Dockerfile detection (project-dockerfile)
  - Extended image cache invalidation (needs-extended-rebuild?)
  - Extended image building (build-extended-image)
affects: [14-05, run-command, image-hierarchy]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "Dual cache invalidation (base image ID + extension Dockerfile hash)"
    - "Label-based dependency tracking for rebuild decisions"

key-files:
  created:
    - src/aishell/docker/extension.clj
  modified: []

key-decisions:
  - "Track both base image ID and extension Dockerfile hash in labels"
  - "Use project path hash for deterministic extended tag naming"
  - "Return nil instead of throwing when no extension Dockerfile exists"

patterns-established:
  - "Label naming: aishell.base.id, aishell.extension.hash"
  - "Extended tag format: aishell:ext-{12-char-hash}"

# Metrics
duration: 1min
completed: 2026-01-20
---

# Phase 14 Plan 04: Project Extension Support Summary

**Per-project Dockerfile extension support with dual cache invalidation for base image changes and extension Dockerfile changes**

## Performance

- **Duration:** 1 min
- **Started:** 2026-01-20T18:09:37Z
- **Completed:** 2026-01-20T18:10:39Z
- **Tasks:** 2
- **Files created:** 1

## Accomplishments

- Created extension module with project Dockerfile detection
- Implemented dual cache invalidation strategy
- Built extended images track their dependencies via labels
- Silent operation when no extension exists (returns nil)

## Task Commits

Both tasks implemented in single file (extension.clj):

| Commit | Type | Description |
|--------|------|-------------|
| dc9c4cd | feat | Per-project Dockerfile extension support |

## Files Created/Modified

- `src/aishell/docker/extension.clj` (149 lines) - Extension handling module
  - `base-image-id-label` - Label constant for base image tracking
  - `extension-hash-label` - Label constant for Dockerfile hash tracking
  - `project-dockerfile` - Detect .aishell/Dockerfile
  - `get-base-image-id` - Get Docker image ID
  - `compute-extended-tag` - Generate project-specific tag
  - `get-extension-dockerfile-hash` - Hash extension Dockerfile content
  - `needs-extended-rebuild?` - Check if rebuild needed (3-arity)
  - `build-extended-image` - Build with proper labels

## Decisions Made

| Decision | Rationale |
|----------|-----------|
| Track base image ID + extension hash | Rebuilds on either change without unnecessary rebuilds |
| Use project path hash for tag | Deterministic, allows multiple projects with extensions |
| Return nil when no extension | Caller decides behavior, matches bash implementation |

## Deviations from Plan

None - plan executed exactly as written.

## Issues Encountered

None.

## Verification Results

```clojure
;; Project Dockerfile detection
(ext/project-dockerfile ".") ;=> nil (no .aishell/Dockerfile in harness repo)

;; Extended tag computation
(ext/compute-extended-tag "/home/user/project") ;=> "aishell:ext-9dad1e4e08b0"

;; Rebuild detection (missing image)
(ext/needs-extended-rebuild? "fake:tag" "aishell:base") ;=> true

;; Extension hash label constant
ext/extension-hash-label ;=> "aishell.extension.hash"
```

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness

Ready for 14-05 (run command) which will use:
- `project-dockerfile` to detect extensions
- `needs-extended-rebuild?` to check cache validity
- `build-extended-image` to build when needed
- Extended tag passed to docker run instead of base

---
*Phase: 14-docker-integration*
*Completed: 2026-01-20*
