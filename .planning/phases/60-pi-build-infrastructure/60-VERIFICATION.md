---
phase: 60-pi-build-infrastructure
verified: 2026-02-18T02:03:35Z
status: passed
score: 4/4 success criteria verified
re_verification: false
---

# Phase 60: Pi Build Infrastructure Verification Report

**Phase Goal:** Pi coding agent can be built into the harness volume and the foundation image includes fd
**Verified:** 2026-02-18T02:03:35Z
**Status:** PASSED
**Re-verification:** No — initial verification

---

## Goal Achievement

### Observable Truths (from Success Criteria)

| # | Truth | Status | Evidence |
|---|-------|--------|----------|
| 1 | `aishell build --with-pi` succeeds and installs @mariozechner/pi-coding-agent via npm in the harness volume | VERIFIED | `:pi` in `harness-keys`, `@mariozechner/pi-coding-agent` in `harness-npm-packages`; `build-install-commands` iterates both dynamically; `handle-setup` wires `:with-pi` through state-map to `populate-volume` |
| 2 | `aishell build --with-pi=1.0.0` pins the pi version in build state and installs that specific version | VERIFIED | `parse-with-flag` parses `--with-pi=1.0.0`; `validate-version` validates it; `:pi-version` persisted in state-map; `build-install-commands` uses version from state for `@mariozechner/pi-coding-agent@1.0.0` |
| 3 | `fd` command is available inside the container (fd-find package with fd symlink in foundation image) | VERIFIED | `fd-find` present in apt-get install list (line 30 of `base-dockerfile`); `RUN ln -s /usr/bin/fdfind /usr/bin/fd` present at line 46 |
| 4 | PI_CODING_AGENT_DIR and PI_SKIP_VERSION_CHECK environment variables from host are passed through to the container | VERIFIED | Both vars present in `api-key-vars` vector (lines 251-252 of `docker/run.clj`); `build-api-env-args` passes them through when set on host |

**Score:** 4/4 truths verified

---

## Required Artifacts

### Plan 01 Artifacts

| Artifact | Expected | Status | Details |
|----------|----------|--------|---------|
| `src/aishell/docker/templates.clj` | fd-find package installation with fd symlink in foundation Dockerfile | VERIFIED | `fd-find` at line 30 in apt-get list (alphabetical after `curl`); `RUN ln -s /usr/bin/fdfind /usr/bin/fd` at line 46 |
| `src/aishell/docker/volume.clj` | Pi harness key and npm package mapping for volume population | VERIFIED | `:pi` in `harness-keys` vector (line 16); `@mariozechner/pi-coding-agent` in `harness-npm-packages` map (line 24) |

### Plan 02 Artifacts

| Artifact | Expected | Status | Details |
|----------|----------|--------|---------|
| `src/aishell/cli.clj` | `--with-pi` flag in setup spec, pi in installed-harnesses, handle-setup state-map, update display | VERIFIED | `:with-pi` in `setup-spec` (line 76); `(:with-pi state) (conj "pi")` in `installed-harnesses` (line 93); `pi-config` parsed, validated, and persisted in `handle-setup` (lines 171-204); `handle-update` displays pi version (lines 310-311); `"pi"` dispatch case (lines 622-623) |
| `src/aishell/docker/run.clj` | PI_CODING_AGENT_DIR and PI_SKIP_VERSION_CHECK env var passthrough, pi config mount, pi alias env | VERIFIED | Both PI vars in `api-key-vars` (lines 251-252); `[".pi"]` in `config-entries` (line 222); `["pi" :with-pi false]` in `build-harness-alias-env-args` `known` vector (line 193) |
| `src/aishell/run.clj` | Pi harness verification and ensure-harness-volume pi check | VERIFIED | `:with-pi` in `ensure-harness-volume` some-check (line 49); `"pi" "Pi coding agent"` in `verify-harness-available` case (line 31); `"pi" (verify-harness-available ...)` and container-cmd `(into ["pi"] merged-args)` in `run-container` (lines 140, 224) |
| `src/aishell/check.clj` | Pi in harness status display | VERIFIED | `["Pi" :with-pi :pi-version]` in `check-harnesses` harnesses vector (line 88) |
| `src/aishell/state.clj` | Pi fields in state schema documentation | VERIFIED | `:with-pi false` and `:pi-version nil` in `write-state` docstring (lines 30, 35) |

