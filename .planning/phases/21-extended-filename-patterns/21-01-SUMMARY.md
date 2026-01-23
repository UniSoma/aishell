---
phase: 21-extended-filename-patterns
plan: 01
subsystem: detection
tags: [babashka, clojure, gcp, terraform, kubernetes, cloud-credentials]

# Dependency graph
requires:
  - phase: 20-02
    provides: SSH key detection, key container detection, PEM file detection patterns
provides:
  - GCP application_default_credentials.json detection (high severity)
  - Terraform state file detection (terraform.tfstate*) with high severity
  - Kubeconfig detection (kubeconfig, .kube/config) with medium severity
  - Cloud credential detection integrated into scan-project
affects: [21-02-extended-patterns, 22-gitleaks-integration]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - Case-insensitive filename matching via str/lower-case post-filtering
    - Glob ** with filename filter for root+subdirectory coverage
    - Path-based matching with str/includes? for .kube/config pattern

key-files:
  created: []
  modified:
    - src/aishell/detection/patterns.clj
    - src/aishell/detection/core.clj

key-decisions:
  - "GCP ADC classified as high severity - long-lived service account credentials"
  - "Terraform state classified as high severity - may contain plaintext secrets"
  - "Kubeconfig classified as medium severity - cluster access credentials"
  - "Use glob ** with filename filter instead of **/pattern* for root level coverage"

patterns-established:
  - "Cloud credential detectors follow same signature: [project-dir excluded-dirs] -> findings"
  - "High-severity cloud files require confirmation before container launch"

# Metrics
duration: 3min
completed: 2026-01-23
---

# Phase 21 Plan 01: Cloud Credential Detection Summary

**GCP application default credentials, Terraform state files, and kubeconfig detected with severity-based warnings before AI agent access**

## Performance

- **Duration:** 3 min
- **Started:** 2026-01-23T16:30:00Z
- **Completed:** 2026-01-23T16:33:00Z
- **Tasks:** 3
- **Files modified:** 2

## Accomplishments

- Added detect-gcp-credentials function for application_default_credentials.json (high severity)
- Added detect-terraform-state function for terraform.tfstate* files (high severity)
- Added detect-kubeconfig function for kubeconfig and .kube/config patterns (medium severity)
- Integrated all three cloud credential detectors into scan-project
- Verified end-to-end detection with test files covering all patterns

## Task Commits

Each task was committed atomically:

1. **Task 1: Add cloud credential detection functions** - `468aeba` (feat)
2. **Task 2: Integrate detectors into scan-project** - `8d0bc12` (feat)
3. **Task 3: Terraform glob pattern fix** - `b18500c` (fix)

## Files Created/Modified

- `src/aishell/detection/patterns.clj` - Added detect-gcp-credentials, detect-terraform-state, detect-kubeconfig functions
- `src/aishell/detection/core.clj` - Updated scan-project to call all three new cloud credential detectors

## Decisions Made

- **GCP ADC severity:** High - these are long-lived service account credentials that Google recommends against storing in files
- **Terraform state severity:** High - state files may contain plaintext secrets (passwords, API keys, certificates)
- **Kubeconfig severity:** Medium - contains cluster access credentials, but typically not direct secrets
- **Glob pattern strategy:** Use `**` glob with filename filter rather than `**/terraform.tfstate*` because babashka.fs `**/` only matches subdirectories, not root level files

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] Terraform state files not detected at root level**
- **Found during:** Task 3 (end-to-end verification)
- **Issue:** Glob pattern `**/terraform.tfstate*` only matched files in subdirectories, not at project root
- **Fix:** Changed to glob `**` with str/starts-with? filter on lowercase filename
- **Files modified:** src/aishell/detection/patterns.clj
- **Verification:** Test directory with terraform.tfstate at root now detected correctly
- **Committed in:** b18500c (fix commit after Task 3)

---

**Total deviations:** 1 auto-fixed (Rule 1 - Bug)
**Impact on plan:** Essential fix for correct detection. No scope creep.

## Issues Encountered

- babashka.fs glob `**/pattern*` only matches in subdirectories, not at root level - discovered during verification and fixed by using `**` with filename filtering

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness

- Plan 21-01 complete: Cloud credential detection working
  - [x] GCP application_default_credentials.json (high severity)
  - [x] terraform.tfstate and terraform.tfstate.backup (high severity)
  - [x] kubeconfig and .kube/config (medium severity)
- Ready for Plan 21-02: Package manager and application secret patterns
- High-severity cloud files (GCP, Terraform) require user confirmation before container launch

---
*Phase: 21-extended-filename-patterns*
*Completed: 2026-01-23*
