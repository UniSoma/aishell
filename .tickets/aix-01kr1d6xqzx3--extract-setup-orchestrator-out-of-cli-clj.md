---
id: aix-01kr1d6xqzx3
title: Extract setup orchestrator out of cli.clj
status: open
type: chore
priority: 2
mode: hitl
created: '2026-05-07T14:24:33.535210443Z'
updated: '2026-07-15T02:44:18.299308037Z'
tags:
- needs-triage
- codebase-design
---

## Description

## What to build

`src/aishell/cli.clj` (~752 lines) mixes argv parsing, help text, and multi-step orchestration. `handle-setup` and `handle-update` together know about state, foundation builds, harness volumes, base/extension cascades, and version validation — 10+ collaborators. There is no "setup" module to test; setup behavior is observable only through the CLI surface.

**Direction:** Extract the orchestration body into a **setup** (or `provisioning`) module whose interface is "given a setup request, drive the build to completion." `cli.clj` becomes a parser + dispatcher.

**Why this matters:**
- **Locality** — the setup state machine lives in one file.
- **Leverage** — the orchestrator becomes callable by tests, by `aishell check --fix`, or by a future SDK.
- **Tests** — run setup against a stubbed docker / filesystem and assert on resulting state.

**Files involved:** `src/aishell/cli.clj` (extraction site), `src/aishell/state.clj`, `src/aishell/docker/build.clj`, `src/aishell/docker/volume.clj`, `src/aishell/docker/base.clj`, `src/aishell/docker/extension.clj` (collaborators).

## Blocked by

None — can start immediately. (Soft cousin: easier after the state-schema ticket; not blocking.)
