# Roadmap: Agentic Harness Sandbox

## Milestones

- ✅ **v1.0 MVP** - Phases 1-8 (shipped 2026-01-18)
- ✅ **v1.1 Runtime Configuration** - Phases 9-10 (shipped 2026-01-19)
- ✅ **v1.2 Hardening** - Phases 11-12 (shipped 2026-01-19)
- ✅ **v2.0 Babashka Rewrite** - Phases 13-18 (shipped 2026-01-21)
- ✅ **v2.3.0 Safe AI Context** - Phases 18.1-23 (shipped 2026-01-24)
- ✅ **v2.4.0 Multi-Harness** - Phases 24-27 (shipped 2026-01-25)
- ✅ **v2.5.0 Optimization** - Phases 28-29 (shipped 2026-01-26)
- ✅ **v2.7.0 tmux Integration** - Phases 30-34 (shipped 2026-01-31)
- ✅ **v2.8.0 Decouple Harness Tools** - Phases 35-38 (shipped 2026-02-01)
- ✅ **v2.9.0 tmux Opt-in** - Phases 39-43 (shipped 2026-02-03)
- ✅ **v2.10.0 Gitleaks Opt-in** - Phases 44-45 (shipped 2026-02-05)
- ✅ **v3.0.0 Docker-native Attach** - Phases 46-52 (shipped 2026-02-06)
- ✅ **v3.1.0 Native Windows Support** - Phases 53-59 (shipped 2026-02-12)
- ✅ **v3.5.0 Pi Coding Agent Support** - Phases 60-62 (shipped 2026-02-18)
- 🚧 **v3.7.0 OpenSpec Support** - Phases 63-65 (in progress)

## Phases

<details>
<summary>✅ v1.0 MVP (Phases 1-8) - SHIPPED 2026-01-18</summary>

See MILESTONES.md for details.

</details>

<details>
<summary>✅ v1.1 Runtime Configuration (Phases 9-10) - SHIPPED 2026-01-19</summary>

See MILESTONES.md for details.

</details>

<details>
<summary>✅ v1.2 Hardening (Phases 11-12) - SHIPPED 2026-01-19</summary>

See MILESTONES.md for details.

</details>

<details>
<summary>✅ v2.0 Babashka Rewrite (Phases 13-18) - SHIPPED 2026-01-21</summary>

See MILESTONES.md for details.

</details>

<details>
<summary>✅ v2.3.0 Safe AI Context (Phases 18.1-23) - SHIPPED 2026-01-24</summary>

See MILESTONES.md for details.

</details>

<details>
<summary>✅ v2.4.0 Multi-Harness (Phases 24-27) - SHIPPED 2026-01-25</summary>

See MILESTONES.md for details.

</details>

<details>
<summary>✅ v2.5.0 Optimization (Phases 28-29) - SHIPPED 2026-01-26</summary>

See MILESTONES.md for details.

</details>

<details>
<summary>✅ v2.7.0 tmux Integration (Phases 30-34) - SHIPPED 2026-01-31</summary>

See MILESTONES.md for details.

</details>

<details>
<summary>✅ v2.8.0 Decouple Harness Tools (Phases 35-38) - SHIPPED 2026-02-01</summary>

See MILESTONES.md for details.

</details>

<details>
<summary>✅ v2.9.0 tmux Opt-in (Phases 39-43) - SHIPPED 2026-02-03</summary>

See MILESTONES.md for details.

</details>

<details>
<summary>✅ v2.10.0 Gitleaks Opt-in (Phases 44-45) - SHIPPED 2026-02-05</summary>

See MILESTONES.md for details.

</details>

<details>
<summary>✅ v3.0.0 Docker-native Attach (Phases 46-52) - SHIPPED 2026-02-06</summary>

See MILESTONES.md for details.

</details>

<details>
<summary>✅ v3.1.0 Native Windows Support (Phases 53-59) - SHIPPED 2026-02-12</summary>

See MILESTONES.md for details.

</details>

<details>
<summary>✅ v3.5.0 Pi Coding Agent Support (Phases 60-62) - SHIPPED 2026-02-18</summary>

See MILESTONES.md for details.

</details>

### 🚧 v3.7.0 OpenSpec Support (In Progress)

**Milestone Goal:** Add OpenSpec as an opt-in development workflow tool available inside containers, following the established `--with-*` build flag pattern.

- [x] **Phase 63: Core OpenSpec Integration** - Build flag, version pinning, volume install, and state tracking (completed 2026-02-18)
- [x] **Phase 64: Documentation** - All 6 user-facing docs updated for OpenSpec (completed 2026-02-18)
- [x] **Phase 65: Release** - Version bump to 3.7.0 and changelog (completed 2026-02-18)

