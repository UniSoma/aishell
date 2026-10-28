---
id: aix-01m61f50zynt
title: Test that docstrings and docs avoid the glossary's _Avoid_ terms
status: open
type: chore
priority: 2
mode: afk
created: '2026-10-28T14:38:14.014736980Z'
updated: '2026-10-28T14:38:14.014736980Z'
tags:
- needs-triage
acceptance:
- title: A test fails when a docstring in src uses a scannable _Avoid_ term outside its allowlist
  done: false
- title: The suite passes on main once the test lands
  done: false
---

## Description

Every review of aix-01m61c17fyb8 on 2026-10-28 (code review, prose refute, context review) caught by hand a term CONTEXT.md lists under _Avoid_: "project image", "project Dockerfile", "foundation" for the Base image. A test can catch these before review.

Sketch: a test reads each CONTEXT.md entry's _Avoid_ list and scans docstrings and comments in `src/` (and possibly README.md / docs/) for those phrases, with an allowlist for legitimate uses (e.g. "foundation" when naming the Foundation image itself, quoted CLI output). Settle in refinement which avoid terms are scannable without false positives; some entries' _Avoid_ words (e.g. "state", "cache") are too generic to grep.

Proposed 2026-10-28; awaiting the user's go-ahead (tagged needs-triage until then).
