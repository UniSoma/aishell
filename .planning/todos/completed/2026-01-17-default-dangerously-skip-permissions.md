---
created: 2026-01-17T21:48
resolved: 2026-01-18
title: Default to --dangerously-skip-permissions for Claude
area: tooling
files:
  - aishell
---

## Problem

When running Claude Code inside the aishell container, the environment is already sandboxed and isolated from the host system. The `--dangerously-skip-permissions` flag would allow Claude to operate without permission prompts, which is safe in this context since the container provides the security boundary.

Currently users must manually add this flag if they want uninterrupted Claude operation.

## Solution

Modified aishell to pass `--dangerously-skip-permissions` by default when launching Claude.

- Default behavior: permissions skipped (container is sandbox)
- Opt-out: `AISHELL_SKIP_PERMISSIONS=false` environment variable

## Status

RESOLVED - Implemented in 06-02-PLAN.md (commit d243591).
