---
phase: 41
plan: 01
subsystem: docker-runtime
tags: [tmux, tpm, entrypoint, docker, conditional-startup]

requires:
  - phase-40: Volume-installed tmux plugins at /tools/tmux/plugins
  - phase-39: Read-only .tmux.conf mount pattern
  - entrypoint: Dynamic user creation and gosu execution

provides:
  - WITH_TMUX env var passed from state to container
  - Plugin path bridging via symlink
  - Config injection with TPM initialization
  - Conditional tmux/shell startup mode
  - Session name "harness" (changed from "main")

affects:
  - phase-42: tmux session will have working plugins
  - phase-43: UAT can verify tmux and shell modes work

tech-stack:
  added: []
  patterns:
    - Idempotent symlink creation with ln -sfn
    - Config injection via copy-to-writable + append
    - Environment variable-based conditional execution
    - Heredoc for minimal config generation

key-files:
  created: []
  modified:
    - src/aishell/docker/run.clj
    - src/aishell/docker/templates.clj

key-decisions:
  - decision: Pass WITH_TMUX as environment variable instead of mounting state.edn
    rationale: Simpler and less fragile than file mount; consistent with existing env var pattern (PRE_START, TERM, etc.)
    alternatives: [Mount state.edn read-only, Parse state.edn in bash]
  - decision: Change session name from "main" to "harness"
    rationale: Consistency with project naming and future multi-session scenarios
    impact: Users with tmux attach scripts may need to update session name
  - decision: Use ~/.tmux.conf.runtime for injected config
    rationale: More discoverable for debugging than /tmp location; container is ephemeral anyway
    alternatives: [/tmp/tmux.conf, ~/.tmux/config.runtime]
  - decision: Warning-only approach for missing TPM directory
    rationale: Consistent with Phase 40 validation philosophy; plugins are enhancement not requirement
    impact: Container starts even if plugin installation failed

duration: 82 seconds
completed: 2026-02-02
---

# Phase 41 Plan 01: TPM Initialization in Entrypoint Summary

**One-liner:** Conditional entrypoint with plugin bridging (symlink), config injection (TPM run), and tmux/shell mode selection via WITH_TMUX env var.

## Performance

- **Total duration:** 82 seconds (1.4 minutes)
- **Task 1:** 30 seconds (WITH_TMUX env var)
- **Task 2:** 45 seconds (entrypoint modifications)
- **Verification:** 7 seconds

## Accomplishments

Bridged the gap between volume-installed plugins (Phase 40) and runtime tmux session:

1. **WITH_TMUX environment variable** - Passed from state `:with-tmux` flag to container via docker run -e flag
2. **Plugin path bridging** - Symlink `/tools/tmux/plugins` to `~/.tmux/plugins` using idempotent `ln -sfn` pattern
3. **Config injection** - Copy read-only `.tmux.conf` to writable `.tmux.conf.runtime`, append TPM run command if not present
4. **Conditional startup** - Replaced hardcoded tmux exec with conditional block: tmux mode when WITH_TMUX=true, direct shell otherwise
5. **Session rename** - Changed from "main" to "harness" for project consistency
6. **Fallback config** - Generate minimal .tmux.conf.runtime if user has no .tmux.conf

## Task Commits

| Task | Commit | Description |
|------|--------|-------------|
| 1 | 1d9f56c | Pass WITH_TMUX env var to container |
| 2 | c1b3070 | Add TPM initialization and conditional startup to entrypoint |

## Files Created

None - modifications only.

## Files Modified

### src/aishell/docker/run.clj
- Added conditional WITH_TMUX=true env var to docker run args (line ~274)
- Guards on `(get state :with-tmux)` flag
- Placed after tmux config mount section for logical grouping

