## ADDED Requirements

### Requirement: UniSoma flag requires OpenCode
The `--unisoma` flag SHALL only be accepted when `--with-opencode` is also provided. If `--unisoma` is used without `--with-opencode`, the CLI SHALL exit with an error message indicating the dependency.

#### Scenario: Setup with --unisoma and --with-opencode
- **WHEN** user runs `aishell setup --with-opencode --unisoma`
- **THEN** setup completes successfully with `:unisoma true` in state

#### Scenario: Setup with --unisoma without --with-opencode
- **WHEN** user runs `aishell setup --unisoma`
- **THEN** CLI exits with error: "--unisoma requires --with-opencode"

### Requirement: Whitelist upsert on setup with UniSoma
When setup runs with both `--with-opencode` and `--unisoma`, the system SHALL write the hard-coded model whitelist to `~/.config/opencode/opencode.json` at the JSON path `providers.opencode.whitelist`. All other keys in the config file SHALL be preserved.

#### Scenario: Fresh setup with no existing opencode.json
- **WHEN** user runs `aishell setup --with-opencode --unisoma` and `~/.config/opencode/opencode.json` does not exist
- **THEN** system creates the file with `{"$schema": "https://opencode.ai/config.json", "providers": {"opencode": {"whitelist": [<model-list>]}}}`

#### Scenario: Setup with existing opencode.json containing other config
- **WHEN** user runs `aishell setup --with-opencode --unisoma` and `opencode.json` contains `{"$schema": "...", "theme": "dark", "providers": {"opencode": {"apiKey": "xxx"}}}`
- **THEN** system writes `opencode.json` with `{"$schema": "...", "theme": "dark", "providers": {"opencode": {"apiKey": "xxx", "whitelist": [<model-list>]}}}` preserving all existing keys

### Requirement: Whitelist removal on setup without UniSoma for previous UniSoma users
When a user who previously set up with `--unisoma` runs setup with `--with-opencode` but without `--unisoma`, the system SHALL remove the `providers.opencode.whitelist` key from `opencode.json`. All other keys SHALL be preserved.

#### Scenario: Previous UniSoma user disables UniSoma
- **WHEN** previous state has `:unisoma true` and user runs `aishell setup --with-opencode` (no `--unisoma`)
- **THEN** system removes `providers.opencode.whitelist` from `opencode.json`, preserving all other config

#### Scenario: Non-UniSoma user sets up OpenCode
- **WHEN** previous state has `:unisoma false` (or no previous state) and user runs `aishell setup --with-opencode`
- **THEN** system does not modify `opencode.json`

### Requirement: Whitelist refresh on update
When `aishell update` runs and state contains `:unisoma true`, the system SHALL re-write the whitelist to `opencode.json` using the current hard-coded model list. This ensures model list updates from new aishell versions are applied without requiring `aishell setup` again.

#### Scenario: Update with UniSoma active
- **WHEN** state has `:unisoma true` and user runs `aishell update`
- **THEN** system upserts `providers.opencode.whitelist` with the current model list, preserving all other config

### Requirement: UniSoma state persistence
The `:unisoma` boolean SHALL be persisted in the aishell state file. It SHALL default to `false`.

#### Scenario: State round-trip
- **WHEN** user runs `aishell setup --with-opencode --unisoma`
- **THEN** `state/read-state` returns a map with `:unisoma true`

### Requirement: UniSoma status display
The system SHALL display UniSoma status in `aishell info` and `aishell update` output when `:unisoma` is active in state.

#### Scenario: Info shows UniSoma enabled
- **WHEN** state has `:unisoma true` and user runs `aishell info`
- **THEN** output includes "UniSoma: enabled" (or similar) in the harness section

#### Scenario: Update shows UniSoma status
- **WHEN** state has `:unisoma true` and user runs `aishell update`
- **THEN** output includes UniSoma status alongside the OpenCode version line
