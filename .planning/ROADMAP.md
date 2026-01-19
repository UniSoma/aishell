# Roadmap: Agentic Harness Sandbox

## Milestones

- ✅ **v1.0 MVP** - Phases 1-8 (shipped 2026-01-18)
- ✅ **v1.1 Runtime Config** - Phases 9-10 (shipped 2026-01-19)
- 🚧 **v1.2 Hardening & Edge Cases** - Phases 11-12 (in progress)

## Phases

<details>
<summary>✅ v1.0 MVP (Phases 1-8) - SHIPPED 2026-01-18</summary>

Phases 1-8 delivered the core sandbox functionality:
- Docker container lifecycle management
- Project mounting with git identity passthrough
- Claude Code and OpenCode harness integration
- Per-project Dockerfile extension support
- Version pinning and explicit build workflow
- curl|bash installation

See git history for phase details.

</details>

<details>
<summary>✅ v1.1 Runtime Config (Phases 9-10) - SHIPPED 2026-01-19</summary>

Phases 9-10 delivered runtime configuration:
- .aishell/run.conf for per-project runtime config
- MOUNTS, ENV, PORTS, DOCKER_ARGS variables
- PRE_START for background sidecar services

See git history for phase details.

</details>

### 🚧 v1.2 Hardening & Edge Cases (In Progress)

**Milestone Goal:** Harden input validation, improve robustness for edge cases, and document known limitations.

#### Phase 11: Code Hardening
**Goal**: Eliminate edge case bugs and add defensive validation across the codebase
**Depends on**: Phase 10 (v1.1 complete)
**Requirements**: VALID-01, VALID-02, VALID-03, ROBUST-01, ROBUST-02, ROBUST-03, SEC-01
**Success Criteria** (what must be TRUE):
  1. Port mapping with IP binding (e.g., 127.0.0.1:8080:80) is accepted and passed to docker run
  2. Invalid version strings (with shell metacharacters) are rejected before reaching npm/curl
  3. Script handles missing HOME gracefully with fallback behavior
  4. Ctrl+C during build phase exits cleanly without orphaned processes or temp files
  5. Running container with --privileged or docker.sock mount prints a warning to stderr
**Plans**: TBD

Plans:
- [ ] 11-01: TBD
- [ ] 11-02: TBD

#### Phase 12: Maintenance & Documentation
**Goal**: Add update detection and document known limitations for users
**Depends on**: Phase 11
**Requirements**: MAINT-01, DOC-01, DOC-02
**Success Criteria** (what must be TRUE):
  1. `aishell` warns when embedded Dockerfile differs from the hash at build time
  2. README documents run.conf parsing limits (no escaped quotes, one value per line)
  3. README documents safe.directory behavior and its effect on host gitconfig
**Plans**: TBD

Plans:
- [ ] 12-01: TBD

## Progress

| Phase | Milestone | Plans Complete | Status | Completed |
|-------|-----------|----------------|--------|-----------|
| 1-8 | v1.0 | 16/16 | Complete | 2026-01-18 |
| 9 | v1.1 | 3/3 | Complete | 2026-01-19 |
| 10 | v1.1 | 1/1 | Complete | 2026-01-19 |
| 11 - Code Hardening | v1.2 | 0/TBD | Not started | - |
| 12 - Maintenance & Docs | v1.2 | 0/TBD | Not started | - |

---
*Roadmap created: 2026-01-19 for v1.2 milestone*
