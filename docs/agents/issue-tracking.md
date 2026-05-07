# Issue Tracking

This project tracks work with **knot** — markdown tickets under `.tickets/` (closed tickets auto-archive to `.tickets/archive/`). Config lives in `.knot.edn` at the repo root.

## Use the `knot` skill

For any ticket-shaped intent — "what's next", "track this", "show me <id>", "I'm done", "blocked on X", "any open bugs?" — invoke the `knot` skill. The CLI keeps frontmatter, the dep graph, and the archive consistent, and resolves partial IDs across live + archive.

## Never hand-edit `.tickets/`

No `Read`, `cat`, `grep`, `Write`, `Edit`, `sed`, or `mv` against files under `.tickets/`. Use `knot show` / `knot list --json` / `knot create` / `knot update` / `knot add-note` / `knot close`.

If a `knot` command behaves unexpectedly, surface the bug — don't bypass.

## Hosted-tracker prefixes

`GH-`, `ENG-`, `LIN-`, `JIRA-` point at *other* trackers (GitHub Issues, Linear, Jira). Use the matching tool for those, not `knot`.

---

# Workflow for engineering skills

The sections below document the workflow the Matt Pocock engineering skills (`to-issues`, `triage`, `to-prd`, `qa`, …) use to interact with knot. See also [triage-labels.md](triage-labels.md) for the triage-role → knot-field mapping.

## When a skill says "publish to the issue tracker"

Use `knot create "<title>"`, then `knot update <id>` to set fields, or `knot add-note <id>` for additional context. Do not write under `.tickets/` by hand.

    knot create "<title>" --type <bug|feature|task|epic|chore> --priority <0..4> --mode <afk|hitl>
    knot update <id> --tags <comma,list> --description "..."
    knot add-note <id> "additional context"

## When a skill says "fetch the relevant ticket"

    knot show <id>            # frontmatter + body for one ticket
    knot list --json          # programmatic listing of live tickets
    knot ready                # non-blocked tickets (alias for "what's next")

Knot resolves partial ids across live + archive, so `knot show 01abc` is enough.

## Patching tickets — `update` vs `edit`

- `knot update <id> [flags]` — non-interactive patch. **Use this from skills.**
- `knot edit <id>` — opens an editor. Reserved for humans.

`knot update --help` lists every patchable field: title, type, priority, mode, assignee, parent, tags, external-ref, description, design, acceptance, body.

### Tag deltas: `--add-tag` / `--remove-tag`

`knot update --tags` overwrites the whole tag list. To **add or remove** tags
without round-tripping the existing set, use the per-tag deltas (repeatable,
idempotent, mutually exclusive with `--tags`):

    knot update <id> --add-tag needs-triage
    knot update <id> --add-tag stale --remove-tag wip

The `triage` skill should reach for `--add-tag needs-triage` / `--add-tag
needs-info` / `--add-tag wontfix` rather than reading-then-replacing the
whole tag list.

## Lifecycle

    knot start <id>                     # open → in_progress
    knot close <id> --summary "..."     # in_progress → closed (auto-archives)
    knot dep <a> <b>                    # mark <a> blocked on <b>
    knot dep tree <id>                  # show what's blocking <id>
