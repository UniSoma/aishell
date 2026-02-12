---
phase: 58
plan: 01
subsystem: build-pipeline
status: complete
completed: 2026-02-12T02:54:51Z
duration: 149s
executor: sonnet

tags:
  - windows
  - build
  - distribution
  - release-pipeline

dependency_graph:
  requires:
    - babashka.fs/windows? (platform detection)
    - neil-pattern (4-line .bat wrapper design)
  provides:
    - dist/aishell.bat (Windows CMD wrapper)
    - Windows-native execution (aishell command without bb prefix)
  affects:
    - build-release.clj (artifact generation)
    - create-release.clj (release assets)
    - release.yml (CI validation)

tech_stack:
  added:
    - Windows .bat wrapper generation
  patterns:
    - CRLF line endings for Windows compatibility
    - neil-pattern (4-line minimal wrapper)
    - Platform-guarded chmod execution

key_files:
  created:
    - dist/aishell.bat: "Windows CMD wrapper with CRLF line endings"
  modified:
    - scripts/build-release.clj: "Added create-bat-wrapper function and generation logic"
    - scripts/create-release.clj: "Added dist-bat asset to release pipeline"
    - .github/workflows/release.yml: "Added .bat artifact verification"

decisions:
  - title: "Use neil-pattern 4-line minimal wrapper"
    rationale: "Simple, auditable, no complex logic needed"
    alternatives: ["bb.exe detection", "PowerShell wrapper"]
    impact: "Windows users can run aishell from PATH without bb prefix"

  - title: "No checksum for .bat file"
    rationale: "4-line file is easily human-auditable, unlike binary"
    impact: "Simpler distribution, no additional checksum to manage"

  - title: "Guard chmod with fs/windows?"
    rationale: "chmod doesn't exist on Windows, causes build failure"
    alternatives: ["Try/catch", "shell detection"]
    impact: "Cross-platform build compatibility"
---

# Phase 58 Plan 01: Windows .bat Wrapper Generation

**One-liner:** Generate Windows .bat wrapper during build and include in GitHub Releases, enabling native `aishell` execution in cmd.exe/PowerShell

## Overview

Added Windows .bat wrapper generation to the build pipeline following the neil-pattern (4-line minimal wrapper). Windows users can now download aishell + aishell.bat, place both in PATH, and run `aishell <command>` natively without the `bb` prefix.

## Implementation Summary

**Task 1: Add .bat wrapper generation to build-release.clj**
- Added `bat-file` definition and `create-bat-wrapper` function
- Function generates 4-line wrapper with explicit CRLF line endings (`\r\n`)
- Wrapper uses `%~dp0` for script path resolution (Windows batch variable)
- Guarded `chmod +x` with `(when-not (fs/windows?))` for platform compatibility
- Updated build completion message to include wrapper path
- Updated header comment to document aishell.bat in Produces section

**Task 2: Include .bat wrapper in GitHub Release pipeline**
- Added `dist-bat` definition and existence check in create-release.clj
- Included aishell.bat as asset in `gh release create` command
- Added CI verification in release.yml to validate .bat artifact exists
- Updated prerequisites in create-release.clj header comment

## Verification Results

All success criteria met:

1. `bb scripts/build-release.clj` produces all artifacts:
   - dist/aishell (executable uberscript)
   - dist/aishell.bat (Windows wrapper)
   - dist/aishell.sha256 (checksum)

2. dist/aishell.bat has correct content:
   ```bat
   @echo off
   set ARGS=%*
   set SCRIPT=%~dp0aishell
   bb -f %SCRIPT% %ARGS%
   ```

3. CRLF line endings verified via `od -c` (shows `\r\n` at line ends)

4. create-release.clj has 5 references to dist-bat/aishell.bat (>= 3 required):
   - dist-bat definition
   - dist-bat existence check
   - dist-bat in gh release create
   - References in comments

5. release.yml includes .bat verification step

6. chmod +x guarded by `(when-not (fs/windows?))` for Windows compatibility

7. Existing build artifacts unchanged (dist/aishell, dist/aishell.sha256)

## Deviations from Plan

None - plan executed exactly as written.

## Technical Details

**Windows .bat wrapper pattern:**
- Uses neil-pattern (4-line minimal wrapper)
- CRLF line endings required for Windows CMD compatibility
- `%~dp0` resolves to directory containing the .bat file
- `bb -f %SCRIPT% %ARGS%` invokes Babashka with script file and arguments

**Platform compatibility:**
- `fs/windows?` detection for platform-specific logic
- chmod skipped on Windows (not available)
- CRLF endings ensure Windows CMD/PowerShell compatibility

**CI/CD integration:**
- GitHub workflow verifies .bat exists before release
- Release script checks all artifacts before gh release create
- .bat included as release asset alongside binary and checksum

## Impact

**User experience:**
- Windows users can now run `aishell <command>` directly in cmd.exe/PowerShell
- No need to prefix commands with `bb` on Windows
- Consistent experience across platforms (Unix: `aishell`, Windows: `aishell`)

**Distribution:**
- GitHub Releases now include aishell.bat as asset
- Windows installation: download aishell + aishell.bat, place both in PATH
- Unix installation unchanged: download aishell, place in PATH

**Build pipeline:**
- Cross-platform compatible (builds on Windows and Unix)
- Single build script produces all artifacts
- CI validates all artifacts before release

## Next Steps

This completes Phase 58 (bat-wrapper). The Windows .bat wrapper is now generated during build and distributed via GitHub Releases, completing the native Windows distribution story.

## Commits

| Task | Description | Commit | Files Modified |
|------|-------------|--------|----------------|
| 1 | Add .bat wrapper generation to build | a9cab30 | scripts/build-release.clj |
| 2 | Include .bat in release pipeline | 444d7b5 | scripts/create-release.clj, .github/workflows/release.yml |

## Self-Check

Verifying all claims in this summary:

**Files exist:**
- dist/aishell: ✓
- dist/aishell.bat: ✓
- dist/aishell.sha256: ✓

**Commits exist:**
- a9cab30: ✓
- 444d7b5: ✓

**Functionality verified:**
- .bat file has 4 lines: ✓
- CRLF line endings present: ✓
- Neil-pattern content correct: ✓
- create-release.clj includes dist-bat: ✓
- release.yml verifies .bat: ✓

## Self-Check: PASSED
