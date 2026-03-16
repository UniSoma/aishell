## 1. State and CLI Flag

- [ ] 1.1 Add `:unisoma false` to default state map in `src/aishell/state.clj`
- [ ] 1.2 Add `:unisoma` to `setup-spec` in `src/aishell/cli.clj` as a boolean flag with description
- [ ] 1.3 Parse `--unisoma` in `handle-setup` and add validation: error if `:unisoma` is true but `:with-opencode` is not enabled
- [ ] 1.4 Add `:unisoma` to `state-map` construction in `handle-setup`

## 2. OpenCode Config Management

- [ ] 2.1 Define `unisoma-models` constant — the hard-coded vector of whitelisted model name strings
- [ ] 2.2 Implement `manage-opencode-whitelist!` function in `cli.clj` that takes new-state and previous-state, performs the upsert/remove/no-op logic on `~/.config/opencode/opencode.json`
- [ ] 2.3 Call `manage-opencode-whitelist!` from `handle-setup` after state-map is built (passing previous state from `state/read-state`)
- [ ] 2.4 Call `manage-opencode-whitelist!` from `handle-update` after volume repopulation (passing state as both new and previous — upsert only)

## 3. Display and Info

- [ ] 3.1 Add UniSoma status line to `handle-update` output (under the OpenCode line, when `:unisoma` is true)
- [ ] 3.2 Add UniSoma status to `src/aishell/info.clj` harness display section
- [ ] 3.3 Add setup help example showing `--with-opencode --unisoma` usage in `print-setup-help`
