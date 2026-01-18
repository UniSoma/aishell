---
phase: 05-distribution
plan: 01
subsystem: distribution
tags: [bash, heredoc, curl, installer, xdg]

# Dependency graph
requires:
  - phase: 04-project-customization
    provides: Project extension support with .aishell/Dockerfile
provides:
  - Self-contained aishell script with embedded heredocs
  - curl|bash installer for easy installation
affects: [version-pinning, documentation]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - Heredoc embedding for multi-file distribution
    - Function-wrapped installer for partial download protection
    - XDG-compliant installation to ~/.local/bin

key-files:
  created:
    - install.sh
  modified:
    - aishell

key-decisions:
  - "Embed Dockerfile, entrypoint.sh, bashrc.aishell as heredocs in aishell"
  - "Extract heredocs to temp dir at build time, clean up after"
  - "Install to ~/.local/bin (XDG standard)"
  - "Warn on missing Docker but continue installation"

patterns-established:
  - "Heredoc embedding: Use quoted markers ('EOF') to prevent expansion"
  - "Installer functions: Wrap all code in functions, call main at end"
  - "Color output: Check tput colors and NO_COLOR environment variable"

# Metrics
duration: 3min
completed: 2026-01-18
---

# Phase 5 Plan 1: Distribution Infrastructure Summary

**Self-contained aishell script with embedded heredocs and curl|bash installer for XDG-compliant installation to ~/.local/bin**

## Performance

- **Duration:** 3 min
- **Started:** 2026-01-18T01:07:21Z
- **Completed:** 2026-01-18T01:10:00Z
- **Tasks:** 2
- **Files modified:** 2

## Accomplishments
- Refactored aishell to be fully self-contained with Dockerfile, entrypoint.sh, and bashrc.aishell embedded as heredocs
- Created install.sh with function-wrapped installer, color support, Docker warning, and PATH detection
- Base image now builds from embedded heredocs without requiring separate files

## Task Commits

Each task was committed atomically:

1. **Task 1: Refactor aishell to embed build files as heredocs** - `8f6ab21` (feat)
2. **Task 2: Create install.sh installer script** - `41b031c` (feat)

## Files Created/Modified
- `aishell` - Added write_dockerfile(), write_entrypoint(), write_bashrc() functions; modified ensure_image() and do_update() to use temp dir extraction
- `install.sh` - New curl|bash installer with color support, Docker check, PATH detection

## Decisions Made
- Used quoted heredoc markers ('DOCKERFILE_EOF', 'ENTRYPOINT_EOF', 'BASHRC_EOF') to prevent variable expansion
- Extract heredocs to mktemp directory, build image, clean up via trap
- Install location is ~/.local/bin per XDG Base Directory specification
- Docker check warns but continues installation (user may install Docker later)
- PATH check suggests adding ~/.local/bin if not in PATH

## Deviations from Plan

None - plan executed exactly as written.

## Issues Encountered
None - implementation followed RESEARCH.md patterns closely.

## User Setup Required

None - no external service configuration required. After installation:
1. Run `curl -fsSL https://raw.githubusercontent.com/jonasrodrigues/harness/main/install.sh | bash`
2. Ensure ~/.local/bin is in PATH
3. Run `aishell --version` to verify

## Next Phase Readiness
- Distribution infrastructure complete
- Ready for Phase 6 (Version Pinning) to add version specification for reproducibility
- Existing Dockerfile, entrypoint.sh, bashrc.aishell files can remain for development convenience (not needed at runtime)

---
*Phase: 05-distribution*
*Completed: 2026-01-18*
