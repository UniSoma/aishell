---
id: aix-01m1kyn7wahm
title: install.ps1 and install.bat install aishell.exe, no babashka
status: open
type: task
priority: 1
mode: afk
created: '2026-09-03T15:37:04.650804594Z'
updated: '2026-09-03T22:10:13.277600695Z'
parent: aix-01m1kydv7r76
tags:
- ready-for-human
---

## Description

Parent spec: aix-01m1kydv7r76 (ADR 0007).

### What to build

`install.ps1` and `install.bat` fetch `aishell-windows-amd64.exe`, verify it against `SHA256SUMS`, and install it as `aishell.exe` in the existing install directory. Neither writes an `aishell.bat` shim, and the babashka/Scoop install blocks are gone. PATH handling and `VERSION` pinning stay as they are.

### Acceptance criteria

- [x] No reference to babashka or Scoop remains in either script
- [x] Both scripts verify against `SHA256SUMS` and clean up on mismatch
- [x] Result is a single `aishell.exe` on PATH; no `.bat` written
- [ ] Verified on a Windows machine or runner against a locally served asset, or against the 4.1.0 release once published

### Blocked by

None (can start immediately).
