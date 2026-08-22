---
id: aix-01m0k3e5txye
title: Move the distro image to debian:trixie-slim (glibc floor 2.39)
status: open
type: task
priority: 2
mode: hitl
created: '2026-08-21T21:25:39.805035512Z'
updated: '2026-08-21T21:26:00.316130070Z'
acceptance:
- title: Foundation image builds successfully from debian:trixie-slim on all three stages
  done: false
- title: Build-time assertion fails the build if the final image's glibc is below 2.39
  done: false
- title: libreadline8t64 and openjdk-21-jre-headless replace their bookworm predecessors; bbin still resolves tools.deps dependencies
  done: false
- title: 'Every existing in-build probe still passes: SQLite compile-option checks, node, bb, gosu, cue, uv'
  done: false
- title: 'aishell info --foundation reports ''Distro: debian:trixie-slim'' scraped from the Dockerfile, not hardcoded'
  done: false
- title: ADR 0005 records the glibc-floor policy; ADR 0004 is amended to note its glibc premise expired
  done: false
- title: 'CONTEXT.md defines ''distro image'' with Avoid: base image'
  done: false
- title: Unreleased changelog entry warns about libreadline8 / openjdk-17 as the shape of downstream extension breakage
  done: false
- title: No 'bookworm' strings remain outside .planning/
  done: false
- title: clj-kondo lint is clean
  done: false
tags:
- ready-for-human
links:
- aix-01m0k3ek8jv3
---

## Description

The foundation image's distro image is `debian:bookworm-slim`, which ships **glibc 2.36**. Prebuilt binaries increasingly target newer glibc, and this has bitten us twice. Move to **`debian:trixie-slim`** (Debian 13, **glibc 2.41**) and declare a floor of **>= 2.39** that the build enforces on every rebuild.

## Vocabulary

"Base image" is already taken: `CONTEXT.md` uses it for `aishell:base`, the per-user layer. The upstream OS image the foundation is built FROM is the **distro image**. This ticket adds that term to the glossary (_Avoid_: base image).

## What changes

All three Dockerfile stages move to trixie: the main stage, the node-source stage (`node:24-trixie-slim`), and the throwaway SQLite builder stage.

Two apt packages do not exist in trixie and must be replaced:

- `libreadline8` -> **`libreadline8t64`** (time_t transition)
- `openjdk-17-jre-headless` -> **`openjdk-21-jre-headless`** (17 is absent from trixie entirely). Explicit pin, not `default-jre-headless`: the version shows up in `aishell info --foundation`, and a silent major-version shift should be visible in a diff. The only consumer is bbin, which wants *a* JRE.

Add a build-time assertion that the final image's glibc is **>= 2.39**. Assert the policy number, not trixie's 2.41 — the assertion encodes the promise; 2.41 is headroom, not the contract.

Stop hardcoding the distro in `aishell info`: derive it from the final `FROM` line the way Babashka, CUE, uv and SQLite versions are already derived, and loosen the node-version regex so it is not anchored to a suite name. Relabel the report line `Base:` -> **`Distro:`**, which also resolves the collision with `aishell:base`. This is prefactoring — it makes the next distro bump a one-line change instead of a seven-file one.

## Deliberate non-changes

- **SQLite stays compiled from source.** ADR 0004's headline justification expires here: it says upstream's prebuilt tools need GLIBC_2.38 against bookworm's 2.36, and at 2.41 those prebuilts would run. Its other reasons survive intact — no prebuilt tools for arm64, no prebuilt shared library at all (so no shadowing of `libsqlite3.so.0`, which is the whole point), and the compile-flag parity floor. Trixie's own sqlite is 3.46.1, still well behind the pinned 3.53.4. **ADR 0004 must be amended** to say the glibc premise expired while the decision stands on its remaining legs — an ADR whose stated reason silently stops being true is worse than one that says so.
- **Tool pins stay frozen.** Babashka, CUE, uv and Gitleaks are deliberately out of scope so a build failure has an unambiguous cause. Separate ticket.
- **`ncurses-term` stays out.** See the linked pin-refresh ticket for why it would not have helped anyway.

## Record the decision

**ADR 0005** records the *policy*, which is the part a future reader needs: the distro image carries a glibc floor as a first-class property, and when a tool outruns the floor we bump the distro rather than vendoring the tool per-case. That policy is what makes the ADR 0004 amendment read as a coherent evolution rather than a reversal.

The changelog entry belongs in `[Unreleased]` and should be written as a warning, not a footnote: a user's own `~/.aishell/Dockerfile` or project extension may pin bookworm-only package names, and `libreadline8`/`openjdk-17-jre-headless` are the shape that breakage takes. Version lands as a **minor** bump (3.24.0) — aishell's own CLI surface is untouched, so SemVer says minor, and the honest handling of downstream breakage is a loud changelog entry.

Remaining `bookworm` strings live in README, `docs/ARCHITECTURE.md` (four spots), and a comment in this repo's own `.aishell/Dockerfile` that claims the foundation ships JRE 17. `.planning/` is historical and stays untouched.

## Verified facts (do not re-derive)

Checked against the Debian package API and Docker Hub, not against a build:

- bookworm glibc 2.36; **trixie glibc 2.41**; ubuntu:24.04 would be 2.39 exactly — rejected for having no headroom. `bookworm-backports` does not ship a newer glibc, so it was never an option.
- `node:24-trixie-slim` exists.
- Every other package in the install block exists in trixie under its current name.
- sqlite3 in trixie is 3.46.1 (bookworm: 3.40.1). ncurses in trixie is 6.5+20250216 (bookworm: 6.4).

## Why hitl

Every trixie claim above comes from package metadata, not from an observed build. The build must run somewhere with a container runtime, and the results must be checked by someone who can see them.

## Rebuild cascade

Already handled, nothing to design: the foundation rebuilds off its embedded content hash, and `aishell:base` tracks the foundation image id via a label, so per-user bases and project extensions invalidate automatically. Users get one rebuild on their next `setup`/`update`, with the usual stale warning until then.

## Watch for

`aishell info`'s package scraper anchors on the *first* multi-line `apt-get install` block. Renaming packages inside that block is safe, but any change to its shape silently changes what `aishell info --foundation` reports.
