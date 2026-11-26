---
id: aix-01m8bxz7e9zj
title: aishell vscode joins a running Sandbox
status: closed
type: feature
priority: 2
mode: afk
created: '2026-11-26T12:41:00.616750160Z'
updated: '2026-11-26T13:03:22.112340407Z'
closed: '2026-11-26T13:03:22.112340407Z'
tags:
- ready-for-agent
acceptance:
- title: With one Sandbox running, bare aishell vscode opens VSCode in it and returns without starting a vscode Sandbox
  done: true
- title: aishell vscode <name> joins the named Sandbox; several running without a name errors with the list
  done: true
- title: Closing the window, Ctrl-C or --stop never stops a joined Sandbox
  done: true
- title: Without setup --with-vscode, aishell vscode refuses with the setup hint
  done: true
- title: With the option on, every Sandbox mounts the aishell-vscode-server volume; no host ~/.vscode-server mount remains
  done: true
- title: aishell volumes lists the volume and prune removes it only when VSCode is disabled
  done: true
- title: aishell check reports the code CLI only when VSCode is enabled
  done: true
- title: Docs and CHANGELOG (Breaking) updated; clj-kondo --lint src test clean
  done: true
---

## Description

`aishell vscode` always starts its own `vscode` Sandbox. A user who runs one Sandbox (bare `aishell`) with harnesses attached in other terminals can't open VSCode in that same Sandbox. Make `aishell vscode` join a running Sandbox the way bare `aishell attach` does. VSCode becomes opt-in at setup, and its server moves into a Docker volume. See docs/adr/0013-vscode-joins-a-running-sandbox.md.

## Design

### Target selection
- Bare `aishell vscode`: with exactly one Sandbox running in the project, join it. With none running, start the `vscode` Sandbox as today. With several running, error and list them, reusing the `resolve-attach-target` listing format.
- `aishell vscode <name>`: join the named Sandbox. Error if it is not running, as `attach` does.
- No `--new` flag.

### Lifetime
- A joined Sandbox is never stopped: no stop when the window closes, no shutdown hook, and `--stop` ignores it. When joining, launch `code` without `--wait` and return, as `--detach` does.
- `--stop` only stops the `vscode` Sandbox.

### Setup option
- `aishell setup --with-vscode`: boolean only, so `--with-vscode=X` is an error. Store it in setup state. `--reuse-config` keeps it, and a plain setup without the flag turns it off.
- With the option off, `aishell vscode` errors with the hint `aishell setup --reuse-config --with-vscode`.

### VSCode server volume
- Named volume `aishell-vscode-server`, mounted at the VSCode server path in every Sandbox (bare `aishell`, each Harness, `vscode`) when the option is on. Not mounted when it is off. Added at runtime, so no image rebuild.
- Replaces the `~/.vscode-server` bind mount in `aishell.vscode/start-container!`. Leave the host directory alone.
- `aishell volumes list`: one row for it, marked active when VSCode is enabled and unused otherwise. `prune`: remove it only when VSCode is disabled, after confirmation, and skip it while a container uses it.

### remoteUser
- Call `ensure-imageconfig!` for the joined container's actual image (`docker inspect`), not the image tag the project resolves to now.

### aishell check
- VSCode enabled and `code` missing: warning, as today. VSCode disabled: one info line, "VSCode disabled (aishell setup --with-vscode)", and no lookup for `code`.

### Docs
- Help text for `aishell vscode` and `setup`, the README VSCode section, docs/CONFIGURATION.md and docs/HARNESSES.md where they mention vscode. CHANGELOG entry under Breaking.

## Notes

**2026-11-26T13:03:22.112340407Z**

aishell vscode joins the only running Sandbox or a named one and never stops it; with none running it starts the vscode Sandbox as before. VSCode is opt-in with setup --with-vscode, and the VSCode server lives in the aishell-vscode-server volume mounted into every Sandbox instead of a bind mount of host ~/.vscode-server. volumes list/prune and check follow the option; docs, CHANGELOG (breaking) and ADR 0013 updated.
