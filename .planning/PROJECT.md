# Agentic Harness Sandbox

## What This Is

A Docker-based sandbox environment for running agentic AI harnesses (Claude Code, OpenCode) in isolated, ephemeral containers. Users run `aishell build` once to create their environment, then `aishell` to enter a shell or `aishell claude`/`aishell opencode` to run harnesses directly. The container mounts projects at the exact host path, preserves git identity, and supports per-project customization via `.aishell/Dockerfile`.

## Core Value

Run agentic AI harnesses in isolated, reproducible environments without polluting the host system.

## Requirements

### Validated

- ✓ User can enter a shell in the sandbox from any project root — v1.0
- ✓ User can run Claude Code directly with `aishell claude` — v1.0
- ✓ User can run OpenCode directly with `aishell opencode` — v1.0
- ✓ Project folder is mounted and accessible inside container — v1.0
- ✓ Git user/email from host is available inside container — v1.0
- ✓ Harness configurations from host are available inside container — v1.0
- ✓ Container is ephemeral (destroyed on exit, only mounted files persist) — v1.0
- ✓ Base image includes essential tools (git, curl, vim, jq, ripgrep, Node.js, Babashka) — v1.0
- ✓ Projects can extend the environment via .aishell/Dockerfile — v1.0
- ✓ Tool installable via curl|bash one-liner — v1.0
- ✓ Version pinning for harnesses (--claude-version, --opencode-version) — v1.0
- ✓ Explicit build/update workflow with state persistence — v1.0
- ✓ Projects can define additional volume mounts via `.aishell/run.conf` — v1.1
- ✓ Projects can define additional environment variables (passthrough or literal) — v1.1
- ✓ Projects can expose port mappings from container to host — v1.1
- ✓ Projects can specify extra docker run arguments — v1.1
- ✓ Projects can define a pre-start command for sidecar/background services — v1.1
- ✓ Port mapping supports IP binding (e.g., 127.0.0.1:8080:80) — v1.2
- ✓ Version strings validated before use in npm/curl commands — v1.2
- ✓ Temp file cleanup consolidated to single handler — v1.2
- ✓ Signal handling during build phase (clean Ctrl+C) — v1.2
- ✓ HOME directory validated before use — v1.2
- ✓ `--init` flag added for zombie process handling — v1.2
- ✓ Dangerous DOCKER_ARGS patterns warned/documented — v1.2
- ✓ Update check warns when embedded Dockerfile changed since build — v1.2
- ✓ run.conf parsing limits documented (no escaped quotes) — v1.2

### Active

- [ ] Rewrite CLI in Babashka with feature parity to v1.2
- [ ] Cross-platform support (Linux, macOS, Windows)
- [ ] Leverage Babashka built-ins (YAML config, better data structures)
- [ ] Parallel development alongside existing Bash version

### Out of Scope

- Persistent containers / named sessions — ephemeral is the design choice
- Windows host support — Linux first, macOS planned for future
- GUI/desktop integration — CLI-focused tool
- SSH agent forwarding — deferred to v2.0
- GPG signing passthrough — deferred to v2.0
- macOS support — deferred to v2.0

## Current Milestone: v2.0 Babashka Rewrite

**Goal:** Rewrite the CLI in Clojure Babashka for cross-platform support and simpler implementation.

**Target features:**
- Full feature parity with v1.2 (all validated requirements)
- Cross-platform: Linux, macOS, Windows (Docker Desktop or WSL2)
- Leverage Babashka built-ins (YAML config, EDN, better data structures)
- Parallel development until production-ready, then deprecate Bash version

## Context

**Shipped:** v1.2 on 2026-01-19 (v1.1 same day, v1.0 MVP on 2026-01-18)

**Codebase:** 1,655 LOC Bash (aishell + install.sh)
**Tech stack:** Bash, Docker, Debian bookworm-slim base, Node.js 24, Babashka 1.12

**v1.2 additions:**
- Consolidated trap/cleanup infrastructure (single EXIT handler)
- Input validation with defense-in-depth (blocklist + allowlist)
- Dangerous DOCKER_ARGS security warnings
- Dockerfile hash detection for update awareness
- Documentation for run.conf limits and safe.directory behavior

**Decisions validated:**
- Ephemeral containers with `--rm` flag work well for the use case
- Dockerfile-based extension via `.aishell/Dockerfile` provides flexibility
- Heredoc embedding for self-contained distribution avoids multi-file complexity
- Explicit `build` command separates concerns better than auto-build
- Shell-style config format (run.conf) native to Bash, no parser dependencies
- Whitelist-based config parsing prevents injection attacks
- Single trap handler with tracking arrays prevents trap override bugs
- Defense-in-depth validation catches more edge cases than single check

## Constraints

- **Docker dependency**: Requires Docker installed on host
- **Linux first**: macOS support deferred to v2.0
- **No Windows**: Docker on Windows is complex; deferred indefinitely

## Key Decisions

| Decision | Rationale | Outcome |
|----------|-----------|---------|
| Ephemeral containers | Simpler model, avoid state management | ✓ Good |
| Dockerfile-based extension | Per-project customization without polluting base | ✓ Good |
| Command pattern: `aishell` shell, `aishell claude` direct | Intuitive UX | ✓ Good |
| Debian over Alpine | glibc compatibility for harness binaries | ✓ Good |
| gosu for user switching | Proper PID 1 handling, signal forwarding | ✓ Good |
| Dynamic user creation | UID/GID matching without pre-configured users | ✓ Good |
| Heredoc embedding | Self-contained distribution, no separate files | ✓ Good |
| Explicit build command | Clear separation of build vs run | ✓ Good |
| State file for flags | Persistent config without JSON complexity | ✓ Good |
| Multi-stage build | Node.js binaries without full runtime overhead | ✓ Good |
| Shell-style config (run.conf) | Native to Bash, no parser dependencies | ✓ Good |
| Whitelist-based config parsing | Prevents injection attacks, explicit allowed vars | ✓ Good |
| printf for docker flags | Avoids echo -e flag interpretation issues | ✓ Good |
| sh -c for PRE_START | Handles complex commands with arguments properly | ✓ Good |
| PRE_START output to /tmp | Prevents sidecar output from polluting terminal | ✓ Good |
| Single trap cleanup EXIT | Prevents trap override bugs, consolidates cleanup logic | ✓ Good |
| CLEANUP_FILES/CLEANUP_PIDS arrays | Track resources for cleanup without trap per-function | ✓ Good |
| Defense in depth validation | Blocklist + allowlist catches more edge cases | ✓ Good |
| Docker --init for zombie reaping | Simpler than custom PID 1 handling, built into Docker | ✓ Good |
| Default case handlers | Prevents silent ignoring of unknown/malformed options | ✓ Good |
| 12-char sha256 for Dockerfile hash | Matches existing project hash pattern, human-readable | ✓ Good |
| Warn-only for version mismatch | Don't block users who intentionally use older images | ✓ Good |

---
*Last updated: 2026-01-20 after starting v2.0 milestone*
