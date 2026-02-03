# aishell Harness Guide

This guide covers installation, authentication, and usage for all AI harnesses that aishell supports.

**Last updated:** v2.9.0

## What are Harnesses?

Harnesses are AI CLI tools that aishell runs in isolated containers. Each harness wraps a different AI provider or multi-provider interface:

- **Claude Code** - Anthropic's autonomous coding agent
- **OpenCode** - Multi-provider AI coding agent (Anthropic, OpenAI, Google, etc.)
- **Codex CLI** - OpenAI's ChatGPT integration for coding
- **Gemini CLI** - Google's Gemini models for development

### How Harnesses Install (v2.8.0+)

Harnesses install into **Docker volumes**, not into the foundation image. This enables fast updates:

**Volume layout:**
- npm packages install to `/tools/npm`
- Go binaries download to `/tools/bin`
- Containers mount the volume read-only at `/tools`
- PATH includes `/tools/npm/bin` and `/tools/bin`

**Benefits:**
- **Fast updates:** `aishell update` refreshes tools without rebuilding the foundation image
- **Shared volumes:** Projects with identical harness configs share a volume
- **Stable cache:** Harness updates leave the Docker extension cache intact

**Volume management:**
```bash
# List harness volumes
aishell volumes

# Remove orphaned volumes
aishell volumes prune
```

## Harness Comparison

| Feature | Claude Code | OpenCode | Codex CLI | Gemini CLI |
|---------|-------------|----------|-----------|------------|
| Provider | Anthropic | Multiple | OpenAI | Google |
| Auth Methods | OAuth, API Key | Per-provider | OAuth, API Key | OAuth, API Key |
| Container Auth | Copy-paste URL | Standard | Device code | Auth on host first |
| Vertex AI | No | Yes | No | Yes |
| Config Dir | ~/.claude | ~/.config/opencode | ~/.codex | ~/.gemini |
| Best For | Autonomous coding | Multi-model flexibility | ChatGPT integration | Gemini models |

## Claude Code

### Overview

Claude Code is Anthropic's official CLI for autonomous coding tasks. It offers IDE-like file operations, code analysis, and multi-step execution.

**Provider:** Anthropic
**Models:** Claude 3.5 Sonnet, Claude Opus 4.5

### Installation

Build aishell with Claude Code support:

```bash
# Latest version
aishell build --with-claude

# Specific version
aishell build --with-claude=2.0.22
```

Pin versions for reproducible environments.

**Build steps:**
1. Builds the foundation image (Debian, Node.js, system tools) -- skipped if cached
2. Creates a harness volume: `aishell-harness-{hash}`
3. Installs the Claude Code npm package into the volume: `npm install -g @anthropic-ai/claude-code`
4. Mounts the volume read-only at `/tools` in containers

**Updating Claude Code:**
```bash
# Update to latest version
aishell update

# Deletes the volume, recreates it, and reinstalls harness tools
```

### Authentication

Claude Code supports two authentication methods.

#### OAuth (Recommended for containers)

OAuth works in containers through a copy-paste URL flow:

```bash
aishell claude
# Follow prompts, copy URL to browser, authenticate
```

The container uses the copy-paste URL method automatically.

#### API Key

Set the `ANTHROPIC_API_KEY` environment variable:

```bash
export ANTHROPIC_API_KEY=sk-ant-...
aishell claude
```

Or pass via `--env` flag:

```bash
aishell --env ANTHROPIC_API_KEY=sk-ant-... claude
```

Get API keys from: https://console.anthropic.com/settings/keys

### Usage

Basic invocation:

```bash
aishell claude
```

Pass arguments to Claude Code:

```bash
aishell claude --help
aishell claude "implement user authentication"
```

Set default arguments in `config.yaml`:

```yaml
harnesses:
  claude:
    args: ["--model", "claude-opus-4.5"]
```

### Environment Variables

