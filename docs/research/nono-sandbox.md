# nono: kernel sandbox for AI agents, compared with aishell

Research notes on [nono](https://nono.sh) and whether it can run alongside
aishell. Gathered from nono's website and repository, the Linux Landlock
documentation and Docker's default seccomp profile on 2026-10-07. The Landlock
probe in section 5 ran inside an aishell container on a Linux 7.0 host. nono
itself was not installed or run. Its APIs are still settling before 1.0, so
re-check the details below against its docs before building on them.

## 1. What nono is

- A Rust CLI (Apache-2.0) from the team behind Sigstore, at about 4.4k GitHub
  stars. Its README says the APIs are stabilizing ahead of a 1.0 release.
- It wraps one process tree in a kernel sandbox, unprivileged, with no daemon,
  container or VM: `nono run --profile nolabs-ai/claude-code -- claude`.
- Mechanisms: Landlock on Linux (needs kernel 5.13+, targets ABI v5, falls back
  to the highest ABI available and logs a warning), Seatbelt (`sandbox_init`)
  on macOS, and Landlock through WSL2 on Windows, where nono claims 84% feature
  parity. seccomp user notification is used only to grant extra capabilities
  on request, and that is disabled on WSL2.
- Once applied, the sandbox can only be tightened, never loosened.

### Features

| Feature | How it works |
|---------|--------------|
| Filesystem | Allowlists of read, write and execute access per path |
| Network | Proxy on a random localhost port; the kernel only lets the child connect to that port. `--block-net` cuts all network access |
| Metadata endpoints | `169.254.0.0/16`, `fe80::/10` and `metadata.google.internal` are always denied. Its docs don't mention RFC 1918 ranges |
| Credential injection | The agent gets `*_BASE_URL=http://127.0.0.1:PORT/<service>`; the proxy adds the real key as a header and forwards the request over TLS. Built-in routes: openai, anthropic and gemini. Keys come from macOS Keychain or Linux Secret Service |
| Upstream proxy | `upstream_proxy` / `NONO_UPSTREAM_PROXY` chains nono's proxy into a corporate one |
| Rollback | `--rollback` takes content-addressed snapshots of the allowed paths before and after a session. Commands: `nono rollback list`, `nono rollback show <id> --diff`, `nono rollback restore <id>` |
| Audit | Hash-chained log, sealed with a SHA-256 Merkle root |
| Profiles | Composable JSON profiles, plus a signed registry with entries for Claude Code, Codex, OpenCode, Copilot CLI, Pi and others |

The docs don't mention OAuth, so nothing says nono can broker the Claude Code
or Codex logins.

## 2. How it differs from aishell

The two tools work at different layers. aishell supplies the environment:
Docker as the boundary, a reproducible foundation image, the project mounted at
its host path, and git identity. nono supplies policy for one process inside
whatever environment it runs in.

| | aishell | nono |
|---|---|---|
| Boundary | Docker container | Landlock / Seatbelt on one process tree |
| Reproducible toolchain | Yes (foundation image) | No, uses whatever is on the host |
| Packages the agent installs | Thrown away with the container | Stay on the host, or are blocked |
| Network control | Planned (ADR 0006): squid sidecar on an `--internal` network | Built in: proxy plus a kernel port restriction |
| Secret broker | Planned for API-key harnesses | Built in, API keys only |
| Rollback, audit log | None | Built in |
| Platforms | Linux, macOS, Windows through Docker | Linux 5.13+, macOS, WSL2 |

## 3. Landlock network rules match the port only

From the kernel's Landlock documentation:

- `struct landlock_net_port_attr` has only `allowed_access` and `port`. It has
  no destination address field, so allowing a connection to port N allows port
  N on every host.
- TCP rules (`NET_BIND_TCP`, `NET_CONNECT_TCP`) arrived in ABI 4. UDP rules
  (`NET_BIND_UDP`, `NET_CONNECT_SEND_UDP`) arrived only in ABI 10.

So on Linux, nono's "child may only reach the proxy port" rule also lets the
child reach any external host listening on that port. The port number is
visible to the child, in the `*_BASE_URL` it is given. Below ABI 10, Landlock
also leaves UDP unrestricted (DNS, QUIC). nono's docs don't say whether it
closes either gap some other way. ADR 0006's design has neither weakness,
because the sandbox's internal network has no route out except through the
proxy sidecar.

## 4. Mapping to the ADR 0006 tickets

| Ticket | What nono does for it |
|--------|-----------------------|
| `aix-01m1203dqawd` network modes / egress proxy | Weaker alone (section 3). Composes: point nono's `upstream_proxy` at the squid sidecar for two layers of filtering |
| `aix-01m1203e6xrj` secret broker for API-key harnesses | Built in, but it reads keys from a keystore service the container doesn't run. The in-container fallback, `--env-credential`, puts the key in the environment, which nono itself calls less secure |
| `aix-01m1203e3n9k` OAuth broker spike | Doesn't help; no OAuth support is documented |
| `aix-01m1203dkknf` security profiles | Doesn't help; capability drops and `no-new-privileges` are Docker-level settings |
| `aix-01m1203dtgtc` resource caps | Doesn't help |
| `aix-01m1203dxk9g` / `aix-01m1203e0gm4` gVisor runtime | Doesn't help; this is a different layer |

nono also offers three things aishell has no plan for:

- **Rollback:** restores files git doesn't track, too.
- **Audit log:** tamper-evident, hash-chained.
- **Narrower filesystem view inside the container:** for example, the
  harness's config directory read-only except for the files it must write.

## 5. Running nono inside the container

Only this direction makes sense. If nono wraps `aishell claude` on the host, it
constrains the aishell CLI, not the container Docker starts, and nono's
profiles block the `docker` command anyway. The workable order is:

```
aishell entrypoint (root) → gosu (drop to the dev user) → nono run --profile … -- <harness>
```

What was checked:

- **Docker allows Landlock:** Docker's default seccomp profile
  (`moby/profiles`, `seccomp/default.json`) allows `landlock_add_rule`,
  `landlock_create_ruleset` and `landlock_restrict_self` with no kernel-version
  condition.
- **Probe result:** inside an aishell container (`/.dockerenv` present) on a
  Linux 7.0 host, `syscall(444, NULL, 0, LANDLOCK_CREATE_RULESET_VERSION)`
  returned **ABI 8**. That is more than nono's target ABI 5 and the ABI 4 its
  per-port rules need. It is still below ABI 10, so Landlock leaves UDP
  unrestricted there.

Caveats:

- **Docker Desktop is untested:** on macOS the container kernel is LinuxKit; on
  WSL2 it is 6.6 today, and per-port rules need 6.7+. In-container network
  filtering may therefore be Linux-only, which conflicts with the platform
  promise in `docs/ARCHITECTURE.md`. Filesystem rules need only 5.13 and should
  work more widely.
- **Not run:** nono has not been installed or run in the container. The next
  step is a smoke test such as `nono run --allow-cwd -- ls` in a throwaway
  container, then the same with a harness profile.

## Implications for aishell

- **Keep the sidecar:** ADR 0006 still holds. nono doesn't replace Docker or
  the sidecar, and its network control on its own is weaker than the planned
  `--internal` bridge.
- **Optional second layer:** nono could become an opt-in layer inside the
  sandbox, launched by the entrypoint after `gosu`, chained to the egress proxy
  through `upstream_proxy`.
- **Smaller pieces to borrow:** rollback and the audit log are the parts aishell
  lacks. They could be adopted without nono's network or credential
  features.

## Sources

Consulted 2026-10-07:

- https://nono.sh — overview, install, CLI examples
- https://nono.sh/os-sandbox — mechanisms, kernel requirements, WSL2 limits
- https://nono.sh/network-filtering — proxy, port restriction, metadata
  denylist, `upstream_proxy`
- https://nono.sh/credential-injection — reverse proxy and keystore-backed keys
- https://nono.sh/undo — rollback snapshots
- https://github.com/nolabs-ai/nono — repository, maturity, license
- https://docs.kernel.org/userspace-api/landlock.html — Landlock ABI history
  and network rule structure
- https://raw.githubusercontent.com/moby/profiles/main/seccomp/default.json —
  Docker default seccomp profile
- https://docs.docker.com/engine/security/seccomp/ — Docker seccomp docs
- `docs/adr/0006-harden-on-docker-with-an-egress-proxy.md` and the
  `aix-01m1203*` tickets in `.tickets/`
