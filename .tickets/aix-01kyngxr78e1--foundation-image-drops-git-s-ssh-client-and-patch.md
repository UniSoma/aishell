---
id: aix-01kyngxr78e1
title: Foundation image drops git's ssh-client and patch recommends
status: open
type: bug
priority: 2
mode: hitl
created: '2026-07-28T23:28:26.856341752Z'
updated: '2026-07-28T23:28:26.856341752Z'
tags:
- docker
- foundation
acceptance:
- title: openssh-client and patch present in the foundation apt list
  done: false
- title: git operations against ssh remotes work in a container with ~/.ssh mounted
  done: false
- title: CONFIGURATION.md documents the ~/.ssh mount opt-in
  done: false
---

## Description

The foundation image installs its apt packages with `--no-install-recommends` (src/aishell/docker/templates.clj:29). Debian's `git` package Recommends `ca-certificates`, `less`, `patch`, and `ssh-client`. The first two are installed explicitly; `patch` and `openssh-client` were silently dropped.

Consequences:

- `git clone git@github.com:...`, `git push`, and `git fetch` against ssh remotes fail inside the container — there is no ssh binary. `docs/agents`-style workflows and any project whose origin is an ssh remote are affected.
- `~/.ssh` is the canonical example in the `mounts:` config documentation (src/aishell/docker/run.clj:100), so users can mount their keys into an image that has no client able to use them.
- `patch` is absent, so agents cannot apply unified diffs with `patch -p1`.

Fix: add `openssh-client` and `patch` to the apt list. Marginal cost ~12 MB (openssh-client 5.8 MB plus the krb5/fido2/libedit chain; patch ~200 kB).
