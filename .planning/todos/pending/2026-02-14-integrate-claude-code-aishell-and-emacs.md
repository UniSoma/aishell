---
created: 2026-02-14T02:19:49.945Z
title: Integrate Claude Code, aishell and Emacs
area: cli
files:
  - src/aishell/docker/run.clj
  - src/aishell/docker/templates.clj
---

## Problem

Editor plugins like [claude-code.el](https://github.com/stevemolitor/claude-code.el) launch Claude Code as a local subprocess and communicate via stdin/stdout through a terminal emulator (eat/vterm). Meanwhile, `aishell claude` runs Claude Code inside a Docker container with `docker run --rm -it`. These two approaches are incompatible by default — the Emacs plugin can't connect to a containerized Claude Code instance.

Currently, the only workaround is a wrapper script that sets `claude-code-program` to invoke `aishell claude` instead of bare `claude`, but this has drawbacks:
- Each invocation starts a fresh container (startup overhead)
- No session persistence across Emacs buffer restarts
- Potential TTY/terminal control sequence edge cases through Docker layer

## Solution

Explore approaches to better integrate aishell with editor plugins:

1. **Wrapper script mode** (minimal): Document the wrapper approach and provide an official `aishell claude --emacs` flag that outputs in a format compatible with editor plugins (no extra TTY flags, clean stdio)

2. **Daemon/attach mode** (medium): Add `aishell claude --daemon` to start a persistent container in background, and `aishell claude --attach` to connect to it. Editor plugins would call `--attach` which is fast (no container startup). Container stays warm between invocations.

3. **Socket/server mode** (ambitious): Expose Claude Code's communication channel (if it supports one) through a Unix socket or TCP port that editor plugins can connect to directly.

4. **Native Emacs package** (ambitious): Build an `aishell.el` package that wraps `claude-code.el` with aishell-specific features (container lifecycle management, project detection, foundation image status).

Research which approach best fits the aishell philosophy and Claude Code's architecture.
