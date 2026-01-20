# Phase 16: Run Commands - Research

**Researched:** 2026-01-20
**Domain:** Container execution, YAML config parsing, interactive process handling
**Confidence:** HIGH

## Summary

Phase 16 implements the "run" side of aishell - entering shells, launching Claude/OpenCode harnesses, and applying per-project configuration. The bash implementation at `aishell` (1,656 LOC) provides a complete reference. The Babashka implementation will use `clj-yaml.core` for YAML parsing and `babashka.process/exec` for process replacement.

Key findings:
1. `babashka.process/exec` replaces the current process (like bash `exec`), perfect for launching docker
2. `clj-yaml.core` is built into Babashka and parses YAML config naturally
3. Git identity is retrieved via shell commands to `git config`
4. Docker TTY handling is automatic with `-it` flags and `exec`

**Primary recommendation:** Use `p/exec` for docker run commands, `clj-yaml.core` for config parsing, and follow bash implementation patterns exactly for docker argument construction.

## Standard Stack

The established libraries/tools for this domain:

### Core
| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| babashka.process | built-in | Process execution, exec | Only option in Babashka |
| clj-yaml.core | built-in | YAML parsing | Built into Babashka, no deps needed |
| babashka.fs | built-in | File system operations | Path handling, exists checks |

### Supporting
| Library | Version | Purpose | When to Use |
|---------|---------|---------|-------------|
| clojure.string | built-in | String manipulation | Path expansion, trim |
| clojure.edn | built-in | EDN parsing | Reading state.edn (already used) |

### Alternatives Considered
| Instead of | Could Use | Tradeoff |
|------------|-----------|----------|
| clj-yaml.core | Manual parsing | YAML lib is built-in, no reason to hand-roll |
| p/exec | p/process | exec replaces process (correct), process returns |

**Installation:**
No additional dependencies - all libraries are built into Babashka.

## Architecture Patterns

### Recommended Project Structure
```
src/aishell/
├── run.clj           # New: run command logic (shell, claude, opencode)
├── config.clj        # New: YAML config loading and validation
├── docker/
│   └── run.clj       # New: docker run command construction
├── cli.clj           # Existing: add run command dispatch
├── state.clj         # Existing: read state for harness checks
└── util.clj          # Existing: path expansion utilities
```

### Pattern 1: Process Replacement with exec
**What:** Use `babashka.process/exec` to replace the Babashka process with Docker
**When to use:** All docker run commands (shell, claude, opencode)
**Example:**
```clojure
;; Source: Babashka built-in, verified in testing
(require '[babashka.process :as p])

;; Simple exec - replaces current process
(p/exec "docker" "run" "--rm" "-it" "aishell:base" "/bin/bash")

;; With options and args vector
(let [docker-args ["docker" "run" "--rm" "-it" "-v" "/path:/path" "aishell:base" "/bin/bash"]]
  (apply p/exec docker-args))

;; With environment variables
(p/exec {:extra-env {"MY_VAR" "value"}} "docker" "run" ...)
```

### Pattern 2: YAML Config Parsing
**What:** Parse .aishell/config.yaml using clj-yaml.core
**When to use:** Loading per-project configuration
**Example:**
```clojure
;; Source: Babashka built-in clj-yaml, verified in testing
(require '[clj-yaml.core :as yaml])
(require '[babashka.fs :as fs])

(defn load-config [project-dir]
  (let [config-path (fs/path project-dir ".aishell" "config.yaml")]
    (when (fs/exists? config-path)
      (yaml/parse-string (slurp (str config-path))))))

;; Example YAML structure:
;; mounts:
;;   - ~/.ssh
;;   - $HOME/.config/git:/home/user/.config/git
;; env:
;;   EDITOR:           # passthrough (nil value in parsed map)
;;   DEBUG: 1          # literal value
;; ports:
;;   - 3000:3000
;; docker_args: "--cap-add=SYS_PTRACE"
;; pre_start: "redis-server --daemonize yes"

;; Parsed result is an ordered map:
;; {:mounts ["~/.ssh" "$HOME/.config/git:/home/user/.config/git"]
;;  :env {:EDITOR nil :DEBUG 1}
;;  :ports ["3000:3000"]
;;  :docker_args "--cap-add=SYS_PTRACE"
;;  :pre_start "redis-server --daemonize yes"}
```

