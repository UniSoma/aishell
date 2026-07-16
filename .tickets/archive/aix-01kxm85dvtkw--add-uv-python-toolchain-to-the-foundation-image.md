---
id: aix-01kxm85dvtkw
title: Add uv (Python toolchain) to the foundation image
status: closed
type: task
priority: 2
mode: afk
created: '2026-07-16T01:20:24.954737188Z'
updated: '2026-07-16T01:25:23.270534066Z'
closed: '2026-07-16T01:25:23.270534066Z'
tags:
- design
external_refs:
- docs/adr/0002-uv-in-foundation-image.md
acceptance:
- title: uv and uvx binaries present in foundation image, verified via uv --version
  done: true
- title: 'Install block follows house style: pinned ARG UV_VERSION, multi-arch case, curl GitHub release tarball, verify'
  done: true
- title: foundation-contents comment in build.clj updated to mention uv
  done: true
- title: 'Docs updated: README tool list + CONFIGURATION.md note on mounts: cache-persistence opt-in'
  done: true
---

## Description

Add `uv` (and `uvx`) to the aishell foundation image so Python tooling is available in every sandbox.

Decisions were settled in a grilling session and recorded in ADR 0002 (docs/adr/0002-uv-in-foundation-image.md):

- **Placement:** unconditional in the foundation image (`base-dockerfile` in `src/aishell/docker/templates.clj`), alongside node/jq/cue. Not ARG-gated, not a per-project overlay.
- **Scope:** `uv` + `uvx` binaries only. No baked Python interpreter, no system python3. Projects pin their own interpreter and uv fetches on demand.
- **Install:** house style — pinned `ARG UV_VERSION` (fetch current stable at implementation time, don't guess), `dpkg`-arch -> case (`x86_64-unknown-linux-gnu` / `aarch64-unknown-linux-gnu`), curl the GitHub release tarball into `/usr/local/bin`, `uv --version` verify. Place with the other single-binary tool installs, before the `developer` user creation.
- **Cache:** leave uv on its HOME defaults. Given `--rm` + ephemeral HOME the interpreter re-downloads once per session. Cross-session persistence is a per-project `mounts:` opt-in, documented not built in. Do NOT redirect uv dirs to /usr/local/share.

Rebuild is automatic via the `foundation-content` hash — no test changes required.

## Design

Touch points:
- `src/aishell/docker/templates.clj` — add the uv install block to `base-dockerfile`.
- `src/aishell/docker/build.clj:101` — update the "contains only ... gitleaks" comment to include uv.
- README — foundation tool list.
- `docs/CONFIGURATION.md` — note the `mounts:` one-liner for persisting `~/.cache/uv` across sessions.

Explicitly rejected: ARG-gating, per-project overlay, baked Python, shared /usr/local/share/uv redirection, unpinned "latest".

## Notes

**2026-07-16T01:25:23.270534066Z**

Added uv/uvx (v0.11.29) to the foundation image in base-dockerfile — house-style pinned ARG + dpkg-arch case + curl GitHub tarball + uv/uvx --version verify, placed after CUE and before the developer user. Updated build.clj foundation-contents docstring, README tool list, and CONFIGURATION.md with a ~/.cache/uv mounts: opt-in note. All 75 tests pass; code-review (standards + spec) clean.
