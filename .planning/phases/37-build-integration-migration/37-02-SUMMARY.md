---
phase: 37
plan: 02
subsystem: build-runtime-integration
tags: [harness-volume, lazy-population, run-orchestration]
requires:
  - 36-01 # Volume naming and hashing
  - 36-02 # Volume population
  - 36-03 # Volume runtime wiring
provides:
  - Lazy harness volume population on container run
  - Automatic stale volume detection and repopulation
  - v2.7.0 state backward compatibility
affects:
  - 37-03 # Volume creation during build (similar lazy pattern)
  - 37-04 # CLI state management (writes harness-volume-name)
tech-stack:
  added: []
  patterns:
    - lazy-initialization
    - pre-flight-checks
key-files:
  created: []
  modified:
    - src/aishell/run.clj
decisions:
  - id: D37-02-01
    summary: ensure-harness-volume returns nil if no harnesses enabled
    rationale: Avoids creating unnecessary volumes when user built image without harnesses
  - id: D37-02-02
    summary: Treat missing volume label as stale (trigger repopulation)
    rationale: Conservative approach ensures volumes always have expected metadata
  - id: D37-02-03
    summary: Build command will handle state updates (harness-volume-name persistence)
    rationale: Run command is read-only for state, build command owns state mutation
metrics:
  duration: 68s
  completed: 2026-02-01
---

# Phase 37 Plan 02: Lazy Volume Population Summary

**One-liner:** Lazy harness volume population on container run with automatic stale detection

## What Was Delivered

Added `ensure-harness-volume` function to run.clj that:
- Auto-populates harness volume if missing on first container run
- Auto-repopulates if volume hash doesn't match current configuration
- Handles v2.7.0 state files gracefully (computes volume name on-the-fly)
- Returns nil if no harnesses enabled (avoids unnecessary volumes)

Wired harness-volume-name through both run-container and run-exec flows for proper volume mounting.

## Commits

| Commit  | Type | Description                                    |
|---------|------|------------------------------------------------|
| 0e11493 | feat | Add lazy harness volume population to run.clj |

## Decisions Made

**D37-02-01: ensure-harness-volume returns nil if no harnesses enabled**
- Problem: Should we create volume even if no harnesses?
- Decision: Return nil, let volume.clj skip mounting
- Rationale: Avoids creating unnecessary Docker resources when user built image without any harnesses
- Impact: Cleaner docker volume ls output, no zombie volumes

**D37-02-02: Treat missing volume label as stale**
- Problem: What if volume exists but missing aishell.harness.hash label?
- Decision: Trigger repopulation
- Rationale: Conservative approach ensures volumes always have expected metadata and are properly tracked
- Impact: Slightly more aggressive repopulation, but guarantees correctness

**D37-02-03: Build command handles state persistence**
- Problem: Should ensure-harness-volume update state with harness-volume-name?
- Decision: No - run.clj is read-only for state, build (plan 37-03) will write it
- Rationale: Separation of concerns - run command executes, build command configures
- Impact: v2.7.0 compatibility maintained (on-the-fly computation works), plan 37-04 will add persistence

## Technical Details

### Implementation Pattern

```clojure
(defn- ensure-harness-volume [state]
  (when (some #(get state %) [:with-claude :with-opencode :with-codex :with-gemini])
    (let [expected-hash (vol/compute-harness-hash state)
          volume-name (or (:harness-volume-name state)
                          (vol/volume-name expected-hash))]
      (cond
        (not (vol/volume-exists? volume-name))
        (do (vol/create-volume ...) (vol/populate-volume ...))

        (not= (vol/get-volume-label ...) expected-hash)
        (vol/populate-volume volume-name state))
      volume-name)))
```

**Key characteristics:**
- Early return (nil) if no harnesses - no volume needed
- Falls back to on-the-fly computation for v2.7.0 state files
- Create + populate on first run
- Repopulate only if hash mismatch
- Returns volume name for mounting regardless of whether work was done

### Integration Points

**run-container flow:**
```
read state → ensure-harness-volume → build-docker-args → docker run
```

**run-exec flow:**
```
read state → ensure-harness-volume → build-docker-args-for-exec → docker run
```

Both flows call ensure-harness-volume before docker args construction, ensuring volume is ready before container starts.

## Validation

**Namespace loading:**
```bash
bb -e '(require (quote [aishell.run]))'
# No errors - volume.clj integration successful
```

**Function presence:**
```bash
grep -n ensure-harness-volume src/aishell/run.clj
# 42: function definition
# 118: run-container call
# 287: run-exec call
```

**Parameter wiring:**
```bash
grep -n :harness-volume-name src/aishell/run.clj
# Passed to both build-docker-args and build-docker-args-for-exec
```

## Deviations from Plan

None - plan executed exactly as written.

## Next Phase Readiness

**For plan 37-03 (Volume creation during build):**
- Ready: Similar ensure-harness-volume pattern can be used
- Pattern: Create + store harness-volume-name in state during build
- Consider: Build should ALWAYS create volume if harnesses enabled (not lazy - eager)

**For plan 37-04 (CLI state management):**
- Ready: run.clj expects :harness-volume-name in state
- Pattern: cli.clj will write harness-volume-name after build completes
- Compatibility: On-the-fly computation ensures v2.7.0 states still work

**Verification needed:**
- End-to-end test: build with harnesses → run → verify volume exists and is mounted
- Stale detection test: modify config → run → verify repopulation triggered
- v2.7.0 migration: old state file → run → verify on-the-fly computation works
