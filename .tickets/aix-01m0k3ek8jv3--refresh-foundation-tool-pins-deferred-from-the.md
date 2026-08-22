---
id: aix-01m0k3ek8jv3
title: Refresh foundation tool pins (deferred from the trixie bump)
status: open
type: task
priority: 3
mode: hitl
created: '2026-08-21T21:25:53.554482774Z'
updated: '2026-08-21T21:26:00.316130070Z'
acceptance:
- title: Each pinned tool version reviewed against upstream and bumped or explicitly left alone with a reason
  done: false
- title: SQLITE_VERSION, SQLITE_YEAR and SQLITE_SHA256 move together if SQLite is bumped, with the SHA confirmed against upstream's published SHA3-256
  done: false
- title: Decision recorded on whether ncurses-term joins the install block, on grounds other than xterm-ghostty
  done: false
- title: Foundation image builds and every in-build probe passes
  done: false
tags:
- ready-for-human
links:
- aix-01m0k3e5txye
---

## Description

Held out of the trixie distro bump under a surgical-scope rule: one concern per change, so a build failure there has an unambiguous cause. A foundation rebuild hits every user either way, so these ride along on whatever the next foundation change is.

## Pins to review

`BABASHKA_VERSION`, `BBIN_VERSION`, `CUE_VERSION`, `UV_VERSION`, `GITLEAKS_VERSION`, and `SQLITE_VERSION` (which also needs its `SQLITE_YEAR` and pinned SHA-256 refreshed — the download id is derived in-shell from the version, but sqlite.org URLs carry the release year, and the SHA-256 must be confirmed against the SHA3-256 upstream publishes before being pinned).

## The ncurses-term question

Trixie makes `ncurses-term` slightly more tempting: ~2,800 terminfo entries for a ~500 kB download, against the ~45 entries `ncurses-base` gives us.

**It would not fix the `xterm-ghostty` papercut, so do not add it for that reason.** Verified against trixie's package file list: `ncurses-term` ships `/usr/share/terminfo/g/ghostty` and there is **no `x/xterm-ghostty` alias**. Ghostty sets `TERM=xterm-ghostty`, so the lookup still fails and the entrypoint's TERM-fallback stays load-bearing. If `ncurses-term` earns its place, it earns it on the other 2,799 entries.

## Note on the SQLite pin

Bumping `SQLITE_VERSION` invalidates only the tail of the build — the COPY from the builder stage is deliberately placed late. The build self-verifies: it fails on a SHA mismatch, and fails if the installed library is missing any expected compile option. See ADR 0004 for why this is compiled from source rather than taken from apt or from upstream prebuilts.
