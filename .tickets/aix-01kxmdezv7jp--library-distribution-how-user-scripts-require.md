---
id: aix-01kxmdezv7jp
title: 'Library distribution: how user scripts require aishell'
status: open
type: task
priority: 2
mode: hitl
created: '2026-07-16T02:53:01.159305555Z'
updated: '2026-09-03T15:33:02.581894340Z'
parent: aix-01kxmde9p330
tags:
- wayfinder:grilling
links:
- aix-01m1kxchv7n0
- aix-01m1kydv7r76
---

## Description

## Question

How does a user's babashka orchestration script actually `require` aishell's orchestration namespaces? Options to weigh: git dep in the user's bb.edn, bbin install, aishell exposing its own classpath (e.g. `aishell orchestrate script.clj` running the script inside aishell's bb environment), or publishing a lib. The answer shapes how the library API is documented and versioned.

## Notes

**2026-09-03T15:14:51.256620289Z**

ADR 0007 moves distribution to a standalone binary: upstream babashka with aishell's uberjar appended, one per platform. Installers stop installing bb, so a host `bb` can no longer be assumed. The binary runs the appended jar's main, not a general `bb`, but it embeds the full runtime — so "aishell exposes its own classpath" becomes `aishell <cmd> script.clj` evaluating the user's script inside the embedded runtime, with no host babashka at all. Weigh that option against git dep / bbin with the new constraint in mind.
