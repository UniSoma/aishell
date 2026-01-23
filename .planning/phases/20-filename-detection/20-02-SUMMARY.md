---
phase: 20-filename-detection
plan: 02
subsystem: detection
tags: [babashka, clojure, ssh-keys, pkcs12, jks, pem, key-files]

# Dependency graph
requires:
  - phase: 20-01
    provides: Environment file detection, threshold-based grouping, patterns namespace
provides:
  - SSH key file detection (id_rsa, id_dsa, id_ed25519, id_ecdsa, *.ppk) with high severity
  - Key container detection (*.p12, *.pfx, *.jks, *.keystore) with high severity
  - PEM/key file detection (*.pem, *.key) with medium severity
  - Complete Phase 20 filename-based detection coverage
affects: [21-content-detection, 22-extended-patterns]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - Case-insensitive extension matching via str/ends-with? on lowercased paths
    - Exact filename matching for SSH keys (id_rsa, etc.)
    - Severity-based classification (high for keys, medium for PEM)

key-files:
  created: []
  modified:
    - src/aishell/detection/patterns.clj
    - src/aishell/detection/core.clj

key-decisions:
  - "SSH keys (id_rsa, id_dsa, id_ed25519, id_ecdsa, *.ppk) classified as high severity"
  - "Key containers (*.p12, *.pfx, *.jks, *.keystore) classified as high severity"
  - "PEM/key files (*.pem, *.key) classified as medium severity (may be certificates or keys)"

patterns-established:
  - "High-severity cryptographic keys require confirmation in interactive mode"
  - "Pattern detectors use consistent signature: [project-dir excluded-dirs] → findings"

# Metrics
duration: 2min
completed: 2026-01-23
---

# Phase 20 Plan 02: SSH Key and Cryptographic File Detection Summary

**SSH private keys, key containers (PKCS12/JKS), and PEM/key files detected with appropriate severity before AI agent access**

## Performance

- **Duration:** 2 min
- **Started:** 2026-01-23T14:55:56Z
- **Completed:** 2026-01-23T14:57:27Z
- **Tasks:** 3 (2 with commits, 1 verification-only)
- **Files modified:** 2

## Accomplishments

- Added detect-ssh-keys function for id_rsa, id_dsa, id_ed25519, id_ecdsa, and *.ppk files (high severity)
- Added detect-key-containers function for *.p12, *.pfx, *.jks, *.keystore files (high severity)
- Added detect-pem-key-files function for *.pem and *.key files (medium severity)
- Integrated all four pattern detectors into scan-project with combined grouping
- Verified end-to-end detection with comprehensive test covering all file types
- Completed Phase 20 filename-based detection (env files + cryptographic keys)

## Task Commits

Each task was committed atomically:

1. **Task 1: Add SSH key and key container detection to patterns.clj** - `9ef7bee` (feat)
2. **Task 2: Update scan-project to call all detectors** - `1667038` (feat)
3. **Task 3: End-to-end verification with test files** - (verification only, no code changes)

## Files Created/Modified

- `src/aishell/detection/patterns.clj` - Added detect-ssh-keys, detect-key-containers, detect-pem-key-files
- `src/aishell/detection/core.clj` - Updated scan-project to call all four pattern detectors

## Decisions Made

- **SSH key severity:** All SSH private keys (OpenSSH and PuTTY) classified as high severity due to direct credential exposure risk
- **Key container severity:** PKCS12, PFX, JKS, and keystore files classified as high severity (contain encrypted private keys)
- **PEM/key severity:** Classified as medium severity because .pem and .key files may contain certificates (low risk) or private keys (high risk) - content detection in Phase 21 will refine this

## Deviations from Plan

None - plan executed exactly as written.

## Issues Encountered

None - all pattern detectors worked as expected with babashka.fs and case-insensitive filtering.

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness

- Phase 20 complete: All filename-based detection requirements met
  - [x] Environment files (.env variants, .envrc) - Plan 20-01
  - [x] SSH keys (id_rsa, id_dsa, id_ed25519, id_ecdsa, *.ppk) - Plan 20-02
  - [x] Key containers (*.p12, *.pfx, *.jks, *.keystore) - Plan 20-02
  - [x] PEM/key files (*.pem, *.key) - Plan 20-02
- Threshold-based grouping working across all file types
- High-severity findings require user confirmation before container launch
- Ready for Phase 21: Content-based detection (API keys in files, PEM content analysis)
- Ready for Phase 22: Extended patterns (config files, cloud credentials, database dumps)

---
*Phase: 20-filename-detection*
*Completed: 2026-01-23*
