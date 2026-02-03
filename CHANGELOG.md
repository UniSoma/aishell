# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

## [2.9.2] - 2026-02-03

### Changed
- **Renamed `build` to `setup`**: The `aishell build` command is now `aishell setup` across the CLI, help text, error messages, and documentation. Internal Docker build logic is unchanged.

### Fixed
- Correct `aishell shell` to `aishell` in install script

### Docs
- Add llm.txt and UX analysis artifacts
- Restructure README for clearer reading order and tighter prose

## [2.9.1] - 2026-02-03

### Added
- **Harness alias injection**: Typing `claude`, `opencode`, etc. in an interactive container session now applies the same `harness_args` from config as `aishell claude` would
  - Claude alias always includes `--dangerously-skip-permissions`
  - Aliases persist across tmux new-window via profile.d sourcing
  - Bypass with `command claude` or `\claude`

### Fixed
- `pre_start` config now uses array format for consistency with other config fields
- Bypass tmux wrapping for `exec` and `gitleaks` commands (skip-tmux guard)
- Resurrect plugin not loading: `populate-volume` now uses pre-computed `tmux-plugins` from state instead of recalculating from raw config
- Attach command defaults to `harness` session name (was hardcoded to `main` in CLI parser)
- Nil guard added to `parse-resurrect-config` to prevent spurious warnings

## [2.9.0] - 2026-02-03

Make tmux fully opt-in with plugin management, user config mounting, and session persistence.

### Changed
- **tmux is now opt-in**: `aishell build --with-tmux` enables tmux; default behavior is no tmux (previously always enabled since v2.7.0)
- **Session name**: tmux session renamed from `main` to `harness` for project naming consistency
- **Attach command**: `aishell attach` now connects to `harness` session by default

### Added
- **`--with-tmux` build flag**: Opt-in to tmux integration at build time, stored in state.edn
- **tmux config mounting**: User's `~/.tmux.conf` auto-mounted read-only into container when tmux is enabled
- **Plugin management**: Declare tmux plugins in `.aishell/config.yaml` under `tmux.plugins` list
  - Format: `owner/repo` (e.g., `tmux-plugins/tmux-sensible`)
  - TPM installed into harness volume at `/tools/tmux/plugins/tpm`
  - Plugins installed non-interactively during `aishell build` / `aishell update`
  - Plugin format validated during config parsing
  - Runtime bridging via symlink from volume to `~/.tmux/plugins`
  - TPM initialization appended to tmux config at container startup
- **tmux-resurrect support**: `tmux.resurrect` config section for session state persistence
  - `resurrect: true` enables with sensible defaults (layout only, no process restoration)
  - Per-project state directory at `~/.aishell/resurrect/{project-hash}/`
  - tmux-resurrect plugin auto-injected when resurrect enabled
  - `restore_processes: true` to opt-in to full process restoration
- **Migration warning**: Users upgrading from v2.7-2.8 see one-time warning about tmux behavior change
- **Attach validation**: `aishell attach` shows helpful error when tmux is not enabled

### Fixed
- Missing `~/.tmux.conf` on host handled gracefully (no error, just skipped)

### Internal
- State schema: added `:with-tmux` field
- Volume hash includes tmux state for proper cache invalidation
- `WITH_TMUX` env var passed to container (simpler than mounting state.edn)
- Migration detection uses state schema shape (presence without `:harness-volume-hash`)
- Marker file at `~/.aishell/.migration-v2.9-warned` prevents repeat warnings

## [2.8.1] - 2026-02-01

### Added
- Set terminal window title (OSC 2) before foreground container launch for easier window identification in Alt+Tab

## [2.8.0] - 2026-02-01

Split monolithic base image into stable foundation layer and volume-mounted harness tools.
Harness version updates no longer force multi-gigabyte extension rebuilds.

### Changed
- **Architecture:** 2-tier system — foundation image (Debian, Node.js, system tools) + harness volume (npm packages, binaries)
- **`aishell update`:** Now refreshes harness volume only (delete + recreate for clean slate). Use `--force` to also rebuild foundation image.
- **Image tag:** `aishell:base` renamed to `aishell:foundation` (backward-compat error message guides migration)
- **Extension cache:** Invalidation now uses foundation image ID, not base image ID

