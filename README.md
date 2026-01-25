# aishell

Docker-based sandbox for running agentic AI harnesses (Claude Code, OpenCode, Codex CLI, Gemini CLI) in isolated, ephemeral containers.

## Features

- **Isolated execution** - AI agents run in ephemeral Docker containers
- **Host path preservation** - Projects mounted at exact host path for seamless operation
- **Git identity passthrough** - Commits preserve your identity
- **Per-project customization** - Extend via `.aishell/Dockerfile`
- **Version pinning** - Lock harness versions for reproducibility
- **Config persistence** - Mounts `~/.claude` and OpenCode configs automatically
- **Runtime configuration** - Custom mounts, env vars, ports via `.aishell/config.yaml`
- **Pre-start commands** - Run sidecar services before shell/harness
- **Sensitive file detection** - Warnings before AI agents access secrets, keys, or credentials
- **Gitleaks integration** - Deep content-based secret scanning with `aishell gitleaks`

## Why aishell?

Running AI coding agents in Docker yourself means dealing with:

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

- **Get paths right** - AI agents reference absolute paths in their responses. If `/home/you/project` on your host becomes `/app` in the container, file references break and the agent gets confused.
- **Preserve git identity** - Without setup, commits appear as "root" or "unknown". You need to pass through `.gitconfig` or set `GIT_AUTHOR_*` variables.
- **Remember all the mounts** - Config directories, credential files, SSH keys, project-specific data. Miss one and things fail mid-session.
- **Handle project-specific needs** - One project needs PostgreSQL client, another needs Python 3.11. Managing multiple Dockerfiles gets messy.
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

# Build with Codex CLI
aishell build --with-codex

# Build with Gemini CLI
aishell build --with-gemini

# Build with multiple harnesses
aishell build --with-claude --with-opencode

# Build with all harnesses
aishell build --with-claude --with-opencode --with-codex --with-gemini

# Build with specific versions (single-flag syntax)
aishell build --with-claude=2.0.22
aishell build --with-codex=0.1.2025062501
```

### Run harnesses

```bash
# Enter interactive shell
aishell

# Run Claude Code
aishell claude

# Run OpenCode
aishell opencode

# Run Codex CLI
aishell codex

# Run Gemini CLI
aishell gemini

# Pass arguments to harness
aishell claude --help
aishell codex --help
aishell gemini --help

# Run Gitleaks secret scanner
aishell gitleaks detect
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

**Config inheritance:** Project configs merge with `~/.aishell/config.yaml` by default. Lists (mounts, ports) concatenate, maps (env) merge with project values taking precedence, scalars (pre_start) are replaced. Set `extends: none` to disable inheritance.

### Sensitive file detection

Before launching a container, aishell scans your project for potentially sensitive files and warns you before AI agents can access them.

**Severity levels:**
- **High** - SSH keys, private keys, cloud credentials, package manager tokens → requires confirmation
- **Medium** - Environment files, tool configs, database credentials → informational warning
- **Low** - Template files (.env.example) → informational notice

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

**Gitleaks for deep scanning:**

Use `aishell gitleaks` for content-based secret detection inside the container:

```bash
# Run gitleaks scan
aishell gitleaks detect

# Run with specific options
aishell gitleaks detect --verbose --no-git
```

aishell tracks when you last ran gitleaks and reminds you if it's been more than 7 days.

**Custom patterns and allowlist:**

Add to `.aishell/config.yaml`:

```yaml
detection:
  # Add custom patterns (extends defaults)
  custom_patterns:
    "*.secret": high
    "internal-*.json":
      severity: medium

  # Suppress false positives
  allowlist:
    - path: "test/fixtures/fake-key.pem"
      reason: "Test fixture, not a real key"

  # Disable freshness warnings (default: true)
  gitleaks_freshness_check: false

  # Custom staleness threshold in days (default: 7)
  gitleaks_freshness_days: 14
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

## Authentication

aishell mounts harness configuration directories from your host (`~/.claude`, `~/.codex`, `~/.gemini`, `~/.config/opencode`, `~/.local/share/opencode`), so authentication persists between container sessions.

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
| `AISHELL_SKIP_PERMISSIONS` | Claude permissions | Set to `false` to enable prompts |

## Base Image Contents

Built on `debian:bookworm-slim` with:

**Runtimes:**
- Node.js 24 (with npm, npx)
- Babashka

**Security tools:**
- Gitleaks v8.30.0 (secret scanning)

**CLI tools:**
- git, curl, jq, ripgrep, vim
- tree, less, file, unzip, watch
- htop, sqlite3, sudo

## License

MIT
