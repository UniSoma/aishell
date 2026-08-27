---
id: aix-01m1203dkknf
title: 'Security profiles: default/strict with cap-drop, no-new-privileges, pids-limit, and blocked docker_args'
status: open
type: feature
priority: 1
mode: afk
created: '2026-08-27T16:15:58.067238058Z'
updated: '2026-08-27T16:17:26.679760990Z'
acceptance:
- title: security_profile accepts default|strict from config and CLI; unknown values exit 1
  done: false
- title: default adds no-new-privileges and pids-limit; strict additionally drops all caps and adds only the entrypoint's minimal set — verified by running the sandbox and a harness under strict
  done: false
- title: docker_args touching security-relevant flags exit 1 with the offending flag named; the override flag prints what it allowed
  done: false
- title: aishell check and aishell info report the active profile
  done: false
- title: CONFIGURATION.md documents both profiles and their known breakage
  done: false
links:
- aix-01kr1d764vty
- aix-01kxmde9p330
tags:
- security
---

## Description

Add a per-project `security_profile` key (`default` | `strict`) to `.aishell/config.yaml` and a matching CLI flag, applied in `build-docker-args-internal` (`src/aishell/docker/run.clj`).

`default`: `--security-opt no-new-privileges`, `--pids-limit 4096`. Breaks nothing that works today except setuid binaries (`sudo` inside the sandbox stops escalating — intended).

`strict`: everything in `default` plus `--cap-drop ALL` and `--cap-add CHOWN,SETUID,SETGID,FOWNER,DAC_OVERRIDE` (the entrypoint's `usermod`/`chown`/`gosu` needs these — verify the exact minimal set by running the entrypoint under the profile). `strict` also selects `network: allowlist` unless the project sets a mode explicitly (network modes ship separately; until then `strict` leaves the network as-is and `aishell check` says so).

`validation.clj`: under either profile, refuse (exit 1, not warn) `docker_args` that touch `--privileged`, `--cap-add`, `--cap-drop`, `--security-opt`, `--network`, `--runtime`, `--pid`, `--userns`, `--device`, or mount `docker.sock`. The only override is a CLI flag (e.g. `--unsafe-docker-args`) that prints every flag it let through. Patterns stay hardcoded and additive per ADR 0006 — this closes `aix-01kr1d764vty`.

Headless runs from the orchestration layer (`aix-01kxmde9p330`) default to `strict`.

Docs: `docs/CONFIGURATION.md` (new key, what each profile does and what it breaks), `docs/ARCHITECTURE.md` security section. Decision record: `docs/adr/0006-harden-on-docker-with-an-egress-proxy.md`.
