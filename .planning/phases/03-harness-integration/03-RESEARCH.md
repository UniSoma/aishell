# Phase 3: Harness Integration - Research

**Researched:** 2026-01-17
**Domain:** AI harness installation, configuration mounting, CLI integration
**Confidence:** HIGH

## Summary

This research covers installing and configuring Claude Code and OpenCode harnesses in a Docker container, mounting their configuration directories from the host, and implementing subcommand-based invocation. Both harnesses can be installed in Debian bookworm-slim with their respective runtimes (Node.js for Claude Code, Go binary for OpenCode).

Key findings:
- Claude Code native installation goes to `~/.claude/bin/claude` and requires Node.js 18+ only for the deprecated npm method
- OpenCode installs as a single Go binary to `~/.opencode/bin/opencode` or `~/.local/bin/opencode`
- Both harnesses support multiple environment variables for API keys and configuration
- Config directories need read-write mounts to allow harnesses to modify their settings

**Primary recommendation:** Use native installers for both harnesses in the Dockerfile with build-time flags (`--with-claude`, `--with-opencode`). Mount config directories read-write and pass through API key environment variables when set on host.

## Standard Stack

The established tools and patterns for this domain:

### Core Components

| Component | Version/Source | Purpose | Why Standard |
|-----------|----------------|---------|--------------|
| Claude Code | Native binary from claude.ai/install.sh | AI coding assistant | Official Anthropic installer, auto-updates |
| OpenCode | Binary from opencode.ai/install | AI coding agent | Official installer, supports multiple providers |
| Node.js | 22 LTS (for npm fallback only) | Claude Code runtime (if npm used) | Current active LTS, native method preferred |

### Installation Methods

**Claude Code (Recommended):**
```bash
# Native installation (recommended by Anthropic)
curl -fsSL https://claude.ai/install.sh | bash
# Installs to ~/.claude/bin/claude
```

**OpenCode:**
```bash
# Native installation
curl -fsSL https://opencode.ai/install | bash
# Installs to ~/.opencode/bin/opencode (default)
```

### Alternatives Considered

| Instead of | Could Use | Tradeoff |
|------------|-----------|----------|
| Native Claude Code | npm install -g @anthropic-ai/claude-code | Deprecated by Anthropic, requires Node.js 18+, permission issues with sudo |
| Native OpenCode | go install github.com/opencode-ai/opencode@latest | Requires Go toolchain in image, larger image size |
| Individual installers | Pre-built combined image | Less flexibility for version control |

## Architecture Patterns

### Recommended Project Structure

```
# Script changes
aishell                    # Modified: add subcommand parsing
entrypoint.sh              # Modified: handle harness commands

# Dockerfile changes
Dockerfile                 # Modified: add harness installation (conditional)

# New files (optional)
Dockerfile.claude          # Claude Code variant
Dockerfile.opencode        # OpenCode variant
```

### Pattern 1: Subcommand Dispatch

**What:** Parse first argument as subcommand, dispatch to appropriate harness or shell
**When to use:** CLI with multiple modes (harness selection)

```bash
# In aishell script
main() {
    local subcommand="${1:-shell}"
    shift 2>/dev/null || true

    case "$subcommand" in
        claude)
            # Pass remaining args to claude inside container
            exec docker run ... "$IMAGE_NAME" claude "$@"
            ;;
        opencode)
            exec docker run ... "$IMAGE_NAME" opencode "$@"
            ;;
        shell|"")
            exec docker run ... "$IMAGE_NAME" /bin/bash
            ;;
        -h|--help)
            usage
            ;;
        *)
            # Treat as shell command to run
            exec docker run ... "$IMAGE_NAME" "$subcommand" "$@"
            ;;
    esac
}
```

### Pattern 2: Conditional Harness Installation

**What:** Use Docker build args to conditionally install harnesses
**When to use:** Optional components that should not bloat base image

```dockerfile
# In Dockerfile
ARG WITH_CLAUDE=false
ARG WITH_OPENCODE=false
ARG CLAUDE_VERSION=latest
ARG OPENCODE_VERSION=latest

# Install Claude Code if requested
RUN if [ "$WITH_CLAUDE" = "true" ]; then \
        curl -fsSL https://claude.ai/install.sh | CLAUDE_INSTALL_CHANNEL=${CLAUDE_VERSION} bash; \
    fi

# Install OpenCode if requested
RUN if [ "$WITH_OPENCODE" = "true" ]; then \
        curl -fsSL https://opencode.ai/install | bash; \
    fi
```

### Pattern 3: Config Directory Mounting

**What:** Mount host config directories read-write into container
**When to use:** Harnesses that need to read/write their own config

