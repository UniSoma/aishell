---
phase: 37
plan: 06
subsystem: docker-templates
tags: [tmux, profile.d, login-shell, environment, gap-closure]
requires: [37-04]
provides: [profile.d-script, tmux-login-shell-environment]
affects: [uat-validation]
tech-stack:
  added: []
  patterns: [login-shell-profile]
key-files:
  created: []
  modified:
    - src/aishell/docker/templates.clj
    - src/aishell/docker/build.clj
decisions:
  - id: profile-d-posix-compatibility
    choice: Use dot-source (. /etc/bash.aishell) instead of source command
    rationale: /etc/profile.d scripts may run under sh (not bash), dot-source is POSIX-compatible
    impact: Ensures compatibility across shell implementations
metrics:
  duration: 1min
  completed: 2026-02-01
---

# Phase 37 Plan 06: Profile.d Login Shell Environment Summary

**One-liner:** Added /etc/profile.d/aishell.sh to fix tmux new-window environment loss (PATH, NODE_PATH, prompt, aliases)

## What Was Built

Fixed GAP-02 (tmux new-window environment loss) by creating a profile.d script that ensures login shells inherit the full aishell environment.

**Problem:** tmux spawns new windows as login shells (bash -l), which source /etc/profile and /etc/profile.d/*.sh but NOT ~/.bashrc. The entrypoint sets PATH/NODE_PATH via export before exec, but these are lost in new tmux windows.

**Solution:** Created /etc/profile.d/aishell.sh that:
- Exports PATH with /tools/npm/bin and /tools/bin for harness tools
- Exports NODE_PATH for module resolution
- Sources /etc/bash.aishell for prompt, aliases, and locale

**Implementation:**
1. Added `profile-d-script` def in templates.clj with PATH/NODE_PATH configuration
2. Wired into base-dockerfile with COPY instruction to /etc/profile.d/aishell.sh
3. Updated write-build-files in build.clj to write profile.d-aishell.sh to build context

## Tasks Completed

| # | Task Name | Status | Commit |
|---|-----------|--------|--------|
| 1 | Create profile.d script content and wire into Dockerfile | ✅ Complete | 3fd0f6e |

## Technical Details

**Key components:**

1. **profile-d-script** (templates.clj):
   - Volume-mounted tools PATH: /tools/npm/bin, /tools/bin
   - NODE_PATH: /tools/npm/lib/node_modules
   - Sources /etc/bash.aishell for shell customizations
   - Uses POSIX-compatible dot-source (. /etc/bash.aishell) not source

2. **Dockerfile integration** (templates.clj):
   - COPY profile.d-aishell.sh /etc/profile.d/aishell.sh
   - Placed after bashrc.aishell, before ENTRYPOINT

3. **Build context** (build.clj):
   - write-build-files now writes profile.d-aishell.sh alongside entrypoint.sh and bashrc.aishell

**Login shell sourcing order:**
1. /etc/profile (sets system PATH, sources /etc/profile.d/*.sh)
2. /etc/profile.d/aishell.sh (sets harness PATH/NODE_PATH, sources bash.aishell)
3. /etc/bash.aishell (sets prompt, aliases, locale)

## Deviations from Plan

None - plan executed exactly as written.

## Decisions Made

**POSIX compatibility in profile.d:**
- Used dot-source (. /etc/bash.aishell) instead of source command
- Rationale: /etc/profile.d scripts may run under sh (not bash), dot-source is POSIX-compatible
- Impact: Ensures compatibility across shell implementations

## Files Changed

**Modified:**
- src/aishell/docker/templates.clj - Added profile-d-script def, updated base-dockerfile
- src/aishell/docker/build.clj - Updated write-build-files to write profile.d-aishell.sh

**Created:**
- None (profile.d script is embedded template content)

## Verification Results

All verification criteria met:
- ✅ profile-d-script def exists with PATH/NODE_PATH and bash.aishell sourcing
- ✅ base-dockerfile contains COPY profile.d-aishell.sh /etc/profile.d/aishell.sh
- ✅ write-build-files writes profile.d-aishell.sh to build context

## Next Phase Readiness

**Ready for:**
- UAT validation: Rebuild foundation image and test tmux new-window environment
- Phase 38: Volume cleanup and documentation

**Blockers:** None

**Concerns:** None - login shell environment wiring is straightforward
