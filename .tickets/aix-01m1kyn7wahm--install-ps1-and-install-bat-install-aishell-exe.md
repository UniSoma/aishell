---
id: aix-01m1kyn7wahm
title: install.ps1 and install.bat install aishell.exe, no babashka
status: open
type: task
priority: 1
mode: afk
created: '2026-09-03T15:37:04.650804594Z'
updated: '2026-09-04T00:33:06.327712571Z'
parent: aix-01m1kydv7r76
tags:
- ready-for-human
acceptance:
- title: No reference to babashka or Scoop remains in either script
  done: true
- title: Both scripts verify against `SHA256SUMS` and clean up on mismatch
  done: true
- title: Result is a single `aishell.exe` on PATH; no `.bat` written
  done: true
- title: Verified on a Windows machine or runner against a locally served asset, or against the 4.1.0 release once published
  done: false
---

## Description

Parent spec: aix-01m1kydv7r76 (ADR 0007).

### What to build

`install.ps1` and `install.bat` fetch `aishell-windows-amd64.exe`, verify it against `SHA256SUMS`, and install it as `aishell.exe` in the existing install directory. Neither writes an `aishell.bat` shim, and the babashka/Scoop install blocks are gone. PATH handling and `VERSION` pinning stay as they are.

### Blocked by

None (can start immediately).

