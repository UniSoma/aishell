## Context

aishell manages OpenCode as a harness tool installed into a Docker volume and run inside containers. OpenCode reads its configuration from `~/.config/opencode/opencode.json`, which is bind-mounted from the host into the container via the existing `:with-opencode` config directory mapping in `docker/run.clj`.

Currently, no aishell flag modifies the OpenCode config file. The `--unisoma` flag introduces the first case of aishell writing into a harness's config file on the host, requiring a read-merge-write pattern to preserve user customizations.

The model whitelist is hard-coded in aishell source. Updates to the list ship with new aishell versions and are applied on `aishell update`.

## Goals / Non-Goals

**Goals:**
- `--unisoma` flag on `aishell setup` that requires `--with-opencode`
- Upsert `providers.opencode.whitelist` into OpenCode config without touching other keys
- Remove the whitelist when a previous UniSoma user runs setup without `--unisoma`
- `aishell update` re-applies the whitelist (picking up model list changes from new aishell versions)

**Non-Goals:**
- Custom UniSoma API endpoint configuration (hard-coded for now)
- Model selection UI at setup time (all whitelisted models are registered)
- Modifying OpenCode source code or plugins
- Supporting `--unisoma` with other harnesses (Claude, Codex, etc.)

## Decisions

### 1. Host-side config write at setup/update time

**Decision**: Write `opencode.json` on the host at `~/.config/opencode/opencode.json` during `aishell setup` and `aishell update`. The existing bind-mount makes it available in the container.

**Alternatives considered**:
- *Volume-time injection*: Write config into the Docker volume during `populate-volume`. Rejected because the config dir is bind-mounted from host, not from the volume — the volume holds the binary, not config.
- *Runtime generation*: Generate config at container start via entrypoint. Rejected because it adds entrypoint complexity and the config file already exists on the host.

**Rationale**: Simplest path — no new mounts, no entrypoint changes, follows the `vscode.clj:ensure-imageconfig!` precedent of host-side config writes.

### 2. Read-merge-write with deep path upsert

**Decision**: Read existing `opencode.json`, deep-set `providers.opencode.whitelist`, write back. Use `assoc-in` for the upsert path. On removal, use `update-in` to `dissoc` the `whitelist` key.

**Rationale**: Preserves any other user-configured providers, models, or settings. The `$schema` key and all sibling keys remain untouched.

### 3. State key `:unisoma` as boolean

**Decision**: Store `:unisoma` as a boolean in state (`state.clj` defaults). The remove-whitelist logic checks `(and (not (:unisoma new-state)) (:unisoma previous-state))`.

**Alternative**: Derive from the presence of the whitelist in the config file. Rejected because reading the config file to determine state inverts the dependency — state should drive config, not the other way around.

### 4. Dedicated function in a new namespace or in `cli.clj`

**Decision**: Add `manage-opencode-whitelist!` as a private function in `cli.clj`, called from both `handle-setup` and `handle-update`. If it grows, extract to a namespace later.

**Rationale**: Follows YAGNI. The function is ~15 lines. A new namespace would be premature for a single function.

## Risks / Trade-offs

- **[Config file corruption on concurrent writes]** → Unlikely in practice since `aishell setup` is a user-initiated CLI command, not a daemon. No mitigation needed.
- **[OpenCode schema changes]** → If OpenCode changes the `providers.opencode.whitelist` schema, the hard-coded path breaks. → Mitigation: The whitelist is a simple array of strings — low risk of schema change. If it happens, a new aishell version fixes it.
- **[User manually edits whitelist, then runs update]** → `aishell update` overwrites their manual edits to the whitelist array. → Acceptable: the whitelist is managed by aishell, not the user. Other config keys are preserved.
