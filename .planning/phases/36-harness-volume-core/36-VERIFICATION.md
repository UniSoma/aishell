---
phase: 36-harness-volume-core
verified: 2026-01-31T23:58:00Z
status: gaps_found
score: 3/5 must-haves verified
gaps:
  - truth: "Harness volume mounts at runtime with PATH and NODE_PATH configured correctly so harness commands are executable"
    status: partial
    reason: "Volume mount infrastructure exists but is NOT wired into actual runtime - no caller passes harness-volume-name to build-docker-args"
    artifacts:
      - path: "src/aishell/docker/run.clj"
        issue: "Accepts :harness-volume-name parameter but no caller provides it - always nil/omitted"
      - path: "src/aishell/run.clj"
        issue: "Calls build-docker-args without :harness-volume-name parameter (lines 160-167)"
    missing:
      - "Integration point in run.clj to compute harness hash from state"
      - "Call to volume/compute-harness-hash before build-docker-args"
      - "Pass computed volume name to :harness-volume-name parameter"
      - "Lazy volume population logic (check exists, create if needed)"
  - truth: "Projects with identical harness combinations share the same volume (same hash = same volume)"
    status: failed
    reason: "Volume naming and hashing functions exist but are NEVER CALLED - volume.clj namespace not imported anywhere"
    artifacts:
      - path: "src/aishell/docker/volume.clj"
        issue: "Namespace exists with all functions implemented but has ZERO imports/usage in codebase"
    missing:
      - "Import aishell.docker.volume in run.clj or other orchestration code"
      - "Call to volume/compute-harness-hash to get volume name"
      - "Pass volume name through the runtime pipeline"
  - truth: "Harness volume rebuilds only when harness versions or flags change, not when foundation image changes"
    status: failed
    reason: "Staleness detection functions exist (get-volume-label) but not integrated - no runtime logic checks or uses volume labels"
    artifacts:
      - path: "src/aishell/docker/volume.clj"
        issue: "get-volume-label and create-volume accept labels but no code creates volumes with staleness metadata"
    missing:
      - "Volume staleness check logic (compare stored hash to current state hash)"
      - "Lazy population trigger based on staleness check"
      - "Label storage during volume creation (aishell.harness.hash label)"
---

# Phase 36: Harness Volume Core Verification Report

**Phase Goal:** Harness tools installed into Docker named volumes and mounted at runtime with correct PATH configuration

**Verified:** 2026-01-31T23:58:00Z
**Status:** gaps_found
**Re-verification:** No — initial verification

## Goal Achievement

### Observable Truths

| # | Truth | Status | Evidence |
|---|-------|--------|----------|
| 1 | Harness tools install successfully into Docker named volume using `npm install -g --prefix /tools/npm` | ✓ VERIFIED | `build-install-commands` generates correct npm command string with NPM_CONFIG_PREFIX; `populate-volume` runs docker container with volume mount |
| 2 | Harness volume mounts at runtime with PATH and NODE_PATH configured correctly so harness commands are executable | ✗ PARTIAL | Infrastructure exists (run.clj accepts :harness-volume-name, entrypoint configures PATH) BUT no caller provides the parameter - always nil |
| 3 | Volume names follow pattern `aishell-harness-{hash}` where hash is derived from harness flags and versions | ✓ VERIFIED | `compute-harness-hash` computes deterministic 12-char SHA-256 hash; `volume-name` returns correct pattern |
| 4 | Projects with identical harness combinations share the same volume (same hash = same volume) | ✗ FAILED | Hash computation is deterministic and order-independent BUT volume.clj namespace is NEVER imported - functions are orphaned |
| 5 | Harness volume rebuilds only when harness versions or flags change, not when foundation image changes | ✗ FAILED | Volume labels support exists (create-volume, get-volume-label) BUT no staleness detection logic implemented - no runtime checks |

**Score:** 2/5 truths fully verified, 1/5 partial (infrastructure present but unwired)

### Required Artifacts

| Artifact | Expected | Status | Details |
|----------|----------|--------|---------|
| `src/aishell/docker/volume.clj` | Harness hash computation, volume naming, population | ✓ SUBSTANTIVE | 253 lines, all functions implemented with docstrings, no stubs |
| `src/aishell/docker/run.clj` | Volume mount and env args in docker run | ✓ SUBSTANTIVE | 327 lines, build-harness-volume-args and build-harness-env-args implemented |
| `src/aishell/docker/templates.clj` | PATH/NODE_PATH in entrypoint | ✓ SUBSTANTIVE | 235 lines, entrypoint has conditional PATH setup (lines 168-171) |

