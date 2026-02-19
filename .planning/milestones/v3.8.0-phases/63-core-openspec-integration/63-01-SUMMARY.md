---
phase: 63-core-openspec-integration
plan: 01
subsystem: build
tags: [openspec, npm, volume, cli, harness-registration]

# Dependency graph
requires:
  - phase: 60-pi-agent-harness-registration
    provides: "Pi harness pattern for npm tool registration and CLI wiring"
provides:
  - "OpenSpec registered as npm harness in volume system (:openspec key)"
  - "--with-openspec CLI flag parsed, validated, and persisted in state"
  - "OpenSpec included in volume hash computation and npm install commands"
  - "aishell update displays OpenSpec version when enabled"
affects: [63-02-PLAN]

# Tech tracking
tech-stack:
  added: ["@fission-ai/openspec (npm package reference)"]
  patterns: ["Same --with-* / harness-keys / harness-npm-packages pattern as Pi"]

key-files:
  created: []
  modified:
    - src/aishell/docker/volume.clj
    - src/aishell/cli.clj
    - src/aishell/state.clj

key-decisions:
  - "OpenSpec follows exact Pi pattern: harness-keys + harness-npm-packages registration, no dispatch entry"
  - "OpenSpec is NOT a harness subcommand (no aishell openspec), only available inside container"

patterns-established:
  - "Non-harness npm tools use same harness-keys/harness-npm-packages pattern but skip dispatch table"

requirements-completed: [BUILD-01, BUILD-02, BUILD-03, VOL-01, VOL-02]

# Metrics
duration: 2min
completed: 2026-02-18
---

# Phase 63 Plan 01: Core OpenSpec Integration Summary

**OpenSpec registered as npm tool in volume system with --with-openspec CLI flag wired through setup, state persistence, volume installation, and update display**

## Performance

- **Duration:** 2 min
- **Started:** 2026-02-18T19:55:37Z
- **Completed:** 2026-02-18T19:57:49Z
- **Tasks:** 2
- **Files modified:** 3

## Accomplishments
- Registered `:openspec` in harness-keys and harness-npm-packages (volume.clj) following Pi pattern
- Wired `--with-openspec` flag through CLI parsing, version validation, state persistence, volume triggers, and update display
- Documented OpenSpec state schema keys in state.clj docstring

## Task Commits

Each task was committed atomically:

1. **Task 1: Register OpenSpec in harness volume system** - `18a1bba` (feat)
2. **Task 2: Wire --with-openspec through CLI setup and update** - `8f3cc9d` (feat)

**Plan metadata:** (pending) (docs: complete plan)

## Files Created/Modified
- `src/aishell/docker/volume.clj` - Added `:openspec` to harness-keys and `@fission-ai/openspec` to harness-npm-packages
- `src/aishell/cli.clj` - Added --with-openspec flag parsing, validation, state-map, volume trigger, update display, setup help, installed-harnesses
- `src/aishell/state.clj` - Updated write-state docstring schema to include :with-openspec and :openspec-version

## Decisions Made
- OpenSpec follows the exact Pi pattern for registration (harness-keys + harness-npm-packages) but is NOT added to the dispatch table since it's not a harness subcommand
- OpenSpec tracked in installed-harnesses so aishell check can display its status

## Deviations from Plan

None - plan executed exactly as written.

## Issues Encountered
None

## User Setup Required
None - no external service configuration required.

## Next Phase Readiness
- OpenSpec is fully registered in volume and CLI systems
- Plan 02 can now implement container-side configuration (if applicable)
- `aishell setup --with-openspec` will install `@fission-ai/openspec` into the /tools volume

## Self-Check: PASSED

All files exist. All commits verified.

---
*Phase: 63-core-openspec-integration*
*Completed: 2026-02-18*
