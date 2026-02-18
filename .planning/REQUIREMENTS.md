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

- [x] **REL-01**: Version bumped to 3.7.0 and CHANGELOG.md updated

## v3.8.0 Requirements

Requirements for Global Base Image Customization milestone. Each maps to roadmap phases.

### Base Image

- [x] **BASE-01**: Global `~/.aishell/Dockerfile` is detected and built as `aishell:base` image with Docker labels for cache tracking
- [x] **BASE-02**: When no global Dockerfile exists, `aishell:base` is a Docker tag alias for `aishell:foundation`
- [x] **BASE-03**: Base image rebuilds automatically when global Dockerfile content changes (content hash comparison)
- [x] **BASE-04**: Foundation image change cascades to base image rebuild, which cascades to extension image rebuilds
- [x] **BASE-05**: `aishell check` shows base image status: "custom (~/.aishell/Dockerfile)" or "default (foundation alias)"
- [x] **BASE-06**: `aishell setup --force` rebuilds base image; `aishell update --force` rebuilds base image
- [x] **BASE-07**: `aishell volumes prune` includes orphaned base images in cleanup
- [x] **BASE-08**: Project extension Dockerfiles accept `FROM aishell:base` (legacy validation removed)

### Documentation

- [ ] **BASE-09**: All user-facing documentation updated for base image customization feature

### Release

- [ ] **BASE-10**: Version bumped and CHANGELOG.md updated

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
| REL-01 | Phase 65 | Complete |
| BASE-01 | Phase 66 | Complete |
| BASE-02 | Phase 66 | Complete |
| BASE-03 | Phase 66 | Complete |
| BASE-04 | Phase 66 | Complete |
| BASE-05 | Phase 66 | Complete |
| BASE-06 | Phase 66 | Complete |
| BASE-07 | Phase 66 | Complete |
| BASE-08 | Phase 66 | Complete |
| BASE-09 | Phase 66 | Pending |
| BASE-10 | Phase 66 | Pending |

**Coverage:**
- v3.7.0 requirements: 7 total, 7 mapped, 0 unmapped
- v3.8.0 requirements: 10 total, 10 mapped, 0 unmapped

---
*Requirements defined: 2026-02-18*
*Last updated: 2026-02-18 after Phase 66 planning*