---

## Key Link Verification

### Plan 01 Key Links

| From | To | Via | Status | Details |
|------|----|-----|--------|---------|
| `src/aishell/docker/volume.clj` | harness volume | `build-install-commands` generates npm install for pi | WIRED | `harness-npm-packages` contains `:pi "@mariozechner/pi-coding-agent"`; `build-install-commands` uses `keep` over `harness-npm-packages` checking `with-{name}` state key — will emit `@mariozechner/pi-coding-agent@{version}` when `:with-pi` is true |
| `src/aishell/docker/volume.clj` | `compute-harness-hash` | `harness-keys` includes `:pi` for hash computation | WIRED | `harness-keys` vector contains `:pi` (line 16); `normalize-harness-config` and `compute-harness-hash` iterate over `harness-keys` — `:pi` presence/absence affects the hash |

### Plan 02 Key Links

| From | To | Via | Status | Details |
|------|----|-----|--------|---------|
| `src/aishell/cli.clj` | `src/aishell/state.clj` | `handle-setup` persists `:with-pi` and `:pi-version` to state | WIRED | `state-map` includes `:with-pi (:enabled? pi-config)` and `:pi-version (:version pi-config)` (lines 198, 204); passed to `state/write-state` |
| `src/aishell/cli.clj` | `src/aishell/docker/volume.clj` | `handle-setup` includes `:with-pi` in state-map for volume hash computation | WIRED | `state-map` with `:with-pi` passed to `vol/compute-harness-hash` (line 206) and volume population guard `(some #(get state-map %) [...:with-pi])` (line 210) |
| `src/aishell/docker/run.clj` | container environment | PI_* env vars passed through via `-e` flags | WIRED | Both vars in `api-key-vars`; `build-api-env-args` filters for host-set vars and emits `-e VAR=value` pairs; called in `build-docker-args-internal` (line 308) |
| `src/aishell/run.clj` | `src/aishell/docker/volume.clj` | `ensure-harness-volume` checks `:with-pi` for volume population trigger | WIRED | Line 49: `(some #(get state %) [:with-claude :with-opencode :with-codex :with-gemini :with-pi])` |
| `src/aishell/cli.clj` | `src/aishell/run.clj` | dispatch function routes `"pi"` command to `run/run-container` | WIRED | Line 622-623: `"pi" (run/run-container "pi" (vec (rest clean-args)) {:unsafe unsafe? :container-name container-name-override})` |
| `src/aishell/run.clj` | `verify-harness-available` | `run-container` calls `verify-harness-available` with `"pi"` mapping to `"Pi coding agent"` | WIRED | Line 140: `"pi" (verify-harness-available "pi" :with-pi state)`; line 31: `"pi" "Pi coding agent"` in display-name case |

---

## Requirements Coverage

| Requirement | Source Plan | Description | Status | Evidence |
|-------------|------------|-------------|--------|---------|
| FOUND-01 | 60-01 | fd-find package installed in foundation image with `fd` symlink | SATISFIED | `fd-find` in apt-get list at line 30 of `base-dockerfile`; `RUN ln -s /usr/bin/fdfind /usr/bin/fd` at line 46 |
| FOUND-02 | 60-01 | Foundation image rebuild triggered on next `aishell update --force` | SATISFIED | Template string hash changes with new `fd-find` content; `check-dockerfile-stale` compares stored hash to current `templates/base-dockerfile` hash; `handle-setup` stores `(hash/compute-hash templates/base-dockerfile)` as `:foundation-hash` |
| HARNESS-01 | 60-02 | User can build with `--with-pi` flag to include pi coding agent | SATISFIED | `:with-pi` in `setup-spec`; `parse-with-flag` parses it; state persisted; volume populated when `:with-pi` true |
| HARNESS-03 | 60-02 | User can pin pi version with `--with-pi=VERSION` at build time | SATISFIED | `parse-with-flag` returns `{:enabled? true :version "1.0.0"}` for `--with-pi=1.0.0`; `validate-version` validates semver; `:pi-version` stored in state; `build-install-commands` uses it |
| HARNESS-05 | 60-01 | Pi installed via npm in harness volume (same pattern as Claude/Codex/Gemini) | SATISFIED | `@mariozechner/pi-coding-agent` in `harness-npm-packages`; `:pi` in `harness-keys`; `build-install-commands` generates `npm install -g @mariozechner/pi-coding-agent@{version}` for enabled pi |
| ENV-01 | 60-02 | PI_CODING_AGENT_DIR env var passed through to container | SATISFIED | `"PI_CODING_AGENT_DIR"` in `api-key-vars` vector (line 251 of `docker/run.clj`); passed when set on host |
| ENV-02 | 60-02 | PI_SKIP_VERSION_CHECK env var passed through to container | SATISFIED | `"PI_SKIP_VERSION_CHECK"` in `api-key-vars` vector (line 252 of `docker/run.clj`); passed when set on host |

