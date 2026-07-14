# Listing filters and columns

Deep semantics for the listing commands (`list`/`ready`/`blocked`/`closed`,
and `prime`). `SKILL.md` covers the common filter set and when to reach for
each; this file is the on-demand reference for the graph filters and the
computed columns.

## Graph filters

All five listing commands accept the common filter set (each repeatable):

```
--type <type>      --mode <afk|hitl>    --tag <tag>
--status <status>  --assignee <user>    --priority <0-4>
--limit <n>
```

On `prime`, filters apply across **all** sections (in_progress + ready +
recently_closed) — `knot prime --assignee me` shows only your tickets
everywhere.

### `--parent <id>`

Filters to the **direct children** of a parent on the four listing commands
(`list` / `ready` / `blocked` / `closed`, *not* `prime`). It is repeatable
(children of any given parent), and its value resolves like any partial id
(live+archive) — an unresolvable value fails loudly (stderr die, or a
`not_found` / `ambiguous_id` envelope under `--json`).

### `--closure <id>[,<id>…]`

Filters to the **undirected transitive closure** of the seed(s) — every
ticket reachable from a seed by walking `:parent`, `:deps`, and `:links`
edges in *both* directions, recursively (the seed itself is included). Use
it for "everything related to this ticket," where `--parent` (1 hop,
children only) and `dep tree` (directed `:deps` only) stop short. Available
on the same four listing commands. Multi-seed is a **union** (comma-separated
or repeatable). `--via <axes>` (comma-separated subset of
`parent,deps,links`, default all three) narrows which edge types the walk
follows — e.g. `--via parent,deps` to skip the noisier `:links` axis. The
closure is computed over the full corpus (archive included) so it never halts
at a closed ticket, but each command's normal display filter still governs
what's shown (`list` defaults to live, `closed` to terminal). It composes
with every other filter (`--type`, `--status`, `--limit`, `--json`); output
is a plain list — no extra columns or JSON fields. Seeds resolve like
`--parent` (partial ids, loud failure on no/ambiguous match).

### `--component <id>`

