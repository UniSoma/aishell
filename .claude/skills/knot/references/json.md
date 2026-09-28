# Knot JSON protocol

Every `knot` command that accepts `--json` emits the same tagged envelope on stdout. This document is the canonical
reference for the shape of that envelope, the per-command `data` payloads, the error and check-code catalogues, and
the partial-id contract.

The runtime is the source of truth: if this document and a live `--json` envelope ever disagree, trust the CLI and
surface the drift.

## Envelope shape

```
{"schema_version": 1, "ok": true,  "data":  <payload>, "meta"?: {...}}
{"schema_version": 1, "ok": false, "error": {"code": "...", "message": "...", ...}}
```

| Key              | Type    | Notes                                                                                   |
|------------------|---------|-----------------------------------------------------------------------------------------|
| `schema_version` | integer | Always `1`. Bumps only on shape-incompatible changes; new keys are additive.             |
| `ok`             | boolean | Success discriminator — see *The `ok` discriminator* for the one carve-out (`knot check`). |
| `data`           | varies  | Present on success. Shape depends on the command — see *Per-command `data`*.            |
| `error`          | object  | Present on failure. Always carries `code` and `message`; some codes carry extra fields.  |
| `meta`           | object  | Optional; today only `meta.archived_to` — see *The `meta` slot*.                        |

Stable invariants:

- `schema_version` is the first key in serialized output.
- `data` and `error` are mutually exclusive — except for the `knot check` carve-out below.
- `meta` is omitted unless the command has metadata to emit; absence is "no metadata", not an error.
- All keys inside the envelope and inside `data` are `snake_case`.
- On-disk YAML key order is preserved through to JSON ticket payloads, so consumers can rely on stable diffs.
- Stdout carries the envelope only. Warnings and human-readable context go to stderr.

### The `ok` discriminator

`ok` mirrors *the outcome of the request*, not "did the command run". `knot check` is the one exception: its `ok` is
a *health verdict* on the project, so `knot check --json` on a project with integrity errors emits `ok: false`
co-emitted with `data`:

```json
{"schema_version": 1, "ok": false, "data": {"issues": [...], "scanned": {...}}}
```

The plain rule (`ok: false` ↔ `error` slot, no `data`) still holds for `check`'s *cannot-scan* exit-2 case
(`no_project` / `config_invalid`).

### The `meta` slot

`meta.archived_to` is a **location report, not a movement event**. It asserts "this ticket is in the archive, here",
so it is emitted whenever the resulting `data.status` is in the project's `:terminal-statuses`, whether or not the call
moved a file — `close` always; `status <id> <terminal>`; `update --status <terminal>`; and a field-only
`update <archived-id> --priority 1`, which lands on a terminal status without moving anything. Two calls that leave the
ticket in the same state emit the same envelope.

```json
{"schema_version": 1, "ok": true, "data": {...}, "meta": {"archived_to": "/home/you/acme/.tickets/archive/kno-01abc--shipped.md"}}
```

**An absent `meta` means the ticket is in the live directory.** The un-archiving direction is deliberately silent —
`reopen`, `status <id> <non-terminal>`, and `update --status <non-terminal>` move the file back and report nothing,
because a non-terminal `data.status` already carries that fact; there is no `restored_to`. Non-transition mutations
omit `meta` regardless of status.

## Path fields

Knot addresses tickets by **id** and locates files by **path**, and never uses one for the other job. The id is the
portable identifier — stable across retitles, closes, machines, and clones, and the only thing any command accepts; no
command takes a path argument. A path is a machine-local *locator*, emitted so a consumer can open the file now. A
consumer that wants portability stores the id.

Every path in the envelope is **absolute** and **POSIX-separated** (forward slashes on every platform, Windows
included). Absolute because the project root is discovered by walking up from cwd, so a root-relative path is not
openable from a subdirectory — terminal hyperlinking and agent file-read tools both resolve against cwd — and because
an agent then never has to join against `project_root` with a second `knot info --json` call. The one exception is
`info`'s `paths.tickets_dir` and `paths.skill_dir`, which echo configured values (`.tickets`, and `:skill-dir` when
set — possibly relative or `~`-prefixed, `null` when unset): they name config values rather than locating files, and
`tickets_path` and `skill_path` are their absolute forms.

