---
status: diagnosed
phase: 40-plugin-installation-in-volume
source: [40-01-SUMMARY.md, 40-02-SUMMARY.md, 40-03-SUMMARY.md]
started: 2026-02-02T17:00:00Z
updated: 2026-02-02T17:30:00Z
---

## Current Test

[testing complete]

## Tests

### 1. TPM installed during build (re-verify)
expected: With a valid plugin declared in tmux.plugins, run `aishell build --with-tmux`. Start container and verify /tools/tmux/plugins/tpm exists.
result: pass

### 2. Declared plugins installed during build (re-verify)
expected: With tmux-plugins/tmux-sensible in tmux.plugins, after `aishell build --with-tmux`, start container and verify /tools/tmux/plugins/tmux-sensible exists.
result: issue
reported: "Neither aishell build --with-tmux nor aishell update resulted in tmux-sensible being installed. Only tpm folder is inside plugins."
severity: major

### 3. Repeated update is idempotent (re-verify)
expected: Run `aishell update` multiple times in a row. No git clone errors — TPM handles existing directory gracefully with pull-if-exists pattern.
result: pass

### 4. No plugins skips TPM installation
expected: Remove all plugins from tmux.plugins (empty list or omit key). Run `aishell build --with-tmux`. Build succeeds without TPM installation step.
result: pass

### 5. Without --with-tmux skips plugin installation
expected: Even with plugins declared in config, run `aishell build` (no --with-tmux). Plugin installation is skipped entirely.
result: pass

### 6. Volume hash changes on tmux config change
expected: After building with --with-tmux and one plugin, add a second plugin to config and run `aishell build --with-tmux` again. A new volume is created (hash changed) and both plugins are installed.
result: pass

## Summary

total: 6
passed: 5
issues: 1
pending: 0
skipped: 0

## Gaps

- truth: "Declared plugins (e.g., tmux-sensible) installed into /tools/tmux/plugins/ during build"
  status: failed
  reason: "User reported: Neither aishell build --with-tmux nor aishell update resulted in tmux-sensible being installed. Only tpm folder is inside plugins."
  severity: major
  test: 2
  root_cause: "build-tpm-install-command writes plugin declarations to /tmp/plugins.conf, but TPM install_plugins script only reads from ~/.tmux.conf. It uses AWK to parse for 'set -g @plugin' lines in the tmux config file, so /tmp/plugins.conf is never read."
  artifacts:
    - path: "src/aishell/docker/volume.clj"
      issue: "Line 209: writes plugin declarations to /tmp/plugins.conf instead of ~/.tmux.conf where TPM expects them"
  missing:
    - "Write plugin declarations to ~/.tmux.conf (or pass correct path) so TPM install_plugins can find them"
  debug_session: ".planning/debug/plugins-not-installing.md"
