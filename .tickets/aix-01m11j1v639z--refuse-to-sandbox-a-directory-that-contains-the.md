---
id: aix-01m11j1v639z
title: Refuse to sandbox a directory that contains the user's home
status: open
type: bug
priority: 1
mode: afk
created: '2026-08-27T12:10:26.371143499Z'
updated: '2026-08-27T12:10:30.798100087Z'
acceptance:
- title: Running any sandbox-launching command (run/shell, harness subcommands, exec, vscode) from $HOME exits 1 with an error naming the directory and the reason
  done: false
- title: The same refusal fires from any ancestor of $HOME (e.g. /home, /), after canonicalizing symlinks and trailing slashes
  done: false
- title: The guard runs before the sensitive-file scan, extension-image resolution, and docker run — no filesystem walk happens first
  done: false
- title: --unsafe does not bypass the guard; the error message says so
  done: false
- title: Read-only commands (info, check, attach) are unaffected
  done: false
- title: Unit tests cover home, ancestor-of-home, symlinked home, and a normal project dir
  done: false
- title: README documents the limit in one sentence
  done: false
tags:
- ready-for-agent
---

## Description

Running `aishell` from the home directory hangs for 10+ minutes and the harness never starts.

Two stages of the start procedure walk the whole project tree, and with `$HOME` as the project that is every file the user owns:

1. Sensitive-file detection runs twelve detectors, each doing its own unpruned recursive glob over the project dir (the excluded-dir set is a post-filter, not a traversal prune). Happens before `docker run`.
2. On Unix the Sandbox's `$HOME` is the host home path, and the project is bind-mounted at its host path. When the project *is* the home (or an ancestor of it), the entrypoint's recursive `chown` of `$HOME/.local` walks the real host `~/.local` through the mount.

Decision: refuse rather than optimize. A sandbox whose root contains the user's home is never what the user meant — it exposes `~/.ssh`, credentials, and every project to the Harness, and stage 2 mutates host files regardless of `--unsafe`. So there is no override.

Rule: the sandbox root (cwd) must not equal or be an ancestor of the user's home directory, compared after canonicalization. That single check covers `$HOME`, `/home`, and `/`.

Place the guard at the top of every launch path, before any work that touches the tree. Error text should name the offending directory, say why, and suggest `cd` into a project.
