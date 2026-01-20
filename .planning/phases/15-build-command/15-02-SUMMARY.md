---
phase: 15-build-command
plan: 02
subsystem: cli
tags: [babashka, cli, flag-parsing, semver, validation]

requires:
  - phase: 15-01
    provides: state.clj module with read/write functions
  - phase: 14-docker-integration
    provides: build.clj with build-base-image function

provides:
  - Build subcommand wired to CLI dispatch
  - Flag parsing for --with-claude, --with-opencode
  - Version validation (semver + dangerous chars)
  - State persistence after build
  - Harness version display in build output

affects: [16-run-command, 17-update-command]

tech-stack:
  added: []
  patterns:
    - Flag parsing without coercion for optional values
    - Boolean true check for CLI flags without values

key-files:
  created: []
  modified:
    - src/aishell/cli.clj
    - src/aishell/docker/build.clj

key-decisions:
  - "No :coerce :string for optional value flags (babashka.cli returns boolean true for flags without values)"
  - "parse-with-flag handles both boolean true and string values"

patterns-established:
  - "Optional value flags: Check (true? value) before string checks"
  - "Harness version output: format-harness-line helper"

duration: 3min
completed: 2026-01-20
---

# Phase 15 Plan 02: Build Subcommand Summary

**Build subcommand with --with-claude/--with-opencode flag parsing, semver validation, and state persistence to ~/.aishell/state.edn**

## Performance

- **Duration:** 3 min
- **Started:** 2026-01-20T19:29:03Z
- **Completed:** 2026-01-20T19:32:20Z
- **Tasks:** 3
- **Files modified:** 2

## Accomplishments

- Wired build subcommand to CLI dispatch with flag parsing
- Added semver validation and dangerous character detection
- State persistence after successful build
- Enhanced build output showing harness versions

## Task Commits

Each task was committed atomically:

1. **Task 1: Add build subcommand to cli.clj** - `af9420b` (feat)
2. **Task 1 fix: Handle boolean true from CLI** - `ab4457f` (fix)
3. **Task 2: Enhance build output with harness versions** - `6661809` (feat)

*Task 3 was verification only - no code changes*

## Files Created/Modified

- `src/aishell/cli.clj` - Build subcommand handler with flag parsing, validation, state persistence
- `src/aishell/docker/build.clj` - Enhanced build output showing harness versions

## Decisions Made

- **No :coerce :string for optional value flags:** babashka.cli returns boolean `true` for flags without values (e.g., `--with-claude`), which cannot be coerced to string. Solution: Don't use :coerce and handle both boolean and string in parse-with-flag.
- **parse-with-flag handles both types:** Check `(true? value)` before string equality checks

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] Fixed flag coercion error for --with-claude without value**
- **Found during:** Task 2 verification
- **Issue:** `./aishell.clj build --with-claude` failed with "cannot transform (implicit) true to string"
- **Fix:** Removed `:coerce :string` from build-spec, added `(true? value)` check in parse-with-flag
- **Files modified:** src/aishell/cli.clj
- **Verification:** `./aishell.clj build --help` and `--with-claude="invalid"` validation work correctly
- **Committed in:** ab4457f

---

**Total deviations:** 1 auto-fixed (blocking)
**Impact on plan:** Bug fix necessary for basic functionality. No scope creep.

## Issues Encountered

- Docker not available in execution environment, so end-to-end build tests could not be run
- Verified all non-Docker tests pass (help, validation, state module functions)

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness

- Build subcommand complete and wired
- State persistence working (verified via state module tests)
- Ready for run command (Phase 16) to read state and launch containers
- Ready for update command (Phase 17) to rebuild with --no-cache

---
*Phase: 15-build-command*
*Completed: 2026-01-20*
