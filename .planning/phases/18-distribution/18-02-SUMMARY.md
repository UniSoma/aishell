---
phase: 18-distribution
plan: 02
subsystem: infra
tags: [curl-bash, installer, checksum, sha256]

# Dependency graph
requires:
  - phase: 18-01
    provides: dist/aishell and dist/aishell.sha256 release artifacts
provides:
  - curl|bash installer with checksum verification
  - Partial download protection via function wrapper
  - Babashka dependency check with install guidance
  - PATH detection and quick start instructions
affects: [README, documentation]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - Function-wrapped installer for partial download protection
    - SHA256 checksum verification with platform detection
    - curl/wget support with retry

key-files:
  created: []
  modified:
    - install.sh

key-decisions:
  - "VERSION and INSTALL_DIR env vars for customization"
  - "Platform-agnostic checksum (sha256sum/shasum -a 256)"
  - "Advisory PATH warning (never modify shell profiles automatically)"

patterns-established:
  - "Pattern: Wrap curl|bash scripts in function, call on last line"
  - "Pattern: Check both curl and wget availability for download"
  - "Pattern: Clean up downloaded binary on checksum failure"

# Metrics
duration: 2min 38sec
completed: 2026-01-21
---

# Phase 18 Plan 02: curl|bash Installer Summary

**Function-wrapped installer with SHA256 verification downloads from GitHub Releases, checks Babashka dependency, and provides PATH guidance**

## Performance

- **Duration:** 2 min 38 sec
- **Started:** 2026-01-21T20:25:01Z
- **Completed:** 2026-01-21T20:27:39Z
- **Tasks:** 2/2
- **Files modified:** 1

## Accomplishments

- Created curl|bash installer with partial download protection
- Implemented SHA256 checksum verification with Linux/macOS support
- Added Babashka dependency check with helpful install URL
- Added PATH detection with clear guidance when ~/.local/bin not in PATH
- Tested full installation flow with local HTTP server

## Task Commits

Each task was committed atomically:

1. **Task 1: Create v2.0 installer script** - `725d833` (feat)
2. **Task 2: Test installer end-to-end with local HTTP server** - (verification only, no commit)

## Files Created/Modified

- `install.sh` - v2.0 curl|bash installer with checksum verification

## Decisions Made

1. **VERSION and INSTALL_DIR env vars** - Support pinning specific versions and custom install locations for advanced users
2. **Platform-agnostic checksum** - Use sha256sum on Linux, shasum -a 256 on macOS for cross-platform support
3. **Advisory PATH warning** - Never modify shell profiles automatically; provide copy-pasteable instructions instead

## Deviations from Plan

None - plan executed exactly as written.

## Issues Encountered

- Initial attempt to use Babashka http-server library failed due to deps resolution in background process
- Resolved by using Babashka's built-in http-kit server for testing

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness

**Ready for use:**
- Installer can be run via: `curl -fsSL https://raw.githubusercontent.com/UniSoma/aishell/main/install.sh | bash`
- Once GitHub Release is created with aishell and aishell.sha256, installer will work

**Prerequisites for production use:**
- Create GitHub Release with v2.0.0 tag
- Upload dist/aishell and dist/aishell.sha256 to the release
- Update README with new installation instructions

**Blocking issues:** None

---
*Phase: 18-distribution*
*Completed: 2026-01-21*
