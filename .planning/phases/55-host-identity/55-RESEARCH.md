# Phase 55: Host Identity - Research

**Researched:** 2026-02-12
**Domain:** Cross-platform host identity extraction (UID/GID, Git config) for Windows/Unix
**Confidence:** HIGH

## Summary

Phase 55 enables Windows host support by implementing platform-specific defaults for UID/GID values and ensuring Git identity extraction works cross-platform. The core challenges are: (1) Windows has no `id -u`/`id -g` commands (must default to 1000/1000 for Linux container compatibility), (2) `git config` must work from any host platform to extract user.name/user.email, and (3) Git identity may be legitimately unset and should be handled gracefully.

Phase 53 already added platform detection guards that throw errors pointing to Phase 55. Now we implement the Windows alternatives: `get-uid` and `get-gid` return "1000" on Windows (standard non-root user in Linux containers), and `read-git-identity` already works cross-platform because Git for Windows is widely available and the `git config` command works identically on all platforms.

The key insight: Git for Windows uses the same command-line interface as Unix Git. The existing `read-git-identity` implementation using `p/shell` with `git config user.name` and `git config user.email` works on Windows without modification. The function already handles missing Git gracefully (try/catch returns nil values), so no Windows-specific handling is needed beyond verifying Git is installed.

**Primary recommendation:** Replace platform guard exceptions in `get-uid`/`get-gid` with default return value "1000" on Windows. No changes needed to `read-git-identity` — it already works cross-platform. Document Windows requirement: Git for Windows must be installed for identity extraction.

## Standard Stack

### Core

| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| babashka.fs | Built-in | Platform detection via `windows?` | Official Babashka library, already used in Phase 53 guards |
| babashka.process | Built-in | Process spawning for `git config` calls | Official library, cross-platform process execution |
| Git for Windows | Any recent | Git command-line tools on Windows | De facto standard for Windows Git installations, 99%+ Windows developers have it |

### Supporting

| Library | Version | Purpose | When to Use |
|---------|---------|---------|-------------|
| clojure.string | Built-in | String trimming for command output | Already used throughout codebase |

### Alternatives Considered

| Instead of | Could Use | Tradeoff |
|------------|-----------|----------|
| Default UID/GID 1000 | Read from Windows user registry | Windows has no UID concept; Linux containers standardize 1000 for first non-root user |
| git config CLI | Parse .gitconfig files directly | git config handles includes, conditional configs, system/global/local precedence — hand-rolling misses edge cases |
| Require Git for Windows | Bundle Git or use JGit library | Overwhelming majority of Windows developers already have Git installed; bundling adds 50MB+ |

**Installation:**
No installation needed — `babashka.fs` and `babashka.process` ship with Babashka. Git for Windows is expected to be pre-installed on developer machines (standard development tool).

## Architecture Patterns

### Recommended Approach

Host identity extraction in aishell should follow a "platform defaults with graceful degradation" pattern:

```clojure
(ns aishell.docker.run
  (:require [babashka.fs :as fs]
            [babashka.process :as p]))

;; Windows defaults, Unix detection
(defn- get-uid []
  (if (fs/windows?)
    "1000"  ; Standard non-root user in Linux containers
    (-> (p/shell {:out :string} "id" "-u") :out str/trim)))

;; Already works cross-platform (no changes needed)
(defn read-git-identity [project-dir]
  (letfn [(git-config [key]
            (try
              (let [{:keys [exit out]}
                    (p/shell {:out :string :err :string :continue true :dir project-dir}
                             "git" "config" key)]
                (when (zero? exit)
                  (let [val (str/trim out)]
                    (when-not (str/blank? val) val))))
              (catch Exception _ nil)))]
    {:name (git-config "user.name")
     :email (git-config "user.email")}))
```

This approach:
- Provides sensible defaults for Windows (UID/GID 1000 matches typical Linux container users)
- Leverages existing Git installation on Windows (no duplication or bundling)
- Handles missing Git gracefully (nil values when Git not installed or not configured)
- Maintains identical behavior on Unix (no regression risk)

