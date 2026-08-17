---
name: knot
description: Ticket tracking through the `knot` CLI — markdown tickets under `.tickets/`, config in `.knot.edn`. Use when a project carries either marker, when an id matches `<prefix>-01<base32>` (`kno-01kqa9sh`), or on ticket-shaped intent — "what's next?", "what's blocked?", "list/filter the backlog", "show me <id>", "track this", "start <id>", "add a note", "close this" — including an autonomous agent picking up unblocked work. Hosted trackers (GitHub Issues, Linear, Jira, Basecamp, Asana, Trello) and ids prefixed for them (`GH-1234`, `ENG-1234`, `JIRA-PROJ-1234`) belong to other tools.
---

# knot — file-based ticket tracker

Tickets are markdown files with YAML frontmatter under `.tickets/`; closing one moves it to `.tickets/archive/`.
Project config lives in `.knot.edn`, which knot finds by walking up from cwd — run commands from inside the project, or
an ancestor's knot project answers instead.

`knot <cmd> --help` is the source of truth for flags; this skill carries the judgment the help text can't. When the two
disagree, the CLI wins — follow it and tell the user the skill has drifted.

With neither `.knot.edn` nor `.tickets/` present, `knot init` starts tracking — run it on an explicit ask, since the
user may already have a tracker.

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

Three invariants knot holds on every write, each of which a hand-edit silently breaks:

- `:updated` and the derived graph stay consistent.
- Ids resolve across live **and** archive; a file glob sees only half the corpus.
- Terminal status and archive placement move together — a flipped `status:` line strands the file where later queries
  miss it.

When a `knot` command surprises you, report it to the user as a bug and stop there. When no `knot` command can express
what you need, that gap is itself the ticket to file.

False economies and their answers:

| Tempting                                        | Answer                                                                             |
|-------------------------------------------------|-------------------------------------------------------------------------------------|
| "cat the file once to confirm the close landed" | `knot show <id>` reads archived tickets too.                                        |
| "create, then `knot show` to see what landed"   | `--json` already returned the whole post-mutation ticket — see *Read-after-write*.  |
| "peek at `.knot.edn` for the allowed statuses"  | `knot info` reports statuses, types, modes, and create-time defaults.               |
| "list everything, then scan for the bugs"       | `knot list --type bug` — see *Filter, don't eyeball*.                               |

### Already primed?

A `SessionStart` `<system-reminder>` may have injected `knot prime` output near the top of the conversation. Read state
from there instead of re-running `prime`; reach for `knot list` / `ready` / `show <id>` when you need it fresher than
session start.

## Intent → command

| The user says…                                          | You run                                                                      |
|---------------------------------------------------------|-------------------------------------------------------------------------------|
| "what's next?" / "what should I pick up?"               | `knot ready` (add `--mode afk` for agent-runnable only)                       |
| "show me the backlog" / "list tickets"                  | `knot list`                                                                   |
| "any open bugs?" / "what's tagged <x>?" / "my tickets"  | `knot list --type bug` / `--tag <x>` / `--assignee <user>`                    |
| "what's under <id>?"                                    | `knot list --parent <id>` (direct children)                                   |
| "what's related to <id>?"                               | `knot list --closure <id>` (everything transitively related, archive included) |
| "what's the live cluster around <id>?"                  | `knot list --component <id>`                                                  |
| "what's blocked?" / "what's blocking <id>?"             | `knot blocked` / `knot dep tree <id>`                                          |
| "what did we close recently?"                           | `knot closed --limit 10`                                                      |
| "what's finished but still open?"                       | `knot prime` — its *Ready to close* section lists active tickets with every AC checked |
| "how's the project doing?"                              | `knot prime`                                                                  |
| "show me <id>" / "tell me about <id>"                   | `knot show <id>`                                                              |
| "let's start <id>"                                      | `knot show <id>`, then `knot start <id>`                                       |
| "I'm done" / "shipped"                                  | `knot close <id> --summary "<what shipped>"`                                   |
| "reopen <id>"                                           | `knot reopen <id>`                                                            |
| "track this" / "open a ticket for X"                    | `knot create "<title>" -t bug -d "…"`                                         |
| "note that…" / "FYI" mid-task                           | `knot add-note <id> "…"`                                                      |
| "retitle / retag / reprioritize <id>"                   | `knot update <id> --title "…" / --tags … / --priority …`                      |
| "<a> is blocked on <b>"                                 | `knot dep <a> <b>`                                                            |
| "these are related: a, b, c"                            | `knot link <a> <b> <c>`                                                       |
| "any integrity issues?" / "any dep cycles?"             | `knot check` / `knot check --code dep_cycle`                                   |
| "what statuses are valid here?"                         | `knot info`                                                                   |
| "give me the frontmatter JSON Schema"                   | `knot schema`                                                                 |
| "let me see this in a browser"                          | `knot serve`                                                                  |

### Filter, don't eyeball

