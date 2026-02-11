# Codebase Unix Dependencies Audit Research: Native Windows support for aishell

## Research Parameters

**Topic**: Native Windows support for aishell (Babashka Clojure CLI tool that manages Docker containers)
**Perspective**: Codebase Unix Dependencies Audit
**Focus**: Deep scan of all Unix-specific patterns in the codebase — shell commands, path handling, process spawning, signal handling, file permissions, symlinks, ANSI codes
**Date**: 2026-02-11
**Session**: 20260211-2024-native-windows-support-aishell

## Key Findings

- **Shell invocations are pervasive**: 18+ instances of hardcoded `/bin/bash` and `/bin/sh` paths in Docker entrypoints, templates, and container execution code. No abstraction layer exists for shell differences.
- **Path handling assumes Unix conventions**: All path construction uses forward slashes, `~` and `$HOME` expansion patterns are Unix-specific, and `fs/canonicalize` relies on symlink resolution unavailable on Windows.
- **User/group ID management is Linux-specific**: Heavy use of `id -u`, `id -g`, `getent`, `useradd`, `groupadd`, and `gosu` for dynamic user creation inside containers. No Windows equivalent pattern exists.
- **Installation script is Bash-only**: The `install.sh` uses Bash 4.x features (`set -euo pipefail`, `[[ ]]`, process substitution), ANSI color codes via `tput`, and Unix tools (`curl`/`wget`, `sha256sum`/`shasum`, `chmod`).
- **Terminal handling uses Unix TERM database**: The codebase queries `infocmp` to validate TERM values, assumes ANSI escape sequence support, and relies on `/etc/profile.d` for login shell initialization.

## Analysis

### 1. Shell Command Invocations

The codebase embeds Unix shell paths throughout container configuration and execution:

**Hardcoded shell paths (18+ instances)**:
- `/bin/bash` used in: `attach.clj` (line 71), `run.clj` (line 224), `docker/templates.clj` (lines 97, 101, 121), `docker/run.clj` (line 289 comment)
- `/bin/sh` used in: `docker/templates.clj` (lines 203-204 for PRE_START hook execution)
- `sh -c` and `bash -c` patterns embedded in entrypoint scripts

**Container CMD and entrypoint defaults**:
```dockerfile
CMD ["/bin/bash"]
ENTRYPOINT ["/usr/local/bin/entrypoint.sh"]
```

**Shell execution in Babashka process spawning**:
The codebase uses `babashka.process/shell` and `babashka.process/exec` extensively to invoke Docker commands, git operations, and system utilities. While Babashka abstracts some platform differences, the shell scripts embedded in container images are pure Bash.

**Rationale**: These paths work in Linux containers but assume Bash availability. Windows containers use PowerShell or cmd.exe, with no `/bin/bash` path.

### 2. Path Handling and Filesystem Patterns

**Unix path conventions**:
- **Forward slash hardcoded**: All path construction uses `/` (e.g., `/tmp/`, `/usr/local/bin/`, `/home/developer/`)
- **Tilde expansion**: `util.clj` implements `~` expansion assuming Unix home directory conventions
- **$HOME environment variable**: Extensive use of `$HOME` for user home paths (util.clj lines 9-10, 20-21)
- **XDG_STATE_HOME**: Uses XDG Base Directory spec (util.clj lines 32-33), which is Linux-specific

**Path examples from codebase**:
```clojure
;; util.clj - Unix-specific path expansion
(defn expand-path [path]
  (when path
    (let [home (get-home)]
      (-> path
          (str/replace #"^~(?=/|$)" home)
          (str/replace #"\$HOME(?=/|$)" home)
          (str/replace #"\$\{HOME\}(?=/|$)" home)))))

;; Hardcoded Unix paths in docker/templates.clj
"/usr/local/bin/entrypoint.sh"
"/home/developer"
"/tmp/pre-start.log"
"/var/lib/apt/lists"
```

**Symlink handling**:
- `fs/canonicalize` used in `docker/naming.clj` (line 20) to resolve symlinks for deterministic hashing
- `ln -s` commands in Dockerfile (lines 47-48) to create npm/npx symlinks
- Windows NTFS supports symlinks only with admin privileges or Developer Mode

