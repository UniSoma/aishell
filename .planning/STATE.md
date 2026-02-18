# Project State

## Project Reference

See: .planning/PROJECT.md (updated 2026-02-18)

**Core value:** Run agentic AI harnesses in isolated, reproducible environments without polluting the host system
**Current focus:** Global Base Image Customization - Phase 66

## Current Position

Phase: 66 of 66 (Global Base Image Customization)
Plan: 2 of 4 in current phase
Status: Executing phase 66
Last activity: 2026-02-18 — Completed 66-01 (Core Base Image Module)

Progress: [██░░░░░░░░] 25%

## Performance Metrics

**Velocity:**
- Total plans completed: 138 across 16 milestones
- Milestones shipped: 16 (v1.0 -> v3.7.0)

**Recent Trend:**
- v3.7.0: 5 plans, 1 day
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
- OpenSpec follows exact Pi pattern: harness-keys + harness-npm-packages registration, no dispatch entry
- Non-harness npm tools use same harness-keys/harness-npm-packages pattern but skip dispatch table
- [Phase 63]: OpenSpec follows exact Pi pattern for registration but is NOT added to dispatch table
- [Phase 63]: OpenSpec added to volume trigger only (not verify-harness-available, not docker/run.clj)
- [Phase 63]: OpenSpec placed after Pi, before Gitleaks in check display ordering
- [Phase 64]: OpenSpec documented as non-harness tool across ARCHITECTURE, TROUBLESHOOTING, and DEVELOPMENT docs
- [Phase 64]: No data mounts added for OpenSpec (no config directory)
- [Phase 64]: OpenSpec documented under 'Additional Tools' section in HARNESSES.md, not as a harness
- [Phase 64]: All user-facing doc files version-bumped to v3.7.0
- [Phase 65]: No link references added to CHANGELOG (project convention)

Recent decisions for Phase 66:
- [Phase 66]: Base image uses label-based staleness detection (same pattern as extension images)
- [Phase 66]: Tag-alias path (docker tag) used when no global Dockerfile exists
- [Phase 66]: Hard-stop on build failure via output/error (user explicitly created Dockerfile)

### Pending Todos

3 deferred todos:
- [Consider Podman support as alternative container runtime](./todos/pending/2026-02-02-consider-podman-support.md) - from Distrobox investigation
- [Detect stale foundation image](./todos/pending/2026-02-02-detect-stale-foundation-image.md) - warn when foundation image is outdated
- [Integrate Claude Code, aishell and Emacs](./todos/pending/2026-02-14-integrate-claude-code-aishell-and-emacs.md) - editor plugin integration with containerized Claude Code

### Roadmap Evolution

- Phase 66 added: Global base image customization

### Blockers/Concerns

None.

### Quick Tasks Completed

| # | Description | Date | Commit | Directory |
|---|-------------|------|--------|-----------|
| 6 | Implement auto-install-bb in install.sh + PowerShell equivalent | 2026-02-12 | e2dd0eb | [6-implement-auto-install-bb-in-install-sh-](./quick/6-implement-auto-install-bb-in-install-sh-/) |
| Phase 63 P01 | 2min | 2 tasks | 3 files |
| Phase 63 P02 | 1min | 2 tasks | 2 files |
| Phase 64 P01 | 2min | 2 tasks | 3 files |
| Phase 64 P02 | 1min | 2 tasks | 3 files |
| Phase 65 P01 | 1min | 2 tasks | 2 files |
| Phase 66 P01 | 2min | 2 tasks | 2 files |

## Session Continuity

Last session: 2026-02-18
Stopped at: Completed 66-01-PLAN.md
Resume file: .planning/phases/66-global-base-image-customization/66-01-SUMMARY.md
