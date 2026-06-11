---
name: knot
description: Use when working in a knot-tracked project, signaled by `.knot.edn` or `.tickets/` at any ancestor of cwd, ids matching `<prefix>-01<base32>`, or intent like "what's next?", "what's blocked?", "list tickets", "show the backlog", "any pending bugs?", "what's open?", "what's tagged <x>?", "my tickets", "show me <id>", "track this", "open a ticket", "start <id>", "close this", "ship it", "add a note", or an autonomous agent told to pick up unblocked work. Do NOT use for hosted trackers (GitHub Issues, Linear, Jira, Basecamp, Asana, Trello) or for ids prefixed with hosted-tracker shortcodes (`GH-1234`, `ENG-1234`, `LIN-1234`, `JIRA-PROJ-1234`) — those have their own tools.
---

# knot — file-based CLI ticket tracker

knot stores each ticket as a markdown file with YAML frontmatter under
`.tickets/`. Closed tickets auto-move to `.tickets/archive/`. Configuration
lives in `.knot.edn` at the repo root (or any ancestor — knot walks up).
Verify cwd is inside the project root before running commands; running
from a parent directory may quietly pick up a different knot project.

If `.knot.edn` and `.tickets/` are both absent and the user wants to start
tracking work with knot, run `knot init`. Don't init without an explicit
signal — the user may already use a different tracker.

## The one rule: use the CLI

**Read tickets only via** `knot show` / `knot list` / `knot ready` /
`knot blocked` / `knot closed` / `knot prime`.

**Write tickets only via** `knot create` / `knot start` / `knot status` /
`knot close` / `knot reopen` / `knot delete` / `knot add-note` /
`knot edit` / `knot update` / `knot dep` / `knot link`.

**Validate project integrity via** `knot check` (cycles, dangling refs,
schema, archive placement).

Never `cat .tickets/<id>--*.md`, `grep -r ... .tickets/`, `vim .tickets/...`,
write a new file under `.tickets/` by hand, or `mv` files between
`.tickets/` and `.tickets/archive/`.

Why this matters:

- `knot` keeps `:updated` and the computed graph consistent on every write.
  A hand-edit silently drifts.
- `knot` resolves partial IDs across both live and archive. File globs miss
  archived tickets entirely.
- `knot close` routes the file from `.tickets/` to `.tickets/archive/`. A
  hand-edit that flips `status: closed` leaves the file in the wrong
  directory and breaks future queries.

If a `knot` command behaves unexpectedly, surface the bug to the user.
Don't reach for `vim`, `sed`, `cat`, or `mv`. **The CLI is the contract**
— `.tickets/` is an implementation detail. If knot's surface area can't
express what you need, that's a knot bug; file it, don't work around it.

### Red flags — STOP

| Rationalization | Reality |
|---|---|
| "I'll just cat the file once to verify the close worked." | `knot show <id>` works on archived tickets too. |
| "I'll `knot create` then `knot show <new-id>` to verify what landed." | `knot create --json` already returns the full post-mutation ticket under `.data` — no chain needed. Same for every write command. See *JSON for parsing*. |
| "I just need to peek at `.knot.edn` for the allowed statuses." | `knot prime --json` exposes the schema. |
| "knot show failed, let me read the markdown directly." | Surface the bug. The file is not the contract. |
| "I want to see all tickets at once, `ls .tickets/` is faster." | `knot list --json` is stable and sees archive. `ls` doesn't. |
| "The user's in a hurry, I'll grep once and move on." | Greppable now, broken later. `knot list --json | jq` instead. |
| "I'll list everything and scan the TYPE column for bugs." | `knot list --type bug`. Filters exist on every read command — use them. |

### Tool mapping — what to reach for

The rule is easier to internalize at the tool-call level. Before invoking
one of these against `.tickets/`, switch to the knot equivalent:

| Tempted to use… on `.tickets/` | Use this instead |
|---|---|
| `Read` / `cat` / `head` / `tail` | `knot show <id>` |
| `Grep` / `grep` / `rg` | `knot list --json \| jq '.data[] \| …'` |
| `ls` | `knot list` (or `knot list --json`) |
| `Write` (new file) | `knot create "<title>" -d "..."` |
| `Edit` (modify file) | `knot add-note <id> "..."` (additive), `knot update <id> --title ... --description ...` (non-interactive set/replace), or `knot edit <id>` (interactive) |
| `Bash` + `mv` to `archive/` | `knot close <id> --summary "..."` |
| `Bash` + `mv` from `archive/` | `knot reopen <id>` |
| `Bash` + `rm` on a ticket file | `knot delete <id>` (refuses when other tickets reference the target — drop the refs first, or use `--cascade` to rewrite them) |
| `sed -i` to flip `status:` | `knot status <id> <new>` |

### Already primed?

If a `<system-reminder>` from `SessionStart` already injected `knot
prime` output (look for it near the top of the conversation), don't
re-run `knot prime`. The state there is current as of session start; for
fresher state run `knot list`, `knot ready`, or `knot show <id>` directly.

## Translating user intent → command

| User says…                                              | You run…                                                     |
|---------------------------------------------------------|--------------------------------------------------------------|
| "what's next?" / "what should I pick up?"               | `knot ready`                                                 |
| "what should an agent work on?"                         | `knot ready --mode afk`                                      |
| "show me the backlog" / "list tickets"                  | `knot list`                                                  |
| "any pending bugs?" / "what bugs are open?"             | `knot list --type bug`                                       |
| "what's afk?" / "what can an agent grab?"               | `knot ready --mode afk` (or `knot list --mode afk`)          |
| "what's tagged <x>?"                                    | `knot list --tag <x>`                                        |
| "what's open for <user>?" / "my tickets"                | `knot list --assignee <user>`                                |
| "what's blocked?"                                       | `knot blocked`                                               |
| "what did I close recently?"                            | `knot closed --limit 10`                                     |
| "what's ready to close?" / "what's done?"               | `knot prime` (Ready to close section) — active tickets whose AC are all checked |
| "show me <id>" / "tell me about <id>"                   | `knot show <id>`                                             |
| "let's start <id>" / "begin <id>"                       | `knot show <id>`, then `knot start <id>`                     |
| "I'm done" / "shipped" / "close this"                   | `knot close <id> --summary "<what shipped>"`                 |
| "reopen <id>"                                           | `knot reopen <id>`                                           |
| "track this as a bug" / "open a ticket for X"           | `knot create "<title>" -t bug …`                             |
| "note that…" / "FYI mid-task"                           | `knot add-note <id> "…"`                                     |
| "retitle <id> to …" / "retag <id> with …" / "set …"     | `knot update <id> --title "…"` / `--tags …` / etc.           |
| "blocked on <other>"                                    | `knot dep <current> <other>`                                 |
| "what's blocking <id>?"                                 | `knot dep tree <id>`                                         |
| "these are related: a, b, c"                            | `knot link <a> <b> <c>`                                      |
| "validate the project" / "any integrity issues?"        | `knot check`                                                 |
| "scan for cycles" / "any dep cycles?"                   | `knot check --code dep_cycle`                                |
| "give me a summary of project state"                    | `knot prime`                                                 |
| "what project is this?" / "what statuses are valid?"    | `knot info`                                                  |
| "what does `knot create` default to?"                   | `knot info --json`                                           |
| "give me the frontmatter JSON Schema"                   | `knot schema` (writes to stdout; `bb gen:schema` updates the checked-in file) |

### Filter, don't eyeball

When the user's question targets a *subset* — a type, mode, tag, status,
assignee, or priority — pass the matching filter rather than running bare
`list` / `ready` / `blocked` / `closed` / `prime` and scanning the columns.
All five listing commands accept the same filter set (each repeatable):

```
--type <type>      --mode <afk|hitl>    --tag <tag>
--status <status>  --assignee <user>    --priority <0-4>
--limit <n>
```

