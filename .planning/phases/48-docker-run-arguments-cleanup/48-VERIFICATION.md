---
phase: 48-docker-run-arguments-cleanup
verified: 2026-02-06T12:50:00Z
status: passed
score: 12/12 must-haves verified
---

# Phase 48: Docker Run Arguments Cleanup Verification Report

**Phase Goal:** All tmux-related mounts and environment variables removed from container runtime
**Verified:** 2026-02-06T12:50:00Z
**Status:** passed

## Goal Achievement

### Observable Truths

| # | Truth | Status | Evidence |
|---|-------|--------|----------|
| 1 | WITH_TMUX env var is never passed to containers | ✓ VERIFIED | No WITH_TMUX in build-docker-args-internal, grep confirms zero matches |
| 2 | tmux config is never mounted into containers | ✓ VERIFIED | build-tmux-config-mount and user-mounted-tmux-config? deleted, no mounting code exists |
| 3 | Resurrect mounts are never added to docker run | ✓ VERIFIED | build-resurrect-mount deleted, no resurrect references in docker/run.clj |
| 4 | Resurrect env args are never added to docker run | ✓ VERIFIED | build-resurrect-env-args deleted, no resurrect references in docker/run.clj |
| 5 | skip-tmux parameter is renamed to skip-interactive throughout | ✓ VERIFIED | 8 skip-interactive references found, zero skip-tmux references |
| 6 | TPM and plugins are not installed in harness volume | ✓ VERIFIED | build-tpm-install-command and inject-resurrect-plugin deleted from volume.clj |
| 7 | Volume hash calculation excludes tmux state | ✓ VERIFIED | normalize-harness-config simplified, tmux-state binding removed |
| 8 | Harness volume population only installs npm packages and OpenCode binary | ✓ VERIFIED | populate-volume simplified, no TPM installation logic |
| 9 | ensure-harness-volume does not check :with-tmux | ✓ VERIFIED | :with-tmux removed from vector at run.clj:47 |
| 10 | No resurrect references remain in docker runtime code paths | ✓ VERIFIED | Zero matches in run.clj, docker/run.clj, docker/volume.clj |
| 11 | Namespace loads without errors after all deletions | ✓ VERIFIED | bb loads aishell.docker.run, aishell.docker.volume, aishell.run, aishell.cli cleanly |
| 12 | Entrypoint handles missing WITH_TMUX gracefully (no crash) | ✓ VERIFIED | All conditionals use `[ "$WITH_TMUX" = "true" ]` pattern (safe for unset) |

**Score:** 12/12 truths verified

### Required Artifacts

| Artifact | Expected | Status | Details |
|----------|----------|--------|---------|
| `src/aishell/docker/run.clj` | Docker run argument construction without tmux | ✓ VERIFIED | 363 lines, substantive, contains skip-interactive, no tmux references |
| `src/aishell/run.clj` | Launcher using skip-interactive parameter | ✓ VERIFIED | 322 lines, substantive, skip-interactive at line 201, no with-tmux |
| `src/aishell/docker/volume.clj` | Volume management without tmux plugin installation | ✓ VERIFIED | 329 lines, substantive, contains normalize-harness-config, no tmux/tpm/resurrect |

**Artifact Details:**

**docker/run.clj (Level 1-3 checks):**
- EXISTS: ✓ (363 lines)
- SUBSTANTIVE: ✓ (no stubs, exports present, adequate length)
- WIRED: ✓ (imported by run.clj, build-docker-args called at line 194)

**run.clj (Level 1-3 checks):**
- EXISTS: ✓ (322 lines)
- SUBSTANTIVE: ✓ (no stubs, exports present, adequate length)
- WIRED: ✓ (imported by cli.clj, functions used throughout)

**volume.clj (Level 1-3 checks):**
- EXISTS: ✓ (329 lines)
- SUBSTANTIVE: ✓ (no stubs, exports present, adequate length)
- WIRED: ✓ (imported by run.clj, compute-harness-hash called at line 48, populate-volume at line 57)

### Key Link Verification

| From | To | Via | Status | Details |
|------|-----|-----|--------|---------|
| run.clj | docker/run.clj | build-docker-args call with skip-interactive | ✓ WIRED | Line 194-204 calls build-docker-args with :skip-interactive parameter |
| docker/run.clj | templates.clj | WITH_TMUX env var removed (safe conditionals) | ✓ VERIFIED | WITH_TMUX not passed, entrypoint uses safe `[ "$WITH_TMUX" = "true" ]` pattern |
| run.clj | docker/volume.clj | compute-harness-hash and populate-volume calls | ✓ WIRED | Lines 48, 57 call volume functions without tmux state |

**Link Pattern Analysis:**

1. **skip-interactive parameter flow:**
   - run.clj:201 sets `:skip-interactive (= cmd "gitleaks")`
   - docker/run.clj:321 receives in build-docker-args destructuring
   - docker/run.clj:274 uses in cond-> for harness aliases
   - ✓ Full chain verified

2. **Volume hash calculation:**
   - run.clj:48 calls `vol/compute-harness-hash state`
   - volume.clj:71-75 implements computation
   - volume.clj:44-51 normalize-harness-config excludes tmux
   - ✓ Tmux state excluded from hash

3. **Entrypoint safety:**
   - docker/run.clj no longer passes WITH_TMUX env var
   - templates.clj:219,231,267,289 use `[ "$WITH_TMUX" = "true" ]`
   - Bash string comparison returns false for unset variables
   - ✓ No crash when WITH_TMUX absent

