---
id: aix-01m1kyn7zqy6
title: aishell upgrade fetches the binary and migrates script installs in place
status: in_progress
type: task
priority: 1
mode: afk
created: '2026-09-03T15:37:04.754678901Z'
updated: '2026-09-03T21:52:41.900420338Z'
parent: aix-01m1kydv7r76
tags:
- ready-for-agent
deps:
- aix-01m1kyn7exas
---

## Description

Parent spec: aix-01m1kydv7r76 (ADR 0007).

### What to build

`aishell upgrade` moves users to the binary. The plan function now chooses the platform asset, the `SHA256SUMS` URL, and the destination (`aishell` on Unix, `aishell.exe` on Windows). A script install is migrated in place: on Unix the script is overwritten by the binary; on Windows `aishell.exe` is written and the old `aishell` and `aishell.bat` are deleted, with a one-line note. When the destination is the running executable on Windows, it is renamed to `aishell.exe.old` before the new file moves in, and on every start a leftover `.old` beside the running executable is deleted quietly. Downloads show a progress bar when stdout is a terminal (curl `-#`, wget `--show-progress`) and otherwise print one line with the asset name and size. Specific-version upgrade and downgrade warning keep working.

### Acceptance criteria

- [x] Plan tests cover all five platforms × script/binary install, pinned and latest, and an explicit error for unsupported pairs
- [x] Script install on Linux upgrades in place to a working binary (manual check)
- [ ] Windows: `.bat` and old script removed, `aishell.exe` installed, `.old` dance works while running, `.old` cleaned on next start
- [x] Checksum mismatch aborts before touching the install
- [x] Progress bar on a TTY, size notice otherwise
- [x] clj-kondo clean

### Blocked by

The upgrade plan seam prefactor.
