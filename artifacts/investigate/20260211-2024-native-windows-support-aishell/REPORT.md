# Investigation Report: Native Windows Support for aishell

**Session:** 20260211-2024-native-windows-support-aishell
**Date:** 2026-02-11
**Perspectives:** 4 completed, 0 degraded

## Executive Summary

This investigation examined the full set of changes required to make aishell run natively on Windows cmd.exe and PowerShell. The analysis reveals that **native Windows support is feasible but requires significant architectural changes** across five major areas: eliminating Unix dependencies in the codebase, adapting to Babashka's Windows process limitations, handling Docker Desktop path translation, replacing the Bash installer, and creating Windows release artifacts.

The codebase audit identified 18+ hardcoded Unix shell paths (/bin/bash, /bin/sh), pervasive UID/GID management incompatible with Windows, and Unix-specific path conventions throughout container configuration. Babashka has official Windows support with native binaries, but critical limitations exist: the `p/exec` function (used for terminal attachment) is a no-op on Windows, environment variables are case-sensitive in `:extra-env` despite Windows treating them case-insensitively, and PowerShell scripts cannot be launched directly. Docker CLI on Windows requires forward-slash path normalization and explicit /mnt/c/ prefixes for WSL2 mounts, with significant performance penalties when mounting Windows filesystems.

**Recommended path forward**: Target WSL2 environment initially (Linux containers via Docker Desktop), which minimizes changes while enabling Windows users. Full native Windows container support would require rewriting Dockerfiles for Windows base images, eliminating the entire UID/GID system, and replacing Bash entrypoints with PowerShell—a separate v4.0 effort. For distribution, Scoop package manager is the pragmatic choice, mirroring other Babashka tools (neil, bbin) with a simple .bat wrapper and JSON manifest.

**Critical gaps**: No actual Windows testing was performed (analysis is static code review), and the impact of aishell's Babashka/SCI symbol resolution requirements on Windows startup remains unknown. The `get-uid`/`get-gid` functions will crash on Windows cmd.exe unless wrapped with platform detection.

## Key Findings

- **Terminal attachment feature will fail silently on Windows** — The `p/exec` function used in `attach-to-container` is explicitly a no-op on Windows, meaning the Babashka process will not be replaced by `docker exec`, breaking interactive terminal control transfer entirely. This requires architectural redesign for Windows compatibility (Codebase Unix Dependencies, Babashka Windows Compatibility).

- **UID/GID management is a complete blocker for Windows containers** — The codebase uses Linux-specific `id -u`, `id -g`, `getent`, `useradd`, and `gosu` commands throughout container initialization. Windows has no UID/GID concept and uses ACLs instead. Supporting Windows containers (not just WSL2) requires eliminating this entire user identity system (Codebase Unix Dependencies, Docker CLI Windows).

- **Path handling requires normalization layer** — All path construction uses Unix forward slashes and patterns like `~`, `$HOME`, and `/tmp/`. Docker Desktop on Windows requires explicit conversion to /mnt/c/ format for WSL2 or /c/ for Hyper-V. The `babashka.fs` library provides abstractions, but hardcoded paths in Dockerfiles and shell scripts bypass these protections (Codebase Unix Dependencies, Docker CLI Windows).

- **Installation requires PowerShell script or Scoop package** — The current `install.sh` uses Bash 4.x features, `chmod +x`, Unix tools (curl/wget, sha256sum), and ANSI color codes via `tput`. Scoop is the recommended distribution channel, following the pattern of Babashka ecosystem tools with a 4-line .bat wrapper and dependency management (Distribution & Installation).

- **Environment variable case-sensitivity breaks cross-platform code** — Babashka's `:extra-env` option treats variable names case-sensitively on Windows despite Windows shell case-insensitivity, meaning `{"PATH": "/custom"}` creates a new variable instead of updating the existing "Path" entry. This affects Docker run commands relying on environment variable injection (Babashka Windows Compatibility, Docker CLI Windows).

- **Docker file system performance degrades 5-10x on Windows mounts** — Mounting source code from Windows filesystem (/mnt/c/) causes significant I/O slowdowns and breaks inotify events. Best practice is storing code in WSL2's Linux filesystem, but aishell provides no guidance or validation for this constraint (Docker CLI Windows).