When the question names a subset, pass the filter — don't list everything and scan the columns. Titles wrap, columns
shift, the archive is absent, and the user can't verify what you skipped.

`list` / `ready` / `blocked` / `closed` / `prime` share a filter set (`knot <cmd> --help` names them; each is
repeatable). What the help won't tell you:

- On `prime` a filter hits every section at once — `knot prime --assignee me` is your tickets everywhere.
- `--acceptance-complete` drops tickets carrying no AC from *both* views, so `=false` is "has an unchecked AC", not
  "isn't finished".
- The three graph filters answer three different questions: `--parent <id>` direct children, `--closure <id>`
  everything transitively related with the archive included, `--component <id>` the seed's live cluster.
- The listing tables carry computed columns — `CC` (component), `AGE`, `AC`, `CHLD`, `LEV` (leverage), `CPL`
  (coupling) — which are derived, not stored, and never filterable.

**Before composing a graph query or reading a computed column, load
[`references/listing-filters-and-columns.md`](references/listing-filters-and-columns.md)** — scope rules (live-induced
vs corpus-wide), fail-fast cases, and `--json` field names are pinned there.

### Partial ids

Ids are `<prefix>-01<10 base32 chars>` (`kno-01kqa9sh4b2c`). Pass what the user gave you through verbatim — 6–8
characters of the suffix usually resolve, across live and archive both. On ambiguity knot prints the candidates; relay
them and let the user pick rather than guessing.

### Read-after-write

Every mutating command takes `--json` and returns the full post-mutation ticket under `.data` — `create`, `update`,
`add-note`, `start` / `status` / `close` / `reopen`, `dep` / `undep`, `link` / `unlink`, `delete`. One invocation gives
you both the write and its result:

```sh
knot create "T" --json | jq -r '.data.id'
```

Chaining `knot show <id>` after a write re-reads what you already hold.

## Creating tickets

`knot create "<title>" [flags]`; `knot create --help` has the flag list. The judgment it doesn't carry:

- Pass `--description` whenever there's context worth keeping. A title-only ticket makes the next reader reconstruct
  intent from scratch.
- Set `--mode afk` when the work is specified well enough for an agent to run it end to end; leave the `hitl` default
  when a human has to be in the loop. Other agents route off this field.
- `--acceptance "<title>"` (repeatable) writes structured criteria into frontmatter and `knot show` renders the
  checklist from them. Author criteria through this flag and `knot update --add-ac` — a hand-written
  `## Acceptance Criteria` body section is display-only and never syncs back.

For multi-line prose, a quoted-delimiter heredoc passes `$vars`, backticks, and quotes through literally:

```sh
knot create "Title" -t bug -p 1 --description "$(cat <<'EOF'
body with `code`, $vars, and "quotes" — all literal
EOF
)"
```

`knot add-note` reads stdin, so it takes a heredoc directly:

```sh
knot add-note <id> <<'EOF'
note body
EOF
```

## Lifecycle

```sh
knot start <id>                              # → the project's active status
knot status <id> <new-status>                # any transition
knot close <id> --summary "shipped in #482"
knot reopen <id>                             # back out of the archive
knot delete <id>                             # remove the file (leaf-only; --cascade rewrites referrers)
```

Always give `knot close` a `--summary`. It lands as a timestamped note and becomes the answer to "what did we ship?"
months later; skipping it loses that for free.

In a project with custom `:statuses` — say a `review` stage between `in_progress` and `closed` — transition with `knot
status <id> <new>` so you don't jump a stage that `start` and `close` skip past. `knot info` prints the project's
ladder.

`knot delete` refuses (exit 1, `has_incoming_refs`) while any other ticket, live or archived, references the target
through `:parent`, `:deps`, or `:links`, and enumerates each referrer — so the bare command doubles as the dry run for
`--cascade`, which drops those refs and then deletes. There is no undo: `.tickets/` is git-tracked and `git checkout`
is the recovery path.

### Transition gates

Two gates block a transition with exit 1 and a JSON `error.code`:

- `acceptance_incomplete` — closing (any active→terminal move) with a frontmatter `:acceptance` entry still unchecked.
  Clear it by checking the box: `knot update <id> --ac "<title>" --done`, which composes with `--status`, so `knot
  update <id> --ac "last AC" --done --status closed` checks and closes in one call.
- `open_children` — starting *or* closing a ticket that has a child in a non-terminal status. Clear it by finishing
  the children.

Override either with `--force`: on close it needs `--summary "<reason>"` alongside (recorded as a note), on start it
stands alone. The full skip-condition matrix and the reason for that asymmetry are in
[`references/lifecycle-gates.md`](references/lifecycle-gates.md).

## Notes and revisions

- `knot add-note <id> "…"` appends a timestamped entry — the tool for observations captured mid-task.
- `knot update <id> [flags]` sets and replaces frontmatter fields, tag and AC deltas, named body sections, and status,
  non-interactively in one shot, returning the result under `--json`. This is the tool for autonomous runs and scripts;
  `knot update --help` carries the flag list.
