---
phase: 03-harness-integration
plan: 01
subsystem: infra
tags: [docker, dockerfile, claude-code, opencode, conditional-build]

# Dependency graph
requires:
  - phase: 01-core-container
    provides: Base Dockerfile with gosu and entrypoint
provides:
  - Conditional Claude Code installation via WITH_CLAUDE build arg
  - Conditional OpenCode installation via WITH_OPENCODE build arg
  - Harness binaries symlinked to /usr/local/bin for PATH accessibility
affects: [03-02, 03-03, aishell script build flags]

# Tech tracking
tech-stack:
  added: [claude-code, opencode]
  patterns: [conditional-dockerfile-installation, build-arg-feature-flags]

key-files:
  created: []
  modified: [Dockerfile]

key-decisions:
  - "Claude Code installer places binary at ~/.local/bin/claude (updated from research)"
  - "OpenCode installer places binary at ~/.opencode/bin/opencode"
  - "Symlinks created to /usr/local/bin for PATH accessibility"
  - "DISABLE_AUTOUPDATER=1 set for Claude in containers"

patterns-established:
  - "Conditional installation: Use ARG with RUN if [...] pattern for optional features"
  - "Harness symlinks: Install in native location, symlink to /usr/local/bin for consistent PATH"

# Metrics
duration: 4min
completed: 2026-01-17
---

# Phase 3 Plan 1: Harness Installation Summary

**Conditional Claude Code and OpenCode installation in Dockerfile via WITH_CLAUDE and WITH_OPENCODE build args**

## Performance

- **Duration:** 4 min
- **Started:** 2026-01-17T20:02:00Z
- **Completed:** 2026-01-17T20:06:00Z
- **Tasks:** 2
- **Files modified:** 1

## Accomplishments
- Added WITH_CLAUDE and WITH_OPENCODE build arguments to Dockerfile
- Claude Code installation via native installer when WITH_CLAUDE=true
- OpenCode installation via native installer when WITH_OPENCODE=true
- All variants verified: base (no harnesses), claude-only, opencode-only, full (both)

## Task Commits

Each task was committed atomically:

1. **Task 1: Add conditional harness installation to Dockerfile** - `f9869bd` (feat)
2. **Task 1 fix: Correct Claude binary path** - `44ebf9b` (fix)

Task 2 had no file changes (validation only).

## Files Created/Modified
- `Dockerfile` - Added ARG WITH_CLAUDE, ARG WITH_OPENCODE, conditional RUN blocks for harness installation

## Decisions Made
- **Claude Code path updated:** Research indicated ~/.claude/bin/claude but native installer now places binary at ~/.local/bin/claude - updated symlink accordingly
- **OpenCode path confirmed:** Native installer places binary at ~/.opencode/bin/opencode as documented
- **DISABLE_AUTOUPDATER=1:** Set for Claude Code installation since containers are ephemeral

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] Incorrect Claude Code binary path**
- **Found during:** Task 2 (verification)
- **Issue:** Plan specified ~/.claude/bin/claude but native installer places binary at ~/.local/bin/claude (symlink to ~/.local/share/claude/versions/X.X.X)
- **Fix:** Updated Dockerfile symlink from /root/.claude/bin/claude to /root/.local/bin/claude
- **Files modified:** Dockerfile
- **Verification:** `docker run --rm --entrypoint="" aishell-claude claude --version` returns version
- **Committed in:** 44ebf9b (fix commit)

---

**Total deviations:** 1 auto-fixed (1 bug fix)
**Impact on plan:** Essential fix - installation path changed since research was conducted. No scope creep.

## Issues Encountered
None - after path correction, all builds and verifications passed.

## User Setup Required
None - no external service configuration required.

## Next Phase Readiness
- Dockerfile now supports conditional harness installation
- Ready for Plan 03-02: CLI subcommands and config mounting
- Ready for Plan 03-03: Environment variable passthrough

---
*Phase: 03-harness-integration*
*Completed: 2026-01-17*