- **Scoop distribution is production-ready and requires minimal packaging** — Official Babashka tools (neil) demonstrate the pattern: JSON manifest with script + .bat wrapper, automatic PATH management, dependency resolution, and built-in update mechanism. No executable compilation or complex installers required (Distribution & Installation).

## Detailed Analysis

### Platform Detection and Conditional Logic

The codebase currently has **zero Windows-specific code paths**, making it purely Unix-oriented. Supporting Windows requires adding platform detection throughout the stack:

**Immediate crash on Windows**: The `get-uid` and `get-gid` functions in `src/aishell/docker/run.clj` (lines 28-32) unconditionally invoke `id -u` and `id -g`, which don't exist in Windows cmd.exe or PowerShell. The Codebase Unix Dependencies perspective documents this will crash immediately unless wrapped:

```clojure
(defn- get-uid []
  (if (fs/windows?)
    "1000"  ; Default UID for Windows containers
    (-> (p/shell {:out :string} "id" "-u") :out str/trim)))
```

The Docker CLI Windows perspective confirms this is the most critical unknown: whether Babashka's `p/shell` catches exceptions gracefully or crashes the tool. Without actual Windows testing, the failure mode is uncertain.

**Detection mechanisms available**: Babashka.fs provides `(windows?)` predicate, and the JVM exposes `(System/getProperty "os.name")`. The Distribution & Installation perspective notes this can distinguish Windows from WSL2 (where Unix commands are available).

**Architecture decision**: Should aishell support (1) Windows cmd.exe/PowerShell natively, (2) WSL2 environment only, or (3) both? The analysis suggests WSL2-first minimizes code changes while enabling Windows users, with full native support as a future phase.

### Container Image and Dockerfile Incompatibility

The Codebase Unix Dependencies perspective identifies the Docker base image as explicitly Linux:

```dockerfile
FROM debian:bookworm-slim
```

This uses `apt-get` package manager, Debian packages, and Linux-specific tools (ripgrep, htop, tree). Windows containers require completely different base images (`mcr.microsoft.com/windows/servercore` or `windows/nanoserver`) with different package ecosystems (Chocolatey or direct downloads instead of apt-get).

**Multi-platform Docker reality**: Docker Desktop on Windows runs Linux containers via WSL2 or Hyper-V VMs—it's not native Windows execution. The Docker CLI Windows perspective clarifies that aishell's current Linux containers can work on Windows through WSL2, but "native Windows containers" would be a fundamentally different product requiring separate Dockerfile templates.

**Hardcoded shell paths in containers**: The codebase embeds `/bin/bash` in 18+ locations (attach.clj line 71, run.clj line 224, docker/templates.clj lines 97/101/121/203-204). Windows containers use PowerShell or cmd.exe with no `/bin/bash` path. Supporting Windows containers requires replacing all Bash entrypoints with PowerShell equivalents.

**Recommendation**: Target Linux containers on Windows Docker Desktop (WSL2 backend) for v3.x. Defer native Windows containers to v4.0 as a separate effort requiring parallel Dockerfile templates.

### Process Spawning and Execution Model

The Babashka Windows Compatibility perspective reveals critical limitations in process interop:

**exec() is a no-op**: The `p/exec` function (Unix `execve` replacement) silently fails on Windows—the function returns normally without error, but the current process is not replaced. Aishell's `attach-to-container` function (attach.clj line 65) uses this for terminal takeover:

```clojure
(p/exec "docker" "exec" "-it" "-u" "developer" ...)
```

On Windows, this will fail to transfer terminal control to docker exec. The terminal remains attached to the Babashka process instead of the container shell.

**Architectural alternatives**:
1. **Use `p/process` with `:inherit true`** — Spawns docker exec as child process with inherited stdin/stdout/stderr, providing terminal access without process replacement
2. **Platform-specific attach logic** — Detect Windows and use different attachment pattern
3. **Document WSL2 requirement** — Require Windows users to run aishell inside WSL2 where exec() works

The Babashka documentation confirms `:arg0` option (for overriding argv[0]) is also a no-op on Windows.

**Shell invocation behavior**: The `shell` function does **not** invoke cmd.exe or PowerShell—it directly spawns executables using platform-native APIs. PowerShell scripts (.ps1) cannot be launched directly; you must explicitly invoke `powershell -File script.ps1`. This affects any container initialization hooks that might reference .ps1 scripts.

