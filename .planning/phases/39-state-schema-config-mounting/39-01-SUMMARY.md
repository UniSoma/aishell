---
phase: 39-state-schema-config-mounting
plan: 01
subsystem: infra
tags: [cli, state-management, config-schema, tmux, babashka]

# Dependency graph
requires:
  - phase: 38-volume-tool-installation
    provides: Foundation image with volume-based harness tool injection
provides:
  - --with-tmux CLI flag for opt-in tmux enablement
  - :with-tmux state persistence in state.edn
  - tmux: config section schema with validation
  - Scalar replacement merge strategy for tmux config
affects:
  - 39-02 (needs :with-tmux state to determine tmux installation)
  - 39-03 (needs tmux config mount logic)
  - 40-tmux-bootstrap (consumes :with-tmux flag)

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "Additive state schema evolution (no migration needed)"
    - "YAML config section validation with type checking"
    - "Scalar replacement merge strategy for config sections"

key-files:
  created: []
  modified:
    - src/aishell/cli.clj
    - src/aishell/state.clj
    - src/aishell/config.clj

key-decisions:
  - ":with-tmux defaults to FALSE (opt-in, not opt-out)"
  - "tmux config uses scalar merge (project replaces global)"
  - "Config validation warns on non-map tmux section"

patterns-established:
  - "Boolean CLI flags parsed with :coerce :boolean"
  - "State schema docs updated inline in write-state docstring"
  - "Config section validation follows detect-config pattern"

# Metrics
duration: 3min
completed: 2026-02-02
---

# Phase 39 Plan 01: State Schema & Config Mounting Summary

**CLI flag --with-tmux persists opt-in state, tmux config section validated as map with scalar merge strategy**

## Performance

- **Duration:** 3 min
- **Started:** 2026-02-02T00:31:42Z
- **Completed:** 2026-02-02T00:34:39Z
- **Tasks:** 2
- **Files modified:** 3

## Accomplishments
- --with-tmux CLI flag added to build command with boolean coercion
- :with-tmux state persistence in state.edn (defaults to false when flag omitted)
- tmux: section accepted in config.yaml without unknown-key warnings
- validate-tmux-config function validates map type with helpful error messages
- Project tmux config replaces global config via scalar merge strategy

## Task Commits

Each task was committed atomically:

1. **Task 1: Add --with-tmux flag to CLI and persist in state** - `6888ed9` (feat)
2. **Task 2: Extend config schema with tmux section validation and merge** - `11ac940` (feat)

## Files Created/Modified
- `src/aishell/cli.clj` - Added :with-tmux to build-spec, state-map persistence, update command display
- `src/aishell/state.clj` - Updated state schema docs with :with-tmux boolean
- `src/aishell/config.clj` - Added :tmux to known-keys, validate-tmux-config function, scalar-keys merge

## Decisions Made

**Default behavior: tmux disabled**
- TMUX-01 requirements specify "default behavior is no tmux"
- Research recommended true for backward compat
- Requirements override research: --with-tmux defaults to FALSE
- Rationale: Opt-in mechanism, not opt-out

**Scalar merge strategy for tmux config**
- Project tmux config fully replaces global
- Not concatenated like :mounts or shallow merged like :env
- Rationale: Plugin lists are user preferences, not composable
- Matches existing :pre_start merge behavior

**Map type validation only**
- validate-tmux-config checks map? type
- Warns if boolean/string provided
- No deep validation of :plugins or :resurrect keys yet
- Rationale: Plugin installation is Phase 41-42 scope

## Deviations from Plan

None - plan executed exactly as written.

## Issues Encountered

None.

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness

Ready for Phase 39-02 (tmux installation logic).

**Blocked by:** None

**Concerns:** None

**Delivered:**
- :with-tmux state available for downstream phases
- tmux: config section schema established for future plugin config
- Scalar merge strategy ensures project-level config control

---
*Phase: 39-state-schema-config-mounting*
*Completed: 2026-02-02*
