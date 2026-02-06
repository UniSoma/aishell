---
phase: 50
plan: 01
subsystem: attach-command
tags: [attach, docker-exec, bash, tmux-removal, cli]

requires:
  - 49-01-entrypoint-simplification
provides:
  - Simplified attach command (docker exec bash wrapper)
  - Positional argument syntax for attach
affects:
  - 51-detach-flag-removal

tech-stack:
  added: []
  patterns:
    - "Direct docker exec bash (no tmux)"
    - "Positional argument CLI pattern"

key-files:
  created: []
  modified:
    - src/aishell/attach.clj
    - src/aishell/cli.clj
    - src/aishell/docker/naming.clj
    - src/aishell/run.clj

decisions:
  - id: attach-positional-syntax
    what: "Use positional argument instead of --name flag"
    why: "Simpler UX: 'aishell attach claude' vs 'aishell attach --name claude'"
    impact: "Breaking change for existing users (v3.0.0 milestone)"
  - id: remove-session-shell-flags
    what: "Removed --session and --shell flags from attach"
    why: "No tmux sessions to manage, attach always opens bash"
    impact: "Users can no longer attach to specific tmux sessions or create shell sessions"

metrics:
  duration: "2 minutes"
  completed: "2026-02-06"
---

# Phase 50 Plan 01: Attach Command Rewrite Summary

**One-liner:** Direct docker exec bash attachment with positional argument syntax, replacing tmux session management.

## What Changed

Rewrote the attach command from tmux session management to a simple docker exec bash wrapper.

### Before (Phase 49)
- `aishell attach --name claude --session harness` - attach to tmux session
- `aishell attach --name claude --shell` - create bash tmux session
- Complex tmux session validation and management
- Support for multiple named tmux sessions

### After (Phase 50-01)
- `aishell attach claude` - open bash shell via docker exec
- Single entry point function: `attach-to-container`
- TTY and container state validation only
- No tmux session management

## Tasks Completed

### Task 1: Rewrite attach.clj
**Commit:** f00170f

**Changes:**
- Removed 5 tmux-specific functions:
  - `validate-tmux-enabled!` - no longer needed (no tmux)
  - `validate-session-exists!` - no longer needed (no sessions)
  - `ensure-bashrc!` - dead code after Phase 49
  - `attach-to-session` - replaced by `attach-to-container`
  - `attach-shell` - replaced by `attach-to-container`
- Removed `clojure.string` require (only used by deleted functions)
- Added single public function: `attach-to-container`
  - Validates TTY and container state
  - Resolves TERM environment variable
  - Uses `p/exec` to replace process with `docker exec -it ... /bin/bash`
- Kept 3 helper functions unchanged:
  - `resolve-term` - validates TERM inside container
  - `validate-tty!` - checks for interactive terminal
  - `validate-container-state!` - checks container exists and is running
- Updated namespace docstring from "tmux session" to "docker exec bash"

**Lines of code:** -102 insertions, +7 (net -95 lines)

### Task 2: Update CLI dispatch and cross-file references
**Commit:** 51d4f9a

**Changes:**

**src/aishell/cli.clj:**
- Replaced entire attach dispatch case (lines 508-553)
- Removed flag parsing logic (--name, --session, --shell)
- Simplified to positional argument: `(first rest-args)`
- Updated help text:
  - `aishell attach <name>` instead of `aishell attach --name <name>`
  - Removed --session and --shell options
  - Removed tmux-specific notes (Ctrl+B D detach, multiple users)
  - Added note about --detach requirement
- Updated PS command hint (line 456): `attach <name>` instead of `attach --name <name>`

**src/aishell/docker/naming.clj:**
- Updated ensure-name-available! error message (line 99)
- Changed from `aishell attach --name X` to `aishell attach X`

**src/aishell/run.clj:**
- Updated detach mode message (line 251)
- Changed from `aishell attach --name X` to `aishell attach X`

**Lines of code:** -33 insertions, +14 (net -19 lines)

## Deviations from Plan

None - plan executed exactly as written.

## Technical Details

### attach-to-container Implementation

```clojure
(defn attach-to-container
  "Attach to a running container by opening a bash shell."
  [name]
  (let [project-dir (System/getProperty "user.dir")
        container-name (naming/container-name project-dir name)]
    (validate-tty!)
    (validate-container-state! container-name name)
    (let [term (resolve-term container-name)
          colorterm (or (System/getenv "COLORTERM") "truecolor")]
      (p/exec "docker" "exec" "-it" "-u" "developer"
              "-e" (str "TERM=" term)
              "-e" (str "COLORTERM=" colorterm)
              "-e" "LANG=C.UTF-8"
              "-e" "LC_ALL=C.UTF-8"
              container-name
              "/bin/bash"))))
```

**Key behaviors:**
1. Validates interactive terminal (no attach from scripts/CI)
2. Validates container exists and is running
3. Resolves TERM value valid inside container (fallback to xterm-256color)
4. Uses `p/exec` to replace current process (gives bash full terminal control)
5. Runs as `developer` user (matches harness execution)
6. Sets LANG/LC_ALL for UTF-8 support

### Error Messages Updated

**Container not found:**
```
Container 'claude' not found.

Use 'aishell ps' to list containers.
To start: aishell claude --detach
```

**Container not running:**
```
Container 'claude' is not running.

To start: aishell claude --detach
Or use: docker start aishell-a1b2c3d4-claude
```

Note: `--detach` references remain (Phase 51 will remove them).

## Next Phase Readiness

**Phase 51 (Detach Flag Removal)** can proceed immediately.

**Blockers:** None

**Concerns:** None

**Dependencies satisfied:**
- Entrypoint simplified (Phase 49) ✓
- Attach simplified (this phase) ✓
- Ready for detach flag removal

## Verification Results

All verification checks passed:

1. ✓ No tmux references in attach.clj
2. ✓ No "attach --name" references in src/
3. ✓ No deleted function references (SCI analysis-time safe)
4. ✓ attach-to-container function definition exists
5. ✓ attach/attach-to-container called from cli.clj
6. ✓ `bb -cp src -e "(require 'aishell.cli)"` succeeds without errors

## User Impact (v3.0.0 Breaking Changes)

**Before (v2.10.0):**
```bash
aishell attach --name claude
aishell attach --name claude --shell
aishell attach --name experiment --session debug
```

**After (v3.0.0):**
```bash
aishell attach claude
aishell attach claude  # always opens bash, no --shell flag needed
# No support for named tmux sessions
```

**Migration path:**
- Replace `aishell attach --name X` with `aishell attach X`
- Remove `--shell` flag (default behavior is now bash)
- Remove `--session` flag (no tmux sessions exist)

## Self-Check: PASSED

All files created:
- (none - docs-only plan)

All commits exist:
- f00170f: refactor(50-01): replace tmux session management with docker exec bash
- 51d4f9a: feat(50-01): update attach dispatch to use positional argument
