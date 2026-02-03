---
status: diagnosed
trigger: "aishell update fails on second run with: fatal: destination path '/tools/tmux/plugins/tpm' already exists and is not an empty directory"
created: 2026-02-02T00:00:00Z
updated: 2026-02-02T00:00:00Z
---

## Current Focus

hypothesis: git clone command in build-tpm-install-command doesn't handle existing TPM directory
test: examining volume.clj and build-tpm-install-command implementation
expecting: find git clone command without existence check or --force-like flag
next_action: read src/aishell/docker/volume.clj

## Symptoms

expected: Running `aishell update` multiple times should be idempotent - refreshing/updating plugins
actual: Second run fails with "fatal: destination path '/tools/tmux/plugins/tpm' already exists and is not an empty directory"
errors: fatal: destination path '/tools/tmux/plugins/tpm' already exists and is not an empty directory. Error: Failed to populate harness volume
reproduction: 1. Run `aishell update` successfully, 2. Run `aishell update` again
started: Always broken (non-idempotent design)

## Eliminated

## Evidence

- timestamp: 2026-02-02T00:00:00Z
  checked: src/aishell/docker/volume.clj lines 192-203
  found: build-tpm-install-command does git clone without checking if /tools/tmux/plugins/tpm already exists
  implication: Second invocation of populate-volume will fail because git clone refuses to clone into existing directory

- timestamp: 2026-02-02T00:00:00Z
  checked: Line 200 specifically
  found: Command is "git clone --depth 1 https://github.com/tmux-plugins/tpm /tools/tmux/plugins/tpm"
  implication: No existence check, no force flag, no rm -rf before clone

- timestamp: 2026-02-02T00:00:00Z
  checked: populate-volume function (lines 287-347)
  found: Function is called on every update, commands are built fresh each time
  implication: On second run, TPM directory from first run already exists, causing git clone to fail

## Resolution

root_cause: git clone in build-tpm-install-command (line 200) doesn't handle existing /tools/tmux/plugins/tpm directory from previous runs
fix: Add existence check or rm -rf before git clone, or use git clone with fallback to git pull
verification: Run aishell update twice - second run should succeed instead of failing
files_changed: [src/aishell/docker/volume.clj]
