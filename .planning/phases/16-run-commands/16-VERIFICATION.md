---
phase: 16-run-commands
verified: 2026-01-21T00:54:02Z
status: passed
score: 9/9 must-haves verified
re_verification:
  previous_status: passed
  previous_score: 7/7
  gaps_closed:
    - "Pass-through args for claude command (--help, --version, etc) are forwarded to Claude Code"
    - "Pass-through args for opencode command (--help, --version, etc) are forwarded to OpenCode"
  gaps_remaining: []
  regressions: []
---

# Phase 16: Run Commands Verification Report

**Phase Goal:** Users can enter shell or run harnesses directly with full configuration support
**Verified:** 2026-01-21T00:54:02Z
**Status:** passed
**Re-verification:** Yes - after gap closure (plan 16-05)

## Goal Achievement

### Observable Truths

| # | Truth | Status | Evidence |
|---|-------|--------|----------|
| 1 | User can run `./aishell` and enter shell in container | VERIFIED | cli.clj:144 calls `run/run-container nil []` for no-command case; run.clj:80 sets container-cmd to `["/bin/bash"]` |
| 2 | User can run `./aishell claude` and Claude Code starts | VERIFIED | cli.clj:168 intercepts "claude" command before dispatch, calls `run/run-container "claude" (vec (rest args))`; run.clj:73-74 builds `["claude" "--dangerously-skip-permissions"]` |
| 3 | User can run `./aishell opencode` and OpenCode starts | VERIFIED | cli.clj:169 intercepts "opencode" command before dispatch, calls `run/run-container "opencode" (vec (rest args))`; run.clj:77 builds `["opencode"]` |
| 4 | Running without build shows clear error message | VERIFIED | run.clj:36 calls `output/error-no-build` when `state/read-state` returns nil; output.clj:87-92 prints "No image built. Run: aishell build" |
| 5 | Running claude without --with-claude build shows error | VERIFIED | run.clj:46 calls `verify-harness-available "claude" :with-claude state`; run.clj:15-20 errors with "Claude Code not installed. Run: aishell build --with-claude" |
| 6 | Extra args pass through to harness (claude) | VERIFIED | cli.clj:168 passes `(vec (rest args))` directly to run-container, bypassing cli/dispatch; run.clj:73-74 uses `(into ["claude" ...] harness-args)` |
| 7 | Extra args pass through to harness (opencode) | VERIFIED | cli.clj:169 passes `(vec (rest args))` directly to run-container, bypassing cli/dispatch; run.clj:77 uses `(into ["opencode"] harness-args)` |
| 8 | Container is destroyed on exit | VERIFIED | docker/run.clj:182 includes `"--rm"` flag in build-docker-args |
| 9 | Config supports both map and array env formats | VERIFIED | docker/run.clj:57-64 `parse-env-string` handles array format; docker/run.clj:82-86 branches on `(map? env)` for both formats |

**Score:** 9/9 truths verified

### Gap Closure Verification (Plan 16-05)

| Gap | Status | Evidence |
|-----|--------|----------|
| Pass-through args for claude | VERIFIED | cli.clj:167-169: Pre-dispatch interception `(case (first args) "claude" (run/run-container "claude" (vec (rest args)))` bypasses babashka.cli entirely |
| Pass-through args for opencode | VERIFIED | cli.clj:167-169: Same pattern for opencode, all args after command name forwarded verbatim |

**Key fix:** Plan 16-05 moved claude/opencode handling BEFORE `cli/dispatch`, so babashka.cli never parses `--help`, `--version`, or any other flags. All args go directly to the container.

### Required Artifacts

