# Phase 1: Core Container Foundation - Research

**Researched:** 2026-01-17
**Domain:** Docker containers, UID/GID mapping, CLI development, ephemeral development environments
**Confidence:** HIGH

## Summary

This research covers the technical foundations needed to build `aishell`, a CLI tool that creates ephemeral Docker containers with the user's project mounted at the exact host path, correct file ownership, and basic development tools.

The core challenge is UID/GID mapping: ensuring files created inside the container have the correct ownership when viewed on the host. The standard solution is dynamic user creation at container startup using tools like `gosu` or `fixuid`, combined with passing the host user's UID/GID at runtime via `docker run -u $(id -u):$(id -g)`.

For the CLI implementation, a Bash script is recommended for Phase 1 due to simplicity, zero dependencies, and the straightforward nature of wrapping Docker commands. The base image should be Debian bookworm-slim for the best balance of compatibility, size (~26MB), and package availability.

**Primary recommendation:** Use Debian bookworm-slim with a dynamic entrypoint that creates a user matching the host UID/GID, mounts the project at the same absolute path, and provides a customized bash prompt indicating the container environment.

## Standard Stack

The established tools and patterns for this domain:

### Core

| Component | Version/Choice | Purpose | Why Standard |
|-----------|---------------|---------|--------------|
| Base Image | debian:bookworm-slim | Container foundation | glibc compatibility, ~26MB, apt packages available |
| User mapping | gosu 1.19 | Step down from root to dynamic user | Industry standard, no PID 1 issues, actively maintained |
| CLI | Bash script | Invoke docker commands | Zero dependencies, Linux-native, sufficient for wrapper |
| Shell | Bash | In-container shell | Universal, familiar, supports PS1 customization |

### Supporting

| Tool | Version | Purpose | When to Use |
|------|---------|---------|-------------|
| fixuid v0.6.0 | Latest | Alternative to gosu for UID/GID | More complex scenarios with multiple path ownership changes |
| tput | System | Terminal color detection | Checking color support before ANSI output |
| getopts | Bash builtin | Argument parsing | Handling -v flag and future options |

### Alternatives Considered

| Instead of | Could Use | Tradeoff |
|------------|-----------|----------|
| debian:bookworm-slim | alpine:3.19 | Smaller (~5MB) but musl libc causes compatibility issues with some tools |
| debian:bookworm-slim | ubuntu:24.04 | Larger (~75MB), more packages by default, more familiar to some |
| gosu | su-exec | Smaller binary but has parser bugs in Alpine versions |
| Bash script | Go CLI | More robust but adds build complexity; overkill for Phase 1 |
| Bash script | Rust CLI | Best performance but steep learning curve; unnecessary for wrapper |

**Installation (in Dockerfile):**
```bash
# Install gosu (verified from official release)
RUN set -eux; \
    apt-get update; \
    apt-get install -y --no-install-recommends ca-certificates curl gnupg; \
    curl -fsSL "https://github.com/tianon/gosu/releases/download/1.19/gosu-$(dpkg --print-architecture)" -o /usr/local/bin/gosu; \
    curl -fsSL "https://github.com/tianon/gosu/releases/download/1.19/gosu-$(dpkg --print-architecture).asc" -o /usr/local/bin/gosu.asc; \
    chmod +x /usr/local/bin/gosu; \
    gosu --version; \
    gosu nobody true
```

## Architecture Patterns

### Recommended Project Structure
```
aishell/
├── aishell                    # Main CLI script (bash)
├── Dockerfile                 # Base image definition
├── entrypoint.sh             # Dynamic user creation and exec
├── bashrc.aishell            # Custom PS1 and shell config
└── README.md                 # Usage documentation
```

### Pattern 1: Dynamic User Creation at Runtime

**What:** Create a user inside the container at startup that matches the host user's UID/GID
**When to use:** Always - this is the core solution for the UID/GID mapping problem
**Example:**

