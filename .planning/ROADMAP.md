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

### v3.5.0 Pi Coding Agent Support (In Progress)

**Milestone Goal:** Add pi coding agent as a first-class harness following the established multi-harness pattern (Codex/Gemini), with fd in foundation image and full CLI integration.

- [x] **Phase 60: Pi Build Infrastructure** - Foundation image (fd), build flags, npm install, env vars (completed 2026-02-18)
- [x] **Phase 61: Pi CLI Integration** - Run command, config mounting, check, help, pass-through args, entrypoint alias (completed 2026-02-18)
- [x] **Phase 62: Pi Documentation** - All user-facing docs updated for pi harness (completed 2026-02-18)

## Phase Details

### Phase 60: Pi Build Infrastructure
**Goal**: Pi coding agent can be built into the harness volume and the foundation image includes fd
**Depends on**: Nothing (first phase of v3.5.0)
**Requirements**: FOUND-01, FOUND-02, HARNESS-01, HARNESS-03, HARNESS-05, ENV-01, ENV-02
**Success Criteria** (what must be TRUE):
  1. `aishell build --with-pi` succeeds and installs @mariozechner/pi-coding-agent via npm in the harness volume
  2. `aishell build --with-pi=1.0.0` pins the pi version in build state and installs that specific version
  3. `fd` command is available inside the container (fd-find package with fd symlink in foundation image)
  4. PI_CODING_AGENT_DIR and PI_SKIP_VERSION_CHECK environment variables from host are passed through to the container
**Plans**: 2 plans

Plans:
- [ ] 60-01-PLAN.md -- Foundation image fd-find + pi npm package in volume infrastructure
- [ ] 60-02-PLAN.md -- CLI --with-pi flag, state integration, env var passthrough, runtime wiring

### Phase 61: Pi CLI Integration
**Goal**: Users can run pi coding agent through aishell with the same UX as Claude/Codex/Gemini
**Depends on**: Phase 60
**Requirements**: HARNESS-02, HARNESS-04, HARNESS-06, HARNESS-07, HARNESS-08, HARNESS-09
**Success Criteria** (what must be TRUE):
  1. `aishell pi` launches pi coding agent inside the container in the mounted project directory
  2. `aishell pi --print "hello"` passes arguments through to pi correctly
  3. Pi config directory (`~/.pi/`) is mounted from host into the container, persisting auth and settings
  4. `aishell check` shows pi installation status and version when pi is installed
  5. `aishell --help` lists the `pi` command when pi is installed, and hides it when not installed
**Plans**: 1 plan

Plans:
- [ ] 61-01-PLAN.md -- Add HARNESS_ALIAS_PI to entrypoint alias loop (completes all pi CLI integration)

### Phase 62: Pi Documentation
**Goal**: All user-facing documentation reflects pi as a first-class harness
**Depends on**: Phase 61
**Requirements**: DOCS-01
**Success Criteria** (what must be TRUE):
  1. README.md includes pi in harness list, build examples, and quickstart
  2. docs/HARNESSES.md has a complete pi section with auth, config, and usage
  3. docs/CONFIGURATION.md documents --with-pi build flag and PI_* env vars
  4. docs/ARCHITECTURE.md, docs/TROUBLESHOOTING.md, and docs/DEVELOPMENT.md updated with pi references
**Plans**: 2 plans

Plans:
- [x] 62-01-PLAN.md -- Update README.md, HARNESSES.md, CONFIGURATION.md with pi harness
- [x] 62-02-PLAN.md -- Update ARCHITECTURE.md, TROUBLESHOOTING.md, DEVELOPMENT.md with pi references

## Progress

**Execution Order:**
Phases execute in numeric order: 60 -> 61 -> 62

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
| 60. Pi Build Infrastructure | 2/2 | Complete    | 2026-02-18 | - |
| 61. Pi CLI Integration | 1/1 | Complete    | 2026-02-18 | - |
| 62. Pi Documentation | 2/2 | Complete    | 2026-02-18 | - |
