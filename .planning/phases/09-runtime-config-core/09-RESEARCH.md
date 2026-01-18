# Phase 9: Runtime Config Core - Research

**Researched:** 2026-01-18
**Domain:** Bash config file parsing, Docker runtime arguments, shell security
**Confidence:** HIGH

## Summary

This phase implements per-project runtime configuration via `.aishell/run.conf`. The core challenges are:
1. **Safely sourcing shell-format config files** - The config uses shell variable syntax (already decided), which means we need to prevent code injection while allowing legitimate variable values
2. **Parsing multiple config formats** - MOUNTS, ENV, PORTS use array-like syntax with space-separated values; DOCKER_ARGS is a string passed directly
3. **Variable expansion** - $HOME needs to be expanded in mount paths before passing to docker

The existing codebase already has a proven pattern for safe config sourcing in `read_state_file()` (lines 379-403 in `aishell`). This same pattern can be extended with a stricter whitelist for allowed variable names (MOUNTS, ENV, PORTS, DOCKER_ARGS).

**Primary recommendation:** Parse run.conf line-by-line with regex validation, allowing only whitelisted variable names in `VAR=value` format. Expand $HOME in mount paths using bash parameter expansion. Build docker run arguments incrementally and pass them to the existing docker run construction.

## Standard Stack

The established libraries/tools for this domain:

### Core
| Component | Version | Purpose | Why Standard |
|-----------|---------|---------|--------------|
| bash read | 5.x | Line-by-line config parsing | Native to bash, no dependencies |
| bash regex | [[ =~ ]] | Pattern matching for validation | Built-in, fast, well-documented |
| IFS | Bash builtin | Splitting array values | Native word splitting |
| realpath | coreutils | Path canonicalization | Handles symlinks, ~, .. |

### Supporting
| Tool | Version | Purpose | When to Use |
|------|---------|---------|-------------|
| envsubst | gettext | Variable expansion alternative | Only if manual expansion too complex |
| getent | glibc | Home directory lookup | For non-current-user HOME |

### Alternatives Considered
| Instead of | Could Use | Tradeoff |
|------------|-----------|----------|
| Line-by-line parsing | source with grep filter | Source still executes code; filter can miss edge cases |
| Bash regex [[ =~ ]] | grep -E validation | Extra process per line; slower |
| Manual $HOME expansion | envsubst | Adds dependency; overkill for single variable |

**Installation:**
```bash
# All tools already available in Debian bookworm-slim base image
# No additional packages needed
```

## Architecture Patterns

### Recommended Config File Structure
```
.aishell/
├── Dockerfile          # (existing) Project image extension
└── run.conf            # (new) Runtime configuration
```

### Config File Format
```bash
# .aishell/run.conf - Runtime configuration for aishell containers
# Lines: VARIABLE=value or VARIABLE="space separated values"

# Volume mounts (host paths, $HOME supported)
MOUNTS="$HOME/.ssh $HOME/.config/git"

# Environment variables (VAR or VAR=value)
ENV="EDITOR DEBUG_MODE=1 MY_VAR=hello"

# Port mappings (host:container)
PORTS="3000:3000 8080:80"

# Extra docker run arguments (passed through directly)
DOCKER_ARGS="--cap-add=SYS_PTRACE"
```

### Pattern 1: Whitelist-Based Config Parsing
**What:** Parse config file allowing only specific variable names
**When to use:** For any config file that will influence command execution
**Example:**
```bash
# Source: Pattern from existing read_state_file() + security best practices
parse_run_conf() {
    local config_file="$1"
    local line_num=0

    # Initialize variables (caller can check if set)
    CONF_MOUNTS=""
    CONF_ENV=""
    CONF_PORTS=""
    CONF_DOCKER_ARGS=""

    [[ ! -f "$config_file" ]] && return 0

    while IFS= read -r line || [[ -n "$line" ]]; do
        ((line_num++))

        # Skip empty lines and comments
        [[ -z "$line" || "$line" =~ ^[[:space:]]*# ]] && continue

        # Trim leading/trailing whitespace
        line="${line#"${line%%[![:space:]]*}"}"
        line="${line%"${line##*[![:space:]]}"}"

        # Validate: must be ALLOWED_VAR=value format
        if [[ "$line" =~ ^(MOUNTS|ENV|PORTS|DOCKER_ARGS)=(.*)$ ]]; then
            local var_name="${BASH_REMATCH[1]}"
            local var_value="${BASH_REMATCH[2]}"

            # Strip surrounding quotes if present
            if [[ "$var_value" =~ ^\"(.*)\"$ ]] || [[ "$var_value" =~ ^\'(.*)\'$ ]]; then
                var_value="${BASH_REMATCH[1]}"
            fi

            # Set the variable with CONF_ prefix
            declare -g "CONF_${var_name}=$var_value"
        else
            error "Syntax error in $config_file line $line_num:
  $line

Expected format: VARIABLE=value
Allowed variables: MOUNTS, ENV, PORTS, DOCKER_ARGS"
        fi
    done < "$config_file"
}
```

