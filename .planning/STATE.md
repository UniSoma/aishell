# Project State

## Project Reference

See: .planning/PROJECT.md (updated 2026-02-18)

**Core value:** Run agentic AI harnesses in isolated, reproducible environments without polluting the host system
**Current focus:** v3.7.0 OpenSpec Support - Phase 63

## Current Position

Phase: 63 of 65 (Core OpenSpec Integration)
Plan: 0 of ? in current phase
Status: Ready to plan
Last activity: 2026-02-18 — Roadmap created for v3.7.0

Progress: [░░░░░░░░░░] 0%

## Performance Metrics

**Velocity:**
- Total plans completed: 137 across 15 milestones
- Milestones shipped: 15 (v1.0 -> v3.6.0)

**Recent Trend:**
- v3.5.0: 5 plans, 1 day
- v3.1.0: 9 plans, 2 days
- v3.0.0: 9 plans, 1 day
- v2.10.0: 4 plans, 1 day
- Trend: Stable velocity

*Updated after each plan completion*

## Accumulated Context

### Decisions

See PROJECT.md for full decision log across all milestones.

Recent decisions for v3.7.0:
- OpenSpec is NOT a harness (no `aishell openspec` subcommand, no config mounts, no API key passthrough)
- Follows `--with-*` / harness volume npm install pattern for build integration
- Available inside container only via `openspec` command

### Pending Todos

3 deferred todos:
- [Consider Podman support as alternative container runtime](./todos/pending/2026-02-02-consider-podman-support.md) - from Distrobox investigation
- [Detect stale foundation image](./todos/pending/2026-02-02-detect-stale-foundation-image.md) - warn when foundation image is outdated
- [Integrate Claude Code, aishell and Emacs](./todos/pending/2026-02-14-integrate-claude-code-aishell-and-emacs.md) - editor plugin integration with containerized Claude Code

### Blockers/Concerns

None.

### Quick Tasks Completed

| # | Description | Date | Commit | Directory |
|---|-------------|------|--------|-----------|
| 6 | Implement auto-install-bb in install.sh + PowerShell equivalent | 2026-02-12 | e2dd0eb | [6-implement-auto-install-bb-in-install-sh-](./quick/6-implement-auto-install-bb-in-install-sh-/) |

## Session Continuity

Last session: 2026-02-18
Stopped at: Phase 63 context gathered
Resume file: .planning/phases/63-core-openspec-integration/63-CONTEXT.md
