---
status: diagnosed
phase: 11-code-hardening
source: 11-01-SUMMARY.md, 11-02-SUMMARY.md
started: 2026-01-19T17:05:00Z
updated: 2026-01-19T17:28:00Z
---

## Current Test

[testing complete]

## Tests

### 1. Ctrl+C During Build Cleanup
expected: Start a build operation, press Ctrl+C during build. Script exits cleanly without orphaned processes or temp files. Spinner stops and temp files are removed.
result: pass

### 2. Port Mapping with IP Binding
expected: Configure a port with IP binding in run.conf (e.g., PORTS="127.0.0.1:8080:80") and run aishell. Docker command should include the IP-bound port mapping (-p 127.0.0.1:8080:80).
result: pass

### 3. Invalid Version Rejection
expected: Try to build or update with an invalid version containing shell metacharacters (e.g., `./aishell build --version "1.0.0; echo pwned"`). The script should reject it with an error before any npm/curl operations.
result: issue
reported: "failed. See: ./aishell build --version \"1.0.0; echo pwned\" - Building image with: Base image only (shell access) - Build complete. Image: aishell:base"
severity: major

### 4. Missing HOME Handling
expected: Run aishell with HOME unset or invalid (e.g., `HOME= ./aishell` or `HOME=/nonexistent ./aishell`). Script should handle this gracefully with fallback behavior rather than crashing.
result: pass

### 5. Zombie Process Reaping
expected: Run a container that creates zombie processes. The container should automatically reap zombies due to the --init flag (tini init process). Check with `docker inspect` or ps inside container.
result: pass

### 6. Dangerous DOCKER_ARGS Warning
expected: Set DOCKER_ARGS="--privileged" or DOCKER_ARGS="-v /var/run/docker.sock:/var/run/docker.sock" in run.conf and start aishell. A warning should be printed to stderr about the security implications.
result: pass

## Summary

total: 6
passed: 5
issues: 1
pending: 0
skipped: 0

## Gaps

- truth: "Invalid version strings with shell metacharacters are rejected before reaching npm/curl"
  status: failed
  reason: "User reported: Script accepted malicious version '1.0.0; echo pwned' and completed build instead of rejecting it"
  severity: major
  test: 3
  root_cause: "do_build() and do_update() case statements lack default case - unknown args like '--version' (without =) are silently ignored, so validate_version() is never called"
  artifacts:
    - path: "aishell"
      issue: "do_build() case statement (lines 837-873) missing default case"
    - path: "aishell"
      issue: "do_update() case statement (lines 1348-1382) missing default case"
  missing:
    - "Add *) default case to do_build() that errors on unknown options"
    - "Add *) default case to do_update() that errors on unknown options"
  debug_session: ".planning/debug/version-validation-bypass.md"
