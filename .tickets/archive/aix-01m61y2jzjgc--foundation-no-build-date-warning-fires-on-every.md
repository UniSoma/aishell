---
id: aix-01m61y2jzjgc
title: Foundation 'no build date' warning fires on every launch after upgrading from v4.4.1
status: closed
type: bug
priority: 2
mode: hitl
created: '2026-10-28T18:59:02.770426197Z'
updated: '2026-10-28T19:03:55.791268565Z'
closed: '2026-10-28T19:03:55.791268565Z'
tags:
- wontfix
---

## Description

Images built by v4.4.1 have no `aishell.build.timestamp` label. After upgrading to 4.5.0, `warn-if-foundation-out-of-date!` (src/aishell/docker/build.clj:25-50) treats a missing label as out of date, so every harness launch, `exec` and `vscode` prints "Foundation image has no build date."

Plain `aishell update` does not clear it: `foundation-build-date` (build.clj:52-63) keeps the previous stamp on a cached rebuild, and an image with no stamp stays unstamped. Only `aishell update --force` stamps the image.

The 4.5.0 changelog tells users to run `update --force` once, but anyone who misses that note sees the warning on every launch.

Options to weigh:

- Warn once per image, then stay quiet until the image changes.
- Report a missing build date only in `aishell check`, and keep the launch warning for images known to be over 30 days old.
- Stamp an unlabelled image on a cached rebuild, so plain `update` clears the warning. The stamp would claim a build date the apt layer may not have, so the age check would start counting from the wrong day.

## Notes

**2026-10-28T19:03:55.791268565Z**

Won't fix (user decision, 2026-10-28): the 'no build date' warning after upgrading from v4.4.1 is a one-time cost; it stops after one 'aishell update --force', and the 4.5.0 changelog upgrade note says so.
