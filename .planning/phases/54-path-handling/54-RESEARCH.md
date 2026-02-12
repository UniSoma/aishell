# Phase 54: Path Handling - Research

**Researched:** 2026-02-12
**Domain:** Cross-platform path handling (Windows/Unix) for Babashka with Docker Desktop
**Confidence:** HIGH

## Summary

Phase 54 tackles cross-platform path handling to enable Windows host support while targeting Linux containers via Docker Desktop. The core challenges are: (1) home directory resolution differs between Windows (USERPROFILE) and Unix (HOME), (2) path separators differ (backslash vs forward slash), (3) state/config locations follow platform conventions (LOCALAPPDATA vs XDG), and (4) Docker volume mount paths must be normalized to forward slashes on Windows.

Babashka provides excellent cross-platform path utilities through `babashka.fs`, including `fs/home` (uses Java's `user.home` property, works everywhere), `fs/expand-home` (expands `~`), `fs/path` (constructs paths with OS-native separators), and `fs/unixify` (normalizes to forward slashes for Docker). The phase requires minimal custom logic because babashka.fs handles most platform differences automatically.

Docker Desktop for Windows with WSL2 backend accepts Windows paths with forward slashes (e.g., `C:/Users/name/project`) in volume mount source positions, automatically translating them for the Linux container environment. The key insight: normalize all mount source paths to forward slashes using `fs/unixify` before passing to Docker, but trust `fs/path` for all internal path construction.

**Primary recommendation:** Use `babashka.fs` utilities for all path operations. Replace custom `get-home` with `fs/home`, enhance `expand-path` to normalize through `fs/path`, add platform-specific state directory logic using `LOCALAPPDATA` on Windows, and normalize mount source paths with `fs/unixify` in `build-mount-args` and project mount construction.

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions

**Home directory resolution:**
- On Windows: `USERPROFILE > HOME > fs/home` priority order
- On Unix: keep current `HOME > fs/home` order
- Silent fallback to `fs/home` if all env vars are unset (no warning, no error)
- `$HOME` in config files is abstract shorthand for "home directory" — `get-home` resolves it correctly on both platforms, no `$USERPROFILE` expansion needed
- No `%USERPROFILE%` (Windows cmd.exe syntax) expansion — `~` is the universal shorthand

**Path separator strategy:**
- Keep OS-native separators internally (let `babashka.fs` do its thing)
- Normalize to forward slashes only when constructing Docker commands
- Enforce all path construction through `fs/path` — audit and eliminate string concatenation with hardcoded `/` separators
- `expand-path` output normalized through `fs/path` to prevent mixed-separator paths
- Trust `fs/path` normalization for path comparison — no separate `path-equals?` helper needed

**Config/state locations:**
- Config: `~/.aishell/` on all platforms (same location everywhere, follows Docker convention)
- State on Windows: `LOCALAPPDATA/aishell` (platform-native for invisible state data)
- State on Unix: keep current XDG (`$XDG_STATE_HOME/aishell` or `~/.local/state/aishell`)
- If `LOCALAPPDATA` unset on Windows: fall back to `~/.local/state/aishell` (Unix default)
- All platform-specific path resolution centralized through `util.clj` functions

**Docker mount path conversion:**
- Smart colon parsing: detect drive letter (`X:`) to avoid splitting on it in mount strings
- Normalize mount source paths to forward slashes before passing to Docker
- Project mount destination: `/workspace` on Windows only, keep same-path mount on Unix
- `LOCAL_HOME` set to `/home/developer` on Windows (Windows home path meaningless in Linux container)
- Source-only mounts (e.g., `~/.ssh`) on Windows: map destination under container home (`/home/developer/.ssh`)
- Explicit `source:dest` mounts: trust user's dest path (already targeting container, should be Unix)

### Claude's Discretion

- Exact regex/logic for drive letter detection in mount parsing
- How `build-harness-config-mounts` translates known config directories on Windows
- Whether to extract Docker path utilities into a separate namespace or keep in `run.clj`
- Implementation details of `fs/path` enforcement audit

### Deferred Ideas (OUT OF SCOPE)

None — discussion stayed within phase scope

</user_constraints>

## Standard Stack

### Core

| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| babashka.fs | Built-in | Cross-platform filesystem operations including path construction, normalization, home directory | Official Babashka library, handles Windows/Unix differences transparently, already imported in 16+ files |
| babashka.process | Built-in | Process spawning (used by `git config` calls) | Official process library, cross-platform |
| System/getenv | Java built-in | Reading environment variables (HOME, USERPROFILE, LOCALAPPDATA, XDG_STATE_HOME) | Standard Java interop, cross-platform |

### Supporting

| Library | Version | Purpose | When to Use |
|---------|---------|---------|-------------|
| clojure.string | Built-in | String manipulation for path expansion patterns | Already used throughout codebase for regex replacement |
| java.nio.file.Path | Java built-in | Underlying path representation (via fs/path) | Indirect use through babashka.fs |

### Alternatives Considered

| Instead of | Could Use | Tradeoff |
|------------|-----------|----------|
| fs/home | Custom USERPROFILE/HOME logic | fs/home uses Java's user.home which already handles platform differences correctly |
| fs/unixify | Manual string/replace backslash→forward slash | Manual approach misses edge cases (UNC paths, mixed separators), fs/unixify is tested |
| Platform-specific state-dir | Same location everywhere | Windows convention is LOCALAPPDATA for app state, improves native feel |

**Installation:**
No installation needed — `babashka.fs` ships with Babashka. Already imported in 16 files across the codebase.

## Architecture Patterns

### Recommended Approach

Path handling in aishell should follow a "trust babashka.fs, normalize at boundaries" pattern:

```clojure
(ns aishell.util
  (:require [babashka.fs :as fs]
            [clojure.string :as str]))

;; Internal paths: use fs/path for construction, keep OS-native separators
(defn config-dir []
  (str (fs/path (get-home) ".aishell")))

;; Docker command boundaries: normalize to forward slashes
(defn normalize-for-docker [path]
  (fs/unixify path))
```

This approach:
- Leverages babashka.fs built-in platform handling
- Minimizes custom platform-specific code
- Normalizes paths only where external systems (Docker) require it
- Maintains OS-native paths internally for better host integration

### Pattern 1: Home Directory Resolution with Platform Priority

**What:** Get home directory with Windows-specific environment variable priority
**When to use:** All code needing home directory path (config, state, expansion)
**Example:**

```clojure
;; Source: Phase 54 user decision + babashka.fs documentation
(defn get-home
  "Get user home directory (cross-platform).
   Windows: USERPROFILE > HOME > fs/home
   Unix: HOME > fs/home
   Silent fallback to fs/home ensures function always succeeds."
  []
  (if (fs/windows?)
    ;; Windows priority order
    (or (System/getenv "USERPROFILE")
        (System/getenv "HOME")
        (str (fs/home)))
    ;; Unix priority order
    (or (System/getenv "HOME")
        (str (fs/home)))))
```

**Why this pattern:**
- Respects Windows convention (USERPROFILE primary) while keeping HOME as fallback
- `fs/home` uses Java's `user.home` property as ultimate fallback (never fails)
- No warnings/errors on fallback — silent success reduces noise
- Centralized in `util.clj` for consistent behavior

### Pattern 2: Path Expansion with Normalization

**What:** Expand `~` and `$HOME` in paths, normalize separators
**When to use:** Processing user-provided paths from config files, CLI arguments
**Example:**

```clojure
;; Source: Phase 54 user decision + Context7 babashka.fs
(defn expand-path
  "Expand ~ and $HOME in path string, normalize separators.
   Works on both Windows and Unix."
  [path]
  (when path
    (let [home (get-home)
          expanded (-> path
                       (str/replace #"^~(?=/|\\|$)" home)
                       (str/replace #"\$HOME(?=/|\\|$)" home)
                       (str/replace #"\$\{HOME\}(?=/|\\|$)" home))]
      ;; Normalize through fs/path to get OS-native separators
      (str (fs/path expanded)))))
```

**Why this pattern:**
- Handles both Unix (`/`) and Windows (`\`) path separators in regex lookahead
- `fs/path` normalization prevents mixed-separator paths
- Returns string (not Path object) for backward compatibility
- No `%USERPROFILE%` expansion (cmd.exe syntax out of scope per user decision)

### Pattern 3: Platform-Specific State Directory

**What:** Use platform-native conventions for state directory location
**When to use:** Determining where to store application state (build flags, runtime data)
**Example:**

```clojure
;; Source: Phase 54 user decision
(defn state-dir
  "Get platform-appropriate state directory for aishell.
   Windows: LOCALAPPDATA/aishell (fallback to XDG default)
   Unix: XDG_STATE_HOME/aishell or ~/.local/state/aishell"
  []
  (if (fs/windows?)
    ;; Windows: prefer LOCALAPPDATA, fallback to XDG default
    (let [localappdata (System/getenv "LOCALAPPDATA")
          fallback (str (fs/path (get-home) ".local" "state"))]
      (str (fs/path (or localappdata fallback) "aishell")))
    ;; Unix: XDG or ~/.local/state/aishell
    (let [xdg-state (or (System/getenv "XDG_STATE_HOME")
                        (str (fs/path (get-home) ".local" "state")))]
      (str (fs/path xdg-state "aishell")))))
```

**Why this pattern:**
- Windows: `LOCALAPPDATA` is standard for hidden app data (e.g., `C:\Users\Name\AppData\Local\aishell`)
- Unix: Respects XDG Base Directory Specification
- Graceful fallback maintains cross-platform consistency
- Config stays at `~/.aishell` on all platforms (user decision: config unified, state platform-native)

### Pattern 4: Docker Volume Mount Path Normalization

**What:** Normalize volume mount source paths to forward slashes for Docker Desktop
**When to use:** Building `-v` flags for `docker run`
**Example:**

```clojure
;; Source: Phase 54 user decision + Docker Desktop WSL2 research
(defn- normalize-mount-source
  "Normalize mount source path for Docker Desktop.
   On Windows: converts backslashes to forward slashes (Docker Desktop WSL2 accepts C:/Users/...)
   On Unix: no-op (already forward slashes)
   Note: Docker Desktop for Windows handles drive letters (C:/) correctly with forward slashes."
  [source-path]
  (if (fs/windows?)
    (fs/unixify source-path)  ; Convert to forward slashes
    source-path))  ; Unix paths already correct

(defn- build-mount-args
  "Build -v flags from mounts config.
   Supports:
   - source-only: ~/.ssh (mounts at container home on Windows, same path on Unix)
   - source:dest: /host/path:/container/path"
  [mounts]
  (when (seq mounts)
    (->> mounts
         (mapcat
           (fn [mount]
             (let [mount-str (str mount)
                   ;; Smart colon parsing: detect drive letter (X:/) to avoid splitting on it
                   [source dest] (parse-mount-string mount-str)
                   source (expand-path source)
                   dest (if dest
                          dest  ; Explicit dest: trust user (container path)
                          (if (fs/windows?)
                            ;; Windows source-only: map under container home
                            (str "/home/developer/" (fs/file-name source))
                            ;; Unix source-only: same path
                            source))]
               (if (fs/exists? source)
                 ["-v" (str (normalize-mount-source source) ":" dest)]
                 (do
                   (output/warn (str "Mount source does not exist: " source))
                   []))))))))

(defn- parse-mount-string
  "Parse mount string 'source' or 'source:dest'.
   Smart colon parsing: detect Windows drive letter (C:/) to avoid splitting on it.
   Returns [source dest] where dest is nil for source-only mounts."
  [mount-str]
  (if (re-matches #"^[A-Za-z]:[/\\].*" mount-str)
    ;; Windows absolute path with drive letter
    (if-let [idx (str/index-of mount-str ":" 2)]  ; Find colon AFTER drive letter
      [(subs mount-str 0 idx) (subs mount-str (inc idx))]
      [mount-str nil])
    ;; Unix path or relative path: split on first colon
    (if-let [idx (str/index-of mount-str ":")]
      [(subs mount-str 0 idx) (subs mount-str (inc idx))]
      [mount-str nil])))
```

**Why this pattern:**
- Docker Desktop for Windows (WSL2 backend) accepts Windows paths with forward slashes (verified)
- Drive letter detection: look for `X:/` or `X:\` at start, find next colon for mount separator
- `fs/unixify` handles all separator normalization (including UNC paths, edge cases)
- Source-only mounts on Windows map under container home (Windows host path meaningless in Linux container)
- Explicit dest paths trusted (user targeting container, should already be Unix-style)

### Pattern 5: Project Mount with Platform-Specific Destination

**What:** Mount project directory at OS-appropriate container path
**When to use:** Building `docker run` arguments for project workspace mount
**Example:**

```clojure
;; Source: Phase 54 user decision
(defn- build-project-mount
  "Build -v and -w flags for project directory mount.
   Windows: mounts at /workspace (host path meaningless in Linux container)
   Unix: mounts at same path (preserves absolute paths in container)"
  [project-dir]
  (let [source (normalize-mount-source project-dir)
        dest (if (fs/windows?) "/workspace" project-dir)]
    ["-v" (str source ":" dest)
     "-w" dest]))

;; In build-docker-args-internal:
;; Replace:
;;   "-v" (str project-dir ":" project-dir)
;;   "-w" project-dir
;; With:
;;   (into (build-project-mount project-dir))
```

**Why this pattern:**
- Windows: `/workspace` convention simplifies container paths, avoids exposing Windows-specific `C:/Users/...` in container
- Unix: Same-path mounting preserves absolute path semantics (scripts can use `pwd` reliably)
- Normalization ensures Docker Desktop accepts the source path
- Working directory (`-w`) matches mount destination

### Pattern 6: Container Home Environment Variable (LOCAL_HOME)

**What:** Set LOCAL_HOME to container home path, not host home path
**When to use:** Building environment variables for docker run
**Example:**

```clojure
;; Source: Phase 54 user decision
(defn- build-docker-args-internal
  [{:keys [project-dir ...]}]
  (let [home (get-home)
        container-home (if (fs/windows?) "/home/developer" home)]
    (-> ["docker" "run" ...]
        ;; ...
        (into ["-e" (str "LOCAL_HOME=" container-home)])
        ;; ...
        )))
```

**Why this pattern:**
- Windows host home (`C:\Users\Name`) is meaningless in Linux container
- Container home is always `/home/developer` (from entrypoint user creation)
- Unix: host home often matches container home (`/home/username`), passing through preserves this
- Entrypoint uses LOCAL_HOME to configure container user's home directory

### Anti-Patterns to Avoid

- **Don't use string concatenation with hardcoded `/` for path construction:** Use `fs/path` instead. String concatenation creates mixed-separator paths on Windows.
- **Don't normalize paths to forward slashes internally:** Keep OS-native separators internally (via `fs/path`). Only normalize at Docker command boundary using `fs/unixify`.
- **Don't assume XDG directories on Windows:** Windows has `LOCALAPPDATA` convention. Use platform-specific state directory logic.
- **Don't parse mount strings with naive `str/split` on `:`:** Windows drive letters contain colons (`C:`). Use smart parsing that detects drive letters first.
- **Don't use `%USERPROFILE%` expansion:** This is cmd.exe-specific syntax. Use `~` as universal shorthand, resolve via `get-home`.

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| Path construction | `(str dir "/" file)` | `(fs/path dir file)` | String concat creates mixed separators on Windows; fs/path handles platform differences |
| Home directory | Custom USERPROFILE/HOME/fallback logic | `(fs/home)` for base, wrap with env var priority | fs/home uses Java's user.home (cross-platform); only add env var priority layer |
| Tilde expansion | Manual `str/replace` with `~` | `(fs/expand-home path)` for `~` only, custom for `$HOME` | fs/expand-home handles `~username` expansion; `$HOME` is shell-specific so custom is appropriate |
| Forward slash conversion | Manual `str/replace` `\` with `/` | `(fs/unixify path)` | Manual approach misses UNC paths (`\\server\share`), mixed separators, edge cases |
| Path comparison | Custom `path-equals?` with normalization | `(fs/path path1)` normalization + `=` | fs/path normalization sufficient per user decision; Java Path equality is reliable |

**Key insight:** Babashka.fs is designed for cross-platform scripting. Most path operations have built-in functions that handle Windows/Unix differences. Custom logic should only add application-specific semantics (priority order, Docker normalization) on top of fs utilities.

## Common Pitfalls

### Pitfall 1: Mixed Path Separators from String Concatenation

**What goes wrong:** Code using `(str dir "/" file)` creates paths like `C:\Users\Name/.aishell/config.yaml` on Windows (backslash from `get-home`, forward slash from string literal)

**Why it happens:** Java's File API accepts forward slashes on Windows, so mixed separators often work, hiding the issue until specific edge cases fail

**How to avoid:**
- Audit codebase for `(str ... "/" ...)` path construction patterns
- Replace with `(fs/path ...)` which uses OS-native separators
- Trust `fs/path` for all path construction, never hardcode separators

**Warning signs:**
- `grep -rn '(str.*"/"' src/` finds string concatenation with path separators
- Paths displayed to users showing mixed separators
- Path comparison failures on Windows when Unix works

### Pitfall 2: Normalizing Paths Too Early

**What goes wrong:** Converting all paths to forward slashes internally breaks Windows APIs that expect native separators

**Why it happens:** Thinking "Docker needs forward slashes" leads to normalizing everywhere, but Windows filesystem APIs work better with native backslashes

**How to avoid:**
- Keep OS-native separators internally (via `fs/path`)
- Only normalize at Docker command boundary using `fs/unixify`
- Trust babashka.fs to use correct separators for platform

**Warning signs:**
- Calling `fs/unixify` on paths passed to `fs/exists?`, `fs/create-dirs`, etc.
- Normalizing paths immediately after construction instead of at use site
- Config file paths showing forward slashes on Windows (breaks native file pickers)

### Pitfall 3: Naive Colon Splitting in Mount Strings

**What goes wrong:** `(str/split mount-str #":")` splits `C:/Users/name/.ssh:/home/developer/.ssh` into three parts `["C" "/Users/name/.ssh" "/home/developer/.ssh"]`, breaking mount parsing

**Why it happens:** Docker uses `:` as separator, Windows uses `:` in drive letters — collision is inevitable

**How to avoid:**
- Detect Windows absolute path pattern (`^[A-Za-z]:[/\\]`) before splitting
- If drive letter detected, find second colon (index 2+) for mount separator
- If no drive letter, split on first colon as usual

**Warning signs:**
- Mount parsing failing with "invalid mount config" on Windows
- Test cases passing on Unix but failing on Windows for same mount string
- Three-part split result when two parts expected

### Pitfall 4: Assuming `$HOME` Works in Config Files on Windows

**What goes wrong:** Users write `$HOME/.aws` in config, expecting expansion, but Windows users have no `HOME` env var (only `USERPROFILE`)

**Why it happens:** Unix convention assumes `HOME` is universally set, but Windows doesn't set it by default

**How to avoid:**
- Document `~` as universal shorthand (works everywhere after `expand-path`)
- Treat `$HOME` in config as abstract "home directory", resolve via `get-home` which handles USERPROFILE
- Don't introduce `$USERPROFILE` expansion (platform-specific, user decision)

**Warning signs:**
- Config examples showing `$HOME` on Windows
- User reports "path not found" for `$HOME/...` configs on Windows
- Documentation mentioning USERPROFILE variable instead of `~` shorthand

### Pitfall 5: Using XDG State Directory on Windows

**What goes wrong:** Windows creates `~/.local/state/aishell` in user's home, non-standard for Windows apps

**Why it happens:** Copying Unix XDG logic without platform check

**How to avoid:**
- Use `LOCALAPPDATA` on Windows (`C:\Users\Name\AppData\Local\aishell`)
- Keep XDG on Unix (`~/.local/state/aishell`)
- Fallback to XDG default if LOCALAPPDATA unset (ensures cross-platform consistency)

**Warning signs:**
- `.local` directory appearing in Windows user home
- Windows users confused about where state is stored
- State directory not following platform conventions

### Pitfall 6: Exposing Windows Host Paths in Container

**What goes wrong:** Mounting project at `C:/Users/Name/projects/myapp` inside Linux container exposes Windows-specific paths

**Why it happens:** Reusing host path as container path for simplicity

**How to avoid:**
- Windows: mount project at `/workspace` (neutral, container-appropriate)
- Unix: mount at same path (preserves absolute path semantics)
- Set `LOCAL_HOME=/home/developer` on Windows (not `C:\Users\Name`)

**Warning signs:**
- Container seeing `C:/Users/...` in `pwd` output on Windows
- Scripts breaking because they expect `/home/...` or `/workspace/...`
- Environment variables leaking Windows paths into Linux container

## Code Examples

Verified patterns from official sources and phase decisions:

### Home Directory Resolution

```clojure
;; Source: Phase 54 decision + https://github.com/babashka/fs/blob/master/API.md
(require '[babashka.fs :as fs])

;; Built-in fs/home uses Java's user.home property (cross-platform)
(fs/home)
;; => #object[java.nio.file.Path 0x... "/home/username"]  ; Unix
;; => #object[java.nio.file.Path 0x... "C:\\Users\\Name"]  ; Windows

;; Custom priority for aishell (env var overrides)
(defn get-home []
  (if (fs/windows?)
    (or (System/getenv "USERPROFILE")
        (System/getenv "HOME")
        (str (fs/home)))
    (or (System/getenv "HOME")
        (str (fs/home)))))
```

### Path Construction

```clojure
;; Source: https://github.com/babashka/fs/blob/master/API.md
(require '[babashka.fs :as fs])

;; Multi-argument path construction (OS-native separators)
(fs/path "C:\\Users\\Name" ".aishell" "config.yaml")
;; => #object[java.nio.file.Path ... "C:\\Users\\Name\\.aishell\\config.yaml"]  ; Windows

(fs/path "/home/username" ".aishell" "config.yaml")
;; => #object[java.nio.file.Path ... "/home/username/.aishell/config.yaml"]  ; Unix

;; Convert to string for storage/comparison
(str (fs/path (get-home) ".aishell"))
;; => "C:\\Users\\Name\\.aishell"  ; Windows
;; => "/home/username/.aishell"    ; Unix
```

### Path Normalization for Docker

```clojure
;; Source: https://github.com/babashka/fs/blob/master/API.md
(require '[babashka.fs :as fs])

;; Normalize to forward slashes (for Docker commands)
(fs/unixify "C:\\Users\\Name\\projects\\myapp")
;; => "C:/Users/Name/projects/myapp"

(fs/unixify "/home/username/projects/myapp")
;; => "/home/username/projects/myapp"  ; No-op on Unix

;; Use at Docker command boundary
(defn build-volume-mount [host-path container-path]
  (let [normalized-source (if (fs/windows?)
                             (fs/unixify host-path)
                             host-path)]
    ["-v" (str normalized-source ":" container-path)]))
```

### Tilde Expansion

```clojure
;; Source: https://github.com/babashka/fs/blob/master/API.md
(require '[babashka.fs :as fs])

;; Built-in expand-home handles ~ prefix
(fs/expand-home "~/.aishell")
;; => #object[java.nio.file.Path ... "C:\\Users\\Name\\.aishell"]  ; Windows
;; => #object[java.nio.file.Path ... "/home/username/.aishell"]    ; Unix

;; Custom expand-path adds $HOME support and normalization
(defn expand-path [path]
  (when path
    (let [home (get-home)
          expanded (-> path
                       (str/replace #"^~(?=/|\\|$)" home)
                       (str/replace #"\$HOME(?=/|\\|$)" home))]
      (str (fs/path expanded)))))
```

### Platform Detection

```clojure
;; Source: https://github.com/babashka/fs/blob/master/API.md
(require '[babashka.fs :as fs])

(fs/windows?)
;; => true   ; on Windows
;; => false  ; on Unix/Linux/macOS

;; Use for platform-specific logic
(if (fs/windows?)
  (System/getenv "LOCALAPPDATA")
  (System/getenv "XDG_STATE_HOME"))
```

### Existing Codebase Patterns (for comparison)

```clojure
;; Current code at src/aishell/util.clj:5-10 (will be updated)
(defn get-home
  "Get user home directory (cross-platform).
   Prefers HOME env var (works with network logins), falls back to fs/home."
  []
  (or (System/getenv "HOME")
      (str (fs/home))))

;; Current code at src/aishell/util.clj:29-34 (will be updated)
(defn state-dir
  "Get XDG state directory for aishell.
   Respects XDG_STATE_HOME if set, otherwise ~/.local/state/aishell."
  []
  (let [xdg-state (or (System/getenv "XDG_STATE_HOME")
                      (str (fs/path (get-home) ".local" "state")))]
    (str (fs/path xdg-state "aishell"))))

;; Current code at src/aishell/docker/run.clj:40-63 (will be updated)
(defn- build-mount-args
  "Build -v flags from mounts config.
   Supports:
   - source-only: ~/.ssh (mounts at same path)
   - source:dest: /host/path:/container/path"
  [mounts]
  (when (seq mounts)
    (->> mounts
         (mapcat
           (fn [mount]
             (let [mount-str (str mount)
                   [source dest] (if (str/includes? mount-str ":")
                                   (str/split mount-str #":" 2)
                                   [mount-str mount-str])
                   source (util/expand-path source)
                   dest (util/expand-path dest)]
               (if (fs/exists? source)
                 ["-v" (str source ":" dest)]
                 (do
                   (output/warn (str "Mount source does not exist: " source))
                   []))))))))

;; Current code at src/aishell/docker/run.clj:242-248 (will be updated)
;; In build-docker-args-internal:
(into [;; Project mount at same path
       "-v" (str project-dir ":" project-dir)
       "-w" project-dir
       ;; User identity for entrypoint
       "-e" (str "LOCAL_UID=" uid)
       "-e" (str "LOCAL_GID=" gid)
       "-e" (str "LOCAL_HOME=" home)])
```

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| Unix-only HOME | Platform-specific env vars (USERPROFILE/HOME) | Phase 54 (v3.1.0) | Windows home directory correctly resolved |
| Hardcoded `/` in path construction | `fs/path` for all path building | Phase 54 (v3.1.0) | OS-native separators, no mixed-separator paths |
| XDG everywhere | Platform-native state directories (LOCALAPPDATA/XDG) | Phase 54 (v3.1.0) | Windows apps follow Windows conventions |
| Same-path mounts | Platform-specific destinations (/workspace on Windows) | Phase 54 (v3.1.0) | Container paths independent of host OS quirks |
| Naive colon split | Smart drive letter detection | Phase 54 (v3.1.0) | Windows mount parsing works correctly |

**Deprecated/outdated:**
- String concatenation for paths: Use `fs/path` instead
- Unix-only path assumptions: Use platform checks and babashka.fs utilities
- `$HOME` as Windows-specific: Treat as abstract "home", resolve via `get-home`

## Open Questions

1. **Drive letter case sensitivity in regex**
   - What we know: Windows drive letters can be lowercase or uppercase (`c:` or `C:`)
   - What's unclear: Should regex be case-insensitive `[A-Za-z]` or rely on Windows normalizing to uppercase?
   - Recommendation: Use case-insensitive `[A-Za-z]` for robustness (LOW confidence - regex pattern choice)

2. **LOCALAPPDATA reliability on all Windows versions**
   - What we know: LOCALAPPDATA is standard since Windows Vista
   - What's unclear: Is it set in all contexts (normal login, network login, service accounts)?
   - Recommendation: Include fallback to XDG default as user decision specifies (HIGH confidence - fallback handles edge cases)

3. **Docker Desktop path translation for `/mnt/c/` vs `/c/`**
   - What we know: WSL2 backend uses `/mnt/c/`, Hyper-V uses `/c/`, but Docker Desktop handles this internally
   - What's unclear: Do we need to detect backend and translate, or does Docker Desktop accept `C:/` directly?
   - Recommendation: Research confirms Docker Desktop accepts `C:/Users/...` with forward slashes, handles translation internally. Use `fs/unixify` without backend detection (HIGH confidence - verified in web search results)

4. **UNC path support (`\\server\share`)**
   - What we know: Windows supports UNC paths for network shares, `fs/unixify` may handle them
   - What's unclear: How does Docker Desktop treat UNC paths? Should we block them or let Docker Desktop handle?
   - Recommendation: Trust `fs/unixify` and Docker Desktop. Add warning if mount fails rather than proactive blocking (MEDIUM confidence - edge case, low priority)

## Codebase Impact Analysis

### Files Requiring Changes

Based on grep analysis and user decisions:

1. **src/aishell/util.clj** (HIGH PRIORITY)
   - `get-home`: Add USERPROFILE priority on Windows
   - `expand-path`: Add Windows separator support, normalize through `fs/path`
   - `state-dir`: Add LOCALAPPDATA logic for Windows

2. **src/aishell/docker/run.clj** (HIGH PRIORITY)
   - `build-mount-args`: Add smart colon parsing, normalize source paths, Windows dest mapping
   - `build-docker-args-internal`: Use `/workspace` on Windows, set LOCAL_HOME to `/home/developer`
   - `build-harness-config-mounts`: Normalize mount sources for Docker

3. **src/aishell/config.clj** (MEDIUM PRIORITY)
   - `global-config-path`: Uses `util/get-home`, indirect benefit from Phase 54 changes
   - `project-config-path`: Uses `fs/path`, already cross-platform

4. **src/aishell/state.clj** (MEDIUM PRIORITY)
   - `state-file`: Uses `util/config-dir` for path, indirect benefit
   - Documentation update: state location differs by platform

### Audit Required

**String concatenation with path separators:**
```bash
grep -rn '(str.*"/"' src/
```
Expected findings: Paths constructed with `(str dir "/" file)` patterns need conversion to `fs/path`

**Direct fs/path usage:**
```bash
grep -rn 'fs/path' src/
```
Verify all usage is correct (returns Path object, must convert to string for storage)

**Path expansion callers:**
```bash
grep -rn 'expand-path' src/
```
Verify all callers expect OS-native separators after Phase 54 changes

### Testing Scope

**Manual testing required (no Windows CI):**
- Windows host with Docker Desktop WSL2 backend
- Verify `get-home` returns correct directory (USERPROFILE path)
- Verify `state-dir` uses LOCALAPPDATA
- Verify volume mounts work with Windows paths
- Verify project mount at `/workspace` works
- Verify harness config mounts (`.claude`, etc.) work

**Test cases to add (if unit testing infrastructure exists):**
- `get-home` priority order (mock env vars)
- `expand-path` with Windows backslash paths
- `parse-mount-string` with drive letters
- `normalize-mount-source` conversion

## Sources

### Primary (HIGH confidence)

- [babashka.fs API documentation](https://github.com/babashka/fs/blob/master/API.md) - `home`, `expand-home`, `path`, `unixify`, `windows?` functions
- [babashka.fs codex documentation](https://github.com/babashka/fs/blob/master/codox/babashka.fs.html) - Detailed function signatures and parameters
- Context7 `/babashka/fs` - Path handling patterns and cross-platform utilities
- Phase 54 CONTEXT.md - User decisions on implementation approach (locked decisions)

### Secondary (MEDIUM confidence)

- [Docker Desktop WSL 2 backend documentation](https://docs.docker.com/desktop/features/wsl/) - WSL2 integration details (verified but lacks specific path format documentation)
- [Docker Community Forums: Mounting paths on WSL2](https://forums.docker.com/t/mounting-path-on-wsl2/143473) - Real-world path format examples (community-verified, multiple confirmations)
- [Docker Community Forums: Mount Windows paths into WSL2](https://forums.docker.com/t/mount-windows-paths-into-wsl2/142812) - WSL2 path translation mechanics
- [Docker Community Forums: What's the correct way to mount a volume on Docker for Windows?](https://forums.docker.com/t/whats-the-correct-way-to-mount-a-volume-on-docker-for-windows/58494) - Volume mount syntax requirements
- [Microsoft Learn: Get started with Docker containers on WSL](https://learn.microsoft.com/en-us/windows/wsl/tutorials/wsl-containers) - Official WSL2 + Docker guidance

### Tertiary (LOW confidence)

- [GitHub Issue: Colons in pathnames (moby/moby #8604)](https://github.com/moby/moby/issues/8604) - Historical context on drive letter colon parsing challenge
- WebSearch: "Docker Desktop Windows accepts forward slashes in volume mount source path" - Multiple sources confirm `C:/Users` format works, but no single authoritative source (verified by cross-referencing community forums)

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH - babashka.fs is official, well-documented, widely used in Babashka ecosystem
- Architecture: HIGH - Patterns based on official babashka.fs documentation and user decisions in CONTEXT.md
- Pitfalls: HIGH - Based on known Windows/Unix path differences and Docker Desktop behavior from official sources and community consensus
- Docker path handling: MEDIUM-HIGH - Community forums confirm `C:/` forward slash format works, but official Docker docs don't explicitly document this (widespread practice evidence strong)

**Research date:** 2026-02-12
**Valid until:** 2026-03-12 (30 days - stable APIs, unlikely to change)

**Phase-specific notes:**
- Phase 54 depends on Phase 53 (platform detection with `fs/windows?`)
- Phase 55 (Host Identity) depends on Phase 54 (needs `get-home`, `expand-path` working on Windows)
- Phase 56 (Process & Execution) depends on Phase 54 (needs mount paths working for docker run)
- User decisions in CONTEXT.md are locked - research focused on implementation details within those constraints

**Codebase context:**
- Babashka project (SCI interpreter), not JVM Clojure
- 16 files already import `babashka.fs` - ecosystem integration exists
- Docker Desktop for Windows with WSL2 backend is target environment (not native Windows containers)
- v3.1.0 adds Windows host support while targeting Linux containers
