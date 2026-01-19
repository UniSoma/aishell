# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

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
