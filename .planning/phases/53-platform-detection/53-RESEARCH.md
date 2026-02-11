# Phase 53: Platform Detection - Research

**Researched:** 2026-02-11
**Domain:** Cross-platform Babashka development (Windows/Unix)
**Confidence:** HIGH

## Summary

Phase 53 establishes the foundation for Windows support by introducing platform detection and guarding Unix-specific code paths. The core challenge is that aishell currently makes Unix-specific assumptions in three areas: (1) calling `id -u`/`id -g` for UID/GID extraction, (2) using `p/exec` which replaces processes via Unix exec system call, and (3) hardcoded Unix path patterns in comments and validation messages.

Babashka provides `(babashka.fs/windows?)` as a zero-argument predicate that returns true on Windows, false otherwise. This is the standard, battle-tested approach for platform detection in the Babashka ecosystem. The phase's scope is strictly limited to guarding existing Unix-specific code paths — NOT implementing Windows alternatives (those come in later phases).

**Primary recommendation:** Use `babashka.fs/windows?` for all platform checks. Guard `id` command invocations and `p/exec` calls with platform detection, but defer alternative implementations to Phases 54-56. This phase focuses on "fail gracefully" rather than "work on Windows."

## Standard Stack

### Core
| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| babashka.fs | Built-in | Cross-platform filesystem utilities including `windows?` | Official Babashka companion library, ships with bb, designed for scripting portability |
| babashka.process | Built-in | Process spawning with cross-platform support | Official process library, handles Windows/Unix differences internally |

### Supporting
| Library | Version | Purpose | When to Use |
|---------|---------|---------|-------------|
| clojure.string | Built-in | String manipulation for platform-specific logic | Already used throughout codebase |
| System/getenv | JVM built-in | Reading environment variables (HOME, USERPROFILE, etc.) | Already used for HOME, needed for USERPROFILE on Windows |

### Alternatives Considered
| Instead of | Could Use | Tradeoff |
|------------|-----------|----------|
| babashka.fs/windows? | System/getProperty "os.name" + str/includes? "Windows" | Manual parsing less reliable, windows? is the idiomatic Babashka approach |
| Platform guards | Feature detection (try/catch) | Cleaner for library code, but explicit platform checks clearer for application logic |

**Installation:**
No installation needed — `babashka.fs` and `babashka.process` ship with Babashka. Already imported in 16 files across the codebase.

## Architecture Patterns

### Recommended Approach

Platform detection in aishell should follow a "guard and defer" pattern for Phase 53:

```clojure
(ns aishell.docker.run
  (:require [babashka.fs :as fs]
            [babashka.process :as p]))

;; Guard Unix-specific code paths
(defn- get-uid []
  (if (fs/windows?)
    (throw (ex-info "UID detection not yet implemented on Windows"
                    {:phase "Phase 55"}))
    (-> (p/shell {:out :string} "id" "-u") :out str/trim)))
```

This approach:
- Makes Windows incompatibility explicit and discoverable
- Prevents silent failures or undefined behavior
- Creates clear todo markers for subsequent phases
- Allows testing the detection logic before implementing alternatives

### Pattern 1: Platform Guard with Deferred Implementation

**What:** Wrap Unix-specific code in platform check with informative error
**When to use:** Phase 53 only — guard existing Unix code without implementing Windows alternatives
**Example:**
```clojure
;; Source: Research recommendation based on phase scope
(defn- get-uid []
  "Get user ID. Unix only in Phase 53."
  (if (fs/windows?)
    (throw (ex-info "UID detection requires Phase 55 (Host Identity)" {}))
    (-> (p/shell {:out :string} "id" "-u") :out str/trim)))

(defn- get-gid []
  "Get group ID. Unix only in Phase 53."
  (if (fs/windows?)
    (throw (ex-info "GID detection requires Phase 55 (Host Identity)" {}))
    (-> (p/shell {:out :string} "id" "-g") :out str/trim)))
```