### Pattern 3: Docker Argument Construction
**What:** Build docker run argument vector from configuration
**When to use:** Constructing full docker run command
**Example:**
```clojure
;; Source: Adapted from bash implementation (aishell lines 1542-1626)
(defn build-docker-args
  "Build docker run argument vector."
  [{:keys [project-dir image-tag config state]}]
  (let [uid (-> (p/shell {:out :string} "id" "-u") :out str/trim)
        gid (-> (p/shell {:out :string} "id" "-g") :out str/trim)
        home (util/get-home)]
    (cond-> ["docker" "run" "--rm" "-it" "--init"
             "-v" (str project-dir ":" project-dir)
             "-w" project-dir
             "-e" (str "LOCAL_UID=" uid)
             "-e" (str "LOCAL_GID=" gid)
             "-e" (str "LOCAL_HOME=" home)
             "-e" (str "TERM=" (or (System/getenv "TERM") "xterm-256color"))]
      ;; Add git identity
      (:git-name state) (into ["-e" (str "GIT_AUTHOR_NAME=" (:git-name state))])
      (:git-email state) (into ["-e" (str "GIT_AUTHOR_EMAIL=" (:git-email state))])
      ;; Add config mounts
      (:mounts config) (into (build-mount-args (:mounts config)))
      ;; Add env vars
      (:env config) (into (build-env-args (:env config)))
      ;; Add ports
      (:ports config) (into (build-port-args (:ports config)))
      ;; Add pre_start
      (:pre_start config) (into ["-e" (str "PRE_START=" (:pre_start config))])
      ;; Add extra docker args (must be before image)
      (:docker_args config) (into (tokenize-docker-args (:docker_args config)))
      ;; Always add image at end
      true (conj image-tag))))
```

### Pattern 4: Git Identity Retrieval
**What:** Read git user.name and user.email from host
**When to use:** Passing git identity to container
**Example:**
```clojure
;; Source: Adapted from bash implementation (aishell lines 946-964)
(defn read-git-identity
  "Read git identity from host configuration.
   Returns {:name \"...\" :email \"...\"} or nil values if not set."
  [project-dir]
  (letfn [(git-config [key]
            (try
              (let [{:keys [exit out]}
                    (p/shell {:out :string :err :string :continue true :dir project-dir}
                             "git" "config" key)]
                (when (zero? exit)
                  (str/trim out)))
              (catch Exception _ nil)))]
    {:name (git-config "user.name")
     :email (git-config "user.email")}))
```

### Anti-Patterns to Avoid
- **Using p/process instead of p/exec:** p/process returns, exec replaces the process. Docker run should use exec.
- **Building docker args as single string:** Build as vector, let exec handle quoting
- **Parsing YAML manually:** clj-yaml.core is built-in and handles edge cases
- **Hardcoding API keys list:** Follow bash pattern of explicit list for clarity

## Don't Hand-Roll

Problems that look simple but have existing solutions:

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| YAML parsing | Regex-based parser | clj-yaml.core | Built-in, handles all YAML features |
| Path expansion (~, $HOME) | Simple replace | util/expand-path (exists) | Already handles edge cases |
| Process replacement | p/process with exit | p/exec | Proper Unix exec semantics |
| TTY detection | isatty checks | Docker -it flags | Docker handles TTY automatically |
| Signal forwarding | Signal handlers | Docker --init | Built into Docker |

**Key insight:** Docker handles most complexity (TTY, signals, PID 1) when using proper flags. The Babashka code just needs to construct the right arguments.

## Common Pitfalls

