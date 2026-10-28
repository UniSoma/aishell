---
id: aix-01m61gkfh089
title: Finish aligning Base image wording and global-dockerfile names with the glossary
status: closed
type: chore
priority: 2
mode: afk
created: '2026-10-28T15:03:36.210093650Z'
updated: '2026-10-28T15:24:09.765025058Z'
closed: '2026-10-28T15:23:20.989238409Z'
tags:
- ready-for-agent
acceptance:
- title: DEVELOPMENT.md names only functions that exist
  done: true
- title: No 'Global base image', 'project extension Dockerfile' or lowercase 'extended image' remains in docs, README or src docstrings
  done: true
- title: check.clj uses base/base-image-tag
  done: true
- title: No global-dockerfile-* name remains in src, test or docs
  done: true
deps:
- aix-01m61edx3fdb
links:
- aix-01m61g5a0b1a
- aix-01m61hs429e6
- aix-01m61hs45kef
---

## Description

Left open by the landing of aix-01m61g5a0b1a (109c00c) on 2026-10-28; the user asks for small cleanups to be done directly.

1. `docs/DEVELOPMENT.md:208` names `docker/extension/build-project-extension`, which does not exist; point it at `build-extended-image` / `needs-extended-rebuild?`.
2. Avoided terms (CONTEXT.md _Avoid_ lists) still in: "Global base image" in docs/ARCHITECTURE.md, docs/TROUBLESHOOTING.md, docs/CONFIGURATION.md and README.md (renaming the CONFIGURATION.md heading means updating the README anchor link); "project extension Dockerfile" and lowercase "extended image" in other extension.clj docstrings.
3. `check.clj:67` writes `"aishell:base"` by hand; use `base/base-image-tag`.
4. Rename `global-dockerfile-path`, `global-dockerfile-exists?` and `global-dockerfile-hash` (docker/base.clj) to `base-dockerfile-*`, with their callers in setup/update and docs/DEVELOPMENT.md:104. CONTEXT.md avoids "global image" for the Base image.

Waits on aix-01m61edx3fdb because both edit cli.clj.

## Notes

**2026-10-28T15:23:20.989238409Z**

DEVELOPMENT.md points at needs-extended-rebuild?/build-extended-image; 'Global base image', 'project extension Dockerfile' and lowercase 'extended image' replaced in docs, README and docstrings (CONFIGURATION heading anchor now #base-image-customization, README and HARNESSES links updated); check.clj defaults to base/base-image-tag; global-dockerfile-* renamed base-dockerfile-* with callers in check, info, cli and docs.
