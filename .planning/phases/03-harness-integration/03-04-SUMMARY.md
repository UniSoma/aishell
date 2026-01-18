---
phase: 03-harness-integration
plan: 04
subsystem: infra
tags: [xdg, opencode, entrypoint, docker, permissions]

# Dependency graph
requires:
  - phase: 03-harness-integration
    provides: OpenCode/Claude Code installation, container user creation
provides:
  - XDG directory structure for harness apps
  - Fix for OpenCode EACCES permission error
affects: []

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "XDG standard directories created at container startup"

key-files:
  created: []
  modified:
    - entrypoint.sh

key-decisions:
  - "Create ~/.local/{state,share,bin} at container startup for XDG compliance"

patterns-established:
  - "XDG directories: entrypoint creates standard XDG directories before user switch"

# Metrics
duration: 2min
completed: 2026-01-17
---

# Phase 03 Plan 04: Fix OpenCode XDG State Directory Summary

**XDG directory creation in entrypoint fixes OpenCode EACCES error and Claude Code ~/.local/bin warning**

## Performance

- **Duration:** 2 min
- **Started:** 2026-01-17T20:54:44Z
- **Completed:** 2026-01-17T20:56:50Z
- **Tasks:** 1 (+ 1 human verification documented)
- **Files modified:** 1

## Accomplishments

- Added XDG directory creation to entrypoint.sh
- Created ~/.local/state (fixes OpenCode EACCES error)
- Created ~/.local/share (common app data location)
- Created ~/.local/bin (fixes Claude Code PATH warning)
- Set proper ownership on ~/.local tree

## Task Commits

Each task was committed atomically:

1. **Task 1: Create XDG directories in entrypoint** - `d0849ae` (fix)
2. **Task 2: Rebuild and verify** - Human verification step (documented below)

## Files Created/Modified

- `entrypoint.sh` - Added XDG directory creation after home directory setup

## Human Verification Required (Task 2)

The following verification steps should be performed manually:

**Commands to run:**
```bash
# Rebuild image with both harnesses
./aishell --with-opencode --with-claude

# Test OpenCode starts without permission errors
./aishell opencode
```

**Success criteria:**
- [ ] `./aishell opencode` launches without EACCES permission errors
- [ ] OpenCode can connect to API and show interface
- [ ] Claude Code warning about ~/.local/bin disappears

## Decisions Made

- Create all three XDG standard directories (state, share, bin) at once for completeness
- Use recursive chown on ~/.local to handle any subdirectory structure

## Deviations from Plan

None - plan executed exactly as written.

## Issues Encountered

None.

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness

- XDG directories now available for all container applications
- OpenCode permission issue should be resolved (pending human verification)
- Ready for Phase 4 (Project Customization)

---
*Phase: 03-harness-integration*
*Completed: 2026-01-17*
