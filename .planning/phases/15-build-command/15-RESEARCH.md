# Phase 15: Build Command - Research

**Researched:** 2026-01-20
**Domain:** CLI build subcommand, state persistence, flag parsing
**Confidence:** HIGH

## Summary

This research analyzes the existing Babashka v2.0 codebase (Phases 13-14) and the bash v1.2 implementation to understand how to implement the `build` subcommand. The foundation is solid: `docker/build.clj` already has image building with spinner, `cli.clj` has dispatch infrastructure, and `util.clj` has `state-dir` for XDG-compliant state storage.

The key gap is wiring: adding the `build` subcommand to dispatch, parsing `--with-claude[=version]` flags, validating versions, persisting state to EDN, and showing enhanced success feedback.

**Primary recommendation:** Extend existing `cli.clj` dispatch table with a `build` entry that uses `babashka.cli` spec for flag parsing, add `state.clj` module for EDN persistence, and enhance build success output to show harness versions.

## Existing Codebase Analysis

### What Phase 13-14 Delivered

| Module | Location | What It Does | Status |
|--------|----------|--------------|--------|
| `core.clj` | `src/aishell/core.clj` | Main entry, version, dynamic CLI require | Complete |
| `cli.clj` | `src/aishell/cli.clj` | Dispatch table, global spec, error handling | Complete - needs `build` entry |
| `docker.clj` | `src/aishell/docker.clj` | Docker availability, image checks, labels | Complete |
| `docker/build.clj` | `src/aishell/docker/build.clj` | `build-base-image` with spinner, caching | Complete - already accepts `:with-claude` etc. |
| `docker/spinner.clj` | `src/aishell/docker/spinner.clj` | `with-spinner` for TTY animation | Complete |
| `docker/hash.clj` | `src/aishell/docker/hash.clj` | SHA-256 hash (12 chars) | Complete |
| `docker/templates.clj` | `src/aishell/docker/templates.clj` | Embedded Dockerfile, entrypoint, bashrc | Complete |
| `docker/extension.clj` | `src/aishell/docker/extension.clj` | Per-project `.aishell/Dockerfile` support | Complete |
| `output.clj` | `src/aishell/output.clj` | Colors, error/warn, command suggestions | Complete |
| `util.clj` | `src/aishell/util.clj` | Paths, `state-dir`, `ensure-dir` | Complete |

### Current CLI Dispatch Structure

```clojure
;; src/aishell/cli.clj (current)
(def dispatch-table
  [{:cmds [] :spec global-spec :fn handle-default}])
```

The dispatch table is ready for expansion. Adding `build` requires a new entry:

```clojure
{:cmds ["build"] :fn handle-build :spec build-spec}
```

### Existing build.clj API

`build-base-image` already accepts the options we need:

```clojure
(defn build-base-image
  "Options:
   - :with-claude - Include Claude Code
   - :with-opencode - Include OpenCode
   - :claude-version - Specific Claude version
   - :opencode-version - Specific OpenCode version
   - :force - Bypass cache check
   - :verbose - Show full build output
   - :quiet - Suppress all output except errors"
  [{:keys [force verbose quiet] :as opts}]
  ...)
```

This is already wired to pass `--build-arg WITH_CLAUDE=true` etc. to Docker.

### Missing: State Persistence

`util.clj` provides `state-dir` but no EDN read/write. Need new `state.clj` module.

## Bash v1.2 Build Implementation

### Build Command Flags (v1.2)

```bash
--with-claude           # Include Claude Code (latest)
--with-opencode         # Include OpenCode (latest)
--claude-version=X.Y.Z  # Specific Claude version
--opencode-version=X.Y.Z # Specific OpenCode version
--no-cache              # Force rebuild
-v, --verbose           # Show detailed output
```

### State Format (v1.2 - Bash)

Location: `$XDG_STATE_HOME/aishell/builds/<project-hash>.state`

```bash
# aishell build state - DO NOT EDIT MANUALLY
# Project: /path/to/project
# Built: 2026-01-20T10:30:00-05:00
BUILD_WITH_CLAUDE=true
BUILD_WITH_OPENCODE=false
BUILD_CLAUDE_VERSION="2.0.22"
BUILD_OPENCODE_VERSION=""
BUILD_IMAGE_TAG="aishell:claude-2.0.22"
BUILD_TIMESTAMP="2026-01-20T10:30:00-05:00"
```

