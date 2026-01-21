# aishell

Docker-based sandbox for running agentic AI harnesses (Claude Code, OpenCode) in isolated, ephemeral containers.

## Features

- **Isolated execution** - AI agents run in ephemeral Docker containers
- **Host path preservation** - Projects mounted at exact host path for seamless operation
- **Git identity passthrough** - Commits preserve your identity
- **Per-project customization** - Extend via `.aishell/Dockerfile`
- **Version pinning** - Lock harness versions for reproducibility
- **Config persistence** - Mounts `~/.claude` and OpenCode configs automatically
- **Runtime configuration** - Custom mounts, env vars, ports via `.aishell/config.yaml`
- **Pre-start commands** - Run sidecar services before shell/harness

## Requirements

- Linux or macOS
- Docker Engine
- [Babashka](https://babashka.org)

## Installation

First, install Babashka if you haven't already: https://babashka.org

Then install aishell:

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

# Build with specific versions (single-flag syntax)
aishell build --with-claude=2.0.22
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

Create `.aishell/config.yaml` to configure container runtime:

```yaml
mounts:
  - /path/to/data
  - source: $HOME/.secrets
    target: /secrets

env:
  DATABASE_URL: passthrough
  MY_VAR: literal_value

ports:
  - "3000:3000"
  - "8080:80"

docker_args: "--memory=4g --cpus=2"

pre_start: "redis-server --daemonize yes"
```

### Git safe.directory

When you run a container, aishell configures git to trust the mounted project directory by adding it to `safe.directory` in the container's gitconfig.

**What happens:**
1. The entrypoint runs `git config --global --add safe.directory /your/project/path`
2. This writes to `~/.gitconfig` inside the container

**Host gitconfig impact:**
If you mount your host's `~/.gitconfig` or `~/.config/git/config` into the container (via `mounts` in config.yaml), the safe.directory entry will be added to your **host's** gitconfig file.

**Why this happens:**
- Git requires safe.directory for directories owned by different users
- Inside the container, the mounted project appears owned by a different user
- This is a security feature (CVE-2022-24765), not a bug

**To avoid modifying host gitconfig:**
Don't mount your host gitconfig into the container. The container creates its own gitconfig that is discarded when the container exits.

## Environment Variables

| `AISHELL_SKIP_PERMISSIONS` | Set to `false` to enable Claude permission prompts |

## Base Image Contents

- Debian bookworm-slim
- Node.js LTS
- Babashka
- git, curl, jq, ripgrep, vim

## License

MIT
