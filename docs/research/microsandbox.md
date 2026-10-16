# microsandbox: microVM sandboxes, compared with aishell

Research notes on [microsandbox](https://github.com/superradcompany/microsandbox)
and whether it could back aishell in place of Docker, or sit beside it.
Gathered on 2026-10-16 from a clone of the repository (docs, source, changelog),
the GitHub API (releases, open issues) and libkrun's README. Doc and source
links below point at the `main` branch, which is 25 commits past the `v0.7.8`
tag; where a feature is newer than `v0.7.8` the text says so. microsandbox was
not installed or run, so nothing here was tested on a live sandbox. Its
maintainers call it beta software, so re-check the details before building on
them.

## 1. What microsandbox is

- A Rust CLI (`msb`) and SDKs (Rust, TypeScript, Python, Go, Ruby) that run each
  sandbox as a microVM with its own Linux kernel. License Apache-2.0, about 8.7k
  GitHub stars, latest release `v0.7.8` on 2026-10-09
  ([repo](https://github.com/superradcompany/microsandbox),
  [releases](https://github.com/superradcompany/microsandbox/releases),
  [LICENSE](https://github.com/superradcompany/microsandbox/blob/main/LICENSE)).
- The VMM is libkrun, a library that uses KVM on Linux and Hypervisor.framework
  on macOS/ARM64 ([libkrun README](https://github.com/containers/libkrun/blob/main/README.md)).
  microsandbox ships its own libkrunfw kernel fork
  ([`.gitmodules`](https://github.com/superradcompany/microsandbox/blob/main/.gitmodules),
  [isolation](https://github.com/superradcompany/microsandbox/blob/main/docs/security/isolation.mdx)).
- No daemon: the SDK or CLI spawns a per-sandbox host process that embeds the
  VMM, a user-space network stack, the filesystem broker and secret injection
  ([SDK overview](https://github.com/superradcompany/microsandbox/blob/main/docs/sdk/overview.mdx),
  [security overview](https://github.com/superradcompany/microsandbox/blob/main/docs/security/overview.mdx)).
- Images are standard OCI images pulled from any registry. A guest agent
  (`agentd`) runs as PID 1 and takes commands from the host over virtio-console
  ([isolation](https://github.com/superradcompany/microsandbox/blob/main/docs/security/isolation.mdx)).
- It also offers a paid cloud backend. This note covers the local backend only.

## 2. How it differs from aishell

| | aishell | microsandbox |
|---|---|---|
| Boundary | Docker container (shared host kernel) | microVM (own kernel, hardware hypervisor) |
| Image source | Dockerfile chain built with `docker build` | OCI image pulled or loaded; no Dockerfile build in `msb` |
| Project mount | Bind mount at the host path | virtio-fs mount at any guest path (section 4) |
| Interactive session | `docker run -it`, `aishell attach` is `docker exec` | `msb exec -t`, `msb attach --job` (section 3) |
| Network control | Planned squid sidecar (ADR 0006) | Built-in policy engine in the host process (section 6) |
| Secret broker | Planned for API-key harnesses | Built-in placeholder substitution (section 5) |
| Platforms | Linux, macOS, Windows through Docker | Linux with KVM, Apple Silicon macOS, Windows with WHP (preview) |

## 3. Interactive TTY, resize, detach and reattach

- **Real PTY:** `msb exec -t` allocates a pseudo-terminal. When stdin is a
  terminal the CLI picks attach mode on its own; `--no-tty` forces pipes
  ([sandbox commands](https://github.com/superradcompany/microsandbox/blob/main/docs/cli/sandbox-commands.mdx)).
  `msb run -t` does the same for a fresh sandbox, and the Claude Code and Codex
  guides use it ([claude-code.mdx](https://github.com/superradcompany/microsandbox/blob/main/docs/how-to/agents/claude-code.mdx)).
- **Resize:** the protocol has an `ExecResize` message
  ([`message.rs`](https://github.com/superradcompany/microsandbox/blob/main/crates/protocol/lib/message.rs)),
  and the SDK's attach loop listens for SIGWINCH and sends it
  ([`attach.rs` lines 326 and 433](https://github.com/superradcompany/microsandbox/blob/main/sdk/rust/lib/sandbox/attach.rs)).
  Whether the CLI's `msb exec -t` goes through that same code path is
  unverified.
- **Detach and reattach:** documented for background jobs. `msb exec -d --tty
  <sandbox> -- sh` starts a job, `msb attach <sandbox> --job ID` reconnects, and
  Ctrl-] detaches without stopping it. One attachment owns input at a time,
  others use `--read-only`, and an abrupt disconnect releases input within 30
  seconds
  ([commands.mdx](https://github.com/superradcompany/microsandbox/blob/main/docs/sandboxes/commands.mdx)).
  Limits: 32 active jobs per sandbox, and only two 512 KiB output segments are
  kept per job. Whether a plain foreground `msb exec -t` also detaches on
  Ctrl-] is unverified for the CLI; the SDK `attach` documents it.
- **Sessions survive in the VM:** a named sandbox keeps running after the client
  exits (`msb create --name`, then `msb exec`). So `tmux` inside the guest works
  the way it does in aishell today, and `msb exec -t <name> -- tmux attach` is
  the analogue of `aishell attach`. This is an inference from the model, not a
  documented tmux recipe.
- **Open issue:** #1562 notes interactive sessions are always captured into
  `exec.log`, with no opt-out yet
  ([issue](https://github.com/superradcompany/microsandbox/issues/1562)). That
  log would contain terminal output, including anything the agent prints.

## 4. Host directory mounts, filesystem and ownership

- **Same absolute path:** the guest path is free-form. `--mount-dir
  SRC:DEST[:OPTIONS]` accepts any absolute `DEST`, so `$PWD:$PWD` should work.
  The docs only show `/app`-style targets, so a deep host path such as
  `/home/user/projects/x` is untested
  ([volumes.mdx](https://github.com/superradcompany/microsandbox/blob/main/docs/sandboxes/volumes.mdx)).
- **Mechanism:** virtio-fs, served by microsandbox's own passthrough filesystem
  in the host process (`PassthroughFs`), not by `virtiofsd`
  ([security/filesystem.mdx](https://github.com/superradcompany/microsandbox/blob/main/docs/security/filesystem.mdx),
  [`crates/filesystem/README.md`](https://github.com/superradcompany/microsandbox/blob/main/crates/filesystem/README.md)).
  Named directory volumes use virtiofs too; disk volumes are ext4 over virtio-blk.
- **Containment:** on Linux 5.6+ lookups use `openat2` with `RESOLVE_BENEATH`;
  on older kernels and macOS they fall back to `O_NOFOLLOW`, which the docs call
  weaker. Read-only mounts are enforced on the host side
  ([security/filesystem.mdx](https://github.com/superradcompany/microsandbox/blob/main/docs/security/filesystem.mdx)).
  libkrun itself gives no protection against a guest reaching other directories
  on a shared virtio-fs device ([libkrun README](https://github.com/containers/libkrun/blob/main/README.md)).
- **Ownership:** by default (`stat-virt=strict`, `host-perms=private`) the guest
  sees "sandbox-local" ownership. Guest uid, gid and mode are stored in an
  extended attribute on the host file, and host inode ownership is not changed.
  A mount can instead present a fixed `uid=N,gid=N` to the guest, which changes
  only the guest's view
  ([volumes.mdx, "Fallback guest ownership"](https://github.com/superradcompany/microsandbox/blob/main/docs/sandboxes/volumes.mdx),
  [`crates/filesystem/README.md`](https://github.com/superradcompany/microsandbox/blob/main/crates/filesystem/README.md)).
  Strict mode requires xattr support on the host filesystem and fails closed
  otherwise ([Rust volumes reference](https://github.com/superradcompany/microsandbox/blob/main/docs/sdk/rust/volumes.mdx)).
- **Consequence for aishell:** aishell creates a user matching the host
  uid/gid so files land with correct ownership. Under microsandbox files
  created by the guest appear on the host owned by the host user (the host
  inode is unchanged), and the guest user can be pinned with `uid`/`gid`
  options. So uid matching looks simpler, but a file's guest-side owner is
  metadata in an xattr that tools on the host do not see. Unverified: how git on
  the host and git in the guest agree about `safe.directory` and ownership.
- **Single files:** `--mount-file SRC:DEST` binds one host file through a
  one-entry filesystem, which fits `~/.gitconfig`
  ([volumes.mdx](https://github.com/superradcompany/microsandbox/blob/main/docs/sandboxes/volumes.mdx)).
- **Performance:** no benchmarks in the docs for mounts. Relevant open issues:
  #1701 (first `statfs` on a bind mount walks the whole host tree), #1702 (one
  slow request on a virtio-fs mount blocks every other request on it) and
  #1567 (macOS bind mounts on FSKit filesystems such as exFAT fail with ENOENT)
  ([issues](https://github.com/superradcompany/microsandbox/issues)). Large
  repositories with many small files are the risky case; treat mount speed as
  unmeasured.

## 5. Credentials, OAuth and "secrets never enter the VM"

### Harness config and OAuth

- The official Claude Code and Codex guides do not mount host config. They
  install the harness inside the sandbox, log in interactively (copy the login
  URL to a host browser if none opens), and leave auth, settings and sessions on
  the sandbox's root disk, which is removed with the sandbox
  ([claude-code.mdx](https://github.com/superradcompany/microsandbox/blob/main/docs/how-to/agents/claude-code.mdx),
  [codex.mdx](https://github.com/superradcompany/microsandbox/blob/main/docs/how-to/agents/codex.mdx)).
- Mounting `~/.claude` or `~/.codex` from the host is possible with `--mount-dir`
  (or `--mount-file`), as plain files. Nothing in the docs covers it, so it is
  untested here. OAuth tokens in those files are real credentials inside the
  guest.
- Env passthrough: `-e KEY=VALUE` sets guest environment variables, which the
  guest can read. The secret feature below is the alternative.
- OAuth is not brokered. The docs mention no OAuth support; secrets are
  header, query and body substitution for a value you supply. A refreshing
  OAuth token pair that the harness writes itself cannot be held back from the
  guest.

### Secret substitution

- The guest environment gets a placeholder (`$MSB_<NAME>` by default). On egress
  to an allowed host, the host-side stack decrypts the intercepted TLS,
  verifies the destination, swaps in the real value and forwards the request
  ([secrets.mdx](https://github.com/superradcompany/microsandbox/blob/main/docs/sandboxes/secrets.mdx),
  [security/secrets.mdx](https://github.com/superradcompany/microsandbox/blob/main/docs/security/secrets.mdx)).
- A substitution happens only if the SNI matches an allowed host, the
  destination IP was resolved for that host through the interceptor (the "DNS
  pin"), TLS is intercepted, and the HTTP `Host`/`:authority` matches the SNI.
  Headers are on by default; query and body are opt-in; HTTP/2 body
  substitution is unsupported.
- Limits the docs state themselves:
  - The allowed endpoint receives the real value and could echo it back.
  - Bypassed TLS (pinned clients) gets no substitution.
  - A placeholder cannot replace a credential the guest uses for local signing.
  - Real values sit in host process memory, and a raw value passed through an
    SDK is persisted in the host-side sandbox config. The CLI keeps only an
    environment reference.
- Fit for aishell's planned API-key broker: good, because it is built in and
  needs no keystore service. Fit for OAuth: none. Interception adds a CA to the
  guest trust store; whether Node-based harnesses pick it up through the system
  store is unverified.

## 6. Network allowlists

- **Enforcement point:** a user-space network stack in the per-sandbox host
  process terminates every packet and checks it against policy. There is no
  host routing or NAT in the path
  ([security/network.mdx](https://github.com/superradcompany/microsandbox/blob/main/docs/security/network.mdx)).
- **Default:** public internet only. Private ranges, loopback, link-local, cloud
  metadata and the host are denied. `localhost` inside the guest stays inside it.
- **Granularity:** rules take the form `action[:direction]@target[:proto[:ports]]`.
  Targets are an IP or CIDR, an exact domain, a domain suffix (`*.example.com`,
  at least two labels), or groups (`public`, `private`, `loopback`, `link-local`,
  `meta`, `multicast`, `host`, `any`). Ports are a single port, a range or any;
  protocols are tcp, udp and icmp. First match wins
  ([sandbox commands](https://github.com/superradcompany/microsandbox/blob/main/docs/cli/sandbox-commands.mdx)).
  The README shows the short form `network: allow: [api.github.com]`.
- **Hostname rules** match on DNS answers plus TLS SNI (and the HTTP authority
  when intercepted). Hostname-only allows fail closed for non-intercepted HTTPS
  by default (`--net-strict`), so a plain allowlist of `api.anthropic.com`
  usually needs TLS interception on.
- **DNS:** handled on the host. Denied names get a local `NXDOMAIN`; private
  answers are rewritten to `NXDOMAIN` unless an address rule allows them (rebind
  protection). Tunneled DNS and DoH over 443 cannot be distinguished from other
  traffic, and the docs advise restricting egress to an allowlist
  ([dns.mdx](https://github.com/superradcompany/microsandbox/blob/main/docs/networking/dns.mdx)).
- **Upstream proxy:** `--proxy` chains egress through HTTP CONNECT, SOCKS4 or
  SOCKS5, so a corporate proxy can sit behind it.
- Compared with ADR 0006's squid sidecar, this is the same shape (policy outside
  the guest) without a second container, and it covers UDP and non-HTTP TCP.

## 7. Platform requirements and rootless operation

| Host | Requirement | Source |
|---|---|---|
| Linux | glibc 2.28+, KVM through `/dev/kvm`, user in the `kvm` group or an ACL; musl-only hosts (bare Alpine) are not supported | [troubleshooting/linux.mdx](https://github.com/superradcompany/microsandbox/blob/main/docs/troubleshooting/linux.mdx) |
| macOS | Apple Silicon only; Intel Macs unsupported | [troubleshooting/macos.mdx](https://github.com/superradcompany/microsandbox/blob/main/docs/troubleshooting/macos.mdx) |
| Windows | Windows 10+, x64 or ARM64, Windows Hypervisor Platform feature (`HypervisorPlatform`, separate from the one WSL2 and Docker Desktop enable); marked preview | [troubleshooting/windows.mdx](https://github.com/superradcompany/microsandbox/blob/main/docs/troubleshooting/windows.mdx) |

- **Rootless:** the per-sandbox process runs as the launching user, with no
  setuid and no elevated capabilities. Linux only needs access to `/dev/kvm`
  ([isolation](https://github.com/superradcompany/microsandbox/blob/main/docs/security/isolation.mdx)).
- **Nested virtualization:** needed wherever `/dev/kvm` is absent, for example
  inside a cloud VM or CI runner. The docs say many hosted runners do not
  expose it. Running `msb` inside a container needs `--device /dev/kvm`; Docker
  Desktop only works if its VM exposes nested virtualization
  ([how-to/docker/docker.mdx](https://github.com/superradcompany/microsandbox/blob/main/docs/how-to/docker/docker.mdx)).
- **Open issue:** #1778 reports that in 0.7.7 guests no longer see `vmx` or
  `/dev/kvm`, a nested virtualization regression from 0.7.6, so running Docker
  or KVM inside the guest is shaky
  ([issue](https://github.com/superradcompany/microsandbox/issues/1778)).
- **Docker Desktop and WSL2 users of aishell:** WSL2 hosts need nested
  virtualization for KVM, which is unverified here. The platform promise in
  `docs/ARCHITECTURE.md` (anywhere Docker runs) is wider than microsandbox's.
- **Kernel supply:** the guest kernel is microsandbox's own, so the host
  kernel version matters for `openat2` containment (5.6+) but not for guest
  features such as Landlock.

## 8. Reusing the Dockerfile image chain

- **No image build.** `msb` pulls and runs OCI images; it has no
  `Dockerfile`-based build command. The documented route for local images is
  `docker build`, then `docker save IMG | msb load`, or push to a local registry
  and `msb pull --insecure`. `msb load` also imports OCI layouts produced by
  BuildKit, Skopeo or ORAS
  ([local-images.mdx](https://github.com/superradcompany/microsandbox/blob/main/docs/how-to/docker/local-images.mdx),
  [image commands](https://github.com/superradcompany/microsandbox/blob/main/docs/cli/image-commands.mdx)).
  So a backend would still need Docker, BuildKit or another builder to produce
  `aishell:foundation`, `aishell:base` and `aishell:ext-{hash}`. It cannot
  remove the Docker dependency for building.
- **Cache:** pulled layers are stored once and shared; each sandbox gets a
  writable layer. A `--root-disk flat:` mode clones a full ext4 per sandbox
  ([image commands](https://github.com/superradcompany/microsandbox/blob/main/docs/cli/image-commands.mdx),
  [optimization.mdx](https://github.com/superradcompany/microsandbox/blob/main/docs/sandboxes/optimization.mdx)).
  aishell's hash-based cache invalidation would need to track the loaded copy
  separately from Docker's.
- **Image behavior inside a VM:** the image's `ENTRYPOINT` and `CMD` are
  honored (`--entrypoint` overrides). aishell's entrypoint creates a user and
  drops privileges with gosu; that script should run as in a container, since
  the guest runs commands as root by default and `msb exec --user` can select
  another user. Unverified: the foundation image running unmodified.
- **Harness volume:** aishell mounts a Docker volume at `/tools`. The
  equivalent is a named directory volume (virtiofs) with `ro`, which exists
  ([volumes.mdx](https://github.com/superradcompany/microsandbox/blob/main/docs/sandboxes/volumes.mdx)).
- **Architecture:** images must match the host CPU architecture (Apple Silicon
  means arm64 guests). Whether aishell's foundation image builds for both is not
  checked here.
- **Sensitive-file detection and gitleaks:** both run on the host before
  launch, so they do not depend on the container runtime.

## 9. Maturity

- **Version and cadence:** `v0.7.8`, 2026-10-09. Releases: 0.7.0 on 2026-09-16
  and 0.7.8 on 2026-10-09 (nine releases in 23 days); 0.6.0 on 2026-06-27 through
  0.6.18 on 2026-09-09. The GitHub release flag is not "prerelease", but the
  README warns: "still beta software. Expect breaking changes, missing
  features, and rough edges"
  ([releases](https://github.com/superradcompany/microsandbox/releases),
  [README](https://github.com/superradcompany/microsandbox/blob/main/README.md)).
- **Breaking changes:** the 0.6 to 0.7 migration needs 0.7.3 or later, because
  earlier 0.7 releases have compatibility gaps. Saved sandboxes are migrated in
  place, but an older SDK or CLI sharing the same `MSB_HOME` can fail once the
  database is upgraded
  ([migrations/v0.7.mdx](https://github.com/superradcompany/microsandbox/blob/main/docs/migrations/v0.7.mdx)).
  `COMPATIBILITY.md` lists the protocol, database and cache boundaries and
  states that public CLI and SDK compatibility is explicitly out of its scope
  ([COMPATIBILITY.md](https://github.com/superradcompany/microsandbox/blob/main/COMPATIBILITY.md)).
  Flags are being renamed (`--forked` to `--cow-mem`, `fork` over `branch`).
- **Open issues:** 106 open at the time of writing. Relevant ones are listed
  in sections 3, 4 and 7.
- **Platform maturity:** Windows is labeled preview.
- **Security posture:** a security policy and private reporting exist
  ([SECURITY.md](https://github.com/superradcompany/microsandbox/blob/main/SECURITY.md)).
  The docs say image signatures and attestations are not checked, only digests.
  Workloads run as root in the guest by default.

## 10. Integration options and what a backend abstraction needs

### CLI from Babashka

- `msb` is a single binary with documented JSON output (`--format json`) on
  several commands, stdout/stderr separation, and exit codes
  ([CLI overview](https://github.com/superradcompany/microsandbox/blob/main/docs/cli/overview.mdx)).
  That suits `babashka.process`.
- aishell's Docker calls map roughly as follows:

| aishell step | Docker today | microsandbox |
|---|---|---|
| Build images | `docker build` | Still Docker or BuildKit, then `msb load` |
| Start | `docker run -it --rm -v ...` | `msb run -t --mount-dir ... --workdir ...` |
| Attach | `docker exec -it` | `msb exec -t NAME -- ...` or `msb attach --job` |
| Stop and clean up | `--rm`, `docker stop` | `msb stop`, `msb rm` (unnamed sandboxes are removed when the command exits) |
| Extra args | `docker_args` passthrough | No equivalent; per-flag translation |
| Resource limits | `--memory`, `--cpus` | `--memory`, `--cpus` |

- **SDK:** Rust, Node, Python, Go and Ruby only. Babashka cannot call them, so
  an SDK route would mean a helper process, which gives no advantage over the
  CLI.
- **Work a backend seam needs in aishell:** a Docker-neutral interface for
  build/load, run, exec/attach, list, stop and image-exists checks;
  translation of `config.yaml` mounts, `docker_args`, environment, and harness
  config mounts; the attach and VSCode integrations (the latter uses Docker
  container attach and has no microsandbox analogue); and `docker_args`
  validation, which would need a separate allowlist. The platform matrix and
  nested-virtualization failures need a clear doctor message (`msb doctor`
  exists).

## Implications for aishell

- **Could it replace Docker?** Not today, and not alone. Docker is still needed
  to build the image chain, the platform reach is narrower, there is no
  `docker_args` equivalent, and the project is beta with fast-moving flags.
  Mount speed, `tmux` attach and OAuth config mounts are unverified in practice.
- **What it would add:** a hardware boundary (own kernel), a built-in egress
  policy with DNS pinning, and a secret broker for API keys, which cover the
  ADR 0006 tickets for network modes and the API-key broker without a squid
  sidecar. It does not help OAuth.
- **Best fit:** an opt-in second backend on Linux and Apple Silicon hosts for
  users who want VM isolation, with Docker kept for builds and for everyone
  else. It complements aishell more than it replaces Docker.

## Verdict

Treat microsandbox as a promising optional backend, not a Docker replacement.
It matches aishell on the hard requirements it documents (real PTY, resize
support, same-path mounts, OCI images, rootless operation) and beats the
planned Docker hardening on isolation and egress control. It loses on
platform coverage, maturity, a missing image build step, and the unmeasured
cost of virtio-fs for large repositories.

## Recommended next steps

1. **Smoke test on a KVM Linux host:** run `aishell:foundation` through
   `docker save | msb load`, then `msb run -t --mount-dir "$PWD:$PWD" --workdir
   "$PWD"`, and check that the entrypoint, gosu and a harness start.
2. **Measure mounts:** time `git status` and a test suite on a large repo
   through the virtio-fs mount against Docker bind mounts, and check #1701 and
   #1702 on the target version.
3. **Check OAuth and ownership:** mount `~/.claude` read-write, confirm login
   persistence and token refresh work, and confirm git on both sides accepts the
   workspace (`safe.directory`, uid).
4. **Check attach:** `msb exec -t` with Ctrl-] detach, `msb attach --job`, and
   `tmux` inside the guest; confirm resize on a live window change.
5. **Try the network policy:** allow only the harness API host with TLS
   interception on, and test that Node-based harnesses trust the injected CA.
6. **Decide on the seam:** if steps 1 to 5 hold, write an ADR for a
   backend interface (build, run, exec, attach, stop) before any code, and
   re-check microsandbox after it leaves its 0.x line.

## Sources

Consulted 2026-10-16:

- https://github.com/superradcompany/microsandbox — repository, README, LICENSE,
  COMPATIBILITY.md, SECURITY.md, `.gitmodules`
- https://github.com/superradcompany/microsandbox/releases — release dates
- https://github.com/superradcompany/microsandbox/issues — #1562, #1567, #1701,
  #1702, #1778 and the open issue count
- `docs/` in the repository, published at https://docs.microsandbox.dev:
  `cli/sandbox-commands.mdx`, `cli/image-commands.mdx`, `cli/overview.mdx`,
  `sandboxes/commands.mdx`, `sandboxes/volumes.mdx`, `sandboxes/secrets.mdx`,
  `sandboxes/optimization.mdx`, `security/*.mdx`, `networking/dns.mdx`,
  `troubleshooting/{linux,macos,windows}.mdx`, `how-to/agents/{claude-code,codex}.mdx`,
  `how-to/docker/{docker,local-images}.mdx`, `migrations/v0.7.mdx`,
  `sdk/overview.mdx`, `sdk/rust/volumes.mdx`
- `crates/filesystem/README.md`, `crates/protocol/lib/message.rs`,
  `sdk/rust/lib/sandbox/attach.rs` in the repository
- https://github.com/containers/libkrun/blob/main/README.md — KVM and
  Hypervisor.framework support, virtio-fs security note
- `docs/ARCHITECTURE.md`, `README.md`, `docs/CONFIGURATION.md` and
  `docs/adr/0006-harden-on-docker-with-an-egress-proxy.md` in this repository
