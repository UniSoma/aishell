# Phase 18: Distribution - Research

**Researched:** 2026-01-21
**Domain:** Babashka uberscript packaging and shell installer distribution
**Confidence:** HIGH

## Summary

This phase involves packaging aishell as a single-file Babashka uberscript and providing a curl|bash installer for distribution. The research confirms that Babashka's `bb uberscript` command is the standard tool for creating single-file executables, and curl|bash remains a widely-used pattern for CLI tool installation despite security concerns when proper mitigations are applied.

**Key findings:**
- Babashka uberscript bundles all source namespaces into a single .clj file with shebang for direct execution
- curl|bash installers are standard for developer tools (rustup, deno, homebrew) when security best practices are followed
- GitHub Releases now provides native SHA256 checksums for all uploaded assets (June 2025 feature)
- Installation to ~/.local/bin is widely supported but PATH inclusion varies by distribution

**Primary recommendation:** Use `bb uberscript` to create single-file executable, host on GitHub Releases with SHA256 checksum, and provide curl|bash installer with function wrapper protection against partial downloads.

## Standard Stack

The established approach for distributing Babashka CLI tools:

### Core
| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| Babashka CLI | Latest | Runtime execution | Required for all bb scripts, already assumed installed |
| bb uberscript | Built-in | Single-file packaging | Official Babashka tool for creating standalone executables |
| GitHub Releases | N/A | Artifact hosting | Standard for open-source tools, provides versioned URLs and native checksums |

### Supporting
| Library | Version | Purpose | When to Use |
|---------|---------|---------|-------------|
| sha256sum / shasum | System | Checksum verification | Installer must verify download integrity |
| curl | System | Download tool | Primary download method, fall back to wget |
| wget | System | Download fallback | Secondary if curl unavailable |

### Alternatives Considered
| Instead of | Could Use | Tradeoff |
|------------|-----------|----------|
| Uberscript | Uberjar | Heavier but handles dynamic requires and resources better |
| GitHub Releases | Self-hosted | More control but requires infrastructure maintenance |
| curl\|bash | Package managers | Better security/UX but requires per-platform packaging |

**Installation:**
No additional dependencies required. Babashka must be pre-installed on user system (Phase 18 assumption).

## Architecture Patterns

### Recommended Project Structure
```
project-root/
├── src/aishell/           # Source namespaces
├── aishell.clj            # Entry script (DEV: loads src/ dynamically)
├── dist/
│   ├── aishell            # Uberscript output (RELEASE: all-in-one)
│   └── aishell.sha256     # Checksum file
├── scripts/
│   └── build-release.sh   # Build automation
└── install.sh             # Installer template (gets uploaded to releases)
```

### Pattern 1: Uberscript Build Process
**What:** Compile all source namespaces into single executable file
**When to use:** Production releases, eliminates classpath dependencies
**Example:**
```bash
# Source: https://book.babashka.org/
# Build uberscript
bb uberscript dist/aishell -m aishell.core

# Add shebang (required for direct execution)
sed -i '1s/^/#!\/usr\/bin\/env bb\n/' dist/aishell

# Make executable
chmod +x dist/aishell

# Generate checksum
sha256sum dist/aishell > dist/aishell.sha256
```

### Pattern 2: Version Injection
**What:** Embed version string in uberscript at build time
**When to use:** All releases, enables --version flag
**Example:**
```bash
# Option 1: Environment variable during build
VERSION="2.0.0" bb uberscript dist/aishell -m aishell.core

# Option 2: Sed replacement after build
VERSION="2.0.0"
sed -i "s/__VERSION__/$VERSION/g" dist/aishell

# In code, read from environment or embedded constant
(def version (or (System/getenv "AISHELL_VERSION") "dev"))
```

