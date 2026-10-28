---
id: aix-01m61n9vbtp7
title: Share one Foundation-image staleness check across run, check and build
status: open
type: chore
priority: 2
mode: afk
created: '2026-10-28T16:25:43.539415389Z'
updated: '2026-10-28T16:25:43.539415389Z'
tags:
- ready-for-agent
acceptance:
- title: One function decides whether the Foundation image is stale; run, check and build call it
  done: false
links:
- aix-01m61n09sqkq
---

## Description

Found 2026-10-28 by the review of aix-01m61n09sqkq (014bfaf): the stored-vs-embedded hash comparison is written out in run.clj:73-76 (warn-if-foundation-stale) and check.clj:90-94, and build.clj:80 makes a third comparison of the same kind. Pull out one foundation-stale? predicate and use it in all three. Behaviour stays the same; don't rename the :dockerfile-hash state key here (separate decision).
