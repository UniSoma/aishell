# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

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
