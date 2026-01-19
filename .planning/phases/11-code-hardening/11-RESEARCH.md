# Phase 11: Code Hardening - Research

**Researched:** 2026-01-19
**Domain:** Bash scripting, input validation, signal handling, Docker security
**Confidence:** HIGH

## Summary

This phase focuses on hardening the aishell Bash script against edge cases and security concerns. The research covers seven requirements across input validation (port mapping, version strings, HOME fallback), robustness (trap consolidation, signal handling, zombie reaping), and security awareness (dangerous Docker arguments).

The current codebase has functional implementations that work for the happy path but need refinement for edge cases. The port mapping regex at line 576 only accepts `host:container` format but Docker supports `ip:host:container`. Multiple `trap` statements exist (lines 102, 731, 755, 1047, 1255, 1278) which can override each other. The version strings are passed directly to npm/curl without validation.

**Primary recommendation:** Consolidate all trap handlers into a single cleanup function registered once at script start; validate all external inputs with strict regex before shell interpolation; add `--init` flag to docker run; warn on dangerous DOCKER_ARGS patterns.

## Standard Stack

This phase uses only Bash built-ins and existing tools in the codebase. No new dependencies required.

### Core

| Tool | Version | Purpose | Why Standard |
|------|---------|---------|--------------|
| Bash | 5.x | Shell scripting | Already in use, POSIX + extensions for regex |
| mktemp | GNU | Temp file creation | Already in use, secure by default |
| trap | Bash built-in | Signal handling | Standard mechanism for cleanup |

### Supporting

| Tool | Purpose | When to Use |
|------|---------|-------------|
| `[[ =~ ]]` | Regex matching | Input validation (POSIX ERE) |
| `${VAR:-default}` | Parameter expansion | HOME fallback |
| `$!` | Background PID | Tracking subprocesses for cleanup |

### Alternatives Considered

| Instead of | Could Use | Tradeoff |
|------------|-----------|----------|
| Bash regex | grep -E | External process; Bash built-in is faster for simple patterns |
| Manual cleanup | Tini in image | Would require modifying Dockerfile; --init is simpler |

## Architecture Patterns

### Consolidated Trap Handler Pattern

**What:** Single cleanup function registered once at script start, managing all resources.

**When to use:** Any script that creates temp files, spawns background processes, or needs cleanup on exit.

**Why:** Each `trap` command overwrites previous handlers for the same signal. Multiple trap statements lead to bugs where early handlers are lost.

**Current Problem (lines 102, 731, 755, etc.):**
```bash
trap 'stop_spinner' EXIT                    # Line 102
trap "rm -rf '$build_dir'" RETURN           # Line 731
trap "rm -rf '$build_dir' '$build_log'" RETURN  # Line 755 - overwrites 731!
```

**Recommended Pattern:**
```bash
# Source: https://mywiki.wooledge.org/SignalTrap
# Source: https://www.putorius.net/using-trap-to-exit-bash-scripts-cleanly.html

# Global state for cleanup
CLEANUP_FILES=()
CLEANUP_PIDS=()

cleanup() {
    # Stop spinner if running
    [[ -n "${spinner_pid:-}" ]] && kill "$spinner_pid" 2>/dev/null || true

    # Remove temp files
    for f in "${CLEANUP_FILES[@]}"; do
        [[ -e "$f" ]] && rm -rf "$f"
    done

    # Kill tracked background processes
    for pid in "${CLEANUP_PIDS[@]}"; do
        kill "$pid" 2>/dev/null || true
    done
}

# Register ONCE at script start - EXIT handles all exit paths
trap cleanup EXIT

# Helper to register files for cleanup
register_cleanup_file() {
    CLEANUP_FILES+=("$1")
}

# Usage in build functions:
build_dir=$(mktemp -d)
register_cleanup_file "$build_dir"
```

### Input Validation Pattern

**What:** Validate all external input before use in shell commands.

**When to use:** Any user input, environment variable, or config file value that gets interpolated into commands.

**Why:** Prevents shell injection and ensures predictable behavior.

