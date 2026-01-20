---
status: complete
phase: 15-build-command
source: [15-01-SUMMARY.md, 15-02-SUMMARY.md]
started: 2026-01-20T19:45:00Z
updated: 2026-01-20T20:30:00Z
---

## Current Test

[testing complete]

## Tests

### 1. Build help displays correctly
expected: Running `./aishell.clj build --help` shows build command description and available flags including --with-claude, --with-opencode.
result: pass

### 2. Build with invalid semver rejected
expected: Running `./aishell.clj build --with-claude=invalid` shows clear error message about invalid version format.
result: pass

### 3. Build with dangerous characters rejected
expected: Running `./aishell.clj build --with-claude="1.0; rm -rf /"` shows clear error message rejecting the dangerous input.
result: pass
note: Re-verified after 15-03 fix - shows "Invalid Claude Code version format" instead of Java cast exception

### 4. Build base image (requires Docker)
expected: Running `./aishell.clj build` starts building Docker image with spinner, shows completion message.
result: pass
note: Re-verified after 15-03 fix - build completes successfully, image visible in `docker images`

### 5. Build with Claude version pinning (requires Docker)
expected: Running `./aishell.clj build --with-claude=2.0.22` builds image and shows "Claude Code: 2.0.22" in output.
result: pass

### 6. State persistence after build (requires Docker)
expected: After successful build with --with-claude, cat ~/.aishell/state.edn shows :with-claude true and version if specified.
result: pass
note: Fixed by using $HOME env var instead of fs/home (network login compatibility)

### 7. Build with no flags clears state (requires Docker)
expected: After previous build with --with-claude, running `./aishell.clj build` without flags clears state (state.edn shows :with-claude false).
result: pass

## Summary

total: 7
passed: 7
issues: 0
pending: 0
skipped: 0

## Gaps

[none]
