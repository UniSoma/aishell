---
id: aix-01kxhsq1sfxw
title: 'claude_shared_paths: user-extensible share allowlist'
status: open
type: task
priority: 2
mode: afk
created: '2026-07-15T02:29:24.909303665Z'
updated: '2026-07-15T02:29:24.909303665Z'
tags:
- isolation
- ready-for-agent
acceptance:
- title: claude_shared_paths parsed and validated; additive global+project merge; entries relative to ~/.claude
  done: false
- title: collision guard rejects machine-state paths with a clear error naming the offending entry
  done: false
- title: absolute paths and paths escaping ~/.claude via .. are rejected
  done: false
- title: entries mount like built-in allowlist entries, including missing-source pre-creation
  done: false
- title: 'verified: a root-level script listed in claude_shared_paths is callable from settings hooks inside an isolated sandbox'
  done: false
- title: clj-kondo lint clean
  done: false
deps:
- aix-01kxhspd3cdj
---

## Description

Users keep personal files in ~/.claude and reference them from settings and hooks (e.g. a statusline script, a usage-fetch script). Under `claude_isolation: project` those break silently because only the built-in allowlist is mounted.

Add a `claude_shared_paths` config key: a list of paths relative to `~/.claude`, merged additively across global and project config (same style as mounts/env), each mounted into the isolated sandbox exactly like a built-in allowlist entry (dirs as dir mounts, files as single-file mounts, missing sources handled per the bootstrapping conventions).

Guard rails: reject entries that resolve to Claude machine-state paths (daemon lock/registry/log, jobs, tasks, sessions, session-env, shell-snapshots, file-history) so config can't re-break the supervisor isolation; reject absolute paths and `..` escapes; entries already covered by the built-in allowlist are a no-op with a warning.
