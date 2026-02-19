# aishell Configuration Reference

Complete reference for aishell configuration options, covering both the global (`~/.aishell/config.yaml`) and project-specific (`.aishell/config.yaml`) config files.

**Last updated:** v3.8.0

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
- [Global Base Image Customization](#global-base-image-customization)
- [Common Patterns](#common-patterns)

---

## Configuration Files

aishell reads two configuration files, which can work together or independently:

| File | Location | Purpose | Scope |
|------|----------|---------|-------|
| **Global config** | `~/.aishell/config.yaml` | Default settings for all projects | All projects on this machine |
| **Project config** | `.aishell/config.yaml` | Project-specific overrides | Single project |

**Loading behavior:**

1. **Project config exists:** aishell uses the project config (with optional global merge)
2. **No project config:** aishell falls back to global config
3. **Neither exists:** aishell uses built-in defaults (minimal mounts only)

**Platform Differences:**

| Aspect | Unix/Linux/macOS | Windows | Notes |
|--------|------------------|---------|-------|
| **Config location** | `~/.aishell/config.yaml` | `~/.aishell/config.yaml` | Same on all platforms |
| **State location** | `~/.local/state/aishell/state.edn` | `%LOCALAPPDATA%\aishell\state.edn` | Windows follows platform convention (AppData) |
| **Config format** | YAML | YAML | Identical format across platforms |
| **Path separators** | Native `/` | Forward slashes `/` in config | aishell normalizes Windows paths automatically |

**Note:** While config files live in the same location (`~/.aishell/`), Windows uses a platform-specific state directory (`%LOCALAPPDATA%\aishell`) following Windows conventions for application data storage.

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

With `extends: global` (the default), aishell merges configs by data type:

| Type | Keys | Behavior | Example |
|------|------|----------|---------|
| **Lists** | `mounts`, `ports`, `docker_args` | Concatenate (global + project) | Global: `["~/.gitconfig"]`<br/>Project: `["~/data:/data"]`<br/>Result: Both mounts |
| **Maps** | `env` | Shallow merge (project overrides global keys) | Global: `{DEBUG: "false"}`<br/>Project: `{DEBUG: "true"}`<br/>Result: `{DEBUG: "true"}` |
| **Map-of-lists** | `harness_args` | Merge keys, concatenate per-harness lists | Global: `{claude: ["--verbose"]}`<br/>Project: `{claude: ["--model", "sonnet"]}`<br/>Result: `{claude: ["--verbose", "--model", "sonnet"]}` |
| **Scalars** | `pre_start`, `gitleaks_freshness_check` | Project replaces global | Global: `pre_start: "echo hi"`<br/>Project: `pre_start: ["echo", "bye"]`<br/>Result: `"echo && bye"` |
| **Custom** | `detection` | Custom merge (see [detection](#detection)) | Enabled: project wins<br/>Patterns: map merge<br/>Allowlist: concatenate |

### When to use `extends: none`

Use `extends: none` when:

- **Testing isolation:** Only the project config should be active
- **Conflicts:** The global config interferes with project needs
- **Self-contained config:** You prefer to specify everything in the project config

**Note:** Even with `extends: none`, aishell always mounts the project directory at the same host path. This is the only mount required for operation. Add `~/.ssh` to your mounts if you need SSH access inside the container.

---

## Full Annotated Example

Example showing all available options:

```yaml
# =============================================================================
# EXTENDS - Config inheritance strategy
# =============================================================================
# Controls how this project config relates to the global (~/.aishell/config.yaml):
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
# Useful for starting services your project needs

pre_start: "redis-server --daemonize yes"

# More complex examples:
# pre_start: "docker-compose -f ./docker-compose.yml up -d"
# pre_start: "postgres -D /data/postgres & nginx -g 'daemon off;' &"

# =============================================================================
# HARNESS_ARGS - Default arguments for AI harnesses
# =============================================================================
# Per-harness default arguments, prepended to CLI args.
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
  pi:
    - "--print"
    - "hello"

# String shorthand (single arg):
# harness_args:
#   claude: "--verbose"               # Auto-converts to ["--verbose"]

# =============================================================================
# GITLEAKS_FRESHNESS_CHECK - Gitleaks scan freshness warning toggle
# =============================================================================
# When true (default), warns before container launch if the gitleaks scan
# is stale. Requires --with-gitleaks build flag; has no effect without it.
# scan is stale (>7 days old) or has never run. Advisory only -- never
# blocks execution.
#
# When Gitleaks is installed, the warning reminds you to run: aishell gitleaks dir .
#
# Set to false to disable the freshness warning entirely.

gitleaks_freshness_check: true

# To disable:
# gitleaks_freshness_check: false

# =============================================================================
# DETECTION - Sensitive file detection configuration
# =============================================================================
# Controls filename-based sensitive file detection before container launch.
# Detects .env files, SSH keys, cloud credentials, etc.

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

**Purpose:** Control config inheritance between global and project config.

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
- Valid only in project config (`.aishell/config.yaml`)
- The global config (`~/.aishell/config.yaml`) ignores this key
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
- Container paths must be absolute (start with `/`)
- Read-only (`:ro`) prevents the container from modifying host files
- aishell always mounts the project directory at the same host path

**Cross-Platform Path Notes:**

- **Home directory expansion:** `~` expands to `$USERPROFILE` on Windows, `$HOME` on Unix. aishell normalizes all paths to forward slashes for Docker mount commands.

- **Windows paths:** Use forward slashes in config files (e.g., `C:/Users/name/data:/data`), not backslashes. aishell automatically normalizes paths via `babashka.fs/unixify`.

- **Source-only mounts on Windows:** Paths without explicit destinations (e.g., `~/.ssh`) map to the same path inside the container, normalized to forward slashes.

- **Example showing cross-platform mounts:**
  ```yaml
  mounts:
    # Works on all platforms - tilde expansion
    - ~/.ssh
    - ~/.gitconfig

    # Windows explicit path (forward slashes)
    - C:/Users/name/project-data:/data

    # Unix explicit path
    - /home/name/project-data:/data

    # Cross-platform with home expansion
    - ~/shared:/shared
  ```

**Merge behavior:** Global and project mounts concatenate (both apply).

**Security warning:** Mounting sensitive paths (e.g., `/etc`, `/var/run/docker.sock`) triggers warnings. Pass `--unsafe` to bypass.

**SSH Keys Security Note:**

Mounting `~/.ssh` exposes your private keys to AI harnesses. Because these agents can execute arbitrary code, they could:
- Read your private keys
- Authenticate to any server your keys reach (git push, SSH to servers)
- Expose key material in conversation context

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

Most workflows need no SSH access -- git operations work with HTTPS and tokens.

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
- Map format is more readable
- An empty map value (`:`) passes through the host environment variable
- Literal values must be quoted strings
- Both formats can coexist, but pick one for consistency

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
- `127.0.0.1` restricts the binding to localhost (more secure)
- `0.0.0.0` exposes the port on all network interfaces
- The host port must be free; Docker errors if it is already in use

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
- Array format is clearer for multi-flag arguments
- aishell passes flags directly to `docker run` (after built-in args)
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

**Purpose:** Run a command in the background before the shell/harness starts.

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
- **String:** Runs as-is via `sh -c`
- **List:** Items join with ` && `, then run via `sh -c`
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
- Runs in background via `nohup ... &`
- Output goes to `/tmp/pre-start.log` (check this file on failure)
- Runs as the container user, not root
- The `&&` separator stops execution if any command fails
- The container does not wait for command completion (non-blocking)

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

**Purpose:** Set default arguments for AI harnesses, prepended to CLI args.

**Type:** Map of harness name to list (or string)

**Harness names:** `claude`, `opencode`, `codex`, `gemini`, `pi`

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

  # Pi defaults
  pi:
    - "--print"
    - "hello"

# String shorthand (single arg)
# harness_args:
#   claude: "--verbose"  # Converts to ["--verbose"]
```

**Notes:**
- Defaults precede CLI args
- For positional flags, the last occurrence wins (CLI overrides defaults)
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
| Pi | `--print hello` | Default prompt |

---

### gitleaks_freshness_check

**Purpose:** Toggle advisory warnings when Gitleaks scan is stale (>7 days).

**Type:** Boolean

**Default:** `true`

**Requires:** `--with-gitleaks` build flag. When Gitleaks is not installed, this setting has no effect (no warnings are shown).

**Example:**

```yaml
# Enable freshness warnings (default)
gitleaks_freshness_check: true

# Disable freshness warnings
gitleaks_freshness_check: false
```

**Notes:**
- Advisory only -- never blocks execution
- The warning appears before container launch (for shell/claude/opencode, not gitleaks itself)
- "Stale" means the scan is >7 days old or has never run
- The warning suggests running `aishell gitleaks dir .`
- aishell stores timestamps in `~/.aishell/gitleaks-scan.edn` (per-project)

**Merge behavior:** Project replaces global (scalar).

**When to disable:**

- **CI/CD environments:** Automated systems need no reminders
- **Trusted projects:** Internal code with no sensitive data
- **Separate scanning:** You run Gitleaks outside aishell

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
- Extends default patterns rather than replacing them
- Reason is optional (shown in warning output)
- Merge: global and project patterns map-merge; project keys override global

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
- Each entry requires both `path` and `reason` keys
- Allowlisted files produce no warning
- Merge: global and project allowlists concatenate

#### detection merge behavior

The `detection` config uses custom merge logic:

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

## Global Base Image Customization

Advanced users can customize the base Docker image used by all projects. This adds a layer between the foundation image and per-project extensions, forming a three-tier image chain:

```
aishell:foundation -> aishell:base -> aishell:ext-{hash}
```

See [ARCHITECTURE.md](ARCHITECTURE.md#three-tier-image-chain--harness-volume) for the full image chain diagram.

### Creating a Global Dockerfile

Create `~/.aishell/Dockerfile` to customize the base image:

```dockerfile
FROM aishell:foundation

# Your global customizations here
```

The recommended `FROM` line is `FROM aishell:foundation`, but this is not enforced. Changes are detected automatically -- the base image rebuilds on the next `aishell` run.

### Use Case Examples

**1. Extra system packages:**

```dockerfile
FROM aishell:foundation

RUN apt-get update && apt-get install -y \
    python3-pip \
    postgresql-client \
    redis-tools \
    && rm -rf /var/lib/apt/lists/*
```

**2. Shell configuration:**

```dockerfile
FROM aishell:foundation

# Global aliases
RUN echo 'alias ll="ls -la"' >> /etc/bash.bashrc && \
    echo 'alias gs="git status"' >> /etc/bash.bashrc

# Custom prompt
RUN echo 'export PS1="\[\033[36m\]\w\[\033[0m\] \$ "' >> /etc/bash.bashrc
```

**3. Development tools:**

```dockerfile
FROM aishell:foundation

RUN pip3 install httpie --break-system-packages && \
    pip3 install pytest --break-system-packages
```

### How It Works

- **Lazy build:** The base image is built on first container run when `~/.aishell/Dockerfile` is detected, not during `aishell setup`.
- **Auto-detection:** aishell hashes the Dockerfile content and compares it to the existing base image label. Changes trigger a rebuild.
- **Cascade rebuilds:** When the base image changes, all per-project extension images rebuild on their next run.
- **Default behavior:** When no `~/.aishell/Dockerfile` exists, `aishell:base` is a tag alias for `aishell:foundation` (zero overhead).

### Project Extension Compatibility

Per-project `.aishell/Dockerfile` files can use either base:

```dockerfile
# Recommended: inherits global customizations
FROM aishell:base

RUN apt-get update && apt-get install -y postgresql-client && rm -rf /var/lib/apt/lists/*
```

```dockerfile
# Also valid: skips global customizations
FROM aishell:foundation

RUN apt-get update && apt-get install -y postgresql-client && rm -rf /var/lib/apt/lists/*
```

Using `FROM aishell:base` is recommended because it inherits any packages, tools, or shell configuration from your global Dockerfile.

### Removing Customizations

To revert to the default base image:

1. Delete `~/.aishell/Dockerfile`
2. On the next run, `aishell:base` reverts to a foundation alias
3. Run `aishell volumes prune` to clean up the orphaned custom base image

### Status Check

```bash
aishell check
# Shows: "Base image: custom (~/.aishell/Dockerfile)" or "Base image: default (foundation alias)"
```

### Build Failures

If your global Dockerfile has errors, the build fails with full Docker output. Fix the Dockerfile or delete it to revert. See [TROUBLESHOOTING.md](TROUBLESHOOTING.md#base-image-build-failures) for details.

---

## Common Patterns

### Database Credentials Mounting

Mount database credentials from the host into the container:

```yaml
mounts:
  - ~/.pgpass:/home/user/.pgpass:ro           # PostgreSQL password file
  - ~/.my.cnf:/home/user/.my.cnf:ro           # MySQL config

env:
  DATABASE_URL: "postgres://localhost/mydb"
```

### Port Exposure for Web Servers

Expose ports for local development:

```yaml
ports:
  - "8080:8080"                # Backend API
  - "3000:3000"                # Frontend dev server
  - "127.0.0.1:5432:5432"      # PostgreSQL (localhost only)
```

### Custom Docker Resource Limits

Limit container resources:

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

Start background services before the harness runs:

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
  pi:
    - "--print"
    - "hello"
```

---

## Setup Options

### Harness Selection Flags

**Purpose:** Choose which AI harnesses to install in the harness volume.

**Usage:**
```bash
# Single harness
aishell setup --with-claude

# Multiple harnesses
aishell setup --with-claude --with-opencode --with-codex --with-pi

# With version pinning
aishell setup --with-claude=2.0.22 --with-codex=0.1.2025062501
```

**Available harnesses:**
- `--with-claude` - Anthropic Claude Code
- `--with-opencode` - Multi-provider OpenCode
- `--with-codex` - OpenAI Codex CLI
- `--with-gemini` - Google Gemini CLI
- `--with-pi` - Mario Zechner's Pi coding agent

**Available tools (non-harness):**
- `--with-openspec` - Fission AI OpenSpec development workflow tool

**Version pinning:**
Use `=VERSION` syntax to pin specific versions:
```bash
--with-claude=2.0.22
--with-codex=0.1.2025062501
--with-openspec=1.2.3
```

Omit version for latest:
```bash
--with-claude  # Uses latest version
```

**State tracking:**
aishell saves harness selection and tool configuration in `~/.aishell/state.edn` and preserves it across updates.

---

### --with-gitleaks

**Purpose:** Install Gitleaks during build for content-based secret scanning.

**Usage:**
```bash
aishell setup --with-claude --with-gitleaks
```

**Behavior:**
- By default, aishell does NOT install Gitleaks
- `--with-gitleaks` enables Gitleaks installation (~15MB added to foundation image)
- Build state records installation status in `~/.aishell/state.edn`
- `aishell --help` shows the `gitleaks` command only when installed

**State tracking:**
```bash
# Check what was installed
cat ~/.aishell/state.edn
# Shows :with-gitleaks true or false
```

**When to use:**
- **Content scanning:** You want deep secret detection beyond filename patterns
- **Pre-commit safety:** Run scans before AI agents see your code
- **Compliance:** Your workflow requires secret scanning

**Image size impact:**
- Without Gitleaks (default): ~265MB
- With Gitleaks: ~280MB
- Cost: ~15MB

**Note:** Without `--with-gitleaks`, the `aishell gitleaks` command is not available.

---

### --force (setup flag)

**Purpose:** Force full foundation image rebuild, bypassing Docker cache.

**Usage:**
```bash
aishell setup --with-claude --force
```

**Behavior:**
- Passes `--no-cache` to `docker build`
- Rebuilds all layers from scratch
- Useful for troubleshooting build issues or pulling fresh dependencies

**When to use:**
- **Troubleshooting:** The build behaves unexpectedly
- **Fresh start:** You want the latest system packages
- **Cache corruption:** You suspect Docker cache has stale layers

---

## Update Command

### aishell update

**Purpose:** Refresh harness tools to latest versions without rebuilding the foundation image.

**Usage:**
```bash
# Refresh harness volume only (fast)
aishell update

# Also rebuild foundation image
aishell update --force
```

**Default behavior (no flags):**
1. Deletes the existing harness volume
2. Creates a new harness volume
3. Populates the volume with harness tools (npm install, binary download)
4. Updates state.edn with the new build time
5. Leaves the foundation image untouched

**With --force:**
1. Rebuilds the foundation image with `--no-cache`
2. Deletes the existing harness volume
3. Creates a new harness volume
4. Populates the volume with harness tools
5. Updates state.edn with new build time and foundation hash

**Preserved from the last build:**
- Enabled harnesses (`--with-claude`, etc.)
- Harness version pins
- Gitleaks installation status (`--with-gitleaks` opt-in)
- OpenSpec installation status (`--with-openspec` opt-in)

**Cannot change harness selection.**
To add or remove harnesses, use `aishell setup`:
```bash
# Add OpenCode to existing Claude installation
aishell setup --with-claude --with-opencode
```

**When to use update:**
- **npm package updates:** Get the latest harness versions
- **Regular refresh:** Periodically update tools
- **After npm publish:** A new harness version was released

**When to use update --force:**
- **System package updates:** Debian/Node.js security patches
- **Foundation changes:** After an aishell version upgrade
- **Troubleshooting:** The foundation image behaves unexpectedly
