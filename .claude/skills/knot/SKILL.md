---
name: knot
description: Ticket tracking through the `knot` CLI — markdown tickets under `.tickets/`, config in `.knot.edn`. Use when a project carries either marker, when an id matches `<prefix>-01<base32>` (`kno-01kqa9sh`) or the document shape that nests inside it (`kno-01kqa9sh-d7f3k`), or on ticket-shaped intent — "what's next?", "the backlog", "show me <id>", "track this", "close this" — including an autonomous agent picking up unblocked work. Hosted-tracker ids (`GH-1234`, `ENG-1234`) belong to their own tools.
---
<!-- installed by knot 0.15.0 -->

# knot — file-based ticket tracker

Tickets are markdown files with YAML frontmatter under `.tickets/`; closing one moves it to `.tickets/archive/`.
Project config lives in `.knot.edn`, which knot finds by walking up from cwd — run commands from inside the project, or
an ancestor's knot project answers instead. With neither marker present, `knot init` starts tracking; run it on an
explicit ask, since the user may already have a tracker.

`knot --help` lists every command and `knot <cmd> --help` its flags, caveats, and examples; this skill carries the
judgment help can't, and keeps no inventory of either on purpose. When the two disagree, the CLI wins — follow it and
tell the user the skill has drifted; `knot skill install` rewrites it from the running binary. An unknown flag is
rejected (`Unknown option: :bogus`, exit 1) rather than absorbed, and the canonical name varies by command (`--tag` on
`list`, `--tags` on `create`), so a rejected flag sends you to that command's help.

## The CLI is the contract

`.tickets/` is an implementation detail; `knot` is the interface. `knot prime` states the rule at session start; what
follows is why it holds and how it cashes out against your own tools:

| Against `.tickets/`, instead of… | Run                                                                      |
|----------------------------------|--------------------------------------------------------------------------|
| `Read` / `cat` / `head`          | `knot show <id>`                                                         |
| `Grep` / `rg`                    | `knot list --json \| jq '.data[] \| …'`                                  |
| `ls`                             | `knot list`                                                              |
| `Write`                          | `knot create "<title>" -d "…"`                                           |
| `Edit` / `sed -i`                | `knot update <id> [flags]` to replace, `knot add-note <id> "…"` to append |
| `mv` into or out of `archive/`   | `knot close <id> --summary "…"` / `knot reopen <id>`                     |
| `rm`                             | `knot delete <id>`                                                       |
| peeking at `.knot.edn`           | `knot info` — statuses, types, modes, create-time defaults               |

Three invariants knot holds on every write, each of which a hand-edit silently breaks:

- `:updated` and the derived graph stay consistent.
- Ids resolve across live **and** archive; a file glob sees only half the corpus.
- Terminal status and archive placement move together — a flipped `status:` line strands the file where later queries
  miss it.

When a `knot` command surprises you, report it to the user as a bug and stop there. When no `knot` command can express
what you need, that gap is itself the ticket to file.

### Already primed?

A `SessionStart` `<system-reminder>` may have injected `knot prime` output near the top of the conversation. Read state
from there instead of re-running `prime`; reach for `knot list` / `ready` / `show <id>` when you need it fresher than
session start.

## Intent → command