**All 7 declared requirements satisfied.**

---

## Observations: Requirements Implemented Beyond Phase 60 Scope

The following requirements are marked Phase 61 Pending in REQUIREMENTS.md but were actually implemented in Phase 60 code:

| Requirement | Phase 61 Claim | Phase 60 Implementation |
|-------------|---------------|------------------------|
| HARNESS-02: User can run `aishell pi` | Phase 61 Pending | IMPLEMENTED — `"pi"` case in `cli.clj` dispatch (line 622); `run-container` handles `"pi"` cmd with `container-cmd (into ["pi"] merged-args)` (line 224) |
| HARNESS-04: Pi config directory mounted from host | Phase 61 Pending | IMPLEMENTED — `[".pi"]` in `build-harness-config-mounts` `config-entries` (line 222 of `docker/run.clj`) |
| HARNESS-07: Pi shown in `aishell --help` when installed | Phase 61 Pending | IMPLEMENTED — `(when (contains? installed "pi") (println ...))` in `print-help` (lines 123-124 of `cli.clj`) |

REQUIREMENTS.md traceability table and checkboxes are stale and should be updated for these three requirements. This does not affect Phase 60 goal achievement.

---

## Anti-Patterns Found

| File | Line | Pattern | Severity | Impact |
|------|------|---------|----------|--------|
| `src/aishell/docker/templates.clj` | 173 | Entrypoint alias loop hardcoded to `HARNESS_ALIAS_CLAUDE HARNESS_ALIAS_OPENCODE HARNESS_ALIAS_CODEX HARNESS_ALIAS_GEMINI` — excludes `HARNESS_ALIAS_PI` | Info | `run.clj` correctly emits `-e HARNESS_ALIAS_PI=...` when pi is installed with `harness_args`, but the entrypoint won't consume it to create the `pi` shell alias. The `pi` alias won't appear in `~/.bash_aliases`. However, HARNESS-09 (entrypoint alias available) is assigned to Phase 61, not Phase 60. The omission is in scope for the next phase. |

---

## Human Verification Required

None — all four success criteria can be determined by static analysis of the source code. Runtime behavior (actual docker build, npm install execution) is beyond static verification scope but the wiring is complete and correct.

---

## Gaps Summary

No gaps. All four success criteria from the roadmap are verified:

1. `@mariozechner/pi-coding-agent` npm install wiring through `harness-npm-packages` + `build-install-commands` + state-map `:with-pi` flag is complete and matches the pattern established by Claude/Codex/Gemini.
2. Version pinning via `--with-pi=VERSION` is parsed, validated, stored in state, and used at install time.
3. `fd-find` is in the foundation Dockerfile apt-get list with the required `fdfind -> fd` symlink.
4. `PI_CODING_AGENT_DIR` and `PI_SKIP_VERSION_CHECK` are in `api-key-vars` and will be forwarded when set on host.

All 3 commits (91d905e, 4d29b66, 7445cbe) are confirmed present in the repository.

The one notable anti-pattern (entrypoint alias loop missing HARNESS_ALIAS_PI) is a known deferred item under HARNESS-09, which is Phase 61 scope.

---

_Verified: 2026-02-18T02:03:35Z_
_Verifier: Claude (gsd-verifier)_
