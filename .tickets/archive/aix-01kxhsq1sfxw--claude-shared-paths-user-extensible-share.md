---
id: aix-01kxhsq1sfxw
title: 'claude_shared_paths: user-extensible share allowlist'
status: closed
type: task
priority: 2
mode: afk
created: '2026-07-15T02:29:24.909303665Z'
updated: '2026-08-22T19:44:47.699536463Z'
closed: '2026-08-22T19:44:47.699536463Z'
tags:
- isolation
- ready-for-agent
acceptance:
- title: claude_shared_paths parsed and validated; additive global+project merge; entries relative to ~/.claude
  done: true
- title: collision guard rejects machine-state paths with a clear error naming the offending entry
  done: true
- title: absolute paths and paths escaping ~/.claude via .. are rejected
  done: true
- title: entries mount like built-in allowlist entries, including missing-source pre-creation
  done: true
- title: 'verified: a root-level script listed in claude_shared_paths is callable from settings hooks inside an isolated sandbox'
  done: true
- title: clj-kondo lint clean
  done: true
deps:
- aix-01kxhspd3cdj
---

## Description

Users keep personal files in ~/.claude and reference them from settings and hooks (e.g. a statusline script, a usage-fetch script). Under `claude_isolation: project` those break silently because only the built-in allowlist is mounted.

Add a `claude_shared_paths` config key: a list of paths relative to `~/.claude`, merged additively across global and project config (same style as mounts/env), each mounted into the isolated sandbox exactly like a built-in allowlist entry (dirs as dir mounts, files as single-file mounts, missing sources handled per the bootstrapping conventions).

Guard rails: reject entries that resolve to Claude machine-state paths (daemon lock/registry/log, jobs, tasks, sessions, session-env, shell-snapshots, file-history) so config can't re-break the supervisor isolation; reject absolute paths and `..` escapes; entries already covered by the built-in allowlist are a no-op with a warning.

## Notes

**2026-08-22T19:44:47.699536463Z**

Landed in cd57ecf. claude_shared_paths is a known config key with additive global+project merge; user-share-entries routes entries through the same mount + pre-creation pipeline as the built-in allowlist; validation/check-claude-shared-paths hard-rejects absolute paths, '..' escapes, and machine-state collisions by name; built-in duplicates warn and no-op. Hook-callable shared script confirmed in a month of real use.
