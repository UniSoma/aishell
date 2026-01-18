# Roadmap: Agentic Harness Sandbox

## Overview

This roadmap delivers a Docker-based sandbox for running agentic AI harnesses (Claude Code, OpenCode) in isolated containers. The journey starts with core container foundations (mounting, permissions, tools), adds git integration for seamless version control, integrates the AI harnesses themselves, enables per-project customization, and concludes with distribution for easy installation. Each phase delivers a verifiable, incremental capability.

## Phases

**Phase Numbering:**
- Integer phases (1, 2, 3): Planned milestone work
- Decimal phases (2.1, 2.2): Urgent insertions (marked with INSERTED)

Decimal phases appear between their surrounding integers in numeric order.

- [x] **Phase 1: Core Container Foundation** - Working container with project mounted at host path, correct permissions, basic tools
- [x] **Phase 2: Git Integration** - Git identity and safe directory configuration work seamlessly
- [x] **Phase 3: Harness Integration** - Claude Code and OpenCode installed, configured, and runnable
- [x] **Phase 4: Project Customization** - Projects can extend base image via .aishell/Dockerfile
- [x] **Phase 5: Distribution** - Tool installable via curl | bash with command available in PATH
- [x] **Phase 6: Final Enhancements** - Version pinning, UX improvements (prompt, permissions), polish
- [ ] **Phase 7: Node.js and Clojure Tooling** - Add stable Node.js, Babashka, and bbin to base image

## Phase Details

### Phase 1: Core Container Foundation
**Goal**: Users can enter an ephemeral container with their project mounted at the exact host path, correct file ownership, and basic CLI tools available
**Depends on**: Nothing (first phase)
**Requirements**: CORE-01, CORE-02, CORE-03, CORE-04, CORE-05, DIST-02, DIST-04
**Success Criteria** (what must be TRUE):
  1. User can run the CLI and enter a shell inside the container
  2. Project files are visible at the same absolute path as on host (e.g., /home/user/project, NOT /workspace)
  3. Files created in container appear on host with correct UID/GID ownership
  4. Container runs as non-root user but sudo is available
  5. Basic tools (git, curl, vim, jq, ripgrep) are available in container
**Plans**: 3 plans

Plans:
- [x] 01-01-PLAN.md — Docker image foundation (Dockerfile, entrypoint, bashrc)
- [x] 01-02-PLAN.md — CLI script (aishell) with Docker integration
- [x] 01-03-PLAN.md — Human verification of all Phase 1 requirements

### Phase 2: Git Integration
**Goal**: Users can make git commits inside the container with their identity and without ownership warnings
**Depends on**: Phase 1
**Requirements**: GIT-01, GIT-02
**Success Criteria** (what must be TRUE):
  1. Running `git config user.name` and `git config user.email` in container returns host values
  2. Running `git status` in mounted project shows no "dubious ownership" errors
  3. Git commits made in container have correct author name and email
**Plans**: 1 plan

Plans:
- [x] 02-01-PLAN.md — Git identity propagation and safe.directory configuration

### Phase 3: Harness Integration
**Goal**: Users can run Claude Code and OpenCode harnesses with their configurations mounted
**Depends on**: Phase 2
**Requirements**: HARNESS-01, HARNESS-02, HARNESS-03, HARNESS-04, HARNESS-05, HARNESS-06, HARNESS-07
**Success Criteria** (what must be TRUE):
  1. Running `<command> claude` launches Claude Code inside container
  2. Running `<command> opencode` launches OpenCode inside container
  3. Running `<command>` (no args) enters a shell in the container
  4. Claude Code can read existing config (API keys, settings) from mounted ~/.claude
  5. OpenCode can read existing config from mounted ~/.config/opencode
**Plans**: 4 plans

Plans:
- [x] 03-01-PLAN.md — Dockerfile: conditional harness installation with build args
- [x] 03-02-PLAN.md — aishell: subcommand parsing, config mounting, env passthrough
- [x] 03-03-PLAN.md — Human verification of all HARNESS requirements
- [x] 03-04-PLAN.md — Fix OpenCode XDG state directory (gap closure)