Combine freely: `knot list --type bug --type chore`, `knot ready --mode
afk --tag p0`, `knot ready --priority 0`, `knot blocked --mode afk`,
`knot closed --type bug --limit 5`.
On `prime`, filters apply across **all** sections (in_progress + ready +
recently_closed) — `knot prime --assignee me` shows only your tickets
everywhere. Visual filtering is error-prone (titles wrap, columns shift,
archived tickets are absent) and harder for the user to verify. Reach for
bare `list` only when the user actually wants the full picture.

When the user gives a partial id (`01kqa9`), pass it through verbatim —
knot resolves it across live + archive. If it's ambiguous, knot prints
candidates; relay them, don't guess.

## Writing tickets

### Create

`knot create "<title>" [flags]` is the only way to create a ticket. Run
`knot create --help` for the full flag list. Most-used flags:

- `-t / --type` (default `task`)
- `-p / --priority` 0 (highest) … 4 (default 2)
- `-a / --assignee`
- `--mode afk` / `--mode hitl` (default `hitl`)
- `--tags`, `--parent`, `--external-ref`
- `-d / --description`, `--design` for body sections
- `--acceptance "<title>"` (repeatable) appends a structured
  acceptance criterion to frontmatter. Each entry is stored as
  `{title, done: false}`; `knot show` synthesizes a `## Acceptance
  Criteria` checklist from these at display time. There is no body
  section to author by hand.
- `--dep <id>` / `--link <id>` (both repeatable, one id per
  occurrence) wire the new ticket into the graph at create time.
  Asymmetry on missing targets:
  - `--dep` is **lenient** — an unresolved id is kept verbatim as a
    forward ref (matches `knot dep`'s tolerant-target contract).
  - `--link` is **strict** — every target must resolve uniquely, or
    the command fails before any file is written. Plain text reports
    `knot create: ...`; `--json` returns a `not_found` /
    `ambiguous_id` error envelope.
  Both flags accept partial ids, dedupe equivalents that resolve to
  the same ticket (preserving first-occurrence order), and may name
  archived targets — a reciprocal `--link` write does not unarchive
  the target. `--dep X --link X` records both. If multiple strict
  inputs are bad, the first failure in left-to-right CLI order wins.

Always pass `--description` when there's any context worth saving — a
title-only ticket forces the next reader to reconstruct intent from
scratch. Default `--mode afk` when the work is well-specified and an
agent could run end-to-end without a human; otherwise leave the `hitl`
default.

To verify what landed in a single invocation, pass `--json`. The
envelope's `.data` carries the full post-mutation ticket (id,
frontmatter, body) — same shape as `knot show --json` minus the four
computed inverse arrays (`blockers`/`blocking`/`children`/`linked`).
**Don't chain `knot show <id>` after a write to read back what you just
wrote** — the data is already in the write envelope. This applies to
every mutating command: `create`, `update`, `add-note`,
`status`/`start`/`close`/`reopen`, `dep`/`undep`, `link`/`unlink`. See
*JSON for parsing* for per-command payload details.

For multi-line prose flags, use a quoted-delimiter heredoc so `$vars`,
backticks, and quotes pass through literally:

```sh
knot create "Title" -t bug -p 1 --description "$(cat <<'EOF'
body with `code`, $vars, and "quotes" — all literal
EOF
)"
```

`knot add-note <id>` reads stdin natively — pipe directly:

```sh
knot add-note <id> <<'EOF'
note body
EOF
```

### Lifecycle

```sh
knot start <id>                                # → in_progress
knot status <id> <new-status>                  # generic transition
knot close <id> --summary "shipped in #482"
knot reopen <id>                               # restore from archive
knot delete <id>                               # remove the file (leaf-only)
knot delete <id> --cascade                     # also rewrite every referrer
```

Always pass `--summary` to `knot close`. The summary becomes a timestamped
note and is the most useful artifact for "what did we ship recently?"
later. Skipping it loses information for free.

