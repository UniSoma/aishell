---
id: aix-01kr1d6yp7tg
title: Decide fate of docker.clj — deepen into a probe, or inline
status: open
type: chore
priority: 2
mode: hitl
created: '2026-05-07T14:24:34.502939899Z'
updated: '2026-05-07T14:24:34.502939899Z'
tags:
- needs-triage
---

## Description

## What to build

`src/aishell/docker.clj` (~77 lines) holds six near-identical pass-throughs over the docker CLI: shell out, check exit, swallow errors, return bool/string. The deletion test concentrates almost no complexity — it just spreads four lines of `try/p/shell/zero?` across callers.

**Direction (decision needed):** Either
- **Inline** it — the helpers earn very little and the project is small enough that a `try/shell` per call site is fine; or
- **Deepen** it into a single **docker probe** returning one inspection record (engine running? foundation image present? labels match?) consumed by `run.clj` and `check.clj`.

**Why this matters:**
- **Locality** for the "is the host ready?" question.
- **Leverage** — `check.clj` and `run.clj` stop duplicating availability gates.
- **Tests** — one mock surface, not five.

**Files involved:** `src/aishell/docker.clj` and its callers (`run.clj`, `check.clj`, `info.clj`, `docker/build.clj`).

## Blocked by

None.
