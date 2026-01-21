# Phase 17: Validation & Polish - Research

**Researched:** 2026-01-21
**Domain:** Docker security validation, hash comparison, CLI subcommands
**Confidence:** HIGH

## Summary

Phase 17 implements validation and security warnings to match v1.2 bash hardening features. The primary work involves: (1) implementing the `update` command, (2) adding `--force` flag to build, (3) warning on dangerous docker_args patterns, and (4) detecting Dockerfile changes.

Version validation already exists in `cli.clj` (lines 18-33) - just verify it works. The focus is on new functionality: update command, build --force flag, security warnings, and Dockerfile change detection.

**Primary recommendation:** Follow v1.2 bash patterns closely - the dangerous patterns are well-defined, the hash storage approach is proven, and the update command is essentially `build --force` with state preservation.

## Standard Stack

No new libraries needed. All functionality uses existing Babashka built-ins.

### Core (Already Available)
| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| clojure.string | built-in | Pattern matching for dangerous args | Core Clojure |
| babashka.fs | built-in | File operations | Already in project |
| clojure.edn | built-in | State persistence | Already in project |
| java.security.MessageDigest | JVM | SHA-256 hashing | Already used in hash.clj |

### No New Dependencies
All required functionality exists in the codebase:
- `aishell.docker.hash/compute-hash` - SHA-256 hashing (12-char truncation)
- `aishell.state/read-state`, `write-state` - State persistence
- `aishell.docker.build/build-base-image` - Build orchestration
- `aishell.output/warn` - Warning output

## Architecture Patterns

### Module Organization
The validation functionality should be distributed across existing modules:

```
src/aishell/
├── cli.clj              # update command dispatch + build --force flag
├── validation.clj       # NEW: dangerous pattern checking
├── docker/
│   └── build.clj        # build --no-cache support + dockerfile hash in state
├── state.clj            # Add :dockerfile-hash to schema
└── run.clj              # Check dockerfile hash on run, warn if stale
```

### Pattern 1: Dangerous Args Checker
**What:** Function that checks docker_args string for dangerous patterns
**When to use:** At config load time or run time (discretion: run time is lazier)

```clojure
;; Source: v1.2 bash implementation (lines 152-182)
(def dangerous-patterns
  [{:pattern "--privileged"
    :message "Container has full host access"}
   {:pattern "docker.sock"
    :message "Container can control Docker daemon"}
   {:pattern #"--cap-add=(SYS_ADMIN|ALL)"
    :message "Elevated capabilities increase container escape risk"}
   {:pattern #"(apparmor|seccomp)=unconfined"
    :message "Disabled security profiles reduce container isolation"}])

(defn check-dangerous-args
  "Check docker_args for dangerous patterns. Returns seq of warnings."
  [docker-args]
  (when docker-args
    (->> dangerous-patterns
         (keep (fn [{:keys [pattern message]}]
                 (when (if (string? pattern)
                         (str/includes? docker-args pattern)
                         (re-find pattern docker-args))
                   message))))))
```

### Pattern 2: State Schema with Dockerfile Hash
**What:** Extend state.edn schema to include dockerfile-hash
**When to use:** Written during build, read during run

```clojure
;; Extended state schema
{:with-claude true
 :with-opencode false
 :claude-version "2.0.22"
 :opencode-version nil
 :image-tag "aishell:base"
 :build-time "2026-01-21T10:30:00Z"
 :dockerfile-hash "abc123def456"}  ; NEW - 12-char SHA-256 hash
```

### Pattern 3: Update Command
**What:** Rebuild with preserved state flags + --no-cache
**When to use:** `aishell update` command

```clojure
;; Update = read state + build with --no-cache + same flags
(defn handle-update [opts]
  (let [state (state/read-state)]
    (when-not state
      (output/error-no-build))
    (build/build-base-image
      {:with-claude (:with-claude state)
       :with-opencode (:with-opencode state)
       :claude-version (:claude-version state)
       :opencode-version (:opencode-version state)
       :force true      ; Always --no-cache for update
       :verbose (:verbose opts)})))
```

### Anti-Patterns to Avoid
- **Blocking on warnings:** Docker security warnings are advisory only - warn, don't block execution
- **Checking warnings at config load:** Better to check at run time to avoid slow startup for non-run commands
- **Storing full Dockerfile content:** Hash is sufficient for change detection, much more compact

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| SHA-256 hashing | Custom implementation | `aishell.docker.hash/compute-hash` | Already implemented in Phase 14 |
| State persistence | New state system | `aishell.state/read-state`, `write-state` | Already implemented in Phase 15 |
| Warning output | Custom stderr writing | `aishell.output/warn` | Consistent formatting, color support |
| CLI dispatch | Custom routing | `babashka.cli/dispatch` | Already used, just add update command |

**Key insight:** This phase is integration work - connecting existing pieces, not building new foundations.

