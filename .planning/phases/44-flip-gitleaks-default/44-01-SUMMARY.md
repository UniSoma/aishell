---
phase: 44-flip-gitleaks-default
plan: 01
subsystem: cli
tags: [gitleaks, cli, docker, dockerfile]

# Dependency graph
requires:
  - phase: 43-pip-native-install
    provides: CLI flag patterns and naming conventions
provides:
  - Consistent --with-* flag pattern for all build options
  - Gitleaks opt-in via --with-gitleaks flag
  - ARG WITH_GITLEAKS=false Dockerfile default
affects: [documentation, v2.10.0-release]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "All build options use positive --with-* flags (no negative --without-* flags)"

key-files:
  created: []
  modified:
    - src/aishell/cli.clj
    - src/aishell/docker/templates.clj
    - src/aishell/docker/build.clj
    - src/aishell/state.clj

key-decisions:
  - "Flipped Gitleaks from opt-out to opt-in to match all other harness flags"
  - "Default to false when :with-gitleaks key absent from state (opt-in semantics)"

patterns-established:
  - "All build options follow --with-* positive flag pattern"
  - "Boolean flags use (boolean (:flag opts)) for nil->false default"

# Metrics
duration: 1.5min
completed: 2026-02-05
---

# Phase 44 Plan 01: Flip Gitleaks Default Summary

**Gitleaks now opt-in via --with-gitleaks flag, establishing consistent --with-* pattern across all build options**

## Performance

- **Duration:** 1.5 min
- **Started:** 2026-02-05T19:29:41Z
- **Completed:** 2026-02-05T19:31:13Z
- **Tasks:** 2
- **Files modified:** 4

## Accomplishments
- Flipped ARG WITH_GITLEAKS default from true to false in Dockerfile template
- Replaced --without-gitleaks with --with-gitleaks in CLI spec and help
- Updated default value logic to opt-in (nil -> false, true -> true)
- All downstream consumers work without modification (already read :with-gitleaks positively)

## Task Commits

Each task was committed atomically:

1. **Task 1: Flip Dockerfile ARG default and update build docstring** - `a575c93` (feat)
2. **Task 2: Replace --without-gitleaks with --with-gitleaks in CLI and update state docstring** - `4208de6` (feat)

## Files Created/Modified
- `src/aishell/docker/templates.clj` - Changed ARG WITH_GITLEAKS=true to false
- `src/aishell/docker/build.clj` - Updated docstring to "default false, opt-in"
- `src/aishell/cli.clj` - Replaced :without-gitleaks with :with-gitleaks, updated help, changed defaults to false
- `src/aishell/state.clj` - Updated docstring to reflect opt-in default

## Decisions Made
- **Positive flag pattern:** Established that all build options use --with-* flags (no negative flags)
- **Opt-in semantics:** When :with-gitleaks is absent from state, default to false (not installed)
- **Backward compatibility:** Existing state files with :with-gitleaks true continue to work
- **Help discoverability:** When no build exists yet, all harnesses still shown in help (including gitleaks)

## Deviations from Plan

None - plan executed exactly as written.

## Issues Encountered

None.

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness

- Gitleaks flip complete and verified
- All verification checks pass (no remnants of old flag, defaults correct, downstream unchanged)
- Ready for Phase 45 (documentation updates)
- Users who run `aishell setup` will now get base image without Gitleaks by default
- Users who want Gitleaks must explicitly pass `--with-gitleaks` flag

---
*Phase: 44-flip-gitleaks-default*
*Completed: 2026-02-05*
