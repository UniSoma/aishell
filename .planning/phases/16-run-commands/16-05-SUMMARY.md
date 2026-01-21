---
phase: 16-run-commands
plan: 05
subsystem: cli
tags: [babashka.cli, pass-through-args, dispatch]
dependency-graph:
  requires: [16-03, 16-04]
  provides: [full-arg-pass-through-to-harnesses]
  affects: []
tech-stack:
  added: []
  patterns: [pre-dispatch-command-interception]
key-files:
  created: []
  modified: [src/aishell/cli.clj]
decisions:
  - Pre-dispatch interception for pass-through commands
metrics:
  duration: 1 min
  completed: 2026-01-21
---

# Phase 16 Plan 05: Fix pass-through args for harness commands

**One-liner:** Intercept claude/opencode commands before cli/dispatch to pass ALL args verbatim to harnesses.

## What Was Done

### Task 1-3: Modify dispatch for pass-through commands

Changed dispatch strategy:
1. Check for "claude" or "opencode" as first arg
2. If matched, call `run/run-container` directly with all remaining args
3. Otherwise, fall through to standard cli/dispatch

This bypasses babashka.cli's option parsing entirely for harness commands, ensuring:
- `--help` goes to the harness, not aishell
- `--version` goes to the harness, not dropped
- All other args pass through unmodified

**Code change:**
```clojure
(defn dispatch [args]
  (case (first args)
    "claude" (run/run-container "claude" (vec (rest args)))
    "opencode" (run/run-container "opencode" (vec (rest args)))
    (cli/dispatch dispatch-table args {:error-fn handle-error :restrict true})))
```

### Cleanup

Removed:
- `handle-run` function (23 lines) - no longer needed
- Dispatch table entries for claude/opencode - dead code

Net change: -20 lines, simpler code.

## Commits

| Hash | Message | Files |
|------|---------|-------|
| 7881c41 | fix(16-05): pass all args through to harness commands | src/aishell/cli.clj |

## Deviations from Plan

None - plan executed exactly as written.

## Decisions Made

| Decision | Context | Rationale |
|----------|---------|-----------|
| Pre-dispatch command interception | Handle pass-through before cli/dispatch | Only way to bypass babashka.cli's option parsing |

## Verification

- `./aishell.clj --help` - Shows aishell help (correct)
- `./aishell.clj build --help` - Shows build help (correct)
- `./aishell.clj claude --help` - Would pass `--help` to Claude (needs Docker)
- `./aishell.clj opencode --version` - Would pass `--version` to OpenCode (needs Docker)

Note: Full verification of pass-through requires Docker and built image. Code logic verified correct.

## Gaps Addressed

From 16-UAT.md:
- Gap 9: `aishell claude --help` shows Claude's help, not aishell's
- Gap 10: `aishell claude --version` passes to Claude, not dropped

## Next Phase Readiness

No blockers. Phase 16 gap closure complete.