### Pattern 2: Process Replacement Guard (p/exec)

**What:** Guard `p/exec` usage which relies on Unix exec system call
**When to use:** Functions using `p/exec` (run.clj:243, attach.clj:65)
**Example:**
```clojure
;; Source: Research recommendation based on babashka.process limitations
(defn attach-to-container [name]
  ;; ... validation ...
  (let [term (resolve-term container-name)
        colorterm (or (System/getenv "COLORTERM") "truecolor")]
    (if (fs/windows?)
      ;; Phase 56: Use p/process with :inherit instead
      (throw (ex-info "Attach requires Phase 56 (Process & Execution)" {}))
      ;; Unix: Replace current process
      (p/exec "docker" "exec" "-it" "-u" "developer"
              "-e" (str "TERM=" term)
              "-e" (str "COLORTERM=" colorterm)
              container-name
              "/bin/bash" "--login"))))
```

### Pattern 3: Comment-Only Unix Paths

**What:** Unix paths in comments and validation messages don't need runtime guards
**When to use:** Hardcoded paths that are documentation only
**Example:**
```clojure
;; These are Linux container paths, not host paths - no guard needed:
;; - "/bin/bash" (container executable)
;; - "/usr/local/bin" (container PATH)
;; - "/tmp/pre-start.log" (container temp file)
;; - "/etc/passwd" (validation warning for mounts)

;; These DO need platform handling (later phases):
;; - (get-home) → Phase 54
;; - (expand-path "~/.aws") → Phase 54
```

### Anti-Patterns to Avoid

- **Don't implement Windows alternatives in Phase 53:** Scope is strictly detection and guarding. Phase 54 (paths), 55 (identity), 56 (process) implement alternatives.
- **Don't use feature detection (try/catch):** Explicit `(fs/windows?)` checks make platform-specific code obvious and searchable.
- **Don't guard container paths:** Paths like `/bin/bash` run inside Linux containers on all platforms. Only guard HOST-side Unix assumptions.

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| OS detection | (str/includes? (System/getProperty "os.name") "Windows") | `(babashka.fs/windows?)` | windows? is tested, canonical, handles edge cases (Cygwin, MSYS2) |
| Process replacement | Custom fork/exec wrapper | `babashka.process/exec` on Unix, `p/process` + `:inherit` on Windows | exec is Unix-specific; babashka.process handles platform differences |
| Path normalization | Manual backslash→forward slash conversion | Wait for Phase 54, use `babashka.fs/path` | fs/path normalizes automatically, handles UNC paths, drive letters |

**Key insight:** Babashka's standard library already handles most cross-platform differences. The challenge is identifying where aishell's code made Unix-specific assumptions and guarding those code paths.

## Common Pitfalls

### Pitfall 1: Guarding Container Paths Instead of Host Paths

**What goes wrong:** Adding platform checks around Linux container paths like `"/bin/bash"` or `"/usr/local/bin"`

**Why it happens:** Seeing hardcoded Unix paths and assuming they need Windows handling

**How to avoid:** Distinguish host-side vs container-side execution:
- Container paths (`/bin/bash`, `/etc/passwd`, `/tmp`) run in Linux containers on ALL platforms
- Host paths (`~/.aishell`, `get-home`, mounted directories) vary by platform
- Shell commands (`id -u`, `git config`) run on HOST, need platform guards

**Warning signs:** Platform check wrapping Dockerfile content, entrypoint script paths, or docker run command arguments that reference container paths

### Pitfall 2: Using p/exec on Windows

**What goes wrong:** `p/exec` throws "UnsupportedOperationException: exec is not supported on this platform"

**Why it happens:** `p/exec` relies on Unix `exec(3)` system call to replace current process image — no Windows equivalent

**How to avoid:**
- Phase 53: Guard `p/exec` with `(when-not (fs/windows?) ...)` and throw informative error
- Phase 56: Replace with `p/process` + `:inherit true` which works cross-platform

