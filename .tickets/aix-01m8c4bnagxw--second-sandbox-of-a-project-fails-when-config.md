---
id: aix-01m8c4bnagxw
title: Second sandbox of a project fails when config publishes ports
status: open
type: bug
priority: 2
mode: hitl
created: '2026-11-26T14:32:39.504746661Z'
updated: '2026-11-26T14:32:39.731965033Z'
tags:
- needs-triage
acceptance:
- title: Reproduced (or ruled out) with two sandboxes of one project and a ports entry
  done: false
- title: Starting a second sandbox no longer fails on ports already published by the first
  done: false
---

## Description

Every `ports` entry in config.yaml is passed as `-p` on every `docker run` (src/aishell/docker/run.clj:642-644 at 0f3fcd6), with no exception for a second sandbox. So with one sandbox already running and publishing a host port, starting a named sandbox (`aishell <harness> <name>` or similar) or the `vscode` sandbox (when none is running to join, ADR 0013) should fail with Docker's "port is already allocated".

Not yet reproduced: found by reading the code during the 2026-11-26 open-ticket review. First step is a repro with Docker.

Related decision: headless sandboxes from the orchestration layer leave out `ports` (note on aix-01kxmde9p330). A fix here may take the same shape, e.g. only the first sandbox of a project publishes ports, or the second warns and skips them.
