---
id: aix-01m8cfkea9ac
title: 'Global security_profile is a floor that extends: none cannot drop'
status: open
type: bug
priority: 1
mode: hitl
created: '2026-11-26T17:49:08.809770657Z'
updated: '2026-11-26T17:49:09.012313139Z'
tags:
- security
- needs-triage
links:
- aix-01m1203dkknf
---

## Description

The project folder is mounted read-write into the Sandbox, so code inside it can edit `.aishell/config.yaml` before the next launch. Since aix-01m1203dkknf (commit a105ac4) the stricter profile wins when global and project configs merge, so a project `default` cannot relax a global `strict`. But a project that sets `extends: none` drops the global config entirely, the global `strict` with it. Decision Y2 on aix-01m1203dkknf named this as a limit and left it open.

Proposed fix, option (a), chosen by the user (2026-11-26): the global `security_profile` is a floor that applies whatever `extends` says. `extends: none` still drops the other global keys, never the profile. The rule sits in `config/load-config`, following ADR 0006 ("config can add restrictions, never relax them").

Also say in docs/CONFIGURATION.md (security_profile section) that `strict` belongs in the global config: a project-only `strict` can be edited away from inside the Sandbox, which no config rule can prevent while the project folder is writable.

Options not chosen: (b) refuse to start when global is strict and the project sets `extends: none` (breaks legitimate `extends: none` uses); (c) (a) plus a warning when the effective profile drops below the last start's (needs per-project state).
