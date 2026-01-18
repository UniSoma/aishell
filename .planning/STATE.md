# Project State

## Project Reference

See: .planning/PROJECT.md (updated 2026-01-17)

**Core value:** Run agentic AI harnesses in isolated, reproducible environments without polluting the host system.
**Current focus:** Milestone v1.0 complete

## Current Position

Phase: 8 of 8 (Explicit Build/Update Commands) - COMPLETE
Plan: 3 of 3 in phase 8
Status: Milestone complete
Last activity: 2026-01-18 - Completed Phase 8 execution

Progress: [##########] 100% (8/8 phases complete)

**Milestone:** v1.0 Complete - All requirements delivered

## Performance Metrics

**Velocity:**
- Total plans completed: 16
- Average duration: 4.8 min
- Total execution time: ~1.3 hours

**By Phase:**

| Phase | Plans | Total | Avg/Plan |
|-------|-------|-------|----------|
| 1. Core Container Foundation | 3/3 | 15 min | 5 min |
| 2. Git Integration | 1/1 | 2 min | 2 min |
| 3. Harness Integration | 4/4 | 26 min | 6.5 min |
| 4. Project Customization | 1/1 | 8 min | 8 min |
| 5. Distribution | 1/1 | 3 min | 3 min |
| 6. Final Enhancements | 2/2 | 4 min | 2 min |
| 7. Node.js and Clojure Tooling | 1/1 | 8 min | 8 min |
| 8. Explicit Build/Update Commands | 3/3 | 13 min | 4.3 min |

**Recent Trend:**
- Last 5 plans: 2 min, 8 min, 4 min, 5 min, 4 min
- Trend: Consistent execution times

*Updated after each plan completion*

## Accumulated Context

### Decisions

Decisions are logged in PROJECT.md Key Decisions table.
Recent decisions affecting current work:

- Debian bookworm-slim over Alpine for glibc compatibility
- gosu 1.19 for user switching (proper PID 1 handling)
- Dynamic user creation at runtime for UID/GID matching
- Color output uses tput for detection, respects NO_COLOR and FORCE_COLOR
- Spinner writes to stderr with TTY detection
- Image auto-builds from Dockerfile in script directory when missing
- Git identity read using `git -C` (respects local .git/config overrides)
- Git env vars only passed if BOTH name and email are set (avoid empty override)
- Git safe.directory configured in entrypoint after home dir creation
- Claude Code installer places binary at ~/.local/bin/claude (not ~/.claude/bin)
- OpenCode installer places binary at ~/.opencode/bin/opencode
- DISABLE_AUTOUPDATER=1 for Claude in containers (ephemeral environment)
- Only mount config dirs that exist on host (avoid Docker bind mount errors)
- Only pass API env vars that are set (avoid empty override)
- Build flags (--with-*) trigger rebuild when passed
- Copy harness binaries to /usr/local/bin (not symlink, as /root/ inaccessible after gosu)
- Pass LOCAL_HOME env var to preserve host home path in container
- Create container user with same home directory path as host
- Create XDG directories (~/.local/{state,share,bin}) at container startup
- Hash project path for extended image tag (aishell:ext-{hash12})
- Use docker labels (aishell.base.id) to track base image version for cache invalidation
- Always run docker build for extensions - let Docker handle caching
- Embed Dockerfile, entrypoint.sh, bashrc.aishell as heredocs in aishell
- Extract heredocs to temp dir at build time, clean up after
- Install to ~/.local/bin (XDG standard)
- Warn on missing Docker but continue installation
- Version format: no v-prefix (e.g., 2.0.22 not v2.0.22)
- Tag format: aishell:claude-X.Y.Z for version-specific images
- PROMPT_DIRTRIM=2 for showing last 2 path components in prompt
- Default --dangerously-skip-permissions for Claude (container is sandbox)
- AISHELL_SKIP_PERMISSIONS=false for opt-out
- Pass COLORTERM env var for 24-bit color support in container
- Set LANG=C.UTF-8 in bashrc.aishell for Unicode character rendering
- Multi-stage build from node:24-bookworm-slim for Node.js binaries
- Babashka static binary for container compatibility (no glibc dependencies)
- Removed bbin after user feedback (requires Java runtime)
- XDG_STATE_HOME with fallback to ~/.local/state for state directory
- Shell variable format for state files (not JSON) for native bash support
- Security validation regex before sourcing state files to prevent code injection
- Atomic write pattern (mktemp + mv) to prevent corruption
- Build subcommand always runs docker build (relies on Docker cache)
- Update merges new flags with existing (additive, not replacement)
- Update always uses --no-cache (get latest versions semantic)
- Stop spinner only clears line when spinner was active
- errexit-compatible pattern: `cmd || result=$?`
- Harness verification via BUILD_WITH_* state flags
- Error messages include exact corrective commands

### Pending Todos

None - all todos resolved.

### Roadmap Evolution

- All 8 phases complete - Milestone v1.0 delivered
- 2 todos completed in 06-02: improve-container-shell-prompt, default-dangerously-skip-permissions
- Statusline parity todo resolved: COLORTERM passthrough + C.UTF-8 locale
- Phase 7 added and completed: Node.js and Clojure Tooling (Node.js LTS, Babashka)
- Phase 8 added and completed: Explicit Build/Update Commands (separate build/run, persist flags, update command)

### Blockers/Concerns

None.

## Session Continuity

Last session: 2026-01-18
Stopped at: Milestone v1.0 complete
Resume file: None