| Site                 | Emitted by                                             | Notes                                     |
|----------------------|--------------------------------------------------------|-------------------------------------------|
| `meta.archived_to`   | `close`, terminal `status`, terminal `update --status` |                                           |
| `data.deleted.path`  | `delete`, `document delete`                            | Where the file *was* — it is gone.        |
| `data.paths.*`       | `info`                                                 | `tickets_dir` and `skill_dir` are the config-echo exceptions. |
| `data.issues[].path` | `check`, on file-level codes only                      | See *`check` shape*.                      |
| `data.skill_dir`     | `prime`                                                | Absolute — the resolved directory whose `SKILL.md` answered the search, `null` when none did. Same key name as `info`'s `paths.skill_dir`, different job: that one echoes the configured `:skill-dir`. |

**Known boundary:** an absolute path is wrong across a container boundary where the repo is mounted at a different
path than the host sees. Knot does not work around this — it is the limitation every path-reporting tool carries.
Consumers crossing such a boundary key off ids and rebuild paths themselves.

## Schema versioning

`schema_version` changes only on *shape-incompatible* breaks — renaming `data`, moving `error.code`. Additive changes
(a new top-level key, a new field inside `data`, a new error or check code, a new optional field on an existing error)
do not bump it. So consumers **tolerate unknown keys**, and branch on `schema_version` only when reading a
known-incompatible field. `knot --version` is the binary version (semver), not the schema version; one binary speaks
one schema version.

## Partial-id contract

Every command that takes a ticket id accepts a partial — typically the first 6–8 characters of the suffix. Resolution
walks both live (`.tickets/`) and archive (`.tickets/archive/`), so closed tickets are always reachable. Two modes:

| Mode                 | Behavior on >1 match                                                                      | Where it applies                                                                        |
|----------------------|-------------------------------------------------------------------------------------------|-----------------------------------------------------------------------------------------|
| **Strict** (default) | `ambiguous_id` envelope with `error.candidates: [<full-id>…]`; exit 1.                    | All read commands; `from` side of every mutation; `link` both sides; `unlink` from.     |
| **Soft**             | Resolves to the first match if unique; a literal that doesn't resolve persists verbatim.  | `dep` / `undep` `to` side; `unlink` `to` side.                                          |

Soft resolution lets an agent undo a previously broken `:deps` / `:links` ref by typing it verbatim — without it,
fixing a corrupted graph would require hand-editing.

`dep tree --json` has its own asymmetry: an unknown root id emits `{ok: true, data: {id, missing: true}}` rather than a
`not_found` error, so consumers can discover broken `:deps` refs *via* the parent that links to them. Branch on
`data.missing` distinctly from `ok: false`.

## Error codes

Every `--json` failure carries `error.code`; `error.message` is always present, human-readable, and for `not_found`
includes the missing id verbatim.

