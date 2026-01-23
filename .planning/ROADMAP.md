# Roadmap: Agentic Harness Sandbox

## Milestones

- ✅ **v1.0 MVP** - Phases 1-8 (shipped 2026-01-18)
- ✅ **v1.1 Per-project Runtime Configuration** - Phases 9-10 (shipped 2026-01-19)
- ✅ **v1.2 Hardening & Edge Cases** - Phases 11-12 (shipped 2026-01-19)
- ✅ **v2.0 Babashka Rewrite** - Phases 13-18 (shipped 2026-01-21)
- 🚧 **v2.1 Safe AI Context Protection** - Phases 19-23 (in progress)

## Phases

<details>
<summary>v1.0-v2.0 (Phases 1-18) - SHIPPED</summary>

See MILESTONES.md for completed milestone details.

</details>

### 🚧 v2.1 Safe AI Context Protection (In Progress)

**Milestone Goal:** Proactively warn users about sensitive data in their project directory before AI agents access it.

**Phase Numbering:**
- Integer phases (19, 20, 21): Planned milestone work
- Decimal phases (19.1, 19.2): Urgent insertions (marked with INSERTED)

- [x] **Phase 19: Core Detection Framework** - Severity tiers, warning display, advisory pattern
- [ ] **Phase 20: Filename-based Detection** - Env files, SSH keys by name, key containers
- [ ] **Phase 21: Content-aware Detection** - Private key markers, GCP service accounts, npmrc auth
- [ ] **Phase 22: Extended Patterns** - Cloud creds, package managers, app secrets
- [ ] **Phase 23: Context Awareness & Configuration** - Gitignore context, mount tracking, custom patterns

## Phase Details

### Phase 18.1: Default Harness Arguments in Config (INSERTED)
**Goal**: Users can specify per-harness default arguments in config.yaml that are automatically applied on every invocation
**Depends on**: v2.0 complete (Phase 18)
**Success Criteria** (what must be TRUE):
  1. User can add `harness_args` key to config.yaml mapping harness names to default arguments
  2. Default arguments are automatically prefixed to user-provided arguments
  3. Per-harness granularity (claude, opencode can have different defaults)
**Plans**: 1 plan

Plans:
- [x] 18.1-01-PLAN.md - Implement harness_args config parsing and argument injection

### Phase 19: Core Detection Framework
**Goal**: Users see warnings about sensitive files with severity tiers when running aishell commands
**Depends on**: v2.0 complete (Phase 18)
**Requirements**: FRMW-01, FRMW-02, FRMW-03
**Success Criteria** (what must be TRUE):
  1. User sees severity level (high/medium/low) alongside each warning
  2. Warnings appear before container runs when invoking any aishell command
  3. User can proceed to container despite any warnings (never blocked)
  4. Warning output follows existing security warning visual pattern
**Plans**: 1 plan

Plans:
- [x] 19-01: Detection framework with severity tiers and display

### Phase 20: Filename-based Detection
**Goal**: Users are warned about sensitive files detected by filename patterns (no content inspection)
**Depends on**: Phase 19
**Requirements**: ENVF-01, ENVF-02, ENVF-03, PKEY-02, PKEY-03, PKEY-04
**Success Criteria** (what must be TRUE):
  1. User sees medium warning when .env file exists in project
  2. User sees medium warning when .env.local, .env.production, or .envrc exist
  3. User sees low-severity info when .env.example or .env.sample exist
  4. User sees high warning when id_rsa, id_dsa, id_ed25519, id_ecdsa, or *.ppk files exist
  5. User sees high warning when *.p12, *.pfx, *.jks, *.keystore files exist
**Plans**: 2 plans

Plans:
- [ ] 20-01: Environment file detection with threshold-based grouping
- [ ] 20-02: SSH key, key container, and PEM/key file detection

### Phase 21: Content-aware Detection
**Goal**: Users are warned about sensitive data detected by inspecting file contents
**Depends on**: Phase 20
**Requirements**: PKEY-01, CLOD-01, PKGM-01
**Success Criteria** (what must be TRUE):
  1. User sees high warning when any file contains "BEGIN OPENSSH PRIVATE KEY", "BEGIN RSA PRIVATE KEY", or similar markers
  2. User sees high warning when JSON file contains both "type": "service_account" and "private_key" (GCP service account)
  3. User sees high warning when .npmrc file contains authToken or _authToken
**Plans**: 2 plans

Plans:
- [ ] 21-01: Private key content detection
- [ ] 21-02: GCP service account and npmrc auth token detection

### Phase 22: Extended Patterns
**Goal**: Users are warned about additional cloud credentials, package manager configs, and application secrets
**Depends on**: Phase 21
**Requirements**: CLOD-02, CLOD-03, CLOD-04, PKGM-02, PKGM-03, PKGM-04, ASEC-01, ASEC-02, ASEC-03
**Success Criteria** (what must be TRUE):
  1. User sees high warning when application_default_credentials.json exists
  2. User sees high warning when terraform.tfstate or terraform.tfstate.backup exist
  3. User sees medium warning when kubeconfig or .kube/config patterns detected
  4. User sees high warning when .pypirc or .netrc exist
  5. User sees medium warning when .yarnrc.yml, .docker/config.json, .terraformrc, or credentials.tfrc.json exist
  6. User sees high warning when Rails master.key or credentials*.yml.enc exist
  7. User sees medium warning when secret.*, secrets.*, vault.*, token.*, apikey.*, or private.* files exist
  8. User sees medium warning when .pgpass, .my.cnf, or database.yml exist
**Plans**: 3 plans

Plans:
- [ ] 22-01: Cloud credential file detection
- [ ] 22-02: Package manager credential detection
- [ ] 22-03: Application secret file detection

### Phase 23: Context Awareness & Configuration
**Goal**: Users can customize detection and get extra warnings for unprotected sensitive files
**Depends on**: Phase 22
**Requirements**: CTXT-01, CTXT-02, CONF-01, CONF-02, CONF-03, CONF-04
**Success Criteria** (what must be TRUE):
  1. User sees extra emphasis when high-severity file is NOT in .gitignore (likely accidental exposure)
  2. Warnings indicate whether sensitive file is in project directory vs additional mount
  3. User can add custom sensitive patterns via .aishell/config.yaml
  4. Custom patterns extend default patterns (not replace)
  5. User can allowlist specific files to suppress false positives
  6. YAML config parsing is safe (no arbitrary object instantiation)
**Plans**: 2 plans

Plans:
- [ ] 23-01: Gitignore context and mount source tracking
- [ ] 23-02: Configuration for custom patterns and allowlist

## Progress

**Execution Order:**
Phases execute in numeric order: 18.1 -> 19 -> 19.1 -> 19.2 -> 20 -> etc.

| Phase | Milestone | Plans Complete | Status | Completed |
|-------|-----------|----------------|--------|-----------|
| 18.1 Default Harness Args (INSERTED) | v2.1 | 1/1 | ✓ Complete | 2026-01-22 |
| 19. Core Detection Framework | v2.1 | 1/1 | ✓ Complete | 2026-01-23 |
| 20. Filename-based Detection | v2.1 | 0/2 | Not started | - |
| 21. Content-aware Detection | v2.1 | 0/2 | Not started | - |
| 22. Extended Patterns | v2.1 | 0/3 | Not started | - |
| 23. Context Awareness & Configuration | v2.1 | 0/2 | Not started | - |

---
*Roadmap created: 2026-01-22*
*Last updated: 2026-01-23 (Phase 19 complete)*