## Dangerous Docker Args Patterns

### From v1.2 Bash Implementation (lines 152-182)
The bash implementation checks these patterns:

| Pattern | Risk Level | Message |
|---------|------------|---------|
| `--privileged` | CRITICAL | Container has full host access |
| `docker.sock` | CRITICAL | Container can control Docker daemon |
| `--cap-add=SYS_ADMIN` | HIGH | Elevated capabilities increase container escape risk |
| `--cap-add=ALL` | HIGH | Elevated capabilities increase container escape risk |
| `apparmor=unconfined` | HIGH | Disabled security profiles reduce container isolation |
| `seccomp=unconfined` | HIGH | Disabled security profiles reduce container isolation |

### Additional Patterns from OWASP (for consideration)
| Pattern | Risk Level | Message |
|---------|------------|---------|
| `--pid=host` | HIGH | Container shares host PID namespace |
| `--net=host` | MEDIUM | Container shares host network stack |
| `--ipc=host` | HIGH | Container shares host IPC namespace |

**Recommendation:** Start with v1.2 patterns (proven, tested). Consider adding OWASP patterns in future hardening phase.

### Warning Output Format
Match v1.2 bash format:

```
Warning: Security notice: Potentially dangerous Docker options detected
  - --privileged: Container has full host access
  - docker.sock mount: Container can control Docker daemon

These options reduce container isolation. Use only if necessary.
```

## Build --force Flag

### Implementation
Add `--force` to build-spec in cli.clj:

```clojure
(def build-spec
  {:with-claude   {:desc "Include Claude Code (optional: =VERSION)"}
   :with-opencode {:desc "Include OpenCode (optional: =VERSION)"}
   :force         {:coerce :boolean :desc "Force rebuild (bypass Docker cache)"}  ; NEW
   :verbose       {:alias :v :coerce :boolean :desc "Show full Docker build output"}
   :help          {:alias :h :coerce :boolean :desc "Show build help"}})
```

### Docker Build Args
In `docker/build.clj`, pass `--no-cache` when `:force` is true:

```clojure
(defn- run-build
  [build-dir tag args verbose? force?]
  (let [cmd (vec (concat ["docker" "build" "-t" tag]
                         (when force? ["--no-cache"])  ; NEW
                         (when verbose? ["--progress=plain"])
                         args
                         ["."]))]
    ...))
```

## Dockerfile Change Detection

### How It Works
1. **At build time:** Compute hash of embedded Dockerfile, store in state.edn
2. **At run time:** Compute current hash, compare with stored hash
3. **If different:** Warn user to run `aishell update`

### Hash Storage
```clojure
;; In state.edn
{:dockerfile-hash "abc123def456"  ; From build time
 ...}
```

### Check on Run (in run.clj)
```clojure
(defn- check-dockerfile-changed
  "Warn if Dockerfile has changed since last build."
  [state]
  (when-let [stored-hash (:dockerfile-hash state)]
    (let [current-hash (hash/compute-hash templates/base-dockerfile)]
      (when (not= stored-hash current-hash)
        (output/warn "Image may be stale. Run 'aishell update' to rebuild.")))))
```

### Message Simplicity
Per CONTEXT.md: "Simple message: Image may be stale. Run 'aishell update' to rebuild."

Don't include hash values in user-facing message (matches v1.2 but simpler).

## Common Pitfalls

### Pitfall 1: Forgetting --no-cache for Update
**What goes wrong:** Update command doesn't actually fetch latest versions
**Why it happens:** Docker cache serves old layers even when npm packages updated
**How to avoid:** Always pass `--no-cache` flag for update command
**Warning signs:** "Update" completes instantly without network activity

### Pitfall 2: Blocking on Dangerous Args
**What goes wrong:** Users with legitimate use cases can't use tool
**Why it happens:** Over-aggressive security posture
**How to avoid:** Advisory warnings only, never block execution
**Warning signs:** Error exit instead of warn

### Pitfall 3: Missing State Check
**What goes wrong:** Update fails cryptically when no prior build exists
**Why it happens:** Assumes state always exists
**How to avoid:** Check `(state/read-state)` returns non-nil before update
**Warning signs:** NullPointerException or confusing error

### Pitfall 4: Hash Stored in Image, Not State
**What goes wrong:** Need to query Docker for comparison
**Why it happens:** Following pattern from image labels
**How to avoid:** Store hash in state.edn - simpler, faster, no Docker calls
**Warning signs:** Slow startup, Docker dependency for simple check

## Code Examples

