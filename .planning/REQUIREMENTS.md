# Requirements: Agentic Harness Sandbox

**Defined:** 2026-01-19
**Core Value:** Run agentic AI harnesses in isolated, reproducible environments without polluting the host system.

## v1.2 Requirements

Requirements for hardening and edge case fixes.

### Input Validation

- [x] **VALID-01**: Port mapping regex accepts IP binding format (e.g., 127.0.0.1:8080:80)
- [x] **VALID-02**: Version strings validated against semver-like pattern before npm/curl use
- [x] **VALID-03**: HOME environment variable validated before use with fallback

### Robustness

- [x] **ROBUST-01**: Temp file cleanup uses single consolidated handler (no trap override bugs)
- [x] **ROBUST-02**: Signal handling during build phase propagates cleanly on Ctrl+C
- [x] **ROBUST-03**: `--init` flag added to docker run for zombie process reaping

### Security Awareness

- [x] **SEC-01**: Dangerous DOCKER_ARGS patterns trigger warning (--privileged, docker.sock)

### Maintenance

- [ ] **MAINT-01**: Update check warns when embedded Dockerfile hash differs from built image

### Documentation

- [ ] **DOC-01**: run.conf parsing limits documented (no escaped quotes, strict format)
- [ ] **DOC-02**: safe.directory behavior documented (may modify host gitconfig)

## v2 Requirements

Deferred to future release.

- **SSH-01**: SSH agent forwarding for git push/pull
- **GPG-01**: GPG signing passthrough
- **MAC-01**: macOS host support

## Out of Scope

| Feature | Reason |
|---------|--------|
| Full shell quoting in run.conf | Complexity not justified; users can use DOCKER_ARGS -e for complex values |
| State file locking | Race condition is rare; "last writer wins" is acceptable |
| Blocking dangerous DOCKER_ARGS | Warning is sufficient; users may have legitimate needs |

## Traceability

| Requirement | Phase | Status |
|-------------|-------|--------|
| VALID-01 | Phase 11 | Complete |
| VALID-02 | Phase 11 | Complete |
| VALID-03 | Phase 11 | Complete |
| ROBUST-01 | Phase 11 | Complete |
| ROBUST-02 | Phase 11 | Complete |
| ROBUST-03 | Phase 11 | Complete |
| SEC-01 | Phase 11 | Complete |
| MAINT-01 | Phase 12 | Pending |
| DOC-01 | Phase 12 | Pending |
| DOC-02 | Phase 12 | Pending |

**Coverage:**
- v1.2 requirements: 10 total
- Mapped to phases: 10
- Unmapped: 0 ✓

---
*Requirements defined: 2026-01-19*
*Last updated: 2026-01-19 after Phase 11 completion*
