# Architecture: tmux + Named Containers Integration

**Project:** harness (aishell)
**Researched:** 2026-01-31
**Confidence:** HIGH

## Executive Summary

The tmux and named containers integration represents a shift from ephemeral (--rm) to persistent container lifecycle, enabling background sessions and reattachment workflows. This requires coordinated changes across 6 existing components plus 2 new namespaces, all while preserving existing ephemeral patterns for backward compatibility.

**Core architectural changes:**
1. **Container lifecycle**: Ephemeral (--rm) → Persistent (named, detached)
2. **Command wrapping**: Direct execution → tmux session wrapper
3. **Naming strategy**: Anonymous → Project-hash-prefixed names
4. **New commands**: `attach` and `ps` for container management

## Current Architecture (Baseline)

### Component Map

| Component | Responsibility | Key Functions |
|-----------|---------------|---------------|
| `cli.clj` | Command dispatch | `dispatch`, `handle-build`, `handle-default` |
| `run.clj` | Container launch orchestration | `run-container`, `run-exec` |
| `docker/run.clj` | Docker flag construction | `build-docker-args`, `build-docker-args-for-exec` |
| `templates.clj` | Embedded Dockerfile/entrypoint | `base-dockerfile`, `entrypoint-script` |
| `state.clj` | Build state persistence (~/.aishell/state.edn) | `read-state`, `write-state` |
| `config.clj` | Per-project config (config.yaml) | `load-config`, `merge-configs` |

### Current Container Launch Flow

```
User runs: aishell claude
    ↓
cli.clj dispatch → run-container "claude"
    ↓
run.clj orchestrates:
  - Load state (verify image exists)
  - Load config (project + global merge)
  - Read git identity
  - Run detection warnings
    ↓
docker/run.clj builds docker args:
  ["docker" "run" "--rm" "-it"
   "-v" "/project:/project"
   "-w" "/project"
   "-e" "LOCAL_UID=..."
   "aishell:base"
   "claude" "--dangerously-skip-permissions"]
    ↓
babashka.process/exec → replaces process
    ↓
entrypoint.sh runs:
  - Create user with matching UID/GID
  - Setup home directory
  - Configure git safe.directory
  - Run pre_start hooks (background)
  - exec gosu developer "claude" [args]
```

**Key characteristics:**
- **Ephemeral**: --rm means container self-destructs on exit
- **Synchronous**: p/exec replaces Babashka process, blocks until exit
- **Stateless**: Each run creates fresh container
- **Anonymous**: No --name flag, Docker assigns random names

## Target Architecture (tmux + Named Containers)

### New Container Lifecycle

```
User runs: aishell claude
    ↓
Check for running container (docker ps --filter name=^aishell-<hash>)
    ↓
    FOUND                           NOT FOUND
    ↓                               ↓
Warn: "Already running"         Launch new container:
Offer: Run anyway? attach?        - Remove --rm flag
Exit or proceed                   - Add --name aishell-<hash>
                                  - Add -d (detached)
                                  - Wrap command in tmux
                                    ↓
                                entrypoint.sh:
                                  - Create user (existing)
                                  - Run pre_start (existing)
                                  - exec gosu developer tmux new-session -s harness "claude [args]"
                                    ↓
                                Container runs in background
                                Babashka process exits
                                    ↓
User runs: aishell attach
    ↓
Find running container (docker ps --filter name=^aishell-<hash>)
    ↓
docker exec -it aishell-<hash> tmux attach-session -t harness
    ↓
User attached to existing session
```

### Component Changes

#### 1. templates.clj (Dockerfile Modification)

**Current:** No tmux installed

**Change:** Add tmux to package list

```dockerfile
RUN apt-get update && apt-get install -y --no-install-recommends \
    bash \
    ca-certificates \
    curl \
    file \
    git \
    htop \
    jq \
    less \
    ripgrep \
    sqlite3 \
    sudo \
    tmux \     # <-- ADD THIS
    tree \
    unzip \
    vim \
    watch \
    && rm -rf /var/lib/apt/lists/*
```

**Impact:** Increases image size minimally (~200KB for tmux binary)

**Confidence:** HIGH - Standard Debian package, well-established

---

#### 2. docker/run.clj (Flag Construction)

**Current:** Builds `["docker" "run" "--rm" "-it" ...]`

**New functionality needed:**

