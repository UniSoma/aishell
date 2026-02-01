---
phase: 37-build-integration-migration
plan: 05
subsystem: infra
tags: [opencode, volume, binary-install, path-configuration]

# Dependency graph
requires:
  - phase: 36-volume-population
    provides: Volume population framework with npm package installation
  - phase: 37-04
    provides: Build integration wiring for volume lifecycle
provides:
  - OpenCode binary installation during volume population
  - /tools/bin in container PATH for binary harnesses
affects: [38-volume-cleanup, future binary harnesses]

# Tech tracking
tech-stack:
  added: [anomalyco/opencode binary]
  patterns: [Binary download pattern for Go-based harnesses]

key-files:
  created: []
  modified:
    - src/aishell/docker/volume.clj
    - src/aishell/docker/templates.clj

key-decisions:
  - "Use anomalyco/opencode GitHub releases (opencode-linux-x64.tar.gz)"
  - "Install binaries to /tools/bin separate from npm packages in /tools/npm"
  - "Add /tools/bin to PATH via directory existence check (same pattern as npm)"

patterns-established:
  - "Binary harness installation: curl | tar -xz -C /tools/bin"
  - "Conditional PATH addition based on directory existence"

# Metrics
duration: 2min
completed: 2026-02-01
---

# Phase 37 Plan 05: OpenCode Binary Installation Summary

**OpenCode Go binary downloaded from GitHub releases during volume population and /tools/bin wired into container PATH**

## Performance

- **Duration:** 2 min
- **Started:** 2026-02-01T19:52:33Z
- **Completed:** 2026-02-01T19:54:47Z
- **Tasks:** 2
- **Files modified:** 2

## Accomplishments
- OpenCode binary (anomalyco/opencode) downloads from GitHub releases when --with-opencode is enabled
- Binary installs to /tools/bin alongside npm packages in /tools/npm
- Container PATH includes /tools/bin when directory exists, making opencode command available
- Supports both "latest" and version-specific downloads via version-tagged releases

## Task Commits

Each task was committed atomically:

1. **Task 1: Add OpenCode binary download to volume population** - `43945c5` (feat)
2. **Task 2: Add /tools/bin to PATH in entrypoint** - `b8d16e1` (feat)

**Plan metadata:** (to be committed)

## Files Created/Modified
- `src/aishell/docker/volume.clj` - Added build-opencode-install-command helper and integrated into build-install-commands
- `src/aishell/docker/templates.clj` - Added /tools/bin to PATH conditional block in entrypoint script

## Decisions Made

**OpenCode source:** Used anomalyco/opencode GitHub releases after verifying repository (plan referenced opencodeco which doesn't exist). Tarball structure confirmed as single binary named "opencode".

**Binary directory:** Installed to /tools/bin separate from npm packages to maintain clear separation between package managers (npm vs direct binary downloads).

**PATH pattern:** Followed existing /tools/npm/bin pattern with directory existence check for consistency and safety.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] Corrected OpenCode GitHub organization**
- **Found during:** Task 1 (researching download URL)
- **Issue:** Plan referenced opencodeco/opencode which returned 404. Actual repository is anomalyco/opencode.
- **Fix:** Updated URL to https://github.com/anomalyco/opencode based on existing codebase references and API verification
- **Files modified:** src/aishell/docker/volume.clj (used correct URL in implementation)
- **Verification:** GitHub API confirmed anomalyco/opencode exists with releases containing opencode-linux-x64.tar.gz
- **Committed in:** 43945c5 (Task 1 commit)

---

**Total deviations:** 1 auto-fixed (1 bug - incorrect GitHub org)
**Impact on plan:** Necessary correction to use actual OpenCode repository. No scope impact.

## Issues Encountered
None - straightforward binary download integration following established npm installation pattern.

## User Setup Required
None - no external service configuration required.

## Next Phase Readiness
- OpenCode binary installation complete, closing GAP-01
- Volume population now handles both npm packages and Go binaries
- Pattern established for future binary-based harnesses
- Ready for volume cleanup and documentation (Phase 38)

---
*Phase: 37-build-integration-migration*
*Completed: 2026-02-01*
