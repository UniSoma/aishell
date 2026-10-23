---
id: aix-01m5kc23x2sd
title: Finish the foundation out-of-date terminology
status: closed
type: chore
priority: 3
mode: afk
created: '2026-10-23T03:14:50.914855004Z'
updated: '2026-10-23T03:19:58.724177299Z'
closed: '2026-10-23T03:19:58.724177299Z'
tags:
- ready-for-agent
acceptance:
- title: foundation-out-of-date-warning and out-of-date-after-days are private
  done: true
- title: No code name says timestamp for the build date; the label string aishell.build.timestamp is unchanged
  done: true
- title: CONTEXT.md defines stale as a content mismatch and the avoid line says so
  done: true
- title: aishell check reports Foundation image matches its embedded Dockerfile
  done: true
- title: bb test passes and clj-kondo --lint src test is clean
  done: true
---

## Description

Follow-up to aix-01m5k46ypnn6 (ac6791d, 7a820e4, 2775f63), approved by Jonas on 2026-10-23. Four small cleanups from that landing's reviews:

1. Make `foundation-out-of-date-warning` and `out-of-date-after-days` private in aishell.docker.build; tests reach them as `#'build/...`.
2. Rename code names from timestamp to build date: `build-timestamp-label` -> `build-date-label`, `build-timestamp` -> `foundation-build-date`, test `build-timestamp-by-build-kind` -> `build-date-by-build-kind`, plus matching locals and docstrings. The label string "aishell.build.timestamp" must NOT change: existing images carry it.
3. CONTEXT.md: add to the Foundation image entry: "A Foundation image built from embedded content other than the running aishell's is *stale*." Change the Foundation build date entry's avoid item to: "stale (that is a content mismatch, not age)".
4. check.clj:89: reword "Base Dockerfile is up to date" to "Foundation image matches its embedded Dockerfile" (update any test asserting the old text).

No behaviour change beyond the check message text.

## Notes

**2026-10-23T03:19:58.724177299Z**

Made the out-of-date helpers private, renamed timestamp code names to build date (label string unchanged), defined stale in CONTEXT.md, and reworded the check message to 'Foundation image matches its embedded Dockerfile'.
