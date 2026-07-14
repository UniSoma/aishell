# Knot JSON protocol (v0.3)

Every `knot` command that accepts `--json` emits the same tagged
envelope on stdout. This document is the canonical reference for the
shape of that envelope, the per-command `data` payloads, the error
codes, and the partial-id contract.

The runtime is the source of truth: drift between this document and
the binary is caught by `test/knot/json_contract_test.clj` in the knot
repository, which spawns a real subprocess for every `--json` command
and pins the shape, the error envelopes, and the documented
asymmetries.

## Envelope shape

```
{"schema_version": 1, "ok": true,  "data":  <payload>, "meta"?: {...}}
{"schema_version": 1, "ok": false, "error": {"code": "...", "message": "...", ...}}
```

| Key              | Type     | Notes                                                                                              |
|------------------|----------|----------------------------------------------------------------------------------------------------|
| `schema_version` | integer  | Always `1` in v0.3. Bumps only on shape-incompatible changes; new keys are additive.               |
| `ok`             | boolean  | Success discriminator. See *The `ok` discriminator* below for the one carve-out (`knot check`).    |
| `data`           | varies   | Present on success. Shape depends on the command — see *Per-command `data`*.                       |
| `error`          | object   | Present on failure. Always carries `code` and `message`; some codes carry extra fields.            |
| `meta`           | object   | Optional. Currently only `meta.archived_to` on terminal-status mutations (`close`, terminal `status`). Path is POSIX-normalized (forward slashes) on every platform. |

### Stable invariants

- `schema_version` is the first key in serialized output and is always `1`.
- `ok` is always present and always a boolean.
- `data` and `error` are mutually exclusive — except for the `knot
  check` carve-out (see below). On `ok: true`, `data` is present and
  `error` is absent. On `ok: false`, `error` is present and `data` is
  absent.
- The `meta` slot is omitted unless the command has metadata to emit;
  consumers should treat its absence as "no metadata," not as an error.
- All keys inside the envelope and inside `data` are `snake_case`.
- The on-disk YAML key order is preserved through to JSON output for
  ticket payloads; consumers that want stable diffs can rely on this.
- Stdout carries the envelope only. Warnings and human-readable
  context go to stderr. JSON consumers can ignore stderr.

### The `ok` discriminator

`ok` mirrors *the outcome of the request*, not "did the command run."
For everything except `knot check`, this means:

- `ok: true` — request succeeded; `data` is the result.
- `ok: false` — request failed; `error` is the diagnosis.

`knot check` is the one exception. Its `ok` mirrors a *health verdict*
on the project, not a request outcome. So when `knot check --json`
finds integrity errors, it emits:

```json
{"schema_version": 1, "ok": false, "data": {"issues": [...], "scanned": {...}}}
```

— `ok: false` co-emitted with `data`. The earlier rule (`ok: false`
↔ `error` slot, no `data`) still holds for `knot check`'s
*cannot-scan* exit-2 case (no project root, invalid `.knot.edn`).

### `meta` slot

Currently used by exactly two commands:

```json
{
  "schema_version": 1,
  "ok": true,
  "data": { ...ticket... },
  "meta": { "archived_to": ".tickets/archive/kno-01abc--shipped.md" }
}
```

Emitted by:

- `close --json` (always — close routes the ticket into archive).
- `status <id> <terminal-status> --json` — when the new status is in
  the project's `:terminal-statuses` set, the same archive routing
  fires and `meta.archived_to` lands on the envelope.

Non-terminal mutations (`start`, non-terminal `status`, `update`,
`dep`, `undep`, `link`, `unlink`, `add-note`, `create`, `reopen`,
`migrate-ac`) do not emit `meta`. Treat its absence as a hard signal
that no archive routing happened.

## Schema versioning

`schema_version` starts at `1` for v0.3 and changes only on
*shape-incompatible* breaks — for example, renaming `data` or moving
`error.code` somewhere else. Additive changes do not bump it:

- A new top-level key (e.g. a future `warnings` array slot).
- A new field inside `data` (e.g. an extra ticket-frontmatter key).
- A new error code, or a new optional field on an existing error.
- A new check-issue code.

