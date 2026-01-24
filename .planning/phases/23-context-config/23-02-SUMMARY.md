---
phase: 23-context-config
plan: 02
subsystem: detection
tags: [detection, configuration, allowlist, custom-patterns, yaml, babashka]

# Dependency graph
requires:
  - phase: 23-01
    provides: gitignore status checking for high-severity findings
  - phase: 22
    provides: gitleaks integration and config merge strategy
  - phase: 20-21
    provides: filename-based detection patterns
provides:
  - User-configurable custom sensitive file patterns via detection.custom_patterns
  - Allowlist support for suppressing false positives via detection.allowlist
  - Global kill switch detection.enabled for disabling all filename detection
  - Detection config merge strategy (enabled scalar, patterns map merge, allowlist concat)
affects: [future-phases-needing-detection-customization]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "Detection config merge: enabled scalar (project wins), custom_patterns map merge, allowlist concatenate"
    - "YAML pattern keys parsed as keywords, handle with (name pattern-key)"
    - "Allowlist supports exact paths, filename-only, and glob patterns"

key-files:
  created: []
  modified:
    - src/aishell/config.clj
    - src/aishell/detection/core.clj
    - src/aishell/detection/patterns.clj
    - src/aishell/run.clj

key-decisions:
  - "Custom patterns extend defaults (don't replace) via concatenation in scan-project"
  - "Allowlisted files completely hidden from output (not shown as 'allowed')"
  - "Invalid severity values in custom patterns skipped silently (no error, just warning)"
  - "Pattern keys from YAML parsed as keywords - use (name pattern-key) to get string"
  - "Allowlist filtering happens in run.clj after scan-project returns findings"

patterns-established:
  - "Detection config has three merge behaviors: enabled (scalar override), custom_patterns (map merge), allowlist (concat)"
  - "filter-allowlisted is public function for reuse, file-allowlisted? is private helper"
  - "scan-project accepts optional detection-config parameter with default {}"

# Metrics
duration: 4min
completed: 2026-01-24
---

# Phase 23 Plan 02: Detection Configuration Summary

**User-configurable custom patterns and allowlists with merge strategy enabling project-specific detection rules and false positive suppression**

## Performance

- **Duration:** 4 min
- **Started:** 2026-01-24T01:50:56Z
- **Completed:** 2026-01-24T01:54:40Z
- **Tasks:** 3
- **Files modified:** 4

## Accomplishments
- Custom sensitive file patterns configurable via YAML detection.custom_patterns
- Allowlist support for suppressing known-safe files via detection.allowlist
- Global detection kill switch via detection.enabled: false
- Smart config merge: patterns extend defaults, allowlists combine global+project

## Task Commits

Each task was committed atomically:

1. **Task 1: Add :detection config key with merge strategy** - `5574856` (feat)
2. **Task 2: Add custom pattern detection and allowlist filtering** - `1cb144b` (feat)
3. **Task 3: Wire detection config into run.clj execution flow** - `a7e9de2` (feat)

## Files Created/Modified
- `src/aishell/config.clj` - Added :detection to known-keys, merge-detection function, validate-detection-config
- `src/aishell/detection/patterns.clj` - Added detect-custom-patterns function with keyword handling
- `src/aishell/detection/core.clj` - Added filter-allowlisted function, updated scan-project signature
- `src/aishell/run.clj` - Integrated detection config loading and allowlist filtering

## Decisions Made

**1. Custom patterns extend defaults (don't replace)**
- Rationale: Users want to add project-specific patterns without losing built-in detection
- Implementation: concat detect-custom-patterns with existing detectors in scan-project

**2. Allowlisted files completely hidden from output**
- Rationale: Reduce noise - users don't want to see "allowed" annotations, just want files gone
- Implementation: filter-allowlisted removes matches before display-warnings

**3. Invalid severity values skipped with warning**
- Rationale: Don't break detection entirely due to typo in one pattern
- Implementation: validate-detection-config warns, detect-custom-patterns filters with :when clause

**4. Pattern keys from YAML parsed as keywords**
- Rationale: clj-yaml parser converts map keys to keywords
- Implementation: Use (name pattern-key) to get string for fs/glob

**5. Allowlist filtering happens in run.clj**
- Rationale: Separation of concerns - scan-project returns all findings, run.clj applies policy
- Implementation: run.clj calls filter-allowlisted before display-warnings

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] Handle YAML pattern keys as keywords**
- **Found during:** Task 3 (end-to-end verification)
- **Issue:** fs/glob failed because pattern was keyword :*.custom-secret not string "*.custom-secret"
- **Fix:** Added keyword check in detect-custom-patterns: `(if (keyword? pattern-key) (name pattern-key) (str pattern-key))`
- **Files modified:** src/aishell/detection/patterns.clj
- **Verification:** Custom pattern detection test passed with YAML config
- **Committed in:** a7e9de2 (Task 3 commit)

---

**Total deviations:** 1 auto-fixed (1 blocking)
**Impact on plan:** Essential fix for YAML config integration. No scope creep.

## Issues Encountered

None - plan executed smoothly with one minor YAML parsing adjustment.

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness

Detection configuration system complete. Users can now:
- Add custom patterns for project-specific sensitive files
- Suppress false positives with allowlist entries
- Disable detection entirely with enabled: false flag

Ready for any future detection enhancements.

---
*Phase: 23-context-config*
*Completed: 2026-01-24*
