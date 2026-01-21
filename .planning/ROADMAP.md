# Roadmap: aishell v2.0 Babashka Rewrite

## Overview

This milestone rewrites aishell from 1,655 LOC Bash to Clojure Babashka for cross-platform support (Linux, macOS) and simpler implementation. The journey starts with CLI foundation and Docker integration, then builds the build command, run commands, and validation layers. Distribution via uberscript completes the rewrite with feature parity to v1.2.

## Milestones

- v1.0 MVP - Phases 1-8 (shipped 2026-01-18)
- v1.1 Runtime Config - Phases 9-10 (shipped 2026-01-19)
- v1.2 Hardening - Phases 11-12 (shipped 2026-01-19)
- **v2.0 Babashka Rewrite** - Phases 13-18 (in progress)

## Phases

- [x] **Phase 13: Foundation** - CLI skeleton with --version, --help, and error handling
- [x] **Phase 14: Docker Integration** - Docker wrapper module and availability checks
- [x] **Phase 15: Build Command** - Full build workflow with state persistence
- [x] **Phase 16: Run Commands** - Shell, claude, opencode with configuration support
- [x] **Phase 17: Validation & Polish** - Version validation, warnings, and hash detection
- [ ] **Phase 18: Distribution** - Uberscript packaging and curl|bash installer

## Phase Details

### Phase 13: Foundation
**Goal**: Establish project structure and core CLI that all subsequent phases build on
**Depends on**: Nothing (first phase of v2.0)
**Requirements**: CLI-01, CLI-02, CLI-08, PLAT-03
**Success Criteria** (what must be TRUE):
  1. User can run `./aishell --version` and see version number
  2. User can run `./aishell --help` and see available commands with descriptions
  3. User sees clear error message when running invalid command (e.g., `./aishell foo`)
  4. Path handling works correctly on both Linux and macOS (forward slashes, home expansion)
**Plans**: 2 plans

Plans:
- [x] 13-01-PLAN.md - Project structure and core CLI (dispatch, version, help)
- [x] 13-02-PLAN.md - Path utilities and enhanced error messages

### Phase 14: Docker Integration
**Goal**: Provide Docker operations that build and run commands depend on
**Depends on**: Phase 13
**Requirements**: DOCK-01, DOCK-02, DOCK-03, DOCK-07, DOCK-08
**Success Criteria** (what must be TRUE):
  1. User sees clear error "Docker not running" when Docker daemon unavailable
  2. User sees clear error "No image built" when running without prior build
  3. Tool can build Docker image from embedded Dockerfile template
  4. Tool caches image builds (second build is instant if nothing changed)
  5. Per-project Dockerfile extension (.aishell/Dockerfile) is detected and applied
**Plans**: 5 plans

Plans:
- [x] 14-01-PLAN.md - Docker wrapper module with availability checks
- [x] 14-02-PLAN.md - Spinner and hash utilities for build support
- [x] 14-03-PLAN.md - Embedded Dockerfile templates and build logic
- [x] 14-04-PLAN.md - Per-project Dockerfile extension support
- [x] 14-05-PLAN.md - CLI integration and error messages

### Phase 15: Build Command
**Goal**: Users can build their sandbox environment with harness version pinning
**Depends on**: Phase 14
**Requirements**: CLI-03, CONF-07, CONF-08
**Success Criteria** (what must be TRUE):
  1. User can run `./aishell build` and see image being built
  2. User can run `./aishell build --with-claude=1.0.0` to pin version (single flag design)
  3. Build flags are persisted in `~/.aishell/state.edn`
  4. Subsequent builds use persisted flags without re-specifying
  5. Build with no flags clears previous state (base image only)
**Plans**: 3 plans

Plans:
- [x] 15-01-PLAN.md - State persistence module (EDN read/write)
- [x] 15-02-PLAN.md - Build subcommand with flag parsing and validation
- [x] 15-03-PLAN.md - Gap closure: fix version type coercion and Docker command vector

