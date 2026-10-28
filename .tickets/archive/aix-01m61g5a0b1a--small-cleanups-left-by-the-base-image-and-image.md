---
id: aix-01m61g5a0b1a
title: Small cleanups left by the --base-image and image-id reviews
status: closed
type: chore
priority: 2
mode: afk
created: '2026-10-28T14:55:51.813553267Z'
updated: '2026-10-28T15:02:38.123435693Z'
closed: '2026-10-28T15:02:38.123435693Z'
tags:
- ready-for-agent
acceptance:
- title: image-id lives in aishell.docker and all callers use it there
  done: true
- title: No hand-written "aishell:foundation" remains in docker/base.clj
  done: true
- title: No 'Global base image' or Base-image-as-'base image' wording remains in the listed namespaces, strings and DEVELOPMENT.md
  done: true
links:
- aix-01m61edx76kq
---

## Description

Raised by the reviews landing aix-01m61edx76kq (108082c) on 2026-10-28; the user asked for them directly.

1. Move `ext/image-id` (docker/extension.clj) next to its sibling helpers `image-exists?` and `get-image-label` in `aishell.docker`; update the callers in extension.clj and docker/base.clj.
2. `docker/base.clj` writes `"aishell:foundation"` by hand; use the existing `foundation-image-tag` constant (docker/build.clj:18), or move the constant where both can share it without a require cycle.
3. Replace avoided glossary terms (CONTEXT.md _Avoid_ lists) in: the namespace docstrings of docker/extension.clj ("base image") and docker/base.clj ("Global base image"); the `;; Image tag for the base layer` echo comment in base.clj; the "Global base image" UI strings in `build-base-image`; and docs/DEVELOPMENT.md:104. UI strings here may change since this ticket asks for it.

## Notes

**2026-10-28T15:02:38.123435693Z**

image-id moved to aishell.docker; base.clj uses build/foundation-image-tag; Global/lowercase base image wording replaced with Base image in base.clj, extension.clj, UI strings and DEVELOPMENT.md
