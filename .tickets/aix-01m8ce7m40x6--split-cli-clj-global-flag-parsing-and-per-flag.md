---
id: aix-01m8ce7m40x6
title: 'Split cli.clj: global flag parsing and per-flag command applicability'
status: open
type: chore
priority: 3
mode: hitl
created: '2026-11-26T17:25:12.960530053Z'
updated: '2026-11-26T17:25:12.960530053Z'
tags:
- codebase-design
- needs-triage
---

## Description

src/aishell/cli.clj is 1114 lines (past the 1000-line locality limit) and holds at least six concepts: version checking, OpenCode whitelist and setup-state resolution with handle-setup, update, volumes, ps formatting, and global flag parsing with dispatch. Each Global flag carries a hand-written set of accepting commands plus a guard (--base-image, --unsafe, --name, and --unsafe-docker-args added in aix-01m1203dkknf).

Move global flag parsing and the flag-applicability guards into their own namespace, driven by one flag -> accepting-commands table. Found by the depth review of aix-01m1203dkknf; the user accepted the growth there and asked for this ticket (2026-11-26).
