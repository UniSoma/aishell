---
created: 2026-01-17T15:40
resolved: 2026-01-18
title: Improve container shell prompt
area: ui
files:
  - aishell
---

## Problem

The shell prompt inside the container currently shows the full absolute path, which takes up too much horizontal space in the terminal. For example:

```
[aishell] jonas@aishell:/home/jonasrodrigues/projects/harness$
```

This is especially problematic for deeply nested directories where the prompt can consume most of the terminal width.

## Solution

Used `PROMPT_DIRTRIM=2` in bashrc.aishell to limit path depth to last 2 components.

Result: `.../parent/current` instead of full absolute path.

## Status

RESOLVED - Implemented in 06-02-PLAN.md (commit 63afd13).