| Function | Purpose | Signature |
|----------|---------|-----------|
| `compute-container-name` | Generate project-hash-prefixed name | `[project-dir] → "aishell-a1b2c3d4"` |
| `build-docker-args-detached` | Build args for background container | `[{:keys [project-dir image-tag config ...]}] → [...]` |

**Flag differences:**

| Mode | Flags | Use Case |
|------|-------|----------|
| Ephemeral (existing) | `--rm -it` | `exec` command, backward compat |
| Detached (new) | `--name X -d -it` | tmux-wrapped harness commands |

**Container naming strategy:**
```clojure
(defn compute-container-name
  "Generate deterministic container name from project path.
   Format: aishell-<8-char-hash>
   Uses SHA-256 of absolute project path for consistency."
  [project-dir]
  (let [hash (-> (java.security.MessageDigest/getInstance "SHA-256")
                 (.digest (.getBytes project-dir "UTF-8"))
                 (take 4)  ; 4 bytes = 8 hex chars
                 (->> (map #(format "%02x" (bit-and % 0xff)))
                      (apply str)))]
    (str "aishell-" hash)))
```

**Why hash-based naming:**
- **Deterministic**: Same project always gets same name
- **Collision-resistant**: 8 hex chars = 4.3 billion combinations
- **Discoverable**: `docker ps` shows recognizable prefix
- **Queryable**: Filter via `--filter name=^aishell-`

