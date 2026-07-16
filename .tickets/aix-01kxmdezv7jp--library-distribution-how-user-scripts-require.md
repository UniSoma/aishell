---
id: aix-01kxmdezv7jp
title: 'Library distribution: how user scripts require aishell'
status: open
type: task
priority: 2
mode: hitl
created: '2026-07-16T02:53:01.159305555Z'
updated: '2026-07-16T02:53:01.159305555Z'
parent: aix-01kxmde9p330
tags:
- wayfinder:grilling
---

## Description

## Question

How does a user's babashka orchestration script actually `require` aishell's orchestration namespaces? Options to weigh: git dep in the user's bb.edn, bbin install, aishell exposing its own classpath (e.g. `aishell orchestrate script.clj` running the script inside aishell's bb environment), or publishing a lib. The answer shapes how the library API is documented and versioned.
