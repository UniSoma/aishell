---
id: aix-01m5k46ypnn6
title: Move the foundation age check into docker.build and call it out of date
status: in_progress
type: chore
priority: 2
mode: afk
created: '2026-10-23T00:57:40.821756521Z'
updated: '2026-10-23T00:57:55.932037141Z'
tags:
- ready-for-agent
acceptance:
- title: The age check lives in aishell.docker.build behind one function taking an image tag; run.clj and vscode.clj call it once each
  done: false
- title: No name, docstring, test or doc uses stale for the age concept; check-dockerfile-stale is untouched
  done: false
- title: CONTEXT.md carries the Foundation build date entry
  done: false
- title: bb test passes and clj-kondo --lint src test is clean
  done: false
---

## Description

Follow-up to aix-01m5jqhzdcd6 (commits 38dbf51, f90ff5f, 764a258), decided by Jonas on 2026-10-23.

1. Move the foundation age check from `warn-if-foundation-stale!` in src/aishell/run.clj into aishell.docker.build, behind one function that takes an image tag (reads the label, applies the clock and threshold, prints the warning). run.clj and vscode.clj each make one call. The label writer, threshold and reader then live in one module.
2. "Stale" already means a Dockerfile content-hash mismatch (`check-dockerfile-stale`, check.clj). Rename the age concept to "out of date": `warn-if-foundation-stale!` and `stale-after-days` and related names, docstrings, tests, and the docs/CONFIGURATION.md sentence. Leave check-dockerfile-stale alone.
3. Add to CONTEXT.md (approved wording, with "out of date" in place of "stale"):
   **Foundation build date**: When the Foundation image's OS packages were last fetched fresh: the time of its first or forced build, carried unchanged through cached rebuilds. A Foundation image whose build date is past the age limit, or unknown, is *out of date*.
   _Avoid_: build time, last build, image age, stale

Behaviour must not change: same label, same 30-day whole-day rule, same call sites, same message.
