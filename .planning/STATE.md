# Project State

## Project Reference

See: .planning/PROJECT.md (updated 2026-01-17)

**Core value:** Run agentic AI harnesses in isolated, reproducible environments without polluting the host system.
**Current focus:** Phase 3 - Harness Integration

## Current Position

Phase: 3 of 5 (Harness Integration)
Plan: 1 of 3 in phase 3
Status: In progress
Last activity: 2026-01-17 - Completed 03-01-PLAN.md

Progress: [█████.....] 50%

## Performance Metrics

**Velocity:**
- Total plans completed: 5
- Average duration: 4 min
- Total execution time: 0.35 hours

**By Phase:**

| Phase | Plans | Total | Avg/Plan |
|-------|-------|-------|----------|
| 1. Core Container Foundation | 3/3 | 15 min | 5 min |
| 2. Git Integration | 1/1 | 2 min | 2 min |
| 3. Harness Integration | 1/3 | 4 min | 4 min |
| 4. Project Customization | 0/1 | - | - |
| 5. Distribution | 0/1 | - | - |

**Recent Trend:**
- Last 5 plans: 8 min, 2 min, 2 min, 4 min
- Trend: Stable

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

### Pending Todos

1 pending todo:
- `improve-container-shell-prompt` - Shorten the PS1 prompt in bashrc.aishell (ui)

### Blockers/Concerns

None.

## Session Continuity

Last session: 2026-01-17
Stopped at: Completed 03-01-PLAN.md (Harness Installation)
Resume file: None
