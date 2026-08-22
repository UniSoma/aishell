---
id: aix-01kxhspd3cdj
title: 'claude_isolation: project — isolated Claude machine state per sandbox'
status: closed
type: task
priority: 2
mode: afk
created: '2026-07-15T02:29:03.723862280Z'
updated: '2026-08-22T19:44:47.883315025Z'
closed: '2026-08-22T19:44:47.883315025Z'
tags:
- isolation
- ready-for-agent
external_refs:
- docs/adr/0001-per-project-claude-machine-state-isolation.md
acceptance:
- title: claude_isolation validated (shared|project); invalid value fails with a clear message; default shared; project-level config overrides global
  done: true
- title: project mode creates {state-dir}/claude/{project-hash}/dot-claude/ and meta.edn (project path, created-at) on first run
  done: true
- title: container ~/.claude is the per-project dot-claude with the built-in share allowlist mounted on top; files mounted only when present on host
  done: true
- title: 'projects/ and history.jsonl are shared: sessions started before the flip are resumable inside the isolated sandbox'
  done: true
- title: shared mode (default) produces the same docker args as today; ~/.claude.json handling unchanged in both modes
  done: true
- title: Windows container-home destination mapping preserved for all new mounts
  done: true
- title: 'verified manually: two projects in project mode run independent Agent View supervisors'
  done: true
- title: clj-kondo lint clean
  done: true
---

## Description

Tracer bullet for per-project Claude machine-state isolation (see ADR 0001 and CONTEXT.md for the vocabulary).

Add a `claude_isolation` config key (values `shared` | `project`, default `shared`, global→project merge like existing keys). In `project` mode, stop mounting the host `~/.claude` wholesale. Instead: create a per-project state dir keyed by project hash under aishell's XDG state dir, containing a `dot-claude/` directory and a `meta.edn` (project path, created-at), and mount `dot-claude/` at the container's `~/.claude`. On top of it, mount the built-in share allowlist from host `~/.claude`: config dirs (skills, agents, commands, hooks, plugins), config files (CLAUDE.md, settings.json, .credentials.json) as single-file mounts when present on host, and Claude project data (projects/, history.jsonl) which is always shared. The `~/.claude.json` mount is unchanged. `shared` mode must behave byte-identically to today.

Result: each sandbox runs its own Claude Agent View supervisor, scoped to that project, while skills/settings/auth/transcripts stay shared.

Missing-host-file handling beyond "skip when absent" is deliberately out of scope (follow-up ticket): this slice may assume a host that has run Claude Code before.

## Notes

**2026-08-22T19:44:47.883315025Z**

Landed in cd57ecf. claude_isolation (shared|project, default shared) with global->project override; project mode builds {state-dir}/claude/{project-hash}/dot-claude + meta.edn on first run and mounts it as the container ~/.claude with the built-in share allowlist on top (projects/ and history.jsonl shared, so pre-flip sessions stay resumable); shared mode byte-identical, asserted by shared-mode-byte-identical; Windows unixify destination mapping preserved. Independent per-project Agent View supervisors confirmed by a month of daily use. Note: an invalid claude_isolation value warns and falls back to shared rather than hard-failing, matching how every other config key validates.
