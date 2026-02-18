---
phase: 60-pi-build-infrastructure
plan: 02
subsystem: infra
tags: [cli, docker, pi-coding-agent, env-passthrough, state-persistence]

# Dependency graph
requires:
  - phase: 60-01
    provides: fd-find in foundation image, pi registered as npm harness in volume infrastructure
provides:
  - --with-pi CLI flag for aishell setup with optional version pinning
  - Pi state persistence (:with-pi, :pi-version) across setup/update cycles
  - PI_CODING_AGENT_DIR and PI_SKIP_VERSION_CHECK env var passthrough to container
  - Pi config directory (~/.pi/) mount from host to container
  - Pi harness alias generation in container
  - aishell pi command dispatch to run-container
  - Pi in aishell check harness status display
  - Pi in aishell update version display
affects: []

# Tech tracking
tech-stack:
  added: []
  patterns: [harness-flag-pattern for pi following claude/codex/gemini convention]

key-files:
  created: []
  modified:
    - src/aishell/cli.clj
    - src/aishell/docker/run.clj
    - src/aishell/run.clj
    - src/aishell/check.clj
    - src/aishell/state.clj

key-decisions:
  - "Pi follows identical --with-X flag pattern as Codex/Gemini (parse-with-flag, validate-version, state-map)"
  - "PI_CODING_AGENT_DIR and PI_SKIP_VERSION_CHECK added as passthrough env vars (only forwarded when set on host)"
  - "Pi alias generation uses false for always? flag (same as codex/gemini, only generates when harness_args configured)"

patterns-established:
  - "Full harness integration pattern: setup-spec -> handle-setup -> state-map -> installed-harnesses -> print-help -> dispatch -> run-container -> check-harnesses"

requirements-completed: [HARNESS-01, HARNESS-03, ENV-01, ENV-02]

# Metrics
duration: 3min
completed: 2026-02-18
---

# Phase 60 Plan 02: Pi CLI Flag, State Integration, Env Passthrough, and Runtime Wiring Summary

**--with-pi CLI flag with state persistence, PI_* env var passthrough, .pi config mount, and full runtime dispatch wiring across cli/run/check**

## Performance

- **Duration:** 3 min
- **Started:** 2026-02-18T01:56:30Z
- **Completed:** 2026-02-18T01:59:49Z
- **Tasks:** 2
- **Files modified:** 5

## Accomplishments
- Added --with-pi flag to aishell setup with optional version pinning (e.g., --with-pi=1.0.0)
- Pi state (:with-pi, :pi-version) persisted and used across setup, update, check, and dispatch
- PI_CODING_AGENT_DIR and PI_SKIP_VERSION_CHECK env vars passed through to container when set on host
- Pi config directory (~/.pi/) mounted from host, pi alias generated, aishell pi dispatches to run-container
- aishell check shows Pi installation status and version

## Task Commits

Each task was committed atomically:

1. **Task 1: Add --with-pi CLI flag and state integration** - `4d29b66` (feat)
2. **Task 2: Add Pi env var passthrough, config mounts, and runtime wiring** - `7445cbe` (feat)

## Files Created/Modified
- `src/aishell/cli.clj` - Added --with-pi to setup-spec, handle-setup, installed-harnesses, print-help, print-setup-help, handle-update, and dispatch
- `src/aishell/docker/run.clj` - Added PI_* env var passthrough, .pi config mount, pi alias env args
- `src/aishell/run.clj` - Added :with-pi to ensure-harness-volume, verify-harness-available, run-container dispatch
- `src/aishell/check.clj` - Added Pi to check-harnesses display vector
- `src/aishell/state.clj` - Added :with-pi and :pi-version to state schema documentation

## Decisions Made
- Pi follows identical --with-X flag pattern as Codex/Gemini (parse-with-flag, validate-version, state-map persistence)
- PI_CODING_AGENT_DIR and PI_SKIP_VERSION_CHECK are passthrough vars (only forwarded when set on host), not literal values
- Pi alias generation uses false for always? flag (same as codex/gemini), only generates alias when harness_args configured

## Deviations from Plan

None - plan executed exactly as written.

## Issues Encountered
None.

## User Setup Required
None - no external service configuration required.

## Next Phase Readiness
- Full pi integration complete across all 5 source files
- Users can now run `aishell setup --with-pi` to build with pi coding agent
- `aishell pi [args]` dispatches to container with proper env vars and config mounts
- Phase 60 (pi-build-infrastructure) is fully complete

## Self-Check: PASSED

- FOUND: src/aishell/cli.clj
- FOUND: src/aishell/docker/run.clj
- FOUND: src/aishell/run.clj
- FOUND: src/aishell/check.clj
- FOUND: src/aishell/state.clj
- FOUND: 60-02-SUMMARY.md
- FOUND: commit 4d29b66
- FOUND: commit 7445cbe

---
*Phase: 60-pi-build-infrastructure*
*Completed: 2026-02-18*
