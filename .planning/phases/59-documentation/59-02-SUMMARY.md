---
phase: 59-documentation
plan: 02
subsystem: documentation
tags: [windows, troubleshooting, development, cross-platform, testing]

dependency_graph:
  requires:
    - phase: 53
      plan: 01
      provides: "fs/windows? platform detection"
    - phase: 54
      plan: 01
      provides: "Windows state directory (LOCALAPPDATA)"
    - phase: 54
      plan: 02
      provides: "Path normalization with fs/unixify"
    - phase: 56
      plan: 01
      provides: "p/process on Windows, p/exec on Unix"
    - phase: 57
      plan: 01
      provides: "ANSI color detection with NO_COLOR/FORCE_COLOR"
    - phase: 58
      plan: 01
      provides: ".bat wrapper for Windows"
  provides:
    - "Windows-specific troubleshooting documentation"
    - "Windows development and testing guide"
  affects:
    - subsystem: documentation
      impact: "Complete Windows support documentation"

tech_stack:
  added: []
  patterns:
    - "Side-by-side platform examples (Unix/Windows)"
    - "Symptom-based troubleshooting entries"
    - "Platform-specific test scenarios"
    - "Cross-platform development patterns documentation"

key_files:
  created: []
  modified:
    - path: docs/TROUBLESHOOTING.md
      changes: "Added Windows-Specific Issues section with 5 symptom entries, Windows PowerShell Quick Diagnostics, Docker Desktop installation reference"
    - path: docs/DEVELOPMENT.md
      changes: "Added Testing on Windows section with 5 platform-specific test scenarios, cross-platform development patterns, updated prerequisites"

decisions:
  - decision: "Inline platform notes rather than separate Windows guide"
    rationale: "Keeps related information together, reduces doc sprawl, easier to maintain, users find solutions faster"
  - decision: "Symptom-based troubleshooting organization"
    rationale: "Users arrive with symptoms, not diagnoses - symptom-first reduces time-to-resolution"
  - decision: "Five platform-specific test scenarios for contributors"
    rationale: "Covers all Windows-specific behaviors from Phases 53-58 - path handling, state directory, process execution, ANSI colors, .bat wrapper"

metrics:
  duration_seconds: 152
  tasks_completed: 2
  files_modified: 2
  lines_added: 411
  commits: 2
  completed_at: "2026-02-12T03:55:38Z"
---

# Phase 59 Plan 02: Windows Troubleshooting and Development Docs Summary

**One-liner:** Added Windows-specific troubleshooting entries (WSL2, path mounting, .bat wrapper, color output, permissions) and Windows testing guide with platform-specific test scenarios for contributors.

## What Was Done

### Task 1: Windows-Specific Troubleshooting Section (Commit af4c928)

Updated TROUBLESHOOTING.md to v3.1.0 with Windows support:

1. **Added Windows-Specific Issues section** with 5 symptom-based troubleshooting entries:
   - "Docker daemon not running" on Windows (WSL2 backend enablement)
   - "No such file or directory" when mounting Windows path (forward slash vs backslash)
   - "aishell.bat not recognized" in cmd.exe (PATH and CRLF line endings)
   - Colors not displaying correctly in PowerShell/cmd.exe (ANSI support, NO_COLOR/FORCE_COLOR)
   - Permission errors when building custom Dockerfile on Windows (WSL2 filesystem, Docker Desktop file sharing)

2. **Updated Quick Diagnostics section** with Windows PowerShell equivalent commands alongside existing bash commands.

3. **Updated "Docker not found" entry** in Setup Issues section with Windows-specific Docker Desktop installation reference.

4. **Updated Table of Contents** to include Windows-Specific Issues section.

**Files modified:** docs/TROUBLESHOOTING.md (+197 lines)

### Task 2: Windows Development and Testing Section (Commit e53c851)

Updated DEVELOPMENT.md to v3.1.0 with Windows development guidance:

1. **Updated Prerequisites section** - Added "Windows 10/11" alongside Linux/macOS, specified Docker Desktop with WSL2 backend, noted Babashka installation via Scoop.

2. **Updated "Clone and Run from Source" section** - Added platform note that `bb -m aishell.core` works identically on all platforms.

3. **Added "Testing on Windows" section** with comprehensive Windows testing guide:

   a. **Prerequisites for Windows Development** - Windows 10/11 with WSL2, Docker Desktop with WSL2 backend, Babashka, PowerShell 7+ recommended.

   b. **Platform-Specific Test Scenarios** - Five critical test scenarios for Windows support:
      - **Path Handling:** Windows path normalization in Docker commands (fs/unixify)
      - **State Directory:** Verify LOCALAPPDATA usage on Windows vs ~/.local/state on Unix
      - **Process Execution:** Test attach uses p/process on Windows (not p/exec)
      - **ANSI Color Output:** Test color detection with NO_COLOR/FORCE_COLOR overrides
      - **Batch Wrapper Generation:** Verify .bat wrapper has 4 lines and CRLF endings

   c. **Cross-Platform Development Patterns** for contributors:
      - Use `babashka.fs/windows?` for platform detection
      - Use `fs/path` for path construction (not string concatenation)
      - Use `fs/unixify` at Docker mount boundaries
      - Guard `p/exec` calls with platform check, use `p/process {:inherit true}` on Windows
      - Guard `chmod` calls with `(when-not (fs/windows?) ...)`
      - Reference ARCHITECTURE.md for full patterns

   d. **Testing Without Windows Machine** - Limitations of simulating Windows on Unix/macOS, recommendation for real Windows testing for significant changes.

