# Requirements: aishell v2.0

**Defined:** 2026-01-20
**Core Value:** Run agentic AI harnesses in isolated, reproducible environments without polluting the host system.

## v2.0 Requirements

Requirements for Babashka rewrite. Each maps to roadmap phases.

### CLI Core

- [ ] **CLI-01**: User can run `aishell --version` to see version
- [ ] **CLI-02**: User can run `aishell --help` to see available commands
- [ ] **CLI-03**: User can run `aishell build` to build the Docker image
- [ ] **CLI-04**: User can run `aishell` to enter a shell in the container
- [ ] **CLI-05**: User can run `aishell claude` to run Claude Code directly
- [ ] **CLI-06**: User can run `aishell opencode` to run OpenCode directly
- [ ] **CLI-07**: User can run `aishell update` to check for updates
- [ ] **CLI-08**: CLI provides clear error messages for invalid commands/options

### Docker Integration

- [ ] **DOCK-01**: Tool checks Docker availability before operations
- [ ] **DOCK-02**: Tool builds Docker image with embedded Dockerfile template
- [ ] **DOCK-03**: Tool caches image builds (rebuilds only when needed)
- [ ] **DOCK-04**: Tool runs containers with proper mounts (project at same path as host)
- [ ] **DOCK-05**: Tool passes git identity (user.name, user.email) to container
- [ ] **DOCK-06**: Tool configures git safe.directory in container
- [ ] **DOCK-07**: Container is ephemeral (--rm flag, destroyed on exit)
- [ ] **DOCK-08**: Tool supports per-project Dockerfile extension (.aishell/Dockerfile)

### Configuration

- [ ] **CONF-01**: Tool reads per-project config from `.aishell/config.yaml`
- [ ] **CONF-02**: Config supports `mounts` for additional volume mounts
- [ ] **CONF-03**: Config supports `env` for environment passthrough and literals
- [ ] **CONF-04**: Config supports `ports` for port mappings
- [ ] **CONF-05**: Config supports `docker_args` for arbitrary docker run flags
- [ ] **CONF-06**: Config supports `pre_start` for background commands
- [ ] **CONF-07**: Tool persists state in `~/.aishell/state.edn`
- [ ] **CONF-08**: Build flags (--claude-version, --opencode-version) are persisted

### Validation

- [ ] **VAL-01**: Tool validates version strings before use
- [ ] **VAL-02**: Tool warns about dangerous docker_args patterns
- [ ] **VAL-03**: Tool detects Dockerfile hash changes and warns if rebuild needed

### Cross-Platform

- [ ] **PLAT-01**: Tool works on Linux (x86_64, aarch64)
- [ ] **PLAT-02**: Tool works on macOS (x86_64, aarch64)
- [ ] **PLAT-03**: Tool handles platform-specific path conventions

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
| CLI-01 | TBD | Pending |
| CLI-02 | TBD | Pending |
| CLI-03 | TBD | Pending |
| CLI-04 | TBD | Pending |
| CLI-05 | TBD | Pending |
| CLI-06 | TBD | Pending |
| CLI-07 | TBD | Pending |
| CLI-08 | TBD | Pending |
| DOCK-01 | TBD | Pending |
| DOCK-02 | TBD | Pending |
| DOCK-03 | TBD | Pending |
| DOCK-04 | TBD | Pending |
| DOCK-05 | TBD | Pending |
| DOCK-06 | TBD | Pending |
| DOCK-07 | TBD | Pending |
| DOCK-08 | TBD | Pending |
| CONF-01 | TBD | Pending |
| CONF-02 | TBD | Pending |
| CONF-03 | TBD | Pending |
| CONF-04 | TBD | Pending |
| CONF-05 | TBD | Pending |
| CONF-06 | TBD | Pending |
| CONF-07 | TBD | Pending |
| CONF-08 | TBD | Pending |
| VAL-01 | TBD | Pending |
| VAL-02 | TBD | Pending |
| VAL-03 | TBD | Pending |
| PLAT-01 | TBD | Pending |
| PLAT-02 | TBD | Pending |
| PLAT-03 | TBD | Pending |
| DIST-01 | TBD | Pending |
| DIST-02 | TBD | Pending |
| DIST-03 | TBD | Pending |

**Coverage:**
- v2.0 requirements: 33 total
- Mapped to phases: 0
- Unmapped: 33

---
*Requirements defined: 2026-01-20*
*Last updated: 2026-01-20 after initial definition*
