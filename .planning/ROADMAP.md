# Roadmap: aishell

## Overview

The aishell project delivers isolated, reproducible Docker environments for AI coding harnesses. v2.6.0 introduces tmux integration with named containers, enabling observability for long-running AI agents through session management and multi-instance support via project-hash-based naming.

## Milestones

- ✅ **v1.0 MVP** - Phases 1-4 (shipped 2026-01-18)
- ✅ **v1.1 Config Extensions** - Phases 5-9 (shipped 2026-01-19)
- ✅ **v1.2 Hardening** - Phases 10-14 (shipped 2026-01-19)
- ✅ **v2.0 Babashka Rewrite** - Phases 15-19 (shipped 2026-01-21)
- ✅ **v2.3.0 Safe AI Context Protection** - Phases 20-24 (shipped 2026-01-24)
- ✅ **v2.4.0 Multi-Harness Support** - Phases 25-27 (shipped 2026-01-25)
- ✅ **v2.5.0 Optimization & Polish** - Phases 28-29 (shipped 2026-01-26)
- 🚧 **v2.6.0 tmux Integration & Named Containers** - Phases 30-34 (in progress)

## Phases

<details>
<summary>✅ v1.0-v2.5.0 (Phases 1-29) - SHIPPED 2026-01-26</summary>

Completed milestones collapsed for brevity. See git history for details.

</details>

### 🚧 v2.6.0 tmux Integration & Named Containers (In Progress)

**Milestone Goal:** Enable observability for long-running AI agents through tmux session management and support multiple harness instances per project via deterministic container naming.

- [x] **Phase 30: Container Utilities & Naming** - Foundation layer: hash generation and Docker query functions
- [ ] **Phase 31: Dockerfile & Image Build** - Add tmux package to base image
- [ ] **Phase 32: Detached Mode & Conflict Detection** - Core functionality: named containers, tmux wrapping, mode detection
- [ ] **Phase 33: Attach Command** - User convenience: reconnect to running containers
- [ ] **Phase 34: PS Command & Polish** - Discovery: list running containers by project

## Phase Details

### Phase 30: Container Utilities & Naming
**Goal**: Provide foundation utilities for container naming and Docker queries that all subsequent phases depend on.

**Depends on**: Nothing (first phase in milestone)

**Requirements**: NAME-01, NAME-02, NAME-03

**Success Criteria** (what must be TRUE):
  1. Container name generation produces deterministic `aishell-{project-hash}-{name}` format where project-hash is first 8 chars of SHA-256
  2. Docker query functions can check if container exists, is running, and filter containers by project hash
  3. Same project directory always produces same container name for given harness
  4. Hash collision probability documented and validated (expect <0.02% at 100 projects)

**Plans:** 1 plan

Plans:
- [x] 30-01-PLAN.md — Container naming utilities and --name CLI flag

### Phase 31: Dockerfile & Image Build
**Goal**: Add tmux to base Docker image without increasing build time or introducing dependency conflicts.

**Depends on**: Phase 30

**Requirements**: IMG-01, IMG-02

**Success Criteria** (what must be TRUE):
  1. tmux package installed in base image via Debian bookworm package manager
  2. `docker run aishell:base tmux -V` reports tmux version (3.3a-3 expected)
  3. Image size increase is minimal (1-2MB expected, not >5MB)
  4. Build succeeds without dependency conflicts or warnings

**Plans:** 1 plan

Plans:
- [ ] 31-01-PLAN.md — Add tmux to base image package list

### Phase 32: Detached Mode & Conflict Detection
**Goal**: Enable harness commands to run in named, detached containers with tmux auto-start while preserving existing shell mode behavior.

**Depends on**: Phase 31

**Requirements**: TMUX-01, TMUX-02, TMUX-03, NAME-04, CONF-01, CONF-02, LIFE-01, LIFE-02

**Success Criteria** (what must be TRUE):
  1. Harness commands (claude, opencode, codex, gemini) auto-start inside a tmux session named "main" in detached container
  2. Shell mode (`aishell`) has tmux available but does NOT auto-start tmux (preserves current UX)
  3. Starting a harness with duplicate container name shows clear error if container running, auto-removes if container stopped
  4. Container uses --rm flag and remains detached after harness starts (user can attach/detach without destroying container)
  5. `docker stop` completes gracefully in <3 seconds with exit code 0 or 143 (not 137 SIGKILL)

**Plans**: TBD

Plans:
- (To be created during plan-phase)

### Phase 33: Attach Command
**Goal**: Provide simple CLI for reconnecting to running containers' tmux sessions.

**Depends on**: Phase 32

**Requirements**: ATTCH-01, ATTCH-02, ATTCH-03, ATTCH-04

**Success Criteria** (what must be TRUE):
  1. `aishell attach <name>` connects to the container's default tmux session via docker exec
  2. `aishell attach <name> --session <session>` connects to a specific tmux session by name
  3. Attaching to non-existent or stopped container produces clear error message (not Docker raw error)
  4. User can detach from tmux session (Ctrl+B D) and container continues running
  5. Multiple users can attach to same container simultaneously (tmux multi-client support)

**Plans**: TBD

Plans:
- (To be created during plan-phase)

### Phase 34: PS Command & Polish
**Goal**: Provide project-scoped container discovery and listing.

**Depends on**: Phase 32 (uses labels from detached mode)

**Requirements**: DISC-01, DISC-02

**Success Criteria** (what must be TRUE):
  1. `aishell ps` lists running containers for current project only (filtered by project hash)
  2. Output shows container name, status, and creation time in readable format
  3. When no containers running for project, shows helpful message (not empty output)
  4. Output distinguishes between containers from different projects when run from different directories

**Plans**: TBD

Plans:
- (To be created during plan-phase)

## Progress

**Execution Order:**
Phases execute in numeric order: 30 → 31 → 32 → 33 → 34

| Phase | Milestone | Plans Complete | Status | Completed |
|-------|-----------|----------------|--------|-----------|
| 1-29. Previous milestones | v1.0-v2.5.0 | All complete | Complete | 2026-01-26 |
| 30. Container Utilities & Naming | v2.6.0 | 1/1 | Complete | 2026-01-31 |
| 31. Dockerfile & Image Build | v2.6.0 | 0/1 | Not started | - |
| 32. Detached Mode & Conflict Detection | v2.6.0 | 0/TBD | Not started | - |
| 33. Attach Command | v2.6.0 | 0/TBD | Not started | - |
| 34. PS Command & Polish | v2.6.0 | 0/TBD | Not started | - |
