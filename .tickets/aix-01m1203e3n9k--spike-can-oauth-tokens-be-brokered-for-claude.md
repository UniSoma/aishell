---
id: aix-01m1203e3n9k
title: 'Spike: can OAuth tokens be brokered for Claude Code and Codex?'
status: open
type: task
priority: 3
mode: hitl
created: '2026-08-27T16:15:58.581396664Z'
updated: '2026-08-27T16:17:27.158173574Z'
acceptance:
- title: 'Per-harness findings recorded for Claude Code and Codex: login-state location, base-URL support, refresh ownership'
  done: false
- title: Feasibility verdict noted; implementation ticket filed if feasible
  done: false
deps:
- aix-01m1203dqawd
links:
- aix-01m1203e6xrj
tags:
- security
---

## Description

ADR 0006 accepts OAuth tokens in-guest, mitigated by egress control. This spike checks whether that acceptance can be lifted. Question: can a sidecar hold `~/.claude/.credentials.json` (and Codex's equivalent under `~/.codex`) and inject `Authorization` on the way to the API, while the harness inside the sandbox sees a dummy credential and still believes it is logged in?

Things to establish, per harness: where login state is read from; whether a base-URL/proxy variable is honored for the API calls (`ANTHROPIC_BASE_URL` for Claude Code; Codex's config `base_url`/provider); whether token refresh happens in-process (if so, the sidecar must own refresh); whether the harness validates the token locally before use.

Outcome is a note on this ticket: feasible (then file the implementation ticket, blocked by the secret broker) or not (then the ADR's residual risk stands). No code is expected from this spike.
