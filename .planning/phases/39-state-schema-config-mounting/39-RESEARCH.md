# Phase 39: State Schema & Config Mounting - Research

**Researched:** 2026-02-02
**Domain:** State management, configuration schema, file mounting patterns
**Confidence:** HIGH

## Summary

Phase 39 establishes the opt-in flag for tmux and mounts user tmux configuration into the container. This is a foundational phase that requires extending existing state and config schemas without breaking backward compatibility. The standard approach uses Clojure's EDN for state persistence with optional validation via Plumatic Schema or clojure.spec, YAML for user-facing configuration with clj-yaml parsing, and Docker bind mounts with read-only flags for config files.

Research focused on three critical domains:
1. **State schema evolution**: Adding `:with-tmux` boolean to existing state.edn without migration
2. **Config schema extension**: Adding optional `tmux:` section to config.yaml with validation
3. **Mount patterns**: Read-only mounting of `~/.tmux.conf` with graceful handling of missing files

The implementation follows established patterns in the codebase: state.clj already handles schema evolution via nil-safe reads, config.clj already validates and merges YAML configs, and docker/run.clj already mounts config files conditionally.

**Primary recommendation:** Extend existing patterns rather than introducing new abstractions. Add `:with-tmux` to state schema with default `true` for backward compatibility, add `tmux:` section to config with scalar replacement merge strategy, and mount `~/.tmux.conf` read-only using existing mount-building patterns.

## Standard Stack

The established libraries/tools for this domain:

### Core
| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| clojure.edn | 1.11+ (stdlib) | State file persistence | Native Clojure data notation, already used in state.clj |
| clj-yaml | 1.0.27 | Config file parsing | Already used in config.clj, de facto YAML for Clojure |
| babashka.fs | 0.5.20+ | File existence checks | Already used throughout, bb-native filesystem API |

### Supporting
| Library | Version | Purpose | When to Use |
|---------|---------|---------|-------------|
| Plumatic Schema | 1.4.1 | Runtime validation | Optional: if adding validation beyond manual checks |
| clojure.spec | 1.11+ (stdlib) | Generative testing | Optional: if adding property-based tests for state |

### Alternatives Considered
| Instead of | Could Use | Tradeoff |
|------------|-----------|----------|
| EDN state | JSON/YAML state | EDN preserves Clojure types (keywords, symbols), JSON requires conversion |
| clj-yaml | clj-commons/clj-yaml | clj-yaml is actively maintained, used in project |
| Manual validation | Schema/Spec | Manual is sufficient for simple schema additions |

**Installation:**

No new dependencies required. All libraries already in project deps.

## Architecture Patterns

### Recommended Project Structure

No changes to project structure. Modifications to existing files:

```
src/aishell/
├── state.clj          # Add :with-tmux to schema docs
├── config.clj         # Add :tmux validation, merge strategy
└── docker/
    └── run.clj        # Add tmux config mount function
```

### Pattern 1: Additive State Schema Evolution

**What:** Extend existing EDN state map with new optional keys. Readers tolerate missing keys via nil-safe accessors.

**When to use:** Adding new build flags that default to safe values

**Example:**
```clojure
;; state.clj - Schema documentation (line 25-40)
;; Before (v2.8.0):
{:with-claude true            ; boolean
 :with-gitleaks true          ; boolean
 :foundation-hash "abc123"    ; 12-char SHA-256
 :harness-volume-hash "def456"}

;; After (v2.9.0):
{:with-claude true            ; boolean
 :with-gitleaks true          ; boolean
 :with-tmux true              ; boolean (whether tmux enabled) - NEW
 :foundation-hash "abc123"    ; 12-char SHA-256
 :harness-volume-hash "def456"}

;; Consumer code uses safe defaults:
(get state :with-tmux true)  ; Default to true if missing (backward compat)
```

**Why this works:**
- EDN readers ignore unknown keys (forward compat)
- Nil/missing keys handled by consumers with defaults (backward compat)
- No migration code needed
- State file remains human-readable

