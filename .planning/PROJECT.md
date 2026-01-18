# Agentic Harness Sandbox

## What This Is

A Docker-based sandbox environment for running agentic AI harnesses (Claude Code, OpenCode) in isolated containers. Users run a single command from any project root to enter a container with their project mounted, harness configurations available, and git working seamlessly with their host identity.

## Core Value

Run agentic AI harnesses in isolated, reproducible environments without polluting the host system.

## Requirements

### Validated

(None yet — ship to validate)

### Active

- [ ] User can enter a shell in the sandbox from any project root
- [ ] User can run Claude Code directly with `<command> claude`
- [ ] User can run OpenCode directly with `<command> opencode`
- [ ] Project folder is mounted and accessible inside container
- [ ] Git user/email from host is available inside container
- [ ] Harness configurations from host are available inside container
- [ ] Container is ephemeral (destroyed on exit, only mounted files persist)
- [ ] Base image is minimal (git, curl, vim, harness dependencies)
- [ ] Projects can extend the environment via Dockerfile.sandbox

### Out of Scope

- Persistent containers / named sessions — ephemeral is the design choice
- Built-in language runtimes (node, python, etc.) in base — projects extend as needed
- GUI/desktop integration — CLI-focused tool
- Windows host support for v1 — Linux/macOS first

## Context

**Target users:** Developers using agentic AI harnesses who want isolation between the harness and their host system.

**Known harnesses:**
- Claude Code — Anthropic's CLI tool for Claude
- OpenCode — Open-source alternative

**Distribution:** Public GitHub repository. Installation method TBD (research needed).

**Naming:** TBD — needs research to find unique, meaningful name that doesn't clash.

## Constraints

- **Docker dependency**: Requires Docker installed on host
- **Linux/macOS first**: Windows support deferred
- **v1 scope**: Basic working — polish and edge cases for v2

## Key Decisions

| Decision | Rationale | Outcome |
|----------|-----------|---------|
| Ephemeral containers | Simpler model, avoid state management, only mounted files persist | — Pending |
| Dockerfile-based extension | Per-project customization without polluting base image | — Pending |
| Command pattern: `<cmd>` shell, `<cmd> <harness>` direct | Intuitive UX, shell is fallback | — Pending |

---
*Last updated: 2025-01-17 after initialization*
