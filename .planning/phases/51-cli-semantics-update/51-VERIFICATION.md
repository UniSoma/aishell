---
phase: 51-cli-semantics-update
verified: 2026-02-06T14:16:25Z
status: passed
score: 7/7 must-haves verified
---

# Phase 51: CLI Semantics Update Verification Report

**Phase Goal:** Default container naming and detach flag removal
**Verified:** 2026-02-06T14:16:25Z
**Status:** passed
**Re-verification:** No — initial verification

## Goal Achievement

### Observable Truths

| # | Truth | Status | Evidence |
|---|-------|--------|----------|
| 1 | `aishell` (no args) creates container named `shell` | ✓ VERIFIED | run.clj:115 - name-part defaults to "shell" when cmd is nil |
| 2 | `aishell claude` creates container named `claude` | ✓ VERIFIED | run.clj:115 - name-part uses cmd value, cli.clj:531 passes "claude" |
| 3 | `aishell --name foo` creates container named `foo` running bash | ✓ VERIFIED | cli.clj:486-487 extracts --name for non-subcommands, cli.clj:542-544 dispatches to run-container when container-name-override set, run.clj:115 uses :container-name opts |
| 4 | `aishell claude --name bar` creates container named `bar` running Claude Code | ✓ VERIFIED | cli.clj:486-487 extracts --name before harness commands, cli.clj:531-532 passes container-name-override, run.clj:115 prioritizes :container-name over cmd |
| 5 | Creation commands error if named container already running | ✓ VERIFIED | run.clj:119 calls naming/ensure-name-available!, naming.clj:90-106 errors on running container |
| 6 | `--detach`/`-d` flag is rejected by CLI (no detached mode) | ✓ VERIFIED | grep detach in src/ returns 0 results, no extraction logic in cli.clj |
| 7 | Help text contains no references to --detach | ✓ VERIFIED | cli.clj:452 ps help mentions no --detach, cli.clj:524 attach help removed --detach references, attach.clj:36,40 error messages have no --detach |

**Score:** 7/7 truths verified

### Required Artifacts

| Artifact | Expected | Status | Details |
|----------|----------|--------|---------|
| `src/aishell/cli.clj` | CLI dispatch without detach extraction, --name extraction for all run modes | ✓ VERIFIED | Lines 486-487: known-subcommands guard excludes setup/update/check/exec/ps/volumes/attach, should-extract-name? true for shell mode. Line 542: dispatch wired to run-container when container-name-override set. No detach extraction present. |
| `src/aishell/run.clj` | Container execution with foreground-only mode | ✓ VERIFIED | Lines 239-243: Only foreground branch exists (window title + p/exec). No if (:detach opts) conditional. Gitleaks uses p/shell (lines 228-238) but for exit code capture, not detached mode. |
| `src/aishell/docker/run.clj` | Docker args without detach flag support | ✓ VERIFIED | Line 229: build-docker-args-internal destructuring has no detach key. No detach flag added to docker args. Line 320: build-docker-args caller passes no :detach. |
| `src/aishell/attach.clj` | Attach error messages without --detach references | ✓ VERIFIED | Lines 36,40: "To start: aishell {short-name}" without " --detach" suffix. grep returns 0 results for "detach" in file. |

### Key Link Verification

| From | To | Via | Status | Details |
|------|-----|-----|--------|---------|
| `cli.clj` | `run.clj` | run/run-container opts map no longer includes :detach key | ✓ WIRED | Lines 531-544: All 7 run/run-container calls pass opts maps with :unsafe, :container-name, :skip-pre-start only. No :detach key present. |
| `run.clj` | `docker/run.clj` | build-docker-args no longer receives :detach key | ✓ WIRED | Line 194-203: build-docker-args receives 10 keys (:project-dir, :image-tag, :config, :state, :git-identity, :skip-pre-start, :skip-interactive, :container-name, :harness-volume-name) - no :detach. |

### Requirements Coverage

| Requirement | Status | Blocking Issue |
|-------------|--------|----------------|
| CLI-01: `aishell` creates container named `shell` | ✓ SATISFIED | None - run.clj defaults name-part to "shell" |
| CLI-02: `aishell <harness>` creates container named `<harness>` | ✓ SATISFIED | None - run.clj uses cmd as name-part |
| CLI-03: `aishell --name X` creates container named `X` with bash | ✓ SATISFIED | None - cli.clj extracts --name for shell mode, dispatches to run-container |
| CLI-04: `aishell <harness> --name X` creates container named `X` with harness | ✓ SATISFIED | None - cli.clj extracts --name before harness, passes to run-container |
| CLI-05: Creation commands error if named container already running | ✓ SATISFIED | None - run.clj calls ensure-name-available! which errors on running |
| CLI-06: `--detach`/`-d` flag removed from CLI | ✓ SATISFIED | None - zero detach references in src/ |

### Anti-Patterns Found

None detected.

All source files load successfully via Babashka SCI:
- `bb -cp src -e "(require '[aishell.cli]) :ok"` → :ok
- `bb -cp src -e "(require '[aishell.run]) :ok"` → :ok
- `bb -cp src -e "(require '[aishell.docker.run]) :ok"` → :ok
- `bb -cp src -e "(require '[aishell.attach]) :ok"` → :ok

### Human Verification Required

None. All observable truths can be verified programmatically by inspecting code paths and data flow.

---

_Verified: 2026-02-06T14:16:25Z_
_Verifier: Claude (gsd-verifier)_
