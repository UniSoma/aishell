---
id: aix-01m61n09sqkq
title: Rename run/check-dockerfile-stale to warn-if-foundation-stale
status: in_progress
type: chore
priority: 2
mode: afk
created: '2026-10-28T16:20:30.640377296Z'
updated: '2026-10-28T16:20:46.137156539Z'
tags:
- ready-for-agent
acceptance:
- title: No check-dockerfile-stale remains in src, test or docs
  done: false
links:
- aix-01m61kh6dddr
---

## Description

Proposed by the prose sweep of aix-01m61kh6dddr (79eb7c3) on 2026-10-28: run/check-dockerfile-stale checks all of the Foundation image's embedded content, not one Dockerfile, and only warns. Rename it to warn-if-foundation-stale with its callers, tests and docstring. Behaviour stays the same.
