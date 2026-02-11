# Docker CLI Windows Behavior Research: Native Windows Support for aishell

## Research Parameters

**Topic**: Native Windows support for aishell (Babashka Clojure CLI tool that manages Docker containers)

**Perspective**: Docker CLI Windows Behavior

**Focus**: Docker Desktop on Windows differences — volume mount path translation, TTY/interactive mode, network behavior in cmd.exe vs PowerShell

**Date**: 2026-02-11

**Session**: 20260211-2024-native-windows-support-aishell

## Key Findings

- **Path translation is mandatory on Windows**: aishell must convert Windows-style paths (C:\Users\foo) to Docker-compatible format (/mnt/c/Users/foo for WSL2 or /c/Users/foo for Hyper-V), as Docker Desktop does not auto-translate all path formats in mount arguments. The codebase currently uses forward slashes but doesn't handle Windows absolute paths.

- **TTY allocation (-it flags) works on Windows but with caveats**: Interactive mode functions from both cmd.exe and PowerShell, but named pipe connections (default for Docker Desktop Windows) have documented limitations with interactive sessions—some Docker exec operations hang when using -it flags over named pipes.

- **Environment variable expansion syntax differs by shell**: Windows cmd.exe uses %VAR% syntax while PowerShell uses $VAR syntax for variable expansion before passing to Docker. The -e flag itself uses VAR=value syntax consistently across platforms, but host variable passthrough depends on which shell the user invokes aishell from.

- **File system performance penalty on WSL2**: Mounting volumes from Windows filesystem (/mnt/c/) instead of Linux filesystem significantly degrades performance; inotify events don't fire for Windows-mounted files, affecting any watch-mode functionality.

- **Docker volume mount syntax requires forward slashes**: Both WSL2 and Hyper-V backends require forward slashes in bind mount paths and explicit /mnt or / prefixes. Backslashes in path strings will fail or be misinterpreted across all Docker Desktop Windows backends.

## Analysis

### Path Translation and Volume Mounting

aishell's Docker argument builder in `src/aishell/docker/run.clj` currently constructs mount arguments using string concatenation: `-v {source}:{dest}`. The source path is expanded using `util/expand-path`, which on Windows will return backslash-separated paths (e.g., C:\Users\username\project).

