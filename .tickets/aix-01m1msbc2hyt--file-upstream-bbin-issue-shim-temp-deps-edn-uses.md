---
id: aix-01m1msbc2hyt
title: 'File upstream bbin issue: shim temp deps.edn uses gensym, collides across UIDs'
status: open
type: task
priority: 3
mode: hitl
created: '2026-09-03T23:23:32.817379985Z'
updated: '2026-09-03T23:23:36.193152749Z'
tags:
- ready-for-human
links:
- aix-01m1ms2ngp23
---

## Description

Upstream babashka/bbin main still uses (gensym "bbin") for the shim's temp deps.edn as of 2026-09-03, and the shim exec's bb so delete-on-exit never fires. Report the fixed-path collision and the exec/delete-on-exit interaction; point at aishell's workaround in the linked ticket. Needs a GitHub login, so a human files it.
