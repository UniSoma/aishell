# Project State

## Project Reference

See: .planning/PROJECT.md (updated 2026-01-31)

**Core value:** Run agentic AI harnesses in isolated, reproducible environments without polluting the host system.

**Current focus:** Phase 35 - Foundation Image Split

## Current Position

Phase: 35 of 38 (Foundation Image Split)
Plan: Ready to plan first plan in phase
Status: Ready to plan
Last activity: 2026-01-31 - Roadmap created for v2.8.0 milestone

Progress: [░░░░░░░░░░] 0% (0/11 plans complete)

**Shipped Milestones:**
- v1.0 MVP - Phases 1-8 (shipped 2026-01-18)
- v1.1 Runtime Config - Phases 9-10 (shipped 2026-01-19)
- v1.2 Hardening - Phases 11-12 (shipped 2026-01-19)
- v2.0 Babashka Rewrite - Phases 13-18 (shipped 2026-01-21)
- v2.3.0 Safe AI Context Protection - Phases 18.1-23 (shipped 2026-01-24)
- v2.4.0 Multi-Harness Support - Phases 24-27 (shipped 2026-01-25)
- v2.5.0 Optimization & Polish - Phases 28-29 (shipped 2026-01-26)
- v2.7.0 tmux Integration & Named Containers - Phases 30-34 (shipped 2026-01-31)

## Performance Metrics

**Velocity (v2.8.0):**
- Total plans completed: 0
- Average duration: N/A (no plans completed yet)
- Total execution time: 0 hours

**By Phase:**

| Phase | Plans | Total | Avg/Plan |
|-------|-------|-------|----------|
| 35 | 0/2 | 0 min | - |
| 36 | 0/3 | 0 min | - |
| 37 | 0/4 | 0 min | - |
| 38 | 0/2 | 0 min | - |

**Recent Trend:**
- No plans completed yet
- Trend: N/A

**Cumulative (v1.0-v2.7.0):**
- Total plans: 80
- Completed plans: 80
- Milestones: 8 (all shipped)
- Days: 14 (2026-01-17 -> 2026-01-31)

*Updated after each plan completion*

## Accumulated Context

### Decisions

Decisions are logged in PROJECT.md Key Decisions table.

Recent decisions affecting v2.8.0 work:

**Architecture (from investigation):**
- 2-tier architecture: Foundation image + harness volume (not 3 images)
- Volume-based injection over layer inversion (simpler for local dev workflow)
- Clean break from `aishell:base` to `aishell:foundation` (no alias)
- Per-project volumes keyed by harness combination hash
- Lazy volume population on first container run

**Key files for implementation:**
- src/aishell/docker/templates.clj - Dockerfile templates
- src/aishell/docker/build.clj - Build orchestration
- src/aishell/docker/extension.clj - Extension cache invalidation
- src/aishell/docker/run.clj - Docker run arguments, volume mounts
- src/aishell/run.clj - Run command orchestration
- src/aishell/state.clj - State persistence (EDN)

### Pending Todos

3 deferred todos:
- [Binary install, conditional Node.js](./todos/pending/2026-01-25-binary-install-claude-code.md) — abandoned (native binary larger than npm)
- [Audit security detection patterns](./todos/pending/2026-01-25-audit-security-detection-patterns.md) — deferred to future milestone
- [Make tmux opt-in with --with-tmux flag](./todos/pending/2026-01-31-make-tmux-opt-in-with-flag.md) — opt-in tmux via `aishell build --with-tmux`

### Blockers/Concerns

None yet.

## Session Continuity

Last session: 2026-01-31 (roadmap creation)
Stopped at: Roadmap and state initialization complete
Resume file: None

**Next step:** `/gsd:plan-phase 35` to create execution plans for foundation image split