Consumers should:

- **Tolerate unknown keys.** New optional fields land between minor
  versions without bumping `schema_version`.
- **Branch on `schema_version` only when reading a known-incompatible
  field.** A `schema_version` bump is a deliberate signal that
  consumers must update; not every change carries one.

`knot --version` is the binary version (semver). It is *not* the
schema version; one binary can speak only one schema version.

## Partial-id contract

Every command that takes a ticket id accepts a partial — typically
the first 6–8 characters of the suffix. Resolution walks both live
(`.tickets/`) and archive (`.tickets/archive/`) so closed tickets are
always reachable.

There are two resolution modes:

| Mode                 | Behavior on >1 match                                                              | Where it applies                                              |
|----------------------|-----------------------------------------------------------------------------------|---------------------------------------------------------------|
| **Strict** (default) | Emits `ambiguous_id` envelope with `error.candidates: [<full-id>...]`; exit 1.    | All read commands; `from`-side of every mutation; `link` both sides; `unlink` from. |
| **Soft**             | Resolves to the first match if unique; if literal partial doesn't resolve, persists verbatim. | `dep` / `undep` `to` side; `unlink` to side.                  |

Soft-resolution lets agents undo a previously broken `:deps`/`:links`
ref by typing it verbatim — without it, fixing a corrupted graph
would require hand-editing. The asymmetry is pinned in the contract
test (`error-envelope-not-found-contract-test`,
`error-envelope-ambiguous-id-contract-test`).

`dep tree --json` has its own asymmetry: an unknown root id emits
`{ok: true, data: {id, missing: true}}` rather than a `not_found`
error, so consumers can discover broken `:deps` refs *via* the parent
that links to them. JSON consumers should branch on `data.missing`
distinctly from `ok: false`.

## Error codes

Every `--json` failure carries `error.code` (string). The catalogue
below lists every code knot emits, what triggers it, what fields it
carries, and which commands surface it.

| Code              | Trigger                                                          | Extra fields              | Commands                                                                 |
|-------------------|------------------------------------------------------------------|---------------------------|--------------------------------------------------------------------------|
| `not_found`       | Strict-resolved id matched no ticket (live or archive).          | —                         | `show`, `start`, `status`, `close`, `reopen`, `delete`, `dep` (from), `undep` (from), `link` (either), `unlink` (from), `add-note`, `update`. |
| `ambiguous_id`    | Strict-resolved partial id matched >1 ticket.                    | `candidates: string[]`    | Same set as `not_found` plus `dep tree`.                                 |
| `cycle`           | `dep <from> <to>` would create a cycle.                          | `cycle: string[]` (path)  | `dep`.                                                                   |
| `has_incoming_refs` | `delete <id>` target is referenced by another ticket (`:parent`/`:deps`/`:links`). | `referrers: {id, field}[]` | `delete` (without `--cascade`). Drop the refs first (`undep`, `unlink`, `update --parent ""`) or re-run with `--cascade` to rewrite them. |
| `invalid_argument` | Validation failure on a flag value or flag combination.         | —                         | `info --json` (e.g. unknown flag); also surfaces from `update --json` for conflicting body flags. Other commands keep argument-parse errors on stderr (see *Argument-parsing errors* below). |
| `acceptance_incomplete` | Active→terminal transition attempted while at least one frontmatter `:acceptance` entry is unchecked. | `open_acceptance: {title}[]` | `close --json`, `status --json` (terminal target), `update --json` (with `--status <terminal>`). Pass `--force --summary "<reason>"` to override. |
| `no_project`      | No `.knot.edn` and no `.tickets/` discoverable from cwd.         | —                         | `check --json` (exit 2), `info --json` (exit 1).                         |
| `config_invalid`  | `.knot.edn` exists but cannot be parsed / contains invalid keys. | —                         | `check --json` (exit 2), `info --json` (exit 1).                         |

`error.message` is always present, always a string, and always
human-readable. For `not_found` it includes the missing id verbatim
so consumers can surface it without re-fetching context.

