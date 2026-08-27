---
id: aix-01m1203e6xrj
title: Secret broker for API-key harnesses
status: open
type: feature
priority: 3
mode: hitl
created: '2026-08-27T16:15:58.685269776Z'
updated: '2026-08-27T16:17:27.246539729Z'
acceptance:
- title: A harness on an API key completes a prompt with only a placeholder in its environment
  done: false
- title: The real key is never present in the sandbox filesystem, environment, or /proc
  done: false
- title: Requests to any host other than the key's declared API host never receive the real key
  done: false
- title: Harness descriptors declare key→host; CONFIGURATION.md and HARNESSES.md document which harnesses are brokered
  done: false
deps:
- aix-01m1203dqawd
links:
- aix-01m1203e3n9k
tags:
- security
---

## Description

For harnesses that authenticate with an API key from `:env-passthrough` (`OPENAI_API_KEY`, `GEMINI_API_KEY`, `GROQ_API_KEY`, …; Claude Code when on `ANTHROPIC_API_KEY`): stop passing the real value into the sandbox. Instead pass a placeholder and route the harness's API traffic through a TLS-terminating sidecar (mitmproxy addon, or squid + a small rewriting upstream) that substitutes the real key only for requests whose SNI, resolved IP and `Host` header all match the key's declared API host.

Prerequisites: network modes (the broker sits behind, or replaces, the egress proxy on the internal bridge) and per-harness verification of base-URL support (`ANTHROPIC_BASE_URL`, `OPENAI_BASE_URL`, Gemini/Vertex endpoints, OpenCode provider config). Harness descriptors gain a mapping from each key to its API host.

Not in scope: OAuth tokens (see the OAuth brokering spike). Decision record: ADR 0006.