### v2.0 Changes Per CONTEXT.md

**Different from v1.2:**
1. **Global state, not per-project:** Store in `~/.aishell/state.edn`, not per-project hash
2. **Single flag design:** `--with-claude=1.0.0` instead of separate `--with-claude` and `--claude-version`
3. **Reset semantics:** Every `build` resets state to exactly what was passed
4. **No `--force`:** Builds always rebuild (Docker layer cache still applies)

## EDN State Management

### State File Location

Per CONTEXT.md: `~/.aishell/state.edn` (global, not per-project)

```clojure
;; Using existing util.clj
(defn state-file []
  (str (fs/path (util/config-dir) "state.edn")))
;; Result: ~/.aishell/state.edn
```

Note: This differs from v1.2's per-project state. The CONTEXT.md explicitly says "global, not per-project".

### State Schema (EDN)

```clojure
{:with-claude true            ; boolean - include Claude Code
 :with-opencode false         ; boolean - include OpenCode
 :claude-version "2.0.22"     ; string or nil - pinned version
 :opencode-version nil        ; string or nil - pinned version
 :image-tag "aishell:base"    ; string - built image tag
 :build-time "2026-01-20T10:30:00Z"}  ; ISO timestamp
```

### Read/Write Pattern

```clojure
(ns aishell.state
  (:require [babashka.fs :as fs]
            [clojure.edn :as edn]
            [aishell.util :as util]))

(defn state-file []
  (str (fs/path (util/config-dir) "state.edn")))

(defn read-state []
  (let [path (state-file)]
    (if (fs/exists? path)
      (edn/read-string (slurp path))
      nil)))

(defn write-state [state]
  (let [path (state-file)]
    (util/ensure-dir (util/config-dir))
    (spit path (pr-str state))))
```

### Why EDN Over Other Formats

| Format | Pros | Cons | Decision |
|--------|------|------|----------|
| EDN | Native Clojure, no deps, pr-str/read-string | - | **Use this** |
| JSON | Universal | Needs cheshire, loses keywords | Skip |
| YAML | Already in project | Overkill for simple state | Skip |
| Properties | Simple | Not idiomatic Clojure | Skip |

## Integration Points

### Adding Build Subcommand to Dispatch

```clojure
;; In cli.clj
(def build-spec
  {:with-claude   {:coerce :string :desc "Include Claude Code [=VERSION]"}
   :with-opencode {:coerce :string :desc "Include OpenCode [=VERSION]"}
   :verbose       {:alias :v :coerce :boolean :desc "Show build output"}
   :help          {:alias :h :coerce :boolean :desc "Show help"}})

(def dispatch-table
  [{:cmds ["build"] :fn handle-build :spec build-spec}
   {:cmds [] :spec global-spec :fn handle-default}])
```

### Flag Parsing: --with-claude[=version]

babashka.cli supports `--flag=value` syntax natively. For optional values:

```clojure
;; --with-claude       -> {:with-claude "true"} when coerce :string
;; --with-claude=1.0.0 -> {:with-claude "1.0.0"}

;; Parse logic:
(defn parse-with-flag [value]
  (cond
    (nil? value) nil                    ; flag not present
    (= value "true") {:enabled? true}   ; --with-claude (no value)
    (= value "latest") {:enabled? true} ; --with-claude=latest
    :else {:enabled? true :version value})) ; --with-claude=1.0.0
```

### Version Validation

From CONTEXT.md: "Strict semver validation at parse time (reject invalid formats before build starts)"

```clojure
(def semver-pattern
  #"^\d+\.\d+\.\d+(-[a-zA-Z0-9.-]+)?(\+[a-zA-Z0-9.-]+)?$")

(def dangerous-chars #"[;&|`$(){}\[\]<>!\\]")

(defn validate-version [version name]
  (when version
    (cond
      (re-find dangerous-chars version)
      (output/error (str "Invalid " name " version: contains shell metacharacters"))

      (not (re-matches semver-pattern version))
      (output/error (str "Invalid " name " version format: " version
                        "\nExpected: X.Y.Z (e.g., 2.0.22, 1.0.0-beta.1)")))))