**Pattern:**
```bash
# Source: https://github.com/semver/semver/issues/981
# Source: https://www.baeldung.com/linux/ip-address-test-valid

# Validate early, fail fast
validate_or_die() {
    local value="$1"
    local pattern="$2"
    local error_msg="$3"

    if [[ ! "$value" =~ $pattern ]]; then
        error "$error_msg"
    fi
}

# Example: version validation
VERSION_PATTERN='^[0-9]+\.[0-9]+\.[0-9]+(-[a-zA-Z0-9.-]+)?$'
validate_or_die "$version" "$VERSION_PATTERN" "Invalid version format: $version"
```

### Anti-Patterns to Avoid

- **Multiple trap statements for same signal:** Overwrites previous handlers
- **trap in loops/functions:** Hard to reason about, easy to forget cleanup
- **Unvalidated input in commands:** Shell injection risk
- **Regex without anchors:** Partial matches cause false positives

## Don't Hand-Roll

Problems with existing solutions in Bash or Docker:

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| Zombie process reaping | Custom PID 1 logic | `docker run --init` | Docker includes Tini, handles edge cases |
| Temp file security | Custom naming | `mktemp` | Race condition resistant, secure by default |
| Semver validation | Basic numeric check | Full semver regex | Pre-release, build metadata are valid semver |
| Process cleanup | Kill by name | Track PIDs, kill by PID | More reliable, avoids killing wrong processes |

**Key insight:** Docker's `--init` flag (line 1357 area) is the correct solution for zombie reaping. It injects Tini transparently with no image changes needed. Do NOT try to handle this in entrypoint.sh.

## Common Pitfalls

### Pitfall 1: Trap Override Bug

**What goes wrong:** Later `trap` calls silently replace earlier handlers.
**Why it happens:** Bash's `trap` is a setter, not an appender.
**How to avoid:** Single trap registration at script start with one consolidated cleanup function.
**Warning signs:** Multiple `trap` statements for EXIT/RETURN in the same script.

**Current codebase issue:**
- Line 102: `trap 'stop_spinner' EXIT`
- Line 731: `trap "rm -rf '$build_dir'" RETURN`
- Line 755: `trap "rm -rf '$build_dir' '$build_log'" RETURN` (overwrites 731)

### Pitfall 2: Shell Injection via Version Strings

**What goes wrong:** Version like `1.0.0; rm -rf /` executed in npm/curl command.
**Why it happens:** Unvalidated input interpolated into shell commands.
**How to avoid:** Validate against strict semver-like pattern before use.
**Warning signs:** Any variable used in `npm install pkg@$version` or `curl ... $version`.

**Current risk points:**
- Line 180: `npm install -g @anthropic-ai/claude-code@"$CLAUDE_VERSION"`
- Line 192: `VERSION="$OPENCODE_VERSION" curl -fsSL https://opencode.ai/install | bash`

### Pitfall 3: PORT Regex Too Restrictive

**What goes wrong:** Valid Docker port mapping `127.0.0.1:8080:80` rejected.
**Why it happens:** Current regex (line 576) only accepts `host:container` format.
**How to avoid:** Update regex to accept `[ip:]host:container[/protocol]` format.
**Warning signs:** Users report working docker commands fail via run.conf.

**Current regex:** `^[0-9]+:[0-9]+(/[a-z]+)?$`
**Needed regex:** `^([0-9.]+:)?[0-9]+:[0-9]+(/[a-z]+)?$` (simplified) or full IP validation.

### Pitfall 4: HOME Not Set

**What goes wrong:** Script fails with cryptic error when HOME is unset or empty.
**Why it happens:** Some environments (cron, certain containers) don't set HOME.
**How to avoid:** Check and provide fallback early in script.
**Warning signs:** Errors about "No such file or directory" with paths containing `//`.

### Pitfall 5: Orphaned Spinner Process

**What goes wrong:** User hits Ctrl+C during build, spinner continues running.
**Why it happens:** Background spinner process not in same process group, doesn't receive SIGINT.
**How to avoid:** Explicitly track spinner PID and kill in cleanup function.
**Warning signs:** Phantom cursor movement after script exit.

### Pitfall 6: SIGINT During Subshell