| Variable | Purpose | Example |
|----------|---------|---------|
| `ANTHROPIC_API_KEY` | API key authentication | `sk-ant-api03-...` |
| `CLAUDE_MAX_TOKENS` | Response token limit | `4096` |
| `CLAUDE_TIMEOUT` | Request timeout (seconds) | `300` |

### Configuration Directory

- **Host:** `~/.claude`
- **Container:** `/root/.claude`

A volume mount preserves config across container restarts.

### Tips & Best Practices

1. **OAuth in containers:** The copy-paste URL flow works well
2. **Model selection:** Use the `--model` flag or config.yaml to select Opus 4.5
3. **Session handling:** Each `aishell claude` invocation starts a new session
4. **File operations:** Claude Code has full filesystem access within the container

## OpenCode

### Overview

OpenCode is a multi-provider AI coding agent that supports Anthropic, OpenAI, Google, and others. It lets you switch models and providers without changing tools.

**Providers:** Anthropic, OpenAI, Google, Azure OpenAI, Vertex AI
**Models:** Provider-dependent (Claude, GPT, Gemini, etc.)

### Installation

Build aishell with OpenCode support:

```bash
# Latest version
aishell build --with-opencode

# Specific version
aishell build --with-opencode=0.2.3
```

**Note:** OpenCode installs as a Go binary (not an npm package), downloaded from GitHub releases to `/tools/bin`.

**Updating OpenCode:**
```bash
aishell update  # Deletes the volume and reinstalls the OpenCode binary
```

### Authentication

OpenCode authentication varies by provider. Set the environment variable for your chosen provider.

#### Anthropic

```bash
export ANTHROPIC_API_KEY=sk-ant-...
aishell opencode
```

#### OpenAI

```bash
export OPENAI_API_KEY=sk-...
aishell opencode
```

#### Google (Gemini)

```bash
export GOOGLE_API_KEY=...
aishell opencode
```

#### Vertex AI

```bash
export GOOGLE_APPLICATION_CREDENTIALS=/path/to/service-account.json
aishell opencode
```

Pass the credentials file into the container:

```bash
aishell --mount $GOOGLE_APPLICATION_CREDENTIALS:/creds/gcp.json \
  --env GOOGLE_APPLICATION_CREDENTIALS=/creds/gcp.json \
  opencode
```

### Usage

Basic invocation:

```bash
aishell opencode
```

Specify model/provider:

```bash
aishell opencode --model claude-3-5-sonnet-20241022
aishell opencode --model gpt-4o
aishell opencode --model gemini-2.0-flash-exp
```

Set default model in `config.yaml`:

```yaml
harnesses:
  opencode:
    args: ["--model", "claude-3-5-sonnet-20241022"]
```

### Environment Variables

| Variable | Purpose | Provider |
|----------|---------|----------|
| `ANTHROPIC_API_KEY` | Anthropic API key | Anthropic |
| `OPENAI_API_KEY` | OpenAI API key | OpenAI |
| `GOOGLE_API_KEY` | Google AI API key | Google |
| `GOOGLE_APPLICATION_CREDENTIALS` | GCP service account | Vertex AI |
| `AZURE_OPENAI_API_KEY` | Azure API key | Azure OpenAI |
| `AZURE_OPENAI_ENDPOINT` | Azure endpoint URL | Azure OpenAI |

### Configuration Directories

- **Host:** `~/.config/opencode`, `~/.local/share/opencode`
- **Container:** `/root/.config/opencode`, `/root/.local/share/opencode`

### Tips & Best Practices

1. **Multi-provider workflows:** Switch models with the `--model` flag
2. **Vertex AI setup:** Authenticate on the host first, then mount credentials
3. **Model availability:** Check OpenCode docs for supported models
4. **Cost management:** Pricing varies by provider and model

## Codex CLI

### Overview

Codex CLI gives command-line access to OpenAI's ChatGPT for coding tasks, integrating conversational AI with shell workflows.

