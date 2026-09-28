# Listing filters and columns

Deep semantics for the listing commands (`list` / `ready` / `blocked` / `closed`, and `prime`). `knot <cmd> --help`
defines every filter and computed column; this file is what the help can't say — where each filter's scope ends, what
fails fast, and how to act on the numbers.

## `--acceptance-complete`

Bare `--acceptance-complete` means `=true`. Tickets with no `:acceptance` list are excluded from *both* views — absent
criteria mean the dimension does not apply — so `=false` and `=true` do not partition the corpus, and `=false` is "has
an unchecked AC", not "isn't finished".

`list --acceptance-complete=true` is close to `prime`'s *Ready to close* but not identical: `prime` additionally
requires the project's `:active-status`, where `list` spans every live status. A fully-checked ticket still in `open`
shows in the `list` view and not in `prime`'s.

## `--assignee ""`

Empty means **unassigned** on all five commands, mirroring `update <id> --assignee ""`, and composes with named handles
in the same call: `--assignee "" --assignee alice` is "free or alice's". `knot ready --assignee ""` is the frontier an
agent claims from; the claim predicate is in [`lifecycle.md`](lifecycle.md).

## Graph filters

Three filters answer three different questions — `--parent` direct children, `--closure` everything transitively
related, `--component` the seed's live cluster. All resolve partial ids and fail loudly on no or ambiguous match (stderr
die, or a `not_found` / `ambiguous_id` envelope under `--json`) — except on `prime`, which always exits 0 and degrades
to the no-project primer instead.

### `--closure`

The undirected transitive closure of the seed(s) over `:parent`, `:deps`, and `:links`, seed included; multi-seed is a
union. The walk is **corpus-wide** — a closed ticket still conducts, so the closure never halts at one — but each
command's display filter still governs what's shown (`list` live, `closed` terminal). It composes with every other
filter, and the output is a plain list: no extra columns or JSON fields. `--via` narrows the edge types, e.g.
`--via parent,deps` to skip the noisier `:links` axis.

### `--component`

The seed's **live-induced** connected component — the traversal treats closed tickets as non-conductive — so it matches
the `CC` column exactly and is distinct from `--closure`, not a live mode of it: a live `A` — closed `C` — live `B` chain
is one closure but two components. Consequences: the seed must be live (a closed seed dies on stderr with exit 1 *even
under `--json`*, no error envelope), it takes a single id and no `--via`, it is mutually exclusive with `--closure`, and
it is absent from `closed` (the archive has no live components). Membership is computed over the full live corpus and
intersected before the display filters, so `--component X --tag p0` is `(X's live component) ∩ (p0-tagged)`, with
`--limit` applied last. Any member names the whole island — feed a `CC` member id straight back in.

## Acting on the columns

`knot help list` defines each computed column and names its `--json` field. What it can't tell you is what to do with
the number:

- **`LEV` picks the next ticket when several are ready.** The highest-leverage ready ticket dissolves the most waiting
  structure, so it is the default answer to "which of these first?" — priority overrides it only when a deadline says
  so.
- **`CPL` is a cost, not a virtue.** A high-coupling ticket needs the most surrounding context loaded before it can be
  reasoned about: a poor fit for a cold agent run and a good candidate for splitting.
- **`LEV` and `CPL` are orthogonal to readiness.** A deps-leaf can be `ready` and the highest-leverage row at once;
  neither number says whether the ticket can be started.
- **`LVL` orders work into waves.** Level 0 can run now, level 1 becomes ready once level 0 closes, and so on —
  `knot blocked --parent <id> --json | jq 'group_by(.level)'` is an orchestrator's schedule with no separate command.
  `LEV` says which ready ticket to take first; `LVL` says how many rounds away the rest are.
- **A `-` in `LVL` is a bug report, not a big number.** The ticket is on a live deps cycle, so no schedule exists until
  the cycle is broken — `knot check --code dep_cycle`, then cut an edge.
- **`CHLD` is progress, not readiness.** An umbrella at `0/5` may still be `ready` — its own integration work is what
  is ready, not its children.
