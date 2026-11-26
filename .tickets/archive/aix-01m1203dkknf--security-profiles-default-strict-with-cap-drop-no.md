---
id: aix-01m1203dkknf
title: 'Security profiles: default and strict, with docker_args refused on security flags'
status: closed
type: feature
priority: 1
mode: hitl
created: '2026-08-27T16:15:58.067238058Z'
updated: '2026-11-26T17:24:44.592907408Z'
closed: '2026-11-26T17:24:44.592907408Z'
acceptance:
- title: An unknown security_profile value exits 1 naming the key and valid values; no key means default; a project config cannot relax a global strict
  done: true
- title: default adds --pids-limit 4096 and nothing else on harness, shell, exec and vscode-start docker runs
  done: true
- title: strict adds --pids-limit 4096, --security-opt no-new-privileges, --cap-drop ALL and --cap-add CHOWN,SETUID,SETGID,FOWNER,DAC_OVERRIDE on all four doors
  done: true
- title: 'On the host under strict: aishell claude starts, the entrypoint finishes and Claude Code answers a prompt; aishell exec id succeeds; sudo true inside fails'
  done: true
- title: A refused docker_args flag, as --flag=v or --flag v, from global or project config, exits 1 before docker run naming every refused entry, on harness, shell, exec and vscode-start paths; --unsafe does not override
  done: true
- title: A mounts entry whose source contains docker.sock exits 1 the same way
  done: true
- title: aishell --unsafe-docker-args <cmd> lets refused entries through and prints one line per entry it let through
  done: true
- title: aishell check prints the active profile and fails on refused docker_args or mounts; aishell info prints the active profile
  done: true
- title: CONFIGURATION.md documents security_profile, what each profile breaks, the refused flags and the override, with the docker_args warning table and --pids-limit use case updated; ARCHITECTURE.md security section names both profiles
  done: true
links:
- aix-01kr1d764vty
- aix-01kxmde9p330
tags:
- security
- host-needed
- settled
---

## Description

A project picks a Security profile with `security_profile: default | strict` in `.aishell/config.yaml` or `~/.aishell/config.yaml`; no key means `default`. The profile applies to every Sandbox start: harness launch, bare shell, `exec`, and the `vscode` Sandbox when it is started rather than joined. `default` caps the process count. `strict` also drops every capability except the five the entrypoint needs to set up the user and hand off through gosu, and turns off privilege escalation, so in-sandbox `sudo` and setuid binaries stop working. `aishell check` and `aishell info` name the active profile.

Under every profile, a `docker_args` entry that grants privilege, changes capabilities or security options, joins a host namespace or another network or runtime, exposes a device, or mounts the Docker socket stops the start with exit 1, naming every offending entry. A `mounts` entry with the Docker socket is refused the same way. The only way past is the Global flag `--unsafe-docker-args`, which prints each entry it let through; `--unsafe` does not apply, and the message says so. `aishell check` reports refused entries as failures.

Network mode selection by `strict` ships with aix-01m1203dqawd. Headless runs on `strict` ship with the orchestration epic aix-01kxmde9p330. Decision record: ADR 0006 (`docs/adr/0006-harden-on-docker-with-an-egress-proxy.md`).

### Decisions

Settled by the user (2026-11-26):

- Y1: config key only; no CLI flag for the profile. The orchestration layer sets `strict` in code ("Y1. (a)", user, 2026-11-26).
- Y2: across global and project config the stricter profile wins; a project config cannot relax a global `strict`. `extends: none` still drops the global config, a named limit not fixed here ("Y2. (a)", user, 2026-11-26).
- Y3: `no-new-privileges` is in `strict` only, as ADR 0006 says; `default` adds only `--pids-limit 4096`, so `sudo` in `pre_start` keeps working under `default` ("Y3. (a)", user, 2026-11-26).
- Y4: the override is a new Global flag `--unsafe-docker-args`, accepted before harness launches, bare `aishell`, `exec` and `vscode`; `--unsafe` keeps its meaning (sensitive-file detection only). The CONTEXT.md Global flag entry lists it; ADR 0010 stays as written ("Y4. (a)", user, 2026-11-26).
- Y5: a `mounts` entry whose source contains `docker.sock` is refused with the same override. The other `mounts` warnings stay warnings ("Y5. (a)", user, 2026-11-26).
- Y6: refused `docker_args` flags are `--privileged`, `--cap-add`, `--cap-drop`, `--security-opt`, `--network`, `--net`, `--runtime`, `--pid`, `--userns`, `--ipc`, `--uts`, `--cgroupns`, `--device`, `--device-cgroup-rule`, `--volumes-from`, and any token containing `docker.sock`; `--pids-limit`, `--gpus` and `-u`/`--user` stay allowed ("Y6. (a)", user, 2026-11-26).