```

### Build Success Output

Per CONTEXT.md: "Success summary shows: image name, size, duration, installed harness versions"

Current `build-base-image` returns:
```clojure
{:success true :image "aishell:base"}
```

Enhanced output needed:
```
Built aishell:base (45.2s, 1.2GB)
  Claude Code: 2.0.22
  OpenCode: latest
```

### Wiring to Existing build-base-image

```clojure
(defn handle-build [{:keys [opts]}]
  ;; 1. Parse flags
  (let [claude-config (parse-with-flag (:with-claude opts))
        opencode-config (parse-with-flag (:with-opencode opts))]

    ;; 2. Validate versions
    (when-let [v (:version claude-config)]
      (validate-version v "Claude Code"))
    (when-let [v (:version opencode-config)]
      (validate-version v "OpenCode"))

    ;; 3. Check Docker
    (docker/check-docker!)

    ;; 4. Show "Replacing existing image..." if applicable
    (when (docker/image-exists? "aishell:base")
      (println "Replacing existing image..."))

    ;; 5. Call existing build function
    (let [result (build/build-base-image
                   {:with-claude (:enabled? claude-config)
                    :with-opencode (:enabled? opencode-config)
                    :claude-version (:version claude-config)
                    :opencode-version (:version opencode-config)
                    :verbose (:verbose opts)
                    :force true})]  ; Always rebuild per CONTEXT.md

      ;; 6. Persist state
      (state/write-state
        {:with-claude (:enabled? claude-config)
         :with-opencode (:enabled? opencode-config)
         :claude-version (:version claude-config)
         :opencode-version (:version opencode-config)
         :image-tag (:image result)
         :build-time (java.time.Instant/now)})

      ;; 7. Show enhanced success output
      (print-build-summary result claude-config opencode-config))))
```

## Key Questions Resolved

### Q1: Where does state go?
**A:** `~/.aishell/state.edn` (global). Per CONTEXT.md: "Flags persisted in `~/.aishell/state.edn` (global, not per-project)"

This differs from v1.2's per-project state. In v2.0, there's one global build that all projects share.

### Q2: How to parse --with-claude vs --with-claude=1.0.0?
**A:** Use `:coerce :string` in babashka.cli spec. When no value provided, babashka.cli returns "true". Parse that:
- `nil` = flag not present
- `"true"` = flag present, no version (latest)
- `"latest"` = explicit latest
- other = version string to validate

### Q3: What happens when build is called without flags?
**A:** Per CONTEXT.md: "aishell build with no flags = base image only, clears any previous flags"

State is reset to `{:with-claude false :with-opencode false ...}`.

### Q4: Should we use Docker's --no-cache?
**A:** Claude's discretion per CONTEXT.md. Recommendation: Don't use `--no-cache` by default (leverage Docker layer cache for speed). The `update` command in Phase 17 will use `--no-cache`.

### Q5: How many error lines to show?
**A:** Claude's discretion per CONTEXT.md. Recommendation: 15 lines - enough context without overwhelming.

### Q6: Warn when clearing previous flags?
**A:** Claude's discretion per CONTEXT.md. Recommendation: No warning - the mental model is "build is explicit", users expect what they pass is what they get.

## Implementation Approach

### New Module: state.clj

```
src/aishell/state.clj
```

Responsibilities:
- `state-file` - returns `~/.aishell/state.edn`
- `read-state` - read and parse EDN, return nil if missing
- `write-state` - ensure dir exists, write EDN

### Changes to cli.clj

1. Add `build-spec` for flag definitions
2. Add `handle-build` function
3. Add `build` entry to dispatch table
4. Add build-specific help

### Changes to docker/build.clj

1. Enhance return value to include more info for summary
2. Consider capturing error output for better error messages

### No Changes Needed

- `docker.clj` - already has all needed functions
- `spinner.clj` - already works
- `hash.clj` - already works
- `templates.clj` - already has all templates
- `output.clj` - already has error/warn
- `util.clj` - already has paths and ensure-dir

## Common Pitfalls

### Pitfall 1: Flag Coercion Type
**What goes wrong:** Using `:coerce :boolean` for `--with-claude` loses version info
**Why it happens:** Boolean coercion converts any value to true/false
**How to avoid:** Use `:coerce :string`, parse manually
**Warning signs:** Version always appears as nil

### Pitfall 2: Missing Parent Directory
**What goes wrong:** `spit` fails when `~/.aishell` doesn't exist
**Why it happens:** First-time users don't have config dir
**How to avoid:** Call `util/ensure-dir` before writing
**Warning signs:** FileNotFoundException on first build

### Pitfall 3: Nil Version vs Empty String
**What goes wrong:** State has `""` instead of `nil` for versions
**Why it happens:** Inconsistent nil handling
**How to avoid:** Normalize empty strings to nil before storage
**Warning signs:** Docker gets `--build-arg CLAUDE_VERSION=` (empty)

### Pitfall 4: Cached Build When User Expects Fresh
**What goes wrong:** User runs `build --with-claude=2.0.23` but gets old version
**Why it happens:** Docker layer cache hit on npm install layer
**How to avoid:** Document that Docker cache is used; `update` uses `--no-cache`
**Warning signs:** Version in container differs from requested

## Code Examples

### Complete build-spec

```clojure
(def build-spec
  {:with-claude   {:coerce :string
                   :desc "Include Claude Code (optional: =VERSION)"}
   :with-opencode {:coerce :string
                   :desc "Include OpenCode (optional: =VERSION)"}
   :verbose       {:alias :v
                   :coerce :boolean
                   :desc "Show full Docker build output"}
   :help          {:alias :h
                   :coerce :boolean
                   :desc "Show this help"}})
