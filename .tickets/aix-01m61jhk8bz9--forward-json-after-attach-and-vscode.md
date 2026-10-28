---
id: aix-01m61jhk8bz9
title: Forward --json after attach -- and vscode --
status: open
type: bug
priority: 2
mode: afk
created: '2026-10-28T15:37:31.645062385Z'
updated: '2026-10-28T15:37:31.645062385Z'
tags:
- ready-for-agent
acceptance:
- title: aishell attach x -- cmd --json passes --json to cmd
  done: false
- title: aishell vscode -- --json passes --json to code
  done: false
- title: aishell ps --json still emits JSON
  done: false
deps:
- aix-01m61hs45kef
links:
- aix-01m61h9gqhew
---

## Description

Found 2026-10-28 by the review of aix-01m61h9gqhew (c5179e5). That fix forwards `--json` after `exec` and harness names via `argv-owning-subcommands` in cli.clj, but `aishell attach x -- cmd --json` and `aishell vscode -- --json` still lose `--json`: after `--` the argv belongs to the attached command or the `code` CLI. Extend the split so everything after `--` for attach and vscode is forwarded untouched.
