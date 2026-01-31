---
phase: 30-container-utilities-naming
verified: 2026-01-31T13:15:00Z
status: passed
score: 7/7 must-haves verified
---

# Phase 30: Container Utilities & Naming Verification Report

**Phase Goal:** Provide foundation utilities for container naming and Docker queries that all subsequent phases depend on.

**Verified:** 2026-01-31T13:15:00Z

**Status:** passed

**Re-verification:** No — initial verification

## Goal Achievement

### Observable Truths

| # | Truth | Status | Evidence |
|---|-------|--------|----------|
| 1 | Container name generation produces deterministic `aishell-{project-hash}-{name}` format where project-hash is first 8 chars of SHA-256 | ✓ VERIFIED | `container-name "." "claude"` → `aishell-09b6d7f0-claude`. Pattern matches `^aishell-[0-9a-f]{8}-.+$`. Tested with bb REPL. |
| 2 | Same project directory always produces same hash regardless of path form (symlinks, trailing slashes) | ✓ VERIFIED | Uses `fs/canonicalize` at line 20. Tested: relative path "." and absolute path produce identical hash `09b6d7f0`. Determinism verified by calling `project-hash` twice. |
| 3 | Docker query functions can check if container exists, is running, and filter containers by project hash | ✓ VERIFIED | `container-exists?`, `container-running?`, `list-project-containers` all exist, handle errors gracefully (return false/empty on error), tested with non-existent container names. All use try/catch and return safe defaults. |
| 4 | Default container name equals the harness name (claude, opencode, codex, gemini) | ✓ VERIFIED | run.clj line 81: `(or (:container-name opts) cmd "shell")`. Tested all harnesses produce correct names: claude→aishell-09b6d7f0-claude, opencode→aishell-09b6d7f0-opencode, etc. |
| 5 | User can pass --name flag to override container name on harness commands | ✓ VERIFIED | CLI extracts `--name VALUE` at cli.clj line 281-288. Passed to run-container as `:container-name` in opts map (lines 295-307). Wired through all harness dispatch cases. |
| 6 | Name length validated against 63-character Docker limit | ✓ VERIFIED | `validate-container-name!` at naming.clj line 37-39 checks name portion ≤46 chars (full name = 17 prefix chars + name). Tested: 47-char name rejected. Docker limit documented in validation error message. |
| 7 | Hash collision probability documented and validated (expect <0.02% at 100 projects) | ✓ VERIFIED | Docstring at naming.clj line 15-18 documents collision math. Birthday paradox formula verified: at 100 projects = 0.000116% (<0.02% ✓), at 1000 projects = 0.0116% (~0.01% ✓). Math is correct. |

**Score:** 7/7 truths verified

### Required Artifacts

| Artifact | Expected | Status | Details |
|----------|----------|--------|---------|
| `src/aishell/docker/naming.clj` | Container naming utilities and Docker query functions | ✓ VERIFIED | **Exists:** 128 lines. **Substantive:** All 8 functions fully implemented with real logic (project-hash, validate-container-name!, container-name, container-exists?, container-running?, remove-container-if-stopped!, ensure-name-available!, list-project-containers). No TODOs/FIXMEs/placeholders. **Wired:** Imported by cli.clj (line 7) and run.clj (line 6), used in dispatch and run-container. |
| `src/aishell/docker/hash.clj` | Updated compute-hash to accept optional length parameter | ⚠️ PARTIAL | **Exists:** Yes, compute-hash defined. **Substantive:** Yes, functional. **Wiring Note:** Plan specifies "optional length parameter" but implementation uses existing 12-char output with subs in naming.clj (line 21: `subs (hash/compute-hash canonical) 0 8`). This works correctly but deviates from plan's "updated compute-hash" description. **Impact:** None — hash.clj was NOT modified (correctly, as plan Task 1 notes), project-hash achieves 8-char output via subs. Status: Artifact provides required functionality but via different pattern than plan implied. |
| `src/aishell/cli.clj` | --name flag extraction and pass-through | ✓ VERIFIED | **Modified:** Yes (per SUMMARY). **Substantive:** Lines 279-288 extract --name VALUE, lines 295-307 pass :container-name to all harness dispatch cases. **Wired:** Calls run/run-container with container-name-override. No stubs. |
| `src/aishell/run.clj` | Container name resolution in run-container | ✓ VERIFIED | **Modified:** Yes (per SUMMARY). **Substantive:** Lines 81-82 compute container-name-str using naming/container-name, line 85 logs for verbose. **Wired:** Uses naming/container-name (line 82). Currently only logs name (not passed to docker yet — Phase 32 responsibility per plan). |
| `src/aishell/output.clj` | Added "attach" and "ps" to known-commands | ✓ VERIFIED | **Modified:** Yes (per SUMMARY). **Substantive:** Line 19 defines known-commands with "attach" and "ps". **Wired:** Used by suggest-command (line 51) for command suggestions. |

