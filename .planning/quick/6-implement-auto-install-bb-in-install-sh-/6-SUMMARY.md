---
phase: quick-6
plan: 01
subsystem: installer
tags: [installer, babashka, windows, powershell, automation]
dependency_graph:
  requires: []
  provides: [auto-install-babashka, windows-one-liner-install]
  affects: [install-sh, install-ps1, readme-quick-start]
tech_stack:
  added:
    - PowerShell installer (install.ps1)
  patterns:
    - Auto-detection and installation of missing dependencies
    - Platform-specific install flows (Unix/Windows)
    - Idempotent installation scripts
key_files:
  created:
    - install.ps1
  modified:
    - install.sh
    - README.md
decisions: []
metrics:
  duration: 141 seconds
  completed: 2026-02-12
---

# Quick Task 6: Auto-Install Babashka in install.sh

**One-liner:** Eliminated manual Babashka prerequisite - install.sh auto-installs bb, install.ps1 provides Windows one-command install.

## Implementation

### Task 1: Add auto-install Babashka to install.sh
- Created `install_babashka()` function that downloads and runs official Babashka installer
- Uses `--dir` flag to install to user-writable location (no sudo required)
- Moved download tool detection (curl/wget) before Babashka check
- Idempotent: skips installation if `bb` already on PATH
- Verifies installation and cleans up temp files at `/tmp/bb-install`

**Commit:** 68dd409

### Task 2: Create install.ps1 for Windows
- Created complete PowerShell installer providing one-command install experience
- Auto-installs Babashka via Scoop (if available) or direct GitHub download
- Downloads aishell + aishell.bat with SHA256 checksum verification
- Persistently updates user PATH environment variable
- Idempotent: skips bb if present, skips PATH if already configured
- Runnable via: `irm https://raw.githubusercontent.com/UniSoma/aishell/main/install.ps1 | iex`

**Commit:** 44eb6fe

### Task 3: Update README.md Quick Start and Prerequisites
- Simplified Windows Quick Start from 5 steps to 3 (one-liner install)
- Added auto-install notes to both Unix and Windows sections
- Updated Prerequisites to reflect Babashka is now auto-installed
- Kept manual Babashka install instructions for users who prefer it

**Commit:** 02cc99d

## Deviations from Plan

None - plan executed exactly as written.

## Verification

- `bash -n install.sh` passes (no syntax errors)
- install.sh contains `install_babashka` function with `--dir` flag usage
- install.ps1 exists (181 lines) with Babashka + aishell download + checksum + PATH logic
- README.md Windows Quick Start is now a one-liner (`irm ... | iex`)
- README.md Unix Quick Start unchanged except for auto-install note
- All scripts are idempotent (safe to run multiple times)

## Success Criteria Met

- ✅ install.sh auto-installs Babashka when missing, skips when present, no sudo needed
- ✅ install.ps1 provides complete Windows install in one command
- ✅ README.md Quick Start reflects the new simplified experience on both platforms
- ✅ All scripts are idempotent

## Impact

**User experience:**
- Unix/Linux/macOS users no longer need to manually install Babashka first
- Windows users get a one-command install instead of 5-step manual process
- Both platforms now have feature parity in install experience

**Technical:**
- install.sh detects missing bb and installs to `~/.local/bin` automatically
- install.ps1 handles Babashka (via Scoop or GitHub), aishell, PATH, all in one script
- Both installers are idempotent and can be safely re-run

## Self-Check: PASSED

Files created:
```
FOUND: /home/jonasrodrigues/projects/harness/install.ps1
```

Files modified:
```
FOUND: /home/jonasrodrigues/projects/harness/install.sh
FOUND: /home/jonasrodrigues/projects/harness/README.md
```

Commits exist:
```
FOUND: 68dd409
FOUND: 44eb6fe
FOUND: 02cc99d
```
