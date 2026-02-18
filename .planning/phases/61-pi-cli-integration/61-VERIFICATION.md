---
phase: 61-pi-cli-integration
verified: 2026-02-18T02:39:22Z
status: passed
score: 6/6 must-haves verified
re_verification: false
---

# Phase 61: Pi CLI Integration Verification Report

**Phase Goal:** Users can run pi coding agent through aishell with the same UX as Claude/Codex/Gemini
**Verified:** 2026-02-18T02:39:22Z
**Status:** PASSED
**Re-verification:** No — initial verification

## Goal Achievement

### Observable Truths

| #   | Truth | Status | Evidence |
| --- | ----- | ------ | -------- |
| 1   | `aishell pi` launches pi coding agent inside the container | VERIFIED | `cli.clj:622` dispatches `"pi"` to `run/run-container "pi"`; `run.clj:140` calls `verify-harness-available "pi" :with-pi`; `run.clj:223-224` builds `(into ["pi"] merged-args)` container cmd |
| 2   | `aishell pi --print "hello"` passes arguments through to pi correctly | VERIFIED | `cli.clj:622` passes `(vec (rest clean-args))` as harness-args; `run.clj:154-155` merges defaults and CLI args; `run.clj:224` passes `merged-args` into container cmd |
| 3   | Pi config directory (`~/.pi/`) is mounted from host into the container | VERIFIED | `docker/run.clj:222` includes `[".pi"]` in `config-entries` list inside `build-harness-config-mounts`; mount is filtered to only apply when directory exists on host |
| 4   | `aishell check` shows pi installation status and version | VERIFIED | `check.clj:88` includes `["Pi" :with-pi :pi-version]` in the `harnesses` vector passed to `check-harnesses`; renders installed/not-installed status with version |
| 5   | `aishell --help` lists the `pi` command when pi is installed, and hides it when not installed | VERIFIED | `cli.clj:93` adds `"pi"` to `installed-harnesses` set when `:with-pi` state is set; `cli.clj:123-124` conditionally prints pi help entry when `(contains? installed "pi")` |
| 6   | Pi alias is available inside the container shell when pi is installed with harness_args configured | VERIFIED | `docker/run.clj:193` includes `["pi" :with-pi false]` in `build-harness-alias-env-args`; generates `HARNESS_ALIAS_PI=pi <args>` env var; `templates.clj:173` for-loop includes `HARNESS_ALIAS_PI`; entrypoint creates bash alias from env var |

**Score:** 6/6 truths verified

### Required Artifacts

| Artifact | Expected | Status | Details |
| -------- | -------- | ------ | ------- |
| `src/aishell/docker/templates.clj` | Entrypoint alias loop includes HARNESS_ALIAS_PI | VERIFIED | Line 173: `for var in HARNESS_ALIAS_CLAUDE HARNESS_ALIAS_OPENCODE HARNESS_ALIAS_CODEX HARNESS_ALIAS_GEMINI HARNESS_ALIAS_PI; do` — `bb` load confirmed OK |

### Key Link Verification

| From | To | Via | Status | Details |
| ---- | -- | --- | ------ | ------- |
| `docker/run.clj:build-harness-alias-env-args` | `templates.clj:entrypoint-script` | HARNESS_ALIAS_PI env var passed to container; entrypoint for-loop iterates and creates bash alias | WIRED | `docker/run.clj:193` includes `"pi"` in known harnesses; dynamically constructs `HARNESS_ALIAS_PI` string at line 202 via `(str "HARNESS_ALIAS_" (str/upper-case name))`; `templates.clj:173` for-loop iterates over `HARNESS_ALIAS_PI` |

### Requirements Coverage

