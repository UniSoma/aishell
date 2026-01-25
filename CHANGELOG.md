# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

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