**Sources:**
- [Docker container naming best practices](https://devtodevops.com/blog/docker-container-naming-convention/)
- [Container name conflict resolution](https://labex.io/tutorials/docker-how-to-resolve-container-naming-conflicts-418051)

---

#### 3. run.clj (Orchestration Changes)

**Current:** Single execution path via `run-container`

**New logic:**

```clojure
(defn run-container
  [cmd harness-args & [opts]]
  ;; ... existing validation ...

  ;; NEW: Conflict detection for tmux-wrapped commands
  (when (and cmd (not= cmd "gitleaks"))  ; tmux mode
    (let [container-name (docker-run/compute-container-name project-dir)
          running? (docker/container-running? container-name)]
      (when running?
        (conflict-detected-handler container-name cmd))))

  ;; Build docker args with appropriate mode
  (let [docker-args (if (and cmd (not= cmd "gitleaks"))
                      ;; Detached mode for harnesses
                      (docker-run/build-docker-args-detached {...})
                      ;; Ephemeral mode for shell/exec
                      (docker-run/build-docker-args {...}))

        ;; Wrap command in tmux for harness commands
        container-cmd (if (and cmd (not= cmd "gitleaks"))
                        ["tmux" "new-session" "-s" "harness"
                         (into ["claude" ...] merged-args)]
                        ;; Existing logic for shell/gitleaks
                        ...)]

    ;; For detached mode, use p/shell and return
    ;; For ephemeral mode, use p/exec (existing)
    (if (and cmd (not= cmd "gitleaks"))
      (do
        (apply p/shell {:inherit true} (concat docker-args container-cmd))
        (println (str "Session running in background. Attach with: aishell attach")))
      (apply p/exec (concat docker-args container-cmd)))))
```

**Conflict detection behavior:**
- **Default**: Error and exit (safe)
- **Optional flags** (future):
  - `--force`: Kill existing and start new
  - `--attach`: Skip launch, attach instead

**Sources:**
- [Docker name conflict handling](https://www.baeldung.com/ops/docker-name-already-in-use)
- [Babashka process exec vs shell](https://github.com/babashka/process)

---

#### 4. cli.clj (New Command Dispatch)

**Current:** Dispatches `build`, `update`, `check`, `exec`, harness commands

**Add:**

```clojure
(def dispatch-table
  [{:cmds ["build"] :fn handle-build :spec build-spec :restrict true}
   {:cmds ["update"] :fn handle-update :spec update-spec :restrict true}
   {:cmds ["attach"] :fn handle-attach :spec attach-spec :restrict true}  ; NEW
   {:cmds ["ps"] :fn handle-ps :spec ps-spec :restrict true}             ; NEW
   {:cmds [] :spec global-spec :fn handle-default}])

(defn dispatch [args]
  (case (first clean-args)
    "check" (check/run-check)
    "exec" (run/run-exec ...)
    "attach" (run/run-attach)    ; NEW
    "ps" (run/run-ps)            ; NEW
    "claude" (run/run-container "claude" ...)
    ...))
```

**Help text additions:**
```
Commands:
  build      Build the container image
  update     Rebuild with latest versions
  check      Validate setup and configuration
  exec       Run one-off command in container
  attach     Attach to running container session   <-- NEW
  ps         List running containers               <-- NEW
  claude     Run Claude Code
  ...
```

---

#### 5. NEW: docker.clj (Container Utilities)

**Add to existing docker.clj:**

```clojure
(defn container-running?
  "Check if a named container exists and is running."
  [container-name]
  (try
    (let [{:keys [exit out]} (p/shell {:out :string :err :string :continue true}
                                       "docker" "ps" "-q"
                                       "--filter" (str "name=^" container-name "$"))]
      (and (zero? exit)
           (not (str/blank? out))))
    (catch Exception _ false)))

(defn container-exists?
  "Check if a named container exists (running or stopped)."
  [container-name]
  (try
    (let [{:keys [exit out]} (p/shell {:out :string :err :string :continue true}
                                       "docker" "ps" "-aq"
                                       "--filter" (str "name=^" container-name "$"))]
      (and (zero? exit)
           (not (str/blank? out))))
    (catch Exception _ false)))

(defn list-aishell-containers
  "List all aishell-prefixed containers with status.
   Returns seq of {:name :status :project-dir} maps."
  []
  (try
    (let [{:keys [exit out]}
          (p/shell {:out :string :err :string :continue true}
                   "docker" "ps" "-a"
                   "--filter" "name=^aishell-"
                   "--format" "{{.Names}}\t{{.Status}}")]
      (when (zero? exit)
        (for [line (str/split-lines out)
              :when (not (str/blank? line))
              :let [[name status] (str/split line #"\t")]]
          {:name name :status status})))
    (catch Exception _ [])))
```

**Filter pattern:**
- `docker ps --filter "name=^aishell-"` matches containers starting with "aishell-"
- Regex anchoring (`^`) ensures prefix match only

**Sources:**
- [Docker ps filtering documentation](https://docs.docker.com/reference/cli/docker/container/ls/)
- [Docker name filter with regex](https://github.com/docker/docker.github.io/issues/11767)

---

#### 6. NEW: attach.clj (Attach Command Handler)

**New namespace:** `src/aishell/attach.clj`

```clojure
(ns aishell.attach
  (:require [babashka.process :as p]
            [aishell.docker :as docker]
            [aishell.docker.run :as docker-run]
            [aishell.output :as output]))

(defn run-attach
  "Attach to running container's tmux session.

   Workflow:
   1. Compute container name from cwd
   2. Check if container running
   3. docker exec -it <name> tmux attach-session -t harness"
  []
  (docker/check-docker!)

  (let [project-dir (System/getProperty "user.dir")
        container-name (docker-run/compute-container-name project-dir)]

    (cond
      (not (docker/container-running? container-name))
      (output/error (str "No running container for this project.\n"
                        "Start with: aishell claude"))

      :else
      ;; Attach to tmux session
      ;; Using exec instead of attach because tmux is not PID 1
      (apply p/exec ["docker" "exec" "-it" container-name
                     "tmux" "attach-session" "-t" "harness"]))))
```

**Why docker exec, not docker attach:**
- `docker attach`: Connects to container's PID 1 (entrypoint)
- `docker exec`: Spawns new process (tmux attach)
- tmux session is NOT PID 1 (gosu → tmux is child process)
- exec allows independent attach/detach without affecting container

**Sources:**
- [Docker attach vs exec differences](https://yasoob.me/posts/docker-attach-vs-exec-when-to-use-what/)
- [tmux in Docker containers](https://gist.github.com/ptrj/b54b8cdd34f632028e669a1e71bc03b8)

---

#### 7. NEW: ps.clj (List Command Handler)

**New namespace:** `src/aishell/ps.clj`

```clojure
(ns aishell.ps
  (:require [aishell.docker :as docker]
            [aishell.output :as output]))

(defn run-ps
  "List all aishell containers with status.

   Output format:
   NAME                STATUS              PROJECT
   aishell-a1b2c3d4    Up 2 hours          /home/user/project
   aishell-e5f6g7h8    Exited (0) 1 day    /home/user/other"
  []
  (docker/check-docker!)

  (let [containers (docker/list-aishell-containers)]
    (if (empty? containers)
      (println "No aishell containers found")
      (do
        (println (str output/BOLD "NAME\t\t\tSTATUS" output/NC))
        (doseq [{:keys [name status]} containers]
          (println (str name "\t" status)))))))
```

**Future enhancement:** Map container names back to project directories
- Requires storing metadata (label or volume mount marker)
- Initial version: Just show containers, user maps manually

---

## Data Flow Changes

### Ephemeral Mode (Backward Compatible)

**Triggers:** Shell mode, `exec` command

```
run.clj
  → docker/run.clj: build-docker-args (--rm -it)
  → p/exec ["docker" "run" "--rm" "-it" ... "aishell:base" "/bin/bash"]
  → Process replacement
```

**Unchanged from current behavior**

### Detached Mode (New)

**Triggers:** Harness commands (claude, opencode, codex, gemini)

```
run.clj
  → docker.clj: container-running? (check conflict)
  → docker/run.clj: compute-container-name
  → docker/run.clj: build-docker-args-detached (--name X -d -it)
  → p/shell ["docker" "run" "--name" "aishell-X" "-d" "-it" ...
             "aishell:base" "tmux" "new-session" "-s" "harness" "claude" ...]
  → Container starts in background
  → Babashka prints success message and exits
```

### Attach Workflow (New)

```
User: aishell attach
  → attach.clj: run-attach
  → docker/run.clj: compute-container-name (from cwd)
  → docker.clj: container-running? (verify exists)
  → p/exec ["docker" "exec" "-it" "aishell-X" "tmux" "attach-session" "-t" "harness"]
  → Process replacement, user in tmux session
```

### PS Workflow (New)

```
User: aishell ps
  → ps.clj: run-ps
  → docker.clj: list-aishell-containers
  → docker ps --filter name=^aishell- --format {{.Names}}\\t{{.Status}}
  → Parse and display table
```

---

## Integration Points

### Existing Components Modified

| Component | Change Type | Reason |
|-----------|-------------|--------|
| templates.clj | Add tmux package | Required for session management |
| docker/run.clj | Add functions | Container naming, detached flags |
| run.clj | Conditional logic | Ephemeral vs detached mode |
| cli.clj | Add commands | attach, ps dispatch |
| docker.clj | Add functions | Container queries (running?, list) |

### New Components

| Component | Purpose | Dependencies |
|-----------|---------|--------------|
| attach.clj | Attach command handler | docker.clj, docker/run.clj |
| ps.clj | List command handler | docker.clj |

**Dependency graph:**
```
cli.clj
 ├─→ attach.clj
 │    ├─→ docker.clj (container-running?)
 │    └─→ docker/run.clj (compute-container-name)
 ├─→ ps.clj
 │    └─→ docker.clj (list-aishell-containers)
 └─→ run.clj
      ├─→ docker.clj (container-running?, NEW)
      └─→ docker/run.clj (build-docker-args-detached, NEW)
```

---

## Architecture Patterns

### Pattern 1: Deterministic Naming

**What:** Hash-based container names from project path

**Why:**
- Same project → same name (idempotent)
- Different projects → different names (isolated)
- No user-provided names (avoid typos, enforce consistency)

**Implementation:**
```clojure
(compute-container-name "/home/user/project")
;; → "aishell-a1b2c3d4"

(compute-container-name "/home/user/other")
;; → "aishell-e5f6g7h8"
```

**Trade-off:** Cannot manually specify names, but ensures consistency

---

### Pattern 2: Mode Detection

**What:** Single `run-container` function handles both ephemeral and detached

**Why:**
- Minimize code duplication
- Preserve existing behavior for shell/exec
- Clear decision point (presence of `cmd` parameter)

**Implementation:**
```clojure
(defn run-container [cmd harness-args opts]
  (let [detached-mode? (and cmd (not= cmd "gitleaks"))]
    (if detached-mode?
      (run-detached cmd harness-args opts)
      (run-ephemeral cmd harness-args opts))))
```

**Alternative considered:** Separate `run-detached` function
- **Rejected:** Duplicates 90% of orchestration logic

---

### Pattern 3: tmux as Wrapper, Not Entrypoint

**What:** Command wrapped in tmux, not tmux as CMD

**Why:**
- Preserves existing entrypoint.sh logic (user creation, pre_start)
- tmux session contains actual command, not entrypoint boilerplate
- Allows ephemeral mode to bypass tmux entirely

**Implementation:**
```bash
# entrypoint.sh final line:
exec gosu "$USER_ID:$GROUP_ID" "$@"

# Where $@ is:
# Ephemeral: ["bash"]
# Detached:  ["tmux" "new-session" "-s" "harness" "claude" ...]
```

**Alternative considered:** ENTRYPOINT ["tmux", "new-session", ...]
- **Rejected:** Breaks ephemeral mode, complicates user creation

**Sources:**
- [tmux in daemon Docker containers](https://gist.github.com/ptrj/b54b8cdd34f632028e669a1e71bc03b8)
- [Keep Docker containers running](https://devopscube.com/keep-docker-container-running/)

---

### Pattern 4: Conflict Detection at Launch

**What:** Check for running container before `docker run`

**Why:**
- Prevents "name already in use" error
- Enables helpful error messages ("attach instead?")
- Safer than --rm with --force (which blindly kills)

**Implementation:**
```clojure
;; Before docker run
(when (docker/container-running? container-name)
  (output/error (str "Container already running for this project.\n"
                    "Options:\n"
                    "  - Attach: aishell attach\n"
                    "  - Kill and restart: docker rm -f " container-name)))
```

**Future enhancement:** `--force` flag to auto-kill and restart

---

## Anti-Patterns to Avoid

### Anti-Pattern 1: User-Provided Container Names

**What:** Let users specify container names via flag

**Why bad:**
- Typos lead to orphaned containers
- Collisions between users/projects
- Breaks deterministic attach (which name to use?)

**Instead:** Hash-based naming (implemented)

---

### Anti-Pattern 2: Stateful Container Discovery

**What:** Store container name in state file (~/.aishell/state.edn)

**Why bad:**
- State file is global, containers are per-project
- State can desync (manual `docker rm`, crashes)
- `docker ps` is source of truth, not our state

**Instead:** Compute name from project-dir at runtime

---

### Anti-Pattern 3: tmux as PID 1

**What:** Start container with `CMD ["tmux", "new-session", ...]`

**Why bad:**
- Breaks user creation logic (must run before tmux)
- Breaks pre_start hooks (must run before harness)
- Complicates signal handling (tmux vs gosu)

**Instead:** Wrap command in tmux via entrypoint args

**Sources:**
- [Docker entrypoint best practices](https://docs.docker.com/engine/containers/run/)

---

### Anti-Pattern 4: Single Container for All Projects

**What:** One global aishell container, reused across projects

**Why bad:**
- Project mounts conflict (which /project to mount?)
- State pollution (one project's changes affect others)
- Harder to clean up (can't remove per-project)

**Instead:** One container per project (hash-based naming)

---

## Scalability Considerations

| Concern | Current | At 10 Projects | At 100 Projects |
|---------|---------|----------------|-----------------|
| Container count | 0 (ephemeral) | 10 detached | 100 detached |
| Naming collisions | N/A | ~0% (8-char hash) | ~0.02% (birthday paradox) |
| Discovery performance | N/A | `docker ps` instant | `docker ps` instant (<1s) |
| Cleanup strategy | Auto (--rm) | Manual `docker rm` | Script: `docker ps -aq --filter name=^aishell- \| xargs docker rm` |

**Collision probability:**
- 8 hex chars = 4,294,967,296 possibilities
- 100 projects = 0.0002% collision chance
- 10,000 projects = 2% collision chance

**If collisions become issue:** Increase hash length to 12 chars (negligible)

---

## Build Order Recommendations

### Phase 1: Foundation (Container Utilities)

**Goal:** Add Docker container query functions

**Changes:**
- `docker.clj`: Add `container-running?`, `container-exists?`, `list-aishell-containers`
- `docker/run.clj`: Add `compute-container-name`

**Testing:**
- Unit tests for hash generation
- Integration tests for docker ps queries

**Why first:** Other phases depend on these utilities

---

### Phase 2: Dockerfile (tmux Installation)

**Goal:** Add tmux to base image

**Changes:**
- `templates.clj`: Add tmux to package list

**Testing:**
- Build image
- `docker run aishell:base tmux -V` (verify installed)

**Why second:** Needed before detached mode testing

---

### Phase 3: Detached Mode (Core Launch)

**Goal:** Enable tmux-wrapped detached containers

**Changes:**
- `docker/run.clj`: Add `build-docker-args-detached`
- `run.clj`: Add conditional logic for detached vs ephemeral
- `run.clj`: Add conflict detection

**Testing:**
- `aishell claude` (starts in background)
- `docker ps` (verify running)
- `docker exec -it aishell-X tmux attach` (manual attach)

**Why third:** Core functionality before convenience commands

---

### Phase 4: Attach Command

**Goal:** User-friendly reattachment

**Changes:**
- `attach.clj`: New namespace
- `cli.clj`: Add attach dispatch

**Testing:**
- `aishell claude` then `aishell attach`
- Error handling: `aishell attach` with no running container

**Why fourth:** Depends on detached mode

---

### Phase 5: PS Command

**Goal:** Container discovery

**Changes:**
- `ps.clj`: New namespace
- `cli.clj`: Add ps dispatch

**Testing:**
- `aishell ps` with no containers
- `aishell ps` with multiple running containers
- `aishell ps` with stopped containers (docker ps -a)

**Why fifth:** Nice-to-have, not blocking

---

### Phase 6: Refinements

**Goal:** Polish UX

**Changes:**
- Improved conflict messages (suggest attach)
- `--force` flag to kill and restart
- Better `ps` output (table formatting, project paths)

**Why last:** Dependent on user feedback

---

## Migration Path

### Backward Compatibility

**Preserved behaviors:**
- Shell mode (`aishell` with no args): Still ephemeral, --rm
- Exec mode (`aishell exec ls`): Still ephemeral, --rm
- Gitleaks: Still ephemeral (special case, uses p/shell but --rm)

**New behaviors:**
- Harness commands (`aishell claude`): Detached, named, tmux-wrapped

**Why this split:**
- Shell/exec are one-off → ephemeral makes sense
- Harnesses are long-running → persistence makes sense
- Gitleaks is scan tool → ephemeral matches mental model

**No breaking changes:** Existing workflows continue to work

---

## Known Limitations

### Limitation 1: No Project Path in PS Output

**Issue:** `aishell ps` shows container names, not which projects they belong to

**Workaround:** User manually maps via `cd /project && aishell attach`

**Future fix:** Store project path as Docker label, query with `--format {{.Label}}`

---

### Limitation 2: Containers Persist After Crashes

**Issue:** If harness crashes, container stays in "Exited" state

**Workaround:** `aishell ps` + manual `docker rm`

**Future fix:** Auto-cleanup command (`aishell clean`)

---

### Limitation 3: Hash Collisions Possible

**Issue:** 8-char hash has ~2% collision chance at 10K projects

**Current risk:** Low (typical user has <100 projects)

**Future fix:** Increase to 12 chars if needed

---

### Limitation 4: No Multi-Session Support

**Issue:** Only one tmux session ("harness") per container

**Impact:** Cannot run multiple harnesses in same container (e.g., claude + opencode)

**Workaround:** Different projects use different containers

**Design decision:** Out of scope for v1

---

## Summary

### Changes by Component

| Component | LOC Changed | Complexity | Risk |
|-----------|-------------|------------|------|
| templates.clj | +1 line | Trivial | Low |
| docker.clj | +40 lines | Low | Low |
| docker/run.clj | +60 lines | Medium | Medium |
| run.clj | +80 lines | Medium | Medium |
| cli.clj | +20 lines | Low | Low |
| attach.clj (new) | ~40 lines | Low | Low |
| ps.clj (new) | ~30 lines | Trivial | Low |

**Total:** ~270 lines added/modified

**Risk areas:**
- Conflict detection logic (must handle all edge cases)
- Mode detection (must not break existing ephemeral workflows)

**Testing strategy:**
- Unit tests: Hash generation, name validation
- Integration tests: Full launch → attach → detach → reattach flow
- Regression tests: Shell mode, exec mode still work

---

## Sources

- [Docker container naming best practices](https://devtodevops.com/blog/docker-container-naming-convention/)
- [Container name conflict resolution](https://labex.io/tutorials/docker-how-to-resolve-container-naming-conflicts-418051)
- [Docker attach vs exec differences](https://yasoob.me/posts/docker-attach-vs-exec-when-to-use-what/)
- [Docker ps filtering documentation](https://docs.docker.com/reference/cli/docker/container/ls/)
- [tmux in daemon Docker containers](https://gist.github.com/ptrj/b54b8cdd34f632028e669a1e71bc03b8)
- [Babashka process API](https://github.com/babashka/process)
- [Docker name already in use error](https://www.baeldung.com/ops/docker-name-already-in-use)
- [Keep Docker containers running](https://devopscube.com/keep-docker-container-running/)
- [Docker name filter with regex](https://github.com/docker/docker.github.io/issues/11767)
