# Requirements: Agentic Harness Sandbox v2.9.0

**Defined:** 2026-02-01
**Core Value:** Run agentic AI harnesses in isolated, reproducible environments without polluting the host system.

## v2.9.0 Requirements

Requirements for tmux opt-in and plugin support. Each maps to roadmap phases.

### Opt-in Behavior

- [x] **TMUX-01**: `aishell build --with-tmux` enables tmux; default behavior is no tmux
- [x] **TMUX-02**: `:with-tmux` flag stored in state.edn and used by downstream commands
- [ ] **TMUX-03**: Entrypoint conditionally starts tmux session (skips when disabled)
- [ ] **TMUX-04**: `attach` command validates tmux is enabled and fails gracefully with helpful message when not
- [ ] **TMUX-05**: Users upgrading from v2.7-2.8 see migration warning about tmux behavioral change

### Config Mounting

- [x] **CONF-01**: User's `~/.tmux.conf` mounted read-only into container when tmux is enabled
- [x] **CONF-02**: Missing `~/.tmux.conf` on host is handled gracefully (no error, just skipped)

### Plugin Management

- [ ] **PLUG-01**: `tmux.plugins` list in `.aishell/config.yaml` declares plugins (format: `owner/repo`)
- [ ] **PLUG-02**: TPM (tmux plugin manager) installed into harness volume at `/tools/tmux/plugins/tpm`
- [ ] **PLUG-03**: Declared plugins installed non-interactively during `aishell build` / `aishell update`
- [ ] **PLUG-04**: Plugin path bridging: symlink from `/tools/tmux/plugins` to `~/.tmux/plugins` in entrypoint
- [ ] **PLUG-05**: TPM run command appended to tmux config at container startup
- [ ] **PLUG-06**: Plugin format validated (`owner/repo` pattern) during config parsing

### Session Persistence

- [ ] **PERS-01**: tmux-resurrect state directory mounted from host when enabled in config
- [ ] **PERS-02**: `tmux.resurrect` section in config.yaml configures state persistence
- [ ] **PERS-03**: Process restoration disabled by default (only window/pane layout restored)

### Documentation

- [ ] **DOCS-01**: All user-facing CLI changes reflected in docs/ (README, ARCHITECTURE, CONFIGURATION, HARNESSES, TROUBLESHOOTING, DEVELOPMENT)

## Future Requirements

Deferred to later milestones.

### Plugin Enhancements

- **PLUG-F01**: Plugin version pinning (specific git tags/commits)
- **PLUG-F02**: Incremental plugin updates (add/remove without full volume rebuild)
- **PLUG-F03**: `aishell tmux-plugins` troubleshooting command

### Session Enhancements

- **PERS-F01**: tmux-continuum auto-save integration
- **PERS-F02**: Multiple resurrect state directories per project

## Out of Scope

| Feature | Reason |
|---------|--------|
| Per-project tmux.conf | Global host config only; per-project adds complexity without clear value |
| Runtime plugin installation (via TPM at container start) | Slow, unreproducible; build-time is better |
| Plugin conflict resolution | Trust TPM to handle; not our domain |
| tmux theme management | User preference via their own .tmux.conf |
| Session resurrection across container restarts by default | Opt-in via resurrect config; ephemeral is the design principle |
| GUI plugin manager | Scope creep; CLI-focused tool |
| Automatic plugin updates | Version drift risk; explicit update via `aishell update` |
| Plugin installation in foundation image | Version drift, slow rebuilds; harness volume is the right layer |

## Traceability

| Requirement | Phase | Status |
|-------------|-------|--------|
| TMUX-01 | Phase 39 | Complete |
| TMUX-02 | Phase 39 | Complete |
| TMUX-03 | Phase 41 | Pending |
| TMUX-04 | Phase 43 | Pending |
| TMUX-05 | Phase 43 | Pending |
| CONF-01 | Phase 39 | Complete |
| CONF-02 | Phase 39 | Complete |
| PLUG-01 | Phase 40 | Pending |
| PLUG-02 | Phase 40 | Pending |
| PLUG-03 | Phase 40 | Pending |
| PLUG-04 | Phase 41 | Pending |
| PLUG-05 | Phase 41 | Pending |
| PLUG-06 | Phase 40 | Pending |
| PERS-01 | Phase 42 | Pending |
| PERS-02 | Phase 42 | Pending |
| PERS-03 | Phase 42 | Pending |
| DOCS-01 | Phase 43 | Pending |

**Coverage:**
- v2.9.0 requirements: 17 total
- Mapped to phases: 17/17 (100%)
- Unmapped: 0

**Phase Distribution:**
- Phase 39: 4 requirements (TMUX-01, TMUX-02, CONF-01, CONF-02)
- Phase 40: 4 requirements (PLUG-01, PLUG-02, PLUG-03, PLUG-06)
- Phase 41: 3 requirements (TMUX-03, PLUG-04, PLUG-05)
- Phase 42: 3 requirements (PERS-01, PERS-02, PERS-03)
- Phase 43: 3 requirements (TMUX-04, TMUX-05, DOCS-01)

---
*Requirements defined: 2026-02-01*
*Last updated: 2026-02-02 after Phase 39 execution*