### Argument-parsing errors

Argument-parsing failures (unknown flag, missing required positional,
out-of-range numeric flag) are CLI-usage errors, not data conditions.
For most commands they continue to die on stderr with exit code 1
even under `--json` — they sit *outside* the JSON envelope contract.

`info --json` and `check --json` are the two exceptions, where any
`--json`-flagged invocation routes argument-parse errors through the
envelope (`invalid_argument` code) for symmetry with their other
error codes (`no_project`, `config_invalid`). This is the
arg-parsing-stays-on-stderr policy with two deliberate carve-outs.

## Per-command `data` shapes

Ticket payloads share a canonical key set across every command. The
"ls-shape" omits `body`; the "single-ticket-shape" includes it.

### Canonical ticket keys

Every ticket emitted under `--json` carries these required keys (at
minimum):

| Key             | Type    | Notes                                                                |
|-----------------|---------|----------------------------------------------------------------------|
| `id`            | string  | `<prefix>-01<10 base32 chars>`.                                      |
| `title`         | string  | May be empty for malformed tickets; never absent.                    |
| `status`        | string  | Member of project `:statuses`.                                       |
| `type`          | string  | Member of project `:types`.                                          |
| `priority`      | integer | `0`–`4` (`0` = highest).                                             |
| `mode`          | string  | Member of project `:modes` (default `afk`/`hitl`).                   |
| `created`       | string  | ISO-8601 UTC.                                                        |
| `updated`       | string  | ISO-8601 UTC; bumped on every successful save.                       |
| `tags`          | array   | Always present, possibly `[]`. *Vector default.*                     |
| `deps`          | array   | Always present, possibly `[]`. *Vector default.*                     |
| `links`         | array   | Always present, possibly `[]`. *Vector default.*                     |
| `external_refs` | array   | Always present, possibly `[]`. *Vector default.*                     |

The four vector-default keys are *always* arrays in `--json` output,
even when the on-disk YAML omits them entirely. This is a deliberate
boundary contract: `jq -r '.data[].tags[]'` works uniformly across
every ticket, regardless of whether the on-disk file declares
`tags:`. On-disk YAML pruning is unchanged — the default is injected
only at the JSON boundary.

Optional keys that may be present:

- `assignee` (string)
- `closed` (string, ISO-8601) — present on tickets in a terminal
  status; emitted by `close`, `closed --json` entries, and
  terminal-`status` envelopes.
- `parent` (string) — id of the parent ticket if any.
- `acceptance` (array of `{title, done}`) — structured acceptance
  criteria (frontmatter, never body).
- `children_total` / `children_terminal` (integer) — computed umbrella
  progress: the count of direct children (`:parent` = this id) across the
  full corpus (live + archive), and the subset in a terminal status
  (`Won't do:` closures included). **Present together, and only on
  umbrella rows** (tickets with ≥1 child) of `list`/`ready`/`blocked`/
  `closed` and `show`. Absence doubles as the predicate — `jq 'select(has("children_total"))'`
  selects umbrellas. Not frontmatter; never written to disk.
- `body` (string) — included in *single-ticket-shape* envelopes; omitted in *ls-shape*.

### Read commands

