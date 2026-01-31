---
plan: 005
type: quick
subsystem: cli
tags: [babashka, tmux, attach, shell]
requires: []
provides:
  - attach-shell-function
  - shell-flag-on-attach-command
affects: []
tech-stack:
  added: []
  patterns: []
key-files:
  created: []
  modified:
    - src/aishell/attach.clj
    - src/aishell/cli.clj
    - docs/HARNESSES.md
decisions: []
metrics:
  duration: ~3 minutes
  completed: 2026-01-31
---

# Quick Task 005: Add --shell Flag to aishell attach Command

**One-liner:** Plain bash shell access via `aishell attach --name foo --shell` using tmux session named 'shell'

## What Was Built

Added a `--shell` flag to the `aishell attach` command that creates or attaches to a dedicated bash shell session inside a running container.

### Key Features

1. **attach-shell function**: New function in `attach.clj` that uses `tmux new-session -A -s shell /bin/bash` to ensure a bash session exists
2. **Mutual exclusion**: `--shell` and `--session` flags are mutually exclusive with clear error messaging
3. **Help documentation**: Updated CLI help text with `--shell` option and example usage
4. **User documentation**: Added "Shell Access" section to HARNESSES.md

### Technical Implementation

**attach-shell function** (`src/aishell/attach.clj`):
- Takes single `name` argument (container name)
- Performs same pre-flight validations as `attach-to-session` (TTY, container state)
- Skips session existence check (not needed with `tmux new-session -A`)
- Uses `tmux new-session -A -s shell /bin/bash` which creates session if missing or attaches if exists

**CLI integration** (`src/aishell/cli.clj`):
- Added `:shell {:coerce :boolean}` to attach command spec
- Mutual exclusion check before name validation
- Calls `attach/attach-shell` when `--shell` is provided
- Calls `attach/attach-to-session` otherwise (existing behavior)

## Deviations from Plan

None - plan executed exactly as written.

## Tasks Completed

| Task | Name | Commit | Files |
|------|------|--------|-------|
| 1 | Add attach-shell function to attach.clj | 4501e3c | src/aishell/attach.clj |
| 2 | Wire --shell flag into CLI with mutual exclusion | 8e922b3 | src/aishell/cli.clj, docs/HARNESSES.md |

## Verification Results

All verification checks passed:

1. ✓ `bb -e "(require '[aishell.attach])"` loads without error
2. ✓ `aishell attach --help` shows --shell in options and examples
3. ✓ `aishell attach --name test --shell --session foo` prints mutual exclusion error
4. ✓ Command structure correct (verified via dispatch function)

## Success Criteria Met

- [x] attach-shell function in attach.clj uses `tmux new-session -A -s shell /bin/bash`
- [x] --shell and --session mutual exclusion enforced with clear error message
- [x] Help text documents --shell flag with example
- [x] HARNESSES.md documents shell access pattern

## Usage Examples

```bash
# Open a bash shell in the 'claude' container
aishell attach --name claude --shell

# This creates (or reattaches to) a tmux session named 'shell' running /bin/bash
# Unlike --session, --shell always ensures a bash session exists
```

## Next Phase Readiness

**Status:** Complete, no blockers

This quick task is standalone and doesn't affect any planned phases. It provides additional convenience for users who want quick shell access to containers without specifying a tmux session name.

## Decisions Made

None - straightforward implementation following existing patterns in `attach-to-session`.

## Files Modified

1. **src/aishell/attach.clj**
   - Added `attach-shell` public function
   - Uses `tmux new-session -A` for automatic create-or-attach

2. **src/aishell/cli.clj**
   - Added `:shell` to attach command spec
   - Implemented mutual exclusion logic
   - Updated help text and examples

3. **docs/HARNESSES.md**
   - Added "Shell Access" subsection under "Detached Mode & tmux"
   - Documented `--shell` flag usage and behavior

## Technical Notes

- The `-A` flag to `tmux new-session` is key: it makes tmux attach if the session exists, or create it if it doesn't
- This avoids the need for `validate-session-exists!` check that `attach-to-session` uses
- Session is always named 'shell' (hardcoded) to provide predictable behavior
- Uses `/bin/bash` as the shell command (standard across all containers)
