---
status: diagnosed
phase: 37-build-integration-migration
source: [37-01-SUMMARY.md, 37-02-SUMMARY.md, 37-03-SUMMARY.md, 37-04-SUMMARY.md]
started: 2026-02-01T02:00:00Z
updated: 2026-02-01T02:15:00Z
---

## Tests

### 1. Build with harnesses creates foundation image + harness volume
expected: Running `aishell build --with-claude` builds foundation image tagged `aishell:foundation` and creates a harness volume `aishell-harness-{hash}`. Both complete without errors.
result: pass

### 2. State file contains v2.8.0 fields after build
expected: After building, `cat ~/.aishell/state.edn` shows `:foundation-hash`, `:harness-volume-hash`, and `:harness-volume-name` fields alongside existing fields. The `:image-tag` should be `aishell:foundation`.
result: pass

### 3. Harness volume only rebuilds when config changes
expected: Running `aishell build --with-claude` a second time (no flag/version changes) should skip volume population (fast). Changing a flag (e.g., adding `--with-codex`) should trigger volume repopulation.
result: pass

### 4. Lazy volume population on run
expected: If you delete the harness volume (`docker volume rm aishell-harness-{hash}`) and then run `aishell claude`, the volume should auto-recreate and populate before the container starts.
result: partial — volume recreates and populates, but only Claude is available. OpenCode is missing despite being configured.
severity: high
gap: GAP-01

### 5. Extension cache invalidation after upgrade
expected: If you had an extension image built under v2.7.0, running `aishell build` after the upgrade should detect the extension is stale (missing `aishell.foundation.id` label) and rebuild it automatically.
result: pass

### 6. v2.7.0 state file backward compatibility
expected: An old state file from v2.7.0 (without `:foundation-hash`, `:harness-volume-hash`, `:harness-volume-name`) should work without errors. Running a container should compute volume name on-the-fly and mount correctly.
result: pass

### 7. Legacy FROM aishell:base produces clear error
expected: If `.aishell/Dockerfile` contains `FROM aishell:base`, running `aishell build` should produce a clear error message instructing to change to `FROM aishell:foundation`. Build should NOT proceed.
result: pass

### 8. New tmux windows have proper shell environment
expected: Inside the container, pressing `Ctrl+b c` to create a new tmux window should have the correct prompt (cyan [aishell] prefix), correct PATH (harness tools available), and correct working directory.
result: fail — new tmux windows have default bash prompt, wrong PATH (no harness tools), and lose environment setup from entrypoint.
severity: high
gap: GAP-02

## Summary

total: 8
passed: 6
issues: 2
pending: 0
skipped: 0

## Gaps

### GAP-01: OpenCode has no installation path in v2.8.0 volume architecture
severity: high
test: 4
symptom: OpenCode not available in container despite being configured with --with-opencode
root-cause: |
  OpenCode is a Go binary, not an npm package. The volume population logic
  (volume.clj:build-install-commands) only handles npm packages via
  harness-npm-packages map. OpenCode is explicitly excluded (volume.clj:20-22).
  The foundation image no longer installs any harness tools, so OpenCode
  has no installation path in the new architecture.
affected-files:
  - src/aishell/docker/volume.clj (build-install-commands, populate-volume)
fix-approach: |
  Add OpenCode binary installation to volume population. Download the Go binary
  via curl into /tools/bin/ alongside the npm packages. Update PATH in
  entrypoint.sh to include /tools/bin if it exists.

### GAP-02: New tmux windows lose shell environment (PATH, prompt, aliases)
severity: high
test: 8
symptom: Pressing Ctrl+b c in tmux creates window with default bash prompt, no harness tools in PATH, and no aishell customizations.
root-cause: |
  tmux spawns new windows as login shells (bash -l). Login shells source
  /etc/profile (which on Debian resets PATH to default values) and ~/.profile,
  but NOT ~/.bashrc. The entrypoint.sh sets PATH/NODE_PATH/LANG via export
  before exec gosu+tmux, and adds bashrc sourcing only to ~/.bashrc.
  New tmux windows lose both:
  1. PATH — /etc/profile overwrites inherited PATH, removing /tools/npm/bin
  2. Prompt/aliases — ~/.bashrc (which sources /etc/bash.aishell) not read by login shells
affected-files:
  - src/aishell/docker/templates.clj (entrypoint-script, bashrc-content, base-dockerfile)
fix-approach: |
  Create /etc/profile.d/aishell.sh in the Dockerfile that:
  1. Adds /tools/npm/bin to PATH if directory exists
  2. Sets NODE_PATH if /tools/npm/lib/node_modules exists
  3. Sources /etc/bash.aishell for prompt, aliases, locale
  Login shells source /etc/profile.d/*.sh after the base PATH is set,
  so new tmux windows will get the full environment.
