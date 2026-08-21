---
id: aix-01m0dpf8n0gf
title: Launch path and shell aliases derive from the registry
status: closed
type: task
priority: 1
mode: afk
created: '2026-08-19T19:02:51.804733824Z'
updated: '2026-08-21T15:01:27.537692431Z'
closed: '2026-08-21T15:01:27.537692431Z'
parent: aix-01m0dp97mdhs
tags:
- registry
- ready-for-agent
acceptance:
- title: Container launch argv and in-sandbox alias argv come from the same interpreter, byte-identical
  done: true
- title: Skip-permissions and Codex flags spelled exactly once (in the registry)
  done: true
- title: Gitleaks launch quirks (no alias, skip pre-start, non-interactive) read from capability fields
  done: true
- title: harness_args defaults applied identically on both paths
  done: true
- title: clj-kondo clean; tests green
  done: true
deps:
- aix-01m0dpf8h3qk
---

## Description

First migrate batch of the parent spec. The run path and the shell-alias builder stop hand-assembling per-harness commands and both call the registry's argv interpreter, so the two paths cannot diverge — including the AISHELL_SKIP_PERMISSIONS override and per-harness harness_args defaults. Gitleaks's launch shape (skip pre-start, non-interactive shell mode) comes from its descriptor's capability fields; its post-run scan-timestamp write stays feature code in the run flow.

Demoable: launching a harness via its subcommand and via its in-sandbox alias produces the same command; flipping AISHELL_SKIP_PERMISSIONS affects both identically.

## Notes

**2026-08-21T15:01:27.537692431Z**

Run path and alias builder both call harness/launch-argv; skip-permissions and Codex flags now spelled once, in the registry (verified by grep: only harness.clj). Gitleaks quirks read from :alias/:pre-start?/:interactive?. Entrypoint alias loop made prefix-scanning, which forces a one-time foundation rebuild. 5 new tests incl. byte-identical argv across both paths. Commit 583eb08.
