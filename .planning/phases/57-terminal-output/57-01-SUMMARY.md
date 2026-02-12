---
phase: 57-terminal-output
plan: 01
subsystem: terminal-io
tags: [color-detection, windows-support, standards-compliance, ansi-colors]
dependency_graph:
  requires: [babashka-fs]
  provides: [centralized-color-detection, windows-terminal-support, no-color-spec, force-color-spec]
  affects: [output, detection-formatters, check]
tech_stack:
  added: []
  patterns: [environment-variable-precedence, platform-detection]
key_files:
  created: []
  modified:
    - src/aishell/output.clj
    - src/aishell/detection/formatters.clj
    - src/aishell/check.clj
decisions:
  - Made colors-enabled? public for cross-module use
  - Implemented NO_COLOR > FORCE_COLOR > auto-detection priority per community standards
  - Added empty string validation per NO_COLOR/FORCE_COLOR specification
  - Used babashka.fs/windows? for platform detection (established pattern)
  - Preserved spinner.clj's separate TTY check (different concern)
metrics:
  duration: 84s
  tasks_completed: 2
  files_modified: 3
  commits: 2
  completed_date: 2026-02-12
---

# Phase 57 Plan 01: Windows-Aware Color Detection Summary

**One-liner:** Standards-compliant ANSI color detection with Windows Terminal/ConEmu support and centralized decision logic

## Objective

Enhanced ANSI color detection to support Windows terminals and community standards (NO_COLOR/FORCE_COLOR), then centralized all color detection through a single public function.

## What Was Done

### Task 1: Enhanced colors-enabled? with standards-compliant detection and Windows support

**Files modified:** `src/aishell/output.clj`

**Changes:**
- Changed `colors-enabled?` from private (`defn-`) to public (`defn`) for cross-module use
- Added `babashka.fs` namespace requirement for platform detection
- Implemented NO_COLOR > FORCE_COLOR > auto-detection priority ordering
- Added empty string validation per NO_COLOR/FORCE_COLOR specification
- Added Windows Terminal detection via `WT_SESSION` environment variable
- Added ConEmu detection via `ConEmuANSI=ON` environment variable
- Added `COLORTERM` detection for modern terminals
- Made TERM check Unix-specific using `(not (fs/windows?))`
- Used `babashka.fs/windows?` for platform detection (established pattern from Phases 53-56)

**Commit:** 28a82b7

### Task 2: Centralized color detection in formatters.clj and check.clj

**Files modified:** `src/aishell/detection/formatters.clj`, `src/aishell/check.clj`

**Changes:**
- Replaced inline `(System/console)` check in formatters.clj DIM definition with `output/colors-enabled?`
- Replaced inline `(System/console)` check in check.clj GREEN definition with `output/colors-enabled?`
- Preserved spinner.clj's separate `(System/console)` check (TTY detection for spinner display, not color)
- All color detection now flows through single centralized function

**Commit:** c16b37c

## Deviations from Plan

None - plan executed exactly as written.

## Technical Details

### NO_COLOR/FORCE_COLOR Implementation

Follows community standards (https://no-color.org/):
- NO_COLOR with non-empty value disables all colors (highest priority)
- FORCE_COLOR with non-empty value enables colors even when piped (overrides auto-detection)
- Empty string values are ignored per specification

### Windows Terminal Detection

The function now detects:
- **Windows Terminal**: `WT_SESSION` environment variable set
- **ConEmu**: `ConEmuANSI=ON` environment variable
- **Modern terminals**: `COLORTERM` set (works on any platform)
- **Unix terminals**: `TERM` variable (not "dumb") - only checked on Unix

### Platform Detection

Uses `babashka.fs/windows?` for platform detection:
- Windows-specific checks: `WT_SESSION`, `ConEmuANSI`
- Unix-specific checks: `TERM` variable
- Platform-agnostic checks: `COLORTERM`, `System/console`

## Verification Results

All verification steps passed:
1. Syntax check: All 3 modules load without errors
2. Public API: `aishell.output/colors-enabled?` resolves (public function)
3. No inline color checks: Zero matches for `(System/console)` in color definitions (spinner.clj correctly excluded)
4. Smoke test: `bb -cp src -m aishell.core -- --help` runs without crash
5. NO_COLOR test: `NO_COLOR=1` successfully disables all ANSI escape codes

## Success Criteria Met

- [x] `colors-enabled?` is public and implements NO_COLOR > FORCE_COLOR > auto-detection priority
- [x] Windows Terminal (WT_SESSION) and ConEmu (ConEmuANSI=ON) detected as ANSI-capable
- [x] All ANSI color definitions across all 3 files use `output/colors-enabled?`
- [x] No inline `(System/console)` checks for color decisions remain (spinner.clj excluded - different concern)
- [x] `aishell --help` works without errors on current platform

## Impact

### For Users

- **Windows users**: Get colored output in Windows Terminal and ConEmu automatically
- **Unix users**: No behavior change (existing detection preserved)
- **All users**: Can disable colors with `NO_COLOR=1` (proper spec compliance)
- **CI/CD pipelines**: Can force colors with `FORCE_COLOR=1` when piped

### For Codebase

- **Single source of truth**: All modules use centralized detection
- **Maintainability**: Color detection logic in one place
- **Testability**: Public function can be tested in isolation
- **Consistency**: All color output respects same detection logic

## Self-Check: PASSED

All files and commits verified:

**Files exist:**
- FOUND: src/aishell/output.clj
- FOUND: src/aishell/detection/formatters.clj
- FOUND: src/aishell/check.clj

**Commits exist:**
- FOUND: 28a82b7 (Task 1)
- FOUND: c16b37c (Task 2)

**Modified files contain expected changes:**
- `src/aishell/output.clj`: Contains public `colors-enabled?` with NO_COLOR/FORCE_COLOR/WT_SESSION/ConEmuANSI logic
- `src/aishell/detection/formatters.clj`: DIM uses `output/colors-enabled?`
- `src/aishell/check.clj`: GREEN uses `output/colors-enabled?`
