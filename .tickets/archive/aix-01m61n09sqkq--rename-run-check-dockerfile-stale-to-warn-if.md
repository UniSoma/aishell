---
id: aix-01m61n09sqkq
title: Rename run/check-dockerfile-stale to warn-if-foundation-stale
status: closed
type: chore
priority: 2
mode: afk
created: '2026-10-28T16:20:30.640377296Z'
updated: '2026-10-28T16:25:43.539415389Z'
closed: '2026-10-28T16:24:37.639459357Z'
tags:
- ready-for-agent
acceptance:
- title: No check-dockerfile-stale remains in src, test or docs
  done: true
links:
- aix-01m61kh6dddr
- aix-01m61n9vbtp7
---

## Description

Proposed by the prose sweep of aix-01m61kh6dddr (79eb7c3) on 2026-10-28: run/check-dockerfile-stale checks all of the Foundation image's embedded content, not one Dockerfile, and only warns. Rename it to warn-if-foundation-stale with its callers, tests and docstring. Behaviour stays the same.

## Notes

**2026-10-28T16:24:37.639459357Z**

Renamed run/check-dockerfile-stale to warn-if-foundation-stale with its one caller; docstring now uses the glossary term stale and says the hash covers files COPYd from the build context; added a run_test covering the three hash paths. Behaviour unchanged.
