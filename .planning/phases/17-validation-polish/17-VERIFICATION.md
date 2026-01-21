---
phase: 17-validation-polish
verified: 2026-01-21T02:30:00Z
status: passed
score: 4/4 must-haves verified
re_verification:
  previous_status: gaps_found
  previous_score: 4/4 (but UAT found 2 gaps)
  gaps_closed:
    - "Security warning displays for dangerous docker_args (vector input now supported)"
    - "Version change in build flags triggers rebuild detection"
  gaps_remaining: []
  regressions: []
---

# Phase 17: Validation & Polish Verification Report

**Phase Goal:** Security validations and update awareness matching v1.2 hardening
**Verified:** 2026-01-21T02:30:00Z
**Status:** passed
**Re-verification:** Yes - after gap closure (17-03-PLAN.md executed)

## Goal Achievement

### Observable Truths

| # | Truth | Status | Evidence |
|---|-------|--------|----------|
| 1 | User can run `./aishell update` to check for updates | VERIFIED | `handle-update` function at cli.clj:133-164, wired to dispatch-table at line 189 |
| 2 | Invalid version strings (e.g., `1.0; rm -rf /`) are rejected with clear error | VERIFIED | `dangerous-chars` pattern at cli.clj:23, `validate-version` at cli.clj:25-35, called at lines 107-108 |
| 3 | Dangerous docker_args patterns (--privileged, docker.sock) trigger warnings | VERIFIED | validation.clj:19-35 handles both string and vector input via `sequential?` check at line 24, called from run.clj:80 |
| 4 | User is warned when embedded Dockerfile changed since last build | VERIFIED | `check-dockerfile-stale` at run.clj:25-32, called at line 76, compares stored vs current hash |

**Score:** 4/4 truths verified

### UAT Gap Closures

#### Gap 1 (Test 4): Vector docker_args crash - CLOSED

**Previous issue:** `check-dangerous-args` crashed with "LazySeq cannot be cast to CharSequence" when config.yaml had `docker_args: ["--privileged"]` (vector format).

**Fix verified at validation.clj:24-26:**
```clojure
(let [args-str (if (sequential? docker-args)
                 (str/join " " docker-args)
                 docker-args)]
```

The function now normalizes vector input to string before pattern matching.

#### Gap 2 (Test 6): Version change not triggering rebuild - CLOSED

**Previous issue:** Changing `--with-claude` version (e.g., 1.0.0 to 1.0.1) did not trigger rebuild - reported "base image is up to date".

**Fix verified at build.clj:38-51:**
```clojure
(defn version-changed?
  "Check if requested harness versions differ from stored state.
   Returns true if rebuild needed due to version change."
  [opts state]
  (or
    (and (:with-claude opts)
         (not= (:claude-version opts) (:claude-version state)))
    (and (:with-opencode opts)
         (not= (:opencode-version opts) (:opencode-version state)))
    (and (:with-claude opts) (not (:with-claude state)))
    (and (:with-opencode opts) (not (:with-opencode state)))))
```

**Integration verified at build.clj:130-132:**
```clojure
(let [state ((requiring-resolve 'aishell.state/read-state))]
  (if-not (or (needs-rebuild? base-image-tag force)
              (version-changed? opts state))
```

Cache now checks both dockerfile hash AND version changes.

### Required Artifacts

| Artifact | Expected | Status | Details |
|----------|----------|--------|---------|
| `src/aishell/validation.clj` | Dangerous docker_args pattern checking (string + vector) | VERIFIED (50 lines) | Exports `check-dangerous-args`, `warn-dangerous-args`; sequential? normalization at line 24 |
| `src/aishell/cli.clj` | update command and --force flag | VERIFIED (214 lines) | `handle-update` at line 133, `:force` in build-spec at line 58, update in dispatch-table at line 189 |
| `src/aishell/docker/build.clj` | --no-cache support + version-aware cache | VERIFIED (180 lines) | `run-build` accepts `force?` at line 87, `version-changed?` at line 38, integrated at line 132 |
| `src/aishell/run.clj` | Stale image detection and security warnings | VERIFIED (102 lines) | `check-dockerfile-stale` at line 25, `validation/warn-dangerous-args` call at line 80 |
| `src/aishell/state.clj` | Dockerfile hash in state schema | VERIFIED (36 lines) | Schema docstring includes `:dockerfile-hash` at line 32 |

