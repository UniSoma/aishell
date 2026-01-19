---
phase: 11-code-hardening
plan: 02
subsystem: validation
tags: [bash, input-validation, semver, docker, security-warnings, zombie-reaping]

# Dependency graph
requires:
  - phase: 11-01
    provides: Consolidated trap/cleanup infrastructure
provides:
  - Version string validation with semver regex and shell metachar blocklist
  - Port mapping IP binding support (127.0.0.1:host:container format)
  - HOME environment fallback handling
  - Zombie process reaping via --init flag
  - Security warnings for dangerous DOCKER_ARGS patterns
affects: [phase-12, future-security-audits]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "Defense in depth: blocklist + allowlist for input validation"
    - "Early validation: validate HOME before any operations"
    - "Docker --init for zombie reaping instead of custom PID 1 handling"

key-files:
  created: []
  modified:
    - aishell

key-decisions:
  - "Validate versions at all entry points (parse_args, do_build, do_update)"
  - "Use glob patterns for dangerous args detection instead of regex"
  - "Add --init early in docker_args for consistent zombie reaping"

patterns-established:
  - "Input validation: Check for dangerous chars first, then format validation"
  - "Security warnings: Non-blocking warnings with detailed explanations"

# Metrics
duration: 3min
completed: 2026-01-19
---

# Phase 11 Plan 02: Input Validation & Security Summary

**Version validation with semver regex and shell metachar blocklist, port IP binding support, HOME fallback, --init zombie reaping, and dangerous DOCKER_ARGS warnings**

## Performance

- **Duration:** 3 min
- **Started:** 2026-01-19T16:35:35Z
- **Completed:** 2026-01-19T16:38:34Z
- **Tasks:** 2
- **Files modified:** 1

## Accomplishments

- Added validate_version() with defense-in-depth (dangerous chars blocklist + semver format)
- Added validate_home() with passwd lookup fallback and /tmp directory creation
- Updated build_port_args() regex to accept IP:host:container format (VALID-01)
- Added warn_dangerous_docker_args() for --privileged, docker.sock, elevated caps, disabled profiles
- Wired validation calls at all entry points (parse_args, do_build, do_update)
- Added --init flag to docker_args for zombie process reaping via tini

## Task Commits

Each task was committed atomically:

1. **Task 1: Add validation functions** - `5e82c36` (feat)
2. **Task 2: Apply validation and add --init flag** - `00997f9` (feat)

## Files Created/Modified

- `aishell` - Input validation functions, security warnings, --init flag, wired validation calls

## Decisions Made

- **Validation at all entry points:** Version validation added to parse_args(), do_build(), and do_update() to catch invalid input regardless of how the user invokes version flags
- **Glob patterns for dangerous args:** Used bash glob patterns (`*"--privileged"*`) instead of regex for dangerous args detection - simpler and sufficient for substring matching
- **Early HOME validation:** validate_home() called as first line of main() to ensure HOME is valid before any path operations

## Deviations from Plan

None - plan executed exactly as written.

## Issues Encountered

None.

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness

- Phase 11 (Code Hardening) complete with all requirements satisfied:
  - VALID-01: Port mapping IP binding (127.0.0.1:8080:80 format)
  - VALID-02: Version string validation (blocks shell metacharacters)
  - VALID-03: HOME fallback handling (passwd lookup + /tmp fallback)
  - ROBUST-03: --init flag for zombie process reaping
  - SEC-01: Dangerous DOCKER_ARGS warnings
- Ready for Phase 12 (Maintenance & Documentation)
- No blockers or concerns

---
*Phase: 11-code-hardening*
*Completed: 2026-01-19*
