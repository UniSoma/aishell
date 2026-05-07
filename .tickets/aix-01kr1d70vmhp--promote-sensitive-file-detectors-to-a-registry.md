---
id: aix-01kr1d70vmhp
title: Promote sensitive-file detectors to a registry
status: open
type: chore
priority: 2
mode: hitl
created: '2026-05-07T14:24:36.724066058Z'
updated: '2026-05-07T14:24:36.724066058Z'
tags:
- needs-triage
---

## Description

## What to build

Every pattern detector in `src/aishell/detection/patterns.clj` (`detect-env-files`, `detect-ssh-keys`, …) has the same signature and shape: `[project-dir excluded-dirs] -> seq of {:path :severity :reason}`. They are called in a hard-coded `concat` in `src/aishell/detection/core.clj`. Adding a pattern is three edits across three files; severity and reason live split between the matcher and `src/aishell/detection/formatters.clj`.

The shared shape is a strong hint at a hidden concept — a **detector** — but the concept is unnamed. It is just six functions with the same signature.

**Direction:** Promote the shape to data — a registry of detector entries (id, matcher, severity, reason). `core.clj`'s scan walks the registry; `formatters.clj` reads severity and reason from the entry, not from a parallel mapping.

**Why this matters:**
- **Locality** — matcher, severity, and reason colocate.
- **Leverage** — `README.md` already advertises user-defined custom detectors via `config.yaml`; today that hook is awkward because there is no detector type.
- **Tests** — stub the registry; adding a pattern is one entry, not three edits.

**Files involved:** `src/aishell/detection/patterns.clj`, `src/aishell/detection/core.clj`, `src/aishell/detection/formatters.clj`.

## Blocked by

None.
