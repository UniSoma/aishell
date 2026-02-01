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
- 🚧 **v2.8.0 Decouple Harness Tools** - Phases 35-38 (in progress)

## Phases

<details>
<summary>✅ v1.0-v2.7.0 Completed Milestones (Phases 1-34) - Collapsed</summary>

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

</details>

### 🚧 v2.8.0 Decouple Harness Tools (In Progress)

**Milestone Goal:** Eliminate cascade invalidation by splitting the monolithic base image into a stable foundation layer and volume-mounted harness tools, so harness version updates no longer force multi-gigabyte extension rebuilds.

#### Phase 35: Foundation Image Split

**Goal:** Create stable foundation image without harness tools, with clean migration path from old `aishell:base` tag

**Depends on:** Phase 34 (v2.7.0 complete)

**Requirements:** FNDN-01, FNDN-02, FNDN-03, BUILD-02

**Success Criteria** (what must be TRUE):
1. Foundation image builds successfully with Debian, Node.js, system tools (tmux, git, curl, etc.), babashka, and gosu but no harness npm packages
2. Foundation image is tagged as `aishell:foundation` and `aishell:base` tag is not created
3. User's `.aishell/Dockerfile` using `FROM aishell:base` produces clear error message with instructions to change to `FROM aishell:foundation`
4. Foundation image only rebuilds when Dockerfile template changes, not when harness versions change

**Plans:** 2 plans

Plans:
- [x] 35-01: Update Dockerfile template to remove harness installations, change tag to foundation
- [x] 35-02: Add base-tag validation with clear error messages for legacy extensions

#### Phase 36: Harness Volume Core

**Goal:** Harness tools installed into Docker named volumes and mounted at runtime with correct PATH configuration

**Depends on:** Phase 35

**Requirements:** HVOL-01, HVOL-02, HVOL-03, HVOL-06, BUILD-03

**Success Criteria** (what must be TRUE):
1. Harness tools install successfully into Docker named volume using `npm install -g --prefix /tools/npm`
2. Harness volume mounts at runtime with PATH and NODE_PATH configured correctly so harness commands are executable
3. Volume names follow pattern `aishell-harness-{hash}` where hash is derived from harness flags and versions
4. Projects with identical harness combinations (same flags/versions) share the same volume (same hash = same volume)
5. Harness volume rebuilds only when harness versions or flags change, not when foundation image changes

**Plans:** 3 plans

Plans:
- [x] 36-01: Implement harness hash computation from flags and versions
- [x] 36-02: Create volume population logic with npm install commands
- [x] 36-03: Wire volume mount into docker run with PATH/NODE_PATH environment configuration

#### Phase 37: Build Integration & Migration

**Goal:** Transparent build UX with automatic state migration and lazy volume population

**Depends on:** Phase 36

**Requirements:** BUILD-01, HVOL-04, HVOL-05, CACHE-01, CACHE-02, MIGR-01, MIGR-02

**Success Criteria** (what must be TRUE):
1. `aishell build` command handles both foundation image build and harness volume population transparently without requiring separate commands
2. Harness volume auto-populates on first container run if volume is empty or stale
3. Stale volume detection works by comparing stored hash against current harness flags and versions
4. Extension image cache invalidation references foundation image ID instead of base image ID
5. State file schema tracks foundation-hash and harness-volume-hash as separate fields
6. Existing state files from v2.7.0 migrate automatically on first run without user intervention
7. Existing extensions auto-rebuild on first build after upgrade because foundation ID has changed

**Plans:** 6 plans

Plans:
- [x] 37-01: Update state schema with foundation-hash and harness-volume-hash fields, implement backward-compatible migration
- [x] 37-02: Implement lazy volume population with staleness detection on container run
- [x] 37-03: Update extension cache invalidation to use foundation image ID
- [x] 37-04: Wire foundation build and volume population into unified build command flow
- [ ] 37-05: Add OpenCode binary installation to volume population (GAP-01 fix)
- [ ] 37-06: Fix tmux new-window environment loss via /etc/profile.d script (GAP-02 fix)

#### Phase 38: Volume Cleanup & Documentation

**Goal:** Volume management commands and comprehensive documentation for new architecture

**Depends on:** Phase 37

**Requirements:** CLEAN-01, CLEAN-02, DOCS-01

**Success Criteria** (what must be TRUE):
1. User can list all harness volumes and identify which are orphaned (not referenced by current state)
2. User can prune orphaned volumes with confirmation prompt to prevent accidental deletion
3. README.md reflects new foundation/volume architecture with migration guidance
4. ARCHITECTURE.md explains 2-tier architecture with foundation image and harness volumes
5. CONFIGURATION.md documents new build behavior and volume management commands
6. TROUBLESHOOTING.md covers common volume-related issues and debugging steps
7. DEVELOPMENT.md updated for contributors understanding the new build system

**Plans:** 2 plans

Plans:
- [ ] 38-01: Implement volume list and prune commands with orphan detection
- [ ] 38-02: Update all documentation files (README, ARCHITECTURE, CONFIGURATION, HARNESSES, TROUBLESHOOTING, DEVELOPMENT)

## Progress

**Execution Order:**
Phases execute numerically: 35 → 36 → 37 → 38

| Phase | Plans Complete | Status | Completed |
|-------|----------------|--------|-----------|
| 35. Foundation Image Split | 2/2 | ✅ Complete | 2026-01-31 |
| 36. Harness Volume Core | 3/3 | ✅ Complete | 2026-02-01 |
| 37. Build Integration & Migration | 4/4 | ✅ Complete | 2026-02-01 |
| 38. Volume Cleanup & Documentation | 0/2 | Not started | - |

**Total:** 9/11 plans complete (82%)

---
*Roadmap created: 2026-01-31*
*Last updated: 2026-02-01*
