---
id: aix-01kxhsptfgnh
title: First-run bootstrapping and credential promotion for isolated sandboxes
status: open
type: task
priority: 2
mode: afk
created: '2026-07-15T02:29:17.422412413Z'
updated: '2026-07-15T02:29:17.422412413Z'
tags:
- isolation
- ready-for-agent
acceptance:
- title: missing allowlist dirs and files pre-created on host before mounting; settings.json seeds as {}, CLAUDE.md as empty; a missing file source never becomes a docker-created directory
  done: false
- title: .credentials.json never seeded; mounted only when present on host; visible notice when starting without it
  done: false
- title: 'promotion: host file absent + project state dir has credentials → copied to host before mounting; idempotent on subsequent starts'
  done: false
- title: covered by tests simulating a fresh HOME for all three paths (present / absent / promote)
  done: false
- title: clj-kondo lint clean
  done: false
deps:
- aix-01kxhspd3cdj
---

## Description

Make `claude_isolation: project` work on a machine where Claude Code has never run (see ADR 0001, First-run bootstrapping).

Before mounting, pre-create any missing share-allowlist source on the host so Docker never manufactures a directory where a file belongs: directories via ensure-dir, `settings.json` seeded with `{}`, `CLAUDE.md` seeded empty (extends the existing seeding pattern used for `~/.claude.json`).

`.credentials.json` is special-cased: never seed it. Mount it only when it exists on the host. When absent, print a notice and start without the mount — Claude's first login inside the sandbox then writes credentials into the per-project state dir, which persists across container restarts. On every sandbox start in project mode, if the host file is still absent but the project state dir has one, promote it: copy it up to the host, then mount it. First login anywhere becomes the shared credential and a project-local copy can never be silently shadowed by a later host file.
