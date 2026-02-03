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
- 📋 **v2.9.0 tmux Opt-in & Plugin Support** - Phases 39-43 (planned)

## Phases

<details>
<summary>✅ v1.0-v2.8.0 Completed Milestones (Phases 1-38) - Collapsed</summary>

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

</details>

### 📋 v2.9.0 tmux Opt-in & Plugin Support (Planned)

**Milestone Goal:** Make tmux opt-in, add plugin management, support user config mounting, enable session persistence.

#### ✅ Phase 39: State Schema & Config Mounting (Complete 2026-02-02)
**Goal**: Establish opt-in flag and mount user tmux configuration
**Depends on**: Phase 38
**Requirements**: TMUX-01, TMUX-02, CONF-01, CONF-02
**Success Criteria** (what must be TRUE):
  1. User can run `aishell build --with-tmux` and flag is stored in state.edn
  2. User can run `aishell build` without flag and tmux is disabled
  3. User's ~/.tmux.conf is mounted read-only into container when tmux enabled
  4. Missing ~/.tmux.conf on host is handled gracefully with no error
**Plans**: 2 plans

Plans:
- [x] 39-01-PLAN.md -- CLI --with-tmux flag, state schema, config tmux section
- [x] 39-02-PLAN.md -- Conditional tmux config mount in docker run

#### ✅ Phase 40: Plugin Installation in Volume (Complete 2026-02-02)
**Goal**: Install TPM and declared plugins into harness volume at build time
**Depends on**: Phase 39
**Requirements**: PLUG-01, PLUG-02, PLUG-03, PLUG-06
**Success Criteria** (what must be TRUE):
  1. User can declare plugins in .aishell/config.yaml under tmux.plugins list
  2. TPM installed into /tools/tmux/plugins/tpm during volume population
  3. Declared plugins installed non-interactively during aishell build
  4. Plugin format validation catches invalid owner/repo patterns before build
  5. aishell update refreshes plugin installations
**Plans**: 4 plans

Plans:
- [x] 40-01-PLAN.md -- Plugin format validation in config parsing
- [x] 40-02-PLAN.md -- TPM and plugin installation in volume population
- [x] 40-03-PLAN.md -- Gap closure: volume hash includes tmux state, idempotent TPM clone
- [x] 40-04-PLAN.md -- Gap closure: fix plugin declaration path for TPM install_plugins

#### ✅ Phase 41: TPM Initialization in Entrypoint (Complete 2026-02-02)
**Goal**: Make installed plugins discoverable to tmux at runtime
**Depends on**: Phase 40
**Requirements**: TMUX-03, PLUG-04, PLUG-05
**Success Criteria** (what must be TRUE):
  1. Plugins installed in /tools/tmux/plugins are accessible at ~/.tmux/plugins
  2. TPM initialization appended to user's tmux config at container startup
  3. tmux session only starts when :with-tmux flag is true in state
  4. Shell mode works correctly with tmux disabled
  5. Harness commands work correctly with tmux disabled
**Plans**: 1 plan

Plans:
- [x] 41-01-PLAN.md -- Entrypoint plugin bridging, config injection, conditional tmux/shell startup + WITH_TMUX env var

#### ✅ Phase 42: Resurrect State Persistence (Complete 2026-02-02)
**Goal**: Enable optional session state persistence via tmux-resurrect
**Depends on**: Phase 41
**Requirements**: PERS-01, PERS-02, PERS-03
**Success Criteria** (what must be TRUE):
  1. User can configure tmux.resurrect section in config.yaml
  2. Resurrect state directory mounted from host when resurrect enabled
  3. Process restoration disabled by default with only layout restoration active
  4. Session state persists across container restarts when resurrect configured
**Plans**: 2 plans

Plans:
- [x] 42-01-PLAN.md -- Config parsing for resurrect (boolean/map) and host state directory mount
- [x] 42-02-PLAN.md -- Plugin auto-injection, entrypoint resurrect config and auto-restore

#### Phase 43: Validation & Migration
**Goal**: Ensure graceful failures and smooth upgrade path for existing users
**Depends on**: Phase 42
**Requirements**: TMUX-04, TMUX-05, DOCS-01
**Success Criteria** (what must be TRUE):
  1. aishell attach validates tmux is enabled and shows helpful error when not
  2. Users upgrading from v2.7-2.8 see migration warning about tmux behavior change
  3. All CLI changes reflected in README.md
  4. All architecture changes reflected in docs/ARCHITECTURE.md
  5. All config changes reflected in docs/CONFIGURATION.md
  6. Troubleshooting guide updated for tmux-related issues
**Plans**: 2 plans

Plans:
- [ ] 43-01-PLAN.md -- Attach tmux validation and v2.9.0 migration warning
- [ ] 43-02-PLAN.md -- Documentation updates for v2.9.0 (all 6 docs files)

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
| 39-43 | v2.9.0 | 9/9 | In progress | - |

**Total:** 99/99 plans complete across 9 milestones, v2.9.0 in progress

---
*Roadmap created: 2026-01-17*
*Last updated: 2026-02-03 after Phase 43 planning complete*