`knot delete <id>` is the destructive twin of `close` — useful for
typo'd `create`s, AI-generated duplicates, and pruning archive noise.
Leaf-only by default: it **refuses** (exit 1) when any other ticket —
live or archived — references the target via `:parent`, `:deps`, or
`:links`. The refusal enumerates each referrer + the field. The bare
command doubles as the dry-run for `--cascade` (same scan, same
referrer list).

`knot delete <id> --cascade` opts into the rewrite: every referrer
(live + archive) has the target dropped from `:deps`/`:links` and its
`:parent` dissoc'd; the resulting field key is dropped when its list
empties (mirrors `undep` / `unlink`). Each cleaned referrer's
`:updated` is bumped — including archived ones. Write order is
referrers first (alphabetical by id), target last; a mid-batch save
failure emits a stderr breadcrumb and aborts before unlinking the
target, so `unknown_id` never leaks. Re-running `--cascade` is
idempotent (already-cleaned referrers are no-ops). Stderr gets one
`knot delete: cleaned <id> (:field, ...)` line per cleaned referrer;
stdout is still just the removed path. `--cascade` on a leaf (zero
referrers) is a silent no-op.

There is no undo — `.tickets/` is git-tracked; `git checkout` is the
documented recovery path. `--json` returns
`{ok:true, data:{deleted:{id,path}, cleaned:[{id,fields:[...]}]}}` on
success and the `has_incoming_refs` error envelope on refusal.

For projects with custom `:statuses` (e.g. adding `"review"` between
`in_progress` and `closed`), prefer explicit `knot status <id> <new>` over
`knot start` / `knot close` so you don't accidentally skip a non-terminal
stage.

#### Acceptance gate on terminal transitions

`knot close`, `knot status <id> <terminal>`, and `knot update <id>
--status <terminal>` all enforce the v0.3 acceptance gate: when the
ticket is in `:active-status` (default `in_progress`) and any
frontmatter `:acceptance` entry has `done: false`, the transition is
blocked. Plain text:

```
knot close: 2 of 2 acceptance criteria are unchecked
  - first AC
  - second AC
use 'knot update <id> --ac "<title>" --done' for each one, or --force --summary "<reason>" to override.
```

JSON: `error.code = "acceptance_incomplete"`, `error.open_acceptance =
[{title}, ...]`, exit 1.

The gate skips on:

- Empty / nil `:acceptance`.
- Intake → terminal transitions (no work was started).
- Terminal → terminal reclassifications (e.g. `closed → wontfix`).

Two ways to clear it:

1. Mark the AC done — `knot update <id> --ac "<title>" --done`. Composes
   with `--status` in one call: `knot update <id> --ac "last AC" --done
   --status closed` checks then closes.
2. `--force --summary "<reason>"`. Required pair: `--force` without a
   non-blank `--summary` exits `invalid_argument`. The summary is
   appended as a Notes entry and serves as the override record.

#### Open-children gate on start and close transitions

The open-children gate fires on two transitions:

- **Close** (`active → terminal`): `knot close`, `knot status <id>
  <terminal>`, `knot update <id> --status <terminal>`.
- **Start** (`* → active`): `knot start`, `knot status <id> <active>`,
  `knot update <id> --status <active>`.

The gate fires when the ticket has at least one child (any ticket
whose `:parent` is this id) whose status is non-terminal. Plain text
(close):

```
knot close: 1 open child blocks this close
  - kno-01krpb3nkfjq
close each child first, or pass --force --summary "<reason>" to ship the umbrella as-is.
```

Plain text (start):

```
knot start: 1 open child blocks this start
  - kno-01krpb3nkfjq
close each child first, or pass --force to start the umbrella anyway.
```

JSON: `error.code = "open_children"`, `error.open_children = [<id>, ...]`,
exit 1 — same envelope shape for both gates.

The gate skips on:

- Tickets with no children.
- Parents whose children are all in a terminal status.
- `active → active` no-op transitions and intake → terminal
  transitions (no meaningful start or close).
