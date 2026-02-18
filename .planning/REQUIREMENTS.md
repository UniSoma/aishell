# Requirements: Agentic Harness Sandbox

**Defined:** 2026-02-18
**Core Value:** Run agentic AI harnesses in isolated, reproducible environments without polluting the host system

## v3.7.0 Requirements

Requirements for OpenSpec support milestone. Each maps to roadmap phases.

### Build

- [x] **BUILD-01**: User can enable OpenSpec with `--with-openspec` build flag
- [x] **BUILD-02**: User can pin OpenSpec version with `--with-openspec=VERSION`
- [x] **BUILD-03**: OpenSpec enabled/version state persisted in state.edn

### Volume

- [x] **VOL-01**: OpenSpec npm package (`@fission-ai/openspec`) installed in harness volume when enabled
- [x] **VOL-02**: Volume hash includes OpenSpec state for proper cache invalidation

### Documentation

- [x] **DOCS-01**: All user-facing CLI changes reflected in docs/ (README.md, ARCHITECTURE.md, CONFIGURATION.md, HARNESSES.md, TROUBLESHOOTING.md, DEVELOPMENT.md)

### Release

- [ ] **REL-01**: Version bumped to 3.7.0 and CHANGELOG.md updated

## Future Requirements

None currently deferred.

## Out of Scope

| Feature | Reason |
|---------|--------|
| `aishell openspec` subcommand | Not a harness; users run `openspec` inside container via `aishell shell` |
| OpenSpec config directory mounting | OpenSpec uses project-local `.openspec/` dir, already available via project mount |
| OpenSpec API key passthrough | OpenSpec requires no API keys |

## Traceability

| Requirement | Phase | Status |
|-------------|-------|--------|
| BUILD-01 | Phase 63 | Complete |
| BUILD-02 | Phase 63 | Complete |
| BUILD-03 | Phase 63 | Complete |
| VOL-01 | Phase 63 | Complete |
| VOL-02 | Phase 63 | Complete |
| DOCS-01 | Phase 64 | Complete |
| REL-01 | Phase 65 | Pending |

**Coverage:**
- v3.7.0 requirements: 7 total
- Mapped to phases: 7
- Unmapped: 0

---
*Requirements defined: 2026-02-18*
*Last updated: 2026-02-18 after roadmap creation*
