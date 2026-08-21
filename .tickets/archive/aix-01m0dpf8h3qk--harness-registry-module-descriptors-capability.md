---
id: aix-01m0dpf8h3qk
title: 'Harness registry module: descriptors, capability filters, argv interpreter (expand)'
status: closed
type: task
priority: 1
mode: afk
created: '2026-08-19T19:02:51.660193151Z'
updated: '2026-08-21T14:48:54.697039509Z'
closed: '2026-08-21T14:48:54.697039509Z'
parent: aix-01m0dp97mdhs
tags:
- registry
- ready-for-agent
- codebase-design
acceptance:
- title: Registry holds six pure-data descriptors (claude, opencode, codex, gemini, pi, gitleaks)
  done: true
- title: Capability-filter derivations for subcommand set, volume participants, suggestion contributions, alias emitters
  done: true
- title: 'One argv interpreter: descriptor + runtime inputs (skip-permissions, harness_args) to final argv, incl gitleaks non-interactive shape'
  done: true
- title: 'Table-driven completeness test: every descriptor carries the fields its capabilities require'
  done: true
- title: Canonical long labels in descriptors; nothing consumes the registry yet; clj-kondo clean
  done: true
deps:
- aix-01m0dpf8cvrx
---

## Description

The expand step of the parent spec. Build the harness registry module (aishell.harness): an ordered, closed vector of harness descriptors — pure data only (identity, canonical label, state/version keys, capabilities as presence-based fields plus explicit flags where absence is meaningful, install kind, config paths, passthrough env vars). No functions in rows.

Include the single argv interpreter that both the launch path and the shell-alias builder will call in later tickets — this is where Claude's skip-permissions rule (and the AISHELL_SKIP_PERMISSIONS override) and Codex's update-check flag get their one spelling.

Demoable through the test suite: completeness, capability filters, and interpreter coverage across all harnesses and runtime-input combinations. Existing call sites are untouched; migration happens in the three sibling tickets.

## Notes

**2026-08-21T14:48:54.697039509Z**

Added src/aishell/harness.clj: six pure-data descriptors, capability filters (subcommands, volume participants, suggestion terms, alias emitters), and the single launch-argv interpreter. 19 new tests incl. table-driven completeness with a negative control. Registry-derived hash input verified byte-identical to volume.clj across all 64 combinations, so vsya is unblocked. Commit 708a201.
