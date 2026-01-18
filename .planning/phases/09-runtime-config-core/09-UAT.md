---
status: diagnosed
phase: 09-runtime-config-core
source: 09-01-SUMMARY.md, 09-02-SUMMARY.md
started: 2026-01-18T21:30:00Z
updated: 2026-01-18T21:40:00Z
---

## Current Test

[Phase 9 testing complete]

## Tests

### 1. Config File Created and Recognized
expected: Create `.aishell/run.conf` in a project directory. Running `aishell -v` shows "Loading runtime config" message indicating the file was found and parsed.
result: pass

### 2. Invalid Config Variable Rejected
expected: Adding an unknown variable like `INVALID_VAR="test"` to run.conf produces a clear error message showing the line number, the offending line, and the list of allowed variables (MOUNTS, ENV, PORTS, DOCKER_ARGS, PRE_START).
result: issue
reported: "Partial. We have a return error code and the container does not start. But we don't have error details. Only shows 'Loading runtime config: /path/to/run.conf' then exits."
severity: major

### 3. MOUNTS with Home Expansion
expected: Setting `MOUNTS="$HOME/.config:/home/user/.config"` or `MOUNTS="~/.config:/home/user/.config"` in run.conf results in the host path being expanded and mounted into the container. Check with `docker inspect` or by accessing the mount inside the container.
result: issue
reported: "FAILED. run.conf with exactly MOUNTS=\"$HOME/.config:/home/user/.config\" does not start. Container won't run. Return code 1 but no error messages."
severity: blocker

### 4. Multiple MOUNTS
expected: Setting `MOUNTS="/path/one:/mount1 /path/two:/mount2"` (space-separated) results in both paths being mounted into the container.
result: skipped
reason: MOUNTS already broken in test 3

### 5. ENV Passthrough
expected: Setting `ENV="MY_VAR"` in run.conf (no value) passes through the host's MY_VAR environment variable to the container. Set `MY_VAR=test` on host, run aishell, verify `echo $MY_VAR` inside container shows "test".
result: issue
reported: "Same issue, container doesn't start"
severity: blocker

### 6. ENV Literal Value
expected: Setting `ENV="MY_VAR=explicit_value"` in run.conf sets the variable to that literal value inside the container, regardless of host value. Verify `echo $MY_VAR` inside container shows "explicit_value".
result: skipped
reason: Config parsing broken - same root cause as tests 3, 5

### 7. PORTS Mapping
expected: Setting `PORTS="8080:80 3000:3000"` in run.conf exposes those ports from the container. Verify with `docker ps` showing the port mappings or by accessing a service on those ports.
result: skipped
reason: Config parsing broken - same root cause as tests 3, 5

### 8. DOCKER_ARGS Passthrough
expected: Setting `DOCKER_ARGS="--memory 512m --cpus 1"` in run.conf passes those flags to docker run. Verify with `docker inspect` showing memory/cpu limits applied.
result: skipped
reason: Config parsing broken - same root cause as tests 3, 5

### 9. Help Shows Runtime Configuration
expected: Running `aishell --help` or `aishell -h` shows a "Runtime Configuration" section documenting the run.conf file and available options.
result: pass

## Summary

total: 9
passed: 2
issues: 3
pending: 0
skipped: 4

## Gaps

- truth: "Invalid config variable produces clear error message with line number and allowed variables"
  status: failed
  reason: "User reported: Partial. We have a return error code and the container does not start. But we don't have error details. Only shows 'Loading runtime config: /path/to/run.conf' then exits."
  severity: major
  test: 2
  root_cause: "parse_run_conf uses exit 1 on error which terminates script, but error messages to stderr may not be displayed due to buffering or terminal configuration. Also uses echo -e which may have portability issues."
  artifacts:
    - path: "aishell"
      issue: "Line 468 uses exit 1, lines 463-467 use echo -e for error output"
  missing:
    - "Use error() function instead of direct echo/exit for consistency"
    - "Ensure error messages are flushed to stderr before exit"
  debug_session: ""

- truth: "MOUNTS with $HOME expansion works and container starts"
  status: failed
  reason: "User reported: FAILED. run.conf with exactly MOUNTS=\"$HOME/.config:/home/user/.config\" does not start. Container won't run. Return code 1 but no error messages."
  severity: blocker
  test: 3
  root_cause: "build_mount_args expects source-only paths (mounted at same path in container), but user used source:destination format. With source:destination input, function produces malformed docker -v arguments with triple colons."
  artifacts:
    - path: "aishell"
      issue: "Lines 505-506 use $expanded:$expanded format, assuming input is source-only"
    - path: "aishell"
      issue: "Line 849 documentation shows source-only format, but test expected source:destination"
  missing:
    - "Either: Support source:destination format in build_mount_args"
    - "Or: Validate and error on colons in mount input with clear message"
    - "Or: Update tests to match actual format (source-only)"
  debug_session: ""

- truth: "ENV passthrough works and container starts"
  status: failed
  reason: "User reported: Same issue, container doesn't start"
  severity: blocker
  test: 5
  root_cause: "Same root cause as test 3 - if ENV config has issues, similar silent failure occurs. Need to verify ENV parsing works with simple passthrough case."
  artifacts:
    - path: "aishell"
      issue: "Cascading failure from MOUNTS test - need isolated ENV test"
  missing:
    - "Test ENV independently without MOUNTS"
    - "Verify parse_run_conf accepts ENV-only config"
  debug_session: ""
