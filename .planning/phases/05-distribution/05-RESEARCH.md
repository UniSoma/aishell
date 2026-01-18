# Phase 5: Distribution - Research

**Researched:** 2026-01-18
**Domain:** curl|bash installers, XDG standards, shell integration
**Confidence:** HIGH

## Summary

This research examines best practices for `curl | bash` installers, installation locations, asset distribution strategies, and error handling. The primary goal is enabling users to install aishell with a single command (`curl ... | bash`) and have it available in PATH without manual configuration.

The standard approach follows patterns established by tools like nvm, rustup, Homebrew, and Claude Code. Key findings:

1. **Installation location**: `~/.local/bin` is the XDG-standard user-local directory that distributions should ensure is in PATH
2. **Installer structure**: Wrap all code in functions to protect against partial downloads, use `set -e` for fail-fast behavior
3. **Content embedding**: For single-script distribution, embed Dockerfile and entrypoint as heredocs within the main script
4. **Hosting**: Use raw.githubusercontent.com with version tags for stable, versioned downloads
5. **Progress output**: Verbose step-by-step output with color support (respecting NO_COLOR)

**Primary recommendation:** Create a self-contained install script that downloads the main `aishell` script (with embedded heredocs) to `~/.local/bin/aishell`, verifies Docker is available (warning if not), and provides clear success/failure output.

## Standard Stack

The established tools/patterns for this domain:

### Core
| Tool/Pattern | Purpose | Why Standard |
|--------------|---------|--------------|
| raw.githubusercontent.com | Script hosting | Free, reliable, version-controlled via git tags |
| `~/.local/bin` | Installation target | XDG standard, user-local, no sudo required |
| Heredoc embedding | Asset bundling | Single file distribution, no external dependencies |
| `curl -fsSL` | Download | Standard flags: fail on HTTP errors, silent, follow redirects |

### Supporting
| Tool/Pattern | Purpose | When to Use |
|--------------|---------|-------------|
| `set -e` (errexit) | Error handling | Always - fail fast on any error |
| `set -u` (nounset) | Variable safety | Always - catch typos and undefined vars |
| `set -o pipefail` | Pipe error handling | Always - fail if any pipe command fails |
| `tput colors` / NO_COLOR | Color output | For user feedback with terminal detection |

### Alternatives Considered
| Instead of | Could Use | Tradeoff |
|------------|-----------|----------|
| raw.githubusercontent.com | GitHub Releases | More complex, better for binaries with checksums |
| Heredoc embedding | Separate downloads | More files to manage, network dependency at runtime |
| `~/.local/bin` | `/usr/local/bin` | Requires sudo, not user-local |

**Installation command pattern:**
```bash
curl -fsSL https://raw.githubusercontent.com/OWNER/REPO/main/install.sh | bash
```

## Architecture Patterns

### Recommended Project Structure
```
/                           # Repository root
├── aishell                 # Main script (with embedded heredocs)
├── Dockerfile              # Used during development (content embedded in aishell)
├── entrypoint.sh           # Used during development (content embedded in aishell)
├── bashrc.aishell          # Used during development (content embedded in aishell)
└── install.sh              # Installer script (downloads aishell to ~/.local/bin)
```

### Pattern 1: Function-Wrapped Installer
**What:** Wrap all installer code in functions, call main at the end
**When to use:** Always for curl|bash scripts
**Why:** Protects against partial download execution

```bash
#!/bin/bash
# Source: nvm install.sh pattern

main() {
    # All installation logic here
    check_prerequisites
    download_script
    install_to_path
    show_success
}

check_prerequisites() {
    # Validation logic
}

# ... other functions ...

# Call main at the very end
main "$@"
```

### Pattern 2: Self-Contained Script with Heredocs
**What:** Embed Dockerfile, entrypoint, and other assets as heredocs in the main script
**When to use:** When distributing multiple files as a single script
**Why:** Eliminates external dependencies, single file download

```bash
#!/bin/bash
# Main script that extracts embedded content at runtime

write_dockerfile() {
    cat > "$1" << 'DOCKERFILE_EOF'
FROM debian:bookworm-slim
# ... Dockerfile content ...
DOCKERFILE_EOF
}

write_entrypoint() {
    cat > "$1" << 'ENTRYPOINT_EOF'
#!/bin/bash
# ... entrypoint content ...
ENTRYPOINT_EOF
}

# Called when building image
ensure_image() {
    local temp_dir
    temp_dir=$(mktemp -d)
    write_dockerfile "$temp_dir/Dockerfile"
    write_entrypoint "$temp_dir/entrypoint.sh"
    # ... build logic ...
}
```

### Pattern 3: Verbose Progress Output
**What:** Show each step as it happens with colored output
**When to use:** Installation scripts where user waits for completion
**Why:** User knows what's happening, can identify where failures occur

