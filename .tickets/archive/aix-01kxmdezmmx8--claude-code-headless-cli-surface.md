---
id: aix-01kxmdezmmx8
title: Claude Code headless CLI surface
status: closed
type: task
priority: 2
mode: afk
created: '2026-07-16T02:53:00.948453198Z'
updated: '2026-07-16T02:59:55.630271519Z'
closed: '2026-07-16T02:59:55.630271519Z'
parent: aix-01kxmde9p330
tags:
- wayfinder:research
assignee: jonas
---

## Description

## Question

What is the exact non-interactive surface of Claude Code that the orchestration library will drive? Specifically: the `claude -p` flags relevant to headless runs; the full schema of the `--output-format json` envelope (result text, session id, cost, duration, error shapes); how `--resume <session-id>` behaves in print mode; behavior of `--dangerously-skip-permissions` when running non-interactively inside a container (root user caveats, env requirements); and any streaming output option (`--output-format stream-json`) worth knowing about for the fog item on progress UX. Primary sources: official Claude Code docs.

## Notes

**2026-07-16T02:59:55.540177184Z**

Resolution — research complete. Findings: docs/research/claude-code-headless-cli.md on branch research/claude-headless-cli (commit acf5593, local only). Verified against official docs (code.claude.com/docs) and CLI v2.1.211.

Key facts for the design tickets:
- JSON envelope (--output-format json): single object with type "result", subtype ("success" | "error_max_turns" | "error_during_execution" | "error_max_budget_usd"), is_error, result (final text, OPTIONAL on error), session_id, total_cost_usd, duration_ms, duration_api_ms, num_turns, usage/modelUsage, permission_denials, uuid. Parsers must branch on is_error/subtype.
- Resume: --resume <session-id> chains print-mode runs; lookup scoped to cwd; sessions live under ~/.claude/projects/<encoded-cwd>/. Session-id stability across resumes has varied between releases — always read session_id back from each run's envelope. --continue is racy; prefer explicit ids.
- Skip-permissions: --dangerously-skip-permissions refuses to start as root (exit 1, distinctive stderr) unless IS_SANDBOX=1; non-root avoids the issue. Managed settings can hard-disable the mode. No first-run acceptance prompt in -p mode.
- Streaming: --output-format stream-json --verbose emits NDJSON (system/init carries session_id up front; optional stream_event deltas with --include-partial-messages); final line is the same result envelope, so result parsing is shared.
- Other: no built-in run timeout (orchestrator enforces its own); exit 0 vs 1/137; auth via ANTHROPIC_API_KEY or ~/.claude/.credentials.json; determinism knobs --strict-mcp-config, --setting-sources, --tools, DISABLE_AUTOUPDATER.

**2026-07-16T02:59:55.630271519Z**

Headless surface documented in docs/research/claude-code-headless-cli.md (branch research/claude-headless-cli); envelope schema, resume, skip-permissions, and streaming facts captured for the API-shape and exec-mechanics tickets.
