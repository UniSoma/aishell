---
id: aix-01m1kyn83zvm
title: Docs, changelog and 4.1.0 version bump for the binary distribution
status: open
type: task
priority: 1
mode: afk
created: '2026-09-03T15:37:04.869877070Z'
updated: '2026-09-03T15:37:04.869877070Z'
parent: aix-01m1kydv7r76
tags:
- ready-for-agent
deps:
- aix-01m1kyn7j5th
- aix-01m1kyn7nsxe
- aix-01m1kyn7s2kj
- aix-01m1kyn7wahm
- aix-01m1kyn7zqy6
---

## Description

Parent spec: aix-01m1kydv7r76 (ADR 0007).

### What to build

Everything a user reads reflects the binary distribution, and the version is set so the release skill can cut 4.1.0. README quick-starts list Docker as the only requirement, drop every "Babashka is installed automatically" note, describe `bbin install` from the git repo as the route for people who want their own babashka, and document the Gatekeeper (`xattr -d com.apple.quarantine`) and SmartScreen ("Unblock") workarounds for browser downloads. The CHANGELOG entry explains the new install shape, that 4.1.0 still publishes the legacy `aishell`, `aishell.bat` and `aishell.sha256` so v4.0.0's upgrade keeps working, and that 4.2.0 drops them. Version bumped to 4.1.0.

### Acceptance criteria

- [ ] README has no babashka prerequisite anywhere
- [ ] CHANGELOG states the bridging window and the 4.2.0 drop
- [ ] Version is 4.1.0 in the CLI namespace
- [ ] `docs/ARCHITECTURE.md` or wherever the platform promise lives mentions the five binaries

### Blocked by

Build script, release workflow, install.sh, Windows installers, upgrade.
