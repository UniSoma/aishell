# Requirements: Agentic Harness Sandbox

**Defined:** 2026-01-17
**Core Value:** Run agentic AI harnesses in isolated, reproducible environments without polluting the host system.

## v1 Requirements

Requirements for initial release. Each maps to roadmap phases.

### Container Core

- [ ] **CORE-01**: Container mounts project at same path as host (e.g., /home/user/project → /home/user/project in container)
- [ ] **CORE-02**: Container is ephemeral (destroyed on exit, only mounted files persist)
- [ ] **CORE-03**: Files created in container have correct host UID/GID ownership
- [ ] **CORE-04**: Container runs as non-root user with sudo access
- [ ] **CORE-05**: Basic CLI tools available (git, curl, vim, jq, ripgrep)
- [ ] **CORE-06**: Projects can extend base image via Dockerfile.sandbox

### Git Integration

- [ ] **GIT-01**: Git user.name and user.email from host ~/.gitconfig work in container
- [ ] **GIT-02**: Git recognizes project directory as safe (no dubious ownership errors)

### Harness Support

- [ ] **HARNESS-01**: User can run Claude Code with `<command> claude`
- [ ] **HARNESS-02**: User can run OpenCode with `<command> opencode`
- [ ] **HARNESS-03**: User can enter shell with `<command>` (no arguments)
- [ ] **HARNESS-04**: Claude Code config (~/.claude, ~/.claude.json) mounted from host
- [ ] **HARNESS-05**: OpenCode config (~/.config/opencode) mounted from host
- [ ] **HARNESS-06**: Claude Code is installed and runnable in container
- [ ] **HARNESS-07**: OpenCode is installed and runnable in container

### Distribution

- [ ] **DIST-01**: Tool installable via curl | bash one-liner
- [ ] **DIST-02**: Works on Linux with Docker Engine
- [ ] **DIST-03**: Installation creates command available in PATH
- [ ] **DIST-04**: Base Docker image published or buildable locally

## v2 Requirements

Deferred to future release. Tracked but not in current roadmap.

### Git Integration (Enhanced)

- **GIT-03**: SSH agent forwarding for git push/pull to private repos
- **GIT-04**: HTTPS credential helper passthrough
- **GIT-05**: GPG signing passthrough for signed commits

### Platform Support

- **PLAT-01**: Works on macOS with Docker Desktop
- **PLAT-02**: Installable via Homebrew tap
- **PLAT-03**: macOS SSH agent socket handling

### Advanced Features

- **ADV-01**: Auto-detect which harnesses are installed on host
- **ADV-02**: Named volumes for cache persistence (npm, cargo, pip)
- **ADV-03**: Network allowlist for security-conscious users
- **ADV-04**: Resource limits (CPU, memory) configuration

## Out of Scope

Explicitly excluded. Documented to prevent scope creep.

| Feature | Reason |
|---------|--------|
| Windows support | Docker Engine on Windows is complex; defer indefinitely |
| Persistent containers | Ephemeral is core design; named sessions contradict core value |
| Multiple accounts | Use separate config directories; not a v1 concern |
| GUI integration | CLI-focused tool; GUI support adds complexity |
| IDE plugins | Users can configure IDE to use CLI; not needed |
| Podman support | Different socket/API; research needed if requested |

## Traceability

Which phases cover which requirements. Updated during roadmap creation.

| Requirement | Phase | Status |
|-------------|-------|--------|
| CORE-01 | Phase 1 | Pending |
| CORE-02 | Phase 1 | Pending |
| CORE-03 | Phase 1 | Pending |
| CORE-04 | Phase 1 | Pending |
| CORE-05 | Phase 1 | Pending |
| CORE-06 | Phase 4 | Pending |
| GIT-01 | Phase 2 | Pending |
| GIT-02 | Phase 2 | Pending |
| HARNESS-01 | Phase 3 | Pending |
| HARNESS-02 | Phase 3 | Pending |
| HARNESS-03 | Phase 3 | Pending |
| HARNESS-04 | Phase 3 | Pending |
| HARNESS-05 | Phase 3 | Pending |
| HARNESS-06 | Phase 3 | Pending |
| HARNESS-07 | Phase 3 | Pending |
| DIST-01 | Phase 5 | Pending |
| DIST-02 | Phase 1 | Pending |
| DIST-03 | Phase 5 | Pending |
| DIST-04 | Phase 1 | Pending |

**Coverage:**
- v1 requirements: 18 total
- Mapped to phases: 18
- Unmapped: 0 ✓

---
*Requirements defined: 2026-01-17*
*Last updated: 2026-01-17 after initial definition*
