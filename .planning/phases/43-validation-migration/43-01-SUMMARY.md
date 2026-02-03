---
phase: 43-validation-migration
plan: 01
subsystem: validation
tags: [tmux, migration, attach, error-handling, backward-compatibility]

# Dependency graph
requires:
  - phase: 42-resurrect-state-persistence
    provides: tmux-resurrect plugin injection and state directory mounting
  - phase: 41-tmux-initialization
    provides: TPM initialization in entrypoint
  - phase: 40-tmux-config-threading
    provides: tmux config parsing and threading
provides:
  - tmux availability pre-flight validation in attach commands
  - one-time migration warning for v2.7-2.8 upgraders
  - graceful error messages for missing tmux with corrective actions
  - default session name standardized to "harness"
affects: [v2.10.0-polish, v3.0.0-stability]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "Pre-flight validation pattern: check prerequisites before exec"
    - "Migration warning pattern: version detection via state schema, one-time marker file"
    - "3-part error messages: problem, context, corrective action"

key-files:
  created:
    - src/aishell/migration.clj
  modified:
    - src/aishell/attach.clj
    - src/aishell/cli.clj

key-decisions:
  - "Migration warning triggers on state exists + lacks :harness-volume-hash (schema-based detection)"
  - "Marker file prevents repeat warnings (~/.aishell/.migration-v2.9-warned)"
  - "Fresh installs never see migration warning (no state.edn)"
  - "Default session changed from 'main' to 'harness' for project naming consistency"

patterns-established:
  - "Pre-flight validation: validate-X-enabled! functions check prerequisites before exec"
  - "Migration warnings: schema-based version detection + one-time marker files"

# Metrics
duration: 2min
completed: 2026-02-03
---

# Phase 43 Plan 01: Validation & Migration Summary

**tmux pre-flight validation with 3-part error messages and schema-based migration warning for v2.7-2.8 upgraders**

## Performance

- **Duration:** 2 min
- **Started:** 2026-02-03T01:49:31Z
- **Completed:** 2026-02-03T01:51:34Z
- **Tasks:** 2
- **Files modified:** 3

## Accomplishments
- attach commands validate tmux availability before attempting session operations
- v2.7-2.8 upgraders see one-time migration warning about tmux opt-in change
- Graceful error messages guide users through rebuild process when tmux missing
- Default session name standardized from "main" to "harness"

## Task Commits

Each task was committed atomically:

1. **Task 1: Add tmux validation to attach commands** - `02495ed` (feat)
2. **Task 2: Create migration warning namespace and integrate into CLI** - `2ae2ceb` (feat)

## Files Created/Modified
- `src/aishell/migration.clj` - One-time migration warnings with version detection
- `src/aishell/attach.clj` - tmux validation and default session name update
- `src/aishell/cli.clj` - Migration warning integration on build and dispatch

## Decisions Made

**Migration detection strategy:**
- Use state schema field `:harness-volume-hash` presence as version marker (pre-v2.9.0 lacks it)
- Marker file at `~/.aishell/.migration-v2.9-warned` prevents repeat warnings
- Fresh installs (no state.edn) never trigger warning

**Error message structure:**
- 3-part pattern: problem statement, context explanation, corrective action steps
- Guides users through exact rebuild process: build --with-tmux, restart, attach

**Session naming:**
- Changed default from "main" to "harness" for consistency with project naming conventions
- Aligns with harness-centric terminology throughout codebase

## Deviations from Plan

None - plan executed exactly as written.

## Issues Encountered

None.

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness

All v2.9.0 functionality complete:
- Phase 39: Base tmux integration (session management, --with-tmux flag)
- Phase 40: Config threading (plugin declarations, resurrect config)
- Phase 41: TPM initialization (plugin installation at startup)
- Phase 42: Resurrect state persistence (config injection, auto-restore)
- Phase 43: Validation & migration (pre-flight checks, upgrade warnings)

Ready for:
- Integration testing
- Documentation updates
- v2.9.0 release preparation

No blockers or concerns.

---
*Phase: 43-validation-migration*
*Completed: 2026-02-03*
