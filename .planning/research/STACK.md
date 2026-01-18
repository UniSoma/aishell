# Stack Research

**Domain:** Docker sandbox for agentic AI harnesses
**Researched:** 2026-01-17
**Confidence:** HIGH (existing solutions verified, official documentation consulted)

---

## Executive Summary

**CRITICAL FINDING:** Docker Sandboxes requires **Docker Desktop 4.50+** — it does NOT work with Docker Engine on Linux. This eliminates the official solution for users running Docker Engine.

The landscape:

1. **Docker Sandboxes** - Official, but **requires Docker Desktop** (not Docker Engine)
2. **aibox** - Community tool, works with Docker Engine, but **no OpenCode support**
3. **Build custom** - Required if: Docker Engine + OpenCode support needed

**Our situation:** User has Docker Engine on Ubuntu + needs OpenCode support → Build custom is justified.

---

## Existing Solutions Analysis

### 1. Docker Sandboxes (Official) - NOT AVAILABLE FOR DOCKER ENGINE

**Source:** [Docker Sandboxes Documentation](https://docs.docker.com/ai/sandboxes/)

Docker provides official first-party support for AI agent sandboxing:

```bash
docker sandbox run claude
```

**Features:**
- Isolates AI agents from host machine
- Mounts workspace with consistent paths
- Persists state across sessions
- Auto-discovers Git identity
- Includes development tools (Docker CLI, GitHub CLI, Node.js, Go, Python 3, Git, ripgrep, jq)
- Runs as non-root user with sudo access
- Supports Claude Code and Gemini out of the box

**CRITICAL Limitation:**
- **Requires Docker Desktop 4.50+** — DOES NOT work with Docker Engine
- Limited customization of the container environment
- Tied to Docker's update cycle
- No OpenCode support

**Verdict:** NOT an option for Docker Engine users on Linux. If you have Docker Desktop, this works well for Claude Code. Does not support OpenCode.

### 2. aibox (Community) - RECOMMENDED FOR CUSTOMIZATION

**Source:** [GitHub - zzev/aibox](https://github.com/zzev/aibox)

**Features:**
- Multi-tool support (Claude Code, Codex, Gemini)
- Multi-account profile management
- SSH key integration with macOS-to-Linux compatibility
- Per-profile Git configuration
- Resource constraints (2 CPU cores, 4GB RAM)
- Non-root execution with dropped capabilities
- npm package: `npm install -g @zzev/aibox`

**Stack:**
- Node.js 20 Alpine base image
- Docker Compose orchestration
- TOML profile storage

**Limitations:**
- Claude account isolation via undocumented `CLAUDE_CONFIG_DIR` (buggy)
- Gemini cannot isolate accounts
- Node.js/npm dependency for the CLI itself

**Verdict:** If you need multi-account or customization beyond Docker Sandboxes, fork aibox rather than building from scratch.

### 3. claude-code-sandbox (Community)

**Source:** [GitHub - textcortex/claude-code-sandbox](https://github.com/textcortex/claude-code-sandbox)

**Features:**
- Automatic Git branch creation for AI work
- Web-based terminal monitoring (localhost:3456)
- Credential forwarding
- Multi-container support
- Interactive diff review with push/PR creation

**Stack:**
- Ubuntu 22.04 base
- Python-based CLI
- Supports Podman as alternative

**Limitations:**
- Alpha software
- Different use case (review workflow, not interactive development)

**Verdict:** Good for autonomous AI workflows where you want to review before merging. Different paradigm than interactive sandboxing.

### 4. Dev Containers (devcontainer.json)

**Source:** [Dev Containers Specification](https://containers.dev/)

**Features:**
- IDE-integrated (VS Code, JetBrains)
- Per-project configuration
- Rich ecosystem of feature extensions
- Works with devcontainers/cli for non-IDE use

**Limitations:**
- Designed for project-specific environments, not tool-specific
- Heavier setup per project
- MCP integration requires additional configuration inside containers

**Verdict:** Use for project-specific dev environments. Not ideal for "run any AI harness on any project" use case.

---

## Decision Matrix: Build vs Use vs Fork

| Requirement | Docker Sandboxes | aibox | Build Custom |
|-------------|------------------|-------|--------------|
| Docker Engine (Linux) | **NO** | Yes | Yes |
| Docker Desktop (Mac/Windows) | Yes | Yes | Yes |
| OpenCode support | No | No | **Yes** |
| Claude Code support | Yes | Yes | Yes |
| Multi-account profiles | Limited | Yes | Yes |
| Custom base image | No | Possible | Yes |
| No npm dependency | Yes | No | Possible |

**Recommendation:** Build custom because:
1. **Docker Engine on Linux** — Docker Sandboxes not available
2. **OpenCode support** — Not in existing tools
3. **No npm dependency** — Simpler installation

---

## Recommended Stack (If Building Custom)

### CLI Technology: Shell Script (Bash)

**Confidence:** HIGH

**Recommendation:** Use **Bash** for v1, not Go or Rust.

| Factor | Bash | Go | Rust |
|--------|------|-----|------|
| Development speed | Fastest | Medium | Slowest |
| Dependencies | None (bash built-in) | Go toolchain | Rust toolchain |
| Distribution | Single file | Single binary | Single binary |
| Docker integration | Native (docker CLI) | SDK available | SDK available |
| Maintenance | Easy | Medium | Medium |
| Target audience fit | DevOps/SREs | Developers | Developers |

**Rationale:**
1. **Target users already have Docker** - they're comfortable with shell commands
2. **Wrapper around docker commands** - no complex logic needing type safety
3. **Fastest time to working v1** - prioritize shipping over elegance
4. **Easy to modify** - users can hack the script themselves
5. **No build step** - distribute single file via curl

**When to switch to Go/Rust:**
- If CLI complexity grows (subcommands, config parsing, TUI)
- If cross-platform Windows support needed
- If performance-critical operations added

**Anti-pattern to avoid:** Don't use Node.js CLI (like aibox does) when your users already have Docker. Adding npm as a dependency is unnecessary friction.

### Base Docker Image: Ubuntu 22.04 LTS

**Confidence:** HIGH

**Source:** [Docker Best Practices](https://docs.docker.com/build/building/best-practices/)

| Image | Size | Compatibility | Package Availability |
|-------|------|---------------|---------------------|
| Alpine | ~5MB | musl libc issues | Limited |
| Debian Slim | ~22MB | Good | Good |
| Ubuntu 22.04 | ~77MB | Excellent | Excellent |

**Recommendation:** `ubuntu:22.04`

**Rationale:**
1. **AI tools expect Ubuntu** - Claude Code, OpenCode tested primarily on Ubuntu/Debian
2. **glibc compatibility** - no musl libc surprises with Python/Node packages
3. **apt package availability** - everything installs without custom builds
4. **User familiarity** - most developers know Ubuntu
5. **LTS support until 2027** - no urgent migration pressure

**What to install in image:**
```dockerfile
FROM ubuntu:22.04

# Core tools
RUN apt-get update && apt-get install -y \
    git \
    curl \
    wget \
    jq \
    ripgrep \
    sudo \
    openssh-client \
    ca-certificates \
    gnupg \
    && rm -rf /var/lib/apt/lists/*

# Node.js (for Claude Code, OpenCode)
RUN curl -fsSL https://deb.nodesource.com/setup_20.x | bash - \
    && apt-get install -y nodejs

# Python 3 (common dependency)
RUN apt-get update && apt-get install -y python3 python3-pip \
    && rm -rf /var/lib/apt/lists/*

# GitHub CLI
RUN curl -fsSL https://cli.github.com/packages/githubcli-archive-keyring.gpg | dd of=/usr/share/keyrings/githubcli-archive-keyring.gpg \
    && echo "deb [arch=$(dpkg --print-architecture) signed-by=/usr/share/keyrings/githubcli-archive-keyring.gpg] https://cli.github.com/packages stable main" | tee /etc/apt/sources.list.d/github-cli.list > /dev/null \
    && apt-get update && apt-get install -y gh \
    && rm -rf /var/lib/apt/lists/*

# AI harnesses
RUN npm install -g @anthropic-ai/claude-code
# OpenCode: install from their recommended method

# Non-root user
RUN useradd -m -s /bin/bash -u 1000 dev \
    && echo "dev ALL=(ALL) NOPASSWD:ALL" >> /etc/sudoers

USER dev
WORKDIR /workspace
```

### Distribution Method: curl | bash

**Confidence:** HIGH

**Recommendation:** Single-file installation via curl, optional Homebrew tap.

**Primary method:**
```bash
curl -fsSL https://raw.githubusercontent.com/[org]/[repo]/main/install.sh | bash
```

**Secondary method (later):**
```bash
brew install [org]/tap/[toolname]
```

**Rationale:**
1. **Matches Docker install pattern** - users familiar with this
2. **Zero dependencies** - just curl and bash
3. **Immediate updates** - always fetches latest
4. **Homebrew for power users** - but not required for v1

**Installation script does:**
1. Download the main script to `~/.local/bin/[toolname]`
2. Make it executable
3. Add to PATH if needed
4. Optionally download/build Docker image

---

## Harness Configuration Paths

### Claude Code

**Source:** [Claude Code Settings Documentation](https://code.claude.com/docs/en/settings)

**Must mount (read-write):**
| Path | Purpose |
|------|---------|
| `~/.claude/` | User settings, agents, plans, memory |
| `~/.claude.json` | OAuth, MCP servers, preferences |
| Project `.claude/` | Project settings (if exists) |
| Project `.mcp.json` | Project MCP servers (if exists) |
| Project `CLAUDE.md` | Project context file |

**Mount strategy:**
```bash
# Mount user config
-v "$HOME/.claude:/home/dev/.claude"
-v "$HOME/.claude.json:/home/dev/.claude.json"

# Mount project (working directory)
-v "$(pwd):/workspace"
```

**Environment variable:**
- `CLAUDE_CONFIG_DIR` - Custom config directory (use for multi-account)

### OpenCode

**Source:** [OpenCode Configuration Documentation](https://opencode.ai/docs/config/)

**Must mount (read-write):**
| Path | Purpose |
|------|---------|
| `~/.config/opencode/` | Global config, agents, commands, plugins |
| Project `opencode.json` | Project config (if exists) |
| Project `.opencode/` | Project agents, commands, modes, plugins |

**Mount strategy:**
```bash
# Mount user config
-v "$HOME/.config/opencode:/home/dev/.config/opencode"

# Mount project
-v "$(pwd):/workspace"
```

**Environment variables:**
- `OPENCODE_CONFIG` - Custom config file path
- `OPENCODE_CONFIG_DIR` - Custom config directory

### Git/SSH Integration

**Source:** [VS Code Sharing Git Credentials](https://code.visualstudio.com/remote/advancedcontainers/sharing-git-credentials)

**Must mount:**
| Path | Purpose |
|------|---------|
| `~/.gitconfig` | Git user identity |
| `~/.ssh/` (read-only) | SSH keys for git operations |
| SSH agent socket | For passphrase-protected keys |

**Mount strategy (Linux):**
```bash
# Git config
-v "$HOME/.gitconfig:/home/dev/.gitconfig:ro"

# SSH keys (read-only)
-v "$HOME/.ssh:/home/dev/.ssh:ro"

# SSH agent forwarding
-v "$SSH_AUTH_SOCK:/ssh-agent"
-e SSH_AUTH_SOCK=/ssh-agent
```

**Mount strategy (macOS with Docker Desktop):**
```bash
# Docker Desktop's magic SSH socket
--mount type=bind,src=/run/host-services/ssh-auth.sock,target=/run/host-services/ssh-auth.sock
-e SSH_AUTH_SOCK=/run/host-services/ssh-auth.sock
```

---

## Naming Recommendations

### Availability Analysis

| Name | npm | GitHub | Conflict Risk |
|------|-----|--------|---------------|
| harness | Scoped only | Major platform (harness.io) | HIGH |
| devbox | Taken | Taken (jetify-com/devbox) | HIGH |
| aibox | Security hold | Multiple repos | HIGH |
| agentbox | Security hold | Multiple repos | HIGH |
| ai-shell | @builder.io/ai-shell | Multiple repos | HIGH |
| codeforge | Taken | Organization exists | HIGH |
| devpod | Related projects | DevPod exists | HIGH |

### Recommended Names (Likely Available)

**Confidence:** MEDIUM (requires verification at time of creation)

1. **agentcage** - "cage" metaphor for isolation, not found in searches
2. **codecage** - Same metaphor, code-focused
3. **aishell** - May be available as base name (not @builder.io scoped)
4. **devharness** - Combines "dev" with "harness", likely unique
5. **sandshell** - "sandbox" + "shell" portmanteau
6. **harnessbox** - Your project folder name + "box"

**Before finalizing, verify:**
1. `npm view [name]` - Check npm
2. `pip show [name]` - Check PyPI
3. GitHub search for `[name]` in repository names
4. Domain availability (optional)

**Naming guidance:**
- Avoid generic terms (sandbox, shell, dev)
- Make it memorable and typeable
- Consider tab-completion friendliness
- Check for embarrassing meanings in other languages

---

## What NOT to Use

### Technologies to Avoid

| Technology | Why Avoid |
|------------|-----------|
| **Alpine base image** | musl libc causes issues with Python/Node packages; debugging harder |
| **Node.js CLI** | Unnecessary dependency when bash suffices; requires npm on host |
| **Docker Compose (v1)** | CLI needs `docker compose` (v2), not `docker-compose` |
| **Podman without testing** | Compatibility varies; test thoroughly if supporting |
| **Nested Docker (DinD)** | Security concerns; use Docker-out-of-Docker (DooD) pattern |
| **gVisor/Firecracker** | Overkill for dev sandboxing; adds complexity |
| **Kubernetes** | Wrong abstraction level for single-developer tool |

### Anti-Patterns to Avoid

1. **Building from scratch when Docker Sandboxes exists** - Validate that official solution doesn't work first
2. **Privileged containers** - Never use `--privileged`; map only needed capabilities
3. **Root user in container** - Always run as non-root with sudo available
4. **Hardcoded paths** - Respect XDG conventions and environment overrides
5. **Ignoring SSH agent** - Users expect git operations to work seamlessly
6. **Mounting entire home directory** - Security risk; mount only what's needed

---

## Sources

### Official Documentation
- [Docker Sandboxes Documentation](https://docs.docker.com/ai/sandboxes/) - Official Docker AI sandbox feature
- [Claude Code Settings](https://code.claude.com/docs/en/settings) - All Claude Code configuration paths
- [OpenCode Configuration](https://opencode.ai/docs/config/) - OpenCode configuration system
- [Docker Best Practices](https://docs.docker.com/build/building/best-practices/) - Docker image building guidelines
- [VS Code Git Credentials Sharing](https://code.visualstudio.com/remote/advancedcontainers/sharing-git-credentials) - SSH agent forwarding patterns

### Community Projects (Verified)
- [aibox (zzev/aibox)](https://github.com/zzev/aibox) - Node.js-based multi-tool sandbox
- [claude-code-sandbox (textcortex)](https://github.com/textcortex/claude-code-sandbox) - Python-based autonomous workflow tool
- [Dev Containers Specification](https://containers.dev/) - Development container standard

### Comparison Articles
- [Alpine vs Debian Slim Comparison](https://alpinelinuxsupport.com/alpine-linux-vs-debian-slim-lightweight-docker-images-comparison/)
- [CLI Framework Comparison 2025](https://medium.com/@no-non-sense-guy/building-great-clis-in-2025-node-js-vs-go-vs-rust-e8e4bf7ee10e)
- [AI Sandboxes: Daytona vs microsandbox](https://pixeljets.com/blog/ai-sandboxes-daytona-vs-microsandbox/)

---

## Appendix: Quick Start Script Template

If building custom, here's a minimal starting point:

```bash
#!/usr/bin/env bash
set -euo pipefail

# Configuration
IMAGE_NAME="[your-image]:latest"
CONTAINER_NAME="[your-tool]-$(basename $(pwd))"

# Detect SSH agent socket
if [[ "$OSTYPE" == "darwin"* ]]; then
    SSH_SOCK="/run/host-services/ssh-auth.sock"
else
    SSH_SOCK="${SSH_AUTH_SOCK:-}"
fi

# Build mount arguments
MOUNTS=(
    # Project
    -v "$(pwd):/workspace"
    # Git
    -v "$HOME/.gitconfig:/home/dev/.gitconfig:ro"
    # Claude Code
    -v "$HOME/.claude:/home/dev/.claude"
    -v "$HOME/.claude.json:/home/dev/.claude.json"
    # OpenCode
    -v "$HOME/.config/opencode:/home/dev/.config/opencode"
)

# SSH agent forwarding
if [[ -n "$SSH_SOCK" ]]; then
    MOUNTS+=(
        -v "$SSH_SOCK:/ssh-agent"
        -e SSH_AUTH_SOCK=/ssh-agent
    )
fi

# Run
docker run -it --rm \
    --name "$CONTAINER_NAME" \
    --user 1000:1000 \
    --security-opt no-new-privileges \
    -w /workspace \
    "${MOUNTS[@]}" \
    "$IMAGE_NAME" \
    "${@:-bash}"
```

This provides a working foundation to iterate from.
