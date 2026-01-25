---
phase: 28-dynamic-help-config-improvements
plan: 02
subsystem: cli
tags: [help, documentation, config]
requires:
  - phase: 28-01
    provides: "State tracking infrastructure for installed harnesses"
provides:
  - Dynamic help output based on build state
  - Comprehensive CONFIGURATION.md for v2.5.0 features
affects: [29-binary-claude-code-install]
tech-stack:
  added: []
  patterns:
    - state-based-ui
    - discoverability-pattern
decisions:
  - id: help-discoverability
    choice: "Show all harnesses when no state file exists"
    rationale: "Aids discoverability for new users who haven't built yet"
  - id: gitleaks-always-shown
    choice: "Always show gitleaks command in help regardless of build state"
    rationale: "Gitleaks may be installed on host, and command works via host mounting"
key-files:
  created: []
  modified:
    - src/aishell/cli.clj
    - docs/CONFIGURATION.md
metrics:
  duration: 2min
  completed: 2026-01-25
---

# Phase 28 Plan 02: Dynamic Help & Documentation Summary

**One-liner:** Context-aware help output showing only installed harnesses, plus comprehensive documentation for pre_start lists and Gitleaks opt-out

## What Was Built

Completed Phase 28 with two deliverables:

1. **Dynamic help output** - `aishell --help` now shows only installed harnesses based on state
2. **Comprehensive documentation** - CONFIGURATION.md updated to v2.5.0 with all new features

## Implementation Details

### Dynamic Help Output

**installed-harnesses function** (cli.clj):
- Reads `state/read-state` to determine which harnesses are installed
- Returns set of installed harness names based on boolean flags
- No state file = returns all harnesses for discoverability
- Enables new users to see what options exist before building

**print-help function** (cli.clj):
- Uses `installed-harnesses` to conditionally show harness commands
- Wraps harness println statements in `when (contains? installed "name")`
- Gitleaks always shown (may be installed on host, command works via mounting)
- build/update/(none) always shown regardless of state

**Behavior examples:**
```bash
# No state file (no build yet)
$ aishell --help
Commands:
  build, update, claude, opencode, codex, gemini, gitleaks, (none)

# State: {:with-claude true :with-codex true}
$ aishell --help
Commands:
  build, update, claude, codex, gitleaks, (none)

# State: all false (base image only)
$ aishell --help
Commands:
  build, update, gitleaks, (none)
```

### Documentation Updates

**Version update:**
- Changed from v2.4.0 to v2.5.0 throughout

**pre_start section enhancements:**
- Added "Type: String or List (v2.5+)" header
- Documented both string and list formats with examples
- Showed list-to-string transformation with `&&` separator
- Updated common use cases table to show list format examples
- Clarified behavior: empty lists filtered, && means sequential failure stops

**Build Options section (NEW):**
- Added new top-level section for build-time flags
- Documented `--without-gitleaks` flag comprehensively
- Explained state tracking in `~/.aishell/state.edn`
- Showed image size impact (~15MB savings)
- Provided use case guidance (minimal images, external Gitleaks, CI/CD)
- Noted that `aishell gitleaks` may still work via host installation

**Merge strategy table update:**
- Updated example to show pre_start accepting list format
- Result shows `"echo && bye"` transformation

**Example updates:**
- Multi-Service Development Environment now uses list format for pre_start

## Verification Results

**Dynamic help verified:**
- No state file → shows all harnesses (claude, opencode, codex, gemini, gitleaks)
- State with only claude → shows claude and gitleaks only
- State with claude+codex → shows claude, codex, and gitleaks
- State with no harnesses → shows gitleaks only
- Reading state.edn is fast (<1ms) - no performance concern

**Documentation verified:**
- v2.5.0 version appears in header
- pre_start section documents list format comprehensively
- Build Options section with --without-gitleaks exists
- All markdown properly formatted
- Examples updated to use new list format

## Technical Decisions

### Decision: Discoverability pattern

**Context:** New users haven't built yet, so no state file exists. Should help be empty or show options?

**Options considered:**
- Show nothing (user must read docs first)
- Show all harnesses (chosen) to aid discoverability
- Show error message prompting build

**Choice:** Show all harnesses when no state file exists

**Rationale:**
- Aids discoverability - users see what harnesses are available
- Helps users make informed build decisions
- Minimal cost (<1ms state read on each --help)
- Matches expectation: new users expect to see available commands

### Decision: Always show gitleaks

**Context:** Gitleaks installation is optional via `--without-gitleaks`, but command may still work.

**Options considered:**
- Show only when `:with-gitleaks true` in state
- Always show (chosen) regardless of build state
- Show with disclaimer when not built

**Choice:** Always show `gitleaks` command in help

**Rationale:**
- Gitleaks may be installed on host system
- Command works via host mounting even if not in container
- Reduces user confusion about "missing" command
- Consistent with other tools that check host PATH

## Deviations from Plan

None - plan executed exactly as written.

## Files Modified

| File | Changes | Purpose |
|------|---------|---------|
| src/aishell/cli.clj | +24, -4 | Add installed-harnesses function, update print-help |
| docs/CONFIGURATION.md | +75, -16 | Update to v2.5.0, document pre_start list and --without-gitleaks |

## Commits

| Task | Hash | Message |
|------|------|---------|
| Task 1 | d56fcaa | feat(28-02): implement dynamic help output based on build state |
| Task 2 | 2869d2b | docs(28-02): update CONFIGURATION.md for v2.5.0 features |

## Phase 28 Complete

**Phase 28 deliverables:**
- ✅ Pre-start list format support (28-01)
- ✅ Conditional Gitleaks installation (28-01)
- ✅ Dynamic help based on installed harnesses (28-02)
- ✅ Comprehensive documentation for v2.5.0 (28-02)

**Phase 28 value:**
Users now have:
- Cleaner multi-command pre-start configuration (list format)
- Smaller images when Gitleaks not needed (~15MB savings)
- Context-aware help showing only relevant commands
- Complete documentation for all new features

## Next Phase Readiness

**Ready for:** Phase 29 (Binary Claude Code installation with conditional Node.js)

**Blockers:** None

**Handoff notes:**
- Dynamic help pattern established - can be extended for other conditional features
- State tracking infrastructure proven reliable for UI decisions
- Documentation template established for new build flags

---
*Phase: 28-dynamic-help-config-improvements*
*Completed: 2026-01-25*
