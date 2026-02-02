---
status: complete
phase: 41-tpm-initialization-in-entrypoint
source: [41-01-SUMMARY.md]
started: 2026-02-02T12:00:00Z
updated: 2026-02-02T12:05:00Z
---

## Current Test

[testing complete]

## Tests

### 1. WITH_TMUX env var passed to container
expected: Run `aishell build --with-tmux` then `aishell run`. Inside the container, `echo $WITH_TMUX` prints `true`.
result: pass

### 2. Plugin path bridging via symlink
expected: Inside a container built with `--with-tmux`, `ls -la ~/.tmux/plugins` shows a symlink pointing to `/tools/tmux/plugins`.
result: pass
note: Initially failed due to stale foundation image. Passed after `aishell build --force`.

### 3. Config injection creates runtime config
expected: Inside a tmux-enabled container, `cat ~/.tmux.conf.runtime` shows your tmux config with a TPM `run` line appended at the end (e.g., `run '~/.tmux/plugins/tpm/tpm'`).
result: pass
note: Initially failed due to stale foundation image. Passed after `aishell build --force`.

### 4. Conditional startup - tmux mode
expected: When built with `--with-tmux`, running the container drops you into a tmux session named "harness". Running `tmux display-message -p '#S'` inside shows `harness`.
result: pass
note: Initially showed "main" due to stale foundation image. Passed after `aishell build --force`.

### 5. Conditional startup - shell mode
expected: When built WITHOUT `--with-tmux`, running the container drops you into a direct shell (no tmux). Running `echo $TMUX` prints empty.
result: pass

## Summary

total: 5
passed: 5
issues: 0
pending: 0
skipped: 0

## Gaps

[none]

## Notes

User discovered that a stale foundation image caused tests 2-4 to initially fail. Rebuilding with `--force` resolved all failures. This suggests `aishell build` should detect when the foundation image is outdated and warn the user. Logged as a todo for a future phase.
