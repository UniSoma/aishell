# Secret scanning stops at sensitive-file detection

aishell's share of secret scanning is sensitive-file detection: the
launch-time check for files whose names suggest secrets the harness would be
able to read. Scanning file contents and git history for committed secrets
belongs to each project, in a pre-commit hook or CI. aishell therefore drops
its Gitleaks support: the `aishell gitleaks` subcommand, the
`--with-gitleaks` setup flag, and the scan freshness reminder.

## Context

Two different threats get called "secret scanning". One is exposure to the
harness: a `.env` or key file in the mounted project that the harness can read and
send out. That happens when a Sandbox starts, so it is aishell's concern, and
sensitive-file detection covers it on every launch without being asked. The
other is commit leakage: a secret landing in git history or a push. That
happens on every change, whether or not aishell is involved, and only a check
on the commit path catches it reliably.

`aishell gitleaks` covered neither threat well. It ran only when someone
remembered to run it. The freshness reminder counted only scans run through
aishell, so a project that already scanned in pre-commit or CI was told its
scan was stale. And the reminder measured days since the last scan, not
changes since then.

It also broke two domain terms. The Foundation image is meant to be identical
for every user, but `WITH_GITLEAKS` was a build argument that made it differ.
A Harness is an AI coding CLI, but Gitleaks sat in the Harness registry
without being one, and needed its own flags and exit-code handling in the
launch path.

## Considered options

- **Keep Gitleaks as an opt-in tool (rejected).** Its one advantage is not
  having to install gitleaks on the host. A Project extension that installs
  it, run with `aishell exec gitleaks dir .`, gives the same thing without a
  per-user Foundation image.
- **Keep only the freshness reminder (rejected).** It is the one piece a
  project setup cannot provide, but it cannot see scans run elsewhere, so it
  is wrong for exactly the projects that take secret scanning seriously.

## Consequences

- Removing a subcommand and a setup flag makes this a major release.
- A saved `:with-gitleaks` in `state.edn` gets the removed-harness
  treatment: one warning, then the key is dropped. Leftover
  `gitleaks_freshness_*` config keys fall under the generic unknown-key
  warning. The old scan timestamp file is left in place.
- The documentation says where gitleaks goes now: pre-commit or CI for commit
  leakage, or a Project extension for anyone who wants it inside the Sandbox.