### Added
- **`aishell volumes`** command to list harness volumes with active/orphaned status
- **`aishell volumes prune`** to remove orphaned harness volumes
- **`aishell update --force`** flag to rebuild foundation image alongside volume refresh
- **OpenCode binary installation** via GitHub releases (separate from npm harnesses)
- **Volume-based harness tools** mounted read-only at `/tools` in containers
- **`/etc/profile.d/aishell.sh`** for tmux new-window environment consistency
- Harness volume labels for metadata (hash, version, harness list)

### Fixed
- Harness version updates no longer invalidate Docker extension cache
- tmux new-window sessions now inherit harness tool PATH correctly

### Internal
- State schema: added `foundation-hash`, `harness-volume-hash`, `harness-volume-name` fields
- `dockerfile-hash` deprecated in favor of `foundation-hash` (still written for backward compat)
- Foundation image builds only on Dockerfile template changes
- Per-project volumes keyed by harness combination hash (shared across identical configs)

## [2.7.1] - 2026-01-31

### Added

- **Shell access flag**: `aishell attach --name <name> --shell` opens a bash shell in a running container
  - Creates or reattaches to a tmux session named `shell` using `tmux new-session -A`
  - Mutually exclusive with `--session` flag

### Fixed

- **Attach color support**: Pass `COLORTERM` env var in attach commands, matching `docker run` behavior
- **Attach bashrc setup**: Ensure `/etc/bash.aishell` is sourced when attaching to containers started with harness commands (e.g., `claude`), restoring custom prompt and color aliases

## [2.7.0] - 2026-01-31

### Added

- **tmux integration**: All container modes auto-start inside tmux session `main`
  - Enables detach/reattach workflow for long-running AI agents
  - TERM validation with automatic fallback to xterm-256color for unsupported terminals (e.g., Ghostty)
  - gosu runs before tmux to ensure user-owned socket (no permission errors)

- **Named containers**: Deterministic container naming with `aishell-{project-hash}-{name}` format
  - Project hash is first 8 chars of SHA-256 of project directory path
  - Default name equals harness name (`claude`, `opencode`, `codex`, `gemini`, `shell`)
  - Override with `--name <name>` flag (e.g., `aishell claude --name reviewer`)

- **Detached mode**: `--detach` / `-d` flag for background container execution
  - `aishell claude --detach` starts container in background and prints attach/stop commands
  - `--rm` flag preserved (containers auto-cleanup when stopped)

- **Conflict detection**: Pre-flight checks before container launch
  - Running container with same name produces clear error with attach guidance
  - Stopped container with same name auto-removed before new launch

- **Attach command**: `aishell attach --name <name>` reconnects to running containers
  - `--session <session>` flag for specific tmux sessions
  - Three-layer validation: TTY check, container state, session existence
  - User-friendly error messages with actionable guidance

- **PS command**: `aishell ps` lists running containers for current project
  - Table output with NAME (short form), STATUS, and CREATED columns
  - Project-scoped filtering by project hash
  - Helpful empty state message with examples

- **Pre-flight check command**: `aishell check` validates configuration, Docker availability, and image state before running

### Fixed

- **Glob pattern matching in allowlist**: Replaced broken `fs/match` with Java NIO `PathMatcher` for correct single-path glob evaluation

## [2.5.0] - 2026-01-26

### Added

- **One-off command execution**: Run commands in container without interactive shell
  - `aishell exec <command>` runs command and exits
  - Automatic TTY detection (works in terminals and pipes/scripts)
  - Exit code propagation from container to host
  - Skips detection warnings and pre_start hooks for fast execution
  - Piping support: `echo "test" | aishell exec cat`

- **Dynamic help output**: Help shows only installed harness commands
  - Reads build state to determine which harnesses are available
  - Shows all harnesses when no build exists (aids discoverability)
  - Gitleaks always shown (may work via host installation)

- **Conditional Gitleaks installation**: `--without-gitleaks` build flag
  - Skip Gitleaks installation to reduce image size (~15MB savings)
  - State tracked as `:with-gitleaks` in `~/.aishell/state.edn`
  - `aishell gitleaks` may still work via host PATH