**Provider:** OpenAI
**Models:** GPT-4o, GPT-4, GPT-3.5-turbo

### Installation

Build aishell with Codex CLI support:

```bash
# Latest version
aishell build --with-codex

# Specific version
aishell build --with-codex=1.0.7
```

### Authentication

Codex CLI supports OAuth and API key authentication.

#### OAuth

**Standard OAuth:**

```bash
aishell codex
# Follows OAuth flow
```

**Device code flow (recommended for containers):**

```bash
aishell codex login --device-auth
```

The device code flow displays a code to enter at a URL, which suits headless environments.

#### API Key

**For `codex` command (interactive chat):**

Set `OPENAI_API_KEY`:

```bash
export OPENAI_API_KEY=sk-...
aishell codex
```

**For `codex exec` (programmatic execution):**

The `exec` subcommand uses `CODEX_API_KEY`:

```bash
export CODEX_API_KEY=sk-...
aishell codex exec "write a Python script to parse JSON"
```

**Important:** `OPENAI_API_KEY` works for login/interactive mode. `CODEX_API_KEY` only works with `codex exec`.

Get API keys from: https://platform.openai.com/api-keys

### Usage

Interactive chat:

```bash
aishell codex
```

Programmatic execution:

```bash
aishell codex exec "implement user authentication"
```

Pass arguments:

```bash
aishell codex --help
aishell codex --model gpt-4o
```

Set defaults in `config.yaml`:

```yaml
harnesses:
  codex:
    args: ["--model", "gpt-4o"]
```

### Environment Variables

| Variable | Purpose | Example |
|----------|---------|---------|
| `OPENAI_API_KEY` | API key for interactive mode | `sk-...` |
| `CODEX_API_KEY` | API key for `codex exec` | `sk-...` |
| `CODEX_MODEL` | Default model | `gpt-4o` |

### Configuration Directory

- **Host:** `~/.codex`
- **Container:** `/root/.codex`

### Tips & Best Practices

1. **Container auth:** Use device code flow: `codex login --device-auth`
2. **Interactive vs exec:** Interactive for chat, exec for automation
3. **Model selection:** GPT-4o recommended for coding tasks
4. **API key distinction:** Remember `OPENAI_API_KEY` vs `CODEX_API_KEY` usage

## Gemini CLI

### Overview

Gemini CLI gives command-line access to Google's Gemini models for development tasks. It supports both direct API access and Vertex AI.

**Provider:** Google
**Models:** Gemini 2.0 Flash, Gemini 1.5 Pro, Gemini 1.5 Flash

### Installation

Build aishell with Gemini CLI support:

```bash
# Latest version
aishell build --with-gemini

# Specific version
aishell build --with-gemini=0.1.5
```

### Authentication

Gemini CLI supports OAuth and API key authentication.

#### OAuth (Host authentication required)

**Important:** You must authenticate on the host first; the container then uses the cached credentials.

**Step 1 - Authenticate on host:**

```bash
# Install Gemini CLI on host
npm install -g @google/gemini-cli

# Authenticate
gemini login
```

**Step 2 - Run in container:**

```bash
aishell gemini
# Uses cached OAuth credentials
```

The container mounts `~/.gemini` automatically.

#### API Key

Set `GEMINI_API_KEY` or `GOOGLE_API_KEY`:

```bash
export GEMINI_API_KEY=...
aishell gemini
```

Or pass via `--env` flag:

```bash
aishell --env GEMINI_API_KEY=... gemini
```

Get API keys from: https://aistudio.google.com/app/apikey

#### Vertex AI

For Vertex AI, set `GOOGLE_APPLICATION_CREDENTIALS`:

```bash
export GOOGLE_APPLICATION_CREDENTIALS=/path/to/service-account.json
aishell --mount $GOOGLE_APPLICATION_CREDENTIALS:/creds/gcp.json \
  --env GOOGLE_APPLICATION_CREDENTIALS=/creds/gcp.json \
  gemini
```

