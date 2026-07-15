---
id: aix-01kr1qpwqxg9
title: Wire aishell volumes [list] --json and reject volumes prune --json
status: open
type: feature
priority: 2
mode: afk
created: '2026-05-07T17:28:02.556561960Z'
updated: '2026-07-15T02:44:32.978306634Z'
tags:
- ready-for-agent
- json
acceptance:
- title: aishell volumes --json and aishell volumes list --json emit identical compact JSON arrays of harness-volume objects
  done: false
- title: 'Each entry has keys: name, status, size, sizeBytes, hash, harnesses'
  done: false
- title: harnesses is a JSON array parsed from the comma-separated docker label; null when the label is missing
  done: false
- title: size is the human string from get-volume-size; null when get-volume-size returns N/A
  done: false
- title: sizeBytes is present on every entry as null (placeholder; real bytes wiring is a follow-up)
  done: false
- title: Empty volume list emits [] (no hint text)
  done: false
- title: aishell volumes prune --json prints {"error":{"message":"…","code":"unsupported_json"}} to stderr and exits 1
  done: false
- title: aishell volumes <unknown> --json prints code unknown_command (not unsupported_json) and exits 1
  done: false
- title: Pure-fn clojure.test coverage for the volumes data builder including missing-label and N/A-size cases
  done: false
- title: bb test passes; clojure -M:clj-kondo --lint src test passes
  done: false
deps:
- aix-01kr1qp6deb1
---

## Description

## What to build

Add JSON output for the volumes listing, and make sure non-listing volumes subcommands reject --json cleanly.

Behavior:
- aishell volumes --json and aishell volumes list --json (both forms) emit a single compact JSON document on stdout: a bare array of harness-volume objects; [] when none. Each entry has {name, status, size, sizeBytes, hash, harnesses}.
  - status is the existing enum: "active" (the volume currently referenced by state) or "orphaned".
  - harnesses is an array of strings parsed from the comma-separated docker label aishell.harnesses; null when the label is missing (preserve honesty over the human "unknown" sentinel).
  - size is the existing human-formatted string (e.g. "234MB") from vol/get-volume-size; null when get-volume-size returns "N/A".
  - sizeBytes is reserved as null in this slice — the field exists in the JSON shape but is not yet populated. Wiring real bytes (via docker system df --format json) is a separate, deferred change; consumers can write .sizeBytes today and it returns null.
  - hash is the trailing hash segment of the volume name (already present in the underlying data via vol/list-harness-volumes).
- aishell volumes prune --json errors with {"error":{"message":"…","code":"unsupported_json"}} on stderr and exit 1. The gate runs after the volumes subcommand resolution (inside handle-volumes) so prune can be distinguished from list.
- aishell volumes <unknown> --json errors with code unknown_command (the unknown wins over the json-support issue), matching the precedence rule established in the infra slice.
- Empty case (no harness volumes) emits [], not an error and no human hint text.

Mechanics:
- Pure data builder (e.g. format-volumes-data) takes the vector returned by vol/list-harness-volumes plus the active volume name (from state) and returns the JSON-shaped vector. The existing human handler should delegate to it for the table-row mapping too where convenient.
- The dispatch-level gate from the infra slice already errors on aishell <non-Group-A> --json. This slice adds the parallel gate inside handle-volumes for the prune / unknown-subcommand cases.

Out of scope: actually computing sizeBytes from docker. Keep the field as null.

## Blocked by

- aix-01kr1qp6deb1 (Wire --json infrastructure and aishell ps --json)
