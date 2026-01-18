---
phase: 03-harness-integration
plan: 02
subsystem: infra
tags: [docker, aishell, cli, subcommands, config-mounts, api-keys, environment-variables]

# Dependency graph
requires:
  - phase: 03-01
    provides: Conditional harness installation in Dockerfile
provides:
  - Subcommand parsing (claude, opencode, update)
  - Build flags (--with-claude, --with-opencode)
  - Config directory mounting (Claude, OpenCode)
  - API key environment passthrough
  - DISABLE_AUTOUPDATER=1 for ephemeral containers
affects: [03-03, end-users, documentation]

# Tech tracking
tech-stack:
  added: []
  patterns: [subcommand-dispatch, conditional-mounting, env-passthrough]

key-files:
  created: []
  modified: [aishell, entrypoint.sh]

key-decisions:
  - "Only mount config dirs that exist on host (avoid Docker bind mount errors)"
  - "Only pass env vars that are set (avoid empty override)"
  - "DISABLE_AUTOUPDATER=1 always passed for ephemeral containers"
  - "Build flags trigger rebuild when passed (--with-claude, --with-opencode)"

patterns-established:
  - "Config mounting: Check existence before adding -v flag"
  - "API env passthrough: Array of known vars, only pass if non-empty"
  - "Subcommand dispatch: parse_args captures command, main() switches on it"

# Metrics
duration: 5min
completed: 2026-01-17
---

# Phase 3 Plan 2: Harness SDK Configuration Summary

**Subcommand-based harness invocation with config mounting and API key passthrough in aishell**

## Performance

- **Duration:** 5 min
- **Started:** 2026-01-17T20:10:00Z
- **Completed:** 2026-01-17T20:15:00Z
- **Tasks:** 3
- **Files modified:** 2

## Accomplishments
- Added `aishell claude` and `aishell opencode` subcommands for harness invocation
- Implemented `aishell update` for rebuilding image with latest harness versions
- Added build flags `--with-claude` and `--with-opencode` for conditional installation
- Config directories automatically mounted when they exist on host
- API keys (ANTHROPIC_API_KEY, OPENAI_API_KEY, etc.) passed through when set
- DISABLE_AUTOUPDATER=1 always set for ephemeral container use

## Task Commits

Each task was committed atomically:

1. **Task 1: Add config mounting and environment passthrough** - `d477092` (feat)
2. **Task 2: Add subcommand parsing and build arg support** - `be7a351` (feat)
3. **Task 3: Implement subcommand dispatch in main()** - `2126aa0` (feat)

## Files Created/Modified
- `aishell` - Added build_config_mounts(), build_api_env(), subcommand parsing, dispatch logic, do_update()
- `entrypoint.sh` - Added PATH extension for harness binaries

## Decisions Made
- **Conditional mounting:** Only add -v flags for directories/files that exist on host (per RESEARCH.md Pitfall 2)
- **Conditional env passthrough:** Only pass env vars that are set to avoid empty override (per RESEARCH.md Pitfall 4)
- **Build flag behavior:** Passing --with-* flags triggers image rebuild even if image exists

## Deviations from Plan

None - plan executed exactly as written.

## Issues Encountered
None.

## User Setup Required
None - no external service configuration required.

## Next Phase Readiness
- aishell now supports full harness invocation via subcommands
- Config mounts and API env vars automatically handled
- Ready for Plan 03-03: Full integration testing
- Requirements HARNESS-01 through HARNESS-05 satisfied

---
*Phase: 03-harness-integration*
*Completed: 2026-01-17*