- **Pre-start list format**: YAML list syntax for `pre_start` config
  - List items joined with ` && ` to form single command
  - String format remains supported (backwards compatible)
  - Empty list items automatically filtered

### Changed

- CONFIGURATION.md updated with pre_start list format and build options documentation
- TROUBLESHOOTING.md updated with exec command issues section
- README.md updated with exec command section and examples

## [2.4.0] - 2026-01-25

### Added

- **OpenAI Codex CLI support**: Run OpenAI Codex CLI in the sandbox
  - `aishell build --with-codex` to include Codex CLI in image
  - `aishell codex [args]` to run Codex directly
  - Version pinning with `--with-codex=VERSION`
  - `~/.codex/` config directory mounted automatically
  - `CODEX_API_KEY` environment variable passed through
  - `harness_args.codex` for default arguments

- **Google Gemini CLI support**: Run Google Gemini CLI in the sandbox
  - `aishell build --with-gemini` to include Gemini CLI in image
  - `aishell gemini [args]` to run Gemini directly
  - Version pinning with `--with-gemini=VERSION`
  - `~/.gemini/` config directory mounted automatically
  - `GEMINI_API_KEY`, `GOOGLE_API_KEY` environment variables passed through
  - `GOOGLE_APPLICATION_CREDENTIALS` mounted for Vertex AI authentication
  - `harness_args.gemini` for default arguments

- **Comprehensive documentation suite** (5 new docs, 3,660+ lines):
  - `docs/ARCHITECTURE.md`: System design, data flow, namespace responsibilities
  - `docs/CONFIGURATION.md`: Complete config.yaml reference with merge strategies
  - `docs/HARNESSES.md`: Setup guide for all 4 harnesses with auth patterns
  - `docs/TROUBLESHOOTING.md`: Common issues organized by symptom
  - `docs/DEVELOPMENT.md`: Guide for adding new harnesses (7-step checklist)

### Changed

- Build state now tracks Codex and Gemini installation status and versions
- README updated with multi-harness documentation, authentication guide, and environment variables table
- Help text includes `codex` and `gemini` commands

## [2.3.0] - 2026-01-24

### Added

- **Sensitive file detection**: Warnings before AI agents access potentially sensitive files
  - Severity tiers (high/medium/low) with appropriate UX per level
  - High-severity requires confirmation in interactive mode, `--unsafe` flag in CI
  - Medium/low severity auto-proceeds with informational warnings
- **Filename-based pattern detection** for 20+ sensitive file types:
  - Environment files: `.env`, `.env.local`, `.env.production`, `.envrc`
  - SSH keys: `id_rsa`, `id_dsa`, `id_ed25519`, `id_ecdsa`, `*.ppk`
  - Key containers: `*.p12`, `*.pfx`, `*.jks`, `*.keystore`
  - PEM/key files: `*.pem`, `*.key`
  - Cloud credentials: `application_default_credentials.json`, `terraform.tfstate*`, kubeconfig
  - Package manager credentials: `.pypirc`, `.netrc`
  - Tool configs: `.npmrc`, `.yarnrc.yml`, `.docker/config.json`, `.terraformrc`
  - Rails secrets: `master.key`, `credentials*.yml.enc`
  - Secret pattern files: `secret.*`, `secrets.*`, `vault.*`, `token.*`, `apikey.*`, `private.*`
  - Database credentials: `.pgpass`, `.my.cnf`, `database.yml`
- **Gitleaks integration**: `aishell gitleaks` command for deep content-based secret scanning
  - Gitleaks v8.30.0 binary included in base container image
  - All gitleaks arguments passed through (e.g., `aishell gitleaks detect --verbose`)
- **Scan freshness tracking**: Warnings when gitleaks hasn't been run recently
  - Default threshold: 7 days (configurable via `gitleaks_freshness_days`)
  - Disable with `gitleaks_freshness_check: false` in config
- **Gitignore awareness**: High-severity files not in `.gitignore` show "(risk: may be committed)"
- **Custom detection patterns**: Add patterns via `detection.custom_patterns` in config.yaml
- **Allowlist**: Suppress false positives via `detection.allowlist` with path and reason