### Dangerous Args Warning Function
```clojure
;; src/aishell/validation.clj
(ns aishell.validation
  (:require [clojure.string :as str]
            [aishell.output :as output]))

(def dangerous-patterns
  [{:pattern "--privileged"
    :message "--privileged: Container has full host access"}
   {:pattern "docker.sock"
    :message "docker.sock mount: Container can control Docker daemon"}
   {:pattern #"--cap-add=(SYS_ADMIN|ALL)"
    :message "Elevated capabilities: Increases container escape risk"}
   {:pattern #"(apparmor|seccomp)=unconfined"
    :message "Disabled security profiles: Reduces container isolation"}])

(defn check-dangerous-args
  "Check docker_args string for dangerous patterns.
   Returns seq of warning messages, or nil if none found."
  [docker-args]
  (when (and docker-args (not (str/blank? docker-args)))
    (seq
      (keep
        (fn [{:keys [pattern message]}]
          (when (if (string? pattern)
                  (str/includes? docker-args pattern)
                  (re-find pattern docker-args))
            message))
        dangerous-patterns))))

(defn warn-dangerous-args
  "Warn about dangerous docker_args if any found."
  [docker-args]
  (when-let [warnings (check-dangerous-args docker-args)]
    (println)  ; Blank line before warning block
    (output/warn "Security notice: Potentially dangerous Docker options detected")
    (doseq [msg warnings]
      (binding [*out* *err*]
        (println (str "  - " msg))))
    (binding [*out* *err*]
      (println)
      (println "These options reduce container isolation. Use only if necessary.")
      (println))))
```

### Update Command Handler
```clojure
;; In cli.clj
(defn handle-update [{:keys [opts]}]
  (let [state (state/read-state)]
    ;; Check state exists
    (when-not state
      (output/error "No previous build found. Run: aishell build"))

    ;; Rebuild with same config + force
    (let [result (build/build-base-image
                   {:with-claude (:with-claude state)
                    :with-opencode (:with-opencode state)
                    :claude-version (:claude-version state)
                    :opencode-version (:opencode-version state)
                    :verbose (:verbose opts)
                    :force true})]

      ;; Update state with new build-time and hash
      (state/write-state
        (assoc state
               :image-tag (:image result)
               :build-time (str (java.time.Instant/now))
               :dockerfile-hash (hash/compute-hash templates/base-dockerfile))))))
```

### Dockerfile Hash Check on Run
```clojure
;; In run.clj, before running container
(defn- check-dockerfile-stale
  "Check if embedded Dockerfile changed since build, warn if so."
  [state]
  (when-let [stored-hash (:dockerfile-hash state)]
    (let [current-hash (hash/compute-hash templates/base-dockerfile)]
      (when (not= stored-hash current-hash)
        (output/warn "Image may be stale. Run 'aishell update' to rebuild.")))))
```

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| Per-project state files | Global ~/.aishell/state.edn | Phase 15 | Simpler, matches v1.2 |
| Hash in Docker label only | Hash in state.edn | This phase | Faster check, no Docker call |
| Version validation at run time | Validation at parse time | Phase 15 | Fail fast |

**Already Current:**
- Version validation (semver + dangerous chars) implemented in Phase 15
- SHA-256 hashing implemented in Phase 14
- State persistence implemented in Phase 15

## Open Questions

None - all questions answered by research:

1. **Dangerous docker_args patterns** - Use v1.2 patterns: `--privileged`, `docker.sock`, `--cap-add=SYS_ADMIN|ALL`, `apparmor=unconfined`, `seccomp=unconfined`
2. **Hash storage** - Store in state.edn, not just Docker labels
3. **v1.2 implementation** - Reviewed bash code lines 152-182 (dangerous args), 450-488 (Dockerfile hash)
4. **Build --force** - Add `--force` flag, pass `--no-cache` to Docker build

## Sources

### Primary (HIGH confidence)
- `/home/jonasrodrigues/projects/harness/aishell` (bash v1.2) - Lines 152-182 (dangerous args), 450-488 (Dockerfile hash), 1341-1473 (update command)
- `/home/jonasrodrigues/projects/harness/src/aishell/cli.clj` - Existing version validation (lines 18-33)
- `/home/jonasrodrigues/projects/harness/src/aishell/docker/build.clj` - Build implementation
- `/home/jonasrodrigues/projects/harness/src/aishell/docker/hash.clj` - SHA-256 hashing

### Secondary (MEDIUM confidence)
- [OWASP Docker Security Cheat Sheet](https://cheatsheetseries.owasp.org/cheatsheets/Docker_Security_Cheat_Sheet.html) - Additional dangerous patterns
- [Docker Security Best Practices](https://docs.docker.com/engine/security/) - Official security docs

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH - No new dependencies, uses existing modules
- Architecture: HIGH - Following proven v1.2 patterns
- Dangerous patterns: HIGH - Directly from v1.2 bash implementation
- Pitfalls: HIGH - Based on implementation details in codebase

**Research date:** 2026-01-21
**Valid until:** 60 days (stable patterns, no expected changes)

---
*Phase: 17-validation-polish*
*Research completed: 2026-01-21*
