# aishell

aishell runs AI coding agents in ephemeral Docker containers. It supports Claude Code, OpenCode, Codex CLI, GitHub Copilot CLI, Gemini CLI, and Pi.

## Why Docker?

AI coding agents install packages, write scripts, and delete files with minimal human review. On your host, one bad command can reach credentials and personal files.

Docker limits the agent to the files and directories that you mount. These usually include your project and the agent's configuration files. The agent cannot access `~/.ssh` or overwrite your shell configuration unless you mount those paths. Packages installed in the container disappear with the container.

The fixed base image gives each developer the same tools and versions. Each concurrent agent gets a separate container.

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

That command still leaves three details for you to manage:

- AI agents use absolute paths. File references break if `/home/you/project` on the host becomes `/app` in the container.
- Git needs your identity. Without it, commits use "root" or "unknown" as the author.
- You must reproduce the same Docker options and mounts on each machine.

aishell configures these details and starts the agent with one command.

### Why not devcontainers?

Devcontainers solve a different problem. They create persistent development environments tied to your IDE.

aishell runs short-lived AI agent sessions from a terminal:

- Containers disappear when the agent exits.
- aishell mounts your project at its host path. Devcontainers usually remap the project to `/workspaces/project`.
- You can run aishell from a terminal, SSH session, or script without an IDE.
- `aishell claude` runs without a `devcontainer.json` file, feature configuration, or IDE integration.

You can use both: devcontainers for your development environment, aishell for running AI agents.

## Quick start

### Unix, macOS, and Linux

```bash
# 1. Install
curl -fsSL https://raw.githubusercontent.com/UniSoma/aishell/main/install.sh | bash

# 2. Build foundation image and select harnesses (one-time)
aishell setup --with-opencode

# 3. Run
aishell opencode
```

### Windows

Use WSL2 and follow the Linux instructions when possible. You can also install aishell with PowerShell. CMD has limited error handling and no colored output.

#### PowerShell

```powershell
# 1. Install
irm https://raw.githubusercontent.com/UniSoma/aishell/main/install.ps1 | iex

# 2. Restart terminal, then build foundation image and select harnesses (one-time)
aishell setup --with-opencode

# 3. Run
aishell opencode
```

#### CMD

```batch
curl -fsSL https://raw.githubusercontent.com/UniSoma/aishell/main/install.bat -o install.bat && install.bat

aishell setup --with-opencode

aishell opencode
```

> Requires Windows 10 version 1803 or later.

<details>
<summary>Prerequisites and troubleshooting</summary>

Requirements:

- Linux and macOS require Docker Engine.
- Windows requires Docker Desktop with the WSL2 backend enabled.

aishell ships as a single executable carrying its own babashka. Docker is the only thing you install yourself.

Docker installation:

- On Linux or macOS, install [Docker Engine](https://docs.docker.com/engine/install/).
- On Windows, install [Docker Desktop](https://www.docker.com/products/docker-desktop/). Enable "Use the WSL 2 based engine" under Settings > General.

Browser downloads:

The install scripts download the binary without a quarantine flag. Browsers add this flag, and aishell binaries are not code-signed. Clear the flag before you run a browser download.

macOS:

```bash
xattr -d com.apple.quarantine ~/Downloads/aishell-macos-aarch64.tar.gz
```

On Windows, right-click the downloaded zip and open Properties. Select "Unblock" before you extract the archive. You can also use PowerShell:

```powershell
Unblock-File .\aishell-windows-amd64.zip
```

Use an existing babashka installation:

The release binaries carry their own babashka, so you do not need one. To run aishell on the babashka you already have, install it from the git repository with [bbin](https://github.com/babashka/bbin):

```bash
bbin install io.github.UniSoma/aishell
```

PATH configuration:

On Unix or macOS, add `~/.local/bin` to `PATH` if necessary:

```bash
export PATH="$HOME/.local/bin:$PATH"
```

On Windows, add the directory that contains `aishell.exe` to `PATH`:

```powershell
[Environment]::SetEnvironmentVariable("Path", $env:Path + ";$env:LOCALAPPDATA\Programs\aishell", [System.EnvironmentVariableTarget]::User)
```

Then restart your terminal.

Update harness tools:

```bash
# Unix/macOS/Linux
aishell update

# Windows (PowerShell or cmd.exe)
aishell update
```

</details>

## How it works

When you run `aishell claude`, aishell starts a Docker container and mounts your project at its host path. It also mounts your Git identity and harness configuration, such as `~/.claude`. aishell applies project configuration from `.aishell/`. The container disappears when the agent exits, but your project files and harness configuration persist.

## Features

### Core

- Runs AI agents in ephemeral Docker containers
- Mounts projects at their host paths
- Preserves your Git identity in commits
- Mounts Claude Code and OpenCode configuration

### Customization

- Adds system packages, shell configuration, and development tools through `~/.aishell/Dockerfile`
- Extends the image for one project through `.aishell/Dockerfile`
- Sets mounts, environment variables, and ports in `.aishell/config.yaml`
- Pins harness versions
- Runs sidecar services before the shell or harness starts

See [Global base image customization](docs/CONFIGURATION.md#global-base-image-customization) for details.

### Power user

- Warns before an agent can access detected secrets, keys, or credentials
- Runs optional content-based secret scans with `aishell gitleaks` when setup includes `--with-gitleaks`
- Runs a single container command with `aishell exec`
- Assigns deterministic container names with `--name`
- Opens VSCode in a container as `developer` and persists the server state across restarts
- Opens a shell in a running container with `aishell attach`; the name is optional when one container is running
- Lists project containers with `aishell ps`
- Lists and prunes orphaned harness volumes with `aishell volumes`
- Shows the image stack and installed tools with `aishell info`
- Checks for new versions at a configurable interval; set `update_check` to disable the check
- Updates aishell and verifies the downloaded checksum with `aishell upgrade`

## Usage

### Set up harnesses

```bash
# Set up with a single harness
aishell setup --with-claude
aishell setup --with-opencode
aishell setup --with-codex
aishell setup --with-copilot
aishell setup --with-gemini
aishell setup --with-pi

# Set up with multiple harnesses
aishell setup --with-claude --with-opencode --with-codex --with-copilot --with-gemini --with-pi

# Set up with specific versions
aishell setup --with-claude=2.0.22
aishell setup --with-codex=0.1.2025062501
aishell setup --with-copilot=0.0.339

# Reuse the last saved setup config, overriding only what you specify
aishell setup --reuse-config
aishell setup --reuse-config --with-opencode       # reset OpenCode to latest
aishell setup --reuse-config --with-opencode=1.2.3 # override inherited version
```

### Run harnesses

```bash
# Enter interactive shell
aishell

# Run a harness
aishell claude
aishell opencode
aishell codex
aishell copilot
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

`aishell exec` uses the mounts and environment from `config.yaml`. It skips pre-start hooks and sensitive file detection.

### Multi-container workflow

Run multiple containers and reconnect later:

```bash
# Start Claude in a second terminal
aishell claude --name reviewer

# List running containers for this project
aishell ps

# Open a shell in a running container
aishell attach reviewer

# With only one container running, the name is optional
aishell attach

# Stop a container (use `aishell ps` to find the container name)
docker stop <container-name>
```

aishell names containers `aishell-{project-hash}-{name}`. Use `aishell ps` to discover container names for your project.

aishell reports an error if a running container already uses the requested name. It removes a stopped container with the same name.

### VSCode (experimental)

Open VSCode in the container as the `developer` user:

```bash
# Open VSCode attached to the container (blocks until window closes, then stops container)
aishell vscode

# Detached mode: container keeps running in the background
aishell vscode --detach

# Stop a detached container when done
aishell vscode --stop

# Pass extra arguments through to the 'code' CLI
aishell vscode --profile Work
aishell vscode --detach --disable-gpu
```

By default, aishell waits for the VSCode window to close and then stops the container. Use `--detach` to leave the container running. Each concurrent `aishell vscode` instance opens a separate window.

aishell passes unrecognized arguments to the `code` CLI. Its own arguments are `--detach`, `--stop`, and `--help`. You can set persistent defaults through `harness_args.vscode` in your [configuration](docs/CONFIGURATION.md#harness_args).

aishell mounts `~/.vscode-server` into the container. VSCode server extensions and cached data persist across container restarts.

This command requires VSCode, the `code` CLI on `PATH`, and the [Dev Containers](https://marketplace.visualstudio.com/items?itemName=ms-vscode-remote.remote-containers) extension. If WSL2 runs Docker inside the distribution, see [Troubleshooting](docs/TROUBLESHOOTING.md#symptom-vscode-dev-container-cant-find-docker-on-wsl2-no-docker-desktop).

### Upgrade aishell

```bash
# Upgrade to latest version
aishell upgrade

# Upgrade to a specific version
aishell upgrade 4.1.0
```

`aishell upgrade` downloads the archive for your platform and verifies it against the release's `SHA256SUMS`. It unpacks the binary before replacing the installed version. Linux and macOS use the system `tar`. Windows reads the zip in the aishell process. A terminal shows a progress bar, while a script receives the download size. Versions before 4.1.0 contain a babashka script instead of platform binaries and cannot use this command.

An upgrade from a 4.0.0 script installation replaces the script in place, so its `PATH` entry remains valid. On Windows, aishell writes `aishell.exe` and reports that it deleted the old `aishell` and `aishell.bat` files.

Windows cannot overwrite a running executable. The upgrade renames the current `aishell.exe` to `aishell.exe.old` and installs the new file. aishell deletes the old file the next time it runs.

### Update harness tools

```bash
# Refresh harness tools (volume refresh, fast)
aishell update

# Refresh harness tools AND rebuild foundation image
# (also recreates the harness volume)
aishell update --force

# Rebuild setup layers from the saved setup config,
# without update's unconditional volume repopulation
aishell setup --reuse-config --force
```

### Check the setup

Check the setup without starting a container:

```bash
aishell check
```

The command checks Docker, the build state, images, configuration, mounts, sensitive files, gitleaks scan age, Git, and the VSCode `code` CLI.

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

> You can use `.sandbox/` as an alias for `.aishell/`. Put the project `Dockerfile` and `config.yaml` in one directory only. Create `.sandbox/` with `aishell setup --dir .sandbox`. Projects that use `.sandbox/` still inherit `~/.aishell/config.yaml`.

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

For CI or other automation, skip the confirmation prompt:

```bash
aishell claude --unsafe  # Skip confirmation prompts
```

aishell refuses to start from your home directory or any directory above it, and `--unsafe` does not change that: a sandbox rooted there would hand the harness your SSH keys, credentials, and every project at once.

### Gitleaks (optional)

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

aishell mounts harness configuration directories from your host (`~/.claude`, `~/.codex`, `~/.copilot`, `~/.gemini`, `~/.pi`, `~/.config/opencode`, `~/.local/share/opencode`), so authentication persists between container sessions.

### Claude Code

#### Interactive OAuth

Run `aishell claude` and follow the prompts. Claude Code displays a URL you can copy-paste into your browser, completing OAuth even from within the container.

#### API key

```bash
export ANTHROPIC_API_KEY="your-key-here"
aishell claude
```

### Codex CLI

#### Interactive OAuth

Run `aishell codex` and select "Sign in with ChatGPT". In headless environments, use:

```bash
codex login --device-auth
```

This displays a code to enter at a URL in your browser.

#### API key

```bash
export OPENAI_API_KEY="your-key-here"  # For login
# or
export CODEX_API_KEY="your-key-here"   # Only works with `codex exec`, not interactive
aishell codex
```

### GitHub Copilot CLI

Run `aishell copilot` and follow the device-code sign-in flow. Login, settings,
plugins, saved permissions, and sessions persist in host `~/.copilot`.

For token authentication, aishell forwards `COPILOT_GITHUB_TOKEN` and
`COPILOT_GH_HOST` when you enable Copilot. Add broader GitHub variables such as
`GH_TOKEN`, `GITHUB_TOKEN`, and `GH_HOST` to `env:`. The Sandbox disables
Copilot's self-updater. Use `aishell update` to retain the configured version.

Persistent defaults under `harness_args.copilot` apply to both
`aishell copilot` and the `copilot` alias inside an interactive Sandbox. aishell
does not add `--allow-all` or `--yolo`.

### Gemini CLI

#### Authenticate on the host

```bash
# On your host machine (not in container)
gemini  # Select "Login with Google"

# Then run in the container. aishell mounts the credentials.
aishell gemini
```

#### API key

```bash
export GEMINI_API_KEY="your-key-here"
# or
export GOOGLE_API_KEY="your-key-here"
aishell gemini
```

Gemini CLI does not support the device-code flow for container authentication. Authenticate on the host or use an API key.

### Pi

Pi uses its own configuration-based authentication. Set up authentication on your host:

```bash
# Authenticate on host first, then credentials persist in ~/.pi
pi auth
```

aishell mounts `~/.pi` in the container.

### OpenCode

aishell mounts the OpenCode configuration directories from your host. These directories are `~/.config/opencode` and `~/.local/share/opencode`. Refer to OpenCode's documentation for authentication methods.

## Environment variables

aishell passes each harness-specific API key when you enable its harness and set the variable on your host. Add other variables, such as AWS and GitHub credentials, to `env:` in `config.yaml`.

### Passed automatically

| Variable | Harness | Notes |
|----------|---------|-------|
| `ANTHROPIC_API_KEY` | Claude, OpenCode | Required for API key auth |
| `OPENAI_API_KEY` | Codex, OpenCode | Used by multiple harnesses |
| `CODEX_API_KEY` | Codex | Only works with `codex exec` mode |
| `COPILOT_GITHUB_TOKEN` | GitHub Copilot CLI | Token authentication |
| `COPILOT_GH_HOST` | GitHub Copilot CLI | Enterprise Cloud/data-residency host |
| `GEMINI_API_KEY` | Gemini | From Google AI Studio |
| `GOOGLE_API_KEY` | Gemini | Alternative to GEMINI_API_KEY |
| `GOOGLE_APPLICATION_CREDENTIALS` | Gemini | Path to JSON key file (Vertex AI) |
| `GOOGLE_CLOUD_PROJECT` | Gemini | Required for Vertex AI |
| `GOOGLE_CLOUD_LOCATION` | Gemini | Required for Vertex AI |
| `GROQ_API_KEY` | OpenCode | For Groq-hosted models |
| `OPENCODE_API_KEY` | OpenCode | OpenCode Zen model router |
| `AZURE_OPENAI_API_KEY` | OpenCode | For Azure-hosted models |
| `AZURE_OPENAI_ENDPOINT` | OpenCode | For Azure-hosted models |
| `PI_CODING_AGENT_DIR` | Pi | Override pi's working directory |

### Set by aishell

aishell sets these variables in every sandbox that enables the harness. It does not read their values from the host. This rule keeps `aishell update` and version pins in control of the installed version. A `config.yaml` entry with the same name has lower precedence. Use `docker_args` to override the value. See [ADR 0008](docs/adr/0008-harness-owned-runtime-environment-precedence.md).

| Variable | Harness | Notes |
|----------|---------|-------|
| `DISABLE_AUTOUPDATER=1` | Claude | Autoupdater off |
| `COPILOT_AUTO_UPDATE=false` | GitHub Copilot CLI | Self-update off |
| `PI_SKIP_VERSION_CHECK=true` | Pi | Startup version check off |

aishell passes `-c check_for_update_on_startup=false` to Codex CLI. Gemini CLI and OpenCode store this setting in configuration files that aishell mounts but does not write.

### Variables that require an `env` entry

Add these variables to `config.yaml` to pass them into containers:

```yaml
env:
  GH_TOKEN: passthrough
  GH_HOST: passthrough
  GITHUB_TOKEN: passthrough
  AWS_ACCESS_KEY_ID: passthrough
  AWS_SECRET_ACCESS_KEY: passthrough
  AWS_REGION: passthrough
  AWS_PROFILE: passthrough
```

| Variable | Purpose | Notes |
|----------|---------|-------|
| `GITHUB_TOKEN` | GitHub API access | For GitHub operations |
| `AWS_ACCESS_KEY_ID` | AWS access | Not auto-passed |
| `AWS_SECRET_ACCESS_KEY` | AWS secret | Not auto-passed |
| `AWS_REGION` | AWS region | Not auto-passed |
| `AWS_PROFILE` | AWS profile | Named profile support |

### Host-side variables

| Variable | Purpose | Notes |
|----------|---------|-------|
| `AISHELL_SKIP_PERMISSIONS` | Claude permissions | Set to `false` to enable prompts (read on host, not passed to container) |
| `AISHELL_RELEASE_URL` | Alternate release tree | Base URL for release assets, default `https://github.com/UniSoma/aishell/releases`. The install scripts, `aishell upgrade` and the update check all read it |

## Reference

### Foundation image contents

The foundation image uses `debian:trixie-slim` and glibc 2.41. The build requires glibc 2.39 or later. See [ADR 0005](docs/adr/0005-glibc-floor-on-the-distro-image.md).

Runtimes:

- Node.js 24 (with npm, npx)
- Babashka
- bbin (shared install dir at `/usr/local/share/bbin`, writable by both root at build and the developer user at runtime)
- OpenJDK 21 JRE (headless), which bbin requires to resolve `tools.deps` dependencies
- CUE v0.17.1 for data validation, configuration, and code generation
- uv v0.11.29 for Python packages and toolchains (`uv`, `uvx`)
- SQLite 3.53.4, compiled from upstream source instead of Debian's 3.46.1 package. It includes
  `sqlite3`, `sqldiff` and `sqlite3_rsync`, plus the shared library, header and
  pkg-config file under `/usr/local`.

uv downloads the Python version that a project pins. The image does not include a Python interpreter.

`ldconfig` places the upstream SQLite library before Debian's library. Programs that link `libsqlite3.so.0` therefore use version 3.53.4. The image excludes `sqlite3_analyzer` because it requires TCL.

Security tools:

- Gitleaks v8.30.0 (optional, via `--with-gitleaks`)

CLI tools:

- git, ssh, patch, curl, jq, ripgrep, fd, vim
- tree, less, file, watch
- htop, sudo, rlwrap
- zip, unzip, zstd, xz

Mount your host keys to use `ssh`. See [Mounts](docs/CONFIGURATION.md#mounts).

Document and data tools:

- `pdftotext`, `pdftoppm`, `pdfinfo`, and `pdfimages` from poppler extract text, select page ranges, and rasterize pages. The `-layout` option preserves tables. CJK CMap tables support PDFs without embedded fonts.
- `xmllint` validates XML and HTML and queries them with XPath.
- `sponge`, `ts`, `chronic`, and `ifne` come from moreutils. `sponge` reads all input before writing, so `cmd file | ... | sponge file` does not truncate the input file.

aishell mounts harness tools from volumes at `/tools`. You can update these npm packages and binaries without rebuilding the foundation image.

### Git safe.directory

When a container starts, aishell adds the mounted project to `safe.directory` in the container's Git configuration.

The entrypoint performs these steps:

1. The entrypoint runs `git config --global --add safe.directory /your/project/path`
2. This writes to `~/.gitconfig` inside the container

If you mount `~/.gitconfig` or `~/.config/git/config`, the command writes the `safe.directory` entry to your host's Git configuration.

Git requires this entry because the project can appear to have a different owner inside the container. This check protects against CVE-2022-24765.

To keep the host configuration unchanged, do not mount it. The container creates a Git configuration file and discards it when the container exits.

## Documentation

The `docs` directory contains the following guides:

- [Architecture](docs/ARCHITECTURE.md): system design, data flow, and codebase structure
- [Configuration](docs/CONFIGURATION.md): `config.yaml` reference and examples
- [Harnesses](docs/HARNESSES.md): setup and usage for each AI harness
- [Troubleshooting](docs/TROUBLESHOOTING.md): common problems and remedies
- [Development](docs/DEVELOPMENT.md): instructions for adding a harness

## License

MIT