### Changed

- Base container image now includes Gitleaks v8.30.0 (multi-arch: amd64, arm64, armv7)

## [2.2.0] - 2026-01-22

### Added

- Default harness arguments with `harness_args` config key - define arguments injected at every harness invocation

## [2.1.0] - 2026-01-22

### Added

- Config inheritance with `extends` key for layered configuration merge strategy

## [2.0.1] - 2026-01-21

### Fixed

- Run pre-start commands as developer user (fixes cache ownership issues)
- Suppress Claude Code npm installation warning

## [2.0.0] - 2026-01-21

### BREAKING CHANGES

- **Babashka required**: aishell now requires Babashka (https://babashka.org) to run
- **Config format changed**: `.aishell/run.conf` (bash) replaced by `.aishell/config.yaml` (YAML)
- **State format changed**: Build state stored in `~/.aishell/state.edn` (EDN format)

### Added

- Complete rewrite in Clojure Babashka for cross-platform support
- YAML configuration with richer data structures
- Single-flag version syntax: `--with-claude=1.0.0` (replaces `--with-claude --claude-version=1.0.0`)
- Levenshtein-based command suggestions for typos
- XDG Base Directory support for state files
- Colored output with NO_COLOR and TERM detection

### Changed

- Distribution via uberscript (single .clj file) instead of bash script
- Installation location unchanged: `~/.local/bin/aishell`

### Migration

1. Uninstall old version: `rm ~/.local/bin/aishell`
2. Install Babashka: https://babashka.org
3. Install new version: `curl -fsSL https://raw.githubusercontent.com/UniSoma/aishell/main/install.sh | bash`
4. Migrate config (if using):
   - Convert `.aishell/run.conf` to `.aishell/config.yaml`
   - See README.md for YAML format
5. `.aishell/Dockerfile` remains compatible, no changes needed

## [1.2.0] - 2026-01-19

### Added

- Consolidated trap/cleanup infrastructure with single EXIT handler
- `register_cleanup()` and `track_pid()` helpers for resource tracking
- Version string validation with semver regex and shell metachar blocklist
- `validate_home()` with passwd lookup fallback and /tmp fallback
- Port mapping IP binding support (e.g., `127.0.0.1:8080:80`)
- Security warnings for dangerous DOCKER_ARGS (--privileged, docker.sock)
- Dockerfile hash detection with runtime mismatch warnings
- `--init` flag for zombie process reaping via tini

### Changed

- Trap handlers consolidated to single EXIT handler (prevents override bugs)
- Default case handlers added to `build` and `update` commands (rejects unknown options)

### Fixed

- Shell injection via version flags now blocked
- Unknown options no longer silently ignored
- Ctrl+C during build now cleans up properly

### Documentation

- run.conf parsing limitations documented (no escaped quotes, one value per line)
- safe.directory behavior documented (may modify host gitconfig)

## [1.1.0] - 2026-01-19

### Added

- Per-project runtime configuration via `.aishell/run.conf`
- `MOUNTS` variable for additional volume mounts with `$HOME` expansion
- `ENV` variable for environment variables (passthrough and literal syntax)
- `PORTS` variable for port mappings (host:container format)
- `DOCKER_ARGS` variable for extra docker run arguments
- `PRE_START` variable for background pre-start commands (sidecar services)
- Runtime Configuration section in `--help` output

### Changed

- Config parser uses whitelist-based validation for security

## [1.0.0] - 2026-01-18

### Added

- `aishell build` command with `--with-claude` and `--with-opencode` flags
- `aishell update` command to rebuild with latest versions
- `aishell claude` and `aishell opencode` commands to run harnesses directly
- Version pinning with `--claude-version` and `--opencode-version` flags
- Per-project state tracking in `~/.local/state/aishell/builds/`
- Project customization via `.aishell/Dockerfile`
- Git identity passthrough (preserves author/committer in container)
- Automatic config mounting (`~/.claude`, `~/.config/opencode`)
- API key passthrough for Anthropic, OpenAI, Gemini, Groq, AWS, Azure, GCP
- Base image with Node.js LTS, Babashka, and common tools
- Installer script for single-command installation
