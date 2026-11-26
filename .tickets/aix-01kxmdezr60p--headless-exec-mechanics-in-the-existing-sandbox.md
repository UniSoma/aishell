---
id: aix-01kxmdezr60p
title: Headless exec mechanics in the existing sandbox
status: open
type: task
priority: 2
mode: hitl
created: '2026-07-16T02:53:01.061960923Z'
updated: '2026-11-26T14:32:39.192028895Z'
parent: aix-01kxmde9p330
tags:
- wayfinder:grilling
---

## Description

## Question

How does a headless run physically execute? Decide how `docker exec` composes with aishell's existing container start/reuse code (src/aishell/docker/run.clj): detecting whether the project sandbox is running, starting it if not, exec'ing without a TTY, working directory and user inside the container. Also decide how a headless run coexists with a concurrent interactive session in the same container — including how this interacts with the in-flight Claude-isolation work (shared vs project machine state). If coexistence proves messy, decide whether the dedicated-orchestration-container escape hatch graduates from the fog.

## Notes

**2026-07-16T02:59:55.731335293Z**

Research input: --dangerously-skip-permissions exits 1 when run as root unless IS_SANDBOX=1 is set in the container env — the exec-mechanics decision must pick non-root exec user and/or set IS_SANDBOX. Also: session lookup is scoped to cwd inside the container, and headless runs share ~/.claude machine state with interactive sessions. See document aix-01kxmde9p330-d43m7 on the parent epic (`knot document show aix-01kxmde9p330-d43m7`).

**2026-11-26T14:32:39.192028895Z**

2026-11-26 decision by the user (Iris ticket review): headless runs always start their own sandbox on the strict profile; they do not join a running interactive sandbox through docker exec. This replaces the epic's locked "Container: reuse the project's existing sandbox via docker exec" choice. A headless sandbox leaves out the config's `ports` entries, so it never collides with host ports a dev sandbox already publishes; publishing on Docker-chosen host ports can be added later if host access is ever needed. Why: ADR 0006 puts headless runs on strict, and a profile is fixed at docker run, which docker exec cannot change. Next: /refine aix-01kxmdezr60p to rewrite the exec mechanics around this.
