# aishell

Docker sandbox for running AI coding agents (Claude Code, OpenCode, Codex CLI, Gemini CLI, Pi) in ephemeral containers, with optional development tools (OpenSpec).

## Why Docker?

AI coding agents run arbitrary code — installing packages, writing scripts, deleting files — with
minimal human review. On your host machine, one bad command can reach your SSH keys, cloud
credentials, browser data, and every file you own.

Docker draws a boundary. The agent sees only what you mount: your project directory and its config
files. It cannot touch `~/.ssh`, overwrite your shell config, or install packages that outlive the
session. When the agent exits, the container disappears. Nothing accumulates.

A fixed base image also means every developer runs agents in the same environment — no "works on my
machine" failures. And if you run multiple agents simultaneously, each gets its own container with no
shared state.

## Why aishell?

Running AI coding agents in Docker means dealing with:

```bash
docker run -it --rm \
  -v "$PWD:$PWD" \
  -v "$HOME/.claude:/home/dev/.claude" \
  -v "$HOME/.config/claude-code:/home/dev/.config/claude-code" \
  -w "$PWD" \
  -e ANTHROPIC_API_KEY \
  --name claude-session \
  my-claude-image claude
```

And that's the simple version. You still need to:

- **Get paths right** - AI agents reference absolute paths. If `/home/you/project` on your host becomes `/app` in the container, file references break.
- **Preserve git identity** - Without setup, commits appear as "root" or "unknown". You need to pass through `.gitconfig` or set `GIT_AUTHOR_*` variables.
- **Reproduce across machines** - That 200-character docker command you perfected? Good luck remembering it on your laptop.

aishell handles all of this. One command, consistent behavior, works everywhere.

### Why not devcontainers?

Devcontainers solve a different problem. They create persistent development environments tied to your IDE.

aishell is purpose-built for AI agents:

- **Ephemeral by design** - Containers spin up, run the agent, and disappear. No state accumulates, no cleanup needed.
- **Host path preservation** - Devcontainers remap your project to `/workspaces/project`. When an AI agent says "edit `/home/you/project/src/main.ts`", that path needs to exist. aishell mounts your project at its real path.
- **CLI-first** - No IDE required. Run from any terminal, SSH session, or script.
- **Zero config for the common case** - `aishell claude` just works. Devcontainers require `devcontainer.json`, features configuration, and IDE integration.

You can use both: devcontainers for your development environment, aishell for running AI agents.

## Quick Start

### Unix/macOS/Linux

```bash
# 1. Install
curl -fsSL https://raw.githubusercontent.com/UniSoma/aishell/main/install.sh | bash

# 2. Build foundation image and select harnesses (one-time)
aishell setup --with-opencode

# 3. Run
aishell opencode
```

> Babashka is installed automatically if not already present.

### Windows

**Recommended:** Use WSL2 and follow the Unix/Linux instructions above — it provides the best experience. Otherwise, use PowerShell. CMD works but has limited error handling and no colored output.

#### PowerShell

```powershell
# 1. Install
irm https://raw.githubusercontent.com/UniSoma/aishell/main/install.ps1 | iex

# 2. Restart terminal, then build foundation image and select harnesses (one-time)
aishell setup --with-opencode

# 3. Run
aishell opencode
```

> Babashka is installed automatically if not already present.

#### CMD

```batch
curl -fsSL https://raw.githubusercontent.com/UniSoma/aishell/main/install.bat -o install.bat && install.bat

aishell setup --with-opencode

aishell opencode
```

> Babashka is installed automatically if not already present.
> Requires Windows 10 version 1803 or later.

<details>
<summary>Prerequisites & troubleshooting</summary>

**Requirements:**

- **Linux/macOS:** Docker Engine (Babashka installed automatically)
- **Windows:** Docker Desktop with WSL2 backend enabled (Babashka installed automatically)