### Pattern 3: curl|bash Installer with Partial Download Protection
**What:** Shell installer wrapped in function, executed on last line
**When to use:** All curl|bash installers to prevent partial execution
**Example:**
```bash
# Source: https://sh.rustup.rs/ (rustup installer pattern)
#!/bin/bash
set -euo pipefail

install_aishell() {
    # All installation logic here
    local version="${VERSION:-latest}"
    local install_dir="${HOME}/.local/bin"

    # Download with retry
    curl -fsSL --retry 3 \
        "https://github.com/USER/REPO/releases/download/${version}/aishell" \
        -o "${install_dir}/aishell"

    # Verify checksum
    local expected_sha
    expected_sha=$(curl -fsSL --retry 3 \
        "https://github.com/USER/REPO/releases/download/${version}/aishell.sha256" \
        | awk '{print $1}')

    local actual_sha
    actual_sha=$(sha256sum "${install_dir}/aishell" | awk '{print $1}')

    if [[ "$actual_sha" != "$expected_sha" ]]; then
        echo "Error: Checksum verification failed" >&2
        exit 1
    fi

    chmod +x "${install_dir}/aishell"
    echo "Installed aishell to ${install_dir}/aishell"
}

# Execute on last line (protects against partial download)
install_aishell "$@"
```

### Pattern 4: Dependency Verification
**What:** Check for required tools before proceeding
**When to use:** Beginning of installer script
**Example:**
```bash
# Source: https://sh.rustup.rs/ (rustup pattern)
check_cmd() {
    command -v "$1" &>/dev/null
}

need_cmd() {
    if ! check_cmd "$1"; then
        echo "Error: need '$1' (command not found)" >&2
        exit 1
    fi
}

# Verify Babashka
if ! check_cmd "bb"; then
    echo "Error: Babashka required. Install: https://babashka.org" >&2
    exit 1
fi

# Verify download tool
if ! check_cmd "curl" && ! check_cmd "wget"; then
    echo "Error: Either 'curl' or 'wget' required" >&2
    exit 1
fi
```

### Pattern 5: PATH Detection and Advisory
**What:** Check if install directory is in PATH, provide guidance if not
**When to use:** After successful installation
**Example:**
```bash
# Source: Legacy install.sh pattern
check_path() {
    if [[ ":$PATH:" != *":$install_dir:"* ]]; then
        echo ""
        echo "Note: $install_dir is not in your PATH."
        echo "Add to your shell profile (~/.bashrc, ~/.zshrc, or ~/.profile):"
        echo ""
        echo "  export PATH=\"\$HOME/.local/bin:\$PATH\""
        echo ""
    fi
}
```

### Anti-Patterns to Avoid
- **Executing installer directly without function wrapper:** Network failures can execute partial scripts
- **No checksum verification:** Download corruption or tampering goes undetected
- **Silent PATH modification:** Modifying shell profiles without explicit user consent is invasive
- **Assuming sha256sum availability:** macOS uses `shasum -a 256`, need fallback logic

## Don't Hand-Roll

Problems that look simple but have existing solutions:

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| Single-file executable | Custom concatenation | `bb uberscript` | Handles namespace ordering, dependency resolution, optimization |
| Download retry logic | Manual loop | `curl --retry 3` or `wget --tries=3` | Built-in exponential backoff, handles transient failures |
| Checksum generation | Manual SHA calculation | GitHub Releases native checksums | Automatic, immutable, displayed in UI (since June 2025) |
| Platform detection | Custom uname parsing | Babashka's platform detection | Handles edge cases like Rosetta emulation, 32-bit userlands |
| Shell profile detection | Custom shell checking | Advisory message only | Too fragile across distributions, let user choose |

**Key insight:** Babashka's uberscript tool already handles the complexity of namespace dependency ordering, dead code elimination (via carve integration), and proper require resolution. Custom concatenation will break on namespace cycles or incorrect load order.

## Common Pitfalls

### Pitfall 1: Dynamic Requires in Uberscripts
**What goes wrong:** Code using dynamic requires fails at runtime in uberscript
**Why it happens:** `bb uberscript` only evaluates top-level `ns` and `require` forms, not code execution
**How to avoid:** Use static top-level requires in all namespaces. If dynamic loading is needed, use uberjar instead.
**Warning signs:** Requires inside functions, conditional requires, `(require ...)` calls in code