### Key Link Verification

| From | To | Via | Status | Details |
|------|-----|-----|--------|---------|
| cli.clj:handle-update | docker/build.clj:build-base-image | `:force true` | WIRED | Line 157: `{... :force true}` passed to build-base-image |
| cli.clj:handle-build | state.clj:write-state | `:dockerfile-hash` | WIRED | Line 131: `:dockerfile-hash (hash/compute-hash templates/base-dockerfile)` |
| run.clj:run-container | validation.clj:warn-dangerous-args | calls with docker_args | WIRED | Line 80: `(validation/warn-dangerous-args docker-args)` |
| run.clj:run-container | check-dockerfile-stale | uses :dockerfile-hash from state | WIRED | Line 76: `(check-dockerfile-stale state)` where state from line 45 |
| docker/build.clj:run-build | Docker CLI | `--no-cache` when force | WIRED | Line 89: `(when force? ["--no-cache"])` |
| docker/build.clj:build-base-image | version-changed? | reads state, compares versions | WIRED | Lines 130-132: requiring-resolve for state, or condition includes version-changed? |

### Requirements Coverage

| Requirement | Status | Notes |
|-------------|--------|-------|
| CLI-07: User can run `aishell update` | SATISFIED | handle-update implements force rebuild with preserved config |
| VAL-01: Tool validates version strings | SATISFIED | dangerous-chars pattern + semver-pattern in cli.clj:20-35 |
| VAL-02: Tool warns about dangerous docker_args | SATISFIED | validation.clj handles both string and vector input |
| VAL-03: Tool detects Dockerfile hash changes | SATISFIED | check-dockerfile-stale compares stored vs current hash |

### Anti-Patterns Found

| File | Line | Pattern | Severity | Impact |
|------|------|---------|----------|--------|
| (none) | - | - | - | No anti-patterns detected |

No TODO, FIXME, placeholder, or "not implemented" patterns found in Phase 17 artifacts.

### Human Verification Required

#### 1. Vector docker_args Warning (Gap 1 closure)
**Test:** Create `.aishell/config.yaml` with `docker_args: ["--privileged"]`, then run `./aishell`  
**Expected:** Security warning "Potentially dangerous Docker options detected", then container starts (no crash)  
**Why human:** Requires Docker available and config file creation

#### 2. Version Change Rebuild (Gap 2 closure)
**Test:** Run `./aishell build --with-claude=1.0.0`, then run `./aishell build --with-claude=1.0.1`  
**Expected:** Second build triggers rebuild (not "up to date" message)  
**Why human:** Requires Docker and observing build behavior

#### 3. Version Validation Rejection
**Test:** Run `./aishell build --with-claude="1.0; rm -rf /"`  
**Expected:** Error message "Invalid Claude Code version: contains shell metacharacters"  
**Why human:** Shell may intercept special characters before CLI receives them

#### 4. Stale Image Warning
**Test:** Build image, modify templates/base-dockerfile, run `./aishell`  
**Expected:** Warning "Image may be stale. Run 'aishell update' to rebuild."  
**Why human:** Requires modifying source code temporarily

## Summary

All Phase 17 must-haves are verified at all three levels after gap closure:

1. **Existence:** All required files exist with expected content
2. **Substantive:** All files have real implementations (no stubs detected)
3. **Wired:** All key links verified - components call each other correctly

### Gap Closure Summary

| Gap | UAT Test | Root Cause | Fix Applied | Status |
|-----|----------|------------|-------------|--------|
| Vector docker_args crash | Test 4 | str/blank? requires string, received vector | sequential? check + str/join normalization | CLOSED |
| Version change no rebuild | Test 6 | Cache only checked dockerfile hash | Added version-changed? function + integration | CLOSED |

The implementation now fully matches the v1.2 hardening security model:
- Version validation rejects shell metacharacters before use in Docker commands
- Dangerous docker_args patterns trigger advisory warnings (accepts both string and vector)
- Stale Dockerfile detection compares hashes and advises rebuild
- Version changes in build flags properly invalidate cache

---

*Verified: 2026-01-21T02:30:00Z*  
*Verifier: Claude (gsd-verifier)*  
*Re-verification after: 17-03-PLAN.md gap closure execution*
