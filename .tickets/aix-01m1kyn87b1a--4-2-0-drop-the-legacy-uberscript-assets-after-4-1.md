---
id: aix-01m1kyn87b1a
title: '4.2.0: drop the legacy uberscript assets after 4.1.0 ships'
status: open
type: task
priority: 1
mode: hitl
created: '2026-09-03T15:37:04.999114489Z'
updated: '2026-09-03T15:37:05.095998989Z'
parent: aix-01m1kydv7r76
tags:
- ready-for-agent
deps:
- aix-01m1kyn83zvm
---

## Description

Parent spec: aix-01m1kydv7r76 (ADR 0007). Timing: start only after 4.1.0 is released on GitHub — this ends the bridging window.

### What to build

The build script and release-creation checks stop producing `aishell`, `aishell.bat` and `aishell.sha256`; the legacy gate in the build script is removed. The CHANGELOG entry for 4.2.0 states that a v4.0.0 install must re-run the installer, since its `upgrade` command fetches assets that no longer exist. Version bumped to 4.2.0.

### Acceptance criteria

- [ ] A full build yields only the five binaries and `SHA256SUMS`
- [ ] Release-creation checks no longer require the legacy trio
- [ ] CHANGELOG explains the v4.0.0 consequence
- [ ] 4.1.0 is published before this ticket is started

### Blocked by

Docs, changelog and 4.1.0 bump.
