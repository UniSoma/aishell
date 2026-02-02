---
phase: 40-plugin-installation-in-volume
plan: 01
subsystem: config
tags: [validation, regex, tmux-plugins, clojure]

# Dependency graph
requires:
  - phase: 39-state-schema-config-mounting
    provides: "tmux config structure in config.yaml"
provides:
  - "Plugin format validation with owner/repo regex pattern"
  - "validate-plugin-format function for format checking"
  - "Extended validate-tmux-config with plugin list validation"
affects: [40-02]

# Tech tracking
tech-stack:
  added: []
  patterns: ["Warning-only validation consistent with existing config validation"]

key-files:
  created: []
  modified: ["src/aishell/config.clj"]

key-decisions:
  - "Use warning-only validation approach (consistent with validate-detection-config and validate-harness-names)"
  - "GitHub owner/repo format: owner 1-39 chars (alphanumeric, hyphens, no leading/trailing), repo 1-100 chars (alphanumeric, dots, hyphens, underscores)"

patterns-established:
  - "Plugin format validation: regex pattern validates during config load, warns on invalid format"
  - "Type checking: plugins must be sequential? of strings, warns on type mismatch"

# Metrics
duration: 1min
completed: 2026-02-02
---

# Phase 40 Plan 01: Plugin Format Validation Summary

**Plugin format validation with GitHub owner/repo regex pattern integrated into config parsing**

## Performance

- **Duration:** 1 min
- **Started:** 2026-02-02T12:27:01Z
- **Completed:** 2026-02-02T12:27:59Z
- **Tasks:** 1
- **Files modified:** 1

## Accomplishments
- Added plugin-format-pattern regex for GitHub owner/repo validation
- Created validate-plugin-format function returning nil for valid, error string for invalid
- Extended validate-tmux-config to validate plugins list structure and format
- Validated implementation with valid and invalid plugin formats

## Task Commits

Each task was committed atomically:

1. **Task 1: Add plugin format validation to config.clj** - `b5d356a` (feat)

## Files Created/Modified
- `src/aishell/config.clj` - Added plugin-format-pattern, validate-plugin-format function, extended validate-tmux-config with plugins list validation

## Decisions Made
- Used warning-only validation approach to match existing validation framework (validate-detection-config, validate-harness-names)
- Regex pattern: `^[a-zA-Z0-9]([a-zA-Z0-9-]{0,37}[a-zA-Z0-9])?/[a-zA-Z0-9._-]{1,100}$` matches GitHub owner/repo constraints

## Deviations from Plan

None - plan executed exactly as written.

## Issues Encountered

None.

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness

Ready for plan 40-02 (TPM and plugin installation in volume during build/update). Plugin format validation ensures bad plugin declarations are caught early before Docker operations begin.

---
*Phase: 40-plugin-installation-in-volume*
*Completed: 2026-02-02*
