## Hard rules (every task)

- **No AI attribution in commit messages or trailers.** No "Generated with", `Co-Authored-By: Claude`, AI emojis, or similar.
- **Run `bb pre-commit` before commit; it is the gate.** It runs the clj-kondo binary over `src` and `test` (`deps.edn` carries no `:clj-kondo` alias), then the test suite, and fails on either. `scripts/` is not linted: it has two known namespace-name errors that stay as they are.
- **The sandbox has no Docker.** There is no `docker` binary here, so anything that runs a container or builds an image is a host-only check: hand the user the exact command.

## Where to look

| Topic | Source |
|-------|--------|
| Issue tracking (knot) | [docs/agents/issue-tracking.md](docs/agents/issue-tracking.md) |
| Landing a ticket: gate, focused tests, ticket-only commit, kept work | [docs/agents/landing.md](docs/agents/landing.md) |

## Agent skills

### Issue tracker

Issues are tracked with **knot** — local markdown tickets under `.tickets/`, managed via the `knot` skill. See [docs/agents/issue-tracking.md](docs/agents/issue-tracking.md).

### Research

Research for a ticket is saved as a knot document on that ticket (`knot document add`), not as a file in the repo.

### Triage labels

Triage state is recorded as knot tags (`needs-triage`, `needs-info`, `ready-for-agent`, `ready-for-human`, `wontfix`). See [docs/agents/triage-labels.md](docs/agents/triage-labels.md).

### Domain docs

Single-context layout — `CONTEXT.md` and `docs/adr/` at the repo root, created lazily by `/grill-with-docs`. See [docs/agents/domain.md](docs/agents/domain.md).