### Usage

Basic invocation:

```bash
aishell gemini
```

Pass arguments:

```bash
aishell gemini --help
aishell gemini --model gemini-2.0-flash-exp
```

Set default model in `config.yaml`:

```yaml
harnesses:
  gemini:
    args: ["--model", "gemini-2.0-flash-exp"]
```

### Environment Variables

| Variable | Purpose | Example |
|----------|---------|---------|
| `GEMINI_API_KEY` | Gemini API key | `AI...` |
| `GOOGLE_API_KEY` | Alternative API key var | `AI...` |
| `GOOGLE_APPLICATION_CREDENTIALS` | GCP service account (Vertex AI) | `/path/to/creds.json` |
| `GEMINI_MODEL` | Default model | `gemini-2.0-flash-exp` |

### Configuration Directory

- **Host:** `~/.gemini`
- **Container:** `/root/.gemini`

### Tips & Best Practices

1. **OAuth setup:** Authenticate on the host first; the container uses the cache
2. **API key:** Use `GEMINI_API_KEY` for the simplest setup
3. **Vertex AI:** Adds enterprise features (audit logs, VPC, quotas)
4. **Model selection:** Gemini 2.0 Flash balances speed and quality

## Detached Mode & tmux

tmux is opt-in via the `--with-tmux` build flag. When enabled, each container runs inside a tmux session named `harness`.

### Enabling tmux

Build with tmux support:

```bash
# Build with harness and tmux
aishell build --with-claude --with-tmux
```

### Running in Background

Detached mode requires tmux:

```bash
# Start Claude Code detached
aishell claude --detach

# Start with a custom name
aishell claude --detach --name reviewer

# Short form
aishell claude -d --name reviewer
```

**Note:** Without `--with-tmux`, `aishell attach` shows an error and prompts you to rebuild.

### Reconnecting

Attach to running containers:

```bash
# Attach to a running container
aishell attach --name claude

# Attach to a specific tmux session (default: harness)
aishell attach --name claude --session harness
```

**Session name:** Default is `harness` (changed from `main` in v2.9.0)

### Shell Access

Open a plain bash shell inside a running container:

```bash
aishell attach --name claude --shell
```

This creates (or reattaches to) a tmux session named `shell` running `/bin/bash`.
Unlike `--session`, which attaches to an existing tmux session, `--shell` always
ensures a bash session exists.

### Listing Containers

```bash
# List running containers for current project
aishell ps
```

### Detaching

Inside a container, press `Ctrl+B D` to detach from the tmux session without stopping the container.

### Stopping

```bash
docker stop aishell-<hash>-claude
```

### tmux Plugins

Configure tmux plugins in `.aishell/config.yaml`:

```yaml
tmux:
  plugins:
    - tmux-plugins/tmux-sensible
    - tmux-plugins/tmux-yank
  resurrect: true  # Enable session persistence
```

**Requirements:** Build with `--with-tmux`. Without it, the plugin config is silently ignored.

**Plugin installation:** The build installs plugins to the harness volume; containers load them at runtime.

### Container Naming

Containers follow the naming pattern `aishell-{project-hash}-{name}`:
- **project-hash**: First 8 characters of the SHA-256 of your project directory path
- **name**: Defaults to the harness name (`claude`, `opencode`, `codex`, `gemini`, `shell`); override with `--name`

This scheme supports multiple instances per project and isolation across projects.

## Running Multiple Harnesses

### Building with Multiple Harnesses

Build with any combination:

```bash
# Two harnesses
aishell build --with-claude --with-opencode

# Three harnesses
aishell build --with-claude --with-opencode --with-codex

# All harnesses
aishell build --with-claude --with-opencode --with-codex --with-gemini
```

Each added harness increases image size.

### Switching Between Harnesses

Invoke each harness separately:

