---
id: aix-01m8ce7kxcxe
title: Refuse mounts that reach the daemon socket by resolved path
status: open
type: bug
priority: 1
mode: hitl
created: '2026-11-26T17:25:12.748324614Z'
updated: '2026-11-26T17:25:12.748324614Z'
tags:
- security
- needs-triage
---

## Description

The docker.sock refusal from aix-01m1203dkknf (commit a105ac4) matches the text `docker.sock` in a docker_args token or a mounts source. The cold skeptic on that landing showed ways past it, each giving the Sandbox the Docker daemon (root on the host) from a project config the Sandbox can write:

- a parent directory: `mounts: ["/var/run:/hr"]`, `docker_args: ["-v", "/:/host"]`
- `--mount type=bind,src=/run,dst=/r`, or a local-volume bind with `volume-opt=device=/var/run`
- a symlink in the project that points at the socket (Docker follows it on the host)
- other socket names: Docker Desktop's `docker.raw.sock`, `containerd.sock`, `podman.sock` when DOCKER_HOST points at Podman

Proposed rule: judge every host path a mount sends to Docker by what it resolves to: collect sources from `mounts`, `-v`/`--volume` and `--mount` src/source/volume-opt=device; resolve symlinks; refuse a socket file, or the daemon socket (from DOCKER_HOST, else /var/run/docker.sock) or a directory above it. Keep the substring test for paths that do not exist yet.

The user chose to land aix-01m1203dkknf with the substring match and file this (2026-11-26).
