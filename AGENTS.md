## Hard rules (every task)

- **No AI attribution in commit messages or trailers.** No "Generated with", `Co-Authored-By: Claude`, AI emojis, or similar.
- **Lint before commit.** `clojure -M:clj-kondo --lint ...`

## Where to look

| Topic | Source |
|-------|--------|
| Issue tracking (knot) | [docs/agents/issue-tracking.md](docs/agents/issue-tracking.md) |

## Agent skills

### Issue tracker

Issues are tracked with **knot** — local markdown tickets under `.tickets/`, managed via the `knot` skill. See [docs/agents/issue-tracking.md](docs/agents/issue-tracking.md).

### Triage labels

Triage state is recorded as knot tags (`needs-triage`, `needs-info`, `ready-for-agent`, `ready-for-human`, `wontfix`). See [docs/agents/triage-labels.md](docs/agents/triage-labels.md).

### Domain docs

Single-context layout — `CONTEXT.md` and `docs/adr/` at the repo root, created lazily by `/grill-with-docs`. See [docs/agents/domain.md](docs/agents/domain.md).

