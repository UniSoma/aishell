---
id: aix-01kxhspd3cdj
title: 'claude_isolation: project — isolated Claude machine state per sandbox'
status: open
type: task
priority: 2
mode: afk
created: '2026-07-15T02:29:03.723862280Z'
updated: '2026-07-15T02:29:03.723862280Z'
tags:
- isolation
- ready-for-agent
external_refs:
- docs/adr/0001-per-project-claude-machine-state-isolation.md
acceptance:
- title: claude_isolation validated (shared|project); invalid value fails with a clear message; default shared; project-level config overrides global
  done: false
- title: project mode creates {state-dir}/claude/{project-hash}/dot-claude/ and meta.edn (project path, created-at) on first run
  done: false
- title: container ~/.claude is the per-project dot-claude with the built-in share allowlist mounted on top; files mounted only when present on host
  done: false
- title: 'projects/ and history.jsonl are shared: sessions started before the flip are resumable inside the isolated sandbox'
  done: false
- title: shared mode (default) produces the same docker args as today; ~/.claude.json handling unchanged in both modes
  done: false
- title: Windows container-home destination mapping preserved for all new mounts
  done: false
- title: 'verified manually: two projects in project mode run independent Agent View supervisors'
  done: false
- title: clj-kondo lint clean
  done: false
---

## Description

Tracer bullet for per-project Claude machine-state isolation (see ADR 0001 and CONTEXT.md for the vocabulary).

Add a `claude_isolation` config key (values `shared` | `project`, default `shared`, global→project merge like existing keys). In `project` mode, stop mounting the host `~/.claude` wholesale. Instead: create a per-project state dir keyed by project hash under aishell's XDG state dir, containing a `dot-claude/` directory and a `meta.edn` (project path, created-at), and mount `dot-claude/` at the container's `~/.claude`. On top of it, mount the built-in share allowlist from host `~/.claude`: config dirs (skills, agents, commands, hooks, plugins), config files (CLAUDE.md, settings.json, .credentials.json) as single-file mounts when present on host, and Claude project data (projects/, history.jsonl) which is always shared. The `~/.claude.json` mount is unchanged. `shared` mode must behave byte-identically to today.

Result: each sandbox runs its own Claude Agent View supervisor, scoped to that project, while skills/settings/auth/transcripts stay shared.

Missing-host-file handling beyond "skip when absent" is deliberately out of scope (follow-up ticket): this slice may assume a host that has run Claude Code before.
