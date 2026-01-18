---
created: 2026-01-18T01:24
title: Investigate Claude statusline host/container parity
area: tooling
files: []
---

## Problem

Claude Code's statusline appears differently when running on the host versus inside an aishell container. This inconsistency may confuse users who expect the same visual experience regardless of where Claude is running.

Need to investigate:
1. What causes the statusline difference (environment variables, terminal detection, config files)
2. Whether the container environment is missing something the host has
3. How Claude Code determines its statusline configuration

## Solution

TBD - requires investigation into Claude Code's statusline configuration mechanism and what environment factors influence it.
