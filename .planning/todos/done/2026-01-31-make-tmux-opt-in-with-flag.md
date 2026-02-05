---
created: 2026-01-31T18:30
title: Make tmux opt-in with --with-tmux flag
area: cli
files:
  - src/aishell/cli.clj
  - src/aishell/docker/templates.clj
---

## Problem

In v2.7.0 we introduced tmux integration: the harness runs inside a tmux session `main`, with support for detached launch, `attach` subcommand, and shell access. However, some users may not want tmux at all. Currently tmux is always enabled, with no way to opt out.

The behavior should be opt-in: `aishell build --with-tmux` enables tmux session creation. Without the flag, no tmux session is started and the `attach` subcommand should not be available (similar to how unspecified harnesses don't appear as subcommands after build).

## Solution

- Add `--with-tmux` flag to `aishell build` command
- Default behavior: no tmux (tmux is opt-in)
- When `--with-tmux` is not passed:
  - Skip tmux session creation in container entrypoint/templates
  - Do not register the `attach` subcommand after build
- When `--with-tmux` is passed:
  - Current behavior (tmux session `main`, attach support, detached launch)
- Store the tmux preference in build config so downstream commands know whether tmux is active
