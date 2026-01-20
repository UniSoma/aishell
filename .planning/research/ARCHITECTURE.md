# Architecture Research: Babashka CLI Structure

**Project:** aishell (Bash to Babashka rewrite)
**Researched:** 2026-01-20
**Overall Confidence:** HIGH

## Summary

Babashka provides a mature ecosystem for CLI development with built-in libraries that directly map to the existing Bash implementation's needs. The recommended architecture separates concerns into distinct namespaces: CLI parsing (babashka.cli), process execution (babashka.process), file operations (babashka.fs), and domain logic. The key architectural wins over Bash are structured data (EDN for config/state), proper error handling with try/catch, and cross-platform process execution without shell metacharacter concerns. Docker integration uses `babashka.process/shell` for synchronous commands with output capture, avoiding the complexity of the Docker pod for this use case.

## Recommended Structure

```
aishell/
|-- bb.edn                    # Babashka project config
|-- aishell                   # Executable entry point (shebang script)
|-- src/
|   +-- aishell/
|       |-- core.clj          # Entry point, main dispatch
|       |-- cli.clj           # Argument parsing, subcommand dispatch
|       |-- docker.clj        # Docker CLI wrapper functions
|       |-- build.clj         # Image build logic (Dockerfile generation)
|       |-- run.clj           # Container run logic
|       |-- config.clj        # run.conf parsing, validation
|       |-- state.clj         # Build state persistence (EDN files)
|       |-- output.clj        # Terminal output, colors, spinners
|       +-- util.clj          # Shared utilities
|-- resources/
|   |-- Dockerfile.edn        # Dockerfile template as data
|   |-- entrypoint.sh         # Container entrypoint (unchanged)
|   +-- bashrc.aishell        # Container bashrc (unchanged)
+-- test/
    +-- aishell/
        +-- *_test.clj        # Test files mirror src structure
```

### bb.edn Configuration

```clojure
{:paths ["src" "resources"]
 :min-bb-version "1.3.0"
 :tasks
 {test {:doc "Run tests"
        :task (shell "bb test")}
  build {:doc "Build uberscript"
         :task (shell "bb uberscript aishell -m aishell.core")}}}
```

### Entry Point Pattern

The executable `aishell` file uses a shebang to invoke Babashka:

```bash
#!/usr/bin/env bb
(require '[aishell.core :as core])
(core/-main *command-line-args*)
```

Or directly in bb.edn with `-m` flag support.

## Namespace Responsibilities

| Namespace | Responsibility | Key Functions |
|-----------|----------------|---------------|
| `aishell.core` | Entry point, top-level dispatch | `-main`, version handling |
| `aishell.cli` | Argument parsing, subcommand routing | `parse-args`, `dispatch`, command specs |
| `aishell.docker` | Docker CLI invocation abstraction | `image-exists?`, `build-image`, `run-container`, `inspect` |
| `aishell.build` | Build command logic, Dockerfile generation | `do-build`, `write-dockerfile`, `compute-image-tag` |
| `aishell.run` | Run/shell/exec command logic | `do-run`, `build-docker-args`, `apply-runtime-config` |
| `aishell.config` | run.conf parsing, validation | `parse-run-conf`, `build-mount-args`, `build-env-args` |
| `aishell.state` | Build state persistence | `read-state`, `write-state`, `get-state-file` |
| `aishell.output` | User-facing output | `error`, `warn`, `verbose`, `spinner` |
| `aishell.util` | Cross-cutting utilities | `validate-version`, `project-hash`, `home-dir` |

## Data Flow

### Build Command

```
User: aishell build --with-claude
           |
           v
+-----------------------------+
|     aishell.cli             |
|  parse-args -> {:command :build
|                :with-claude true}
+-----------------------------+
           |
           v
+-----------------------------+
|     aishell.build           |
|  1. Validate inputs         |
|  2. Compute image tag       |
|  3. Generate Dockerfile     |<-- aishell.util (templates)
|  4. Write temp build files  |<-- babashka.fs
|  5. Call docker build       |<-- aishell.docker
|  6. Persist state           |<-- aishell.state
+-----------------------------+
           |
           v
+-----------------------------+
|     aishell.docker          |
|  (shell {:out :string}      |
|    "docker" "build" args)   |
+-----------------------------+
           |
           v
+-----------------------------+
|     aishell.state           |
|  (spit state-file           |
|    (pr-str state-map))      |
+-----------------------------+
```

