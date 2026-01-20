# Pitfalls Research: Babashka + Docker + Cross-Platform

**Domain:** CLI tool migration from Bash to Babashka with Docker integration
**Researched:** 2026-01-20
**Confidence:** MEDIUM (verified through official docs and community sources)

---

## Summary

Migrating a cross-platform CLI from Bash to Babashka introduces specific pitfalls around process handling, path normalization, Windows compatibility, and Docker integration. The current Bash implementation relies on behaviors (UID/GID detection, SSH socket forwarding, signal handling, TTY allocation) that work differently or require explicit handling in Babashka. Windows support is the highest-risk area due to fundamental differences in path handling, environment variables, and lack of Unix concepts like UID/GID.

---

## Critical Pitfalls

Mistakes that cause broken functionality or require significant rework.

---

### 1. Windows Environment Variable Case Sensitivity

**Risk:** HIGH
**Description:** Babashka's `babashka.process` treats environment variable names as case-sensitive even on Windows. Setting `:extra-env {"PATH" "..."}` will NOT update the existing `Path` variable on Windows, resulting in broken PATH modification.

**Warning Signs:**
- Tools not found after supposedly adding to PATH
- Environment variables appear duplicated with different cases
- Works on Linux/macOS but fails on Windows

**Prevention:**
1. Always use the exact case that Windows expects (typically `Path`, not `PATH`)
2. Create a helper function that normalizes env var names per platform:
   ```clojure
   (defn normalize-env-key [k]
     (if (windows?)
       (case (str/lower-case k)
         "path" "Path"
         "home" "USERPROFILE"
         k)
       k))
   ```
3. Test on Windows early in development cycle

**Phase:** Phase 1 (Core Architecture) - Must establish env var handling strategy upfront

