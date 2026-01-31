---
status: complete
phase: 33-attach-command
source: 33-01-SUMMARY.md
started: 2026-01-31T18:00:00Z
updated: 2026-01-31T18:10:00Z
---

## Current Test

[testing complete]

## Tests

### 1. Attach to Running Container
expected: Run `aishell attach --name <container-name>` targeting a running detached container. You should be connected to the tmux "main" session and see the running harness with full interactivity.
result: issue
reported: "Failed. Container is running (saw it on docker ps) but: aishell attach --name claude returns 'Error: No tmux sessions found in container claude. The container may have been started without tmux.'"
severity: major

### 2. Detach from Attached Session
expected: While attached to a container's tmux session, press Ctrl+B then D. You should be returned to your host terminal. The container continues running in the background.
result: skipped
reason: Blocked by Test 1 — cannot attach to test detach

### 3. Attach to Non-Existent Container
expected: Run `aishell attach --name nonexistent`. You should see a clear error message indicating the container was not found (not a raw Docker error).
result: pass

### 4. Attach to Stopped Container
expected: Run `aishell attach --name <stopped-container>` targeting a container that exists but is stopped. You should see a specific error message distinguishing "stopped" from "not found", with guidance on how to restart.
result: pass

### 5. Attach Without TTY
expected: Run `aishell attach --name <name>` from a non-interactive context (e.g., piped input). You should see an error message indicating a TTY is required.
result: skipped
reason: Blocked by Test 1 — core attach flow broken

### 6. Attach Help Text
expected: Run `aishell attach --help`. You should see usage information including the --name flag, optional --session flag, and detach instructions (Ctrl+B D).
result: pass

## Summary

total: 6
passed: 3
issues: 1
pending: 0
skipped: 2

## Gaps

- truth: "Attach connects to running container's tmux main session with full interactivity"
  status: failed
  reason: "User reported: Container is running but attach returns 'No tmux sessions found in container claude'. Container started with --detach, visible in docker ps, but tmux session not detected."
  severity: major
  test: 1
  root_cause: ""
  artifacts: []
  missing: []
  debug_session: ""
