# Phase 37: Build Integration & Migration - Research

**Researched:** 2026-02-01
**Domain:** State file schema migration, lazy initialization patterns, cache invalidation logic, build UX integration
**Confidence:** HIGH

## Summary

Phase 37 implements the final integration layer for volume-based harness injection, focusing on transparent build UX, lazy volume population, state schema migration, and cache invalidation updates. This phase completes the transition from the v2.7.x architecture (single `aishell:base` image) to v2.8.0 (foundation image + harness volumes).

The implementation centers on four critical components: (1) extending the state schema with separate foundation-hash and harness-volume-hash fields while maintaining backward compatibility with v2.7.0 state files, (2) implementing lazy volume population on first container run with staleness detection, (3) updating extension cache invalidation to reference foundation image ID instead of base image ID, and (4) integrating foundation build and volume population into a unified build command flow. The migration path leverages EDN's flexible schema and Clojure's nil-safe reading patterns to enable zero-downtime upgrades.

**Primary recommendation:** Use additive state schema migration (new fields default to nil, old fields remain functional), implement lazy volume population with hash-based staleness detection on container run, update extension cache to track foundation-image-id-label, and coordinate foundation build + volume population transparently in `aishell build` command.

## Standard Stack

### Core Technologies

| Technology | Version | Purpose | Why Standard |
|------------|---------|---------|--------------|
| EDN (Extensible Data Notation) | Clojure 1.12+ | State file format | Clojure's native serialization format, schema-flexible, human-readable |
| SHA-256 hashing | Java MessageDigest | Volume/image hash computation | Already used in aishell for cache invalidation (hash.clj) |
| Docker volume labels | Docker 1.9+ | Metadata storage for staleness detection | First-class Docker feature for volume metadata |
| Clojure nil-safe reading | Clojure core | Backward-compatible state parsing | `(get state :new-field default-value)` pattern handles missing keys gracefully |

### Supporting Patterns

| Pattern | Purpose | When to Use |
|---------|---------|-------------|
| Additive schema migration | Add new fields without breaking old readers | Migrating state files across versions |
| Lazy initialization | Defer expensive operations until needed | Volume population (only on first run or staleness) |
| Hash-based staleness detection | Compare stored hash against current config | Detecting when volumes need repopulation |
| Soft deprecation | Keep old fields functional while introducing new | Transitioning from :dockerfile-hash to :foundation-hash |

### Migration Approach

Phase 37 uses **additive schema migration** instead of versioned migration files:

```clojure
;; Old state (v2.7.0)
{:with-claude true
 :claude-version "2.0.22"
 :image-tag "aishell:base"
 :dockerfile-hash "abc123def456"}

;; New state (v2.8.0) - additive, backward compatible
{:with-claude true
 :claude-version "2.0.22"
 :image-tag "aishell:foundation"     ; Updated by build
 :dockerfile-hash "abc123def456"     ; Kept for backward compat (soft deprecated)
 :foundation-hash "abc123def456"     ; New field (nil in old files)
 :harness-volume-hash "def789ghi012" ; New field (nil in old files)
 :harness-volume-name "aishell-harness-def789ghi012"} ; New field
```

## Architecture Patterns

### Pattern 1: Additive State Schema Migration

**What:** Add new fields to state schema without removing old fields, use nil-safe reading for all new fields

**When to use:** Migrating state files across versions where backward compatibility is required

**Implementation:**

```clojure
;; state.clj - Updated schema (v2.8.0)
(defn write-state
  "Write state to file.

   State schema:
   {:with-claude true
    :claude-version \"2.0.22\"
    :image-tag \"aishell:foundation\"   ; Updated from \"aishell:base\"
    :dockerfile-hash \"abc123def456\"   ; DEPRECATED: Use :foundation-hash (kept for compatibility)
    :foundation-hash \"abc123def456\"   ; NEW: Foundation Dockerfile hash
    :harness-volume-hash \"def789ghi012\" ; NEW: Hash of enabled harnesses+versions
    :harness-volume-name \"aishell-harness-def789ghi012\"} ; NEW: Volume name for docker run"
  [state]
  ;; Implementation unchanged - writes whatever map is provided
  )

;; Reading with nil-safe defaults
(defn read-state []
  (when-let [state (edn/read-string (slurp (state-file)))]
    state)) ; Returns nil for missing keys (Clojure default)

;; Consumer code handles nil gracefully
(let [state (state/read-state)
      foundation-hash (or (:foundation-hash state)
                          (:dockerfile-hash state)) ; Fallback for v2.7.0 files
      volume-hash (:harness-volume-hash state)] ; nil in old files
  ;; If volume-hash is nil, trigger population
  )
```

