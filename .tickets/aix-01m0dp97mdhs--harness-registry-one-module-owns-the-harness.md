---
id: aix-01m0dp97mdhs
title: 'Harness registry: one module owns the Harness; remove OpenSpec'
status: open
type: task
priority: 1
mode: afk
created: '2026-08-19T18:59:34.122245173Z'
updated: '2026-08-19T18:59:34.122245173Z'
tags:
- registry
- ready-for-agent
- codebase-design
links:
- aix-01kxmdfj56ct
- aix-01kr1d71b75y
---

## Description

## Problem Statement

Adding, changing, or even correctly displaying a Harness in aishell requires editing ~12 namespaces, each holding its own literal list of harness facts. The lists have already drifted: `pi` and `a` get no typo suggestions, Codex is labeled "Codex" in some output and "Codex CLI" in others, Claude's skip-permissions rule is spelled twice (run path vs shell alias) and can silently diverge, `harness_args.openspec` validates but is never consumed, and `harness_args.gitleaks` is silently dropped. No test can catch a missed site. Separately, the OpenSpec harness no longer makes sense in aishell and should be removed.

## Solution

One deep module — the **harness registry** (`aishell.harness`) — holds an ordered, closed vector of **harness descriptors** (pure data: identity, canonical label, state/version keys, capabilities, install source, config paths, passthrough env vars). Every enumeration site becomes a derivation from the registry: subcommand sets, suggestion lists, volume-hash inputs, mount and env tables, labels, launch argv, shell aliases. A single interpreter function derives the final launch argv from a descriptor plus runtime inputs (skip-permissions setting, `harness_args`), called by both the run path and the alias builder. OpenSpec is removed entirely, with a one-time warning for users who had it enabled.

Both terms are defined in CONTEXT.md.

## User Stories

1. As an aishell maintainer, I want every Harness fact in one descriptor, so that adding a Harness is a one-module edit instead of a ~12-namespace sweep.
2. As an aishell maintainer, I want a table-driven completeness test over the registry, so that a descriptor missing a required field fails CI instead of surfacing as runtime drift.
3. As an aishell user, I want `aishell p1` (or any typo near `pi`) to suggest `pi`, so that typo suggestions cover every subcommand.
4. As an aishell user, I want each Harness to have one canonical label everywhere (`setup`, `info`, `check`, `update`, help text), so that output is consistent.
5. As an aishell user, I want Claude's skip-permissions behavior (including the `AISHELL_SKIP_PERMISSIONS` override) to be identical whether I launch via `aishell claude` or via the in-sandbox shell alias, so that the two paths cannot diverge.
6. As an aishell user, I want my `harness_args` defaults applied identically on the run path and in shell aliases, so that a sandbox shell behaves like the CLI.
7. As an aishell user, I want `harness_args` for a Harness that cannot be launched (e.g. gitleaks today) to produce a warning instead of being silently dropped, so that my config mistakes are visible.
8. As an aishell user with an existing setup, I want my harness volume hash unchanged by this refactor, so that upgrading aishell does not force a volume rebuild.
9. As an aishell user who had OpenSpec enabled, I want a one-time warning on my next `run`/`update`/`setup` telling me OpenSpec support was removed, so that its disappearance is not silent.
10. As an aishell user who had OpenSpec enabled, I want my harness volume to repopulate without OpenSpec automatically (via the hash change), so that no manual cleanup is needed.
11. As an aishell user, I want `--with-openspec` to be rejected as an unknown flag, so that the removal is unambiguous.
12. As an aishell maintainer, I want gitleaks's launch quirks (no alias, skip pre-start, non-interactive shell mode) expressed as descriptor capability fields, so that launch shape is data, not scattered special cases.
13. As the author of the orchestration epic, I want per-harness static facts available as data from one registry, so that the harness-provider protocol (aix-01kxmdfj56ct) can build its behavioral seam on top without re-enumerating facts.
14. As an agent working in this repo, I want "which harnesses exist and what can each do" answerable by reading one namespace, so that navigation doesn't require bouncing across 12 files.
15. As an aishell maintainer, I want the registry closed (not user-extensible), so that the descriptor schema can evolve without a config-compatibility contract.

## Implementation Decisions

