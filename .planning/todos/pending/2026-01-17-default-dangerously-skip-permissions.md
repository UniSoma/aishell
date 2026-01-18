---
created: 2026-01-17T21:48
title: Default to --dangerously-skip-permissions for Claude
area: tooling
files:
  - scripts/aishell
---

## Problem

When running Claude Code inside the aishell container, the environment is already sandboxed and isolated from the host system. The `--dangerously-skip-permissions` flag would allow Claude to operate without permission prompts, which is safe in this context since the container provides the security boundary.

Currently users must manually add this flag if they want uninterrupted Claude operation.

## Solution

Modify the Claude harness script to pass `--dangerously-skip-permissions` by default when launching Claude inside the container. Consider:
- Making this configurable via environment variable (e.g., `AISHELL_SKIP_PERMISSIONS=1` by default)
- Documenting that the container isolation makes this safe
- Potentially allowing users to opt-out if desired
