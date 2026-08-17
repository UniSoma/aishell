---
id: aix-01m082a4r9hh
title: Bare `aishell attach` attaches to the single running container
status: closed
type: feature
priority: 2
mode: afk
created: '2026-08-17T14:34:20.297695477Z'
updated: '2026-08-17T14:50:29.133803648Z'
closed: '2026-08-17T14:50:29.133803648Z'
tags:
- ready-for-agent
acceptance:
- title: With exactly one running container, bare `aishell attach` attaches to it and announces the pick on stderr
  done: true
- title: With exactly one running container, `aishell attach -- <command>` runs the command in it
  done: true
- title: With no running containers, attach errors with start guidance, names any stopped containers, and hints at Docker
  done: true
- title: With several running containers, attach errors listing each candidate name and status plus a copyable command
  done: true
- title: Explicit `aishell attach <name>` behaves exactly as before, with no announcement line
  done: true
- title: The resolver is a pure namespace unit-tested over zero, one, many, stopped-only, and empty container vectors, with no Docker
  done: true
- title: The attach parser returns a nil name instead of an error, with the contract change covered in its test namespace
  done: true
- title: '`attach -h`, README, llm.txt, and CHANGELOG describe the optional name argument'
  done: true
---

## Description

`aishell attach` demands a container name. With one container running, the user has to run `aishell ps`, read the name back, and type it. Make the name optional: with no name, attach resolves to the single running container in the project.

## The rule

Candidates are the project's containers as `aishell ps` sees them, filtered to running (a `^Up` status).

- Exactly one running -> attach to it.
- Zero, or more than one -> error, exit 1.

Deliberate non-exceptions, so the rule stays predictable from the `ps` output in front of the user:

- `vscode` containers are not special-cased. They show up in `ps`, so they count.
- Bootstrap state does not gate the pick. Explicit `aishell attach <name>` ignores `pending` and `failed` today, and inference must not be stricter.
- `aishell attach -- <command>` with no name infers the same way.

## Messages

An inferred pick announces itself on stderr before the terminal is handed over (`Attaching to 'claude'...`). Explicit attach stays silent.

Zero running:

```
No running containers in this project.

To start one:  aishell claude

Stopped: claude, shell
If this is unexpected, check Docker: aishell check
```

The `Stopped:` line appears only when stopped containers exist. The Docker hint is there because the container listing swallows exceptions and returns an empty vector, so a dead daemon is indistinguishable from an empty project.

Multiple running, candidates listed so a name can be copied straight out of the error:

```
Multiple running containers — name one:

  claude    Up 3 minutes
  shell     Up 20 minutes

  aishell attach claude
```

## Design

`aishell.attach.parse` becomes purely syntactic: no name yields a nil name and the "container name required" error goes away. It still rejects a trailing bare `--` and two names before `--`. All "which container" meaning moves to a new pure resolver namespace beside `parse` and `invocation`, taking the raw container vector and returning a name or an error string — which keeps both error bodies above unit-testable without Docker.

The resolver hands the attach entry point a short name, not a full container name, so explicit and inferred attach share one path. The resulting re-validation is redundant but deliberate: a container that dies between the listing and the exec then produces the existing clear "not running" error. The TTY check stays first — cheapest, and true regardless of container count.

`extract-short-name` is private to the CLI namespace today and the resolver needs it. Move it beside `container-name`, whose inverse it is.

## Out of scope

- The same inference for `aishell exec`. It is non-interactive and often scripted, where an implicit target is a footgun.
- Making the container listing distinguish daemon failure from an empty project. That is the honest fix for the Docker hint above, but it also changes `ps`.
- The `ps` footer's "To attach: aishell attach <name>". Making it conditional on the running count is branching for a one-word gain.

## Notes

**2026-08-17T14:50:29.133803648Z**

Bare `aishell attach` now resolves the single running container: parse became purely syntactic (nil name), a new pure `aishell.attach.resolve` namespace picks the target or explains why it can't, and `extract-short-name` moved to `aishell.docker.naming`. Docs and `attach -h` updated.
