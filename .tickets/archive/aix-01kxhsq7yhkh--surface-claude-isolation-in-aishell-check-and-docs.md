---
id: aix-01kxhsq7yhkh
title: Surface claude isolation in aishell check and docs
status: closed
type: task
priority: 2
mode: afk
created: '2026-07-15T02:29:31.215041511Z'
updated: '2026-08-22T19:44:47.787933373Z'
closed: '2026-08-22T19:44:47.787933373Z'
tags:
- isolation
- ready-for-agent
acceptance:
- title: aishell check shows effective isolation mode; in project mode also state dir path/existence and credentials source (host-mounted vs project-local)
  done: true
- title: CONFIGURATION.md documents claude_isolation and claude_shared_paths with examples
  done: true
- title: TROUBLESHOOTING.md covers EBUSY-on-rename, cross-sandbox transcript visibility, per-sandbox Agent View, container-bound background sessions
  done: true
- title: clj-kondo lint clean
  done: true
deps:
- aix-01kxhspd3cdj
---

## Description

Make the feature discoverable and debuggable without reading ADR 0001.

`aishell check`: report the effective claude isolation mode and, in project mode, the per-project state dir path and whether it exists, plus whether host credentials are mounted or the sandbox is running on a project-local login.

Docs: CONFIGURATION.md documents `claude_isolation` and `claude_shared_paths` (values, merge semantics, examples). TROUBLESHOOTING.md records the caveats: single-file mounts can fail atomic-rename writes with EBUSY (symptom + the symlink upgrade path), transcripts remain readable across sandboxes, Agent View is per-sandbox by design (no unified view), and background sessions stop when their container stops (state survives, daemon reconnects on next start).

May need small touch-ups after the bootstrapping and shared-paths tickets land; write against whatever has merged.

## Notes

**2026-08-22T19:44:47.787933373Z**

Landed in cd57ecf. check-claude-isolation reports effective mode plus, in project mode, state-dir path/existence and credentials source (host-mounted / project-local / none yet). CONFIGURATION.md documents both keys with examples; TROUBLESHOOTING.md covers EBUSY-on-rename, cross-sandbox transcript visibility, per-sandbox Agent View, and container-bound background sessions.
