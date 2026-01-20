# Phase 14: Docker Integration - Research

**Researched:** 2026-01-20
**Domain:** Docker CLI wrapper in Babashka (subprocess handling, build caching, image labels)
**Confidence:** HIGH

## Summary

Phase 14 provides Docker operations that build and run commands depend on: daemon availability checks, image building with caching, and per-project Dockerfile extension. This research focuses on invoking Docker CLI from Babashka, managing build cache through content hashing and image labels, and handling subprocess output for progress indicators.

The standard approach uses `babashka.process/shell` for simple Docker commands (info, inspect) and `babashka.process/process` for build operations where output streaming is needed. Cache invalidation uses Dockerfile content SHA-256 hashes stored as Docker image labels, matching the existing bash implementation pattern. Progress feedback uses a simple spinner implementation in a background thread (no external dependencies needed).

**Primary recommendation:** Use `shell` for quick Docker queries (check availability, inspect labels), `process` with streaming for builds, and store cache hash in image labels via `--label` flag for Docker-native invalidation tracking.

## Standard Stack

The established libraries/tools for this domain:

### Core (Built into Babashka)

| Library | Purpose | Why Standard |
|---------|---------|--------------|
| `babashka.process` | Docker CLI invocation, subprocess management | Built-in, handles output capture/streaming |
| `babashka.fs` | Temp directory creation, file operations | Built-in, cross-platform |
| `clj-commons/digest` | SHA-256 hash computation for cache invalidation | Compatible with Babashka since v0.6.4 |
| `clojure.java.io` | Stream reading for output processing | Built-in core library |

### Supporting

| Library | Purpose | When to Use |
|---------|---------|-------------|
| `java.security.MessageDigest` | Alternative SHA-256 if digest library unavailable | Direct Java interop in Babashka |
| `clojure.string` | Output parsing, trimming | Built-in |

### Alternatives Considered

| Instead of | Could Use | Tradeoff |
|------------|-----------|----------|
| Image labels for cache | State file with hash | Labels are Docker-native, survive image export/import; state file is simpler but requires separate tracking |
| `process` for all Docker commands | `shell` for everything | `shell` is simpler for commands where we just need success/failure and captured output |
| External spinner library | Simple thread-based spinner | Built-in approach has no dependencies, matches bash version behavior |

**Installation:**
```bash
# No external installation needed - all libraries built into Babashka
# For clj-commons/digest, add to bb.edn if not using Java MessageDigest directly:
{:deps {org.clj-commons/digest {:mvn/version "1.4.100"}}}
```

## Architecture Patterns

### Recommended Module Structure
```
src/aishell/
  docker.clj           # Docker wrapper: availability, build, inspect, labels
  docker/
    build.clj          # Build logic: cache invalidation, Dockerfile embedding
    spinner.clj        # Progress indicator for builds (optional, can inline)
```

### Pattern 1: Docker Availability Check

**What:** Check if Docker CLI exists and daemon is running
**When to use:** Before any Docker operation
**Example:**
```clojure
;; Source: babashka.process documentation
(require '[babashka.process :as p])
(require '[babashka.fs :as fs])

(defn docker-available?
  "Check if docker command exists in PATH"
  []
  (some? (fs/which "docker")))

(defn docker-running?
  "Check if Docker daemon is responsive"
  []
  (try
    (let [{:keys [exit]} (p/shell {:out :string :err :string :continue true}
                                   "docker" "info")]
      (zero? exit))
    (catch Exception _ false)))

(defn check-docker!
  "Verify Docker is available and running, exit with error if not"
  []
  (cond
    (not (docker-available?))
    (output/error "Docker is not installed")

    (not (docker-running?))
    (output/error "Docker not running")))
```

### Pattern 2: Capturing Docker Command Output

