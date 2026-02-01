# Project State

## Project Reference

See: .planning/PROJECT.md (updated 2026-01-31)

**Core value:** Run agentic AI harnesses in isolated, reproducible environments without polluting the host system.

**Current focus:** Phase 38 - Volume Cleanup & Documentation

## Current Position

Phase: 38 of 38 (Volume Cleanup & Documentation)
Plan: 3 of 3 complete in phase
Status: Phase complete
Last activity: 2026-02-01 - Completed 38-03-PLAN.md (Documentation & Changelog)

Progress: [██████████] 100% (14/14 plans complete)

**Shipped Milestones:**
- v1.0 MVP - Phases 1-8 (shipped 2026-01-18)
- v1.1 Runtime Config - Phases 9-10 (shipped 2026-01-19)
- v1.2 Hardening - Phases 11-12 (shipped 2026-01-19)
- v2.0 Babashka Rewrite - Phases 13-18 (shipped 2026-01-21)
- v2.3.0 Safe AI Context Protection - Phases 18.1-23 (shipped 2026-01-24)
- v2.4.0 Multi-Harness Support - Phases 24-27 (shipped 2026-01-25)
- v2.5.0 Optimization & Polish - Phases 28-29 (shipped 2026-01-26)
- v2.7.0 tmux Integration & Named Containers - Phases 30-34 (shipped 2026-01-31)
- v2.8.0 Foundation/Volume Architecture - Phases 35-38 (shipped 2026-02-01)

## Performance Metrics

**Velocity (v2.8.0):**
- Total plans completed: 14
- Average duration: 3 min
- Total execution time: 0.37 hours

**By Phase:**

| Phase | Plans | Total | Avg/Plan |
|-------|-------|-------|----------|
| 35 | 2/2 | 6 min | 3 min |
| 36 | 3/3 | 5 min | 2 min |
| 37 | 6/6 | 7 min | 1 min |
| 38 | 3/3 | 12 min | 4 min |

**Recent Trend:**
- Last plan: 6 min (38-03)
- Trend: Documentation plans ~4-6 min with comprehensive updates

**Cumulative (v1.0-v2.8.0):**
- Total plans: 94
- Completed plans: 94
- Milestones: 9 (all shipped)
- Days: 15 (2026-01-17 -> 2026-02-01)

*Updated after each plan completion*

## Accumulated Context

### Decisions

Decisions are logged in PROJECT.md Key Decisions table.

Recent decisions affecting v2.8.0 work:

**Architecture (from investigation and 35-01):**
- 2-tier architecture: Foundation image + harness volume (not 3 images)
- Volume-based injection over layer inversion (simpler for local dev workflow)
- Clean break from `aishell:base` to `aishell:foundation` (backward compat alias maintained)
- Per-project volumes keyed by harness combination hash
- Lazy volume population on first container run
- Gitleaks stays in foundation (security infrastructure, not harness)
- Foundation rebuilds only on Dockerfile template changes, not harness version changes

**Migration handling (from 35-02):**
- Case-insensitive regex with word boundary to detect legacy FROM aishell:base
- Exit with error (not warning) to prevent silent breakage on upgrade
- Validate before build attempt for clearer user experience

**Volume naming (from 36-01):**
- Hash only enabled harnesses with versions (exclude disabled harnesses)
- Normalize nil versions to 'latest' for consistency
- Use alphabetical sorting for order-independent hashing
- 12-character hex hash matches existing hash.clj pattern

**Volume population (from 36-02):**
- NPM package names: @anthropic-ai/claude-code, @codex-ai/codex, @google/gemini-cli
- OpenCode excluded from npm installation (Go binary, not npm package)
- Use NPM_CONFIG_PREFIX=/tools/npm for installation directory
- Set world-readable permissions (chmod -R a+rX) for non-root execution
- Populate via temporary Docker containers with --rm flag

**Volume runtime wiring (from 36-03):**
- Mount harness volume read-only at /tools for security
- Use HARNESS_VOLUME env var as signal (not PATH in -e flag)
- Directory existence check (-d /tools/npm/bin) for safe PATH activation
- PATH order: harness tools -> user local -> system (prepend /tools/npm/bin)
- NODE_PATH set to /tools/npm/lib/node_modules for module resolution

