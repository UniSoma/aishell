# Project Research Summary

**Project:** Docker Sandbox for Agentic AI Harnesses
**Domain:** Developer tooling / Container-based development environments
**Researched:** 2026-01-17
**Confidence:** HIGH

## Executive Summary

This project creates a Docker-based sandbox for running agentic AI harnesses (Claude Code, OpenCode) in isolated containers.

**CRITICAL FINDING:** Docker Sandboxes requires **Docker Desktop 4.50+** — it does NOT work with Docker Engine on Linux. This eliminates the official Docker solution for users running Docker Engine.

**Decision matrix:**

| Requirement | Docker Sandboxes | aibox | Build Custom |
|-------------|------------------|-------|--------------|
| Docker Engine (Linux) | **NO** | Yes | Yes |
| OpenCode support | No | No | **Yes** |

**Our situation:** User runs Docker Engine on Ubuntu + needs OpenCode support → **Building custom is justified.**

If proceeding with custom development, the recommended approach is a **Bash CLI wrapper** invoking Docker with properly configured mounts, using **Ubuntu 22.04** as the base image. The architecture follows a three-layer pattern: CLI wrapper (shell script) -> Docker image (Dockerfile) -> Entrypoint script. This is well-documented, low-risk territory with established patterns from Docker Sandboxes, VS Code devcontainers, and community projects like aibox.

The key risks are **UID/GID mismatch** (files created as wrong user, breaking host workflows), **SSH agent forwarding failures** (git push/pull broken), and **macOS volume performance degradation**. All have documented mitigations. The total estimated build effort for MVP is 4-6 days if proceeding with custom development.

## Key Findings

### Recommended Stack

The stack is intentionally minimal: a Bash CLI script orchestrating Docker with an Ubuntu-based image. This avoids the npm dependency that aibox requires while matching the user's existing Docker familiarity.

**Core technologies:**
- **Bash**: CLI wrapper script (~100-200 lines) - universal availability, no build step, easy to modify
- **Docker**: Container runtime - standard, well-documented, required for the use case
- **Ubuntu 22.04 LTS**: Base image - excellent package availability, glibc compatibility, AI tools tested primarily on Ubuntu/Debian
- **Node.js 20 LTS**: Required for Claude Code - installed in container, not on host

**Existing alternatives (evaluate first):**
- **Docker Sandboxes** (`docker sandbox run claude`): Official Docker feature, requires Docker Desktop
- **aibox** (`npm install -g @zzev/aibox`): Multi-tool support, multi-account profiles, npm dependency

### Expected Features

**Must have (table stakes):**
- Project directory mounting at `/workspace`
- Git user/email passthrough from host `.gitconfig`
- Ephemeral containers (destroyed on exit)
- Single command entry (`harness`, `harness claude`, `harness opencode`)
- Basic CLI tools (git, curl, vim, ripgrep, jq, gh)
- Non-root user with sudo access (UID 1000)

**Should have (differentiators):**
- Multiple harness support (Claude Code, OpenCode) - key differentiator over Docker Sandboxes
- SSH agent forwarding for git push/pull
- Project-specific extensions via `Dockerfile.sandbox`
- Persistent config volumes for harness settings
- Cache mount optimization (npm, cargo, pip caches)

**Defer (v2+):**
- GPG signing passthrough (high complexity, niche use case)
- Network allowlist restrictions (full access acceptable for local dev)
- Session persistence (ephemeral is the core value)
- Background/headless mode

### Architecture Approach

The architecture is a three-component pipeline: a Bash CLI wrapper that parses arguments, detects project context, and constructs `docker run` commands; a Docker image with base tools and harness dependencies; and an entrypoint script that configures the runtime environment and execs to the target (harness or shell). This matches the pattern used by Docker Sandboxes, aibox, and VS Code devcontainers.

