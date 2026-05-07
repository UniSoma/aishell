---
id: aix-01kr1d72wv6c
title: Delete migration.clj no-op and its call sites
status: closed
type: chore
priority: 2
mode: afk
created: '2026-05-07T14:24:38.811180526Z'
updated: '2026-05-07T19:52:58.677774419Z'
closed: '2026-05-07T19:52:58.677774419Z'
tags:
- needs-triage
---

## Description

## What to build

`src/aishell/migration.clj` (9 lines) is a no-op left over from v3.0.0 when tmux support was dropped. It has two callers in `src/aishell/cli.clj` that exist only to keep the namespace alive — the comment in the file even says so.

**Direction:** Delete the namespace and remove its call sites. If migration warnings are needed in the future, introduce them with a real abstraction (e.g. a `MigrationNotice` record) at that time — not a no-op stub.

**Pre-flight reminder (Phase 47 lesson):** before deleting, `grep -rn "show-v2\\.9-migration-warning\\|aishell\\.migration" .` to find ALL callers across the whole repo. Babashka/SCI resolves symbols at analysis time, so a missed caller will crash the tool at startup.

**Files involved:** `src/aishell/migration.clj` (delete), `src/aishell/cli.clj` (remove call sites at ~lines 218 and 595).

## Acceptance hint

Sanity-check that `aishell --help` still works after the delete — that was the failure mode last time.

## Blocked by

None.

## Notes

**2026-05-07T19:52:58.677774419Z**

Deleted src/aishell/migration.clj and three call sites in src/aishell/cli.clj (handle-setup, do-dispatch, and the require). aishell --help, --version, ps --json verified post-delete.