| Code                    | Trigger                                                                                         | Extra fields                 | Commands                                                                                                                                  |
|-------------------------|-------------------------------------------------------------------------------------------------|------------------------------|-------------------------------------------------------------------------------------------------------------------------------------------|
| `not_found`             | Strict-resolved id matched no ticket (live or archive).                                         | —                            | `show`, `start`, `status`, `close`, `reopen`, `delete`, `dep` (from), `undep` (from), `link` (either), `unlink` (from), `add-note`, `update`, `document add` / `document list` (the owning ticket), `document show` / `document replace` / `document delete` (`--ticket`). |
| `ambiguous_id`          | Strict-resolved partial id matched >1 ticket.                                                   | `candidates: string[]`       | Same set as `not_found` plus `dep tree`.                                                                                                  |
| `doc_not_found`         | A document selector matched no document. Separate from `not_found` so the reader is not sent looking for a ticket. | —                            | `document show`, `document replace`, `document delete`.                                                                                           |
| `ambiguous_doc`         | A document selector matched more than one document. Never resolved to a first match.            | `candidates: string[]`       | `document show`, `document replace`, `document delete`.                                                                                           |
| `invalid_doc_type`      | A document write named a type outside `.knot.edn`'s `:doc-types`. Nothing is written.           | `value: string`, `allowed: string[]` | `document add`, `document replace`. Also a `check` code — see *`check` shape*.                                                        |
| `cycle`                 | `dep <from> <to>` would create a cycle.                                                         | `cycle: string[]` (the path) | `dep`.                                                                                                                                    |
| `has_incoming_refs`     | `delete` target is referenced by another ticket through `:parent` / `:deps` / `:links`, or owns documents. | `referrers: {id, field}[]`, `documents: string[]` | `delete` without `--cascade`. Drop the refs first (`undep`, `unlink`, `update --parent ""`), remove the documents (`document delete`), or re-run with `--cascade`. |
| `invalid_argument`      | Validation failure on a flag value or flag combination, or a missing required positional.      | —                            | Any command run without its required positional (title, id or selector); `info --json`, `check --json` (any parse error); `update --json` (conflicting body flags); `close` / `status` / `update` (`--force` with a blank `--summary` while a gate fires on a terminal target); `document add` (missing `--title`), `document replace` (missing `--title` or `--type` — the replace is total, so neither is carried over), and `document show` (`--type` with no value, or `--type` combined with `--ticket`, which has nothing left to narrow once the selector is the owning ticket). Other commands keep their remaining argument-parse errors on stderr — see below. |
| `acceptance_incomplete` | Active→terminal transition with at least one frontmatter `:acceptance` entry unchecked.         | `open_acceptance: {title}[]` | `close`, `status` (terminal target), `update --status <terminal>`.                                                                        |
| `open_children`         | Start or close of a ticket with a child in a non-terminal status.                               | `open_children: string[]`    | `start`, `close`, `status`, `update --status`.                                                                                            |
| `missing_required_docs` | A transition into a status listed in `.knot.edn`'s `:required-docs`, where the ticket does not own a document of every type that status requires. | `missing_doc_types: string[]`, `target: string` | `start`, `close`, `status`, `update --status`. Attach the documents, or pass `--force`. Only the missing types are listed, not every required one.  |
| `already_assigned`      | `--if-unassigned` passed and the ticket already carries a non-blank `assignee`. Nothing written. | `current_assignee: string`   | `start`, `update`.                                                                                                                        |
| `no_project`            | No `.knot.edn` and no `.tickets/` discoverable from cwd.                                        | —                            | `check` (exit 2), `info` (exit 1), `skill install` without a `<dir>` (exit 1).                                                                                                        |
| `config_invalid`        | `.knot.edn` exists but cannot be parsed or contains invalid keys.                               | —                            | `check` (exit 2), `info` (exit 1).                                                                                                        |

Argument-parsing failures (unknown flag, out-of-range numeric) are CLI-usage errors, not data conditions: for most
commands they die on stderr with exit 1 even under `--json`, outside the envelope contract. `info --json` and
`check --json` are the two carve-outs that route them through the envelope as `invalid_argument`, for symmetry with
their other codes. A missing required positional is not one of these failures: every `--json` command reports it as
`invalid_argument`.

## Per-command `data` shapes

Ticket payloads share a canonical key set across every command. The *ls-shape* omits `body`; the
*single-ticket-shape* includes it.

### Canonical ticket keys

Every ticket emitted under `--json` carries at minimum:

| Key             | Type    | Notes                                                    |
|-----------------|---------|----------------------------------------------------------|
| `id`            | string  | `<prefix>-01<10 base32 chars>`.                          |
| `title`         | string  | May be empty for malformed tickets; never absent.        |
| `status`        | string  | Member of project `:statuses`.                           |
| `type`          | string  | Member of project `:types`.                              |
| `priority`      | integer | `0`–`4` (`0` = highest).                                 |
| `mode`          | string  | Member of project `:modes` (default `afk` / `hitl`).     |
| `created`       | string  | ISO-8601 UTC.                                            |
| `updated`       | string  | ISO-8601 UTC; bumped on every successful save.           |
| `tags`          | array   | *Vector default* — always present, possibly `[]`.        |
| `deps`          | array   | *Vector default.*                                        |
| `links`         | array   | *Vector default.*                                        |
| `external_refs` | array   | *Vector default.*                                        |

