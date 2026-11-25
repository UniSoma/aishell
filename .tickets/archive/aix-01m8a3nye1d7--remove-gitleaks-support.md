---
id: aix-01m8a3nye1d7
title: Remove Gitleaks support
status: closed
type: feature
priority: 2
mode: afk
created: '2026-11-25T19:42:19.015233469Z'
updated: '2026-11-25T20:06:21.082180851Z'
closed: '2026-11-25T20:06:21.082180851Z'
tags:
- ready-for-agent
acceptance:
- title: '`aishell gitleaks` and `aishell setup --with-gitleaks` are refused as an unknown command and an unknown option'
  done: true
- title: Nothing in the Harness registry or launch path refers to gitleaks any more; the registry holds only AI coding CLIs
  done: true
- title: The Foundation image build takes no WITH_GITLEAKS arg, does not install gitleaks, and its content hash changes, so users get one rebuild
  done: true
- title: A state.edn with :with-gitleaks true warns once on setup/update, the key is removed, and the next command is silent; this is covered by a test
  done: true
- title: 'gitleaks_freshness_* keys are no longer known config keys, and nothing reads them: no freshness warning at launch and no gitleaks line in aishell check or aishell info'
  done: true
- title: CONFIGURATION.md has the 'where gitleaks goes now' section; README, ARCHITECTURE, TROUBLESHOOTING and llm.txt no longer describe aishell's Gitleaks support
  done: true
- title: CHANGELOG Unreleased has a breaking entry that names the removal, the migration, and the orphaned scan-state file
  done: true
- title: Ticket aix-01kr1d73gmbm is closed as wontfix, superseded by this one; the gitleaks fields are dropped from aix-01kr1qqhakrv and aix-01kr1qr59100
  done: true
- title: The test suite passes and clj-kondo --lint src test is clean
  done: true
links:
- aix-01kr1d73gmbm
- aix-01kr1qqhakrv
- aix-01kr1qr59100
---

## Description

Remove aishell's Gitleaks support, per ADR 0012 (secret scanning stops at sensitive-file detection). aishell keeps Sensitive-file detection as its only secret check. Scanning file contents and git history for committed secrets is left to each project (pre-commit or CI).

### What goes

- The `aishell gitleaks` subcommand and the Gitleaks entry in the Harness registry, including every special case the launch path has for it: skipping pre_start, skipping sensitive-file detection and the shell aliases, treating exit 1 as success, and writing the scan timestamp.
- The `setup --with-gitleaks` flag, the `WITH_GITLEAKS` build arg and the conditional install step in the Foundation image. After this, the Foundation image is identical for every user again.
- Scan freshness: the scan-state and warnings code, the launch-time warning, the `aishell check` line, and the `gitleaks_freshness_check` / `gitleaks_freshness_threshold` config keys.
- The Gitleaks line in `aishell info` and the `:with-gitleaks` state key.

### How old installs behave

- A saved `:with-gitleaks` in state.edn goes through the removed-harness path: one warning that points to the new docs section, then the key is removed from the saved state.
- Leftover `gitleaks_freshness_*` keys get the generic "Unknown config keys" warning, and the "Valid keys" list stops naming them. A leftover `harness_args.gitleaks` gets the existing unknown-harness warning. Neither gets a special message.
- `aishell gitleaks` and `setup --with-gitleaks` fail like any unknown command or option.
- The old scan timestamp file in the aishell state dir stays on disk. The CHANGELOG says it is safe to delete.

### Docs

- Replace the Gitleaks section in CONFIGURATION.md with a short "where gitleaks goes now" section: pre-commit or CI for commit leakage, or a Project extension that installs gitleaks, run with `aishell exec gitleaks dir .`.
- Remove the Gitleaks content from README, ARCHITECTURE (the "two-layer security" framing becomes Sensitive-file detection alone), TROUBLESHOOTING and llm.txt.
- Add a CHANGELOG Unreleased entry marked as breaking. This ships as 5.0.0, but cutting the release is a separate step.

### Out of scope

The historical `.planning/` and `artifacts/` documents stay as they are. ADR 0012 and the Sensitive-file detection entry in CONTEXT.md are already written.

## Notes

**2026-11-25T20:06:21.082180851Z**

Removed aishell's Gitleaks support per ADR 0012: the gitleaks subcommand and registry entry, --with-gitleaks and the WITH_GITLEAKS Foundation build arg, scan freshness (state, warnings, check and info lines, config keys). A saved :with-gitleaks gets the removed-harness warning once. Docs gain a Secret scanning section; CHANGELOG Unreleased carries the breaking entry for 5.0.0.