### Run/Shell Command

```
User: aishell claude
           |
           v
+-----------------------------+
|     aishell.cli             |
|  parse-args -> {:command :claude
|                :args [...]}
+-----------------------------+
           |
           v
+-----------------------------+
|     aishell.run             |
|  1. Load state              |<-- aishell.state
|  2. Verify image exists     |<-- aishell.docker
|  3. Handle extension        |
|  4. Load run.conf           |<-- aishell.config
|  5. Build docker args       |
|  6. Read git identity       |
|  7. Exec container          |<-- aishell.docker
+-----------------------------+
           |
           v
+-----------------------------+
|     aishell.config          |
|  parse-run-conf -> {:mounts [...]
|                    :env {...}
|                    :ports [...]}
+-----------------------------+
           |
           v
+-----------------------------+
|     aishell.docker          |
|  (shell "docker" "run"      |
|    "-it" "--rm" ...args     |
|    image command)           |
+-----------------------------+
```

### Update Command

```
User: aishell update --with-opencode
           |
           v
+-----------------------------+
|     aishell.cli             |
|  parse-args -> {:command :update
|                :with-opencode true}
+-----------------------------+
           |
           v
+-----------------------------+
|     aishell.build           |
|  1. Load existing state     |<-- aishell.state
|  2. Merge new flags         |
|  3. Rebuild (--no-cache)    |<-- aishell.docker
|  4. Update state            |<-- aishell.state
+-----------------------------+
```

## Docker Integration Pattern

### Recommended: babashka.process/shell

Use `shell` for synchronous Docker commands with proper output handling:

```clojure
(ns aishell.docker
  (:require [babashka.process :refer [shell sh]]
            [clojure.string :as str]))

;; Check if image exists
(defn image-exists? [tag]
  (try
    (shell {:out :string :err :string}
           "docker" "image" "inspect" tag)
    true
    (catch Exception _ false)))

;; Build image with progress output (inherit stdout/stderr)
(defn build-image [{:keys [dockerfile-dir tag build-args no-cache?]}]
  (let [args (cond-> ["docker" "build" "-t" tag]
               no-cache? (conj "--no-cache")
               build-args (into (mapcat (fn [[k v]] ["--build-arg" (str k "=" v)]) build-args))
               true (conj dockerfile-dir))]
    (apply shell args)))

;; Build image with captured output (for non-verbose mode)
(defn build-image-quiet [{:keys [dockerfile-dir tag build-args no-cache?]}]
  (let [args (cond-> ["docker" "build" "-t" tag]
               no-cache? (conj "--no-cache")
               build-args (into (mapcat (fn [[k v]] ["--build-arg" (str k "=" v)]) build-args))
               true (conj dockerfile-dir))
        result (apply sh args)]
    (when-not (zero? (:exit result))
      (throw (ex-info "Build failed" {:output (:err result)})))
    result))

;; Run interactive container (replaces current process)
(defn run-container [{:keys [image docker-args command]}]
  (let [args (concat ["docker" "run" "--rm" "-it" "--init"]
                     docker-args
                     [image]
                     command)]
    ;; Use exec semantics - this replaces the process
    (apply shell {:inherit true} args)))

;; Inspect image for labels
(defn get-image-label [image label]
  (let [result (sh "docker" "inspect"
                   (str "--format={{index .Config.Labels \"" label "\"}}")
                   image)]
    (when (zero? (:exit result))
      (str/trim (:out result)))))
```

### Why Not the Docker Pod?

