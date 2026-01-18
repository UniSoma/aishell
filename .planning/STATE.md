# Project State

## Project Reference

See: .planning/PROJECT.md (updated 2026-01-17)

**Core value:** Run agentic AI harnesses in isolated, reproducible environments without polluting the host system.
**Current focus:** Phase 5 - Distribution

## Current Position

Phase: 4 of 6 (Project Customization) - COMPLETE
Plan: 1 of 1 in phase 4
Status: Phase 4 complete, ready for Phase 5
Last activity: 2026-01-18 - Completed 04-01-PLAN.md (project extension support)

Progress: [██████....] 60%

## Performance Metrics

**Velocity:**
- Total plans completed: 9
- Average duration: 5.7 min
- Total execution time: 0.85 hours

**By Phase:**

| Phase | Plans | Total | Avg/Plan |
|-------|-------|-------|----------|
| 1. Core Container Foundation | 3/3 | 15 min | 5 min |
| 2. Git Integration | 1/1 | 2 min | 2 min |
| 3. Harness Integration | 4/4 | 26 min | 6.5 min |
| 4. Project Customization | 1/1 | 8 min | 8 min |
| 5. Distribution | 0/1 | - | - |
| 6. Version Pinning | 0/1 | - | - |

**Recent Trend:**
- Last 5 plans: 4 min, 5 min, 15 min, 2 min, 8 min
- Trend: Phase 4 complete with project extension support

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

### Pending Todos

1 pending todo:
- `improve-container-shell-prompt` - Shorten the PS1 prompt in bashrc.aishell (ui)

### Roadmap Evolution

- Phase 6 added: Version Pinning (specify exact harness versions for reproducibility)

### Blockers/Concerns

None.

## Session Continuity

Last session: 2026-01-18
Stopped at: Completed 04-01-PLAN.md (project extension support)
Resume file: None
