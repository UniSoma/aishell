---
id: aix-01m6s1689gfa
title: Move the foundation image's Node base from 24 to 26
status: open
type: task
priority: 3
mode: hitl
created: '2026-11-06T18:16:00.552813278Z'
updated: '2026-11-06T18:16:00.552813278Z'
tags:
- ready-for-human
acceptance:
- title: Every harness CLI that installs through npm is checked for Node 26 support (declared engines and a launch in a Sandbox), and any that does not support it is named with a decision
  done: false
- title: The Node stage, the info test, README and ARCHITECTURE all say 26, and CHANGELOG carries the change with an upgrade note for user layers that depend on Node 24
  done: false
- title: Foundation image builds on amd64 and arm64 and every in-build probe passes
  done: false
links:
- aix-01m0k3ek8jv3
---

## Description

Node 26 ("Lithium") became Active LTS on 2026-10-28. Node 24 entered Maintenance LTS on 2026-10-20 and reaches end of life on 2028-04-30, so there is no urgency, but the foundation image should move before 24's maintenance window runs short. docker-node publishes `26-trixie-slim` for amd64 and arm64v8, so the distro stays trixie.

Kept out of the 2026-11-06 tool pin bump (babashka, uv, gosu, SQLite) under the surgical-scope rule: one concern per foundation change, so a build or harness failure has one cause. A Node major can break the npm-installed harnesses, which the tool bumps cannot.

### Sites

- `src/aishell/docker/templates.clj:27`: `FROM node:24-trixie-slim AS node-source`
- `test/aishell/info_test.clj:51-52`: asserts major `24` and tag `24-trixie-slim`
- `README.md:609`: "Node.js 24 (with npm, npx)"
- `docs/ARCHITECTURE.md:87`: "Node.js 24 runtime"
