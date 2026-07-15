---
id: aix-01kr1qqhakrv
title: Wire aishell info --json (with --foundation --json)
status: open
type: feature
priority: 2
mode: afk
created: '2026-05-07T17:28:23.633967378Z'
updated: '2026-07-15T02:44:33.099606433Z'
tags:
- ready-for-agent
- json
acceptance:
- title: 'aishell info --json emits one compact JSON object with the documented top-level keys: project, foundation, baseImage, projectExtension, harnesses, unisoma, hostConfigPaths, state'
  done: false
- title: harnesses object always contains all six keys (claude, opencode, codex, gemini, pi, openspec) with {enabled, version}; missing harnesses get enabled:false / version:null
  done: false
- title: 'When no setup state exists, aishell info --json still emits a valid object: state.found is false, all harnesses enabled:false, hostConfigPaths is {}'
  done: false
- title: 'aishell info --foundation --json emits {"foundationDockerfile": "<raw Dockerfile string>"}'
  done: false
- title: Existing human aishell info and aishell info --foundation output is unchanged (same banners, same content)
  done: false
- title: info.clj is refactored to a pure build-info-data data builder consumed by both human and JSON paths (no logic duplication)
  done: false
- title: Pure-fn clojure.test coverage for build-info-data including the no-state case and the harnesses default-shape case
  done: false
- title: bb test passes; clojure -M:clj-kondo --lint src test passes
  done: false
deps:
- aix-01kr1qp6deb1
---

## Description

## What to build

Add JSON output for aishell info, including the --foundation variant. This slice carries the largest refactor of the four — splitting info.clj's print-as-you-go body into a pure data builder consumed by both human and JSON paths.

Behavior:
- aishell info --json emits a single compact JSON object on stdout (no banners, no separators) with this top-level shape:
  {
    project: {directory, hash, containerPrefix, imageTag},
    foundation: {tag, base, systemPackages: [...], runtimes: {node, babashka, bbin, cue, gosu}, gitleaks: {installed, version}},
    baseImage: {tag, custom},
    projectExtension: {configured, tag},
    harnesses: {claude: {enabled, version}, opencode: {…}, codex: {…}, gemini: {…}, pi: {…}, openspec: {…}},
    unisoma,
    hostConfigPaths: {claude: ["/abs/.claude", "/abs/.claude.json"], …},
    state: {found, buildTime}
  }
- The shape is stable when no setup state exists: state.found is false, every harness has enabled: false / version: null, hostConfigPaths is an empty object {}, and projectExtension.configured / baseImage.custom are computed from the filesystem as the human path already does. Consumers can write a single jq query that works in both states.
- aishell info --foundation --json emits {"foundationDockerfile": "FROM debian…"} (the raw embedded Dockerfile as a single string field). --foundation wins over the regular info shape, parallel to how the human path exits early today.
- The human path (aishell info, aishell info --foundation without --json) is unchanged — same section banners, same content, same separators.

Mechanics:
- Extract a pure build-info-data function (no I/O beyond the same state/template/fs reads the human path already does) that returns the JSON-shaped map. The human renderer consumes the same map (or its sub-pieces) and prints the existing human layout. This makes the data path testable in isolation and ensures the two paths can never drift.
- Versions for runtimes (node, babashka, bbin, cue) come from the existing parse-* helpers on the embedded Dockerfile template.
- gitleaks.installed / gitleaks.version reflect the same logic the human path uses (state-dependent; null/false when not installed).

## Blocked by

- aix-01kr1qp6deb1 (Wire --json infrastructure and aishell ps --json)