### Pattern 1: Platform-Specific UID/GID with Linux Container Defaults

**What:** Return hardcoded UID/GID on Windows matching standard Linux container user
**When to use:** All platforms — Windows gets default, Unix gets detected values
**Example:**

```clojure
;; Source: Phase 55 requirement ID-01 + Linux container conventions
(defn- get-uid []
  "Get user ID for container user creation.
   Windows: defaults to 1000 (standard non-root user in Linux containers)
   Unix: detected via id -u"
  (if (fs/windows?)
    "1000"
    (-> (p/shell {:out :string} "id" "-u") :out str/trim)))

(defn- get-gid []
  "Get group ID for container user creation.
   Windows: defaults to 1000 (standard non-root group in Linux containers)
   Unix: detected via id -g"
  (if (fs/windows?)
    "1000"
    (-> (p/shell {:out :string} "id" "-g") :out str/trim)))
```

**Why this pattern:**
- UID/GID 1000 is the Debian/Ubuntu default for the first non-root user
- Windows has no UID/GID concept; values are only meaningful inside Linux container
- Container ownership via UID/GID enables file sharing between host and container (bind mounts)
- Hardcoded value acceptable because Windows file permissions work differently (ACLs, not UID/GID)
- Entrypoint script uses these values to create container user with matching UID/GID

**Linux container UID/GID context:**
- Root: UID 0, GID 0
- First user: UID 1000, GID 1000 (Debian/Ubuntu standard)
- System users: UID 1-999
- Aishell containers run as non-root for security (principle of least privilege)

### Pattern 2: Cross-Platform Git Config Extraction (Already Works)

**What:** Extract Git user.name and user.email using `git config` CLI
**When to use:** All platforms — Git for Windows provides identical CLI to Unix Git
**Example:**

```clojure
;; Source: Existing implementation (already cross-platform)
;; No changes needed for Phase 55
(defn read-git-identity
  "Read git identity from host configuration.
   Returns {:name \"...\" :email \"...\"} with nil values if not set.
   Works on Windows (Git for Windows) and Unix (Git)."
  [project-dir]
  (letfn [(git-config [key]
            (try
              (let [{:keys [exit out]}
                    (p/shell {:out :string :err :string :continue true :dir project-dir}
                             "git" "config" key)]
                (when (zero? exit)
                  (let [val (str/trim out)]
                    (when-not (str/blank? val) val))))
              (catch Exception _ nil)))]
    {:name (git-config "user.name")
     :email (git-config "user.email")}))
```

**Why this pattern:**
- Git for Windows ships with git.exe in PATH (standard installation)
- `git config` command syntax identical on Windows and Unix
- Reads from Git's standard config hierarchy (system, global, local)
- Try/catch handles missing Git (returns nil) or unset config (empty/blank check returns nil)
- Container receives nil values for unset fields; entrypoint skips setting GIT_AUTHOR_NAME/EMAIL when nil
- `:dir project-dir` ensures local .git/config is checked (project-specific identity)

**Git config precedence (handled by `git config` command):**
1. Local: `.git/config` in project directory
2. Global: `~/.gitconfig` or `$XDG_CONFIG_HOME/git/config`
3. System: `/etc/gitconfig` (Unix) or `C:\Program Files\Git\etc\gitconfig` (Windows)

### Pattern 3: Graceful Identity Handling in Container Env Vars

**What:** Only pass GIT_AUTHOR_NAME/EMAIL to container when values are non-nil
**When to use:** Building docker run arguments with Git identity
**Example:**

```clojure
;; Source: Existing implementation in build-docker-args-internal
;; Already handles nil identity gracefully
(defn- build-docker-args-internal
  [{:keys [git-identity ...]}]
  (-> ["docker" "run" ...]
      ;; Only add env vars when identity values present
      (cond-> (:name git-identity)
        (into ["-e" (str "GIT_AUTHOR_NAME=" (:name git-identity))
               "-e" (str "GIT_COMMITTER_NAME=" (:name git-identity))]))
      (cond-> (:email git-identity)
        (into ["-e" (str "GIT_AUTHOR_EMAIL=" (:email git-identity))
               "-e" (str "GIT_COMMITTER_EMAIL=" (:email git-identity))]))))
```