**Why this works:** EDN maps in Clojure return nil for missing keys (not errors). Old state files lack new fields, but `(get state :new-field)` returns nil safely. New code checks for nil and handles gracefully (trigger lazy population, use fallback values).

**Source:** [EDN specification](https://github.com/edn-format/edn) - extensible format, no strict schema enforcement

### Pattern 2: Lazy Volume Population on First Run

**What:** Check volume staleness at container run time (not build time), populate only if empty or hash mismatch

**When to use:** Deferring expensive operations (npm install ~90s) until container actually runs

**Implementation:**

```clojure
;; run.clj - Pre-flight check before docker run
(defn ensure-harness-volume-ready
  "Ensure harness volume exists and is up-to-date.
   Populates lazily on first run or when stale."
  [state]
  (let [expected-hash (vol/compute-harness-hash state)
        volume-name (vol/volume-name expected-hash)]
    (cond
      ;; Volume doesn't exist - create and populate
      (not (vol/volume-exists? volume-name))
      (do
        (vol/create-volume volume-name {"aishell.harness.hash" expected-hash
                                        "aishell.harness.version" "2.8.0"})
        (vol/populate-volume volume-name state))

      ;; Volume exists but hash mismatch - stale, repopulate
      (not= (vol/get-volume-label volume-name "aishell.harness.hash")
            expected-hash)
      (do
        (println "Harness configuration changed, updating volume...")
        (vol/populate-volume volume-name state))

      ;; Volume exists and fresh - no action
      :else
      nil)
    volume-name))

;; Call before docker run
(defn run-container [cmd harness-args opts]
  (let [state (state/read-state)
        ;; ... existing code ...
        harness-volume-name (ensure-harness-volume-ready state)]
    ;; Proceed with docker run using harness-volume-name
    ))
```

**Performance:** Volume existence check is fast (~10ms via `docker volume inspect`). Population only happens on first run (90s) or config change (rare).

**Source pattern:** Similar to Docker Compose's dependency wait logic and npm's lockfile staleness checks ([GitHub Actions cache invalidation](https://notes.kodekloud.com/docs/GitHub-Actions-Certification/Continuous-Integration-with-GitHub-Actions/Invalidate-Cache))

### Pattern 3: Foundation Image ID Tracking for Extension Cache

**What:** Update extension cache invalidation to reference foundation image ID instead of base image ID

**When to use:** Extension Dockerfiles use `FROM aishell:foundation` (not `FROM aishell:base`)

**Implementation:**

```clojure
;; extension.clj - Updated cache invalidation (CACHE-01)
(def foundation-image-id-label "aishell.foundation.id") ; Changed from base-image-id-label

(defn needs-extended-rebuild?
  "Check if extended image needs rebuilding.
   Now tracks foundation image ID instead of base image ID."
  [extended-tag foundation-image-tag project-dir]
  (cond
    (not (docker/image-exists? extended-tag))
    true

    :else
    (let [stored-foundation-id (docker/get-image-label extended-tag foundation-image-id-label)
          current-foundation-id (get-base-image-id foundation-image-tag)] ; Renamed but same logic
      (or (not= stored-foundation-id current-foundation-id)
          ;; Also check extension Dockerfile hash
          (let [stored-hash (docker/get-image-label extended-tag extension-hash-label)
                current-hash (get-extension-dockerfile-hash project-dir)]
            (not= stored-hash current-hash))))))

;; build-extended-image now labels with foundation ID
(defn build-extended-image [{:keys [foundation-tag extended-tag ...]}]
  (let [foundation-id (get-base-image-id foundation-tag)
        build-args [...
                    (str "--label=" foundation-image-id-label "=" foundation-id)
                    ...]]
    ;; Build extension
    ))
```

**Migration behavior (MIGR-02):** Existing extensions have `aishell.base.id` label pointing to old `aishell:base` image ID. After upgrade, foundation image has new ID, so all extensions auto-rebuild on first build (desired behavior - forces clean slate).

**Source:** Existing pattern in `extension.clj` lines 92-130, adapted to track foundation instead of base

### Pattern 4: Transparent Build Command Flow

**What:** Coordinate foundation image build and harness volume population in single `aishell build` command

**When to use:** User runs `aishell build --with-claude` (BUILD-01 requirement)

**Implementation:**

```clojure
;; cli.clj - Updated build handler
(defn handle-build [{:keys [opts]}]
  (let [;; Parse harness flags
        claude-config (parse-with-flag (:with-claude opts))
        ;; ... other harnesses ...

        ;; Step 1: Build foundation image (only if Dockerfile changed)
        foundation-result (build/build-foundation-image
                            {:with-gitleaks with-gitleaks
                             :verbose (:verbose opts)
                             :force (:force opts)})
        foundation-tag (:image foundation-result)

        ;; Step 2: Compute harness volume hash
        state-for-hash {:with-claude (:enabled? claude-config)
                        :claude-version (:version claude-config)
                        ;; ... other harnesses ...
                        }
        harness-hash (vol/compute-harness-hash state-for-hash)
        volume-name (vol/volume-name harness-hash)

        ;; Step 3: Populate volume if needed (lazy - only if missing or stale)
        _ (when (or (not (vol/volume-exists? volume-name))
                    (not= (vol/get-volume-label volume-name "aishell.harness.hash")
                          harness-hash))
            (vol/create-volume volume-name {"aishell.harness.hash" harness-hash})
            (vol/populate-volume volume-name state-for-hash {:verbose (:verbose opts)}))]

    ;; Step 4: Write state with new schema fields
    (state/write-state
      {:with-claude (:enabled? claude-config)
       :claude-version (:version claude-config)
       ;; ... other harnesses ...
       :image-tag foundation-tag
       :dockerfile-hash (hash/compute-hash templates/base-dockerfile) ; Kept for compat
       :foundation-hash (hash/compute-hash templates/base-dockerfile)  ; New field
       :harness-volume-hash harness-hash                               ; New field
       :harness-volume-name volume-name                                ; New field
       :build-time (str (java.time.Instant/now))})))
```

**User experience:** `aishell build --with-claude` now transparently:
1. Builds foundation image (if Dockerfile changed) - ~60s
2. Creates harness volume (if doesn't exist) - instant
3. Populates volume with claude-code (if missing/stale) - ~90s
4. Writes state with all metadata

No separate commands needed. Future runs skip steps 1-3 if cached.

### Pattern 5: Hash-Based Staleness Detection

**What:** Compare stored hash in volume label against current harness configuration to detect staleness

**When to use:** Every container run (pre-flight check), to decide if volume needs repopulation

**Implementation:**

```clojure
;; volume.clj - Staleness detection
(defn volume-stale?
  "Check if volume needs repopulation.
   Returns true if:
   - Volume doesn't exist
   - Stored hash doesn't match current config hash"
  [volume-name expected-hash]
  (or (not (volume-exists? volume-name))
      (not= (get-volume-label volume-name "aishell.harness.hash")
            expected-hash)))

;; Usage in run.clj
(let [state (state/read-state)
      expected-hash (vol/compute-harness-hash state)
      volume-name (or (:harness-volume-name state)
                      (vol/volume-name expected-hash))]
  (when (vol/volume-stale? volume-name expected-hash)
    (println "Harness configuration changed, updating volume...")
    (vol/populate-volume volume-name state)))
```

**Performance:** Hash comparison is instant. Label read via `docker volume inspect` is ~10ms. No filesystem inspection needed.

**Source pattern:** [GitHub Actions cache invalidation](https://notes.kodekloud.com/docs/GitHub-Actions/Continuous-Integration-with-GitHub-Actions/Cache-Node-Dependencies) uses `hashFiles('package-lock.json')` for cache keys, same principle

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| Versioned migration files | Custom migration runner with .edn migration definitions | Additive schema + nil-safe reading | EDN is schema-flexible, nil-safe reading handles missing fields naturally, no migration runner needed |
| State file version tracking | `:schema-version` field with migration logic | Presence/absence of fields | Clojure idiom: check for field existence with `(contains? state :new-field)` or use nil default |
| Volume content verification | Hash entire volume filesystem | Docker volume labels with config hash | Labels are first-class metadata, faster than filesystem inspection |
| Build orchestration state machine | Complex workflow with intermediate states | Sequential build steps with early returns | Simpler code, easier to debug, matches existing aishell patterns |
| Graceful degradation on missing volumes | Try-catch around docker run | Pre-flight volume existence check | Fail-fast with clear error message better UX than obscure Docker error |

**Key insight:** Clojure's nil-safe map reading and EDN's flexible schema make formal migration systems unnecessary. Additive changes (new fields default to nil) and presence checks (`(contains? ...)`) provide natural backward compatibility. This aligns with Docker's approach to [API versioning](https://docs.docker.com/reference/api/engine/) (soft deprecation, not hard breaks).

## Common Pitfalls

### Pitfall 1: Breaking Old State Files

**What goes wrong:** New code requires new fields to exist, crashes when reading v2.7.0 state files

**Why it happens:** Assuming all state files have v2.8.0 schema fields

**How to avoid:**

```clojure
;; WRONG: Assumes field exists
(let [volume-hash (:harness-volume-hash state)]
  (vol/volume-name volume-hash)) ; NPE if state from v2.7.0

;; CORRECT: Nil-safe with fallback
(let [volume-hash (:harness-volume-hash state)]
  (when volume-hash
    (vol/volume-name volume-hash)))
;; Or compute on-the-fly if missing
(let [volume-hash (or (:harness-volume-hash state)
                      (vol/compute-harness-hash state))]
  (vol/volume-name volume-hash))
```

**Warning signs:** Errors on first run after upgrade, NPEs in state reading code

**Source:** [Clojure best practices](https://clojurepatterns.com/10/28/8/) - schema migration via additive changes

### Pitfall 2: Race Condition in Lazy Volume Population

**What goes wrong:** Two containers with same harness config start simultaneously, both try to populate same volume, corruption

**Why it happens:** No locking during `ensure-harness-volume-ready`

**How to avoid:** Docker volume creation is atomic. Use create-then-check pattern:

```clojure
;; WRONG: Check then create (race window)
(when-not (vol/volume-exists? volume-name)
  (vol/create-volume volume-name labels)
  (vol/populate-volume volume-name state))

;; CORRECT: Try create, catch if exists
(defn ensure-volume-populated [volume-name state]
  (try
    ;; Try to create (atomic operation)
    (vol/create-volume volume-name {"aishell.harness.hash" expected-hash})
    (vol/populate-volume volume-name state) ; Only first creator populates
    (catch Exception e
      ;; Volume already exists (another process created it)
      ;; Check if populated (label exists = populated)
      (when-not (vol/get-volume-label volume-name "aishell.harness.hash")
        ;; Race: volume created but not populated yet, retry after delay
        (Thread/sleep 1000)
        (when-not (vol/get-volume-label volume-name "aishell.harness.hash")
          (throw (ex-info "Volume exists but not populated" {})))))))
```

**Alternative (simpler):** Accept that concurrent populates are rare, let Docker's layer cache make duplicate npm installs fast (~5s instead of 90s). First container wins, second just re-installs same packages (idempotent).

**Warning signs:** Intermittent "module not found" errors, corrupted node_modules

**Mitigation:** Phase 36 already implements volume population via temporary container - these are isolated, so concurrent populates to same volume are safe (Docker handles concurrency).

### Pitfall 3: Extension Cache False Negative After Upgrade

**What goes wrong:** Extensions don't rebuild after upgrade even though foundation ID changed (MIGR-02 requirement violated)

**Why it happens:** Extension has `aishell.base.id` label, code looks for `aishell.foundation.id` label

**How to avoid:** Fallback to old label name during migration period:

```clojure
(defn needs-extended-rebuild? [extended-tag foundation-tag project-dir]
  (let [stored-id (or (docker/get-image-label extended-tag "aishell.foundation.id")
                      (docker/get-image-label extended-tag "aishell.base.id")) ; Fallback
        current-id (get-base-image-id foundation-tag)]
    (not= stored-id current-id)))
```

**Better approach:** Force rebuild on first build after upgrade by checking for new label absence:

```clojure
(defn needs-extended-rebuild? [extended-tag foundation-tag project-dir]
  (let [stored-id (docker/get-image-label extended-tag "aishell.foundation.id")]
    (or (nil? stored-id) ; No new label = old extension, force rebuild
        (not= stored-id (get-base-image-id foundation-tag)))))
```

**Warning signs:** Extensions not rebuilding after upgrade, using old base image

**Source pattern:** Similar to Docker's [soft deprecation](https://docs.docker.com/engine/deprecated/) approach

### Pitfall 4: Forgetting to Update Dockerfile-Hash Fallback

**What goes wrong:** Foundation hash computation uses different logic than original dockerfile-hash, breaks cache

**Why it happens:** Copy-paste error, different template content

**How to avoid:** Both hashes should compute from same source:

```clojure
;; build.clj - Foundation hash
(defn get-foundation-dockerfile-hash []
  (hash/compute-hash templates/base-dockerfile)) ; Same template

;; cli.clj - State writing
(state/write-state
  {:dockerfile-hash (hash/compute-hash templates/base-dockerfile) ; Same
   :foundation-hash (hash/compute-hash templates/base-dockerfile) ; Same
   ;; Both point to same template, so hashes are identical (for now)
   })
```

**Future-proofing:** When foundation template diverges from base template (Phase 38+), update both hash computations to use foundation template.

**Warning signs:** Foundation image rebuilding unnecessarily on every build

### Pitfall 5: State Stale After Volume Cleanup

**What goes wrong:** User manually deletes volume, state still references it, container fails to start

**Why it happens:** State file not synchronized with Docker volume state

**How to avoid:** Pre-flight existence check before docker run:

```clojure
(defn run-container [cmd harness-args opts]
  (let [state (state/read-state)
        volume-name (:harness-volume-name state)]
    ;; Verify volume exists, repopulate if missing
    (when (and volume-name (not (vol/volume-exists? volume-name)))
      (println "Harness volume missing, repopulating...")
      (vol/create-volume volume-name {"aishell.harness.hash" (:harness-volume-hash state)})
      (vol/populate-volume volume-name state))
    ;; Proceed with docker run
    ))
```

**Alternative:** Always compute volume name from state (don't store in state), re-check existence every run. Slightly slower but more robust.

**Warning signs:** "Volume not found" Docker errors, containers failing to start

## Code Examples

Verified patterns from investigation artifacts and official sources:

### State Schema Migration

```clojure
;; src/aishell/state.clj - Updated schema documentation
(defn write-state
  "Write state to file, creating directory if needed.

   State schema (v2.8.0):
   {:with-claude true
    :with-opencode false
    :with-codex true
    :with-gemini false
    :with-gitleaks true
    :claude-version \"2.0.22\"
    :opencode-version nil
    :codex-version \"0.89.0\"
    :gemini-version nil
    :image-tag \"aishell:foundation\"
    :build-time \"2026-02-01T12:34:56Z\"
    :dockerfile-hash \"abc123def456\"        ; DEPRECATED: Use :foundation-hash (kept for v2.7.0 compat)
    :foundation-hash \"abc123def456\"        ; NEW: Hash of foundation Dockerfile template
    :harness-volume-hash \"def789ghi012\"    ; NEW: Hash of enabled harnesses + versions
    :harness-volume-name \"aishell-harness-def789ghi012\"} ; NEW: Docker volume name for runtime mounting"
  [state]
  (let [path (state-file)]
    (util/ensure-dir (util/config-dir))
    (spit path (pr-str state))))

;; Reading with backward compatibility
(defn read-state []
  (let [path (state-file)]
    (when (fs/exists? path)
      (edn/read-string (slurp path)))))
;; Returns map with whatever fields exist - missing fields return nil on access
```

**Source:** Existing `state.clj` lines 9-41, extended with new fields

### Lazy Volume Population in run.clj

```clojure
;; src/aishell/run.clj - Pre-flight volume check
(ns aishell.run
  (:require ;; ... existing requires ...
            [aishell.docker.volume :as vol]))

(defn- ensure-harness-volume
  "Ensure harness volume exists and is up-to-date.
   Populates lazily if missing or stale (hash mismatch).
   Returns volume name for docker run mounting."
  [state]
  (when (some #(get state %) [:with-claude :with-opencode :with-codex :with-gemini])
    (let [expected-hash (vol/compute-harness-hash state)
          volume-name (or (:harness-volume-name state) ; Prefer stored name
                          (vol/volume-name expected-hash)) ; Compute if missing (v2.7.0 upgrade)
          stored-hash (when (vol/volume-exists? volume-name)
                        (vol/get-volume-label volume-name "aishell.harness.hash"))]
      (cond
        ;; Volume missing - create and populate
        (not (vol/volume-exists? volume-name))
        (do
          (output/info "Creating harness volume...")
          (vol/create-volume volume-name {"aishell.harness.hash" expected-hash
                                          "aishell.harness.version" "2.8.0"})
          (vol/populate-volume volume-name state)
          volume-name)

        ;; Volume exists but stale (hash mismatch)
        (not= stored-hash expected-hash)
        (do
          (output/info "Harness configuration changed, updating volume...")
          (vol/populate-volume volume-name state)
          volume-name)

        ;; Volume fresh - no action
        :else
        volume-name))))

(defn run-container [cmd harness-args & [opts]]
  (docker/check-docker!)
  (let [state (state/read-state)]
    (when-not state
      (output/error-no-build))

    (let [project-dir (System/getProperty "user.dir")
          base-tag (or (:image-tag state) "aishell:base") ; Fallback for v2.7.0

          ;; NEW: Ensure harness volume ready before container run (HVOL-04, HVOL-05)
          harness-volume-name (ensure-harness-volume state)

          ;; Existing code for extension resolution, config, git identity...
          image-tag (resolve-image-tag base-tag project-dir false)
          cfg (config/load-config project-dir)
          git-id (docker-run/read-git-identity project-dir)

          ;; Build docker args WITH harness volume mount
          docker-args (docker-run/build-docker-args
                        {:project-dir project-dir
                         :image-tag image-tag
                         :config cfg
                         :git-identity git-id
                         :harness-volume-name harness-volume-name ; NEW parameter
                         ;; ... existing opts ...
                         })]
      ;; Execute container as before
      (apply p/exec (concat docker-args container-cmd)))))
```

**Source pattern:** Adapted from existing `run-container` function (run.clj lines 61-223), extended with volume pre-flight

### Extension Cache Invalidation Update

```clojure
;; src/aishell/docker/extension.clj - Updated for foundation tracking (CACHE-01)
(ns aishell.docker.extension
  ;; ... existing requires ...
  )

;; Updated label constant
(def foundation-image-id-label "aishell.foundation.id") ; Changed from "aishell.base.id"
(def extension-hash-label "aishell.extension.hash")

(defn get-foundation-image-id
  "Get Docker image ID for foundation image.
   Renamed from get-base-image-id but logic unchanged."
  [foundation-tag]
  (try
    (let [{:keys [exit out]}
          (p/shell {:out :string :err :string :continue true}
                   "docker" "inspect" "--format={{.Id}}" foundation-tag)]
      (when (zero? exit)
        (str/trim out)))
    (catch Exception _ nil)))

(defn needs-extended-rebuild?
  "Check if extended image needs rebuilding.
   Now tracks foundation image ID instead of base image ID (CACHE-01)."
  ([extended-tag foundation-tag]
   (needs-extended-rebuild? extended-tag foundation-tag nil))
  ([extended-tag foundation-tag project-dir]
   (cond
     (not (docker/image-exists? extended-tag))
     true

     :else
     (let [stored-foundation-id (docker/get-image-label extended-tag foundation-image-id-label)
           current-foundation-id (get-foundation-image-id foundation-tag)]
       (cond
         ;; Foundation image changed OR no label (old extension) - rebuild (MIGR-02)
         (or (nil? stored-foundation-id)
             (not= stored-foundation-id current-foundation-id))
         true

         ;; Check extension Dockerfile hash if project-dir provided
         (and project-dir (project-dockerfile project-dir))
         (let [stored-hash (docker/get-image-label extended-tag extension-hash-label)
               current-hash (get-extension-dockerfile-hash project-dir)]
           (not= stored-hash current-hash))

         :else false)))))

(defn build-extended-image
  "Build extended image from project Dockerfile.
   Now labels with foundation image ID instead of base image ID."
  [{:keys [project-dir foundation-tag extended-tag force verbose]}]
  (when-let [dockerfile-path (project-dockerfile project-dir)]
    (let [foundation-id (get-foundation-image-id foundation-tag)
          extension-hash (get-extension-dockerfile-hash project-dir)
          build-args (cond-> ["-f" dockerfile-path
                              "-t" extended-tag
                              (str "--label=" foundation-image-id-label "=" foundation-id) ; Updated label
                              (str "--label=" extension-hash-label "=" extension-hash)]
                       force (conj "--no-cache")
                       verbose (conj "--progress=plain"))]
      ;; Build logic unchanged
      )))
```

**Source:** Existing `extension.clj` lines 1-174, updated label names and function signatures

### Transparent Build Integration

```clojure
;; src/aishell/cli.clj - Updated handle-build (BUILD-01)
(defn handle-build [{:keys [opts]}]
  (if (:help opts)
    (print-build-help)
    (let [;; Parse harness flags (existing)
          claude-config (parse-with-flag (:with-claude opts))
          opencode-config (parse-with-flag (:with-opencode opts))
          codex-config (parse-with-flag (:with-codex opts))
          gemini-config (parse-with-flag (:with-gemini opts))
          with-gitleaks (not (:without-gitleaks opts))

          ;; Validate versions (existing)
          _ (validate-version (:version claude-config) "Claude Code")
          _ (validate-version (:version opencode-config) "OpenCode")
          _ (validate-version (:version codex-config) "Codex")
          _ (validate-version (:version gemini-config) "Gemini")

          ;; Step 1: Build foundation image (updated - was build-base-image)
          _ (when (docker/image-exists? build/foundation-image-tag)
              (println "Replacing existing image..."))
          foundation-result (build/build-foundation-image
                              {:with-gitleaks with-gitleaks
                               :verbose (:verbose opts)
                               :force (:force opts)})
          foundation-tag (:image foundation-result)

          ;; Step 2: Prepare state map for hash computation
          state-map {:with-claude (:enabled? claude-config)
                     :with-opencode (:enabled? opencode-config)
                     :with-codex (:enabled? codex-config)
                     :with-gemini (:enabled? gemini-config)
                     :with-gitleaks with-gitleaks
                     :claude-version (:version claude-config)
                     :opencode-version (:version opencode-config)
                     :codex-version (:version codex-config)
                     :gemini-version (:version gemini-config)}

          ;; Step 3: Compute harness volume hash and create volume (NEW)
          harness-hash (vol/compute-harness-hash state-map)
          volume-name (vol/volume-name harness-hash)

          ;; Step 4: Populate volume if needed (NEW - lazy, only if missing/stale)
          _ (when (or (not (vol/volume-exists? volume-name))
                      (not= (vol/get-volume-label volume-name "aishell.harness.hash")
                            harness-hash))
              (when-not (vol/volume-exists? volume-name)
                (vol/create-volume volume-name {"aishell.harness.hash" harness-hash
                                                "aishell.harness.version" "2.8.0"}))
              (vol/populate-volume volume-name state-map {:verbose (:verbose opts)}))]

      ;; Step 5: Write state with NEW schema fields (CACHE-02)
      (state/write-state
        (assoc state-map
               :image-tag foundation-tag
               :build-time (str (java.time.Instant/now))
               :dockerfile-hash (hash/compute-hash templates/base-dockerfile)    ; Kept for v2.7.0 compat
               :foundation-hash (hash/compute-hash templates/base-dockerfile)    ; NEW
               :harness-volume-hash harness-hash                                 ; NEW
               :harness-volume-name volume-name)))))                             ; NEW
```

**Source:** Existing `handle-build` function (cli.clj lines 148-187), extended with volume steps

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| Single :dockerfile-hash | :foundation-hash + :harness-volume-hash | v2.8.0 (Phase 37) | Independent cache invalidation for foundation vs harnesses |
| Base image referenced by extensions | Foundation image referenced by extensions | v2.8.0 (Phase 37) | Extensions rebuild on foundation change, not harness change |
| Build-time harness installation | Lazy runtime harness population | v2.8.0 (Phase 37) | Faster builds (foundation only), populate on first run |
| Versioned migration files | Additive schema + nil-safe reading | v2.8.0 (Phase 37) | Zero-config upgrades, no migration runner needed |

**Deprecated/outdated:**
- `:dockerfile-hash` as primary cache key: Now use `:foundation-hash` for foundation, `:harness-volume-hash` for volumes (`:dockerfile-hash` kept for v2.7.0 compatibility)
- `aishell.base.id` label in extensions: Now `aishell.foundation.id` (fallback to old label for one release cycle)
- Build-time volume population: Now lazy (on first run or staleness detection)

**Emerging patterns (2026):**
- Hash-based cache invalidation: [GitHub Actions](https://notes.kodekloud.com/docs/GitHub-Actions-Certification/Continuous-Integration-with-GitHub-Actions/Invalidate-Cache) uses `hashFiles('package-lock.json')` in cache keys
- Additive schema migration: [Clojure patterns](https://clojurepatterns.com/10/28/8/) recommend adding fields without removing old ones
- Soft deprecation: [Docker's approach](https://docs.docker.com/engine/deprecated/) to maintaining backward compatibility

## Open Questions

### 1. Volume cleanup trigger during development

**What we know:** Phase 37 implements lazy population and staleness detection but not cleanup. Orphaned volumes accumulate (~500MB each) as users experiment with harness combinations.

**What's unclear:** Should cleanup be part of Phase 37 (BUILD-01) or deferred to Phase 38 (CLEAN-01, CLEAN-02)?

**Recommendation:** Defer to Phase 38. Phase 37 focuses on core migration and lazy population. Cleanup is a separate concern (requires age tracking, user confirmation, orphan detection). During development, users can manually `docker volume prune` if needed.

**Validation approach:** Monitor volume count during Phase 37 testing. If >5 volumes accumulate quickly, prioritize cleanup in Phase 38.

### 2. State file location change for multi-project scenarios

**What we know:** Current state is global (`~/.aishell/state.edn`), tracks single build configuration. With harness volumes, different projects can use different harness combinations.

**What's unclear:** Should state be per-project (`{project}/.aishell/state.edn`) or remain global with multi-config support?

**Recommendation:** Keep global state for Phase 37. The state tracks foundation build (global) + last harness combination (also reasonable to be global - most users have one primary setup). If multi-project needs emerge, Phase 38+ can add per-project state overlay.

**Mitigation:** Volume names are already per-harness-config (hash-based), so different projects using different harnesses get different volumes automatically. State file is mostly informational (avoid rebuilds), not functional dependency.

### 3. Error handling for corrupted volume labels

**What we know:** Staleness detection reads `aishell.harness.hash` label from volumes. If label is missing (manual editing, corruption), behavior is undefined.

**What's unclear:** Should missing label trigger repopulation (safe but slow) or error (fast but might break working volumes)?

**Recommendation:** Treat missing label as stale (trigger repopulation). Conservative approach prevents "volume works but not detected" issues. User can always `docker volume rm` if repopulation is unwanted.

**Validation approach:** Test with manually created volume (no labels), verify repopulation triggers and succeeds.

### 4. Extension rebuild cascade after foundation change

**What we know:** MIGR-02 requires extensions auto-rebuild on first build after upgrade (foundation ID changed). This is correct (forces clean slate) but potentially slow if user has many projects with extensions.

**What's unclear:** Should we warn user before rebuilding all extensions, or just rebuild silently?

**Recommendation:** Silent rebuild for Phase 37 (matches existing extension auto-rebuild behavior). If performance issues emerge, Phase 38+ can add progress feedback or parallel builds.

**Mitigation:** Extensions rebuild in parallel if user runs `aishell build` in multiple projects simultaneously (Docker handles concurrent builds). Most users have 1-2 extensions, rebuild is ~30s each.

## Sources

### Primary (HIGH confidence)

- [Docker API backward compatibility](https://docs.docker.com/reference/api/engine/) - Versioned API, soft deprecation patterns
- [Docker deprecated features](https://docs.docker.com/engine/deprecated/) - Migration approach examples
- [EDN specification](https://github.com/edn-format/edn) - Format flexibility, no schema enforcement
- [Clojure schema migration patterns](https://clojurepatterns.com/10/28/8/) - Additive schema changes
- [GitHub Actions cache invalidation](https://notes.kodekloud.com/docs/GitHub-Actions-Certification/Continuous-Integration-with-GitHub-Actions/Invalidate-Cache) - Hash-based cache keys
- [npm cache patterns](https://notes.kodekloud.com/docs/GitHub-Actions/Continuous-Integration-with-GitHub-Actions/Cache-Node-Dependencies) - Lockfile hash invalidation
- [Investigation Report: Volume-Based Tool Injection](file:///home/jonasrodrigues/projects/harness/artifacts/investigate/20260131-1807-decoupling-harness-tools-from-docker-extensions/volume-based-injection-raw.md) - Lazy population patterns
- [Investigation Report: Layer Inversion Architecture](file:///home/jonasrodrigues/projects/harness/artifacts/investigate/20260131-1807-decoupling-harness-tools-from-docker-extensions/layer-inversion-architecture-raw.md) - State schema design, foundation-hash separation
- [aishell codebase](file:///home/jonasrodrigues/projects/harness/src/aishell/) - Existing state.clj, extension.clj, run.clj patterns

### Secondary (MEDIUM confidence)

- [Docker Compose migration guide](https://docs.docker.com/compose/releases/migrate/) - Backward compatibility strategies
- [Cascading cache invalidation](https://philipwalton.com/articles/cascading-cache-invalidation/) - Hash-based dependency tracking
- [Phase 36 Research](file:///home/jonasrodrigues/projects/harness/.planning/phases/36-harness-volume-core/36-RESEARCH.md) - Volume population, hash computation patterns

### Tertiary (LOW confidence)

- [Migratus migration library](https://github.com/yogthos/migratus) - Clojure database migrations (not directly applicable but pattern reference)
- [CircleCI cache discussion](https://discuss.circleci.com/t/destroy-npm-cache-node-modules-based-on-a-hash-of-package-json-or-if-present-npm-shrinkwrap-json/2985) - Hash-based cache invalidation community patterns

## Metadata

**Confidence breakdown:**
- State schema migration: HIGH - EDN flexibility documented, additive pattern is standard Clojure practice
- Lazy volume population: HIGH - Pattern implemented in Phase 36, extension to run.clj is straightforward
- Extension cache invalidation: HIGH - Direct adaptation of existing extension.clj logic (lines 92-130)
- Build integration: HIGH - Existing cli.clj handle-build provides clear integration point
- Hash-based staleness: HIGH - Phase 36 implemented hash computation, label reading is existing Docker feature
- Race condition handling: MEDIUM - Docker volume create atomicity is documented, concurrent npm installs need validation
- Migration UX: MEDIUM - Zero-downtime upgrade assumption needs testing with real v2.7.0 -> v2.8.0 upgrade

**Research date:** 2026-02-01
**Valid until:** 2026-03-15 (45 days - implementation phase, stable technologies)

**Phase 37 specific notes:**
- Implementation builds directly on Phase 36 volume infrastructure (hash computation, population, mounting)
- State schema changes are additive (backward compatible by design)
- Extension cache update requires careful testing (MIGR-02 auto-rebuild requirement)
- Build integration coordinates foundation build + volume population transparently (BUILD-01)
- Focus: Integration and migration, not new primitives (Phase 36 provided primitives)