**Babashka fs abstraction**: The `babashka.fs` library provides some cross-platform abstractions (e.g., `fs/path`, `fs/exists?`), but the codebase uses hardcoded Unix paths in Dockerfiles and shell scripts that bypass these abstractions.

### 3. User and Group ID Management

**Linux-specific user identity patterns**:
The entire user permission model assumes Linux UID/GID semantics:

**Host UID/GID detection** (`docker/run.clj` lines 28-32):
```clojure
(defn- get-uid []
  (-> (p/shell {:out :string} "id" "-u") :out str/trim))

(defn- get-gid []
  (-> (p/shell {:out :string} "id" "-g") :out str/trim))
```

**Container user creation** (entrypoint.sh lines 108-127):
```bash
USER_ID=${LOCAL_UID:-1000}
GROUP_ID=${LOCAL_GID:-1000}
USER_HOME=${LOCAL_HOME:-/home/developer}

if ! getent group "$GROUP_ID" > /dev/null 2>&1; then
    groupadd -g "$GROUP_ID" "$USERNAME" 2>/dev/null || true
fi

if ! getent passwd "$USER_ID" > /dev/null 2>&1; then
    useradd --shell /bin/bash \
        -u "$USER_ID" \
        -g "$GROUP_ID" \
        -d "$USER_HOME" \
        -m "$USERNAME" 2>/dev/null || true
fi

# Dynamic user lookup
ACTUAL_USER=$(getent passwd "$USER_ID" | cut -d: -f1)
export HOME=${LOCAL_HOME:-$(getent passwd "$USER_ID" | cut -d: -f6)}
```

**File ownership management**:
- `chown "$USER_ID:$GROUP_ID" "$HOME"` (line 137)
- `chmod` commands for permissions (lines 56, 64, 82, 88, 153)
- `gosu` for privilege dropping (lines 63-66, 204, 217)

**Windows incompatibility**: Windows has no UID/GID concept. Windows containers use Windows user accounts or the SYSTEM account. File ownership is managed through ACLs, not Unix permissions.

### 4. File Permissions and Execution

**chmod/chown usage** (10+ instances):
- `chmod +x` to mark scripts executable: `entrypoint.sh` (line 88), `bb` (line 56), `gosu` (line 64), `gitleaks` (line 82)
- `chmod 0440` for sudoers config (line 153)
- `chmod -R a+rX /tools` for world-readable tools (docker/volume.clj line 229)
- `chown -R "$USER_ID:$GROUP_ID" "$HOME/.local"` (line 141)

**Execute bit semantics**:
- Unix execute permission bit determines script executability
- Windows uses file extensions (`.exe`, `.bat`, `.ps1`) and ACLs, not mode bits

**Sudoers configuration**:
- Creates `/etc/sudoers.d/developer` with `NOPASSWD:ALL` (lines 152-153)
- Windows has no `/etc/sudoers` — uses Group Policy or local admins group

### 5. Installation Script (install.sh)

**Bash-specific syntax** (157 lines of pure Bash):
- Shebang: `#!/bin/bash`
- `set -euo pipefail` (line 11) — Bash 4.x feature
- `[[ -t 1 ]]` test operator (line 21) — Bash-specific
- `${NO_COLOR:-}` parameter expansion (line 21)
- ANSI color codes via `tput colors` (lines 23-31)
- Process substitution: `<(echo ...)` patterns

**Unix tool dependencies**:
- `curl` or `wget` for downloads (lines 60-106)
- `sha256sum` or `shasum` for checksum verification (lines 114-118)
- `awk` for text processing (lines 111, 115, 117)
- `chmod +x` to mark executable (line 131)

**Path assumptions**:
- Default install: `~/.local/bin` (line 17)
- PATH check: `[[ ":$PATH:" != *":$install_dir:"* ]]` (line 139)
- Shell profile references: `~/.bashrc`, `~/.zshrc`, `~/.profile` (line 142)

