---
id: aix-01kxmdfj56ct
title: Harness provider protocol
status: open
type: task
priority: 2
mode: hitl
created: '2026-07-16T02:53:19.907593579Z'
updated: '2026-07-16T02:53:19.907593579Z'
parent: aix-01kxmde9p330
tags:
- wayfinder:grilling
deps:
- aix-01kxmdfj1ehw
---

## Description

## Question

Design the seam a future harness (OpenCode, Codex, Gemini, pi) implements to become orchestrable: which operations it must provide (headless command construction, output/envelope parsing, resume invocation), the mechanism (Clojure protocol vs multimethods vs plain maps of fns), and how providers are registered/selected. Claude Code is the only v1 implementation, but the seam must not leak Claude-isms.
