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
  - [resources](#resources)
  - [security_profile](#security_profile)
  - [pre_start](#pre_start)
  - [harness_args](#harness_args)
  - [pi_packages](#pi_packages)
  - [update_check](#update_check)
  - [detection](#detection)
  - [Secret scanning](#secret-scanning)
  - [claude_isolation](#claude_isolation)
  - [claude_shared_paths](#claude_shared_paths)
- [Base Image Customization](#base-image-customization)
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
| **Maps** | `env`, `resources` | Shallow merge (project overrides global keys) | Global: `{DEBUG: "false"}`<br/>Project: `{DEBUG: "true"}`<br/>Result: `{DEBUG: "true"}` |
| **Map-of-lists** | `harness_args` | Merge keys, concatenate per-harness lists | Global: `{claude: ["--verbose"]}`<br/>Project: `{claude: ["--model", "sonnet"]}`<br/>Result: `{claude: ["--verbose", "--model", "sonnet"]}` |
| **Scalars** | `pre_start`, `claude_isolation` | Project replaces global | Global: `pre_start: "echo hi"`<br/>Project: `pre_start: ["echo", "bye"]`<br/>Result: `"echo && bye"` |
| **Stricter wins** | `security_profile` | The stricter profile applies, so a project cannot relax a global `strict` | Global: `strict`<br/>Project: `default`<br/>Result: `strict` |
| **Custom** | `detection` | Custom merge (see [detection](#detection)) | Enabled: project wins<br/>Patterns: map merge<br/>Allowlist: concatenate |
| **Global-only** | `pi_packages`, `update_check` | Not merged (global config only) | Project values ignored with warning |

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
#       - Maps (env, resources): shallow merge (project overrides global)
#       - Scalars (pre_start): project replaces global
#       - security_profile: the stricter profile wins
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
# ${VAR} and ${VAR:-default} are read from the shell that runs aishell
ports:
  - "8080:8080"                       # Web server
  - "3000:3000"                       # Dev server
  - "127.0.0.1:5432:5432"             # Postgres (localhost only)
  - "9090:9090/udp"                   # UDP protocol

# =============================================================================
# DOCKER_ARGS - Additional docker run arguments
# =============================================================================
# Supports string (space-separated) or array format
# Flags that weaken isolation (--privileged, --network, --cap-add, ...) stop
# the launch; see docker_args below.

# For memory and CPU caps, use resources (below).

# String format:
# docker_args: "--hostname=aishell --add-host=myhost:127.0.0.1"

# Array format (recommended for complex args):
docker_args:
  - "--hostname=aishell"
  - "--add-host=myhost:127.0.0.1"

# =============================================================================
# RESOURCES - Memory and CPU caps for the Sandbox container
# =============================================================================
# Both keys are optional; an absent key sets no cap. aishell has no default.
resources:
  memory: "8g"                      # docker run --memory 8g
  cpus: 4                           # docker run --cpus 4

# =============================================================================
# SECURITY_PROFILE - Restrictions the Sandbox runs under
# =============================================================================
# default: caps the process count. strict: also drops capabilities and
# privilege escalation, so sudo stops working inside the Sandbox.
security_profile: default

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
  omp:
    - "--model"
    - "sonnet"
  vscode:
    - "--profile"
    - "Work"

# String shorthand (single arg):
# harness_args:
#   claude: "--verbose"               # Auto-converts to ["--verbose"]

# =============================================================================
# UPDATE_CHECK - Automatic update notification (global-only)
# =============================================================================
# Periodically checks GitHub for newer aishell versions and prints a
# one-line warning on stderr. Advisory only — does not auto-update.
# When a check is due, may add up to 5 seconds of startup latency.
# Skipped for --help, --version, and upgrade commands.

update_check:
  enabled: true        # Set to false to disable (default: true)
  interval_days: 1     # How often to check, in days (default: 1)

# To disable:
# update_check:
#   enabled: false

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

**Persisting the uv (Python) cache:** The Foundation image ships `uv`/`uvx`
but no interpreter — because containers run `--rm` with an ephemeral home, the
first `uv run`/`uv sync` in each session re-downloads the pinned interpreter.
To persist that download across sessions, mount uv's cache from the host (on
Unix the container home mirrors the host home, so the same path maps through):

```yaml
mounts:
  - ~/.cache/uv
```

**Git over ssh remotes:** The Foundation image ships `openssh-client`, but the
container has no keys of its own — cloning, fetching, or pushing an `ssh://` or
`git@host:` remote needs your host keys mounted in:

```yaml
mounts:
  - ~/.ssh
```

Mount read-only (`~/.ssh:/home/developer/.ssh:ro`) if you want the container to
use your keys but never modify `known_hosts`. Note that aishell warns about
mounting private keys — `~/.ssh/id_*` is a flagged sensitive path — so this is a
deliberate trade of key exposure for working ssh remotes. An https remote with a
token, or a mounted ssh-agent socket, avoids exposing the keys themselves.

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

**Security warning:** Mounting sensitive paths (e.g., `/`, `/etc/passwd`, `~/.aws`, `~/.ssh` private keys) triggers warnings. A mount whose host path names `docker.sock` is refused: it would let the Sandbox control the Docker daemon. Put `--unsafe-docker-args` before the command (`aishell --unsafe-docker-args claude`) to let it through; `--unsafe` does not.

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

**Precedence:** `env` < Harness update policy < `docker_args`. Each enabled
Harness contributes fixed variables aishell owns — `DISABLE_AUTOUPDATER=1`
(Claude Code), `COPILOT_AUTO_UPDATE=false` (GitHub Copilot CLI),
`PI_SKIP_VERSION_CHECK=true` (pi) — and those are set after `env`, so an `env`
entry of the same name does not take effect. `docker_args` comes last and wins
over both. See [ADR 0008](adr/0008-harness-owned-runtime-environment-precedence.md).

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

**Environment variables:** An entry can take parts of its value from environment variables, so a project can commit a mapping whose host port each developer can override:

```yaml
ports:
  - "127.0.0.1:${WEAVE_NREPL_PORT:-7888}:7888"
```

- `${VAR}` is replaced by the value of `VAR`.
- `${VAR:-default}` is replaced by the value of `VAR`, or by `default` when `VAR` is unset or empty.
- `$VAR` without braces is not expanded.

The value comes from the environment of the shell that runs `aishell`, so an exported variable or a mise `[env]` entry sets it. aishell exits with an error naming the variable and the entry when `${VAR}` has no default and `VAR` is unset or empty; it never substitutes an empty string. The result is then checked like any other entry, and an invalid one is reported along with the entry as written. Only `ports` entries are expanded this way.

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
  - "--hostname=aishell"              # Custom hostname
  - "--add-host=db:192.168.1.100"    # Custom /etc/hosts entry

# String format (alternative)
# docker_args: "--hostname=aishell --add-host=db:192.168.1.100"
```

**Notes:**
- Array format is clearer for multi-flag arguments
- aishell passes flags directly to `docker run` (after built-in args)
- Flags that weaken the Sandbox's isolation are refused (see below)

**Merge behavior:** Global and project docker_args concatenate (both apply).

**Precedence:** these flags are emitted last, so `-e` flags here override both
`env` and the fixed update policy aishell sets per Harness. That makes
`docker_args` the deliberate escape hatch — re-enabling a Harness self-updater
inside a Sandbox with a read-only, pinned Harness volume is on you. See [ADR 0008](adr/0008-harness-owned-runtime-environment-precedence.md).

**Refused flags:** a launch whose `docker_args` carry any of these, written as `--flag=value` or `--flag value`, stops with exit 1 and an error that names every refused entry. This holds under every [security_profile](#security_profile) and for harness launches, the shell, `exec` and the VSCode Sandbox.

| Flag | Why it is refused |
|------|------|
| `--privileged` | Full host access |
| `--cap-add`, `--cap-drop` | Changes the capabilities the Security profile sets |
| `--security-opt` | Changes privilege escalation, seccomp or AppArmor |
| `--network`, `--net` | Joins the host or another network |
| `--runtime` | Swaps the OCI runtime |
| `--pid`, `--userns`, `--ipc`, `--uts`, `--cgroupns` | Joins a host namespace |
| `--device`, `--device-cgroup-rule` | Exposes host devices |
| `--volumes-from` | Mounts another container's volumes |
| any entry naming `docker.sock` | Lets the Sandbox control the Docker daemon |

The only way past is the Global flag `--unsafe-docker-args`, put before the command (`aishell --unsafe-docker-args claude`). It prints one warning for each entry it lets through, on every start. `--unsafe` does not apply. The list is fixed in aishell; config cannot shorten it ([ADR 0006](adr/0006-harden-on-docker-with-an-egress-proxy.md)). `aishell check` reports refused entries as failures.

**Common use cases:**

| Use Case | Example |
|----------|---------|
| Resource limits beyond [resources](#resources) | `--memory-swap=4g --pids-limit=100` (overrides the profile's 4096) |
| Custom hostname | `--hostname=dev-box` |
| DNS config | `--dns=8.8.8.8` |
| /etc/hosts | `--add-host=api:192.168.1.50` |
| Tmpfs mount | `--tmpfs=/tmp:rw,noexec,nosuid,size=1g` |

---

### resources

**Purpose:** Cap the memory and CPUs the Sandbox container may use.

**Type:** Map with two optional keys, `memory` and `cpus`

**Example:**

```yaml
resources:
  memory: "8g"      # docker run --memory 8g
  cpus: 4           # docker run --cpus 4
```

**Values:**
- `memory`: a size in Docker's format: a number with an optional unit, either `b` or one of `k`, `m`, `g`, `t`, `p` alone or followed by `b` or `ib`, in either case, such as `512m`, `8g`, `1.5G`, `8GB` or `8GiB`. A bare number, or a YAML integer, is a byte count. Docker rounds the size down to whole bytes, so a size under one byte, such as `0.5`, is refused like zero.
- `cpus`: a number of CPUs above zero, such as `4` or `1.5`, with at most 9 decimal places. A YAML number or a string both work.

**Notes:**
- aishell sets no cap of its own: a key you leave out adds no flag. To remove a cap, remove the key; zero is refused.
- The caps apply to every Sandbox `docker run`: the interactive shell, harness launches, `exec` and the VSCode launch.
- A value Docker would refuse stops the launch with exit 1 and an error that names the key (`resources.memory` or `resources.cpus`). Limits that depend on the host, such as Docker's 6 MB memory floor or more CPUs than the host has, are left to Docker to report.
- aishell warns on any other key under `resources` and ignores it.
- `aishell info` shows the caps in effect for the current project.

**Merge behavior:** Shallow merge, like `env`: a project key overrides the same global key, and the other global key is kept. With `extends: none` the global map is dropped.

**Precedence:** the flags go just before `docker_args`, so a `--memory` or `--cpus` in `docker_args` wins over `resources`.

---

### security_profile

**Purpose:** Choose the [Security profile](../CONTEXT.md) the Sandbox runs under: the restrictions on capabilities, privilege escalation and process count.

**Type:** String, `default` or `strict`

**Example:**

```yaml
security_profile: strict
```

**Values:**
- `default` (also when the key is absent): adds `--pids-limit 4096`. Nothing else changes; a Sandbox that runs more than 4096 processes hits the cap.
- `strict`: everything in `default`, plus `--security-opt no-new-privileges`, `--cap-drop ALL` and `--cap-add` for `CHOWN`, `SETUID`, `SETGID`, `FOWNER` and `DAC_OVERRIDE`, the capabilities the entrypoint needs to create your user and switch to it.

**What strict breaks:**
- `sudo` and every other setuid binary inside the Sandbox stop escalating, including `sudo` in `pre_start`. Install packages in a [Project extension](ARCHITECTURE.md#extension-system) Dockerfile instead of with `sudo apt-get` at run time.
- Binaries that gain capabilities through file capabilities, rather than setuid, lose them too. Your own processes already run as a non-root user, so they hold no capabilities under either profile.

**Notes:**
- The profile applies to every Sandbox `docker run`: the interactive shell, harness launches, `exec` and the VSCode Sandbox when `aishell vscode` starts one.
- Any other value stops the launch with exit 1 and an error that names `security_profile` and the valid values.
- `aishell check` and `aishell info` show the profile in effect for the current project.
- A [refused](#docker_args) `docker_args` flag stops the launch under either profile.

**Merge behavior:** The stricter profile wins: a global `strict` cannot be relaxed by a project `default`. With `extends: none` the global config is dropped, the global profile with it.

**Precedence:** the profile's flags go before `resources` and `docker_args`, so a `docker_args` entry that `--unsafe-docker-args` lets through takes effect after them.

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

**Harness names:** `claude`, `opencode`, `codex`, `copilot`, `gemini`, `pi`, `omp`, `vscode`

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

  # GitHub Copilot CLI defaults
  copilot:
    - "--model"
    - "gpt-5"

  # Gemini defaults
  gemini:
    - "--model"
    - "gemini-2.0-flash-exp"

  # Pi defaults
  pi:
    - "--print"
    - "hello"

  # oh-my-pi (omp) defaults
  omp:
    - "--model"
    - "sonnet"

  # VSCode defaults (passed to 'code' CLI)
  vscode:
    - "--profile"
    - "Work"

# String shorthand (single arg)
# harness_args:
#   claude: "--verbose"  # Converts to ["--verbose"]
```

**Notes:**
- Defaults precede CLI args
- For positional flags, the last occurrence wins (CLI overrides defaults)
- String values auto-convert to single-element lists
- Copilot defaults apply identically to `aishell copilot` and the in-Sandbox
  `copilot` alias; aishell does not add `--allow-all` or `--yolo`
- Copilot-enabled Sandboxes mount host `~/.copilot`, pass through only
  `COPILOT_GITHUB_TOKEN` and `COPILOT_GH_HOST`, and force
  `COPILOT_AUTO_UPDATE=false`. Add `GH_TOKEN`, `GITHUB_TOKEN`, or `GH_HOST`
  explicitly under `env` if required.
- Claude- and pi-enabled Sandboxes force `DISABLE_AUTOUPDATER=1` and
  `PI_SKIP_VERSION_CHECK=true` the same way, and Codex CLI launches with
  `-c check_for_update_on_startup=false`
- omp gets no fixed variables or arguments: its update check can only be
  turned off in its own config (`startup.checkUpdate: false`). The `omp` alias
  inside an interactive Sandbox exists only when `harness_args.omp` has entries

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
| GitHub Copilot CLI | `--model gpt-5` | Default model |
| Gemini | `--model gemini-2.0-flash-exp` | Specific model |
| Pi | `--print hello` | Default prompt |
| omp | `--model sonnet` | Default model |

---

### pi_packages

**Purpose:** Automatically install Pi plugins before entering the container.

**Type:** List of strings

**Scope:** Global config only (`~/.aishell/config.yaml`). Ignored in project configs with a warning.

**Requires:** `--with-pi` (Pi must be installed via `aishell setup --with-pi`)

**Example:**

```yaml
pi_packages:
  - "npm:pi-mcp-adapter"
  - "npm:pi-web-search"
```

**Behavior:**

1. Before entering the main container, aishell runs `pi install <pkg>` for each package in a short-lived Docker container
2. Installed packages are stored in `~/.pi/` on the host (persistent across runs)
3. aishell computes a hash of the package list and stores it at `~/.aishell/.pi-packages-hash`
4. On subsequent runs, if the hash matches, installation is skipped entirely (fast path)
5. If installation fails, aishell warns but does not block container entry; the next run retries

**Notes:**
- Only runs for `aishell shell`, `aishell claude`, etc. — not for `aishell exec` (one-off commands)
- Silently skipped when Pi is not installed (`--with-pi` not used during setup)
- Changing the package list triggers re-installation on the next run

**Merge behavior:** Global-only setting. Project `pi_packages` entries are ignored (with warning).

---

### update_check

**Purpose:** Control automatic update notifications when a newer aishell version is available.

**Type:** Map with two keys: `enabled`, `interval_days`

**Scope:** Global config only (`~/.aishell/config.yaml`). Ignored in project configs with a warning.

**Default:** Enabled, checks once per day.

**Example:**

```yaml
# Check every 7 days
update_check:
  enabled: true
  interval_days: 7

# Disable entirely
update_check:
  enabled: false
```

**Keys:**

| Key | Type | Default | Description |
|-----|------|---------|-------------|
| `enabled` | Boolean | `true` | Enable or disable update checks |
| `interval_days` | Integer | `1` | Minimum days between checks |

**Behavior:**

- On each CLI invocation (except `--help`, `--version`, `upgrade`), aishell checks whether the configured interval has elapsed since the last check
- If due, it follows GitHub's `/releases/latest` redirect to discover the newest version
- If a newer version exists, prints a one-line warning to stderr
- The check timestamp is persisted in `~/.local/state/aishell/update-check.edn`, so failed checks (offline, DNS issues) still advance the timer and don't retry on every invocation
- Advisory only — never auto-updates. When a check is due, the network lookup is bounded by a 5-second timeout; all other invocations skip instantly

**Notes:**
- Uses the same GitHub mechanism as `aishell upgrade` (follows redirect, no API token required)
- The check result is cached: once checked, subsequent invocations within the interval return immediately with no network call
- Errors are silently ignored — a broken network connection never affects CLI behavior

**Merge behavior:** Global-only setting. Project `update_check` entries are ignored (with warning).

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

### Secret scanning

aishell's own secret check is [sensitive-file detection](#detection): at launch it looks at file names in the project and warns about files likely to hold secrets. aishell does not scan file contents or git history.

To catch secrets before they are committed, run [gitleaks](https://github.com/gitleaks/gitleaks) or a similar scanner in the project's pre-commit hook or CI. That check runs on every commit, whether or not aishell is involved.

To run gitleaks inside the Sandbox instead, install it in a Project extension. This `.aishell/Dockerfile` installs a pinned release binary for the image's architecture:

```dockerfile
FROM aishell:base

ARG GITLEAKS_VERSION=8.30.1
RUN set -eux; \
    case "$(dpkg --print-architecture)" in \
        amd64) glArch='x64' ;; \
        arm64) glArch='arm64' ;; \
        *) echo "unsupported architecture"; exit 1 ;; \
    esac; \
    curl -fsSL "https://github.com/gitleaks/gitleaks/releases/download/v${GITLEAKS_VERSION}/gitleaks_${GITLEAKS_VERSION}_linux_${glArch}.tar.gz" \
    | tar -xz -C /usr/local/bin gitleaks; \
    gitleaks version
```

Then scan the project:

```bash
aishell exec gitleaks dir .
```

Pick the version from the [gitleaks releases](https://github.com/gitleaks/gitleaks/releases) page.

aishell versions before 5.0.0 kept gitleaks scan timestamps in `~/.local/state/aishell/gitleaks-scans.edn` (under `$XDG_STATE_HOME/aishell` when that is set). Nothing reads that file any more, and you can delete it.

---

### claude_isolation

**Purpose:** Control whether Claude Code's `~/.claude` state is shared across all sandboxes or isolated per project.

**Type:** String

**Values:**
- `"shared"` (default) - Mount the host `~/.claude` directory wholesale into every sandbox. This is the historical behavior: all projects share one set of skills, agents, credentials, and Claude machine state (daemon registry, session locks, jobs).
- `"project"` - Give this project its own `~/.claude` under a per-project state dir (keyed by a hash of the project path), with a built-in allowlist of shared config (skills, agents, commands, hooks, plugins, credentials, `CLAUDE.md`, `settings.json`, transcripts) mounted on top. Isolates Claude machine state so concurrent sandboxes no longer corrupt each other's daemon/session runtime data.

**Example:**

```yaml
# Isolate this project's Claude machine state from other sandboxes
claude_isolation: project
```

**Notes:**
- In `project` mode the per-project state lives under `{state-dir}/claude/{project-hash}/dot-claude/` (`state-dir` is `$XDG_STATE_HOME/aishell` or `~/.local/state/aishell`). Run `aishell check` to see the resolved path and whether it exists.
- The container's `~/.claude` path is identical in both modes, so resume, history, and credentials work the same inside the sandbox.
- Credentials: on first `project`-mode start with no host login, Claude starts without credentials and you log in inside the sandbox; the login persists into the per-project state dir and is promoted up to the host `~/.claude/.credentials.json` on a later start.
- Extend the shared allowlist with [claude_shared_paths](#claude_shared_paths).

**Merge behavior:** Scalar — a project value replaces the global value.

---

### claude_shared_paths

**Purpose:** Extend the built-in Claude share allowlist with additional paths (relative to `~/.claude`) that should be shared across sandboxes when running in `project` isolation mode.

**Type:** List of strings

**Values:** Each entry is a path relative to `~/.claude` (e.g. `my-tool` for a directory, `config.toml` for a file). A missing host source is pre-created before mounting: an entry whose final segment contains a `.` is treated as a file, otherwise as a directory.

**Example:**

```yaml
claude_isolation: project
claude_shared_paths:
  - shared-scripts          # directory shared across all sandboxes
  - todos                   # another shared directory
  - my-global.md            # a single shared file
```

**Notes:**
- Only takes effect in `claude_isolation: project` mode; ignored in `shared` mode (where everything is already shared).
- Entries that duplicate a built-in allowlist path (skills, agents, commands, hooks, plugins, projects, `CLAUDE.md`, `settings.json`, `history.jsonl`, `.credentials.json`) are ignored with a warning.
- **Rejected (the run aborts):** absolute paths, `..` escapes above `~/.claude`, and any path that collides with Claude machine state (daemon registry, session locks, jobs, tasks, roster, shell snapshots, file history, and similar). Sharing machine-state paths across sandboxes is exactly what `project` mode exists to prevent.

**Merge behavior:** Global and project lists concatenate (both apply).

---

## Base Image Customization

Advanced users can customize the Base image used by all projects. This adds a layer between the Foundation image and each project's Project extension, forming a three-tier image chain:

```
aishell:foundation -> aishell:base -> aishell:ext-{hash}
```

See [ARCHITECTURE.md](ARCHITECTURE.md#three-tier-image-chain--harness-volume) for the full image chain diagram.

### Creating `~/.aishell/Dockerfile`

Create `~/.aishell/Dockerfile` to customize the Base image:

```dockerfile
FROM aishell:foundation

# Your machine-wide customizations here
```

The recommended `FROM` line is `FROM aishell:foundation`, but this is not enforced. Changes are detected automatically -- the Base image rebuilds on the next `aishell` run.

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

- **Build timing:** `aishell setup` and `aishell update` build the Base image from `~/.aishell/Dockerfile`, and every run checks it again. Without `--force`, each rebuilds it only when the image is missing, the Dockerfile has changed or the Foundation image has changed.
- **Auto-detection:** aishell hashes the Dockerfile content and compares it to the existing Base image label. Changes trigger a rebuild.
- **Cascade rebuilds:** When the Base image changes, all Extended images rebuild on their next run.
- **Default behavior:** When no `~/.aishell/Dockerfile` exists, `aishell:base` is a tag alias for `aishell:foundation` (zero overhead).

### Project Extension Compatibility

Per-project `.aishell/Dockerfile` files can use either base:

```dockerfile
# Recommended: inherits machine-wide customizations
FROM aishell:base

RUN apt-get update && apt-get install -y postgresql-client && rm -rf /var/lib/apt/lists/*
```

```dockerfile
# Also valid: skips machine-wide customizations
FROM aishell:foundation

RUN apt-get update && apt-get install -y postgresql-client && rm -rf /var/lib/apt/lists/*
```

Using `FROM aishell:base` is recommended because it inherits any packages, tools, or shell configuration from your `~/.aishell/Dockerfile`.

### Removing Customizations

To revert to the default Base image:

1. Delete `~/.aishell/Dockerfile`
2. On the next run, `aishell:base` reverts to an alias of the Foundation image
3. Run `aishell volumes prune` to clean up the Base image built from the removed Dockerfile

### Status Check

```bash
aishell check
# Shows: "Base image: built from ~/.aishell/Dockerfile" or "Base image: default (alias of the Foundation image)"
```

### Build Failures

If your `~/.aishell/Dockerfile` has errors, the build fails with full Docker output. Fix the Dockerfile or delete it to revert. See [TROUBLESHOOTING.md](TROUBLESHOOTING.md#base-image-build-failures) for details.

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

Cap memory and CPUs with [resources](#resources); pass other limits through `docker_args`:

```yaml
resources:
  cpus: 2                      # Max 2 CPU cores
  memory: "4g"                 # Max 4GB RAM

docker_args:
  - "--memory-swap=4g"         # No swap beyond memory limit
  - "--pids-limit=100"         # Lower the security profile's 4096-process cap
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

resources:
  cpus: 4
  memory: "8g"

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
aishell setup --with-claude --with-opencode --with-codex --with-copilot --with-pi --with-omp

# With version pinning
aishell setup --with-claude=2.0.22 --with-codex=0.1.2025062501 --with-copilot=1.2.3
```

**Available harnesses:**
- `--with-claude` - Anthropic Claude Code
- `--with-opencode` - Multi-provider OpenCode
- `--with-codex` - OpenAI Codex CLI
- `--with-copilot` - GitHub Copilot CLI
- `--with-gemini` - Google Gemini CLI
- `--with-pi` - Mario Zechner's Pi coding agent
- `--with-omp` - oh-my-pi (omp), Stencil Labs' fork of Pi

**Version pinning:**
Use `=VERSION` syntax to pin specific versions:
```bash
--with-claude=2.0.22
--with-codex=0.1.2025062501
--with-copilot=1.2.3
--with-omp=18.12.1     # GitHub release tag v18.12.1
```

Omit version for latest:
```bash
--with-claude  # Uses latest version
```

**State tracking:**
aishell saves harness selection and tool configuration in `~/.aishell/state.edn` and preserves it across updates.

### --reuse-config

**Purpose:** Start from the saved setup intent, then override only the flags you pass on the current command.

**Usage:**
```bash
# Reuse the saved setup config exactly
aishell setup --reuse-config

# Reuse saved config, but reset OpenCode to latest
aishell setup --reuse-config --with-opencode

# Reuse saved config, but pin OpenCode to a new version
aishell setup --reuse-config --with-opencode=1.2.3

# Rebuild the Foundation and Base images from the saved config
aishell setup --reuse-config --force
```

**Behavior:**
- Requires an existing saved setup in `~/.aishell/state.edn`
- Omitted setup flags inherit from the saved setup intent
- Explicit `--with-...=VERSION` flags override inherited version pins
- A bare `--with-...` flag resets that tool to `latest`
- Inherited settings include harness selection, version pins, `--unisoma` and `--with-vscode`
- Validation runs against the merged effective config before the build starts
- Setup still recomputes derived build metadata (hashes, build time, volume name) before persisting state

**When to use:**
- **Foundation rebuilds:** Rebuild setup layers from your saved config
- **Small overrides:** Keep most of the saved config, change one or two tools
- **Avoid update semantics:** Unlike `aishell update`, this does not unconditionally delete and repopulate the harness volume when the effective config is unchanged

**When not to use:**
- **Write a brand-new config:** Use plain `aishell setup ...`
- **Disable saved tools:** Use plain `aishell setup ...` with the exact desired selection

---

### --force (setup flag)

**Purpose:** Force full Foundation image rebuild, bypassing Docker cache.

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

**Purpose:** Refresh harness tools to latest versions. The Foundation image is rebuilt only when it is missing or stale.

**Usage:**
```bash
# Refresh harness tools
aishell update

# Also rebuild the Foundation image with --no-cache, even when current
aishell update --force
```

**Default behavior (no flags):**
1. Rebuilds the Foundation image only when it is missing or stale
2. Refreshes the Base image
3. With a harness enabled, deletes the harness volume, recreates it and populates it with harness tools (npm install, binary download)
4. Updates state.edn with the new build time, `dockerfile-hash` and `foundation-hash`

**With --force:**
1. Rebuilds the Foundation image with `--no-cache`
2. When `~/.aishell/Dockerfile` exists, rebuilds the Base image from it even when it is current
3. With a harness enabled, deletes, recreates and repopulates the harness volume
4. Updates state.edn with the new build time, `dockerfile-hash` and `foundation-hash`

**Preserved from the last build:**
- Enabled harnesses (`--with-claude`, etc.)
- Harness version pins

**Cannot change harness selection.**
To add or remove harnesses, use `aishell setup`:
```bash
# Add OpenCode to existing Claude installation
aishell setup --with-claude --with-opencode
```

**When to use `setup --reuse-config` instead:**
```bash
# Rebuild the Foundation and Base images from the saved config
# without update's unconditional volume repopulation
aishell setup --reuse-config --force
```

Use `aishell setup --reuse-config` when you want setup semantics:
- start from the saved setup intent
- override only selected flags
- avoid unconditional harness-volume repopulation when the effective config is unchanged

**When to use update:**
- **npm package updates:** Get the latest harness versions
- **Regular refresh:** Periodically update tools
- **After npm publish:** A new harness version was released

**When to use update --force:**
- **System package updates:** Debian/Node.js security patches. Starting a sandbox warns that the Foundation image is out of date when it is more than 30 days old, or was built before aishell recorded build dates. Only `--force` resets the date: a plain `update` may reuse the cached OS package layer, so the image keeps its old build date.
- **Foundation changes:** After an aishell version upgrade
- **Troubleshooting:** The Foundation image behaves unexpectedly
