# Roadmap: Agentic Harness Sandbox

## Milestones

- ✅ **v1.0 MVP** - Phases 1-8 (shipped 2026-01-18)
- ✅ **v1.1 Per-project Runtime Configuration** - Phases 9-10 (shipped 2026-01-19)
- ✅ **v1.2 Hardening & Edge Cases** - Phases 11-12 (shipped 2026-01-19)
- ✅ **v2.0 Babashka Rewrite** - Phases 13-18 (shipped 2026-01-21)
- ✅ **v2.3.0 Safe AI Context Protection** - Phases 18.1-23 (shipped 2026-01-24)
- ✅ **v2.4.0 Multi-Harness Support** - Phases 24-27 (shipped 2026-01-25)
- ✅ **v2.5.0 Optimization & Polish** - Phases 28-29 (shipped 2026-01-26)
- ✅ **v2.7.0 tmux Integration & Named Containers** - Phases 30-34 (shipped 2026-01-31)
- ✅ **v2.8.0 Decouple Harness Tools** - Phases 35-38 (shipped 2026-02-01)
- ✅ **v2.9.0 tmux Opt-in & Plugin Support** - Phases 39-43 (shipped 2026-02-03)
- 🚧 **v2.10.0 Gitleaks Opt-in** - Phases 44-45 (in progress)

## Phases

<details>
<summary>✅ v1.0-v2.9.0 Completed Milestones (Phases 1-43) - Collapsed</summary>

See MILESTONES.md for full historical context.

**Summary:**
- v1.0: Docker-based ephemeral sandbox (Phases 1-8)
- v1.1: Per-project runtime configuration (Phases 9-10)
- v1.2: Hardening and edge cases (Phases 11-12)
- v2.0: Babashka rewrite with YAML config (Phases 13-18)
- v2.3.0: Safe AI context protection with Gitleaks (Phases 18.1-23)
- v2.4.0: Multi-harness support (Codex, Gemini) (Phases 24-27)
- v2.5.0: Optimization and polish (Phases 28-29)
- v2.7.0: tmux integration and named containers (Phases 30-34)
- v2.8.0: Foundation/volume architecture split (Phases 35-38)
- v2.9.0: tmux opt-in and plugin support (Phases 39-43)

</details>

### 🚧 v2.10.0 Gitleaks Opt-in (In Progress)

**Milestone Goal:** Flip Gitleaks from opt-out to opt-in. Users who want Gitleaks scanning must explicitly request it at build time. Filename-based detection continues to work independently.

#### Phase 44: Flip Gitleaks Default
**Goal**: Gitleaks installation requires explicit opt-in at build time
**Depends on**: Phase 43 (v2.9.0 complete)
**Requirements**: BUILD-01, BUILD-02, HELP-01, PIPE-01, PIPE-02
**Success Criteria** (what must be TRUE):
  1. Building without flags produces image without Gitleaks installed
  2. Building with `--with-gitleaks` produces image with Gitleaks installed
  3. `aishell --help` shows `gitleaks` command only when Gitleaks is installed
  4. Pipeline runs without Gitleaks staleness warning when Gitleaks not installed
  5. Filename-based secret detection still blocks suspicious files (independent of Gitleaks)
**Plans**: TBD

Plans:
- [ ] 44-01: TBD

#### Phase 45: Documentation Updates
**Goal**: Documentation reflects the new Gitleaks opt-in default
**Depends on**: Phase 44
**Requirements**: DOCS-01, DOCS-02, DOCS-03, DOCS-04
**Success Criteria** (what must be TRUE):
  1. README.md documents `--with-gitleaks` flag and explains opt-in behavior
  2. CONFIGURATION.md explains Gitleaks configuration (when installed)
  3. TROUBLESHOOTING.md covers Gitleaks-related issues (or notes N/A when not installed)
  4. HARNESSES.md updated if it references Gitleaks behavior
**Plans**: TBD

Plans:
- [ ] 45-01: TBD

## Progress

| Phase | Milestone | Plans Complete | Status | Completed |
|-------|-----------|----------------|--------|-----------|
| 1-8 | v1.0 | 16/16 | Complete | 2026-01-18 |
| 9-10 | v1.1 | 4/4 | Complete | 2026-01-19 |
| 11-12 | v1.2 | 4/4 | Complete | 2026-01-19 |
| 13-18 | v2.0 | 22/22 | Complete | 2026-01-21 |
| 18.1-23 | v2.3.0 | 11/11 | Complete | 2026-01-24 |
| 24-27 | v2.4.0 | 8/8 | Complete | 2026-01-25 |
| 28-29 | v2.5.0 | 4/4 | Complete | 2026-01-26 |
| 30-34 | v2.7.0 | 7/7 | Complete | 2026-01-31 |
| 35-38 | v2.8.0 | 14/14 | Complete | 2026-02-01 |
| 39-43 | v2.9.0 | 12/12 | Complete | 2026-02-03 |
| 44 | v2.10.0 | 0/? | Not started | - |
| 45 | v2.10.0 | 0/? | Not started | - |

**Total:** 102/102 plans complete across 10 milestones (v2.10.0 in progress)

---
*Roadmap created: 2026-01-17*
*Last updated: 2026-02-05 — v2.10.0 milestone added*
