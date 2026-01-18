# Roadmap: Agentic Harness Sandbox

## Overview

This roadmap delivers a Docker-based sandbox for running agentic AI harnesses (Claude Code, OpenCode) in isolated containers. The journey starts with core container foundations (mounting, permissions, tools), adds git integration for seamless version control, integrates the AI harnesses themselves, enables per-project customization, and concludes with distribution for easy installation. Each phase delivers a verifiable, incremental capability.

## Phases

**Phase Numbering:**
- Integer phases (1, 2, 3): Planned milestone work
- Decimal phases (2.1, 2.2): Urgent insertions (marked with INSERTED)

Decimal phases appear between their surrounding integers in numeric order.

- [ ] **Phase 1: Core Container Foundation** - Working container with project mounted at host path, correct permissions, basic tools
- [ ] **Phase 2: Git Integration** - Git identity and safe directory configuration work seamlessly
- [ ] **Phase 3: Harness Integration** - Claude Code and OpenCode installed, configured, and runnable
- [ ] **Phase 4: Project Customization** - Projects can extend base image via Dockerfile.sandbox
- [ ] **Phase 5: Distribution** - Tool installable via curl | bash with command available in PATH

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
- [ ] 01-01-PLAN.md — Docker image foundation (Dockerfile, entrypoint, bashrc)
- [ ] 01-02-PLAN.md — CLI script (aishell) with Docker integration
- [ ] 01-03-PLAN.md — Human verification of all Phase 1 requirements

### Phase 2: Git Integration
**Goal**: Users can make git commits inside the container with their identity and without ownership warnings
**Depends on**: Phase 1
**Requirements**: GIT-01, GIT-02
**Success Criteria** (what must be TRUE):
  1. Running `git config user.name` and `git config user.email` in container returns host values
  2. Running `git status` in mounted project shows no "dubious ownership" errors
  3. Git commits made in container have correct author name and email
**Plans**: TBD

Plans:
- [ ] 02-01: TBD

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
**Plans**: TBD

Plans:
- [ ] 03-01: TBD
- [ ] 03-02: TBD
- [ ] 03-03: TBD

### Phase 4: Project Customization
**Goal**: Projects can extend the base environment with additional dependencies without modifying the core tool
**Depends on**: Phase 3
**Requirements**: CORE-06
**Success Criteria** (what must be TRUE):
  1. If Dockerfile.sandbox exists in project root, CLI detects and builds extended image
  2. Extended image includes project-specific dependencies (e.g., node, python, rust)
  3. Builds are cached (subsequent runs don't rebuild unless Dockerfile.sandbox changes)
**Plans**: TBD

Plans:
- [ ] 04-01: TBD

### Phase 5: Distribution
**Goal**: Users can install the tool with a single command and have it available in PATH
**Depends on**: Phase 4
**Requirements**: DIST-01, DIST-03
**Success Criteria** (what must be TRUE):
  1. User can run `curl ... | bash` and install the tool
  2. After installation, command is available in PATH without manual configuration
  3. Installation works on fresh Linux system with Docker Engine installed
**Plans**: TBD

Plans:
- [ ] 05-01: TBD

## Progress

**Execution Order:**
Phases execute in numeric order: 1 -> 2 -> 3 -> 4 -> 5

| Phase | Plans Complete | Status | Completed |
|-------|----------------|--------|-----------|
| 1. Core Container Foundation | 0/3 | Planned | - |
| 2. Git Integration | 0/1 | Not started | - |
| 3. Harness Integration | 0/3 | Not started | - |
| 4. Project Customization | 0/1 | Not started | - |
| 5. Distribution | 0/1 | Not started | - |

---
*Roadmap created: 2026-01-17*
*Phase 1 planned: 2026-01-17*
