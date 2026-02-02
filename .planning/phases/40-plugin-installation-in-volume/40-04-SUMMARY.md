---
phase: 40-plugin-installation-in-volume
plan: 04
subsystem: infra
tags: [tmux, tpm, docker, volume, plugins]

# Dependency graph
requires:
  - phase: 40-03
    provides: "Idempotent TPM git clone and volume hash including tmux state"
provides:
  - "TPM plugin installation working - declared plugins appear in /tools/tmux/plugins/"
  - "Fixed build-tpm-install-command to write declarations where TPM reads them"
affects: [41-tpm-initialization-in-entrypoint]

# Tech tracking
tech-stack:
  added: []
  patterns: ["TPM plugin declaration via ~/.tmux.conf for build-time installation"]

key-files:
  created: []
  modified: ["src/aishell/docker/volume.clj"]

key-decisions:
  - "Write plugin declarations to ~/.tmux.conf (not /tmp/plugins.conf) for TPM compatibility"
  - "Use ~/.tmux.conf in ephemeral build container - does not conflict with runtime mount"

patterns-established:
  - "TPM install_plugins AWK parser requires plugin declarations in ~/.tmux.conf"
  - "Build container ~/.tmux.conf is ephemeral to build phase only"

# Metrics
duration: <1min
completed: 2026-02-02
---

# Phase 40 Plan 04: TPM Plugin Declaration Path Fix Summary

**TPM plugin installation fixed by writing declarations to ~/.tmux.conf where install_plugins AWK parser reads them**

## Performance

- **Duration:** <1 min
- **Started:** 2026-02-02T14:17:27Z
- **Completed:** 2026-02-02T14:17:53Z
- **Tasks:** 1
- **Files modified:** 1

## Accomplishments
- Fixed plugin declaration path from /tmp/plugins.conf to ~/.tmux.conf
- TPM install_plugins now correctly discovers and installs declared plugins
- UAT Test 2 gap closed - declared plugins appear in /tools/tmux/plugins/

## Task Commits

Each task was committed atomically:

1. **Task 1: Fix plugin declaration path in build-tpm-install-command** - `9c77430` (fix)

## Files Created/Modified
- `src/aishell/docker/volume.clj` - Changed line 209 to write plugin declarations to ~/.tmux.conf instead of /tmp/plugins.conf

## Decisions Made

**Write plugin declarations to ~/.tmux.conf for TPM compatibility**
- TPM's install_plugins script uses AWK to parse ~/.tmux.conf for `set -g @plugin` lines
- Writing to any other path is invisible to TPM's parser
- The build container runs as root, so ~/.tmux.conf resolves to /root/.tmux.conf in the ephemeral build container
- This file is temporary to the --rm build container and does not conflict with the user's tmux.conf mounted at runtime

**Use ~/.tmux.conf instead of explicit /root/.tmux.conf**
- Maintains portability regardless of container user
- Follows shell convention of ~ expansion

## Deviations from Plan

None - plan executed exactly as written.

## Issues Encountered

None

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness

- TPM plugin installation now fully functional during build
- Declared plugins installed into /tools/tmux/plugins/ during volume population
- Ready for Phase 41 (TPM initialization in entrypoint) to handle runtime initialization
- All Phase 40 UAT gaps now closed

---
*Phase: 40-plugin-installation-in-volume*
*Completed: 2026-02-02*