| Command              | `data` shape                                          | Body? | Notes                                                                                                                                                            |
|----------------------|-------------------------------------------------------|-------|------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `list` (alias `ls`)  | `ticket[]`                                            | no    | Live tickets only (terminal-status tickets live in archive). Umbrella rows additionally carry `children_total`/`children_terminal` (see *Optional keys*) — true of `ready`/`blocked`/`closed` too. |
| `ready`              | `ticket[]`                                            | no    | Non-terminal, non-blocked, sorted by priority.                                                                                                                   |
| `blocked`            | `ticket[]`                                            | no    | Non-terminal tickets with at least one open `:deps` ref.                                                                                                         |
| `closed`             | `ticket[]`                                            | no    | Terminal-status tickets from archive; entries additionally carry `closed` (ISO-8601 string).                                                                     |
| `show <id>`          | `ticket`                                              | yes   | Plus computed inverse arrays at `data.blockers`, `data.blocking`, `data.children`, `data.linked`. Each inverse entry is `{id, title, status}` or `{id, missing: true}`. Umbrella tickets also carry `children_total`/`children_terminal` (see *Optional keys*). |
| `dep tree <id>`      | `{id, title?, status?, missing?, seen_before?, deps?}` | n/a   | Recursive tree node. Tolerant root: missing id emits `{id, missing: true}` with `ok: true`. Seen-before nodes carry `seen_before: true` and omit `deps`.         |
| `prime`              | `{project, in_progress, ready_to_close, ready, ready_truncated, ready_remaining, recently_closed}` | n/a | `project` is `{found, prefix, project_name?, live_count, archive_count}`. `ready_truncated` is boolean; `ready_remaining` is integer. Ticket entries are body-less. `in_progress` entries may carry `stale: true` when `:updated` is 14+ days old; the flag is **in_progress-only** — `ready` copies of the same ticket never carry it. To find stalled work, iterate `.in_progress` and filter on `stale`. `ready_to_close` is a parallel array of active-status tickets whose every `:acceptance` entry is checked — mutually exclusive with `in_progress` (those tickets do not also appear there); vacuously-complete tickets (no AC list) deliberately do not migrate into it. |
| `info`               | `{project, paths, defaults, allowed_values, counts}`  | n/a   | See *`info` shape* below.                                                                                                                                        |
| `check`              | `{issues: issue[], scanned: {live, archive}}`         | n/a   | See *`check` shape* below. May co-emit with `ok: false` (health verdict).                                                                                        |

### Mutating commands

| Command                     | `data` shape | Body? | `meta`? | Notes                                                          |
|-----------------------------|--------------|-------|---------|----------------------------------------------------------------|
| `create <title>`            | `ticket`     | yes   | —       | Brand-new ticket; vector defaults injected at boundary.        |
| `start <id>`                | `ticket`     | yes   | —       | `data.status` flipped to the project's active status.          |
| `status <id> <new>`         | `ticket`     | yes   | conditional | `meta.archived_to` present iff `<new>` is in `:terminal-statuses`. |
| `close <id>`                | `ticket`     | yes   | yes     | Always emits `meta.archived_to`; `data.closed` populated.      |
| `reopen <id>`               | `ticket`     | yes   | —       | Restores from archive; `data.closed` cleared.                  |
| `dep <from> <to>`           | `ticket` (the `from`) | yes | — | `data.deps` reflects the post-add list.                       |
| `undep <from> <to>`         | `ticket` (the `from`) | yes | — | `data.deps` reflects the post-remove list.                    |
| `link <a> <b> [<c>...]`     | `ticket[]`   | no    | —       | One entry per touched ticket; `data` is body-less.             |
| `unlink <from> <to>`        | `ticket[]`   | no    | —       | Both touched tickets returned.                                 |
| `add-note <id> "<text>"`    | `ticket`     | yes   | —       | `data.body` includes the new note.                             |
| `update <id> [flags...]`    | `ticket`     | yes   | —       | `update` never archives, so `meta` is never present.           |
| `delete <id>` (+`--cascade`)| `{deleted: {id, path}, cleaned: [{id, fields:[...]}]}` | n/a | — | Target is gone; envelope carries the removed id + posix path. Without `--cascade`, `cleaned` is `[]` and a non-leaf delete emits the `has_incoming_refs` error envelope instead. With `--cascade`, `cleaned` lists every rewritten referrer (alphabetical by id, fields as string vector). |
| `migrate-ac`                | `{migrated, unchanged, total}` | n/a | — | Counts triple. `total == migrated + unchanged` invariant.   |

### `info` shape

