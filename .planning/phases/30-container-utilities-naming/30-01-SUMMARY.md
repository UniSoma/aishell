---
phase: 30-container-utilities-naming
plan: 01
subsystem: infra
tags: [docker, clojure, babashka, sha256, naming]

# Dependency graph
requires:
  - phase: 29-performance
    provides: Existing docker namespaces, hash utilities, CLI dispatch pattern
provides:
  - Container naming utilities with deterministic SHA-256 hashing
  - Docker state query functions (exists, running, list)
  - --name CLI flag support for container name overrides
  - Collision-resistant 8-char hex naming (2^32 space, <0.02% collision at 100 projects)
affects: [31-detached-mode, 32-attach-command, 33-ps-command, 34-cleanup]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "Deterministic container naming: aishell-{8-char-hash}-{name}"
    - "Path canonicalization with fs/canonicalize for symlink resolution"
    - "Docker query pattern: ps --filter with anchored regex for exact matching"

key-files:
  created:
    - src/aishell/docker/naming.clj
  modified:
    - src/aishell/cli.clj
    - src/aishell/run.clj
    - src/aishell/output.clj

key-decisions:
  - "Use 8-char SHA-256 hash (2^32 space) for collision probability <0.02% at 100 projects"
  - "Canonicalize paths with fs/canonicalize to ensure same hash for symlinks/trailing slashes"
  - "Extract --name flag before dispatch (same pattern as --unsafe) to prevent pass-through to harness"
  - "Default container name equals harness name (claude/opencode/codex/gemini) or 'shell' for interactive mode"

patterns-established:
  - "naming/container-name: Single source of truth for name generation"
  - "naming/validate-container-name!: 63-char Docker limit enforcement (name portion max 46 chars)"
  - "Docker ps filtering: Use ^anchor$ regex to avoid substring matching"

# Metrics
duration: 3min
completed: 2026-01-31
---

# Phase 30 Plan 01: Container Utilities & Naming Summary

**Deterministic container naming with 8-char SHA-256 hashing, Docker state queries, and --name CLI flag support**

## Performance

- **Duration:** 3 min
- **Started:** 2026-01-31T12:56:15Z
- **Completed:** 2026-01-31T12:59:20Z
- **Tasks:** 2
- **Files modified:** 4

## Accomplishments

- Created naming.clj with 8 utility functions for container naming and Docker state queries
- Wired --name CLI flag into dispatch and run-container flow
- Established deterministic naming pattern: aishell-{8-char-hash}-{name}
- Implemented path normalization with fs/canonicalize for consistent hashing across symlinks
- Added collision probability documentation (<0.02% at 100 projects)

## Task Commits

Each task was committed atomically:

1. **Task 1: Create naming.clj namespace with all utility functions** - `e3689c3` (feat)
2. **Task 2: Wire --name flag into CLI dispatch and run-container** - `167daf3` (feat)

## Files Created/Modified

- `src/aishell/docker/naming.clj` - Container naming utilities and Docker state queries (project-hash, container-name, validate-container-name!, container-exists?, container-running?, remove-container-if-stopped!, ensure-name-available!, list-project-containers)
- `src/aishell/cli.clj` - Added naming require, --name extraction before dispatch, pass container-name to run-container
- `src/aishell/run.clj` - Added naming require, resolve container-name in run-container, verbose logging for verification
- `src/aishell/output.clj` - Added "attach" and "ps" to known-commands for future command suggestions

## Decisions Made

**1. 8-char hash truncation (not 12-char from existing hash/compute-hash)**
- Rationale: 2^32 space (4.3 billion hashes) provides <0.02% collision probability at 100 projects via birthday paradox analysis
- Trade-off: Accepted minimal collision risk for shorter, more readable container names
- Implementation: Use existing hash/compute-hash (returns 12 chars), take first 8 with subs

**2. Default name = harness name (not "default" or random)**
- Rationale: Intuitive behavior - `aishell claude` creates `aishell-HASH-claude`
- User benefit: Easy to identify which harness is running in `docker ps` output
- Special case: Shell mode uses "shell" as default name

**3. Extract --name before dispatch (not in CLI spec)**
- Rationale: Consistent with --unsafe pattern, prevents pass-through to harness
- Benefit: Works with any harness command position: `aishell --name reviewer claude` and `aishell claude --name reviewer`
- Implementation: Manual extraction before case statement routing

## Deviations from Plan

None - plan executed exactly as written.

## Issues Encountered

None - all functions implemented successfully with expected behavior.

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness

**Phase 31 (Detached Mode) is ready:**
- Container naming utilities available for --name flag support
- ensure-name-available! ready for pre-flight collision checking
- remove-container-if-stopped! ready for cleanup before docker run --name
- container-exists? and container-running? ready for state verification

**Phase 32 (Attach Command) is ready:**
- container-name resolution available for attach target lookup
- container-running? available for attach pre-flight validation
- list-project-containers ready for "no container found" error messages

**Phase 33 (PS Command) is ready:**
- list-project-containers provides container enumeration with status/created metadata
- project-hash available for filtering project-specific containers

**No blockers or concerns.**

---
*Phase: 30-container-utilities-naming*
*Completed: 2026-01-31*