**Problem**: Docker Desktop on Windows requires forward slashes in mount paths. The [official Docker documentation](https://docs.docker.com/desktop/features/wsl/) and community forum discussions confirm that backslashes cause mount failures or misinterpretation. Docker Desktop does some path translation magic for certain operations, but bind mounts require explicit proper formatting.

**WSL2 vs Hyper-V differences**:
- WSL2 (modern default): Windows paths like C:\Users\foo must translate to /mnt/c/Users/foo
- Hyper-V (older): Windows paths like C:\Users\foo must translate to /c/Users/foo

The [Linux file system vs Windows file system performance issue](https://docs.docker.com/desktop/features/wsl/best-practices/) is critical: if users mount source code from Windows (`/mnt/c/Users/...`), they'll experience significant I/O slowdowns and lose inotify support. Best practice is to work within WSL2's Linux filesystem, but aishell currently doesn't guide users toward this architecture.

### TTY and Interactive Mode Behavior

aishell uses `-it` flags for interactive mode and `-i` flags for non-interactive piped execution (in `build-docker-args` and `build-docker-args-for-exec`). According to Docker documentation and [Baeldung's TTY guide](https://www.baeldung.com/ops/docker-container-tty), both `-i` (stdin open) and `-t` (pseudo-TTY) work on Windows, with the note that PowerShell ISE should not be used for interactive sessions.

**Named pipe limitation**: Docker Desktop for Windows uses named pipes (//./pipe/docker_engine) by default instead of Unix sockets. [Community reports](https://github.com/apocas/docker-modem/issues/83) document that Docker exec operations requiring duplex streams (like interactive sessions) can hang when connected via named pipes. This is a known limitation that doesn't affect the -it flag syntax itself but may cause hanging on certain operations.

**Shell-specific behavior**:
- cmd.exe: Supports -it flags; variable expansion uses %VAR% before Docker invocation
- PowerShell: Supports -it flags; variable expansion uses $VAR before Docker invocation
- WSL2 bash: Standard Linux semantics; uses $VAR

The aishell code passes variables via the -e flag syntax (KEY=value), which is shell-agnostic. However, if users want to pass host variables through using bare `-e VAR` (passthrough mode), the logic at line 94 in `run.clj` uses `System/getenv`, which will read from the JVM's environment. On Windows launched from cmd.exe, that environment will be populated with Windows environment variables, so this should work correctly. The distinction between %VAR% and $VAR is only relevant if users construct custom docker_args strings that attempt variable substitution—aishell's main path doesn't do this.

### Environment Variable Passing

aishell supports two modes of environment variable configuration:
1. **Literal values**: `-e KEY=value` (always works, shell-independent)
2. **Passthrough from host**: `-e KEY` (requires checking System.getenv on the aishell JVM)

The code structure is sound for both modes. On Windows, when aishell runs from cmd.exe or PowerShell, the JVM's environment will contain the current shell's variables regardless of syntax (%VAR% vs $VAR%), because the environment was already inherited by the JVM at startup.

However, if users include custom docker_args with embedded variable expansion, they need to use the correct syntax for their shell:
```
# In cmd.exe
docker_args: -e CUSTOM=%MY_VAR%

# In PowerShell
docker_args: -e CUSTOM=$env:MY_VAR
```

aishell doesn't validate or transform these strings, so [the responsibility falls on users](https://docs.docker.com/compose/how-tos/environment-variables/set-environment-variables/).

### Port Mapping and Networking

According to the [Docker Desktop networking documentation](https://docs.docker.com/desktop/networking/), port publishing works identically on Windows and Linux. The `-p HOST:CONTAINER` syntax is universal. aishell's port validation pattern (line 104 in `run.clj`) accepts formats like `8080:80` and `127.0.0.1:8080:80`, which are platform-independent.

One difference: Windows Docker Desktop cannot route traffic to Linux containers directly (they run in WSL2 VM isolation), but can ping Windows containers. For aishell's use case of mounting host project directories and running tools, port mapping should work normally to localhost.

### Docker Compose vs docker run Behavioral Differences

While aishell currently uses direct `docker run` invocation rather than Docker Compose, understanding their differences is relevant for future Windows support. According to [Docker community forums](https://www.theserverside.com/blog/Coffee-Talk-Java-News-Stories-and-Opinions/Docker-run-vs-docker-compose-Whats-the-difference), Docker Compose has historically had more issues with Windows path handling. The core behavioral differences are:

- Docker Compose applies different default networking (automatic bridge)
- Docker Compose has had bugs with %VAR% expansion on Windows (some versions fixed by COMPOSE_CONVERT_WINDOWS_PATHS environment variable)
- Container orchestration logic differs, but individual container startup is equivalent when configs are properly translated

For aishell's direct `docker run` approach, these differences shouldn't apply. However, if future versions consider Compose integration, additional path normalization would be needed.

### Known Windows-Specific Gotchas

#### 1. WSL2 Version Requirements
[Docker Desktop WSL2 best practices](https://docs.docker.com/desktop/features/wsl/best-practices/) require WSL 2.1.5 minimum. Older versions cause Docker Desktop to hang during startup or upgrades. aishell should document this requirement if promoting Windows support.

#### 2. Memory Management and Kernel Caching
On WSL2, containers can consume excessive memory if the kernel page cache isn't reclaimed. The workaround (`echo 1 > /proc/sys/vm/drop_caches`) requires root access within WSL2. This could affect long-running harness operations on Windows systems with limited RAM.

#### 3. Path Separator in Shell Strings
[The most problematic issue](https://blog.sixeyed.com/windows-dockerfiles-and-the-backtick-backslash-backlash/): any shell command string passed via `-e` or `docker_args` that contains backslashes will be misinterpreted. For example, if `pre_start` config contains a path like `C:\project\script.sh`, it will fail. Users must use forward slashes even on Windows: `C:/project/script.sh`.

#### 4. Docker Socket Access Limitations
On Windows, the Docker socket isn't available as a Unix socket for bind-mounting into containers (common pattern for Docker-in-Docker). This is a fundamental architecture difference—Docker Desktop for Windows doesn't expose the daemon socket to containers. If aishell ever needs to support socket forwarding, it won't work on Windows Desktop without significant architectural changes (like socket forwarding through WSL2).

#### 5. Performance Degradation with /mnt/c Mounts
[Best practices documentation](https://docs.docker.com/desktop/features/wsl/best-practices/) emphasizes storing source code in the WSL2 Linux filesystem, not /mnt/c. aishell's current behavior (mounting whatever path user provides) will work but with 5-10x slower I/O on Windows-mounted directories. No watch/inotify events fire for /mnt/c files, breaking any future file-watching features.

### Babashka-Specific Windows Considerations

aishell is written in Babashka Clojure, which runs on the JVM but with SCI (Small Clojure Interpreter). Windows support inherits both JVM and SCI limitations:

1. **Process spawning**: Babashka's `babashka.process` library spawns `docker` command-line directly. On Windows, this invokes docker.exe via cmd.exe, which adds shell quoting and escaping concerns.

2. **Shell command execution**: The code at line 19 in `run.clj` uses `(p/shell {:out :string} "git" "config" key)`. This works fine on Windows because arguments are passed as a vector, not a shell string, avoiding quoting issues.

3. **Path expansion**: Babashka's `babashka.fs/which` (used to check if docker is available) should work correctly on Windows, finding docker.exe in PATH.

4. **UID/GID passing**: Lines 28-32 in `run.clj` call `id -u` and `id -g`. These commands **don't exist in Windows cmd.exe**. On WSL2, aishell would need to detect it's running inside WSL and skip these commands, or gracefully handle the exception. If run from Windows cmd.exe directly (not WSL2 bash), this will crash.

**Critical issue**: The current code unconditionally calls `get-uid` and `get-gid`, which will fail on Windows cmd.exe. For Windows support, aishell needs:
```clojure
(defn- get-uid []
  (if (fs/windows?)
    "1000"  ; Default UID for Windows containers
    (-> (p/shell {:out :string} "id" "-u") :out str/trim)))
```

### Impact on aishell's Current Architecture

Examining `src/aishell/docker/run.clj` and `src/aishell/docker/volume.clj`:

**Must-fix for Windows**:
1. Lines 28-32: Add Windows detection; skip uid/gid retrieval on Windows
2. Line 51 (mount source path): Convert backslashes to forward slashes
3. Line 237 (project-dir mount): Ensure forward slashes and /mnt prefix on WSL2

**Should-fix for good Windows UX**:
1. Line 302 (volume mount in populate-volume): Apply same path normalization
2. Documentation: Warn against using /mnt/c paths for source code
3. Volume population: May need WSL2 detection if using npm packages with native dependencies

**Low priority but considerate**:
1. Document WSL2 2.1.5 minimum requirement
2. Add Windows shell detection (cmd.exe vs PowerShell) for clearer error messages
3. Consider memory/resource recommendations for Windows users

## Sources

**Primary Sources** (Tier 1):

- [Official Docker Documentation: Desktop WSL 2 Backend](https://docs.docker.com/desktop/features/wsl/)
- [Official Docker Desktop WSL 2 Best Practices](https://docs.docker.com/desktop/features/wsl/best-practices/)
- [Official Docker Desktop Networking](https://docs.docker.com/desktop/networking/)
- [Official Docker Container Run Reference](https://docs.docker.com/reference/cli/docker/container/run/)
- [Official Docker Set Environment Variables Documentation](https://docs.docker.com/compose/how-tos/environment-variables/set-environment-variables/)

**Expert Analysis** (Tier 2):

- [How To Mount Your Current Working Directory To Your Docker Container In Windows](https://medium.com/@kale.miller96/how-to-mount-your-current-working-directory-to-your-docker-container-in-windows-74e47fa104d7)
- [Baeldung: How to Enter a Running Docker Container With a TTY](https://www.baeldung.com/ops/docker-container-tty)
- [Setting Up Docker for Windows and WSL to Work Flawlessly](https://nickjanetakis.com/blog/setting-up-docker-for-windows-and-wsl-to-work-flawlessly)
- [Windows, Dockerfiles and the Backtick Backslash Backlash](https://blog.sixeyed.com/windows-dockerfiles-and-the-backtick-backslash-backlash/)

**Community and Issue Reports** (Tier 2-3):

- [Docker Community Forum: Correct Way to Mount a Volume on Docker for Windows](https://forums.docker.com/t/whats-the-correct-way-to-mount-a-volume-on-docker-for-windows/58494)
- [GitHub Issue: Docker Modem Duplex Stream Hang on Named Pipes](https://github.com/apocas/docker-modem/issues/83)
- [GitHub Discussion: WSL2 Docker Volume Location](https://github.com/microsoft/WSL/discussions/4176)
- [Docker Community Forum: WSL 2 and Port Forwarding](https://forums.docker.com/t/wsl-2-and-port-forwarding/94891)

**Tools and Libraries** (Tier 2):

- [Babashka Documentation: Process Library](https://github.com/babashka/process)
- [Babashka Official Site](https://babashka.org/)

## Confidence Assessment

**Overall Confidence**: Moderate-High (65-75%)

**Factors supporting confidence**:
- Official Docker documentation is extensive and current for WSL2 backend
- Docker Desktop for Windows is a mature product (2+ years stable) with well-documented limitations
- Community forum discussions provide real-world validation of documented behaviors
- aishell codebase is readable and path logic is straightforward

**Factors limiting confidence**:
- No direct testing on Windows systems (research is literature-based)
- Babashka's specific Windows behavior not extensively documented; relying on general JVM assumptions
- Docker Desktop Windows implementation details (named pipe handling, memory limits) may vary by version
- WSL2 integration changes with Windows 11 updates; some edge cases may be version-specific
- aishell's Babashka/SCI symbol resolution requirements not fully analyzed for Windows startup

**Gaps**:
- Actual Windows-based testing of aishell required to validate uid/gid failure hypothesis
- Unknown if Babashka's `babashka.fs/which` correctly finds docker.exe on Windows PATH
- No investigation of potential issues with git config calls (line 16-26) if Git isn't in PATH
- Memory/resource constraints on typical Windows Docker Desktop configurations not quantified
- No analysis of how aishell's shell spawning in pre_start hooks behaves on Windows shells

**Most critical unknown**: Whether the unconditional `get-uid`/`get-gid` calls will actually crash or gracefully degrade on Windows cmd.exe. If Babashka's `p/shell` catches exceptions, the tool might continue with nil values; if not, it will crash immediately on Windows.
