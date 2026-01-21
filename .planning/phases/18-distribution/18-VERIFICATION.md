---
phase: 18-distribution
verified: 2026-01-21T20:31:01Z
status: passed
score: 3/3 must-haves verified
---

# Phase 18: Distribution Verification Report

**Phase Goal:** Users can install aishell via curl|bash one-liner
**Verified:** 2026-01-21T20:31:01Z
**Status:** passed
**Re-verification:** No - initial verification

## Goal Achievement

### Observable Truths

| # | Truth | Status | Evidence |
|---|-------|--------|----------|
| 1 | User can run `curl -fsSL ... \| bash` to install aishell | VERIFIED | install.sh exists (157 lines), has function wrapper, correct structure, downloads from GitHub Releases URLs |
| 2 | aishell is distributed as single-file uberscript | VERIFIED | dist/aishell exists (1649 lines, 60KB), has `#!/usr/bin/env bb` shebang, bundles 15 namespaces |
| 3 | Installer assumes Babashka installed (clear error if missing) | VERIFIED | install.sh line 53-55: checks `command -v bb`, outputs "Babashka required. Install: https://babashka.org" |

**Score:** 3/3 truths verified

### Required Artifacts

| Artifact | Expected | Status | Details |
|----------|----------|--------|---------|
| `dist/aishell` | Executable uberscript with shebang | VERIFIED | 1649 lines, `#!/usr/bin/env bb` shebang, 15 namespaces bundled |
| `dist/aishell.sha256` | Checksum file | VERIFIED | SHA256 hash matches actual file (502c63cd...) |
| `install.sh` | curl\|bash installer with function wrapper | VERIFIED | 157 lines, `install_aishell()` wrapper, call on last line |
| `scripts/build-release.sh` | Build automation script | VERIFIED | 55 lines, creates uberscript with `bb uberscript -m aishell.core` |
| `src/aishell/core.clj` | Clean entry point with static requires | VERIFIED | 11 lines, static `(:require [aishell.cli :as cli])`, no dynamic require |
| `src/aishell/cli.clj` | Version and print functions | VERIFIED | Contains `def version "2.0.0"`, `print-version`, `print-version-json` |
| `README.md` | v2.0 documentation | VERIFIED | Documents Babashka requirement, config.yaml, curl\|bash install |
| `CHANGELOG.md` | v2.0 release notes | VERIFIED | [2.0.0] entry with BREAKING CHANGES section |

### Key Link Verification

| From | To | Via | Status | Details |
|------|----|-----|--------|---------|
| scripts/build-release.sh | src/aishell/core.clj | `bb uberscript -m aishell.core` | WIRED | Line 26: `bb uberscript "$OUTPUT_FILE" -m aishell.core` |
| src/aishell/core.clj | src/aishell/cli.clj | static require | WIRED | Line 2: `(:require [aishell.cli :as cli]` |
| install.sh | GitHub Releases | curl download | WIRED | Lines 78,80: `${repo_url}/releases/latest/download/aishell` |
| install.sh | ~/.local/bin/aishell | file copy and chmod | WIRED | Line 131: `chmod +x "${install_dir}/aishell"` |
| README.md | install.sh | curl\|bash command | WIRED | Line 29: `curl -fsSL .../install.sh \| bash` |

### Uberscript Execution Verification

| Test | Expected | Actual | Status |
|------|----------|--------|--------|
| `./dist/aishell --version` | "aishell 2.0.0" | "aishell 2.0.0" | PASS |
| `./dist/aishell --help` | Help text with commands | Shows Usage, Commands, Examples | PASS |
| `head -1 dist/aishell` | `#!/usr/bin/env bb` | `#!/usr/bin/env bb` | PASS |
| Namespace count | 15+ | 15 | PASS |
| Checksum match | SHA256 matches .sha256 file | Both: 502c63cd678cb655fc3e8f62840f3558222f62ff1e25495b80864f5287465e2b | PASS |

### Installer Verification

| Check | Expected | Actual | Status |
|-------|----------|--------|--------|
| Syntax validation | `bash -n` passes | Syntax OK | PASS |
| Function wrapper | `install_aishell()` defined | Found | PASS |
| Function call at end | Last line calls function | `install_aishell "$@"` | PASS |
| Babashka check | `command -v bb` with error | Line 53-55 | PASS |
| Download URL | GitHub Releases pattern | `${repo_url}/releases/latest/download/aishell` | PASS |
| Checksum verification | sha256sum/shasum logic | Lines 114-118 | PASS |
| PATH warning | Warns if ~/.local/bin not in PATH | Line 141 | PASS |
| Quick start | Shows aishell commands | Line 149-152 | PASS |

### Documentation Verification

| Check | Expected | Status |
|-------|----------|--------|
| README mentions Babashka | Yes | VERIFIED (line 20, 24, 146) |
| README uses config.yaml | Yes | VERIFIED (line 13, 94, 124) |
| README has no run.conf | None | VERIFIED (no matches) |
| CHANGELOG has v2.0.0 | Yes | VERIFIED (line 8) |
| CHANGELOG has BREAKING CHANGES | Yes | VERIFIED (line 10) |

### Circular Dependency Elimination

| Check | Expected | Status |
|-------|----------|--------|
| core.clj uses static require for cli | `:require [aishell.cli :as cli]` | VERIFIED |
| core.clj has no dynamic require | No `require '[aishell.cli` | VERIFIED |
| cli.clj does not require core | No `aishell.core` reference | VERIFIED |

### Legacy Cleanup

| Check | Expected | Status |
|-------|----------|--------|
| Legacy `aishell` bash script removed | File does not exist at repo root | VERIFIED |

### Anti-Patterns Found

| File | Line | Pattern | Severity | Impact |
|------|------|---------|----------|--------|
| (none) | - | - | - | No anti-patterns found |

### Human Verification Required

None required. All automated checks pass.

## Summary

Phase 18 Distribution goal achieved. All three success criteria verified:

1. **curl\|bash installer works**: install.sh exists with proper structure (function wrapper for partial download protection), downloads from GitHub Releases, verifies SHA256 checksum, checks for Babashka dependency with clear error message.

2. **Single-file uberscript**: dist/aishell is a 1649-line executable Clojure file with `#!/usr/bin/env bb` shebang, bundles all 15 namespaces, executes `--version` and `--help` correctly.

3. **Babashka dependency check**: install.sh lines 53-55 check for `bb` command and output clear error with install URL if missing.

Supporting artifacts verified:
- build-release.sh automates uberscript creation with checksum
- Circular dependency eliminated (core uses static require for cli, cli doesn't require core)
- README updated for v2.0 (Babashka requirement, config.yaml format)
- CHANGELOG has v2.0.0 entry with BREAKING CHANGES and migration guide
- Legacy bash aishell removed from repo root

---

*Verified: 2026-01-21T20:31:01Z*
*Verifier: Claude (gsd-verifier)*
