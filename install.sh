#!/bin/bash
# install.sh - Installer for aishell
# Downloads the aishell binary for this platform from GitHub Releases and
# verifies it against the release's SHA256SUMS. Nothing else is installed.
#
# Usage: curl -fsSL https://raw.githubusercontent.com/UniSoma/aishell/main/install.sh | bash
#
# Environment variables:
#   VERSION              - Version to install (default: latest)
#   INSTALL_DIR          - Installation directory (default: ~/.local/bin)
#   AISHELL_RELEASE_URL  - Release base URL (default: the GitHub releases page)

set -euo pipefail

# Global, not local: the EXIT trap fires after install_aishell has returned.
aishell_tmp_dir=""

install_aishell() {
    # --- Configuration ---
    local releases_url="${AISHELL_RELEASE_URL:-https://github.com/UniSoma/aishell/releases}"
    local version="${VERSION:-latest}"
    local install_dir="${INSTALL_DIR:-${HOME}/.local/bin}"
    local supported="linux-amd64, linux-aarch64, macos-amd64, macos-aarch64"

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

    # --- Check for download tool ---
    local downloader=""
    if command -v curl &>/dev/null; then
        downloader="curl"
    elif command -v wget &>/dev/null; then
        downloader="wget"
    else
        error "Either 'curl' or 'wget' required"
        exit 1
    fi

    fetch() {
        local url="$1" dest="$2"
        if [[ "$downloader" == "curl" ]]; then
            curl -fsSL --retry 3 "$url" -o "$dest"
        else
            wget -q --tries=3 -O "$dest" "$url"
        fi
    }

    # --- Detect Platform ---
    local os arch
    case "$(uname -s)" in
        Linux) os="linux" ;;
        Darwin) os="macos" ;;
        *)
            error "Unsupported operating system: $(uname -s). Supported: ${supported}"
            exit 1
            ;;
    esac

    case "$(uname -m)" in
        x86_64 | amd64) arch="amd64" ;;
        aarch64 | arm64) arch="aarch64" ;;
        *)
            error "Unsupported architecture: $(uname -m). Supported: ${supported}"
            exit 1
            ;;
    esac

    # A Rosetta shell on Apple Silicon reports x86_64; hw.optional.arm64 does not.
    if [[ "$os" == "macos" ]] && [[ "$(sysctl -n hw.optional.arm64 2>/dev/null || echo 0)" == "1" ]]; then
        arch="aarch64"
    fi

    local asset="aishell-${os}-${arch}"

    # --- Create Install Directory ---
    if ! mkdir -p "$install_dir" 2>/dev/null; then
        error "Cannot write to ${install_dir}. Check permissions."
        exit 1
    fi

    # --- Determine Download URLs ---
    local asset_base
    if [[ "$version" == "latest" ]]; then
        asset_base="${releases_url}/latest/download"
    else
        asset_base="${releases_url}/download/v${version}"
    fi
    local download_url="${asset_base}/${asset}"
    local checksum_url="${asset_base}/SHA256SUMS"

    # --- Download (to a temp dir, so a bad download never touches the install) ---
    aishell_tmp_dir=$(mktemp -d)
    trap 'rm -rf "${aishell_tmp_dir:?}"' EXIT

    info "Downloading ${asset}..."
    if ! fetch "$download_url" "${aishell_tmp_dir}/${asset}"; then
        error "Failed to download ${asset} from ${download_url}"
        exit 1
    fi
    if ! fetch "$checksum_url" "${aishell_tmp_dir}/SHA256SUMS"; then
        error "Failed to download checksum from ${checksum_url}"
        exit 1
    fi

    # --- Verify Checksum ---
    info "Verifying checksum..."
    # Match the filename token exactly: "aishell" is a prefix of "aishell-linux-amd64".
    local expected_sha
    expected_sha=$(awk -v name="$asset" '$2 == name || $2 == "*" name {print tolower($1); exit}' \
        "${aishell_tmp_dir}/SHA256SUMS")

    if [[ -z "$expected_sha" ]]; then
        error "SHA256SUMS from ${checksum_url} lists no entry for ${asset}"
        exit 1
    fi

    local actual_sha
    if command -v sha256sum &>/dev/null; then
        actual_sha=$(sha256sum "${aishell_tmp_dir}/${asset}" | awk '{print tolower($1)}')
    else
        actual_sha=$(shasum -a 256 "${aishell_tmp_dir}/${asset}" | awk '{print tolower($1)}')
    fi

    if [[ "$actual_sha" != "$expected_sha" ]]; then
        error "Checksum verification failed"
        echo "  Expected: $expected_sha" >&2
        echo "  Got:      $actual_sha" >&2
        exit 1
    fi

    # --- Install ---
    info "Installing..."
    if ! mv -f "${aishell_tmp_dir}/${asset}" "${install_dir}/aishell"; then
        error "Failed to install to ${install_dir}/aishell"
        exit 1
    fi
    # After the move: a cross-filesystem mv copies, and the copy's mode is not
    # guaranteed to be the one we set on the temp file.
    chmod +x "${install_dir}/aishell"

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
