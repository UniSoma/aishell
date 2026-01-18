---
created: 2026-01-18T01:24
resolved: 2026-01-18
title: Investigate Claude statusline host/container parity
area: tooling
files:
  - aishell
---

## Problem

Claude Code's statusline appears differently when running on the host versus inside an aishell container. This inconsistency may confuse users who expect the same visual experience regardless of where Claude is running.

## Root Cause

Two environment variables were missing in the container:

1. **`COLORTERM`** - Signals 24-bit color capability to Claude Code
   - Host: Usually set to `truecolor` by terminal emulator
   - Container: Not passed through → Claude may render with reduced colors

2. **`LANG` / `LC_ALL`** - Character encoding for Unicode rendering
   - Host: Usually `en_US.UTF-8` or similar
   - Container: Defaults to `POSIX` or `C` → Unicode characters (█, ░, etc.) may render incorrectly

## Solution

Two changes to `aishell`:

### 1. Pass COLORTERM to container (line 745)

```bash
-e "COLORTERM=${COLORTERM:-truecolor}"
```

### 2. Set UTF-8 locale in bashrc.aishell

```bash
export LANG=C.UTF-8
export LC_ALL=C.UTF-8
```

Using `C.UTF-8` instead of `en_US.UTF-8` because it's available by default in Debian without installing the `locales` package.

## Verification

After rebuilding with `./aishell --rebuild --with-claude`:
- TERM, COLORTERM passed correctly
- LANG/LC_ALL set to C.UTF-8
- Unicode characters render in statusline
- Colors match host appearance

## Status

RESOLVED - Changes committed to aishell script.
