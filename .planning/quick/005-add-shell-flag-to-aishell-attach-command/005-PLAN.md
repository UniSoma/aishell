---
plan: 005
type: execute
wave: 1
depends_on: []
files_modified:
  - src/aishell/attach.clj
  - src/aishell/cli.clj
  - docs/HARNESSES.md
autonomous: true
must_haves:
  truths:
    - "aishell attach --name foo --shell creates/attaches to a tmux session named 'shell' running /bin/bash"
    - "aishell attach --name foo --shell --session bar prints mutual exclusion error"
    - "aishell attach --help shows --shell flag documentation"
  artifacts:
    - path: "src/aishell/attach.clj"
      provides: "attach-shell function"
    - path: "src/aishell/cli.clj"
      provides: "--shell flag parsing and mutual exclusion with --session"
  key_links:
    - from: "src/aishell/cli.clj"
      to: "src/aishell/attach.clj"
      via: "attach/attach-shell call"
      pattern: "attach/attach-shell"
---

<objective>
Add --shell flag to the `aishell attach` command that creates or attaches to a tmux session named "shell" running /bin/bash inside the target container. --shell and --session are mutually exclusive.

Purpose: Let users get a plain bash shell inside a container without interfering with existing tmux sessions.
Output: Working --shell flag on attach command with docs.
</objective>

<context>
@src/aishell/attach.clj
@src/aishell/cli.clj
@docs/HARNESSES.md
</context>

<tasks>

<task type="auto">
  <name>Task 1: Add attach-shell function to attach.clj</name>
  <files>src/aishell/attach.clj</files>
  <action>
Add a new public function `attach-shell` in src/aishell/attach.clj that takes a single `name` argument.

The function should:
1. Resolve `container-name` from project-dir and name (same as attach-to-session)
2. Run `validate-tty!` and `validate-container-state!` (same as attach-to-session)
3. Do NOT call `validate-session-exists!` -- the tmux session may not exist yet; `new-session -A` handles both create and attach
4. Resolve TERM via `resolve-term`
5. Use `p/exec` to run: `docker exec -it -u developer -e TERM=<term> -e LANG=C.UTF-8 -e LC_ALL=C.UTF-8 <container-name> tmux new-session -A -s shell /bin/bash`

The key difference from attach-to-session: uses `tmux new-session -A -s shell /bin/bash` instead of `tmux attach-session -t <session>`. The `-A` flag makes tmux attach if the session exists or create it if it doesn't.
  </action>
  <verify>Load the namespace in a REPL or confirm no syntax errors: `cd /home/jonasrodrigues/projects/harness && bb -e "(require '[aishell.attach])" 2>&1`</verify>
  <done>attach-shell function exists in attach.clj, accepts name arg, execs docker with tmux new-session -A -s shell /bin/bash</done>
</task>

<task type="auto">
  <name>Task 2: Wire --shell flag into CLI with mutual exclusion</name>
  <files>src/aishell/cli.clj, docs/HARNESSES.md</files>
  <action>
In src/aishell/cli.clj, modify the "attach" command handling (lines ~332-363):

1. Add `--shell` to the cli/parse-opts spec: `{:spec {:name {} :session {} :shell {:coerce :boolean}}}`. The :shell opt is a boolean flag (no value argument).

2. Add mutual exclusion check BEFORE the existing name check. After `opts` is parsed, check:
   - If both `:shell` and `:session` are truthy, print error: `"--shell and --session are mutually exclusive.\n\n--shell creates a bash session named 'shell'.\n--session attaches to a specific existing tmux session."`
   - If `:shell` is truthy (and no `:session`), call `(attach/attach-shell (:name opts))`
   - Otherwise, existing behavior: `(attach/attach-to-session (:name opts) (or (:session opts) "main"))`

3. Update the help text in the attach help block (lines ~336-354):
   - Add to Options: `"  --shell              Open a bash shell (creates tmux session 'shell')"`
   - Add example: `"  aishell attach --name claude --shell"` with description `"Open a bash shell in the 'claude' container"`
   - Add note: `"  --shell and --session are mutually exclusive."`

4. In docs/HARNESSES.md, in the "Detached Mode & tmux" section (around line 470-506), add a brief subsection or example showing `--shell` usage:
   ```
   ### Shell Access

   Open a plain bash shell inside a running container:

   ```bash
   aishell attach --name claude --shell
   ```

   This creates (or reattaches to) a tmux session named `shell` running `/bin/bash`.
   Unlike `--session`, which attaches to an existing tmux session, `--shell` always
   ensures a bash session exists.
   ```
  </action>
  <verify>
1. Syntax check: `cd /home/jonasrodrigues/projects/harness && bb -e "(require '[aishell.cli])" 2>&1`
2. Help output: `cd /home/jonasrodrigues/projects/harness && bb -m aishell.cli attach --help 2>&1 | grep -q shell && echo "OK: --shell in help"`
3. Mutual exclusion: `cd /home/jonasrodrigues/projects/harness && bb -m aishell.cli attach --name test --shell --session foo 2>&1 | grep -q "mutually exclusive" && echo "OK: mutual exclusion works"`
  </verify>
  <done>--shell flag parses correctly, mutual exclusion with --session produces clear error, help text documents --shell, HARNESSES.md updated</done>
</task>

</tasks>

<verification>
1. `bb -e "(require '[aishell.attach])"` loads without error
2. `bb -m aishell.cli attach --help` shows --shell in options and examples
3. `bb -m aishell.cli attach --name test --shell --session foo` prints mutual exclusion error
4. `bb -m aishell.cli attach --name test --shell` attempts docker exec with `tmux new-session -A -s shell /bin/bash` (will fail if no container, but command structure is correct)
</verification>

<success_criteria>
- attach-shell function in attach.clj uses `tmux new-session -A -s shell /bin/bash`
- --shell and --session mutual exclusion enforced with clear error message
- Help text documents --shell flag with example
- HARNESSES.md documents shell access pattern
</success_criteria>

<output>
After completion, create `.planning/quick/005-add-shell-flag-to-aishell-attach-command/005-SUMMARY.md`
</output>
