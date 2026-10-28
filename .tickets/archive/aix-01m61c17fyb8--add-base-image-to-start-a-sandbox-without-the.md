---
id: aix-01m61c17fyb8
title: Add --base-image to start a Sandbox without the Project extension
status: closed
type: feature
priority: 2
mode: afk
created: '2026-10-28T13:43:43.870372842Z'
updated: '2026-10-28T14:25:36.344326078Z'
closed: '2026-10-28T14:19:36.043805024Z'
tags:
- ready-for-agent
acceptance:
- title: aishell --base-image <harness> starts the Sandbox on aishell:base in a project whose Project extension fails to build
  done: true
- title: --base-image works with harness subcommands, shell, the bare default and exec
  done: true
- title: --base-image after the subcommand is passed through to the harness or command untouched
  done: true
- title: --base-image with attach, vscode or a management command fails with a clear error
  done: true
- title: Project config.yaml still applies under --base-image, except pre_start, which is skipped
  done: true
- title: A one-line startup notice says the Project extension was skipped, or that none exists
  done: true
- title: A failed Extended image build suggests aishell --base-image in its error
  done: true
- title: Top-level help and README document --base-image
  done: true
links:
- aix-01m61dyzmayj
- aix-01m61edx003f
- aix-01m61edx3fdb
---

## Description

When a project's `.aishell/Dockerfile` (its Project extension) is broken, the Extended image fails to build and no Sandbox for that project starts. If you only use harnesses inside aishell, you then have no sandboxed Claude to fix the Dockerfile with. Today the workaround is renaming the file.

`aishell --base-image <harness>` (and `aishell --base-image`, `aishell --base-image shell`, `aishell --base-image exec ...`) starts the Sandbox on the Base image (`aishell:base`) and ignores the Project extension.

## Design

Decisions settled in a grilling session:

- **Image:** the Base image. The per-user `~/.aishell/Dockerfile` still applies. There is no Foundation-image level; to recover from a broken per-user layer, rename that file.
- **Project config:** `.aishell/config.yaml` still applies in full (mounts, env, harness_args, security profile, network mode, runtime), except that `pre_start` is skipped, because it is the setting most likely to depend on the broken image.
- **Name:** `--base-image`. `--bare` collides with Claude Code's own `--bare`, and anything built on "extension" risks Gemini CLI's `-e/--extensions`.
- **Position:** recognized only *before* the subcommand. Args after the harness name pass through untouched, unlike `--unsafe`, which is stripped anywhere.
- **Commands:** accepted on harness subcommands, `shell`, the bare default and `exec` (which already skips `pre_start`). Rejected with a clear error on `attach` (the Sandbox already exists), `vscode` and the management commands.
- **One-shot only:** no config.yaml key and no environment variable.
- **Sandbox identity:** same container name as a normal launch (`aishell-{hash}-{name}`).
- **Notice:** one line at startup saying the Project extension was skipped, or that there was none to skip. With no Project extension the launch still proceeds and still skips `pre_start`.
- **Failure hint:** when the Extended image build fails, the error suggests `aishell --base-image <harness>`.
- **Out of scope:** running a last-good Extended image left over from before the breakage.

Help: "Run on the Base image, skipping the Project extension and pre_start." Glossary terms Project extension and Extended image were added to CONTEXT.md.

## Notes

**2026-10-28T14:19:36.043805024Z**

Added aishell --base-image: a leading flag that starts the Sandbox on the Base image without the Project extension and skips pre_start. Accepted on harness launches, the bare default and exec; refused on attach, vscode and management commands. A failed Extended image build now suggests it. Verified by the user on a real project with a broken .aishell/Dockerfile.
