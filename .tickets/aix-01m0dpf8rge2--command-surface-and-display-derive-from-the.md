---
id: aix-01m0dpf8rge2
title: Command surface and display derive from the registry
status: open
type: task
priority: 1
mode: afk
created: '2026-08-19T19:02:51.916753883Z'
updated: '2026-08-19T19:02:51.916753883Z'
parent: aix-01m0dp97mdhs
tags:
- registry
- ready-for-agent
acceptance:
- title: Dispatch and pass-through subcommand sets derive from the registry
  done: false
- title: Typo suggestions cover every subcommand (pi and a now suggested); suggestion tests extended
  done: false
- title: Setup flags and help ordering derive from the registry
  done: false
- title: One canonical label per harness across setup, update, info, check, and help
  done: false
- title: clj-kondo clean; tests green
  done: false
deps:
- aix-01m0dpf8h3qk
---

## Description

Second migrate batch of the parent spec. Every command-surface enumeration — known subcommands, pass-through harness subcommands, typo-suggestion vocabulary, setup flag definitions and help ordering, and the harness labels printed by setup/update/info/check — becomes a derivation from the registry.

Ships two user-visible drift fixes, noted in the changelog: typo suggestions now know pi and a, and labels unify to the canonical long form ("Codex CLI", "Pi coding agent", ...), accepting the small output diffs.

Demoable from the CLI: a typo near pi suggests pi; info/check/setup print consistent labels.
