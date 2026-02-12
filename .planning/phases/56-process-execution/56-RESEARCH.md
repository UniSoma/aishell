# Phase 56: Process & Execution - Research

**Researched:** 2026-02-12
**Domain:** Cross-platform process execution (Windows/Unix) for Docker container attachment and startup
**Confidence:** HIGH

## Summary

Phase 56 enables Windows support for container execution and attachment by replacing Unix-specific `p/exec` calls with cross-platform `p/process` + `:inherit` patterns. The core challenge: `p/exec` (Unix `execve` replacement) is a no-op on Windows, meaning `aishell attach` and `aishell <harness>` commands silently fail to transfer terminal control on Windows platforms. The current code throws helpful error messages pointing to Phase 56, now we implement the Windows-compatible alternative.

The key insight from babashka.process documentation: `p/exec` replaces the current process image (Unix-only operation), while `p/process` with `:inherit true` spawns a child process with inherited stdin/stdout/stderr streams, achieving identical user experience across platforms. The `:inherit` option directs all I/O through the parent process's streams, providing transparent terminal passthrough without process replacement.

Aishell has two process replacement use cases: (1) `attach-to-container` — opens interactive bash shell in running container via `docker exec -it`, (2) `run-container` — starts container with harness command and transfers terminal control. Both currently use `p/exec` on Unix and throw errors on Windows. The Windows-compatible pattern uses `p/process` with `:inherit true`, waits via `@` (deref), propagates exit code via `System/exit`.

**Primary recommendation:** Replace Windows platform guards with `p/process {:inherit true}` + deref + `System/exit` pattern. On Unix, retain `p/exec` for cleaner process tree (no parent Babashka process). Platform detection via `fs/windows?` already in place from Phase 53.

## Standard Stack

### Core

| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| babashka.process | Built-in | Cross-platform process spawning and I/O inheritance | Official Babashka library, documented Windows support, `:inherit` semantics proven |
| babashka.fs | Built-in | Platform detection via `windows?` predicate | Official library, already used in Phases 53-55 for platform guards |

### Supporting

| Library | Version | Purpose | When to Use |
|---------|---------|---------|-------------|
| System/exit | Java built-in | Propagate subprocess exit code to parent shell | Required on Windows when `p/exec` unavailable (cannot replace process) |

### Alternatives Considered

| Instead of | Could Use | Tradeoff |
|------------|-----------|----------|
| `p/exec` on Windows | Require WSL2 environment only | Limits Windows users to WSL2, breaks native cmd.exe/PowerShell support |
| `p/process :inherit true` on all platforms | Use `p/exec` everywhere (Unix + Windows) | Impossible — `p/exec` is no-op on Windows (GraalVM limitation, not babashka) |
| `p/shell :inherit true` | `p/process :inherit true` | Nearly identical; `p/shell` defaults to throwing on non-zero exit, both support `:inherit` |
| Manual stream piping | Built-in `:inherit` option | Custom stream forwarding adds complexity, misses edge cases (signal handling, terminal resizing) |

**Installation:**
No installation needed — `babashka.process` and `babashka.fs` ship with Babashka. Works on Windows with Babashka native binary (available via Scoop, Chocolatey, or direct download).

## Architecture Patterns

### Recommended Project Structure

No new files required. Changes localized to existing execution modules:

```
src/aishell/
├── attach.clj         # attach-to-container: p/exec → platform-specific
├── run.clj            # run-container: p/exec → platform-specific
└── docker/run.clj     # No changes (UID/GID already Windows-compatible from Phase 55)
```

### Pattern 1: Platform-Specific Process Replacement

**What:** Detect platform and use `p/exec` (Unix) or `p/process` + `System/exit` (Windows)
**When to use:** Container attachment and harness execution (foreground terminal takeover)
**Example:**

