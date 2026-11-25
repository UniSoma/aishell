---
id: aix-01m8a59rabm0
title: Drop harness capability flags no harness turns off
status: open
type: chore
priority: 3
mode: hitl
created: '2026-11-25T20:10:36.740335675Z'
updated: '2026-11-25T20:10:36.740335675Z'
tags:
- needs-triage
- codebase-design
links:
- aix-01m8a3nye1d7
---

## Description

Every harness in the registry now sets `:interactive?`, `:pre-start?` and `:accepts-config-defaults?` to true. Gitleaks, removed in aix-01m8a3nye1d7, was the only descriptor that set them false. The code that handles the false case therefore never runs for a real harness, and only tests reach it by faking a descriptor:

- the defaultless branch of the harness argv interpreter (`launch-argv`)
- the "ignored harness_args" warning in the run path and the `defaultless-harnesses` set in config validation
- the skip-interactive and skip-pre-start launch flags as driven by a harness descriptor (`exec` still sets both itself)

`:volume-participant?` and `:version-key` are in the same position: every harness participates in the volume and carries a version.

### Direction

Drop the capability booleans no harness turns off, with the branches that read them and the tests that fake a descriptor to reach them. Update the descriptor schema comment, DEVELOPMENT.md's "adding a harness" guide (it lists the booleans as required descriptor fields) and the harness contract tests. A future harness that needs one of these behaviours brings the capability back with its first real user.

Keep the `exec` path's own skip-pre-start / skip-interactive behaviour: it does not come from a descriptor.