### Key Link Verification

| From | To | Via | Status | Details |
|------|-----|-----|--------|---------|
| naming.clj | hash.clj | hash/compute-hash | ✓ WIRED | Line 21: `(subs (hash/compute-hash canonical) 0 8)`. Call exists and result is used (substring extraction). |
| naming.clj | babashka.fs | fs/canonicalize | ✓ WIRED | Line 20: `(str (fs/canonicalize project-dir))`. Call exists, result used in hash computation. |
| run.clj | naming.clj | naming/container-name | ✓ WIRED | Line 82: `(naming/container-name project-dir name-part)`. Call exists, result assigned to container-name-str and logged. |
| cli.clj | run.clj | :container-name in opts | ✓ WIRED | Lines 295-307 pass `{:unsafe unsafe? :container-name container-name-override}` to run/run-container. All harness cases updated. Value extracted from args (lines 281-288). |

### Requirements Coverage

| Requirement | Status | Evidence |
|-------------|--------|----------|
| **NAME-01:** Container name format `aishell-{8-char-hash}-{name}` where hash is SHA-256 | ✓ SATISFIED | container-name function generates correct format. Tested: `aishell-09b6d7f0-claude`. Hash verified as 8 hex chars. SHA-256 via hash/compute-hash. |
| **NAME-02:** Default container name equals harness name | ✓ SATISFIED | run.clj line 81 uses `(or (:container-name opts) cmd "shell")`. All harnesses tested produce correct names (claude→claude, opencode→opencode, etc.). |
| **NAME-03:** User can override with `--name <name>` flag | ✓ SATISFIED | CLI extracts --name VALUE (cli.clj 281-288), passes to run-container, used in naming/container-name. End-to-end wiring verified. |

### Anti-Patterns Found

| File | Line | Pattern | Severity | Impact |
|------|------|---------|----------|--------|
| N/A | N/A | No anti-patterns found | N/A | No TODOs, FIXMEs, placeholders, empty returns, or console.log-only implementations detected. |

### Deviations from Plan

**1. hash.clj not modified (deviation from plan artifact description)**

- **Plan stated:** "Updated compute-hash to accept optional length parameter"
- **Actual implementation:** hash.clj unchanged. naming.clj uses `(subs (hash/compute-hash canonical) 0 8)` to achieve 8-char hash.
- **Rationale:** Plan Task 1 action correctly noted "Do NOT modify `hash/compute-hash` signature" — the artifact description was imprecise.
- **Impact:** None. Functionality achieved correctly. 8-char hash works as expected.
- **Assessment:** This is a documentation issue in plan frontmatter, not an implementation gap.

**2. Container name not yet passed to docker run --name (expected)**

- **Current state:** run.clj computes container-name-str but only logs it (line 85: verbose output).
- **Plan expectation:** "Do NOT add `--name` to the docker run args vector yet. Phase 32 handles that."
- **Assessment:** Correct according to plan. This phase establishes naming utilities; Phase 32 wires --name into docker run.

## Summary

**All 7 must-have truths verified.** Phase 30 successfully delivers:

1. **Deterministic 8-char SHA-256 hashing** with collision probability <0.02% at 100 projects (mathematically verified)
2. **Path normalization** via fs/canonicalize ensures symlinks/relative paths produce identical hashes
3. **8 Docker utility functions** all substantive, error-safe, and wired correctly
4. **Default naming convention** harness name = container name (intuitive UX)
5. **--name flag support** extracted in CLI, passed through to run-container
6. **63-char Docker limit enforcement** with clear validation errors
7. **Foundation for Phases 31-34** all dependencies satisfied

**No blockers.** No gaps. No stubs. No human verification required.

**Next phase readiness:**
- Phase 31 (Detached Mode): ✓ Ready — naming utilities available
- Phase 32 (Attach): ✓ Ready — container-name resolution available
- Phase 33 (PS): ✓ Ready — list-project-containers available
- Phase 34 (Cleanup): ✓ Ready — Docker query functions available

---

*Verified: 2026-01-31T13:15:00Z*  
*Verifier: Claude (gsd-verifier)*