```clojure
;; Source: babashka.process API documentation + investigation report pattern

;; BEFORE (Phase 50, Unix-only):
(p/exec "docker" "exec" "-it" "-u" "developer"
        "-e" (str "TERM=" term)
        container-name
        "/bin/bash" "--login")

;; AFTER (Phase 56, cross-platform):
(if (fs/windows?)
  ;; Windows: spawn child process with inherited streams, wait, propagate exit
  (let [result @(p/process {:inherit true}
                           "docker" "exec" "-it" "-u" "developer"
                           "-e" (str "TERM=" term)
                           container-name
                           "/bin/bash" "--login")]
    (System/exit (:exit result)))
  ;; Unix: replace process image (cleaner, no parent process)
  (p/exec "docker" "exec" "-it" "-u" "developer"
          "-e" (str "TERM=" term)
          container-name
          "/bin/bash" "--login"))
```

**Why this pattern:**
- `p/exec` unavailable on Windows (GraalVM native-image limitation, not Babashka choice)
- `:inherit true` is shorthand for `:in :inherit :out :inherit :err :inherit`
- `@` (deref) blocks until subprocess completes, adds `:exit` to result map
- `System/exit (:exit result)` propagates Docker exit code to parent shell
- User experience identical on both platforms (terminal attached, Ctrl+C works, exit code preserved)
- Process tree differs: Unix has single process (Docker), Windows has Babashka parent + Docker child

### Pattern 2: Shell Command Execution with Exit Code Propagation

**What:** Use `p/shell` with `:inherit true` for one-off commands (already used in gitleaks flow)
**When to use:** Non-interactive command execution where exit code matters (already working on Windows)
**Example:**

```clojure
;; Source: Existing implementation in run.clj lines 230-239 (gitleaks command)

;; Already cross-platform (no changes needed):
(let [result (apply p/shell {:inherit true :continue true}
                    (concat docker-args container-cmd))]
  ;; Update state if needed
  (when scan-completed?
    (scan-state/write-scan-timestamp project-dir))
  ;; Propagate exit code
  (System/exit (:exit result)))
```

**Why this pattern:**
- `p/shell` defaults to throwing on non-zero exit; `:continue true` disables this
- `:inherit true` is equivalent to `:in :inherit :out :inherit :err :inherit`
- Already works on Windows (no `p/exec` used)
- Pattern proven in gitleaks integration (lines 230-239 of run.clj)

### Pattern 3: Explicit Stream Inheritance with Deref

**What:** Use `p/process` with explicit `:inherit true` and deref for waiting
**When to use:** When you need control over when to wait (vs `p/shell` which waits immediately)
**Example:**

```clojure
;; Source: babashka.process README.md examples

;; Start process with inherited streams
(let [proc (p/process {:inherit true} "docker" "run" ...)]
  ;; Do other work while process runs in background
  (println "Container starting...")
  ;; Wait for completion when ready
  (let [result @proc]
    (if (zero? (:exit result))
      (println "Success")
      (println "Failed with exit code" (:exit result)))))
```

**Why this pattern:**
- `p/process` returns immediately (non-blocking)
- Deref blocks until completion
- Useful for spinner/progress UI while container starts
- Currently not needed (aishell blocks during startup), but good to know for future

### Anti-Patterns to Avoid

- **Don't use `p/exec` on Windows:** It's a no-op (returns normally without replacing process), breaking terminal control transfer silently
- **Don't forget to propagate exit code:** `(System/exit (:exit result))` required on Windows; without it, failed Docker commands return exit 0 to shell
- **Don't use `:out :inherit` without `:in :inherit`:** Interactive commands (docker exec -it) need stdin forwarding, not just stdout
- **Don't manually pipe streams when `:inherit` works:** Custom stream forwarding misses signal handling, terminal resizing events, and character encoding edge cases
- **Don't use `p/shell` without `:continue true` if you need to check exit code:** Default behavior throws exceptions on non-zero exit

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| Process replacement on Windows | Custom JNI/FFI bindings to Windows CreateProcess | `p/process :inherit true` + `System/exit` | Windows has no `execve` equivalent; child process + exit code propagation achieves same UX |
| Stream forwarding | Manual `future` + `java.io.InputStream` copying | `:inherit true` option | Built-in inheritance handles buffering, character encoding, signal propagation, terminal resizing |
| Exit code propagation | Parse Docker output for error messages | Check `:exit` value from deref'd process | Exit codes are standard Unix contract; parsing output is fragile and locale-dependent |
| Platform detection | Parse `(System/getProperty "os.name")` string | `(fs/windows?)` predicate | `babashka.fs` handles edge cases (Cygwin, WSL detection, case variations) |

