#!/bin/bash
# install.sh - Installer for aishell v2.0
# Downloads aishell from GitHub Releases with checksum verification
#
# Usage: curl -fsSL https://raw.githubusercontent.com/UniSoma/aishell/main/install.sh | bash
#
# Environment variables:
#   VERSION     - Version to install (default: latest)
#   INSTALL_DIR - Installation directory (default: ~/.local/bin)

set -euo pipefail

install_babashka() {
    local install_dir="$1"
    local downloader="$2"

    local bb_installer="/tmp/bb-install"
    local bb_install_url="https://raw.githubusercontent.com/babashka/babashka/master/install"

    info "Downloading Babashka installer..."
    if [[ "$downloader" == "curl" ]]; then
        if ! curl -fsSL --retry 3 "$bb_install_url" -o "$bb_installer"; then
            error "Failed to download Babashka installer"
            exit 1
        fi
    else
        if ! wget -q --tries=3 -O "$bb_installer" "$bb_install_url"; then
            error "Failed to download Babashka installer"
            exit 1
        fi
    fi

    info "Installing Babashka to ${install_dir}..."
    if ! bash "$bb_installer" --dir "$install_dir"; then
        error "Babashka installation failed"
        rm -f "$bb_installer"
        exit 1
    fi

    # Cleanup
    rm -f "$bb_installer"

    # Verify installation
    if [[ ! -f "${install_dir}/bb" ]]; then
        error "Babashka installation failed - bb not found at ${install_dir}/bb"
        exit 1
    fi
}

install_aishell() {
    # --- Configuration ---
    local repo_url="https://github.com/UniSoma/aishell"
    local version="${VERSION:-latest}"
    local install_dir="${INSTALL_DIR:-${HOME}/.local/bin}"

    # --- Color Support ---
    local blue="" green="" red="" yellow="" bold="" nc=""
    if [[ -t 1 ]] && [[ -z "${NO_COLOR:-}" ]]; then
        local colors
        colors=$(tput colors 2>/dev/null) || colors=0
        if [[ "$colors" -ge 8 ]]; then
            blue='\033[0;34m'
            green='\033[0;32m'
            red='\033[0;31m'
            yellow='\033[0;33m'
            bold='\033[1m'
            nc='\033[0m'
        fi
    fi

    # --- Output Functions ---
    info() {
        printf "${blue}==>${nc} ${bold}%s${nc}\n" "$1"
    }

    success() {
        printf "${green}==>${nc} %s\n" "$1"
    }

    error() {
        printf "${red}Error:${nc} %s\n" "$1" >&2
    }

    warn() {
        printf "${yellow}Warning:${nc} %s\n" "$1"
    }

    # --- Check for download tool (needed before Babashka check) ---
    local downloader=""
    if command -v curl &>/dev/null; then
        downloader="curl"
    elif command -v wget &>/dev/null; then
        downloader="wget"
    else
        error "Either 'curl' or 'wget' required"
        exit 1
    fi

    # --- Dependency Checks ---
    # Check for Babashka
    if ! command -v bb &>/dev/null; then
        info "Babashka not found. Installing..."
        install_babashka "$install_dir" "$downloader"
        success "Babashka installed to ${install_dir}/bb"
    else
        success "Babashka found: $(command -v bb)"
    fi

    # --- Create Install Directory ---
    if ! mkdir -p "$install_dir" 2>/dev/null; then
        error "Cannot write to ${install_dir}. Check permissions."
        exit 1
    fi

    # --- Determine Download URL ---
    local download_url
    if [[ "$version" == "latest" ]]; then
        download_url="${repo_url}/releases/latest/download/aishell"
    else
        download_url="${repo_url}/releases/download/v${version}/aishell"
    fi
    local checksum_url="${download_url}.sha256"

    # --- Download ---
    info "Downloading aishell..."
    if [[ "$downloader" == "curl" ]]; then
        if ! curl -fsSL --retry 3 "$download_url" -o "${install_dir}/aishell"; then
            error "Failed to download aishell from ${download_url}"
            exit 1
        fi
        if ! curl -fsSL --retry 3 "$checksum_url" -o /tmp/aishell.sha256; then
            error "Failed to download checksum from ${checksum_url}"
            rm -f "${install_dir}/aishell"
            exit 1
        fi
    else
        if ! wget -q --tries=3 -O "${install_dir}/aishell" "$download_url"; then
            error "Failed to download aishell from ${download_url}"
            exit 1
        fi
        if ! wget -q --tries=3 -O /tmp/aishell.sha256 "$checksum_url"; then
            error "Failed to download checksum from ${checksum_url}"
            rm -f "${install_dir}/aishell"
            exit 1
        fi
    fi

    # --- Verify Checksum ---
    info "Verifying checksum..."
    local expected_sha
    expected_sha=$(awk '{print $1}' /tmp/aishell.sha256)

    local actual_sha
    if command -v sha256sum &>/dev/null; then
        actual_sha=$(sha256sum "${install_dir}/aishell" | awk '{print $1}')
    else
        actual_sha=$(shasum -a 256 "${install_dir}/aishell" | awk '{print $1}')
    fi

    if [[ "$actual_sha" != "$expected_sha" ]]; then
        error "Checksum verification failed"
        echo "  Expected: $expected_sha" >&2
        echo "  Got:      $actual_sha" >&2
        rm -f "${install_dir}/aishell"
        rm -f /tmp/aishell.sha256
        exit 1
    fi

    # --- Install ---
    info "Installing..."
    chmod +x "${install_dir}/aishell"

    # Cleanup
    rm -f /tmp/aishell.sha256

    success "Done! Installed aishell to ${install_dir}/aishell"

    # --- PATH Check and Quick Start ---
    if [[ ":$PATH:" != *":$install_dir:"* ]]; then
        echo ""
        warn "${install_dir} is not in your PATH."
        echo "Add to your shell profile (~/.bashrc, ~/.zshrc, or ~/.profile):"
        echo ""
        echo "  export PATH=\"\$HOME/.local/bin:\$PATH\""
        echo ""
        echo "Then restart your shell or run: source ~/.bashrc"
    else
        echo ""
        echo "Quick start:"
        echo "  aishell setup    # Set up Docker image and select harnesses"
        echo "  aishell          # Start interactive shell in container"
        echo ""
    fi
}

# Execute on last line (protects against partial downloads)
install_aishell "$@"