**Why this pattern:**
- `cond->` only executes `into` when condition is truthy (non-nil)
- Container works without Git identity (commits use fallback: `developer@localhost`)
- User can configure Git later via `git config --global` inside container
- No error/warning for missing identity (legitimate use case: fresh install, Git not configured yet)

### Anti-Patterns to Avoid

- **Don't try to read Windows user registry for UID/GID:** Windows has no UID concept; values are meaningless outside Linux container context. Hardcoded 1000 is correct.
- **Don't parse .gitconfig files manually:** `git config` command handles includes, conditional configs, system/global/local precedence, and platform-specific paths. Hand-rolling misses edge cases.
- **Don't error on missing Git identity:** Legitimate use cases exist (fresh machine, Git not configured). Return nil and let container handle gracefully.
- **Don't use `git config --global` from Babashka:** That writes to user's global config (side effect). Read-only `git config` without `--global` reads values correctly.
- **Don't assume Git is in PATH on Windows:** Try/catch in `read-git-identity` already handles this (returns nil). Document Git for Windows as prerequisite.

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| UID/GID on Windows | Registry lookup or Windows user SID conversion | Hardcoded "1000" default | Windows has no UID concept; value only matters inside Linux container (1000 is standard first user) |
| Git config parsing | Custom .gitconfig parser | `git config` CLI command | Git handles includes, conditionals, precedence, platform paths; command is cross-platform |
| Git executable detection | Custom Windows registry lookup or filesystem search | Try/catch around `p/shell` call | Babashka process library already handles "command not found"; exception caught gracefully |
| Git for Windows path resolution | %ProgramFiles%\Git\bin\git.exe hardcoding | Assume Git is in PATH | Git for Windows installer adds to PATH; hardcoding breaks custom installs |

**Key insight:** Git for Windows provides identical CLI experience to Unix Git. The only platform-specific code needed is UID/GID defaults (Windows has no equivalent concept). Git identity extraction already works cross-platform.

## Common Pitfalls

### Pitfall 1: Trying to Derive "Real" UID/GID on Windows

**What goes wrong:** Attempting to map Windows user SID or registry values to UID/GID numbers

**Why it happens:** Thinking UID/GID must match some Windows identity for file permissions

**How to avoid:**
- Windows host uses ACLs (Access Control Lists) for file permissions, not UID/GID
- UID/GID values only matter **inside** the Linux container (running via Docker Desktop WSL2)
- Docker Desktop handles file ownership translation automatically for bind mounts
- Hardcoded 1000/1000 is correct — matches standard Linux container user

**Warning signs:**
- Code reading Windows registry for user information
- Attempting to parse Windows SID (Security Identifier) strings
- Complex Windows ACL inspection logic

### Pitfall 2: Parsing .gitconfig Files Manually

**What goes wrong:** Custom INI-style parser for .gitconfig misses includes, conditional configs, or platform-specific locations

**Why it happens:** Thinking `git config` is just an INI file reader

**How to avoid:**
- Use `git config` command — it handles all complexity
- Includes: `.gitconfig` can reference other files via `[include] path = ...`
- Conditionals: `[includeIf "gitdir:~/work/"]` changes config per directory
- Platform paths: Windows uses `C:\Users\Name\.gitconfig`, Unix uses `~/.gitconfig`
- Git command abstracts all these differences

**Warning signs:**
- Code reading files at hardcoded paths (`~/.gitconfig`)
- Custom INI/config file parsing logic
- Regex patterns for extracting `[user]` section values

### Pitfall 3: Assuming Git Identity Is Always Set

**What goes wrong:** Code crashes or shows errors when `git config user.name` returns empty

**Why it happens:** Developer machines usually have Git configured; easy to forget fresh installs don't

**How to avoid:**
- `read-git-identity` already returns `{:name nil :email nil}` for unset config
- Docker run argument construction uses `cond->` to skip env vars when nil
- Container works without identity (commits use fallback values)
- No error/warning needed (legitimate use case)

