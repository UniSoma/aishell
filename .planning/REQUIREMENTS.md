# Requirements: Agentic Harness Sandbox

**Defined:** 2026-01-31
**Core Value:** Run agentic AI harnesses in isolated, reproducible environments without polluting the host system.

## v2.8.0 Requirements

Requirements for harness tool decoupling. Each maps to roadmap phases.

### Foundation Image

- [x] **FNDN-01**: Foundation image contains Debian + Node.js + system tools but no harness npm packages
- [x] **FNDN-02**: Foundation image tagged as `aishell:foundation` (replaces `aishell:base`)
- [x] **FNDN-03**: Clear error message when user's `.aishell/Dockerfile` uses `FROM aishell:base` with fix instructions

### Harness Volume

- [x] **HVOL-01**: Harness tools installed into Docker named volume via `npm install -g --prefix`
- [x] **HVOL-02**: Harness volume mounted at runtime with PATH and NODE_PATH environment variables configured
- [x] **HVOL-03**: Per-project volumes named `aishell-harness-{hash}` where hash derives from harness flags+versions
- [x] **HVOL-04**: Lazy volume population on first container run if volume is empty or stale
- [x] **HVOL-05**: Stale volume detection comparing state hash against current harness flags+versions
- [x] **HVOL-06**: Volume shared across projects with identical harness combinations (same hash = same volume)

### Build UX

- [x] **BUILD-01**: `aishell build` handles both foundation image and harness volume transparently
- [x] **BUILD-02**: Foundation image only rebuilds when system dependencies change (Dockerfile template changes)
- [x] **BUILD-03**: Harness volume only rebuilds when harness versions or flags change

### Cache Invalidation

- [x] **CACHE-01**: Extension image tracking references foundation image ID instead of base image ID
- [x] **CACHE-02**: State schema tracks foundation-hash and harness-volume-hash as separate fields

### Migration

- [x] **MIGR-01**: State file schema migrated from old format on first run (backward compatible read)
- [x] **MIGR-02**: Existing extensions auto-rebuild on first build after upgrade (foundation ID changed)

### Volume Cleanup

- [ ] **CLEAN-01**: Command to list orphaned harness volumes (not referenced by current state)
- [ ] **CLEAN-02**: Command to prune orphaned harness volumes with confirmation

### Documentation

- [ ] **DOCS-01**: All user-facing CLI changes reflected in docs/ (README.md, ARCHITECTURE.md, CONFIGURATION.md, HARNESSES.md, TROUBLESHOOTING.md, DEVELOPMENT.md)

## Future Requirements

Deferred to later milestones.

### Host-native Bind Mounts
- **HBIND-01**: Option to bind-mount host-installed harness tools instead of volume
- **HBIND-02**: Auto-detect harness tools on host PATH and offer bind-mount

### Advanced Volume Management
- **AVOL-01**: Volume size reporting in `aishell doctor`
- **AVOL-02**: Volume age reporting and auto-cleanup policy

## Out of Scope

| Feature | Reason |
|---------|--------|
| Separate harness Docker image | First-principles analysis showed foundation image can populate volumes directly — no separate image needed |
| `aishell:base` alias for backward compat | Clean break chosen — alias adds maintenance burden and delays migration |
| COPY --link layer inversion | Over-engineered for local dev workflow; volume approach is simpler |
| Host-native bind mounts | Requires host Node.js assumption; defer to future |
| Windows volume support | Windows is out of scope for entire project |

## Traceability

| Requirement | Phase | Status |
|-------------|-------|--------|
| FNDN-01 | Phase 35 | Complete |
| FNDN-02 | Phase 35 | Complete |
| FNDN-03 | Phase 35 | Complete |
| BUILD-02 | Phase 35 | Complete |
| HVOL-01 | Phase 36 | Complete |
| HVOL-02 | Phase 36 | Complete |
| HVOL-03 | Phase 36 | Complete |
| HVOL-06 | Phase 36 | Complete |
| BUILD-03 | Phase 36 | Complete |
| BUILD-01 | Phase 37 | Complete |
| HVOL-04 | Phase 37 | Complete |
| HVOL-05 | Phase 37 | Complete |
| CACHE-01 | Phase 37 | Complete |
| CACHE-02 | Phase 37 | Complete |
| MIGR-01 | Phase 37 | Complete |
| MIGR-02 | Phase 37 | Complete |
| CLEAN-01 | Phase 38 | Pending |
| CLEAN-02 | Phase 38 | Pending |
| DOCS-01 | Phase 38 | Pending |

**Coverage:**
- v2.8.0 requirements: 19 total
- Mapped to phases: 19
- Unmapped: 0

---
*Requirements defined: 2026-01-31*
*Last updated: 2026-02-01 after Phase 37 completion*