4. **Updated Table of Contents** to include "Testing on Windows" entry.

**Files modified:** docs/DEVELOPMENT.md (+214 lines)

## Deviations from Plan

None - plan executed exactly as written.

## Verification Results

### Overall Phase Checks

- ✅ TROUBLESHOOTING.md has Windows-Specific Issues section with symptom-based entries
- ✅ DEVELOPMENT.md has Testing on Windows section with test scenarios
- ✅ Both files updated to v3.1.0
- ✅ No existing content removed or broken
- ✅ Markdown formatting valid
- ✅ Table of Contents updated in both files

### Task 1 Verification (TROUBLESHOOTING.md)

- ✅ `grep -n "Windows-Specific Issues"` returns section heading
- ✅ `grep -n "Docker Desktop"` returns 10 matches
- ✅ `grep -n "aishell.bat"` returns 7 matches
- ✅ `grep -n "PowerShell"` returns 4 matches
- ✅ `grep -n "v3.1.0"` returns match on "Last updated"
- ✅ `grep -c "### Symptom:"` returns 31 (26 existing + 5 new Windows entries)
- ✅ Existing troubleshooting entries preserved and unchanged

### Task 2 Verification (DEVELOPMENT.md)

- ✅ `grep -n "v3.1.0"` returns match on "Last updated"
- ✅ `grep -n "Testing on Windows"` returns section heading
- ✅ `grep -n "Windows 10"` returns 2 matches
- ✅ `grep -n "fs/windows?"` returns 4 matches
- ✅ `grep -n "fs/unixify"` returns 3 matches
- ✅ `grep -n "p/process"` returns 6 matches
- ✅ Existing development content (Adding a New Harness, Testing Locally, etc.) preserved

## Self-Check: PASSED

### Files Created/Modified Verification

**Modified files:**
- ✅ docs/TROUBLESHOOTING.md exists and contains Windows-Specific Issues section
- ✅ docs/DEVELOPMENT.md exists and contains Testing on Windows section

**Commands executed:**
```bash
[ -f "docs/TROUBLESHOOTING.md" ] && echo "FOUND: docs/TROUBLESHOOTING.md" || echo "MISSING: docs/TROUBLESHOOTING.md"
# Output: FOUND: docs/TROUBLESHOOTING.md

[ -f "docs/DEVELOPMENT.md" ] && echo "FOUND: docs/DEVELOPMENT.md" || echo "MISSING: docs/DEVELOPMENT.md"
# Output: FOUND: docs/DEVELOPMENT.md
```

### Commits Verification

**Commits created:**
- ✅ af4c928: docs(59-02): add Windows-specific troubleshooting section
- ✅ e53c851: docs(59-02): add Windows development and testing section

**Commands executed:**
```bash
git log --oneline --all | grep -q "af4c928" && echo "FOUND: af4c928" || echo "MISSING: af4c928"
# Output: FOUND: af4c928

git log --oneline --all | grep -q "e53c851" && echo "FOUND: e53c851" || echo "MISSING: e53c851"
# Output: FOUND: e53c851
```

## Technical Notes

### Documentation Patterns Applied

1. **Side-by-Side Platform Examples:** Unix and Windows examples shown together for Quick Diagnostics section, making it easy for users to find platform-specific commands without context switching.

2. **Symptom-First Troubleshooting:** All 5 Windows entries follow "Symptom: [what user sees]" format with Cause and step-by-step Resolution. Users arrive with symptoms, not diagnoses.

3. **Platform-Specific Test Scenarios:** Five scenarios cover all Windows-specific behaviors from Phases 53-58 (path handling, state directory, process execution, ANSI colors, .bat wrapper). Provides concrete test steps for contributors.

4. **Cross-Platform Development Patterns:** Documents established patterns (fs/windows?, fs/unixify, p/process) with code examples showing correct vs incorrect approaches. Reduces reimplementation and platform-specific bugs.

### Content Coverage

**TROUBLESHOOTING.md Windows entries cover:**
- Docker Desktop WSL2 backend setup and verification
- Windows path format (forward slashes vs backslashes, tilde expansion)
- .bat wrapper PATH and CRLF line ending requirements
- Terminal color support (Windows Terminal, PowerShell, cmd.exe differences)
- Docker Desktop file sharing and WSL2 filesystem recommendations

**DEVELOPMENT.md Testing on Windows covers:**
- Prerequisites (Windows 10/11, WSL2, Docker Desktop, Babashka, PowerShell 7+)
- Five platform-specific test scenarios with concrete commands
- Cross-platform development patterns for contributors (5 key patterns)
- Testing limitations without Windows machine (what can/cannot be simulated)

### Integration with Existing Documentation

- **TROUBLESHOOTING.md:** New Windows entries follow existing symptom-based format exactly. Quick Diagnostics section now has both Unix and Windows blocks side-by-side. Table of Contents updated.

- **DEVELOPMENT.md:** New Testing on Windows section placed after Testing Locally section. Prerequisites section updated to include Windows. Cross-platform patterns reference existing ARCHITECTURE.md. Table of Contents updated.

## Completion Status

- ✅ Task 1: Add Windows-specific troubleshooting section to TROUBLESHOOTING.md
- ✅ Task 2: Add Windows development and testing section to DEVELOPMENT.md
- ✅ Overall verification passed
- ✅ Self-check passed
- ✅ SUMMARY.md created

**Total duration:** 152 seconds (~2.5 minutes)
**Commits:** 2
**Files modified:** 2
**Lines added:** 411

Phase 59 Plan 02 complete. Documentation now fully reflects Windows support added in Phases 53-58.