```

### Version Validation (Complete)

```clojure
(def semver-pattern
  #"^\d+\.\d+\.\d+(-[a-zA-Z0-9.-]+)?(\+[a-zA-Z0-9.-]+)?$")

(def dangerous-chars #"[;&|`$(){}\[\]<>!\\]")

(defn validate-version
  "Validate version string. Returns nil on success, calls output/error on failure (exits)."
  [version harness-name]
  (when (and version (not= version "true") (not= version "latest"))
    (cond
      (re-find dangerous-chars version)
      (output/error (str "Invalid " harness-name " version: contains shell metacharacters"))

      (not (re-matches semver-pattern version))
      (output/error (str "Invalid " harness-name " version format: " version
                        "\nExpected: X.Y.Z or X.Y.Z-prerelease (e.g., 2.0.22, 1.0.0-beta.1)")))))
```

### State Read/Write (Complete)

```clojure
(ns aishell.state
  (:require [babashka.fs :as fs]
            [clojure.edn :as edn]
            [aishell.util :as util]))

(defn state-file
  "Path to global state file: ~/.aishell/state.edn"
  []
  (str (fs/path (util/config-dir) "state.edn")))

(defn read-state
  "Read state from file, returns nil if file doesn't exist."
  []
  (let [path (state-file)]
    (when (fs/exists? path)
      (edn/read-string (slurp path)))))

(defn write-state
  "Write state to file, creating directory if needed."
  [state]
  (let [path (state-file)]
    (util/ensure-dir (util/config-dir))
    (spit path (pr-str state))))

(defn clear-state
  "Delete state file."
  []
  (let [path (state-file)]
    (when (fs/exists? path)
      (fs/delete path))))
```

## Sources

### Primary (HIGH confidence)
- Existing codebase: `src/aishell/*.clj` - Direct code inspection
- CONTEXT.md decisions - User-locked implementation choices
- bash v1.2 implementation - `git show main:aishell`

### Secondary (MEDIUM confidence)
- [babashka/cli GitHub](https://github.com/babashka/cli) - Dispatch and spec patterns
- [babashka/cli API.md](https://github.com/babashka/cli/blob/main/API.md) - Function signatures
- [Babashka book](https://book.babashka.org/) - EDN file patterns

## Metadata

**Confidence breakdown:**
- Existing codebase: HIGH - Direct code inspection
- Flag parsing: HIGH - babashka.cli docs verified
- State persistence: HIGH - Standard Clojure EDN patterns
- Integration: HIGH - Existing build.clj already has the API

**Research date:** 2026-01-20
**Valid until:** 2026-02-20 (stable patterns, no fast-moving dependencies)
