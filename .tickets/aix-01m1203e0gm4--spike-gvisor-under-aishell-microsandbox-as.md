---
id: aix-01m1203e0gm4
title: 'Spike: gVisor under aishell (microsandbox as fallback)'
status: open
type: task
priority: 2
mode: hitl
created: '2026-08-27T16:15:58.480480684Z'
updated: '2026-08-27T16:17:27.068795372Z'
acceptance:
- title: gVisor installed and registered; docker info lists runsc
  done: false
- title: Entrypoint and a Claude Code OAuth prompt succeed under runsc, or the failure is recorded
  done: false
- title: Host→guest file visibility and inotify behavior recorded
  done: false
- title: git status and npm install timings under runc and runsc recorded
  done: false
- title: 'Decision noted: keep gVisor, or fall back to microsandbox with its own results'
  done: false
tags:
- security
---

## Description

Time-box: one session, on the host (this cannot run inside an aishell sandbox — no docker, no /dev/kvm there). Host facts as of filing: rootful Docker 28-era with AppArmor + builtin seccomp, runtimes `runc` + `nvidia`, `/dev/kvm` present, `runsc` not installed.

Steps:
1. Install gVisor (`runsc` + `containerd-shim-runsc-v1`) from gVisor's apt repo; register it in `/etc/docker/daemon.json`; `docker info` must list `runsc`.
2. Run this project's sandbox with `docker_args: ["--runtime=runsc"]` (or the `runtime` key if it has landed). Check the entrypoint completes (`usermod`/`chown`/`gosu`) and the harness starts.
3. Run Claude Code via OAuth inside it; complete a real prompt that edits a file.
4. Host→guest visibility: edit a project file on the host, confirm the sandbox sees it; run a file watcher (`npx chokidar` or `inotifywait`) inside and confirm host edits trigger it — inotify on gofer-backed mounts is a known gVisor weak spot.
5. Measure `git status` and an `npm install` of a mid-size project under runc vs runsc.
6. Note whether GPU access (`nvidia` runtime) matters for any sandbox — gVisor's nvproxy is limited.

Exit criteria for keeping gVisor: steps 2–4 pass, step 5 within ~2× of runc. If it fails, record why here and repeat the shape with microsandbox (`msb doctor`, `--mount-dir <proj>:<same path>:rw`, Claude Code via OAuth, same visibility and timing checks). Either way, record the outcome as a note and, if a runtime other than runc is viable, un-block the `runtime` key ticket.
