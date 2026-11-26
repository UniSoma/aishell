---
id: aix-01kxmde9p330-d43m7
ticket: aix-01kxmde9p330
title: Claude Code headless CLI surface
type: other
created: '2026-11-26T13:37:35.904788025Z'
updated: '2026-11-26T13:37:35.904788025Z'
---

# Claude Code headless CLI surface

What an orchestrator needs to drive Claude Code with `claude -p`. Checked against the official docs (code.claude.com/docs: cli-reference, headless, agent-sdk/sessions, permission-modes, env-vars, errors) and the local CLI 2.1.211 on 2026-07-16. Flags and JSON shapes move between releases: re-check `claude --help` when the pinned harness version changes. Trimmed from aishell@530a0131:docs/research/claude-code-headless-cli.md, which also lists the full flag surface.

## Invocation

- `claude -p "<prompt>"` runs one agent loop and exits. Piped stdin is read as extra context, capped at 10 MB (exit 1 beyond it).
- No `--cwd` flag: the working directory is the process cwd, so the orchestrator sets it (for aishell, the `docker exec` workdir). `--add-dir` grants more directories.
- Limits: `--max-turns N` (ends with `subtype: "error_max_turns"`), `--max-budget-usd` (`"error_max_budget_usd"`).
- Model: `--model <alias-or-id>`, `--fallback-model`.
- Tools: `--tools` restricts which built-in tools exist; `--allowedTools` / `--disallowedTools` take specifiers such as `Bash(git log:*)`.
- Reproducible runs: `--strict-mcp-config` with `--mcp-config`, explicit `--setting-sources`, `DISABLE_AUTOUPDATER=1`. `--bare` skips hooks, plugins, MCP, CLAUDE.md and OAuth, so it only fits API-key auth.

## The `--output-format json` envelope

One JSON object on stdout at the end of the run:

```json
{"type": "result", "subtype": "success", "is_error": false,
 "result": "final text", "session_id": "<uuid>",
 "total_cost_usd": 0.0042, "duration_ms": 3210, "duration_api_ms": 2100,
 "num_turns": 2, "usage": {...}, "modelUsage": {...},
 "permission_denials": [], "uuid": "<uuid>"}
```

- `subtype` is `"success"` or an error name (`error_during_execution`, `error_max_turns`, `error_max_budget_usd`, auth errors); `is_error` is true exactly when it is an error.
- On error `result` may hold the message or be absent. A parser keys on `type == "result"`, branches on `is_error`/`subtype`, and treats `result` as optional.
- `structured_output` appears only with `--json-schema`.

## Sessions

- `--resume <id>` continues a session; lookup is scoped to the cwd, so a resumed run must use the same cwd inside the container. `--continue` takes the newest session for the cwd and races when several runs share a project.
- Current docs say `--resume` keeps the id and `--fork-session` mints a new one, but older releases minted a new id on every resumed print run. Always read `session_id` back from each envelope.
- Transcripts live in `~/.claude/projects/<encoded-cwd>/<id>.jsonl` (root moves with `CLAUDE_CONFIG_DIR`). Headless and interactive sessions in one container share this machine state.

## `--dangerously-skip-permissions` in a container

- Same as `--permission-mode bypassPermissions`. It still honours explicit `ask` rules and, since 2.1.208, blocks `rm -rf /`-style deletions.
- As root (uid 0) or under sudo it refuses to start with exit 1 and `--dangerously-skip-permissions cannot be used with root/sudo privileges for security reasons`.
- `IS_SANDBOX=1` in the environment allows it as root. Running as a non-root user avoids the check entirely.
- Managed settings can disable the mode (`permissions.disableBypassPermissionsMode: "disable"`); no env var overrides that.
- The first-run risk prompt does not appear in `-p` mode.

## `--output-format stream-json`

- NDJSON, one event per line; needs `--verbose` in `-p` mode. The last line is the same `result` envelope, so the json and stream-json paths can share one parser.
- First event: `type: "system"`, `subtype: "init"`, carrying `session_id` before the run ends. Then `assistant`, `user` (tool results) and, with `--include-partial-messages`, `stream_event` token deltas.
- `--input-format stream-json` (several user messages in one process) is thinly documented; treat it as a later enhancement.

## Exit codes, timeouts, auth

- Exit 0 on success, 1 on errors (auth, blocked bypass, oversized stdin, invalid schema), 137 usually an OOM kill. A run that ends in an error subtype can still exit 0: check `is_error`, not only the exit code.
- No overall timeout. The orchestrator imposes a wall-clock limit and kills the process group. Related knobs: `API_TIMEOUT_MS` (per request, 600000), `BASH_DEFAULT_TIMEOUT_MS` (120000), `CLAUDE_CODE_PRINT_BG_WAIT_CEILING_MS` (600000).
- Auth: `ANTHROPIC_API_KEY` is used without prompting; OAuth credentials in `~/.claude/.credentials.json` are picked up automatically, which is what aishell's mounted Claude home provides.
- With `-p` and piped stdio no trust or first-run dialogs appear.

## What this means for the v1 design

1. Spawn `claude -p <prompt> --output-format json --max-turns N` with the project dir as cwd; parse one envelope.
2. Persist the `session_id` from each envelope and pass it to `--resume`; keep the container cwd and Claude home stable across resumed runs.
3. Exec as the non-root user, or set `IS_SANDBOX=1` if root is unavoidable; surface the exit-1 message clearly when bypass is blocked.
4. Wrap each run in an orchestrator-side timeout.
5. Streaming can come later through stream-json without a second result parser.
