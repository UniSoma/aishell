# Via Negativa: aishell UX Review

## Current State

The `llm.txt` describes aishell's CLI UX: setup, running harnesses, exec, update, check, ps, attach, volumes, configuration (YAML with inheritance), environment variables, authentication, security (3 layers), container naming, detached mode, foundation image contents, state files, common workflows, troubleshooting, and exit codes.

## Candidates for Removal

- **`aishell volumes list` subcommand**: Remove because bare `aishell volumes` already does the same thing. The alias adds cognitive load without value. Impact: one fewer thing to remember.

- **List format for `env`**: Remove because map format is strictly better (clearer, less ambiguous passthrough syntax). Supporting both creates documentation weight and parsing complexity. Impact: simpler config, simpler docs, one code path.

- **`--version --json`**: Remove because the primary consumer is humans. If machines need version info, they can parse `state.edn` directly or use a programmatic API. Impact: less flag surface area.

- **`gitleaks_freshness_check` config key**: Remove because an advisory-only warning that never blocks is noise. Users who care about Gitleaks will run it; users who don't will learn to ignore the warning. Impact: fewer config knobs, less nag fatigue.

- **`--without-gitleaks` flag**: Remove because if Gitleaks is truly optional, make it opt-in (`--with-gitleaks`) instead of opt-out. Currently the mental model is inconsistent: harnesses are opt-in, but Gitleaks is opt-out. Impact: consistent `--with-*` pattern, simpler setup command.

- **Low-severity detection notices**: Remove because notices nobody acts on are clutter. Keep high (blocks) and medium (warns); drop low. Impact: less output noise during launch.

- **`extends: none` in project config**: Remove because this is a power-user escape hatch that complicates the mental model. If a project needs to ignore global config, the user can just not have a global config, or use a minimal one. Impact: simpler inheritance model.

- **Troubleshooting section "Session name 'main' not found"**: Remove because this is migration debt from v2.9.0. After one or two releases, nobody hits this. Impact: shorter docs.

- **`pre_start` list format (join with &&)**: Remove because users can write `cmd1 && cmd2 && cmd3` as a single string. Two formats for the same thing adds parsing code and doc weight. Impact: one format, simpler.

- **`--unsafe` flag name**: Rename, don't remove — but the current name is worth flagging. "Unsafe" is vague. `--skip-detection` or `--no-scan` communicates what it actually does. Impact: clearer intent.

## Essential (Keep)

- **`setup` / `update` / `check` / `ps` / `attach` / `exec`**: Core command surface. Each serves a distinct purpose.
- **`--with-*` harness flags**: Clean opt-in model.
- **`--detach` / `--name`**: Essential for multi-session workflows.
- **Map-format `env`, `mounts`, `ports`, `docker_args`**: Core config primitives.
- **`harness_args`**: Genuinely useful for per-project defaults.
- **Sensitive file detection (high/medium)**: Real security value.
- **Project Dockerfile extension**: Clean extensibility without overcomplicating the tool.
- **Path preservation design**: Core differentiator, solves a real problem.
- **tmux resurrect**: Solves real pain for detached workflows.

## Do Not Add

- **Plugin/extension system**: Would multiply complexity. The Dockerfile extension point is sufficient.
- **Built-in IDE integration**: Stay CLI-native. IDEs can call the CLI.
- **Container orchestration (compose-like features)**: Scope creep. One container per harness is the right constraint.
- **Remote/cloud container support**: Different product entirely.
- **Config validation CLI (`aishell config validate`)**: `aishell check` already covers this. Don't split it.

## Simplified State

- Commands: `setup`, `update`, `check`, `ps`, `attach`, `exec`, `volumes`, `gitleaks`, `{harness-name}`
- Setup flags: `--with-claude`, `--with-opencode`, `--with-codex`, `--with-gemini`, `--with-tmux`, `--with-gitleaks` (opt-in, not opt-out), `--force`, `--verbose`
- Run flags: `--detach`, `--name`, `--skip-detection`
- Config: map-only `env`, single-string `pre_start`, drop `extends: none`, drop `gitleaks_freshness_check`
- Detection: two severities (high = confirm, medium = warn), drop low
- Docs: drop migration-era troubleshooting entries, trim dual-format examples

The net effect: fewer flags, fewer config formats, fewer severity levels, consistent opt-in patterns, and a docs surface that's meaningfully shorter without losing any real capability.