**Example:**
```clojure
;; BAD - Won't work in uberscript
(defn load-plugin [name]
  (require (symbol (str "plugin." name))))

;; GOOD - Works in uberscript
(ns my.app
  (:require [plugin.one :as p1]
            [plugin.two :as p2]))
```

### Pitfall 2: Resource Loading in Uberscripts
**What goes wrong:** `io/resource` calls fail because uberscript has no classpath
**Why it happens:** Resources aren't bundled into uberscript, only source code
**How to avoid:** Embed resources as strings/data structures in code, or use uberjar
**Warning signs:** `io/resource`, `slurp (io/resource ...)` calls

### Pitfall 3: Missing Shebang After Uberscript Build
**What goes wrong:** Generated uberscript isn't executable without `bb` prefix
**Why it happens:** `bb uberscript` doesn't add shebang automatically
**How to avoid:** Always add `#!/usr/bin/env bb\n` as first line after build
**Warning signs:** Need to run `bb aishell` instead of `./aishell`

### Pitfall 4: Partial Download Execution
**What goes wrong:** If download interrupts mid-script, bash executes incomplete code
**Why it happens:** Bash executes as it reads, doesn't wait for complete download
**How to avoid:** Wrap entire installer in function, call on last line only
**Warning signs:** Installer not wrapped in function, no protective pattern

### Pitfall 5: Checksum Comparison Pitfalls
**What goes wrong:** Checksum verification passes when it shouldn't or fails when it should
**Why it happens:** Different checksum tools on different platforms, whitespace differences
**How to avoid:** Use portable comparison, handle both `sha256sum` and `shasum -a 256`
**Warning signs:** Direct string comparison without normalization

**Example:**
```bash
# BAD - Platform-specific, whitespace issues
expected="abc123"
actual=$(sha256sum file.txt)  # Returns "abc123  file.txt"
[[ "$actual" == "$expected" ]]  # Fails due to whitespace

# GOOD - Portable, normalized
expected="abc123"
if command -v sha256sum &>/dev/null; then
    actual=$(sha256sum file.txt | awk '{print $1}')
else
    actual=$(shasum -a 256 file.txt | awk '{print $1}')
fi
[[ "$actual" == "$expected" ]]  # Works correctly
```

### Pitfall 6: ~/.local/bin Not in PATH
**What goes wrong:** User installs successfully but can't run `aishell` command
**Why it happens:** Not all distributions include ~/.local/bin in default PATH
**How to avoid:** Provide clear post-install instructions, don't modify shell profiles automatically
**Warning signs:** "command not found" after successful install on Arch, antiX, some minimal distros

## Code Examples

Verified patterns from official sources:

### Complete Uberscript Build Script
```bash
# Source: https://book.babashka.org/ and https://github.com/vedang/bb-scripts
#!/bin/bash
set -euo pipefail

VERSION="${1:-dev}"
OUTPUT_DIR="dist"
OUTPUT_FILE="${OUTPUT_DIR}/aishell"

# Create output directory
mkdir -p "$OUTPUT_DIR"

# Build uberscript with main namespace
bb uberscript "$OUTPUT_FILE" -m aishell.core

# Add shebang for direct execution
sed -i '1s/^/#!\/usr\/bin\/env bb\n/' "$OUTPUT_FILE"

# Inject version (replace placeholder in source)
# Assumes source has (def version "__VERSION__")
sed -i "s/__VERSION__/$VERSION/g" "$OUTPUT_FILE"

# Make executable
chmod +x "$OUTPUT_FILE"

# Generate checksum
if command -v sha256sum &>/dev/null; then
    sha256sum "$OUTPUT_FILE" | awk '{print $1 "  " $2}' > "${OUTPUT_FILE}.sha256"
else
    shasum -a 256 "$OUTPUT_FILE" | awk '{print $1 "  " $2}' > "${OUTPUT_FILE}.sha256"
fi

echo "Built $OUTPUT_FILE (version: $VERSION)"
cat "${OUTPUT_FILE}.sha256"
```