**Major components:**
1. **CLI Wrapper** (`bin/sandbox`) - Parse arguments, detect project, build if Dockerfile.sandbox exists, invoke docker run
2. **Base Docker Image** (`docker/Dockerfile`) - Ubuntu 22.04 + Node.js 20 + Claude Code + OpenCode + basic tools
3. **Entrypoint Script** (`docker/entrypoint.sh`) - Configure git identity, validate mounts, exec to target command

**Configuration passthrough:**
| Config | Host Location | Container Location | Mount Type |
|--------|--------------|-------------------|------------|
| Project | `$PWD` | `/workspace` | Bind (rw) |
| Git config | `~/.gitconfig` | `/home/sandbox/.gitconfig` | Bind (ro) |
| Claude Code | `~/.claude` | `/home/sandbox/.claude` | Bind (rw) |
| OpenCode | `~/.config/opencode` | `/home/sandbox/.config/opencode` | Bind (rw) |
| SSH keys | `$SSH_AUTH_SOCK` | `/ssh.socket` | Socket forward |

### Critical Pitfalls

1. **UID/GID Mismatch** - Files created as root unusable on host. **Solve:** Run container with `--user $(id -u):$(id -g)` or create matching user in Dockerfile with build args.

2. **SSH Agent Forwarding Fails** - Git push/pull broken. **Solve:** Mount `$SSH_AUTH_SOCK` on Linux; use `/run/host-services/ssh-auth.sock` on macOS Docker Desktop.

3. **Signal Handling Broken** - Container won't stop cleanly. **Solve:** Use exec form for ENTRYPOINT, use `exec` in shell scripts to replace shell with target process.

4. **Config Security** - Mounting `~/.claude` exposes API keys. **Solve:** Mount read-only where possible, use Docker secrets for sensitive data, document risks.

5. **macOS Volume Performance** - 3-6x slower file operations. **Solve:** Use named volumes for heavy directories (node_modules), document OrbStack as faster alternative.

## Implications for Roadmap

Based on research, suggested phase structure:

### Phase 1: Core Container Foundation
**Rationale:** Establishes the foundational loop - enter container, see files, exit. Everything else depends on this working correctly.
**Delivers:** Working container with project mounted, correct file permissions, basic tools available
**Addresses:** Project mounting, ephemeral containers, non-root user with sudo, basic CLI tools
**Avoids:** UID/GID mismatch (critical), signal handling issues, TTY allocation errors
**Effort estimate:** 1-2 days

### Phase 2: Git Integration
**Rationale:** Git is core to the "working seamlessly" requirement. Without git identity and SSH agent, the sandbox is unusable for real work.
**Delivers:** Git commits with correct author, SSH push/pull to private repos
**Addresses:** Git config passthrough, SSH agent forwarding, safe.directory configuration
**Avoids:** "Please tell me who you are" errors, "dubious ownership" errors, permission denied on git operations
**Effort estimate:** 1-2 days

### Phase 3: Harness Integration
**Rationale:** With core container and git working, add the actual AI harnesses. Claude Code first (more users), OpenCode second (key differentiator).
**Delivers:** `sandbox claude` launches Claude Code; `sandbox opencode` launches OpenCode
**Addresses:** Harness config mounting, CLI subcommand parsing, harness-specific dependencies
**Uses:** Node.js 20 from stack, entrypoint command switching from architecture
**Effort estimate:** 1-2 days

### Phase 4: Project Customization
**Rationale:** Allow projects to extend base image with their own dependencies without modifying the core tool.
**Delivers:** Dockerfile.sandbox detection and building, cache mount optimization
**Addresses:** Per-project extension mechanism, build caching
**Avoids:** Fat base image anti-pattern, rebuild on every change
**Effort estimate:** 0.5-1 day

### Phase 5: Polish and Documentation
**Rationale:** Usability improvements that make the tool pleasant to use.
**Delivers:** Installation script, timezone handling, resource limit documentation, macOS performance guidance
**Addresses:** Distribution method (curl | bash), minor pitfalls (timezone, DNS)
**Effort estimate:** 1 day

