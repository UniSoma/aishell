---
id: aix-01kxmde9p330
title: Orchestration layer for aishell sandboxes
status: open
type: epic
priority: 2
mode: hitl
created: '2026-07-16T02:52:38.466589346Z'
updated: '2026-07-16T03:00:15.429734729Z'
tags:
- wayfinder:map
---

## Destination

v1 orchestration layer design locked: a babashka library (harness-provider abstraction, Claude Code as the only shipped provider) plus a thin `aishell run` CLI, executing headless runs sequentially inside the project's existing sandbox with full autonomy (`--dangerously-skip-permissions`), returning the raw + parsed `--output-format json` envelope, supporting session resume. The map is done when every v1 design decision is resolved and ordinary implementation tickets exist in knot, ready for normal execution sessions.

## Notes

- Domain: aishell — babashka CLI managing per-project Docker sandboxes for AI coding harnesses. See CONTEXT.md for the ubiquitous language (harness, sandbox, project hash, Claude isolation).
- Inspiration: sandcastle (https://github.com/mattpocock/sandcastle) — TypeScript orchestration library. We borrow use cases (implement→review pipelines, resumable headless runs), not its architecture. All tooling in babashka.
- Skills to consult per session: /grilling and /domain-modeling for decision tickets; /research for research tickets.
- Standing preference: no AI attribution in commits; lint with clj-kondo before commit.
- Interaction to watch: headless runs exec into the same container as live interactive sessions — the in-flight Claude-isolation work (shared vs project machine state) may affect this.

## Decisions so far

Locked during charting (no ticket; recorded here):

- Form — library-first, thin CLI layered on top.
- Harnesses — provider abstraction from day one; Claude Code is v1's only provider.
- Worktrees — none in v1; runs execute against the project dir, sequential only.
- Container — reuse the project's existing sandbox via docker exec, starting it if needed.
- Result — raw + parsed JSON envelope; no schema validation.
- Sessions — resume yes, fork no.
- Permissions — always --dangerously-skip-permissions; the container is the permission model.
- Map scope — planning only; implementation handed off.

Resolved tickets:

- [Claude Code headless CLI surface](aix-01kxmdezmmx8) — headless surface documented in docs/research/claude-code-headless-cli.md (branch research/claude-headless-cli): JSON envelope schema (branch on is_error/subtype; result optional on error), always read session_id back from each envelope, skip-permissions needs non-root or IS_SANDBOX=1, stream-json's final line is the same envelope, no built-in timeout.

## Not yet specified

- Streaming/progress UX for long-running headless runs — research confirms stream-json's final line is the same result envelope, so streaming can share the parser; whether v1 buffers or streams is an API/CLI-shape question.
- Whether a dedicated per-project orchestration container becomes necessary if sharing machine state with interactive sessions proves messy — hangs on the headless-exec-mechanics ticket.

## Out of scope

- Parallel fan-out: git worktrees, branch strategies (head / merge-to-head / branch) — deferred past v1.
- Session fork (parallel conversation branching).
- Schema-validated structured output with auto-retry.
- Headless providers for harnesses other than Claude Code (the protocol is in scope; other implementations are not).