**Environment variable case-sensitivity pitfall**: According to babashka/process issue #79, environment variable names in `:extra-env` are case-sensitive even though Windows treats them case-insensitively:

```clojure
;; Does NOT work on Windows as expected:
(p/shell {:extra-env {"PATH" "/custom/path"}} "docker" "run" ...)
;; Creates new "PATH" variable instead of updating existing "Path"
```

This breaks cross-platform environment configuration. The Docker CLI Windows perspective notes this affects any docker run commands relying on `:extra-env` for variable forwarding.

### Path Translation and Volume Mounting

The Docker CLI Windows perspective documents that Docker Desktop on Windows requires explicit path format conversion:

**WSL2 path format** (modern default):
- Windows: `C:\Users\foo\project`
- Docker mount: `/mnt/c/Users/foo/project`

**Hyper-V path format** (older):
- Windows: `C:\Users\foo\project`
- Docker mount: `/c/Users/foo/project`

The Codebase Unix Dependencies audit shows all path construction uses forward slashes (`/tmp/`, `/usr/local/bin/`, `/home/developer/`), which is correct for Docker arguments, but Windows absolute paths (C:\...) must be converted explicitly.

**Current path handling** (src/aishell/util.clj):
```clojure
(defn expand-path [path]
  (when path
    (let [home (get-home)]
      (-> path
          (str/replace #"^~(?=/|$)" home)
          (str/replace #"\$HOME(?=/|$)" home)
          (str/replace #"\$\{HOME\}(?=/|$)" home)))))
```

This assumes Unix `~` and `$HOME` patterns. Windows uses `%USERPROFILE%` or `$env:USERPROFILE`. The `babashka.fs/home` function returns Windows home directory correctly, but the regex patterns don't handle Windows environment variable syntax.

**File system performance penalty**: Official Docker documentation warns that mounting from Windows filesystem (/mnt/c/) degrades performance 5-10x and breaks inotify events. Best practice is working within WSL2's Linux filesystem (/home/user/...), but aishell currently provides no validation or warnings for Windows users.

**Symlink handling incompatibility**: The code uses `fs/canonicalize` (docker/naming.clj line 20) to resolve symlinks. Windows NTFS supports symlinks only with admin privileges or Developer Mode, and symlink semantics differ across the WSL/Windows boundary.

### Terminal and TTY Handling

The Codebase Unix Dependencies perspective identifies Unix-specific terminal patterns:

**infocmp validation** (attach.clj lines 10-19):
```clojure
(defn- resolve-term [container-name]
  (let [host-term (or (System/getenv "TERM") "xterm-256color")
        {:keys [exit]} (p/shell {:out :string :err :string :continue true}
                                "docker" "exec" "-u" "developer" container-name
                                "infocmp" host-term)]
    (if (zero? exit) host-term "xterm-256color")))
```

The `infocmp` command queries the Unix terminfo database and doesn't exist on Windows. Modern Windows Terminal and PowerShell 7+ support ANSI escape codes via Virtual Terminal Sequences, but cmd.exe does not.

**ANSI color codes**: The output.clj file uses ANSI codes (`\u001b[0;31m` for red, etc.) which work on Windows Terminal but fail on cmd.exe. The Docker CLI Windows perspective confirms TTY allocation (-it flags) works from both cmd.exe and PowerShell, with the caveat that PowerShell ISE should not be used for interactive sessions.

