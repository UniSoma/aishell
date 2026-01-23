---
phase: 22-gitleaks-integration
plan: 02
subsystem: infra
tags: [gitleaks, security, state-persistence, warnings, xdg]

# Dependency graph
requires:
  - phase: 22-01
    provides: Gitleaks v8.30.0 binary and command passthrough
provides:
  - Per-project scan timestamp tracking in XDG state directory
  - Freshness warnings before container launch when scan is stale
  - Automatic timestamp update after successful gitleaks scans
  - Config toggle for disabling freshness warnings
affects: [23-context-config]

# Tech tracking
tech-stack:
  added: [java.time.Instant, java.time.Duration]
  patterns: [XDG state directory usage, advisory warnings pattern, config toggle pattern]

key-files:
  created:
    - src/aishell/gitleaks/scan_state.clj
    - src/aishell/gitleaks/warnings.clj
  modified:
    - src/aishell/config.clj
    - src/aishell/run.clj
    - src/aishell/core.clj

key-decisions:
  - "Default staleness threshold: 7 days"
  - "State file location: ~/.local/state/aishell/gitleaks-scans.edn"
  - "Absolute project paths as state keys for debuggability"
  - "Use shell instead of exec for gitleaks to capture exit code"
  - "Config key: gitleaks_freshness_check (default: enabled)"

patterns-established:
  - "XDG state directory pattern: util/state-dir for per-project state persistence"
  - "Advisory warning pattern: display before launch, don't block execution"
  - "Config toggle pattern: (not= false (:key config)) to allow nil/missing = enabled"

# Metrics
duration: 2.4min
completed: 2026-01-23
---

# Phase 22 Plan 02: Gitleaks Freshness Tracking Summary

**Per-project scan timestamp persistence in XDG state directory with 7-day staleness warnings before container launch**

## Performance

- **Duration:** 2.4 min
- **Started:** 2026-01-23T20:57:32Z
- **Completed:** 2026-01-23T21:00:15Z
- **Tasks:** 3
- **Files modified:** 5

## Accomplishments
- Scan state persistence tracks last gitleaks scan per project in XDG state directory
- Advisory warnings display before shell/claude/opencode launch when scan is stale (7+ days) or missing
- Timestamp automatically updates after successful gitleaks command execution
- Config toggle allows disabling freshness warnings via gitleaks_freshness_check: false

## Task Commits

Each task was committed atomically:

1. **Task 1: Create scan_state.clj for timestamp persistence** - `87a471e` (feat)
2. **Task 2: Create warnings.clj for freshness warning display** - `7c0ae14` (feat)
3. **Task 3: Add gitleaks_freshness_check to config and integrate warnings** - `a0cb8b5` (feat)

## Files Created/Modified
- `src/aishell/gitleaks/scan_state.clj` - Per-project timestamp persistence with stale? checker
- `src/aishell/gitleaks/warnings.clj` - Freshness warning display with actionable message
- `src/aishell/config.clj` - Added gitleaks_freshness_check to known-keys
- `src/aishell/run.clj` - Integrated freshness warnings and timestamp updates
- `src/aishell/core.clj` - Added static requires for uberscript

## Decisions Made
- **Default threshold: 7 days** - Balances nudging users without being annoying. Scans older than 7 days trigger staleness warning.
- **State file location: ~/.local/state/aishell/gitleaks-scans.edn** - Follows XDG Base Directory spec for state persistence separate from config.
- **Absolute paths as keys** - State file maps absolute project paths to timestamps for debuggability (user can inspect state file).
- **Shell vs exec for gitleaks** - Changed gitleaks command from p/exec to p/shell to capture exit code and update timestamp on success (exit 0).
- **Config toggle: gitleaks_freshness_check** - Defaults to enabled. Users can disable via `gitleaks_freshness_check: false` in config.yaml.

## Deviations from Plan

None - plan executed exactly as written.

## Issues Encountered

None.

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness

Gitleaks integration complete. Users now get:
1. Fast secret scanning via `aishell gitleaks` command (22-01)
2. Automatic freshness reminders before container launch (22-02)

Ready for Phase 23: Context Config implementation.

---
*Phase: 22-gitleaks-integration*
*Completed: 2026-01-23*
