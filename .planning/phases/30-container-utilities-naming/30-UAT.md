---
status: complete
phase: 30-container-utilities-naming
source: 30-01-SUMMARY.md
started: 2026-01-31T13:00:00Z
updated: 2026-01-31T13:10:00Z
---

## Current Test

[testing complete]

## Tests

### 1. Deterministic Container Naming
expected: Evaluating `(naming/container-name "/some/path" "claude")` twice returns identical result in format `aishell-XXXXXXXX-claude`. A different path produces a different hash.
result: pass

### 2. --name Flag Override
expected: Running `aishell --name reviewer claude` extracts the `--name reviewer` flag and passes `reviewer` as the container name override (not passed through to the harness). The flag works in any position: `aishell claude --name reviewer` also works.
result: pass

### 3. Name Validation Rejects Invalid Names
expected: Calling `validate-container-name!` with invalid input (e.g., name starting with `-` or containing `$`) exits with a clear error message explaining the naming rules.
result: pass

### 4. Name Length Validation
expected: Calling `validate-container-name!` with a name longer than 46 characters exits with an error explaining the 63-character Docker limit.
result: pass

### 5. Path Canonicalization
expected: `(naming/project-hash ".")` and `(naming/project-hash "/home/jonasrodrigues/projects/harness")` (or whatever the canonical path is) return the same hash, proving symlinks and relative paths resolve identically.
result: pass

### 6. Default Container Name for Shell Mode
expected: Running `aishell` (no command) passes `nil` harness with no container-name override. The default name for shell mode should be "shell" (as documented in decisions).
result: pass

## Summary

total: 6
passed: 6
issues: 0
pending: 0
skipped: 0

## Gaps

[none yet]
