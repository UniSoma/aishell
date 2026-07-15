---
id: aix-01kr1d6w8k2p
title: Introduce image-tier abstraction across foundation/base/extension
status: open
type: chore
priority: 2
mode: hitl
created: '2026-05-07T14:24:32.019081501Z'
updated: '2026-07-15T02:44:18.180927162Z'
tags:
- needs-triage
- codebase-design
---

## Description

## What to build

`docs/ARCHITECTURE.md` describes a three-tier image chain (foundation → base → extension), but no module owns the concept. `src/aishell/docker/base.clj`, `src/aishell/docker/extension.clj`, and `src/aishell/docker/build.clj` each reinvent the same pattern: Dockerfile content + content hash + cache label + parent image + rebuild-if-stale. The logic is split across three differently-shaped places.

**Direction:** Introduce **image tier** as a deep module — a tier knows its Dockerfile source, target tag, parent tag, cache-label key, and rebuild policy. The three tiers become three adapters of one interface.

**Why this matters:**
- **Locality** for cache-invalidation bugs — today they hide between tiers.
- **Leverage** — adding a tier or tweaking rebuild semantics is one change, not three.
- **Tests** — drive a fake docker without rebuilding actual images.

**Files involved:** `src/aishell/docker/base.clj`, `src/aishell/docker/extension.clj`, `src/aishell/docker/build.clj`, `src/aishell/docker/hash.clj`.

## Blocked by

None.
