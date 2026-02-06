---
phase: 48-docker-run-arguments-cleanup
plan: 02
subsystem: docker-volume-management
tags: [docker, volume, tmux-removal, clojure, babashka]
requires:
  - 48-01
provides:
  - "Volume management without tmux plugin installation"
  - "Volume hash calculation excludes tmux state"
  - "Simplified harness volume population"
affects:
  - 48-03
tech-stack:
  removed:
    - "TPM (Tmux Plugin Manager)"
    - "tmux-resurrect plugin"
key-files:
  modified:
    - src/aishell/docker/volume.clj
    - src/aishell/run.clj
decisions:
  - title: "Remove tmux from volume hash calculation"
    rationale: "Tmux state no longer needs to be part of harness volume identity"
    impact: "Existing volumes remain compatible, new volumes exclude tmux state"
  - title: "Delete TPM installation functions"
    rationale: "No longer installing tmux plugins in harness volumes"
    impact: "Cleaner volume.clj, reduced complexity"
metrics:
  duration: "87 seconds"
  completed: "2026-02-06"
---

# Phase 48 Plan 02: Remove tmux from volume management Summary

**One-liner:** Eliminated tmux plugin installation and state from harness volume hash and population logic.

## Overview

Removed all tmux-related code from volume management (`docker/volume.clj`) and simplified the harness volume check in the launcher (`run.clj`). This eliminates TPM installation, plugin management, resurrect plugin injection, and tmux state from volume hash calculation.

## What Was Built

### Volume Management Cleanup (volume.clj)
- **Deleted functions:**
  - `inject-resurrect-plugin` - Auto-add resurrect to plugin list (no callers)
  - `build-tpm-install-command` - TPM and plugin installation command builder

- **Simplified functions:**
  - `normalize-harness-config` - Removed tmux-state from hash calculation (no longer includes tmux plugins in volume identity)
  - `populate-volume` - Removed TPM installation logic, updated docstring

### Launcher Cleanup (run.clj)
- **Simplified harness check:**
  - Removed `:with-tmux` from `ensure-harness-volume` decision vector
  - Volume check now only considers actual harness tools (claude, opencode, codex, gemini)

## Task Commits

| Task | Description | Commit | Files |
|------|-------------|--------|-------|
| 1 | Delete tmux functions and clean up volume hash and population | 5ea4459 | src/aishell/docker/volume.clj |
| 2 | Remove :with-tmux from ensure-harness-volume check | 1f62f39 | src/aishell/run.clj |

## Deviations from Plan

None - plan executed exactly as written.

## Technical Details

### Changes to Volume Hash Calculation
The `normalize-harness-config` function previously included tmux state in the volume hash:
```clojure
;; BEFORE: Hash included tmux plugins
[:tmux {:plugins (vec (sort (or (:tmux-plugins state) [])))}]

;; AFTER: Tmux state excluded entirely
```

This change means:
- Volumes are now identified solely by harness tools (claude, opencode, codex, gemini)
- Existing volumes remain valid (hash backward compatible for non-tmux configs)
- No tmux plugin state affects volume identity

### Changes to Volume Population
The `populate-volume` function previously installed TPM and plugins:
```clojure
;; BEFORE: Combined npm + TPM installation
(str install-commands (when tmux-install (str " && " tmux-install)))

;; AFTER: Only npm packages and OpenCode binary
install-commands
```

### Babashka/SCI Safety
Before deleting functions, verified no callers existed:
- `inject-resurrect-plugin` - 0 callers (safe to delete)
- `build-tpm-install-command` - 1 caller in `populate-volume` (updated in same edit)

## Verification Results

All verifications passed:
- ✓ `aishell.docker.volume` namespace loads cleanly
- ✓ `aishell.run` namespace loads cleanly
- ✓ `aishell.cli` namespace loads cleanly (full tool test)
- ✓ No tmux/tpm/resurrect references in `docker/volume.clj`
- ✓ No with-tmux references in `run.clj`
- ✓ No resurrect references in docker runtime code paths

## Next Phase Readiness

**Blockers:** None

**Concerns:** None

**Phase 48-03 (next):** Ready to proceed with CLI flag removal and state schema cleanup.

## Self-Check: PASSED

All commits verified:
- ✓ Commit 5ea4459 exists
- ✓ Commit 1f62f39 exists

All modified files verified:
- ✓ src/aishell/docker/volume.clj modified
- ✓ src/aishell/run.clj modified
