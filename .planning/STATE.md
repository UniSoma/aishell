# Project State

## Project Reference

See: .planning/PROJECT.md (updated 2026-01-22)

**Core value:** Run agentic AI harnesses in isolated, reproducible environments without polluting the host system.
**Current focus:** Phase 20 - Filename-based Detection

## Current Position

Phase: 20 (Filename-based Detection)
Plan: 2 of 2 in current phase
Status: Phase complete
Last activity: 2026-01-23 — Completed 20-02-PLAN.md

Progress: [####################] 100% v2.0 | [████░░░░░░] 36% v2.1 (4/11 plans)

**Shipped Milestones:**
- v1.0 MVP — Phases 1-8 (shipped 2026-01-18)
- v1.1 Runtime Config — Phases 9-10 (shipped 2026-01-19)
- v1.2 Hardening — Phases 11-12 (shipped 2026-01-19)
- v2.0 Babashka Rewrite — Phases 13-18 (shipped 2026-01-21)

## Performance Metrics

**v2.1 Velocity:**
- Total plans completed: 4
- Average duration: 2.5 min
- Total execution time: 10 min

**By Phase:**

| Phase | Plans | Total | Avg/Plan |
|-------|-------|-------|----------|
| 18.1-default-harness-args | 1/1 | 2min | 2.0min |
| 19-core-framework | 1/1 | 4min | 4.0min |
| 20-filename-detection | 2/2 | 4min | 2.0min |
| 21-content-detection | 0/2 | - | - |
| 22-extended-patterns | 0/3 | - | - |
| 23-context-config | 0/2 | - | - |

**v2.0 Reference (for comparison):**
- Total plans completed: 22
- Average duration: 2.4 min
- Total execution time: 52.9 min

## Accumulated Context

### Decisions

See PROJECT.md Key Decisions table.
Recent decisions affecting current work:

- [20-02]: SSH keys (id_rsa, id_dsa, id_ed25519, id_ecdsa, *.ppk) classified as high severity
- [20-02]: Key containers (*.p12, *.pfx, *.jks, *.keystore) classified as high severity
- [20-02]: PEM/key files (*.pem, *.key) classified as medium severity (may be certificates or keys)
- [20-01]: Threshold-of-3 grouping: show files individually if ≤3, summarize with count if >3
- [20-01]: Case-insensitive matching via clojure.string/lower-case post-filtering
- [20-01]: Template detection: .env files with 'example' or 'sample' in name are low-severity
- [20-01]: Summary format shows count + 2 sample filenames for context
- [19-01]: Use defmulti for format-finding to enable extension in later phases
- [19-01]: High-severity requires y/n in interactive mode, exit 1 in non-interactive
- [19-01]: Medium/low severity auto-proceeds in all modes
- [19-01]: Detection hook placed after warn-dangerous-mounts, before docker-args
- [18.1-01]: Global defaults prepend to project defaults (concatenate lists per harness)
- [18.1-01]: String values auto-normalize to single-element lists for better DX

### Pending Todos

0 pending todos in `.planning/todos/pending/`

### Roadmap Evolution

- Phase 18.1 inserted after Phase 18: Default harness arguments in config (URGENT) — from pending todo

### Blockers/Concerns

None.

### Quick Tasks Completed

| # | Description | Date | Commit | Directory |
|---|-------------|------|--------|-----------|
| 004 | Add extends key for config merge strategy | 2026-01-22 | ebd6ff7 | [004-add-extends-key-for-config-merge-strateg](./quick/004-add-extends-key-for-config-merge-strateg/) |

## Session Continuity

Last session: 2026-01-23
Stopped at: Completed 20-02-PLAN.md (SSH key and cryptographic file detection) - Phase 20 complete
Resume file: None