The four vector-default keys are always arrays in `--json` output even when the on-disk YAML omits them, so
`jq -r '.data[].tags[]'` works uniformly across every ticket. The default is injected only at the JSON boundary;
on-disk pruning is unchanged.

Optional keys:

- `assignee` (string).
- `closed` (string, ISO-8601) — present on tickets in a terminal status.
- `parent` (string) — id of the parent ticket.
- `acceptance` (array of `{title, done}`) — structured criteria, from frontmatter only.
- `children_total` / `children_terminal` (integer) — umbrella progress: direct children across the full corpus (live +
  archive) and the subset in a terminal status. **Present together, and only on umbrella rows** of the four listings
  and `show`, so absence doubles as the predicate — `jq 'select(has("children_total"))'` selects umbrellas. Computed,
  never written to disk.
- `cc`, `leverage`, `coupling`, `level` (integer or `null`) — the `CC` / `LEV` / `CPL` / `LVL` metrics, present on
  **every** `list` / `ready` / `blocked` row and omitted by every other command. Uniform shape: branch on `null`, never
  on key presence. `cc` is `null` for a singleton; `leverage` and `coupling` are `null` on a closed row surfaced by
  `list --status closed`; `level` is `null` on such a row and on a live deps cycle. Definitions are in `knot help list`,
  scope rules and what to do with the numbers in [`graph.md`](graph.md).
- `doc_types` (array of strings) — the distinct types of the documents the ticket owns, the `DOCS` column. **Present
  only on owning rows** of `list` / `ready` / `blocked` / `closed` and of `prime`'s `ready` and `in_progress`
  entries; absent on a ticket owning none, and never on `show`, which carries the fuller `documents` array instead.
- `body` (string) — single-ticket-shape only.
- `sections` (object) — `show --json` only: the same body split by `## ` heading, keyed by the heading slugified the
  way ticket filenames are (`## User Stories` → `user-stories`), each value the raw markdown below it, untrimmed, in
  body order. Text before the first heading lands under `""` (omitted when blank); a repeated heading concatenates.

### Read commands

