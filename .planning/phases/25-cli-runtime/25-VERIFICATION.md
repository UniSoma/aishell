---
phase: 25-cli-runtime
verified: 2026-01-25T10:30:00Z
status: passed
score: 8/8 must-haves verified
---

# Phase 25: CLI & Runtime Verification Report

**Phase Goal:** Enable running Codex and Gemini from aishell with proper config mounting and environment setup
**Verified:** 2026-01-25T10:30:00Z
**Status:** passed
**Re-verification:** No - initial verification

## Goal Achievement

### Observable Truths

| # | Truth | Status | Evidence |
|---|-------|--------|----------|
| 1 | User can run `aishell codex [args]` and arguments pass through correctly | VERIFIED | cli.clj:249 dispatches to run-container; run.clj:163-164 constructs `["codex" ...merged-args]`; merged-args combines defaults + CLI args at line 105 |
| 2 | User can run `aishell gemini [args]` and arguments pass through correctly | VERIFIED | cli.clj:250 dispatches to run-container; run.clj:166-167 constructs `["gemini" ...merged-args]`; same argument merging as codex |
| 3 | Codex config directory (~/.codex/) is mounted and accessible in container | VERIFIED | docker/run.clj:141-142 adds `[(str home "/.codex") (str home "/.codex")]` to config-paths |
| 4 | Gemini config directory (~/.gemini/) is mounted and accessible in container | VERIFIED | docker/run.clj:143-144 adds `[(str home "/.gemini") (str home "/.gemini")]` to config-paths |
| 5 | CODEX_API_KEY environment variable is passed through to container | VERIFIED | docker/run.clj:153 includes "CODEX_API_KEY" in api-key-vars |
| 6 | GEMINI_API_KEY and GOOGLE_API_KEY environment variables are passed through | VERIFIED | docker/run.clj:154 has "GEMINI_API_KEY"; docker/run.clj:155 has "GOOGLE_API_KEY" |
| 7 | GOOGLE_APPLICATION_CREDENTIALS is passed through for Vertex AI authentication | VERIFIED | docker/run.clj:166 has env var in api-key-vars; docker/run.clj:175-181 build-gcp-credentials-mount mounts file :ro; docker/run.clj:226 integrates into build-docker-args |
| 8 | User can configure default args via config.yaml harness_args.codex and harness_args.gemini | VERIFIED | config.clj:15 known-harnesses includes "codex" and "gemini"; run.clj:99 gets defaults via `(get-in cfg [:harness_args (keyword cmd)])` |

**Score:** 8/8 truths verified

### Required Artifacts

| Artifact | Expected | Status | Details |
|----------|----------|--------|---------|
| `src/aishell/docker/run.clj` | Config mounts and env var passthrough for Codex/Gemini | VERIFIED | ~/.codex and ~/.gemini mounts (lines 141-144), CODEX_API_KEY/GEMINI_API_KEY/GOOGLE_API_KEY in api-key-vars (lines 153-155), build-gcp-credentials-mount function (lines 175-181) |
| `src/aishell/cli.clj` | CLI dispatch for codex and gemini commands | VERIFIED | Case statements for "codex" (line 249) and "gemini" (line 250), help text (lines 81-82, 93) |
| `src/aishell/run.clj` | Harness verification and container command construction | VERIFIED | verify-harness-available cases (lines 26-27), verification calls (lines 88-89), container-cmd cases (lines 163-167) |
| `src/aishell/config.clj` | Known harnesses validation | VERIFIED | known-harnesses set includes "codex" and "gemini" (line 15) |
| `src/aishell/output.clj` | Command suggestions for typos | VERIFIED | known-commands set includes "codex" and "gemini" (line 19) |

### Key Link Verification

