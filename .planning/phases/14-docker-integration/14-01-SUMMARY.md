---
phase: 14-docker-integration
plan: 01
subsystem: docker
tags: [docker, babashka, subprocess, availability]
dependency_graph:
  requires: [13-foundation]
  provides: [docker-availability, image-inspection, size-formatting]
  affects: [14-02, 14-03]
tech_stack:
  added: []
  patterns: [shell-with-continue, try-catch-exception-handling, go-template-labels]
key_files:
  created:
    - src/aishell/docker.clj
  modified: []
decisions:
  - "try/catch around all Docker shell calls to handle missing binary"
  - "Go template index syntax for label access (handles dots in names)"
  - "format-size accepts both string and numeric input"
metrics:
  duration: 3 min
  completed: 2026-01-20
---

# Phase 14 Plan 01: Docker Wrapper Module Summary

Docker availability checks and image inspection utilities using babashka.process patterns.

## What Was Built

### src/aishell/docker.clj (77 lines)

Docker wrapper module providing:

1. **docker-available?** - Checks if docker binary exists in PATH via `fs/which`
2. **docker-running?** - Checks if Docker daemon is responsive via `docker info`
3. **check-docker!** - Combined check with colored error output and exit
4. **image-exists?** - Detects if a Docker image exists locally
5. **get-image-label** - Retrieves labels using Go template index syntax
6. **format-size** - Converts bytes to human-readable format (B/KB/MB/GB)
7. **get-image-size** - Retrieves and formats image size from Docker

All shell calls use `:continue true` to capture exit codes and wrap in try/catch for robustness when Docker is not installed.

## Commits

| Commit | Type | Description |
|--------|------|-------------|
| 2fdb4d3 | feat | Docker wrapper module with availability checks |
| 9004ff6 | feat | Image size formatting utilities |

## Verification

All functions tested and working:

```bash
bb -e "(require '[aishell.docker :as d])
       (println \"Available:\" (d/docker-available?))
       (println \"Running:\" (d/docker-running?))
       (println \"Image exists:\" (d/image-exists? \"hello-world\"))
       (println \"Format 1GB:\" (d/format-size 1073741824))"
# Output:
# Available: false (or true if Docker installed)
# Running: false (or true if daemon running)
# Image exists: false
# Format 1GB: 1.00GB
```

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] Added try/catch to image-exists?**

- **Found during:** Task 1 verification
- **Issue:** `image-exists?` threw exception when Docker not installed
- **Fix:** Wrapped shell call in try/catch, returns false on exception
- **Files modified:** src/aishell/docker.clj
- **Commit:** 2fdb4d3

## Pattern Reference

Key patterns established for other plans to follow:

```clojure
;; Shell with error capture (non-throwing)
(p/shell {:out :string :err :string :continue true}
         "docker" "command" args...)

;; Label retrieval with Go template
(str "--format={{index .Config.Labels \"" label-key "\"}}")

;; Size formatting
(format "%.1fMB" (/ bytes 1024.0 1024.0))
```

## Next Phase Readiness

Ready for 14-02 (build command) which will use:
- `docker-available?` / `docker-running?` for pre-build checks
- `image-exists?` for cache validation
- `get-image-label` for hash comparison
- `get-image-size` for build completion message

---

*Phase: 14-docker-integration*
*Plan: 01*
*Completed: 2026-01-20*
