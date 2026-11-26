---
id: aix-01m1203e3n9k
title: 'Spike: can OAuth tokens be brokered for Claude Code, Codex and Copilot?'
status: open
type: task
priority: 3
mode: hitl
created: '2026-08-27T16:15:58.581396664Z'
updated: '2026-11-26T14:25:04.329352805Z'
acceptance:
- title: Feasibility verdict noted; implementation ticket filed if feasible
  done: false
- title: 'Per-harness findings recorded for Claude Code, Codex and Copilot: login-state location, base-URL support, refresh ownership'
  done: false
links:
- aix-01m1203e6xrj
tags:
- security
---

## Description

ADR 0006 accepts OAuth tokens in-guest, mitigated by egress control. This spike checks whether that acceptance can be lifted. Question: can a sidecar hold `~/.claude/.credentials.json` (and Codex's equivalent under `~/.codex`) and inject `Authorization` on the way to the API, while the harness inside the sandbox sees a dummy credential and still believes it is logged in?

Things to establish, per harness: where login state is read from; whether a base-URL/proxy variable is honored for the API calls (`ANTHROPIC_BASE_URL` for Claude Code; Codex's config `base_url`/provider); whether token refresh happens in-process (if so, the sidecar must own refresh); whether the harness validates the token locally before use.

Outcome is a note on this ticket: feasible (then file the implementation ticket, blocked by the secret broker) or not (then the ADR's residual risk stands). No code is expected from this spike.

## Notes

**2026-11-26T14:25:04.329352805Z**

2026-11-26: Dependency on aix-01m1203dqawd (network modes) dropped by the user: the spike is research with no code and does not need the proxy to run. Copilot (added to the harness registry in 733bdc7d) joins the harnesses to check; it likely signs in with a GitHub token rather than an API key.
