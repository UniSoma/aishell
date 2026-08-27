---
id: aix-01m1203dxk9g
title: 'Runtime key: opt-in gVisor (runsc) with aishell check reporting'
status: open
type: feature
priority: 2
mode: afk
created: '2026-08-27T16:15:58.387248453Z'
updated: '2026-08-27T16:17:26.980021882Z'
acceptance:
- title: runtime accepts runc|runsc; runsc reaches docker run as --runtime=runsc
  done: false
- title: aishell check lists available runtimes and flags runsc missing when selected
  done: false
- title: A harness completes a prompt under runsc on this host; TROUBLESHOOTING.md lists known gVisor limitations found
  done: false
deps:
- aix-01m1203e0gm4
tags:
- security
---

## Description

Add a per-project `runtime` key (`runc` default | `runsc`) and CLI flag, mapped to `--runtime` on `docker run`. Independent of the security profile by decision (ADR 0006): a profile never auto-selects a runtime, because runsc is Linux-only and silent per-machine variance is what we are avoiding.

`aishell check` detects available runtimes from `docker info` (`.Runtimes`) and reports whether `runsc` is installed; selecting `runsc` where it is absent exits 1 with install guidance (gVisor's apt repo). `--runtime` in `docker_args` is one of the blocked flags from the security-profiles ticket, so this key is the only path.

Blocked by the gVisor spike: if the spike shelves gVisor, this ticket is closed as wontfix.
