---
phase: 12-maintenance-documentation
verified: 2026-01-19T17:15:00Z
status: passed
score: 3/3 must-haves verified
---

# Phase 12: Maintenance & Documentation Verification Report

**Phase Goal:** Add update detection and document known limitations for users
**Verified:** 2026-01-19T17:15:00Z
**Status:** passed
**Re-verification:** No - initial verification

## Goal Achievement

### Observable Truths

| # | Truth | Status | Evidence |
|---|-------|--------|----------|
| 1 | aishell warns when embedded Dockerfile differs from hash at build time | VERIFIED | `check_dockerfile_changed()` at line 469 compares hashes and calls `warn()` |
| 2 | README documents run.conf parsing limits (no escaped quotes, one value per line) | VERIFIED | "run.conf Limitations" section at lines 108-128 documents both limitations |
| 3 | README documents safe.directory behavior and host gitconfig effect | VERIFIED | "Git safe.directory" section at lines 130-148 documents behavior and impact |

**Score:** 3/3 truths verified

### Required Artifacts

| Artifact | Expected | Status | Details |
|----------|----------|--------|---------|
| `aishell:get_dockerfile_hash()` | Compute hash of embedded Dockerfile | VERIFIED | Lines 452-460, uses sha256sum with 12-char truncation |
| `aishell:get_image_dockerfile_hash()` | Read hash label from Docker image | VERIFIED | Lines 463-466, uses docker inspect with format |
| `aishell:check_dockerfile_changed()` | Compare hashes and warn if different | VERIFIED | Lines 469-488, warns with specific message |
| `aishell:do_build()` label injection | Add dockerfile hash label during build | VERIFIED | Lines 909-912, adds `--label "aishell.dockerfile.hash=$hash"` |
| `aishell:do_update()` label injection | Add dockerfile hash label during update | VERIFIED | Lines 1438-1441, same pattern as do_build |
| `aishell:main()` hash check | Call check_dockerfile_changed before run | VERIFIED | Lines 1514-1515, called after handle_extension |
| `README.md` run.conf section | Document parsing limitations | VERIFIED | Lines 108-128, "run.conf Limitations" heading |
| `README.md` safe.directory section | Document gitconfig behavior | VERIFIED | Lines 130-148, "Git safe.directory" heading |

### Key Link Verification

| From | To | Via | Status | Details |
|------|----|-----|--------|---------|
| `do_build()` | Docker image label | `--label` build arg | WIRED | Hash computed and added to build_args array |
| `do_update()` | Docker image label | `--label` build arg | WIRED | Same pattern as do_build |
| `main()` | `check_dockerfile_changed()` | Function call | WIRED | Called at line 1515 with IMAGE_TO_RUN |
| `check_dockerfile_changed()` | `get_dockerfile_hash()` | Function call | WIRED | Called at line 479 |
| `check_dockerfile_changed()` | `get_image_dockerfile_hash()` | Function call | WIRED | Called at line 473 |
| `check_dockerfile_changed()` | `warn()` | Function call | WIRED | Called at line 481 when hashes differ |

### Requirements Coverage

| Requirement | Status | Evidence |
|-------------|--------|----------|
| MAINT-01: Update check warns when Dockerfile hash differs | SATISFIED | Hash computed at build, stored as label, compared at runtime with warning |
| DOC-01: run.conf parsing limits documented | SATISFIED | README lines 108-128 document syntax and limitations |
| DOC-02: safe.directory behavior documented | SATISFIED | README lines 130-148 document behavior and host impact |

### Anti-Patterns Found

| File | Line | Pattern | Severity | Impact |
|------|------|---------|----------|--------|
| None | - | - | - | No anti-patterns found |

Scanned `aishell` for TODO/FIXME/placeholder patterns - none found in the new code.

### Human Verification Required

None required. All three success criteria are verifiable programmatically:
1. Hash comparison logic is testable via code inspection
2. Documentation content is verifiable by reading README
3. No visual, real-time, or external service behavior to verify

## Verification Details

### MAINT-01: Dockerfile Hash Detection

**Mechanism verified:**

1. **Hash computation** (`get_dockerfile_hash`, lines 452-460):
   - Creates temp directory
   - Writes Dockerfile via `write_dockerfile()`
   - Computes sha256sum with 12-char truncation
   - Cleans up temp directory
   - Returns hash string

2. **Label storage** (do_build line 912, do_update line 1441):
   - `build_args+=(--label "aishell.dockerfile.hash=$dockerfile_hash")`
   - Label is stored in Docker image metadata

3. **Runtime check** (main line 1515):
   - `check_dockerfile_changed "$IMAGE_TO_RUN"` called before container run
   - Retrieves hash from image label via `docker inspect`
   - Compares with current embedded Dockerfile hash
   - Warns if different, with specific hashes shown

4. **Backward compatibility** (line 476):
   - `[[ -z "$built_hash" ]] && return 0` - skips check for old images without label

### DOC-01: run.conf Limitations

**README section verified (lines 108-128):**

- Header: "### run.conf Limitations"
- Supported syntax documented:
  - Unquoted values
  - Double-quoted values
  - Single-quoted values
  - Comments
- Not supported documented:
  - Escaped quotes: `VAR="value with \"quotes\""` explicitly listed
  - Multi-line values: "Each assignment must be on one line"
  - Shell expansion: `$VAR` and `$(command)` not expanded
  - Continuation lines: No backslash continuation
- Workaround provided for complex values via DOCKER_ARGS

### DOC-02: safe.directory Behavior

**README section verified (lines 130-148):**

- Header: "### Git safe.directory"
- What happens documented:
  - `git config --global --add safe.directory /path`
  - Writes to `~/.gitconfig` inside container
- Host gitconfig impact documented:
  - If host gitconfig mounted via MOUNTS, entry added to host file
  - Bold emphasis on "**host's** gitconfig file"
- Why it happens:
  - Git requires safe.directory for different user ownership
  - CVE-2022-24765 reference
- How to avoid:
  - "Don't mount your host gitconfig into the container"

## Summary

All three success criteria verified against actual code:

1. **MAINT-01**: Hash detection implemented with full chain (compute -> store -> compare -> warn)
2. **DOC-01**: run.conf limitations documented including escaped quotes and multi-line restrictions
3. **DOC-02**: safe.directory behavior documented including host gitconfig impact

Phase 12 goal achieved. Ready for release.

---

*Verified: 2026-01-19T17:15:00Z*
*Verifier: Claude (gsd-verifier)*