## Phase Details

### Phase 63: Core OpenSpec Integration
**Goal**: Users can opt into OpenSpec at build time and use it inside containers
**Depends on**: Nothing (first phase of milestone)
**Requirements**: BUILD-01, BUILD-02, BUILD-03, VOL-01, VOL-02
**Success Criteria** (what must be TRUE):
  1. User can run `aishell build --with-openspec` and the build completes successfully with OpenSpec enabled
  2. User can run `aishell build --with-openspec=1.2.3` to pin a specific OpenSpec version
  3. Running `aishell shell` after building with `--with-openspec` gives access to the `openspec` command inside the container
  4. Rebuilding without `--with-openspec` after a previous build with it results in OpenSpec no longer being available (state correctly tracks opt-in/out)
  5. Changing the OpenSpec version or toggling its enabled state triggers volume recreation on next container run (hash invalidation works)
**Plans**: 2 plans

Plans:
- [ ] 63-01-PLAN.md -- Build flag, state persistence, volume registration, setup/update wiring
- [ ] 63-02-PLAN.md -- Runtime volume trigger, check command status display

### Phase 64: Documentation
**Goal**: All user-facing documentation reflects OpenSpec availability and usage
**Depends on**: Phase 63
**Requirements**: DOCS-01
**Success Criteria** (what must be TRUE):
  1. README.md mentions OpenSpec as an available tool with `--with-openspec` build flag
  2. docs/HARNESSES.md (or equivalent section) describes OpenSpec, its purpose, and how to enable it
  3. docs/CONFIGURATION.md documents the `--with-openspec` and `--with-openspec=VERSION` flags
  4. docs/ARCHITECTURE.md, docs/TROUBLESHOOTING.md, and docs/DEVELOPMENT.md are updated where OpenSpec affects their content
**Plans**: 2 plans

Plans:
- [ ] 64-01-PLAN.md -- User-facing docs: README.md, HARNESSES.md, CONFIGURATION.md
- [ ] 64-02-PLAN.md -- Internal/developer docs: ARCHITECTURE.md, TROUBLESHOOTING.md, DEVELOPMENT.md

### Phase 65: Release
**Goal**: v3.7.0 is tagged and ready for users
**Depends on**: Phase 64
**Requirements**: REL-01
**Success Criteria** (what must be TRUE):
  1. CLI version string reports 3.7.0
  2. CHANGELOG.md has a v3.7.0 entry summarizing OpenSpec support
**Plans**: 1 plan

Plans:
- [ ] 65-01-PLAN.md -- Version bump to 3.7.0 and CHANGELOG entry

## Progress

**Execution Order:**
Phases execute in numeric order: 63 → 64 → 65

| Phase | Milestone | Plans Complete | Status | Completed |
|-------|-----------|----------------|--------|-----------|
| 1-8. MVP | v1.0 | All | Complete | 2026-01-18 |
| 9-10. Runtime Config | v1.1 | All | Complete | 2026-01-19 |
| 11-12. Hardening | v1.2 | All | Complete | 2026-01-19 |
| 13-18. Babashka Rewrite | v2.0 | All | Complete | 2026-01-21 |
| 18.1-23. Safe AI Context | v2.3.0 | All | Complete | 2026-01-24 |
| 24-27. Multi-Harness | v2.4.0 | All | Complete | 2026-01-25 |
| 28-29. Optimization | v2.5.0 | All | Complete | 2026-01-26 |
| 30-34. tmux Integration | v2.7.0 | All | Complete | 2026-01-31 |
| 35-38. Decouple Harness | v2.8.0 | All | Complete | 2026-02-01 |
| 39-43. tmux Opt-in | v2.9.0 | All | Complete | 2026-02-03 |
| 44-45. Gitleaks Opt-in | v2.10.0 | All | Complete | 2026-02-05 |
| 46-52. Docker-native Attach | v3.0.0 | All | Complete | 2026-02-06 |
| 53-59. Native Windows Support | v3.1.0 | All | Complete | 2026-02-12 |
| 60-62. Pi Coding Agent | v3.5.0 | All | Complete | 2026-02-18 |
| 63. Core OpenSpec Integration | 2/2 | Complete    | 2026-02-18 | - |
| 64. Documentation | 2/2 | Complete    | 2026-02-18 | - |
| 65. Release | 1/1 | Complete    | 2026-02-18 | - |
