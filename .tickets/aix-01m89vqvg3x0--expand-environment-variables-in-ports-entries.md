---
id: aix-01m89vqvg3x0
title: Expand environment variables in ports entries
status: in_progress
type: feature
priority: 2
mode: afk
created: '2026-11-25T17:23:32.993798437Z'
updated: '2026-11-25T17:25:03.842854196Z'
acceptance:
- title: Tests cover set, unset+default, empty+default, unset no default (error), empty no default (error), empty default (error), pattern failure after substitution, plain entry unchanged
  done: true
- title: docs/CONFIGURATION.md ports section documents the syntax, the example, and where the value comes from
  done: true
- title: Released in a tagged version
  done: false
---

## Description

Let a project commit a port mapping whose host side each developer can override:

    ports:
      - "127.0.0.1:${WEAVE_NREPL_PORT:-7888}:7888"

Before the port-pattern check, each ports entry (global and project, after concatenation) has ${VAR} and ${VAR:-default} replaced from the host environment of the aishell process. $VAR without braces is left as written. Names match [A-Za-z_][A-Za-z0-9_]*; a default is the text up to the first }, used as written. Any other form stays as written and fails the usual pattern check.

Errors: ${VAR} unset, ${VAR} set but empty, and ${VAR:-} with an empty default each exit with a message naming the variable and the entry. An empty string is never substituted.

A pattern failure after substitution prints the usual message plus a "(from <entry as written>)" line. A plain entry prints exactly today's message.

Out of scope: expansion in other keys, a local override file, port ranges.