### Pitfall 1: Using p/shell or p/process for docker run
**What goes wrong:** Command returns immediately, doesn't connect stdin/stdout properly
**Why it happens:** p/shell and p/process are for capturing output, not interactive processes
**How to avoid:** Use `p/exec` which replaces the current process
**Warning signs:** Docker container exits immediately, no interactive shell

### Pitfall 2: Forgetting to apply vector args to exec
**What goes wrong:** Args passed as single vector instead of spread
**Why it happens:** p/exec expects varargs, not a vector
**How to avoid:** Use `(apply p/exec docker-args)` when building args as vector
**Warning signs:** Error about can't find command "[docker"

### Pitfall 3: YAML env map vs list handling
**What goes wrong:** Config uses map for env (key: value), bash used list (KEY=value)
**Why it happens:** YAML maps are more natural than lists for key-value pairs
**How to avoid:** Handle both formats - map keys with nil values are passthrough, map keys with values are literals
**Warning signs:** ENV vars not passed to container

### Pitfall 4: Missing harness availability check
**What goes wrong:** User runs `aishell claude` but Claude wasn't in build
**Why it happens:** Build flags not checked before run
**How to avoid:** Read state.edn and verify :with-claude before running
**Warning signs:** "claude: command not found" inside container

### Pitfall 5: Global vs project config precedence
**What goes wrong:** Project config not overriding global config
**Why it happens:** Loading both and merging incorrectly
**How to avoid:** Project-first lookup: use project config if exists, else global, else none
**Warning signs:** User's project config ignored

## Code Examples

Verified patterns from bash implementation and Babashka testing:

### Complete Run Command Handler
```clojure
;; Source: Adapted from bash implementation main() lines 1476-1653
(defn run-container
  "Run docker container for shell or harness.

   Arguments:
   - cmd: nil (shell), \"claude\", or \"opencode\"
   - harness-args: Arguments to pass to harness"
  [cmd harness-args]
  ;; Check Docker
  (docker/check-docker!)

  ;; Read state
  (let [state (state/read-state)]
    (when-not state
      (output/error-no-build))

    ;; Verify harness if requested
    (when (and (= cmd "claude") (not (:with-claude state)))
      (output/error "Claude Code not installed. Run: aishell build --with-claude"))
    (when (and (= cmd "opencode") (not (:with-opencode state)))
      (output/error "OpenCode not installed. Run: aishell build --with-opencode"))

    ;; Load config
    (let [project-dir (System/getProperty "user.dir")
          config (load-config project-dir)
          git-id (read-git-identity project-dir)
          image-tag (or (:image-tag state) "aishell:base")

          ;; Build docker args
          docker-args (build-docker-args
                        {:project-dir project-dir
                         :image-tag image-tag
                         :config config
                         :git-id git-id})

          ;; Determine command to run in container
          container-cmd (case cmd
                          "claude" (into ["claude" "--dangerously-skip-permissions"]
                                        harness-args)
                          "opencode" (into ["opencode"] harness-args)
                          nil ["/bin/bash"]
                          [cmd])]

      ;; Execute - replaces current process
      (apply p/exec (concat docker-args container-cmd)))))
```

### Config Loading with Fallback
```clojure
;; Source: Context decisions - project-first lookup
(defn load-config
  "Load config.yaml with project-first, global-fallback strategy.
   Returns parsed config map or nil if no config exists."
  [project-dir]
  (let [project-config (fs/path project-dir ".aishell" "config.yaml")
        global-config (fs/path (util/get-home) ".aishell" "config.yaml")]
    (cond
      (fs/exists? project-config)
      (try
        (yaml/parse-string (slurp (str project-config)))
        (catch Exception e
          (output/error (str "Invalid config syntax: " (.getMessage e)))))

      (fs/exists? global-config)
      (try
        (yaml/parse-string (slurp (str global-config)))
        (catch Exception e
          (output/error (str "Invalid config syntax: " (.getMessage e)))))

      :else nil)))
```

