# Project State

## Project Reference

See: .planning/PROJECT.md (updated 2026-01-31)

**Core value:** Run agentic AI harnesses in isolated, reproducible environments without polluting the host system.
**Current focus:** Phase 34 - PS Command & Polish

## Current Position

Phase: 34 of 34 (PS Command & Polish — In Progress)
Plan: 1 of 1 in current phase
Status: Plan 34-01 complete
Last activity: 2026-01-31 — Completed 34-01-PLAN.md

Progress: [██████████] 100% (34 phases complete out of 34 total)

**Shipped Milestones:**
- v1.0 MVP - Phases 1-8 (shipped 2026-01-18)
- v1.1 Runtime Config - Phases 9-10 (shipped 2026-01-19)
- v1.2 Hardening - Phases 11-12 (shipped 2026-01-19)
- v2.0 Babashka Rewrite - Phases 13-18 (shipped 2026-01-21)
- v2.3.0 Safe AI Context Protection - Phases 18.1-23 (shipped 2026-01-24)
- v2.4.0 Multi-Harness Support - Phases 24-27 (shipped 2026-01-25)
- v2.5.0 Optimization & Polish - Phases 28-29 (shipped 2026-01-26)

## Performance Metrics

**Cumulative (v1.0-v2.5.0):**
- Total plans: 73
- Completed plans: 73
- Milestones: 7 (all shipped)
- Days: 10 (2026-01-17 -> 2026-01-26)

**v2.6.0 (Complete):**
- Plans completed: 7 (30-01, 31-01, 32-01, 32-02, 32-UAT, 32-03, 33-01, 34-01)
- Average duration: 2min
- Trend: Complete - all phases shipped

*Updated after each plan completion*

## Accumulated Context

### Decisions

Decisions are logged in PROJECT.md Key Decisions table.
Recent decisions affecting current work:

- v2.6.0 (34-01): Use clojure.pprint/print-table for readable table output (standard library, no dependencies)
- v2.6.0 (34-01): Extract short names by splitting on hyphen with limit 3 (handles user names with hyphens)
- v2.6.0 (34-01): Use Docker timestamps as-is (no relative time formatting for simplicity)
- v2.6.0 (33-01): Only extract --name flag for harness commands (attach parses its own --name flag)
- v2.6.0 (33-01): Use p/exec for terminal takeover in attach (not p/shell, provides full TTY control)
- v2.6.0 (33-01): Show attach help only on --help flag (not on empty args, consistent with error-first pattern)
- v2.6.0 (32-03): Use infocmp for TERM validation rather than installing additional terminfo packages (defensive check, avoids image bloat)
- v2.6.0 (32-03): Fallback to xterm-256color for unsupported TERM values (universally available in Debian, preserves color support)
- v2.6.0 (32-03): Position TERM validation after gosu setup, before tmux exec in entrypoint lifecycle
- v2.6.0 (32-02): -d short form safe for --detach (no conflicts with harness flags)
- v2.6.0 (32-02): All containers get --name flag (unified naming across foreground/detached modes)
- v2.6.0 (32-02): --rm + --detach valid in modern Docker (auto-cleanup when stopped)
- v2.6.0 (32-01): All container modes auto-start inside tmux session 'main' (override of ROADMAP shell mode criterion for consistency)
- v2.6.0 (32-01): gosu runs before tmux in exec chain (ensures user-owned socket, avoids permission errors)
- v2.6.0 (31-01): tmux added to base image package list (single-line change in templates.clj)
- v2.6.0 (30-01): Use 8-char SHA-256 hash for container names (2^32 space, <0.02% collision at 100 projects)
- v2.6.0 (30-01): Default container name equals harness name (claude/opencode/codex/gemini) or 'shell'
- v2.5.0: Binary install approach abandoned (native Claude binary 213MB larger than npm)
- v2.3.0: Content detection delegated to Gitleaks (better coverage than custom patterns)
- v2.0: Babashka over Bash (cross-platform, better data structures, YAML native)

### Pending Todos

2 deferred todos:
- [Binary install, conditional Node.js](./todos/pending/2026-01-25-binary-install-claude-code.md) — abandoned (native binary larger than npm)
- [Audit security detection patterns](./todos/pending/2026-01-25-audit-security-detection-patterns.md) — deferred to future milestone

### Blockers/Concerns

**Phase 32 (Detached Mode):**
- ✅ Socket permissions - RESOLVED: gosu now runs before tmux in exec chain (32-01)
- ✅ --rm + --name conflict - RESOLVED: pre-flight conflict detection via ensure-name-available! (32-02)
- ✅ TERM validation - RESOLVED: entrypoint validates TERM before tmux, fallbacks to xterm-256color (32-03)
- Signal handling validation needed - verify existing --init flag (tini as PID 1) handles SIGTERM correctly with tmux. If `docker stop` takes >3s and exits with code 137, trap handlers may be needed.

## Session Continuity

Last session: 2026-01-31T18:36:20Z
Stopped at: Phase 34 Plan 01 complete (ps command implemented)
Resume file: None

Next step: v2.6.0 complete - ready for release/documentation
