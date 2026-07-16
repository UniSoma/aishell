# Claude Code headless CLI surface

Research notes for driving Claude Code non-interactively from an orchestration
library (aishell). Verified against the official documentation and the locally
installed CLI (`claude --version`: **2.1.211**) on 2026-07-16. Flag surface and
JSON shapes evolve between releases; re-verify against `claude --help` when
bumping the pinned harness version.

## 1. Print mode (`claude -p`) flags

`-p, --print` runs one non-interactive request/agent loop and exits.

### Prompt passing

- Positional argument: `claude -p "prompt text"`.
- Stdin: when stdin is not a TTY it is read automatically and passed as
  context; both can combine (`echo "data" | claude -p "analyze this"`).
- Piped stdin is capped at 10 MB (since v2.1.128); exceeding it exits 1 with a
  clear error on stderr.
- `--input-format text` (default) or `stream-json` (NDJSON user messages on
  stdin, enabling several messages inside one invocation — see section 5).

### Output

| Flag | Values / notes |
|------|----------------|
| `--output-format` | `text` (default), `json` (single envelope), `stream-json` (NDJSON) |
| `--verbose` | Turn-by-turn output; required with `stream-json` in `-p` mode for full event detail |
| `--include-partial-messages` | Adds raw `stream_event` token deltas; only valid with `--output-format stream-json` |
| `--json-schema <schema>` | Validates the reply against a JSON Schema; adds a `structured_output` field to the envelope |

### Model selection

- `--model <alias-or-id>` — alias (`haiku`, `sonnet`, `opus`) or a full model
  id (e.g. `claude-3-5-sonnet-20241022`).
- `--fallback-model <models>` — fallback(s) if the primary is overloaded.
- Env overrides: `ANTHROPIC_MODEL`, `ANTHROPIC_DEFAULT_{HAIKU,SONNET,OPUS}_MODEL`.

### Execution constraints

- `--max-turns <n>` — cap on agentic turns; exceeding yields a result envelope
  with `subtype: "error_max_turns"`.
- `--max-budget-usd <amount>` — stop after spending the amount (API pricing);
  `subtype: "error_max_budget_usd"` on trip.
- `--session-id <uuid>` — pin the session id (must be a valid UUID).

### Working directory

- The working directory is simply the process cwd — there is no `--cwd` flag;
  the orchestrator sets it when spawning the process.
- `--add-dir <directories>` — additional directories tools may access
  (repeatable).

### Tool allow/deny

- `--allowedTools` / `--allowed-tools` — comma- or space-separated tool names
  with optional specifiers, auto-approved without a permission prompt.
- `--disallowedTools` / `--disallowed-tools` — same syntax, denied.
- `--tools <list>` — restrict which built-in tools exist at all (`""` = none,
  `default` = all, or an explicit list like `"Bash,Edit,Read,Grep"`).
- Specifier syntax: `Bash(git log:*)` / `Bash(git commit *)` (prefix match),
  `Read(./path)`, `Edit(/src/**)` (gitignore-style), `WebFetch(domain:example.com)`.

### System prompt

- `--system-prompt <text>` / `--system-prompt-file <path>` — replace the
  default system prompt entirely.
- `--append-system-prompt <text>` / `--append-system-prompt-file <path>` —
  append to the default.
- `--exclude-dynamic-system-prompt-sections` — moves per-machine sections
  (cwd, git status, env) into the first user message to improve prompt-cache
  reuse; ignored with `--system-prompt`.

### Permissions and sessions (details in sections 3–4)

- `--permission-mode <mode>` — `default`, `acceptEdits`, `plan`, `dontAsk`,
  `bypassPermissions` (the last is what `--dangerously-skip-permissions` sets).
- `--permission-prompt-tool <mcp-tool>` — delegate permission prompts to an
  MCP tool (the only way to get interactive-style approvals in `-p` mode).
