---
created: 2026-01-25T17:22
title: Add shell command for one-off container execution
area: cli
files:
  - src/aishell/cli.clj
  - src/aishell/docker/run.clj
---

## Problem

Currently, aishell only supports running AI harnesses interactively inside the container. There's no easy way to run one-off commands in the container environment without starting a full interactive session.

Use cases:
- Run a quick `git status` or `npm install` inside the container
- Execute build/test commands in the isolated environment
- Debug container setup without launching a harness
- Script automation that needs container isolation

Similar to how `docker exec` or `bash -c "..."` work, but with all aishell's mount/env setup.

## Solution

TBD - idea is underspecified, needs refinement.

**Initial thoughts:**
- New subcommand: `aishell shell <command>` or `aishell exec <command>`
- Reuse existing `docker/run.clj` logic for mounts, env vars, user mapping
- Pass command directly to container instead of harness binary
- Consider: should it be interactive (`-it`) or non-interactive by default?
- Consider: how to handle arguments with spaces/quotes?

**Possible syntax options:**
```bash
# Option A: shell subcommand
aishell shell "npm install"
aishell shell ls -la

# Option B: exec subcommand (mirrors docker exec)
aishell exec "git status"

# Option C: double-dash separator
aishell -- npm install
```

**Questions to resolve:**
1. Interactive vs non-interactive default?
2. How to pass complex commands with pipes/redirects?
3. Should it auto-build the image if missing?
4. Should it support `--workdir` override?
