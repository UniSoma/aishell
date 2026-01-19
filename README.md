# aishell

Docker-based sandbox for running agentic AI harnesses (Claude Code, OpenCode) in isolated, ephemeral containers.

## Features

- **Isolated execution** - AI agents run in ephemeral Docker containers
- **Host path preservation** - Projects mounted at exact host path for seamless operation
- **Git identity passthrough** - Commits preserve your identity
- **Per-project customization** - Extend via `.aishell/Dockerfile`
- **Version pinning** - Lock harness versions for reproducibility
- **Config persistence** - Mounts `~/.claude` and OpenCode configs automatically
- **Runtime configuration** - Custom mounts, env vars, ports via `.aishell/run.conf`
- **Pre-start commands** - Run sidecar services before shell/harness

## Requirements

- Linux
- Docker Engine

## Installation

```bash
curl -fsSL https://raw.githubusercontent.com/UniSoma/aishell/main/install.sh | bash
```

Add `~/.local/bin` to your PATH if not already present:

```bash
export PATH="$HOME/.local/bin:$PATH"
```

## Usage

### Build an image

```bash
# Build with Claude Code
aishell build --with-claude

# Build with OpenCode
aishell build --with-opencode

# Build with both
aishell build --with-claude --with-opencode

# Build with specific versions
aishell build --with-claude --claude-version=2.0.22
```

### Run harnesses

```bash
# Enter interactive shell
aishell

# Run Claude Code
aishell claude

# Run OpenCode
aishell opencode

# Pass arguments to harness
aishell claude --help
```

### Update to latest versions

```bash
# Rebuild with latest versions (uses saved configuration)
aishell update

# Add a harness to existing build
aishell update --with-opencode
```

### Project customization

Create `.aishell/Dockerfile` to extend the base image:

```dockerfile
FROM aishell:base

RUN apt-get update && apt-get install -y postgresql-client
```

### Runtime configuration

Create `.aishell/run.conf` to configure container runtime:

```bash
# Additional volume mounts
MOUNTS="/path/to/data $HOME/.secrets:/secrets"

# Environment variables (passthrough or literal)
ENV="DATABASE_URL MY_VAR=literal_value"

# Port mappings (host:container)
PORTS="3000:3000 8080:80"

# Extra docker run arguments
DOCKER_ARGS="--memory=4g --cpus=2"

# Pre-start command (runs in background before shell)
PRE_START="redis-server --daemonize yes"
```

## Environment Variables

| Variable | Description |
|----------|-------------|
| `ANTHROPIC_API_KEY` | Anthropic API key (passed to container) |
| `OPENAI_API_KEY` | OpenAI API key (passed to container) |
| `AISHELL_SKIP_PERMISSIONS` | Set to `false` to enable Claude permission prompts |

## Base Image Contents

- Debian bookworm-slim
- Node.js LTS
- Babashka
- git, curl, jq, ripgrep, vim

## License

MIT