### Requirements Coverage

Phase 48 covers requirements TMUX-04, TMUX-05, TMUX-06, TMUX-07, TMUX-09:

| Requirement | Status | Evidence |
|-------------|--------|----------|
| TMUX-04: WITH_TMUX env var removed from docker run | ✓ SATISFIED | Zero matches for WITH_TMUX in docker/run.clj, no cond-> blocks passing it |
| TMUX-05: tmux config mounting removed | ✓ SATISFIED | build-tmux-config-mount and user-mounted-tmux-config? deleted |
| TMUX-06: TPM and plugin installation removed from volume | ✓ SATISFIED | build-tpm-install-command and inject-resurrect-plugin deleted |
| TMUX-07: Volume hash excludes tmux state | ✓ SATISFIED | normalize-harness-config simplified, tmux-state removed |
| TMUX-09: Resurrect mounts removed from docker run | ✓ SATISFIED | build-resurrect-mount and build-resurrect-env-args deleted, zero resurrect references |

**All phase 48 requirements satisfied.**

### Anti-Patterns Found

No anti-patterns detected. All deletions were clean:

- ✓ No TODO/FIXME comments introduced
- ✓ No placeholder content
- ✓ No empty return statements
- ✓ No console.log-only implementations
- ✓ All function deletions accompanied by caller updates

**Clean refactoring with no technical debt.**

### Code Quality Verification

**Namespace loading:**
```bash
bb -e "(require 'aishell.docker.run)"    # ✓ Clean
bb -e "(require 'aishell.docker.volume)" # ✓ Clean
bb -e "(require 'aishell.run)"           # ✓ Clean
bb -e "(require 'aishell.cli)"           # ✓ Clean
```

**Grep verification results:**
- `grep -rn "skip-tmux" src/` → NO_MATCHES (✓)
- `grep -rn "WITH_TMUX" src/aishell/docker/run.clj` → NO_MATCHES (✓)
- `grep -rn "tmux\|tpm\|resurrect" src/aishell/docker/run.clj` → NO_MATCHES (✓)
- `grep -rn "tmux\|tpm\|resurrect" src/aishell/docker/volume.clj` → NO_MATCHES (✓)
- `grep -rn "with-tmux" src/aishell/run.clj` → NO_MATCHES (✓)
- `grep -rn "resurrect" src/aishell/docker/run.clj src/aishell/docker/volume.clj src/aishell/run.clj` → NO_MATCHES (✓)

**Commit verification:**
- 37d1ff6: refactor(48-01): remove tmux functions and rename skip-tmux to skip-interactive (✓)
- db06d65: refactor(48-01): rename skip-tmux to skip-interactive in run.clj (✓)
- 5ea4459: refactor(48-02): remove tmux functions and simplify volume management (✓)
- 1f62f39: refactor(48-02): remove :with-tmux from ensure-harness-volume check (✓)

All 4 commits exist and match expected work.

### Verification Methodology

**Level 1 (Existence):** Confirmed all 3 files exist with expected line counts
**Level 2 (Substantive):** Verified no stub patterns, adequate length, real exports
**Level 3 (Wired):** Traced function calls between files, confirmed usage

**Pattern checks:**
- Function deletion safety: Grepped for all callers before accepting deletions as complete
- Parameter rename completeness: Verified skip-tmux → skip-interactive across all references
- Entrypoint safety: Confirmed bash string comparison pattern handles unset variables

**Babashka/SCI safety:**
Per project memory lesson: Verified zero callers for deleted functions using grep across entire src/ directory. All deletions accompanied by caller updates in same plan/commit.

---

## Summary

**Phase 48 goal ACHIEVED.** All tmux-related mounts and environment variables successfully removed from container runtime.

### What Changed

**Plan 48-01 (docker/run.clj cleanup):**
- Deleted 4 tmux functions: user-mounted-tmux-config?, build-tmux-config-mount, build-resurrect-mount, build-resurrect-env-args
- Removed 4 tmux cond-> blocks from build-docker-args-internal
- Renamed skip-tmux to skip-interactive throughout (8 references)
- Updated caller in run.clj

**Plan 48-02 (volume.clj cleanup):**
- Deleted 2 tmux functions: inject-resurrect-plugin, build-tpm-install-command
- Simplified normalize-harness-config (removed tmux-state from hash)
- Simplified populate-volume (removed TPM installation)
- Removed :with-tmux from ensure-harness-volume check

### Impact

**Container runtime:**
- No WITH_TMUX env var passed
- No tmux config mounts (/tmp/host-tmux.conf, runtime config)
- No resurrect mounts or env args
- Entrypoint conditionals safely skip when WITH_TMUX unset

**Volume management:**
- Hash calculation excludes tmux state (backward compatible)
- Volume population only installs npm packages + OpenCode binary
- No TPM or plugin installation

**Code quality:**
- All namespaces load cleanly in Babashka
- Zero tmux/resurrect references in runtime code paths (run.clj, docker/run.clj, docker/volume.clj)
- skip-interactive parameter clarifies purpose (controls interactive features, not just tmux)

### Next Phase Readiness

**Ready for Phase 49 (Entrypoint Simplification):**
- WITH_TMUX no longer passed by docker run
- Entrypoint conditionals can be removed (currently safe but dead code)
- No tmux state in volume or config system

**No blockers. No regressions.**

---

_Verified: 2026-02-06T12:50:00Z_
_Verifier: Claude (gsd-verifier)_