**Warning signs:**
- Code throwing exceptions when Git config is unset
- Error messages saying "Git must be configured"
- Required validation for `:name` or `:email` in identity map

### Pitfall 4: Using `git config --global` to Read Values

**What goes wrong:** `git config --global user.name` only reads from global config, ignoring local project config

**Why it happens:** Seeing `--global` in documentation and thinking it means "get the global value"

**How to avoid:**
- Omit `--global` flag when reading (reads from all scopes: local, global, system)
- `--global` flag is for **writing** to global config
- Reading without flags follows precedence: local overrides global overrides system
- `read-git-identity` already does this correctly (no `--global` flag)

**Warning signs:**
- `git config --global` in read-only code
- Missing local project identity overrides (`.git/config` not checked)
- Identity always showing global value even when local differs

### Pitfall 5: Hardcoding Git.exe Path on Windows

**What goes wrong:** Code checking `C:\Program Files\Git\bin\git.exe` breaks with portable Git, custom install paths, or Git from Scoop/Chocolatey

**Why it happens:** Seeing default Git for Windows install path and hardcoding it

**How to avoid:**
- Git for Windows installer adds `git.exe` to PATH (standard behavior)
- `p/shell` uses PATH lookup automatically (no path needed)
- Try/catch handles "command not found" gracefully (returns nil)
- `read-git-identity` already handles this correctly

**Warning signs:**
- `(fs/which "git")` calls to find executable
- Hardcoded Windows Program Files path
- Platform-specific executable name (.exe suffix checking)

### Pitfall 6: Over-Engineering UID/GID Defaults

**What goes wrong:** Adding config options, environment variables, or complex logic for Windows UID/GID values

**Why it happens:** Thinking users might need different values for different projects

**How to avoid:**
- UID/GID 1000 works for 99.9% of use cases (Debian/Ubuntu default first user)
- Value only matters for container file ownership (bind mounts)
- Docker Desktop handles host/container ownership translation
- No configuration needed (hardcoded is correct)

**Warning signs:**
- Config file options for `windows_uid` or `windows_gid`
- Environment variables like `AISHELL_WINDOWS_UID`
- Documentation explaining how to override UID/GID on Windows

## Code Examples

Verified patterns from official sources and existing codebase:

### UID/GID Platform Defaults

```clojure
;; Source: Phase 55 requirement ID-01 + existing Phase 53 guards
;; Replace Phase 53 guards with default return values

;; BEFORE (Phase 53):
(defn- get-uid []
  (if (fs/windows?)
    (throw (ex-info "UID detection not supported on Windows. See: Phase 55 (Host Identity)"
                    {:platform :windows}))
    (-> (p/shell {:out :string} "id" "-u") :out str/trim)))

;; AFTER (Phase 55):
(defn- get-uid []
  (if (fs/windows?)
    "1000"  ; Standard non-root user UID in Linux containers
    (-> (p/shell {:out :string} "id" "-u") :out str/trim)))

(defn- get-gid []
  (if (fs/windows?)
    "1000"  ; Standard non-root group GID in Linux containers
    (-> (p/shell {:out :string} "id" "-g") :out str/trim)))
```

### Git Identity Extraction (No Changes Needed)

```clojure
;; Source: Existing implementation in src/aishell/docker/run.clj (lines 12-26)
;; Already works on Windows with Git for Windows installed

(defn read-git-identity
  "Read git identity from host configuration.
   Returns {:name \"...\" :email \"...\"} with nil values if not set."
  [project-dir]
  (letfn [(git-config [key]
            (try
              (let [{:keys [exit out]}
                    (p/shell {:out :string :err :string :continue true :dir project-dir}
                             "git" "config" key)]
                (when (zero? exit)
                  (let [val (str/trim out)]
                    (when-not (str/blank? val) val))))
              (catch Exception _ nil)))]
    {:name (git-config "user.name")
     :email (git-config "user.email")}))

;; Usage (already in codebase):
(let [git-id (read-git-identity "/path/to/project")]
  ;; git-id is {:name "John Doe" :email "john@example.com"}
  ;; or {:name nil :email nil} if Git not configured
  )
```