**Docker:**
- Linux/macOS: Install [Docker Engine](https://docs.docker.com/engine/install/)
- Windows: Install [Docker Desktop](https://www.docker.com/products/docker-desktop/) and enable WSL2 backend (Settings → General → "Use the WSL 2 based engine")

**Babashka:**
Babashka is installed automatically by the install scripts. If you prefer to install it manually:
- Linux/macOS: https://babashka.org
- Windows: Install via [Scoop](https://scoop.sh) (`scoop install babashka`) or download binary from https://babashka.org

**PATH configuration:**

Unix/macOS - Add `~/.local/bin` to PATH if not already present:

```bash
export PATH="$HOME/.local/bin:$PATH"
```

Windows - After installation, add the directory containing `aishell.bat` to PATH:

```powershell
[Environment]::SetEnvironmentVariable("Path", $env:Path + ";$env:LOCALAPPDATA\Programs\aishell", [System.EnvironmentVariableTarget]::User)
```

Then restart your terminal.

**Updating harness tools:**

```bash
# Unix/macOS/Linux
aishell update

# Windows (PowerShell or cmd.exe)
aishell update
```

</details>

## How it works

When you run `aishell claude`, aishell launches an ephemeral Docker container with your project mounted at its real host path so file references stay valid. Your git identity and harness configs (e.g. `~/.claude`) are passed through automatically. Any per-project customization from `.aishell/` is applied. When the agent exits, the container disappears — only your project files and harness configs persist.

## Features

### Core

- **Isolated execution** - AI agents run in ephemeral Docker containers
- **Host path preservation** - Projects mounted at their real host path so file references stay valid
- **Git identity passthrough** - Commits preserve your identity
- **Config persistence** - Mounts `~/.claude` and OpenCode configs

### Customization

- **Global base image** - Customize the base image for all projects via `~/.aishell/Dockerfile` (extra system packages, shell config, dev tools). See [Configuration docs](docs/CONFIGURATION.md#global-base-image-customization) for details.
- **Per-project customization** - Extend via `.aishell/Dockerfile`
- **Runtime configuration** - Custom mounts, env vars, ports via `.aishell/config.yaml`
- **Version pinning** - Lock harness versions for reproducibility
- **Pre-start commands** - Run sidecar services before shell/harness

### Power user

- **Sensitive file detection** - Warnings before AI agents access secrets, keys, or credentials
- **Gitleaks integration** - Opt-in deep content-based secret scanning with `aishell gitleaks` (requires `--with-gitleaks`)
- **OpenSpec integration** - Opt-in development workflow tool available inside containers (requires `--with-openspec`)
- **One-off commands** - Run single commands in container with `aishell exec`
- **Named containers** - Deterministic naming with `--name` override
- **VSCode integration** - Open VSCode attached to a container as `developer` with `aishell vscode`, server state persisted across restarts
- **Attach** - Open a shell in a running container via `aishell attach`
- **Container discovery** - List project containers with `aishell ps`
- **Volume management** - List and prune orphaned harness volumes with `aishell volumes`
- **Image info** - Show image stack and installed tools with `aishell info`
- **Self-upgrade** - Update aishell itself with `aishell upgrade`, with checksum verification

## Usage

### Set up harnesses

```bash
# Set up with a single harness
aishell setup --with-claude
aishell setup --with-opencode
aishell setup --with-codex
aishell setup --with-gemini
aishell setup --with-pi

# Set up with multiple harnesses
aishell setup --with-claude --with-opencode --with-codex --with-gemini --with-pi --with-openspec

# Set up with specific versions
aishell setup --with-claude=2.0.22
aishell setup --with-codex=0.1.2025062501

# Set up with optional development tools
aishell setup --with-claude --with-openspec
aishell setup --with-claude --with-openspec=1.2.3
```

### Run harnesses

```bash
# Enter interactive shell
aishell

# Run a harness
aishell claude
aishell opencode
aishell codex
aishell gemini
aishell pi

# Pass arguments to harness
aishell claude --help
```

### One-off commands

Run commands in the container without entering interactive shell:

```bash
aishell exec ls -la
aishell exec npm install
aishell exec node --version

# Use with pipes
echo "hello" | aishell exec cat
cat package.json | aishell exec jq '.scripts'
```

**Note:** The exec command uses the same mounts and environment from your config.yaml, but skips pre-start hooks and sensitive file detection for fast execution.

### Multi-container workflow

Run multiple containers and reconnect later:

```bash
# Start Claude in a second terminal
aishell claude --name reviewer

# List running containers for this project
aishell ps

# Open a shell in a running container
aishell attach reviewer

# Stop a container (use `aishell ps` to find the container name)
docker stop <container-name>
```

Containers are named `aishell-{project-hash}-{name}`. Use `aishell ps` to discover container names for your project.

**Conflict detection:** Starting a container with a name already in use by a running container shows an error with guidance. Stopped containers with the same name are auto-removed.

### VSCode (Experimental)

Open VSCode attached to the container as the `developer` user — no manual configuration needed:

```bash
# Open VSCode attached to the container (blocks until window closes, then stops container)
aishell vscode

# Detached mode: container keeps running in the background
aishell vscode --detach

# Stop a detached container when done
aishell vscode --stop
```

By default, aishell waits for the VSCode window to close, then stops the container automatically. Use `--detach` to leave the container running in the background. Multiple `aishell vscode` instances can run simultaneously — each opens a dedicated window.

The `~/.vscode-server` directory is mounted into the container, so VSCode server extensions and cached data persist across container restarts.

**Prerequisites:** VSCode with `code` CLI on PATH and the [Dev Containers](https://marketplace.visualstudio.com/items?itemName=ms-vscode-remote.remote-containers) extension installed. On WSL2 with Docker installed inside the distro (not Docker Desktop), see [Troubleshooting](docs/TROUBLESHOOTING.md#symptom-vscode-dev-container-cant-find-docker-on-wsl2-no-docker-desktop).

### Upgrade aishell

```bash
# Upgrade to latest version
aishell upgrade

# Upgrade to a specific version
aishell upgrade 3.4.0
```

### Update harness tools

```bash
# Refresh harness tools (volume refresh, fast)
aishell update

# Refresh harness tools AND rebuild foundation image
aishell update --force
```

### Validate setup

Run pre-flight checks without launching any container:

```bash
aishell check
```

Checks Docker availability, build state, image existence, configuration validity, mount paths, sensitive files, gitleaks scan freshness, and tool availability (git, VSCode `code` CLI).

## Configuration

### Project customization

Create `.aishell/Dockerfile` to extend the foundation image:

```dockerfile
FROM aishell:foundation

RUN apt-get update && apt-get install -y postgresql-client
```

### Runtime configuration

Create `.aishell/config.yaml` to configure container runtime:

```yaml
# Inherit from global config (default: "global", or "none" to disable)
extends: global

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

### Config inheritance

Project configs merge with `~/.aishell/config.yaml` by default. See [Configuration docs](docs/CONFIGURATION.md) for merge strategy details. Set `extends: none` to disable inheritance.

## Security

### Sensitive file detection

Before launching a container, aishell scans your project for potentially sensitive files and warns you before AI agents can access them.

```
$ aishell claude
⚠ Sensitive files detected:

  HIGH: SSH private key
    ~/.ssh/id_rsa

  MEDIUM: Environment file
    .env
    .env.local

Proceed? (y/n)
```

**Bypass for CI/automation:**

```bash
aishell claude --unsafe  # Skip confirmation prompts
```

### Gitleaks (Optional)

Gitleaks is opt-in. Enable it at build time:

```bash
aishell setup --with-claude --with-gitleaks
```

Then use `aishell gitleaks` for content-based secret detection inside the container:

```bash
aishell gitleaks detect
aishell gitleaks detect --verbose --no-git
```

aishell tracks when you last ran gitleaks and reminds you if it's been more than 7 days.

For custom detection patterns, allowlists, and freshness configuration, see [Configuration docs](docs/CONFIGURATION.md).

## Authentication

aishell mounts harness configuration directories from your host (`~/.claude`, `~/.codex`, `~/.gemini`, `~/.pi`, `~/.config/opencode`, `~/.local/share/opencode`), so authentication persists between container sessions.

### Claude Code

**Option 1: Interactive OAuth**

Run `aishell claude` and follow the prompts. Claude Code displays a URL you can copy-paste into your browser, completing OAuth even from within the container.

**Option 2: API Key**

```bash
export ANTHROPIC_API_KEY="your-key-here"
aishell claude
```

### Codex CLI

**Option 1: Interactive OAuth**

Run `aishell codex` and select "Sign in with ChatGPT". In headless environments, use:

```bash
codex login --device-auth
```

This displays a code to enter at a URL in your browser.

**Option 2: API Key**

```bash
export OPENAI_API_KEY="your-key-here"  # For login
# or
export CODEX_API_KEY="your-key-here"   # Only works with `codex exec`, not interactive
aishell codex
```

### Gemini CLI

**Option 1: Authenticate on host first**

```bash
# On your host machine (not in container)
gemini  # Select "Login with Google"

# Then run in container - credentials are mounted
aishell gemini
```

**Option 2: API Key**

```bash
export GEMINI_API_KEY="your-key-here"
# or
export GOOGLE_API_KEY="your-key-here"
aishell gemini
```

**Note:** Gemini CLI does not support device code flow for container authentication. Either authenticate on host first, or use an API key.

### Pi

Pi uses its own configuration-based authentication. Set up authentication on your host:

```bash
# Authenticate on host first, then credentials persist in ~/.pi
pi auth
```

The container mounts `~/.pi` automatically.

### OpenCode

OpenCode configuration directories (`~/.config/opencode`, `~/.local/share/opencode`) are mounted from your host. Refer to OpenCode's documentation for authentication methods.

## Environment Variables

aishell automatically passes these environment variables to containers when set on your host:

### Harness-Specific Keys

| Variable | Purpose | Notes |
|----------|---------|-------|
| `ANTHROPIC_API_KEY` | Claude Code API access | Required for API key auth |
| `OPENAI_API_KEY` | Codex login, OpenCode | Used by multiple harnesses |
| `CODEX_API_KEY` | Codex CLI API access | Only works with `codex exec` mode |
| `GEMINI_API_KEY` | Gemini CLI API access | From Google AI Studio |
| `GOOGLE_API_KEY` | Gemini/Vertex AI access | Alternative to GEMINI_API_KEY |
| `GROQ_API_KEY` | Groq API access | For Groq-hosted models |
| `OPENCODE_API_KEY` | OpenCode Zen model router | For Pi or other tools using Zen |

### Cloud Provider Credentials

| Variable | Purpose | Notes |
|----------|---------|-------|
| `GOOGLE_APPLICATION_CREDENTIALS` | Vertex AI service account | Path to JSON key file |
| `GOOGLE_CLOUD_PROJECT` | GCP project ID | Required for Vertex AI |
| `GOOGLE_CLOUD_LOCATION` | GCP region | Required for Vertex AI |
| `AWS_ACCESS_KEY_ID` | AWS access | For harnesses using AWS |
| `AWS_SECRET_ACCESS_KEY` | AWS secret | For harnesses using AWS |
| `AWS_REGION` | AWS region | For harnesses using AWS |
| `AWS_PROFILE` | AWS profile | Named profile support |
| `AZURE_OPENAI_API_KEY` | Azure OpenAI | For Azure-hosted models |
| `AZURE_OPENAI_ENDPOINT` | Azure endpoint | For Azure-hosted models |

### Other

| Variable | Purpose | Notes |
|----------|---------|-------|
| `GITHUB_TOKEN` | GitHub API access | For GitHub operations |
| `PI_CODING_AGENT_DIR` | Pi working directory | Override pi's default directory |
| `PI_SKIP_VERSION_CHECK` | Pi version check | Set to `true` to skip |
| `AISHELL_SKIP_PERMISSIONS` | Claude permissions | Set to `false` to enable prompts |

## Reference

### Foundation image contents

Built on `debian:bookworm-slim` with:

**Runtimes:**
- Node.js 24 (with npm, npx)
- Babashka

**Security tools:**
- Gitleaks v8.30.0 (optional, via `--with-gitleaks`)

**CLI tools:**
- git, curl, jq, ripgrep, fd, vim
- tree, less, file, unzip, watch
- htop, sqlite3, sudo

**Harness tools** (npm packages, binaries) are mounted from volumes at `/tools`, not baked into the image. This includes optional tools like OpenSpec when enabled.
This allows harness updates without rebuilding the foundation image.

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

## Documentation

For detailed documentation, see the [docs/](docs/) folder:

- **[Architecture](docs/ARCHITECTURE.md)** - System design, data flow, and codebase structure
- **[Configuration](docs/CONFIGURATION.md)** - Complete config.yaml reference with examples
- **[Harnesses](docs/HARNESSES.md)** - Setup and usage guide for each AI harness
- **[Troubleshooting](docs/TROUBLESHOOTING.md)** - Common issues and solutions
- **[Development](docs/DEVELOPMENT.md)** - Guide for adding new harnesses

## License

MIT
