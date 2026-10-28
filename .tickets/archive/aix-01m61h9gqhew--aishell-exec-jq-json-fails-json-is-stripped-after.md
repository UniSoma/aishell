---
id: aix-01m61h9gqhew
title: 'aishell exec jq --json fails: --json is stripped after exec'
status: closed
type: bug
priority: 2
mode: afk
created: '2026-10-28T15:15:38.339120507Z'
updated: '2026-10-28T17:35:52.059023152Z'
closed: '2026-10-28T17:35:52.059023152Z'
tags:
- ready-for-agent
acceptance:
- title: aishell exec jq --json runs jq with --json in the container
  done: true
- title: aishell ps --json still emits JSON
  done: true
deps:
- aix-01m61h9gmbth
links:
- aix-01m61edx3fdb
- aix-01m61jhk8bz9
---

## Description

Found while landing aix-01m61edx3fdb (faadcb5) on 2026-10-28; predates it. `cli/dispatch` removes `--json` from anywhere in argv for non-harness commands, so `aishell exec jq --json` errors with `unsupported_json` instead of running `jq --json` in the container.

Direction: after `exec`, the argv is the container command; forward it untouched, `--json` included, the way harness args are. `aishell --json exec ...` (leading) can keep its current meaning.

## Notes

**2026-10-28T15:36:45.851379996Z**

AC1 needs a host with Docker (not installed on the landing host). Observed so far: dispatch forwards ["jq" "--json"] to run/run-exec (test json-after-exec-belongs-to-the-container-command), and bb -m aishell.core exec jq --json now reaches run-exec's Docker check instead of failing with unsupported_json. To verify: in a project with aishell set up, run `aishell exec jq --json -n '{}'` (the jq in the image must support --json, or use `aishell exec sh -c 'echo "$@"' _ --json` and expect it to print --json). Expect the command to run in the container with --json; no unsupported_json error.

**2026-10-28T17:35:52.059023152Z**

aishell exec passes a trailing --json to the container command; verified on a Docker host (exec sh -c 'echo "$@"' _ --json printed --json).