- New module `aishell.harness`: the harness registry. Rows are every `--with-X` flag bearer after OpenSpec removal: claude, opencode, codex, gemini, pi, gitleaks. vscode is not a Harness and stays out; its `harness_args` acceptance is a known wart left for a future command registry.
- Descriptors are pure data. Capabilities are presence-based where natural (no alias field = no alias) with explicit flags where absence is meaningful (interactive?, pre-start?, install kind: npm package / binary tarball / image-baked).
- One argv interpreter function in the registry module: (descriptor, runtime inputs {skip-permissions?, harness-args}) → final argv. Both the container launch path and the shell-alias builder call it. This removes the double spelling of Claude's `--dangerously-skip-permissions` / `AISHELL_SKIP_PERMISSIONS` logic and Codex's update-check flag.
- Today's divergent membership sets (known harnesses in config validation, volume-hash participants, pass-through subcommands, suggestion-list entries) become capability filters over the registry.
- One canonical label per Harness, the long form: "Claude Code", "OpenCode", "Codex CLI", "Gemini CLI", "Pi coding agent". Output diffs in `info`/`setup`/`check` are accepted.
- Volume-hash compatibility is preserved: the hash derivation sorts harness keys internally, so registry order (display order) never affects the hash, and no user's volume rebuilds from this refactor alone.
- Gitleaks's post-run scan-timestamp write stays feature code in the run flow — descriptors hold no functions.
- OpenSpec removal covers: the `--with-openspec` flag, state and version keys (stripped on next state write, with a one-time removal warning when found), npm install at volume-populate time, the `~/.config/openspec` mount, the `OPENSPEC_TELEMETRY=0` env var, config validation membership, and help text. The repo's own `openspec/` change-proposal directory is deleted as well (dead workflow; knot replaced it).
- All enumeration sites migrate in one change — no transition period where literal lists and the registry coexist.
- Behavior fixes shipped by this change (pi suggestions, label unification, gitleaks `harness_args` warning, OpenSpec removal) are each noted in the changelog.
- Out of the registry: the UniSoma OpenCode model whitelist (org policy feature, not a Harness fact) and direct state-map reads (state-schema ownership is aix-01kr1d71b75y, sequenced later).
- Consistent with the harness-provider-protocol epic: that ticket designs a behavioral seam (headless run / envelope parse / resume); this registry is its static-data substrate, built standalone first.

## Testing Decisions

- Tests cross seams, not implementations: assert what callers observe (derived sets, labels, argv vectors, hashes, warnings), never how a derivation walks the registry.
- Primary seam (new, the only new one): the registry interface itself — completeness of every descriptor against its declared capabilities, capability-filter derivations (subcommand set, volume participants, suggestion contributions, alias emitters), and the argv interpreter across all harnesses and runtime-input combinations (skip-permissions on/off, `harness_args` present/absent, gitleaks's non-interactive shape).
- Existing seams reused: the volume-hash normalization/derivation functions get a hash-equivalence test pinning that registry-derived input hashes byte-identically to today's enabled-harness inputs; the setup-state resolution tests (already in place) extend to OpenSpec key stripping and the one-time removal warning; the typo-suggestion tests (already in place) extend to assert `pi` and `a` are suggested.
- No test goes through docker; the launch and volume namespaces become thin consumers of registry derivations and are verified through the registry's tests. A docker-invocation seam is separate future work.
- Prior art: the existing pure-function test style in the config, check, output, naming, and attach test namespaces.

## Out of Scope

- The harness-provider protocol and orchestration layer (aix-01kxmdfj56ct, aix-01kxmde9p330) — this registry is their substrate, not their implementation.
- User-extensible harness descriptors (config-supplied harnesses or overrides).
- State-schema ownership / typed accessors (aix-01kr1d71b75y) — callers keep reading state maps directly for now.
- The docker-invocation seam, launch-pipeline, provisioning, and command-registry candidates from the same architecture review.
- Moving the UniSoma whitelist or the vscode pseudo-harness handling.

## Further Notes

- Design settled in a grilling session on 2026-08-19 (architecture review candidate #1). CONTEXT.md already defines **Harness descriptor** and **Harness registry**.
- Lint with clj-kondo before committing; no AI attribution in commit messages (repo hard rules).
- The May 2026 tickets on the launch pipeline and state schema remain open; this ticket intentionally narrows to static facts.