**What goes wrong:** User hits Ctrl+C, but docker build continues.
**Why it happens:** Bash waits for foreground command to complete before handling signal.
**How to avoid:** For long-running commands, consider running in background and waiting with trap handling.
**Warning signs:** Ctrl+C appears ignored during docker build.

## Code Examples

### VALID-01: Port Mapping with IP Binding

```bash
# Source: https://docs.docker.com/engine/network/port-publishing/
# Docker port syntax: [host_ip:]host_port:container_port[/protocol]

# Comprehensive regex for Docker port mapping
# Supports: 8080:80, 127.0.0.1:8080:80, 192.168.1.100:8080:80/tcp
PORT_REGEX='^(([0-9]{1,3}\.){3}[0-9]{1,3}:)?[0-9]+:[0-9]+(/[a-z]+)?$'

build_port_args() {
    local ports_str="$1"
    [[ -z "$ports_str" ]] && return 0

    for port_spec in $ports_str; do
        if [[ "$port_spec" =~ $PORT_REGEX ]]; then
            printf '%s\n' "-p"
            printf '%s\n' "$port_spec"
        else
            error "Invalid port mapping: $port_spec
Expected format: HOST_PORT:CONTAINER_PORT or IP:HOST_PORT:CONTAINER_PORT
Examples: 8080:80, 127.0.0.1:8080:80, 8080:80/udp"
        fi
    done
}
```

### VALID-02: Version String Validation

```bash
# Source: https://github.com/semver/semver/issues/981
# Source: https://github.com/har7an/bash-semver-regex

# Semver-like pattern (major.minor.patch with optional pre-release)
# Intentionally simpler than full semver - just blocks shell metacharacters
VERSION_REGEX='^[0-9]+\.[0-9]+\.[0-9]+(-[a-zA-Z0-9.-]+)?(\+[a-zA-Z0-9.-]+)?$'

# Shell metacharacter blocklist (defensive)
DANGEROUS_CHARS_REGEX='[;&|`$(){}[\]<>!\\]'

validate_version() {
    local version="$1"
    local name="$2"  # "Claude Code" or "OpenCode"

    # Empty is OK (means "latest")
    [[ -z "$version" ]] && return 0

    # Block dangerous characters first (defense in depth)
    if [[ "$version" =~ $DANGEROUS_CHARS_REGEX ]]; then
        error "Invalid $name version: contains shell metacharacters"
    fi

    # Validate semver-like format
    if [[ ! "$version" =~ $VERSION_REGEX ]]; then
        error "Invalid $name version format: $version
Expected: X.Y.Z or X.Y.Z-prerelease (e.g., 2.0.22, 1.0.0-beta.1)"
    fi
}

# Usage in argument parsing:
--claude-version=*)
    claude_version="${1#--claude-version=}"
    validate_version "$claude_version" "Claude Code"
    ;;
```

### VALID-03: HOME Fallback

```bash
# Source: https://specifications.freedesktop.org/basedir/basedir-spec-latest.html

# Validate HOME early in script, provide fallback
validate_home() {
    if [[ -z "${HOME:-}" ]]; then
        # Try to determine from passwd entry
        HOME=$(getent passwd "$(id -u)" | cut -d: -f6) || true

        # Final fallback
        if [[ -z "$HOME" ]]; then
            HOME="/tmp/aishell-$$"
            warn "HOME not set, using fallback: $HOME"
            mkdir -p "$HOME"
        fi
        export HOME
    fi

    # Ensure HOME directory exists
    if [[ ! -d "$HOME" ]]; then
        warn "HOME directory does not exist: $HOME"
        mkdir -p "$HOME" 2>/dev/null || error "Cannot create HOME directory"
    fi
}

# Call early in main():
validate_home
```

### ROBUST-01: Consolidated Trap Handler

```bash
# Source: https://mywiki.wooledge.org/SignalTrap
# Source: https://www.putorius.net/using-trap-to-exit-bash-scripts-cleanly.html

# Global cleanup state
declare -a CLEANUP_FILES=()
declare -a CLEANUP_PIDS=()
spinner_pid=""

