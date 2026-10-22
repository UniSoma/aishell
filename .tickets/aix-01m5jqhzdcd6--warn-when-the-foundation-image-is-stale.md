---
id: aix-01m5jqhzdcd6
title: Warn when the foundation image is stale
status: in_progress
type: feature
priority: 1
mode: afk
created: '2026-10-22T21:16:30.506778338Z'
updated: '2026-10-22T21:17:09.736048984Z'
tags:
- ready-for-agent
acceptance:
- title: Foundation builds carry a build-timestamp label
  done: false
- title: Starting a sandbox on a foundation older than 30 days, or without the label, prints a warning naming aishell update --force
  done: false
- title: A fresh foundation prints no warning
  done: false
- title: Tests cover the age check, including the missing-label case
  done: false
- title: clj-kondo --lint src test is clean
  done: false
---

## Description

On 2026-10-22 the sandbox still trusted emSign ECC Root CA - G3 (eMudhra, compromised intermediates; Debian #1151413) because the foundation image's apt layer was reused from Docker cache, freezing ca-certificates at 20250419 while trixie-security shipped 20250419+deb13u1. Nothing tells users their image is old.

Add a build-timestamp label to the foundation image at build time. When a sandbox starts and the label is older than 30 days, print a one-line warning suggesting `aishell update --force`. Images without the label (built before this change) count as stale.