```bash
#!/bin/bash
# entrypoint.sh - Source: gosu documentation and Docker best practices

set -e

# Get UID/GID from environment (passed via docker run -u)
USER_ID=${LOCAL_UID:-1000}
GROUP_ID=${LOCAL_GID:-1000}
USERNAME=developer

# Create group if it doesn't exist
if ! getent group "$GROUP_ID" > /dev/null 2>&1; then
    groupadd -g "$GROUP_ID" "$USERNAME"
fi

# Create user if it doesn't exist
if ! getent passwd "$USER_ID" > /dev/null 2>&1; then
    useradd --shell /bin/bash -u "$USER_ID" -g "$GROUP_ID" -o -c "" -m "$USERNAME"
fi

# Ensure home directory exists and has correct ownership
export HOME=/home/$USERNAME
mkdir -p "$HOME"
chown "$USER_ID:$GROUP_ID" "$HOME"

# Execute command as the created user
exec gosu "$USER_ID:$GROUP_ID" "$@"
```

### Pattern 2: Same-Path Volume Mount

**What:** Mount the host directory at the exact same path inside the container
**When to use:** Always - required by CORE-01
**Example:**

```bash
# Mount current directory at same path, set as working directory
docker run --rm -it \
    -v "$(pwd):$(pwd)" \
    -w "$(pwd)" \
    -e "LOCAL_UID=$(id -u)" \
    -e "LOCAL_GID=$(id -g)" \
    aishell:latest /bin/bash
```

### Pattern 3: Ephemeral Container with --rm

**What:** Container is automatically removed when it exits
**When to use:** Always - required by CORE-02
**Example:**

```bash
# The --rm flag removes container on exit
# Anonymous volumes are also cleaned up
# Named volumes persist (not used in this project)
docker run --rm -it myimage
```

### Pattern 4: Color and TTY Detection in Bash

**What:** Detect if terminal supports colors before using ANSI codes
**When to use:** For error messages and progress indicators
**Example:**

```bash
# Color support detection - Source: Baeldung, ArchWiki
supports_color() {
    # Not a TTY? No colors.
    [[ ! -t 1 ]] && return 1

    # NO_COLOR environment variable respected
    [[ -n "$NO_COLOR" ]] && return 1

    # FORCE_COLOR overrides detection
    [[ -n "$FORCE_COLOR" ]] && return 0

    # Check terminal capabilities
    local colors
    colors=$(tput colors 2>/dev/null) || return 1
    [[ "$colors" -ge 8 ]]
}

# Set colors based on support
if supports_color; then
    RED='\033[0;31m'
    YELLOW='\033[0;33m'
    NC='\033[0m'
else
    RED=''
    YELLOW=''
    NC=''
fi

error() {
    echo -e "${RED}Error:${NC} $1" >&2
    exit 1
}
```

### Pattern 5: Custom Shell Prompt for Container

**What:** Modify PS1 to indicate user is inside the container
**When to use:** For in-container experience (per CONTEXT.md decision)
**Example:**

```bash
# bashrc.aishell - sourced in container
# Format: [aishell] ~/project $
export PS1='\[\033[0;36m\][aishell]\[\033[0m\] \w \$ '
```

### Anti-Patterns to Avoid

- **Building UID/GID into the image:** Creates images that only work for one user. Always pass UID/GID at runtime.
- **Using sudo for user switching:** sudo doesn't properly handle PID 1, signal forwarding, or TTY. Use gosu instead.
- **Mounting to /workspace:** Breaks tools that rely on absolute paths. Mount to the same path as host.
- **Running as root inside container:** Violates security best practices and creates file permission issues. Always step down to non-root user.
- **Hardcoding ANSI colors:** Breaks piped output and non-TTY environments. Always detect TTY and color support.

## Don't Hand-Roll

Problems that look simple but have existing solutions:

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| User switching in containers | Custom scripts with su/sudo | gosu | Proper PID 1 handling, signal forwarding, no TTY issues |
| UID/GID file ownership | Recursive chown scripts | Dynamic user creation + gosu | Chown is slow, doesn't handle new files |
| Progress spinners | Character-by-character animation | Background process with spinner array | Race conditions, cursor position issues |
| Argument parsing | Manual $1, $2 handling | getopts builtin | Edge cases with optional args, combined flags |
| Color detection | Checking TERM variable | tput colors + TTY check | TERM alone doesn't guarantee color support |

**Key insight:** The UID/GID mapping problem has been solved many times. The entrypoint pattern with gosu is battle-tested and handles edge cases (supplementary groups, home directory, signal forwarding) that custom scripts miss.

## Common Pitfalls

### Pitfall 1: Files Created as Root
**What goes wrong:** User creates files inside container, they appear as root:root on host
**Why it happens:** Container ran as root, or user switching wasn't set up
**How to avoid:** Always pass `-u $(id -u):$(id -g)` and use gosu entrypoint
**Warning signs:** `ls -la` shows root ownership on newly created files