**Warning signs:** Code using `p/exec` for attach or run commands needs Windows alternative

### Pitfall 3: Over-scoping Phase 53

**What goes wrong:** Implementing Windows path handling, UID/GID defaults, or process alternatives in Phase 53

**Why it happens:** Seeing the full Windows support picture and wanting to solve everything at once

**How to avoid:** Phase 53 scope is ONLY:
- Add `(babashka.fs/windows?)` detection capability
- Guard Unix-specific code with platform checks
- Throw informative errors pointing to later phases
- Do NOT implement Windows alternatives yet

**Warning signs:** PRs touching path expansion logic, home directory detection, or process spawning beyond adding guards

### Pitfall 4: Not Testing Detection Logic

**What goes wrong:** Platform guards never tested, break when first Windows user tries

**How to avoid:**
- Add manual test on Windows to verify guards trigger
- Verify error messages are helpful ("requires Phase 55" not "not implemented")
- Check that `(fs/windows?)` returns expected boolean on test platforms

**Warning signs:** No verification step in plan, no documented test procedure

## Code Examples

Verified patterns from official sources:

### Platform Detection

```clojure
;; Source: https://github.com/babashka/fs/blob/master/API.md
(require '[babashka.fs :as fs])

(fs/windows?)
;; => true on Windows
;; => false on Unix/Linux/macOS
```

### Process Execution Cross-Platform

```clojure
;; Source: https://github.com/babashka/process/blob/master/README.md

;; Unix-specific: p/exec replaces current process (Unix exec call)
;; Does NOT work on Windows
(require '[babashka.process :as p])
(p/exec "docker" "exec" "-it" "container" "bash")

;; Cross-platform: p/process with :inherit
;; Works on Windows and Unix
(let [proc (p/process {:inherit true} "docker" "exec" "-it" "container" "bash")]
  @proc) ; wait for completion
```

### Existing Usage in Codebase

```clojure
;; Current code at src/aishell/docker/run.clj:28-32
(defn- get-uid []
  (-> (p/shell {:out :string} "id" "-u") :out str/trim))

(defn- get-gid []
  (-> (p/shell {:out :string} "id" "-g") :out str/trim))

;; Phase 53 guarded version:
(defn- get-uid []
  (if (fs/windows?)
    (throw (ex-info "UID detection requires Phase 55" {:phase 55}))
    (-> (p/shell {:out :string} "id" "-u") :out str/trim)))

(defn- get-gid []
  (if (fs/windows?)
    (throw (ex-info "GID detection requires Phase 55" {:phase 55}))
    (-> (p/shell {:out :string} "id" "-g") :out str/trim)))
```

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| Unix-only scripts | Cross-platform Babashka | v2.0 (2026-01-21) | Foundation for Windows support |
| Bash assumptions | Platform detection | Phase 53 (v3.1.0) | Explicit platform handling |
| `clojure.java.process` | `babashka.process` | Babashka ecosystem | Better Windows support, tokenization |

**Deprecated/outdated:**
- Manual `os.name` parsing: Use `babashka.fs/windows?` instead
- Feature detection for platform: Explicit platform checks clearer for application logic
- Assuming Unix: v3.1.0 explicitly supports Windows hosts with Linux containers

## Codebase Analysis

### Unix-Specific Code Identified

Based on grep analysis of the codebase:

**1. UID/GID Extraction (HIGH PRIORITY)**
- `src/aishell/docker/run.clj:28-32` - `get-uid` and `get-gid` functions
- Calls `id -u` and `id -g` shell commands
- Used in `build-docker-args-internal` to set LOCAL_UID/LOCAL_GID
- **Action:** Guard with platform check, defer Windows default (1000/1000) to Phase 55

