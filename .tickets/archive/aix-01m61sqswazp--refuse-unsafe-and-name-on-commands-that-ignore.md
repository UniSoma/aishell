---
id: aix-01m61sqswazp
title: Refuse --unsafe and --name on commands that ignore them
status: closed
type: feature
priority: 2
mode: afk
created: '2026-10-28T17:43:15.082087595Z'
updated: '2026-10-28T17:49:01.139961321Z'
closed: '2026-10-28T17:49:01.139961321Z'
tags:
- ready-for-agent
acceptance:
- title: aishell --unsafe <cmd> errors for every own subcommand (exec, ps, setup, check, …) and still works on harnesses, shell and bare aishell
  done: true
- title: aishell --name x <cmd> errors for every own subcommand and still works on harnesses, shell and bare aishell
  done: true
- title: Tests cover the refusals and the accepted launches
  done: true
- title: CHANGELOG [Unreleased] records the change
  done: true
external_refs:
- git:4512ba456c2eeadd5875642ff0298010cd565564
---

## Description

Decided by the user 2026-10-28. `aishell --unsafe ps` and `aishell --name x exec ls` accept the flag and silently drop it: only the long-running sandbox launches (harness subcommands, `shell`, bare `aishell`) read `unsafe?` and `container-name` (cli.clj do-dispatch*, ~line 863). Refuse them the way `--base-image` already is refused on own subcommands (cli.clj ~868-872): an error naming the flag and the command, with a hint like `Try: aishell --unsafe <harness>`.

`exec` is one of the commands that refuse both: it runs a throwaway container under its own name and has no sensitive-file prompt to skip (run/run-exec receives only :base-image). Add a CHANGELOG [Unreleased] entry.

## Notes

**2026-10-28T17:49:01.139961321Z**

aishell's own subcommands (exec, attach/a, vscode, ps, setup, update, check, volumes, info, upgrade) refuse a leading --unsafe or --name with an error naming the flag; harness launches and bare aishell still take them. Refusal runs before the update check. CHANGELOG and llm.txt updated.
