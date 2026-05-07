---
id: aix-01kr1d75npmh
title: Make output/error testable (separate format from System/exit)
status: open
type: chore
priority: 2
mode: hitl
created: '2026-05-07T14:24:41.654209041Z'
updated: '2026-05-07T14:24:41.654209041Z'
tags:
- needs-triage
---

## Description

## What to build

`src/aishell/output.clj` is imported by nearly every namespace. `output/error` calls `System/exit`, making error paths untestable — every test that touches an error path either swallows the exit or only exercises the happy path.

**Direction:** Separate "format an error message" from "exit the process." The decision to exit lives at the CLI top level (one boundary); the format-error helper is pure and callable from anywhere.

**Why this matters:**
- **Tests** — error-handling code paths can finally be covered.
- **Locality** — the exit decision lives at one boundary, not scattered through every error site.
- **Leverage** — the formatter could be reused for non-fatal warnings, log lines, or structured output.

**Files involved:** `src/aishell/output.clj` and its many callers (almost every namespace).

## Acceptance hint

Verify CLI exit semantics (correct exit codes for known failure modes) end-to-end after the change — this is the regression risk.

## Blocked by

None.