| From | To | Via | Status | Details |
|------|-----|-----|--------|---------|
| src/aishell/cli.clj | src/aishell/run.clj | run/run-container call | WIRED | cli.clj:249-250 calls `(run/run-container "codex" ...)` and `(run/run-container "gemini" ...)` |
| src/aishell/run.clj | container execution | container-cmd case statement | WIRED | run.clj:163-167 constructs `(into ["codex"] merged-args)` and `(into ["gemini"] merged-args)` |
| src/aishell/run.clj | src/aishell/config.clj | config/load-config call | WIRED | run.clj:94 loads config; run.clj:99 extracts harness_args |
| src/aishell/docker/run.clj | Docker container | build-docker-args function | WIRED | run.clj:147-152 calls docker-run/build-docker-args; result includes all mounts and env vars |
| src/aishell/run.clj | state verification | verify-harness-available | WIRED | run.clj:88-89 calls verify with :with-codex and :with-gemini state keys |

### Requirements Coverage

Based on ROADMAP.md Phase 25 requirements:

| Requirement | Status | Blocking Issue |
|-------------|--------|----------------|
| CODEX-03: Config directory mounting | SATISFIED | - |
| CODEX-04: API key passthrough | SATISFIED | - |
| CODEX-05: CLI dispatch | SATISFIED | - |
| CODEX-06: Harness verification | SATISFIED | - |
| GEMINI-03: Config directory mounting | SATISFIED | - |
| GEMINI-04: API key passthrough | SATISFIED | - |
| GEMINI-05: GCP credentials | SATISFIED | - |
| GEMINI-06: CLI dispatch | SATISFIED | - |
| GEMINI-07: Harness verification | SATISFIED | - |

### Anti-Patterns Found

| File | Line | Pattern | Severity | Impact |
|------|------|---------|----------|--------|
| - | - | None found | - | - |

No TODO/FIXME comments, no placeholder implementations, no stub patterns found in the modified files.

### Human Verification Required

While all automated checks pass, the following items would benefit from human verification:

### 1. End-to-End Codex Execution

**Test:** Build image with `aishell build --with-codex`, then run `aishell codex --version`
**Expected:** Codex CLI runs and shows version, config from ~/.codex/ is accessible
**Why human:** Requires actual Docker container execution and Codex CLI installation

### 2. End-to-End Gemini Execution

**Test:** Build image with `aishell build --with-gemini`, then run `aishell gemini --version`
**Expected:** Gemini CLI runs and shows version, config from ~/.gemini/ is accessible
**Why human:** Requires actual Docker container execution and Gemini CLI installation

### 3. API Key Environment Variable Passthrough

**Test:** Set `CODEX_API_KEY=test` and `GEMINI_API_KEY=test`, run `aishell codex` / `aishell gemini`, verify env vars are available in container
**Expected:** Environment variables visible inside container
**Why human:** Requires running container and checking environment

### 4. Config Defaults from harness_args

**Test:** Create config.yaml with `harness_args: {codex: ["--help"]}`, run `aishell codex`
**Expected:** Codex receives --help flag from config defaults
**Why human:** Requires testing config loading and argument merging in real execution

## Summary

All 8 observable truths have been verified through code inspection:

1. **CLI Dispatch:** Both `aishell codex` and `aishell gemini` commands are routed to `run/run-container` in cli.clj
2. **Argument Pass-through:** Arguments are merged with config defaults and passed to the container command
3. **Config Mounts:** ~/.codex and ~/.gemini directories are added to harness config mounts
4. **API Keys:** CODEX_API_KEY, GEMINI_API_KEY, and GOOGLE_API_KEY are in the api-key-vars list
5. **GCP Credentials:** GOOGLE_APPLICATION_CREDENTIALS env var is passed through AND the referenced file is mounted read-only
6. **Config Validation:** known-harnesses in config.clj includes "codex" and "gemini" for harness_args validation
7. **Help Text:** Both commands appear in CLI help with examples
8. **Error Messages:** verify-harness-available provides correct error messages directing users to build with the appropriate flag

The implementation follows the established harness addition pattern (cli.clj dispatch, run.clj verify+cmd, config.clj known-harnesses, output.clj known-commands) as documented in the SUMMARY.

---

*Verified: 2026-01-25T10:30:00Z*
*Verifier: Claude (gsd-verifier)*
