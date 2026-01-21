---
phase: quick-001
plan: 01
subsystem: security/validation
tags: [security, mounts, warnings, advisory]

dependency-graph:
  requires: [17-02]  # Dangerous docker_args warnings
  provides: [dangerous-mount-path-warnings]
  affects: []

tech-stack:
  added: []
  patterns: [advisory-warnings, pattern-matching]

key-files:
  created: []
  modified:
    - src/aishell/validation.clj
    - src/aishell/run.clj

decisions:
  - "Regex patterns for all dangerous paths"
  - "Extract source path before colon for source:dest format"
  - "Advisory only - does not block execution"

metrics:
  duration: 1.4 min
  completed: 2026-01-21
---

# Quick Task 001: Add Dangerous Mount Path Warnings Summary

**One-liner:** Advisory warnings for sensitive mount paths (/, ~/.aws, docker.sock, etc.) matching docker_args pattern

## Completed Tasks

| Task | Name | Commit | Duration |
|------|------|--------|----------|
| 1 | Add dangerous mount path detection to validation.clj | d00f76f | 1 min |
| 2 | Integrate mount warnings into run.clj | 9f9f883 | 0.4 min |

## Implementation Details

### Dangerous Mount Path Patterns

Added detection for 10 dangerous mount path categories:

1. **Root filesystem** (`/`) - Full host access
2. **System user database** (`/etc/passwd`)
3. **System password hashes** (`/etc/shadow`)
4. **Docker socket** (`docker.sock`) - Docker daemon control
5. **AWS credentials** (`~/.aws`)
6. **Azure credentials** (`~/.azure`)
7. **GPG keys** (`~/.gnupg`)
8. **Kubernetes credentials** (`~/.kube`)
9. **Password manager** (`~/.password-store`)
10. **SSH private keys** (`~/.ssh/id_*`)

### Key Functions Added

```clojure
;; Pattern definitions
(def dangerous-mount-paths [...])

;; Detection function - returns warnings or nil
(defn check-dangerous-mounts [mounts] ...)

;; Warning display - advisory, does not block
(defn warn-dangerous-mounts [mounts] ...)
```

### Integration Point

Warning called in `run.clj` immediately after `warn-dangerous-args`:

```clojure
;; Warn about dangerous mount paths (advisory warning)
_ (when-let [mounts (:mounts cfg)]
    (validation/warn-dangerous-mounts mounts))
```

## Verification Results

All success criteria verified:
- All 10 dangerous patterns correctly detected
- Source path extraction works for `source:dest` format
- Safe paths produce no warnings (nil)
- Warning format matches docker_args pattern
- Advisory only - execution continues

## Deviations from Plan

None - plan executed exactly as written.

## Files Modified

### src/aishell/validation.clj (+54 lines)
- Added `dangerous-mount-paths` def with 10 pattern/message pairs
- Added `check-dangerous-mounts` function
- Added `warn-dangerous-mounts` function

### src/aishell/run.clj (+4 lines)
- Added mount path warning call after docker_args warning
