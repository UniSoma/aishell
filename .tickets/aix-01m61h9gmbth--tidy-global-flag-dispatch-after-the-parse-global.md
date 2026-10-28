---
id: aix-01m61h9gmbth
title: Tidy global-flag dispatch after the parse-global-flags refactor
status: open
type: chore
priority: 2
mode: afk
created: '2026-10-28T15:15:38.236876677Z'
updated: '2026-10-28T15:15:38.236876677Z'
tags:
- ready-for-agent
acceptance:
- title: One code path starts the shell for an empty command
  done: false
- title: The reason --json stays out of parse-global-flags is written beside it
  done: false
- title: dispatched-command is gone; tests use dispatched
  done: false
- title: No has-subcommand? or unowned-flags local remains in cli.clj
  done: false
deps:
- aix-01m61gkfh089
links:
- aix-01m61edx3fdb
---

## Description

Small findings from landing aix-01m61edx3fdb (faadcb5) on 2026-10-28; the user wants small cleanups done directly.

1. Depth review, blocking: two places decide "no command means a shell": `do-dispatch*` checks `(and (empty? args) (or unsafe? verbose? container-name base-image?))` and `handle-default`'s `:else` starts a shell for bare `aishell`. Send every empty-args dispatch to `(run/run-container nil [] launch-opts)` and delete the unreachable `:else`.
2. Depth review: `--json` is decided in `dispatch`, not `parse-global-flags`. Keep it there and write the reason beside it: `--json` is valid after aishell's own subcommands (`aishell ps --json`), so it can't be a leading-only flag.
3. Fold the `dispatched-command` test helper into `dispatched` (repeated stubs).
4. Rename the local `has-subcommand?` in `do-dispatch*` to `own-subcommand?`: it is true only for aishell's own subcommands.
5. Context review: the `cli.clj` docstring calls `--json`/`--help` "leading flags aishell does not own". Say "the other leading flags (`--json`, `--help`), which dispatch reads later" and rename the local `unowned-flags` to `later-flags`.

Waits on aix-01m61gkfh089 because both edit cli.clj. Behaviour stays the same.
