---
id: aix-01m5kd8xtzw0
title: Say content match, not up to date, for foundation staleness
status: closed
type: chore
priority: 4
mode: afk
created: '2026-10-23T03:36:02.655669305Z'
updated: '2026-10-23T03:39:29.819106988Z'
closed: '2026-10-23T03:39:29.819106988Z'
tags:
- ready-for-agent
acceptance:
- title: The foundation build no longer says up to date for a content match
  done: true
- title: check-dockerfile-staleness is renamed check-foundation-staleness
  done: true
- title: get-dockerfile-hash and needs-rebuild? docstrings describe the hash as covering all embedded content
  done: true
- title: bb test passes and clj-kondo --lint src test is clean
  done: true
---

## Description

Follow-up to aix-01m5kc23x2sd (0c51cb5, 6187bdb), approved by Jonas on 2026-10-23. Per CONTEXT.md, "stale" is a content mismatch and "out of date" is age; these spots still blur the two:

1. build.clj (~line 163) prints "Image aishell:foundation is up to date (use --force to rebuild)" for a content match. Reword to "Image aishell:foundation matches its embedded Dockerfile (use --force to rebuild)", consistent with the approved aishell check message. Update any test asserting the old text.
2. Rename `check-dockerfile-staleness` to `check-foundation-staleness` (it checks all embedded content), updating callers and tests.
3. Fix docstrings: `get-dockerfile-hash` (remove the em dash) and `needs-rebuild?` (says "Dockerfile hash changed since build"; the hash covers all embedded content).

No behaviour change beyond the one message text.

## Notes

**2026-10-23T03:39:29.819106988Z**

A cached foundation build now prints 'Image aishell:foundation matches its embedded Dockerfile (use --force to rebuild)' in place of 'is up to date'. check-dockerfile-staleness is renamed check-foundation-staleness. The get-dockerfile-hash and needs-rebuild? docstrings now describe the hash as covering all embedded content.