```bash
# Source: Homebrew install.sh pattern

info() {
    if supports_color; then
        printf '\033[0;34m==>\033[0m \033[1m%s\033[0m\n' "$1"
    else
        printf '==> %s\n' "$1"
    fi
}

success() {
    if supports_color; then
        printf '\033[0;32m==>\033[0m %s\n' "$1"
    else
        printf '==> %s\n' "$1"
    fi
}

error() {
    if supports_color; then
        printf '\033[0;31mError:\033[0m %s\n' "$1" >&2
    else
        printf 'Error: %s\n' "$1" >&2
    fi
}

supports_color() {
    [[ -t 1 ]] && [[ -z "${NO_COLOR:-}" ]] && [[ "$(tput colors 2>/dev/null)" -ge 8 ]]
}
```

### Anti-Patterns to Avoid
- **Not wrapping in functions:** Exposes to partial download risk
- **Using `set -x` in production:** Too verbose, exposes internals
- **Modifying shell rc files without checking:** Can break existing configurations
- **Silent failures:** User doesn't know what went wrong
- **Requiring sudo:** User-local installation is safer and simpler

## Don't Hand-Roll

Problems that look simple but have existing solutions:

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| Color detection | Manual TERM parsing | `tput colors` + NO_COLOR check | Edge cases, terminal variations |
| Shell profile detection | Hardcoded paths | Check multiple files in order | bash_profile vs profile vs bashrc varies |
| Download with redirects | Basic curl | `curl -fsSL` | Handle HTTP errors, redirects properly |
| Error handling | Manual checks | `set -euo pipefail` | Comprehensive, fail-fast behavior |
| Safe string comparison | `[ $var = val ]` | `[[ "$var" = "val" ]]` | Word splitting, glob expansion safety |

**Key insight:** Shell scripting has many subtle edge cases. Using established patterns from tools like nvm, rustup, and Homebrew provides battle-tested solutions.

## Common Pitfalls

### Pitfall 1: Partial Download Execution
**What goes wrong:** Network interruption during download executes truncated script
**Why it happens:** curl pipes directly to bash, bash executes as it receives
**How to avoid:** Wrap all code in functions, call main at end
**Warning signs:** Any top-level code outside functions

### Pitfall 2: PATH Not Configured
**What goes wrong:** User installs successfully but command not found
**Why it happens:** `~/.local/bin` may not be in user's PATH
**How to avoid:** Check PATH, provide clear instructions if missing
**Warning signs:** Successful install but "command not found"

```bash
# Detection and suggestion pattern
if [[ ":$PATH:" != *":$HOME/.local/bin:"* ]]; then
    echo "Note: $HOME/.local/bin is not in your PATH"
    echo "Add this to your shell profile:"
    echo '  export PATH="$HOME/.local/bin:$PATH"'
fi
```

