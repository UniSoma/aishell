# aishell Configuration Reference

Complete reference for aishell configuration options. This document covers both global (`~/.aishell/config.yaml`) and project-specific (`.aishell/config.yaml`) configuration files.

**Last updated:** v2.8.0

---

## Table of Contents

- [Configuration Files](#configuration-files)
- [Config Inheritance (extends)](#config-inheritance-extends)
- [Full Annotated Example](#full-annotated-example)
- [Configuration Options](#configuration-options)
  - [extends](#extends)
  - [mounts](#mounts)
  - [env](#env)
  - [ports](#ports)
  - [docker_args](#docker_args)
  - [pre_start](#pre_start)
  - [harness_args](#harness_args)
  - [gitleaks_freshness_check](#gitleaks_freshness_check)
  - [detection](#detection)
- [Common Patterns](#common-patterns)

---

## Configuration Files

aishell supports two configuration files that can work together or independently:

| File | Location | Purpose | Scope |
|------|----------|---------|-------|
| **Global config** | `~/.aishell/config.yaml` | Default settings for all projects | All projects on this machine |
| **Project config** | `.aishell/config.yaml` | Project-specific overrides | Single project |

**Loading behavior:**

1. **Project config exists:** Uses project config (with optional global merge)
2. **No project config:** Falls back to global config
3. **Neither exists:** Uses aishell defaults (minimal mounts only)

---

## Config Inheritance (extends)

The `extends` key controls how project and global configs relate.

### Syntax

```yaml
# Project config (.aishell/config.yaml)
extends: global  # Merge with global config (default)
```

```yaml
# Project config (.aishell/config.yaml)
extends: none    # Ignore global config
```

### Merge Strategy

When `extends: global` (default), configs merge according to data type:

| Type | Keys | Behavior | Example |
|------|------|----------|---------|
| **Lists** | `mounts`, `ports`, `docker_args` | Concatenate (global + project) | Global: `["~/.gitconfig"]`<br/>Project: `["~/data:/data"]`<br/>Result: Both mounts |
| **Maps** | `env` | Shallow merge (project overrides global keys) | Global: `{DEBUG: "false"}`<br/>Project: `{DEBUG: "true"}`<br/>Result: `{DEBUG: "true"}` |
| **Map-of-lists** | `harness_args` | Merge keys, concatenate per-harness lists | Global: `{claude: ["--verbose"]}`<br/>Project: `{claude: ["--model", "sonnet"]}`<br/>Result: `{claude: ["--verbose", "--model", "sonnet"]}` |
| **Scalars** | `pre_start`, `gitleaks_freshness_check` | Project replaces global | Global: `pre_start: "echo hi"`<br/>Project: `pre_start: ["echo", "bye"]`<br/>Result: `"echo && bye"` |
| **Custom** | `detection` | Custom merge (see [detection](#detection)) | Enabled: project wins<br/>Patterns: map merge<br/>Allowlist: concatenate |

### When to use `extends: none`

Use `extends: none` when:

- **Testing isolation:** You want to ensure only project config is active
- **Conflicting requirements:** Global config interferes with project needs
- **Explicit configuration:** You prefer to specify everything in project config

**Note:** Even with `extends: none`, aishell always mounts the project directory (at the same path as on the host). This is the only core mount required for operation. Add `~/.ssh` to your mounts config if you need SSH access inside the container.

---

## Full Annotated Example

Complete example showing all available options:

```yaml
# =============================================================================
# EXTENDS - Config inheritance strategy
# =============================================================================
# Controls how this project config relates to global (~/.aishell/config.yaml):
#   - "global" (default): Merge with global config
#       - Lists (mounts, ports, docker_args): concatenate (global + project)
#       - Maps (env): shallow merge (project overrides global)
#       - Scalars (pre_start): project replaces global
#   - "none": This config fully replaces global (no merging)

extends: global

# =============================================================================
# MOUNTS - Additional directories to mount into container
# =============================================================================
# Supports two formats:
#   - source-only: mounts at same path inside container
#   - source:dest: mounts source at dest path
mounts:
  - ~/.gitconfig                      # Git config (same path)
  - /host/path:/container/path        # Custom mapping
  - ~/data:/data                      # Home expansion works
  - ~/.aws:/home/user/.aws:ro         # Read-only mount

# =============================================================================
# ENV - Environment variables
# =============================================================================
# Supports two formats:
#
# Map format (recommended):
env:
  MY_VAR: "hello"                     # Literal value
  DEBUG: "true"                       # Another literal
  EDITOR:                             # Passthrough from host (empty value)
  DATABASE_URL: "postgres://localhost/mydb"

# Array format (alternative):
# env:
#   - MY_VAR=hello                    # Literal with =
#   - DEBUG=true                      # Another literal
#   - EDITOR                          # Passthrough (no =)
#   - DATABASE_URL=postgres://localhost/mydb

# =============================================================================
# PORTS - Port mappings (host:container)
# =============================================================================
# Format: HOST_PORT:CONTAINER_PORT or IP:HOST_PORT:CONTAINER_PORT
ports:
  - "8080:8080"                       # Web server
  - "3000:3000"                       # Dev server
  - "127.0.0.1:5432:5432"             # Postgres (localhost only)
  - "9090:9090/udp"                   # UDP protocol

# =============================================================================
# DOCKER_ARGS - Additional docker run arguments
# =============================================================================
# Supports string (space-separated) or array format
# WARNING: Some flags are dangerous (--privileged, --network=host, etc.)

# String format:
# docker_args: "--cpus=2 --memory=4g"

# Array format (recommended for complex args):
docker_args:
  - "--cpus=2"
  - "--memory=4g"
  - "--hostname=aishell"
  - "--add-host=myhost:127.0.0.1"

# =============================================================================
# PRE_START - Command to run before shell starts
# =============================================================================
# Runs in background, output goes to /tmp/pre-start.log
# Useful for starting services needed by your project

pre_start: "redis-server --daemonize yes"

# More complex examples:
# pre_start: "docker-compose -f ./docker-compose.yml up -d"
# pre_start: "postgres -D /data/postgres & nginx -g 'daemon off;' &"

# =============================================================================
# HARNESS_ARGS - Default arguments for AI harnesses
# =============================================================================
# Per-harness default arguments that are automatically prepended to CLI args.
# Useful for flags you always want (--plugin, --model, etc.)
#
# Merging behavior:
#   - Global and project harness_args concatenate per-harness
#   - Defaults come first, CLI args follow (CLI can override by position)
#   - String values auto-convert to single-element lists

harness_args:
  claude:
    - "--plugin"                      # Plugin flag
    - "context7"                      # Plugin name
    - "--model"                       # Model flag
    - "sonnet"                        # Model name
  opencode:
    - "--provider"
    - "anthropic"
  codex:
    - "--verbose"
  gemini:
    - "--model"
    - "gemini-2.0-flash-exp"

# String shorthand (single arg):
# harness_args:
#   claude: "--verbose"               # Auto-converts to ["--verbose"]

# =============================================================================
# GITLEAKS_FRESHNESS_CHECK - Gitleaks scan freshness warning toggle
# =============================================================================
# When true (default), shows a warning before container launch if gitleaks
# scan is stale (>7 days old) or has never been run. Advisory only - never
# blocks execution.
#
# The warning reminds you to run: aishell gitleaks dir .
#
# Set to false to disable the freshness warning entirely.

gitleaks_freshness_check: true

# To disable:
# gitleaks_freshness_check: false

# =============================================================================
# DETECTION - Sensitive file detection configuration
# =============================================================================
# Controls the filename-based sensitive file detection that runs before
# container launch. Detects .env files, SSH keys, cloud credentials, etc.

detection:
  # Global toggle - set to false to disable all filename detection
  # Default: true (enabled)
  enabled: true

  # Custom patterns - add project-specific sensitive file patterns
  # Extends defaults (doesn't replace). Patterns use glob syntax.
  #
  # Full syntax with reason:
  custom_patterns:
    "*.secret":
      severity: high
      reason: "Project secret file"
    "config/credentials.*":
      severity: medium
      reason: "Credential config files"

  # Shorthand syntax (severity only):
  # custom_patterns:
  #   "*.frubas": high
  #   "internal/*.key": medium

  # Allowlist - suppress false positives for specific files
  # Each entry requires 'path' and 'reason' keys.
  # Path can be: exact path, filename-only, or glob pattern.
  #
  # Examples:
  allowlist:
    - path: ".env.ci"                 # Exact filename
      reason: "CI template, no secrets"
    - path: "test/fixtures/*.key"     # Glob pattern
      reason: "Test fixture keys"
    - path: "docs/example.pem"        # Specific file
      reason: "Documentation example"
```

---

## Configuration Options

### extends

**Purpose:** Control config inheritance between global and project configs.

**Type:** String

**Values:**
- `"global"` (default) - Merge with global config
- `"none"` - Ignore global config

**Example:**

```yaml
# Use project config only, ignore global
extends: none
```

**Notes:**
- Only valid in project config (`.aishell/config.yaml`)
- In global config (`~/.aishell/config.yaml`), this key is ignored
- See [Config Inheritance](#config-inheritance-extends) for merge behavior

---

### mounts

**Purpose:** Mount additional directories into the container.

**Type:** List of strings

**Formats:**

1. **Source-only:** `/host/path` (mounts at same path in container)
2. **Source:Dest:** `/host/path:/container/path` (custom container path)
3. **Read-only:** `/host/path:/container/path:ro` (prevents container writes)

**Example:**

```yaml
mounts:
  - ~/.gitconfig                      # Git config at same path
  - ~/projects/shared:/shared         # Custom mount point
  - ~/.aws:/home/user/.aws:ro         # Read-only AWS credentials
  - /etc/timezone:/etc/timezone:ro    # Share host timezone
```

**Notes:**
- Home directory expansion (`~`) works in source paths
- Container paths are absolute (must start with `/`)
- Read-only (`:ro`) prevents container from modifying host files
- Project directory (at same path as host) is always mounted (built-in)

**Merge behavior:** Global and project mounts concatenate (both apply).

**Security warning:** Mounting sensitive paths (e.g., `/etc`, `/var/run/docker.sock`) triggers warnings. Use `--unsafe` to bypass.

**⚠️ SSH Keys Security Note:**

Mounting `~/.ssh` gives AI harnesses access to your private keys. Since these agents can execute arbitrary code, they could:
- Read your private keys
- Authenticate to any server your keys access (git push, SSH to servers)
- Potentially expose key material in conversation context

**Safer alternatives:**

| Instead of | Consider |
|------------|----------|
| Mounting `~/.ssh` | Use HTTPS + tokens for git (`GH_TOKEN`, `GITLAB_TOKEN`) |
| Mounting all keys | Mount only a specific deploy key with limited permissions |

**If you must mount SSH keys:**

```yaml
# Mount read-only and only what's needed
mounts:
  - ~/.ssh/id_ed25519_deploy:~/.ssh/id_ed25519:ro
  - ~/.ssh/known_hosts:~/.ssh/known_hosts:ro
```

For most workflows, AI harnesses don't need SSH access — git operations work with HTTPS and tokens.

---

### env

**Purpose:** Pass environment variables to the container.

**Type:** Map (recommended) or List

**Formats:**

1. **Map with value:** `KEY: "value"` (literal value)
2. **Map without value:** `KEY:` (passthrough from host)
3. **List with =:** `"KEY=value"` (literal value)
4. **List without =:** `"KEY"` (passthrough from host)

**Example:**

```yaml
# Map format (recommended)
env:
  DEBUG: "true"                       # Literal value
  DATABASE_URL: "postgres://db/app"   # Connection string
  EDITOR:                             # Passthrough from host $EDITOR
  AWS_REGION: "us-east-1"

# List format (alternative)
# env:
#   - DEBUG=true
#   - DATABASE_URL=postgres://db/app
#   - EDITOR                          # Passthrough
#   - AWS_REGION=us-east-1
```

**Notes:**
- Map format recommended for readability
- Empty map value (`:`) passes through host environment variable
- Literal values must be quoted strings
- Both formats can coexist (map + list), but pick one for consistency

**Merge behavior:** Global and project envs shallow-merge (project keys override global).

**Common use cases:**

| Use Case | Example |
|----------|---------|
| Debug flags | `DEBUG: "true"` |
| API keys | `OPENAI_API_KEY:` (passthrough) |
| Database URLs | `DATABASE_URL: "postgres://..."` |
| Tool config | `EDITOR: "vim"` |

---

### ports

**Purpose:** Expose container ports to host.

**Type:** List of strings

**Formats:**

1. **Simple:** `"HOST_PORT:CONTAINER_PORT"` (all interfaces)
2. **IP binding:** `"IP:HOST_PORT:CONTAINER_PORT"` (specific interface)
3. **Protocol:** `"HOST_PORT:CONTAINER_PORT/PROTOCOL"` (tcp/udp)

**Example:**

```yaml
ports:
  - "8080:8080"                       # Web app on all interfaces
  - "127.0.0.1:5432:5432"             # PostgreSQL on localhost only
  - "3000:3000"                       # Dev server
  - "9090:9090/udp"                   # UDP service
```

**Notes:**
- Default protocol is `tcp`
- `127.0.0.1` binding restricts to localhost (more secure)
- `0.0.0.0` binding exposes to all network interfaces
- Host port must be free (Docker will error if already in use)

**Merge behavior:** Global and project ports concatenate (both apply).

**Common use cases:**

| Service | Example |
|---------|---------|
| Web server | `"8080:8080"` |
| Database | `"127.0.0.1:5432:5432"` |
| Jupyter | `"8888:8888"` |
| WebSocket | `"3001:3001"` |

---

### docker_args

**Purpose:** Pass additional arguments to `docker run`.

**Type:** String (space-separated) or List

**Example:**

```yaml
# Array format (recommended)
docker_args:
  - "--cpus=2"                        # Limit CPU cores
  - "--memory=4g"                     # Limit memory
  - "--hostname=aishell"              # Custom hostname
  - "--add-host=db:192.168.1.100"    # Custom /etc/hosts entry

# String format (alternative)
# docker_args: "--cpus=2 --memory=4g --hostname=aishell"
```

**Notes:**
- Array format recommended for complex arguments
- Flags are passed directly to `docker run` (after built-in args)
- Some flags trigger security warnings (see below)

**Merge behavior:** Global and project docker_args concatenate (both apply).

**Dangerous flags (trigger warnings):**

| Flag | Risk |
|------|------|
| `--privileged` | Full host access |
| `--cap-add=SYS_ADMIN` | Elevated capabilities |
| `--cap-add=ALL` | All capabilities |
| `--security-opt apparmor=unconfined` | Disable security |
| `--network=host` | No network isolation |
| `--pid=host` | Host process namespace |
| `--ipc=host` | Host IPC namespace |

**Common use cases:**

| Use Case | Example |
|----------|---------|
| Resource limits | `--cpus=2 --memory=4g` |
| Custom hostname | `--hostname=dev-box` |
| DNS config | `--dns=8.8.8.8` |
| /etc/hosts | `--add-host=api:192.168.1.50` |
| Tmpfs mount | `--tmpfs=/tmp:rw,noexec,nosuid,size=1g` |

---

### pre_start

**Purpose:** Run a background command before the shell/harness starts.

**Type:** String or List (v2.5+)

**Formats:**

**String format (original):**
```yaml
pre_start: "redis-server --daemonize yes"
```

**List format (v2.5+):**
```yaml
pre_start:
  - "echo 'Starting services...'"
  - "redis-server --daemonize yes"
  - "sleep 2"
  - "echo 'Services ready'"
```

**Behavior:**
- **String:** Executed as-is via `sh -c`
- **List:** Items joined with ` && ` separator, then executed via `sh -c`
- **Empty list:** No pre-start command runs
- **Empty items:** Filtered out automatically

**Example transformation:**
```yaml
# List input
pre_start:
  - "echo 'Step 1'"
  - "echo 'Step 2'"

# Becomes equivalent to
pre_start: "echo 'Step 1' && echo 'Step 2'"
```

**Notes:**
- Command runs in background (via `nohup ... &`)
- Output redirected to `/tmp/pre-start.log` (check if command fails)
- Runs as container user (not root)
- Use `&&` separator means if any command fails, execution stops
- Container doesn't wait for command completion (non-blocking)

**Merge behavior:** Project replaces global (only one pre_start runs).

**Common use cases:**

| Use Case | Example |
|----------|---------|
| Database | `["postgres -D /data/postgres &"]` |
| Redis | `["redis-server --daemonize yes"]` |
| Multiple services | `["redis-server --daemonize yes", "nginx -g 'daemon off;' &"]` |
| Wait for deps | `["redis-server --daemonize yes", "sleep 2", "echo 'Ready'"]` |

---

### harness_args

**Purpose:** Set default arguments for AI harnesses (prepended to CLI args).

**Type:** Map of harness name to list (or string)

**Harness names:** `claude`, `opencode`, `codex`, `gemini`

**Example:**

```yaml
harness_args:
  # Claude defaults
  claude:
    - "--plugin"
    - "context7"
    - "--model"
    - "sonnet"

  # OpenCode defaults
  opencode:
    - "--provider"
    - "anthropic"

  # Codex defaults
  codex:
    - "--verbose"

  # Gemini defaults
  gemini:
    - "--model"
    - "gemini-2.0-flash-exp"

# String shorthand (single arg)
# harness_args:
#   claude: "--verbose"  # Converts to ["--verbose"]
```

**Notes:**
- Defaults prepend to CLI args (CLI comes after)
- Positional flags: Last occurrence wins (CLI can override defaults)
- String values auto-convert to single-element lists

**Merge behavior:** Per-harness lists concatenate (global defaults + project defaults).

**Argument order:**

```
[global defaults] + [project defaults] + [CLI args]
```

**Example:**

```yaml
# Global config
harness_args:
  claude: ["--verbose"]

# Project config
harness_args:
  claude: ["--model", "sonnet"]

# CLI
$ aishell claude --workspace /workspace

# Actual command
claude --verbose --model sonnet --workspace /workspace
```

**Common use cases:**

| Harness | Default Args | Purpose |
|---------|--------------|---------|
| Claude | `--plugin context7` | Load plugin |
| Claude | `--model sonnet` | Default model |
| OpenCode | `--provider anthropic` | API provider |
| Codex | `--verbose` | Debug output |
| Gemini | `--model gemini-2.0-flash-exp` | Specific model |

---

### gitleaks_freshness_check

**Purpose:** Toggle advisory warnings when Gitleaks scan is stale (>7 days).

**Type:** Boolean

**Default:** `true`

**Example:**

```yaml
# Enable freshness warnings (default)
gitleaks_freshness_check: true

# Disable freshness warnings
gitleaks_freshness_check: false
```

**Notes:**
- Advisory only - never blocks execution
- Warning appears before container launch (for shell/claude/opencode, not gitleaks itself)
- "Stale" = scan >7 days old or never run
- Warning suggests: `aishell gitleaks dir .`
- Timestamp stored in `~/.aishell/gitleaks-scan.edn` (per-project)

**Merge behavior:** Project replaces global (scalar).

**When to disable:**

- **CI/CD environments:** Automated systems don't need reminders
- **Trusted projects:** Internal code with no sensitive data
- **Frequent annoyers:** You run Gitleaks separately and don't want reminders

---

### detection

**Purpose:** Configure sensitive file detection (filename-based scanning).

**Type:** Map with three keys: `enabled`, `custom_patterns`, `allowlist`

**Structure:**

```yaml
detection:
  enabled: true | false
  custom_patterns:
    "pattern":
      severity: high | medium | low
      reason: "explanation"
  allowlist:
    - path: "path/to/file"
      reason: "why it's safe"
```

#### detection.enabled

**Purpose:** Global toggle for all filename-based detection.

**Type:** Boolean

**Default:** `true`

**Example:**

```yaml
detection:
  enabled: false  # Disable all detection
```

**Notes:**
- `false` = skip all detection checks (same as `--unsafe`)
- `true` = run detection with default + custom patterns
- Merge behavior: Project wins (scalar)

#### detection.custom_patterns

**Purpose:** Add project-specific sensitive file patterns.

**Type:** Map of glob pattern to severity/reason

**Severities:** `high`, `medium`, `low`

**Example:**

```yaml
detection:
  custom_patterns:
    # Full syntax
    "*.secret":
      severity: high
      reason: "Project secret files"

    "config/credentials.*":
      severity: medium
      reason: "Legacy credential config"

    # Shorthand (severity only)
    "*.key": high
    "internal/*.pem": medium
```

**Notes:**
- Patterns use glob syntax (`*`, `**`, `?`, `[]`)
- Extends default patterns (doesn't replace)
- Reason is optional (shown in warning output)
- Merge behavior: Global and project patterns map-merge (project keys override global)

**Default patterns (built-in):**

- `.env*` (high) - Environment variables
- `*.pem`, `*.key` (high) - Private keys
- `gcloud-credentials.json` (high) - GCP credentials
- `.aws/credentials` (high) - AWS credentials
- And more... (see `aishell.detection.patterns` namespace)

#### detection.allowlist

**Purpose:** Suppress false positives for specific files.

**Type:** List of maps with `path` and `reason` keys

**Path formats:**
- Exact path: `".env.ci"`
- Filename only: `"test.key"`
- Glob pattern: `"test/fixtures/*.pem"`

**Example:**

```yaml
detection:
  allowlist:
    - path: ".env.test"
      reason: "Test environment template, no secrets"

    - path: "test/fixtures/*.key"
      reason: "Test fixture keys (fake data)"

    - path: "docs/example-credentials.json"
      reason: "Documentation example"
```

**Notes:**
- Each entry MUST have both `path` and `reason` keys
- Allowlisted files are completely hidden (no warning)
- Merge behavior: Global and project allowlists concatenate (both apply)

#### detection merge behavior

The `detection` config has custom merge logic:

| Key | Merge Type | Behavior |
|-----|------------|----------|
| `enabled` | Scalar | Project wins |
| `custom_patterns` | Map | Project keys override global keys |
| `allowlist` | List | Concatenate (both apply) |

**Example merge:**

```yaml
# Global
detection:
  enabled: true
  custom_patterns:
    "*.secret": high
  allowlist:
    - path: ".env.ci"
      reason: "CI template"

# Project
detection:
  custom_patterns:
    "*.key": high
  allowlist:
    - path: "test.pem"
      reason: "Test fixture"

# Merged result
detection:
  enabled: true                # From global (project didn't override)
  custom_patterns:
    "*.secret": high           # From global
    "*.key": high              # From project
  allowlist:
    - path: ".env.ci"          # From global
      reason: "CI template"
    - path: "test.pem"         # From project
      reason: "Test fixture"
```

---

## Common Patterns

### Database Credentials Mounting

Mount database credentials from host into container:

```yaml
mounts:
  - ~/.pgpass:/home/user/.pgpass:ro           # PostgreSQL password file
  - ~/.my.cnf:/home/user/.my.cnf:ro           # MySQL config

env:
  DATABASE_URL: "postgres://localhost/mydb"
```

### Port Exposure for Web Servers

Expose ports for local development servers:

```yaml
ports:
  - "8080:8080"                # Backend API
  - "3000:3000"                # Frontend dev server
  - "127.0.0.1:5432:5432"      # PostgreSQL (localhost only)
```

### Custom Docker Resource Limits

Limit container resource usage:

```yaml
docker_args:
  - "--cpus=2"                 # Max 2 CPU cores
  - "--memory=4g"              # Max 4GB RAM
  - "--memory-swap=4g"         # No swap beyond memory limit
  - "--pids-limit=100"         # Limit process count
```

### Per-Harness Model Configuration

Set default models for different harnesses:

```yaml
harness_args:
  claude:
    - "--model"
    - "sonnet"                 # Default to Claude Sonnet

  gemini:
    - "--model"
    - "gemini-2.0-flash-exp"  # Default to Flash model
```

### Service Startup with pre_start

Start background services before harness runs:

```yaml
pre_start: "redis-server --daemonize yes && postgres -D /data/postgres &"
```

### Project-Specific Sensitive File Patterns

Detect project-specific credential files:

```yaml
detection:
  custom_patterns:
    "secrets/*.yaml":
      severity: high
      reason: "Project secret configuration"

    "internal/api-keys.json":
      severity: high
      reason: "Internal API keys"

  allowlist:
    - path: "secrets/example.yaml"
      reason: "Documentation example"
```

### Isolated Project Config (No Global Merge)

Use only project config, ignore global:

```yaml
extends: none

mounts:
  - ~/my-project-data:/data

env:
  PROJECT_ENV: "production"

ports:
  - "8080:8080"
```

### Multi-Service Development Environment

Full development setup with services and tools:

```yaml
extends: global

mounts:
  - ~/projects/shared:/shared               # Shared code
  - ~/.docker:/home/user/.docker:ro         # Docker config

env:
  DATABASE_URL: "postgres://localhost/dev"
  REDIS_URL: "redis://localhost:6379"

ports:
  - "127.0.0.1:5432:5432"                   # PostgreSQL
  - "127.0.0.1:6379:6379"                   # Redis
  - "8080:8080"                             # API server
  - "3000:3000"                             # Frontend

docker_args:
  - "--cpus=4"
  - "--memory=8g"

pre_start:
  - "redis-server --daemonize yes"
  - "postgres -D /data/postgres &"

harness_args:
  claude:
    - "--model"
    - "sonnet"
    - "--plugin"
    - "context7"
```

---

## Build Options

### Harness Selection Flags

**Purpose:** Choose which AI harnesses to include in the harness volume.

**Usage:**
```bash
# Single harness
aishell build --with-claude

# Multiple harnesses
aishell build --with-claude --with-opencode --with-codex

# With version pinning
aishell build --with-claude=2.0.22 --with-codex=0.1.2025062501
```

**Available harnesses:**
- `--with-claude` - Anthropic Claude Code
- `--with-opencode` - Multi-provider OpenCode
- `--with-codex` - OpenAI Codex CLI
- `--with-gemini` - Google Gemini CLI

**Version pinning:**
Use `=VERSION` syntax to pin specific versions:
```bash
--with-claude=2.0.22
--with-codex=0.1.2025062501
```

Omit version for latest:
```bash
--with-claude  # Uses latest version
```

**State tracking:**
Harness selection is saved in `~/.aishell/state.edn` and preserved across updates.

---

### --without-gitleaks

**Purpose:** Skip Gitleaks installation during build to reduce foundation image size.

**Usage:**
```bash
aishell build --with-claude --without-gitleaks
```

**Behavior:**
- By default, Gitleaks is installed in the foundation image (~15MB)
- `--without-gitleaks` skips installation
- Build state records installation status in `~/.aishell/state.edn`
- `aishell --help` still shows `gitleaks` command (may work via host installation)

**State tracking:**
```bash
# Check what was installed
cat ~/.aishell/state.edn
# Shows :with-gitleaks true or false
```

**When to use:**
- **Minimal images:** Reduce foundation image size when Gitleaks not needed
- **External Gitleaks:** Using Gitleaks installed on host instead
- **CI/CD:** Build environments where secret scanning handled elsewhere

**Image size impact:**
- With Gitleaks: ~280MB
- Without Gitleaks: ~265MB
- Savings: ~15MB

**Note:** The `aishell gitleaks` command may still work if Gitleaks is installed on your host system or mounted into the container.

---

### --force (build flag)

**Purpose:** Force full foundation image rebuild, bypassing Docker cache.

**Usage:**
```bash
aishell build --with-claude --force
```

**Behavior:**
- Passes `--no-cache` to `docker build`
- Rebuilds all layers from scratch
- Useful for troubleshooting build issues or ensuring fresh dependencies

**When to use:**
- **Troubleshooting:** Build behaving unexpectedly
- **Fresh start:** Want to ensure all system packages are latest
- **Cache corruption:** Suspect Docker cache has stale layers

---

## Update Command

### aishell update

**Purpose:** Refresh harness tools to latest versions without rebuilding foundation image.

**Usage:**
```bash
# Refresh harness volume only (fast)
aishell update

# Also rebuild foundation image
aishell update --force
```

**Default behavior (no flags):**
1. Delete existing harness volume
2. Create new harness volume
3. Populate volume with harness tools (npm install, binary download)
4. Update state.edn with new build-time
5. Foundation image untouched

**With --force:**
1. Rebuild foundation image with `--no-cache`
2. Delete existing harness volume
3. Create new harness volume
4. Populate volume with harness tools
5. Update state.edn with new build-time and foundation-hash

**Preserved from last build:**
- Which harnesses are enabled (`--with-claude`, etc.)
- Harness version pins
- Gitleaks installation status

**Cannot change harness selection:**
To add/remove harnesses, use `aishell build`:
```bash
# Add OpenCode to existing Claude installation
aishell build --with-claude --with-opencode
```

**When to use update:**
- **npm package updates:** Get latest harness versions
- **Regular refresh:** Periodically update tools
- **After npm publish:** New harness version released

**When to use update --force:**
- **System package updates:** Debian/Node.js security updates
- **Foundation changes:** After aishell version upgrade
- **Troubleshooting:** Foundation image behaving unexpectedly