```json
{
  "project":        { "knot_version": "0.3.0", "name": "...", "prefix": "kno", "config_present": true },
  "paths":          { "cwd": "...", "project_root": "...", "config_path": "...", "tickets_dir": ".tickets", "tickets_path": "...", "archive_path": "..." },
  "defaults":       { "default_assignee": "...", "effective_create_assignee": "...", "default_type": "task", "default_priority": 2, "default_mode": "hitl" },
  "allowed_values": { "statuses": [...], "active_status": "in_progress", "terminal_statuses": [...], "types": [...], "modes": [...], "afk_mode": "afk", "priority_range": { "min": 0, "max": 4 } },
  "counts":         { "live_count": N, "archive_count": M, "total_count": N + M }
}
```

`counts` uses raw filesystem listing (top-level `*.md` files only —
no parsing). For health verdicts and integrity validation, use
`knot check`.

All path strings under `paths` are POSIX-normalized (forward slashes)
on every platform — Windows callers don't have to branch on
`os.name`. Same rule as `meta.archived_to`.

### `check` shape

```json
{
  "issues": [
    { "severity": "error", "code": "dep_cycle", "ids": ["kno-01a", "kno-01b"], "message": "..." },
    ...
  ],
  "scanned": { "live": N, "archive": M }
}
```

Issue entries are sorted `severity` desc → `code` asc → first-id asc
→ `message` asc. The sort is identical in JSON and text output, so
diffs over time are stable.

`severity` serializes as a snake_case string (`"error"`,
`"warning"`), not as a Clojure keyword. Issue entries carry
`severity`, `code`, `ids`, `message` at minimum; some codes (e.g.
`invalid_priority`, the enum-validators) additionally carry `field`
and `value`.

Issue codes (`error.code` is open enum — knot may add codes without
bumping `schema_version`):

| Code                       | Severity | Trigger                                                                                                |
|----------------------------|----------|--------------------------------------------------------------------------------------------------------|
| `dep_cycle`                | error    | A cycle exists in the `:deps` graph (live + archive).                                                  |
| `unknown_id`               | error    | A `:deps`, `:links`, or `:parent` ref points at no ticket.                                             |
| `invalid_status`           | error    | Ticket `:status` is not in project `:statuses`.                                                        |
| `invalid_type`             | error    | Ticket `:type` is not in project `:types`.                                                             |
| `invalid_mode`             | error    | Ticket `:mode` is not in project `:modes`.                                                             |
| `invalid_priority`         | error    | Ticket `:priority` is not an integer in `0..4`.                                                        |
| `terminal_outside_archive` | error    | Bidirectional: terminal-status ticket lives outside `archive/`, or non-terminal lives inside `archive/`. |
| `missing_required_field`   | error    | Ticket frontmatter is missing one of the required keys.                                                |
| `frontmatter_parse_error`  | error    | Ticket file has unparseable YAML frontmatter.                                                          |
| `invalid_active_status`    | error    | `.knot.edn` `:active-status` is not in `:statuses`.                                                    |
| `acceptance_invalid`       | error    | Frontmatter `:acceptance` entry is malformed (non-map, missing `:title`, missing `:done`, etc.).       |

Filter with `--code <code>` (repeatable; OR within, AND across with
`--severity`). Filters apply *before* the exit-code decision —
`knot check --code dep_cycle` exits 0 on a project that has cycle-free
state, even if other issue codes exist (grep semantics).

Exit codes:

- `0` — clean (filter view is empty).
- `1` — errors found in the filter view.
- `2` — unable to scan (no project root, invalid `.knot.edn`). Under
  `--json`, this surfaces as `{ok: false, error: {code:
  "no_project"|"config_invalid", ...}}` on stdout (not stderr).

## Examples

### Success — list

```sh
$ knot list --json
```
```json
{
  "schema_version": 1,
  "ok": true,
  "data": [
    {
      "id": "kno-01kqgqf4aw4j",
      "title": "README and JSON protocol documentation for v0.3",
      "status": "open",
      "type": "chore",
      "priority": 3,
      "mode": "afk",
      "created": "2026-05-01T02:56:42.844118574Z",
      "updated": "2026-05-05T01:38:54.088449090Z",
      "tags": ["v0.3", "docs"],
      "deps": [],
      "links": [],
      "external_refs": []
    }
  ]
}
```

### Error — not_found

