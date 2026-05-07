---
id: aix-01kr1f2504bn
title: 'Attach: run a command then drop into the container shell'
status: closed
type: feature
priority: 2
mode: afk
created: '2026-05-07T14:56:54.276735288Z'
updated: '2026-05-07T15:17:12.381940290Z'
closed: '2026-05-07T15:17:12.381940290Z'
tags:
- ready-for-agent
---

## Description

## Problem Statement

When attaching to an aishell container to run a TUI like `btm`, the user must run `aishell attach <name>` to enter the container shell, then manually type the command. Users who repeatedly launch the same tool on attach (system monitors, REPLs, log tails) hit this friction every time.

## Solution

Extend `aishell attach` so a command can follow `--`:

```
aishell attach <name> [-- <command> [args...]]
```

When a command is provided, aishell opens the container shell, executes the command first, and on exit drops the user into the same shell — not the host. This is exactly equivalent to running `aishell attach <name>` and manually typing the command: same login env, same TTY, same post-exit shell. When no `--` is given, behavior is unchanged.

## User Stories

1. As an aishell user monitoring container resources, I want to run `aishell attach session -- btm`, so that my TUI launches immediately and I land in the shell when I exit it.
2. As an aishell user, I want `aishell attach session` (no `--`) to keep working exactly as today, so that nothing breaks for users who don't need the new capability.
3. As an aishell user invoking a multi-arg command, I want `aishell attach session -- vim "my notes.md"` to preserve argument boundaries verbatim, so that filenames containing spaces open correctly.
4. As an aishell user whose command exits non-zero, I want to still land in the container shell, so that I can investigate the failure rather than being thrown back to the host.
5. As an aishell user running shell pipelines, I want `aishell attach session -- bash -c "btm | tee log"` to work, so that I can opt into shell features explicitly.
6. As an aishell user pressing Ctrl+C while the command runs, I want SIGINT to interrupt only the command, so that I drop into the container shell instead of aborting the whole attach.
7. As an aishell user typing `aishell attach session --` with nothing after, I want a clear error so that I do not mistakenly think I have attached without a command.
8. As an aishell user typing `aishell attach -- btm` without a name, I want the existing "container name required" error so that the failure mode is consistent with how attach already behaves.
9. As an aishell user running `aishell attach --help`, I want the new `-- <command>` form documented in the help text, so that I can discover the feature.
10. As an aishell user passing `--` more than once (`-- bash -c -- foo`), I want only the first `--` consumed by aishell so that subsequent `--` reach the inner command intact.
11. As an aishell user on Windows, I want the new form to work the same as on Unix, so that the experience is cross-platform consistent.
12. As an aishell user without a TTY (piped invocation), I want the command to error the same way today's plain attach does, so that I am directed to use `aishell exec` for non-interactive use.
13. As an aishell maintainer, I want the argv parser tested in isolation, so that edge-case behavior is regression-protected as flags are added later.
14. As an aishell maintainer, I want the docker-exec argv builder tested in isolation, so that the bash wrapper string is locked in by an asserted contract.

## Implementation Decisions

**CLI grammar (locked).**
- `--` is the required separator between aishell args and the command. No `--` means no command; today's behavior is preserved.
- Exactly one container name must precede `--`.
- Everything after the first `--` is the command argv. Further `--` tokens are part of the command.
- Empty command after `--` is an error: `attach: '--' given but no command followed`.

**Execution model (locked).**
Single `docker exec -it -u developer <name>` invocation, single TTY, single bash session. The shell is launched as:

```
/bin/bash --login -c '"$@"; exec /bin/bash --login' _ <argv...>
```

- The outer login bash sources `/etc/profile.d/aishell.sh`, so the container's PATH is set before the command runs.
- `"$@"` invokes the command with argv intact — no shell re-parsing, no escaping.
- `;` (not `&&`) ensures the post-shell launches whether the command succeeds or fails.
- `exec /bin/bash --login` replaces the wrapper with a fresh interactive login shell. The user lands in exactly the same shape they would land in if they had run plain `aishell attach <name>` and typed the command manually.

**Argv vs. shell-string semantics (locked).**
Argv passthrough, matching `docker exec` and `kubectl exec`. Pre-quoted shell strings (`-- "btm | tee log"`) are not supported as a single command — users wanting shell features write `-- bash -c "btm | tee log"`.

**Modules.**