**Key insight:** `p/exec` is a Unix-specific optimization (cleaner process tree), not a required feature. Child process spawning with inherited I/O provides identical user experience on Windows, with acceptable tradeoff of parent process remaining in process tree.

## Common Pitfalls

### Pitfall 1: Assuming p/exec Works Everywhere

**What goes wrong:** Code using `p/exec` on Windows silently fails — function returns normally (no error), but process replacement doesn't happen, leaving Babashka process running and breaking terminal control transfer

**Why it happens:** GraalVM native-image limitation — Windows has no `execve` system call, so `p/exec` is implemented as no-op (documented behavior, not a bug)

**How to avoid:**
- Always wrap `p/exec` calls with `(fs/windows?)` platform detection
- Use `p/process {:inherit true}` + deref + `System/exit` on Windows
- Test on Windows to verify terminal control works correctly

**Warning signs:**
- `aishell attach` returns to shell prompt immediately after "attaching" (no terminal transfer)
- `aishell claude` shows Babashka process in `ps` output while container runs
- Container runs but terminal doesn't receive stdin from user

### Pitfall 2: Forgetting to Propagate Exit Code

**What goes wrong:** Docker command fails (exit code 125, 127, etc.) but shell sees exit 0, breaking CI/CD pipelines and error detection

**Why it happens:** `p/process` returns a map with `:exit` key, but Babashka's default exit code is 0 unless explicitly set

**How to avoid:**
- Always deref process result: `(let [result @(p/process ...)] ...)`
- Always propagate exit code: `(System/exit (:exit result))`
- Pattern already proven in gitleaks flow (run.clj line 239)

**Warning signs:**
- Shell scripts using `set -e` don't stop when aishell command fails
- CI/CD builds succeed even when container startup fails
- Manual `echo $?` after failed command shows 0

### Pitfall 3: Using :out :inherit Without :in :inherit

**What goes wrong:** Interactive commands (docker exec -it bash) display output but don't accept user input — appears frozen

**Why it happens:** Thinking `:out :inherit` is sufficient for terminal passthrough, forgetting stdin matters for interactive shells

**How to avoid:**
- Use `:inherit true` shorthand (sets all three: `:in`, `:out`, `:err`)
- Or explicitly specify all three: `{:in :inherit :out :inherit :err :inherit}`
- Never mix `:inherit` with `:string` or streams for interactive commands

**Warning signs:**
- Container shell prompt appears but typing has no effect
- `docker exec` works from cmd.exe but not from `aishell attach`
- Terminal shows output but `read` commands timeout

### Pitfall 4: Not Waiting for Process Completion

**What goes wrong:** Code calls `(p/process ...)` and immediately continues, racing with subprocess — exit code not available, resources leak

**Why it happens:** `p/process` is non-blocking (unlike `p/shell`), returns immediately with running process handle

**How to avoid:**
- Always deref when you need to wait: `@(p/process ...)`
- Or store process and deref later: `(let [proc (p/process ...)] ... @proc)`
- Use `p/shell` if you always want to block and throw on errors

**Warning signs:**
- Code continues before Docker command finishes
- Exit code check throws NPE (`:exit` key not present until process completes)
- Process cleanup never happens (container left running)

### Pitfall 5: Mixing p/shell and p/process Expectations

**What goes wrong:** Using `p/process` and expecting `p/shell` behavior (automatic error throwing) or vice versa

