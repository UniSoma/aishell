# Writes

Every write to a ticket picks between appending and overwriting, and the next one does not undo the last.

## Notes and revisions

`knot add-note` appends a timestamped entry — the tool for observations captured mid-task. `knot update` replaces:
`--description` the section, `--body` the entire body (destructive, git is the undo), fields and status in the same
call, non-interactively — the tool for scripts and autonomous runs. `knot edit` opens `$EDITOR` and needs a TTY. To add
to a ticket, reach for `add-note`.

## A render is not a body

Six sections in a `knot show` render — `## Acceptance Criteria`, `## Blockers`, `## Blocking`, `## Children`,
`## Linked`, `## Documents` — are synthesized from the `acceptance`, `deps`, `parent`, and `links` fields and from the
document corpus, and are marked in the render by an HTML comment naming the source. What you would have written under
one goes through the owning writer instead (`--add-ac`, `knot dep`, `--parent`, `knot link`, `knot document add`).
`--body` refuses those six names; near-synonyms are not refused and are the same mistake — a hand-written
`## Blocked by`, `## Depends on`, or `## Attachments` is prose that stops matching the graph the moment the graph
moves.

## Notes, documents and the body

Three places hold prose. A **note** is an observation about the work, appended in time order — `knot add-note`. The
**body** is the ticket's own account of itself, replaced in place — `knot update`. A **document** is a whole artifact
the ticket carries rather than is — a spec, a plan, a transcript — with its own title, type and file, and a ticket may
carry any number. Reach for a document when the content would swamp the ticket or has a life of its own; for the body
when the content *is* the ticket.

A document id nests inside its owning ticket — `kno-01m2s4ecygyc-d7f3k` belongs to `kno-01m2s4ecygyc` — so you can
read the owner off the id without a lookup. `knot document --help` states the shape and what a selector accepts.

Listings show a DOCS column naming the types each ticket owns, so check there before
writing a spec that may already exist. A project can require one: `.knot.edn`'s `:required-docs` names
document types a ticket must own before it may enter a status, and the transition is refused until they
are attached.

## Replace vs delta

`--tags` and `--external-ref` replace the whole list, so re-sending a list to add one value drops anything you hadn't
read first. For a one-value change reach for the delta flags — `--add-tag` / `--remove-tag`, `--add-external-ref` /
`--remove-external-ref`, `--add-ac` / `--remove-ac` — which leave the rest untouched.

Address a criterion by the number `knot show` prints beside it rather than retyping it — real AC titles run to a
paragraph — and batch flips in one write: `knot update <id> --ac 2 --ac 5 --done`.
