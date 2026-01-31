(ns aishell.docker.templates
  "Embedded build files for Docker image creation.

   Contains the Dockerfile, entrypoint.sh, and bashrc.aishell content
   as multiline strings. These match the bash version heredocs exactly.")

(def base-dockerfile
  "# Aishell Foundation Image
# Debian-based container with dynamic user creation, gosu for user switching,
# and stable foundation layer with system dependencies.

# Stage 1: Node.js source (for multi-stage copy)
FROM node:24-bookworm-slim AS node-source

# Stage 2: Main image
FROM debian:bookworm-slim

# Build arguments for developer tools
ARG BABASHKA_VERSION=1.12.214

# Avoid prompts during package installation
ENV DEBIAN_FRONTEND=noninteractive

# Install required packages in single layer
RUN apt-get update && apt-get install -y --no-install-recommends \\
    bash \\
    ca-certificates \\
    curl \\
    file \\
    git \\
    htop \\
    jq \\
    less \\
    ripgrep \\
    sqlite3 \\
    sudo \\
    tmux \\
    tree \\
    unzip \\
    vim \\
    watch \\
    && rm -rf /var/lib/apt/lists/*

# Install Node.js via multi-stage copy from official image
COPY --from=node-source /usr/local/bin/node /usr/local/bin/node
COPY --from=node-source /usr/local/lib/node_modules /usr/local/lib/node_modules
RUN ln -s /usr/local/lib/node_modules/npm/bin/npm-cli.js /usr/local/bin/npm \\
    && ln -s /usr/local/lib/node_modules/npm/bin/npx-cli.js /usr/local/bin/npx \\
    && node --version \\
    && npm --version

# Install Babashka (static binary for container compatibility)
RUN set -eux; \\
    curl -fsSL \"https://github.com/babashka/babashka/releases/download/v${BABASHKA_VERSION}/babashka-${BABASHKA_VERSION}-linux-amd64-static.tar.gz\" \\
    | tar -xz -C /usr/local/bin bb; \\
    chmod +x /usr/local/bin/bb; \\
    bb --version

# Install gosu 1.19 for proper user switching
# Source: https://github.com/tianon/gosu
RUN set -eux; \\
    dpkgArch=\"$(dpkg --print-architecture)\"; \\
    curl -fsSL \"https://github.com/tianon/gosu/releases/download/1.19/gosu-${dpkgArch}\" -o /usr/local/bin/gosu; \\
    chmod +x /usr/local/bin/gosu; \\
    gosu --version; \\
    gosu nobody true

# Install Gitleaks for secret scanning (conditional)
ARG WITH_GITLEAKS=true
ARG GITLEAKS_VERSION=8.30.0
RUN if [ \"$WITH_GITLEAKS\" = \"true\" ]; then \\
        set -eux; \\
        dpkgArch=\"$(dpkg --print-architecture)\"; \\
        case \"${dpkgArch##*-}\" in \\
            amd64) glArch='x64' ;; \\
            arm64) glArch='arm64' ;; \\
            armhf) glArch='armv7' ;; \\
            *) echo \"unsupported architecture: $dpkgArch\"; exit 1 ;; \\
        esac; \\
        curl -fsSL \"https://github.com/gitleaks/gitleaks/releases/download/v${GITLEAKS_VERSION}/gitleaks_${GITLEAKS_VERSION}_linux_${glArch}.tar.gz\" \\
        | tar -xz -C /usr/local/bin gitleaks; \\
        chmod +x /usr/local/bin/gitleaks; \\
        gitleaks version; \\
    fi

# Enable tmux mouse mode for scroll support inside containers
# Without this, scroll events are interpreted as arrow keys by the inner application
RUN echo 'set -g mouse on' > /etc/tmux.conf

# Copy entrypoint script
COPY entrypoint.sh /usr/local/bin/entrypoint.sh
RUN chmod +x /usr/local/bin/entrypoint.sh

# Copy custom bashrc
COPY bashrc.aishell /etc/bash.aishell

ENTRYPOINT [\"/usr/local/bin/entrypoint.sh\"]
CMD [\"/bin/bash\"]
")

(def entrypoint-script
  "#!/bin/bash
# entrypoint.sh - Dynamic user creation with gosu
# Creates a user at container startup that matches the host user's UID/GID

set -e

# Read UID/GID/HOME from environment variables
USER_ID=${LOCAL_UID:-1000}
GROUP_ID=${LOCAL_GID:-1000}
USER_HOME=${LOCAL_HOME:-/home/developer}
USERNAME=developer

# Create group if GID doesn't exist
if ! getent group \"$GROUP_ID\" > /dev/null 2>&1; then
    groupadd -g \"$GROUP_ID\" \"$USERNAME\" 2>/dev/null || true
fi

# Create user if UID doesn't exist
# Use -d to set home directory to match host path (for config mounts)
if ! getent passwd \"$USER_ID\" > /dev/null 2>&1; then
    useradd --shell /bin/bash \\
        -u \"$USER_ID\" \\
        -g \"$GROUP_ID\" \\
        -d \"$USER_HOME\" \\
        -o \\
        -c \"Container User\" \\
        -m \"$USERNAME\" 2>/dev/null || true
fi

# Get the username for this UID (might be different if user already existed)
ACTUAL_USER=$(getent passwd \"$USER_ID\" | cut -d: -f1)
# Use LOCAL_HOME if provided, otherwise fall back to passwd entry
export HOME=${LOCAL_HOME:-$(getent passwd \"$USER_ID\" | cut -d: -f6)}

# Ensure home directory exists with correct ownership
mkdir -p \"$HOME\"
chown \"$USER_ID:$GROUP_ID\" \"$HOME\"

# Create XDG standard directories (apps expect these to be writable)
mkdir -p \"$HOME/.local/state\" \"$HOME/.local/share\" \"$HOME/.local/bin\"
chown -R \"$USER_ID:$GROUP_ID\" \"$HOME/.local\"

# Configure git safe.directory to trust the mounted project path (GIT-02)
# PWD is set by docker run -w flag
# Must run after home directory exists (git config --global needs $HOME/.gitconfig writable)
# Must run before exec gosu (runs as root, can write to the gitconfig that will be owned by user)
if [ -n \"$PWD\" ]; then
    git config --global --add safe.directory \"$PWD\"
fi

# Setup passwordless sudo for the user
echo \"$ACTUAL_USER ALL=(ALL) NOPASSWD:ALL\" > /etc/sudoers.d/developer
chmod 0440 /etc/sudoers.d/developer

# Source custom bashrc if starting bash
if [ \"$1\" = \"/bin/bash\" ] || [ \"$1\" = \"bash\" ]; then
    # Only add source line if not already present
    if ! grep -q \"source /etc/bash.aishell\" \"$HOME/.bashrc\" 2>/dev/null; then
        echo \"source /etc/bash.aishell\" >> \"$HOME/.bashrc\"
    fi
fi

# Add harness bin directories to PATH if they exist
export PATH=\"$HOME/.local/bin:/usr/local/bin:$PATH\"

# Volume-mounted harness tools PATH configuration
if [ -d \"/tools/npm/bin\" ]; then
  export PATH=\"/tools/npm/bin:$PATH\"
  export NODE_PATH=\"/tools/npm/lib/node_modules\"
fi

# Suppress Claude Code npm vs native installer warning (npm install still works)
export DISABLE_INSTALLATION_CHECKS=1

# Execute pre-start command if specified (PRE-01, PRE-02, PRE-03)
# Runs as developer user so caches (.m2, .npm, etc.) go to the right place
# Use sudo in pre_start if root is needed for specific commands
if [[ -n \"${PRE_START:-}\" ]]; then
    # Run in background as developer user, redirect all output to log file
    # Using sh -c ensures proper argument handling for complex commands
    gosu \"$USER_ID:$GROUP_ID\" sh -c \"$PRE_START\" > /tmp/pre-start.log 2>&1 &
fi

# Validate TERM has terminfo entry; fallback to xterm-256color if missing
# Prevents tmux failure with custom terminals (e.g., xterm-ghostty from Ghostty)
if command -v infocmp >/dev/null 2>&1 && ! infocmp \"$TERM\" >/dev/null 2>&1; then
    export TERM=xterm-256color
fi

# Set UTF-8 locale before tmux starts so tmux enables Unicode mode
# Without this, tmux defaults to ASCII and Unicode characters render as dashes
export LANG=C.UTF-8
export LC_ALL=C.UTF-8

# Execute command as the user via gosu, auto-start in tmux session
# -A: attach if session exists, create if not (idempotent)
# -s main: session named \\\"main\\\" (consistent naming for attach command)
# -c \\\"$PWD\\\": start in working directory (set by docker run -w)
# \\\"$@\\\": the actual command (bash, claude, opencode, codex, gemini)
exec gosu \"$USER_ID:$GROUP_ID\" tmux new-session -A -s main -c \"$PWD\" \"$@\"
")

(def bashrc-content
  "# /etc/bash.aishell - Custom shell configuration for aishell containers

# UTF-8 locale for Unicode character support (statusline, symbols)
# C.UTF-8 is available by default in Debian without installing locales package
export LANG=C.UTF-8
export LC_ALL=C.UTF-8

# Limit directory depth in prompt (shows .../parent/current instead of full path)
export PROMPT_DIRTRIM=2

# Custom prompt: [aishell] ~/project $
# Cyan [aishell] prefix, then working directory, then $
export PS1='\\[\\033[0;36m\\][aishell]\\[\\033[0m\\] \\w \\$ '

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
")