**What:** Run Docker command and capture output as string
**When to use:** `docker inspect`, `docker images`, any query command
**Example:**
```clojure
;; Source: babashka.process shell with :out :string
(require '[babashka.process :as p])
(require '[clojure.string :as str])

(defn docker-inspect-label
  "Get a specific label from a Docker image"
  [image label-key]
  (try
    (let [{:keys [exit out]}
          (p/shell {:out :string :err :string :continue true}
                   "docker" "inspect"
                   (str "--format={{index .Config.Labels \"" label-key "\"}}")
                   image)]
      (when (zero? exit)
        (let [value (str/trim out)]
          (when (not= value "<no value>")
            value))))
    (catch Exception _ nil)))

(defn image-exists?
  "Check if a Docker image exists locally"
  [image-tag]
  (let [{:keys [exit]} (p/shell {:out :string :err :string :continue true}
                                 "docker" "image" "inspect" image-tag)]
    (zero? exit)))
```

### Pattern 3: Streaming Build Output with Progress

**What:** Run Docker build while streaming output or showing spinner
**When to use:** `docker build` operations
**Example:**
```clojure
;; Source: babashka.process README - streaming pattern
(require '[babashka.process :as p])
(require '[clojure.java.io :as io])

(defn build-with-spinner
  "Build Docker image showing spinner, capture output for errors"
  [build-args image-tag build-dir]
  (let [output-buffer (java.io.StringWriter.)
        running (atom true)
        spinner-chars [\| \/ \- \\]
        spinner-thread (future
                         (loop [i 0]
                           (when @running
                             (print (str "\rBuilding image " (nth spinner-chars (mod i 4))))
                             (flush)
                             (Thread/sleep 100)
                             (recur (inc i)))))]
    (try
      (let [proc (p/process {:dir build-dir
                              :err :inherit  ;; Show errors immediately
                              :shutdown p/destroy-tree}
                             (concat ["docker" "build" "-t" image-tag]
                                     build-args
                                     ["."]))]
        ;; Read stdout to buffer (for error reporting if build fails)
        (with-open [rdr (io/reader (:out proc))]
          (doseq [line (line-seq rdr)]
            (.write output-buffer (str line "\n"))))
        ;; Wait for completion
        (let [{:keys [exit]} @proc]
          (reset! running false)
          (print "\r")  ;; Clear spinner
          (if (zero? exit)
            {:success true :output (str output-buffer)}
            {:success false :output (str output-buffer) :exit exit})))
      (finally
        (reset! running false)))))

(defn build-verbose
  "Build Docker image with full output streaming"
  [build-args image-tag build-dir]
  (let [{:keys [exit]}
        (p/shell {:dir build-dir
                  :out :inherit
                  :err :inherit}
                 (str/join " " (concat ["docker" "build" "-t" image-tag "--progress=plain"]
                                        build-args [".""])))]
    {:success (zero? exit) :exit exit}))
```

### Pattern 4: Cache Hash Computation and Label Storage

**What:** Compute Dockerfile content hash, store in image label
**When to use:** Build caching for invalidation detection
**Example:**
```clojure
;; Source: clj-commons/digest + Docker labels pattern from existing bash
(require '[clj-commons.digest :as digest])
(require '[clojure.java.io :as io])

(def dockerfile-hash-label "aishell.dockerfile.hash")
(def base-image-id-label "aishell.base.id")

(defn compute-dockerfile-hash
  "Compute SHA-256 hash of Dockerfile content (first 12 chars)"
  [dockerfile-content]
  (subs (digest/sha-256 dockerfile-content) 0 12))

;; Alternative using Java MessageDigest directly (no external dep)
(defn compute-dockerfile-hash-native
  "Compute SHA-256 hash using Java MessageDigest"
  [dockerfile-content]
  (let [md (java.security.MessageDigest/getInstance "SHA-256")
        bytes (.digest md (.getBytes dockerfile-content "UTF-8"))]
    (subs (apply str (map #(format "%02x" (bit-and % 0xff)) bytes)) 0 12)))

(defn get-image-dockerfile-hash
  "Read hash label from built image"
  [image]
  (docker-inspect-label image dockerfile-hash-label))

(defn needs-rebuild?
  "Check if image needs rebuild based on Dockerfile hash"
  [image current-hash]
  (let [built-hash (get-image-dockerfile-hash image)]
    (or (nil? built-hash)
        (not= built-hash current-hash))))

(defn build-label-args
  "Build --label arguments for docker build"
  [dockerfile-hash base-image-id]
  (cond-> [(str "--label=" dockerfile-hash-label "=" dockerfile-hash)]
    base-image-id (conj (str "--label=" base-image-id-label "=" base-image-id))))
```