**Source:** [Clojure: Values and Change - Identity and State](https://clojure.org/about/state)

---

### Pattern 2: YAML Config Section with Validation

**What:** Add optional top-level section to config.yaml schema. Validate structure on load with helpful warnings.

**When to use:** Adding user-configurable features with multiple related settings

**Example:**
```clojure
;; config.clj - Add to known-keys (line 11)
(def known-keys
  #{:mounts :env :ports :docker_args :pre_start :extends
    :harness_args :gitleaks_freshness_check :detection
    :tmux})  ; NEW

;; Validation function (new, after line 109)
(defn validate-tmux-config
  "Validate tmux config structure. Warns on invalid format."
  [tmux-config source-path]
  (when tmux-config
    ;; Example: Validate plugins is list of strings
    (when-let [plugins (:plugins tmux-config)]
      (when-not (and (sequential? plugins)
                    (every? string? plugins))
        (output/warn (str "Invalid tmux.plugins in " source-path
                         ": expected list of strings"))))))

;; User's config.yaml:
tmux:
  plugins:
    - tmux-plugins/tmux-sensible
    - tmux-plugins/tmux-resurrect
```

**Why this pattern:**
- User-facing YAML remains clean and intuitive
- Validation provides early feedback
- Optional sections gracefully absent
- Follows existing detection: and harness_args: patterns

**Source:** Project's existing config.clj validation patterns

---

### Pattern 3: Conditional Read-Only File Mounting

**What:** Mount host file into container only if it exists. Use `:ro` flag to prevent container writes.

**When to use:** Mounting user config files that may or may not exist

**Example:**
```clojure
;; docker/run.clj - New mount function (insert after line 166)
(defn- build-tmux-config-mount
  "Build mount args for ~/.tmux.conf if it exists.
   Mounts read-only to prevent container from modifying host config.

   Returns ['-v' 'host-path:container-path:ro'] or empty vector."
  [state]
  (when (get state :with-tmux true)
    (let [home (util/get-home)
          host-path (str home "/.tmux.conf")
          container-path (str home "/.tmux.conf")]
      (when (fs/exists? host-path)
        ["-v" (str host-path ":" container-path ":ro")]))))

;; Usage in build-docker-args-internal (after line 233):
(into (or (build-tmux-config-mount state) []))
```

**Why read-only:**
- Config is user-owned on host
- Prevents accidental corruption from container processes
- Matches pattern for other config mounts (gitconfig, etc.)
- Docker best practice for config files

**Why graceful absence:**
- Not all users have .tmux.conf
- Missing file should not block container start
- User can add later, rebuild to mount

**Sources:**
- [Docker Bind Mounts - Official Docs](https://docs.docker.com/engine/storage/bind-mounts/)
- [Docker Read-Only Mount Best Practices](https://www.absh.dev/docker-read-only-mount/)
- [Mounting Single Files in Docker](https://www.baeldung.com/ops/docker-mount-single-file-in-volume)

---

### Pattern 4: Scalar Replacement Config Merge

**What:** Project config fully replaces global config for scalar/map sections, not concatenate.

**When to use:** User preferences that are "either global OR project", not composable

**Example:**
```clojure
;; config.clj - Add :tmux to scalar-keys (line 154)
(let [scalar-keys #{:pre_start :gitleaks_freshness_check :tmux}]  ; Add :tmux
  ...)

;; Behavior:
;; Global config:
tmux:
  plugins:
    - tmux-plugins/tmux-sensible

;; Project config:
tmux:
  plugins:
    - tmux-plugins/tmux-resurrect

;; Merged result: project replaces global entirely
tmux:
  plugins:
    - tmux-plugins/tmux-resurrect  ; Global tmux-sensible NOT included
```

**Why scalar, not list concat:**
- Plugin lists are user preferences, not composable
- User wants "my plugins" not "global + mine"
- Resurrect config is per-environment, not additive
- Matches existing `:pre_start` merge behavior

**Alternative:** List concatenation like `:mounts` (add both global + project plugins)
- **Rejected:** Plugin conflicts, unexpected plugin combinations, unclear precedence

**Source:** Project's existing merge-configs function (config.clj line 142-205)

---

### Anti-Patterns to Avoid

- **Migrating existing state files:** EDN schema evolution handles missing keys naturally. Don't write migration code.
- **Required config sections:** Make tmux: optional. Missing section means "no custom config", use defaults.
- **Write-enabled config mounts:** Always use `:ro` for config files. Prevents container from corrupting host configs.
- **Failing on missing .tmux.conf:** Gracefully skip mount if file doesn't exist. User may not use tmux customization.

## Don't Hand-Roll

Problems that look simple but have existing solutions:

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| EDN schema validation | Custom validators | clojure.spec or Plumatic Schema | Spec handles coercion, error messages, generative testing |
| YAML parsing edge cases | Manual key checks | clj-yaml with validation functions | Handles anchors, aliases, type coercion edge cases |
| Path expansion (~, $HOME) | String replacement | util/expand-path (existing) | Handles shell variable expansion, tilde in middle of path |
| File existence checking | java.io.File | babashka.fs/exists? | Cross-platform, handles symlinks correctly |

**Key insight:** The codebase already has robust patterns for state evolution, config validation, and mount construction. Extend existing functions rather than creating parallel implementations.

## Common Pitfalls

### Pitfall 1: Breaking Backward Compatibility on Read

**What goes wrong:** Old aishell versions fail when reading state.edn with new `:with-tmux` key.

**Why it happens:** EDN readers by default ignore unknown keys, but consumers might fail on unexpected structure if doing strict validation.

**How to avoid:**
- Don't add schema validation that rejects unknown keys
- Let consumers default missing keys: `(get state :with-tmux true)`
- Test with state.edn from v2.8.0 (no :with-tmux key)

**Warning signs:**
- "Unknown key :with-tmux" errors in consumers
- NullPointerException when accessing missing key
- Schema validation rejects state from older versions

**Source:** [Clojure State Management Patterns](https://dzone.com/articles/clojure-state-management)

---

### Pitfall 2: Mount Collision with Existing Config

**What goes wrong:** User has both `mounts: [~/.tmux.conf]` in config.yaml AND automatic tmux config mounting. Docker creates duplicate mount entries.

**Why it happens:** Two code paths create `-v ~/.tmux.conf:~/.tmux.conf` mounts independently.

**How to avoid:**
- Check if user explicitly mounted .tmux.conf in config
- Skip automatic mount if already in user mounts
- Document that tmux: section auto-mounts config

**Detection code:**
```clojure
(defn- user-mounted-tmux-config?
  "Check if user explicitly mounted .tmux.conf in config mounts"
  [config]
  (let [mounts (get config :mounts [])]
    (some #(str/includes? (str %) ".tmux.conf") mounts)))

;; In build-tmux-config-mount:
(when (and (get state :with-tmux true)
           (not (user-mounted-tmux-config? config)))
  ...)
```

---

### Pitfall 3: YAML Parsing Ambiguity

**What goes wrong:** User writes `tmux: true` (boolean) instead of `tmux:` (map). YAML parser accepts it, runtime fails.

**Why it happens:** YAML allows scalars at any key. Parser doesn't enforce structure until runtime access.

**How to avoid:**
- Validate tmux config type in validate-tmux-config
- Warn if tmux is not a map: "Expected map, got boolean"
- Provide example in config.yaml comments

**Prevention:**
```clojure
(defn validate-tmux-config [tmux-config source-path]
  (when tmux-config
    (when-not (map? tmux-config)
      (output/warn (str "Invalid tmux config in " source-path
                       ": expected map with 'plugins' and 'resurrect' keys"
                       ", got: " (type tmux-config))))))
```

---

### Pitfall 4: Mount Path Resolution Order

**What goes wrong:** User has `$HOME` set differently than where `~` expands. Mounted file appears in wrong location.

**Why it happens:** `~` expands via shell, `$HOME` is env variable. Docker doesn't expand either, receives literal paths.

**How to avoid:**
- Use util/expand-path for BOTH source and dest paths
- Verify expanded paths are absolute
- Test with users who have complex $HOME setups (NFS home dirs, etc.)

**Source:** Project's existing build-mount-args function (docker/run.clj line 32-55)

---

## Code Examples

Verified patterns from official sources:

### Read State with Default Value
```clojure
;; Source: Clojure core get function
;; Usage in any consumer of state (run.clj, attach.clj, etc.)
(let [state (state/read-state)]
  (when (get state :with-tmux true)  ; Default to true if missing
    ...))
```

### Validate Config Section Type
```clojure
;; Source: Project's validate-detection-config pattern (config.clj line 68-93)
(defn validate-tmux-config
  "Validate tmux config. Warns on invalid structure."
  [tmux-config source-path]
  (when tmux-config
    (when-not (map? tmux-config)
      (output/warn (str "Invalid tmux section in " source-path
                       ": expected map, got " (type tmux-config))))))
```

### Conditional Read-Only Mount
```clojure
;; Source: Docker bind mount docs + project's build-mount-args pattern
(defn- build-tmux-config-mount
  "Mount ~/.tmux.conf read-only if it exists."
  [state]
  (when (get state :with-tmux true)
    (let [home (util/get-home)
          host-path (str home "/.tmux.conf")]
      (when (fs/exists? host-path)
        ["-v" (str host-path ":" host-path ":ro")]))))
```

### Check File Exists Before Mount
```clojure
;; Source: babashka.fs documentation
(when (fs/exists? "/path/to/file")
  ;; Safe to mount
  ...)
```

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| Manual JSON/YAML for state | EDN with keyword keys | v1.0 (project start) | Type safety, native Clojure types |
| String-based config parsing | clj-yaml with validation | v2.0 | Structured validation, better errors |
| Always-mount configs | Conditional mounting | v2.9.0 (this phase) | Graceful handling of missing files |

**Deprecated/outdated:**
- N/A: This is a new feature, no deprecation

## Open Questions

Things that couldn't be fully resolved:

1. **Should :with-tmux default to true or false?**
   - What we know: Todo says "opt-in", existing behavior is always-on
   - What's unclear: "Opt-in" could mean "default false" or "user must choose"
   - Recommendation: Default TRUE for backward compat, explicit `--without-tmux` to disable

2. **Should attach command fail or warn when tmux disabled?**
   - What we know: Attach requires tmux session to exist
   - What's unclear: Hard error vs soft warning with guidance
   - Recommendation: Hard error with helpful message: "Rebuild with --with-tmux"

3. **Should config.yaml tmux: section be in this phase?**
   - What we know: Phase 39 success criteria mention flag + config mounting
   - What's unclear: Plugin configuration is Phase 41-42 scope
   - Recommendation: Add tmux: schema now, validate structure only. Plugin installation later.

## Sources

### Primary (HIGH confidence)
- [Clojure.org - Values and Change: Identity and State](https://clojure.org/about/state)
- [Docker Bind Mounts Documentation](https://docs.docker.com/engine/storage/bind-mounts/)
- [Babashka CLI Documentation](https://github.com/babashka/cli)
- Project's existing state.clj, config.clj, docker/run.clj implementations

### Secondary (MEDIUM confidence)
- [Plumatic Schema Documentation](http://plumatic.github.io/schema/)
- [Clojure State Management Patterns](https://dzone.com/articles/clojure-state-management)
- [Docker Read-Only Mount Best Practices](https://www.absh.dev/docker-read-only-mount/)
- [Mounting Single Files in Docker](https://www.baeldung.com/ops/docker-mount-single-file-in-volume)
- [Managing Developer Shell with Docker](https://bergie.iki.fi/blog/docker-developer-shell/)

### Tertiary (LOW confidence)
- [Dockerize Your Dotfiles](https://www.sainnhe.dev/post/dockerize-your-dotfiles/) - general patterns, not specific to this use case
- [Stuart Sierra's Component](https://github.com/stuartsierra/component) - state management library, overkill for this phase

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH - All libraries already in project, verified in use
- Architecture: HIGH - Extends existing patterns, no new abstractions
- Pitfalls: HIGH - Verified against existing codebase pitfalls (mount collisions, YAML ambiguity)

**Research date:** 2026-02-02
**Valid until:** 30 days (stable technologies, no fast-moving dependencies)