| The user says…                                         | You run                                                                             |
|--------------------------------------------------------|-------------------------------------------------------------------------------------|
| "what's next?" / "what should I pick up?"              | `knot ready` (add `--mode afk` for agent-runnable only)                             |
| "what's unclaimed?" / "take the next free one"         | `knot ready --assignee ""`, then `knot start <id> --assignee <you> --if-unassigned` |
| "any open bugs?" / "what's tagged <x>?" / "my tickets" | `knot list --type bug` / `--tag <x>` / `--assignee <user>`                          |
| "what's under <id>?"                                   | `knot list --parent <id>` — direct children                                         |
| "what's related to <id>?"                              | `knot list --closure <id>` — everything transitively related, archive included      |
| "what's the live cluster around <id>?"                 | `knot list --component <id>`                                                        |
| "what's blocked?" / "what's blocking <id>?"            | `knot blocked` / `knot dep tree <id>`                                               |
| "what's finished but still open?"                      | `knot prime` — its *Ready to close* section lists active tickets with every AC checked |
| "how's the project doing?"                             | `knot prime`                                                                        |
| "let's start <id>"                                     | `knot show <id>`, then `knot start <id>`                                            |
| "I'm done" / "shipped"                                 | `knot close <id> --summary "<what shipped>"`                                        |
| "track this" / "open a ticket for X"                   | `knot create "<title>" -t bug -d "…"`                                               |
| "note that…" / "FYI" mid-task                          | `knot add-note <id> "…"`                                                            |
| "retitle / retag / reprioritize <id>"                  | `knot update <id> --title "…" / --add-tag … / --priority …`                         |
| "<a> is blocked on <b>"                                | `knot dep <a> <b>`                                                                  |
| "these are related: a, b, c"                           | `knot link <a> <b> <c>`                                                             |
| "is the project consistent?" / "any dep cycles?"       | `knot check` / `knot check --code dep_cycle`                                        |

### Filter at the query

When the question names a subset, pass the filter: titles wrap, columns shift, the archive is absent from `list`, and
the user can't verify what you skipped. `list` / `ready` / `blocked` / `closed` / `prime` share one filter set, each
flag repeatable; on `prime` a filter hits every section at once, so `knot prime --assignee me` is your tickets
everywhere.

Before composing a graph query (`--parent` / `--closure` / `--component`) or acting on a computed column (`LEV`, `CPL`,
`LVL`, `CC`), load [`references/graph.md`](references/graph.md) — scope rules (live-induced vs corpus-wide), fail-fast
cases, and what each number tells you to do are pinned there.

### Partial ids

Ids are `<prefix>-01<10 base32 chars>` (`kno-01kqa9sh4b2c`). Pass what the user gave you through verbatim — 6–8
characters of the suffix usually resolve, across live and archive both. On ambiguity knot prints the candidates; relay
them and let the user pick rather than guessing.

### Read-after-write

Every mutating command takes `--json` and returns the full post-mutation ticket under `.data`, so one invocation is
both the write and its result:

```sh
knot create "T" --json | jq -r '.data.id'
```

A `knot show <id>` chained after a write re-reads what you already hold.

## Creating tickets

- Pass `--description` whenever there's context worth keeping. A title-only ticket makes the next reader reconstruct
  intent from scratch.
- Set `--mode afk` when the work is specified well enough for an agent to run it end to end; leave the `hitl` default
  when a human has to be in the loop. Other agents route off this field.
- Author acceptance criteria through `--acceptance` (repeatable) and later `knot update --add-ac`: they live in
  frontmatter and `knot show` renders the checklist from them. A hand-written `## Acceptance Criteria` body section is
  display-only and never syncs back.

For multi-line prose, a quoted-delimiter heredoc passes `$vars`, backticks, and quotes through literally — as a flag
value, or straight into `knot add-note <id>`, which reads stdin:

```sh
knot create "Title" -t bug -p 1 --description "$(cat <<'EOF'
body with `code`, $vars, and "quotes" — all literal
EOF
)"
```

## Lifecycle

`start` moves a ticket to the project's active status and `close` to its first terminal one; `status <id> <new>` is
any transition. In a project with custom `:statuses` — say a `review` stage between `in_progress` and `closed` —
transition with `status` so you don't jump a stage the two shortcuts skip past. `knot info` prints the ladder.

Always give `knot close` a `--summary`. It lands as a timestamped note and becomes the answer to "what did we ship?"
months later; skipping it loses that for free. When a commit is what finished the ticket, add
`--external-ref git:<full-sha>` and keep the sha out of the summary prose: the ref is a field an agent can read back
out of `--json`, the summary is prose nobody can query.

