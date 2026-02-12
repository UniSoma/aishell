---
phase: 53-platform-detection
plan: 01
subsystem: platform-detection
tags:
  - cross-platform
  - windows-support
  - error-handling
dependency_graph:
  requires:
    - babashka.fs
  provides:
    - platform-guarded-uid-gid
    - platform-guarded-exec
  affects:
    - src/aishell/docker/run.clj
    - src/aishell/run.clj
    - src/aishell/attach.clj
tech_stack:
  added:
    - babashka.fs/windows? predicate
  patterns:
    - platform-guard-pattern
key_files:
  created: []
  modified:
    - src/aishell/docker/run.clj
    - src/aishell/run.clj
    - src/aishell/attach.clj
decisions:
  - Guard Unix-specific host operations with fs/windows? checks
  - Throw ex-info with Phase reference for future implementation guidance
  - Leave container paths unguarded (Linux containers work on all platforms)
metrics:
  duration_seconds: 81
  completed_at: "2026-02-11T21:37:02Z"
  tasks_completed: 2
  files_modified: 3
  commits: 2
---

# Phase 53 Plan 01: Platform Detection Guards Summary

**One-liner:** Added platform guards to UID/GID extraction and process replacement using babashka.fs/windows? predicate to prevent crashes on Windows.

## Objective

Add platform detection guards to all Unix-specific host-side code paths using `babashka.fs/windows?`. This prevents runtime crashes when aishell runs on Windows by guarding `id -u`/`id -g` commands and `p/exec` calls with platform checks that throw informative errors pointing to later phases that will implement Windows alternatives.

## Implementation Summary

### Task 1: Guard UID/GID extraction in docker/run.clj

**Commit:** f15f2c1

Modified `get-uid` and `get-gid` functions in `src/aishell/docker/run.clj` to check platform before executing Unix `id` command.

**Changes:**
- Wrapped `get-uid` logic in `(if (fs/windows?) (throw ...) ...)` pattern
- Wrapped `get-gid` logic with same platform guard
- Both throw `ex-info` on Windows with message: "UID/GID detection not supported on Windows. See: Phase 55 (Host Identity)"
- Unix behavior unchanged - guards only activate when `(fs/windows?)` returns true
- Leveraged existing `[babashka.fs :as fs]` import (line 5)

**Files modified:**
- src/aishell/docker/run.clj (lines 28-32)

### Task 2: Guard p/exec calls in run.clj and attach.clj

**Commit:** b2649b1

Added platform guards to process replacement (`p/exec`) calls in both run and attach modules.

**Changes in src/aishell/run.clj:**
- Added `[babashka.fs :as fs]` to ns require vector
- Wrapped `(apply p/exec ...)` at line 243 in platform check
- Throws ex-info on Windows: "Process replacement (p/exec) not supported on Windows. See: Phase 56 (Process & Execution)"
- Did NOT guard gitleaks branch (uses `p/shell`, already cross-platform)

**Changes in src/aishell/attach.clj:**
- Added `[babashka.fs :as fs]` to ns require vector
- Wrapped `(p/exec "docker" "exec" ...)` at line 65 in platform check
- Same ex-info pattern as run.clj with Phase 56 reference

**Files modified:**
- src/aishell/run.clj (ns declaration + line 243)
- src/aishell/attach.clj (ns declaration + line 65)

## Verification Results

All verification steps passed:

1. **Platform detection works:**
   ```bash
   bb -e "(require '[babashka.fs :as fs]) (println (fs/windows?))"
   # Output: false (on Unix host)
   ```

2. **All files load successfully:**
   ```bash
   bb -e "(require '[aishell.docker.run])"  # OK
   bb -e "(require '[aishell.run])"         # OK
   bb -e "(require '[aishell.attach])"      # OK
   ```

3. **4 guards in place (grep confirmed):**
   - 2 in docker/run.clj (get-uid line 29, get-gid line 35)
   - 1 in run.clj (line 244)
   - 1 in attach.clj (line 66)

4. **CLI still works on Unix:**
   ```bash
   bb aishell.clj --help  # Shows help without errors
   ```

5. **Container paths NOT guarded:**
   - Grep for `fs/windows?` shows no matches near `/bin/bash`, `/usr/local/bin`, `/etc/passwd`, or `/tmp/`
   - Container operations remain unchanged

## Deviations from Plan

None - plan executed exactly as written. All implementation details matched the specification:
- Used existing `babashka.fs` import in docker/run.clj
- Added `babashka.fs` to run.clj and attach.clj as planned
- Applied platform guard pattern consistently across all 4 locations
- Maintained exact error message format with Phase references
- Left container paths unguarded as instructed

## Key Decisions

1. **Platform guard pattern:** Chose `(if (fs/windows?) (throw ...) ...)` pattern for clarity and consistency across all guarded functions.

2. **Error messages reference future phases:** Ex-info messages point to Phase 55 (Host Identity) for UID/GID and Phase 56 (Process & Execution) for p/exec, providing clear roadmap for Windows implementation.

3. **Container paths left unguarded:** Correctly identified that paths like `/bin/bash`, `/usr/local/bin` run inside Linux containers and work on all host platforms via Docker.

4. **p/shell calls NOT guarded:** Correctly identified that `p/shell` (used in gitleaks branch) is already cross-platform and doesn't need guards.

## Testing Evidence

**File loads (SCI analysis-time resolution):**
All three files load in Babashka without SCI errors, confirming correct symbol resolution at analysis time.

**Platform detection callable:**
`(fs/windows?)` returns `false` on Unix host, confirming predicate is correctly imported and callable.

**CLI functional:**
`bb aishell.clj --help` executes successfully, confirming no behavior change on Unix systems.

## Impact

### Changed Behavior
None on Unix systems - all guards are inactive when `(fs/windows?)` returns false.

### New Capabilities
Windows users now get informative error messages instead of cryptic crashes when attempting operations that require Unix commands or process replacement.

### Foundation for Future Work
Guards provide clear error messages pointing to:
- Phase 55: Will implement Windows-compatible UID/GID defaults
- Phase 56: Will implement Windows-compatible process alternatives (p/process with :inherit)

## Files Modified

**src/aishell/docker/run.clj** (2 guards)
- Lines 28-32: get-uid platform guard
- Lines 34-38: get-gid platform guard

**src/aishell/run.clj** (1 guard + import)
- Line 5: Added `[babashka.fs :as fs]` to requires
- Lines 244-247: p/exec platform guard in run-container

**src/aishell/attach.clj** (1 guard + import)
- Line 5: Added `[babashka.fs :as fs]` to requires
- Lines 66-73: p/exec platform guard in attach-to-container

## Self-Check: PASSED

**Created files:** None (plan specified modifications only)

**Modified files exist:**
```bash
[ -f "src/aishell/docker/run.clj" ] && echo "FOUND: src/aishell/docker/run.clj"
# FOUND: src/aishell/docker/run.clj
[ -f "src/aishell/run.clj" ] && echo "FOUND: src/aishell/run.clj"
# FOUND: src/aishell/run.clj
[ -f "src/aishell/attach.clj" ] && echo "FOUND: src/aishell/attach.clj"
# FOUND: src/aishell/attach.clj
```

**Commits exist:**
```bash
git log --oneline --all | grep -q "f15f2c1" && echo "FOUND: f15f2c1"
# FOUND: f15f2c1
git log --oneline --all | grep -q "b2649b1" && echo "FOUND: b2649b1"
# FOUND: b2649b1
```

All files and commits verified. Self-check passed.
