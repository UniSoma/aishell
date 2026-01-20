---
phase: 16-run-commands
verified: 2026-01-20T22:55:00Z
status: passed
score: 7/7 must-haves verified
re_verification:
  previous_status: passed
  previous_score: 7/7
  gaps_closed:
    - "env config handles array format [FOO=bar] in addition to map format {FOO: bar}"
    - "claude/opencode commands pass-through arguments to harness"
  gaps_remaining: []
  regressions: []
---

# Phase 16: Run Commands Verification Report

**Phase Goal:** Users can enter shell or run harnesses directly with full configuration support
**Verified:** 2026-01-20T22:55:00Z
**Status:** passed
**Re-verification:** Yes - after gap closure (plan 16-04)

## Goal Achievement

### Observable Truths

| # | Truth | Status | Evidence |
|---|-------|--------|----------|
| 1 | User can run `./aishell` and enter shell in container | VERIFIED | `handle-default` calls `run/run-container nil []` when no args; `run-container` builds docker args with `/bin/bash` command |
| 2 | User can run `./aishell claude` and Claude Code starts | VERIFIED | dispatch-table routes "claude" to `handle-run`; `run-container` appends `["claude" "--dangerously-skip-permissions"]` |
| 3 | User can run `./aishell opencode` and OpenCode starts | VERIFIED | dispatch-table routes "opencode" to `handle-run`; `run-container` appends `["opencode"]` |
| 4 | Running without build shows clear error message | VERIFIED | `output/error-no-build` called when `state/read-state` returns nil: "No image built. Run: aishell build" |
| 5 | Running claude without --with-claude build shows error | VERIFIED | `verify-harness-available` checks `(get state :with-claude)` and errors with "Claude Code not installed. Run: aishell build --with-claude" |
| 6 | Extra args pass through to harness | VERIFIED | run.clj:73-77: `(into ["claude" "--dangerously-skip-permissions"] harness-args)` and `(into ["opencode"] harness-args)` |
| 7 | Container is destroyed on exit | VERIFIED | `build-docker-args` includes `"--rm"` flag at docker/run.clj:182 |

**Score:** 7/7 truths verified

### Gap Closure Verification (Plan 16-04)

| Gap | Status | Evidence |
|-----|--------|----------|
| env config handles array format | VERIFIED | docker/run.clj:57-64 `parse-env-string` parses "KEY=value" strings; line 82-86 checks `(map? env)` and routes array format through `parse-env-string` |
| claude/opencode pass-through args | VERIFIED | cli.clj:172-173 both entries have `:restrict false`; run.clj:74,77 use `(into [...] harness-args)` |

### Required Artifacts

| Artifact | Expected | Status | Details |
|----------|----------|--------|---------|
| `src/aishell/config.clj` | YAML config loading with validation | VERIFIED | 79 lines, exports load-config, validate-config, config-source |
| `src/aishell/docker/run.clj` | Docker run argument construction | VERIFIED | 234 lines, exports build-docker-args, read-git-identity, includes parse-env-string helper |
| `src/aishell/run.clj` | Run command orchestration | VERIFIED | 84 lines, exports run-container, passes harness-args to container command |
| `src/aishell/cli.clj` | CLI dispatch for shell/claude/opencode | VERIFIED | Contains handle-run (lines 128-150), dispatch-table with :restrict false for claude/opencode (lines 172-173) |

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
| cli.clj | run.clj | run/run-container | WIRED | Line 150 in cli.clj passes cmd and args |

### Env Config Dual Format Verification

| Format | Example | Status | Evidence |
|--------|---------|--------|----------|
| Map with literal | `{FOO: bar}` | VERIFIED | Line 84: `(map (fn [[k v]] [(name k) v]) env)` |
| Map with passthrough | `{FOO:}` | VERIFIED | Line 90-96: nil value triggers passthrough logic |
| Array with literal | `[FOO=bar]` | VERIFIED | Line 86: `(map parse-env-string env)` -> lines 61-64 split on "=" |
| Array with passthrough | `[FOO]` | VERIFIED | Line 63-64: no "=" returns `[s nil]` for passthrough |

### Pass-through Args Verification

| Command | Status | Evidence |
|---------|--------|----------|
| `./aishell claude --version` | VERIFIED | cli.clj:172 `:restrict false` allows unknown opts; run.clj:74 `(into [...] harness-args)` |
| `./aishell claude --model opus` | VERIFIED | Same mechanism - args collected and forwarded |
| `./aishell opencode --help` | VERIFIED | cli.clj:173 `:restrict false`; run.clj:77 `(into ["opencode"] harness-args)` |

### Requirements Coverage

| Requirement | Status | Notes |
|-------------|--------|-------|
| CLI-04 (shell command) | SATISFIED | `./aishell` runs shell |
| CLI-05 (claude command) | SATISFIED | `./aishell claude` runs Claude with pass-through args |
| CLI-06 (opencode command) | SATISFIED | `./aishell opencode` runs OpenCode with pass-through args |
| DOCK-04 (project mount) | SATISFIED | Same-path mount in build-docker-args |
| DOCK-05 (git identity) | SATISFIED | GIT_* env vars from read-git-identity |
| DOCK-06 (ephemeral container) | SATISFIED | --rm flag |
| CONF-01 (YAML config) | SATISFIED | clj-yaml parsing in config.clj |
| CONF-02 (project config path) | SATISFIED | .aishell/config.yaml |
| CONF-03 (global fallback) | SATISFIED | ~/.aishell/config.yaml |
| CONF-04 (mounts) | SATISFIED | build-mount-args with expand-path |
| CONF-05 (env) | SATISFIED | build-env-args with dual format (map + array) support |
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

#### 2. Claude Code Launch with Args
**Test:** Build with `--with-claude`, then run `./aishell claude --version`
**Expected:** Claude Code shows version (argument passed through)
**Why human:** Requires Docker runtime and Claude installation

#### 3. Config Application (Array Format)
**Test:** Create `.aishell/config.yaml` with `env: [FOO=bar]`, run `./aishell`, check `echo $FOO`
**Expected:** FOO=bar inside container
**Why human:** Requires Docker runtime

#### 4. Config Application (Map Format)
**Test:** Create `.aishell/config.yaml` with `env: {FOO: bar}`, run `./aishell`, check `echo $FOO`
**Expected:** FOO=bar inside container
**Why human:** Requires Docker runtime

#### 5. PRE_START Execution
**Test:** Set `pre_start: "touch /tmp/test"` in config, run container, check `/tmp/test` exists
**Expected:** File created by pre_start command
**Why human:** Requires Docker runtime

### Summary

Phase 16 goal "Users can enter shell or run harnesses directly with full configuration support" is **ACHIEVED**.

**Re-verification notes:**
- Previous verification (2026-01-20T21:50:00Z) passed 7/7 but UAT revealed two issues
- Plan 16-04 fixed both issues:
  1. `parse-env-string` added to handle array format `[FOO=bar]` in env config
  2. `:restrict false` added to claude/opencode dispatch entries for pass-through args
- All original must-haves remain verified (no regressions)
- Both gap closures verified in code

All must-haves verified:
- **Config loading:** YAML parsing with project-first/global-fallback, dual format env support
- **Docker args:** Full argument construction including mounts, env (map+array), ports, docker_args, pre_start
- **CLI dispatch:** shell/claude/opencode commands with pass-through args and error handling
- **Wiring:** All key links verified between modules

Human verification items are for runtime behavior that requires Docker availability.

---

*Verified: 2026-01-20T22:55:00Z*
*Verifier: Claude (gsd-verifier)*
*Re-verification: After plan 16-04 gap closure*
