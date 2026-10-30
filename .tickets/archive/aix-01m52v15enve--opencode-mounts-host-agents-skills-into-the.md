---
id: aix-01m52v15enve
title: OpenCode mounts host ~/.agents/skills into the sandbox
status: closed
type: feature
priority: 2
mode: afk
created: '2026-10-16T17:09:23.029372246Z'
updated: '2026-10-16T17:21:43.501990810Z'
closed: '2026-10-16T17:21:43.501990810Z'
tags:
- ready-for-agent
external_refs:
- git:3a8913b98be1ecf98a26b869734e5dea4eea9af6
---

## Problem Statement

I keep my agent skills in `~/.agents/skills` on the host. When I run OpenCode through aishell, the sandbox cannot see that folder, so OpenCode starts without my skills and any skill I install from inside the sandbox is lost when the container is removed.

## Solution

When the OpenCode Harness is enabled, aishell mounts the host's `~/.agents/skills` into the Sandbox at the same path, read-write, like the other OpenCode config paths. If the folder does not exist on the host, aishell creates it first, as it already does for `~/.config/opencode`. Only OpenCode gets this mount; every other Harness is unchanged.

## User Stories

1. As an OpenCode user, I want `~/.agents/skills` available inside the Sandbox, so that OpenCode finds my skills without manual setup.
2. As an OpenCode user, I want the mount at the same path as on the host, so that paths in my skills resolve identically inside and outside the Sandbox.
3. As an OpenCode user, I want the mount to be read-write, so that skills installed from inside the Sandbox persist on the host.
4. As a first-time OpenCode user without `~/.agents/skills`, I want aishell to create it, so that the mount works on the first run and skills installed in the Sandbox are not lost.
5. As a user of Codex, Claude Code, Copilot, Gemini or pi only, I want nothing about `~/.agents` mounted, so that my Sandbox is unchanged.
6. As a user without OpenCode enabled, I want no `~/.agents/skills` mount and no folder created on my host, so that my home directory is not touched needlessly.
7. As a user, I want only `~/.agents/skills` mounted and not all of `~/.agents`, so that unrelated tool data stays out of the Sandbox.
8. As a user running `aishell info`, I want `~/.agents/skills` listed under OpenCode's config paths, so that I can see what is mounted.
9. As a Windows user, I want the folder mounted under the container home like the other config paths, so that the feature works on my platform.
10. As an aishell maintainer, I want the mount declared in the OpenCode Harness descriptor, so that the Harness registry stays the single source of truth.

## Implementation Decisions

- Add `~/.agents/skills` (home-relative `.agents/skills`, a directory) to the OpenCode Harness descriptor's config paths. No new mechanism and no new descriptor field.
- Existing behaviour is reused as is: the host folder is created when missing, the mount is read-write, the destination equals the host path on Unix and is mapped under the container home on Windows, and the mount applies only when the OpenCode state flag is enabled.
- `aishell info` is derived from the same config-path map, so it lists the new path with no extra change.
- No other Harness descriptor changes. No ADR and no new glossary term: this is one more config path of an existing Harness.
- Update the Harness docs and the changelog.

## Testing Decisions

- Good tests assert external behaviour (which mounts are produced for a given state and home), not implementation details.
- Seam 1: the Harness config-dirs map, derived from the registry. Extend the existing table test so the OpenCode entry lists `.config/opencode`, `.local/share/opencode` and `.agents/skills`, in that order, with other Harnesses unchanged.
- Seam 2: the Harness config mounts builder, driven through the existing test helper in the run tests. Cover: OpenCode enabled with the folder present yields a `-v` for `<home>/.agents/skills` at the same path; OpenCode enabled with the folder absent creates it and mounts it; OpenCode not enabled yields no such mount; another Harness alone (for example Codex) yields no such mount.
- Prior art: the existing run tests for config mounts and the config-dirs table test.
- Run `clj-kondo --lint src test` before committing.

## Out of Scope

- Mounting `~/.agents/skills` for any Harness other than OpenCode.
- Mounting the whole `~/.agents` folder.
- Read-only mounts or an opt-out setting.
- Mounting only when the folder already exists.
- Project-level skills folders.
- Any change to Claude skills handling.

## Further Notes

- Language: Harness, Harness descriptor, Sandbox as defined in `CONTEXT.md`.

## Notes

**2026-10-16T17:21:43.501990810Z**

OpenCode now mounts the host ~/.agents/skills read-write at the same path, creating it on the host when missing; no other Harness changed. Tests, docs and changelog updated.
