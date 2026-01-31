---
phase: 33-attach-command
plan: 01
subsystem: cli
tags: [babashka, tmux, docker, attach, cli]

# Dependency graph
requires:
  - phase: 32-detached-mode
    provides: "Containers start with --detach flag in tmux sessions"
  - phase: 30-container-naming
    provides: "Container naming utilities (container-name, container-exists?, container-running?)"
provides:
  - "aishell attach command for reconnecting to detached containers"
  - "Pre-flight validations for TTY, container state, and session existence"
  - "User-friendly error messages with actionable guidance"
affects: [34-container-lifecycle]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "CLI command parsing with babashka.cli before dispatch"
    - "p/exec for terminal takeover (not p/shell)"
    - "Three-layer validation pattern: TTY → container state → session state"

key-files:
  created:
    - src/aishell/attach.clj
  modified:
    - src/aishell/cli.clj

key-decisions:
  - "Only extract --name flag for harness commands, not attach (attach parses its own --name)"
  - "Use p/exec instead of p/shell for final docker exec (terminal takeover)"
  - "Show help when --help flag provided, not when args are empty (consistent with other commands)"
  - "Default to 'main' session when --session not specified"

patterns-established:
  - "Pre-flight validation pattern: TTY → container state → session state"
  - "User-friendly error messages with actionable next steps"
  - "Command-specific --name parsing (not global extraction for all commands)"

# Metrics
duration: 7min
completed: 2026-01-31
---

# Phase 33 Plan 01: Attach Command Summary

**`aishell attach` command reconnects to detached containers' tmux sessions with TTY, container, and session validations**

## Performance

- **Duration:** 7 minutes
- **Started:** 2026-01-31T17:37:30Z
- **Completed:** 2026-01-31T17:44:39Z
- **Tasks:** 2
- **Files modified:** 2

## Accomplishments
- Implemented `aishell attach --name <name>` command for reconnecting to running containers
- Added three-layer validation: TTY check, container state check, session existence check
- User-friendly error messages with specific guidance for each failure case
- Help text with usage examples and detach instructions (Ctrl+B D)

## Task Commits

Each task was committed atomically:

1. **Task 1: Create attach.clj with pre-flight validations and docker exec** - `af8d03b` (feat)
2. **Task 2: Integrate attach into CLI dispatch and help** - `94bef1c` (feat)

## Files Created/Modified
- `src/aishell/attach.clj` - New namespace with attach-to-session function and three validation functions
- `src/aishell/cli.clj` - Added attach command dispatch, help text, and conditional --name extraction

## Decisions Made

**Only extract --name flag for harness commands**
- Rationale: attach command parses its own --name flag with different semantics than harness commands. Pre-extracting it would strip it before the attach handler could parse it.
- Implementation: Added `harness-commands` set check before --name extraction in dispatch function.
- Files: src/aishell/cli.clj

**Use p/exec instead of p/shell for terminal takeover**
- Rationale: p/exec replaces the current process, giving tmux full terminal control without additional shell layers. p/shell would create a subprocess and not transfer TTY properly.
- Implementation: Final docker exec call uses p/exec with direct arguments (no sh -c wrapping).
- Files: src/aishell/attach.clj

**Show help only when --help flag provided**
- Rationale: Showing help on empty args would be inconsistent with how other commands handle missing required flags. Error message is more actionable.
- Implementation: Help shown only when `-h` or `--help` in args, not when args empty.
- Files: src/aishell/cli.clj

## Deviations from Plan

None - plan executed exactly as written.

## Issues Encountered

**Initial testing showed --name flag being stripped**
- Problem: Pre-dispatch --name extraction was removing the flag before attach handler could parse it.
- Root cause: --name extraction applied to all commands, not just harness commands.
- Solution: Added conditional extraction based on `harness-commands` set membership.
- Impact: Required amending Task 2 commit to include the fix.

## Next Phase Readiness

Ready for Phase 34 (Container Lifecycle Commands).

The attach command completes the detached mode workflow:
1. Start: `aishell claude --detach --name myproject`
2. Reconnect: `aishell attach --name myproject`
3. Detach: Press Ctrl+B D

All validation layers working:
- TTY check prevents non-interactive usage
- Container state check with specific errors for not-found vs stopped
- Session validation with available session listing

---
*Phase: 33-attach-command*
*Completed: 2026-01-31*
