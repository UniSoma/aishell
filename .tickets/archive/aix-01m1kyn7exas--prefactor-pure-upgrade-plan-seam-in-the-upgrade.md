---
id: aix-01m1kyn7exas
title: 'Prefactor: pure upgrade plan seam in the upgrade module'
status: closed
type: task
priority: 1
mode: afk
created: '2026-09-03T15:37:04.221877097Z'
updated: '2026-09-04T00:33:07.875464064Z'
closed: '2026-09-03T20:56:31.114187447Z'
parent: aix-01m1kydv7r76
tags:
- ready-for-agent
acceptance:
- title: Plan function is pure and unit-tested for the current uberscript shape on Unix and Windows (asset `aishell`, `.bat` refresh on Windows, latest vs pinned URLs)
  done: true
- title: Checksum-file lookup and script detection unit-tested
  done: true
- title: '`aishell upgrade` end-to-end behaviour unchanged (manual check against the v4.0.0 release)'
  done: true
- title: Tests run via `bb test`; clj-kondo clean
  done: true
---

## Description

Parent spec: aix-01m1kydv7r76 (ADR 0007). Prefactor only — no user-visible change.

### What to build

`aishell upgrade` behaves exactly as today, but the decision of what to fetch, where to put it and what to clean up becomes a pure function of the environment (OS, architecture, installed path, whether the installed file is a script, whether a `.bat` sits beside it, current and target version). It returns a plan: asset name, download URLs for the asset and its checksum file, destination path, files to delete after success, whether a rename-to-`.old` step applies, and notes to print. The existing entry point becomes a thin executor: detect environment, compute plan, download, verify, apply.

Also add, with tests, the two helpers the binary slices need: lookup of one asset's hash in a multi-line `hash  filename` checksum file (missing asset is an error, hashes compared case-insensitively), and detection of a script install from the installed file's leading `#!` bytes.

### Blocked by

None (can start immediately).


## Notes

**2026-09-03T20:56:31.114187447Z**

Upgrade decisions moved into a pure seam: upgrade-plan, detect-env, execute-plan, plus checksum-for, shebang? and read-leading-bytes. aishell upgrade still fetches the legacy uberscript; behaviour verified end-to-end against the real v4.0.0 release into a temp dir. 183 tests green, clj-kondo clean.
