---
created: 2026-01-25T02:16
title: Dynamic help based on installed harnesses
area: cli
files:
  - src/aishell/cli.clj:79-95
  - src/aishell/state.clj
  - src/aishell/docker/build.clj
---

## Problem

Currently `aishell --help` shows all harness commands (claude, opencode, codex, gemini, gitleaks) regardless of whether they were actually installed during `aishell build`. This is misleading because users see commands that won't work.

Two related improvements:

1. **Dynamic help**: Read global state to see which harnesses were selected during build, only show help for installed ones.

2. **Opt-out gitleaks**: Add `--without-gitleaks` build flag to skip gitleaks installation. When not installed, omit from help.

## Solution

1. In `print-help` (cli.clj), read state and conditionally print harness commands:
   ```clojure
   (when (:with-claude state)
     (println "  claude     Run Claude Code"))
   ```

2. Add `--without-gitleaks` flag to build command, track in state as `:with-gitleaks` (default true for backwards compat).

3. Update Dockerfile template to conditionally install gitleaks based on WITH_GITLEAKS ARG.

TBD: Consider whether gitleaks should be opt-in like other harnesses (`--with-gitleaks`) or opt-out (`--without-gitleaks`). Opt-out preserves backwards compatibility.
