---
phase: 06-final-enhancements
plan: 02
subsystem: cli-ux
tags: [ux, shell-prompt, permissions, docker, security]
dependency_graph:
  requires: [06-01]
  provides: [concise-shell-prompt, auto-skip-permissions]
  affects: []
tech_stack:
  added: []
  patterns: [PROMPT_DIRTRIM, permission-opt-out-pattern]
key_files:
  created: []
  modified: [aishell]
decisions:
  - "PROMPT_DIRTRIM=2 for showing last 2 path components in prompt"
  - "Default --dangerously-skip-permissions for Claude (container is sandbox)"
  - "AISHELL_SKIP_PERMISSIONS=false for opt-out"
metrics:
  duration: 2 min
  completed: 2026-01-18
---

# Phase 6 Plan 2: UX Improvements Summary

Concise shell prompt via PROMPT_DIRTRIM=2 and auto-skip permissions for Claude in sandboxed container.

## Performance

- **Duration:** 2 min
- **Started:** 2026-01-18T12:25:53Z
- **Completed:** 2026-01-18T12:27:02Z
- **Tasks:** 2
- **Files modified:** 1

## Accomplishments

- Container shell prompt now shows abbreviated paths (`.../parent/current` instead of full path)
- Claude Code runs with --dangerously-skip-permissions by default (container is the sandbox)
- Users can opt-out via AISHELL_SKIP_PERMISSIONS=false environment variable
- Help output now documents the AISHELL_SKIP_PERMISSIONS environment variable

## Task Commits

1. **Task 1: Add PROMPT_DIRTRIM to bashrc** - `63afd13` (feat)
2. **Task 2: Add --dangerously-skip-permissions for Claude** - `d243591` (feat)

## Files Modified

- `aishell` - Added PROMPT_DIRTRIM=2 to bashrc.aishell heredoc, added permission skip logic to claude dispatch, added Environment Variables section to usage()

## Implementation Details

### PROMPT_DIRTRIM in bashrc.aishell

```bash
# Limit directory depth in prompt (shows .../parent/current instead of full path)
export PROMPT_DIRTRIM=2
```

This Bash 4+ native feature abbreviates long paths in the \w prompt escape. With PROMPT_DIRTRIM=2, `/home/user/projects/harness/deep/nested` shows as `.../deep/nested`.

### Permission Skip in Claude Dispatch

```bash
claude)
    local claude_args=()
    # Default: skip permissions (container is the sandbox)
    # Opt-out: AISHELL_SKIP_PERMISSIONS=false
    if [[ "${AISHELL_SKIP_PERMISSIONS:-true}" != "false" ]]; then
        claude_args+=(--dangerously-skip-permissions)
    fi
    exec docker run "${docker_args[@]}" "$IMAGE_TO_RUN" claude "${claude_args[@]}" "${HARNESS_ARGS[@]}"
    ;;
```

Default behavior: `--dangerously-skip-permissions` is added automatically since the container itself provides isolation.

Opt-out: Set `AISHELL_SKIP_PERMISSIONS=false` to require permission prompts.

## Decisions Made

1. **PROMPT_DIRTRIM=2:** Shows last 2 path components, balancing context with brevity
2. **Container-as-sandbox philosophy:** The Docker container IS the sandbox, so file operation permissions are redundant
3. **Opt-out via environment variable:** Users who want permission prompts can set AISHELL_SKIP_PERMISSIONS=false

## Deviations from Plan

None - plan executed exactly as written.

## Issues Encountered

None.

## Next Phase Readiness

Plan 06-02 complete. All Phase 6 enhancements are now implemented:
- Version pinning (06-01)
- UX improvements (06-02)

Remaining pending todos from STATE.md:
- `claude-statusline-host-container-parity` - Investigate Claude statusline difference between host and container (future investigation)

---
*Phase: 06-final-enhancements*
*Completed: 2026-01-18*