# Consolidated cleanup function
cleanup() {
    local exit_code=$?

    # Stop spinner if running
    if [[ -n "$spinner_pid" ]]; then
        kill "$spinner_pid" 2>/dev/null || true
        printf "\r\033[K" >&2  # Clear spinner line
        spinner_pid=""
    fi

    # Remove registered temp files/directories
    for path in "${CLEANUP_FILES[@]}"; do
        [[ -e "$path" ]] && rm -rf "$path" 2>/dev/null
    done

    # Kill tracked background processes
    for pid in "${CLEANUP_PIDS[@]}"; do
        kill "$pid" 2>/dev/null || true
    done

    exit "$exit_code"
}

# Register cleanup ONCE at script start
trap cleanup EXIT

# Helper functions
register_cleanup() {
    CLEANUP_FILES+=("$1")
}

track_pid() {
    CLEANUP_PIDS+=("$1")
}

# Modified spinner that uses global
start_spinner() {
    local msg="$1"
    [[ ! -t 2 ]] && return

    while true; do
        printf "\r%s %c " "$msg" "${spin:i++%${#spin}:1}" >&2
        sleep 0.1
    done &
    spinner_pid=$!
    track_pid "$spinner_pid"
}

stop_spinner() {
    if [[ -n "$spinner_pid" ]]; then
        kill "$spinner_pid" 2>/dev/null || true
        spinner_pid=""
        printf "\r\033[K" >&2
    fi
}

# Usage in build:
build_dir=$(mktemp -d)
register_cleanup "$build_dir"
# No more trap overrides needed!
```

### ROBUST-02: Signal Handling During Build

```bash
# Source: https://linuxvox.com/blog/bash-how-do-i-make-sub-processes-of-a-script-be-terminated-when-the-script-is-terminated/
# Source: https://www.vidarholen.net/contents/blog/?p=34

# For interactive builds, ensure Ctrl+C propagates cleanly
# The EXIT trap handles cleanup; we just need to ensure
# background docker processes are tracked

do_build_with_signal_handling() {
    local build_dir build_log docker_pid

    build_dir=$(mktemp -d)
    register_cleanup "$build_dir"

    if [[ "$verbose_build" == true ]]; then
        # Foreground: Ctrl+C naturally propagates
        docker build "${build_args[@]}" -t "$target_tag" "$build_dir"
    else
        build_log=$(mktemp)
        register_cleanup "$build_log"

        start_spinner "Building image"

        # Run docker in background so we can handle signals
        docker build "${build_args[@]}" -t "$target_tag" "$build_dir" > "$build_log" 2>&1 &
        docker_pid=$!
        track_pid "$docker_pid"

        # Wait for docker build - if we get signaled, cleanup runs
        if wait "$docker_pid"; then
            stop_spinner
        else
            stop_spinner
            error "Build failed:
$(cat "$build_log")"
        fi
    fi
}
```

### ROBUST-03: Adding --init Flag

```bash
# Source: https://github.com/krallin/tini
# Source: https://www.paolomainardi.com/posts/docker-run-init/

# Add --init to docker_args array for zombie reaping
# This injects tini as PID 1, handling zombie processes

# In main() where docker_args is built:
local -a docker_args=(
    --rm -it
    --init  # ROBUST-03: Use tini for zombie process reaping
    -v "$project_dir:$project_dir"
    -w "$project_dir"
    # ... rest of args
)
```

### SEC-01: Dangerous DOCKER_ARGS Warning

```bash
# Source: https://cheatsheetseries.owasp.org/cheatsheets/Docker_Security_Cheat_Sheet.html
# Source: https://docs.docker.com/engine/security/

# Patterns that warrant security warnings
DANGEROUS_DOCKER_PATTERNS=(
    "--privileged"
    "/var/run/docker.sock"
    "docker.sock"
    "--cap-add=SYS_ADMIN"
    "--cap-add=ALL"
    "--security-opt.*apparmor=unconfined"
    "--security-opt.*seccomp=unconfined"
)