### Key Link Verification

| From | To | Via | Status | Details |
|------|----|----|--------|---------|
| volume.clj | hash.clj | hash/compute-hash | ✓ WIRED | Line 75: calls hash/compute-hash for SHA-256 |
| volume.clj | docker volume create | babashka.process/shell | ✓ WIRED | Line 159: applies p/shell with docker volume create command |
| volume.clj | docker run --rm | babashka.process/shell | ✓ WIRED | Line 225-228: docker run with volume mount for npm install |
| volume.clj | build.clj | build/foundation-image-tag | ✓ WIRED | Line 227: uses build/foundation-image-tag for temporary container |
| run.clj | volume.clj | aishell.docker.volume import | ✗ NOT_WIRED | volume.clj namespace NOT imported in run.clj - functions never called |
| run.clj | build-docker-args | :harness-volume-name parameter | ✗ PARTIAL | Parameter accepted (line 291) BUT never provided by callers (lines 160-167, 261-266) |
| entrypoint | /tools/npm/bin | PATH prepend | ✓ WIRED | Lines 168-171: conditional PATH setup when directory exists |

**Critical Gap:** The volume.clj namespace is a fully-implemented, well-tested module that is NEVER IMPORTED OR USED. All functions are orphaned code.

### Requirements Coverage

| Requirement | Status | Blocking Issue |
|-------------|--------|----------------|
| HVOL-01: Harness tools installed into Docker named volume | ✓ SATISFIED | Infrastructure complete, functions work standalone |
| HVOL-02: Volume mounted at runtime with PATH/NODE_PATH | ⚠️ PARTIAL | Mount logic exists but not triggered (no volume name passed) |
| HVOL-03: Volume naming pattern | ✓ SATISFIED | Pattern correct, hash deterministic |
| HVOL-06: Volume sharing across projects | ✗ BLOCKED | Hash functions never called - no volume names generated |
| BUILD-03: Volume rebuilds only on harness changes | ✗ BLOCKED | Staleness detection not implemented |

### Anti-Patterns Found

| File | Line | Pattern | Severity | Impact |
|------|------|---------|----------|--------|
| src/aishell/run.clj | 160-167 | build-docker-args called without :harness-volume-name | 🛑 Blocker | Volume mount never happens - harness tools not accessible at runtime |
| src/aishell/run.clj | 261-266 | build-docker-args-for-exec called without :harness-volume-name | 🛑 Blocker | Exec commands also don't get volume mount |
| src/aishell/run.clj | 1-17 | volume.clj not imported | 🛑 Blocker | All volume naming/hashing/population functions are orphaned code |
| src/aishell/docker/volume.clj | N/A | Zero imports in codebase | 🛑 Blocker | Well-implemented namespace that is completely unused |

**Pattern:** This is a classic "infrastructure without integration" anti-pattern. All the pieces were built correctly in isolation but were NEVER WIRED INTO THE RUNTIME PIPELINE.

### Gaps Summary

Phase 36 delivered **infrastructure code** but NOT **goal achievement**. The code quality is excellent (no stubs, good documentation, deterministic logic), but the critical integration step was skipped.

**What exists:**
- ✓ Volume hash computation (deterministic, order-independent)
- ✓ Volume naming pattern (aishell-harness-{hash})
- ✓ NPM install command generation (correct packages, versions, permissions)
- ✓ Volume CRUD operations (create, inspect, populate)
- ✓ Docker run argument builders (volume mount, env vars)
- ✓ Entrypoint PATH configuration (conditional, safe)

**What's missing:**
- ✗ Import of volume.clj namespace in runtime code
- ✗ Call to compute-harness-hash from run.clj
- ✗ Passing volume name to build-docker-args
- ✗ Lazy population logic (check if volume exists, create if needed, populate if empty)
- ✗ Staleness detection (compare current hash to stored label)
- ✗ Volume label storage (hash, version metadata)

**Root cause:** Phase 36 was split into three sub-plans (36-01, 36-02, 36-03) that each built a piece of the system, but there was NO integration plan (36-04) to wire the pieces together. The summaries claimed "ready for Phase 37" but the actual runtime pipeline was never modified.

**Impact:** The phase goal "Harness tools installed into Docker named volumes and mounted at runtime" is NOT achieved. The summaries are misleading - they describe what was BUILT, not what was ACHIEVED. A user running `aishell shell` after this phase will NOT get volume-mounted harness tools. They'll get the same behavior as before Phase 36.

---

_Verified: 2026-01-31T23:58:00Z_
_Verifier: Claude (gsd-verifier)_
