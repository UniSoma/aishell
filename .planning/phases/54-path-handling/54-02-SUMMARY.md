---
phase: 54-path-handling
plan: 02
subsystem: docker-runtime
tags: [cross-platform, windows, docker, mounts, path-normalization]
dependency_graph:
  requires:
    - phase: 54-01
      provides: cross-platform-path-utilities
  provides:
    - cross-platform-docker-mount-construction
    - windows-drive-letter-aware-parsing
    - docker-path-normalization
  affects: [docker-execution, mount-validation, windows-support]
tech_stack:
  added: []
  patterns: [fs-unixify-for-docker, smart-colon-parsing, platform-aware-destinations]
key_files:
  created: []
  modified: [src/aishell/docker/run.clj, src/aishell/check.clj]
decisions:
  - "Source-only mounts on Windows map to /home/developer/<name> (container home), not host path"
  - "Project mounts at /workspace on Windows for container-friendly path, same-path on Unix"
  - "LOCAL_HOME set to /home/developer on Windows for consistent container environment"
  - "Mount sources normalized via fs/unixify on Windows (Docker Desktop accepts forward slashes)"
  - "Harness config mounts use fs/path instead of string concatenation, map to /home/developer on Windows"
metrics:
  duration_seconds: 101
  tasks_completed: 2
  files_modified: 2
  completed_date: 2026-02-12
---

# Phase 54 Plan 02: Cross-Platform Docker Mount Construction Summary

**Windows drive letter-aware mount parsing with fs/unixify normalization, platform-specific destination mapping, and /workspace project mounts**

## Performance

- **Duration:** 1 min 41 sec
- **Started:** 2026-02-12T01:13:47Z
- **Completed:** 2026-02-12T01:15:28Z
- **Tasks:** 2
- **Files modified:** 2

## Accomplishments
- Smart mount string parsing that handles Windows drive letter colons (C:\Users\name\.ssh:dest)
- Docker source path normalization using fs/unixify on Windows for Docker Desktop compatibility
- Platform-aware mount destination mapping: source-only mounts under /home/developer on Windows, same-path on Unix
- Project mounts at /workspace on Windows (container-friendly), same-path on Unix
- Harness config mounts using fs/path instead of string concatenation with hardcoded slashes
- Consistent mount validation in check.clj using same smart colon parsing logic

## Task Commits

Each task was committed atomically:

1. **Task 1: Add mount parsing and Docker path helpers to docker/run.clj** - `b6d9a0a` (feat)
2. **Task 2: Update check.clj mount parsing for Windows drive letter support** - `0be9666` (feat)

## Files Created/Modified
- `src/aishell/docker/run.clj` - Added parse-mount-string, normalize-mount-source; rewrote build-mount-args, build-harness-config-mounts, project mount logic in build-docker-args-internal
- `src/aishell/check.clj` - Updated check-mounts with smart colon parsing for Windows drive letters

## Implementation Details

### Smart Colon Parsing

**parse-mount-string function** detects Windows drive letters to avoid splitting on them:

```clojure
(if (re-matches #"^[A-Za-z]:[/\\].*" mount-str)
  ;; Windows absolute path — find colon AFTER drive letter
  (if-let [idx (str/index-of mount-str ":" 2)]
    [(subs mount-str 0 idx) (subs mount-str (inc idx))]
    [mount-str nil])
  ;; Unix path — split on first colon
  (if-let [idx (str/index-of mount-str ":")]
    [(subs mount-str 0 idx) (subs mount-str (inc idx))]
    [mount-str nil]))
```

Examples:
- `C:\Users\name\.ssh:/home/developer/.ssh` → `["C:\Users\name\.ssh" "/home/developer/.ssh"]`
- `~/.ssh` → `["~/.ssh" nil]`
- `/host/path:/container/path` → `["/host/path" "/container/path"]`

### Docker Path Normalization

**normalize-mount-source function** uses fs/unixify on Windows:

```clojure
(if (fs/windows?)
  (fs/unixify source-path)
  source-path)
```

Docker Desktop for Windows (WSL2) accepts forward-slash paths, so `C:\Users\name` becomes `C:/Users/name` in Docker commands.

### Platform-Aware Destinations

**build-mount-args:**
- Source-only mounts on Windows: `~/.ssh` → `/home/developer/.ssh` (container home)
- Source-only mounts on Unix: `~/.ssh` → `~/.ssh` (same path)
- Explicit dest mounts: trust user's container path on both platforms

