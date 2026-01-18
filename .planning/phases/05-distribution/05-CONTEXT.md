# Phase 5: Distribution - Context

**Gathered:** 2026-01-18
**Status:** Ready for planning

<domain>
## Phase Boundary

Deliver the tool so users can install via `curl | bash` and have it available in PATH. After installation, command is available without manual configuration. Installation works on fresh Linux system with Docker Engine installed.

</domain>

<decisions>
## Implementation Decisions

### Install location
- Install to `~/.local/bin/aishell` (user-local, follows XDG)
- Research best practices for PATH configuration (not a V1 requirement, but nice-to-have; should also work on WSL2)
- Single self-contained script — all content (Dockerfile, entrypoint, bashrc) embedded as heredocs
- Host install script at raw.githubusercontent.com from the repo

### Install script behavior
- Verbose progress output — show each step as it happens
- Use colors with NO_COLOR support (respect NO_COLOR env var)
- Overwrite always on re-run — simple, stateless, always gets latest
- No checksum/signature verification for V1

### Prerequisites & validation
- Check for Docker and warn if missing (but continue install)
- No Docker version check — assume recent enough if exists
- No curl/bash check — if curl | bash ran, they exist
- Clear error message and exit on failure — explain what failed, suggest fix, exit non-zero

### Upgrade path
- Upgrade via re-running `curl | bash` (same as install)
- `aishell --version` shows version, no update check
- VERSION variable hardcoded at top of script
- Print installed version in success message: "Installed aishell vX.Y.Z to ~/.local/bin/aishell"

### Claude's Discretion
- Exact progress message wording
- Color scheme for output
- Whether to suggest PATH fix if ~/.local/bin not in PATH

</decisions>

<specifics>
## Specific Ideas

- Should work on WSL2 (Windows Subsystem for Linux) — research during planning
- Success message format: "Installed aishell vX.Y.Z to ~/.local/bin/aishell"

</specifics>

<deferred>
## Deferred Ideas

- Checksum/signature verification for downloads — add to future security phase
- `aishell upgrade` built-in command — consider for later convenience
- Auto-add ~/.local/bin to PATH in shell rc — research best practices first

</deferred>

---

*Phase: 05-distribution*
*Context gathered: 2026-01-18*
