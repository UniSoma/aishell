---
id: aix-01m0dpf8vsya
title: Install, mounts, env passthrough, and volume hash derive from the registry
status: open
type: task
priority: 1
mode: afk
created: '2026-08-19T19:02:52.021230721Z'
updated: '2026-08-19T19:02:52.021230721Z'
parent: aix-01m0dp97mdhs
tags:
- registry
- ready-for-agent
acceptance:
- title: Install commands (npm and binary tarball) derive from descriptors
  done: false
- title: Config-dir/file mounts and API-key env passthrough derive from descriptors
  done: false
- title: 'Hash-equivalence test: registry-derived hash input is byte-identical to today''s for every enabled-harness combination'
  done: false
- title: harness_args for a non-launchable harness warns instead of silently dropping
  done: false
- title: clj-kondo clean; tests green
  done: false
deps:
- aix-01m0dpf8h3qk
---

## Description

Third migrate batch of the parent spec. Volume population (npm packages, the OpenCode binary tarball, version pinning), sandbox config-path mounts, API-key env passthrough, and the volume-hash input all become registry derivations.

Hash compatibility is the hard constraint: the hash derivation sorts harness keys internally so registry (display) order never affects it, and a hash-equivalence test pins that no user's volume rebuilds from this refactor. Also fixes the silent drop of harness_args for a harness without a launch (gitleaks) — it warns instead; changelog entry.
