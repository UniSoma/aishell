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
ARG BABASHKA_VERSION=1.12.218
ARG BBIN_VERSION=0.2.5
ARG CUE_VERSION=0.16.1

# Avoid prompts during package installation
ENV DEBIAN_FRONTEND=noninteractive

# Install required packages in single layer
# openjdk-17-jre-headless: required by bbin for tools.deps dep resolution
RUN apt-get update && apt-get install -y --no-install-recommends \\
    bash \\
    bc \\
    ca-certificates \\
    curl \\
    fd-find \\
    file \\
    git \\
    htop \\
    jq \\
    less \\
    openjdk-17-jre-headless \\
    ripgrep \\
    rlwrap \\
    sqlite3 \\
    sudo \\
    tree \\
    unzip \\
    vim \\
    watch \\
    && rm -rf /var/lib/apt/lists/*

# Create fd symlink (Debian packages fd-find as fdfind, but tools expect fd)
RUN ln -s /usr/bin/fdfind /usr/bin/fd

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

# Install bbin (Babashka script installer)
RUN set -eux; \\
    curl -fsSL \"https://raw.githubusercontent.com/babashka/bbin/v${BBIN_VERSION}/bbin\" -o /usr/local/bin/bbin; \\
    chmod +x /usr/local/bin/bbin

# Shared bbin install dir (executables on PATH for everyone).
# We deliberately do NOT set BABASHKA_BBIN_DIR — that puts bbin in legacy
# single-dir mode and triggers a 'bbin migrate' warning on every invocation.
ENV BABASHKA_BBIN_BIN_DIR=/usr/local/share/bbin/bin

# Put the bbin install dir on PATH for build-time RUN layers in extending
# images. Runtime shells already get it via /etc/profile.d/aishell.sh and
# /etc/bash.aishell, but Dockerfile RUN spawns a non-interactive non-login
# bash that sources neither — so wrappers produced by `bbin install` would
# otherwise be unreachable from a downstream `RUN clj-mytool --help` step.
ENV PATH=${BABASHKA_BBIN_BIN_DIR}:${PATH}

# Shared Clojure tools jar location (honored by deps.clj — the babashka
# emulator of the clojure CLI used by babashka.deps/add-deps).
ENV DEPS_CLJ_TOOLS_DIR=/usr/local/share/deps.clj/ClojureTools

# Shared tools.deps git-libs cache. `bbin install <git-url> --git/sha …`
# from an extending Dockerfile runs as root and clones into ~/.gitlibs;
# bbin's wrapper then sets that path as the cwd when it shells out to
# java, so the developer user (UID 1000) needs to be able to traverse it.
# tools.deps honors GITLIBS directly (unlike :mvn/local-repo, which -Srepro
# strips), so a single env var redirects both build-time and runtime writes.
ENV GITLIBS=/usr/local/share/gitlibs

# Shared Clojure CLI config dir. `clojure -Ttools install :as <name>` writes
# the tool registry to $CLJ_CONFIG/tools/<name>.edn, defaulting to per-user
# $HOME/.clojure — so a build-time install as root would land in /root/.clojure
# and be invisible to the developer user at runtime (\"Unknown tool: <name>\").
# Setting CLJ_CONFIG to a shared dir makes both contexts read/write the same
# registry. Per-user customization is still possible by overriding CLJ_CONFIG
# in a developer's shell session.
ENV CLJ_CONFIG=/usr/local/share/clojure-config

# Shared Maven cache. babashka.deps/add-deps invokes deps.clj with -Srepro,
# which ignores user/project deps.edn — so :mvn/local-repo cannot be set via
# config and the only reliable way to redirect downloads is to symlink the
# default ~/.m2/repository path. Build-time pre-warming (root) and runtime
# installs (developer) both write to /usr/local/share/m2 via this symlink.
RUN mkdir -p /usr/local/share/bbin/bin \\
             /usr/local/share/m2 \\
             /usr/local/share/gitlibs \\
             /usr/local/share/clojure-config/tools \\
             /usr/local/share/deps.clj/ClojureTools \\
             /root/.m2 \\
    && ln -sfn /usr/local/share/m2 /root/.m2/repository \\
    && chown -R 1000:1000 /usr/local/share/bbin \\
                          /usr/local/share/m2 \\
                          /usr/local/share/gitlibs \\
                          /usr/local/share/clojure-config \\
                          /usr/local/share/deps.clj

# Pre-warm bbin's tools.deps cache: downloads clojure-tools jar to
# DEPS_CLJ_TOOLS_DIR and Maven deps to the shared /usr/local/share/m2
# (via the /root/.m2/repository symlink). The developer user picks up the
# warm cache through an equivalent symlink created in the entrypoint.
RUN bbin version \\
    && test -d /usr/local/share/m2/org/clojure

# Install gosu 1.19 for proper user switching
# Source: https://github.com/tianon/gosu
RUN set -eux; \\
    dpkgArch=\"$(dpkg --print-architecture)\"; \\
    curl -fsSL \"https://github.com/tianon/gosu/releases/download/1.19/gosu-${dpkgArch}\" -o /usr/local/bin/gosu; \\
    chmod +x /usr/local/bin/gosu; \\
    gosu --version; \\
    gosu nobody true

# Install CUE for data validation, configuration, and code generation
# Source: https://github.com/cue-lang/cue
RUN set -eux; \\
    dpkgArch=\"$(dpkg --print-architecture)\"; \\
    case \"${dpkgArch}\" in \\
        amd64) cueArch='amd64' ;; \\
        arm64) cueArch='arm64' ;; \\
        *) echo \"unsupported architecture: $dpkgArch\"; exit 1 ;; \\
    esac; \\
    curl -fsSL \"https://github.com/cue-lang/cue/releases/download/v${CUE_VERSION}/cue_v${CUE_VERSION}_linux_${cueArch}.tar.gz\" \\
    | tar -xz -C /usr/local/bin cue; \\
    chmod +x /usr/local/bin/cue; \\
    cue version

# Install Gitleaks for secret scanning (conditional, opt-in)
ARG WITH_GITLEAKS=false
ARG GITLEAKS_VERSION=8.30.1
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

# Create developer user at build time (visible in /etc/passwd for VSCode Dev Containers)
# UID/GID are adjusted at runtime by entrypoint to match host user
RUN groupadd -g 1000 developer \\
    && useradd -m -s /bin/bash -u 1000 -g 1000 developer

# Copy entrypoint script
COPY entrypoint.sh /usr/local/bin/entrypoint.sh
RUN chmod +x /usr/local/bin/entrypoint.sh

# Copy custom bashrc
COPY bashrc.aishell /etc/bash.aishell

# Copy profile.d script for login shell environment
COPY profile.d-aishell.sh /etc/profile.d/aishell.sh

# Replace Debian's /etc/profile with a version whose PATH default is gated on
# PATH being unset. The stock file unconditionally clobbers PATH for non-root
# login shells, which wipes ENV PATH overrides set by extending images
# (e.g. a downstream JDK install). In a container PATH is always provided by
# image metadata, so the fallback never triggers in practice.
COPY etc-profile /etc/profile

ENTRYPOINT [\"/usr/local/bin/entrypoint.sh\"]
CMD [\"/bin/bash\"]
")

(def entrypoint-script
  "#!/bin/bash
# entrypoint.sh - User setup with gosu
# Adjusts the pre-created developer user to match the host user's UID/GID

set -e

# Read UID/GID/HOME from environment variables
USER_ID=${LOCAL_UID:-1000}
GROUP_ID=${LOCAL_GID:-1000}
USER_HOME=${LOCAL_HOME:-/home/developer}
USERNAME=developer

# Adjust developer group GID to match host (skip if already correct)
CURRENT_GID=$(id -g $USERNAME)
if [ \"$CURRENT_GID\" != \"$GROUP_ID\" ]; then
    groupmod -g \"$GROUP_ID\" developer 2>/dev/null || true
fi

# Adjust developer user UID to match host (skip if already correct)
CURRENT_UID=$(id -u $USERNAME)
if [ \"$CURRENT_UID\" != \"$USER_ID\" ]; then
    usermod -u \"$USER_ID\" developer 2>/dev/null || true
fi

# Update home directory if different from default
usermod -d \"$USER_HOME\" developer 2>/dev/null || true

ACTUAL_USER=developer
# Use LOCAL_HOME if provided, otherwise fall back to passwd entry
export HOME=${LOCAL_HOME:-$(getent passwd \"$USER_ID\" | cut -d: -f6)}

# Ensure home directory exists with correct ownership
mkdir -p \"$HOME\"
chown \"$USER_ID:$GROUP_ID\" \"$HOME\"

# Create XDG standard directories (apps expect these to be writable)
mkdir -p \"$HOME/.local/state\" \"$HOME/.local/share\" \"$HOME/.local/bin\"
chown -R \"$USER_ID:$GROUP_ID\" \"$HOME/.local\"

# Sync shared bbin + Maven caches to the host UID so the developer user
# can install/remove packages at runtime (build-time runs as root).
for dir in /usr/local/share/bbin \\
           /usr/local/share/m2 \\
           /usr/local/share/gitlibs \\
           /usr/local/share/clojure-config \\
           /usr/local/share/deps.clj; do
    if [ -d \"$dir\" ]; then
        chown -R \"$USER_ID:$GROUP_ID\" \"$dir\"
    fi
done

# Symlink ~/.m2/repository to the shared Maven cache so bbin (which calls
# tools.deps with -Srepro and writes to the default ~/.m2/repository) finds
# the pre-warmed deps. Only do this when $HOME/.m2 does NOT already exist
# — this preserves any user-supplied bind mount (host ~/.m2 → container)
# and avoids leaking a container-only symlink onto the host filesystem.
if [ ! -e \"$HOME/.m2\" ]; then
    mkdir -p \"$HOME/.m2\"
    chown \"$USER_ID:$GROUP_ID\" \"$HOME/.m2\"
    ln -sfn /usr/local/share/m2 \"$HOME/.m2/repository\"
    chown -h \"$USER_ID:$GROUP_ID\" \"$HOME/.m2/repository\"
fi

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

# Generate harness aliases for interactive shell use
# Mirrors what 'aishell <harness>' does with config defaults
ALIAS_FILE=\"$HOME/.bash_aliases\"
: > \"$ALIAS_FILE\"
for var in HARNESS_ALIAS_CLAUDE HARNESS_ALIAS_OPENCODE HARNESS_ALIAS_CODEX HARNESS_ALIAS_GEMINI HARNESS_ALIAS_PI; do
    cmd=\"${!var}\"
    if [ -n \"$cmd\" ]; then
        name=\"${var##*_}\"
        name=\"$(echo \"$name\" | tr 'A-Z' 'a-z')\"
        echo \"alias $name='$cmd'\" >> \"$ALIAS_FILE\"
    fi
done
chown \"$USER_ID:$GROUP_ID\" \"$ALIAS_FILE\"

# Ensure aliases are sourced from bashrc
if ! grep -q \".bash_aliases\" \"$HOME/.bashrc\" 2>/dev/null; then
    echo '[ -f ~/.bash_aliases ] && . ~/.bash_aliases' >> \"$HOME/.bashrc\"
fi

# Add harness bin directories to PATH if they exist
export PATH=\"$HOME/.local/bin:${BABASHKA_BBIN_BIN_DIR:-/usr/local/share/bbin/bin}:/usr/local/bin:$PATH\"

# Volume-mounted harness tools PATH configuration
if [ -d \"/tools/npm/bin\" ]; then
  export PATH=\"/tools/npm/bin:$PATH\"
  export NODE_PATH=\"/tools/npm/lib/node_modules\"
fi

if [ -d \"/tools/bin\" ]; then
  export PATH=\"/tools/bin:$PATH\"
fi

# Suppress Claude Code npm vs native installer warning (npm install still works)
export DISABLE_INSTALLATION_CHECKS=1

# Bootstrap readiness sentinels for `aishell ps` (see docker/bootstrap.clj).
# /tmp lives on the writable layer (not tmpfs), so sentinels persist across
# docker stop/start. Removing them here ensures a config change from
# pre_start to no-pre_start surfaces as bootstrap=none on the next start
# instead of a stale done/failed.
rm -f /tmp/pre-start.done /tmp/pre-start.failed

# Execute pre-start command if specified (PRE-01, PRE-02, PRE-03)
# Runs as developer user so caches (.m2, .npm, etc.) go to the right place
# Use sudo in pre_start if root is needed for specific commands
if [[ -n \"${PRE_START:-}\" ]]; then
    # Subshell, backgrounded with &, captures pre_start exit code and writes
    # the corresponding sentinel. The subshell survives the entrypoint's
    # `exec` below: the launching shell is replaced by the foreground
    # command, the subshell is re-parented to PID 1, and continues running
    # until pre_start finishes.
    #
    # `set +e` is mandatory here. The entrypoint declares `set -e` at the
    # top of the script, which is inherited by the subshell — without
    # disabling it, a non-zero gosu exit would abort the subshell *before*
    # the if/else runs, and no failed sentinel would ever be written.
    (
      set +e
      gosu \"$USER_ID:$GROUP_ID\" sh -c \"$PRE_START\" > /tmp/pre-start.log 2>&1
      ec=$?
      if [ $ec -eq 0 ]; then
        touch /tmp/pre-start.done
      else
        echo \"$ec\" > /tmp/pre-start.failed
      fi
    ) &
fi

# Validate TERM has terminfo entry; fallback to xterm-256color if missing
if command -v infocmp >/dev/null 2>&1 && ! infocmp \"$TERM\" >/dev/null 2>&1; then
    export TERM=xterm-256color
fi

# Set UTF-8 locale for proper Unicode rendering
export LANG=C.UTF-8
export LC_ALL=C.UTF-8

# Execute command as developer user
exec gosu \"$USER_ID:$GROUP_ID\" \"$@\"
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

# Source harness aliases if available
if [ -f ~/.bash_aliases ]; then
    . ~/.bash_aliases
fi
")

(def profile-d-script
  "# /etc/profile.d/aishell.sh - Login shell environment for aishell containers
# Sourced by /etc/profile on login shell startup (new shell sessions, ssh, etc.)
# Ensures harness tools and shell customizations persist across shell sessions.
# Critical for 'aishell attach' which starts a login shell via docker exec.

# User local bin (native tool installations like Claude Code)
export PATH=\"$HOME/.local/bin:$PATH\"

# Shared bbin install dir (build-time and runtime installs land here)
export PATH=\"${BABASHKA_BBIN_BIN_DIR:-/usr/local/share/bbin/bin}:$PATH\"

# Volume-mounted harness tools PATH configuration
if [ -d \"/tools/npm/bin\" ]; then
  export PATH=\"/tools/npm/bin:$PATH\"
  export NODE_PATH=\"/tools/npm/lib/node_modules\"
fi

if [ -d \"/tools/bin\" ]; then
  export PATH=\"/tools/bin:$PATH\"
fi

# Suppress Claude Code npm vs native installer warning
export DISABLE_INSTALLATION_CHECKS=1

# Source shell customizations (prompt, aliases, locale)
if [ -f \"/etc/bash.aishell\" ]; then
  . /etc/bash.aishell
fi

# Source harness aliases for login shells
if [ -f \"$HOME/.bash_aliases\" ]; then
  . \"$HOME/.bash_aliases\"
fi
")

(def etc-profile-content
  "# /etc/profile: system-wide .profile file for the Bourne shell (sh(1))
# and Bourne compatible shells (bash(1), ksh(1), ash(1), ...).
#
# Customized for aishell. Upstream Debian unconditionally resets PATH for
# non-root login shells, which silently discards any ENV PATH overrides set
# by images extending aishell:foundation (e.g. a downstream JDK install).
# We only apply the default when PATH is empty, which in a container only
# happens if something explicitly unsets it.

if [ -z \"${PATH:-}\" ]; then
  if [ \"$(id -u)\" -eq 0 ]; then
    PATH=\"/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin\"
  else
    PATH=\"/usr/local/bin:/usr/bin:/bin:/usr/local/games:/usr/games\"
  fi
  export PATH
fi

if [ \"${PS1-}\" ]; then
  if [ \"${BASH-}\" ] && [ \"$BASH\" != \"/bin/sh\" ]; then
    # The file bash.bashrc already sets the default PS1.
    # PS1='\\h:\\w\\$ '
    if [ -f /etc/bash.bashrc ]; then
      . /etc/bash.bashrc
    fi
  else
    if [ \"$(id -u)\" -eq 0 ]; then
      PS1='# '
    else
      PS1='$ '
    fi
  fi
fi

if [ -d /etc/profile.d ]; then
  for i in /etc/profile.d/*.sh; do
    if [ -r $i ]; then
      . $i
    fi
  done
  unset i
fi
")

(def foundation-content
  "Concatenation of every file baked into the foundation image at build
   time — the Dockerfile plus all files COPY'd in by `write-build-files`.

   Hashing this (rather than just `base-dockerfile`) is what lets cache
   invalidation pick up an entrypoint or bashrc edit. If you add a new
   COPY in the Dockerfile, append the corresponding template here in the
   same order so the hash stays stable."
  (str base-dockerfile
       entrypoint-script
       bashrc-content
       profile-d-script
       etc-profile-content))