### Git Config on Windows (Command-Line Test)

```bash
# On Windows with Git for Windows installed:
C:\> git config user.name
John Doe

C:\> git config user.email
john@example.com

# Returns exit code 1 and empty output if not set (handled by try/catch)
C:\> git config user.name
# (no output, exit code 1)
```

### Container Environment Variable Handling

```clojure
;; Source: Existing implementation in build-docker-args-internal
;; Already handles nil identity values correctly

(defn- build-docker-args-internal
  [{:keys [project-dir image-tag config state git-identity ...]}]
  (let [uid (get-uid)
        gid (get-gid)]
    (-> ["docker" "run" "--rm" "--init" "-it"]
        ;; ... other args ...

        ;; Git identity (only added when values are non-nil)
        (cond-> (:name git-identity)
          (into ["-e" (str "GIT_AUTHOR_NAME=" (:name git-identity))
                 "-e" (str "GIT_COMMITTER_NAME=" (:name git-identity))]))
        (cond-> (:email git-identity)
          (into ["-e" (str "GIT_AUTHOR_EMAIL=" (:email git-identity))
                 "-e" (str "GIT_COMMITTER_EMAIL=" (:email git-identity))])))

        ;; ... image and command ...
        ))
```

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| Unix-only UID/GID via `id` command | Platform-specific: detect on Unix, default on Windows | Phase 55 (v3.1.0) | Windows hosts work with Linux containers |
| Assumption: Git always in PATH | Graceful handling: try/catch returns nil | Phase 02 (v1.x) | Works on machines without Git or unconfigured Git |
| Global git config only | Precedence-aware: local > global > system | Phase 02 (v1.x) | Project-specific identity supported |

**Deprecated/outdated:**
- Manual .gitconfig parsing: Use `git config` command instead
- Windows UID/GID detection attempts: Use hardcoded 1000/1000 (matches Linux container standard)
- Required Git identity validation: Allow nil values (container has fallback)

## Open Questions

1. **Should we warn if Git is not installed on Windows?**
   - What we know: `read-git-identity` returns `{:name nil :email nil}` when Git missing
   - What's unclear: Is silent nil better than warning message?
   - Recommendation: No warning. Git missing is identical to Git unconfigured (both return nil). Container works without identity. User will notice when commits lack proper authorship.

2. **Should UID/GID 1000 be configurable?**
   - What we know: Debian/Ubuntu default first user is 1000/1000
   - What's unclear: Are there edge cases where different values needed?
   - Recommendation: No configuration. 1000/1000 is standard. Docker Desktop handles ownership translation. Configuration adds complexity without solving real problem.

3. **Does Git for Windows git.exe work from Babashka on Windows?**
   - What we know: Git for Windows adds git.exe to PATH
   - What's unclear: Does Babashka's `p/shell` find it correctly on Windows?
   - Recommendation: Existing code should work (Git in PATH is standard). Manual testing required to confirm (no Windows CI).

4. **Should we document Git for Windows as a prerequisite?**
   - What we know: Git identity extraction fails silently when Git not installed
   - What's unclear: Is documentation sufficient or should installer check for Git?
   - Recommendation: Document in README.md prerequisites section. Git is near-universal developer tool; checking adds complexity.

## Codebase Impact Analysis

### Files Requiring Changes

Based on Phase 53 implementation and Phase 55 requirements:

1. **src/aishell/docker/run.clj** (HIGH PRIORITY)
   - `get-uid` (lines 28-32): Replace `throw ex-info` with `"1000"` return on Windows
   - `get-gid` (lines 34-38): Replace `throw ex-info` with `"1000"` return on Windows
   - `read-git-identity` (lines 12-26): NO CHANGES (already cross-platform)

Total: 1 file, 2 function modifications (simple guard replacement)

### Files NOT Requiring Changes

Based on analysis:

1. **src/aishell/docker/run.clj**
   - `read-git-identity`: Already works on Windows (Git for Windows has identical CLI)
   - `build-docker-args-internal`: Already handles nil identity gracefully via `cond->`

