# Project State

## Project Reference

See: .planning/PROJECT.md (updated 2026-02-01)

**Core value:** Run agentic AI harnesses in isolated, reproducible environments without polluting the host system.

**Current focus:** Phase 42 - Resurrect State Persistence

## Current Position

Phase: 42 of 43 (Resurrect State Persistence) — COMPLETE
Plan: 2 of 2 complete
Status: Phase complete - resurrect feature fully operational
Last activity: 2026-02-02 — Completed 42-02 plugin injection and auto-restore

Progress: [████████░░] 73% (of v2.9.0 milestone, phase 42 complete)

**Shipped Milestones:**
- v1.0 MVP - Phases 1-8 (shipped 2026-01-18)
- v1.1 Runtime Config - Phases 9-10 (shipped 2026-01-19)
- v1.2 Hardening - Phases 11-12 (shipped 2026-01-19)
- v2.0 Babashka Rewrite - Phases 13-18 (shipped 2026-01-21)
- v2.3.0 Safe AI Context Protection - Phases 18.1-23 (shipped 2026-01-24)
- v2.4.0 Multi-Harness Support - Phases 24-27 (shipped 2026-01-25)
- v2.5.0 Optimization & Polish - Phases 28-29 (shipped 2026-01-26)
- v2.7.0 tmux Integration & Named Containers - Phases 30-34 (shipped 2026-01-31)
- v2.8.0 Decouple Harness Tools - Phases 35-38 (shipped 2026-02-01)

## Performance Metrics

**Velocity:**
- Total plans completed: 100 (90 in v1.0-v2.8.0 + 10 in v2.9.0)
- v2.9.0 tracking: 10 plans completed (39-01, 39-02, 40-01, 40-02, 40-03, 40-04, 41-01, 42-01, 42-02)

**Recent Trend:**
- v2.9.0: 10 plans completed (avg 1.5min duration)
- v2.8.0: 14 plans across 4 phases
- Trend: Stable execution velocity with quick gap closures

## Accumulated Context

### Decisions

Decisions are logged in PROJECT.md Key Decisions table.
Recent decisions affecting current work:

- v2.9.0: Auto-inject tmux-resurrect plugin when resurrect enabled (no manual declaration needed)
- v2.9.0: Deduplicate resurrect plugin if user already declared it (silent, no warnings)
- v2.9.0: Process restoration defaults to 'false' for safety, explicit restore_processes: true enables ':all:' mode
- v2.9.0: Auto-restore runs via run-shell after TPM initialization in config file
- v2.9.0: Pass resurrect config as RESURRECT_ENABLED and RESURRECT_RESTORE_PROCESSES env vars to entrypoint
- v2.9.0: resurrect: true -> {:enabled true :restore_processes false} (sensible default)
- v2.9.0: resurrect state directory at ~/.aishell/resurrect/{project-hash}/ for per-project isolation
- v2.9.0: resurrect config silently ignored when tmux disabled (no warnings)
- v2.9.0: Mount ~/.tmux.conf read-only to prevent container modification
- v2.9.0: Auto-mount skipped if user has explicit .tmux.conf in config mounts
- v2.9.0: :with-tmux defaults to FALSE (opt-in, not opt-out per TMUX-01 requirements)
- v2.9.0: tmux config uses scalar merge (project replaces global)
- v2.9.0: Plugin format validation uses warning-only approach (consistent with existing validation framework)
- v2.9.0: GitHub owner/repo regex pattern for plugin validation
- v2.9.0: TPM plugin declarations written to ~/.tmux.conf for install_plugins AWK parser
- v2.9.0: TPM installs to /tools/tmux/plugins with TMUX_PLUGIN_MANAGER_PATH override
- v2.9.0: Config threading to volume population uses optional :config key for backward compatibility
- v2.9.0: Volume hash includes tmux state (:with-tmux flag and sorted plugin list)
- v2.9.0: Idempotent git clone uses pull-if-exists pattern (safer than rm -rf)
- v2.9.0: Build/update guards check :with-tmux to trigger volume population for tmux-only configs
- v2.9.0: WITH_TMUX passed as env var instead of mounting state.edn (simpler and consistent with existing pattern)
- v2.9.0: Session name changed from "main" to "harness" (project naming consistency)
- v2.9.0: Runtime config at ~/.tmux.conf.runtime for injected TPM initialization (more discoverable than /tmp)
- v2.8.0: Volume-based harness tool injection decouples tools from foundation image
- v2.8.0: Foundation image (aishell:foundation) replaces base image with clean break
- v2.8.0: Content-hash volume naming enables cross-project sharing
- v2.7.0: Named containers with project hash isolation

### Pending Todos

2 deferred todos:
- [Audit security detection patterns](./todos/pending/2026-01-25-audit-security-detection-patterns.md) — deferred to future milestone
- [Consider Podman support as alternative container runtime](./todos/pending/2026-02-02-consider-podman-support.md) — from Distrobox investigation

### Blockers/Concerns

None.

## Session Continuity

Last session: 2026-02-02
Stopped at: Completed 42-02 plugin injection and auto-restore
Resume file: None

**Next step:** Phase 42 complete. v2.9.0 milestone ready for testing.

**Recent completions:**
- 42-02: Resurrect plugin injection and auto-restore - 2min
- 42-01: Resurrect config parsing and state directory mounting - 1min
- 41-01: TPM initialization in entrypoint with conditional startup - 1.4min
- 40-04: Gap closure fixing TPM plugin installation path - <1min