### Pitfall 2: No Home Directory for Dynamically Created User
**What goes wrong:** Tools fail because $HOME doesn't exist or isn't writable
**Why it happens:** useradd was called but home directory wasn't created
**How to avoid:** Use `useradd -m` or explicitly create and chown home directory
**Warning signs:** Errors about "permission denied" in ~/.something paths

### Pitfall 3: Spinner Breaks Pipe Output
**What goes wrong:** Spinner characters appear in output when piped to another command
**Why it happens:** Spinner writes to stdout instead of stderr, or doesn't detect non-TTY
**How to avoid:** Write progress to stderr, check `[[ -t 2 ]]` before animation
**Warning signs:** Garbled output when running `aishell command | grep something`

### Pitfall 4: Docker Not Running Detection Fails
**What goes wrong:** Cryptic error messages when Docker daemon isn't running
**Why it happens:** Script calls docker without checking daemon status first
**How to avoid:** Check `docker info >/dev/null 2>&1` before operations
**Warning signs:** "Cannot connect to Docker daemon" errors mid-operation

### Pitfall 5: Relative Paths in Volume Mounts
**What goes wrong:** Mount fails or mounts wrong directory
**Why it happens:** Relative path used instead of absolute path
**How to avoid:** Always use `$(pwd)` or `$PWD` for current directory, expand paths
**Warning signs:** "Bind mount source path does not exist" errors

### Pitfall 6: Missing setuid Bit on gosu
**What goes wrong:** gosu fails to switch users with permission errors
**Why it happens:** gosu wasn't installed with proper permissions
**How to avoid:** `chmod +s /usr/local/bin/gosu` or download from official release
**Warning signs:** "operation not permitted" when gosu tries to switch

## Code Examples

Verified patterns from official sources:

### Complete Dockerfile for Base Image

```dockerfile
# Source: Docker best practices, Debian official image
FROM debian:bookworm-slim

# Avoid prompts during package installation
ENV DEBIAN_FRONTEND=noninteractive

# Install required packages in single layer
RUN apt-get update && apt-get install -y --no-install-recommends \
    bash \
    ca-certificates \
    curl \
    git \
    jq \
    less \
    ripgrep \
    sudo \
    vim \
    && rm -rf /var/lib/apt/lists/*

# Install gosu for proper user switching
# Source: https://github.com/tianon/gosu
RUN set -eux; \
    dpkgArch="$(dpkg --print-architecture)"; \
    curl -fsSL "https://github.com/tianon/gosu/releases/download/1.19/gosu-${dpkgArch}" -o /usr/local/bin/gosu; \
    chmod +x /usr/local/bin/gosu; \
    gosu --version; \
    gosu nobody true

# Copy entrypoint script
COPY entrypoint.sh /usr/local/bin/entrypoint.sh
RUN chmod +x /usr/local/bin/entrypoint.sh

# Copy custom bashrc
COPY bashrc.aishell /etc/bash.aishell

ENTRYPOINT ["/usr/local/bin/entrypoint.sh"]
CMD ["/bin/bash"]
```

### Complete Entrypoint Script

```bash
#!/bin/bash
# entrypoint.sh - Dynamic user creation with gosu
# Source: gosu documentation, Docker volume permissions patterns

set -e

# Read UID/GID from environment variables
USER_ID=${LOCAL_UID:-1000}
GROUP_ID=${LOCAL_GID:-1000}
USERNAME=developer

# Create group if GID doesn't exist
if ! getent group "$GROUP_ID" > /dev/null 2>&1; then
    groupadd -g "$GROUP_ID" "$USERNAME" 2>/dev/null || true
fi

# Create user if UID doesn't exist
if ! getent passwd "$USER_ID" > /dev/null 2>&1; then
    useradd --shell /bin/bash \
        -u "$USER_ID" \
        -g "$GROUP_ID" \
        -o \
        -c "Container User" \
        -m "$USERNAME" 2>/dev/null || true
fi

# Get the username for this UID (might be different if user already existed)
ACTUAL_USER=$(getent passwd "$USER_ID" | cut -d: -f1)
export HOME=$(getent passwd "$USER_ID" | cut -d: -f6)

# Ensure home directory exists with correct ownership
mkdir -p "$HOME"
chown "$USER_ID:$GROUP_ID" "$HOME"

# Setup passwordless sudo for the user
echo "$ACTUAL_USER ALL=(ALL) NOPASSWD:ALL" > /etc/sudoers.d/developer
chmod 0440 /etc/sudoers.d/developer

# Source custom bashrc if starting bash
if [ "$1" = "/bin/bash" ] || [ "$1" = "bash" ]; then
    echo "source /etc/bash.aishell" >> "$HOME/.bashrc"
fi

# Execute command as the user via gosu
exec gosu "$USER_ID:$GROUP_ID" "$@"
```