**2. Process Replacement (HIGH PRIORITY)**
- `src/aishell/run.clj:243` - `p/exec` for docker run
- `src/aishell/attach.clj:65` - `p/exec` for docker exec
- Unix-only system call, Windows throws UnsupportedOperationException
- **Action:** Guard with platform check, defer `p/process` + `:inherit` alternative to Phase 56

**3. Unix Paths in Container Context (LOW PRIORITY)**
- Container paths: `/bin/bash`, `/usr/local/bin`, `/etc/passwd`, `/tmp/pre-start.log`
- These run INSIDE Linux containers on all platforms
- **Action:** NO changes needed — these are container paths, not host paths

**4. Path Expansion Assumptions (OUT OF SCOPE)**
- `src/aishell/util.clj:12-21` - `expand-path` function
- Uses `~/` and `$HOME` patterns (Unix convention)
- **Action:** Deferred to Phase 54 (Path Handling)

### Files Requiring Changes

Based on Unix-specific code analysis:

1. **src/aishell/docker/run.clj** - Guard `get-uid` and `get-gid`
2. **src/aishell/run.clj** - Guard `p/exec` usage
3. **src/aishell/attach.clj** - Guard `p/exec` usage

Total: 3 files, ~6 guard points

## Open Questions

1. **Error message strategy for deferred implementations**
   - What we know: Throwing ex-info with phase reference is clear
   - What's unclear: Should errors include "run in WSL2" workaround suggestion?
   - Recommendation: Keep errors simple, point to phase number only. WSL2 is out of scope per requirements.

2. **Testing platform detection without Windows environment**
   - What we know: No Windows CI/CD pipeline available
   - What's unclear: How to verify guards work before Windows testing
   - Recommendation: Manual testing only, document test procedure in verification plan

3. **Babashka.fs/windows? edge cases**
   - What we know: Works on Windows, returns false on Unix
   - What's unclear: Behavior in Cygwin, MSYS2, Git Bash
   - Recommendation: Trust babashka.fs implementation, document any discovered edge cases

## Sources

### Primary (HIGH confidence)
- [babashka.fs API](https://github.com/babashka/fs/blob/master/API.md) - `windows?` function documentation
- [babashka.process README](https://github.com/babashka/process/blob/master/README.md) - exec vs process differences, Windows compatibility
- [babashka.process API](https://github.com/babashka/process/blob/master/API.md) - exec function limitations
- Context7 `/babashka/fs` - Platform detection patterns
- Context7 `/babashka/process` - Process execution cross-platform

### Secondary (MEDIUM confidence)
- [Babashka Book](https://book.babashka.org/) - Cross-platform scripting best practices (verified with official docs)
- [GitHub babashka/babashka](https://github.com/babashka/babashka) - Native scripting environment design (verified with book)
- [Brave Clojure: Babashka Babooka](https://www.braveclojure.com/quests/babooka/) - Command-line Clojure patterns (general guidance)

### Tertiary (LOW confidence)
- WebSearch: "Babashka cross-platform Windows Unix differences 2026" - General ecosystem knowledge, needs verification
- WebSearch: "babashka process exec Windows compatibility 2026" - Process library details, verified against official API

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH - babashka.fs and babashka.process are official, well-documented libraries
- Architecture: HIGH - Phase scope clearly defined, patterns verified from official docs
- Pitfalls: HIGH - Based on official documentation of exec limitations and Unix-specific system calls

**Research date:** 2026-02-11
**Valid until:** 2026-03-11 (30 days - stable APIs, unlikely to change)

**Phase-specific notes:**
- Phase 53 scope: Detection and guarding ONLY, no Windows alternatives
- Phase 54 dependency: Path handling builds on platform detection
- Phase 55 dependency: Identity (UID/GID) builds on platform detection
- Phase 56 dependency: Process execution builds on platform detection

**Codebase context:**
- Babashka project, not JVM Clojure
- SCI interpreter resolves symbols at analysis time (file load)
- Any function deletion requires updating ALL callers, even in dead code paths
- v3.0.0 removed tmux, simplified architecture enables Windows support