- `-c, --continue`, `-r, --resume [id]`, `--fork-session`.

### Configuration isolation

- `--settings <file-or-json>` — extra settings from a JSON file or inline JSON.
- `--setting-sources user,project,local` — which setting scopes to load.
- `--mcp-config <files-or-json>` plus `--strict-mcp-config` — use only the MCP
  servers given on the command line, ignoring user/project MCP config.
- `--bare` — minimal mode: skips hooks, plugins, MCP, CLAUDE.md discovery,
  keychain/OAuth reads; auth comes only from `ANTHROPIC_API_KEY` or an
  `apiKeyHelper` in `--settings`.

## 2. `--output-format json` envelope

A single JSON object on stdout at the end of the run:

```json
{
  "type": "result",
  "subtype": "success",
  "is_error": false,
  "result": "The final text response...",
  "session_id": "550e8400-e29b-41d4-a716-446655440000",
  "total_cost_usd": 0.0042,
  "duration_ms": 3210,
  "duration_api_ms": 2100,
  "num_turns": 2,
  "usage": {
    "input_tokens": 512,
    "output_tokens": 256,
    "cache_read_input_tokens": 0
  },
  "modelUsage": {
    "claude-3-5-sonnet-20241022": {
      "input_tokens": 512,
      "output_tokens": 256,
      "cache_read_input_tokens": 0
    }
  },
  "permission_denials": [],
  "uuid": "550e8400-e29b-41d4-a716-446655440001"
}
```

Field notes:

| Field | Meaning |
|-------|---------|
| `type` | Always `"result"` for the final envelope |
| `subtype` | `"success"`, or an error name: `"error_during_execution"`, `"error_max_turns"`, `"error_max_budget_usd"`, auth errors, ... |
| `is_error` | `true` iff `subtype` is an error |
| `result` | Final assistant text; on error it holds the error message or may be absent — parsers must treat it as optional |
| `session_id` | UUID of the session; feed to `--resume` |
| `total_cost_usd` | Cost at API pricing (input + output + cache) |
| `duration_ms` / `duration_api_ms` | Wall clock vs. time spent in API calls |
| `num_turns` | Agent-loop iterations |
| `usage` / `modelUsage` | Aggregate and per-model token counts |
| `structured_output` | Present only with `--json-schema`; the validated object |
| `permission_denials` | Tool calls blocked by permission rules |
| `uuid` | Unique id of this result event |

Error variant: same envelope shape with `is_error: true` and an error
`subtype`; do not assume `result` is present. A robust parser keys on
`type == "result"`, then `is_error`/`subtype`, and treats `result` as optional.

## 3. `--resume` and `--continue` in print mode

- `--continue` / `-c`: resumes the **most recent** session for the current
  working directory. No id needed, but racy if multiple sessions touch the
  same project — an orchestrator should prefer explicit ids.
- `--resume <session-id>`: resumes that specific session; lookup is scoped to
  the current working directory (running from a different cwd will not find
  it).
- Chaining works: capture `session_id` from the JSON envelope of run N and
  pass it as `--resume` for run N+1, repeatedly.
- Session id stability: per current docs, plain `--resume`/`--continue`
  continues under the **same** session id; `--fork-session` mints a **new**
  id, branching from the existing history (useful for exploring alternatives
  without polluting the original transcript). Older releases generated a new
  id on every resumed print run, so an orchestrator should **always read
  `session_id` back from each run's result envelope** rather than assuming
  the id it passed in is still current.
- Storage: `~/.claude/projects/<encoded-cwd>/<session-id>.jsonl` (cwd encoded
  by replacing non-alphanumerics with `-`; root overridable via
  `CLAUDE_CONFIG_DIR`). Sessions are plain JSONL transcripts — they can be
  copied between machines/containers to the same encoded path to resume
  elsewhere.

## 4. `--dangerously-skip-permissions` in containers

