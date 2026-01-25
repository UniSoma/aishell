---
phase: 25-cli-runtime
plan: 01
subsystem: infra
tags: [docker, codex, gemini, gcp, vertex-ai]

# Dependency graph
requires:
  - phase: 24-dockerfile---build-infrastructure
    provides: Dockerfile with Codex and Gemini install support
provides:
  - Config directory mounts for ~/.codex and ~/.gemini
  - API key passthrough for CODEX_API_KEY, GEMINI_API_KEY, GOOGLE_API_KEY
  - GCP credentials file mount for Vertex AI authentication
affects: [25-02-cli-dispatch, gemini-vertex-ai-usage]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - GCP credentials file mounting with read-only access

key-files:
  created: []
  modified:
    - src/aishell/docker/run.clj

key-decisions:
  - "Mount GCP credentials file read-only for security"
  - "API key order: group Codex/Gemini keys together after OPENAI_API_KEY"

patterns-established:
  - "Credentials file mount pattern: when env var references a file, mount the file separately"

# Metrics
duration: 2min
completed: 2026-01-25
---

# Phase 25 Plan 01: Docker Runtime Support Summary

**Config mounts and environment variable passthrough for Codex and Gemini harnesses in Docker containers**

## Performance

- **Duration:** 2 min
- **Started:** 2026-01-25T02:11:52Z
- **Completed:** 2026-01-25T02:14:00Z
- **Tasks:** 3
- **Files modified:** 1

## Accomplishments

- Added ~/.codex and ~/.gemini config directory mounts
- Added CODEX_API_KEY, GEMINI_API_KEY, GOOGLE_API_KEY, GOOGLE_CLOUD_LOCATION to env passthrough
- Added GCP credentials file mount for Vertex AI authentication

## Task Commits

Each task was committed atomically:

1. **Task 1: Add Codex and Gemini config directory mounts** - `edd90d9` (feat)
2. **Task 2: Add Codex and Gemini API key environment variables** - `503790b` (feat)
3. **Task 3: Add GCP credentials file mount** - `7403e30` (feat)

## Files Created/Modified

- `src/aishell/docker/run.clj` - Added config mounts, API key vars, and GCP credentials mount function

## Decisions Made

- Mount GCP credentials file read-only (:ro flag) for security - container should not modify credentials
- Grouped Codex/Gemini API keys together in api-key-vars after OPENAI_API_KEY for logical ordering
- Used public function for build-gcp-credentials-mount to enable testing

## Deviations from Plan

None - plan executed exactly as written.

## Issues Encountered

None.

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness

- Docker runtime support complete for Codex and Gemini
- Ready for 25-02 (CLI dispatch and harness verification)

---
*Phase: 25-cli-runtime*
*Completed: 2026-01-25*
