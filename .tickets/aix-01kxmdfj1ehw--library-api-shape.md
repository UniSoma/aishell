---
id: aix-01kxmdfj1ehw
title: Library API shape
status: open
type: task
priority: 2
mode: hitl
created: '2026-07-16T02:53:19.786668196Z'
updated: '2026-07-16T02:53:19.786668196Z'
parent: aix-01kxmde9p330
tags:
- wayfinder:grilling
deps:
- aix-01kxmdezmmx8
---

## Description

## Question

Design the babashka library's public API: namespace layout (e.g. `aishell.orchestrate`), the run function's signature and options map, the exact keys of the result map (raw output, parsed envelope, exit, session id, cost), resume ergonomics (`:resume session-id` — or a session value threaded between runs), prompt input (inline string vs file), and failure/timeout semantics (nonzero exit, harness errors, run timeouts — throw vs error value). Informed by the Claude Code headless CLI surface research.
