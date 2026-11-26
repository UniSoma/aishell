---
id: aix-01m61f50wy0c
title: Add a bb pre-commit task that runs lint and tests
status: closed
type: chore
priority: 2
mode: afk
created: '2026-10-28T14:38:13.918420233Z'
updated: '2026-11-26T14:33:54.970184463Z'
closed: '2026-11-26T14:33:54.970184463Z'
tags:
- settled
acceptance:
- title: bb pre-commit fails when clj-kondo reports an error in src or test
  done: true
- title: bb pre-commit fails when a test fails
  done: true
- title: AGENTS.md names bb pre-commit as the gate
  done: true
---

## Description

As of 7462c39 the required gate is two commands (`clj-kondo --lint src test` per AGENTS.md, and `bb test`), and `bb.edn` has only a `test` task. Agent skills (`land`, `clean-prose`) look for a single project gate such as `bb pre-commit`; each landing brief on 2026-10-28 had to spell the gate out by hand.

Add a `pre-commit` task to `bb.edn` that runs clj-kondo over `src` and `test` (not `scripts/`, which has two known namespace errors) and then the test suite, failing on either. Point AGENTS.md's "Lint before commit" rule at it.

## Notes

**2026-11-26T14:33:54.970184463Z**

Added a bb pre-commit task (clj-kondo over src and test, then the test suite) and pointed AGENTS.md at it as the gate.
