# Phase 18: Distribution - Context

**Gathered:** 2026-01-21
**Status:** Ready for planning

<domain>
## Phase Boundary

Package aishell as a single-file uberscript and provide a curl|bash installer for easy installation. Users can install aishell via one-liner. Assumes Babashka is already installed on target system.

</domain>

<decisions>
## Implementation Decisions

### Installation location
- Default install path: `~/.local/bin/aishell`
- Create `~/.local/bin` silently if it doesn't exist (mkdir -p without notification)

### Installer behavior
- Show progress steps during install: Downloading... Verifying... Installing... Done
- Overwrite existing installation silently (good for updates, no prompts)
- Verify download with SHA256 checksum
- Show quick start instructions after successful install (how to run aishell build, aishell shell)

### Uberscript packaging
- Version embedded at build time (injected into uberscript during build process)
- Release filename: `aishell` (no extension, like standard Unix binaries)
- Host artifacts on GitHub Releases (versioned, stable URLs, checksums alongside)
- Default to latest release (VERSION=x.y.z env var for specific version)

### Error handling
- Missing Babashka: "Babashka required. Install: https://babashka.org"
- Checksum verification failure: Fail immediately with clear message, no retry
- Permission denied: "Cannot write to ~/.local/bin. Check permissions."

### Claude's Discretion
- PATH detection approach (warn with instructions if ~/.local/bin not in PATH)
- Whether to support INSTALL_DIR env var for custom location
- Network failure behavior (immediate fail vs one retry)
- Exact progress message wording
- Quick start message content

</decisions>

<specifics>
## Specific Ideas

No specific requirements — open to standard approaches for curl|bash installers.

</specifics>

<deferred>
## Deferred Ideas

None — discussion stayed within phase scope

</deferred>

---

*Phase: 18-distribution*
*Context gathered: 2026-01-21*