### Pattern 5: Embedded Dockerfile Management

**What:** Embed Dockerfile content in Clojure, write to temp dir for build
**When to use:** Building base image from embedded template
**Example:**
```clojure
;; Source: Pattern from existing bash implementation
(require '[babashka.fs :as fs])

(def base-dockerfile
  "# Aishell Base Image
FROM debian:bookworm-slim

ARG WITH_CLAUDE=false
ARG WITH_OPENCODE=false
# ... rest of Dockerfile content
")

(defn write-build-files
  "Write Dockerfile and supporting files to temp directory"
  [build-dir]
  (spit (str (fs/path build-dir "Dockerfile")) base-dockerfile)
  (spit (str (fs/path build-dir "entrypoint.sh")) entrypoint-script)
  (spit (str (fs/path build-dir "bashrc.aishell")) bashrc-content))

(defn with-build-context
  "Execute function with temporary build directory"
  [f]
  (let [build-dir (str (fs/create-temp-dir {:prefix "aishell-build-"}))]
    (try
      (write-build-files build-dir)
      (f build-dir)
      (finally
        (fs/delete-tree build-dir)))))
```

### Pattern 6: Project Dockerfile Extension

**What:** Detect and build project-specific Dockerfile extension
**When to use:** `.aishell/Dockerfile` exists in project
**Example:**
```clojure
;; Source: Pattern from existing bash implementation
(require '[babashka.fs :as fs])

(defn project-dockerfile
  "Get project Dockerfile path if exists"
  [project-dir]
  (let [dockerfile (fs/path project-dir ".aishell" "Dockerfile")]
    (when (fs/exists? dockerfile)
      (str dockerfile))))

(defn get-base-image-id
  "Get image ID for an image tag"
  [image-tag]
  (let [{:keys [exit out]}
        (p/shell {:out :string :err :string :continue true}
                 "docker" "inspect" "--format={{.Id}}" image-tag)]
    (when (zero? exit)
      (str/trim out))))

(defn needs-extended-rebuild?
  "Check if extended image needs rebuild (base changed)"
  [extended-tag base-image-tag]
  (let [stored-base-id (docker-inspect-label extended-tag base-image-id-label)
        current-base-id (get-base-image-id base-image-tag)]
    (or (nil? stored-base-id)
        (not= stored-base-id current-base-id))))

(defn build-extended-image
  "Build project Dockerfile on top of base image"
  [project-dir base-tag extended-tag]
  (let [base-id (get-base-image-id base-tag)
        dockerfile (project-dockerfile project-dir)]
    (build-with-args
      ["-f" dockerfile
       (str "--label=" base-image-id-label "=" base-id)]
      extended-tag
      project-dir)))
```

### Anti-Patterns to Avoid

- **Using `exec` for Docker commands:** `exec` replaces the current process; use `shell` or `process` instead
- **Blocking on large output without streaming:** Use `process` with reader for large outputs to avoid memory issues
- **Hardcoding Docker paths:** Use `fs/which` to find Docker binary
- **Ignoring exit codes:** Always check `:exit` from shell/process result
- **Mixing `shell` token args with string interpolation:** Pass args as separate strings to avoid shell injection

## Don't Hand-Roll

