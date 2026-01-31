---
status: complete
phase: 32-detached-mode-conflict-detection
source: [32-01-SUMMARY.md, 32-02-SUMMARY.md]
started: 2026-01-31T15:00:00Z
updated: 2026-01-31T15:15:00Z
---

## Current Test

[testing complete]

## Tests

### 1. Foreground Mode with Named Container
expected: Running `aishell claude` (or any harness) launches in foreground. Container gets a named container in format `aishell-{hash}-claude`. Behavior is backwards compatible.
result: pass

### 2. Tmux Session Auto-Start
expected: Inside any running container, `tmux ls` shows a session named "main". All modes (shell, harness) run inside tmux by default.
result: issue
reported: "missing or unsuitable terminal: xterm-ghostty"
severity: blocker

### 3. Detached Mode Launch
expected: Running `aishell claude --detach` starts the container in background and exits immediately back to host shell. Container continues running.
result: issue
reported: "Container starts and prints feedback but then dies immediately. docker ps does not show it. Caused by tmux failing with missing or unsuitable terminal: xterm-ghostty"
severity: blocker

### 4. Short Detach Flag
expected: Running `aishell claude -d` works identically to `--detach` — container starts detached.
result: skipped
reason: Unable to test due to tmux terminal issue

### 5. Detached Feedback Commands
expected: After detached launch, output shows: container name confirmation, attach command, docker exec shell command, stop command, and ps command.
result: pass

### 6. Conflict Detection — Running Container
expected: Starting a second `aishell claude --detach` while first is still running shows a clear error message with a hint to attach instead. Does NOT start a duplicate.
result: skipped
reason: Unable to test due to tmux terminal issue — containers die immediately

### 7. Conflict Detection — Stopped Container
expected: If a named container exists but is stopped, starting a new one auto-removes the stopped container and launches fresh. No manual cleanup needed.
result: skipped
reason: Unable to test due to tmux terminal issue

### 8. Shell Mode with Named Container
expected: Running `aishell` (shell mode) also gets a named container (name='shell') and runs inside tmux, but otherwise behaves as before.
result: skipped
reason: Unable to test due to tmux terminal issue

## Summary

total: 8
passed: 2
issues: 2
pending: 0
skipped: 4

## Gaps

- truth: "All container modes run inside tmux session 'main'"
  status: failed
  reason: "User reported: missing or unsuitable terminal: xterm-ghostty"
  severity: blocker
  test: 2
  root_cause: ""
  artifacts: []
  missing: []
  debug_session: ""

- truth: "Detached container stays running after launch"
  status: failed
  reason: "User reported: Container starts and prints feedback but then dies immediately. docker ps does not show it. Caused by tmux failing with missing or unsuitable terminal: xterm-ghostty"
  severity: blocker
  test: 3
  root_cause: ""
  artifacts: []
  missing: []
  debug_session: ""
