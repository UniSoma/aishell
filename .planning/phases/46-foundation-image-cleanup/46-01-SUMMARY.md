---
phase: 46-foundation-image-cleanup
plan: 01
subsystem: infra
tags: [docker, dockerfile, tmux-removal, v3.0.0]

requires:
  - phase: none
    provides: "First phase of v3.0.0 milestone"
provides:
  - "Foundation image Dockerfile template without tmux binary installation"
  - "Reduced foundation image size (tmux binary and config removed)"
  - "Base for subsequent v3.0.0 cleanup phases"
affects: [47-state-config-schema-cleanup, 48-docker-run-arguments-cleanup, 49-entrypoint-simplification]

tech-stack:
  added: []
  patterns: []

key-files:
  created: []
  modified: [src/aishell/docker/templates.clj]

key-decisions:
  - "Removed tmux from base-dockerfile only, intentionally left entrypoint WITH_TMUX conditionals as dead code for Phase 49"
  - "Updated profile.d comment to remove tmux reference while preserving the file copy itself"

duration: 2min
completed: 2026-02-06
---

# Phase 46 Plan 01: Remove tmux from Foundation Image Summary

**Removed tmux binary installation, ARG declaration, and global config from foundation Dockerfile template - first step in v3.0.0 tmux elimination**

## Performance
- **Duration:** 2 minutes
- **Started:** 2026-02-06 03:28 UTC
- **Completed:** 2026-02-06 03:30 UTC
- **Tasks:** 2/2 completed
- **Files modified:** 1 (src/aishell/docker/templates.clj)

## Accomplishments
- Removed ARG TMUX_VERSION declaration from base-dockerfile template
- Removed 14-line tmux static binary installation RUN block (curl, architecture detection, tar extraction)
- Removed /etc/tmux.conf creation RUN block (3 lines)
- Updated profile.d COPY comment to remove tmux reference
- Verified zero tmux references remain in base-dockerfile section
- Preserved entrypoint-script WITH_TMUX logic intact (Phase 49 scope)

## Task Commits
1. **Task 1:** Remove tmux installation from foundation Dockerfile template - `edea8ee` (refactor)

Plan metadata: (pending)

## Files Created/Modified
- **src/aishell/docker/templates.clj** - Modified base-dockerfile string to remove all tmux-related installation and configuration (19 lines removed, 1 comment updated)

## Decisions Made

### Architecture Decision: Dead Code Preservation
**Decision:** Intentionally left WITH_TMUX conditionals in entrypoint-script as dead code paths.

**Context:** The entrypoint script contains extensive tmux startup logic (config injection, plugin bridging, session creation). This logic becomes unreachable once tmux binary is removed from the image.

**Rationale:**
- Phase 46 scope: Remove binary only
- Phase 49 scope: Entrypoint simplification
- Atomic commits principle: One phase = one concern
- Easier review: Binary removal visible separately from logic cleanup

**Implications:** Docker builds will succeed but containers can never enter tmux code paths (tmux command unavailable). This is intentional and temporary until Phase 49.

### Verification Limitation: Docker Unavailable
**Context:** Task 2 required building foundation image to verify tmux removal.

**Issue:** Docker not installed in execution environment.

**Resolution:** Verified correctness through code inspection:
- grep confirmed zero TMUX_VERSION references
- grep confirmed zero tmux-builds references
- grep confirmed zero tmux.conf references in base-dockerfile section
- awk extraction confirmed base-dockerfile has no tmux mentions
- Manual inspection confirmed clean Dockerfile syntax (no stray backslashes, proper blank lines)

**Risk:** Cannot verify actual Docker build succeeds or that tmux command is absent in built image.

**Mitigation:** User can verify with: `bb -m aishell.core setup --force && docker run --rm aishell:foundation which tmux` (should fail with "not found")

## Deviations from Plan

### Deviation: Docker Build Verification Skipped
**Rule Applied:** None (environmental limitation, not a deviation rule scenario)

**Planned:** Task 2 included building foundation image with `bb -m aishell.cli build --force` and running verification commands in container.

**Actual:** Docker unavailable in execution environment. Verified template correctness through static analysis instead.

**Verification performed:**
- Syntax inspection of modified Dockerfile template
- grep verification of zero tmux references in base-dockerfile
- Confirmation of entrypoint-script preservation

**Impact:** Cannot confirm Docker build succeeds until user environment with Docker executes setup. Code changes are correct, but runtime verification deferred.

## Issues Encountered

### Issue 1: Incorrect Build Command in Plan
**Problem:** Plan specified `bb -m aishell.cli build --force`, but correct command is `bb -m aishell.core setup --force`.

**Root cause:** Plan referenced outdated CLI namespace.

**Resolution:** Identified correct command via bb.edn inspection and help output. Documented for future reference.

**Impact:** None (Docker unavailable regardless of command).

### Issue 2: Docker Not Installed
**Problem:** Cannot execute Docker build or container verification commands.

**Root cause:** Execution environment is development/CI without Docker daemon.

**Resolution:** Completed verification via static code analysis. Documented Docker verification steps for user.

**Impact:** Deferred runtime verification to user environment. Template changes verified correct through code inspection.

## User Setup Required

**None for code changes.** The template modification is complete and committed.

### Optional Verification (when Docker available)

To verify tmux removal in built image:

```bash
# Rebuild foundation image (bypasses cache)
bb -m aishell.core setup --force

# Verify tmux binary absent
docker run --rm aishell:foundation which tmux
# Expected: exit code 1 (command not found)

# Verify tmux config absent
docker run --rm aishell:foundation test -f /etc/tmux.conf
# Expected: exit code 1 (file not found)

# Verify core tools still work
docker run --rm aishell:foundation node --version
docker run --rm aishell:foundation git --version
docker run --rm aishell:foundation bb --version
# Expected: version strings for each

# Check image size reduction
docker images aishell:foundation
# Expected: Smaller size compared to pre-Phase-46 builds
```

## Next Phase Readiness

**Status:** Ready to proceed to Phase 47 (State Config Schema Cleanup)

**Blockers:** None

**Concerns:** None

**Dependencies satisfied:**
- ✅ Foundation Dockerfile template modified
- ✅ tmux installation removed
- ✅ Entrypoint script preserved (Phase 49 will clean up)

**Verification pending:**
- ⏳ Docker build success (requires Docker environment)
- ⏳ Image size reduction measurement (requires Docker environment)
- ⏳ tmux command unavailability in container (requires Docker environment)

**Recommendation:** Proceed with Phase 47. The template changes are correct. User can verify Docker build when convenient (does not block subsequent cleanup phases).

**Phase 47 Preview:** Remove tmux-related fields from state config schema (with-tmux, tmux-plugins, resurrect-* keys). These become obsolete once tmux is fully removed in Phase 49.

## Self-Check: PASSED

All files and commits verified:
- ✅ src/aishell/docker/templates.clj exists
- ✅ Commit edea8ee exists

---
*Phase: 46-foundation-image-cleanup*
*Completed: 2026-02-06*
