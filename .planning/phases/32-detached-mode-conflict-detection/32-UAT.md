---
status: complete
phase: 32-detached-mode-conflict-detection
source: [32-01-SUMMARY.md, 32-02-SUMMARY.md, 32-03-SUMMARY.md]
started: 2026-01-31T18:00:00Z
updated: 2026-01-31T18:15:00Z
---

## Current Test

[testing complete]

## Tests

### 1. Foreground Mode with Named Container
expected: Running `aishell claude` launches in foreground with a named container in format `aishell-{hash}-claude`. Behavior is backwards compatible.
result: pass

### 2. Tmux Session Auto-Start
expected: Inside any running container, running `tmux ls` shows a session named "main". All modes (shell, harness) run inside tmux by default.
result: pass

### 3. Detached Mode Launch
expected: Running `aishell claude --detach` (or `-d`) starts the container in background and exits immediately back to host shell. Container continues running (`docker ps` shows it).
result: pass

### 4. Detached Feedback Commands
expected: After detached launch, output shows container name confirmation, attach command, docker exec shell command, stop command, and ps command.
result: pass

### 5. Conflict Detection — Running Container
expected: Starting a second `aishell claude --detach` while first is still running shows a clear error message with a hint to attach instead. Does NOT start a duplicate.
result: pass

### 6. Conflict Detection — Stopped Container
expected: If a named container exists but is stopped, starting a new one auto-removes the stopped container and launches fresh. No manual cleanup needed.
result: pass

### 7. Shell Mode with Named Container
expected: Running `aishell` (shell mode) gets a named container (name='shell') and runs inside tmux. Otherwise behaves as before.
result: pass

### 8. TERM Validation
expected: Running a container from a terminal with a custom TERM (e.g., xterm-ghostty from Ghostty) does NOT cause tmux to fail. Container falls back to xterm-256color if terminfo is missing.
result: pass

## Summary

total: 8
passed: 8
issues: 0
pending: 0
skipped: 0

## Gaps

[none yet]
