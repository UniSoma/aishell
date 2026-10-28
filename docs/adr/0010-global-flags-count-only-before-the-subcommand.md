# Global flags count only before the subcommand

aishell reads its Global flags (`--verbose`, `--base-image`, `--unsafe` and
`--name`) only when they come before the subcommand: `aishell --verbose
claude`. Everything after a harness name goes to the Harness untouched, so
`aishell claude --verbose` turns on Claude Code's verbose mode, not aishell's.
`--base-image`, `--unsafe` and `--name` given before a subcommand that does
not use them are refused with an error rather than ignored.

## Context

Harnesses own the arguments after their name, and they reuse the same flag
names: Claude Code has its own `--verbose`. aishell used to take `--unsafe`
and `--name` from anywhere in the arguments, and moved the harness name to the
front before reading `--verbose`. That swallowed flags meant for the Harness,
and in the other direction sent a leading `--verbose` to the Harness instead
of turning on aishell's output.

## Decision

- **Position decides ownership.** Flags before the subcommand are aishell's;
  flags after a harness name are the Harness's. One parser,
  `cli/parse-global-flags`, reads them before dispatch.
- **A flag that the subcommand ignores is an error.** `--base-image`
  applies only to harness launches, bare `aishell` and `exec`; `--unsafe`
  and `--name` only to harness launches and bare `aishell`. `aishell --unsafe ps` exits 1 instead of quietly dropping the
  flag. `--verbose` is accepted before any subcommand.
- **`--json`, `--help` and `--version` are not Global flags.** They keep their
  own handling per command.

## Considered options

- **Read aishell's flags from anywhere, with `--` to forward to the Harness.**
  Rejected: every existing harness invocation that passes a flag aishell also
  knows would change meaning, and users would have to remember which names
  collide.
- **Reserve aishell-specific names (`--aishell-verbose`).** Rejected: longer
  to type for the common case, and the collision problem returns with each new
  Harness flag.

## Consequences

Scripts that put `--unsafe` or `--name` after the harness name had to move
them in front. Flags that used to be silently ignored now stop the command.
