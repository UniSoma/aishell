# VSCode joins a running Sandbox

`aishell vscode` opens VSCode in a Sandbox that is already running, the
same way bare `aishell attach` does: with exactly one Sandbox running it
joins that one, and with a name it joins the named one. It starts its own
`vscode` Sandbox only when none is running. VSCode is now opt-in at setup
(`aishell setup --with-vscode`), and the VSCode server lives in an
aishell-managed Docker volume instead of a bind mount of host
`~/.vscode-server`.

## Context

The usual way to work is one Sandbox, started by bare `aishell`, with
several harnesses attached to it in other terminals. `aishell vscode`
always started a separate `vscode` Sandbox, so the editor never saw the
processes, files outside the project and tools of the Sandbox the
harnesses were running in.

Joining a Sandbox makes the VSCode window an Attached session. It does not
own that Sandbox, so `aishell vscode` must never stop it, and the window
disconnects when the Owning session exits.

A Sandbox started by a Harness or by bare `aishell` had no VSCode server
mount, so joining it would reinstall the server and extensions every time
the Sandbox started. Mounting the server into every Sandbox fixes that, but
mounting host `~/.vscode-server` would let any Harness change code that
VSCode runs. If the host is ever reached through Remote-SSH, that directory
is the host's real VSCode server, so a Harness could escape the Sandbox
through it.

## Decision

- Bare `aishell vscode` joins the only running Sandbox, and
  `aishell vscode <name>` joins the named one. With none running it starts
  the `vscode` Sandbox. With several running it errors and lists them.
  There is no flag that forces a separate Sandbox.
- A joined Sandbox is never stopped by `aishell vscode`: not when the
  window closes, not on Ctrl-C, and not by `--stop`. The command returns
  right away after opening the window.
- `aishell setup --with-vscode` is a boolean setup option. With it off,
  `aishell vscode` refuses to run. With it on, every Sandbox mounts the
  VSCode server volume at runtime.
- The VSCode server volume is a named Docker volume that never shares host
  `~/.vscode-server`.

## Considered options

- **An explicit `--join` flag (rejected).** It keeps today's behaviour
  but makes the common case harder to type, and differs from `attach`.
- **Accept a server reinstall in joined Sandboxes (rejected).** It is the
  smallest change, but it costs a server download each time a Sandbox
  starts.
- **Bind-mount host `~/.vscode-server` into every Sandbox (rejected).** It
  persists just as well, but it exposes the host's server to every Harness.

## Consequences

- Making VSCode opt-in breaks `aishell vscode` for users who have not run
  `aishell setup --with-vscode`. The release notes call it out as a
  breaking change.
- Server data left in host `~/.vscode-server` by earlier versions is no
  longer used. aishell does not delete it.
- `aishell volumes` lists the VSCode server volume, and `prune` removes it
  only when VSCode is disabled.
