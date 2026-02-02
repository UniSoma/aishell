---
status: complete
phase: 39-state-schema-config-mounting
source: [39-01-SUMMARY.md, 39-02-SUMMARY.md]
started: 2026-02-02T12:00:00Z
updated: 2026-02-02T12:10:00Z
---

## Current Test

[testing complete]

## Tests

### 1. Build with --with-tmux flag stores state
expected: Running `aishell build --with-tmux` completes successfully and stores `:with-tmux true` in state.edn
result: pass

### 2. Build without --with-tmux defaults to disabled
expected: Running `aishell build` (no --with-tmux flag) completes successfully and `:with-tmux` is false or absent in state.edn
result: pass

### 3. --with-tmux shown in build help
expected: Running `aishell build --help` or `aishell help build` shows --with-tmux as an available option
result: pass

### 4. tmux config section accepted in config.yaml
expected: Adding `tmux:` section (as a map, e.g. `tmux: {plugins: []}`) to .aishell/config.yaml produces no unknown-key warnings during build
result: pass

### 5. Invalid tmux config type warns
expected: Setting `tmux: true` (boolean instead of map) in config.yaml produces a helpful warning message about expected map type
result: pass

### 6. ~/.tmux.conf mounted when tmux enabled
expected: After `aishell build --with-tmux`, running the container shows ~/.tmux.conf from host mounted read-only inside the container (visible via mount or checking /root/.tmux.conf or /home/user/.tmux.conf)
result: pass

### 7. Missing ~/.tmux.conf handled gracefully
expected: If ~/.tmux.conf does not exist on host, `aishell build --with-tmux` and container start complete without error or warning
result: pass

### 8. User explicit tmux mount takes precedence
expected: If user has an explicit .tmux.conf mount in config.yaml mounts section, the auto-mount is skipped (no duplicate mount error)
result: pass

### 9. Project tmux config replaces global config
expected: If both global (~/.aishell/config.yaml) and project (.aishell/config.yaml) define tmux: sections, the project config fully replaces global (scalar merge, not deep merge)
result: skipped
reason: No observable effect until Phase 40+ consumes tmux config; merge logic verifiable then

## Summary

total: 9
passed: 8
issues: 0
pending: 0
skipped: 1

## Gaps

[none yet]
