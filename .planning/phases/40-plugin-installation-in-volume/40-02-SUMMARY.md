---
phase: 40-plugin-installation-in-volume
plan: 02
subsystem: infra
tags: [tmux, tpm, volume, plugins, docker]

# Dependency graph
requires:
  - phase: 40-01
    provides: Plugin format validation in config parsing
provides:
  - TPM installation command builder for volume population
  - Config threading from build/update/run commands to volume population
  - Conditional tmux plugin installation during volume creation
affects: [40-03, 40-04]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - Volume population pipeline extended with conditional plugin installation
    - Config passed through to volume population for feature-specific customization

key-files:
  created: []
  modified:
    - src/aishell/docker/volume.clj
    - src/aishell/cli.clj
    - src/aishell/run.clj

key-decisions:
  - "TPM installs to /tools/tmux/plugins with TMUX_PLUGIN_MANAGER_PATH override"
  - "Plugin installation skipped when no plugins declared or :with-tmux false"
  - "Config threading uses optional :config key in opts map for backward compatibility"

patterns-established:
  - "Volume population accepts :config in opts for conditional feature installation"
  - "Shell command builders return nil for graceful skipping of optional features"

# Metrics
duration: 3min
completed: 2026-02-02
---

# Phase 40 Plan 02: TPM and Plugin Installation in Volume Summary

**TPM and declared plugins installed non-interactively into harness volume during build/update with conditional execution based on :with-tmux flag and config**

## Performance

- **Duration:** 3 min
- **Started:** 2026-02-02T12:29:55Z
- **Completed:** 2026-02-02T12:33:10Z
- **Tasks:** 2
- **Files modified:** 3

## Accomplishments
- TPM installation command builder generates correct shell command with git clone, install_plugins, and chmod
- Volume population pipeline conditionally includes tmux plugin installation when :with-tmux enabled and plugins declared
- Config threaded from all populate-volume call sites (build, update, run)
- Graceful skipping when plugins empty/nil or :with-tmux false

## Task Commits

Each task was committed atomically:

1. **Task 1: Add TPM install command builder to volume.clj and extend populate-volume** - `0710d25` (feat)
2. **Task 2: Thread config to populate-volume in build and update commands** - `790b103` (feat)

## Files Created/Modified
- `src/aishell/docker/volume.clj` - Added build-tpm-install-command function, extended populate-volume to accept :config and conditionally include tmux installation
- `src/aishell/cli.clj` - Added aishell.config require, modified handle-build and handle-update to load config and pass to populate-volume
- `src/aishell/run.clj` - Modified ensure-harness-volume to accept config parameter, updated run-container to load config early

## Decisions Made
- TPM installs to /tools/tmux/plugins (non-standard path) with TMUX_PLUGIN_MANAGER_PATH environment variable override for bin/install_plugins
- Plugin config temporarily written to /tmp/plugins.conf (auto-cleaned when --rm container exits)
- Config threading uses optional :config key in opts map to maintain backward compatibility with existing ensure-harness-volume callers
- chmod -R a+rX applied to /tools/tmux for non-root container user access

## Deviations from Plan

None - plan executed exactly as written.

## Issues Encountered

None

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness

Ready for Phase 40-03 (tmux config mounting).

- Plugin installation pipeline complete
- Config properly threaded through all volume population paths
- Empty plugins list gracefully skips TPM installation
- All verification checks pass

---
*Phase: 40-plugin-installation-in-volume*
*Completed: 2026-02-02*