**build-harness-config-mounts:**
- Windows: All harness configs mount under `/home/developer/` (e.g., `C:\Users\name\.claude` → `/home/developer/.claude`)
- Unix: Same-path mounts preserved (e.g., `/home/jonasrodrigues/.claude` → `/home/jonasrodrigues/.claude`)
- Path construction: `(apply fs/path home components)` instead of `(str home "/.claude")`

**build-docker-args-internal (project mount):**
- Windows: Project at `/workspace`, working dir `/workspace`, LOCAL_HOME `/home/developer`
- Unix: Project at same path, working dir same path, LOCAL_HOME host home (unchanged)

### Validation Consistency

**check.clj check-mounts** uses same smart colon parsing logic (inlined to avoid dependency on docker/run.clj):

```clojure
source (if (re-matches #"^[A-Za-z]:[/\\].*" mount-str)
         ;; Windows absolute path — before second colon
         (if-let [idx (str/index-of mount-str ":" 2)]
           (subs mount-str 0 idx)
           mount-str)
         ;; Unix path — before first colon
         (first (str/split mount-str #":" 2)))
```

## Decisions Made

1. **Source-only mounts on Windows map to /home/developer/<name>** - Windows host paths are meaningless in Linux container, mapping under container home creates consistent environment
2. **Project at /workspace on Windows** - Avoids Windows path complexity in container, provides standard working directory
3. **LOCAL_HOME=/home/developer on Windows** - Consistent with other Windows path mappings, creates uniform container environment
4. **fs/unixify for Docker paths on Windows** - Docker Desktop accepts forward slashes, simplifies path handling
5. **fs/path for harness config construction** - Eliminates hardcoded `/` separators, platform-agnostic

## Deviations from Plan

None - plan executed exactly as written.

## Issues Encountered

None - implementation followed plan specifications without obstacles.

## Verification Results

All verification checks passed:

**Function counts:**
- `parse-mount-string`: 2 occurrences (defn + call) ✓
- `normalize-mount-source`: 4 occurrences (defn + 3 calls) ✓
- `fs/unixify`: 1 occurrence ✓
- `/workspace`: 1 occurrence ✓
- `container-home`: 4 occurrences ✓
- `A-Za-z` in check.clj: 1 occurrence ✓

**No hardcoded path separators:**
- `grep "(str home \"/" src/aishell/docker/run.clj` → No matches ✓

**File loading:**
- `bb -e "(require '[aishell.docker.run])"` → Success ✓
- `bb -e "(require '[aishell.check])"` → Success ✓

**CLI functionality:**
- `bb aishell.clj --help` → Success ✓
- `bb aishell.clj check` → Success (Docker not installed warning expected) ✓

## Next Phase Readiness

**Ready for Phase 55 (Host Identity):**
- Mount construction fully cross-platform compatible
- Path normalization handles Windows → Docker translation
- Project workspace location decoupled from host path structure

**Windows support status:**
- Mount paths: ✓ Complete
- Path expansion: ✓ Complete (Phase 54-01)
- User identity: Pending (Phase 55 - UID/GID handling)

No blockers - clean continuation to host identity work.

---
*Phase: 54-path-handling*
*Completed: 2026-02-12*

## Self-Check

Verifying claimed artifacts exist:

```bash
# Check modified files exist
[ -f "/home/jonasrodrigues/projects/harness/src/aishell/docker/run.clj" ] && echo "FOUND: src/aishell/docker/run.clj" || echo "MISSING: src/aishell/docker/run.clj"
# Result: FOUND: src/aishell/docker/run.clj

[ -f "/home/jonasrodrigues/projects/harness/src/aishell/check.clj" ] && echo "FOUND: src/aishell/check.clj" || echo "MISSING: src/aishell/check.clj"
# Result: FOUND: src/aishell/check.clj

# Check commits exist
git log --oneline --all | grep -q "b6d9a0a" && echo "FOUND: b6d9a0a" || echo "MISSING: b6d9a0a"
# Result: FOUND: b6d9a0a

git log --oneline --all | grep -q "0be9666" && echo "FOUND: 0be9666" || echo "MISSING: 0be9666"
# Result: FOUND: 0be9666
```

## Self-Check: PASSED

All claimed artifacts verified successfully.