- Terminal → terminal reclassifications.

Override is `--force`, with asymmetric `--summary` semantics:

- **Close**: `--force --summary "<reason>"` is the required pair —
  `--force` without a non-blank `--summary` exits `invalid_argument`.
  The summary is appended as a Notes entry and serves as the override
  record. When both AC and open-children gates would fire on the
  same close, a single `--force` bypasses both and stderr emits one
  warning per gate.
- **Start**: `--force` alone is enough (no `--summary` required, and
  passing `--summary` to a non-terminal target is rejected up front).
  Start is provisional — you can `update --status` back to intake at
  zero cost — so the bypass leaves only the stderr enumeration as a
  trace, not a Notes entry.

### Notes and editing

```sh
knot add-note <id> "raced GC under load"      # one-shot, append-only
knot add-note <id>                            # opens $EDITOR
knot edit <id>                                # opens whole file in $EDITOR
knot update <id> --priority 0 --tags p0,auth  # non-interactive set/replace
knot update <id> --description "New desc."    # replace ## Description in place
knot update <id> --body "Plain body."         # destructive whole-body replace
```

Prefer `knot add-note` for capturing observations mid-task. For
**non-interactive** revisions (autonomous agents, scripts), use `knot
update <id> [flags...]` — it sets/replaces frontmatter and named body
sections in one shot, returns the post-mutation ticket via `--json`,
and never opens an editor. Reach for `knot edit` only in interactive
sessions to free-form a file in `$EDITOR`; in an autonomous run with
no terminal, `knot edit` will fail.

Flag set on `knot update`:

- Frontmatter: `--title`, `--type`, `--priority`, `--mode`,
  `--assignee`, `--parent`, `--tags` (comma-list), `--external-ref`
  (repeatable). Pass `""` (or no values for `--external-ref`) on
  optional fields to clear them; `--tags ""` clears all tags.
- Tag deltas: `--add-tag <t>` / `--remove-tag <t>` apply per-tag
  changes without round-tripping the full list (repeatable; mutually
  exclusive with `--tags`). Idempotent per tag; existing order is
  preserved, removes drop in place, adds append at the end. An empty
  resulting set clears `:tags`.
- Body sections (replace in place; create if missing):
  `--description`, `--design`.
- Acceptance flip: `--ac "<title>" --done` (or `--undone`) toggles
  the `:done` state of one frontmatter `acceptance` entry. The title
  must match exactly. `--done` and `--undone` are mutually exclusive;
  `--ac` requires one of them.
- Acceptance deltas: `--add-ac "<title>"` / `--remove-ac "<title>"`
  add or remove AC entries (repeatable; idempotent on exact-match
  title). Adds append with `done: false`; removes drop in place;
  emptying the list clears the `:acceptance` key. Composes with
  `--ac --done/--undone` in a single call — apply order is
  **add → flip → remove**, so a flip can target a just-added title.
  The same title in both directions exits 1 `invalid_argument`.
- Whole body: `--body <text>` — destructive, mutually exclusive with
  the sectional flags. There is **no `--force`** for `--body`; git is
  the documented undo path. The `## Acceptance Criteria` section in
  the body is **display-only on write** — `--body` does not sync the
  section back to frontmatter; use `--add-ac` / `--remove-ac` / `--ac`
  to mutate criteria.
- Status transition: `--status <new>`. AC mutations apply *before* the
  acceptance gate, so `knot update <id> --ac "last AC" --done --status
  closed` checks then closes in one call. `--summary` is required on
  terminal targets when overriding the gate; see
  *Acceptance gate on terminal transitions* above.
- `--force` (with `--summary`) bypasses the acceptance gate on a
  terminal `--status` transition. Silent no-op when the gate would
  not fire.
- `--json` returns the v0.3 envelope wrapping the post-mutation
  ticket (no `:meta` slot — `update` never archives).

`update` is purely set/replace. To **append** to a body, use
`add-note` instead — that's its job.

### Graph operations

