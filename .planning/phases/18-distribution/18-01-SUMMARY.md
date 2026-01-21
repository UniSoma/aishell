# Phase 18 Plan 01: Build Infrastructure Summary

**One-liner:** Static requires enable uberscript bundling; build script produces dist/aishell with shebang and checksum

## Execution Details

| Metric | Value |
|--------|-------|
| Duration | 3 min 39 sec |
| Tasks | 4/4 |
| Deviations | 1 (auto-fixed) |
| Commits | 4 |

## Changes Made

### Task 1: Move version from core.clj to cli.clj

**Commit:** `8d3f799`

Added version constant and print functions to cli.clj:
- `(def version "2.0.0")`
- `print-version` function
- `print-version-json` function

Removed `[aishell.core :as core]` require from cli.clj, breaking the circular dependency.

**Files modified:**
- `src/aishell/cli.clj` - Added version functions, removed core require

### Task 2: Simplify core.clj with static require

**Commit:** `afdfd6f`

Rewrote core.clj to use static require:
- Added `[aishell.cli :as cli]` to ns require
- Removed dynamic `(require '[aishell.cli ...])` from -main
- Removed `(resolve 'aishell.cli/dispatch)` - now calls `cli/dispatch` directly
- Removed version functions (moved to cli.clj)

**Files modified:**
- `src/aishell/core.clj` - Static require, removed version functions

### Task 3: Create build-release.sh script

**Commit:** `d9f2627`

Created `scripts/build-release.sh` that:
1. Creates dist/ directory
2. Removes existing uberscript (bb refuses to overwrite)
3. Runs `bb uberscript dist/aishell -m aishell.core`
4. Adds shebang `#!/usr/bin/env bb` (platform-aware sed)
5. Makes executable with chmod +x
6. Generates checksum file with platform detection (sha256sum/shasum)

**Files created:**
- `scripts/build-release.sh` - Build automation script

### Task 4: Verify uberscript execution

Verification-only task, no file changes.

**Results:**
- `./dist/aishell --version` outputs "aishell 2.0.0"
- `./dist/aishell --help` displays help text
- Shebang present: `#!/usr/bin/env bb`
- 15 namespaces bundled in uberscript
- Checksum verification passes

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] Double version output in uberscript**

- **Found during:** Task 3 verification
- **Issue:** `./dist/aishell --version` printed version twice because both the `when` guard and uberscript's `-m` entry point called `-main`
- **Fix:** Removed `(when (= *file* ...))` guard from core.clj since `-m` handles entry
- **Commit:** `65fa741`

## Decisions Made

| Decision | Rationale |
|----------|-----------|
| Version in cli.clj, not core.clj | Eliminates circular dependency: cli needed version, core needed cli |
| Remove when guard from core.clj | bb uberscript -m adds its own entry; guard caused double execution |
| rm -f before bb uberscript | bb refuses to overwrite existing files |
| Platform detection for sed and sha256 | macOS uses `sed -i ''` and `shasum -a 256`, Linux uses `sed -i` and `sha256sum` |

## Artifacts Produced

| Artifact | Purpose |
|----------|---------|
| `dist/aishell` | Executable uberscript (1653 lines, 15 namespaces) |
| `dist/aishell.sha256` | SHA256 checksum for verification |
| `scripts/build-release.sh` | Reproducible build automation |

## Verification Results

All success criteria met:
- [x] Circular dependency eliminated (cli no longer requires core)
- [x] core.clj uses static requires (no dynamic require hack)
- [x] Build script creates reproducible uberscript artifacts
- [x] Uberscript bundles ALL namespaces (15 total)
- [x] Uberscript executes --version and --help without classpath issues
- [x] Checksum generation works (tested on Linux)

## Next Phase Readiness

**Ready for Plan 02:** curl|bash installer

Prerequisites met:
- dist/aishell exists with shebang
- dist/aishell.sha256 provides verification
- Build script enables reproducible releases

**Blocking issues:** None
