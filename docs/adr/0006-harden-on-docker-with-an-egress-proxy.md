# Harden on Docker, with an egress proxy as the network control

aishell keeps Docker as its sandbox primitive and closes its isolation gaps
from inside Docker's own flags: a **security profile** that drops
capabilities and privilege escalation, and a **network mode** enforced by an
aishell-managed **egress proxy** sidecar on an internal network. Stronger
primitives (gVisor, microsandbox) are opt-in runtimes, never the default.
OAuth tokens stay inside the sandbox; the network control is what limits the
damage of their theft.

## Context

The sandbox today is `docker run` with a bind-mounted project on the default
bridge. That gives the harness — and anything it installs or runs — the host's
LAN, the Docker gateway, cloud metadata endpoints, real API keys in its
environment, `sudo`, and no resource caps. A prompt injection needs no exploit
to exfiltrate a token or reach a local database; `docker_args` can undo any
restriction with only a warning.

The threat model this decision serves, in priority order: credential theft,
LAN and local-service reach, host files outside the project, damage to the
project itself, and last, kernel escape. The adversary is arbitrary code
running with the harness's privileges (a steered harness, a postinstall
script, a compromised harness package). Multi-tenant hosts are out of scope.
The two harnesses in daily use (Claude Code, Codex) authenticate with OAuth
tokens on disk, not API keys; the platform promise in `docs/ARCHITECTURE.md`
(Linux, macOS and Windows/WSL2 from one codebase) still holds.

## Considered Options

- **Harden on Docker (chosen).** Everything expressed as `docker run` flags
  or as a second container is portable to every Docker Desktop backend.
  Kernel escape ranks last in the threat model, so the shared kernel is not
  the problem worth changing the primitive for.

- **Egress proxy sidecar, not a host firewall.** Every network mode except
  `open` and `none` attaches the sandbox to an `--internal` bridge whose only
  exit is a squid sidecar, reached through `HTTP_PROXY`/`HTTPS_PROXY`. The
  proxy resolves DNS itself, so `restricted` can refuse loopback, RFC1918,
  link-local and metadata addresses by *resolved* IP (DNS rebinding does not
  help), and `allowlist` can deny by default. Tools that ignore the proxy
  variables fail closed because the internal network has no route out.
  iptables rules on `DOCKER-USER` would be simpler on Linux and impossible on
  Docker Desktop; rejected on the platform promise. tinyproxy filters by name
  only and cannot enforce `restricted`; rejected. mitmproxy is what a
  secret broker would need, but TLS termination is more than the network
  control requires; deferred to that work.

- **OAuth tokens accepted in-guest.** Brokering a secret through a
  TLS-terminating sidecar works for API keys behind a base URL, and is
  planned for the API-key harnesses. It does not fit OAuth: Claude Code and
  Codex manage login state and token refresh from files they read
  themselves. With egress control a stolen OAuth token can only be used
  toward allowed hosts, from inside the sandbox — it can spend the
  subscription, not leave. That is the accepted residual risk; brokering
  OAuth is a feasibility spike, not a commitment.

- **Opt-in profiles now, secure defaults later.** `strict` (`--cap-drop ALL`
  plus the minimum the entrypoint needs to `usermod`/`chown`/`gosu`,
  `no-new-privileges`, `--pids-limit`, `network: allowlist`) breaks `apt`
  flows, phone-home tools and in-sandbox `sudo` (`no-new-privileges` neuters
  setuid, so the binary can stay). `default` carries only the changes that
  break nothing. Headless runs from the orchestration layer start on
  `strict`: full autonomy with nobody watching is where the risk sits. The
  interactive default moves to `restricted` once the allowlists have been
  dogfooded. Memory and CPU caps are user-set keys with no profile default;
  OOM-killing a build is worse than no cap.

- **Security checks are hardcoded and additive.** `validation.clj` keeps its
  patterns in code; under a profile, `docker_args` that touch
  security-relevant flags (`--privileged`, `--cap-add`, `--security-opt`,
  `--network`, `--runtime`, `docker.sock`) are refused rather than warned
  about. The only override is an explicit CLI flag that prints what it
  disabled. Config can add restrictions, never relax them — a check a config
  file can switch off protects nothing against code that can write config
  files. This settles ticket `aix-01kr1d764vty`.

- **gVisor as an opt-in `runtime`, microsandbox as its fallback.** `runsc`
  keeps images, the three-tier build, bind mounts and the entrypoint
  unchanged for a `--runtime` flag, needs no KVM, and is GA. microsandbox
  gives a true VM boundary and removes root dockerd, but replaces the whole
  primitive (no Dockerfile, virtio-fs, beta). Both are Linux-only, so
  neither can be selected by a profile: `runtime` is its own key, and
  `aishell check` reports availability. The spike runs gVisor first;
  microsandbox only if gVisor fails its exit criteria.

## Consequences

- Two new sandbox concepts enter the vocabulary: security profile and network
  mode, plus the egress proxy that implements the latter (see `CONTEXT.md`).
- One squid container per host, started on demand and shared by sandboxes;
  its lifecycle becomes aishell's responsibility, including on Docker Desktop.
- Harness descriptors gain a default allowlist (API hosts, telemetry, package
  registries the harness itself pulls from). Every harness and package
  manager in the foundation image must be verified to honor proxy variables;
  those that do not simply have no network under `restricted`/`allowlist`.
- `docker_args` stops being an all-purpose escape hatch. Users who relied on
  it for `--network host` or `--privileged` need the CLI override, which is
  loud on purpose.
- The entrypoint's root-then-`gosu` sequence keeps `CHOWN`, `SETUID`,
  `SETGID`, `FOWNER`, `DAC_OVERRIDE` even under `strict`; removing them means
  a different identity scheme (userns remap), which is not part of this
  decision.
- The `Sandbox` term stays "the Docker container". If the gVisor spike ever
  leads to a non-Docker backend, that is a new ADR and a glossary change.