| Command                     | `data` shape                                                                                       | Body? | Notes                                                                                                                                                                                                                                                                                                                                                                                                       |
|-----------------------------|----------------------------------------------------------------------------------------------------|-------|-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `list` (alias `ls`)         | `ticket[]`                                                                                         | no    | Live tickets only. Rows owning documents carry `doc_types`.                                                                                                                                                                                                                                                                                                                                                                                          |
| `ready`                     | `ticket[]`                                                                                         | no    | Non-terminal, non-blocked, sorted by priority.                                                                                                                                                                                                                                                                                                                                                              |
| `blocked`                   | `ticket[]`                                                                                         | no    | Non-terminal tickets with at least one open `:deps` ref.                                                                                                                                                                                                                                                                                                                                                    |
| `closed`                    | `ticket[]`                                                                                         | no    | Terminal-status tickets from the archive; entries carry `closed`.                                                                                                                                                                                                                                                                                                                                           |
| `show <id>`                 | `ticket`                                                                                           | yes   | Plus `sections`, the computed inverse arrays `data.blockers`, `data.blocking`, `data.children`, `data.linked` — each entry `{id, title, status}` or `{id, missing: true}` — and `data.documents`, always present (`[]` when the ticket owns none), each entry `{id, title, type}`. Documents carry **metadata only**: no bodies, so the envelope stays bounded. Read a body with `knot document show <id> --json`.                                                                                                                                                                                                                                 |
| `document add <ticket>`     | `{id, ticket, title, type, created, updated}`                                                  | no    | The new document's metadata.                                                                                                                                                                                                                                                                                                                                                                                |
| `document show <sel>`       | `{id, ticket, title, type, created, updated, body}`                                            | yes   | The one surface that carries a document body.                                                                                                                                                                                                                                                                                                                                                               |
| `document replace <sel>`    | `{id, ticket, title, type, created, updated}`                                                  | no    | Post-replace metadata. `created` is preserved; `updated` bumps.                                                                                                                                                                                                                                                                                                                                             |
| `document delete <sel>`     | `{deleted: {id, path}}`                                                                        | n/a   | `path` is where the file *was*.                                                                                                                                                                                                                                                                                                                                                                             |
| `document list <ticket>`    | `{ticket, documents}`                                                                          | no    | `documents` is always present, `[]` when the ticket owns none; entries are `{id, ticket, title, type, created, updated}`.                                                                                                                                                                                                                                                                                    |
| `dep tree <id>`             | `{id, title?, status?, missing?, seen_before?, deps?}`                                             | n/a   | Recursive tree node. Tolerant root: a missing id emits `{id, missing: true}` with `ok: true`. Seen-before nodes carry `seen_before: true` and omit `deps`.                                                                                                                                                                                                                                                   |
| `prime`                     | `{project, in_progress, ready_to_close, ready, ready_truncated, ready_remaining, recently_closed, skill_installed, skill_dir, skill_stale, skill_version}` | n/a   | `project` is `{found, prefix, project_name?, live_count, archive_count}`; `ready_truncated` boolean, `ready_remaining` integer; ticket entries body-less. `in_progress` entries may carry `stale: true` (`:updated` 14+ days old) — **in_progress-only**: a `ready` copy of the same ticket never carries it, so iterate `.in_progress` to find stalled work. `ready_to_close` holds active-status tickets whose every `:acceptance` entry is checked, mutually exclusive with `in_progress`; tickets with no AC list never migrate into it. `skill_installed` is a boolean and `skill_dir` the directory whose `SKILL.md` answered the search (`:skill-dir`, then `<project-root>/.claude/skills/knot`, then `~/.claude/skills/knot`), or `null` when none did — the key is always present. `skill_version` is the version stamped into that `SKILL.md`, or `null` when there is no skill or no readable stamp; `skill_stale` is `true` when a skill was found and its stamp is anything but the running CLI's version. Unlike `check`'s `skill_stale`, this flags a home copy too. |
| `info`                      | `{project, paths, defaults, allowed_values, counts}`                                               | n/a   | See *`info` shape*. `counts.doc_count` sizes the document corpus.                                                                                                                                                                                                                                                                                                                                                                                         |
| `check`                     | `{issues: issue[], scanned: {live, archive, docs}}`                                                | n/a   | See *`check` shape*. May co-emit with `ok: false` (health verdict).                                                                                                                                                                                                                                                                                                                                         |

### Mutating commands

