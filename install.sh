#!/bin/bash
# install.sh - Installer for aishell
# Downloads aishell to ~/.local/bin and makes it executable
#
# Usage: curl -fsSL https://raw.githubusercontent.com/UniSoma/aishell/main/install.sh | bash

set -euo pipefail

VERSION="1.1.0"
INSTALL_DIR="${HOME}/.local/bin"
REPO_URL="https://raw.githubusercontent.com/UniSoma/aishell/main/aishell"

# --- Color Support ---
supports_color() {
    [[ -t 1 ]] && [[ -z "${NO_COLOR:-}" ]] && {
        local colors
        colors=$(tput colors 2>/dev/null) || return 1
        [[ "$colors" -ge 8 ]]
    }
}

setup_colors() {
    if supports_color; then
        BLUE='\033[0;34m'
        GREEN='\033[0;32m'
        RED='\033[0;31m'
        YELLOW='\033[0;33m'
        BOLD='\033[1m'
        NC='\033[0m'
    else
        BLUE=''
        GREEN=''
        RED=''
        YELLOW=''
        BOLD=''
        NC=''
    fi
}

# --- Output Functions ---
info() {
    printf "${BLUE}==>${NC} ${BOLD}%s${NC}\n" "$1"
}

success() {
    printf "${GREEN}==>${NC} %s\n" "$1"
}

error() {
    printf "${RED}Error:${NC} %s\n" "$1" >&2
    exit 1
}

warn() {
    printf "${YELLOW}Warning:${NC} %s\n" "$1"
}

# --- Core Functions ---
check_docker() {
    if ! command -v docker &>/dev/null; then
        echo ""
        warn "Docker is not installed."
        echo "aishell requires Docker to run. Install Docker from: https://docs.docker.com/get-docker/"
        echo ""
        return 0  # Continue installation anyway
    fi

    if ! docker info &>/dev/null 2>&1; then
        echo ""
        warn "Docker is installed but not running."
        echo "Start Docker before using aishell."
        echo ""
        return 0  # Continue installation anyway
    fi
}

ensure_install_dir() {
    if [[ ! -d "$INSTALL_DIR" ]]; then
        info "Creating $INSTALL_DIR"
        mkdir -p "$INSTALL_DIR"
    fi
}

download_script() {
    info "Downloading aishell..."
    if ! curl -fsSL "$REPO_URL" -o "${INSTALL_DIR}/aishell"; then
        error "Failed to download aishell from $REPO_URL"
    fi
    chmod +x "${INSTALL_DIR}/aishell"
}

check_path() {
    if [[ ":$PATH:" != *":$INSTALL_DIR:"* ]]; then
        echo ""
        echo "Note: $INSTALL_DIR is not in your PATH."
        echo "Add the following to your shell profile (~/.bashrc, ~/.zshrc, or ~/.profile):"
        echo ""
        echo "  export PATH=\"\$HOME/.local/bin:\$PATH\""
        echo ""
        echo "Then restart your shell or run: source ~/.bashrc"
    fi
}

# --- Main ---
main() {
    setup_colors

    info "Installing aishell v${VERSION}..."

    check_docker
    ensure_install_dir
    download_script

    success "Installed aishell v${VERSION} to ${INSTALL_DIR}/aishell"

    check_path
}

# Call main at the end (protects against partial download)
main "$@"