warn_dangerous_docker_args() {
    local docker_args="$1"
    local warned=false

    for pattern in "${DANGEROUS_DOCKER_PATTERNS[@]}"; do
        if [[ "$docker_args" =~ $pattern ]]; then
            if [[ "$warned" == false ]]; then
                echo "" >&2
                warn "Security notice: Potentially dangerous Docker options detected"
                warned=true
            fi

            case "$pattern" in
                "--privileged")
                    echo "  - --privileged: Container has full host access" >&2
                    ;;
                *"docker.sock"*)
                    echo "  - docker.sock mount: Container can control Docker daemon" >&2
                    ;;
                *"SYS_ADMIN"*|*"ALL"*)
                    echo "  - Elevated capabilities: Increases container escape risk" >&2
                    ;;
                *"apparmor"*|*"seccomp"*)
                    echo "  - Disabled security profiles: Reduces container isolation" >&2
                    ;;
            esac
        fi
    done

    if [[ "$warned" == true ]]; then
        echo "" >&2
        echo "These options reduce container isolation. Use only if necessary." >&2
        echo "" >&2
    fi
}

# Call after parsing run.conf:
if [[ -n "$CONF_DOCKER_ARGS" ]]; then
    warn_dangerous_docker_args "$CONF_DOCKER_ARGS"
fi
```

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| Multiple trap statements | Single consolidated handler | Best practice since Bash 4 | Prevents cleanup bugs |
| Custom PID 1 handling | Docker --init flag | Docker 1.13 (2017) | Simpler, more reliable |
| Manual zombie reaping | Tini built into Docker | Docker 1.13 | No image changes needed |
| Whitelist validation | Blocklist + whitelist | Defense in depth | Catches more edge cases |

**Deprecated/outdated:**
- `trap ... RETURN` for function-local cleanup: Error-prone, prefer EXIT with tracking
- Custom init processes in containers: Use --init unless you need specific behavior
- Signal numbers in trap: Use symbolic names (SIGINT, SIGTERM) for portability

## Open Questions

1. **IPv6 Port Binding**
   - What we know: Docker supports IPv6 format `[::]::80`
   - What's unclear: Should aishell support this in PORTS?
   - Recommendation: Start with IPv4 only, document limitation

2. **Version "latest" Handling**
   - What we know: Empty version means latest in npm/curl
   - What's unclear: Should we accept literal string "latest"?
   - Recommendation: Accept empty or valid semver, reject "latest" as it's implicit

3. **Build Interruption State**
   - What we know: Ctrl+C during build can leave partial temp files
   - What's unclear: Should we track docker build layers for cleanup?
   - Recommendation: Clean temp dir is sufficient; Docker handles its own cache

## Sources

### Primary (HIGH confidence)
- [Docker Port Publishing Documentation](https://docs.docker.com/engine/network/port-publishing/) - Official syntax for -p flag
- [Docker Security Documentation](https://docs.docker.com/engine/security/) - Security best practices
- [OWASP Docker Security Cheat Sheet](https://cheatsheetseries.owasp.org/cheatsheets/Docker_Security_Cheat_Sheet.html) - Security patterns
- [Tini (krallin/tini)](https://github.com/krallin/tini) - Official init for containers

### Secondary (MEDIUM confidence)
- [Greg's Wiki - SignalTrap](https://mywiki.wooledge.org/SignalTrap) - Bash trap best practices
- [Baeldung - Bash Signal Handling](https://www.baeldung.com/linux/bash-signal-handling) - Signal patterns
- [semver/semver#981](https://github.com/semver/semver/issues/981) - POSIX semver regex
- [har7an/bash-semver-regex](https://github.com/har7an/bash-semver-regex) - Bash semver implementation
- [XDG Base Directory Specification](https://specifications.freedesktop.org/basedir/basedir-spec-latest.html) - HOME/XDG standards

### Tertiary (LOW confidence)
- Various blog posts on trap consolidation patterns
- Community discussions on zombie process handling

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH - Using only Bash built-ins, well-documented
- Architecture (trap consolidation): HIGH - Well-established pattern, multiple authoritative sources
- Architecture (validation): HIGH - Standard defensive coding practice
- Pitfalls: HIGH - Based on actual code review of aishell
- Docker --init: HIGH - Official Docker feature, documented

**Research date:** 2026-01-19
**Valid until:** 90 days (stable Bash/Docker patterns)
