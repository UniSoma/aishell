---
phase: 16-run-commands
verified: 2026-01-20T21:50:00Z
status: passed
score: 7/7 must-haves verified
---

# Phase 16: Run Commands Verification Report

**Phase Goal:** Users can enter shell or run harnesses directly with full configuration support
**Verified:** 2026-01-20T21:50:00Z
**Status:** passed
**Re-verification:** No - initial verification

## Goal Achievement

### Observable Truths

| # | Truth | Status | Evidence |
|---|-------|--------|----------|
| 1 | User can run `./aishell` and enter shell in container | VERIFIED | `handle-default` calls `run/run-container nil []` when no args; `run-container` builds docker args with `/bin/bash` command |
| 2 | User can run `./aishell claude` and Claude Code starts | VERIFIED | dispatch-table routes "claude" to `handle-run`; `run-container` appends `["claude" "--dangerously-skip-permissions"]` |
| 3 | User can run `./aishell opencode` and OpenCode starts | VERIFIED | dispatch-table routes "opencode" to `handle-run`; `run-container` appends `["opencode"]` |
| 4 | Running without build shows clear error message | VERIFIED | `output/error-no-build` called when `state/read-state` returns nil: "No image built. Run: aishell build" |
| 5 | Running claude without --with-claude build shows error | VERIFIED | `verify-harness-available` checks `(get state :with-claude)` and errors with "Claude Code not installed. Run: aishell build --with-claude" |
| 6 | Extra args pass through to harness | VERIFIED | `(into ["claude" "--dangerously-skip-permissions"] harness-args)` in run.clj:73-74 |
| 7 | Container is destroyed on exit | VERIFIED | `build-docker-args` includes `"--rm"` flag at docker/run.clj:163 |

**Score:** 7/7 truths verified

### Required Artifacts

| Artifact | Expected | Status | Details |
|----------|----------|--------|---------|
| `src/aishell/config.clj` | YAML config loading with validation | VERIFIED | 79 lines, exports load-config, validate-config, config-source |
| `src/aishell/docker/run.clj` | Docker run argument construction | VERIFIED | 215 lines, exports build-docker-args, read-git-identity |
| `src/aishell/run.clj` | Run command orchestration | VERIFIED | 84 lines, exports run-container |
| `src/aishell/cli.clj` | CLI dispatch for shell/claude/opencode | VERIFIED | Contains handle-run (lines 128-150), dispatch-table includes claude/opencode (lines 170-174) |

### Key Link Verification

| From | To | Via | Status | Details |
|------|-----|-----|--------|---------|
| config.clj | clj-yaml.core | yaml/parse-string | WIRED | Lines 53, 62 in config.clj |
| config.clj | util.clj | util/get-home | WIRED | Line 21 in config.clj |
| docker/run.clj | util.clj | util/expand-path | WIRED | Lines 49-50 in docker/run.clj |
| docker/run.clj | babashka.process | p/shell | WIRED | Lines 17, 27, 30 for git config and id commands |
| run.clj | config.clj | config/load-config | WIRED | Line 52 in run.clj |
| run.clj | docker/run.clj | docker-run/build-docker-args | WIRED | Lines 64-68 in run.clj |
| run.clj | babashka.process | p/exec | WIRED | Line 83 in run.clj: `(apply p/exec ...)` |
| cli.clj | run.clj | run/run-container | WIRED | Lines 150, 168 in cli.clj |

### Plan 01 (Config Module) Verification

| Truth | Status | Evidence |
|-------|--------|----------|
| Config module can load .aishell/config.yaml from project directory | VERIFIED | `project-config-path` + `fs/exists?` check at line 50 |
| Config module falls back to ~/.aishell/config.yaml if project config missing | VERIFIED | `global-config-path` + fallback logic at lines 58-65 |
| Config module returns nil if no config exists | VERIFIED | `:else nil` at line 68 |
| Invalid YAML syntax causes immediate error exit | VERIFIED | `catch Exception e` -> `output/error` at lines 55-56, 64-65 |
| Unknown config keys trigger warning but don't fail | VERIFIED | `validate-config` at lines 23-33: warns via `output/warn`, returns config |

### Plan 02 (Docker Run Args) Verification