### Pattern 2: Safe $HOME Expansion in Paths
**What:** Expand $HOME in mount paths before passing to docker
**When to use:** Processing MOUNTS configuration
**Example:**
```bash
# Source: Bash parameter expansion best practices
expand_home_in_path() {
    local path="$1"

    # Replace $HOME with actual value
    path="${path//\$HOME/$HOME}"

    # Also handle ${HOME} syntax
    path="${path//\$\{HOME\}/$HOME}"

    # Handle ~ at start of path (tilde expansion doesn't happen in quotes)
    if [[ "$path" == "~"* ]]; then
        path="$HOME${path:1}"
    fi

    echo "$path"
}

# Usage for MOUNTS array
build_mount_args() {
    local mounts_str="$1"
    local -a mount_args=()

    # Split on whitespace
    for mount in $mounts_str; do
        local expanded_path
        expanded_path=$(expand_home_in_path "$mount")

        # Warn if path doesn't exist (Docker handles gracefully)
        [[ ! -e "$expanded_path" ]] && warn "Mount path does not exist: $expanded_path"

        # Mount at same path in container (read-write)
        mount_args+=("-v" "$expanded_path:$expanded_path")
    done

    printf '%s\n' "${mount_args[@]}"
}
```

### Pattern 3: ENV Variable Parsing (Passthrough vs Literal)
**What:** Handle both VAR (passthrough) and VAR=value (literal) syntax
**When to use:** Processing ENV configuration
**Example:**
```bash
# Source: Docker -e syntax, similar to docker --env-file format
build_env_args() {
    local env_str="$1"
    local -a env_args=()

    # Split on whitespace
    for env_entry in $env_str; do
        if [[ "$env_entry" == *"="* ]]; then
            # Literal value: VAR=value -> -e VAR=value
            env_args+=("-e" "$env_entry")
        else
            # Passthrough: VAR -> -e VAR (docker inherits from host)
            # Only pass if set on host
            if [[ -n "${!env_entry:-}" ]]; then
                env_args+=("-e" "$env_entry")
            else
                warn "Environment variable not set on host: $env_entry"
            fi
        fi
    done

    printf '%s\n' "${env_args[@]}"
}
```

### Pattern 4: Port Mapping Validation
**What:** Validate port syntax before passing to docker
**When to use:** Processing PORTS configuration
**Example:**
```bash
# Source: Docker port mapping documentation
build_port_args() {
    local ports_str="$1"
    local -a port_args=()

    # Split on whitespace
    for port in $ports_str; do
        # Validate format: [host_port]:[container_port][/protocol]
        if [[ "$port" =~ ^([0-9]+):([0-9]+)(/[a-z]+)?$ ]]; then
            port_args+=("-p" "$port")
        else
            error "Invalid port format: $port
Expected: host_port:container_port (e.g., 8080:80)"
        fi
    done

    printf '%s\n' "${port_args[@]}"
}
```

### Pattern 5: Integrating Config into Docker Run
**What:** Merge run.conf settings into existing docker run command
**When to use:** At container launch time
**Example:**
```bash
# Integration point in main() after existing docker_args setup
apply_runtime_config() {
    local project_dir="$1"
    local -n docker_args_ref="$2"  # nameref to docker_args array

    local config_file="$project_dir/.aishell/run.conf"

    # Parse config (sets CONF_* variables)
    parse_run_conf "$config_file"

    # Add mounts
    if [[ -n "$CONF_MOUNTS" ]]; then
        while IFS= read -r arg; do
            [[ -n "$arg" ]] && docker_args_ref+=("$arg")
        done < <(build_mount_args "$CONF_MOUNTS")
    fi

    # Add environment variables
    if [[ -n "$CONF_ENV" ]]; then
        while IFS= read -r arg; do
            [[ -n "$arg" ]] && docker_args_ref+=("$arg")
        done < <(build_env_args "$CONF_ENV")
    fi

    # Add port mappings
    if [[ -n "$CONF_PORTS" ]]; then
        while IFS= read -r arg; do
            [[ -n "$arg" ]] && docker_args_ref+=("$arg")
        done < <(build_port_args "$CONF_PORTS")
    fi

    # Add extra docker args (passed through as-is, split on whitespace)
    if [[ -n "$CONF_DOCKER_ARGS" ]]; then
        # shellcheck disable=SC2206
        docker_args_ref+=($CONF_DOCKER_ARGS)
    fi
}
```