### CLI Script Structure (aishell)

```bash
#!/bin/bash
# aishell - AI Shell Container Launcher
# Launches an ephemeral container with project mounted at host path

set -e

VERSION="0.1.0"
IMAGE_NAME="aishell"
VERBOSE=false

# --- Color Support ---
supports_color() {
    [[ ! -t 1 ]] && return 1
    [[ -n "$NO_COLOR" ]] && return 1
    [[ -n "$FORCE_COLOR" ]] && return 0
    local colors
    colors=$(tput colors 2>/dev/null) || return 1
    [[ "$colors" -ge 8 ]]
}

if supports_color; then
    RED='\033[0;31m'
    YELLOW='\033[0;33m'
    NC='\033[0m'
else
    RED='' YELLOW='' NC=''
fi

# --- Output Functions ---
error() {
    echo -e "${RED}Error:${NC} $1" >&2
    exit 1
}

warn() {
    echo -e "${YELLOW}Warning:${NC} $1" >&2
}

verbose() {
    [[ "$VERBOSE" == true ]] && echo "$1" >&2
}

# --- Spinner for Long Operations ---
spinner_pid=""

start_spinner() {
    local msg="$1"
    local spin='|/-\'
    local i=0

    # Only show spinner if stderr is a TTY
    [[ ! -t 2 ]] && return

    while true; do
        printf "\r%s %c " "$msg" "${spin:i++%${#spin}:1}" >&2
        sleep 0.1
    done &
    spinner_pid=$!
}

stop_spinner() {
    [[ -n "$spinner_pid" ]] && kill "$spinner_pid" 2>/dev/null
    spinner_pid=""
    printf "\r\033[K" >&2  # Clear line
}

trap 'stop_spinner' EXIT

# --- Docker Checks ---
check_docker() {
    if ! command -v docker &> /dev/null; then
        error "Docker is not installed. Please install Docker and try again."
    fi

    if ! docker info >/dev/null 2>&1; then
        error "Docker is not running. Please start Docker and try again."
    fi
}

# --- Argument Parsing ---
usage() {
    cat << EOF
Usage: aishell [OPTIONS]

Launch an ephemeral container with the current directory mounted.

Options:
    -v, --verbose    Show detailed output
    -h, --help       Show this help message
    --version        Show version

EOF
}

parse_args() {
    while [[ $# -gt 0 ]]; do
        case $1 in
            -v|--verbose)
                VERBOSE=true
                shift
                ;;
            -h|--help)
                usage
                exit 0
                ;;
            --version)
                echo "aishell $VERSION"
                exit 0
                ;;
            *)
                error "Unknown option: $1"
                ;;
        esac
    done
}

# --- Image Management ---
ensure_image() {
    if ! docker image inspect "$IMAGE_NAME" >/dev/null 2>&1; then
        verbose "Image not found, building..."
        start_spinner "Building image"

        # Build from Dockerfile in script directory
        local script_dir="$(dirname "$(readlink -f "$0")")"
        docker build -t "$IMAGE_NAME" "$script_dir" >/dev/null 2>&1 || {
            stop_spinner
            error "Failed to build image"
        }

        stop_spinner
        verbose "Image built successfully"
    fi
}

# --- Main ---
main() {
    parse_args "$@"
    check_docker
    ensure_image

    local project_dir="$(pwd)"

    verbose "Launching container..."
    verbose "  Project: $project_dir"
    verbose "  UID/GID: $(id -u):$(id -g)"

    exec docker run --rm -it \
        -v "$project_dir:$project_dir" \
        -w "$project_dir" \
        -e "LOCAL_UID=$(id -u)" \
        -e "LOCAL_GID=$(id -g)" \
        -e "TERM=${TERM:-xterm-256color}" \
        "$IMAGE_NAME"
}

main "$@"
```

