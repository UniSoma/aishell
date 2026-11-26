---
id: aix-01kxmde9p330
title: Orchestration layer for aishell sandboxes
status: open
type: epic
priority: 2
mode: hitl
created: '2026-07-16T02:52:38.466589346Z'
updated: '2026-11-26T14:32:39.256425440Z'
tags:
- wayfinder:map
links:
- aix-01m1203dkknf
---

## Destination

v1 orchestration layer design locked: a babashka library (harness-provider abstraction, Claude Code as the only shipped provider) plus a thin `aishell run` CLI, executing headless runs sequentially inside the project's existing sandbox with full autonomy (`--dangerously-skip-permissions`), returning the raw + parsed `--output-format json` envelope, supporting session resume. The map is done when every v1 design decision is resolved and ordinary implementation tickets exist in knot, ready for normal execution sessions.

## Notes

- Domain: aishell — babashka CLI managing per-project Docker sandboxes for AI coding harnesses. See CONTEXT.md for the ubiquitous language (harness, sandbox, project hash, Claude isolation).
- Inspiration: sandcastle (https://github.com/mattpocock/sandcastle) — TypeScript orchestration library. We borrow use cases (implement→review pipelines, resumable headless runs), not its architecture. All tooling in babashka.
- Skills to consult per session: /grilling and /domain-modeling for decision tickets; /research for research tickets.
- Standing preference: no AI attribution in commits; lint with clj-kondo before commit.
- Interaction to watch: headless runs exec into the same container as live interactive sessions — the in-flight Claude-isolation work (shared vs project machine state) may affect this.

**2026-08-27T16:15:59.619581288Z**

ADR 0006: headless runs default to the strict security profile (see aix-01m1203dkknf). Add that to the v1 design decisions.

**2026-09-08T12:31:21.374005124Z**

herdr (https://herdr.dev) is an orthogonal host-side layer, not an input to this design: a terminal multiplexer run on the host, one sandbox per pane, launched as `HERDR_AGENT=claude aishell claude`. It needs no aishell code and does not touch the headless `docker exec` mechanics here. The open 'streaming/progress UX for long-running headless runs' item stays open — herdr answers only the supervised case (a human watching a pane), not whether `aishell run` buffers or streams. See docs/HERDR.md, which also records why herdr's control socket is never mounted into a sandbox.

**2026-11-26T14:32:39.256425440Z**

2026-11-26 decision by the user (Iris ticket review): headless runs always start their own sandbox on the strict profile; they do not join a running interactive sandbox through docker exec. This replaces the epic's locked "Container: reuse the project's existing sandbox via docker exec" choice. A headless sandbox leaves out the config's `ports` entries, so it never collides with host ports a dev sandbox already publishes; publishing on Docker-chosen host ports can be added later if host access is ever needed. Why: ADR 0006 puts headless runs on strict, and a profile is fixed at docker run, which docker exec cannot change. Next: /refine aix-01kxmdezr60p to rewrite the exec mechanics around this.

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

- [Claude Code headless CLI surface](aix-01kxmdezmmx8) — headless surface documented in this epic's document aix-01kxmde9p330-d43m7: JSON envelope schema (branch on is_error/subtype; result optional on error), always read session_id back from each envelope, skip-permissions needs non-root or IS_SANDBOX=1, stream-json's final line is the same envelope, no built-in timeout.

## Not yet specified

- Streaming/progress UX for long-running headless runs — research confirms stream-json's final line is the same result envelope, so streaming can share the parser; whether v1 buffers or streams is an API/CLI-shape question.
- Whether a dedicated per-project orchestration container becomes necessary if sharing machine state with interactive sessions proves messy — hangs on the headless-exec-mechanics ticket.

## Out of scope

- Parallel fan-out: git worktrees, branch strategies (head / merge-to-head / branch) — deferred past v1.
- Session fork (parallel conversation branching).
- Schema-validated structured output with auto-retry.
- Headless providers for harnesses other than Claude Code (the protocol is in scope; other implementations are not).