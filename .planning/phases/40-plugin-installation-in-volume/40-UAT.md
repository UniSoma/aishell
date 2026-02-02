---
status: diagnosed
phase: 40-plugin-installation-in-volume
source: [40-01-SUMMARY.md, 40-02-SUMMARY.md]
started: 2026-02-02T18:00:00Z
updated: 2026-02-02T18:15:00Z
---

## Current Test

[testing complete]

## Tests

### 1. Invalid plugin format triggers warning
expected: Add an invalid plugin entry (e.g., "not-a-valid-format") to tmux.plugins in .aishell/config.yaml. Run `aishell build --with-tmux`. Config loading warns about invalid plugin format but does not fail.
result: pass

### 2. Valid plugin format accepted silently
expected: Add a valid plugin (e.g., "tmux-plugins/tmux-sensible") to tmux.plugins. Run `aishell build --with-tmux`. No validation warnings for that plugin.
result: pass

### 3. TPM installed into volume during build
expected: With valid plugins declared and --with-tmux, run `aishell build`. After build completes, the harness volume should contain /tools/tmux/plugins/tpm (TPM clone).
result: issue
reported: "Failed. See that my config has a plugin defined, but starting the container there's no such directory /tools/tmux"
severity: major

### 4. Declared plugins installed during build
expected: With "tmux-plugins/tmux-sensible" in plugins list, after `aishell build --with-tmux`, the volume should contain /tools/tmux/plugins/tmux-sensible.
result: issue
reported: "same issue, /tools/tmux doesn't exist"
severity: major

### 5. No plugins declared skips TPM installation
expected: Remove all plugins from tmux.plugins (empty list or omit). Run `aishell build --with-tmux`. Build succeeds without TPM installation step.
result: skipped
reason: Blocked by tests 3/4 - build path not installing plugins

### 6. tmux disabled skips plugin installation
expected: Run `aishell build` without --with-tmux flag. Even if plugins are declared in config, no plugin installation occurs.
result: skipped
reason: Blocked by tests 3/4 - build path not installing plugins

### 7. Update refreshes plugin installations
expected: With plugins declared and --with-tmux, run `aishell update`. Plugins are re-installed/refreshed in the volume.
result: pass

### 8. Non-string plugin entry triggers type warning
expected: Add a non-string entry to tmux.plugins (e.g., a number like 123). Config loading warns about invalid type.
result: pass

### 9. Multiple plugins installed correctly
expected: Declare 2+ plugins (e.g., "tmux-plugins/tmux-sensible", "tmux-plugins/tmux-yank"). After build, both are present in /tools/tmux/plugins/.
result: issue
reported: "aishell update fails with: fatal: destination path '/tools/tmux/plugins/tpm' already exists and is not an empty directory. Error: Failed to populate harness volume"
severity: major

## Summary

total: 9
passed: 4
issues: 3
pending: 0
skipped: 2

## Gaps

- truth: "TPM and plugins installed into volume during aishell build --with-tmux"
  status: failed
  reason: "User reported: /tools/tmux directory doesn't exist after build. Build path does not trigger plugin installation."
  severity: major
  test: 3
  root_cause: "compute-harness-hash only includes AI harness configs (claude/codex/gemini/opencode), not tmux state or plugins. Volume hash unchanged when adding --with-tmux, so build skips populate-volume."
  artifacts:
    - path: "src/aishell/docker/volume.clj"
      issue: "harness-keys excludes :tmux; normalize-harness-config doesn't include tmux config in hash"
    - path: "src/aishell/cli.clj"
      issue: "Build path only calls populate-volume when vol-missing? or vol-stale? (lines 210-218)"
  missing:
    - "Include :with-tmux flag and plugin list in volume hash computation"
  debug_session: ".planning/debug/build-missing-plugins.md"

- truth: "Declared plugins present in volume after aishell build --with-tmux"
  status: failed
  reason: "User reported: /tools/tmux doesn't exist after build. Same root cause as test 3."
  severity: major
  test: 4
  root_cause: "Same as test 3 — volume hash doesn't include tmux config, build path skips repopulation"
  artifacts:
    - path: "src/aishell/docker/volume.clj"
      issue: "harness-keys excludes :tmux"
  missing:
    - "Same fix as test 3"
  debug_session: ".planning/debug/build-missing-plugins.md"

- truth: "Multiple plugins installed correctly on repeated update (idempotent)"
  status: failed
  reason: "User reported: aishell update fails with git clone fatal error - destination path already exists and is not empty"
  severity: major
  test: 9
  root_cause: "build-tpm-install-command generates bare git clone without existence check. On second run, /tools/tmux/plugins/tpm already exists, causing git clone to fail."
  artifacts:
    - path: "src/aishell/docker/volume.clj"
      issue: "Line 200: git clone command has no idempotency guard"
  missing:
    - "Add existence check before git clone (skip if exists, or rm -rf first, or git pull if exists)"
  debug_session: ".planning/debug/tpm-clone-idempotency.md"