```sh
knot dep <from> <to>            # <from> waits on <to>; cycle-checked on add
knot dep tree <id>              # ASCII tree; --full to expand dups
knot undep <from> <to>

knot link <a> <b> [<c>...]      # symmetric peer link across every pair
knot unlink <from> <to>
```

`deps` are directional ("blocks") and honored by `knot ready`. `links`
are symmetric ("see also"). Use `dep` when one ticket has to wait on
another; use `link` for "here's context". `knot dep` rejects
cycle-creating edges at write time; to scan an already-corrupted graph
(e.g. after a hand-edit) use `knot check --code dep_cycle`.

## Project integrity

```sh
knot check                      # validate every ticket + config; exit 0/1/2
knot check <id>...              # narrow per-ticket checks; globals still run
knot check --code dep_cycle     # filter by issue code (repeatable)
knot check --severity error     # filter by severity (closed enum)
knot check --json               # envelope; data.issues sorted, data.scanned counts
```

`knot check` walks every ticket (live + archive) and the config and
emits issues for: dep cycles, dangling `:deps`/`:links`/`:parent` ids,
invalid status/type/mode/priority, terminal-vs-archive placement,
missing required fields, frontmatter parse errors, and an
invalid-`:active-status` config. Filters apply *before* the exit-code
decision (grep semantics). Exit `2` means unable to scan (no project
root or invalid `.knot.edn`) — different from `1` (errors found in
the filtered view).

## AFK vs HITL: agent-runnable work

`mode` is a peer dimension to status and priority:

- `afk` = an agent can run this alone, no human in the loop
- `hitl` = needs a human (default for new tickets)

`knot ready --mode afk` is the canonical "what can an agent grab?" query.
When **you** are the agent and the user has handed you autonomy, run the
checklist:

- [ ] `knot prime --mode afk` (skip if prime is already in the session)
- [ ] `knot ready --mode afk --json` to enumerate candidates
- [ ] `knot show <id>` to confirm scope
- [ ] `knot start <id>` to claim
- [ ] `knot add-note <id> "<progress>"` after non-trivial milestones
- [ ] `knot update <id> --priority …` / `--tags …` for non-interactive frontmatter or section revisions (never `knot edit` — it opens `$EDITOR` and will fail without a TTY)
- [ ] `knot close <id> --summary "<what landed>"` when shipped

Don't autonomously pick up `hitl` tickets unless the user explicitly
authorizes that ticket. The mode is the contract.

## JSON for parsing

Every read AND mutating command accepts `--json` and emits a tagged
envelope on stdout with snake_case keys. Warnings and errors go to
stderr. The canonical contract lives in
[`references/json-protocol.md`](references/json-protocol.md) — per-command
`data` shapes, the full error-code catalogue, the `knot check`
issue-code catalogue, and the partial-id contract are pinned there
(and in the knot repo, exercised by `test/knot/json_contract_test.clj`).

```json
{"schema_version": 1, "ok": true, "data": <payload>}
```

The actual payload sits at `.data`. List-shaped commands (`list`,
`ready`, `blocked`, `closed`) put an array there; object-shaped
commands (`show`, `dep tree`, `prime`, `check`) put an object. On
errors (e.g. `show <missing-id>`), the envelope flips to
`{schema_version: 1, ok: false, error: {code, message, ...}}` with no
`data` slot. `knot check` is the one exception: it may emit
`{ok: false, data: {...}}` because `ok` mirrors a health verdict.

Mutating commands (`create`, `start`, `status`, `close`, `reopen`,
`dep`, `undep`, `link`, `unlink`, `add-note`, `update`) put the
touched ticket under `.data` — eliminating the read-after-write
round-trip. Lifecycle commands, `add-note`, and `update` emit a
single ticket object (body included). `dep`/`undep` emit the `from`
ticket with the updated `:deps`. `link`/`unlink` emit an array of
every touched ticket (body excluded, ls-shape). `delete --json`
emits `{deleted:{id,path}, cleaned:[{id,fields:[...]}]}` instead of a
ticket payload (the target is gone); `cleaned` is `[]` without
`--cascade` and populated otherwise. `close --json` (and any `status`
transition to a terminal status) adds `meta.archived_to` with the
archive path:

