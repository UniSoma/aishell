---
id: aix-01m1kyn7nsxe
title: 'Release workflow: build, five-runner smoke matrix, then release'
status: open
type: task
priority: 1
mode: afk
created: '2026-09-03T15:37:04.436449350Z'
updated: '2026-09-03T22:10:14.269928219Z'
parent: aix-01m1kydv7r76
tags:
- ready-for-human
deps:
- aix-01m1kyn7j5th
---

## Description

Parent spec: aix-01m1kydv7r76 (ADR 0007).

### What to build

The release workflow becomes three stages. A build job on ubuntu runs the build script and uploads `dist/` as a workflow artifact. A smoke matrix job downloads it on `ubuntu-latest`, `ubuntu-24.04-arm`, `macos-13`, `macos-latest` and `windows-latest`, runs the matching binary with `--version --json`, and asserts the version equals the tag. The release job depends on the matrix and creates the GitHub release with every asset. The existing `dry_run` input skips only the release job, so a workflow_dispatch dry run proves the whole path. The `setup-clojure` step pins the same babashka version the build script pins.

### Acceptance criteria

- [ ] A dry-run dispatch builds, smoke-tests on all five runners and creates no release
- [x] The tagged path publishes only after the matrix is green
- [x] bb version in the workflow matches the build script pin

### Blocked by

The build script ticket.
