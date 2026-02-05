# Requirements: Agentic Harness Sandbox

**Defined:** 2026-02-05
**Core Value:** Run agentic AI harnesses in isolated, reproducible environments without polluting the host system.

## v2.10.0 Requirements

Requirements for Gitleaks Opt-in milestone. Flip Gitleaks from opt-out to opt-in while preserving filename-based detection.

### Build System

- [ ] **BUILD-01**: `--with-gitleaks` flag enables Gitleaks installation (flip default to off)
- [ ] **BUILD-02**: Gitleaks NOT installed by default (current default = installed)

### Help System

- [ ] **HELP-01**: `aishell gitleaks` command hidden from `--help` when Gitleaks not installed

### Pipeline Integration

- [ ] **PIPE-01**: Gitleaks staleness warning skipped when Gitleaks not installed
- [ ] **PIPE-02**: Filename-based detection continues to work (independent of Gitleaks)

### Documentation

- [ ] **DOCS-01**: README.md updated (build flags, default behavior)
- [ ] **DOCS-02**: CONFIGURATION.md updated (Gitleaks config section)
- [ ] **DOCS-03**: TROUBLESHOOTING.md updated (Gitleaks-related issues)
- [ ] **DOCS-04**: HARNESSES.md updated if Gitleaks mentioned there

## Out of Scope

| Feature | Reason |
|---------|--------|
| Remove filename-based detection | Babashka-side detection is lightweight and valuable without Gitleaks |
| Gitleaks auto-install on first `aishell gitleaks` run | Adds complexity; explicit build flag is cleaner |

## Traceability

| Requirement | Phase | Status |
|-------------|-------|--------|
| BUILD-01 | Phase 44 | Pending |
| BUILD-02 | Phase 44 | Pending |
| HELP-01 | Phase 44 | Pending |
| PIPE-01 | Phase 44 | Pending |
| PIPE-02 | Phase 44 | Pending |
| DOCS-01 | Phase 45 | Pending |
| DOCS-02 | Phase 45 | Pending |
| DOCS-03 | Phase 45 | Pending |
| DOCS-04 | Phase 45 | Pending |

**Coverage:**
- v2.10.0 requirements: 9 total
- Mapped to phases: 9
- Unmapped: 0

---
*Requirements defined: 2026-02-05*
*Last updated: 2026-02-05 — Traceability completed*
