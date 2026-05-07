---
id: aix-01kr1qp6deb1
title: Wire --json infrastructure and aishell ps --json
status: open
type: feature
priority: 2
mode: afk
created: '2026-05-07T17:27:39.694143701Z'
updated: '2026-05-07T17:27:39.694143701Z'
tags:
- ready-for-agent
acceptance:
- title: aishell ps --json emits a compact JSON array with name, fullName, status, created keys; trailing newline; no human banners
  done: false
- title: aishell ps --json emits [] when no containers exist for the project
  done: false
- title: aishell --json ps and aishell ps --json behave identically
  done: false
- title: aishell --version --json continues to emit {"name":"aishell","version":"…"} via the shared emitter (no hand-rolled JSON left in cli.clj)
  done: false
- title: aishell setup --json (and any other non-Group-A command with --json) prints {"error":{"message":"…","code":"unsupported_json"}} to stderr and exits 1
  done: false
- title: aishell --json with no subcommand prints unsupported_json error and exits 1
  done: false
- title: aishell foobar --json prints unknown_command (not unsupported_json) and exits 1
  done: false
- title: aishell --help (with or without --json) prints the human help text; the --json description in Global Options is qualified to list supported commands
  done: false
- title: aishell claude --json continues to forward --json to Claude (post-position pass-through preserved for non-Group-A)
  done: false
- title: Migration warning, daily update-check, output/warn, and output/verbose are silent on stdout and stderr in JSON mode
  done: false
- title: Colors are force-disabled in JSON mode regardless of TTY (no ANSI in error message strings)
  done: false
- title: Pure-fn clojure.test coverage for the ps data builder, the dispatch JSON-support classifier, output/emit-json, and output/emit-error-json
  done: false
- title: bb test passes; clojure -M:clj-kondo --lint src test passes
  done: false
---

## Description

## What to build

First end-to-end JSON command for aishell. Lays down all cross-cutting infrastructure used by every other --json slice, then wires aishell ps --json as the canonical demo.

Behavior:
- aishell ps --json (and aishell --json ps) emits a single compact JSON document on stdout: a bare array of {name, fullName, status, created} per project container; [] when none. Trailing newline. No banners, no "To attach…" hint.
- aishell --version --json keeps emitting {"name":"aishell","version":"…"} but now goes through the shared emitter (replaces the hand-rolled JSON string in src/aishell/cli.clj).
- aishell <anything-not-in-Group-A> --json (e.g. setup, update, attach, claude, exec, gitleaks, vscode, upgrade) errors with a JSON document on stderr and exit 1: {"error":{"message":"…","code":"unsupported_json"}}. Pre-position only for non-Group-A: aishell claude --json keeps passing --json through to claude (post-position consumption is reserved for Group A).
- aishell --json with no subcommand and no --version errors as unsupported_json.
- aishell --help and aishell --json --help emit the existing human help; --help wins over --json.
- In JSON mode, suppress the v2.9 migration warning, the daily update-check notice, output/warn, and output/verbose. Force-disable colors regardless of TTY.
- Errors raised from any handler in JSON mode produce {"error":{"message":"…","code":"…"}} on stderr + exit 1. Initial error code catalog: unsupported_json, unknown_command, no_setup, docker_unavailable, internal_error. unknown_command wins over unsupported_json when both apply.

Mechanics (constraints, not exhaustive code direction):
- Extract --json early in dispatch (alongside the existing --unsafe / --name extraction at src/aishell/cli.clj) and thread through a new dynamic var output/*json-output* (parallel to the existing *verbose*).
- Add output/emit-json (compact, trailing newline, to stdout) and output/emit-error-json (to stderr, exits 1) in src/aishell/output.clj. Make output/error and output/warn JSON-mode-aware.
- Dispatch-level gate decides Group A membership and resolves unknown_command vs unsupported_json before handlers run.
- Update the --json description in global-spec to enumerate supported commands: ps, volumes list, info, check, --version.
- Pure data builder for ps (e.g. format-ps-data) that takes the docker container list and returns the JSON-shaped vector; the existing handler delegates to it for the human path too.

Out of scope (handled in follow-up slices): volumes list, info, check JSON paths.

## Blocked by

None - can start immediately.
