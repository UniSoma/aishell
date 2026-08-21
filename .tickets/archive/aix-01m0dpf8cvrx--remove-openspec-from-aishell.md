---
id: aix-01m0dpf8cvrx
title: Remove OpenSpec from aishell
status: closed
type: task
priority: 1
mode: afk
created: '2026-08-19T19:02:51.546974996Z'
updated: '2026-08-21T14:21:49.645464949Z'
closed: '2026-08-21T14:21:49.645464949Z'
parent: aix-01m0dp97mdhs
tags:
- registry
- ready-for-agent
acceptance:
- title: --with-openspec rejected as unknown flag; gone from setup help and config validation
  done: true
- title: State with :with-openspec true warns once about removal, keys stripped on next state write
  done: true
- title: OpenSpec npm install, config-dir mount, and telemetry env var removed
  done: true
- title: Repo openspec/ directory deleted
  done: true
- title: Changelog entry; clj-kondo clean; tests green
  done: true
---

## Description

Prefactor for the harness registry (parent spec): OpenSpec no longer makes sense in aishell and is removed before the registry exists, so the registry is never born with an OpenSpec row.

End-to-end behavior: a user who never enabled OpenSpec sees no change (volume hash covers only enabled harnesses). A user with OpenSpec enabled gets a one-time warning on the next run/update/setup that OpenSpec support was removed; their stale state keys are dropped on the next state write and the harness volume repopulates without OpenSpec via the hash change. --with-openspec fails as a generic unknown flag. The repo's own openspec/ change-proposal directory (dead workflow, replaced by knot) is deleted too.

Extend the existing setup-state resolution tests to cover key stripping and the one-time warning.

## Notes

**2026-08-21T14:21:49.645464949Z**

OpenSpec removed from aishell across nine namespaces, six docs, and the repo's own openspec/ tree. --with-openspec is rejected as an unknown flag; state that had it enabled warns once and is stripped on the write that follows; the harness volume repopulates via the hash change. Users who never enabled it are untouched — pinned by a hash-equivalence test against the pre-change value. clj-kondo at baseline (14 warnings, 0 errors), 90 tests / 254 assertions green.