`knot delete` refuses while any other ticket, live or archived, still references the target, and enumerates each
referrer — so the bare command doubles as the dry run for `--cascade`. There is no undo: `.tickets/` is git-tracked
and `git checkout` is the recovery path.

### Transition gates

Three gates block a transition with exit 1 and a JSON `error.code`:

- `acceptance_incomplete` — closing (any active→terminal move) with a frontmatter `:acceptance` entry still unchecked.
  Clear it by checking the box: `knot update <id> --ac 3 --done`, which composes with `--status`, so
  `knot update <id> --ac 3 --done --status closed` checks and closes in one call.
- `open_children` — starting *or* closing a ticket that has a child in a non-terminal status. Clear it by finishing
  the children.
- `missing_required_docs` — entering a status that `.knot.edn`'s `:required-docs` lists while the ticket lacks a
  document of a required type. Clear it by attaching the document.

Override any of them with `--force`: on close it needs `--summary "<reason>"` alongside (recorded as a note), on start it
stands alone. The full skip-condition matrix and the reason for that asymmetry are in
[`references/lifecycle.md`](references/lifecycle.md).

## Notes and revisions

`knot add-note` appends a timestamped entry, `knot update` replaces (`--description` the section, `--body` the whole
body), and `knot edit` opens `$EDITOR`. Before writing prose into a ticket, load
[`references/writes.md`](references/writes.md) — which of the three fits, the six `knot show` sections that render a
field and must never be hand-written, and the whole-list flags whose delta counterparts you want instead.

## Graph: deps vs links

A **dep** is directional and gates readiness: `knot dep <from> <to>` makes `<from>` wait on `<to>`, `knot ready`
surfaces only tickets whose deps have all reached a terminal status, and a dep pointing at nothing counts as unresolved —
the ticket sits in `blocked` until you fix the ref. A **link** is symmetric and carries no scheduling meaning. Use a dep
when one ticket must wait on another; use a link for "here's related context". `knot dep` refuses a cycle-creating edge
at write time; `knot check --code dep_cycle` finds cycles already on disk, and a `-` in the `LVL` column is the same
report.

## Working autonomously

`mode` is a contract, not a hint: `afk` means an agent can run the ticket alone, `hitl` — the default — means a human
is in the loop. Handed autonomy, load [`references/autonomous.md`](references/autonomous.md) before picking anything up.

## JSON

Every read and mutating command takes `--json` and prints one tagged envelope on stdout, snake_case throughout;
warnings and human-readable context go to stderr.

```json
{"schema_version": 1, "ok": true, "data": <payload>}
```

`.data` is an array for the list-shaped commands (`list`, `ready`, `blocked`, `closed`, `link`, `unlink`) and an object
otherwise. Failures flip to `{"ok": false, "error": {"code", "message", …}}` with no `data` slot — except `knot check`,
whose `ok` is the project's health verdict rather than the request's outcome and so can carry both. A terminal
transition adds `meta.archived_to`; it reports where the ticket file *is*, so an absent `meta` means the live directory.
`tags`, `deps`, `links`, and `external_refs` are always arrays, so `jq -r '.data[].tags[]'` is safe on every ticket.

```sh
knot list --json              | jq '.data[] | select(.priority <= 1)'
knot ready --json --mode afk  | jq -r '.data | sort_by(.priority) | .[0].id'
knot close <id> --json        | jq -r '.meta.archived_to'
knot check --json             | jq '.data.issues'
```

Drive decision logic off `--json`; table output is for humans — column widths shift and titles contain whitespace.
Per-command `data` shapes, the error-code and check-code catalogues, the strict-vs-soft partial-id resolution modes,
and `prime`'s `stale` / `ready_to_close` fields are pinned in [`references/json.md`](references/json.md).

## When knot isn't the tracker

GitHub Issues, Linear, Jira, Basecamp, Asana, and Trello are hosted trackers with their own tools. Knot tickets are
markdown in the working tree; hosted ones are not. When the user names a hosted tracker or a remote id like `GH-482` or
`ENG-1234`, use the tool that owns it.
