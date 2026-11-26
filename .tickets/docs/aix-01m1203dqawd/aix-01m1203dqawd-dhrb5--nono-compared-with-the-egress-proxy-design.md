---
id: aix-01m1203dqawd-dhrb5
ticket: aix-01m1203dqawd
title: nono compared with the egress-proxy design
type: other
created: '2026-11-26T13:38:04.015924933Z'
updated: '2026-11-26T13:38:04.015924933Z'
---

# nono compared with the egress-proxy design

Whether nono (nono.sh), a Landlock/Seatbelt sandbox for AI agents, can replace or sit beside the squid sidecar of ADR 0006. Gathered 2026-10-07 from nono's site and repository, the kernel Landlock docs and Docker's default seccomp profile; a Landlock probe ran inside an aishell container on a Linux 7.0 host. nono itself was not installed or run, and its APIs are still settling before 1.0. Trimmed from aishell@530a0131:docs/research/nono-sandbox.md, which also covers nono's credential injection, rollback and audit log.

## What nono does for networking

- It wraps one process tree, unprivileged, with no container or VM: Landlock on Linux (kernel 5.13+, targets ABI 5), Seatbelt on macOS, Landlock through WSL2 on Windows.
- Network: a proxy on a random localhost port, and a kernel rule that lets the child connect only to that port. `--block-net` cuts all network access.
- Always denied: `169.254.0.0/16`, `fe80::/10`, `metadata.google.internal`. Its docs don't mention RFC 1918 ranges.
- `upstream_proxy` / `NONO_UPSTREAM_PROXY` chains its proxy into another proxy.

## Why it does not replace the sidecar

From the kernel Landlock docs:

- `struct landlock_net_port_attr` has only `allowed_access` and `port`, no destination address. Allowing port N allows port N on every host, and the child sees the port in the `*_BASE_URL` it is given.
- TCP rules arrived in ABI 4; UDP rules (`NET_BIND_UDP`, `NET_CONNECT_SEND_UDP`) only in ABI 10. Below ABI 10, UDP (DNS, QUIC) is unrestricted.

nono's docs don't say whether it closes either gap another way. The sidecar design has neither weakness: the sandbox's `--internal` network has no route out except through the proxy.

## Running it inside the container as a second layer

Only this direction works. Wrapping `aishell claude` on the host constrains the CLI, not the container, and nono's profiles block `docker` anyway. The order would be:

```
aishell entrypoint (root) -> gosu (drop to the dev user) -> nono run --profile ... -- <harness>
```

with nono's `upstream_proxy` pointed at the squid sidecar.

- Docker's default seccomp profile allows `landlock_create_ruleset`, `landlock_add_rule` and `landlock_restrict_self` with no kernel-version condition.
- Probe: inside an aishell container, `syscall(444, NULL, 0, LANDLOCK_CREATE_RULESET_VERSION)` returned ABI 8. Enough for nono's ABI 5 and the ABI 4 per-port rules, still below ABI 10.
- Docker Desktop is untested. WSL2's kernel is 6.6 and per-port rules need 6.7+; macOS runs LinuxKit. In-container network filtering may be Linux-only, against the platform promise in docs/ARCHITECTURE.md.
- Next step if pursued: `nono run --allow-cwd -- ls` in a throwaway container, then a harness profile.

## Conclusion for this ticket

Keep the sidecar; ADR 0006 holds. nono could later be an opt-in layer inside the sandbox, chained to the egress proxy, but it is not part of this ticket.
