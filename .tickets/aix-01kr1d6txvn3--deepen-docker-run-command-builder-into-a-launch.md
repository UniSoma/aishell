---
id: aix-01kr1d6txvn3
title: Deepen docker run command-builder into a launch pipeline
status: open
type: chore
priority: 2
mode: hitl
created: '2026-05-07T14:24:30.651747678Z'
updated: '2026-07-15T02:44:18.068604119Z'
tags:
- needs-triage
- codebase-design
---

## Description

## What to build

`src/aishell/docker/run.clj` (~482 lines) has 15+ small `build-*-args` helpers (mounts, env, ports, harness volume, harness aliases, harness configs) threaded through one ~85-line composer. Each helper is shallow; reordering them breaks intent; tests either run the whole thread or assert on trivial string concatenation.

**Direction:** Reframe the module around the **container launch** as the deep concept. Helpers collapse into one composition pipeline; the public interface becomes "given a launch request, produce the argv." Builders stop being a public API surface.

**Why this matters:**
- **Locality** — one place to learn how an aishell container is launched.
- **Leverage** — the same pipeline can serve `claude`, `attach`, `exec`, `vscode`. Today each touches the helpers slightly differently.
- **Tests** — assert on resulting argv given launch inputs, not on string fragments.

**Files involved:** `src/aishell/docker/run.clj`, plus callers `src/aishell/run.clj`, `src/aishell/attach.clj`, `src/aishell/vscode.clj`.

## Blocked by

None — can start immediately. (Soft cousin: ticket on image tiers may make this tidier afterward, but does not block.)
