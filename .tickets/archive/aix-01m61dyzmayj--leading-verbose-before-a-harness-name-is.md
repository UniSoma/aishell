---
id: aix-01m61dyzmayj
title: Leading --verbose before a harness name is forwarded to the harness
status: closed
type: bug
priority: 2
mode: afk
created: '2026-10-28T14:17:27.428592285Z'
updated: '2026-10-28T14:38:01.843941526Z'
closed: '2026-10-28T14:38:01.843941526Z'
tags:
- ready-for-agent
acceptance:
- title: aishell --verbose claude enables aishell's verbose output and does not forward --verbose to Claude Code
  done: true
- title: aishell claude --verbose still forwards --verbose to Claude Code
  done: true
links:
- aix-01m61c17fyb8
- aix-01m61edx3fdb
---

## Description

`aishell --verbose claude` should turn on aishell's verbose build output (help text and docs/TROUBLESHOOTING.md both say so). Instead it forwards `--verbose` to Claude Code and aishell stays quiet.

Cause: `cli/dispatch` hoists a pass-through harness name to the front of argv (`(into [sub] (remove #{sub} args))`) before `do-dispatch` runs `split-leading-verbose`. After the hoist `--verbose` sits behind the harness name, so the split sees no leading flag.

Repro (stubs, no Docker needed):

    bb -cp src -e '(require (quote [aishell.cli :as c])) (with-redefs [aishell.run/run-container (fn [& a] (prn a aishell.output/*verbose*)) aishell.update-check/maybe-check-for-update (fn [& _])] (c/dispatch ["--verbose" "claude" "-p" "x"]))'

prints `("claude" ["--verbose" "-p" "x"] ...) false`.

Fix direction: split the leading `--verbose` in `dispatch` before the hoist, the way `--base-image` is split (aix-01m61c17fyb8).

## Notes

**2026-10-28T14:38:01.843941526Z**

dispatch now splits a leading --verbose before the pass-through harness hoist, as it does --base-image, so aishell --verbose claude turns on aishell's verbose output and aishell claude --verbose still forwards the flag to Claude Code.
