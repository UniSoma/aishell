---
status: diagnosed
phase: 16-run-commands
source: [16-01-SUMMARY.md, 16-02-SUMMARY.md, 16-03-SUMMARY.md, 16-04-SUMMARY.md]
started: 2026-01-21T00:00:00Z
updated: 2026-01-21T00:02:00Z
---

## Current Test

[testing complete]

## Tests

### 1. Enter shell with no arguments
expected: Running `./aishell.clj` with no arguments enters an interactive shell inside the container. You see a bash prompt and can run commands like `ls`, `pwd`, etc.
result: pass

### 2. Project mounted at same path
expected: Inside the container, `pwd` shows the same path as on the host (e.g., /home/user/project). Your project files are visible with `ls`.
result: pass

### 3. Git identity available
expected: Inside the container, running `git config user.name` and `git config user.email` shows your host git identity (same values as outside the container).
result: pass

### 4. Container is ephemeral
expected: Exit the shell (type `exit`), then run `./aishell.clj` again. Any files created in non-mounted directories (like `/tmp/testfile`) are gone. Only files in your project directory persist.
result: pass

### 5. Run Claude Code
expected: Running `./aishell.clj claude` starts Claude Code inside the container. Claude Code launches and shows its interface (or error if no ANTHROPIC_API_KEY).
result: pass

### 6. Run OpenCode
expected: Running `./aishell.clj opencode` starts OpenCode inside the container. OpenCode's interface appears (or error if not configured).
result: pass

### 7. Config env (array format)
expected: With `.aishell/config.yaml` containing `env: ["FOO=bar"]`, running `echo $FOO` inside container shows `bar`.
result: pass

### 8. Config env (map format)
expected: With `.aishell/config.yaml` containing `env: {FOO: bar}`, running `echo $FOO` inside container shows `bar`.
result: pass

### 9. Pass-through args for claude
expected: Running `./aishell.clj claude --help` passes `--help` to Claude Code inside the container, showing Claude's help output (not an "Unknown option" error).
result: issue
reported: "./aishell.clj claude --help shows aishell.clj help and ./aishell.clj claude --version enters claude code inside the container, instead of showing its version"
severity: major

### 10. Pass-through args for opencode
expected: Running `./aishell.clj opencode --help` passes `--help` to OpenCode inside the container, showing OpenCode's help output.
result: issue
reported: "Same behaviour as claude: --help prints this tool help and --version does not work, opens opencode directly"
severity: major

## Summary

total: 10
passed: 8
issues: 2
pending: 0
skipped: 0

## Gaps

- truth: "Pass-through args for claude command are forwarded to Claude Code"
  status: failed
  reason: "User reported: ./aishell.clj claude --help shows aishell.clj help and ./aishell.clj claude --version enters claude code inside the container, instead of showing its version"
  severity: major
  test: 9
  root_cause: "babashka.cli dispatch parses --help before handle-run sees it (spec has :help), and :restrict false drops unknown options like --version instead of passing them to args"
  artifacts:
    - path: "src/aishell/cli.clj"
      issue: "Lines 172-173: claude/opencode dispatch entries use spec with :help and rely on :restrict false which drops unknown options"
  missing:
    - "Handle claude/opencode commands before cli/dispatch to pass all args verbatim"
  debug_session: ""

- truth: "Pass-through args for opencode command are forwarded to OpenCode"
  status: failed
  reason: "User reported: Same behaviour as claude: --help prints this tool help and --version does not work, opens opencode directly"
  severity: major
  test: 10
  root_cause: "Same as test 9 - babashka.cli dispatch parses/drops options before they reach the harness"
  artifacts:
    - path: "src/aishell/cli.clj"
      issue: "Same as test 9"
  missing:
    - "Same fix as test 9"
  debug_session: ""
