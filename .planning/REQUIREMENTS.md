# Requirements: Agentic Harness Sandbox

**Defined:** 2026-02-11
**Core Value:** Run agentic AI harnesses in isolated, reproducible environments without polluting the host system

## v3.1.0 Requirements

Requirements for native Windows host support (cmd.exe/PowerShell with Linux containers via Docker Desktop).

### Platform Detection

- [ ] **PLAT-01**: Platform detection utility (`windows?`) available for conditional host-side logic
- [ ] **PLAT-02**: All host-side Unix-specific code paths guarded by platform detection (no unguarded `id`, `p/exec`, or Unix path assumptions)

### Process & Execution

- [ ] **PROC-01**: `aishell attach <name>` works on Windows using `p/process` with `:inherit` instead of `p/exec`
- [ ] **PROC-02**: `aishell <harness>` (claude, opencode, etc.) works on Windows — docker run invocation succeeds

### Path Handling

- [ ] **PATH-01**: `get-home` returns correct home directory on Windows (`USERPROFILE` fallback)
- [ ] **PATH-02**: `expand-path` handles Windows backslash paths correctly
- [ ] **PATH-03**: State/config directories use `LOCALAPPDATA` on Windows instead of XDG
- [ ] **PATH-04**: Volume mount source paths normalized for Docker Desktop (backslashes to forward slashes)

### Host Identity

- [ ] **ID-01**: UID/GID detection defaults to 1000/1000 on Windows instead of calling `id -u`/`id -g`
- [ ] **ID-02**: Git config extraction (`git config user.name/user.email`) works on Windows

### Terminal & Output

- [ ] **TERM-01**: ANSI color codes auto-detected and stripped when terminal lacks support (check `WT_SESSION`, `TERM`, `ConEmuANSI`)
- [ ] **TERM-02**: TERM/COLORTERM have sensible defaults when not set (Windows doesn't set these)

### Distribution

- [ ] **DIST-01**: `aishell.bat` wrapper enables running `aishell` from Windows PATH without `bb` prefix

### Documentation

- [ ] **DOCS-01**: All user-facing CLI changes reflected in docs/ (README, ARCHITECTURE, CONFIGURATION, HARNESSES, TROUBLESHOOTING, DEVELOPMENT)

## Future Requirements

### Windows Distribution

- **WDIST-01**: Scoop package manifest for automated installation
- **WDIST-02**: PowerShell installer script (install.ps1)
- **WDIST-03**: Automated Windows CI/CD testing pipeline

### Native Windows Containers

- **WCON-01**: Windows-native Dockerfile with PowerShell entrypoint
- **WCON-02**: ACL-based permission management replacing UID/GID system

## Out of Scope

| Feature | Reason |
|---------|--------|
| Native Windows containers | Linux containers via Docker Desktop cover 95%+ of Windows Docker users; separate Dockerfile/entrypoint required |
| Scoop/winget packaging | Users install babashka and docker manually; packaging deferred |
| PowerShell installer (install.ps1) | Not needed when babashka + docker assumed available |
| Windows CI/CD pipeline | No Windows test environment available; manual testing only |
| WSL2-only mode | Targeting native cmd.exe/PowerShell, not WSL2 shell |

## Traceability

| Requirement | Phase | Status |
|-------------|-------|--------|
| PLAT-01 | Phase 53 | Pending |
| PLAT-02 | Phase 53 | Pending |
| PROC-01 | Phase 56 | Pending |
| PROC-02 | Phase 56 | Pending |
| PATH-01 | Phase 54 | Pending |
| PATH-02 | Phase 54 | Pending |
| PATH-03 | Phase 54 | Pending |
| PATH-04 | Phase 54 | Pending |
| ID-01 | Phase 55 | Pending |
| ID-02 | Phase 55 | Pending |
| TERM-01 | Phase 57 | Pending |
| TERM-02 | Phase 57 | Pending |
| DIST-01 | Phase 58 | Pending |
| DOCS-01 | Phase 59 | Pending |

**Coverage:**
- v3.1.0 requirements: 14 total
- Mapped to phases: 14 (100%)
- Unmapped: 0

---
*Requirements defined: 2026-02-11*
*Last updated: 2026-02-11 after roadmap creation*