### Mount Argument Building
```clojure
;; Source: Adapted from bash build_mount_args lines 649-702
(defn build-mount-args
  "Build -v flags from mounts config.

   Supports:
   - source-only: /path/to/dir (mounts at same path)
   - source:destination: /host/path:/container/path

   Expands $HOME and ~ in paths."
  [mounts]
  (when (seq mounts)
    (->> mounts
         (mapcat (fn [mount]
                   (let [[source dest] (if (str/includes? (str mount) ":")
                                        (str/split (str mount) #":" 2)
                                        [(str mount) (str mount)])
                         source (util/expand-path source)
                         dest (util/expand-path dest)]
                     (if (fs/exists? source)
                       ["-v" (str source ":" dest)]
                       (do
                         (output/warn (str "Mount source does not exist: " source))
                         []))))))))
```

### Environment Variable Building
```clojure
;; Source: Adapted from bash build_env_args lines 706-726
;; Plus context decision: Docker-compose style VAR (passthrough) or VAR=value (literal)
(defn build-env-args
  "Build -e flags from env config.

   In YAML, env is a map:
   - key with nil value: passthrough from host (ENV:)
   - key with value: literal (ENV: value)

   Example YAML:
   env:
     EDITOR:      # passthrough - inherits from host
     DEBUG: 1     # literal - always 1"
  [env-map]
  (when (seq env-map)
    (->> env-map
         (mapcat (fn [[k v]]
                   (let [key-name (name k)]
                     (if (nil? v)
                       ;; Passthrough: only add if set on host
                       (if-let [host-val (System/getenv key-name)]
                         ["-e" key-name]
                         (do
                           (output/warn (str "Skipping unset host variable: " key-name))
                           []))
                       ;; Literal value
                       ["-e" (str key-name "=" v)])))))))
```

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| run.conf (bash style) | config.yaml (YAML) | v2.0 | Cleaner config, built-in parser |
| Manual state file parsing | EDN state.edn | v2.0 | Type-safe, Clojure native |
| Bash exec | p/exec | v2.0 | Same semantics, Clojure syntax |

**Deprecated/outdated:**
- run.conf format: Replaced by config.yaml in v2.0 (no backward compat per REQUIREMENTS.md)

## Open Questions

Things that couldn't be fully resolved:

1. **Harness mismatch detection (Claude's discretion)**
   - What we know: Can check state.edn :with-claude before running
   - What's unclear: Should we also check container has binary? (let container fail vs pre-check)
   - Recommendation: Check state.edn only (simpler), let container fail if state is wrong

2. **TTY detection for non-interactive runs**
   - What we know: Docker -it works for TTY, but what if piping input?
   - What's unclear: Should we detect non-TTY and omit -t flag?
   - Recommendation: Always use -it, Docker handles gracefully

3. **Config validation strictness**
   - What we know: Context says "fail fast on invalid syntax or unknown keys"
   - What's unclear: What counts as "unknown key"? Typos like "mount:" vs "mounts:"?
   - Recommendation: Validate known keys only, warn on unknown (more user-friendly)

## Sources

### Primary (HIGH confidence)
- Bash implementation `/home/jonasrodrigues/projects/harness/aishell` - Complete reference (1,656 LOC)
- Babashka testing via `bb -e '...'` - Verified exec, yaml, process behavior
- Existing Babashka source `/home/jonasrodrigues/projects/harness/src/aishell/*.clj` - Established patterns

### Secondary (MEDIUM confidence)
- 16-CONTEXT.md - User decisions on config loading, container lifecycle
- PROJECT.md and REQUIREMENTS.md - Requirements and constraints

### Tertiary (LOW confidence)
- None - all patterns verified through testing or bash reference

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH - All libraries are Babashka built-ins, verified via testing
- Architecture: HIGH - Direct translation from working bash implementation
- Pitfalls: HIGH - Based on actual testing and bash implementation experience

**Research date:** 2026-01-20
**Valid until:** Stable - patterns unlikely to change in 30+ days
