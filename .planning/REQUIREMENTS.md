# Requirements: aishell v2.0

**Defined:** 2026-01-20
**Core Value:** Run agentic AI harnesses in isolated, reproducible environments without polluting the host system.

## v2.0 Requirements

Requirements for Babashka rewrite. Each maps to roadmap phases.

### CLI Core

- [x] **CLI-01**: User can run `aishell --version` to see version
- [x] **CLI-02**: User can run `aishell --help` to see available commands
- [x] **CLI-03**: User can run `aishell build` to build the Docker image
- [ ] **CLI-04**: User can run `aishell` to enter a shell in the container
- [ ] **CLI-05**: User can run `aishell claude` to run Claude Code directly
- [ ] **CLI-06**: User can run `aishell opencode` to run OpenCode directly
- [ ] **CLI-07**: User can run `aishell update` to check for updates
- [x] **CLI-08**: CLI provides clear error messages for invalid commands/options

### Docker Integration

- [x] **DOCK-01**: Tool checks Docker availability before operations
- [x] **DOCK-02**: Tool builds Docker image with embedded Dockerfile template
- [x] **DOCK-03**: Tool caches image builds (rebuilds only when needed)
- [ ] **DOCK-04**: Tool runs containers with proper mounts (project at same path as host)
- [ ] **DOCK-05**: Tool passes git identity (user.name, user.email) to container
- [ ] **DOCK-06**: Tool configures git safe.directory in container
- [x] **DOCK-07**: Container is ephemeral (--rm flag, destroyed on exit)
- [x] **DOCK-08**: Tool supports per-project Dockerfile extension (.aishell/Dockerfile)

### Configuration

- [ ] **CONF-01**: Tool reads per-project config from `.aishell/config.yaml`
- [ ] **CONF-02**: Config supports `mounts` for additional volume mounts
- [ ] **CONF-03**: Config supports `env` for environment passthrough and literals
- [ ] **CONF-04**: Config supports `ports` for port mappings
- [ ] **CONF-05**: Config supports `docker_args` for arbitrary docker run flags
- [ ] **CONF-06**: Config supports `pre_start` for background commands
- [x] **CONF-07**: Tool persists state in `~/.aishell/state.edn`
- [x] **CONF-08**: Build flags (--claude-version, --opencode-version) are persisted

### Validation

- [ ] **VAL-01**: Tool validates version strings before use
- [ ] **VAL-02**: Tool warns about dangerous docker_args patterns
- [ ] **VAL-03**: Tool detects Dockerfile hash changes and warns if rebuild needed

### Cross-Platform

- [ ] **PLAT-01**: Tool works on Linux (x86_64, aarch64)
- [ ] **PLAT-02**: Tool works on macOS (x86_64, aarch64)
- [x] **PLAT-03**: Tool handles platform-specific path conventions

### Distribution

- [ ] **DIST-01**: Tool can be installed via curl|bash one-liner
- [ ] **DIST-02**: Tool is distributed as single-file uberscript
- [ ] **DIST-03**: Babashka is assumed installed (not bundled)

## v2.1 Requirements

Deferred to future release.

### Windows Support

- **WIN-01**: Tool works on Windows with Docker Desktop
- **WIN-02**: Tool works on Windows with WSL2 + Docker
- **WIN-03**: Tool handles Windows path conventions

### Advanced Features

- **ADV-01**: SSH agent forwarding for git push/pull
- **ADV-02**: GPG signing passthrough

## Out of Scope

| Feature | Reason |
|---------|--------|
| Backward compat with run.conf | Clean break to YAML, simpler implementation |
| Bundling Babashka | Users install Babashka separately (single binary, easy) |
| Windows in v2.0 | High complexity, defer to v2.1 |
| Session persistence | Ephemeral is the design choice |
| Docker pod integration | Shell wrapping simpler, output streaming works |

## Traceability

Which phases cover which requirements. Updated during roadmap creation.

| Requirement | Phase | Status |
|-------------|-------|--------|
| CLI-01 | Phase 13 | Complete |
| CLI-02 | Phase 13 | Complete |
| CLI-03 | Phase 15 | Complete |
| CLI-04 | Phase 16 | Pending |
| CLI-05 | Phase 16 | Pending |
| CLI-06 | Phase 16 | Pending |
| CLI-07 | Phase 17 | Pending |
| CLI-08 | Phase 13 | Complete |
| DOCK-01 | Phase 14 | Complete |
| DOCK-02 | Phase 14 | Complete |
| DOCK-03 | Phase 14 | Complete |
| DOCK-04 | Phase 16 | Pending |
| DOCK-05 | Phase 16 | Pending |
| DOCK-06 | Phase 16 | Pending |
| DOCK-07 | Phase 14 | Complete |
| DOCK-08 | Phase 14 | Complete |
| CONF-01 | Phase 16 | Pending |
| CONF-02 | Phase 16 | Pending |
| CONF-03 | Phase 16 | Pending |
| CONF-04 | Phase 16 | Pending |
| CONF-05 | Phase 16 | Pending |
| CONF-06 | Phase 16 | Pending |
| CONF-07 | Phase 15 | Complete |
| CONF-08 | Phase 15 | Complete |
| VAL-01 | Phase 17 | Pending |
| VAL-02 | Phase 17 | Pending |
| VAL-03 | Phase 17 | Pending |
| PLAT-01 | Phase 16 | Pending |
| PLAT-02 | Phase 16 | Pending |
| PLAT-03 | Phase 13 | Complete |
| DIST-01 | Phase 18 | Pending |
| DIST-02 | Phase 18 | Pending |
| DIST-03 | Phase 18 | Pending |

**Coverage:**
- v2.0 requirements: 33 total
- Mapped to phases: 33
- Unmapped: 0

---
*Requirements defined: 2026-01-20*
*Last updated: 2026-01-20 after Phase 15 completion*