The [pod-lispyclouds-docker](https://github.com/lispyclouds/pod-lispyclouds-docker) pod provides programmatic Docker API access. However, for this project:

1. **Shell commands suffice** - We only need build, run, inspect
2. **Output handling matters** - Build progress should stream to terminal
3. **Exec semantics required** - `docker run -it` replaces the process
4. **Fewer dependencies** - No pod binary to distribute

The pod would be useful if we needed complex container orchestration, but CLI wrapping via `shell` is simpler and more maintainable.

### Cross-Platform Considerations

```clojure
;; babashka.process handles command tokenization cross-platform
;; This works on Linux, macOS, Windows:
(shell "docker" "run" "--rm" image)

;; For paths with spaces, quoting is handled automatically:
(shell "docker" "run" "-v" (str project-dir ":" project-dir) image)

;; Environment variables via :extra-env (case-sensitive on Windows)
(shell {:extra-env {"FOO" "bar"}} "docker" "run" image)
```

## Configuration Approach

### Recommendation: EDN for State, Plain Text for run.conf

**State files (`.local/state/aishell/builds/*.edn`):**

```clojure
;; EDN - machine-readable, Clojure-native
{:project-path "/home/user/myproject"
 :with-claude true
 :with-opencode false
 :claude-version "2.0.22"
 :opencode-version nil
 :image-tag "aishell:claude-2.0.22"
 :built-at #inst "2026-01-20T10:30:00Z"
 :dockerfile-hash "abc123def456"}
```

**Runtime config (`.aishell/run.conf`):**

Keep the existing shell-variable format for user familiarity, but parse it into structured data:

```clojure
(ns aishell.config
  (:require [clojure.string :as str]
            [clojure.java.io :as io]))

(def allowed-vars #{"MOUNTS" "ENV" "PORTS" "DOCKER_ARGS" "PRE_START"})

(defn parse-run-conf [path]
  (when (.exists (io/file path))
    (reduce
     (fn [config line]
       (let [line (str/trim line)]
         (cond
           (str/blank? line) config
           (str/starts-with? line "#") config
           :else
           (let [[_ var value] (re-matches #"([A-Z_]+)=\"?([^\"]*)\"?" line)]
             (if (allowed-vars var)
               (assoc config (keyword (str/lower-case var)) value)
               (throw (ex-info (str "Unknown config variable: " var)
                               {:line line})))))))
     {}
     (str/split-lines (slurp path)))))
```

### Why Not YAML?

1. **EDN is native** - No parsing library needed, `edn/read-string` is built-in
2. **Clojure data** - Maps, keywords, sets work directly
3. **Instants supported** - `#inst` for timestamps
4. **Consistent** - Same format as bb.edn

### Why Keep run.conf Format?

1. **User familiarity** - Users already have these files
2. **Shell compatibility** - Can still be sourced by shell if needed
3. **Migration path** - No breaking change for existing users

## State Management

### State File Location

Follow XDG Base Directory spec (matching current Bash implementation):

```clojure
(ns aishell.state
  (:require [babashka.fs :as fs]
            [clojure.edn :as edn]))

(defn state-dir []
  (let [base (or (System/getenv "XDG_STATE_HOME")
                 (str (System/getProperty "user.home") "/.local/state"))]
    (fs/path base "aishell" "builds")))

(defn project-hash [project-dir]
  (-> project-dir
      fs/canonicalize
      str
      hash
      (format "%012x")))

(defn state-file [project-dir]
  (fs/path (state-dir) (str (project-hash project-dir) ".edn")))
```

### Atomic State Updates

```clojure
(defn write-state [project-dir state]
  (let [path (state-file project-dir)
        temp (fs/create-temp-file)]
    (fs/create-dirs (fs/parent path))
    (spit (str temp) (pr-str state))
    (fs/move temp path {:replace-existing true})))

(defn read-state [project-dir]
  (let [path (state-file project-dir)]
    (when (fs/exists? path)
      (edn/read-string (slurp (str path))))))
```

## Cleanup and Shutdown Hooks

Babashka supports JVM shutdown hooks for cleanup:

```clojure
(ns aishell.util
  (:import [java.lang Runtime]))

(defonce cleanup-registry (atom []))

(defn register-cleanup [f]
  (swap! cleanup-registry conj f))

(defn- run-cleanups []
  (doseq [f @cleanup-registry]
    (try (f) (catch Exception _))))

;; Register once at startup
(defn init-cleanup-handler []
  (-> (Runtime/getRuntime)
      (.addShutdownHook (Thread. run-cleanups))))

;; Usage:
(let [temp-dir (fs/create-temp-dir)]
  (register-cleanup #(fs/delete-tree temp-dir))
  ;; ... use temp-dir ...
  )
```

Note: `babashka.process/shell` automatically kills subprocesses on shutdown, so the main cleanup needs are for temp files.

## Error Handling Pattern

```clojure
(ns aishell.output
  (:require [clojure.string :as str]))

(def ^:dynamic *verbose* false)

(defn supports-color? []
  (and (System/console)
       (nil? (System/getenv "NO_COLOR"))
       (or (System/getenv "FORCE_COLOR")
           (some-> (System/getenv "TERM")
                   (str/includes? "color")))))

(def RED (if (supports-color?) "\u001b[31m" ""))
(def YELLOW (if (supports-color?) "\u001b[33m" ""))
(def NC (if (supports-color?) "\u001b[0m" ""))

(defn error [msg]
  (binding [*out* *err*]
    (println (str RED "Error:" NC " " msg)))
  (System/exit 1))

(defn warn [msg]
  (binding [*out* *err*]
    (println (str YELLOW "Warning:" NC " " msg))))

(defn verbose [msg]
  (when *verbose*
    (binding [*out* *err*]
      (println msg))))
```

## CLI Parsing with babashka.cli

```clojure
(ns aishell.cli
  (:require [babashka.cli :as cli]))

(def global-spec
  {:verbose {:alias :v
             :desc "Show detailed output"
             :coerce :boolean}
   :help {:alias :h
          :desc "Show help"
          :coerce :boolean}
   :version {:desc "Show version"
             :coerce :boolean}})

(def build-spec
  {:with-claude {:desc "Include Claude Code"
                 :coerce :boolean}
   :with-opencode {:desc "Include OpenCode"
                   :coerce :boolean}
   :claude-version {:desc "Claude Code version (e.g., 2.0.22)"
                    :coerce :string}
   :opencode-version {:desc "OpenCode version"
                      :coerce :string}
   :no-cache {:desc "Force fresh build"
              :coerce :boolean}})

(defn parse-args [args]
  (let [parsed (cli/parse-args args {:spec global-spec
                                     :args->opts [:command]})]
    (case (:command parsed)
      "build" (merge parsed (cli/parse-args (:args parsed) {:spec build-spec}))
      "update" (merge parsed (cli/parse-args (:args parsed) {:spec build-spec}))
      "claude" (assoc parsed :harness-args (:args parsed))
      "opencode" (assoc parsed :harness-args (:args parsed))
      parsed)))
```

## Build Order for Incremental Delivery

Based on dependencies between components:

### Phase 1: Foundation
**Goal:** Basic project structure, can invoke `aishell --version`

1. `bb.edn` - Project configuration
2. `aishell.util` - Version, home directory, validation
3. `aishell.output` - Error/warn/verbose (minimal, no spinner)
4. `aishell.cli` - Argument parsing (global options only)
5. `aishell.core` - Entry point, version/help dispatch

**Exit criteria:** `./aishell --version` works

### Phase 2: Docker Integration
**Goal:** Can check Docker status, inspect images

1. `aishell.docker` - Docker CLI wrapper functions
   - `check-docker` (is Docker running?)
   - `image-exists?`
   - `get-image-label`

**Exit criteria:** `./aishell` shows "Docker not running" or "No build found"

### Phase 3: Build Command
**Goal:** `aishell build --with-claude` creates image

1. `aishell.state` - State file read/write
2. `aishell.build` - Build command implementation
   - Dockerfile generation (port embedded heredocs)
   - Build argument handling
   - State persistence

**Exit criteria:** Can build image, state file created

### Phase 4: Run Commands
**Goal:** `aishell claude` launches container

1. `aishell.config` - run.conf parsing
2. `aishell.run` - Run/shell/exec logic
   - Git identity detection
   - Config mount building
   - API env vars
   - run.conf application

**Exit criteria:** Full `aishell claude` flow works

### Phase 5: Polish
**Goal:** Feature parity with Bash version

1. Update command (merge state)
2. Extension Dockerfile handling
3. Spinner for build progress
4. Dockerfile hash change detection
5. Security warnings for dangerous Docker args

**Exit criteria:** All Bash features ported

### Phase 6: Testing and Distribution
**Goal:** Production-ready

1. Unit tests for each namespace
2. Integration tests for full flows
3. Uberscript generation for single-file distribution
4. Documentation

## Patterns to Follow

### Pattern 1: Pure Functions with Side Effects at Edges

**What:** Keep business logic pure; isolate I/O at namespace boundaries.

**Example:**
```clojure
;; Pure: compute what to do
(defn compute-build-args [opts]
  (cond-> []
    (:no-cache opts) (conj "--no-cache")
    (:with-claude opts) (conj "--build-arg" "WITH_CLAUDE=true")))

;; Impure: execute it
(defn do-build [opts]
  (let [args (compute-build-args opts)]
    (apply shell "docker" "build" args)))
```

### Pattern 2: Data-Driven Dispatch

**What:** Use maps for command dispatch rather than long case statements.

**Example:**
```clojure
(def commands
  {:build   {:fn do-build   :spec build-spec}
   :update  {:fn do-update  :spec build-spec}
   :claude  {:fn do-claude  :spec run-spec}
   :opencode {:fn do-opencode :spec run-spec}})

(defn dispatch [{:keys [command] :as opts}]
  (if-let [cmd (commands (keyword command))]
    ((:fn cmd) opts)
    (do-shell opts)))
```

### Pattern 3: Explicit Error Boundaries

**What:** Catch exceptions at command boundaries, not deep in call stack.

**Example:**
```clojure
(defn -main [& args]
  (try
    (let [opts (parse-args args)]
      (dispatch opts))
    (catch clojure.lang.ExceptionInfo e
      (error (ex-message e)))
    (catch Exception e
      (error (str "Unexpected error: " (.getMessage e))))))
```

## Anti-Patterns to Avoid

### Anti-Pattern 1: Global Mutable State

**What:** Using `def` or `defonce` atoms for state that should be threaded through functions.

**Why bad:** Hard to test, implicit dependencies.

**Instead:** Pass state as arguments, return updated state.

### Anti-Pattern 2: String Building for Commands

**What:** Building command strings then splitting them.

```clojure
;; Bad
(shell (str "docker build " (when no-cache "--no-cache") " -t " tag))
```

**Why bad:** Shell injection risk, quoting issues.

**Instead:** Use varargs with `shell`:
```clojure
;; Good
(apply shell (cond-> ["docker" "build"]
               no-cache (conj "--no-cache")
               true (conj "-t" tag)))
```

### Anti-Pattern 3: Deep Nesting

**What:** Deeply nested `let`/`if`/`when` forms.

**Instead:** Use threading macros, early returns via `when-not`/`throw`.

## Confidence Assessment

| Area | Level | Reasoning |
|------|-------|-----------|
| Project Structure | HIGH | Standard Babashka patterns, well-documented in [Babashka book](https://book.babashka.org/) |
| CLI Parsing | HIGH | [babashka.cli](https://github.com/babashka/cli) is built-in since bb 0.9.160 |
| Process Execution | HIGH | [babashka.process](https://github.com/babashka/process) is mature, built-in |
| Docker Integration | HIGH | Shell wrapping is straightforward, no complex API needed |
| State Management | HIGH | EDN read/write is trivial, atomic file ops via babashka.fs |
| Cleanup Handlers | MEDIUM | JVM shutdown hooks work, but less battle-tested than Bash traps |
| Cross-Platform | MEDIUM | babashka.process handles tokenization, but Windows path edge cases possible |

## Open Questions

1. **Uberscript vs bb invocation:** Should the distributed artifact be a single uberscript file, or require bb installation?
   - Recommendation: Uberscript for portability

2. **Spinner implementation:** Native Babashka or shell out to external tool?
   - Recommendation: Native using threads and `System.console`

3. **Windows support:** Current Bash script is Linux/macOS only. Extend to Windows?
   - Recommendation: Defer to post-MVP; Babashka enables this but Docker behavior differs

## Sources

- [Babashka CLI](https://github.com/babashka/cli) - Argument parsing library
- [Babashka Process](https://github.com/babashka/process) - Subprocess execution
- [Babashka FS](https://github.com/babashka/fs) - File system utilities
- [Babashka Book](https://book.babashka.org/) - Project structure and organization
- [cli-tools](https://github.com/hlship/cli-tools) - Subcommand patterns
- [Bash and Babashka Equivalents](https://github.com/babashka/babashka/wiki/Bash-and-Babashka-equivalents) - Migration patterns
