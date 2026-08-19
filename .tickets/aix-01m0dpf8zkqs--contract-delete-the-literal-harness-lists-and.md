---
id: aix-01m0dpf8zkqs
title: 'Contract: delete the literal harness lists and close out the registry migration'
status: open
type: task
priority: 1
mode: afk
created: '2026-08-19T19:02:52.136622156Z'
updated: '2026-08-19T19:02:52.136622156Z'
parent: aix-01m0dp97mdhs
tags:
- registry
- ready-for-agent
acceptance:
- title: No per-harness literal enumeration remains outside the registry module (swept and verified)
  done: false
- title: Changelog lists each user-visible fix from the migration
  done: false
- title: Parent spec acceptance reviewed and confirmed
  done: false
- title: clj-kondo clean; full test suite green
  done: false
deps:
- aix-01m0dpf8n0gf
- aix-01m0dpf8rge2
- aix-01m0dpf8vsya
---

## Description

The contract step of the parent spec's expand-contract sequence. With all three migrate batches landed, delete every leftover per-harness literal (label maps, keyword vectors, name sets, case branches) and verify by sweep that "which harnesses exist and what can each do" is answerable only from the registry module.

Consolidate the changelog entries for the user-visible fixes (pi suggestions, label unification, gitleaks harness_args warning, skip-permissions single-sourcing) and confirm the parent spec's testing decisions are all satisfied.