### Complete curl|bash Installer
```bash
# Source: Composite of https://sh.rustup.rs/ and https://raw.githubusercontent.com/babashka/babashka/master/install
#!/bin/bash
set -euo pipefail

install_aishell() {
    local version="${VERSION:-latest}"
    local install_dir="${INSTALL_DIR:-${HOME}/.local/bin}"
    local repo_url="https://github.com/USER/REPO"

    # Check for Babashka
    if ! command -v bb &>/dev/null; then
        echo "Error: Babashka required. Install: https://babashka.org" >&2
        exit 1
    fi

    # Check for download tool
    local downloader=""
    if command -v curl &>/dev/null; then
        downloader="curl"
    elif command -v wget &>/dev/null; then
        downloader="wget"
    else
        echo "Error: Either 'curl' or 'wget' required" >&2
        exit 1
    fi

    # Create install directory
    mkdir -p "$install_dir"

    # Determine download URL
    local download_url
    if [[ "$version" == "latest" ]]; then
        download_url="${repo_url}/releases/latest/download/aishell"
    else
        download_url="${repo_url}/releases/download/v${version}/aishell"
    fi

    # Download binary
    echo "Downloading aishell ${version}..."
    if [[ "$downloader" == "curl" ]]; then
        curl -fsSL --retry 3 "$download_url" -o "${install_dir}/aishell"
        curl -fsSL --retry 3 "${download_url}.sha256" -o /tmp/aishell.sha256
    else
        wget -q --tries=3 -O "${install_dir}/aishell" "$download_url"
        wget -q --tries=3 -O /tmp/aishell.sha256 "${download_url}.sha256"
    fi

    # Verify checksum
    echo "Verifying checksum..."
    local expected_sha
    expected_sha=$(awk '{print $1}' /tmp/aishell.sha256)

    local actual_sha
    if command -v sha256sum &>/dev/null; then
        actual_sha=$(sha256sum "${install_dir}/aishell" | awk '{print $1}')
    else
        actual_sha=$(shasum -a 256 "${install_dir}/aishell" | awk '{print $1}')
    fi

    if [[ "$actual_sha" != "$expected_sha" ]]; then
        echo "Error: Checksum verification failed" >&2
        echo "  Expected: $expected_sha" >&2
        echo "  Got:      $actual_sha" >&2
        rm -f "${install_dir}/aishell"
        exit 1
    fi

    # Make executable
    chmod +x "${install_dir}/aishell"

    # Cleanup
    rm -f /tmp/aishell.sha256

    echo ""
    echo "Installed aishell to ${install_dir}/aishell"

    # Check PATH
    if [[ ":$PATH:" != *":$install_dir:"* ]]; then
        echo ""
        echo "Note: ${install_dir} is not in your PATH."
        echo "Add to your shell profile (~/.bashrc, ~/.zshrc, or ~/.profile):"
        echo ""
        echo "  export PATH=\"${install_dir}:\$PATH\""
        echo ""
    else
        echo ""
        echo "Quick start:"
        echo "  aishell build    # Build Docker image"
        echo "  aishell shell    # Start interactive shell"
        echo ""
    fi
}

# Execute on last line (protects against partial downloads)
install_aishell "$@"
```