### Custom Bashrc for Container

```bash
# /etc/bash.aishell - Custom shell configuration for aishell containers

# Custom prompt: [aishell] ~/project $
export PS1='\[\033[0;36m\][aishell]\[\033[0m\] \w \$ '

# Useful aliases
alias ll='ls -la'
alias la='ls -A'

# Ensure colors in common commands
alias ls='ls --color=auto'
alias grep='grep --color=auto'

# Set editor
export EDITOR=vim
export VISUAL=vim

# History settings
export HISTSIZE=10000
export HISTFILESIZE=20000
export HISTCONTROL=ignoreboth:erasedups
```

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| Running containers as root | Non-root with gosu/dynamic user | 2018-2020 | Security best practice, proper file permissions |
| Hardcoded UID 1000 in images | Runtime UID/GID passed via -u | 2019+ | Images work for all users, not just UID 1000 |
| sudo for user switching | gosu (no TTY issues, proper PID 1) | 2015+ | Cleaner signal handling, no password prompts |
| Alpine for all containers | Debian-slim for dev containers | 2022+ | Better glibc compatibility, fewer surprises |
| /workspace mount path | Same-as-host path mounting | Always preferred | Tools expecting absolute paths work correctly |

**Deprecated/outdated:**
- **su-exec in Alpine:** Has parser bugs since v0.2 (years without fix). Use gosu instead.
- **USER instruction alone:** Only sets default user, doesn't help with volume permissions. Need runtime UID matching.
- **fixuid for simple cases:** Overkill when gosu + dynamic user works. Reserve for complex multi-path scenarios.

## Open Questions

Things that couldn't be fully resolved:

1. **Docker group permissions vs sudo**
   - What we know: User may need to be in docker group to run docker without sudo
   - What's unclear: Should aishell check for this and provide guidance?
   - Recommendation: Detect permission denied errors and suggest adding user to docker group

2. **Image caching strategy**
   - What we know: First run needs to build image, subsequent runs use cache
   - What's unclear: When should image be rebuilt? Version mismatch detection?
   - Recommendation: For Phase 1, always use existing image; add --rebuild flag later

3. **Working directory for git-ignored paths**
   - What we know: pwd works, but user might want to run from subdirectory
   - What's unclear: Should aishell detect if pwd is inside a git repo?
   - Recommendation: Per CONTEXT.md decision, use pwd directly (not git root detection)

## Sources

### Primary (HIGH confidence)
- [Docker Bind Mounts Documentation](https://docs.docker.com/engine/storage/bind-mounts/) - Official mount syntax
- [gosu GitHub Repository](https://github.com/tianon/gosu) - Version 1.19, installation, usage
- [fixuid GitHub Repository](https://github.com/boxboat/fixuid) - Version 0.6.0, alternative approach
- [Debian Official Docker Image](https://hub.docker.com/_/debian) - bookworm-slim details
- [Docker USER Instruction](https://www.docker.com/blog/understanding-the-docker-user-instruction/) - Official best practices

### Secondary (MEDIUM confidence)
- [Nick Janetakis Blog](https://nickjanetakis.com/blog/running-docker-containers-as-a-non-root-user-with-a-custom-uid-and-gid) - UID/GID patterns verified with official docs
- [Evil Martians CLI UX](https://evilmartians.com/chronicles/cli-ux-best-practices-3-patterns-for-improving-progress-displays) - Progress indicator patterns
- [VS Code Add Non-Root User](https://code.visualstudio.com/remote/advancedcontainers/add-nonroot-user) - Microsoft's recommended pattern
- [Baeldung Spinner Guide](https://www.baeldung.com/linux/bash-show-spinner-long-tasks) - Bash spinner implementation

### Tertiary (LOW confidence)
- WebSearch results for CLI comparison (Go vs Rust vs Bash) - General consensus, no authoritative source
- Forum posts on Docker permission issues - Corroborated with official docs

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH - Based on official Docker documentation, gosu repository, Debian official images
- Architecture: HIGH - Patterns verified across multiple authoritative sources (Docker docs, VS Code docs, gosu docs)
- Pitfalls: MEDIUM - Based on community experience corroborated with official documentation
- CLI implementation: MEDIUM - Bash is pragmatic choice; Go would be more robust for future phases

**Research date:** 2026-01-17
**Valid until:** 2026-02-17 (30 days - stable domain, infrequent major changes)
