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
| BUILD-01 | TBD | Pending |
| BUILD-02 | TBD | Pending |
| HELP-01 | TBD | Pending |
| PIPE-01 | TBD | Pending |
| PIPE-02 | TBD | Pending |
| DOCS-01 | TBD | Pending |
| DOCS-02 | TBD | Pending |
| DOCS-03 | TBD | Pending |
| DOCS-04 | TBD | Pending |

**Coverage:**
- v2.10.0 requirements: 9 total
- Mapped to phases: 0
- Unmapped: 9 (pending roadmap)

---
*Requirements defined: 2026-02-05*
*Last updated: 2026-02-05 after initial definition*
