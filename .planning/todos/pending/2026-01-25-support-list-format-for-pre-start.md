---
created: 2026-01-25T15:01
title: Support list format for pre_start
area: config
files:
  - src/aishell/config.clj
  - src/aishell/docker/templates.clj
  - docs/CONFIGURATION.md
---

## Problem

Currently `pre_start` only accepts a single string. When users need to run multiple commands, they must manually chain them with `&&`:

```yaml
pre_start: "redis-server --daemonize yes && nginx -g 'daemon off;' &"
```

This becomes unwieldy with multiple commands and is error-prone (missing `&&`, quote escaping).

## Solution

Accept both string (current) and list formats:

```yaml
# Current format (still supported)
pre_start: "redis-server --daemonize yes"

# New list format
pre_start:
  - "redis-server --daemonize yes"
  - "nginx -g 'daemon off;' &"
  - "echo 'Services started'"
```

Implementation:
- In config loading, normalize to string by joining list elements with ` && `
- Update `docs/CONFIGURATION.md` to document both formats
- Update `docs/CONFIGURATION.md` merge strategy table (list behavior)
