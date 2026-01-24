# Project State

## Project Reference

See: .planning/PROJECT.md (updated 2026-01-22)

**Core value:** Run agentic AI harnesses in isolated, reproducible environments without polluting the host system.
**Current focus:** Phase 23 - Context & Configuration (next up)

## Current Position

Phase: 23 of 23 (Context & Configuration) - COMPLETE
Plan: 2 of 2 in current phase - COMPLETE
Status: Phase complete
Last activity: 2026-01-24 - Completed 23-02-PLAN.md (detection configuration)

Progress: [####################] 100% v2.0 | [██████████] 100% v2.1 (10/10 plans)

**Shipped Milestones:**
- v1.0 MVP - Phases 1-8 (shipped 2026-01-18)
- v1.1 Runtime Config - Phases 9-10 (shipped 2026-01-19)
- v1.2 Hardening - Phases 11-12 (shipped 2026-01-19)
- v2.0 Babashka Rewrite - Phases 13-18 (shipped 2026-01-21)

## Performance Metrics

**v2.1 Velocity:**
- Total plans completed: 10
- Average duration: 2.8 min
- Total execution time: 28.0 min

**By Phase:**

| Phase | Plans | Total | Avg/Plan |
|-------|-------|-------|----------|
| 18.1-default-harness-args | 1/1 | 2min | 2.0min |
| 19-core-framework | 1/1 | 4min | 4.0min |
| 20-filename-detection | 2/2 | 4min | 2.0min |
| 21-extended-filename-patterns | 2/2 | 6min | 3.0min |
| 22-gitleaks-integration | 2/2 | 5min | 2.5min |
| 23-context-config | 2/2 | 7min | 3.5min |

**v2.0 Reference (for comparison):**
- Total plans completed: 22
- Average duration: 2.4 min
- Total execution time: 52.9 min

## Accumulated Context

### Decisions

See PROJECT.md Key Decisions table.
Recent decisions affecting current work:

- [23-02]: Custom patterns extend defaults (don't replace) via concatenation in scan-project
- [23-02]: Allowlisted files completely hidden from output (not shown as 'allowed')
- [23-02]: Invalid severity values in custom patterns skipped silently (no error, just warning)
- [23-02]: Pattern keys from YAML parsed as keywords - use (name pattern-key) to get string
- [23-02]: Allowlist filtering happens in run.clj after scan-project returns findings
- [23-01]: Only annotate high-severity findings with gitignore status (medium/low don't warrant overhead)
- [23-01]: Show "(risk: may be committed)" only when gitignored? returns false (explicitly unprotected)
- [23-01]: nil from gitignored? (non-git/error) treated as unknown - no annotation to avoid noise
- [23-01]: Perform annotation before grouping by severity for cleaner separation of concerns
- [22-UAT]: Only update timestamp for actual scans (dir/git/detect/protect), not help
- [22-UAT]: Use contains? not if-let for scalar config merge (handles false values)
- [22-02]: Default staleness threshold: 7 days (balances nudging without annoyance)
- [22-02]: State file location: ~/.local/state/aishell/gitleaks-scans.edn (XDG Base Directory spec)
- [22-02]: Absolute paths as state keys for debuggability
- [22-02]: Shell vs exec for gitleaks to capture exit code for timestamp updates
- [22-02]: Config key: gitleaks_freshness_check (defaults to enabled)
- [22-01]: Gitleaks v8.30.0 pinned with multi-arch support (amd64, arm64, armv7)
- [22-01]: Skip-pre-start via `-e PRE_START=` to unset env var in entrypoint
- [22-01]: Gitleaks command is pure passthrough (no defaults merging)
- [roadmap]: Content-based detection delegated to Gitleaks (runs inside container via `aishell gitleaks`)
- [roadmap]: Filename-based detection stays in aishell (pre-container, fast checks)
- [roadmap]: Gitleaks freshness warning on all commands if scan is stale (default 7 days)
- [21-02]: Package manager creds (.pypirc, .netrc) classified as high severity
- [21-02]: Rails master.key classified as high severity - decrypts all secrets
- [21-02]: Tool configs (.npmrc, .docker/config.json) classified as medium severity
- [21-02]: Secret pattern files (secret.*, token.*) classified as medium severity
- [21-02]: Database configs (.pgpass, .my.cnf, database.yml) classified as medium severity
- [21-02]: Use filename filtering not glob patterns for secret.* matching (babashka.fs quirk)
- [21-01]: GCP ADC classified as high severity - long-lived service account credentials
- [21-01]: Terraform state classified as high severity - may contain plaintext secrets
- [21-01]: Kubeconfig classified as medium severity - cluster access credentials
- [21-01]: Use glob ** with filename filter for root+subdirectory coverage (babashka.fs quirk)
- [20-02]: SSH keys (id_rsa, id_dsa, id_ed25519, id_ecdsa, *.ppk) classified as high severity
- [20-02]: Key containers (*.p12, *.pfx, *.jks, *.keystore) classified as high severity
- [20-02]: PEM/key files (*.pem, *.key) classified as medium severity (may be certificates or keys)
- [20-01]: Threshold-of-3 grouping: show files individually if <=3, summarize with count if >3
- [20-01]: Case-insensitive matching via clojure.string/lower-case post-filtering
- [20-01]: Template detection: .env files with 'example' or 'sample' in name are low-severity
- [20-01]: Summary format shows count + 2 sample filenames for context
- [19-01]: Use defmulti for format-finding to enable extension in later phases
- [19-01]: High-severity requires y/n in interactive mode, exit 1 in non-interactive
- [19-01]: Medium/low severity auto-proceeds in all modes
- [19-01]: Detection hook placed after warn-dangerous-mounts, before docker-args
- [18.1-01]: Global defaults prepend to project defaults (concatenate lists per harness)
- [18.1-01]: String values auto-normalize to single-element lists for better DX

### Pending Todos

0 pending todos in `.planning/todos/pending/`

### Roadmap Evolution

- Phase 18.1 inserted after Phase 18: Default harness arguments in config (URGENT) - from pending todo

### Blockers/Concerns

None.

### Quick Tasks Completed

| # | Description | Date | Commit | Directory |
|---|-------------|------|--------|-----------|
| 004 | Add extends key for config merge strategy | 2026-01-22 | ebd6ff7 | [004-add-extends-key-for-config-merge-strateg](./quick/004-add-extends-key-for-config-merge-strateg/) |

## Session Continuity

Last session: 2026-01-24
Stopped at: Completed 23-02-PLAN.md (detection configuration)
Resume file: None

23-02 Summary:
User-configurable custom patterns and allowlists with merge strategy enabling project-specific detection rules and false positive suppression.

Commits from 23-02:
- 5574856: feat(23-02): add detection config key with merge strategy
- 1cb144b: feat(23-02): add custom pattern detection and allowlist filtering
- a7e9de2: feat(23-02): wire detection config into run.clj execution flow