```json
{"schema_version": 1, "ok": true, "data": {...ticket...},
 "meta": {"archived_to": ".tickets/archive/kno-01abc--…md"}}
```

`archived_to` is POSIX-normalized (forward slashes) on every platform — Windows callers don't have to branch on `os.name`. The same rule applies to `info --json` path fields (`paths.*`).

Mutating-command error envelopes mirror the read-side contract:
missing ids emit `{ok:false, error:{code:"not_found", ...}}`
(exit 1); partial-id ambiguity emits `code: "ambiguous_id"` with a
`candidates` array; `dep --json` cycle rejection emits `code:
"cycle"` with the offending path under `error.cycle`; `delete --json`
refusal emits `code: "has_incoming_refs"` with a `referrers`
array. `info --json` adds `invalid_argument` (unknown flag), and
both `info --json` and `check --json` add `no_project` /
`config_invalid` for discovery failures (exit 1 / exit 2
respectively). For most other commands, argument-parsing errors stay
on stderr — see
[`references/json-protocol.md`](references/json-protocol.md) for the
full code catalogue.

Ticket payloads always carry `tags`, `deps`, `links`, and
`external_refs` as arrays — `[]` when unset, populated otherwise — so
`jq -r '.data[].tags[]'` is safe regardless of which tickets have
tags. On-disk YAML still omits these fields when empty; the `[]`
default is injected at the JSON boundary only.

```sh
knot list --json           | jq '.data[] | select(.priority <= 1)'
knot ready --json --mode afk
knot show <id> --json      | jq -r '.data.title'
knot prime --json
knot check --json          | jq '.data.issues'   # integrity issues, if any

# Pick the highest-priority unblocked afk ticket, id only:
knot ready --json --mode afk | jq -r '.data | sort_by(.priority) | .[0].id'

# Mutate then read the post-state in one shot:
knot start <id> --json       | jq -r '.data.status'
knot close <id> --json       | jq -r '.meta.archived_to'
knot create "T" --json       | jq -r '.data.id'
knot add-note <id> "x" --json | jq -r '.data.body'
knot update <id> --priority 0 --tags p0 --json | jq -r '.data.priority'
```

For any decision logic, prefer `--json | jq` over parsing tables. Don't
pipe table output through `awk`/`grep` — column widths shift and titles
can contain whitespace. `--json` is stable.

`prime --json` flags stalled in-progress work with `stale: true` (set when
`:updated` is 14+ days old), but the flag appears **only on `in_progress`
entries** — `ready` copies of the same ticket never carry it. Iterate
`.in_progress` (not `.ready`) when hunting for forgotten work.

`prime --json` also exposes `data.ready_to_close` — a parallel array to
`in_progress` and `ready` carrying active-status tickets whose every
acceptance entry is checked. Same body-less compact ticket shape; no
derived `acceptance_progress` field. The same tickets do **not** appear
in `data.in_progress` (mutually exclusive partition). Vacuously-complete
tickets (no AC list) deliberately do not migrate — only tickets with an
explicit fully-checked checklist count. The text renderer surfaces the
matching `## Ready to close` section between `## In Progress` and
`## Ready`, omitted entirely when empty.

Listing tables (`knot ls`, `knot ready`, `knot blocked`, `knot closed`)
gain a conditional `AC` column rendered as `d/t` (e.g. `2/5`)
immediately before `TITLE`. The column is omitted entirely when no
ticket in the result set has acceptance, so quiet projects don't pay
the width cost. Tickets without AC render as `-`. Force-closed terminal
tickets render their partial counts (`2/5`) — useful audit signal when
scanning archive. `ls --json` is unchanged: raw `:acceptance` already
passes through.

