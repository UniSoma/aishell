---
id: aix-01kr1d73gmbm
title: Consolidate gitleaks scan_state and warnings
status: open
type: chore
priority: 2
mode: hitl
created: '2026-05-07T14:24:39.443938975Z'
updated: '2026-05-07T14:24:39.443938975Z'
tags:
- needs-triage
---

## Description

## What to build

`src/aishell/gitleaks/scan_state.clj` (~48 lines, file I/O + EDN) and `src/aishell/gitleaks/warnings.clj` (~32 lines, display) share exactly one data point: "days since last scan." The split is by line count, not by seam. The display module reaches directly into the persistence module's format.

**Direction:** Decide whether to merge into a single `gitleaks.clj` (~80 lines), or fold into a broader gitleaks module (alongside whatever else gets added). Either way, eliminate the leaky split: display logic should not know the EDN format.

**Why this matters:**
- **Locality** — scan freshness lives in one place.
- The "why" of two namespaces here was line count, not testability or independent reuse.

**Files involved:** `src/aishell/gitleaks/scan_state.clj`, `src/aishell/gitleaks/warnings.clj`, callers (`run.clj`, `cli.clj`, `check.clj`).

## Blocked by

None.