### src/aishell/docker/templates.clj
- **Section 1 (Plugin bridging):** Added after locale setup, symlinks /tools/tmux/plugins to ~/.tmux/plugins with idempotent ln -sfn
- **Section 2 (Config injection):** Copies .tmux.conf to .tmux.conf.runtime, appends TPM run line, or creates minimal config if no user config exists
- **Section 3 (Conditional startup):** Replaced hardcoded tmux exec (line 208) with conditional block:
  - tmux mode: `exec gosu ... tmux -f "$RUNTIME_TMUX_CONF" new-session -A -s harness ...`
  - shell mode: `exec gosu ... "$@"`
- Sets TMUX_PLUGIN_MANAGER_PATH env var for plugin discovery
- Includes warning if TPM directory missing (non-blocking)
- Hard fail if tmux binary missing when WITH_TMUX=true

## Decisions Made

### 1. Environment Variable vs State File Mount
**Decision:** Pass WITH_TMUX as environment variable from state to container.

**Rationale:**
- state.edn lives on host at ~/.aishell/state.edn, not in project directory
- Mounting it requires knowing host home path and adding volume mount
- Environment variable is simpler, matches existing pattern (PRE_START, TERM, COLORTERM all use env vars)
- Less fragile than grep-parsing EDN from mounted file

**Alternatives considered:**
- Mount state.edn read-only to container
- Parse state.edn in bash entrypoint script

**Impact:** Entrypoint relies on env var being set correctly by run.clj. Consistent with existing architecture.

### 2. Session Name Change
**Decision:** Change tmux session name from "main" to "harness".

**Rationale:**
- "harness" aligns with project naming (aishell is a harness runner)
- Clearer semantic meaning in multi-session scenarios
- Documented in Phase 41 CONTEXT.md as design decision

**Alternatives considered:**
- Keep "main" for backward compatibility
- Use dynamic session name from config

**Impact:** Users with hardcoded `tmux attach -t main` commands need to update to `tmux attach -t harness` or use `aishell attach` (which handles session name).

### 3. Runtime Config Location
**Decision:** Use `~/.tmux.conf.runtime` for injected config instead of /tmp location.

**Rationale:**
- Keeps all tmux config in one logical location (~/.tmux directory)
- More discoverable for debugging (users expect tmux config in home, not /tmp)
- Container is ephemeral so cleanup doesn't matter
- Explicit .runtime extension indicates it's generated

**Alternatives considered:**
- /tmp/tmux.conf (ephemeral, auto-cleaned)
- ~/.tmux/config.runtime (XDG-style naming)

**Impact:** Generated config file visible in home directory but clearly marked as runtime artifact.

### 4. Error Handling Philosophy
**Decision:** Warning-only for missing plugins, hard fail only for tmux binary missing when explicitly requested.

**Rationale:**
- Consistent with Phase 40 warning-only validation approach
- Plugins are nice-to-have enhancement, not critical for container operation
- User explicitly requested tmux (--with-tmux flag), so tmux failure should error
- Partial functionality (shell mode) better than blocking container startup

**Alternatives considered:**
- Hard fail if plugins missing
- Silent failure (no warnings)

**Impact:** Container starts successfully even if plugin installation failed in Phase 40. User sees warnings but can still use tmux.

## Deviations from Plan

None - plan executed exactly as written.

## Issues Encountered

None. Both tasks completed smoothly:
- Clojure syntax validated successfully
- Bash syntax validated successfully
- All verification checks passed

## Next Phase Readiness

**Phase 42 (Runtime Plugin Loading):** ✅ Ready
- WITH_TMUX env var passed to container
- Plugin symlink bridges volume to user's home
- TPM run command injected into config
- Entrypoint correctly starts tmux with runtime config

**Phase 43 (UAT):** ✅ Ready
- Shell mode available for testing (WITH_TMUX=false)
- tmux mode available for testing (WITH_TMUX=true)
- Session name "harness" documented

**Blockers:** None

**Concerns:** None

**Next steps:**
1. Phase 42: Verify tmux session actually starts and plugins load
2. Phase 43: UAT with both tmux and shell modes
3. Consider XDG path support (~/.config/tmux/tmux.conf) in future enhancement if users request it
