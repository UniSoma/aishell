## Why

The UniSoma team uses aishell with OpenCode and needs to filter which models are available — not the full OpenCode catalog. Currently there is no mechanism to restrict which models OpenCode exposes. By adding a `--unisoma` flag to `aishell setup`, we can automatically configure an OpenCode model whitelist so the team sees only approved models, while keeping the setup experience a single command.

## What Changes

- Add `--unisoma` CLI flag to `aishell setup` (boolean, requires `--with-opencode`)
- Persist `:unisoma` in aishell state so `aishell update` can re-apply the whitelist with updated model lists from newer aishell versions
- On setup/update with `--unisoma`: upsert `providers.opencode.whitelist` into `~/.config/opencode/opencode.json` with the hard-coded model list, preserving all other config
- On setup without `--unisoma` when previous state had `:unisoma true`: remove `providers.opencode.whitelist` from the config file, preserving all other config
- Error if `--unisoma` is used without `--with-opencode`

## Capabilities

### New Capabilities
- `unisoma-provider`: OpenCode model whitelist management for UniSoma users — flag parsing, state persistence, config file upsert/removal, and update-path refresh

### Modified Capabilities

_None — this is additive. No existing spec-level behavior changes._

## Impact

- **CLI**: `src/aishell/cli.clj` — new flag in `setup-spec`, parsing, validation, state-map construction, display in `handle-update`
- **State**: `src/aishell/state.clj` — new `:unisoma` boolean key in defaults
- **Config write**: New function to read/merge/write `~/.config/opencode/opencode.json` (called from both `handle-setup` and `handle-update`)
- **Info/Check**: `src/aishell/info.clj`, `src/aishell/check.clj` — cosmetic display of UniSoma status
