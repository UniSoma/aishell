---
status: diagnosed
trigger: "aishell build --with-tmux does not install TPM or plugins into the volume"
created: 2026-02-02T00:00:00Z
updated: 2026-02-02T00:10:00Z
symptoms_prefilled: true
goal: find_root_cause_only
---

## Current Focus

hypothesis: REFINED - User runs with both --with-tmux AND --with-claude. Build path passes state-map to populate-volume, update path passes state. state-map vs state is the difference.
test: Check what populate-volume receives in build vs update
expecting: state-map in build doesn't have :with-tmux field, but state in update does
next_action: Check line 215 vs line 331 - what's passed to populate-volume

## Symptoms

expected: `aishell build --with-tmux` should install TPM and plugins to /tools/tmux in volume
actual: `/tools/tmux` directory doesn't exist after build
errors: none reported
reproduction: Run `aishell build --with-tmux` with plugins declared in config
started: Current issue - update path works correctly, build path doesn't

## Eliminated

## Evidence

- timestamp: 2026-02-02T00:01:00Z
  checked: cli.clj handle-build function (lines 153-228)
  found: Build path calls populate-volume with {:verbose (:verbose opts) :config cfg} at line 215
  implication: Config IS threaded through in build path

- timestamp: 2026-02-02T00:02:00Z
  checked: cli.clj handle-update function (lines 275-344)
  found: Update path calls populate-volume with {:verbose (:verbose opts) :config cfg} at line 331
  implication: Both paths thread config identically - this is NOT the issue

- timestamp: 2026-02-02T00:03:00Z
  checked: volume.clj populate-volume function (lines 287-347)
  found: Function extracts tmux plugins from opts: (get-in opts [:config :tmux :plugins]) at lines 315-316
  implication: populate-volume depends on :config in opts to get plugin list

- timestamp: 2026-02-02T00:04:00Z
  checked: volume.clj build-tpm-install-command (lines 192-203)
  found: Returns nil if (seq plugins) is empty or nil
  implication: No plugins → no TPM installation

- timestamp: 2026-02-02T00:05:00Z
  checked: cli.clj build path conditional (line 198)
  found: populate-volume only called when (some #(get state-map %) [:with-claude :with-opencode :with-codex :with-gemini])
  implication: If ONLY --with-tmux is set (no harnesses), populate-volume never runs

- timestamp: 2026-02-02T00:06:00Z
  checked: run.clj ensure-harness-volume function (line 47)
  found: Same conditional - only checks for harnesses, not :with-tmux
  implication: run.clj has identical issue - explains why update works (it uses same build path)

- timestamp: 2026-02-02T00:07:00Z
  checked: cli.clj build path lines 210-218
  found: populate-volume only called when (or vol-missing? vol-stale?)
  implication: If volume exists and hash matches, populate-volume is skipped!

- timestamp: 2026-02-02T00:08:00Z
  checked: volume.clj compute-harness-hash (lines 53-75)
  found: Hash only includes harness config, NOT tmux state
  implication: Adding --with-tmux doesn't change hash, so volume is not considered stale

- timestamp: 2026-02-02T00:09:00Z
  checked: volume.clj normalize-harness-config (lines 25-51)
  found: Only processes harness-keys [:claude :codex :gemini :opencode], not :tmux
  implication: CONFIRMED - tmux not included in hash calculation

## Resolution

root_cause: compute-harness-hash only includes harnesses [:claude :codex :gemini :opencode] in hash calculation (volume.clj line 14-16). When user adds --with-tmux to existing build, volume hash doesn't change, so build path sees volume as "current" and skips populate-volume (cli.clj line 210-218). Update works because it unconditionally deletes and recreates volume (cli.clj line 327).

fix: Include :with-tmux and tmux plugin config in harness hash computation, so adding/removing tmux or changing plugins triggers volume repopulation.

verification: Build with --with-claude, then build again with --with-claude --with-tmux - should trigger volume rebuild.

files_changed:
  - src/aishell/docker/volume.clj: normalize-harness-config needs to include tmux in hash
  - src/aishell/docker/volume.clj: compute-harness-hash should include tmux plugin list from config