### Pitfall 3: Silent Docker Check Failure
**What goes wrong:** Install succeeds but tool fails on first use
**Why it happens:** Docker not installed or not running
**How to avoid:** Check Docker during install, warn clearly (but don't block)
**Warning signs:** No Docker check in installer

### Pitfall 4: Overwriting Without Backup
**What goes wrong:** User loses customizations on re-install
**Why it happens:** Simple overwrite strategy
**How to avoid:** For this project, CONTEXT.md specifies "overwrite always" which is acceptable since the script shouldn't be modified by users

### Pitfall 5: WSL2 PATH Issues
**What goes wrong:** `~/.local/bin` not in PATH on WSL2
**Why it happens:** WSL2 may source profile files differently, Windows PATH mixing
**How to avoid:** Same solution as Pitfall 2 - detect and suggest
**Warning signs:** Works on native Linux, fails on WSL2

### Pitfall 6: Hardcoded Paths Breaking on Different Systems
**What goes wrong:** Script uses `/home/user` instead of `$HOME`
**Why it happens:** Testing only on own system
**How to avoid:** Always use `$HOME`, never hardcode paths
**Warning signs:** Any absolute path not derived from environment variables

## Code Examples

Verified patterns from official sources:

### Strict Mode Header
```bash
#!/bin/bash
# Source: Community best practices
set -euo pipefail
```

### Color-Aware Output Functions
```bash
# Source: Homebrew install.sh pattern

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
        BOLD='\033[1m'
        NC='\033[0m'
    else
        BLUE=''
        GREEN=''
        RED=''
        BOLD=''
        NC=''
    fi
}

info() {
    printf "${BLUE}==>${NC} ${BOLD}%s${NC}\n" "$1"
}

success() {
    printf "${GREEN}==>${NC} %s\n" "$1"
}

error() {
    printf "${RED}Error:${NC} %s\n" "$1" >&2
}
```

### Installation Directory Setup
```bash
# Source: XDG specification, rustup/nvm patterns

INSTALL_DIR="${HOME}/.local/bin"

ensure_install_dir() {
    if [[ ! -d "$INSTALL_DIR" ]]; then
        info "Creating $INSTALL_DIR"
        mkdir -p "$INSTALL_DIR"
    fi
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
```

### Docker Availability Check
```bash
# Source: Project requirement (warn but continue)

check_docker() {
    if ! command -v docker &>/dev/null; then
        echo ""
        echo "Warning: Docker is not installed."
        echo "aishell requires Docker to run. Install Docker from: https://docs.docker.com/get-docker/"
        echo ""
        return 0  # Continue installation anyway
    fi

    if ! docker info &>/dev/null 2>&1; then
        echo ""
        echo "Warning: Docker is installed but not running."
        echo "Start Docker before using aishell."
        echo ""
        return 0  # Continue installation anyway
    fi
}
```

### Main Function Pattern
```bash
# Source: nvm install.sh pattern

main() {
    setup_colors

    info "Installing aishell..."

    check_docker
    ensure_install_dir
    download_script
    set_permissions

    success "Installed aishell v${VERSION} to ${INSTALL_DIR}/aishell"
    check_path
}

# Ensure script is not executed if partially downloaded
main "$@"
```

### Heredoc Embedding for Dockerfile
```bash
# Source: makeself pattern, adapted

write_dockerfile() {
    local target_dir="$1"
    cat > "${target_dir}/Dockerfile" << 'DOCKERFILE_EOF'
FROM debian:bookworm-slim

ARG WITH_CLAUDE=false
ARG WITH_OPENCODE=false

ENV DEBIAN_FRONTEND=noninteractive

RUN apt-get update && apt-get install -y --no-install-recommends \
    bash ca-certificates curl git jq less ripgrep sudo vim \
    && rm -rf /var/lib/apt/lists/*

# ... rest of Dockerfile ...
DOCKERFILE_EOF
}
```

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| `/usr/local/bin` with sudo | `~/.local/bin` user-local | XDG adoption ~2020 | No sudo needed, safer |
| Simple curl pipe | Function-wrapped scripts | Always best practice | Partial download protection |
| Hardcoded bash_profile | Multi-file detection | Modern installers | Works across shells |
| Silent install | Verbose progress | User experience focus | Users understand what happens |

**Deprecated/outdated:**
- Using `~/.bash_profile` only: Doesn't work on all systems; need to check multiple files
- Requiring root/sudo for user tools: User-local installation is preferred
- `set -e` alone: Add `set -u` and `set -o pipefail` for comprehensive error handling

## Open Questions

Things that couldn't be fully resolved:

1. **Automatic PATH configuration**
   - What we know: XDG says distros SHOULD add ~/.local/bin to PATH, but not all do
   - What's unclear: Whether to auto-modify shell rc files or just suggest
   - Recommendation: CONTEXT.md defers this - just suggest the fix, don't auto-modify

2. **Version in install URL**
   - What we know: Can use tags (v1.0.0) or branches (main) in raw.githubusercontent.com URLs
   - What's unclear: Project doesn't have releases yet
   - Recommendation: Start with main branch, move to versioned tags when releases exist

## Sources

### Primary (HIGH confidence)
- [XDG Base Directory Specification](https://specifications.freedesktop.org/basedir/latest/) - Installation location
- [ArchWiki XDG Base Directory](https://wiki.archlinux.org/title/XDG_Base_Directory) - PATH configuration
- [nvm install.sh](https://github.com/nvm-sh/nvm) - Installer patterns, shell detection
- [rustup installation](https://rust-lang.github.io/rustup/installation/index.html) - Binary installer patterns
- [Homebrew install.sh](https://raw.githubusercontent.com/Homebrew/install/HEAD/install.sh) - Progress output, error handling

### Secondary (MEDIUM confidence)
- [Best practices for curl in shell scripts](https://www.joyfulbikeshedding.com/blog/2020-05-11-best-practices-when-using-curl-in-shell-scripts.html) - curl flags
- [Safer bash scripts with set -euxo pipefail](https://coderwall.com/p/fkfaqq/safer-bash-scripts-with-set-euxo-pipefail) - Error handling
- [Bash Tips: Embedding Files](https://blog.tratif.com/2023/02/17/bash-tips-6-embedding-files-in-a-single-bash-script/) - Heredoc patterns

### Tertiary (LOW confidence)
- Various GitHub issue discussions on WSL2 PATH handling

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH - XDG specification is authoritative, patterns from major tools verified
- Architecture: HIGH - Patterns taken directly from nvm, rustup, Homebrew source code
- Pitfalls: HIGH - Well-documented in security literature and tool documentation

**Research date:** 2026-01-18
**Valid until:** 60 days (stable domain, patterns rarely change)
