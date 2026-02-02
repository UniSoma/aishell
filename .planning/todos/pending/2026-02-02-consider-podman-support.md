---
created: 2026-02-02T00:17
title: Consider Podman support as alternative container runtime
area: cli
files:
  - src/harness/docker.clj
  - src/harness/docker/run.clj
  - src/harness/docker/build.clj
  - src/harness/docker/volume.clj
---

## Problem

aishell is currently hard-coupled to Docker Engine as its only container runtime. The Distrobox investigation (artifacts/investigate/20260202-0006-distrobox-as-aishell-container-backend/) concluded that while Distrobox itself is a poor fit (design philosophy conflict, macOS support loss), direct Podman support could provide meaningful benefits:

- **Rootless containers**: Podman runs without a daemon and without root, improving security posture
- **Docker CLI compatibility**: Podman's CLI is largely compatible with Docker's, reducing migration effort
- **No daemon dependency**: Eliminates the Docker daemon as a single point of failure
- **WSL2 story**: Podman works well in WSL2 without Docker Desktop licensing concerns

The investigation estimated 40-50% of the codebase touches Docker primitives, but much of that uses standard CLI commands that Podman supports identically.

## Solution

TBD — needs its own investigation/planning. Possible approaches:

1. **Runtime abstraction layer**: Thin interface over `docker`/`podman` CLI commands, auto-detect available runtime
2. **Podman-first with Docker fallback**: Since Podman is Docker-compatible, test with Podman and document Docker as alternative
3. **Configuration option**: Let users choose runtime in global config (`runtime: docker|podman`)

Key questions to resolve:
- Does `podman build` handle all aishell's Dockerfile patterns?
- Does `podman volume` behave identically for harness volumes?
- How does rootless Podman affect UID/GID mapping in the entrypoint?
- macOS Podman (via podman-machine) — is it reliable enough?
