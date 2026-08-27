---
id: aix-01m1203dtgtc
title: 'Resource caps: memory and cpus config keys'
status: open
type: feature
priority: 3
mode: afk
created: '2026-08-27T16:15:58.288259919Z'
updated: '2026-08-27T16:17:26.881759516Z'
acceptance:
- title: resources.memory and resources.cpus reach docker run; absent keys add no flags
  done: false
- title: Invalid values exit 1 with the key named
  done: false
- title: aishell info shows the caps; CONFIGURATION.md documents them
  done: false
tags:
- security
---

## Description

Add `resources: {memory: "8g", cpus: "4"}` to `.aishell/config.yaml`, mapped to `--memory` / `--cpus` on `docker run`. No default in either security profile (ADR 0006: OOM-killing a build is worse than no cap). Validate units the way Docker does; surface the active caps in `aishell info`. Document in `docs/CONFIGURATION.md`.