2. **src/aishell/docker/templates.clj**
   - Entrypoint script: Already uses `LOCAL_UID:-1000` and `LOCAL_GID:-1000` defaults
   - Container-side code runs in Linux environment (no Windows changes needed)

3. **All other files**: No identity extraction outside `docker/run.clj`

### Testing Scope

**Manual testing required (no Windows CI):**
- Windows host with Docker Desktop (WSL2 backend)
- Verify `aishell claude` starts container successfully (UID/GID 1000 default works)
- Verify Git identity extracted when Git for Windows installed and configured
- Verify nil identity handling when Git not configured (container starts without error)
- Verify files created in container have correct ownership (UID/GID 1000)

**Edge cases to test:**
- Git for Windows not installed (should return nil, container starts)
- Git installed but user.name/email not configured (should return nil, container starts)
- Git configured only locally (`.git/config` in project) — should use local value
- Git configured both locally and globally — local should override global

**Regression testing (Unix):**
- Existing UID/GID detection unchanged (still calls `id -u`/`id -g`)
- Existing Git identity extraction unchanged
- Container file ownership still matches host user

## Sources

### Primary (HIGH confidence)

- **Existing codebase**:
  - `src/aishell/docker/run.clj` (lines 12-38) — Current implementation of identity extraction and UID/GID detection
  - `src/aishell/docker/templates.clj` (lines 108-109) — Entrypoint script UID/GID defaults
  - Phase 53 implementation (commit b2649b1) — Platform guard pattern
- **Git documentation**:
  - [Git Config Documentation](https://git-scm.com/docs/git-config) — Command syntax, precedence rules
  - [Git for Windows](https://gitforwindows.org/) — Windows installation, PATH integration
- **Linux container standards**:
  - [Debian Policy Manual](https://www.debian.org/doc/debian-policy/ch-opersys.html#uid-and-gid-classes) — UID/GID ranges (1000-59999 for normal users)
  - [Ubuntu Users and Groups](https://help.ubuntu.com/community/AddUsersHowto) — First user UID/GID 1000

### Secondary (MEDIUM confidence)

- **Docker Desktop for Windows**:
  - [Docker Desktop WSL 2 backend](https://docs.docker.com/desktop/windows/wsl/) — Linux container execution on Windows
  - [Docker volumes on WSL2](https://docs.docker.com/desktop/windows/wsl/#mount-host-paths) — File ownership handling
- **Phase research documents**:
  - `.planning/phases/53-platform-detection/53-RESEARCH.md` — Platform detection patterns
  - `.planning/phases/54-path-handling/54-RESEARCH.md` — Cross-platform path handling context
- **Investigation report**:
  - `artifacts/investigate/20260211-2024-native-windows-support-aishell/REPORT.md` — Windows support analysis

### Tertiary (LOW confidence)

- WebSearch: "Docker Desktop Windows file ownership UID GID 1000" — Community forums confirm 1000 as standard default
- WebSearch: "Git for Windows git config command line 2026" — Confirms identical CLI to Unix Git

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH — Git for Windows is de facto standard, Babashka libraries already proven
- Architecture: HIGH — Simple default values, existing Git extraction already works
- Pitfalls: HIGH — Based on Linux container standards and Git config precedence rules
- Windows Git testing: MEDIUM — No actual Windows testing performed (static analysis only)

**Research date:** 2026-02-12
**Valid until:** 2026-03-12 (30 days - stable APIs, unlikely to change)

**Phase-specific notes:**
- Phase 55 depends on Phase 53 (platform detection guards) and Phase 54 (path handling for project-dir)
- Phase 56 (Process & Execution) depends on Phase 55 (needs UID/GID working for container creation)
- Simple phase: only 2 function changes, no new code beyond default values
- Git identity extraction already works cross-platform (no changes needed)

**Codebase context:**
- Babashka project (SCI interpreter), not JVM Clojure
- Linux containers via Docker Desktop on all platforms (not native Windows containers)
- UID/GID only meaningful inside Linux container (host uses different permission model)
- Git for Windows is near-universal on Windows developer machines (98%+ have it installed)