Every listing table also carries an `AGE` column to the immediate
left of `AC` (or `TITLE` when `AC` is absent), bucketed from each
ticket's `:updated` against `now`: `Nd` (<14d), `Nw` (14–42d, floor
by 7), `Nm` (>42d, floor by 30), or `-` when `:updated` is missing
or unparseable. Same bucketing the `knot prime ## In Progress`
column already uses. `--json` is unchanged — consumers compute age
client-side from the existing `:updated` field; no new keys, no
schema bump.

## Partial ID resolution

Ids are 12-char ULID suffixes (`01` + 10 base32 chars) prefixed with the
project shortcode (`kno-`, `app-`, etc.). The first 6–8 chars of the
suffix are usually unique — `01kqa9sh` resolves day-to-day. knot resolves
across live + archive. On ambiguity, knot prints candidates; relay them
to the user instead of guessing.

## Project setup

```sh
knot init
```

Run `knot init --help` for prefix / tickets-dir / force overrides.
`.knot.edn` is plain EDN — `knot prime --json` exposes the project's
allowed `:statuses`, `:types`, and `:modes` if you need them; reading
`.knot.edn` directly with the Read tool is also fine when the CLI doesn't
cover what you need.

## When this skill DOESN'T apply

GitHub Issues, Linear, Jira, Basecamp, Asana, Trello — different tools,
different skills. Knot tickets live in the working tree as markdown;
hosted trackers do not. If the user names one of those (or references a
remote id like `GH-482`, `ENG-1234`), use the tool they named.

## Quick reference

```
init / prime / info                    project setup, agent context primer,
                                       runtime config + allowed values
list (alias ls) / show                 read live; show one
ready / blocked / closed               backlog views (--limit + full filter set)
check                                  project-integrity scan (cycles, dangling
                                       refs, schema, archive placement)
create                                 new ticket (-t -p -a --tags --mode
                                       -d --design --acceptance --parent
                                       --external-ref --dep --link)
                                       --acceptance / --dep / --link are
                                       repeatable; --dep is lenient on
                                       missing, --link is strict
start / status / close / reopen        lifecycle (--summary on close;
                                       --force to bypass the
                                       open-children gate at start;
                                       --force --summary to bypass the
                                       acceptance and open-children
                                       gates at close)
delete                                 remove a ticket file. Leaf-only
                                       by default (refuses on incoming
                                       :parent / :deps / :links refs);
                                       --cascade rewrites every referrer
                                       first (live + archive) and then
                                       deletes the file
add-note / edit / update               annotation (edit is interactive,
                                       update is non-interactive set/replace
                                       with --title --type --priority --mode
                                       --assignee --parent --tags
                                       --external-ref --description --design
                                       --body; flip one acceptance entry
                                       with --ac "<title>" --done|--undone)
dep / undep / dep tree                 directional graph; cycle-checked on add
link / unlink                          symmetric graph
serve                                  read-only browser panel on loopback
                                       (--port, --open / --no-open, --dev)
```

Most commands return `0` on success and `1` on error. `knot check`
adds `2` for unable-to-scan (no project root or invalid `.knot.edn`).
Every read command supports `--json` and the filter flags `--type`,
`--mode`, `--tag`, `--status`, `--assignee` (each repeatable). Every
mutating command (`create`, `start`, `status`, `close`, `reopen`,
`delete`, `dep`, `undep`, `link`, `unlink`, `add-note`, `update`) also
supports `--json` — the envelope's `data` is the touched ticket(s)
(or `{deleted, cleaned}` for `delete`); `close --json` and terminal
`status --json` add `meta.archived_to`.
`knot check` uses its own filters: `--severity` (error|warning,
closed enum) and `--code` (open enum), both repeatable; OR within a
flag, AND across flags.

Every command rejects unknown flags: `knot <cmd> --bogus` exits 1 with
`Unknown option: :bogus` on stderr rather than silently absorbing the
typo. If a flag you expect to work errors this way, consult `knot <cmd>
--help` for the canonical name (e.g. `--tag` vs `--tags` differs by
command).
