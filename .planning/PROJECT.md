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

### Active

(None — planning next milestone)

### Out of Scope

- Persistent containers / named sessions — ephemeral is the design choice
- Windows host support — Linux first, macOS planned for future
- GUI/desktop integration — CLI-focused tool
- SSH agent forwarding — deferred to v1.2
- GPG signing passthrough — deferred to v1.2
- macOS support — deferred to v1.2

## Context

**Shipped:** v1.1 on 2026-01-19 (v1.0 MVP on 2026-01-18)

**Codebase:** 1,461 LOC Bash (aishell + install.sh)
**Tech stack:** Bash, Docker, Debian bookworm-slim base, Node.js 24, Babashka 1.12

**v1.1 additions:**
- `.aishell/run.conf` for per-project runtime configuration
- MOUNTS, ENV, PORTS, DOCKER_ARGS variables
- PRE_START for background sidecar services

**Decisions validated:**
- Ephemeral containers with `--rm` flag work well for the use case
- Dockerfile-based extension via `.aishell/Dockerfile` provides flexibility
- Heredoc embedding for self-contained distribution avoids multi-file complexity
- Explicit `build` command separates concerns better than auto-build
- Shell-style config format (run.conf) native to Bash, no parser dependencies
- Whitelist-based config parsing prevents injection attacks

## Constraints

- **Docker dependency**: Requires Docker installed on host
- **Linux first**: macOS support deferred to v1.1
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

---
*Last updated: 2026-01-19 after v1.1 milestone*
