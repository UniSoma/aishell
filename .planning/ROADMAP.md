# Roadmap: v1.1 Per-project Runtime Configuration

**Created:** 2026-01-18
**Milestone:** v1.1
**Phases:** 9-10 (continuing from v1.0)

## Overview

| Phase | Name | Requirements | Status |
|-------|------|--------------|--------|
| 9 | Runtime Config Core | RCONF-*, MOUNT-*, ENV-*, PORT-*, DARG-* | Complete |
| 10 | Pre-Start Command | PRE-* | Complete |

## Phase 9: Runtime Config Core

**Goal:** Enable per-project runtime configuration via `.aishell/run.conf`

**Plans:** 2 plans

Plans:
- [x] 09-01-PLAN.md — Config file parser with validation and error handling
- [x] 09-02-PLAN.md — Argument builders and integration into main()

**Requirements covered:**
- RCONF-01, RCONF-02, RCONF-03 (config file)
- MOUNT-01, MOUNT-02, MOUNT-03 (volume mounts)
- ENV-01, ENV-02, ENV-03 (environment variables)
- PORT-01, PORT-02 (port mappings)
- DARG-01, DARG-02 (docker arguments)

**Success criteria:**
1. User creates `.aishell/run.conf` with MOUNTS, ENV, PORTS, DOCKER_ARGS
2. Running `aishell` applies all configured mounts to container
3. Running `aishell` passes through configured environment variables
4. Running `aishell` exposes configured ports
5. Syntax errors in config produce clear error messages

**Key implementation notes:**
- Config is sourced after validation (whitelist allowed variable names)
- Mounts expand $HOME before passing to docker
- ENV supports both passthrough (VAR) and literal (VAR=value) syntax
- All config is applied at `docker run` time, not build time

## Phase 10: Pre-Start Command

**Goal:** Enable background services/sidecars via pre-start hook

**Plans:** 1 plan

Plans:
- [x] 10-01-PLAN.md — PRE_START whitelist, env passthrough, and entrypoint execution

**Requirements covered:**
- PRE-01, PRE-02, PRE-03

**Success criteria:**
1. User specifies `PRE_START="command"` in run.conf
2. Command executes inside container before main process starts
3. Command runs in background (does not block)
4. Main process (shell/harness) starts normally after PRE_START

**Key implementation notes:**
- PRE_START runs in entrypoint.sh before exec to main command
- Uses `sh -c "$PRE_START" > /tmp/pre-start.log 2>&1 &` for background execution
- No nohup/disown needed (exec replaces shell entirely)
- Stdout/stderr captured in /tmp/pre-start.log

## Dependencies

```
Phase 9 ─────────────────────► Phase 10
(config parsing)              (uses config for PRE_START)
```

Phase 10 depends on Phase 9 (needs config file infrastructure).

## Risk Assessment

| Risk | Likelihood | Impact | Mitigation |
|------|------------|--------|------------|
| Config injection | Medium | High | Whitelist allowed variables, validate syntax |
| PRE_START blocks | Low | Medium | Document background requirement, use & |
| Mount paths don't exist | Medium | Low | Warn but continue (Docker handles gracefully) |

---
*Roadmap created: 2026-01-18*
*Phase 9 planned: 2026-01-18*
*Phase 9 completed: 2026-01-18*
*Phase 10 planned: 2026-01-18*
*Phase 10 completed: 2026-01-18*