```bash
# In aishell script
build_docker_args() {
    local -a args=(...)

    # Mount Claude Code config if exists
    if [[ -d "$HOME/.claude" ]]; then
        args+=(-v "$HOME/.claude:$HOME/.claude")
    fi
    if [[ -f "$HOME/.claude.json" ]]; then
        args+=(-v "$HOME/.claude.json:$HOME/.claude.json")
    fi

    # Mount OpenCode config if exists
    if [[ -d "$HOME/.config/opencode" ]]; then
        args+=(-v "$HOME/.config/opencode:$HOME/.config/opencode")
    fi

    echo "${args[@]}"
}
```

### Pattern 4: Environment Variable Passthrough

**What:** Pass API key environment variables from host to container only when set
**When to use:** Avoid passing empty variables that could override config

```bash
# In aishell script
add_env_if_set() {
    local var_name="$1"
    if [[ -n "${!var_name:-}" ]]; then
        echo "-e" "$var_name=${!var_name}"
    fi
}

# Common API keys to pass through
for var in ANTHROPIC_API_KEY OPENAI_API_KEY GEMINI_API_KEY GROQ_API_KEY \
           AWS_ACCESS_KEY_ID AWS_SECRET_ACCESS_KEY AWS_REGION \
           AZURE_OPENAI_API_KEY AZURE_OPENAI_ENDPOINT; do
    docker_args+=($(add_env_if_set "$var"))
done
```

### Anti-Patterns to Avoid

- **Installing both harnesses unconditionally:** Wastes space and build time for users who only need one
- **Using `sudo npm install -g`:** Causes permission issues and security risks
- **Mounting config directories read-only:** Harnesses need to write session data, MCP configs, etc.
- **Hardcoding API keys in Dockerfile:** Security risk; always use environment variables
- **Passing empty environment variables:** Can override legitimate config with empty strings

## Don't Hand-Roll

Problems that look simple but have existing solutions:

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| Claude Code installation | curl + wget + untar | Native installer script | Handles architecture detection, PATH setup, auto-updates |
| OpenCode installation | go install in container | Native installer script | Avoids Go toolchain in final image |
| API key management | Custom config parsing | Environment variable passthrough | Standard pattern, works with existing secrets management |
| User authentication | Custom token storage | Let harnesses handle it | OAuth flows are complex, harnesses manage session in ~/.claude.json |
| Version selection | Manual download URLs | Install script with channel flags | Maintained by harness authors |

**Key insight:** Both harnesses have mature installation scripts that handle platform detection, binary placement, and PATH configuration. Using them directly is more reliable than reimplementing.

## Common Pitfalls

### Pitfall 1: Claude Code Authentication Conflicts

**What goes wrong:** Container shows "Auth conflict" warning when both OAuth token and ANTHROPIC_API_KEY are present
**Why it happens:** Claude Code detects both authentication methods and warns about precedence
**How to avoid:** Document that users should use ONE method: either log in via `claude /login` OR set ANTHROPIC_API_KEY
**Warning signs:** Unexpected billing (API vs subscription), "Auth conflict" warnings on startup

### Pitfall 2: Missing Config Directories

**What goes wrong:** Docker mount fails with error when host directory doesn't exist
**Why it happens:** Bind mounts require source path to exist (unlike volume mounts)
**How to avoid:** Only add mount flags if directory/file exists on host
**Warning signs:** `docker run` error about missing mount source

### Pitfall 3: PATH Not Set for Harness Binaries

**What goes wrong:** `claude: command not found` or `opencode: command not found` inside container
**Why it happens:** Native installers add to ~/.bashrc but entrypoint doesn't source it before exec
**How to avoid:** Add harness bin directories to PATH in entrypoint or Dockerfile ENV
**Warning signs:** Harness works in interactive shell but fails when invoked directly

### Pitfall 4: Permission Denied on Config Files

**What goes wrong:** Harness cannot write to mounted config directory
**Why it happens:** UID/GID mismatch between host and container user
**How to avoid:** Already handled by dynamic user creation (Phase 1), ensure same approach for config mounts
**Warning signs:** "Permission denied" errors when harness tries to save settings

### Pitfall 5: Harness Update in Read-Only Container

**What goes wrong:** Claude Code auto-updater fails in ephemeral container
**Why it happens:** Container filesystem is ephemeral, updates are lost
**How to avoid:** Document that `aishell update` rebuilds image with latest harness versions
**Warning signs:** Update notifications that never persist, repeated update attempts

## Code Examples

Verified patterns from research:

### Claude Code Config Directory Structure
```
~/.claude/                    # User config directory
├── settings.json             # User-level settings
├── settings.local.json       # User-specific local settings (optional)
├── bin/                      # Native binary installation
│   └── claude                # Main binary
├── agents/                   # User subagents
│   └── *.md                  # Custom agent definitions
├── commands/                 # Custom slash commands
│   └── *.md                  # Command definitions
└── CLAUDE.md                 # User-level memory/instructions

~/.claude.json                # Legacy config, OAuth session, MCP configs
.claude/                      # Project-level config (in project dir)
├── settings.json             # Project settings (version controlled)
├── settings.local.json       # Project local settings (gitignored)
└── commands/                 # Project custom commands
.mcp.json                     # Project MCP server config
```

### OpenCode Config Directory Structure
```
~/.config/opencode/           # XDG-compliant config directory
├── opencode.json             # Global configuration
├── agent/                    # Custom agent definitions
├── command/                  # Custom command definitions
└── plugin/                   # Plugin files

~/.opencode/                  # Alternative installation location
└── bin/
    └── opencode              # Main binary

~/.local/share/opencode/      # Data directory
└── auth.json                 # Stored credentials from /connect
```

### Environment Variables - Claude Code
```bash
# Authentication
ANTHROPIC_API_KEY             # API key (pay-as-you-go billing)
ANTHROPIC_AUTH_TOKEN          # Custom Authorization header
ANTHROPIC_FOUNDRY_API_KEY     # Microsoft Foundry
AWS_BEARER_TOKEN_BEDROCK      # AWS Bedrock

# Model Configuration
ANTHROPIC_MODEL               # Override default model
CLAUDE_CODE_SUBAGENT_MODEL    # Model for subagents

# Behavior
DISABLE_AUTOUPDATER=1         # Disable auto-updates (recommended for containers)
DISABLE_TELEMETRY=1           # Opt out of analytics
BASH_DEFAULT_TIMEOUT_MS       # Bash command timeout

# Configuration
CLAUDE_CONFIG_DIR             # Custom config directory location
```

### Environment Variables - OpenCode
```bash
# Configuration
OPENCODE_CONFIG               # Path to custom config file
OPENCODE_CONFIG_DIR           # Custom config directory
OPENCODE_CONFIG_CONTENT       # Inline configuration (JSON)
OPENCODE_MODEL                # Override default model

# API Keys (provider-specific)
ANTHROPIC_API_KEY             # Anthropic/Claude models
OPENAI_API_KEY                # OpenAI/GPT models
GEMINI_API_KEY                # Google Gemini
GROQ_API_KEY                  # Groq
GITHUB_TOKEN                  # GitHub Copilot

# AWS Bedrock
AWS_ACCESS_KEY_ID
AWS_SECRET_ACCESS_KEY
AWS_REGION
AWS_PROFILE

# Azure
AZURE_OPENAI_API_KEY
AZURE_OPENAI_ENDPOINT
AZURE_RESOURCE_NAME

# Google Cloud
GOOGLE_CLOUD_PROJECT
GOOGLE_CLOUD_LOCATION
GOOGLE_APPLICATION_CREDENTIALS
```

### Dockerfile Pattern for Optional Harnesses
```dockerfile
# Build arguments for optional harness installation
ARG WITH_CLAUDE=false
ARG WITH_OPENCODE=false

# Install Claude Code (native binary)
RUN if [ "$WITH_CLAUDE" = "true" ]; then \
        export DISABLE_AUTOUPDATER=1 && \
        curl -fsSL https://claude.ai/install.sh | bash && \
        # Make binary available system-wide
        ln -sf /root/.claude/bin/claude /usr/local/bin/claude; \
    fi

# Install OpenCode (native binary)
RUN if [ "$WITH_OPENCODE" = "true" ]; then \
        curl -fsSL https://opencode.ai/install | bash && \
        # Make binary available system-wide
        ln -sf /root/.opencode/bin/opencode /usr/local/bin/opencode; \
    fi

# Update PATH to include harness binaries
ENV PATH="/root/.claude/bin:/root/.opencode/bin:${PATH}"
```

### Script Pattern for Config Mounting
```bash
# Build mount arguments for harness configs
build_config_mounts() {
    local -a mounts=()

    # Claude Code configs
    [[ -d "$HOME/.claude" ]] && mounts+=(-v "$HOME/.claude:$HOME/.claude")
    [[ -f "$HOME/.claude.json" ]] && mounts+=(-v "$HOME/.claude.json:$HOME/.claude.json")

    # OpenCode configs
    [[ -d "$HOME/.config/opencode" ]] && mounts+=(-v "$HOME/.config/opencode:$HOME/.config/opencode")
    [[ -f "$HOME/.opencode.json" ]] && mounts+=(-v "$HOME/.opencode.json:$HOME/.opencode.json")

    # Additional OpenCode data (credentials)
    [[ -d "$HOME/.local/share/opencode" ]] && mounts+=(-v "$HOME/.local/share/opencode:$HOME/.local/share/opencode")

    printf '%s\n' "${mounts[@]}"
}
```

