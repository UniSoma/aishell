---
status: diagnosed
phase: 16-run-commands
source: [16-01-SUMMARY.md, 16-02-SUMMARY.md, 16-03-SUMMARY.md]
started: 2026-01-20T12:00:00Z
updated: 2026-01-20T12:30:00Z
---

## Current Test

[testing complete]

## Tests

### 1. Shell Entry (Default Command)
expected: Running `./aishell.clj` with no arguments enters a bash shell inside the container. You see a container prompt.
result: pass

### 2. Claude Command
expected: Running `./aishell.clj claude` starts Claude Code inside the container. Claude's interface appears.
result: pass

### 3. OpenCode Command
expected: Running `./aishell.clj opencode` starts OpenCode inside the container. OpenCode's interface appears.
result: pass

### 4. Project Mount at Same Path
expected: Inside the container, the project is mounted at the same path as on the host (e.g., /home/user/project inside matches /home/user/project on host). Files are accessible.
result: pass

### 5. Git Identity Available
expected: Inside the container, running `git config user.name` and `git config user.email` shows your host git identity.
result: pass

### 6. Container Ephemeral
expected: After exiting the container, it's destroyed (no leftover container). Only mounted files persist changes.
result: pass

### 7. Per-Project Config (config.yaml)
expected: Creating `.aishell/config.yaml` with mounts, env, ports, docker_args, or pre_start applies those settings. Example: adding `env: [FOO=bar]` makes $FOO=bar available in container.
result: issue
reported: "./aishell.clj Error: Unexpected error: java.lang.Character cannot be cast to clojure.lang.Named"
severity: major

## Summary

total: 7
passed: 6
issues: 2
pending: 0
skipped: 0

## Gaps

- truth: "Per-project config.yaml with env settings applies those settings to container"
  status: failed
  reason: "User reported: ./aishell.clj Error: Unexpected error: java.lang.Character cannot be cast to clojure.lang.Named"
  severity: major
  test: 7
  root_cause: "build-env-args in docker/run.clj expects env as a map ({:FOO bar}) but user provided array format ([FOO=bar]). String destructuring as [k v] yields characters, not key/value pairs."
  artifacts:
    - path: "src/aishell/docker/run.clj"
      issue: "build-env-args lines 57-79 only handles map format"
  missing:
    - "Handle array format: parse 'KEY=value' strings"
    - "Support both formats for user convenience"
  debug_session: ""

- truth: "Arguments passed to claude/opencode commands are forwarded to the harness"
  status: failed
  reason: "User reported: ./aishell.clj claude --version gives Error: Unknown option: :version"
  severity: major
  test: 2 (additional finding)
  root_cause: "Global :restrict true in cli/dispatch rejects unknown options for all commands, including claude/opencode which should allow pass-through args"
  artifacts:
    - path: "src/aishell/cli.clj"
      issue: "Line 191-192: dispatch has global :restrict true"
  missing:
    - "Add :restrict false to claude/opencode dispatch entries"
  debug_session: ""