**Sources:**
- [babashka/process README](https://github.com/babashka/process) - Windows TIP section

---

### 2. UID/GID Concepts Don't Exist on Windows

**Risk:** HIGH
**Description:** The current implementation relies heavily on `id -u` and `id -g` for Docker permission mapping. These concepts don't exist on Windows. Docker Desktop for Windows handles permissions differently (SMB mounts, no UID/GID mapping needed).

**Warning Signs:**
- `id -u` command fails or returns wrong values on Windows
- Docker volume permissions work unexpectedly on Windows
- Logic branches assume UID/GID are always valid integers

**Prevention:**
1. Abstract user identity into a platform-specific module:
   ```clojure
   (defn get-user-identity []
     (if (windows?)
       {:uid nil :gid nil :needs-mapping? false}
       {:uid (parse-int (sh-out "id" "-u"))
        :gid (parse-int (sh-out "id" "-g"))
        :needs-mapping? true}))
   ```
2. Conditionally apply `--user` flag only on Linux/macOS
3. Document that Docker Desktop for Windows handles permissions automatically

**Phase:** Phase 1 (Core Architecture) - Fundamental to Docker integration design

**Sources:**
- [Docker Forum: UID/GID on Windows](https://forums.docker.com/t/find-uid-and-gid-on-windows-for-mounted-directories/53320)
- [Devilbox Docs: UID/GID on Windows](https://devilbox.readthedocs.io/en/latest/howto/uid-and-gid/find-uid-and-gid-on-win.html)

---

### 3. exec() Only Works in Native Images

**Risk:** HIGH
**Description:** Babashka's `babashka.process/exec` function (which replaces the current process) only works in GraalVM native images. The current Bash script uses `exec docker run ...` to replace the shell process with Docker. If running Babashka via JVM (not native binary), this will fail silently or throw.

**Warning Signs:**
- Script doesn't terminate properly after Docker container exits
- Process lingers after Docker run
- Works in bb binary but fails when running via JVM

**Prevention:**
1. Always use the native Babashka binary, not JVM-based execution
2. Document this requirement clearly
3. Add runtime check:
   ```clojure
   (defn can-exec? []
     (System/getProperty "org.graalvm.nativeimage.imagecode"))
   ```
4. Fall back to `shell` with process replacement semantics if needed

**Phase:** Phase 2 (Process Management) - Critical for proper signal forwarding

**Sources:**
- [babashka.process API](https://github.com/babashka/process/blob/master/API.md)

---

### 4. SSH Agent Socket Path Differences Across Platforms

**Risk:** HIGH
**Description:** SSH agent socket paths differ dramatically across platforms:
- Linux: `$SSH_AUTH_SOCK` (typically `/run/user/1000/keyring/ssh`)
- macOS: `/run/host-services/ssh-auth.sock` (Docker Desktop)
- Windows native: Named pipe `\\.\pipe\openssh-ssh-agent`
- Windows WSL: Requires npiperelay + socat bridge

**Warning Signs:**
- `ssh-add -l` fails inside container
- Git operations fail with permission denied
- SSH_AUTH_SOCK set but socket doesn't exist

**Prevention:**
1. Create platform-specific SSH socket resolution:
   ```clojure
   (defn ssh-auth-sock []
     (cond
       (macos?) "/run/host-services/ssh-auth.sock"
       (linux?) (System/getenv "SSH_AUTH_SOCK")
       (windows?) nil)) ; Cannot directly forward on Windows
   ```
2. Document WSL requirements for Windows users
3. Provide graceful degradation with clear error message
4. Test SSH connectivity in container startup

**Phase:** Phase 3 (Git Integration) - After core container launch works

**Sources:**
- [WSL SSH Agent Forwarding](https://stuartleeks.com/posts/wsl-ssh-key-forward-to-windows/)
- [VS Code Git Credentials](https://code.visualstudio.com/remote/advancedcontainers/sharing-git-credentials)

---

### 5. shell Function Doesn't Invoke Actual Shell

**Risk:** HIGH
**Description:** `babashka.process/shell` does NOT invoke bash/cmd.exe - it directly executes programs. Shell-specific syntax like wildcards (`rm -rf target/*`), pipes, redirections, and environment variable expansion will not work.

**Warning Signs:**
- Wildcards don't expand (`*.txt` passed literally)
- Pipes fail (command includes `|` character)
- Environment variable substitution doesn't happen

**Prevention:**
1. Never rely on shell expansion in process calls
2. For glob patterns, use `babashka.fs/glob` first:
   ```clojure
   (require '[babashka.fs :as fs])
   (doseq [f (fs/glob "target" "*.class")]
     (fs/delete f))
   ```
3. For shell commands, explicitly invoke the shell:
   ```clojure
   (shell "bash" "-c" "rm -rf target/*")
   ;; On Windows:
   (shell "cmd" "/c" "del /q target\\*")
   ```

**Phase:** Phase 1 (Core Architecture) - Affects all command execution

**Sources:**
- [Babashka Book](https://book.babashka.org/)
- [babashka/process README](https://github.com/babashka/process)

---

## Windows-Specific Pitfalls

---

### 6. Path Separator and Slash Direction

**Risk:** MEDIUM
**Description:** Windows uses backslashes (`\`) as path separators while Unix uses forward slashes (`/`). Docker commands and mount paths must use forward slashes even on Windows. Mixing them causes hard-to-debug failures.

**Warning Signs:**
- Docker mount paths fail with "invalid characters"
- Paths with backslashes work in local commands but fail in Docker
- Path joining produces incorrect results

**Prevention:**
1. Use `babashka.fs/unixify` for all paths passed to Docker:
   ```clojure
   (require '[babashka.fs :as fs])
   (fs/unixify "C:\\Users\\dev\\project") ; => "C:/Users/dev/project"
   ```
2. Use `fs/path` for cross-platform path construction
3. Never use string concatenation for paths

**Phase:** Phase 1 (Core Architecture) - Must establish path handling utilities

**Sources:**
- [babashka.fs API](https://github.com/babashka/fs/blob/master/API.md)

---

### 7. Cygwin/MSYS2 Path Translation

**Risk:** MEDIUM
**Description:** When running Babashka from Git Bash, Cygwin, or MSYS2, paths are Cygwin-style (`/c/Users/...`) but bb expects Windows-style (`C:\Users\...`). The shell passes Cygwin paths to bb, causing file not found errors.

**Warning Signs:**
- Scripts work in PowerShell/cmd but fail in Git Bash
- File paths with `/c/` or `/home/` prefix fail
- bb reports file not found for existing files

**Prevention:**
1. Detect Cygwin environment and convert paths:
   ```clojure
   (defn cygwin-env? []
     (some? (re-find #"MINGW|MSYS|CYGWIN"
                     (or (System/getenv "MSYSTEM") ""))))

   (defn cygpath [path]
     (if (cygwin-env?)
       (str/trim (shell-out "cygpath" "-w" path))
       path))
   ```
2. Document supported Windows shells
3. Consider wrapper scripts for each shell type

**Phase:** Phase 4 (Windows Support) - After core functionality works

**Sources:**
- [Babashka Cygwin Issues](https://clojurians-log.clojureverse.org/babashka/2021-07-14)

---

### 8. Windows Executable Resolution

**Risk:** MEDIUM
**Description:** Windows resolves executables by extension in order: `.com`, `.exe`, `.bat`, `.cmd`. Babashka cannot directly launch `.ps1` scripts - they must go through PowerShell. This affects finding tools like `docker` which is actually `docker.exe`.

**Warning Signs:**
- Commands work in shell but `command -v` equivalent fails
- PowerShell scripts don't execute
- Scripts assume executable exists without extension

**Prevention:**
1. Always specify full path or rely on process resolution
2. For PowerShell scripts:
   ```clojure
   (shell "powershell" "-File" "script.ps1")
   ```
3. Use `babashka.fs/which` for cross-platform executable lookup
4. Don't assume shebang-based execution works

**Phase:** Phase 4 (Windows Support)

**Sources:**
- [babashka.process Windows section](https://github.com/babashka/process)

---

### 9. HOME vs USERPROFILE Confusion

**Risk:** MEDIUM
**Description:** Unix uses `$HOME` for user home directory. Windows uses `%USERPROFILE%`. In some contexts (Git Bash), both exist with different values. Config file paths will break if hardcoded to either.

**Warning Signs:**
- Config directories not found on Windows
- `~/.config` expansion fails
- Different behavior in cmd vs PowerShell vs Git Bash

**Prevention:**
1. Create unified home directory function:
   ```clojure
   (defn home-dir []
     (or (System/getenv "HOME")
         (System/getenv "USERPROFILE")
         (System/getProperty "user.home")))
   ```
2. Use `babashka.fs/home` which handles this
3. Test in all Windows shell environments

**Phase:** Phase 1 (Core Architecture)

**Sources:**
- [babashka.fs documentation](https://github.com/babashka/fs)

---

### 10. Windows Line Endings in Process Output

**Risk:** LOW
**Description:** Commands on Windows may output `\r\n` line endings. If parsing output line-by-line, this can leave `\r` characters in values causing subtle comparison failures.

**Warning Signs:**
- String comparisons fail despite looking identical
- JSON parsing fails with "unexpected character"
- Hidden `^M` characters when debugging

**Prevention:**
1. Always trim process output:
   ```clojure
   (str/trim (shell-out "command"))
   ```
2. Use `str/trim-newline` which handles `\r\n`
3. Consider normalizing all output: `(str/replace output #"\r\n" "\n")`

**Phase:** Phase 2 (Process Management)

---

## Migration Pitfalls (Bash to Babashka)

---

### 11. cd Doesn't Persist Across Commands

**Risk:** HIGH
**Description:** In Bash, `cd foo && command` changes the directory for subsequent commands. In Babashka, each `shell` call starts fresh. The `:dir` option only applies to that single call.

**Warning Signs:**
- Commands execute in wrong directory
- Relative paths resolve incorrectly
- Multi-step build sequences fail

**Prevention:**
1. Use `:dir` option consistently:
   ```clojure
   (shell {:dir "foo"} "install.sh")
   ```
2. For multi-command sequences in same dir, use explicit shell:
   ```clojure
   (shell "bash" "-c" "cd foo && ./install.sh && ./verify.sh")
   ```
3. Track working directory in application state if needed

**Phase:** Phase 1 (Core Architecture)

**Sources:**
- [Bash and Babashka Equivalents Wiki](https://github.com/babashka/babashka/wiki/Bash-and-Babashka-equivalents)

---

### 12. Array/List Handling Differs from Bash

**Risk:** MEDIUM
**Description:** Bash arrays like `docker_args+=(-v "$path")` with word splitting behavior don't translate directly. Babashka uses persistent vectors with different semantics. Passing arrays to shell commands requires care.

**Warning Signs:**
- Arguments get concatenated instead of being separate
- Quoting problems in command construction
- Commands work with few args but fail with many

**Prevention:**
1. Build argument vectors explicitly:
   ```clojure
   (def docker-args (atom []))
   (swap! docker-args conj "-v" path)
   ;; Apply with spread:
   (apply shell "docker" "run" @docker-args)
   ```
2. Use `into` for merging argument lists
3. Test with arguments containing spaces

**Phase:** Phase 2 (Process Management)

---

### 13. Heredoc/Embedded File Handling

**Risk:** MEDIUM
**Description:** The current Bash script uses heredocs to embed Dockerfile content. Babashka doesn't have heredocs. Must use multi-line strings or external resource files.

**Warning Signs:**
- Embedded content has escaping issues
- Indentation becomes part of content
- Special characters corrupt embedded files

**Prevention:**
1. Use raw strings for embedded content:
   ```clojure
   (def dockerfile-content
     "FROM debian:bookworm-slim
   ARG WITH_CLAUDE=false
   ...")
   ```
2. Or use `slurp` with embedded resources
3. Consider moving templates to separate files

**Phase:** Phase 2 (Build System)

---

### 14. *file* Not Set in Tasks

**Risk:** LOW
**Description:** Bash's `$0` gives the script path. Babashka's `*file*` is not set when running as tasks. This affects resolving paths relative to the script.

**Warning Signs:**
- Relative path resolution fails in tasks
- Works as standalone script but not as task
- Resource files not found

**Prevention:**
1. Use `babashka.fs/cwd` for current directory
2. Pass explicit paths rather than relying on script location
3. Use `(System/getProperty "babashka.file")` for script location

**Phase:** Phase 1 (Core Architecture)

**Sources:**
- [Bash and Babashka Equivalents](https://github.com/babashka/babashka/wiki/Bash-and-Babashka-equivalents)

---

### 15. Signal Handling Requires Explicit Setup

**Risk:** MEDIUM
**Description:** Bash's `trap cleanup EXIT` automatically handles SIGTERM, SIGINT. Babashka requires explicit shutdown hook registration. Without it, Ctrl+C may leave Docker containers running.

**Warning Signs:**
- Ctrl+C doesn't clean up resources
- Orphaned containers after script termination
- Temp files not deleted on interrupt

**Prevention:**
1. Register explicit shutdown hooks:
   ```clojure
   (defn setup-cleanup! [cleanup-fn]
     (-> (Runtime/getRuntime)
         (.addShutdownHook
           (Thread. cleanup-fn))))
   ```
2. Use `:shutdown destroy-tree` for long-running processes
3. Track resources that need cleanup in an atom

**Phase:** Phase 2 (Process Management)

**Sources:**
- [Babashka Book - Signal Handling](https://book.babashka.org/)
- [babashka/process shutdown hooks](https://github.com/babashka/process/issues/25)

---

### 16. TTY Detection Works Differently

**Risk:** MEDIUM
**Description:** The Bash `[[ -t 0 ]]` test for TTY availability becomes platform-dependent in Babashka. The `is_tty.clj` example uses inheritance checking, which only works on Unix.

**Warning Signs:**
- Interactive features fail in CI/cron
- "Input device is not a TTY" errors
- Different behavior in terminal vs piped execution

**Prevention:**
1. Use platform-aware TTY detection:
   ```clojure
   (defn tty? []
     (if (windows?)
       ;; Windows: check if running in console
       (some? (System/console))
       ;; Unix: use isatty equivalent
       (:exit (shell {:continue true} "test" "-t" "0"))))
   ```
2. Make `-t` flag conditional based on detection
3. Document interactive vs non-interactive behavior

**Phase:** Phase 2 (Process Management)

**Sources:**
- [Babashka Examples - is_tty.clj](https://github.com/babashka/babashka/blob/master/examples/is_tty.clj)

---

## Docker Integration Pitfalls

---

### 17. Docker Socket Communication on Unix vs Windows

**Risk:** MEDIUM
**Description:** Docker API communication uses Unix sockets on Linux/macOS (`/var/run/docker.sock`) but named pipes on Windows (`//./pipe/docker_engine`). Direct Docker API calls require platform-specific handling.

**Warning Signs:**
- Docker commands work but API calls fail
- Socket connection refused on Windows
- Different behavior between platforms

**Prevention:**
1. Use Docker CLI commands rather than direct socket API
2. If API needed, use platform-specific socket paths:
   ```clojure
   (defn docker-socket []
     (if (windows?)
       "//./pipe/docker_engine"
       "/var/run/docker.sock"))
   ```
3. Consider `contajners` library for Babashka-compatible Docker API

**Phase:** Phase 3 (Advanced Docker Features)

**Sources:**
- [Docker babashka-pod](https://github.com/docker/babashka-pod-docker)
- [contajners Babashka compatibility](https://github.com/lispyclouds/contajners/issues/3)

---

### 18. Docker Build Output Parsing

**Risk:** LOW
**Description:** Docker build output format differs between BuildKit (default) and legacy builder. Parsing build output for progress/errors requires handling both formats.

**Warning Signs:**
- Build progress display breaks
- Error extraction fails
- Different output on different Docker versions

**Prevention:**
1. Use `--progress=plain` for consistent output
2. Capture exit code rather than parsing output
3. For verbose mode, just stream output without parsing

**Phase:** Phase 2 (Build System)

---

## Phase-Specific Warnings

| Phase Topic | Likely Pitfall | Mitigation |
|-------------|----------------|------------|
| Core Architecture | Environment variable case sensitivity | Platform-aware env key normalization |
| Core Architecture | Path handling (slashes, HOME) | Use babashka.fs consistently |
| Core Architecture | shell doesn't expand globs | Use babashka.fs/glob explicitly |
| Process Management | exec() only in native images | Document bb binary requirement |
| Process Management | Signal handling not automatic | Explicit shutdown hooks |
| Process Management | cd doesn't persist | Use :dir option consistently |
| Build System | Heredoc migration | Use multi-line strings or external files |
| Git Integration | SSH socket path differences | Platform-specific socket resolution |
| Windows Support | UID/GID concepts missing | Conditional permission mapping |
| Windows Support | Cygwin path translation | Detect and convert paths |
| Windows Support | Executable resolution | Use babashka.fs/which |

---

## Summary: Top 5 Pitfalls to Solve First

1. **Environment Variable Case Sensitivity** - Silent failures on Windows. Solve in Phase 1.
2. **shell Doesn't Invoke Shell** - Glob/pipe failures. Solve in Phase 1.
3. **UID/GID Windows Absence** - Docker permission logic must be conditional. Solve in Phase 1.
4. **exec() Native Image Requirement** - Process lifecycle management. Solve in Phase 2.
5. **SSH Socket Platform Differences** - Git integration core feature. Solve in Phase 3.

---

## Confidence Assessment

| Area | Confidence | Reasoning |
|------|------------|-----------|
| babashka.process behavior | HIGH | Official docs, well-documented |
| Windows env var handling | HIGH | Explicitly documented in README |
| Path handling utilities | HIGH | babashka.fs is mature and documented |
| SSH socket forwarding | MEDIUM | Community sources, platform-specific complexity |
| Windows Cygwin issues | MEDIUM | Community reports, less official documentation |
| exec() behavior | HIGH | Explicitly documented as GraalVM-only |

---

## Sources Summary

### Official Documentation
- [Babashka Book](https://book.babashka.org/)
- [babashka/process README](https://github.com/babashka/process)
- [babashka/fs API](https://github.com/babashka/fs/blob/master/API.md)
- [Bash and Babashka Equivalents Wiki](https://github.com/babashka/babashka/wiki/Bash-and-Babashka-equivalents)

### Community Resources
- [Changing my mind: Converting a script from bash to Babashka](https://blog.agical.se/en/posts/changing-my-mind--converting-a-script-from-bash-to-babashka/)
- [How to Do Things With Babashka](https://presumably.de/how-to-do-things-with-babashka.html)
- [WSL SSH Agent Forwarding](https://stuartleeks.com/posts/wsl-ssh-key-forward-to-windows/)

### Platform-Specific
- [Docker Forum: Windows UID/GID](https://forums.docker.com/t/find-uid-and-gid-on-windows-for-mounted-directories/53320)
- [contajners Babashka compatibility](https://github.com/lispyclouds/contajners/issues/3)
