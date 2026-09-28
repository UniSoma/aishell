# Lifecycle gates

Three gates guard status transitions — the **acceptance gate** (terminal transitions), the **open-children gate**
(start *and* close) and the **required-documents gate** (any status `.knot.edn` names) — and an opt-in check, the **conditional claim** (`--if-unassigned`), guards who gets to do the
work. `SKILL.md` covers what fires the gates and how to clear them in the moment; this file is the full skip-condition
matrix, the start-vs-close `--summary` asymmetry, and the judgment behind the claim predicate.

## Acceptance gate

Fires on any active→terminal transition — `close`, `status <id> <terminal>`, `update --status <terminal>` — when the
ticket sits in the project's `:active-status` and any frontmatter `:acceptance` entry has `done: false`
(`error.code = "acceptance_incomplete"`, exit 1).

Skips on:

- Empty or absent `:acceptance`.
- Intake → terminal (no work was started).
- Terminal → terminal reclassification (`closed → wontfix`).

## Open-children gate

Fires on **close** (active → terminal) and **start** (* → active), through any of the three transition commands, when
at least one child — a ticket whose `:parent` is this id — is in a non-terminal status (`error.code = "open_children"`,
exit 1, the same envelope on both transitions).

Skips on:

- No children, or every child terminal.
- Active → active no-ops and intake → terminal (no meaningful start or close).
- Terminal → terminal reclassification.

## Required-documents gate

Fires on a move into any status that `.knot.edn`'s `:required-docs` lists, through `start`, `close`, `status` or
`update --status`, when the ticket does not own a document of every type that status requires
(`error.code = "missing_required_docs"`, exit 1, `error.missing_doc_types` naming only the missing types).

Skips on:

- A status with no entry in `:required-docs`, or a project with no `:required-docs` at all.
- Re-entering the status the ticket already holds: the gate guards the move, not residence.

Unlike the other two, it does not skip intake → terminal or terminal → terminal: if `closed` requires a document, every
route into `closed` needs one.

## `--force` and the `--summary` asymmetry

- **Close**: `--force --summary "<reason>"` is a required pair — `--force` with a blank `--summary` exits
  `invalid_argument`. The summary lands as a Notes entry and is the override's record. When several gates would fire on the
  same close, one `--force` bypasses them all and stderr warns once per gate. The pair is checked only when a gate actually
  fires: with nothing to bypass, `--force` is a no-op and a summary-less close goes through like any other.
- **Start**: `--force` alone; a `--summary` on a non-terminal target is rejected up front. Start is provisional —
  `update --status` back to intake costs nothing — so the bypass leaves only the stderr enumeration as a trace, not a
  Notes entry.

## The conditional claim

`knot start <id> --assignee <me> --if-unassigned` — and `knot update` with the same flag — answers "take this ticket,
only if nobody already did": the assignee is read before the gates and before the write, a non-blank one writes nothing
and exits 1, and `--json` reports `already_assigned` with `error.current_assignee`. On a lost claim `update` drops every
other flag in the call, so `--if-unassigned --assignee me --priority 0` never half-applies.

What the flag text can't say:

- The predicate answers "was this free?", not "is this mine?" — re-running a claim you already won reports
  `already_assigned`, which for a polling loop is the right answer: someone holds it, move on.
- The read-modify-write window between the check and the save is small but real. This is a same-host courtesy
  protocol, not a lock; for a genuinely contended frontier, partition it instead (by `--parent`, say).
