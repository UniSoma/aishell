---
id: aix-01m61edx003f
title: 'Tidy the --base-image follow-ups: key names, glossary terms, README example'
status: open
type: chore
priority: 2
mode: afk
created: '2026-10-28T14:25:36.250583130Z'
updated: '2026-10-28T14:25:36.250583130Z'
tags:
- ready-for-agent
acceptance:
- title: resolve-image-tag, run-container and run-exec share the :base-image key; no caller translates it
  done: false
- title: base-image-notice is folded into resolve-image-tag and both notice cases are tested through it
  done: false
- title: :foundation-tag is renamed :base-tag in build-extended-image and its caller
  done: false
- title: README Project customization extends aishell:base
  done: false
- title: No 'project image' or 'Building project extension' remains in src
  done: false
links:
- aix-01m61c17fyb8
---

## Description

Follow-ups from the reviews of aix-01m61c17fyb8 (`--base-image`, landed at 7462c39). Each is small and keeps behaviour the same.

1. `run/resolve-image-tag` takes `{:base-image? ...}` while `run-container` and `run-exec` opts carry `:base-image`, so both callers translate. Make the key `:base-image` throughout and pass opts straight through.
2. Fold `run/base-image-notice` (one caller) into `resolve-image-tag`; move the no-Project-extension case into `base-image-skips-the-project-extension` in run_test.clj.
3. Rename the `:foundation-tag` key of `ext/build-extended-image` to `:base-tag` (extension.clj docstring, destructuring, use; run.clj call site). It holds the Base image tag; CONTEXT.md avoids "foundation" for that layer.
4. README "Project customization": "extend the foundation image" becomes "extend the Base image (`aishell:base`)", and the example uses `FROM aishell:base`.
5. Glossary terms: the spinner label "Building project extension" in extension.clj becomes "Building Extended image". The `base-image-commands` docstring in cli.clj drops "project image": "aishell's own subcommands that start a container and so accept --base-image." (Decided 2026-10-28: an `exec` container is not a Sandbox; it is unnamed and runs no harness.)

Out of scope: inlining `split-leading-verbose` (goes with the global-flag parsing ticket) and renaming `get-foundation-image-id` (own ticket).
