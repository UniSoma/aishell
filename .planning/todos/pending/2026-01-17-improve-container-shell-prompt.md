---
created: 2026-01-17T15:40
title: Improve container shell prompt
area: ui
files:
  - bashrc.aishell
---

## Problem

The shell prompt inside the container currently shows the full absolute path, which takes up too much horizontal space in the terminal. For example:

```
[aishell] jonas@aishell:/home/jonasrodrigues/projects/harness$
```

This is especially problematic for deeply nested directories where the prompt can consume most of the terminal width.

## Solution

Modify the PS1 in `bashrc.aishell` to show a shorter path representation:

Options:
- Show only the current directory name (`\W` instead of `\w`)
- Show abbreviated path (e.g., `~/p/harness` or just last 2 components)
- Use `PROMPT_DIRTRIM=2` to limit path depth

TBD: Decide which approach best balances context vs. brevity.