| Command                       | `data` shape                                            | Body? | `meta`?     | Notes                                                                                                                                                                                                                                       |
|-------------------------------|---------------------------------------------------------|-------|-------------|---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `create <title>`              | `ticket`                                                | yes   | —           | Brand-new ticket; vector defaults injected at the boundary.                                                                                                                                                                                 |
| `start <id>`                  | `ticket`                                                | yes   | —           | `data.status` flipped to the project's active status.                                                                                                                                                                                       |
| `status <id> <new>`           | `ticket`                                                | yes   | conditional | `meta.archived_to` iff `<new>` is terminal.                                                                                                                                                                                                 |
| `close <id>`                  | `ticket`                                                | yes   | yes         | `data.closed` populated.                                                                                                                                                                                                                    |
| `reopen <id>`                 | `ticket`                                                | yes   | —           | `data.closed` cleared; no path reported.                                                                                                                                                                                                    |
| `dep <from> <to>`             | `ticket` (the `from`)                                   | yes   | —           | `data.deps` is the post-add list.                                                                                                                                                                                                           |
| `undep <from> <to>`           | `ticket` (the `from`)                                   | yes   | —           | `data.deps` is the post-remove list.                                                                                                                                                                                                        |
| `link <a> <b> [<c>…]`         | `ticket[]`                                              | no    | —           | One entry per touched ticket.                                                                                                                                                                                                               |
| `unlink <from> <to>`          | `ticket[]`                                              | no    | —           | Both touched tickets.                                                                                                                                                                                                                       |
| `add-note <id> "<text>"`      | `ticket`                                                | yes   | —           | `data.body` includes the new note.                                                                                                                                                                                                          |
| `update <id> [flags…]`        | `ticket`                                                | yes   | conditional | `meta.archived_to` iff the resulting `data.status` is terminal — including a field-only update to an archived ticket.                                                                                                                       |
| `delete <id>` (+ `--cascade`) | `{deleted: {id, path}, cleaned: [{id, fields: […]}], documents: […]}`   | n/a   | —           | Without `--cascade`, `cleaned` and `documents` are `[]`, and a delete blocked by a referrer or an owned document emits `has_incoming_refs` instead. With it, `cleaned` lists every rewritten referrer, alphabetical by id, `fields` as a string vector, and `documents` lists the ids of the documents removed after the ticket.                                          |
| `migrate-ac`                  | `{migrated, unchanged, total}`                          | n/a   | —           | One-shot legacy migration; `total == migrated + unchanged`.                                                                                                                                                                                 |
| `skill install [<dir>]`       | `{dir, files}`                                          | n/a   | —           | `dir` is the absolute install directory; `files` lists the written files as paths relative to it, in write order. Every install overwrites those files.                                                                                       |

### `info` shape

```json
{
  "project":        { "knot_version": "…", "name": "…", "prefix": "kno", "config_present": true },
  "paths":          { "cwd": "…", "project_root": "…", "config_path": "…", "tickets_dir": ".tickets", "tickets_path": "…", "archive_path": "…", "docs_path": "…", "skill_dir": null, "skill_path": "…" },
  "defaults":       { "default_assignee": "…", "effective_create_assignee": "…", "default_type": "task", "default_priority": 2, "default_mode": "hitl", "default_doc_type": "other" },
  "allowed_values": { "statuses": [...], "active_status": "in_progress", "terminal_statuses": [...], "types": [...], "modes": [...], "afk_mode": "afk", "doc_types": [...], "required_docs": { "<status>": [...] }, "priority_range": { "min": 0, "max": 4 } },
  "counts":         { "live_count": N, "archive_count": M, "total_count": N + M, "doc_count": D }
}
```

`counts` is a raw filesystem listing (no parsing): `live_count` and `archive_count` are top-level `*.md` in those two
directories, and `doc_count` is `*.md` one level down under `docs/`. `total_count` is `live + archive` and does **not**
include documents — they are a separate corpus, and a consumer reading `total_count` means tickets.

Because nothing is parsed or classified, a document filed in the ticket directory instead of under `docs/` counts
toward `live_count` and not toward `doc_count`. That is a real fault, and `knot check` is what diagnoses it
(`doc_directory_mismatch`); for a health verdict use `knot check`, not these counts.

### `check` shape

```json
{
  "issues": [
    { "severity": "error", "code": "dep_cycle", "ids": ["kno-01a", "kno-01b"], "message": "…" }
  ],
  "scanned": { "live": N, "archive": M, "docs": D }
}
```

Issues are sorted `severity` desc → `code` asc → first id asc → `message` asc, identically in JSON and text, so diffs
over time are stable. Every entry carries `severity` (`"error"` / `"warning"`), `code`, `ids`, and `message`; the enum
validators add `field` and `value`. Several codes add `path` (absolute, POSIX-separated — see *Path fields*):
`terminal_outside_archive`, `frontmatter_parse_error` (which also interpolates it into `message`; read `path`, not
`message`), `missing_required_field` when the missing field is `id`, `skill_stale`, whose `path` is the `SKILL.md`
and whose `value` is always present: the stamped version, or `null` when the stamp is missing or unreadable, and the
per-document codes `invalid_doc_type`, `doc_directory_mismatch`, `doc_id_owner_mismatch` and `doc_unknown_ticket`, whose `path` is the document
file, and `unreachable_documents`, whose `path` is the directory holding them. Treat `path` as optional and branch on
its presence.