| Artifact | Expected | Status | Lines | Details |
|----------|----------|--------|-------|---------|
| `src/aishell/config.clj` | YAML config loading with validation | VERIFIED | 78 | exports load-config, validate-config, config-source |
| `src/aishell/docker/run.clj` | Docker run argument construction | VERIFIED | 233 | exports build-docker-args, read-git-identity; includes parse-env-string for array format |
| `src/aishell/run.clj` | Run command orchestration | VERIFIED | 83 | exports run-container, passes harness-args to container command |
| `src/aishell/cli.clj` | CLI dispatch for shell/claude/opencode | VERIFIED | 172 | dispatch function at lines 164-172 with pre-dispatch interception for pass-through commands |
| `src/aishell/state.clj` | State persistence | VERIFIED | 35 | exports read-state, write-state |
| `src/aishell/output.clj` | Error/warning output | VERIFIED | 92 | exports error, warn, error-no-build, error-unknown-command |

### Key Link Verification

| From | To | Via | Status | Evidence |
|------|-----|-----|--------|---------|
| cli.clj | run.clj | run/run-container | WIRED | cli.clj:8 requires run, lines 144,168,169 call run/run-container |
| run.clj | config.clj | config/load-config | WIRED | run.clj:7 requires config, line 52 calls config/load-config |
| run.clj | docker/run.clj | docker-run/build-docker-args | WIRED | run.clj:6 requires docker.run, line 64 calls docker-run/build-docker-args |
| run.clj | state.clj | state/read-state | WIRED | run.clj:8 requires state, line 33 calls state/read-state |
| run.clj | docker.clj | docker/check-docker! | WIRED | run.clj:5 requires docker, line 30 calls docker/check-docker! |
| config.clj | clj-yaml.core | yaml/parse-string | WIRED | config.clj:4 requires clj-yaml, lines 52,61 call yaml/parse-string |
| docker/run.clj | babashka.process | p/shell, p/exec | WIRED | docker/run.clj:4 requires process, lines 17,27,30 use p/shell |

### Pass-through Args Implementation Detail

**Before Plan 16-05 (broken):**
```clojure
;; dispatch-table had entries for claude/opencode with :spec containing :help
;; babashka.cli parsed --help before handle-run saw it
{:cmds ["claude"] :fn #(handle-run % "claude") :spec {:help {:alias :h ...}} :restrict false}
```

**After Plan 16-05 (fixed):**
```clojure
(defn dispatch [args]
  (case (first args)
    "claude" (run/run-container "claude" (vec (rest args)))   ;; <-- bypasses cli/dispatch
    "opencode" (run/run-container "opencode" (vec (rest args)))
    (cli/dispatch dispatch-table args {:error-fn handle-error :restrict true})))
```

The fix intercepts claude/opencode BEFORE `cli/dispatch`, so:
- `./aishell claude --help` passes `["--help"]` to Claude inside container
- `./aishell claude --version` passes `["--version"]` to Claude inside container
- `./aishell claude -p "query"` passes `["-p" "query"]` to Claude inside container

### Env Config Dual Format Verification

| Format | Example | Status | Evidence |
|--------|---------|--------|----------|
| Map with literal | `{FOO: bar}` | VERIFIED | docker/run.clj:84: `(map (fn [[k v]] [(name k) v]) env)` |
| Map with passthrough | `{FOO:}` | VERIFIED | docker/run.clj:90-96: nil value triggers `System/getenv` lookup |
| Array with literal | `[FOO=bar]` | VERIFIED | docker/run.clj:86: `(map parse-env-string env)` splits on "=" at line 62 |
| Array with passthrough | `[FOO]` | VERIFIED | docker/run.clj:63-64: no "=" returns `[s nil]` for passthrough |

### Requirements Coverage