### Script Pattern for API Key Passthrough
```bash
# Build environment arguments for API keys
build_api_env() {
    local -a envs=()

    # Common API keys - only pass if set (avoid empty override)
    local api_vars=(
        ANTHROPIC_API_KEY
        OPENAI_API_KEY
        GEMINI_API_KEY
        GROQ_API_KEY
        GITHUB_TOKEN
        AWS_ACCESS_KEY_ID
        AWS_SECRET_ACCESS_KEY
        AWS_REGION
        AWS_PROFILE
        AZURE_OPENAI_API_KEY
        AZURE_OPENAI_ENDPOINT
        GOOGLE_CLOUD_PROJECT
        GOOGLE_APPLICATION_CREDENTIALS
    )

    for var in "${api_vars[@]}"; do
        [[ -n "${!var:-}" ]] && envs+=(-e "$var=${!var}")
    done

    # Recommended container settings
    envs+=(-e "DISABLE_AUTOUPDATER=1")  # Disable Claude auto-update in container

    printf '%s\n' "${envs[@]}"
}
```

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| npm install -g @anthropic-ai/claude-code | Native binary installer | 2025 | No Node.js required, cleaner install |
| Manual Claude Code version pinning | Install script with channels | 2025 | Simpler version management |
| Go toolchain for OpenCode | Pre-built binary installer | 2024 | Smaller image, faster install |
| Mounting ~/.config only | Mount multiple config locations | 2025 | OpenCode uses multiple paths |

**Deprecated/outdated:**
- `npm install -g @anthropic-ai/claude-code`: Officially deprecated by Anthropic, native installer recommended
- Alpine-based images for Claude Code: Requires extra dependencies (libgcc, libstdc++, ripgrep), Debian preferred
- Storing API keys in config files: Environment variables preferred for security

## Open Questions

Things that couldn't be fully resolved:

1. **Claude Code Session Persistence**
   - What we know: OAuth session stored in ~/.claude.json, survives container restarts if mounted
   - What's unclear: Exact behavior when ~/.claude.json mounted but session expired
   - Recommendation: Mount ~/.claude.json, let harness handle re-authentication gracefully

2. **OpenCode Auth Storage Location**
   - What we know: `/connect` stores credentials in ~/.local/share/opencode/auth.json
   - What's unclear: Whether this path is configurable via OPENCODE_CONFIG_DIR
   - Recommendation: Mount ~/.local/share/opencode in addition to ~/.config/opencode

3. **Harness Update Strategy**
   - What we know: `aishell update` should rebuild image with latest versions
   - What's unclear: Best UX for version pinning vs "always latest"
   - Recommendation: Default to latest, support version flags per CONTEXT.md decisions

## Sources

### Primary (HIGH confidence)
- [Claude Code Setup Docs](https://code.claude.com/docs/en/setup) - Installation methods, system requirements
- [Claude Code Settings Docs](https://code.claude.com/docs/en/settings) - Config file hierarchy, environment variables
- [OpenCode GitHub](https://github.com/opencode-ai/opencode) - Installation, configuration
- [OpenCode Config Docs](https://opencode.ai/docs/config/) - Config file paths, priority order
- [OpenCode Providers Docs](https://opencode.ai/docs/providers/) - Supported providers, environment variables

### Secondary (MEDIUM confidence)
- [Docker Environment Variables Best Practices](https://docs.docker.com/compose/how-tos/environment-variables/best-practices/) - Passthrough patterns
- [Node.js Docker Best Practices](https://snyk.io/blog/choosing-the-best-node-js-docker-image/) - Image selection
- [CloudSpinx Node.js Debian Install](https://cloudspinx.com/how-to-install-node-js-on-debian-bookworm/) - Debian-specific setup

### Tertiary (LOW confidence)
- Various blog posts on harness installation - Confirmed against official docs
- GitHub issues on authentication - Edge cases documented

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH - Official documentation for both harnesses
- Architecture: HIGH - Patterns verified against existing codebase and harness docs
- Pitfalls: MEDIUM - Based on GitHub issues and community reports, some edge cases may exist

**Research date:** 2026-01-17
**Valid until:** 30 days (harnesses update frequently, native installers handle changes)

---
*Phase: 03-harness-integration*
*Research completed: 2026-01-17*
