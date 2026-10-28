---
id: aix-01m61h9gqhew
title: 'aishell exec jq --json fails: --json is stripped after exec'
status: open
type: bug
priority: 2
mode: afk
created: '2026-10-28T15:15:38.339120507Z'
updated: '2026-10-28T15:15:38.339120507Z'
tags:
- ready-for-agent
acceptance:
- title: aishell exec jq --json runs jq with --json in the container
  done: false
- title: aishell ps --json still emits JSON
  done: false
deps:
- aix-01m61h9gmbth
links:
- aix-01m61edx3fdb
---

## Description

Found while landing aix-01m61edx3fdb (faadcb5) on 2026-10-28; predates it. `cli/dispatch` removes `--json` from anywhere in argv for non-harness commands, so `aishell exec jq --json` errors with `unsupported_json` instead of running `jq --json` in the container.

Direction: after `exec`, the argv is the container command; forward it untouched, `--json` included, the way harness args are. `aishell --json exec ...` (leading) can keep its current meaning.
