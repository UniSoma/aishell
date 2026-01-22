---
created: 2026-01-22T14:50
title: Add default harness arguments in config.yaml
area: config
files:
  - src/aishell/config.clj
  - src/aishell/run.clj
---

## Problem

Users may want to specify default arguments for specific harnesses that are automatically applied every time that harness is launched. For example, always adding `--add-plugin some-plugin` when running `aishell claude ...`.

Currently, users must manually specify these arguments on every invocation, which is tedious for commonly-used flags.

## Solution

Add a new config key (e.g., `harness_args` or `default_args`) that maps harness names to their default arguments:

```yaml
harness_args:
  claude: "--add-plugin context7"
  aider: "--model gpt-4"
```

These arguments would be automatically prefixed to any custom arguments provided at launch time:

```bash
aishell claude --verbose
# becomes: claude --add-plugin context7 --verbose
```

Implementation considerations:
- Where to inject: before or after user args? (prefix suggested)
- Per-harness granularity vs global defaults
- Interaction with existing config keys
