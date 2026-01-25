# Roadmap: Agentic Harness Sandbox

## Milestones

- v1.0 MVP - Phases 1-8 (shipped 2026-01-18)
- v1.1 Runtime Config - Phases 9-10 (shipped 2026-01-19)
- v1.2 Hardening - Phases 11-12 (shipped 2026-01-19)
- v2.0 Babashka Rewrite - Phases 13-18 (shipped 2026-01-21)
- v2.3.0 Safe AI Context Protection - Phases 18.1-23 (shipped 2026-01-24)
- **v2.4.0 Multi-Harness Support** - Phases 24-27 (in progress)

## Phases

<details>
<summary>v1.0-v2.3.0 (Phases 1-23) - SHIPPED</summary>

See `.planning/milestones/` for completed milestone details:
- v1.0-ROADMAP.md
- v1.1-ROADMAP.md
- v1.2-ROADMAP.md
- v2.0-ROADMAP.md
- v2.3-ROADMAP.md

</details>

## v2.4.0 Multi-Harness Support (In Progress)

**Milestone Goal:** Add OpenAI Codex CLI and Google Gemini CLI support to aishell, enabling users to run multiple AI coding harnesses in the same isolated sandbox environment.

### Phase 24: Dockerfile & Build Infrastructure
**Goal**: Extend Docker image and build system to support Codex and Gemini installation
**Depends on**: Phase 23 (v2.3.0 complete)
**Requirements**: CODEX-01, CODEX-02, GEMINI-01, GEMINI-02, BUILD-01, BUILD-02, BUILD-03
**Success Criteria** (what must be TRUE):
  1. User can run `aishell build --with-codex` to build image with Codex CLI installed
  2. User can run `aishell build --with-gemini` to build image with Gemini CLI installed
  3. User can pin versions with `--with-codex=1.2.3` and `--with-gemini=0.5.0`
  4. Build state tracks which harnesses are installed and their versions
  5. Build summary shows Codex and Gemini versions when installed
**Plans**: 2 plans

Plans:
- [x] 24-01-PLAN.md — Dockerfile template + CLI build flags for Codex/Gemini
- [x] 24-02-PLAN.md — Build integration, version detection, and state tracking

### Phase 25: CLI & Runtime
**Goal**: Enable running Codex and Gemini from aishell with proper config mounting and environment setup
**Depends on**: Phase 24
**Requirements**: CODEX-03, CODEX-04, CODEX-05, CODEX-06, GEMINI-03, GEMINI-04, GEMINI-05, GEMINI-06, GEMINI-07
**Success Criteria** (what must be TRUE):
  1. User can run `aishell codex [args]` and arguments pass through correctly
  2. User can run `aishell gemini [args]` and arguments pass through correctly
  3. Codex config directory (~/.codex/) is mounted and accessible in container
  4. Gemini config directory (~/.gemini/) is mounted and accessible in container
  5. CODEX_API_KEY environment variable is passed through to container
  6. GEMINI_API_KEY and GOOGLE_API_KEY environment variables are passed through
  7. GOOGLE_APPLICATION_CREDENTIALS is passed through for Vertex AI authentication
  8. User can configure default args via config.yaml harness_args.codex and harness_args.gemini
**Plans**: 2 plans

Plans:
- [x] 25-01-PLAN.md — Docker runtime setup (config mounts, env vars, GCP credentials)
- [x] 25-02-PLAN.md — CLI dispatch and harness integration (routing, verification, help)

### Phase 26: Documentation
**Goal**: Document new harness commands, authentication methods, and environment variables
**Depends on**: Phase 25
**Requirements**: DOCS-01, DOCS-02, DOCS-03
**Success Criteria** (what must be TRUE):
  1. README shows `aishell codex` and `aishell gemini` in usage examples
  2. README documents authentication methods (API key preferred for containers, OAuth on host)
  3. README lists all environment variables for each harness (CODEX_API_KEY, GEMINI_API_KEY, GOOGLE_API_KEY, GOOGLE_APPLICATION_CREDENTIALS)
**Plans**: TBD

Plans:
- [ ] TBD

### Phase 27: Comprehensive Documentation
**Goal**: Create in-depth documentation covering architecture, configuration, all harnesses, troubleshooting, and development guide
**Depends on**: Phase 26
**Requirements**: CDOCS-01, CDOCS-02, CDOCS-03, CDOCS-04, CDOCS-05
**Success Criteria** (what must be TRUE):
  1. docs/ARCHITECTURE.md explains codebase structure, namespace responsibilities, and data flow
  2. docs/CONFIGURATION.md covers all config.yaml options with annotated examples
  3. docs/HARNESSES.md documents each harness (Claude, OpenCode, Codex, Gemini) with setup, usage, and tips
  4. docs/TROUBLESHOOTING.md covers common issues, error messages, and solutions
  5. docs/DEVELOPMENT.md explains how to add new harnesses or extend aishell functionality
**Plans**: TBD

Plans:
- [ ] TBD

## Progress

**Execution Order:**
Phases execute in numeric order: 24 -> 25 -> 26 -> 27

| Phase | Milestone | Plans Complete | Status | Completed |
|-------|-----------|----------------|--------|-----------|
| 1-8 | v1.0 | 16/16 | Complete | 2026-01-18 |
| 9-10 | v1.1 | 4/4 | Complete | 2026-01-19 |
| 11-12 | v1.2 | 4/4 | Complete | 2026-01-19 |
| 13-18 | v2.0 | 22/22 | Complete | 2026-01-21 |
| 18.1-23 | v2.3.0 | 11/11 | Complete | 2026-01-24 |
| 24. Build Infrastructure | v2.4.0 | 2/2 | Complete | 2026-01-25 |
| 25. CLI & Runtime | v2.4.0 | 2/2 | Complete | 2026-01-25 |
| 26. Documentation | v2.4.0 | 0/0 | Not started | - |
| 27. Comprehensive Docs | v2.4.0 | 0/0 | Not started | - |

**Total:** 61 plans completed across 5 milestones

---
*Roadmap created: 2026-01-17*
*Last updated: 2026-01-25 after Phase 25 complete*
