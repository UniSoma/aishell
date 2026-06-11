---
id: aix-01ktva69h05h
title: Support .sandbox as alias for project .aishell config dir
status: closed
type: feature
priority: 2
mode: afk
created: '2026-06-11T12:23:25.984241370Z'
updated: '2026-06-11T12:34:15.805470321Z'
closed: '2026-06-11T12:34:15.805470321Z'
tags:
- ready-for-agent
acceptance:
- title: A single resolver in aishell.util returns the active project config dir name, checking the filesystem for .aishell / .sandbox
  done: true
- title: config, docker/extension, util, run, check, info route project-dir lookups through the resolver (no hardcoded .aishell literals for the project dir)
  done: true
- title: 'A project with only .sandbox/ is fully honored: Dockerfile extension, config.yaml load, info/check all work'
  done: true
- title: A project with both .aishell/ and .sandbox/ fails with a clear use-only-one error at the CLI boundary, for every command
  done: true
- title: Global ~/.aishell/ behavior unchanged; a .sandbox repo still inherits global ~/.aishell/config.yaml as fallback
  done: true
- title: aishell setup --dir .sandbox scaffolds into .sandbox/; --dir defaults to .aishell; invalid values rejected
  done: true
- title: info/check/path messages name the active dir, not a hardcoded .aishell
  done: true
- title: Windows miscased-Dockerfile guard inspects the active dir and names it in the error
  done: true
- title: 'Internal names unchanged: /etc/bash.aishell, bashrc.aishell, Docker labels, aishell:base tag, ~/.aishell/'
  done: true
- title: README mentions .sandbox/ alias; CHANGELOG entry added
  done: true
- title: clj-kondo lint passes
  done: true
---

## Description

Recognize a project-level .sandbox/ directory as a full alias for .aishell/ (the per-repo dir holding Dockerfile + config.yaml). Motivation: some repos use aishell purely as a sandbox tool and want no "ai" naming in their tree.

Scope is the project-level config dir ONLY. The global ~/.aishell/ (base Dockerfile, config.yaml, state.edn, .pi-packages-hash) is unchanged — there is no ~/.sandbox/. A repo using .sandbox/ still inherits the global ~/.aishell/config.yaml as fallback; that is intended, not a leak.

Resolution is centralized: introduce a single resolver in aishell.util that returns the active project config dir name (.aishell or .sandbox) by checking the filesystem. Existing helpers (config/project-config-path, extension/project-dockerfile, util/project-config-path) and the call sites in run, check, info all delegate to it. Match the literal lowercase names only — no new casing tolerance on the dir name itself.

When BOTH .aishell/ and .sandbox/ exist in a project, the resolver throws an ex-info ("Found both .aishell/ and .sandbox/ — use only one") caught at the CLI top-level error boundary, so every command fails consistently and clearly.

aishell setup gains a --dir flag accepting .aishell or .sandbox, defaulting to .aishell. All path messages (info, check, vscode hints, etc.) name the ACTIVE dir rather than a hardcoded .aishell, so a .sandbox user never sees a misleading .aishell/... path.

The Windows miscased-Dockerfile guard in docker/extension.clj routes through the resolver: it inspects the active dir and its error message names the active dir.

Explicitly OUT of scope (stay aishell, untouched): /etc/bash.aishell, the bashrc.aishell build artifact, Docker labels (aishell.base.id, aishell.extension.hash), image tags (aishell:base), and the global ~/.aishell/. None of these appear in a user repo tree.

Docs: brief README mention of .sandbox/ as an alternative, plus a CHANGELOG entry. Discoverable, not prominent.

## Notes

**2026-06-11T12:34:15.805470321Z**

Added resolve-project-config-dir in aishell.util; config/extension/check/info/cli/run route project-dir lookups through it. .sandbox/ is a full alias for .aishell/; both-present throws ex-info caught at the CLI boundary. setup --dir scaffolds the chosen dir (defaults .aishell, invalid rejected). Path messages name the active dir; Windows miscased guard routes through the resolver. Global ~/.aishell/ untouched. README + CHANGELOG updated; tests added in util_test/cli_test; lint clean.
