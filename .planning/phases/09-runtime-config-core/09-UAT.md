---
status: complete
phase: 09-runtime-config-core
source: 09-01-SUMMARY.md, 09-02-SUMMARY.md, 09-03-SUMMARY.md
started: 2026-01-18T21:30:00Z
updated: 2026-01-18T23:15:00Z
---

## Current Test

[testing complete]

## Tests

### 1. Config File Created and Recognized
expected: Create `.aishell/run.conf` in a project directory. Running `aishell -v` shows "Loading runtime config" message indicating the file was found and parsed.
result: pass

### 2. Invalid Config Variable Rejected
expected: Adding an unknown variable like `INVALID_VAR="test"` to run.conf produces a clear error message showing the line number, the offending line, and the list of allowed variables (MOUNTS, ENV, PORTS, DOCKER_ARGS, PRE_START).
result: pass

### 3. MOUNTS with Home Expansion
expected: Setting `MOUNTS="$HOME/.config:/home/user/.config"` or `MOUNTS="~/.config:/home/user/.config"` in run.conf results in the host path being expanded and mounted into the container.
result: pass

### 4. Multiple MOUNTS
expected: Setting `MOUNTS="/path/one:/mount1 /path/two:/mount2"` (space-separated) results in both paths being mounted into the container.
result: pass

### 5. ENV Passthrough
expected: Setting `ENV="MY_VAR"` in run.conf (no value) passes through the host's MY_VAR environment variable to the container. Set `MY_VAR=test` on host, run aishell, verify `echo $MY_VAR` inside container shows "test".
result: pass

### 6. ENV Literal Value
expected: Setting `ENV="MY_VAR=explicit_value"` in run.conf sets the variable to that literal value inside the container, regardless of host value. Verify `echo $MY_VAR` inside container shows "explicit_value".
result: pass

### 7. PORTS Mapping
expected: Setting `PORTS="8080:80 3000:3000"` in run.conf exposes those ports from the container. Verify with `docker ps` showing the port mappings.
result: pass

### 8. DOCKER_ARGS Passthrough
expected: Setting `DOCKER_ARGS="--memory 512m --cpus 1"` in run.conf passes those flags to docker run. Verify with `docker inspect` showing memory/cpu limits applied.
result: pass

### 9. Help Shows Runtime Configuration
expected: Running `aishell --help` or `aishell -h` shows a "Runtime Configuration" section documenting the run.conf file and available options.
result: pass

## Summary

total: 9
passed: 9
issues: 0
pending: 0
skipped: 0

## Gaps

[none]