### GitHub Actions Release Workflow
```yaml
# Source: https://github.blog/changelog/2025-06-03-releases-now-expose-digests-for-release-assets/
name: Release
on:
  push:
    tags:
      - 'v*'

jobs:
  release:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4

      - name: Install Babashka
        run: |
          curl -fsSL https://raw.githubusercontent.com/babashka/babashka/master/install | bash
          sudo mv bb /usr/local/bin/

      - name: Build release
        run: |
          VERSION="${GITHUB_REF#refs/tags/v}"
          ./scripts/build-release.sh "$VERSION"

      - name: Create Release
        id: create_release
        uses: actions/create-release@v1
        env:
          GITHUB_TOKEN: ${{ secrets.GITHUB_TOKEN }}
        with:
          tag_name: ${{ github.ref }}
          release_name: Release ${{ github.ref }}
          draft: false
          prerelease: false

      - name: Upload aishell binary
        uses: actions/upload-release-asset@v1
        env:
          GITHUB_TOKEN: ${{ secrets.GITHUB_TOKEN }}
        with:
          upload_url: ${{ steps.create_release.outputs.upload_url }}
          asset_path: ./dist/aishell
          asset_name: aishell
          asset_content_type: application/octet-stream

      - name: Upload checksum
        uses: actions/upload-release-asset@v1
        env:
          GITHUB_TOKEN: ${{ secrets.GITHUB_TOKEN }}
        with:
          upload_url: ${{ steps.create_release.outputs.upload_url }}
          asset_path: ./dist/aishell.sha256
          asset_name: aishell.sha256
          asset_content_type: text/plain
```

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| Manual checksum files | GitHub native checksums | June 2025 | Auto-generated SHA256 for all release assets, displayed in UI |
| Uberscript namespace ordering issues | Fixed ordering | Fixed in bb 0.2.x+ | Namespaces now output in correct dependency order |
| No uberscript optimization | Carve integration | Available since bb 0.2.0+ | Can reduce uberscript size by 72% by removing unused vars |
| ~/.profile only | Multiple shell profiles | Ongoing evolution | Must advise for bash, zsh, fish separately |

**Deprecated/outdated:**
- **Uberjar-only distribution:** Uberscript now handles most cases, uberjar only needed for resources/dynamic requires
- **Custom namespace bundling:** `bb uberscript` supersedes manual concatenation approaches
- **Manual SHA256 in GitHub Actions:** GitHub now provides native checksums, no need for custom actions

## Open Questions

Things that couldn't be fully resolved:

1. **Optimal carve integration for uberscript optimization**
   - What we know: Can reduce size by 72% by removing unused vars
   - What's unclear: How to integrate carve into build process, configuration needed
   - Recommendation: Skip optimization for initial release, add later if size becomes issue

2. **VERSION=latest URL resolution**
   - What we know: GitHub Releases supports /releases/latest/download/artifact
   - What's unclear: Behavior if no releases exist, caching implications
   - Recommendation: Default to latest, support VERSION=x.y.z override for pinning

3. **INSTALL_DIR override support**
   - What we know: Users may want custom install locations
   - What's unclear: Whether to support or keep simple
   - Recommendation: Support INSTALL_DIR env var (low complexity, high value for advanced users)

## Sources

### Primary (HIGH confidence)
- [Babashka Book - Official Documentation](https://book.babashka.org/) - Uberscript creation, command-line usage
- [Babashka Install Script](https://github.com/babashka/babashka/blob/master/install) - Official installer patterns
- [Rustup Installer](https://sh.rustup.rs/) - Industry-standard curl|bash implementation
- [GitHub Changelog: Releases expose digests](https://github.blog/changelog/2025-06-03-releases-now-expose-digests-for-release-assets/) - Native checksum support
- [Deno Installation Documentation](https://docs.deno.com/runtime/getting_started/installation/) - curl|bash installer example

### Secondary (MEDIUM confidence)
- [bb-scripts by vedang](https://github.com/vedang/bb-scripts) - Real-world uberscript examples with shebang handling
- [Is curl|bash insecure? - Sandstorm Blog](https://sandstorm.io/news/2015-09-24-is-curl-bash-insecure-pgp-verified-install) - Security analysis and mitigation patterns
- [GitHub community discussion on release checksums](https://github.com/orgs/community/discussions/23512) - Checksum best practices
- [Download and verify file hashes guide](https://transloadit.com/devtips/hashing-files-with-curl-a-developer-s-guide/) - Portable checksum verification patterns

### Tertiary (LOW confidence)
- Various WebSearch results on ~/.local/bin PATH behavior across distributions - Implementation varies significantly
- Community discussions on uberscript limitations - Anecdotal, not authoritative

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH - Babashka official docs and real-world usage confirmed
- Architecture: HIGH - Verified patterns from rustup, babashka, deno installers
- Pitfalls: HIGH - Documented in official Babashka book and GitHub issues

**Research date:** 2026-01-21
**Valid until:** 90 days (stable tooling, slow-moving standards)
