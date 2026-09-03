---
id: aix-01m1kyn7j5th
title: 'Build script: five platform binaries, SHA256SUMS, legacy trio behind a gate'
status: closed
type: task
priority: 1
mode: afk
created: '2026-09-03T15:37:04.325583771Z'
updated: '2026-09-03T21:26:17.929614871Z'
closed: '2026-09-03T21:26:17.929614871Z'
parent: aix-01m1kydv7r76
tags:
- ready-for-agent
---

## Description

Parent spec: aix-01m1kydv7r76 (ADR 0007).

### What to build

The release build script produces five standalone executables — `aishell-linux-amd64`, `aishell-linux-aarch64`, `aishell-macos-amd64`, `aishell-macos-aarch64`, `aishell-windows-amd64.exe` — each the upstream babashka binary for that platform (static builds on Linux) with aishell's uberjar appended, plus one `SHA256SUMS` in `hash  filename` format listing every asset. The babashka version is one pinned constant, 1.13.220. The uberjar is built once with main class `aishell.core` on an explicit `src` classpath so `test/` is not bundled. A target selector builds only the host platform so a maintainer can verify locally with `--version --json`. The legacy trio (`aishell` uberscript with shebang, `aishell.bat`, `aishell.sha256`) is still produced, behind a clearly marked gate that the 4.2.0 follow-up removes.

### Acceptance criteria

- [x] Full build on Linux yields the five binaries, `SHA256SUMS`, and the legacy trio
- [x] Host-only build yields one binary that answers `--version --json` with the version from the CLI namespace
- [x] babashka version pinned in exactly one place in the script
- [x] Release-creation script's file checks cover the new assets and the legacy trio
- [x] clj-kondo clean

### Blocked by

None (can start immediately).

## Notes

**2026-09-03T21:26:17.929614871Z**

build-release.clj produces the five platform binaries by appending the uberjar to the pinned babashka 1.13.220 (upstream archives now verified against their published .sha256), plus SHA256SUMS and the legacy trio behind a 4.2.0 gate. --target host builds only the host platform. create-release.clj checks all assets and reads the version from a host-runnable binary. Full five-target build ran and produced correct ELF, Mach-O and PE binaries.
