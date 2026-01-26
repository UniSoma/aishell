# Project State

## Project Reference

See: .planning/PROJECT.md (updated 2026-01-25)

**Core value:** Run agentic AI harnesses in isolated, reproducible environments without polluting the host system.
**Current focus:** v2.5.0 Optimization & Polish

## Current Position

Phase: 29 of 29 (Exec Command)
Plan: 2 of 2
Status: Phase complete
Last activity: 2026-01-26 - Completed 29-02-PLAN.md (exec command documentation)

Progress: [████████████████████] 100%

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

## Accumulated Context

### Decisions

See PROJECT.md Key Decisions tables for full history.

**Recent (Phase 29):**

| ID | Decision | Rationale |
|----|----------|-----------|
| tty-detection-method | Use System/console for TTY auto-detection | Already used in existing codebase (detection/core.clj, output.clj), portable, simple |
| exec-skip-detection | Skip detection warnings for exec command | Fast path for quick commands. Users can run aishell gitleaks separately if needed |
| exec-skip-pre-start | Always skip pre_start hooks for exec | One-off commands shouldn't start sidecars. Only interactive sessions need pre_start |
| exec-always-stdin | Always include -i flag even without TTY | Without -i, piped input fails silently. Required for echo test \| aishell exec cat |
| exec-use-shell-not-exec | Use p/shell with :inherit for exit code propagation | Need to capture exit code. p/exec replaces process |

**Phase 28:**

| ID | Decision | Rationale |
|----|----------|-----------|
| pre-start-list-normalization | Normalize at YAML load time in config.clj | Single point of normalization ensures consistent behavior |
| gitleaks-opt-out-flag | Use --without-gitleaks (opt-out) vs --with-gitleaks (opt-in) | Maintains backwards compatibility |
| with-gitleaks-positive-tracking | Invert flag to :with-gitleaks for positive state tracking | State reflects what is installed, not what is excluded |
| help-discoverability | Show all harnesses when no state file exists | Aids discoverability for new users who haven't built yet |
| gitleaks-always-shown | Always show gitleaks command in help regardless of build state | Gitleaks may be installed on host, and command works via host mounting |
| abandon-binary-install | Abandoned Phase 29 (binary install) due to larger image size | Native Claude binary (213MB) larger than npm package; no net savings |

### Pending Todos

0 pending todos (Phase 29 complete):

1 completed todo (archived with Phase 29):
- [One-off container execution](./todos/pending/2026-01-25-add-shell-command-for-one-off-execution.md) → Completed in 29-01

2 completed todos (archived with Phase 28):
- [Dynamic help](./todos/pending/2026-01-25-dynamic-help-based-on-installed-harnesses.md) → Completed in 28-02
- [Pre-start list format](./todos/pending/2026-01-25-support-list-format-for-pre-start.md) → Completed in 28-01

2 deferred todos:
- [Binary install, conditional Node.js](./todos/pending/2026-01-25-binary-install-claude-code.md) — abandoned (native binary larger than npm)
- [Audit security detection patterns](./todos/pending/2026-01-25-audit-security-detection-patterns.md) — deferred to future milestone

### Blockers/Concerns

None.

## Session Continuity

Last session: 2026-01-26
Stopped at: Completed Phase 29 (Exec Command) - exec command implementation and documentation
Resume file: None

Next step: All planned phases complete - project ready for user testing and feedback