**State schema migration (from 37-01):**
- Additive schema migration: new fields default to nil, no migration code needed
- Mark :dockerfile-hash as deprecated in favor of :foundation-hash for clarity
- v2.8.0 schema adds :foundation-hash, :harness-volume-hash, :harness-volume-name
- EDN's flexible schema provides natural backward compatibility

**Lazy volume population (from 37-02):**
- ensure-harness-volume returns nil if no harnesses enabled (avoids unnecessary volumes)
- Treat missing volume label as stale (triggers repopulation for safety)
- Build command will handle state persistence (run.clj is read-only for state)
- v2.7.0 compatibility maintained via on-the-fly volume name computation

**Extension cache migration (from 37-03):**
- Extension cache invalidation now uses foundation-image-id-label (aishell.foundation.id)
- Migration via nil label detection: old extensions have no aishell.foundation.id, trigger rebuild
- Backward-compat alias base-image-id-label maintained in build.clj
- Function renamed: get-base-image-id → get-foundation-image-id

**Build integration wiring (from 37-04):**
- Volume population only happens when at least one harness is enabled (some check on harness flags)
- Volume is only populated if missing OR hash mismatch (lazy, not every build)
- Both :dockerfile-hash (deprecated) and :foundation-hash (new) are written for backward compatibility
- :harness-volume-name is stored so run.clj can use it without recomputing

**OpenCode binary installation (from 37-05):**
- OpenCode binary downloaded from anomalyco/opencode GitHub releases (not opencodeco)
- Binary installs to /tools/bin separate from npm packages in /tools/npm
- /tools/bin added to PATH via directory existence check (same pattern as npm)
- Tarball contains single binary named "opencode" (opencode-linux-x64.tar.gz)
- Pattern established: curl | tar -xz -C /tools/bin for Go-based harnesses

**Profile.d login shell environment (from 37-06):**
- Created /etc/profile.d/aishell.sh to fix tmux new-window environment loss (GAP-02)
- Login shells source /etc/profile → /etc/profile.d/*.sh → bash.aishell (for prompt/aliases)
- Uses dot-source (. /etc/bash.aishell) instead of source for POSIX compatibility
- Sets PATH (/tools/npm/bin, /tools/bin) and NODE_PATH for harness tools in login shells

**Update command redesign (from 38-01):**
- Update command default behavior: volume refresh only (no foundation rebuild)
- Foundation rebuild is opt-in via --force flag
- Volume repopulation uses unconditional delete + recreate (not check-if-stale)
- No harnesses enabled results in informational message, not error
- Update workflow: delete volume → create volume → populate volume
- State updates preserve harness config, only update build-time (unless --force adds foundation-hash)

**Key files for implementation:**
- src/aishell/docker/templates.clj - Dockerfile templates
- src/aishell/docker/build.clj - Build orchestration
- src/aishell/docker/extension.clj - Extension cache invalidation
- src/aishell/docker/run.clj - Docker run arguments, volume mounts
- src/aishell/run.clj - Run command orchestration
- src/aishell/state.clj - State persistence (EDN)

### Pending Todos

3 deferred todos:
- [Binary install, conditional Node.js](./todos/pending/2026-01-25-binary-install-claude-code.md) — abandoned (native binary larger than npm)
- [Audit security detection patterns](./todos/pending/2026-01-25-audit-security-detection-patterns.md) — deferred to future milestone
- [Make tmux opt-in with --with-tmux flag](./todos/pending/2026-01-31-make-tmux-opt-in-with-flag.md) — opt-in tmux via `aishell build --with-tmux`

### Blockers/Concerns

None yet.

## Session Continuity

Last session: 2026-02-01T21:08:44Z
Stopped at: Completed 38-03-PLAN.md (Documentation & Changelog)
Resume file: None

**Next step:** Phase 38 complete. Project v2.8.0 shipped. Ready for future planning.
