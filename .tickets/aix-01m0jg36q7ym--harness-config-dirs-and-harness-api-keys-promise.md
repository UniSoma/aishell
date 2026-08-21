---
id: aix-01m0jg36q7ym
title: harness-config-dirs and harness-api-keys promise display order but rely on array-map size
status: open
type: task
priority: 3
mode: hitl
created: '2026-08-21T15:47:37.319402952Z'
updated: '2026-08-21T15:47:37.319402952Z'
tags:
- registry
- needs-triage
---

## Description

Found by the verification agent during the harness registry migration (aix-01m0dp97mdhs).

`harness-config-dirs` and `harness-api-keys` in src/aishell/docker/run.clj both build with `(into {} ...)` and their docstrings promise entries in display order. That holds only while the map is small enough to stay an array-map. A ninth entry flips it to a hash-map and the -v and -e argv silently reorder.

Not a live bug: the registry has six harnesses today. Fix by building with `into []` plus lookup, or by sorting explicitly, so the docstring's promise is real.
