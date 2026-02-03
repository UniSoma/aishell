---
phase: 40-plugin-installation-in-volume
plan: 03
subsystem: infra
tags: [tmux, volume, hash, git, idempotency]

# Dependency graph
requires:
  - phase: 40-02
    provides: Volume population with tmux plugin installation
provides:
  - Volume hash includes tmux configuration state
  - TPM git clone with idempotency guard
  - Build/update guards trigger volume population for tmux-only configs
affects: [41-tpm-initialization-in-entrypoint, integration-testing]

# Tech tracking
tech-stack:
  added: []
  patterns: ["Idempotent git operations with pull-if-exists pattern"]

key-files:
  created: []
  modified:
    - src/aishell/docker/volume.clj
    - src/aishell/cli.clj

key-decisions:
  - "Volume hash includes :with-tmux flag and sorted plugin list for content-based invalidation"
  - "TPM git clone uses pull-if-exists pattern instead of rm -rf for safer idempotency"
  - "Build and update guards check :with-tmux alongside harness flags to trigger volume population"

patterns-established:
  - "Idempotent git clone: check dir exists → pull if exists, clone if missing"

# Metrics
duration: 2min
completed: 2026-02-02
---

# Phase 40 Plan 03: Gap Closure Summary

**Volume hash computation includes tmux state, build guards trigger for tmux-only configs, and TPM git clone is idempotent**

## Performance

- **Duration:** 2 min
- **Started:** 2026-02-02T13:39:07Z
- **Completed:** 2026-02-02T13:40:55Z
- **Tasks:** 2
- **Files modified:** 2

## Accomplishments
- Volume hash changes when tmux configuration changes (flag toggle or plugin list modification)
- `aishell build --with-tmux` triggers volume population even without AI harnesses
- `aishell update` succeeds idempotently when TPM already exists in volume
- Multiple plugins install correctly on repeated update operations

## Task Commits

Each task was committed atomically:

1. **Task 1: Include tmux state in volume hash and fix build guard** - `c51c00f` (feat)
2. **Task 2: Make TPM git clone idempotent** - `a0be76d` (feat)

## Files Created/Modified
- `src/aishell/docker/volume.clj` - normalize-harness-config appends tmux state with sorted plugins; build-tpm-install-command uses if-exists guard for git operations
- `src/aishell/cli.clj` - handle-build and handle-update guards include :with-tmux check; state-map includes :tmux-plugins for hash computation

## Decisions Made
- **Volume hash includes tmux state**: The `normalize-harness-config` function appends `[:tmux {:plugins [...]}]` to the hash input when `:with-tmux` is true. This ensures volume recreation when tmux configuration changes.
- **Pull-if-exists pattern for idempotency**: Instead of `rm -rf` before clone, the command checks if TPM directory exists and runs `git pull --ff-only` if present, `git clone` if missing. Safer for retry scenarios.
- **Build/update guards include :with-tmux**: Both `handle-build` and `handle-update` now check `:with-tmux` alongside AI harness flags when deciding whether to populate volume. Ensures tmux-only configs work.

## Deviations from Plan

None - plan executed exactly as written.

## Issues Encountered

None. Both tasks implemented cleanly with verification passing on first attempt.

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness

**Ready for Phase 41: TPM Initialization in Entrypoint**

This gap closure fixes the three UAT failures:
- **UAT test 3** (TPM installed during build) - Fixed by including :with-tmux in build guard
- **UAT test 4** (declared plugins installed during build) - Same root cause as test 3, now fixed
- **UAT test 9** (multiple plugins on repeated update) - Fixed by idempotent git clone

The volume system now correctly:
1. Detects tmux configuration changes (hash includes tmux state)
2. Triggers volume population for tmux-only configs (guard includes :with-tmux)
3. Handles repeated update operations safely (idempotent git operations)

No blockers for Phase 41. The volume layer is complete and tested.

---
*Phase: 40-plugin-installation-in-volume*
*Completed: 2026-02-02*
