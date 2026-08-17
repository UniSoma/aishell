# Lifecycle gates

Two gates guard status transitions: the **acceptance gate** (terminal
transitions) and the **open-children gate** (start *and* close). `SKILL.md`
covers what fires them and how to clear them in the moment; this file is the
on-demand reference for the full skip-condition matrix and the start-vs-close
`--summary` asymmetry.

## Acceptance gate on terminal transitions

`knot close`, `knot status <id> <terminal>`, and `knot update <id> --status <terminal>` all enforce the v0.3 acceptance
gate: when the ticket is in `:active-status` (default `in_progress`) and any frontmatter `:acceptance` entry has `done:
false`, the transition is blocked (JSON `error.code = "acceptance_incomplete"`, exit 1).

The gate skips on:

- Empty / nil `:acceptance`.
- Intake → terminal transitions (no work was started).
- Terminal → terminal reclassifications (e.g. `closed → wontfix`).

Two ways to clear it:

1. Mark the AC done — `knot update <id> --ac "<title>" --done`. Composes with `--status` in one call: `knot update <id> --ac "last AC" --done --status closed` checks then closes.
2. `--force --summary "<reason>"`. Required pair: `--force` without a non-blank `--summary` exits `invalid_argument`. The summary is appended as a Notes entry and serves as the override record.

## Open-children gate on start and close transitions

The open-children gate fires on two transitions:

- **Close** (`active → terminal`): `knot close`, `knot status <id> <terminal>`, `knot update <id> --status <terminal>`.
- **Start** (`* → active`): `knot start`, `knot status <id> <active>`, `knot update <id> --status <active>`.

The gate fires when the ticket has at least one child (any ticket whose `:parent` is this id) whose status is
non-terminal (JSON `error.code = "open_children"`, exit 1 — same envelope shape for both transitions).

The gate skips on:

- Tickets with no children.
- Parents whose children are all in a terminal status.
- `active → active` no-op transitions and intake → terminal transitions (no meaningful start or close).
- Terminal → terminal reclassifications.

Override is `--force`, with asymmetric `--summary` semantics:

- **Close**: `--force --summary "<reason>"` is the required pair — `--force` without a non-blank `--summary` exits
  `invalid_argument`. The summary is appended as a Notes entry and serves as the override record. When both AC and
  open-children gates would fire on the same close, a single `--force` bypasses both and stderr emits one warning per
  gate.
- **Start**: `--force` alone is enough (no `--summary` required, and passing `--summary` to a non-terminal target is
  rejected up front). Start is provisional — you can `update --status` back to intake at zero cost — so the bypass
  leaves only the stderr enumeration as a trace, not a Notes entry.
