---
id: aix-01m0dpf8rge2
title: Command surface and display derive from the registry
status: closed
type: task
priority: 1
mode: afk
created: '2026-08-19T19:02:51.916753883Z'
updated: '2026-08-21T15:23:44.590805900Z'
closed: '2026-08-21T15:23:44.590805900Z'
parent: aix-01m0dp97mdhs
tags:
- registry
- ready-for-agent
acceptance:
- title: Dispatch and pass-through subcommand sets derive from the registry
  done: true
- title: Typo suggestions cover every subcommand (pi and a now suggested); suggestion tests extended
  done: true
- title: Setup flags and help ordering derive from the registry
  done: true
- title: One canonical label per harness across setup, update, info, check, and help
  done: true
- title: clj-kondo clean; tests green
  done: true
deps:
- aix-01m0dpf8h3qk
---

## Description

Second migrate batch of the parent spec. Every command-surface enumeration — known subcommands, pass-through harness subcommands, typo-suggestion vocabulary, setup flag definitions and help ordering, and the harness labels printed by setup/update/info/check — becomes a derivation from the registry.

Ships two user-visible drift fixes, noted in the changelog: typo suggestions now know pi and a, and labels unify to the canonical long form ("Codex CLI", "Pi coding agent", ...), accepting the small output diffs.

Demoable from the CLI: a typo near pi suggests pi; info/check/setup print consistent labels.

## Notes

**2026-08-21T15:23:44.590805900Z**

Command surface, setup flags, help ordering and all harness labels derive from the registry. Suggestion vocabulary now covers every subcommand with an alphabetical tie-break; suggestion tests created (none existed). Labels unified to canonical long forms. Commit c5498e2.