- `aishell.attach.parse` *(new, deep)* — pure: `(parse-attach-args rest-args) → {:name, :command-argv} | {:error msg}`. Encapsulates `--`-split, name validation, and edge-case errors. No I/O.
- `aishell.attach.invocation` *(new, deep)* — pure: `(build-docker-exec-argv {:container, :term, :command-argv}) → vector-of-strings`. Encapsulates the bash wrapper construction. When `:command-argv` is nil/empty, returns today's plain `bash --login` form.
- `aishell.cli` attach handler *(modified)* — calls the parser, prints errors via the existing error-printer, hands `{:name, :command-argv}` to `attach-to-container`.
- `aishell.attach/attach-to-container` *(modified)* — accepts an optional `:command-argv` key, delegates argv construction to `aishell.attach.invocation`, applies it through both Unix `p/exec` and Windows `p/process` code paths.

**Validation reuse.**
TTY validation (`validate-tty!`) and container-state validation (`validate-container-state!`) apply unchanged. The post-shell needs a TTY in both forms.

**Help text.**
The `attach` block in the CLI help gains a one-line synopsis `aishell attach <name> [-- <command> [args...]]` and a one-line note that the command runs first, then the user drops into the container shell.

## Testing Decisions

A good test for this work asserts external behavior, not internal structure: given inputs to a pure function, assert the exact output. Implementation details (helper arity, intermediate maps) are not asserted.

**Tested modules.**

- `aishell.attach.parse` — exhaustive unit tests covering: happy path with no command (`["session"]`), with command (`["session" "--" "btm"]`), multi-arg command (`["session" "--" "vim" "my notes.md"]`), multiple `--` (`["session" "--" "bash" "-c" "--" "foo"]` → argv `["bash" "-c" "--" "foo"]`), empty command (`["session" "--"]` → exact error), no name (`["--" "btm"]` → exact error), multiple names before `--` (`["a" "b" "--" "btm"]` → exact error).
- `aishell.attach.invocation` — equality tests on the produced argv: no-command form regression-locks today's shape (`src/aishell/attach.clj:76,85`); with-command form locks the exact wrapper `["/bin/bash" "--login" "-c" "\"$@\"; exec /bin/bash --login" "_" "btm"]`. TERM/COLORTERM/LANG/LC_ALL env passthrough preserved in both forms.

**Prior art.**
The repository has no `test/` tree today; `bb.edn` declares no `:test` paths. Establishing a minimal Babashka test harness (`test/` path, a `bb test` task, the first `clojure.test` namespaces) is part of this work — there is no existing pattern to follow. Subsequent tests in the project will reuse this scaffolding.

**Not tested here.**
No end-to-end test against a real Docker daemon. The pure-function tests cover the contract; the `docker exec` invocation is a thin shell out of the builder's output and not worth the integration overhead for this PR.

## Out of Scope

- New `attach` flags (`--user`, `--workdir`, `--env`). This work adds only the `-- <command>` shape.
- Recording the pre-run command into the post-shell's bash history. Would require host→container history-file plumbing for marginal value.
- Non-TTY scripted use of attach. `aishell exec` already covers that case.
- Changes to `aishell exec`, `aishell vscode`, or any other subcommand.
- Refactoring the existing docker-run command-builder pipeline. There is a separate ticket (`aix-01kr1d6txvn3`) tracking deepening that pipeline; this work does not pre-empt it.

## Further Notes

- The single-bash-with-`exec` model means signals (SIGINT, SIGQUIT) reach the foreground command directly without a shell wrapper intercepting them. After the command exits, the wrapper bash is replaced by `exec`, so no lingering parent process sits between the user and the shell.
- The argv-injection trick (`'"$@"' _ <argv>`) is portable across bash versions and avoids any host-side shell-escaping logic. The Clojure code passes `command-argv` straight through — no string interpolation, no escape paths.
- `aishell.attach.parse` and `aishell.attach.invocation` are intentionally small modules. Their value is testability: both isolate pure logic from the I/O-heavy `attach-to-container` orchestration. They are not intended to grow into general-purpose CLI utilities.

## Notes

**2026-05-07T15:17:12.381940290Z**

Shipped attach -- <command> form.

- New aishell.attach.parse (pure): splits args around `--`, validates name/cmd, returns {:name :command-argv} or {:error}. 7 unit tests.
- New aishell.attach.invocation (pure): builds the docker exec argv. With command-argv, wraps as `bash --login -c '"$@"; exec /bin/bash --login' _ <argv...>` so signals reach the foreground command and the user lands in a fresh login shell on exit. 4 unit tests.
- Wired into aishell.attach/attach-to-container (now `(name & {:keys [command-argv]})`) and aishell.cli attach handler.
- bb test scaffolding added: test/run_tests.clj discovery runner, `bb test` task in bb.edn, test/ on :paths. First clojure.test in the repo.
- Help text shows the new synopsis and run-then-drop note.

Out of scope per ticket and confirmed: no Docker integration tests; existing flags (--user/--workdir/--env) untouched; aishell exec/vscode untouched.