### Phase 16: Run Commands
**Goal**: Users can enter shell or run harnesses directly with full configuration support
**Depends on**: Phase 15
**Requirements**: CLI-04, CLI-05, CLI-06, DOCK-04, DOCK-05, DOCK-06, CONF-01, CONF-02, CONF-03, CONF-04, CONF-05, CONF-06, PLAT-01, PLAT-02
**Success Criteria** (what must be TRUE):
  1. User can run `./aishell` and enter a shell inside the container
  2. User can run `./aishell claude` and Claude Code starts in the container
  3. User can run `./aishell opencode` and OpenCode starts in the container
  4. Project is mounted at same path as host (e.g., /home/user/project inside container)
  5. Git identity (user.name, user.email) is available inside container
  6. Container is ephemeral (destroyed on exit, only mounted files persist)
  7. Per-project config.yaml mounts, env, ports, docker_args, pre_start work
  8. Tool works on Linux (x86_64, aarch64) and macOS (x86_64, aarch64)
**Plans**: 5 plans

Plans:
- [x] 16-01-PLAN.md - YAML config loading with project-first, global-fallback strategy
- [x] 16-02-PLAN.md - Docker run argument building (mounts, env, ports, git identity)
- [x] 16-03-PLAN.md - CLI integration for shell, claude, and opencode commands
- [x] 16-04-PLAN.md - Gap closure: env array format and pass-through args
- [x] 16-05-PLAN.md - Gap closure: pass-through args for harness commands

### Phase 17: Validation & Polish
**Goal**: Security validations and update awareness matching v1.2 hardening
**Depends on**: Phase 16
**Requirements**: CLI-07, VAL-01, VAL-02, VAL-03
**Success Criteria** (what must be TRUE):
  1. User can run `./aishell update` to check for updates
  2. Invalid version strings (e.g., `1.0; rm -rf /`) are rejected with clear error
  3. Dangerous docker_args patterns (--privileged, docker.sock) trigger warnings
  4. User is warned when embedded Dockerfile changed since last build
**Plans**: 4 plans

Plans:
- [x] 17-01-PLAN.md - Build --force flag, dockerfile hash storage, update command
- [x] 17-02-PLAN.md - Dangerous args validation and stale image warnings
- [x] 17-03-PLAN.md - Gap closure: vector docker_args and version cache invalidation
- [x] 17-04-PLAN.md - Gap closure: tokenize-docker-args vector handling

### Phase 18: Distribution
**Goal**: Users can install aishell via curl|bash one-liner
**Depends on**: Phase 17
**Requirements**: DIST-01, DIST-02, DIST-03
**Success Criteria** (what must be TRUE):
  1. User can run `curl -fsSL ... | bash` to install aishell
  2. aishell is distributed as single-file uberscript (one .clj file)
  3. Installer assumes Babashka is installed (provides clear error if missing)
**Plans**: 3 plans

Plans:
- [ ] 18-01-PLAN.md - Build script and uberscript packaging
- [ ] 18-02-PLAN.md - curl|bash installer with checksum verification
- [ ] 18-03-PLAN.md - Legacy cleanup and documentation update

## Progress

**Execution Order:**
Phases 13 through 18 execute sequentially. Decimal phases (if inserted) appear between their surrounding integers.

| Phase | Milestone | Plans Complete | Status | Completed |
|-------|-----------|----------------|--------|-----------|
| 13. Foundation | v2.0 | 2/2 | Complete | 2026-01-20 |
| 14. Docker Integration | v2.0 | 5/5 | Complete | 2026-01-20 |
| 15. Build Command | v2.0 | 3/3 | Complete | 2026-01-20 |
| 16. Run Commands | v2.0 | 5/5 | Complete | 2026-01-21 |
| 17. Validation & Polish | v2.0 | 4/4 | Complete | 2026-01-21 |
| 18. Distribution | v2.0 | 0/3 | Not started | - |

---
*Roadmap created: 2026-01-20*
*Last updated: 2026-01-21 after Phase 18 planning*