- `knot edit <id>` opens the whole file in `$EDITOR`. Interactive sessions only — it fails without a TTY.

`update` never appends: `--description` replaces the `## Description` section, and `--body` replaces the entire body
(destructive, no `--force`, git is the undo). To add to a ticket, reach for `add-note`.

The trap in the middle is set-semantics: `--tags` replaces the whole tag list, so adding one tag by re-sending the list
drops anything you hadn't read first. For a one-tag or one-criterion change reach for the delta flags —
`--add-tag` / `--remove-tag` and `--add-ac` / `--remove-ac` — which are idempotent and leave the rest untouched.

## Graph: deps vs links

```sh
knot dep <from> <to>       # <from> waits on <to>; rejected if it would close a cycle
knot dep tree <id>         # ASCII tree of the deps subtree (--full expands duplicates)
knot undep <from> <to>

knot link <a> <b> [<c>…]   # symmetric "see also" across every pair
knot unlink <from> <to>
```

`:deps` are directional and gate readiness — `knot ready` surfaces only tickets whose deps have all reached a terminal
status, and a dep ref pointing at nothing counts as unresolved, holding the ticket out of `ready` until you fix it.
`:links` are symmetric and carry no scheduling meaning. Use a dep when one ticket must wait on another; use a link for
"here's related context". `knot dep` refuses cycle-creating edges at write time; `knot check --code dep_cycle` scans
for cycles already on disk.

## Project integrity

`knot check` walks every ticket (live + archive) plus the config, reporting dep cycles, dangling `:deps` / `:links` /
`:parent` refs, invalid status/type/mode/priority, malformed acceptance entries, terminal-vs-archive misplacement,
missing required fields, and frontmatter parse errors. Narrow it with `knot check <id>…`, `--code <code>`, or
`--severity error`; filters apply before the exit-code verdict, grep-style. Exit `0` is clean, `1` means errors in the
filtered view, `2` means it couldn't scan at all (no project root, unparseable `.knot.edn`). The issue-code catalogue
is in [`references/json-protocol.md`](references/json-protocol.md).

## Working autonomously

`mode` is a peer dimension to status and priority: `afk` means an agent can run the ticket alone, `hitl` means a human
is in the loop (the default for new tickets). Treat it as a contract — pick up a `hitl` ticket only when the user
authorizes that specific ticket.

Handed autonomy, the loop is `knot prime --mode afk` — run it unless a `SessionStart` reminder already put it in the
conversation. It prints the sequence (enumerate → confirm → claim → note → update → close) and it is the single source
of truth for that sequence; this skill deliberately keeps no second copy to drift against it.

## JSON

Every read and mutating command takes `--json` and prints one tagged envelope on stdout, snake_case throughout;
warnings and human-readable context go to stderr.

```json
{"schema_version": 1, "ok": true, "data": <payload>}
```

`.data` is an array for the list-shaped commands (`list`, `ready`, `blocked`, `closed`, `link`, `unlink`) and an object
otherwise. Failures flip to `{"ok": false, "error": {"code", "message", …}}` with no `data` slot — except `knot check`,
whose `ok` reports the project's health rather than the request's outcome and so can carry both. `close`, `status`, and
`update` add `meta.archived_to` when the resulting status is terminal — it reports where the ticket file *is*, so an
absent `meta` means the live directory, not "nothing moved". The vector-default keys `tags`, `deps`, `links`, and
`external_refs` are always arrays, so `jq -r '.data[].tags[]'` is safe on every ticket.

```sh
knot list --json              | jq '.data[] | select(.priority <= 1)'
knot ready --json --mode afk  | jq -r '.data | sort_by(.priority) | .[0].id'
knot close <id> --json        | jq -r '.meta.archived_to'
knot check --json             | jq '.data.issues'
```

Drive decision logic off `--json`, never off table output — column widths shift and titles contain whitespace.
Per-command `data` shapes, the error-code and check-code catalogues, the strict-vs-soft partial-id resolution modes,
and `prime`'s `stale` / `ready_to_close` fields are pinned in
[`references/json-protocol.md`](references/json-protocol.md).

## Finding a command or a flag

`knot --help` lists every command, `knot <cmd> --help` its flags, caveats, and examples. This skill carries no
inventory of either on purpose — a list here is a copy that outlives what it copied.

Exit codes are `0` success and `1` error, plus `2` on `knot check` for unable-to-scan. Unknown flags are rejected
rather than absorbed (`Unknown option: :bogus`, exit 1) — when a flag you expect fails that way, `knot <cmd> --help`
has the canonical name, which varies by command (`--tag` vs `--tags`).

## When knot isn't the tracker

GitHub Issues, Linear, Jira, Basecamp, Asana, and Trello are hosted trackers with their own tools. Knot tickets are
markdown in the working tree; hosted ones are not. If the user names a hosted tracker or a remote id like `GH-482` or
`ENG-1234`, use the tool that owns it.