| Truth | Status | Evidence |
|-------|--------|----------|
| Docker run args include project mount at same path as host | VERIFIED | Line 165: `"-v" (str project-dir ":" project-dir)` |
| Docker run args include git identity env vars when available | VERIFIED | Lines 176-181: GIT_AUTHOR_NAME, GIT_COMMITTER_NAME, GIT_AUTHOR_EMAIL, GIT_COMMITTER_EMAIL |
| Container is ephemeral and interactive | VERIFIED | Line 163: `"--rm" "-it" "--init"` |
| Mount paths with ~ and $HOME are expanded correctly | VERIFIED | Lines 49-50: `util/expand-path` called on source and dest |
| Env vars with nil value are passthrough, with value are literal | VERIFIED | Lines 71-78: nil -> `["-e" key-name]`, value -> `["-e" (str key-name "=" v)]` |
| Ports are validated for format before adding to args | VERIFIED | Line 96: `re-matches port-pattern` before adding `-p` flag |
| PRE_START is passed via -e PRE_START=command | VERIFIED | Lines 206-207: `(into ["-e" (str "PRE_START=" (:pre_start config))])` |

### Plan 03 (CLI Dispatch) Verification

| Truth | Status | Evidence |
|-------|--------|----------|
| User can run ./aishell and enter shell in container | VERIFIED | Lines 166-168: `handle-default` calls `run/run-container nil []` |
| User can run ./aishell claude and Claude Code starts | VERIFIED | Line 172: dispatch to `handle-run % "claude"`, line 73: appends `["claude" "--dangerously-skip-permissions"]` |
| User can run ./aishell opencode and OpenCode starts | VERIFIED | Line 173: dispatch to `handle-run % "opencode"`, line 77: appends `["opencode"]` |
| Running without build shows clear error message | VERIFIED | run.clj lines 35-36: `output/error-no-build` |
| Running claude without --with-claude build shows error | VERIFIED | run.clj lines 44-48: `verify-harness-available` |
| Extra args pass through to harness | VERIFIED | cli.clj line 150: `(run/run-container cmd (vec args))`, run.clj lines 72-77: `(into [...] harness-args)` |
| Container is destroyed on exit | VERIFIED | `--rm` flag in build-docker-args |

### Requirements Coverage

| Requirement | Status | Notes |
|-------------|--------|-------|
| CLI-04 (shell command) | SATISFIED | `./aishell` runs shell |
| CLI-05 (claude command) | SATISFIED | `./aishell claude` runs Claude |
| CLI-06 (opencode command) | SATISFIED | `./aishell opencode` runs OpenCode |
| DOCK-04 (project mount) | SATISFIED | Same-path mount in build-docker-args |
| DOCK-05 (git identity) | SATISFIED | GIT_* env vars from read-git-identity |
| DOCK-06 (ephemeral container) | SATISFIED | --rm flag |
| CONF-01 (YAML config) | SATISFIED | clj-yaml parsing in config.clj |
| CONF-02 (project config path) | SATISFIED | .aishell/config.yaml |
| CONF-03 (global fallback) | SATISFIED | ~/.aishell/config.yaml |
| CONF-04 (mounts) | SATISFIED | build-mount-args with expand-path |
| CONF-05 (env) | SATISFIED | build-env-args with passthrough support |
| CONF-06 (pre_start) | SATISFIED | PRE_START env var passed, entrypoint handles |
| PLAT-01 (UID/GID mapping) | SATISFIED | LOCAL_UID, LOCAL_GID env vars |
| PLAT-02 (home directory) | SATISFIED | LOCAL_HOME env var |

### Anti-Patterns Found

| File | Line | Pattern | Severity | Impact |
|------|------|---------|----------|--------|
| None | - | - | - | - |

No TODO, FIXME, placeholder, or stub patterns found in any of the phase artifacts.

### Human Verification Required

#### 1. Container Shell Entry
**Test:** Run `./aishell` in a project with Docker available
**Expected:** Container starts, shell prompt appears, `pwd` shows same path as host
**Why human:** Requires Docker runtime

#### 2. Claude Code Launch
**Test:** Build with `--with-claude`, then run `./aishell claude`
**Expected:** Claude Code starts with permission prompts skipped
**Why human:** Requires Docker runtime and Claude installation

#### 3. Config Application
**Test:** Create `.aishell/config.yaml` with mounts/env, run `./aishell`
**Expected:** Mounts available in container, env vars set
**Why human:** Requires Docker runtime

#### 4. PRE_START Execution
**Test:** Set `pre_start: "touch /tmp/test"` in config, run container, check `/tmp/test` exists
**Expected:** File created by pre_start command
**Why human:** Requires Docker runtime

### Summary

Phase 16 goal "Users can enter shell or run harnesses directly with full configuration support" is **ACHIEVED**.

All must-haves verified:
- **Config loading:** YAML parsing with project-first/global-fallback, validation, unknown key warnings
- **Docker args:** Full argument construction including mounts, env, ports, docker_args, pre_start
- **CLI dispatch:** shell/claude/opencode commands with help and error handling
- **Wiring:** All key links verified between modules

Human verification items are for runtime behavior that requires Docker availability.

---

*Verified: 2026-01-20T21:50:00Z*
*Verifier: Claude (gsd-verifier)*
