# Project State

## Project Reference

See: .planning/PROJECT.md (updated 2026-01-25)

**Core value:** Run agentic AI harnesses in isolated, reproducible environments without polluting the host system.
**Current focus:** v2.5.0 Optimization & Polish

## Current Position

Phase: 28 of 30 (Dynamic Help & Config Improvements)
Plan: 02 of 02
Status: Phase complete
Last activity: 2026-01-25 - Completed 28-02-PLAN.md

Progress: [██░░░░░░░░░░░░░░░░░░] 3.0%

**Shipped Milestones:**
- v1.0 MVP - Phases 1-8 (shipped 2026-01-18)
- v1.1 Runtime Config - Phases 9-10 (shipped 2026-01-19)
- v1.2 Hardening - Phases 11-12 (shipped 2026-01-19)
- v2.0 Babashka Rewrite - Phases 13-18 (shipped 2026-01-21)
- v2.3.0 Safe AI Context Protection - Phases 18.1-23 (shipped 2026-01-24)
- v2.4.0 Multi-Harness Support - Phases 24-27 (shipped 2026-01-25)

## Performance Metrics

**Cumulative (v1.0-v2.4.0):**
- Total plans: 70
- Completed plans: 70
- Milestones: 6 (all shipped)
- Days: 9 (2026-01-17 -> 2026-01-25)

## Accumulated Context

### Decisions

See PROJECT.md Key Decisions tables for full history.

**Recent (Phase 28):**

| ID | Decision | Rationale |
|----|----------|-----------|
| pre-start-list-normalization | Normalize at YAML load time in config.clj | Single point of normalization ensures consistent behavior |
| gitleaks-opt-out-flag | Use --without-gitleaks (opt-out) vs --with-gitleaks (opt-in) | Maintains backwards compatibility |
| with-gitleaks-positive-tracking | Invert flag to :with-gitleaks for positive state tracking | State reflects what is installed, not what is excluded |
| help-discoverability | Show all harnesses when no state file exists | Aids discoverability for new users who haven't built yet |
| gitleaks-always-shown | Always show gitleaks command in help regardless of build state | Gitleaks may be installed on host, and command works via host mounting |

### Pending Todos

2 pending todos mapped to phases (archive when phase completes):

| Todo | Phase | Status |
|------|-------|--------|
| [Binary install, conditional Node.js](./todos/pending/2026-01-25-binary-install-claude-code.md) | 29 | Pending |
| [One-off container execution](./todos/pending/2026-01-25-add-shell-command-for-one-off-execution.md) | 30 | Pending |

2 completed todos (archived with Phase 28):
- [Dynamic help](./todos/pending/2026-01-25-dynamic-help-based-on-installed-harnesses.md) → Completed in 28-02
- [Pre-start list format](./todos/pending/2026-01-25-support-list-format-for-pre-start.md) → Completed in 28-01

1 deferred todo:
- [Audit security detection patterns](./todos/pending/2026-01-25-audit-security-detection-patterns.md) — deferred to future milestone

### Blockers/Concerns

None.

## Session Continuity

Last session: 2026-01-25
Stopped at: Completed Phase 28 (Dynamic Help & Config Improvements)
Resume file: None

Next step: Plan Phase 29 (Binary Claude Code installation with conditional Node.js)