```bash
# Use Claude Code
aishell claude "implement feature X"

# Use OpenCode
aishell opencode "implement feature Y"

# Use Codex CLI
aishell codex "implement feature Z"

# Use Gemini CLI
aishell gemini "implement feature W"
```

Each invocation runs in its own container.

### Shared Environment Variables

Some environment variables apply to multiple harnesses:

```bash
# Works for both Claude Code and OpenCode (Anthropic)
export ANTHROPIC_API_KEY=sk-ant-...

# Works for both Codex CLI and OpenCode (OpenAI)
export OPENAI_API_KEY=sk-...

# Works for both Gemini CLI and OpenCode (Google)
export GOOGLE_API_KEY=...
```

### Configuration Management

Each harness has its own config section in `config.yaml`:

```yaml
harnesses:
  claude:
    args: ["--model", "claude-opus-4.5"]

  opencode:
    args: ["--model", "claude-3-5-sonnet-20241022"]

  codex:
    args: ["--model", "gpt-4o"]

  gemini:
    args: ["--model", "gemini-2.0-flash-exp"]
```

## Authentication Quick Reference

### OAuth Authentication

| Harness | Method | Container Support |
|---------|--------|-------------------|
| Claude Code | Copy-paste URL | ✓ Excellent |
| OpenCode | Provider-dependent | ✓ Varies by provider |
| Codex CLI | Device code flow | ✓ Good with `--device-auth` |
| Gemini CLI | Host auth required | ✓ Requires host setup |

### API Key Authentication

| Harness | Environment Variable | Where to Get |
|---------|---------------------|--------------|
| Claude Code | `ANTHROPIC_API_KEY` | https://console.anthropic.com/settings/keys |
| OpenCode (Anthropic) | `ANTHROPIC_API_KEY` | https://console.anthropic.com/settings/keys |
| OpenCode (OpenAI) | `OPENAI_API_KEY` | https://platform.openai.com/api-keys |
| OpenCode (Google) | `GOOGLE_API_KEY` | https://aistudio.google.com/app/apikey |
| Codex CLI | `OPENAI_API_KEY` (chat) or `CODEX_API_KEY` (exec) | https://platform.openai.com/api-keys |
| Gemini CLI | `GEMINI_API_KEY` or `GOOGLE_API_KEY` | https://aistudio.google.com/app/apikey |

### Vertex AI Authentication

| Harness | Environment Variable | Setup |
|---------|---------------------|-------|
| OpenCode | `GOOGLE_APPLICATION_CREDENTIALS` | Service account JSON, mount into container |
| Gemini CLI | `GOOGLE_APPLICATION_CREDENTIALS` | Service account JSON, mount into container |

## Troubleshooting

### Authentication Issues

**OAuth fails in container:**
- Claude Code: Ensure copy-paste URL flow completes
- Codex CLI: Use `--device-auth` flag
- Gemini CLI: Authenticate on host first with `gemini login`

**API key not recognized:**
- Check that the environment variable name matches the harness requirements
- Verify the API key format and permissions
- Pass the key explicitly with `--env`: `aishell --env KEY=value harness`

### Configuration Persistence

**Config not persisting:**
- Verify volume mounts with `aishell info`
- Check config directory permissions in the container
- Confirm `~/.aishell/config.yaml` mounts correctly

**Configs conflicting:**
- Config precedence: project > user > system
- Run `aishell debug` to see the effective config
- Check that the `extends` key does not create circular references

### Performance Issues

**Slow startup:**
- Multiple harnesses increase image size; consider separate images per harness
- Pin versions to cache layers

**Network timeouts:**
- Increase the timeout via environment variables
- Check network connectivity from the container
- Verify proxy settings if behind a corporate firewall

## See Also

- [Environment Variables Reference](ENVIRONMENT.md) - Complete env var documentation
- [Configuration Guide](CONFIGURATION.md) - Config file and precedence
- [README](../README.md) - Main project documentation
