---
id: aix-01kr1d71b75y
title: state.clj owns the schema (typed accessors, single migration point)
status: open
type: chore
priority: 2
mode: hitl
created: '2026-05-07T14:24:37.222878296Z'
updated: '2026-05-07T14:24:37.222878296Z'
tags:
- needs-triage
---

## Description

## What to build

`src/aishell/state.clj` (~49 lines) interface is "read EDN / write EDN." Callers across `cli.clj`, `run.clj`, `info.clj`, `check.clj`, and `update_check.clj` reach in for specific keys. The ~48-line schema lives in a docstring, not in code. Migrations (`:dockerfile-hash` -> `:foundation-hash`) are encoded as conditionals scattered across callers.

**Project-memory flag:** Phase 47 incident — schema-adjacent deletes can break the tool because Babashka/SCI resolves symbols at analysis time. The wider the surface that knows the EDN keys, the higher the cost of any change.

**Direction:** Make `state.clj` own the schema — typed accessors per field, a single read/migrate step, a single write point. Callers stop knowing the on-disk format.

**Why this matters:**
- **Locality** — every "what is in state?" question lands in one file.
- **Leverage** — future schema changes are one diff, not a grep-and-pray sweep across the codebase.
- **Tests** — orchestration code can construct state values without writing EDN files.

**Files involved:** `src/aishell/state.clj` plus all callers (`cli.clj`, `run.clj`, `info.clj`, `check.clj`, `update_check.clj`).

## Blocked by

None.
