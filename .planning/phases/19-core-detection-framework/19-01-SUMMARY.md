---
phase: 19-core-detection-framework
plan: 01
subsystem: detection
tags: [babashka, clojure, defmulti, terminal-ui, security-warnings]

# Dependency graph
requires:
  - phase: 14-cli-commands
    provides: cli dispatch pattern, run.clj orchestration
  - phase: 17-config-management
    provides: validation.clj warning patterns
provides:
  - Core detection framework with scan-project, display-warnings, confirm-if-needed
  - Severity-based formatting (high/medium/low) with color output
  - --unsafe flag for CI/automation bypass
  - Extensible defmulti format-finding for custom formatters
affects: [20-filename-detection, 21-content-detection, 22-extended-patterns]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - defmulti dispatch for extensible detection formatters
    - Severity grouping with high-first ordering
    - Non-interactive mode detection with CI bypass

key-files:
  created:
    - src/aishell/detection/core.clj
    - src/aishell/detection/formatters.clj
  modified:
    - src/aishell/cli.clj
    - src/aishell/run.clj

key-decisions:
  - "Use defmulti for format-finding to allow extension in later phases"
  - "High-severity requires y/n in interactive mode, error+exit 1 in non-interactive"
  - "Medium/low severity auto-proceeds in all modes"
  - "Detection hook placed after warn-dangerous-mounts, before docker-args"

patterns-established:
  - "Detection namespace: core.clj for logic, formatters.clj for display"
  - "confirm-if-needed pattern: exit 0 abort, exit 1 error, true proceed"
  - "--unsafe flag extracted before harness pass-through"

# Metrics
duration: 4min
completed: 2026-01-23
---

# Phase 19 Plan 01: Core Detection Framework Summary

**Extensible detection framework with severity tiers (high/medium/low), defmulti formatters, y/n confirmation for high-severity, and --unsafe bypass for CI**

## Performance

- **Duration:** 4 min
- **Started:** 2026-01-23T13:12:45Z
- **Completed:** 2026-01-23T13:17:00Z
- **Tasks:** 3 (2 with commits, 1 verification-only)
- **Files modified:** 4

## Accomplishments

- Created detection/core.clj with scan-project, display-warnings, confirm-if-needed
- Created detection/formatters.clj with severity-config and defmulti format-finding
- Added --unsafe flag extraction in cli.clj with pass-through handling
- Hooked detection into run.clj after warn-dangerous-mounts, before docker-args
- Non-interactive high-severity exits 1 with --unsafe requirement message
- Medium/low auto-proceeds without prompting

## Task Commits

Each task was committed atomically:

1. **Task 1: Create detection namespace with formatters and core** - `131fa1f` (feat)
2. **Task 2: Add --unsafe flag and hook detection into run flow** - `9aa827f` (feat)
3. **Task 3: Test end-to-end flow with mock finding** - (verification only, no code changes)

## Files Created/Modified

- `src/aishell/detection/core.clj` - scan-project, display-warnings, confirm-if-needed, excluded-dirs
- `src/aishell/detection/formatters.clj` - severity-config, format-severity-label, defmulti format-finding
- `src/aishell/cli.clj` - --unsafe flag extraction, updated run-container callsites with opts map
- `src/aishell/run.clj` - detection.core require, detection hook after warn-dangerous-mounts

## Decisions Made

- **defmulti for format-finding:** Enables extension in Phases 20-22 without modifying core namespace
- **Optional opts map with & [opts]:** Maintains backward compatibility with existing callsites
- **Non-interactive detection:** High-severity in CI requires --unsafe (exit 1), medium/low auto-proceeds
- **Detection hook placement:** After config validation warnings, before building docker-args

## Deviations from Plan

None - plan executed exactly as written.

## Issues Encountered

- **defmethod docstring:** Clojure defmethod doesn't support inline docstrings - moved to comment above
- **Parenthesis count:** Initial edit to handle-default added extra closing paren - fixed immediately

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness

- Framework skeleton complete with empty scan-project returning []
- Phase 20 will populate scan-project with filename pattern detection
- defmulti format-finding ready for custom formatter methods per detection type
- All success criteria verified:
  - [x] detection/core.clj exists with scan-project, display-warnings, confirm-if-needed
  - [x] detection/formatters.clj exists with severity formatting
  - [x] cli.clj parses --unsafe and passes to run-container
  - [x] cli.clj handle-default passes empty opts map (backward compatible)
  - [x] run.clj hooks detection before container execution
  - [x] scan-project returns [] (placeholder for Phase 20 patterns)
  - [x] display-warnings correctly groups by severity (high first)
  - [x] confirm-if-needed prompts for high-severity in interactive mode
  - [x] confirm-if-needed requires --unsafe for high-severity in non-interactive mode
  - [x] confirm-if-needed auto-proceeds for medium/low in any mode
  - [x] All namespaces load without error

---
*Phase: 19-core-detection-framework*
*Completed: 2026-01-23*
