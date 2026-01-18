#!/bin/bash
# entrypoint.sh - Dynamic user creation with gosu
# Creates a user at container startup that matches the host user's UID/GID

set -e

# Read UID/GID/HOME from environment variables
USER_ID=${LOCAL_UID:-1000}
GROUP_ID=${LOCAL_GID:-1000}
USER_HOME=${LOCAL_HOME:-/home/developer}
USERNAME=developer

# Create group if GID doesn't exist
if ! getent group "$GROUP_ID" > /dev/null 2>&1; then
    groupadd -g "$GROUP_ID" "$USERNAME" 2>/dev/null || true
fi

# Create user if UID doesn't exist
# Use -d to set home directory to match host path (for config mounts)
if ! getent passwd "$USER_ID" > /dev/null 2>&1; then
    useradd --shell /bin/bash \
        -u "$USER_ID" \
        -g "$GROUP_ID" \
        -d "$USER_HOME" \
        -o \
        -c "Container User" \
        -m "$USERNAME" 2>/dev/null || true
fi

# Get the username for this UID (might be different if user already existed)
ACTUAL_USER=$(getent passwd "$USER_ID" | cut -d: -f1)
# Use LOCAL_HOME if provided, otherwise fall back to passwd entry
export HOME=${LOCAL_HOME:-$(getent passwd "$USER_ID" | cut -d: -f6)}

# Ensure home directory exists with correct ownership
mkdir -p "$HOME"
chown "$USER_ID:$GROUP_ID" "$HOME"

# Configure git safe.directory to trust the mounted project path (GIT-02)
# PWD is set by docker run -w flag
# Must run after home directory exists (git config --global needs $HOME/.gitconfig writable)
# Must run before exec gosu (runs as root, can write to the gitconfig that will be owned by user)
if [ -n "$PWD" ]; then
    git config --global --add safe.directory "$PWD"
fi

# Setup passwordless sudo for the user
echo "$ACTUAL_USER ALL=(ALL) NOPASSWD:ALL" > /etc/sudoers.d/developer
chmod 0440 /etc/sudoers.d/developer

# Source custom bashrc if starting bash
if [ "$1" = "/bin/bash" ] || [ "$1" = "bash" ]; then
    # Only add source line if not already present
    if ! grep -q "source /etc/bash.aishell" "$HOME/.bashrc" 2>/dev/null; then
        echo "source /etc/bash.aishell" >> "$HOME/.bashrc"
    fi
fi

# Add harness bin directories to PATH if they exist
[[ -d /usr/local/bin ]] && export PATH="/usr/local/bin:$PATH"

# Execute command as the user via gosu
exec gosu "$USER_ID:$GROUP_ID" "$@"