**Windows blockers**:
- No native Bash interpreter (WSL provides Bash, but that's a Linux VM)
- PowerShell or cmd.exe would require complete rewrite
- ANSI color codes work in modern PowerShell 7+ but have different escape sequences

### 6. Process Spawning and Execution

**Babashka process library usage**:
The codebase uses `babashka.process` for all external command execution. This library provides cross-platform abstractions for:
- `p/shell` - spawns commands via shell
- `p/exec` - replaces current process (Unix `execve` semantics)
- `p/process` - spawns background processes

**Platform-specific behavior**:
- `p/exec` in `attach.clj` (line 65) uses Unix process replacement semantics
- `p/shell` inherits working directory and environment
- Windows would use `CreateProcess` API instead of `fork`+`exec`

**Docker CLI invocations**:
All Docker commands are spawned as external processes:
```clojure
(p/shell "docker" "run" "--rm" "--init" ...)
(p/shell "docker" "exec" "-it" "-u" "developer" ...)
(p/shell "docker" "inspect" "--format={{.Size}}" image-tag)
```

Docker CLI itself is cross-platform, but command construction assumes Unix-style arguments (no path translation needed).

### 7. Terminal and TTY Handling

**TERM database queries**:
`attach.clj` validates TERM values using `infocmp` (lines 10-19):
```clojure
(defn- resolve-term [container-name]
  (let [host-term (or (System/getenv "TERM") "xterm-256color")
        {:keys [exit]} (p/shell {:out :string :err :string :continue true}
                                "docker" "exec" "-u" "developer" container-name
                                "infocmp" host-term)]
    (if (zero? exit) host-term "xterm-256color")))
```

**TTY detection**:
- `(some? (System/console))` to detect interactive terminal (attach.clj line 25, docker/spinner.clj line 14)
- `System/console` returns nil in non-interactive contexts (pipes, redirects, CI)
- Works on both Unix and Windows, but Windows has different console API semantics

**ANSI escape codes**:
`output.clj` uses ANSI codes for colors:
```clojure
(def RED (if (colors-enabled?) "\u001b[0;31m" ""))
(def YELLOW (if (colors-enabled?) "\u001b[0;33m" ""))
(def CYAN (if (colors-enabled?) "\u001b[0;36m" ""))
```

**Prompt configuration**:
```bash
# docker/templates.clj line 233
export PS1='\\[\\033[0;36m\\][aishell]\\[\\033[0m\\] \\w \\$ '
```

**Windows compatibility**: Modern Windows Terminal and PowerShell 7+ support ANSI codes via Virtual Terminal Sequences, but cmd.exe does not. The `infocmp` command is Unix-specific and doesn't exist on Windows.

### 8. Environment Variables

**Unix-specific variables**:
- `HOME` - universally used (util.clj, docker/run.clj, docker/templates.clj)
- `XDG_STATE_HOME` - Linux XDG spec (util.clj line 32)
- `TERM` and `COLORTERM` - Unix terminal configuration (attach.clj lines 66-67, docker/run.clj lines 244-245)
- `USER` - Unix username (docker/templates.clj line 111)
- `LANG` and `LC_ALL` - locale settings (attach.clj lines 68-69)

**Windows equivalents**:
- `USERPROFILE` instead of `HOME`
- `USERNAME` instead of `USER`
- No standard equivalent for `XDG_STATE_HOME` (use `LOCALAPPDATA` or `APPDATA`)

**Cross-platform variables used**:
- `CI` - to detect CI environments (docker/spinner.clj line 15)
- `NO_COLOR` - to disable colors (output.clj line 8)
- API keys: `ANTHROPIC_API_KEY`, `OPENAI_API_KEY`, etc. (docker/run.clj lines 195-209)

### 9. Docker Container Image Base

**Debian Linux base**:
```dockerfile
FROM debian:bookworm-slim
```

The foundation image is explicitly Linux:
- `apt-get` package manager (line 25)
- Debian package installation
- Linux-specific tools: `ripgrep`, `htop`, `tree`, `vim`, `less`, `watch`
- `dpkg --print-architecture` for platform detection (line 62)

**Windows containers**: Would require `FROM mcr.microsoft.com/windows/servercore` or `mcr.microsoft.com/windows/nanoserver` base images. These have completely different package ecosystems (no apt-get, use Chocolatey or direct downloads).

**Multi-platform Docker**: Docker supports multi-arch builds, but Windows and Linux containers are fundamentally incompatible. You cannot run a Linux container on Windows Docker (it uses WSL2 or Hyper-V to run Linux VMs). Native Windows containers run Windows executables.

### 10. Git Operations

**Git command invocations**:
- `git config user.name` and `git config user.email` (docker/run.clj lines 16-26)
- `git check-ignore` for .gitignore detection (detection/gitignore.clj line 22)

**Cross-platform**: Git CLI works on Windows, but path handling differs:
- Windows Git uses Windows-style paths (`C:\Users\...`)
- Unix Git uses forward slashes (`/home/user/...`)

The codebase uses Babashka's `fs` library for most path operations, which abstracts platform differences. However, git commands are invoked directly without path translation.

### 11. Package Manager and Binary Downloads

**Unix package managers in Dockerfile**:
- `apt-get` for Debian packages (line 25)
- Direct binary downloads with `curl | tar -xz` (lines 54-55, 80-81)
- `npm install -g` for Node.js packages (docker/volume.clj line 206)

**Binary format assumptions**:
- Downloads Linux x64/arm64 binaries for Babashka (line 54), gosu (line 63), Gitleaks (line 80)
- `tar -xz` for extracting gzip'd tarballs
- Uses `dpkg --print-architecture` to select correct binary variant (line 62)

**Windows equivalents**:
- Chocolatey, winget, or scoop for package management
- ZIP archives instead of tar.gz
- `.exe` Windows executables instead of ELF binaries

### 12. Babashka Runtime Assumptions

**Babashka on Windows**: Babashka itself runs on Windows (native .exe builds available), but:
- The codebase uses Unix paths in Dockerfiles and shell scripts
- `babashka.fs` provides cross-platform abstractions, but embedded Bash scripts bypass these
- Docker for Windows adds complexity (Linux containers via WSL2 vs Windows containers)

**Key Babashka libraries used**:
- `babashka.fs` - cross-platform file operations (works on Windows)
- `babashka.process` - cross-platform process spawning (works on Windows with caveats)
- `clj-yaml` - pure Clojure, platform-independent

### 13. Locale and Character Encoding

**UTF-8 assumptions**:
```bash
export LANG=C.UTF-8
export LC_ALL=C.UTF-8
```

**Windows equivalents**: Windows uses UTF-16 internally and different locale identifiers. PowerShell defaults to UTF-16LE for output encoding. The `LANG` environment variable is not standard on Windows.

### 14. Hardcoded Temporary Paths

**Unix /tmp usage**:
- `/tmp/pre-start.log` (docker/run.clj line 289, docker/templates.clj line 204)
- `/tmp/aishell.sha256` (install.sh lines 91, 101, 125, 134)

**Windows equivalents**: `%TEMP%` or `$env:TEMP` (typically `C:\Users\<user>\AppData\Local\Temp`)

### 15. No Windows-Specific Code Found

**Search results**: No instances of:
- Windows path separators (`\` or `\\`)
- Windows environment variables (`%USERPROFILE%`, `%APPDATA%`)
- PowerShell invocations
- `.bat` or `.ps1` script generation
- `C:\` drive letters
- WSL detection or integration

The codebase is purely Unix-oriented with zero Windows adaptations.

## Second-Order Effects and Edge Cases

### Docker for Windows Complexity

Windows users typically run Docker Desktop, which offers two modes:
1. **Linux containers via WSL2**: Runs a Linux VM, mounts Windows paths via `/mnt/c/`, introduces path translation layer
2. **Windows containers**: Native Windows executables, completely different base images and ecosystem

Current aishell design assumes Linux containers exclusively. Supporting Windows containers would require:
- Rewriting all Dockerfile templates (different base image, package manager, user model)
- Replacing Bash entrypoint with PowerShell
- Eliminating UID/GID mapping (Windows has no equivalent)

### Path Translation Challenges

Even with Linux containers on Windows (via WSL2), path handling is complex:
- Windows paths: `C:\Users\john\project`
- Docker mount: `/mnt/c/Users/john/project` (case-sensitive!)
- Git in container sees Unix paths, Git on Windows sees Windows paths
- Symlinks don't work across WSL/Windows boundary

### Installation on Windows

The `install.sh` script is a complete blocker for Windows users without WSL. Options:
1. **Require WSL**: Document that Windows users must use WSL2
2. **PowerShell installer**: Rewrite install.sh as install.ps1
3. **Binary distribution**: Provide standalone .exe via GitHub Releases

### Babashka fs Library Cross-Platform Support

The `babashka.fs` library claims cross-platform support, and many operations work on Windows:
- `fs/exists?`, `fs/create-dirs`, `fs/path` work correctly
- `fs/home` returns Windows home directory
- `fs/canonicalize` works but behaves differently (no symlink resolution semantics)

However, the codebase bypasses these abstractions in key places:
- Dockerfile templates embed Unix paths directly
- Shell scripts use hardcoded `/bin/bash`, `/tmp/`, `/usr/local/bin/`
- Path expansion in `util.clj` assumes Unix `~` and `$HOME` patterns

### Container-Host Path Mapping

Docker mounts work differently on Windows:
- Linux Docker: direct bind mount with same path on both sides
- Windows Docker (WSL2): translates `C:\Users\...` to `/mnt/c/Users/...`
- Windows Docker (Hyper-V): uses VM shared folders with different semantics

The codebase assumes symmetric paths (mount source and destination can be identical), which breaks on Windows.

## Confidence Assessment

**Overall Confidence**: High

**Factors**:
- Direct codebase examination via Glob, Grep, and Read tools provides definitive evidence
- All 25 Clojure source files scanned systematically
- Patterns searched include: shell paths, file permissions, user management, ANSI codes, path separators, environment variables
- Cross-referenced findings across multiple files to confirm patterns
- Babashka and Docker documentation consulted for platform compatibility context

**Gaps**:
- Did not test actual Windows execution (analysis is static code review only)
- Babashka.process library may have undocumented Windows adaptations
- Docker for Windows behavior can vary by version and configuration
- Third-party dependencies (clj-yaml, etc.) not audited for Windows compatibility

**Certainty by category**:
- **Shell invocations**: 100% certain — hardcoded `/bin/bash` paths found in 18+ locations
- **Path handling**: 95% certain — all observed paths use Unix conventions, but `babashka.fs` provides some abstraction
- **User/group management**: 100% certain — Linux UID/GID model permeates codebase with no Windows fallback
- **Installation script**: 100% certain — pure Bash with no PowerShell equivalent
- **Terminal handling**: 90% certain — ANSI codes work on modern Windows but `infocmp` is Unix-only

## Sources

**Primary Sources** (Tier 1):
- [Codebase: src/aishell/*.clj](file:///home/jonasrodrigues/projects/harness/src/aishell/) (25 Clojure source files examined)
- [Codebase: install.sh](file:///home/jonasrodrigues/projects/harness/install.sh) (Bash installation script)
- [Codebase: bb.edn](file:///home/jonasrodrigues/projects/harness/bb.edn) (Babashka project configuration)

**Expert Analysis** (Tier 2):
- [Babashka Documentation](https://book.babashka.org/) (Cross-platform Clojure scripting runtime)
- [Docker for Windows Documentation](https://docs.docker.com/desktop/install/windows-install/) (Windows container modes and WSL2 integration)
- [Dockerfile Reference](https://docs.docker.com/reference/dockerfile/) (Multi-platform builds and Windows base images)

**Technical Context** (Tier 2):
- [XDG Base Directory Specification](https://specifications.freedesktop.org/basedir-spec/latest/) (Linux-specific config directory standard)
- [ANSI Escape Codes](https://en.wikipedia.org/wiki/ANSI_escape_code) (Terminal color and formatting sequences)
- [Windows Subsystem for Linux (WSL) Documentation](https://learn.microsoft.com/en-us/windows/wsl/) (Path translation and filesystem mapping)