**Why it happens:** Similar names and overlapping functionality, easy to confuse defaults

**How to avoid:**
- **`p/shell`** defaults: blocks until completion, throws on non-zero exit, `:inherit` I/O
- **`p/process`** defaults: returns immediately, never throws, captures output
- Use `:continue true` with `p/shell` if you want to check exit code manually
- Use `:inherit true` with `p/process` if you want terminal passthrough

**Warning signs:**
- Expecting `p/process` to throw exceptions on failure (it doesn't)
- Surprise when `p/shell` blocks (it always does)
- Lost output when using `p/process` without `:out :inherit`

### Pitfall 6: Platform Detection After Process Call

**What goes wrong:** Calling `p/exec` first, then checking platform inside exception handler — on Windows, no exception is thrown (no-op behavior)

**Why it happens:** Assuming `p/exec` will throw an exception on Windows (it doesn't — documented as no-op)

**How to avoid:**
- Always check `(fs/windows?)` **before** choosing `p/exec` vs `p/process`
- Pattern: `(if (fs/windows?) (p/process ...) (p/exec ...))`
- Never rely on exception handling to detect platform

**Warning signs:**
- Try/catch around `p/exec` with Windows fallback (won't trigger)
- Platform detection in error handling code (too late)
- Logs showing "using exec" on Windows (means no-op happened)

## Code Examples

Verified patterns from official sources and existing codebase:

### Pattern: attach-to-container Cross-Platform Rewrite

```clojure
;; Source: Phase 56 requirement PROC-01 + babashka.process API docs
;; Location: src/aishell/attach.clj lines 44-75

;; BEFORE (Phase 50, Unix-only):
(defn attach-to-container [name]
  (let [project-dir (System/getProperty "user.dir")
        container-name (naming/container-name project-dir name)]
    (validate-tty!)
    (validate-container-state! container-name name)
    (let [term (resolve-term container-name)
          colorterm (or (System/getenv "COLORTERM") "truecolor")]
      ;; Throws on Windows with helpful error message
      (if (fs/windows?)
        (throw (ex-info "Process replacement (p/exec) not supported on Windows. See: Phase 56 (Process & Execution)"
                        {:platform :windows}))
        (p/exec "docker" "exec" "-it" "-u" "developer"
                "-e" (str "TERM=" term)
                "-e" (str "COLORTERM=" colorterm)
                "-e" "LANG=C.UTF-8"
                "-e" "LC_ALL=C.UTF-8"
                container-name
                "/bin/bash" "--login")))))

;; AFTER (Phase 56, cross-platform):
(defn attach-to-container [name]
  (let [project-dir (System/getProperty "user.dir")
        container-name (naming/container-name project-dir name)]
    (validate-tty!)
    (validate-container-state! container-name name)
    (let [term (resolve-term container-name)
          colorterm (or (System/getenv "COLORTERM") "truecolor")]
      ;; Platform-specific execution
      (if (fs/windows?)
        ;; Windows: spawn with inherited I/O, wait, propagate exit
        (let [result @(p/process {:inherit true}
                                 "docker" "exec" "-it" "-u" "developer"
                                 "-e" (str "TERM=" term)
                                 "-e" (str "COLORTERM=" colorterm)
                                 "-e" "LANG=C.UTF-8"
                                 "-e" "LC_ALL=C.UTF-8"
                                 container-name
                                 "/bin/bash" "--login")]
          (System/exit (:exit result)))
        ;; Unix: replace process (cleaner process tree)
        (p/exec "docker" "exec" "-it" "-u" "developer"
                "-e" (str "TERM=" term)
                "-e" (str "COLORTERM=" colorterm)
                "-e" "LANG=C.UTF-8"
                "-e" "LC_ALL=C.UTF-8"
                container-name
                "/bin/bash" "--login")))))
```

### Pattern: run-container Foreground Execution

```clojure
;; Source: Phase 56 requirement PROC-02 + existing run.clj pattern
;; Location: src/aishell/run.clj lines 240-247

;; BEFORE (Phase 50, Unix-only):
(let [project-name (.getName (java.io.File. project-dir))]
  (print (str "\033]2;[aishell] " project-name "\007"))
  (flush)
  ;; Throws on Windows with helpful error message
  (if (fs/windows?)
    (throw (ex-info "Process replacement (p/exec) not supported on Windows. See: Phase 56 (Process & Execution)"
                    {:platform :windows}))
    (apply p/exec (concat docker-args container-cmd))))

;; AFTER (Phase 56, cross-platform):
(let [project-name (.getName (java.io.File. project-dir))]
  (print (str "\033]2;[aishell] " project-name "\007"))
  (flush)
  ;; Platform-specific execution
  (if (fs/windows?)
    ;; Windows: spawn with inherited I/O, wait, propagate exit
    (let [result @(apply p/process {:inherit true}
                         (concat docker-args container-cmd))]
      (System/exit (:exit result)))
    ;; Unix: replace process (cleaner process tree)
    (apply p/exec (concat docker-args container-cmd))))
```

### Pattern: Gitleaks Integration (Already Works on Windows)

```clojure
;; Source: Existing implementation in run.clj lines 229-239
;; Already cross-platform — uses p/shell instead of p/exec
;; No changes needed for Phase 56

(if (= cmd "gitleaks")
  (let [result (apply p/shell {:inherit true :continue true}
                      (concat docker-args container-cmd))
        ;; Only update timestamp for actual scan subcommands
        scan-subcommands #{"dir" "git" "detect" "protect"}
        first-arg (first harness-args)
        is-scan? (contains? scan-subcommands first-arg)
        ;; Gitleaks exit codes: 0=no leaks, 1=leaks found (both successful)
        scan-completed? (contains? #{0 1} (:exit result))]
    (when (and scan-completed? is-scan?)
      (scan-state/write-scan-timestamp project-dir))
    (System/exit (:exit result)))
  ;; Foreground mode: use exec (Unix) or process (Windows)
  ...)
```

### Testing: Verify Process Inheritance on Windows

```bash
# On Windows cmd.exe or PowerShell:
# Test attach command (interactive shell)
aishell attach claude
# Should: transfer terminal to container bash shell
# Should: Ctrl+C terminates bash, exits container
# Should: exit code from container bash propagated to shell

# Test harness execution (foreground command)
aishell claude --version
# Should: display Claude Code version
# Should: exit code 0 on success

# Test non-zero exit code propagation
aishell exec sh -c "exit 42"
# Should: return exit code 42 to shell
# Verify: echo %ERRORLEVEL% (cmd.exe) or $LASTEXITCODE (PowerShell)

# Test interactive command with stdin
echo "print('hello')" | aishell exec python3
# Should: read from stdin pipe, execute Python, output "hello"
```

### Comparison: p/exec vs p/process :inherit true

```clojure
;; Unix behavior (p/exec):
;; Before: [shell] → [Babashka aishell] → calls p/exec
;; After:  [shell] → [docker exec bash]
;; Process tree: Babashka replaced, only Docker remains
;; Exit code: Docker exit code becomes shell exit code (automatic)

;; Windows behavior (p/process :inherit true):
;; Before: [shell] → [Babashka aishell] → calls p/process
;; After:  [shell] → [Babashka aishell] → [docker exec bash]
;;                     (waits via deref)    (runs with inherited I/O)
;; Process tree: Babashka remains as parent, Docker is child
;; Exit code: Manually propagated via System/exit

;; User experience: Identical on both platforms
;; - Terminal attached to container bash
;; - Stdin/stdout/stderr work transparently
;; - Ctrl+C terminates container process
;; - Exit code propagated to shell correctly
```

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| Unix-only `p/exec` with platform guards | Platform-specific: `p/exec` (Unix) or `p/process :inherit` (Windows) | Phase 56 (v3.1.0) | Windows hosts work natively (cmd.exe, PowerShell, Windows Terminal) |
| Throwing errors on Windows platform | Windows-compatible execution with exit code propagation | Phase 56 (v3.1.0) | Native Windows support for attach and harness commands |
| Single execution path for all platforms | Platform detection and conditional logic | Phases 53-56 (v3.1.0) | Cross-platform support while optimizing per-platform (clean process tree on Unix) |

**Deprecated/outdated:**
- Unix-only process replacement assumptions: Must support both `p/exec` and `p/process :inherit` patterns
- Silent failure on Windows: Phase 53 added helpful error messages, Phase 56 implements Windows support
- Assuming `p/exec` throws errors on unsupported platforms: It's a documented no-op, not an error

## Open Questions

1. **Does terminal resizing work with p/process :inherit on Windows?**
   - What we know: `docker exec -it` supports terminal resizing on Windows via Docker Desktop
   - What's unclear: Does Babashka's `:inherit` forward SIGWINCH signals (terminal resize events)?
   - Recommendation: Manual testing required. If broken, document as known limitation (minor UX degradation, not blocker).

2. **Should we keep p/exec on Unix or unify to p/process everywhere?**
   - What we know: `p/exec` cleaner process tree (no parent Babashka process), but `p/process :inherit` functionally identical
   - What's unclear: Is process tree cleanliness worth maintaining two code paths?
   - Recommendation: Keep platform-specific for now (performance/cleanliness benefit on Unix), revisit if maintenance burden increases.

3. **Do Ctrl+C and other signals propagate correctly on Windows?**
   - What we know: Docker Desktop documentation confirms Ctrl+C works with `docker exec -it` on Windows
   - What's unclear: Does Babashka's process spawning interfere with signal propagation?
   - Recommendation: Manual testing required. Test Ctrl+C, Ctrl+Z, Ctrl+D in Windows Terminal and PowerShell.

4. **Should we add :continue true to p/process calls?**
   - What we know: `p/shell` throws on non-zero exit by default; `p/process` never throws (no `:continue` option needed)
   - What's unclear: Does `:continue true` have any effect with `p/process`?
   - Recommendation: No. `:continue` is `p/shell`-specific. `p/process` never throws, so `:continue` is not applicable.

## Codebase Impact Analysis

### Files Requiring Changes

Based on existing Phase 53 platform guards and Phase 56 requirements:

1. **src/aishell/attach.clj** (HIGH PRIORITY)
   - Lines 66-75: Replace Windows platform guard with `p/process :inherit` + `System/exit` pattern
   - Existing validations (TTY check, container state) unchanged
   - `resolve-term` function works on both platforms (already cross-platform)

2. **src/aishell/run.clj** (HIGH PRIORITY)
   - Lines 244-247: Replace Windows platform guard with `p/process :inherit` + `System/exit` pattern
   - Gitleaks flow (lines 229-239) unchanged (already uses `p/shell :inherit`, works on Windows)
   - Window title escape sequence (line 242) works on Windows Terminal, harmless on cmd.exe

Total: 2 files, 2 locations (attach + run), simple pattern replacement

### Files NOT Requiring Changes

Based on analysis:

1. **src/aishell/run.clj**
   - Gitleaks integration (lines 229-239): Already uses `p/shell :inherit true` + `System/exit` — works on Windows
   - `run-exec` function (lines 300-305): Already uses `p/shell :inherit true` + `System/exit` — works on Windows

2. **src/aishell/docker/run.clj**
   - `build-docker-args`: Constructs argument vector, doesn't invoke processes
   - UID/GID functions: Already Windows-compatible from Phase 55

3. **All other files**: No process execution outside attach.clj and run.clj

### Testing Scope

**Manual testing required on Windows:**
- Windows 10/11 with Docker Desktop (WSL2 backend)
- Test shells: cmd.exe, PowerShell 5.1, PowerShell 7, Windows Terminal
- Verify `aishell attach claude` opens interactive bash, accepts stdin, propagates exit code
- Verify `aishell claude` runs Claude Code, terminal I/O works, exit code correct
- Verify Ctrl+C terminates container process and returns to shell
- Verify non-zero exit codes propagate correctly (test: `aishell exec sh -c "exit 42"`)

**Edge cases to test:**
- Unicode in container output (investigation report notes Babashka issue #1009)
- Long-running containers (ensure Babashka parent process doesn't interfere)
- Piped input: `echo "test" | aishell exec cat` (stdin inheritance)
- Multiple simultaneous containers (process isolation)

**Regression testing (Unix):**
- Existing attach and run commands unchanged (still use `p/exec`)
- Process tree remains clean (no parent Babashka process after exec)
- Exit code propagation unchanged
- TTY behavior unchanged

## Sources

### Primary (HIGH confidence)

- **babashka.process official documentation**:
  - [API.md](https://github.com/babashka/process/blob/master/API.md) — `p/exec` and `p/process` API signatures, platform limitations
  - [README.md](https://github.com/babashka/process/blob/master/README.md) — `:inherit` option semantics, deref behavior, exit code checking
  - [Discussion #88](https://github.com/babashka/process/discussions/88) — `:inherit` vs Clojure `*out*` streams clarification
- **Existing codebase**:
  - `src/aishell/attach.clj` (lines 44-75) — Current attach implementation with Phase 53 Windows guards
  - `src/aishell/run.clj` (lines 229-247) — Current run implementation, gitleaks pattern, Windows guards
  - `src/aishell/docker/run.clj` (lines 28-36) — UID/GID Windows handling from Phase 55
- **Investigation report**:
  - `artifacts/investigate/20260211-2024-native-windows-support-aishell/REPORT.md` — Comprehensive Windows support analysis, `p/exec` no-op behavior, process spawning alternatives

### Secondary (MEDIUM confidence)

- **babashka.process GitHub issues**:
  - [Issue #15 - Windows Support](https://github.com/babashka/process/issues/15) — Windows platform compatibility discussion
- **GraalVM documentation**:
  - Native Image limitations on Windows — `exec` system call unavailability
- **Docker Desktop documentation**:
  - [WSL 2 Backend](https://docs.docker.com/desktop/features/wsl/) — Windows container execution via WSL2
  - [TTY allocation](https://docs.docker.com/reference/cli/docker/container/exec/) — `-it` flag behavior on Windows

### Tertiary (LOW confidence)

- WebSearch: "babashka process exec Windows no-op 2026" — Confirms documented `p/exec` limitation
- WebSearch: "babashka process inherit exit code 2026" — Community patterns for exit code handling

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH — Official Babashka libraries, documented APIs, proven in production
- Architecture: HIGH — Simple pattern replacement, existing code paths proven (gitleaks uses identical pattern)
- Pitfalls: HIGH — Based on official documentation and investigation report findings
- Windows testing: MEDIUM — No actual Windows testing performed (static analysis only)

**Research date:** 2026-02-12
**Valid until:** 2026-03-12 (30 days - stable APIs, babashka.process mature and unlikely to change)

**Phase-specific notes:**
- Phase 56 depends on Phase 55 (UID/GID Windows defaults) and Phase 53 (platform detection)
- Phase 54 (path handling) unrelated to process execution (different concerns)
- Simple phase: only 2 locations need changes, pattern well-documented
- Process replacement alternative (`p/process :inherit`) proven in existing gitleaks integration

**Codebase context:**
- Babashka project (SCI interpreter), not JVM Clojure
- GraalVM native-image constraints: `p/exec` unavailable on Windows (not Babashka's choice)
- `:inherit true` is babashka.process shorthand for `:in :inherit :out :inherit :err :inherit`
- `@` (deref) on process blocks until completion, adds `:exit` to result map
- `System/exit` required on Windows (cannot replace process, must propagate exit code manually)
