---
id: aix-01m61tetcve0
title: Read :foundation-hash, falling back to :dockerfile-hash
status: closed
type: chore
priority: 2
mode: afk
created: '2026-10-28T17:55:49.275600319Z'
updated: '2026-10-28T17:59:56.863671092Z'
closed: '2026-10-28T17:59:56.863671092Z'
tags:
- ready-for-agent
acceptance:
- title: run and check read :foundation-hash, falling back to :dockerfile-hash; tests cover both state shapes
  done: true
- title: setup and update still write both keys
  done: true
- title: get-dockerfile-hash is renamed foundation-content-hash and cli.clj's state writes call it
  done: true
- title: Docs no longer say nothing reads :foundation-hash
  done: true
external_refs:
- git:42a5b4e5da194437d5571edd5231613b9ce3e869
---

## Description

Decided by the user 2026-10-28. v2.8.0 deprecated the state key `:dockerfile-hash` in favour of `:foundation-hash` (CHANGELOG ~line 689), and setup and update write both with the same value (cli.clj ~491, ~615), but check.clj and run.clj still read only `:dockerfile-hash`. Finish the move: one state-level reader returns `:foundation-hash`, falling back to `:dockerfile-hash` for state files written before v2.8.0, and run's staleness warning and check's report use it. Keep writing both keys so an older aishell still finds the one it reads. Rename `build/get-dockerfile-hash` to `foundation-content-hash` (it hashes the Dockerfile and every file it COPYs) and use it for the state writes in cli.clj. The image label `aishell.dockerfile.hash` stays as it is. Update docs that say nothing reads `:foundation-hash` (state.clj docstring, ARCHITECTURE.md, DEVELOPMENT.md).

## Notes

**2026-10-28T17:59:56.863671092Z**

run and check read the Foundation content hash through state/foundation-hash (:foundation-hash, falling back to :dockerfile-hash); setup and update write both keys through state/assoc-foundation-hash; build/get-dockerfile-hash renamed foundation-content-hash. Image label unchanged.
