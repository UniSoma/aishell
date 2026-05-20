---
id: aix-01ks30a7rqgm
title: Add --reuse-config mode to aishell setup
status: closed
type: feature
priority: 2
mode: afk
created: '2026-05-20T15:33:21.303426289Z'
updated: '2026-05-20T17:31:25.724367084Z'
closed: '2026-05-20T17:31:25.724367084Z'
tags:
- ready-for-agent
acceptance:
- title: '`aishell setup --reuse-config` fails clearly when no saved setup exists and tells the user to run plain `aishell setup --with-...` to write a new configuration'
  done: true
- title: In reuse mode, omitted options inherit from the saved intent config, while explicit CLI flags override inherited values; bare `--with-...` resets that tool to latest
  done: true
- title: Reuse mode inherits the full saved intent config, including harness enables, version pins, `with-gitleaks`, and `unisoma`, but always recomputes derived build metadata before persisting state
  done: true
- title: Validation runs against the merged effective config, so inherited OpenCode satisfies `--unisoma`, and invalid saved config fails clearly with a recovery hint
  done: true
- title: '`aishell setup --reuse-config --force` rebuilds foundation/base layers without `aishell update`''s unconditional harness-volume repopulation; if the effective config changes, setup still creates/populates the appropriate harness volume as needed'
  done: true
- title: When `--reuse-config` is active, setup prints the effective reused configuration before building; plain `aishell setup` output remains unchanged
  done: true
- title: '`aishell setup --help`, `aishell update --help`, and user-facing docs explain `--reuse-config`, its override semantics, and when to use it instead of `update`'
  done: true
- title: Focused tests cover the setup-config resolver semantics, and the implementation passes `bb test` plus `clojure -M:clj-kondo --lint src test`
  done: true
---

## Description

Add a new `aishell setup --reuse-config` mode that seeds setup defaults from the saved global setup and then applies any explicit CLI flags as overrides. This mode should let users rebuild foundation/base layers from the current saved setup without `aishell update`'s unconditional harness-volume repopulation, while still allowing normal `setup` behavior when the effective config changes.

The mode should reuse the full saved intent config (enabled harnesses, version pins, `with-gitleaks`, `unisoma`), validate the merged effective config, print the effective reused configuration when reuse mode is active, and keep plain `aishell setup` fully declarative and non-inheriting.

Document the distinction between `update` and `setup --reuse-config`, including the foundation-rebuild use case, and add focused tests around the setup-config resolver semantics.

## Notes

**2026-05-20T17:31:25.724367084Z**

Added setup --reuse-config with saved-config inheritance, merged validation, updated help/docs, and focused tests.
