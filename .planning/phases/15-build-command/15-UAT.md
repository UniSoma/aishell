---
status: diagnosed
phase: 15-build-command
source: [15-01-SUMMARY.md, 15-02-SUMMARY.md]
started: 2026-01-20T19:45:00Z
updated: 2026-01-20T19:55:00Z
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
result: issue
reported: "I would not qualify this as a clear error message: Error: Unexpected error: java.lang.Double cannot be cast to java.lang.CharSequence"
severity: major

### 4. Build base image (requires Docker)
expected: Running `./aishell.clj build` starts building Docker image with spinner, shows completion message.
result: issue
reported: "./aishell.clj build shows: Error: Unexpected error: Cannot run program \"[docker\" (in directory \"/tmp/aishell-build-8474181630085602333\"): Exec failed, error: 2 (No such file or directory)"
severity: blocker

### 5. Build with Claude version pinning (requires Docker)
expected: Running `./aishell.clj build --with-claude=2.0.22` builds image and shows "Claude Code: 2.0.22" in output.
result: skipped
reason: Blocked by Test 4 build command failure

### 6. State persistence after build (requires Docker)
expected: After successful build with --with-claude, cat ~/.aishell/state.edn shows :with-claude true and version if specified.
result: skipped
reason: Blocked by Test 4 build command failure

### 7. Build with no flags clears state (requires Docker)
expected: After previous build with --with-claude, running `./aishell.clj build` without flags clears state (state.edn shows :with-claude false).
result: skipped
reason: Blocked by Test 4 build command failure

## Summary

total: 7
passed: 2
issues: 2
pending: 0
skipped: 3

## Gaps

- truth: "Build with dangerous characters shows clear error message"
  status: failed
  reason: "User reported: Error shows Java exception (java.lang.Double cannot be cast to java.lang.CharSequence) instead of user-friendly message"
  severity: major
  test: 3
  root_cause: "babashka.cli auto-coerces numeric-looking values to Double; parse-with-flag passes Double to validate-version which calls re-find expecting String"
  artifacts:
    - path: "src/aishell/cli.clj"
      issue: "parse-with-flag doesn't convert values to string; validate-version receives Double"
  missing:
    - "Add (str value) in parse-with-flag :else clause to ensure version is always a string"
  debug_session: ".planning/debug/double-cast-charsequence.md"

- truth: "Build command executes Docker build successfully"
  status: failed
  reason: "User reported: Cannot run program \"[docker\" - command array being stringified incorrectly"
  severity: blocker
  test: 4
  root_cause: "run-build passes vector cmd directly to p/shell instead of using apply to spread arguments"
  artifacts:
    - path: "src/aishell/docker/build.clj"
      issue: "run-build lines 79-82 and 85-89 pass cmd vector to p/process and p/shell without apply"
  missing:
    - "Use (apply p/process opts cmd) and (apply p/shell opts cmd) to spread vector arguments"
  debug_session: ".planning/debug/docker-command-vector.md"
