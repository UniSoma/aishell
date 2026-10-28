---
id: aix-01m61k2j7rx3
title: Make the update help text and state schema docs match the code
status: closed
type: chore
priority: 2
mode: afk
created: '2026-10-28T15:46:47.665033327Z'
updated: '2026-10-28T16:07:47.100287160Z'
closed: '2026-10-28T16:07:47.100287160Z'
tags:
- ready-for-agent
acceptance:
- title: aishell update --help describes when the Foundation image is rebuilt correctly
  done: true
- title: write-state docstring and ARCHITECTURE.md state schema match which hash key is read
  done: true
links:
- aix-01m61hs429e6
---

## Description

Found 2026-10-28 while landing aix-01m61hs429e6 (03fa07a).

1. `aishell update --help` says "Foundation image is NOT rebuilt unless --force is used", but update rebuilds it whenever it is missing or stale. Fix the copy (CHANGELOG line under Fixed).
2. The `state/write-state` docstring and the docs/ARCHITECTURE.md state schema call `:dockerfile-hash` deprecated in favour of `:foundation-hash`, but `check` and `run` read `:dockerfile-hash` and nothing reads `:foundation-hash`; the schema also omits `:dockerfile-hash`. Make the docs describe the code as it is. Don't move readers between keys here; old state files must stay readable.

## Notes

**2026-10-28T16:07:47.100287160Z**

aishell update --help now says update rebuilds a missing or stale Foundation image and that --force force-rebuilds Foundation and any custom Base image (CHANGELOG Fixed entry). The write-state docstring, ARCHITECTURE.md, DEVELOPMENT.md and CONFIGURATION.md state that check and run read :dockerfile-hash and nothing reads :foundation-hash; no reader moved.
