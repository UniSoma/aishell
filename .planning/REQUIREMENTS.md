# Requirements: Agentic Harness Sandbox v2.5.0

**Defined:** 2026-01-25
**Core Value:** Run agentic AI harnesses in isolated, reproducible environments without polluting the host system

## v2.5.0 Requirements

Requirements for v2.5.0 Optimization & Polish milestone. Each maps to roadmap phases.

### CLI

- [x] **CLI-01**: Help output shows only installed harness commands (reads build state)
- [x] **CLI-02**: Build command accepts `--without-gitleaks` flag to skip Gitleaks installation
- [x] **CLI-03**: Gitleaks installation state tracked in ~/.aishell/state.edn
- [x] **CLI-04**: New `aishell exec <command>` subcommand runs command in container
- [x] **CLI-05**: Exec command uses all standard mounts/env from config
- [x] **CLI-06**: Exec command auto-detects TTY (allocate when stdin is terminal)
- [x] **CLI-07**: Exec command requires prior build (clear error if image missing)

### Config

- [x] **CFG-01**: Pre-start config accepts YAML list format
- [x] **CFG-02**: List items joined with ` && ` to form single command
- [x] **CFG-03**: String format remains supported (backwards compatible)

### Docker

- [ ] **DKR-01**: Claude Code installed via native installer (not npm)
- [ ] **DKR-02**: Codex CLI installed via binary download (not npm)
- [ ] **DKR-03**: Node.js only included when WITH_GEMINI=true
- [ ] **DKR-04**: Multi-stage build updated for conditional Node.js copy
- [ ] **DKR-05**: Architecture detection for Codex binary (amd64/arm64)
- [ ] **DKR-06**: Claude auto-updater disabled in container (DISABLE_AUTOUPDATER=1)
- [ ] **DKR-07**: Version pinning works for native installers

### Documentation

- [x] **DOC-01**: CONFIGURATION.md updated for pre_start list format with examples
- [x] **DOC-02**: CONFIGURATION.md updated for --without-gitleaks build flag
- [ ] **DOC-03**: HARNESSES.md updated for native installer methods (Claude, Codex)
- [ ] **DOC-04**: ARCHITECTURE.md updated for conditional Node.js build flow
- [x] **DOC-05**: README.md updated for `aishell exec` command usage
- [ ] **DOC-06**: DEVELOPMENT.md updated for binary install pattern in harness checklist
- [x] **DOC-07**: TROUBLESHOOTING.md updated for common exec issues

## Out of Scope

| Feature | Reason |
|---------|--------|
| Security pattern audit | Deferred to future milestone (todo exists) |
| Windows support | Docker on Windows is complex; deferred indefinitely |
| SSH agent forwarding | Deferred to future version |
| Workdir override for exec | Keep simple, use cd in command if needed |

## Traceability

| Requirement | Phase | Status |
|-------------|-------|--------|
| CLI-01 | Phase 28 | Complete |
| CLI-02 | Phase 28 | Complete |
| CLI-03 | Phase 28 | Complete |
| CFG-01 | Phase 28 | Complete |
| CFG-02 | Phase 28 | Complete |
| CFG-03 | Phase 28 | Complete |
| DOC-01 | Phase 28 | Complete |
| DOC-02 | Phase 28 | Complete |
| CLI-04 | Phase 29 | Complete |
| CLI-05 | Phase 29 | Complete |
| CLI-06 | Phase 29 | Complete |
| CLI-07 | Phase 29 | Complete |
| DOC-05 | Phase 29 | Complete |
| DOC-07 | Phase 29 | Complete |
| DKR-01 | — | Abandoned (binary install larger than npm) |
| DKR-02 | — | Abandoned (binary install larger than npm) |
| DKR-03 | — | Abandoned (binary install larger than npm) |
| DKR-04 | — | Abandoned (binary install larger than npm) |
| DKR-05 | — | Abandoned (binary install larger than npm) |
| DKR-06 | — | Abandoned (binary install larger than npm) |
| DKR-07 | — | Abandoned (binary install larger than npm) |
| DOC-03 | — | Abandoned (binary install larger than npm) |
| DOC-04 | — | Abandoned (binary install larger than npm) |
| DOC-06 | — | Abandoned (binary install larger than npm) |

**Coverage:**
- v2.5.0 requirements: 24 total
- Complete: 14 (Phase 28: 8, Phase 29: 6)
- Abandoned: 10 (DKR-01–07, DOC-03, DOC-04, DOC-06 — binary install approach abandoned)

---
*Requirements defined: 2026-01-25*
*Last updated: 2026-01-26 after Phase 29 completion*