```sh
$ knot show kno-ghost --json    # exits 1
```
```json
{
  "schema_version": 1,
  "ok": false,
  "error": {
    "code": "not_found",
    "message": "No ticket matching id: kno-ghost"
  }
}
```

### Error — ambiguous_id (partial-id collision)

```sh
$ knot show kno-01abc --json    # two tickets share that prefix; exits 1
```
```json
{
  "schema_version": 1,
  "ok": false,
  "error": {
    "code": "ambiguous_id",
    "message": "Ambiguous id 'kno-01abc' matches: kno-01abc111111, kno-01abc222222",
    "candidates": ["kno-01abc111111", "kno-01abc222222"]
  }
}
```

### Mutation with meta — close

```sh
$ knot close kno-01abc --summary "shipped" --json
```
```json
{
  "schema_version": 1,
  "ok": true,
  "data": {
    "id": "kno-01abc111111",
    "title": "...",
    "status": "closed",
    "closed": "2026-05-05T01:38:54.088449090Z",
    "tags": [],
    "deps": [],
    "links": [],
    "external_refs": [],
    "body": "...full body..."
  },
  "meta": {
    "archived_to": ".tickets/archive/kno-01abc111111--shipped.md"
  }
}
```

### Health verdict — check (the `ok: false` + `data` carve-out)

```sh
$ knot check --json    # integrity errors found; exits 1
```
```json
{
  "schema_version": 1,
  "ok": false,
  "data": {
    "issues": [
      {
        "severity": "error",
        "code": "invalid_status",
        "ids": ["kno-01abc111111"],
        "field": "status",
        "value": "not-a-real-status",
        "message": "invalid status \"not-a-real-status\": expected one of [\"open\" \"in_progress\" \"closed\"]"
      }
    ],
    "scanned": { "live": 1, "archive": 0 }
  }
}
```

### Acceptance gate — close on incomplete AC

```sh
$ knot close kno-01abc --json    # in_progress, two unchecked AC; exits 1
```
```json
{
  "schema_version": 1,
  "ok": false,
  "error": {
    "code": "acceptance_incomplete",
    "message": "2 of 2 acceptance criteria are unchecked",
    "open_acceptance": [
      { "title": "first AC" },
      { "title": "second AC" }
    ]
  }
}
```

The same envelope surfaces from `status <id> <terminal> --json` and
`update <id> --status <terminal> --json` when the source status is the
project's `:active-status` and at least one `:acceptance` entry has
`done: false`. `--force --summary "<reason>"` overrides the gate; the
summary is recorded as a Notes entry on the ticket.

### Cycle envelope — dep

```sh
$ knot dep kno-01b kno-01a --json    # closes a cycle; exits 1
```
```json
{
  "schema_version": 1,
  "ok": false,
  "error": {
    "code": "cycle",
    "message": "...",
    "cycle": ["kno-01a", "kno-01b", "kno-01a"]
  }
}
```

## Recipes

```sh
# Pick the highest-priority unblocked afk ticket, id only:
knot ready --json --mode afk | jq -r '.data | sort_by(.priority) | .[0].id'

# Mutate then read the post-state in one shot:
knot start <id> --json       | jq -r '.data.status'
knot close <id> --json       | jq -r '.meta.archived_to'
knot create "T" --json       | jq -r '.data.id'

# Branch on the envelope's discriminator:
out=$(knot show "$id" --json)
if echo "$out" | jq -e '.ok' >/dev/null; then
  echo "$out" | jq -r '.data.title'
else
  code=$(echo "$out" | jq -r '.error.code')
  case "$code" in
    not_found)    echo "missing"; ;;
    ambiguous_id) echo "$out" | jq -r '.error.candidates[]'; ;;
  esac
fi

# Tolerate missing dep-tree roots (data.missing branch):
knot dep tree "$id" --json |
  jq -r 'if .data.missing then "missing root: \(.data.id)" else .data | .. | objects | .id end'

# Watch the project for new integrity issues:
knot check --json | jq '.data.issues[] | select(.severity == "error")'
```
