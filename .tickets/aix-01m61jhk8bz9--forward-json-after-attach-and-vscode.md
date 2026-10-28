---
id: aix-01m61jhk8bz9
title: Forward --json after attach -- and vscode --
status: in_progress
type: bug
priority: 2
mode: afk
created: '2026-10-28T15:37:31.645062385Z'
updated: '2026-10-28T16:01:14.469198265Z'
tags:
- ready-for-agent
acceptance:
- title: aishell attach x -- cmd --json passes --json to cmd
  done: false
- title: aishell vscode -- --json passes --json to code
  done: false
- title: aishell ps --json still emits JSON
  done: true
deps:
- aix-01m61hs45kef
links:
- aix-01m61h9gqhew
---

## Description

Found 2026-10-28 by the review of aix-01m61h9gqhew (c5179e5). That fix forwards `--json` after `exec` and harness names via `argv-owning-subcommands` in cli.clj, but `aishell attach x -- cmd --json` and `aishell vscode -- --json` still lose `--json`: after `--` the argv belongs to the attached command or the `code` CLI. Extend the split so everything after `--` for attach and vscode is forwarded untouched.

## Notes

**2026-10-28T16:01:14.469198265Z**

AC1 and AC2 need a host with Docker, an interactive terminal and VSCode's code CLI (none on the landing host). Observed so far: dispatch hands ["cmd" "--json"] to attach/attach-to-container and {:code-args ["--json"]} to vscode/open-vscode (tests flags-after-attach-separator-belong-to-the-command and flags-after-vscode-separator-belong-to-code), and bb -m aishell.core attach x -- cmd --json and vscode -- --json now reach the TTY and code-CLI checks instead of failing with unsupported_json. To verify AC1: start a container (aishell claude, or aishell in another terminal), then run `aishell attach <name> -- sh -c 'echo "$@"; exit' _ --json` and expect it to print --json, with no unsupported_json error. To verify AC2: run `aishell --verbose vscode -- --json` and expect the verbose line 'Passing to code CLI: [... "--json"]' with no literal "--" before it, and code to receive --json (code may then report it as an unknown option, which is fine).