Assumed:

- The profile resolves in `aishell.config` (`resolve-security-profile`, beside `resolve-claude-isolation`); `security_profile` joins `known-keys` and the valid-keys message. An unknown value exits 1.
- `build-security-args` sits in `aishell.docker.run` beside `build-resource-args`, emitted before `resources` and `docker_args`.
- `strict` adds `--pids-limit 4096`, `--security-opt no-new-privileges`, `--cap-drop ALL`, and `--cap-add` for CHOWN, SETUID, SETGID, FOWNER, DAC_OVERRIDE: the set ADR 0006 fixes. Removing more is out of scope; the host run in the acceptance shows the five suffice.
- `--pids-limit 4096` is a flat bound Docker enforces; carried from the original ticket, not measured against large builds.
- The refusal is `validation/check-refused-entries!` over the entries `validation/refused-entries` finds, a hard reject through `output/error` like `check-claude-shared-paths`. It replaces `dangerous-patterns` and `warn-dangerous-args` (`apparmor|seccomp=unconfined` falls under `--security-opt`). It runs as the first step of `build-docker-args-internal`, so the four doors share one check and no host config path is created first; the earlier name check and harness volume are idempotent caches. The override arrives as an opt key `:unsafe-docker-args`.
- Matching runs on tokens after `util/expand-vars`; a token matches on the flag name before any `=`, covering both `--flag=v` and `--flag v`; `docker.sock` matches anywhere in a token. The error lists every match.
- When overridden, one `warn` line per entry let through, on every start.
- `aishell check` prints the profile and refusals in its Security section; a refusal is a critical failure (exit 1). `aishell info` gains a Security section like its Resources section.

Door sweep:

- Rule: a Sandbox never starts with config that escalates privilege, joins host namespaces or networks, swaps the runtime, exposes devices, or mounts the Docker socket, unless the host user passed `--unsafe-docker-args`.
- Doors: `run-container` (harness, shell), `run-exec`, `vscode/start-container!`; global and project config, whose `docker_args` concatenate; the `mounts` key. Not doors: `attach` and the `vscode` join (`docker exec -u developer`, inheriting the container's settings); `docker build` in `docker/build.clj` (labels only); `harness_args` (go to the harness).
- Variants (`rg "docker_args|:mounts" src` plus a read of `build-docker-args-internal`): `ports` is regex-checked; `env` and `pre_start` values are single argv elements after `-e` and cannot inject flags; `resources` are validated; `mounts` destination options are out, since they cannot grant capabilities or namespaces.
- Untrusted input the fix reads: the YAML of both configs. Cost: linear in tokens.
- 1. Trusts the hardcoded list and the host argv, never config. 2. Boundary: the project config, writable from inside the Sandbox, into the host's `docker run` argv. 3. The check lives in `aishell.validation`, called from `build-docker-args-internal`. 4. A check, not a mechanism: `docker_args` stays a free-form escape hatch, and ADR 0006 chose a refused list over an allowlist.
- Refused-case tests: one per door, each spelling, the global source, `--unsafe` not overriding, `docker.sock` in `mounts`.

## Notes

**2026-11-26T17:24:35.646059591Z**

Host check by the user (2026-11-26), strict profile from the uncommitted checkout: `aishell claude -p` answered "ok"; `aishell exec` ran (`exec sudo true` refused by no-new-privileges, exit 1; `exec grep CapBnd /proc/self/status` printed 00000000000000cb = CHOWN, DAC_OVERRIDE, FOWNER, SETGID, SETUID). `exec id` itself was not run; the other exec runs show the exec path works.

**2026-11-26T17:24:35.767731018Z**

Landing review, settled by the user (2026-11-26): the docker.sock refusal stays a substring match as Y5/Y6 decided ("1. (a)"); a mount can still reach the daemon through a parent directory, a symlink, --mount, or another socket name, left to a follow-up ticket. cli.clj growth past 1000 lines accepted, split left to a follow-up ticket ("2. agree").

**2026-11-26T17:24:44.592907408Z**

Security profiles: security_profile default|strict (default adds --pids-limit 4096; strict adds no-new-privileges and drops all capabilities but CHOWN, SETUID, SETGID, FOWNER, DAC_OVERRIDE), stricter profile wins across configs. docker_args security flags and docker.sock mounts are refused on every Sandbox start unless --unsafe-docker-args is given; aishell check and info report the profile.
