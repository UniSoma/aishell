# aishell Harness Guide

This guide covers installation, authentication, and usage for all AI harnesses that aishell supports.

**Last updated:** v3.8.0

## What are Harnesses?

Harnesses are AI CLI tools that aishell runs in isolated containers. Each harness wraps a different AI provider or multi-provider interface:

- **Claude Code** - Anthropic's autonomous coding agent
- **OpenCode** - Multi-provider AI coding agent (Anthropic, OpenAI, Google, etc.)
- **Codex CLI** - OpenAI's ChatGPT integration for coding
- **GitHub Copilot CLI** - GitHub's terminal Harness for coding
- **Gemini CLI** - Google's Gemini models for development
- **Pi** - Mario Zechner's autonomous coding agent

### How Harnesses Install (v2.8.0+)

Harnesses install into **Docker volumes**, not into the foundation image. This enables fast updates:

**Volume layout:**
- npm packages install to `/tools/npm`
- Containers mount the volume read-only at `/tools` (immutable toolchain)
- PATH includes `/tools/npm/bin`

**Benefits:**
- **Fast updates:** `aishell update` refreshes tools without rebuilding the foundation image
- **Shared volumes:** Projects with identical harness configs share a volume
- **Stable cache:** Harness updates leave the Docker extension cache intact

