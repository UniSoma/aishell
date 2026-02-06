---
created: 2026-02-06T04:30
title: Consider adding pi coding-agent as another harness
area: general
files: []
---

## Problem

The project currently supports specific agentic AI tools (Claude Code, aider, etc.) as harnesses.
There's a new open-source coding agent at https://github.com/badlogic/pi-mono/tree/main/packages/coding-agent
that could be worth adding as another supported harness.

Need to evaluate:
- What runtime requirements it has (Node.js? Python? Other?)
- How it's invoked (CLI? Server?)
- Whether it fits the aishell container model (isolated, reproducible)
- What configuration/setup it needs

## Solution

TBD — requires investigation of the pi coding-agent repo to understand its architecture and runtime needs before deciding if/how to add it as a harness.