Filters to the seed's **live-induced connected component** (the `CC`
column's action-companion: the column reveals the live islands,
`--component` isolates one to work on it). Available on `list`/`ready`/`blocked`
(NOT `closed` — the archive has no live components). It restricts the
*traversal* to live tickets (closed = non-conductive), so it matches the `CC`
column exactly — and is therefore **distinct from `--closure`**, not a live mode
of it: `--closure` is corpus-wide where a closed ticket still conducts, so a
live `A`—closed `C`—live `B` chain is one closure but two live components. Fixed
shape (ADR 0014): a **single** id resolved by partial match like `--parent`
(**never** an ordinal — `--component 1` fails to resolve; an unresolvable or
ambiguous seed dies on stderr, or returns a `not_found` / `ambiguous_id`
envelope under `--json`), **all** axes (no `--via`), the seed **must be live**
(a closed seed is a fail-fast error, not a silent empty — note this one dies on
stderr with exit 1 even under `--json`, it is *not* an error envelope), and
**mutually exclusive with `--closure`** (passing both → fail-fast). Membership is
computed over the full live corpus and intersected before display filters, so
`--component X --tag p0` is `(X's live component) ∩ (p0-tagged)`; `--limit`
applies last. Output shape is unchanged (the `CC` column still renders the
cluster's constant ordinal; `--json` rows still carry `cc`) — it is a filter,
not a visualization. Any cluster member names the whole island, so feed a `CC`
member id straight back in.

### Composition

```
knot list --type bug --type chore
knot ready --mode afk --tag p0
knot ready --priority 0
knot blocked --mode afk
knot closed --type bug --limit 5
knot list --parent kno-01abc
knot list --closure kno-01abc --via parent,deps
knot list --component kno-01abc
```

## Columns

Full layout:
`CC ID STATUS PRI MODE TYPE ASSIGNEE AGE [AC] [CHLD] [LEV] [CPL] TITLE`.

### `CC` — connected components

`list`/`ready`/`blocked` (NOT `closed`) carry a leading `CC` column (before
`ID`) marking which connected component of the *live-induced* graph the row
sits in, over all three axes (`:parent` ∪ `:deps` ∪ `:links`, undirected) —
closed tickets are non-conductive, so a cluster joined only through a closed
bridge splits. Membership and size are **filter-independent** (computed over
all live tickets; `--tag`/`--type`/`--limit` never change a row's component).
The label is a **throwaway global ordinal**: only components with **≥2 live
members** are numbered, **size-descending** (largest = `1`), ties by min
member id; **singletons render `-`**. Numbering is global, so a filtered view
may show non-contiguous numbers (`1, 3, 4`) — it is a within-snapshot grouping
aid, not a stable id. The text column is present **iff at least one visible
row carries a real ordinal** (stricter than `LEV`/`CPL`: an all-singleton view
shows no column). In `--json`, **every** list/ready/blocked row carries a `cc`
field — integer ordinal or **`null`** for singletons (uniform shape; don't
branch on key-presence); `closed --json`, `show`, and all non-listing commands
omit it. NB the live-induced scope deliberately differs from `--closure`
(corpus-wide, single-seed).

### `CHLD` — umbrella progress

When a result set contains at least one *umbrella* (a ticket with ≥1 direct
child), the four listing commands add a `CHLD` column showing `terminal/total`
of that ticket's direct children (`-` for non-umbrellas); the column is hidden
entirely when no umbrella is present. `show` mirrors this as a
`## Children (d/t)` heading. `terminal` counts every closed child including
`Won't do:` closures, and the tally spans live+archive, so it asserts nothing
about readiness — an umbrella at `0/5` can still be `ready`. In `--json`,
umbrella rows carry `children_total`/`children_terminal` (present only on
umbrellas, so `jq 'select(has("children_total"))'` selects them); read these
instead of re-deriving the rollup from `--parent` queries.

### `LEV` — leverage

`list`/`ready`/`blocked` (NOT `closed`) carry a `LEV` column: the count of
*live* tickets that transitively depend on the row through `:deps` — its
forward unblocking cone, computed over the *live-induced* deps subgraph. A
closed intermediary is non-conductive and **severs** the cone (its dependents
are not reached through it, and it is not tallied); cycles are guarded and
broken refs dropped; the row itself is excluded. High `LEV` flags a keystone —
closing it unblocks the most work. It is independent of readiness: a deps-leaf
can be both `ready` and highest-leverage. The column is always present on those
three listings (`-` never appears; a leaf shows `0`). In `--json`, those rows
carry a `leverage` integer; `closed --json`, `show`, and all non-listing
commands omit it.

### `CPL` — coupling

`list`/`ready`/`blocked` (NOT `closed`) carry a `CPL` column beside `LEV`: the
count of *distinct live* tickets the row is directly connected to at one hop
through `:deps` (in **either** direction) or `:links` — its undirected 1-hop
degree over those two axes, computed over the *live-induced* graph. `:parent`
is excluded (that rollup is `CHLD`); neighbors are deduped across axes (a pair
joined by both a dep and a link counts once); closed neighbors and broken refs
are dropped; the row itself is never counted. High `CPL` flags a tangled,
high-context ticket. It is 1-hop only (no transitive walk). The column is
always present on those three listings (`-` never appears; an isolated ticket
shows `0`). In `--json`, those rows carry a `coupling` integer; `closed --json`,
`show`, and all non-listing commands omit it.

### `AC` — acceptance progress

Listing tables (`list`/`ready`/`blocked`/`closed`) gain a conditional `AC`
column rendered as `d/t` (e.g. `2/5`) immediately before `TITLE`. The column
is omitted entirely when no ticket in the result set has acceptance, so quiet
projects don't pay the width cost. Tickets without AC render as `-`.
Force-closed terminal tickets render their partial counts (`2/5`) — useful
audit signal when scanning archive. `--json` is unchanged: raw `:acceptance`
already passes through.

### `AGE`

Every listing table carries an `AGE` column to the immediate left of `AC` (or
`TITLE` when `AC` is absent), bucketed from each ticket's `:updated` against
`now`: `Nd` (<14d), `Nw` (14–42d, floor by 7), `Nm` (>42d, floor by 30), or `-`
when `:updated` is missing or unparseable. Same bucketing the
`knot prime ## In Progress` column already uses. `--json` is unchanged —
consumers compute age client-side from the existing `:updated` field; no new
keys, no schema bump.