| Requirement | Source Plan | Description | Status | Evidence |
| ----------- | ----------- | ----------- | ------ | -------- |
| HARNESS-02 | 61-01-PLAN.md | User can run `aishell pi` to launch pi coding agent in container | SATISFIED | `cli.clj:622` dispatches `"pi"` to `run/run-container`; `run.clj:140` verifies availability; `run.clj:223-224` builds container command |
| HARNESS-04 | 61-01-PLAN.md | Pi config directory (`~/.pi/`) mounted from host to persist auth and settings | SATISFIED | `docker/run.clj:222` `[".pi"]` in config-entries; mounted conditionally when host dir exists |
| HARNESS-06 | 61-01-PLAN.md | `aishell check` displays pi installation status and version | SATISFIED | `check.clj:88` `["Pi" :with-pi :pi-version]` in harnesses vector |
| HARNESS-07 | 61-01-PLAN.md | Pi shown in `aishell --help` when installed | SATISFIED | `cli.clj:93` adds `"pi"` to installed set; `cli.clj:123-124` conditional help print |
| HARNESS-08 | 61-01-PLAN.md | Pi pass-through args work (e.g., `aishell pi --print "hello"`) | SATISFIED | `cli.clj:622` passes `(vec (rest clean-args))`; `run.clj:154-155` merges and forwards as `merged-args` |
| HARNESS-09 | 61-01-PLAN.md | Entrypoint alias `pi` available inside container shell | SATISFIED | `templates.clj:173` for-loop includes `HARNESS_ALIAS_PI`; alias created when env var is set |

**Orphaned requirements check:** REQUIREMENTS.md maps HARNESS-02, HARNESS-04, HARNESS-06, HARNESS-07, HARNESS-08, HARNESS-09 to Phase 61 — all 6 are claimed by 61-01-PLAN.md. No orphaned requirements.

**Note on scope:** HARNESS-01 (build with `--with-pi`), HARNESS-03 (pin version), HARNESS-05 (npm install in volume) are mapped to Phase 60 in REQUIREMENTS.md — correctly not claimed by Phase 61 plans.

### Anti-Patterns Found

None. No TODO/FIXME/placeholder comments found in the modified file (`src/aishell/docker/templates.clj`).

### Human Verification Required

#### 1. Pi Alias in Live Container Shell

**Test:** Run `aishell setup --with-pi` to build with pi enabled, then `aishell pi` to enter container. In the shell, run `type pi` or `alias | grep pi`.
**Expected:** `pi` alias exists pointing to `pi <harness_args>` (if configured) or just `pi`.
**Why human:** The alias creation runs at container startup via entrypoint.sh — can't verify the runtime bash environment programmatically.

#### 2. Pi Args Pass-Through End-to-End

**Test:** Run `aishell pi --print "hello"` with pi installed.
**Expected:** Pi coding agent receives `--print hello` as arguments and outputs accordingly.
**Why human:** Requires a live container with pi installed to observe actual argument forwarding behavior.

#### 3. `aishell check` Pi Version Display

**Test:** Run `aishell check` after `aishell setup --with-pi`.
**Expected:** Check output shows "Pi installed (vX.Y.Z)" with the actual installed version.
**Why human:** Version display requires a live state file with `:pi-version` populated from a real setup run.

---

## Summary

Phase 61 adds `HARNESS_ALIAS_PI` to the entrypoint for-loop in `templates.clj` (line 173), which was the single missing piece after Phase 60 wired pi across the entire CLI stack. All 6 success criteria from the ROADMAP are verified:

- **Dispatch:** `aishell pi` routes through cli.clj -> run.clj -> docker/run.clj identically to other harnesses
- **Args pass-through:** CLI args forwarded via `merged-args` into `(into ["pi"] merged-args)` container cmd
- **Config mount:** `~/.pi/` in `build-harness-config-mounts` config-entries list
- **Check display:** `["Pi" :with-pi :pi-version]` entry in `check-harnesses` harnesses vector
- **Help listing:** Conditional print in `print-help` gated on `(contains? installed "pi")`
- **Container alias:** `HARNESS_ALIAS_PI` in entrypoint for-loop creates `pi` bash alias when env var is set

Commit `378a072` is verified in git history with the correct single-file change to `templates.clj`. `bb` confirms templates.clj loads without syntax errors.

---

_Verified: 2026-02-18T02:39:22Z_
_Verifier: Claude (gsd-verifier)_
