---
id: aix-01m61edx3fdb
title: Parse aishell's leading global flags once at dispatch entry
status: closed
type: task
priority: 2
mode: afk
created: '2026-10-28T14:25:36.344326078Z'
updated: '2026-10-28T15:12:39.377384427Z'
closed: '2026-10-28T15:12:39.377384427Z'
tags:
- ready-for-agent
acceptance:
- title: aishell's global flags are parsed in one place at dispatch entry
  done: true
- title: aix-01m61dyzmayj's acceptance criteria hold
  done: true
- title: aishell --name foo claude --json launches claude with --json forwarded
  done: true
- title: aishell --verbose setup runs setup verbosely, not a shell
  done: true
- title: --unsafe and --name are recognized only before the subcommand; after it they reach the harness or command untouched
  done: true
- title: Docs and CHANGELOG describe the leading-only rule
  done: true
links:
- aix-01m61dyzmayj
- aix-01m61c17fyb8
- aix-01kr1d6xqzx3
deps:
- aix-01m61dyzmayj
---

## Description

As of 7462c39, `cli.clj` scans aishell's own flags four ways: `split-leading-flag` (knows `--name` takes a value), `do-dispatch*` (pulls `--name VALUE` and `--unsafe` from anywhere in argv), `subcommand-of` (does not know `--name` takes a value) and the harness hoist in `dispatch`. The results travel three ways: `base-image?` positionally, `--verbose` as a dynamic binding, `--unsafe`/`--name` as opts.

Known breakage from the mismatch:
- `aishell --verbose claude` forwards `--verbose` to Claude Code (aix-01m61dyzmayj).
- `aishell --name foo claude --json` prints `Unknown command: --name`.
- `aishell --verbose setup` / `aishell --unsafe update` start a shell instead of the command.

Direction (depth review of aix-01m61c17fyb8, 2026-10-28): parse every aishell global flag once at the top of `dispatch` into one map plus subcommand plus remaining args, and hand that to `do-dispatch*`. Deletes `split-leading-verbose`, the positional threading and `subcommand-of`'s separate rules.

Open decision before landing: whether `--unsafe` and `--name` stay accepted anywhere in argv or become leading-only like `--verbose` and `--base-image`. Leading-only changes what `aishell claude --unsafe` means. Refine this ticket to settle it.

## Notes

**2026-10-28T14:55:51.631912706Z**

Decided by the user 2026-10-28: --unsafe and --name become leading-only, like --verbose and --base-image. After the subcommand they are forwarded to the harness/command untouched. Changes what 'aishell claude --unsafe' means: needs a CHANGELOG entry and doc updates (README.md:394 shows 'aishell claude --unsafe'; docs/TROUBLESHOOTING.md and docs/CONFIGURATION.md mention --unsafe).

**2026-10-28T15:12:39.377384427Z**

parse-global-flags reads --verbose, --base-image, --unsafe and --name once, before the subcommand only; after a harness name they are forwarded. Leading flags no longer turn setup/update/--help into a shell, --name foo claude --json works, docs, help and CHANGELOG updated.