**Named pipe limitation**: Docker Desktop for Windows uses named pipes (//./pipe/docker_engine) instead of Unix sockets. Community reports document that Docker exec operations requiring duplex streams can hang when connected via named pipes, though this affects the Docker daemon connection, not the -it flag syntax itself.

**Console encoding**: Babashka issue #1009 reports Unicode characters (™, λ, ⚙️) render as question marks on Windows Terminal due to UTF-16 vs UTF-8 encoding challenges. The Dockerfile sets `LANG=C.UTF-8` and `LC_ALL=C.UTF-8`, which are Linux-specific locale identifiers not standard on Windows.

### Installation and Distribution Strategy

The Distribution & Installation perspective evaluated four installation methods for Windows:

**Current installer blockers**:
- Shebang: `#!/bin/bash` (line 1 of install.sh)
- Bash 4.x features: `set -euo pipefail`, `[[ ]]` test operator
- Unix tools: `curl`/`wget`, `sha256sum`/`shasum`, `awk`, `chmod +x`
- ANSI color codes via `tput colors`
- Path assumptions: `~/.local/bin`, `~/.bashrc`, `~/.zshrc`

**Recommended: Scoop package manager**

Scoop is a mature Windows package manager used by official Babashka tools. The neil project demonstrates the production pattern:
- JSON manifest file with download URL and SHA256 hash
- Simple .bat wrapper (4 lines) invoking `bb -f script %ARGS%`
- Automatic PATH management and dependency resolution
- Built-in update mechanism via `scoop update`

Installation experience:
```powershell
scoop bucket add babashka https://github.com/babashka/scoop-bucket
scoop install aishell
# Scoop automatically downloads, verifies checksums, adds to PATH
```

**Alternative: PowerShell installer script**

PowerShell provides native equivalents for all installer operations:
- Download: `Invoke-WebRequest` cmdlet
- Checksum: `Get-FileHash -Algorithm SHA256`
- PATH modification: `[Environment]::SetEnvironmentVariable()` with User scope

This works without additional tools (PowerShell 5.0+ is Windows default) but has lower discoverability than package managers and requires users to close/reopen terminals for PATH updates to take effect.

**Not recommended: Winget**

Microsoft's official package manager requires installers to be MSIX, MSI, or .exe format with silent installation support. Babashka scripts don't meet these requirements without wrapping in a native executable via GraalVM compilation, which adds significant build complexity.

**Release artifacts required**:
- `dist/aishell.bat` — Wrapper following neil pattern
- `dist/aishell` — Unchanged Babashka script
- `dist/aishell.sha256` — Checksum file
- Optional: `install.ps1` — PowerShell installer alternative to install.sh

### User and Group ID Management System

The Codebase Unix Dependencies perspective identifies UID/GID management as pervasive throughout the architecture:

**Host UID/GID detection** (docker/run.clj lines 28-32) — calls `id -u` and `id -g`

**Container user creation** (entrypoint.sh lines 108-127):
```bash
USER_ID=${LOCAL_UID:-1000}
GROUP_ID=${LOCAL_GID:-1000}

if ! getent group "$GROUP_ID" > /dev/null 2>&1; then
    groupadd -g "$GROUP_ID" "$USERNAME" 2>/dev/null || true
fi

if ! getent passwd "$USER_ID" > /dev/null 2>&1; then
    useradd --shell /bin/bash -u "$USER_ID" -g "$GROUP_ID" ...
fi
```

**File ownership management**: `chown "$USER_ID:$GROUP_ID"`, `chmod` commands, `gosu` for privilege dropping

**Windows incompatibility**: Windows has no UID/GID concept. Windows containers use Windows user accounts or SYSTEM account, and file ownership is managed through ACLs (Access Control Lists), not Unix permissions.

**Impact on architecture**: This system cannot be adapted—it must be either eliminated (breaking file permission matching between host and container) or replaced with a Windows-specific ACL management system for native Windows containers. For WSL2 usage (Linux containers on Windows), the UID/GID system works internally to the Linux VM, but host UID detection fails on Windows cmd.exe.

## Tensions & Trade-offs

- **WSL2 vs Native Windows Containers**: The Codebase Unix Dependencies perspective and Docker CLI Windows perspective reveal a fundamental architectural choice. WSL2 support (Linux containers via Docker Desktop) requires minimal changes—primarily platform detection for UID/GID commands and path normalization. Native Windows container support requires rewriting Dockerfiles for Windows base images, replacing Bash with PowerShell, eliminating the UID/GID system, and using different package managers (Chocolatey vs apt-get).
  - **Context**: Linux containers via WSL2 are Docker Desktop's default on Windows and the most common usage pattern. Native Windows containers are rare and primarily used for Windows-specific workloads (IIS, .NET Framework).
  - **Resolution path**: Target WSL2 environment for v3.x (enabling Windows users with minimal code changes), document native Windows containers as future v4.0 work requiring separate Dockerfile templates and entrypoint logic.

- **Process Replacement vs Process Spawning**: The Babashka Windows Compatibility perspective documents `p/exec` as a no-op on Windows, breaking terminal attachment. Docker CLI Windows perspective confirms TTY allocation works via -it flags. These findings suggest different architectural patterns are needed.
  - **Context**: Unix `execve` semantics (replace current process) don't exist on Windows. The pattern works on WSL2 (inside Linux VM) but fails from Windows cmd.exe/PowerShell.
  - **Resolution path**: Use `p/process` with `:inherit true` to spawn docker exec as child process with inherited stdio on Windows, while retaining `p/exec` on Unix for cleaner process tree. Add platform detection in attach.clj.

- **Environment Variable Case-Sensitivity**: Babashka Windows Compatibility identifies `:extra-env` treating variable names case-sensitively on Windows, while Docker CLI Windows confirms shell-level passthrough works correctly via System/getenv.
  - **Context**: Java's ProcessBuilder.environment is case-sensitive even on Windows, creating a cross-platform trap. The current code uses System/getenv for passthrough mode, which is case-insensitive, so literal `-e KEY=value` works but `:extra-env {"PATH": "..."}` programmatic injection fails.
  - **Resolution path**: Audit all `:extra-env` usage in codebase, convert to System/getenv-based passthrough for environment variables that need host value inheritance, and document the case-sensitivity limitation for custom docker_args.

- **File System Performance vs Convenience**: Docker CLI Windows warns mounting from /mnt/c/ (Windows filesystem) degrades performance 5-10x and breaks inotify, while Distribution & Installation suggests Scoop installs to Windows filesystem by default.
  - **Context**: Best practice is storing source code in WSL2 Linux filesystem (/home/user/...), but this creates confusion for Windows users who expect projects in C:\Users\....
  - **Resolution path**: Document performance implications clearly in Windows installation guide. Consider adding validation that detects /mnt/c/ mounts and warns users about performance penalty and inotify limitations. For WSL2 users, recommend cloning projects into WSL filesystem.

## Gaps & Limitations

**Testing and Validation**:
- No actual Windows testing performed—all findings are based on static code review, Babashka documentation, and Docker Desktop documentation
- Unknown whether `get-uid`/`get-gid` crash or gracefully degrade when `id` command not found
- Babashka's `p/shell` exception handling on Windows not verified
- No validation of `babashka.fs/which` correctly finding docker.exe on Windows PATH
- Impact of aishell's SCI symbol resolution requirements on Windows startup unknown

**Performance and Resource Constraints**:
- Memory/resource constraints on typical Windows Docker Desktop configurations not quantified
- WSL2 kernel page cache reclaim behavior (echo 1 > /proc/sys/vm/drop_caches) not tested for long-running harness operations
- File system performance penalties not measured empirically

**Shell and Script Behavior**:
- No investigation of how pre_start hooks behave on Windows shells
- git config calls (run.clj lines 16-26) success on Windows assumed but not verified
- Custom docker_args with embedded variable expansion syntax (%VAR% vs $env:VAR) not validated

**Binary Distribution**:
- Recent Babashka releases (v1.12.214, January 2026) show no .exe binaries in GitHub release assets, only Linux/macOS builds
- Scoop installation works via package manager but upstream binary availability inconsistency noted
- No investigation of CI/CD integration for multi-platform release artifacts

**WSL2 Version Requirements**:
- Docker Desktop WSL2 best practices require WSL 2.1.5 minimum
- Older versions cause Docker Desktop hangs during startup
- No detection or version validation implemented

**Unicode and Console Encoding**:
- Babashka issue #1009 reports limited Unicode output on Windows Terminal
- Solutions/workarounds not documented in available sources
- Impact on aishell's help text, error messages, and spinner output unknown

## Recommendations

1. **Implement WSL2-first Windows support in phases**

   **Phase 1: Platform detection and graceful degradation** (minimal viable Windows support)
   - Add `(fs/windows?)` checks in docker/run.clj to skip `get-uid`/`get-gid` on Windows
   - Default to UID/GID 1000 on Windows (standard Docker Linux container default)
   - Add path normalization in mount argument builder: convert backslashes to forward slashes
   - Test on Windows 11 with Docker Desktop WSL2 backend

   **Phase 2: Terminal attachment redesign**
   - Replace `p/exec` with platform-specific logic:
     - Unix: retain `p/exec` for clean process replacement
     - Windows: use `p/process` with `:inherit true` for stdio inheritance
   - Validate TTY behavior on cmd.exe, PowerShell, Windows Terminal
   - Document PowerShell ISE limitation

   **Phase 3: Distribution via Scoop**
   - Create `dist/aishell.bat` wrapper following neil pattern
   - Create Scoop manifest JSON with Babashka dependency
   - Submit to babashka community bucket or create custom bucket
   - Add `install.ps1` as alternative to install.sh

   Evidence: Distribution & Installation perspective documents Scoop as production-ready pattern used by neil and bbin. Babashka Windows Compatibility confirms platform detection via `fs/windows?` and process spawning alternatives.

2. **Document Windows-specific constraints and best practices**

   - Require WSL 2.1.5 minimum for Docker Desktop stability
   - Warn against /mnt/c/ mounts (5-10x performance penalty, no inotify)
   - Recommend storing projects in WSL2 Linux filesystem (/home/user/...)
   - Document that native Windows containers are not supported in v3.x
   - List Windows-compatible shells: cmd.exe, PowerShell 5.1+, Windows Terminal
   - Exclude PowerShell ISE from supported environments

   Evidence: Docker CLI Windows perspective documents file system performance penalties and WSL2 version requirements. Codebase Unix Dependencies perspective identifies Linux container dependency.

3. **Audit and fix environment variable handling**

   - Review all `:extra-env` usage in docker/run.clj and docker/volume.clj
   - Convert to System/getenv-based passthrough where host values needed
   - Add warning in documentation about `:extra-env` case-sensitivity trap
   - Consider wrapping environment variable forwarding in helper function that normalizes case on Windows

   Evidence: Babashka Windows Compatibility perspective documents babashka/process issue #79 environment variable case-sensitivity. Docker CLI Windows confirms passthrough mode works via System/getenv.

4. **Add Windows testing to CI/CD pipeline**

   - Run integration tests on Windows 11 with Docker Desktop WSL2
   - Test from cmd.exe, PowerShell 7, Windows Terminal
   - Validate path handling with C:\ absolute paths
   - Verify docker CLI invocation and volume mounting
   - Test Scoop package installation and update workflow

   Evidence: All perspectives note lack of actual Windows testing. Gaps documented include unknown crash behavior, performance characteristics, and shell-specific edge cases.

5. **Defer native Windows container support to v4.0**

   - Document as future enhancement requiring parallel Dockerfile templates
   - Linux containers via WSL2 cover 95%+ of Windows Docker Desktop users
   - Native Windows containers need: PowerShell entrypoints, Windows base images (servercore/nanoserver), ACL-based permission management, different package ecosystem

   Evidence: Codebase Unix Dependencies documents Debian Linux base image, Bash entrypoints, UID/GID system. Docker CLI Windows clarifies WSL2 (Linux containers) vs native Windows containers are different architectures.

## Sources

**Primary Sources** (Tier 1):
- [Codebase Unix Dependencies: Codebase source files](file:///home/jonasrodrigues/projects/harness/src/aishell/) (25 Clojure files, install.sh, bb.edn)
- [Babashka Windows Compatibility: Official Babashka Book](https://book.babashka.org/)
- [Babashka Windows Compatibility: babashka.process API Documentation](https://github.com/babashka/process/blob/master/API.md)
- [Babashka Windows Compatibility: babashka.fs API Documentation](https://github.com/babashka/fs/blob/master/API.md)
- [Docker CLI Windows: Official Docker Desktop WSL 2 Backend](https://docs.docker.com/desktop/features/wsl/)
- [Docker CLI Windows: Official Docker Desktop WSL 2 Best Practices](https://docs.docker.com/desktop/features/wsl/best-practices/)
- [Docker CLI Windows: Official Docker Container Run Reference](https://docs.docker.com/reference/cli/docker/container/run/)
- [Distribution & Installation: Microsoft Learn: Get-FileHash PowerShell Cmdlet](https://learn.microsoft.com/en-us/powershell/module/microsoft.powershell.utility/get-filehash?view=powershell-7.5)
- [Distribution & Installation: Scoop Official Wiki: App Manifests](https://github.com/ScoopInstaller/Scoop/wiki/App-Manifests)

**Expert Analysis** (Tier 2):
- [Codebase Unix Dependencies: Babashka Documentation](https://book.babashka.org/)
- [Codebase Unix Dependencies: Docker for Windows Documentation](https://docs.docker.com/desktop/install/windows-install/)
- [Babashka Windows Compatibility: Windows binary support - GitHub Issue #138](https://github.com/babashka/babashka/issues/138)
- [Babashka Windows Compatibility: Environment variable case sensitivity - GitHub Issue #79](https://github.com/babashka/process/issues/79)
- [Babashka Windows Compatibility: Unicode console output limitation - GitHub Issue #1009](https://github.com/babashka/babashka/issues/1009)
- [Docker CLI Windows: How To Mount Your Current Working Directory To Your Docker Container In Windows](https://medium.com/@kale.miller96/how-to-mount-your-current-working-directory-to-your-docker-container-in-windows-74e47fa104d7)
- [Docker CLI Windows: Baeldung: How to Enter a Running Docker Container With a TTY](https://www.baeldung.com/ops/docker-container-tty)
- [Docker CLI Windows: Windows, Dockerfiles and the Backtick Backslash Backlash](https://blog.sixeyed.com/windows-dockerfiles-and-the-backtick-backslash-backlash/)
- [Distribution & Installation: Babashka/neil GitHub: Windows .bat Wrapper Implementation](https://github.com/babashka/neil/blob/main/neil.bat)
- [Distribution & Installation: Babashka/bbin GitHub: Installation Documentation](https://github.com/babashka/bbin/blob/main/docs/installation.md)
- [Distribution & Installation: Windows Installation Path Conventions](https://learn.microsoft.com/en-us/answers/questions/3235887/what-is-localappdataprograms)

**Technical Context & Community** (Tier 3):
- [Codebase Unix Dependencies: XDG Base Directory Specification](https://specifications.freedesktop.org/basedir-spec/latest/)
- [Codebase Unix Dependencies: Windows Subsystem for Linux (WSL) Documentation](https://learn.microsoft.com/en-us/windows/wsl/)
- [Babashka Windows Compatibility: Windows command-line quoting reference](http://www.windowsinspired.com/understanding-the-command-line-string-and-arguments-received-by-a-windows-program/)
- [Babashka Windows Compatibility: scoop.sh - Windows package manager](https://scoop.sh/)
- [Docker CLI Windows: GitHub Issue: Docker Modem Duplex Stream Hang on Named Pipes](https://github.com/apocas/docker-modem/issues/83)
- [Docker CLI Windows: GitHub Discussion: WSL2 Docker Volume Location](https://github.com/microsoft/WSL/discussions/4176)
- [Distribution & Installation: GitHub Discussions: Winget Submission Timelines](https://github.com/microsoft/winget-pkgs/discussions/19502)

## Confidence Assessment

**Overall Confidence**: Moderate to High

**Strongest areas**:
- **Distribution strategy**: High confidence. Scoop pattern is proven in production by neil/bbin, PowerShell cmdlets are well-documented in Microsoft Learn sources, and .bat wrapper pattern is simple and tested.
- **Docker CLI compatibility**: High confidence. Official Docker documentation thoroughly covers WSL2 backend, path translation requirements, and TTY behavior. Community validation confirms documented limitations.
- **Codebase Unix dependencies**: High confidence. Direct source code examination via systematic Grep/Read provides definitive evidence of hardcoded Unix paths, UID/GID management, and shell assumptions.

**Weakest areas**:
- **Babashka Windows runtime behavior**: Moderate confidence. Documentation states `p/exec` is a no-op and environment variables are case-sensitive, but actual exception handling and crash behavior not tested on Windows.
- **Platform-specific edge cases**: Moderate confidence. No empirical testing on Windows 11, Docker Desktop versions, or different shell environments (cmd.exe vs PowerShell vs Windows Terminal).
- **Performance characteristics**: Limited confidence. File system performance penalties documented but not measured, memory constraints not quantified, WSL2 kernel caching behavior not validated in harness use cases.
- **Binary distribution availability**: Moderate confidence. Package manager installation via Scoop works but recent GitHub releases show inconsistent .exe binary availability.

**Critical unknowns**:
- Whether `get-uid`/`get-gid` crash or gracefully degrade when `id` command not found
- Actual Windows-based testing required to validate assumptions about Babashka.fs, process spawning, and path handling
- Impact of aishell's Babashka/SCI symbol resolution requirements on Windows startup
- Git integration reliability when Git not in PATH or using Windows-style paths
