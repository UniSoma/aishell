---
id: aix-01m1kyn7j5th
title: 'Build script: five platform binaries, SHA256SUMS, legacy trio behind a gate'
status: open
type: task
priority: 1
mode: afk
created: '2026-09-03T15:37:04.325583771Z'
updated: '2026-09-03T15:37:04.325583771Z'
parent: aix-01m1kydv7r76
tags:
- ready-for-agent
---

## Description

Parent spec: aix-01m1kydv7r76 (ADR 0007).

### What to build

The release build script produces five standalone executables — `aishell-linux-amd64`, `aishell-linux-aarch64`, `aishell-macos-amd64`, `aishell-macos-aarch64`, `aishell-windows-amd64.exe` — each the upstream babashka binary for that platform (static builds on Linux) with aishell's uberjar appended, plus one `SHA256SUMS` in `hash  filename` format listing every asset. The babashka version is one pinned constant, 1.13.220. The uberjar is built once with main class `aishell.core` on an explicit `src` classpath so `test/` is not bundled. A target selector builds only the host platform so a maintainer can verify locally with `--version --json`. The legacy trio (`aishell` uberscript with shebang, `aishell.bat`, `aishell.sha256`) is still produced, behind a clearly marked gate that the 4.2.0 follow-up removes.

### Acceptance criteria

- [ ] Full build on Linux yields the five binaries, `SHA256SUMS`, and the legacy trio
- [ ] Host-only build yields one binary that answers `--version --json` with the version from the CLI namespace
- [ ] babashka version pinned in exactly one place in the script
- [ ] Release-creation script's file checks cover the new assets and the legacy trio
- [ ] clj-kondo clean

### Blocked by

None (can start immediately).
