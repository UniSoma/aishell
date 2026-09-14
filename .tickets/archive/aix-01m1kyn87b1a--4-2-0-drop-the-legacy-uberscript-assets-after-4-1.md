---
id: aix-01m1kyn87b1a
title: '4.3.0: drop the legacy uberscript assets'
status: closed
type: task
priority: 1
mode: hitl
created: '2026-09-03T15:37:04.999114489Z'
updated: '2026-09-14T12:13:40.334770710Z'
closed: '2026-09-14T12:13:40.334770710Z'
parent: aix-01m1kydv7r76
tags:
- ready-for-agent
deps:
- aix-01m1kyn83zvm
acceptance:
- title: A full build yields only the five binaries and `SHA256SUMS`
  done: true
- title: Release-creation checks no longer require the legacy trio
  done: true
- title: CHANGELOG explains the v4.0.0 consequence
  done: true
---

## Description

Parent spec: aix-01m1kydv7r76 (ADR 0007). Timing: the bridging window ends with this ticket; it can start now that 4.1.0 (2026-09-04) and 4.2.0 (2026-09-09) are both on GitHub.

### Why 4.3.0, not 4.2.0

This was planned for 4.2.0, but v4.2.0 was cut for the Copilot harness and the runtime-env work without touching the legacy gate, so it still shipped `aishell`, `aishell.bat` and `aishell.sha256`. The bridging window is therefore one release longer than ADR 0007 first said; the ADR carries an amendment and the CHANGELOG has a note under 4.2.0. Do not do this drop in a 4.2.x patch: a v4.0.0 `upgrade` fetches the newest release, whatever its number.

### What to build

The build script and release-creation checks stop producing `aishell`, `aishell.bat` and `aishell.sha256`; the legacy gate in the build script (`legacy-assets?`) and the code it guards are removed. The CHANGELOG entry for 4.3.0 states that a v4.0.0 install must re-run the installer or `aishell upgrade 4.2.0` first, since its `upgrade` command fetches assets that no longer exist. Version bumped to 4.3.0.

## Notes

**2026-09-09T03:32:04.989170125Z**

Retargeted from 4.2.0 to 4.3.0: v4.2.0 was released on 2026-09-09 with the legacy gate still on, so it kept publishing the bridging trio.

**2026-09-14T12:13:40.334770710Z**

Dropped the legacy gate and the uberscript/bat/sha256 trio from build-release.clj and create-release.clj; full build verified to yield the five archives plus SHA256SUMS and create-release preflight accepts that set; CHANGELOG Unreleased entry explains that a v4.0.0 install must re-run the installer or upgrade to 4.2.0 first; version bumped to 4.3.0; ARCHITECTURE.md and DEVELOPMENT.md updated.