### Phase Ordering Rationale

- **Dependencies:** Phase 1 must complete before anything else works. Phase 2 (git) depends on Phase 1 (container). Phase 3 (harnesses) depends on both. Phase 4 (extensions) depends on Phase 3.
- **Risk reduction:** The highest-risk pitfalls (UID/GID, SSH agent) are addressed in Phases 1-2. If these fail, we discover early.
- **Value delivery:** Each phase produces a usable increment. After Phase 1, users have a functional container. After Phase 2, they can commit. After Phase 3, they have the full value proposition.

### Research Flags

**Phases needing standard patterns (skip research-phase):**
- **Phase 1:** Well-documented Docker patterns, multiple reference implementations
- **Phase 2:** VS Code devcontainer docs provide complete SSH/git integration patterns
- **Phase 4:** Straightforward conditional build logic

**Phases with potential complexity:**
- **Phase 3 (OpenCode):** OpenCode installation and configuration less documented than Claude Code. May need to verify installation method and config paths during implementation.
- **Phase 5 (macOS):** macOS-specific socket paths and performance mitigations need testing on actual macOS system.

## Confidence Assessment

| Area | Confidence | Notes |
|------|------------|-------|
| Stack | HIGH | Official Docker docs, multiple verified community projects |
| Features | HIGH | Cross-referenced Docker Sandboxes, devcontainer spec, aibox |
| Architecture | HIGH | Pattern consistent across Docker Sandboxes, aibox, devcontainers |
| Pitfalls | HIGH | Multiple authoritative sources, Claude Code-specific docs available |

**Overall confidence:** HIGH

### Gaps to Address

- **OpenCode installation:** Verify exact installation method and dependencies during Phase 3. OpenCode docs less comprehensive than Claude Code.
- **macOS testing:** SSH agent socket path and volume performance mitigations need validation on actual macOS hardware.
- **GPG signing:** Explicitly deferred. If users request, will need dedicated research (high complexity, cross-platform issues).
- **Podman support:** Not researched. If needed, would require separate investigation of compatibility differences.

## Build vs Use Decision

**Docker Sandboxes — NOT AVAILABLE:**
- Requires Docker Desktop 4.50+ — does NOT work with Docker Engine on Linux
- Not an option for this project

**aibox — POSSIBLE BUT LIMITED:**
- Works with Docker Engine
- No OpenCode support
- Requires npm on host

**Build custom — JUSTIFIED:**
- Works with Docker Engine on Linux
- OpenCode support (key differentiator)
- No npm/Node.js dependency on host
- Full control over the tool

**Decision: Build custom** is the right choice for this project.

## Sources

### Primary (HIGH confidence)
- [Docker Sandboxes Documentation](https://docs.docker.com/ai/sandboxes/) - Official Docker AI sandbox feature
- [Claude Code Settings](https://code.claude.com/docs/en/settings) - Configuration paths
- [Claude Code DevContainer](https://code.claude.com/docs/en/devcontainer) - Reference implementation
- [VS Code Git Credentials](https://code.visualstudio.com/remote/advancedcontainers/sharing-git-credentials) - SSH/git patterns
- [Docker Bind Mounts](https://docs.docker.com/engine/storage/bind-mounts/) - Mount best practices
- [Docker ENTRYPOINT Best Practices](https://www.docker.com/blog/docker-best-practices-choosing-between-run-cmd-and-entrypoint/) - Signal handling

### Secondary (MEDIUM confidence)
- [aibox (zzev/aibox)](https://github.com/zzev/aibox) - Community multi-tool sandbox
- [OpenCode Configuration](https://opencode.ai/docs/config/) - OpenCode config paths
- [Docker macOS Performance 2025](https://www.paolomainardi.com/posts/docker-performance-macos-2025/) - Volume performance

### Tertiary (LOW confidence)
- Various Medium articles on AI agent sandboxing - Community patterns, needs validation

---
*Research completed: 2026-01-17*
*Ready for roadmap: yes*
