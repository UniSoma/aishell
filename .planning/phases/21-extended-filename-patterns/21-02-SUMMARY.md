---
phase: 21-extended-filename-patterns
plan: 02
subsystem: detection
tags: [babashka, glob, security, file-detection, package-manager, rails, database]

# Dependency graph
requires:
  - phase: 21-01
    provides: cloud credential detection functions and scan-project foundation
  - phase: 20
    provides: base detection framework with threshold-of-3 summarization
provides:
  - Package manager credentials detection (.pypirc, .netrc)
  - Tool configuration detection (.npmrc, .yarnrc.yml, .docker/config.json, .terraformrc)
  - Rails secrets detection (master.key, credentials*.yml.enc)
  - Secret pattern file detection (secret.*, secrets.*, vault.*, token.*, apikey.*, private.*)
  - Database credentials detection (.pgpass, .my.cnf, database.yml)
affects: [22-gitleaks-integration, 23-context-config]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "Use ** glob with filename filter for babashka.fs (not **/pattern.*)"
    - "Multiple severity types can match same file (master.key is rails-secret high AND pem-key medium)"

key-files:
  created: []
  modified:
    - src/aishell/detection/patterns.clj
    - src/aishell/detection/core.clj

key-decisions:
  - "Package manager creds (.pypirc, .netrc) classified as high severity - contain auth tokens"
  - "Rails master.key classified as high severity - decrypts all application secrets"
  - "Tool configs (.npmrc, .docker/config.json) classified as medium - may contain credentials"
  - "Secret pattern files (secret.*, token.*) classified as medium - pattern-based heuristic"
  - "Database configs (.pgpass, .my.cnf, database.yml) classified as medium severity"
  - "Use filename filtering not glob patterns for secret.* matching (babashka.fs quirk)"

patterns-established:
  - "Multiple overlapping detections acceptable: master.key detected as rails-secret (high) and pem-key (medium)"
  - "Threshold-of-3 summarization automatically groups >3 same-type findings"

# Metrics
duration: 3min
completed: 2026-01-23
---

# Phase 21 Plan 02: Package Manager and Application Secrets Summary

**Package manager credentials, Rails secrets, tool configs, secret patterns, and database files detection completing Phase 21**

## Performance

- **Duration:** 3 min
- **Started:** 2026-01-23
- **Completed:** 2026-01-23
- **Tasks:** 3
- **Files modified:** 2

## Accomplishments
- Five new detection functions covering package managers, tool configs, Rails, secret patterns, and databases
- All 12 detectors integrated into scan-project (Phase 20: 4, Phase 21-01: 3, Phase 21-02: 5)
- High-severity warnings for package manager credentials and Rails secrets
- Medium-severity warnings for tool configs, secret-named files, and database credentials

## Task Commits

Each task was committed atomically:

1. **Task 1: Add detection functions to patterns.clj** - `9ac6e38` (feat)
2. **Task 2: Integrate detectors into scan-project** - `20589ba` (feat)
3. **Task 3: End-to-end verification + bug fix** - `da312eb` (fix)

## Files Created/Modified
- `src/aishell/detection/patterns.clj` - Added 5 detection functions (detect-package-manager-credentials, detect-tool-configs, detect-rails-secrets, detect-secret-pattern-files, detect-database-credentials)
- `src/aishell/detection/core.clj` - Integrated all 5 new detectors into scan-project concat chain

## Decisions Made
- Package manager credentials (.pypirc, .netrc) are high severity - contain PyPI/machine auth tokens
- Rails master.key is high severity - decrypts all encrypted credentials in Rails apps
- Tool configs (npm, yarn, docker, terraform) are medium severity - may contain tokens/registry auth
- Secret pattern files use prefix matching (secret.*, vault.*, token.*, etc.) for heuristic detection
- Database configs (.pgpass, .my.cnf, database.yml) are medium severity - often contain connection credentials
- Overlapping detections are acceptable (e.g., master.key triggers both rails-secret and pem-key)

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] Fixed secret-pattern-files glob pattern not matching root directory**
- **Found during:** Task 3 (End-to-end verification)
- **Issue:** `**/secret.*` glob pattern doesn't match files in root directory (babashka.fs quirk - ** requires at least one directory level)
- **Fix:** Changed from glob patterns to `**` with filename prefix filtering
- **Files modified:** src/aishell/detection/patterns.clj
- **Verification:** Reran test - 6 secret pattern files now detected correctly
- **Committed in:** da312eb (Task 3 commit)

---

**Total deviations:** 1 auto-fixed (1 bug)
**Impact on plan:** Bug fix was necessary for correct functionality. No scope creep.

## Issues Encountered
None beyond the glob pattern bug (handled as deviation).

## User Setup Required
None - no external service configuration required.

## Next Phase Readiness
- Phase 21 (Extended Filename Patterns) is now complete
- All 12 detectors operational: env, ssh, key-containers, pem-key, gcp, terraform, kubeconfig, package-manager, tool-configs, rails, secret-pattern, database
- Ready for Phase 22 (Gitleaks Integration) for content-based secret detection

---
*Phase: 21-extended-filename-patterns*
*Completed: 2026-01-23*
