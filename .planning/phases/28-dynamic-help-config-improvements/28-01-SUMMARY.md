---
phase: 28-dynamic-help-config-improvements
plan: 01
subsystem: config
tags: [config, docker, gitleaks, pre-start, yaml]
requires: [27-04]
provides:
  - pre_start list format support
  - --without-gitleaks build flag
  - :with-gitleaks state tracking
affects: [28-02]
tech-stack:
  added: []
  patterns:
    - yaml-normalization
    - conditional-docker-layers
decisions:
  - id: pre-start-list-normalization
    choice: "Normalize at YAML load time in config.clj"
    rationale: "Single point of normalization ensures consistent behavior across all config usage"
  - id: gitleaks-opt-out-flag
    choice: "Use --without-gitleaks (opt-out) instead of --with-gitleaks (opt-in)"
    rationale: "Maintains backwards compatibility - default behavior includes Gitleaks"
  - id: with-gitleaks-positive-tracking
    choice: "Invert flag to :with-gitleaks for positive state tracking"
    rationale: "State reflects what is installed, not what is excluded"
key-files:
  created: []
  modified:
    - src/aishell/config.clj
    - src/aishell/cli.clj
    - src/aishell/state.clj
    - src/aishell/docker/build.clj
    - src/aishell/docker/templates.clj
metrics:
  duration: 3min
  completed: 2026-01-25
---

# Phase 28 Plan 01: Core Infrastructure Summary

**One-liner:** Pre-start list format normalization and conditional Gitleaks installation with state tracking

## What Was Built

Implemented two key infrastructure improvements:

1. **Pre-start list format support** - Users can now define `pre_start` as YAML list that joins with `&&` separator
2. **Conditional Gitleaks installation** - Users can skip Gitleaks with `--without-gitleaks` flag to reduce image size

## Implementation Details

### Pre-start List Format

**normalize-pre-start function** (config.clj):
- Accepts string, list, or nil
- String format passes through unchanged (backwards compatible)
- List format filters empty items and joins with ` && ` separator
- Returns string or nil
- Called automatically by load-yaml-config after YAML parsing

**Example transformations:**
```yaml
# Before (v2.4.0 - string only)
pre_start: "echo 'Step 1' && echo 'Step 2' && echo 'Step 3'"

# After (v2.5.0 - list supported)
pre_start:
  - "echo 'Step 1'"
  - "echo 'Step 2'"
  - ""              # empty items filtered
  - "echo 'Step 3'"
# Result: "echo 'Step 1' && echo 'Step 2' && echo 'Step 3'"
```

### Conditional Gitleaks Installation

**CLI changes** (cli.clj):
- Added `:without-gitleaks` flag to build-spec with boolean coercion
- Parse flag and invert to `:with-gitleaks` for positive tracking
- Pass `:with-gitleaks` to build-base-image and write to state.edn
- Updated print-build-help to show `--without-gitleaks` example

**Docker build** (build.clj):
- Updated build-docker-args to accept `:with-gitleaks` parameter
- Always pass `WITH_GITLEAKS` build arg (true or false) for cache consistency
- Updated version-changed? to trigger rebuild when :with-gitleaks changes

**Dockerfile** (templates.clj):
- Added `ARG WITH_GITLEAKS=true` (default true for backwards compat)
- Wrapped Gitleaks installation RUN command in `if [ "$WITH_GITLEAKS" = "true" ]` conditional
- Conditional RUN produces empty layer when skipped (minimal overhead)

**State tracking** (state.clj):
- Added `:with-gitleaks` field to state schema documentation
- Default true for backwards compatibility with existing state files

## Verification Results

**Pre-start list format:**
- List format correctly joins with `&&` separator
- Empty list items properly filtered
- String format unchanged (backwards compatible)

**Gitleaks flag:**
- `aishell build --help` shows `--without-gitleaks` option
- build-spec includes `:without-gitleaks` key
- state schema documents `:with-gitleaks` field

## Technical Decisions

### Decision: Normalize at YAML load time

**Context:** Pre-start list normalization could happen at YAML load, config merge, or runtime.

**Options considered:**
- Load time (chosen): Single normalization point in load-yaml-config
- Merge time: Normalize during config merging
- Runtime: Normalize when passing to Docker

**Choice:** Load time normalization in config.clj

**Rationale:**
- Single point of normalization ensures consistent behavior
- All config consumers see normalized format
- Simplifies testing and debugging

### Decision: Opt-out flag naming

**Context:** Gitleaks is currently installed by default. Need to allow users to skip it.

**Options considered:**
- `--with-gitleaks` (opt-in): Would break backwards compatibility
- `--without-gitleaks` (opt-out, chosen): Maintains current default behavior
- `--skip-gitleaks`: Less clear semantics

**Choice:** `--without-gitleaks` (opt-out)

**Rationale:**
- Backwards compatible - existing builds continue to include Gitleaks
- Clear semantics - "without" directly indicates exclusion
- Matches pattern of other CLI tools

### Decision: Positive state tracking

**Context:** CLI flag is negative (`--without-gitleaks`) but state should track what exists.

**Options considered:**
- Store `:without-gitleaks` as-is
- Invert to `:with-gitleaks` (chosen)

**Choice:** Invert flag to `:with-gitleaks` for state storage

**Rationale:**
- State reflects what is installed, not what is excluded
- Consistent with other boolean state fields (`:with-claude`, `:with-opencode`)
- Easier to reason about when reading state.edn

## Deviations from Plan

None - plan executed exactly as written.

## Files Modified

| File | Lines Changed | Purpose |
|------|---------------|---------|
| src/aishell/config.clj | +15 | Add normalize-pre-start function and integrate with load-yaml-config |
| src/aishell/cli.clj | +4 | Add --without-gitleaks flag, parse and pass to build |
| src/aishell/state.clj | +1 | Update state schema docstring |
| src/aishell/docker/build.clj | +5 | Pass WITH_GITLEAKS build arg, check version changes |
| src/aishell/docker/templates.clj | +8, -7 | Wrap Gitleaks installation in conditional |

## Commits

| Hash | Message |
|------|---------|
| 85f2b4f | feat(28-01): implement pre_start list format normalization |
| d5106ba | feat(28-01): add --without-gitleaks flag to CLI and state |
| 7958a06 | feat(28-01): implement conditional Gitleaks installation in Docker build |

## Next Phase Readiness

**Ready for:** Phase 28 Plan 02 (Dynamic help based on installed harnesses)

**Blockers:** None

**Notes:**
- State tracking infrastructure now in place for dynamic help to detect installed harnesses
- Pre-start list format enables cleaner multi-command setup in upcoming plans
- Gitleaks can now be excluded for minimal image size in CI/test environments
