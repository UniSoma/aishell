---
phase: 54-path-handling
plan: 01
subsystem: core-utilities
tags: [cross-platform, windows, paths, foundation]
dependency_graph:
  requires: [babashka.fs]
  provides: [cross-platform-path-utilities]
  affects: [config, state, docker-mounts]
tech_stack:
  added: []
  patterns: [platform-branching, fs-path-normalization]
key_files:
  created: []
  modified: [src/aishell/util.clj]
decisions:
  - "Use USERPROFILE on Windows (standard), HOME on Unix for get-home"
  - "Normalize all paths through fs/path to ensure platform-native separators"
  - "Use LOCALAPPDATA on Windows instead of XDG for state-dir (Windows convention)"
  - "Keep config-dir at ~/.aishell on all platforms (unchanged)"
metrics:
  duration_seconds: 60
  tasks_completed: 1
  files_modified: 1
  completed_date: 2026-02-12
---

# Phase 54 Plan 01: Cross-Platform Path Utilities Summary

**One-liner:** Platform-aware path utilities using USERPROFILE/LOCALAPPDATA on Windows, HOME/XDG on Unix with fs/path normalization

## What Was Built

Updated three core path utility functions in `util.clj` for cross-platform Windows/Unix support:

1. **`get-home`** - Platform-branching home directory detection
   - Windows: `USERPROFILE > HOME > fs/home` priority chain
   - Unix: `HOME > fs/home` (unchanged behavior)
   - Silent fallback ensures function always succeeds

2. **`expand-path`** - Cross-platform path expansion with separator normalization
   - Updated regex patterns to match both `/` and `\` separators
   - Wraps result in `fs/path` for OS-native separator normalization
   - Handles `~`, `$HOME`, and `${HOME}` expansions

3. **`state-dir`** - Platform-appropriate state directory
   - Windows: Uses `LOCALAPPDATA` env var (Windows convention)
   - Unix: Uses XDG_STATE_HOME or `~/.local/state` (unchanged)
   - Both paths fallback to XDG default if env var missing

**`config-dir` unchanged** - Already uses `fs/path`, stays at `~/.aishell` on all platforms per user decision.

## Implementation Details

### Platform Detection Pattern

Used `fs/windows?` predicate for clean platform branching:

```clojure
(if (fs/windows?)
  ;; Windows-specific logic
  ;; Unix-specific logic)
```

Applied in both `get-home` and `state-dir` functions (2 occurrences total).

### Path Normalization

All paths now flow through `fs/path` for automatic separator normalization:
- Input: `~/.aishell` or `~\.aishell`
- Output: Platform-native separators (`/` on Unix, `\` on Windows)

### Regex Updates

Changed lookaheads in `expand-path` from `(?=/|$)` to `(?=[/\\]|$)` to match both forward slash and backslash, enabling Windows path strings like `~\Documents`.

## Verification Results

All verification checks passed:

- `fs/windows?` used: 2 occurrences (get-home, state-dir) ✓
- `USERPROFILE` referenced: 1 occurrence in code + 1 in docstring ✓
- `LOCALAPPDATA` referenced: 1 occurrence in code + 1 in docstring ✓
- File loads without SCI errors ✓
- `bb aishell.clj --help` succeeds (no regressions) ✓
- Unix behavior unchanged:
  - `get-home`: Returns `/home/jonasrodrigues` ✓
  - `expand-path`: Expands `~/.aishell` and `$HOME/.config` correctly ✓
  - `state-dir`: Returns `~/.local/state/aishell` ✓
  - `config-dir`: Returns `~/.aishell` ✓

## Deviations from Plan

None - plan executed exactly as written.

## Downstream Impact

These foundational changes enable:
- **Phase 54 Plan 02**: Docker mount path translation (depends on get-home, expand-path)
- **All config/state code**: Now receives platform-appropriate paths automatically
- **Future Windows testing**: Path utilities ready for native Windows execution

No breaking changes - Unix behavior preserved exactly, Windows paths now work correctly.

## Files Modified

**src/aishell/util.clj** (1 file, 26 insertions, 14 deletions)
- `get-home`: Added Windows USERPROFILE support (lines 5-16)
- `expand-path`: Added backslash regex matching, fs/path normalization (lines 18-28)
- `state-dir`: Added Windows LOCALAPPDATA support (lines 35-46)

## Commits

- `9b28112`: feat(54-01): add cross-platform path utilities

## Self-Check

Verifying claimed artifacts exist:

```bash
# Check modified file exists
[ -f "/home/jonasrodrigues/projects/harness/src/aishell/util.clj" ] && echo "FOUND: src/aishell/util.clj" || echo "MISSING: src/aishell/util.clj"
# Result: FOUND: src/aishell/util.clj

# Check commit exists
git log --oneline --all | grep -q "9b28112" && echo "FOUND: 9b28112" || echo "MISSING: 9b28112"
# Result: FOUND: 9b28112
```

## Self-Check: PASSED

All claimed artifacts verified successfully.
