# Requirements: Agentic Harness Sandbox

**Defined:** 2026-01-18
**Core Value:** Run agentic AI harnesses in isolated, reproducible environments without polluting the host system.

## v1.1 Requirements

Requirements for per-project runtime configuration.

### Runtime Configuration

- [ ] **RCONF-01**: User can create `.aishell/run.conf` to configure container runtime
- [ ] **RCONF-02**: Config file is sourced safely with validation (no code injection)
- [ ] **RCONF-03**: Config syntax errors produce helpful error messages

### Volume Mounts

- [ ] **MOUNT-01**: User can specify additional mounts via `MOUNTS` variable
- [ ] **MOUNT-02**: Paths support `$HOME` expansion
- [ ] **MOUNT-03**: Mounts are created as read-write at same path in container

### Environment Variables

- [ ] **ENV-01**: User can specify env vars via `ENV` variable
- [ ] **ENV-02**: `VAR` syntax passes through value from host
- [ ] **ENV-03**: `VAR=value` syntax sets literal value

### Port Mappings

- [ ] **PORT-01**: User can specify port mappings via `PORTS` variable
- [ ] **PORT-02**: Supports `host:container` format

### Docker Arguments

- [ ] **DARG-01**: User can specify extra docker run args via `DOCKER_ARGS`
- [ ] **DARG-02**: Args are passed through to docker run command

### Pre-Start Command

- [x] **PRE-01**: User can specify pre-start command via `PRE_START`
- [x] **PRE-02**: Command runs inside container before main process
- [x] **PRE-03**: Command runs in background (does not block shell/harness)

## v2 Requirements

Deferred to future release.

### SSH/Credentials

- **SSH-01**: SSH agent forwarding for git push to private repos
- **SSH-02**: GPG signing passthrough

### Platform Support

- **PLAT-01**: macOS Docker Desktop support
- **PLAT-02**: macOS SSH socket path handling

## Out of Scope

| Feature | Reason |
|---------|--------|
| Windows support | Docker on Windows too complex |
| Persistent containers | Ephemeral is core design choice |
| GUI integration | CLI-focused tool |
| Complex orchestration (docker-compose style) | PRE_START covers simple sidecars; complex orchestration is different tool |

## Traceability

| Requirement | Phase | Status |
|-------------|-------|--------|
| RCONF-01 | Phase 9 | Complete |
| RCONF-02 | Phase 9 | Complete |
| RCONF-03 | Phase 9 | Complete |
| MOUNT-01 | Phase 9 | Complete |
| MOUNT-02 | Phase 9 | Complete |
| MOUNT-03 | Phase 9 | Complete |
| ENV-01 | Phase 9 | Complete |
| ENV-02 | Phase 9 | Complete |
| ENV-03 | Phase 9 | Complete |
| PORT-01 | Phase 9 | Complete |
| PORT-02 | Phase 9 | Complete |
| DARG-01 | Phase 9 | Complete |
| DARG-02 | Phase 9 | Complete |
| PRE-01 | Phase 10 | Complete |
| PRE-02 | Phase 10 | Complete |
| PRE-03 | Phase 10 | Complete |

**Coverage:**
- v1.1 requirements: 16 total
- Mapped to phases: 16
- Unmapped: 0

---
*Requirements defined: 2026-01-18*
*Last updated: 2026-01-18 after Phase 10 completion*
