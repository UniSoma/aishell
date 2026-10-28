---
id: aix-01m61hs429e6
title: aishell check reports the Foundation image as the Base image
status: open
type: bug
priority: 2
mode: afk
created: '2026-10-28T15:24:09.659520871Z'
updated: '2026-10-28T15:24:09.659520871Z'
tags:
- ready-for-agent
acceptance:
- title: After setup, aishell check reports the Base image as aishell:base
  done: false
- title: check-extension compares against the Base image, so a current Extended image on a custom Base image is not reported as needing a rebuild
  done: false
- title: A test covers check with state as setup writes it
  done: false
deps:
- aix-01m61h9gqhew
links:
- aix-01m61gkfh089
---

## Description

Found 2026-10-28 while landing aix-01m61gkfh089 (2caad42). `check/check-base-image` reads `(:image-tag state)`, but setup writes the Foundation image tag there (`cli.clj:492`, `cli.clj:622` at 2caad42). After setup, `aishell check` prints "Base image exists: aishell:foundation". It also passes that tag to `check-extension` as the base tag, so with a custom Base image (`~/.aishell/Dockerfile`) `check` likely keeps reporting that the Project extension needs a rebuild.

Direction: the Base image check and the extension check should use `base/base-image-tag`; decide whether `:image-tag` in state still has a reader, and if not, what to do with it (keep for compatibility vs. stop writing). Add a test that runs the check with state as setup writes it.
