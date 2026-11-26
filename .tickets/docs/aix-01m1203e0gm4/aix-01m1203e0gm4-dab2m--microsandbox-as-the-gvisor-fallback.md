---
id: aix-01m1203e0gm4-dab2m
ticket: aix-01m1203e0gm4
title: microsandbox as the gVisor fallback
type: other
created: '2026-11-26T13:38:04.245929388Z'
updated: '2026-11-26T13:38:04.245929388Z'
---

# microsandbox as the gVisor fallback

What the fallback branch of this spike needs to know about microsandbox (github.com/superradcompany/microsandbox), a microVM sandbox on libkrun. Gathered 2026-10-16 from the repository at `main` (25 commits past `v0.7.8`), the GitHub API and libkrun's README. It was not installed or run; its maintainers call it beta. Trimmed from aishell@530a0131:docs/research/microsandbox.md, which links every doc page and issue cited here.

## What it is

- A Rust CLI (`msb`) and SDKs that run each sandbox as a microVM with its own kernel (libkrun on KVM or Hypervisor.framework). Apache-2.0. No daemon: a per-sandbox host process embeds the VMM, a user-space network stack, the filesystem server and secret injection.
- Runs standard OCI images; a guest agent is PID 1. The image's `ENTRYPOINT` is honoured, so aishell's entrypoint (useradd, gosu) should run, but the Foundation image running unmodified is unverified.

## Host requirements

- Linux: glibc 2.28+, `/dev/kvm` with the user in the `kvm` group. Rootless, no setuid.
- macOS: Apple Silicon only. Windows: WHP feature, preview.
- No `/dev/kvm` means nested virtualization is needed (cloud VMs, CI, Docker Desktop, WSL2). Issue #1778: 0.7.7 lost `vmx` in guests.

## The checks the spike repeats

- **Image:** no Dockerfile build. `docker build`, then `docker save IMG | msb load` (or a local registry). Docker stays for builds. Images must match the host architecture.
- **Same-path mount:** `--mount-dir SRC:DEST[:OPTIONS]` takes any absolute DEST, so `$PWD:$PWD` should work; deep host paths are undocumented. virtio-fs served by microsandbox's own passthrough filesystem; `openat2` containment on Linux 5.6+.
- **Ownership:** by default guest uid/gid/mode live in an xattr on the host file and the host inode is unchanged; a mount can pin `uid=N,gid=N`. Strict mode needs xattr support and fails closed. Unverified: git `safe.directory` on both sides.
- **File visibility and speed:** no mount benchmarks. Open issues #1701 (first `statfs` walks the host tree) and #1702 (one slow request blocks the mount). Large repos with many small files are the risk.
- **Claude Code via OAuth:** the official guides log in inside the guest and keep auth on the sandbox disk. Mounting `~/.claude` with `--mount-dir` is possible but untested. OAuth is not brokered.
- **TTY and attach:** `msb exec -t` gives a PTY; the protocol has a resize message. `msb exec -d --tty` plus `msb attach --job ID` detach and reattach (Ctrl-]). A named sandbox outlives the client, so `msb exec -t NAME -- tmux attach` is the analogue of `aishell attach` (inferred). Issue #1562: interactive output always goes to `exec.log`.
- **Single files:** `--mount-file SRC:DEST` fits `~/.gitconfig`. The `/tools` harness volume maps to a named virtiofs volume with `ro`.
- **Diagnostics:** `msb doctor`.

## What it would add beyond gVisor

- A built-in egress policy in the host process: rules by IP, CIDR, domain or suffix, port and protocol, host-side DNS with rebind protection, default deny of private ranges and metadata. Hostname-only allows need TLS interception for HTTPS; whether Node harnesses trust the injected CA is unverified.
- Placeholder secret substitution for API keys on egress to allowed hosts. Useless for OAuth.

## Limits against aishell

- No `docker_args` equivalent; each flag needs translation. The VSCode attach has no analogue.
- Narrower platform reach than "anywhere Docker runs".
- 0.x with fast-moving flags; the 0.6 to 0.7 migration needed 0.7.3+, and CLI compatibility is outside its compatibility policy.

## Verdict at the time

A promising opt-in second backend on Linux and Apple Silicon, not a Docker replacement. If the spike's checks pass, write an ADR for a backend interface (build/load, run, exec, attach, stop) before any code.