Problems that look simple but have existing solutions:

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| Docker command execution | Manual ProcessBuilder | `babashka.process/shell` | Handles args, env, streams |
| Content hashing | Manual bytes/hex conversion | `clj-commons/digest` or `MessageDigest` | Tested, handles encoding |
| Temp directory | Manual mktemp | `babashka.fs/create-temp-dir` | Cross-platform, auto-naming |
| Image label query | Parsing JSON from `docker inspect` | `--format` template | Simpler, more efficient |
| File hash caching | Custom file change detection | Docker build cache + labels | Docker handles layer caching |

**Key insight:** Docker's native build cache handles most caching; we only need to track Dockerfile content changes via labels to know when cache might be stale.

## Common Pitfalls

### Pitfall 1: Shell vs Process Selection

**What goes wrong:** Using `shell` for builds blocks on large output or hides errors
**Why it happens:** `shell` inherits I/O by default, which is good for interactive but bad for capturing
**How to avoid:** Use `process` for builds where you need to capture or stream output
**Warning signs:** Build hangs, or errors not captured for reporting

```clojure
;; Wrong for builds
(p/shell "docker" "build" "-t" tag ".")  ;; Output goes to terminal, can't capture errors

;; Correct for builds
(p/process {:out :string :err :inherit} "docker" "build" "-t" tag ".")
```

### Pitfall 2: Docker Command Argument Quoting

**What goes wrong:** Arguments with spaces or special characters fail
**Why it happens:** String interpolation instead of argument vector
**How to avoid:** Pass arguments as separate strings in vector, not concatenated
**Warning signs:** "Invalid reference format" or similar Docker errors

```clojure
;; Wrong
(p/shell (str "docker build -t " tag " --build-arg MSG=\"hello world\" ."))

;; Correct
(p/shell "docker" "build" "-t" tag "--build-arg" "MSG=hello world" ".")
```

### Pitfall 3: Label Value Escaping

**What goes wrong:** Label values with special characters break `--format` template
**Why it happens:** Go template syntax requires escaping
**How to avoid:** Use `index` syntax for label access
**Warning signs:** Empty results or parsing errors from docker inspect

```clojure
;; Wrong - breaks on dots in label name
(str "--format={{.Config.Labels.aishell.dockerfile.hash}}")

;; Correct - index syntax handles dots
(str "--format={{index .Config.Labels \"aishell.dockerfile.hash\"}}")
```

### Pitfall 4: Spinner Thread Cleanup

**What goes wrong:** Spinner keeps running after build fails, garbles output
**Why it happens:** Exception thrown without stopping spinner thread
**How to avoid:** Use try/finally to reset running atom
**Warning signs:** Spinner characters appear in error messages

```clojure
;; Always use try/finally
(try
  (do-build)
  (finally
    (reset! running false)))
```

### Pitfall 5: TTY Detection for Spinner

**What goes wrong:** Spinner escape codes appear in CI logs
**Why it happens:** Spinner runs when stderr is not a terminal
**How to avoid:** Check `(System/console)` before starting spinner
**Warning signs:** `[K` and similar escape codes in log files

```clojure
(defn should-show-spinner?
  "Only show spinner if stderr is a TTY"
  []
  (some? (System/console)))
```

### Pitfall 6: Image Size Reporting

**What goes wrong:** Size not available or shows wrong format
**Why it happens:** Need to query after build completes, format varies
**How to avoid:** Use `docker image inspect` with size format after successful build
**Warning signs:** "0B" size or missing size in completion message

```clojure
(defn get-image-size
  "Get human-readable image size"
  [image-tag]
  (let [{:keys [exit out]}
        (p/shell {:out :string :continue true}
                 "docker" "image" "inspect" "--format={{.Size}}" image-tag)]
    (when (zero? exit)
      (format-bytes (parse-long (str/trim out))))))
```

## Code Examples

Verified patterns from official sources:

### Complete Docker Availability Check Module
```clojure
;; Source: babashka.process docs, babashka.fs docs
(ns aishell.docker
  (:require [babashka.process :as p]
            [babashka.fs :as fs]
            [aishell.output :as output]))

(defn docker-available?
  "Check if docker command exists in PATH"
  []
  (some? (fs/which "docker")))

(defn docker-running?
  "Check if Docker daemon is responsive"
  []
  (try
    (let [{:keys [exit]} (p/shell {:out :string :err :string :continue true}
                                   "docker" "info")]
      (zero? exit))
    (catch Exception _ false)))

(defn check-docker!
  "Verify Docker is available and running, exit with error if not"
  []
  (cond
    (not (docker-available?))
    (output/error "Docker is not installed")

    (not (docker-running?))
    (output/error "Docker not running")))

(defn image-exists?
  "Check if a Docker image exists locally"
  [image-tag]
  (let [{:keys [exit]} (p/shell {:out :string :err :string :continue true}
                                 "docker" "image" "inspect" image-tag)]
    (zero? exit)))
```

### Build with Cache Validation
```clojure
;; Source: Pattern from existing bash + babashka.process
(ns aishell.docker.build
  (:require [babashka.process :as p]
            [babashka.fs :as fs]
            [clojure.string :as str]
            [aishell.docker :as docker]
            [aishell.output :as output]))

(def dockerfile-hash-label "aishell.dockerfile.hash")

(defn compute-hash
  "Compute SHA-256 hash (first 12 chars) of content"
  [content]
  (let [md (java.security.MessageDigest/getInstance "SHA-256")
        bytes (.digest md (.getBytes content "UTF-8"))]
    (subs (apply str (map #(format "%02x" (bit-and % 0xff)) bytes)) 0 12)))

(defn get-image-label
  "Get a specific label from a Docker image"
  [image label-key]
  (try
    (let [{:keys [exit out]}
          (p/shell {:out :string :err :string :continue true}
                   "docker" "inspect"
                   (str "--format={{index .Config.Labels \"" label-key "\"}}")
                   image)]
      (when (zero? exit)
        (let [value (str/trim out)]
          (when-not (or (empty? value) (= value "<no value>"))
            value))))
    (catch Exception _ nil)))

(defn needs-rebuild?
  "Check if image needs rebuild based on Dockerfile hash"
  [image-tag dockerfile-content force?]
  (or force?
      (not (docker/image-exists? image-tag))
      (let [current-hash (compute-hash dockerfile-content)
            built-hash (get-image-label image-tag dockerfile-hash-label)]
        (not= current-hash built-hash))))

(defn format-size
  "Format bytes to human-readable size"
  [bytes]
  (cond
    (< bytes 1024) (str bytes "B")
    (< bytes (* 1024 1024)) (format "%.1fKB" (/ bytes 1024.0))
    (< bytes (* 1024 1024 1024)) (format "%.1fMB" (/ bytes 1024.0 1024.0))
    :else (format "%.2fGB" (/ bytes 1024.0 1024.0 1024.0))))

(defn get-image-size
  "Get human-readable image size"
  [image-tag]
  (let [{:keys [exit out]}
        (p/shell {:out :string :continue true}
                 "docker" "image" "inspect" "--format={{.Size}}" image-tag)]
    (when (zero? exit)
      (format-size (parse-long (str/trim out))))))
```

### Simple Spinner Implementation
```clojure
;; Source: Standard terminal animation pattern
(ns aishell.docker.spinner
  (:require [aishell.output :as output]))

(def spinner-chars [\| \/ \- \\])

(defn should-animate?
  "Check if we should show animated output"
  []
  (and (some? (System/console))
       (nil? (System/getenv "CI"))))

(defn with-spinner
  "Execute function while showing spinner, returns function result"
  [message f]
  (if-not (should-animate?)
    ;; No animation: just print message and run
    (do
      (binding [*out* *err*]
        (println message))
      (f))
    ;; Animated spinner
    (let [running (atom true)
          spinner-thread (future
                           (loop [i 0]
                             (when @running
                               (binding [*out* *err*]
                                 (print (str "\r" (nth spinner-chars (mod i 4)) " " message " "))
                                 (flush))
                               (Thread/sleep 100)
                               (recur (inc i)))))]
      (try
        (let [result (f)]
          (binding [*out* *err*]
            (print "\r")
            (print (apply str (repeat (+ 3 (count message)) " ")))
            (print "\r")
            (flush))
          result)
        (finally
          (reset! running false))))))
```