`code` is an open enum — knot may add codes without bumping `schema_version`:

| Code                       | Severity | Trigger                                                                                                   |
|----------------------------|----------|-----------------------------------------------------------------------------------------------------------|
| `dep_cycle`                | error    | A cycle exists in the `:deps` graph (live + archive).                                                     |
| `unknown_id`               | error    | A `:deps`, `:links`, or `:parent` ref points at no ticket.                                                |
| `invalid_status`           | error    | Ticket `:status` is not in project `:statuses`.                                                           |
| `invalid_type`             | error    | Ticket `:type` is not in project `:types`.                                                                |
| `invalid_mode`             | error    | Ticket `:mode` is not in project `:modes`.                                                                |
| `invalid_priority`         | error    | Ticket `:priority` is not an integer in `0..4`.                                                           |
| `terminal_outside_archive` | error    | Bidirectional: a terminal-status ticket outside `archive/`, or a non-terminal one inside it.              |
| `missing_required_field`   | error    | Ticket or document frontmatter is missing a required key.                                                 |
| `frontmatter_parse_error`  | error    | Ticket file has unparseable YAML frontmatter.                                                             |
| `invalid_active_status`    | error    | `.knot.edn` `:active-status` is not in `:statuses`.                                                       |
| `acceptance_invalid`       | error    | A frontmatter `:acceptance` entry is malformed (non-map, missing `:title` or `:done`).                    |
| `legacy_acceptance_section` | warning | A body still carries a `## Acceptance Criteria` section; `knot migrate-ac` lifts it into frontmatter, after which the warning self-clears. |
| `reserved_section`         | warning  | A body carries a `## Blockers`, `## Blocking`, `## Children`, or `## Linked` heading that `show` renders from fields. `## Documents` has its own code — see `legacy_documents_section`. |
| `duplicate_section`        | warning  | A body carries the same `## ` heading more than once; the copies concatenate, so nothing downstream shows it. |
| `invalid_doc_type`         | error    | A document's `:type` is not in project `:doc-types`. Carries `path`.                                       |
| `doc_directory_mismatch`   | error    | A document's owner directory and its own `ticket` field name different tickets, or it has no `ticket` field. Also covers a document file sitting in the ticket directory or `archive/` rather than under `docs/` — it is classified by filename, so it is diagnosed as misplaced rather than as a malformed ticket. Carries `path`. |
| `doc_id_owner_mismatch`    | error    | The owning ticket embedded in a document's id and its own `ticket` field name different tickets, or the id does not carry an owner at all. The field decides; the id is a locator, so the id is what is wrong. Carries `path`. |
| `doc_unknown_ticket`       | error    | A document names a ticket that resolves to nothing. Carries `path`.                                       |
| `duplicate_doc_id`         | error    | Two or more document files claim the same document id; the message names every path. |
| `unreachable_documents`    | warning  | Documents exist at the default corpus location (`<tickets-dir>/docs`) while `.knot.edn`'s `:docs-dir` points somewhere holding none, so no command can see them. Usually a mistyped or newly-set key. Carries `path`. Move the files or correct the key. |
| `legacy_documents_section` | warning  | A body carries a `## Documents` heading written before the heading was reserved; `show` now renders that section from the document corpus. Remove the heading by hand and re-add its content with `knot document add`, after which the warning self-clears. |
| `skill_stale`              | warning  | The project's installed skill (`:skill-dir`, else `.claude/skills/knot`) has a version stamp that is missing, unreadable, or differs from the CLI's. Fix: `knot skill install`, then commit. The home copy is never checked. |

## Example — an error with an extra field

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

# Read one body section instead of the whole ticket:
knot show "$id" --json | jq -r '.data.sections.description'

# List the open acceptance criteria of a ticket:
knot show "$id" --json | jq -r '.data.acceptance[] | select(.done | not) | .title'

# Watch the project for new integrity issues:
knot check --json | jq '.data.issues[] | select(.severity == "error")'
```