- Equivalent to `--permission-mode bypassPermissions`. Skips all permission
  prompts, with residual exceptions: explicit `ask` permission rules, MCP
  tools marked as requiring user interaction, and (since v2.1.208) a circuit
  breaker that still blocks `rm -rf /` / `rm -rf ~` style root/home deletions
  even in bypass mode.
- **Root restriction**: when running as root (uid 0) or under sudo, the CLI
  refuses to start with
  `--dangerously-skip-permissions cannot be used with root/sudo privileges for
  security reasons` and **exit code 1**.
- **Escape hatch**: `IS_SANDBOX=1` in the environment allows bypass mode as
  root inside a recognized sandbox (dev containers etc.). This is exactly the
  containerized-orchestrator case:
  `IS_SANDBOX=1 claude --dangerously-skip-permissions -p "..."`.
  Running as a non-root user inside the container avoids the restriction
  entirely and is the cleaner posture.
- **Config gate**: managed settings can disable the mode via
  `permissions.disableBypassPermissionsMode: "disable"`; if so, the flag is
  rejected regardless of env vars.
- The interactive first-run "accept the risk" prompt for bypass mode does not
  apply in `-p` mode; the flag takes effect directly.
- `--allow-dangerously-skip-permissions` merely makes the mode *available*
  (interactive mode-cycling) without activating it — not relevant headlessly.

## 5. `--output-format stream-json`

- NDJSON: one JSON object per line on stdout; the **last** line is the same
  `result` envelope as `--output-format json`, so a streaming consumer gets
  progress *and* the final summary from one stream.
- In `-p` mode, use with `--verbose`; add `--include-partial-messages` for
  token-level deltas.
- Event types (top-level `type`):
  - `system` with `subtype: "init"` — first event; carries `session_id`
    (available immediately, before completion), `model`, `tools`,
    `mcp_servers`. Other `system` subtypes: `api_retry`, `plugin_install`.
  - `assistant` — each complete assistant message (content blocks incl. tool
    use).
  - `user` — tool results / echoed user messages (`--replay-user-messages`).
  - `stream_event` — raw API streaming events (`message_start`,
    `content_block_delta` with `delta.type: "text_delta"` etc.); only with
    `--include-partial-messages`.
  - `result` — final envelope.
- Every event carries `session_id` and `uuid`; subagent output can be
  attributed via `parent_tool_use_id` (see `--forward-subagent-text`).
- Live progress extraction is straightforward, e.g.:

  ```bash
  claude -p "..." --output-format stream-json --verbose --include-partial-messages \
    | jq -rj 'select(.type == "stream_event" and .event.delta.type? == "text_delta")
              | .event.delta.text'
  ```

- `--input-format stream-json` accepts NDJSON user messages on stdin
  (`{"type":"user", ...}` lines), enabling multi-message bidirectional runs in
  a single process; documentation here is thinner (tracked in upstream GitHub
  issues), so treat as a later enhancement, not the first orchestration
  primitive.

**Feasibility verdict**: live progress streaming is practical — parse NDJSON
lines, surface `assistant`/`stream_event` content as progress, and read the
final `result` line as the run outcome.

## 6. Other orchestration facts

- **Exit codes**: `0` success; `1` on errors (auth failure, blocked bypass
  mode, oversized stdin, invalid schema, prompt failure); `137` typically
  OOM-kill. Note that "the agent ran but ended in an error subtype" can still
  exit 0 in some variants — check `is_error` in the envelope, not just the
  exit code.
- **Timeouts**: no built-in overall session timeout — the orchestrator must
  impose its own wall-clock limit. Related env knobs: `API_TIMEOUT_MS`
  (per-API-request, default 600000), `BASH_DEFAULT_TIMEOUT_MS` (tool-level
  Bash timeout, default 120000), `CLAUDE_CODE_PRINT_BG_WAIT_CEILING_MS`
  (max wait for background subagents in `-p` mode, default 600000; `0` =
  unlimited).