### Phase 4: Project Customization
**Goal**: Projects can extend the base environment with additional dependencies via .aishell/Dockerfile
**Depends on**: Phase 3
**Requirements**: CORE-06
**Success Criteria** (what must be TRUE):
  1. If .aishell/Dockerfile exists in project, CLI detects and builds extended image
  2. Extended image includes project-specific dependencies (e.g., node, python, rust)
  3. Builds are cached (subsequent runs don't rebuild unless Dockerfile or base changes)
**Plans**: 1 plan

Plans:
- [x] 04-01-PLAN.md — Extension detection, building, caching, and verification

### Phase 5: Distribution
**Goal**: Users can install the tool with a single command and have it available in PATH
**Depends on**: Phase 4
**Requirements**: DIST-01, DIST-03
**Success Criteria** (what must be TRUE):
  1. User can run `curl ... | bash` and install the tool
  2. After installation, command is available in PATH without manual configuration
  3. Installation works on fresh Linux system with Docker Engine installed
**Plans**: 1 plan

Plans:
- [x] 05-01-PLAN.md — Self-contained aishell with heredocs and curl|bash installer

### Phase 6: Final Enhancements
**Goal**: Polish the user experience with version pinning, shorter shell prompt, and auto-skip permissions in sandboxed environment
**Depends on**: Phase 5
**Requirements**: VERSION-01, VERSION-02, VERSION-03, VERSION-04, UX-01, UX-02
**Success Criteria** (what must be TRUE):
  1. User can specify `--claude-version=X.Y.Z` to install a specific Claude Code version
  2. User can specify `--opencode-version=X.Y.Z` to install a specific OpenCode version
  3. Without version flags, latest version is installed (current behavior)
  4. Version is baked into image tag for caching (e.g., `aishell:claude-1.0.5`)
  5. Container shell prompt is concise (not full absolute path)
  6. Claude Code runs with `--dangerously-skip-permissions` by default (container is sandbox)
  7. Users can opt-out of auto-skip permissions via environment variable
**Plans**: 2 plans

Plans:
- [x] 06-01-PLAN.md — Version pinning: CLI flags, Dockerfile args, image tagging
- [x] 06-02-PLAN.md — UX improvements: PROMPT_DIRTRIM, --dangerously-skip-permissions

### Phase 7: Node.js and Clojure Tooling
**Goal**: Base image includes stable Node.js LTS, Babashka (bb), and bbin for enhanced scripting capabilities
**Depends on**: Phase 6
**Requirements**: DEV-01, DEV-02, DEV-03
**Success Criteria** (what must be TRUE):
  1. Node.js LTS is available in container (`node --version` works)
  2. npm is available in container (`npm --version` works)
  3. Babashka is available in container (`bb --version` works)
  4. bbin is available in container (`bbin version` works)
  5. All tools work for non-root container user
**Plans**: 1 plan

Plans:
- [ ] 07-01-PLAN.md — Multi-stage Dockerfile with Node.js, Babashka, bbin installation

## Progress

**Execution Order:**
Phases execute in numeric order: 1 -> 2 -> 3 -> 4 -> 5 -> 6 -> 7

| Phase | Plans Complete | Status | Completed |
|-------|----------------|--------|-----------|
| 1. Core Container Foundation | 3/3 | Complete | 2026-01-17 |
| 2. Git Integration | 1/1 | Complete | 2026-01-17 |
| 3. Harness Integration | 4/4 | Complete | 2026-01-17 |
| 4. Project Customization | 1/1 | Complete | 2026-01-18 |
| 5. Distribution | 1/1 | Complete | 2026-01-18 |
| 6. Final Enhancements | 2/2 | Complete | 2026-01-18 |
| 7. Node.js and Clojure Tooling | 0/1 | Not Started | - |

---
*Roadmap created: 2026-01-17*
*Phase 1 planned: 2026-01-17*
*Phase 2 planned: 2026-01-17*
*Phase 3 planned: 2026-01-17*
*Phase 4 planned: 2026-01-17*
*Phase 5 planned: 2026-01-18*
*Phase 6 planned: 2026-01-18*
*Phase 7 planned: 2026-01-18*
