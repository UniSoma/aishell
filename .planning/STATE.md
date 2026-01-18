# Project State

## Project Reference

See: .planning/PROJECT.md (updated 2026-01-17)

**Core value:** Run agentic AI harnesses in isolated, reproducible environments without polluting the host system.
**Current focus:** Phase 6 - Version Pinning

## Current Position

Phase: 5 of 6 (Distribution) - COMPLETE
Plan: 1 of 1 in phase 5
Status: Phase 5 complete, ready for Phase 6
Last activity: 2026-01-18 - Completed 05-01-PLAN.md (distribution infrastructure)

Progress: [████████..] 80%

## Performance Metrics

**Velocity:**
- Total plans completed: 10
- Average duration: 5.4 min
- Total execution time: 0.9 hours

**By Phase:**

| Phase | Plans | Total | Avg/Plan |
|-------|-------|-------|----------|
| 1. Core Container Foundation | 3/3 | 15 min | 5 min |
| 2. Git Integration | 1/1 | 2 min | 2 min |
| 3. Harness Integration | 4/4 | 26 min | 6.5 min |
| 4. Project Customization | 1/1 | 8 min | 8 min |
| 5. Distribution | 1/1 | 3 min | 3 min |
| 6. Version Pinning | 0/1 | - | - |

**Recent Trend:**
- Last 5 plans: 5 min, 15 min, 2 min, 8 min, 3 min
- Trend: Phase 5 complete with curl|bash installer

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

### Pending Todos

3 pending todos:
- `improve-container-shell-prompt` - Shorten the PS1 prompt in bashrc.aishell (ui)
- `default-dangerously-skip-permissions` - Default to --dangerously-skip-permissions for Claude (tooling)
- `claude-statusline-host-container-parity` - Investigate Claude statusline difference between host and container (tooling)

### Roadmap Evolution

- Phase 6 added: Version Pinning (specify exact harness versions for reproducibility)

### Blockers/Concerns

None.

## Session Continuity

Last session: 2026-01-18
Stopped at: Completed 05-01-PLAN.md (distribution infrastructure)
Resume file: None