### Anti-Patterns to Avoid
- **Using source directly:** Even with grep filtering, source executes code. Parse line-by-line instead.
- **Allowing arbitrary variable names:** Whitelist only MOUNTS, ENV, PORTS, DOCKER_ARGS.
- **Eval on user input:** Never use eval with config values.
- **Ignoring parse errors:** Fail early with helpful messages including line numbers.
- **Expanding $HOME after quoting:** Must expand before the value is quoted for docker.

## Don't Hand-Roll

Problems that look simple but have existing solutions:

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| JSON/YAML parsing | grep/sed extraction | jq / yq | Edge cases with escaping, nesting |
| Path canonicalization | Manual string ops | realpath | Symlinks, .., ~ handling |
| Complex env file parsing | Custom parser | docker --env-file | Docker handles format natively |
| Port validation | Simple string check | Regex with capture groups | IPv6, protocols, ranges |

**Key insight:** The run.conf format is intentionally simple (shell variables) because the target audience is developers who will edit it by hand. Complex formats like JSON would be harder to write and validate.

## Common Pitfalls

### Pitfall 1: Code Injection via Config File
**What goes wrong:** Malicious run.conf executes arbitrary commands
**Why it happens:** Using source, eval, or insufficient validation
**How to avoid:** Parse line-by-line; whitelist only allowed variable names; never execute config content
**Warning signs:** Config "values" that look like commands; backticks or $() in values

### Pitfall 2: Unquoted Values with Spaces
**What goes wrong:** MOUNTS="$HOME/My Documents" becomes two separate paths
**Why it happens:** Word splitting when iterating over values
**How to avoid:** Document that paths with spaces should not be used, OR implement proper quote handling
**Warning signs:** "Mount path does not exist" for partial paths

### Pitfall 3: $HOME Not Expanded Before Docker
**What goes wrong:** Docker sees literal "$HOME" string, mount fails
**Why it happens:** Config value is quoted when passed to docker
**How to avoid:** Expand $HOME using parameter substitution before building -v argument
**Warning signs:** Docker error about path starting with $

### Pitfall 4: Empty Variables Causing Argument Issues
**What goes wrong:** Empty -e "" or -v "" causes docker run to fail
**Why it happens:** Not checking if config variables are set before building args
**How to avoid:** Check for non-empty values before adding to docker_args array
**Warning signs:** Docker errors about empty arguments

### Pitfall 5: Unhelpful Syntax Error Messages
**What goes wrong:** User gets "parse error" with no indication of what's wrong
**Why it happens:** Error handling doesn't include line number or content
**How to avoid:** Track line numbers during parsing; show the offending line; suggest correct format
**Warning signs:** User confusion about which line has the error

### Pitfall 6: Relative Paths in MOUNTS
**What goes wrong:** Relative path works from one directory but not another
**Why it happens:** Path is relative to where user runs aishell, not project root
**How to avoid:** Either require absolute paths or resolve relative to project directory
**Warning signs:** "works on my machine" issues

## Code Examples

Verified patterns from official sources and existing codebase:

### Complete Config Parser with Error Handling
```bash
# Source: Adapted from existing read_state_file() pattern + Bash Hackers Wiki
# File: Functions to add to aishell

# Allowed variable names for run.conf (whitelist)
readonly RUNCONF_ALLOWED_VARS="MOUNTS|ENV|PORTS|DOCKER_ARGS"

# Parse .aishell/run.conf safely
# Sets CONF_* variables for each allowed config option
parse_run_conf() {
    local config_file="$1"
    local line_num=0
    local has_errors=false

    # Initialize config variables
    CONF_MOUNTS=""
    CONF_ENV=""
    CONF_PORTS=""
    CONF_DOCKER_ARGS=""

    # No config file is valid (use defaults)
    [[ ! -f "$config_file" ]] && return 0

    while IFS= read -r line || [[ -n "$line" ]]; do
        ((line_num++))

        # Skip empty lines
        [[ -z "${line// /}" ]] && continue

        # Skip comments
        [[ "$line" =~ ^[[:space:]]*# ]] && continue

        # Trim whitespace
        line="${line#"${line%%[![:space:]]*}"}"
        line="${line%"${line##*[![:space:]]}"}"

        # Validate: ALLOWED_VAR=value or ALLOWED_VAR="value"
        if [[ "$line" =~ ^($RUNCONF_ALLOWED_VARS)=(.*)$ ]]; then
            local var_name="${BASH_REMATCH[1]}"
            local var_value="${BASH_REMATCH[2]}"

            # Remove surrounding quotes (double or single)
            if [[ "$var_value" =~ ^\"(.*)\"$ ]]; then
                var_value="${BASH_REMATCH[1]}"
            elif [[ "$var_value" =~ ^\'(.*)\'$ ]]; then
                var_value="${BASH_REMATCH[1]}"
            fi

            # Set CONF_ prefixed variable
            declare -g "CONF_${var_name}=$var_value"
        else
            # Provide helpful error
            echo -e "${RED}Config error${NC} in $config_file line $line_num:" >&2
            echo "  $line" >&2
            echo "" >&2
            echo "Expected format: VARIABLE=value or VARIABLE=\"value with spaces\"" >&2
            echo "Allowed variables: MOUNTS, ENV, PORTS, DOCKER_ARGS" >&2
            has_errors=true
        fi
    done < "$config_file"

    if [[ "$has_errors" == true ]]; then
        exit 1
    fi
}
```

### Mount Argument Builder with $HOME Expansion
```bash
# Source: Docker bind mount documentation + bash parameter expansion
build_mount_args() {
    local mounts_str="$1"

    [[ -z "$mounts_str" ]] && return 0

    # Split on whitespace, respecting that paths shouldn't have spaces
    for mount_path in $mounts_str; do
        # Expand $HOME and ${HOME}
        local expanded="${mount_path//\$HOME/$HOME}"
        expanded="${expanded//\$\{HOME\}/$HOME}"

        # Handle tilde
        [[ "$expanded" == "~"* ]] && expanded="$HOME${expanded:1}"

        # Resolve to absolute path (handles relative, .., symlinks)
        if [[ -e "$expanded" ]]; then
            expanded=$(realpath "$expanded")
        else
            # Warn but don't fail - Docker will handle gracefully
            warn "Mount source does not exist: $expanded"
        fi

        # Output mount flag (same path in container, read-write)
        echo "-v"
        echo "$expanded:$expanded"
    done
}
```

### Environment Argument Builder
```bash
# Source: Docker environment variable documentation
build_env_args() {
    local env_str="$1"

    [[ -z "$env_str" ]] && return 0

    for env_entry in $env_str; do
        if [[ "$env_entry" == *"="* ]]; then
            # Literal assignment: VAR=value
            echo "-e"
            echo "$env_entry"
        else
            # Passthrough: VAR (inherit from host)
            # Check if variable exists on host
            if [[ -v "$env_entry" ]]; then
                echo "-e"
                echo "$env_entry"
            else
                warn "Skipping unset host variable: $env_entry"
            fi
        fi
    done
}
```

### Port Mapping Builder with Validation
```bash
# Source: Docker port mapping documentation
build_port_args() {
    local ports_str="$1"

    [[ -z "$ports_str" ]] && return 0

    for port_spec in $ports_str; do
        # Validate format: host:container or host:container/protocol
        # Basic validation - Docker will give detailed errors for edge cases
        if [[ "$port_spec" =~ ^[0-9]+:[0-9]+(/[a-z]+)?$ ]]; then
            echo "-p"
            echo "$port_spec"
        else
            error "Invalid port mapping: $port_spec
Expected format: HOST_PORT:CONTAINER_PORT (e.g., 8080:80)
Optional protocol: 8080:80/udp"
        fi
    done
}
```