- **Auth**:
  - `ANTHROPIC_API_KEY` — direct API key; in `-p` mode used without any
    approval prompt.
  - OAuth (subscription) credentials live in `~/.claude/.credentials.json`
    and are picked up automatically when present — this is what a mounted
    per-project Claude home gives the container. Not read in `--bare` mode.
  - `ANTHROPIC_AUTH_TOKEN` for custom Bearer tokens; provider-specific keys
    exist for Bedrock/Vertex/Foundry.
- **Non-TTY**: with `-p` and piped stdio, no interactive prompts occur —
  workspace-trust and first-run dialogs are skipped; invalid settings fail
  without dialogs. Without `-p`, a non-TTY stdin still lands in
  non-interactive handling, but an orchestrator should always pass `-p`
  explicitly.
- **Useful env vars**: `CLAUDE_CONFIG_DIR` (relocate `~/.claude`),
  `DISABLE_AUTOUPDATER`, `DISABLE_TELEMETRY`, `MAX_THINKING_TOKENS`,
  `CLAUDE_CODE_MAX_OUTPUT_TOKENS`, `CLAUDE_CODE_RETRY_WATCHDOG` (retry
  transient 429/529 errors), `CLAUDECODE=1` (set in subprocesses spawned by
  Claude Code, handy for detection). Precedence: process env vars > settings
  file `env` block > managed settings.

## Implications for aishell orchestration

1. **Invocation shape**: spawn
   `claude -p <prompt> --output-format json --max-turns N` with cwd set to the
   project workspace; parse a single JSON envelope from stdout. Treat
   `result` as optional and branch on `is_error`/`subtype`.
2. **Session chaining**: persist the `session_id` returned in each envelope
   and pass it via `--resume` for follow-up turns; never assume the id you
   passed in is the id that comes back. Sessions live under
   `~/.claude/projects/<encoded-cwd>/`, so aishell's per-project Claude-home
   isolation automatically isolates session state too — but it also means the
   container's cwd and Claude home must be stable across resumed runs.
3. **Permissions**: inside the sandbox, either run Claude as a non-root user
   with `--dangerously-skip-permissions`, or set `IS_SANDBOX=1` if root is
   unavoidable. Expect exit 1 with a distinctive stderr message if bypass is
   blocked, and surface that clearly.
4. **Timeout is on us**: wrap each run in an orchestrator-side wall-clock
   timeout and kill the process group on expiry; the CLI will not stop itself.
5. **Streaming later**: `--output-format stream-json --verbose` is a clean
   upgrade path for live progress — same final envelope on the last line, so
   the result-parsing code is shared with the plain-json path.
6. **Reproducibility**: consider `--strict-mcp-config`, explicit
   `--setting-sources`, `--tools`/`--allowedTools`, and `DISABLE_AUTOUPDATER=1`
   to keep container runs deterministic and independent of host user config.

## Sources

Consulted 2026-07-16 (official Claude Code documentation; `docs.claude.com`
Claude Code pages redirect to `code.claude.com/docs`):

- https://code.claude.com/docs/en/cli-reference — CLI reference (flag list)
- https://code.claude.com/docs/en/headless — headless / print-mode guide,
  JSON and stream-JSON output
- https://code.claude.com/docs/en/agent-sdk/overview — Agent SDK overview
- https://code.claude.com/docs/en/agent-sdk/streaming-output — stream-json and
  partial messages
- https://code.claude.com/docs/en/agent-sdk/sessions — sessions: continue,
  resume, fork, storage layout
- https://code.claude.com/docs/en/env-vars — environment variables
- https://code.claude.com/docs/en/permissions — permission rules and tool
  specifier syntax
- https://code.claude.com/docs/en/permission-modes — bypassPermissions
  behavior and root restriction
- https://code.claude.com/docs/en/errors — errors and exit codes
- Local CLI: `claude --help`, `claude --version` (2.1.211)
