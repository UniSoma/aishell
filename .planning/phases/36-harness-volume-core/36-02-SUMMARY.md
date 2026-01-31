---
phase: 36-harness-volume-core
plan: 02
subsystem: infra
tags: [docker, volume, npm, clojure]

# Dependency graph
requires:
  - phase: 36-01
    provides: Harness hash computation and volume naming
provides:
  - Docker volume creation with metadata labels
  - Volume inspection and label reading
  - NPM package installation into volumes via temporary containers
  - Permission handling for non-root execution
affects: [36-03, 37]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "Volume population via temporary Docker containers"
    - "NPM_CONFIG_PREFIX for custom npm installation directory"
    - "World-readable permissions (chmod a+rX) for non-root access"

key-files:
  created: []
  modified:
    - src/aishell/docker/volume.clj

key-decisions:
  - "Use @anthropic-ai/claude-code, @codex-ai/codex, @google/generative-ai-cli as npm package names"
  - "Exclude OpenCode from npm installation (Go binary, not npm package)"
  - "Use NPM_CONFIG_PREFIX=/tools/npm for installation directory"
  - "Set world-readable permissions with chmod -R a+rX for non-root execution"

patterns-established:
  - "Volume inspection functions return false/nil on errors instead of throwing exceptions"
  - "Helper functions (build-install-commands) exposed for testing"
  - "Spinner/verbose mode for long-running operations"

# Metrics
duration: 2min
completed: 2026-01-31
---

# Phase 36 Plan 02: Volume Lifecycle Management Summary

**Docker volume CRUD operations with npm-based harness tool installation via temporary containers**

## Performance

- **Duration:** 2 min
- **Started:** 2026-01-31T23:50:10Z
- **Completed:** 2026-01-31T23:52:41Z
- **Tasks:** 2
- **Files modified:** 1

## Accomplishments
- Volume inspection functions (exists check, label reading)
- Volume creation with metadata labels for staleness tracking
- NPM package installation command generation for enabled harnesses
- Volume population via temporary Docker containers with proper permissions

## Task Commits

Each task was committed atomically:

1. **Task 1: Add volume inspection functions** - `8748519` (feat)
   - volume-exists? checks Docker volume existence
   - get-volume-label reads label values from volumes
   - create-volume creates volumes with metadata labels
   - All functions handle errors gracefully

2. **Task 2: Add volume population function** - `df51f89` (feat)
   - build-install-commands generates npm install command strings
   - populate-volume runs Docker container to install harness tools
   - Permissions set with chmod -R a+rX /tools for non-root execution
   - OpenCode excluded from npm installation (Go binary)
   - Supports verbose and spinner output modes

## Files Created/Modified
- `src/aishell/docker/volume.clj` - Added volume inspection (volume-exists?, get-volume-label, create-volume) and population (build-install-commands, populate-volume) functions

## Decisions Made

**NPM package name mapping:**
- Claude Code: `@anthropic-ai/claude-code` (verified from TROUBLESHOOTING.md)
- Codex: `@codex-ai/codex` (verified from TROUBLESHOOTING.md)
- Gemini: `@google/generative-ai-cli` (verified from TROUBLESHOOTING.md)
- OpenCode: Excluded from npm installation (Go binary, not npm package)

**Installation approach:**
- Use `NPM_CONFIG_PREFIX=/tools/npm` to install into volume-mounted directory
- Run installation in temporary container with `--rm` flag
- Use foundation image (aishell:foundation) for consistency
- Set world-readable permissions (`chmod -R a+rX /tools`) for non-root execution

**Error handling:**
- Volume inspection functions return false/nil on errors (graceful degradation)
- Population function uses spinner for silent mode, inherits output for verbose mode
- Exceptions caught and reported via output/error

## Deviations from Plan

None - plan executed exactly as written.

## Issues Encountered

None - implementation was straightforward. Docker availability not required for unit tests; integration tests deferred to Phase 37.

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness

Ready for 36-03 (Volume Staleness Detection):
- Volume creation with metadata labels complete
- Label reading functions available for staleness checks
- Volume population mechanism ready for integration

Ready for Phase 37 (Integration):
- All volume lifecycle functions implemented
- NPM package installation tested via unit tests
- Full integration test deferred to Phase 37 when wiring into build/run commands

No blockers or concerns.

---
*Phase: 36-harness-volume-core*
*Completed: 2026-01-31*