### Integration into Main Docker Run
```bash
# Source: Extension of existing docker_args pattern in aishell
# This goes in the main() function after existing docker_args initialization

# Load runtime config if present
local config_file="$project_dir/.aishell/run.conf"
if [[ -f "$config_file" ]]; then
    verbose "Loading runtime config: $config_file"
    parse_run_conf "$config_file"

    # Add configured mounts
    if [[ -n "$CONF_MOUNTS" ]]; then
        while IFS= read -r arg; do
            [[ -n "$arg" ]] && docker_args+=("$arg")
        done < <(build_mount_args "$CONF_MOUNTS")
    fi

    # Add configured environment variables
    if [[ -n "$CONF_ENV" ]]; then
        while IFS= read -r arg; do
            [[ -n "$arg" ]] && docker_args+=("$arg")
        done < <(build_env_args "$CONF_ENV")
    fi

    # Add configured port mappings
    if [[ -n "$CONF_PORTS" ]]; then
        while IFS= read -r arg; do
            [[ -n "$arg" ]] && docker_args+=("$arg")
        done < <(build_port_args "$CONF_PORTS")
    fi

    # Add extra docker arguments (passed through directly)
    if [[ -n "$CONF_DOCKER_ARGS" ]]; then
        # Word splitting is intentional here for multiple args
        # shellcheck disable=SC2206
        docker_args+=($CONF_DOCKER_ARGS)
    fi
fi
```

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| Source config file directly | Line-by-line parsing with whitelist | Best practice | Prevents code injection |
| Manual path expansion | Parameter substitution ${var//old/new} | Bash 3+ | More reliable than sed/awk |
| Fail silently on bad config | Error with line number and suggestion | UX improvement | Users can fix issues quickly |

**Deprecated/outdated:**
- **source with grep pre-filter:** Still allows some code execution vectors; parse instead
- **envsubst for variable expansion:** Overkill when only $HOME needs expansion

## Open Questions

Things that couldn't be fully resolved:

1. **Paths with spaces in MOUNTS**
   - What we know: Word splitting on whitespace breaks paths like "$HOME/My Documents"
   - What's unclear: Whether to support quoted paths or just document the limitation
   - Recommendation: Document that paths with spaces are not supported; suggest symlinks as workaround. More complex quoting adds parsing complexity.

2. **Validating DOCKER_ARGS safety**
   - What we know: DOCKER_ARGS is passed through directly to docker run
   - What's unclear: Whether to blacklist dangerous options like --privileged
   - Recommendation: Pass through as-is. User is already trusted (they're editing .aishell/run.conf). Docker itself will validate args.

3. **ENV value escaping**
   - What we know: ENV="VAR=value with spaces" needs careful handling
   - What's unclear: How to handle quotes and special characters within values
   - Recommendation: For v1.1, require simple values without quotes. Document that complex values should use docker --env-file approach.

## Sources

### Primary (HIGH confidence)
- [Docker Container Run Reference](https://docs.docker.com/reference/cli/docker/container/run/) - Volume, env, port syntax
- [Bash Hackers Wiki - Config Files](https://flokoe.github.io/bash-hackers-wiki/howto/conffile/) - Safe config parsing patterns
- [GNU Bash Reference - Parameter Expansion](https://www.gnu.org/software/bash/manual/html_node/Shell-Parameter-Expansion.html) - $HOME expansion syntax
- [Safe .env File Loading in Bash (2025)](https://slhck.info/bash/2025/11/28/safe-env-file-loading-bash.html) - Modern approach to config loading

### Secondary (MEDIUM confidence)
- [Apple Developer - Shell Script Security](https://developer.apple.com/library/archive/documentation/OpenSource/Conceptual/ShellScripting/ShellScriptSecurity/ShellScriptSecurity.html) - Whitelisting patterns
- [Docker Bind Mounts Documentation](https://docs.docker.com/engine/storage/bind-mounts/) - Mount syntax details
- Existing codebase `read_state_file()` pattern (lines 379-403 of aishell) - Proven validation approach

### Tertiary (LOW confidence)
- Community patterns for bash config parsing from forums
- WebSearch results on regex validation

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH - All bash builtins, well-documented patterns
- Architecture: HIGH - Extends proven patterns from existing codebase
- Pitfalls: HIGH - Based on documented security issues and common errors
- Code examples: HIGH - Tested patterns adapted from official documentation

**Research date:** 2026-01-18
**Valid until:** 2026-02-18 (30 days - stable domain, shell patterns don't change rapidly)
