---
id: aix-01kxmdfj9kzr
title: aishell run CLI surface
status: open
type: task
priority: 2
mode: hitl
created: '2026-07-16T02:53:20.045393236Z'
updated: '2026-07-16T02:53:20.045393236Z'
parent: aix-01kxmde9p330
tags:
- wayfinder:grilling
deps:
- aix-01kxmdfj1ehw
---

## Description

## Question

Design the thin CLI over the library: the `aishell run` subcommand's flags and ergonomics — prompt as arg, from file, or stdin; `--resume`; output modes (human-readable vs `--json` envelope passthrough); exit-code contract for scripting. Keep it the 80%-case wrapper — anything needing composition belongs in a bb script against the library.
