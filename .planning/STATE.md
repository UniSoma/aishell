# Project State

## Project Reference

See: .planning/PROJECT.md (updated 2026-01-31)

**Core value:** Run agentic AI harnesses in isolated, reproducible environments without polluting the host system.
**Current focus:** Phase 32 - Detached Mode & Conflict Detection

## Current Position

Phase: 32 of 34 (Detached Mode & Conflict Detection — In Progress)
Plan: 1 of 3 in current phase
Status: In progress
Last activity: 2026-01-31 — Completed 32-01-PLAN.md (tmux auto-start)

Progress: [█████████░] 92% (31 phases complete, 1 of 3 plans in phase 32)

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

**v2.6.0 (In Progress):**
- Plans completed: 3
- Average duration: 1min
- Trend: On track

*Updated after each plan completion*

## Accumulated Context

### Decisions

Decisions are logged in PROJECT.md Key Decisions table.
Recent decisions affecting current work:

- v2.6.0 (32-01): All container modes auto-start inside tmux session 'main' (override of ROADMAP shell mode criterion for consistency)
- v2.6.0 (32-01): gosu runs before tmux in exec chain (ensures user-owned socket, avoids permission errors)
- v2.6.0 (31-01): tmux added to base image package list (single-line change in templates.clj)
- v2.6.0 (30-01): Use 8-char SHA-256 hash for container names (2^32 space, <0.02% collision at 100 projects)
- v2.6.0 (30-01): Default container name equals harness name (claude/opencode/codex/gemini) or 'shell'
- v2.6.0 (30-01): Extract --name flag before dispatch (consistent with --unsafe pattern)
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
- Signal handling validation needed - verify existing --init flag (tini as PID 1) handles SIGTERM correctly with tmux. If `docker stop` takes >3s and exits with code 137, trap handlers may be needed.
- --rm + --name conflict - pre-flight collision check required to handle stopped containers with duplicate names.

## Session Continuity

Last session: 2026-01-31
Stopped at: Completed 32-01-PLAN.md (tmux auto-start)
Resume file: None

Next step: Continue with 32-02 (attach command) or 32-03 (conflict detection)
