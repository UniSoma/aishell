# Requirements: Agentic Harness Sandbox

**Defined:** 2026-02-06
**Core Value:** Run agentic AI harnesses in isolated, reproducible environments without polluting the host system.

## v3.0.0 Requirements

Requirements for Docker-native attach milestone. Removes tmux from containers, simplifies attach to docker exec, adjusts CLI semantics.

### Tmux Removal

- [ ] **TMUX-01**: Foundation image built without tmux binary
- [ ] **TMUX-02**: `--with-tmux` build flag removed from CLI
- [ ] **TMUX-03**: `:with-tmux` and `:tmux-plugins` removed from state schema
- [ ] **TMUX-04**: `WITH_TMUX` env var removed from docker run arguments
- [ ] **TMUX-05**: tmux config mounting removed (staging, runtime config, `.tmux.conf`)
- [ ] **TMUX-06**: TPM and plugin installation removed from volume population
- [ ] **TMUX-07**: Volume hash no longer depends on tmux state
- [ ] **TMUX-08**: `tmux:` section removed from config schema (plugins, resurrect)
- [ ] **TMUX-09**: Resurrect mounts removed from docker run
- [ ] **TMUX-10**: Entrypoint simplified — no conditional tmux fork, direct `exec gosu`

### Attach Simplification

- [ ] **ATTCH-01**: `aishell attach <name>` runs `docker exec -it <container> bash`
- [ ] **ATTCH-02**: `--session` and `--shell` flags removed from attach
- [ ] **ATTCH-03**: Attach validates TTY, container existence, and running state
- [ ] **ATTCH-04**: Attach takes single positional argument (container name)

### CLI Semantics

- [ ] **CLI-01**: `aishell` (no args) creates container named `shell`, runs bash
- [ ] **CLI-02**: `aishell <harness>` creates container named `<harness>`
- [ ] **CLI-03**: `aishell --name X` creates container named `X` with bash
- [ ] **CLI-04**: `aishell <harness> --name X` creates container named `X` with harness
- [ ] **CLI-05**: Creation commands error if named container already running
- [ ] **CLI-06**: `--detach`/`-d` flag removed from CLI

### Documentation

- [ ] **DOCS-01**: All user-facing CLI changes reflected in docs/ (README, ARCHITECTURE, CONFIGURATION, HARNESSES, TROUBLESHOOTING, DEVELOPMENT)

## Future Requirements

None deferred — v3.0.0 is a focused removal/simplification milestone.

## Out of Scope

| Feature | Reason |
|---------|--------|
| tmux inside containers | Window management belongs on the host (v3.0.0 design decision) |
| Detached/background mode | Always-interactive simplifies the model; use host tools for backgrounding |
| Docker logs integration | Not needed when always attached; users can run `docker logs` directly |
| Multiple attach sessions | `docker exec` handles this natively — each attach is independent |

## Traceability

| Requirement | Phase | Status |
|-------------|-------|--------|
| TMUX-01 | — | Pending |
| TMUX-02 | — | Pending |
| TMUX-03 | — | Pending |
| TMUX-04 | — | Pending |
| TMUX-05 | — | Pending |
| TMUX-06 | — | Pending |
| TMUX-07 | — | Pending |
| TMUX-08 | — | Pending |
| TMUX-09 | — | Pending |
| TMUX-10 | — | Pending |
| ATTCH-01 | — | Pending |
| ATTCH-02 | — | Pending |
| ATTCH-03 | — | Pending |
| ATTCH-04 | — | Pending |
| CLI-01 | — | Pending |
| CLI-02 | — | Pending |
| CLI-03 | — | Pending |
| CLI-04 | — | Pending |
| CLI-05 | — | Pending |
| CLI-06 | — | Pending |
| DOCS-01 | — | Pending |

**Coverage:**
- v3.0.0 requirements: 21 total
- Mapped to phases: 0
- Unmapped: 21

---
*Requirements defined: 2026-02-06*
*Last updated: 2026-02-06 after initial definition*
