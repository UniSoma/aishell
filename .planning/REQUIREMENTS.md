# Requirements: Agentic Harness Sandbox

**Defined:** 2026-01-22
**Core Value:** Run agentic AI harnesses in isolated, reproducible environments without polluting the host system.

## v2.1 Requirements

Requirements for v2.1 Safe AI Context Protection. Extends existing security warnings to project directory contents.

### Core Detection Framework

- [x] **FRMW-01**: Detection system supports severity tiers (high/medium/low) for different finding types
- [x] **FRMW-02**: Warnings display during `aishell` / `aishell claude` / `aishell opencode` before container runs
- [x] **FRMW-03**: Warnings are advisory only (never block) consistent with existing security warning pattern

### Environment Files

- [ ] **ENVF-01**: Warn (medium) when .env files exist in project directory
- [ ] **ENVF-02**: Warn (medium) when .env.* variant files exist (.env.local, .env.production, .envrc)
- [ ] **ENVF-03**: Inform (low) when .env.example/.env.sample exist (template files, lower risk)

### Private Keys & Certificates (Content-Aware)

- [ ] **PKEY-01**: Warn (high) when files contain private key markers (BEGIN OPENSSH/RSA/EC/DSA/PRIVATE KEY, PuTTY-User-Key-File)
- [ ] **PKEY-02**: Warn (high) when common SSH key files exist (id_rsa, id_dsa, id_ed25519, id_ecdsa, *.ppk)
- [ ] **PKEY-03**: Warn (high) when key container files exist (*.p12, *.pfx, *.jks, *.keystore)
- [ ] **PKEY-04**: Warn (medium) when *.pem or *.key files exist (may be certs, not just private keys)

### Cloud Credentials

- [ ] **CLOD-01**: Warn (high) when GCP service account files detected (JSON with "type": "service_account" and "private_key")
- [ ] **CLOD-02**: Warn (high) when application_default_credentials.json exists
- [ ] **CLOD-03**: Warn (high) when terraform.tfstate* files exist (can contain plaintext secrets)
- [ ] **CLOD-04**: Warn (medium) when kubeconfig or .kube/config patterns detected

### Package Manager & Tool Credentials

- [ ] **PKGM-01**: Warn (high) when .npmrc with authToken detected (content check)
- [ ] **PKGM-02**: Warn (high) when .pypirc exists
- [ ] **PKGM-03**: Warn (high) when .netrc exists
- [ ] **PKGM-04**: Warn (medium) when .yarnrc.yml, .docker/config.json, .terraformrc, credentials.tfrc.json exist

### Application Secrets

- [ ] **ASEC-01**: Warn (high) when Rails master.key or credentials*.yml.enc exist
- [ ] **ASEC-02**: Warn (medium) when secret.*, secrets.*, vault.*, token.*, apikey.*, private.* files exist
- [ ] **ASEC-03**: Warn (medium) when database credential files exist (.pgpass, .my.cnf, database.yml)

### Context Awareness

- [ ] **CTXT-01**: Extra warning when high-severity files exist but are NOT in .gitignore (likely accidental)
- [ ] **CTXT-02**: Warnings indicate whether file is in mounted project directory vs additional mounts

### Configuration

- [ ] **CONF-01**: Sensitive file patterns configurable via .aishell/config.yaml
- [ ] **CONF-02**: Custom patterns are additive by default (extend, don't replace defaults)
- [ ] **CONF-03**: Explicit allowlist supported to suppress specific false positives
- [ ] **CONF-04**: Config parsing is safe (no YAML tags that instantiate objects)

## Future Requirements

Deferred to v2.2 or later. Tracked but not in current roadmap.

### Advanced Secret Detection

- **SECR-01**: Built-in detection of AWS access key patterns in file contents
- **SECR-02**: Built-in detection of GitHub token patterns in file contents
- **SECR-03**: Built-in detection of generic high-entropy strings (API key heuristics)
- **SECR-04**: Optional gitleaks integration for deep scanning

### Audit Command

- **AUDT-01**: `aishell audit` command for pre-run security check
- **AUDT-02**: Audit command produces structured report (JSON/YAML)
- **AUDT-03**: Audit command supports CI/CD mode (exit code on findings)

### Strict Mode

- **STRM-01**: Optional strict mode that blocks on high-severity findings
- **STRM-02**: Strict mode configurable via CLI flag or config

## Out of Scope

Explicitly excluded. Documented to prevent scope creep.

| Feature | Reason |
|---------|--------|
| Blocking by default | Advisory pattern only - never block, just inform (per v2.0 decision) |
| PII detection (SSN, credit card) | High false positive rate, enterprise feature, defer to v3+ |
| Git history scanning | High complexity, significant performance impact |
| Network egress monitoring | Out of scope for container startup - would require runtime hooks |
| Secret encryption/vaulting | Not harness's responsibility - integrates with external tools |
| Windows-specific credential paths | Windows support deferred indefinitely |
| Home directory scanning | Warns only about project dir and explicit mounts, not ~/.aws etc. unless mounted |
| Full AWS/GCP/Azure SDK credential chain | Too complex; focus on files in project context |

## Traceability

Which phases cover which requirements. Updated during roadmap creation.

| Requirement | Phase | Status |
|-------------|-------|--------|
| FRMW-01 | Phase 19 | Complete |
| FRMW-02 | Phase 19 | Complete |
| FRMW-03 | Phase 19 | Complete |
| ENVF-01 | Phase 20 | Pending |
| ENVF-02 | Phase 20 | Pending |
| ENVF-03 | Phase 20 | Pending |
| PKEY-01 | Phase 21 | Pending |
| PKEY-02 | Phase 20 | Pending |
| PKEY-03 | Phase 20 | Pending |
| PKEY-04 | Phase 20 | Pending |
| CLOD-01 | Phase 21 | Pending |
| CLOD-02 | Phase 22 | Pending |
| CLOD-03 | Phase 22 | Pending |
| CLOD-04 | Phase 22 | Pending |
| PKGM-01 | Phase 21 | Pending |
| PKGM-02 | Phase 22 | Pending |
| PKGM-03 | Phase 22 | Pending |
| PKGM-04 | Phase 22 | Pending |
| ASEC-01 | Phase 22 | Pending |
| ASEC-02 | Phase 22 | Pending |
| ASEC-03 | Phase 22 | Pending |
| CTXT-01 | Phase 23 | Pending |
| CTXT-02 | Phase 23 | Pending |
| CONF-01 | Phase 23 | Pending |
| CONF-02 | Phase 23 | Pending |
| CONF-03 | Phase 23 | Pending |
| CONF-04 | Phase 23 | Pending |

**Coverage:**
- v2.1 requirements: 27 total
- Mapped to phases: 27
- Unmapped: 0

---
*Requirements defined: 2026-01-22*
*Last updated: 2026-01-23 - Phase 19 requirements complete*