## State of the Art

| Old Approach (Bash) | Current Approach (Babashka) | When Changed | Impact |
|--------------------|-----------------------------|--------------|--------|
| Shell subprocesses via `$(docker ...)` | `babashka.process/shell` | Babashka adoption | Type-safe, no shell injection |
| `sha256sum \| cut` for hashing | `MessageDigest` or `clj-commons/digest` | Babashka adoption | No subprocess, cross-platform |
| Background process with `&` | Clojure `future` | Babashka adoption | Proper thread management |
| `kill $pid` for spinner stop | Atom with `reset!` | Babashka adoption | Coordinated shutdown |
| Temp file with `mktemp` | `babashka.fs/create-temp-dir` | Babashka adoption | Auto-cleanup, cross-platform |

**Deprecated/outdated:**
- Using `shell` string concatenation: Always use argument vectors
- `clojure.java.shell/sh`: Prefer `babashka.process` for better streaming support

## Open Questions

Things that couldn't be fully resolved:

1. **Cache hit feedback: silent vs brief message**
   - What we know: CONTEXT.md defers to research
   - Options: Silent (user doesn't need to know), Brief message ("Using cached image"), Verbose-only
   - Recommendation: **Brief message only in verbose mode** - keeps default quiet while allowing debugging
   - Rationale: Matches Docker's own behavior of silent cache hits

2. **Build time measurement precision**
   - What we know: Docker doesn't provide build timing natively
   - Options: Wrap with `time` macro, manual System/currentTimeMillis
   - Recommendation: Use `System/currentTimeMillis` before/after build for elapsed time
   - Example: "Built aishell:abc123 (45s, 1.2GB)"

3. **Docker error output: pass through vs summarize**
   - What we know: CONTEXT.md marks as Claude's discretion
   - Recommendation: **Pass through stderr directly** - Docker's errors are already user-friendly
   - In verbose mode: full output; in default: captured for error reporting only

## Sources

### Primary (HIGH confidence)
- [babashka/process GitHub](https://github.com/babashka/process) - shell, process, streaming patterns
- [babashka/process API.md](https://github.com/babashka/process/blob/master/API.md) - Full API reference
- [Docker Docs: docker inspect](https://docs.docker.com/reference/cli/docker/image/inspect/) - Label query syntax
- [Docker Docs: build cache](https://docs.docker.com/build/cache/invalidation/) - Cache invalidation rules
- [clj-commons/digest](https://github.com/clj-commons/digest) - SHA-256 hashing API

### Secondary (MEDIUM confidence)
- [Docker Docs: object labels](https://docs.docker.com/engine/manage-resources/labels/) - Label best practices
- Existing bash implementation in `/home/jonasrodrigues/projects/harness/aishell` - Verified patterns to port

### Tertiary (LOW confidence)
- [pmonks/spinner](https://github.com/pmonks/spinner) - Reference for spinner patterns (not confirmed Babashka-compatible)

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH - Built-in Babashka libraries, well-documented
- Architecture: HIGH - Direct port of verified bash patterns
- Pitfalls: HIGH - Identified through documentation and code analysis
- Code examples: HIGH - Tested against babashka.process documentation

**Research date:** 2026-01-20
**Valid until:** 2026-03-20 (Docker CLI and Babashka process APIs are stable)

---
*Phase: 14-docker-integration*
*Research completed: 2026-01-20*
