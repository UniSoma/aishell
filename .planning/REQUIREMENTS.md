# Requirements: Agentic Harness Sandbox

**Defined:** 2026-02-18
**Core Value:** Run agentic AI harnesses in isolated, reproducible environments without polluting the host system

## v3.5.0 Requirements

Requirements for pi coding agent support. Each maps to roadmap phases.

### Harness Integration

- [ ] **HARNESS-01**: User can build with `--with-pi` flag to include pi coding agent
- [ ] **HARNESS-02**: User can run `aishell pi` to launch pi coding agent in container
- [ ] **HARNESS-03**: User can pin pi version with `--with-pi=VERSION` at build time
- [ ] **HARNESS-04**: Pi config directory (`~/.pi/`) mounted from host to persist auth and settings
- [ ] **HARNESS-05**: Pi installed via npm in harness volume (same pattern as Claude/Codex/Gemini)
- [ ] **HARNESS-06**: `aishell check` displays pi installation status and version
- [ ] **HARNESS-07**: Pi shown in `aishell --help` when installed
- [ ] **HARNESS-08**: Pi pass-through args work (e.g., `aishell pi --print "hello"`)
- [ ] **HARNESS-09**: Entrypoint alias `pi` available inside container shell

### Foundation Image

- [ ] **FOUND-01**: fd-find package installed in foundation image with `fd` symlink
- [ ] **FOUND-02**: Foundation image rebuild triggered on next `aishell update --force`

### Environment

- [ ] **ENV-01**: PI_CODING_AGENT_DIR environment variable passed through to container
- [ ] **ENV-02**: PI_SKIP_VERSION_CHECK environment variable passed through to container

### Documentation

- [ ] **DOCS-01**: All user-facing CLI changes reflected in docs/ (README.md, docs/ARCHITECTURE.md, docs/CONFIGURATION.md, docs/HARNESSES.md, docs/TROUBLESHOOTING.md, docs/DEVELOPMENT.md)

## Future Requirements

### Pi Ecosystem

- **PI-F01**: Pi extensions/skills mounting from host `.pi/` directory
- **PI-F02**: Pi package installation support (`pi install`) with git/npm inside container
- **PI-F03**: Pi RPC mode integration for IDE embedding workflows

## Out of Scope

| Feature | Reason |
|---------|--------|
| MCP support for pi | Pi explicitly rejects MCP by design — extensibility through TypeScript extensions |
| Pi OAuth login flow inside container | Interactive browser-based OAuth requires host browser; use API keys instead |
| Pi image display (Kitty graphics) | Container terminals don't support Kitty graphics protocol; pi falls back gracefully |
| Pi clipboard integration | No display server in container; pi uses OSC 52 fallback automatically |

## Traceability

| Requirement | Phase | Status |
|-------------|-------|--------|
| HARNESS-01 | — | Pending |
| HARNESS-02 | — | Pending |
| HARNESS-03 | — | Pending |
| HARNESS-04 | — | Pending |
| HARNESS-05 | — | Pending |
| HARNESS-06 | — | Pending |
| HARNESS-07 | — | Pending |
| HARNESS-08 | — | Pending |
| HARNESS-09 | — | Pending |
| FOUND-01 | — | Pending |
| FOUND-02 | — | Pending |
| ENV-01 | — | Pending |
| ENV-02 | — | Pending |
| DOCS-01 | — | Pending |

**Coverage:**
- v3.5.0 requirements: 14 total
- Mapped to phases: 0
- Unmapped: 14 ⚠️

---
*Requirements defined: 2026-02-18*
*Last updated: 2026-02-18 after initial definition*