| Requirement | Status | Notes |
|-------------|--------|-------|
| CLI-04 (shell command) | SATISFIED | `./aishell` runs interactive shell |
| CLI-05 (claude command) | SATISFIED | `./aishell claude [args]` runs Claude with full pass-through |
| CLI-06 (opencode command) | SATISFIED | `./aishell opencode [args]` runs OpenCode with full pass-through |
| DOCK-04 (project mount) | SATISFIED | Same-path mount at docker/run.clj:184-185 |
| DOCK-05 (git identity) | SATISFIED | GIT_* env vars from read-git-identity at docker/run.clj:195-200 |
| DOCK-06 (ephemeral container) | SATISFIED | --rm flag at docker/run.clj:182 |
| CONF-01 (YAML config) | SATISFIED | clj-yaml parsing in config.clj |
| CONF-02 (project config path) | SATISFIED | .aishell/config.yaml at config.clj:14-16 |
| CONF-03 (global fallback) | SATISFIED | ~/.aishell/config.yaml at config.clj:18-21 |
| CONF-04 (mounts) | SATISFIED | build-mount-args at docker/run.clj:32-55 |
| CONF-05 (env) | SATISFIED | build-env-args with dual format at docker/run.clj:66-98 |
| CONF-06 (pre_start) | SATISFIED | PRE_START env var at docker/run.clj:225-226 |
| PLAT-01 (UID/GID mapping) | SATISFIED | LOCAL_UID, LOCAL_GID at docker/run.clj:187-188 |
| PLAT-02 (home directory) | SATISFIED | LOCAL_HOME at docker/run.clj:189 |

### Anti-Patterns Scan

| File | Line | Pattern | Severity | Impact |
|------|------|---------|----------|--------|
| None | - | - | - | - |

**Scan results:**
- TODO/FIXME/XXX/HACK: 0 found
- Placeholder text: 0 found
- Empty implementations (return null/{}): 0 found
- Console.log only implementations: N/A (Clojure)

All files are substantive implementations with no stub patterns.

### Human Verification Required

#### 1. Container Shell Entry
**Test:** Run `./aishell` in a project with Docker available
**Expected:** Container starts, shell prompt appears, `pwd` shows same path as host
**Why human:** Requires Docker runtime

#### 2. Claude Code Launch with Pass-through Args
**Test:** Build with `--with-claude`, then run `./aishell claude --help`
**Expected:** Claude Code's help output appears (not aishell's help)
**Why human:** Requires Docker runtime and Claude installation

#### 3. Claude Code Version Check
**Test:** Run `./aishell claude --version`
**Expected:** Claude Code shows its version number
**Why human:** Requires Docker runtime and Claude installation

#### 4. OpenCode Launch with Pass-through Args
**Test:** Build with `--with-opencode`, then run `./aishell opencode --help`
**Expected:** OpenCode's help output appears (not aishell's help)
**Why human:** Requires Docker runtime and OpenCode installation

#### 5. Config env Array Format
**Test:** Create `.aishell/config.yaml` with `env: [FOO=bar]`, run `./aishell`, check `echo $FOO`
**Expected:** FOO=bar inside container
**Why human:** Requires Docker runtime

#### 6. Config env Map Format
**Test:** Create `.aishell/config.yaml` with `env: {FOO: bar}`, run `./aishell`, check `echo $FOO`
**Expected:** FOO=bar inside container
**Why human:** Requires Docker runtime

### Summary

Phase 16 goal "Users can enter shell or run harnesses directly with full configuration support" is **ACHIEVED**.

**Re-verification notes:**
- Previous verification (2026-01-20T22:55:00Z) passed 7/7 original truths
- UAT (16-UAT.md) revealed 2 issues with pass-through args (tests 9, 10)
- Plan 16-05 fixed by pre-dispatch command interception
- This verification confirms fix is in code at cli.clj:164-172
- All 9 truths verified (7 original + 2 gap closures)
- No regressions in original functionality

**Code quality:**
- 693 total lines across 6 core files
- No stub patterns detected
- All key links verified wired
- Clean separation: config loading, docker args, run orchestration, CLI dispatch

Human verification items are for runtime behavior requiring Docker availability.

---

*Verified: 2026-01-21T00:54:02Z*
*Verifier: Claude (gsd-verifier)*
*Re-verification: After plan 16-05 gap closure*