**Base image customization:**
Advanced users can customize the base image globally via `~/.aishell/Dockerfile` (extra system packages, shell config, dev tools). Project `.aishell/Dockerfile` extensions should use `FROM aishell:base` (recommended) to inherit global customizations. See [Configuration](CONFIGURATION.md#global-base-image-customization) for details.

**Volume management:**
```bash
# List harness volumes
aishell volumes

# Remove orphaned volumes
aishell volumes prune
```

## Harness Comparison

| Feature | Claude Code | OpenCode | Codex CLI | GitHub Copilot CLI | Gemini CLI | Pi |
|---------|-------------|----------|-----------|--------------------|------------|-----|
| Provider | Anthropic | Multiple | OpenAI | GitHub | Google | Mario Zechner |
| Auth Methods | OAuth, API Key | Per-provider | OAuth, API Key | Device OAuth, token | OAuth, API Key | API Key |
| Container Auth | Copy-paste URL | Standard | Device code | Device code | Auth on host first | Standard |
| Vertex AI | No | Yes | No | No | Yes | No |
| Config Dir | ~/.claude | ~/.config/opencode | ~/.codex | ~/.copilot | ~/.gemini | ~/.pi |
| Best For | Autonomous coding | Multi-model flexibility | ChatGPT integration | GitHub Copilot workflows | Gemini models | Autonomous coding |

## Claude Code

### Overview

Claude Code is Anthropic's official CLI for autonomous coding tasks. It offers IDE-like file operations, code analysis, and multi-step execution.

**Provider:** Anthropic
**Models:** Claude 3.5 Sonnet, Claude Opus 4.5

### Installation

Build aishell with Claude Code support:

```bash
# Latest version
aishell setup --with-claude

# Specific version
aishell setup --with-claude=2.0.22
```

Pin versions for reproducible environments.

**Build steps:**
1. Builds the foundation image (Debian, Node.js, system tools) -- skipped if cached
2. Creates a harness volume: `aishell-harness-{hash}`
3. Installs the Claude Code npm package into the volume: `npm install -g @anthropic-ai/claude-code`
4. Mounts the volume at `/tools` in containers

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

**Set by aishell:** `DISABLE_AUTOUPDATER=1`, in Claude-enabled Sandboxes only.
The Harness volume is read-only and pinned, so `aishell update` and
`aishell setup --with-claude=VERSION` stay authoritative. It overrides an `env:`
entry of the same name; `docker_args` is the escape hatch. See [ADR 0008](adr/0008-harness-owned-runtime-environment-precedence.md).

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
aishell setup --with-opencode

# Specific version
aishell setup --with-opencode=2.0.16
```

**Note:** OpenCode V2 installs from npm as `@opencode/cli`, the same package its official installer downloads. The package picks the right binary for the container's platform. Version pins must be 2.x; V1 releases are published under a different package.

**Updating OpenCode:**
```bash
aishell update  # Deletes the volume and reinstalls OpenCode
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

**Set by aishell:** nothing. OpenCode disables its self-update through the
`autoupdate` key in `opencode.json`, not an environment variable, and aishell
mounts your config rather than writing to it. Set `"autoupdate": false` yourself
if OpenCode tries to update inside a Sandbox.

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
aishell setup --with-codex

# Specific version
aishell setup --with-codex=1.0.7
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

**Set by aishell:** `-c check_for_update_on_startup=false` on every launch —
argv rather than an environment variable, because that is the knob Codex
offers. See [ADR 0008](adr/0008-harness-owned-runtime-environment-precedence.md).

### Configuration Directory

- **Host:** `~/.codex`
- **Container:** `/root/.codex`

### Tips & Best Practices

1. **Container auth:** Use device code flow: `codex login --device-auth`
2. **Interactive vs exec:** Interactive for chat, exec for automation
3. **Model selection:** GPT-4o recommended for coding tasks
4. **API key distinction:** Remember `OPENAI_API_KEY` vs `CODEX_API_KEY` usage

## GitHub Copilot CLI

GitHub Copilot CLI is GitHub's Harness for terminal coding workflows.

### Installation

```bash
aishell setup --with-copilot                 # @github/copilot@latest
aishell setup --with-copilot=1.2.3           # exact version
```

The package installs into the shared Harness volume. Use `aishell update` to
refresh it; aishell sets `COPILOT_AUTO_UPDATE=false` in Copilot-enabled
Sandboxes so Copilot does not modify the read-only volume or defeat a pin.

### Authentication and persistence

Run `aishell copilot` and complete the device-code OAuth flow. The complete
host `~/.copilot` directory is mounted at the same path in the Sandbox, so
login, settings, plugins, saved permissions, and sessions persist.

If present on the host, `COPILOT_GITHUB_TOKEN` and `COPILOT_GH_HOST` are
forwarded automatically, and only when Copilot is enabled. `GH_TOKEN`,
`GITHUB_TOKEN`, and `GH_HOST` are deliberately not automatic; opt into any of
them through the normal `env:` configuration. `COPILOT_HOME`,
`COPILOT_CACHE_HOME`, and external cache locations are not forwarded or
mounted.

**Set by aishell:** `COPILOT_AUTO_UPDATE=false`, in Copilot-enabled Sandboxes
only. It overrides an `env:` entry of the same name; `docker_args` is the
escape hatch. See [ADR 0008](adr/0008-harness-owned-runtime-environment-precedence.md).

### Usage

```bash
aishell copilot
aishell copilot --help
```

Arguments under `harness_args.copilot` are prepended to command-line arguments
for both direct launches and the `copilot` alias inside an interactive Sandbox:

```yaml
harness_args:
  copilot:
    - "--model"
    - "gpt-5"
```

Copilot retains its own permission prompts by default; aishell never injects
`--allow-all` or `--yolo`.

## Gemini CLI

### Overview

Gemini CLI gives command-line access to Google's Gemini models for development tasks. It supports both direct API access and Vertex AI.

**Provider:** Google
**Models:** Gemini 2.0 Flash, Gemini 1.5 Pro, Gemini 1.5 Flash

### Installation

Build aishell with Gemini CLI support:

```bash
# Latest version
aishell setup --with-gemini

# Specific version
aishell setup --with-gemini=0.1.5
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

**Set by aishell:** nothing. Gemini CLI disables its auto-update through
`general.enableAutoUpdate` in `~/.gemini/settings.json`, not an environment
variable, and aishell mounts that directory rather than writing to it. Set it to
`false` yourself if Gemini CLI tries to update inside a Sandbox.

### Configuration Directory

- **Host:** `~/.gemini`
- **Container:** `/root/.gemini`

### Tips & Best Practices

1. **OAuth setup:** Authenticate on the host first; the container uses the cache
2. **API key:** Use `GEMINI_API_KEY` for the simplest setup
3. **Vertex AI:** Adds enterprise features (audit logs, VPC, quotas)
4. **Model selection:** Gemini 2.0 Flash balances speed and quality

## Pi

### Overview

Pi is an autonomous coding agent by Mario Zechner. It uses fd for file discovery and operates as a CLI tool for development tasks.

**Author:** Mario Zechner
**Package:** @earendil-works/pi-coding-agent

### Installation

```bash
# Latest version
aishell setup --with-pi

# Specific version
aishell setup --with-pi=1.0.0
```

**Build steps:**
1. Builds the foundation image (includes fd for pi's file discovery) -- skipped if cached
2. Creates a harness volume: `aishell-harness-{hash}`
3. Installs the Pi npm package into the volume: `npm install -g @earendil-works/pi-coding-agent`
4. Mounts the volume at `/tools` in containers

**Updating Pi:**
```bash
aishell update
```

### Authentication

Pi uses its own authentication mechanism. Configure authentication through pi's config directory.

Set up authentication on your host:

```bash
# Authenticate on host first, then credentials persist in ~/.pi
pi auth
```

Or configure via environment:

The container mounts `~/.pi` automatically.

### Usage

Basic invocation:

```bash
aishell pi
```

Pass arguments to Pi:

```bash
aishell pi --help
aishell pi --print "hello"
```

Set default arguments in `config.yaml`:

```yaml
harnesses:
  pi:
    args: ["--print", "hello"]
```

### Environment Variables

| Variable | Purpose | Example |
|----------|---------|---------|
| `PI_CODING_AGENT_DIR` | Override pi's working directory | `/custom/path` |

**Set by aishell:** `PI_SKIP_VERSION_CHECK=true`, in pi-enabled Sandboxes only.
The check is noise against a read-only, pinned Harness volume. It is no longer
read from the host: exporting it there has no effect, and it overrides an `env:`
entry of the same name, with `docker_args` as the escape hatch. See [ADR 0008](adr/0008-harness-owned-runtime-environment-precedence.md).

### Configuration Directory

- **Host:** `~/.pi`
- **Container:** `/root/.pi`

A volume mount preserves config across container restarts.

### Tips & Best Practices

1. **fd dependency:** Pi uses fd for file discovery, which is pre-installed in the foundation image
2. **Config persistence:** Authenticate on host; credentials mount automatically
3. **Session handling:** Each `aishell pi` invocation starts a new session
4. **File operations:** Pi has full filesystem access within the container

## Multi-Container Workflow

Run multiple named containers simultaneously and reconnect to them.

### Starting Named Containers

Each container gets a default name matching its harness (`claude`, `opencode`, `codex`, `copilot`, `gemini`, `pi`, `vscode`, `shell`). Override with `--name`:

```bash
# Start Claude (default name: claude)
aishell claude

# Start a second Claude with a custom name
aishell claude --name reviewer
```

### Listing Containers

```bash
# List running containers for current project
aishell ps
```

### Attaching to Running Containers

Open a bash shell in a running container:

```bash
# Attach to a running container by name
aishell attach claude

# Attach to a custom-named container
aishell attach reviewer
```

`aishell attach` runs `docker exec -it <container> bash`, giving you a new shell session inside the running container. The original harness process continues running.

Attach can also launch a command instead of dropping straight to a shell. Everything after `--` is
run inside the container, and a login shell takes over when it exits:

```bash
aishell attach claude -- claude
```

### Several Claude sessions, one sandbox

To run more than one Claude Code session against the same project, start the sandbox once and add
sessions with `attach`:

```bash
# Terminal 1 — starts the container
aishell claude

# Terminals 2, 3, … — extra sessions in that same container
aishell attach -- claude
```

Prefer this over starting a second container with `--name` when the sessions are meant to run **at
the same time**. Claude Code's supervisor state — `daemon.lock`, `daemon/roster.json`, `jobs/` —
is only meaningful within one PID namespace and one `/tmp`. Sessions sharing a container share
both, so Agent View sees all of them and background jobs behave. Two concurrent containers under
the default `claude_isolation: shared` instead write that state over each other through the host's
`~/.claude`, which breaks Agent View; see
[ADR 0001](adr/0001-per-project-claude-machine-state-isolation.md).

`aishell claude --name reviewer` remains the right tool for a session that should be genuinely
independent — its own filesystem view, its own lifetime. Set `claude_isolation: project` when two
such containers run concurrently.

Three things to know about the shared-sandbox pattern:

- **The first terminal owns the container.** It holds the `docker run --rm`; the attached sessions
  are `docker exec` children. Closing it kills every session in that sandbox, mid-work and without
  warning. If that worries you, start the container with a plain shell (`aishell shell`) so the
  owning terminal is obviously infrastructure, and make every Claude session an attach.
- **Attached sessions leave a shell behind.** When Claude exits, the login shell takes over and the
  terminal stays open. Type `exit` to close it.
- **All sessions share one working directory**, along with the sandbox's resource limits and
  network policy. Two agents editing the same tree is no safer here than it is on your host.

For driving these sessions from a multiplexer, see [Running aishell under herdr](HERDR.md).

### Stopping Containers

```bash
docker stop aishell-<hash>-claude
```

### Container Naming

Containers follow the naming pattern `aishell-{project-hash}-{name}`:
- **project-hash**: First 8 characters of the SHA-256 of your project directory path
- **name**: Defaults to the harness name (`claude`, `opencode`, `codex`, `copilot`, `gemini`, `pi`, `vscode`, `shell`); override with `--name`

## Running Multiple Harnesses

### Building with Multiple Harnesses

Build with any combination:

```bash
# Two harnesses
aishell setup --with-claude --with-opencode

# Three harnesses
aishell setup --with-claude --with-opencode --with-codex --with-copilot

# All harnesses
aishell setup --with-claude --with-opencode --with-codex --with-copilot --with-gemini --with-pi
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

# Use Pi
aishell pi "implement feature V"
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

  pi:
    args: ["--print", "hello"]
```

## Authentication Quick Reference

### OAuth Authentication

| Harness | Method | Container Support |
|---------|--------|-------------------|
| Claude Code | Copy-paste URL | ✓ Excellent |
| OpenCode | Provider-dependent | ✓ Varies by provider |
| Codex CLI | Device code flow | ✓ Good with `--device-auth` |
| Gemini CLI | Host auth required | ✓ Requires host setup |
| Pi | Config-based | ✓ Standard |

### API Key Authentication

| Harness | Environment Variable | Where to Get |
|---------|---------------------|--------------|
| Claude Code | `ANTHROPIC_API_KEY` | https://console.anthropic.com/settings/keys |
| OpenCode (Anthropic) | `ANTHROPIC_API_KEY` | https://console.anthropic.com/settings/keys |
| OpenCode (OpenAI) | `OPENAI_API_KEY` | https://platform.openai.com/api-keys |
| OpenCode (Google) | `GOOGLE_API_KEY` | https://aistudio.google.com/app/apikey |
| Codex CLI | `OPENAI_API_KEY` (chat) or `CODEX_API_KEY` (exec) | https://platform.openai.com/api-keys |
| Gemini CLI | `GEMINI_API_KEY` or `GOOGLE_API_KEY` | https://aistudio.google.com/app/apikey |
| Pi | (config-based auth) | See pi documentation |

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
- Check image stack and installed harnesses with `aishell info`
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
