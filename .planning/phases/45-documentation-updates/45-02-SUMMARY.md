---
phase: 45
plan: 02
subsystem: documentation
tags: [docs, gitleaks, opt-in, troubleshooting, architecture]
dependencies:
  requires: [44-02]
  provides: [supporting-docs-opt-in-gitleaks]
  affects: [user-facing-docs]
tech-stack:
  added: []
  patterns: []
key-files:
  created: []
  modified:
    - docs/TROUBLESHOOTING.md
    - docs/ARCHITECTURE.md
decisions: []
metrics:
  duration: 1.25min
  completed: 2026-02-05
---

# Phase 45 Plan 02: Update Supporting Documentation Summary

**One-liner:** Updated TROUBLESHOOTING.md and ARCHITECTURE.md to reflect Gitleaks opt-in default with --with-gitleaks flag

## What Was Done

Updated supporting documentation files to align with Phase 44's Gitleaks opt-in default change.

### Changes Made

**1. docs/TROUBLESHOOTING.md**
- Added new troubleshooting section for "gitleaks command not found" pointing users to --with-gitleaks
- Added context note to existing "Gitleaks scan takes too long" section noting it applies only when Gitleaks is installed
- Provides clear resolution path for users expecting Gitleaks by default

**2. docs/ARCHITECTURE.md**
- Updated design principles to describe Gitleaks as opt-in content scanning
- Changed foundation image contents from --without-gitleaks to --with-gitleaks
- Updated build diagram to show conditional Gitleaks installation
- Changed state.edn example to show :with-gitleaks false as default
- Added opt-in context to Gitleaks layer description with build flag requirement
- Updated FAQ to note Gitleaks applies when installed

### Task Commits

| Task | Description | Commit | Files |
|------|-------------|--------|-------|
| 1 | Update TROUBLESHOOTING.md with opt-in Gitleaks context | 08b8bb3 | docs/TROUBLESHOOTING.md |
| 2 | Update ARCHITECTURE.md with opt-in Gitleaks semantics | e04ad43 | docs/ARCHITECTURE.md |

## Verification

All verification criteria met:
- ✅ No references to --without-gitleaks remain in either file
- ✅ Both files contain --with-gitleaks references at updated locations
- ✅ ARCHITECTURE.md contains 6 opt-in references
- ✅ TROUBLESHOOTING.md covers "command not found" issue with resolution
- ✅ No Markdown formatting issues introduced

## Deviations from Plan

None - plan executed exactly as written.

## Decisions Made

No architectural or implementation decisions required for this documentation update.

## Next Phase Readiness

**Status:** Complete ✓

**Blockers:** None

**Concerns:** None

**Recommendations:**
- Consider reviewing user-facing documentation consistency across all docs
- Verify --with-gitleaks messaging is consistent in README.md (already updated in 45-01)

## Notes

This plan completed the documentation updates for Phase 44's Gitleaks opt-in change. Supporting documentation now aligns with primary documentation (README, CONFIGURATION).

## Self-Check: PASSED
