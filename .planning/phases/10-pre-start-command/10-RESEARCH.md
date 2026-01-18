# Phase 10: Pre-Start Command - Research

**Researched:** 2026-01-18
**Domain:** Bash background processes, Docker entrypoint patterns, container lifecycle
**Confidence:** HIGH

## Summary

This phase enables users to run a background command inside the container before the main process (shell or harness) starts. The PRE_START command is specified in `.aishell/run.conf` and executed in `entrypoint.sh` before the `exec gosu` line.

The core challenges are:
1. **Background execution without blocking** - The command must run with `&` and not block the shell/harness startup
2. **Output redirection** - stdout/stderr must go to a file to avoid polluting the terminal
3. **Process persistence** - The background process must survive the entrypoint script's `exec`
4. **Security** - The command comes from a user-controlled config file, but the user already has full container access

The implementation requires changes in two places:
1. `aishell` (host script) - Extend `parse_run_conf` whitelist to include PRE_START, pass it to container via environment variable
2. `entrypoint.sh` (container script) - Execute PRE_START in background before `exec gosu`

**Primary recommendation:** Add PRE_START to the run.conf whitelist, pass it to the container as an environment variable, and execute it in entrypoint.sh using `sh -c "$PRE_START" > /tmp/pre-start.log 2>&1 &` before the final `exec gosu` line. No `nohup` or `disown` needed because `exec` replaces the shell entirely.

## Standard Stack

The established tools for this domain:

### Core
| Component | Version | Purpose | Why Standard |
|-----------|---------|---------|--------------|
| bash `&` | Bash 5.x | Background process execution | Native, simplest approach |
| `sh -c` | POSIX | Execute command string | Safe subshell execution |
| `> /tmp/file 2>&1` | POSIX | Output redirection | Captures all output without blocking |
| `exec` | Bash builtin | Replace shell with command | Required for proper PID 1 handling |

### Supporting
| Tool | Purpose | When to Use |
|------|---------|-------------|
| `nohup` | Prevent SIGHUP on logout | NOT needed - exec replaces shell |
| `disown` | Remove from job table | NOT needed - exec replaces shell |
| `/proc/1/fd/1` | Write to container stdout | Only if PRE_START logs need `docker logs` |

### Alternatives Considered
| Instead of | Could Use | Tradeoff |
|------------|-----------|----------|
| `sh -c "$cmd" &` | Direct background `$cmd &` | `sh -c` is safer for complex commands with arguments |
| Log to /tmp | Log to /proc/1/fd/1 | /tmp is simpler; /proc requires same user, adds complexity |
| Pass via env var | Mount config file | Env var is simpler, already proven pattern in codebase |

**No additional packages needed.** All functionality is available in the base Debian image.

## Architecture Patterns

### Recommended Implementation Flow
```
run.conf (host)     entrypoint.sh (container)
      |                      |
      v                      v
 parse_run_conf()    Read PRE_START env var
      |                      |
      v                      v
 -e PRE_START=...    sh -c "$PRE_START" > /tmp/pre-start.log 2>&1 &
      |                      |
      v                      v
 docker run         exec gosu ... main command
```

### Pattern 1: Extending Config Whitelist
**What:** Add PRE_START to the allowed variables in run.conf
**When to use:** Any new config variable that needs to be parsed from run.conf
**Example:**
```bash
# In aishell - extend the whitelist
readonly RUNCONF_ALLOWED_VARS="MOUNTS|ENV|PORTS|DOCKER_ARGS|PRE_START"

# parse_run_conf will automatically handle PRE_START=... lines
# and set CONF_PRE_START variable
```

### Pattern 2: Passing Command to Container via Environment
**What:** Pass PRE_START as an environment variable to docker run
**When to use:** When container needs to execute user-specified command
**Example:**
```bash
# In aishell main() - add to docker_args
if [[ -n "$CONF_PRE_START" ]]; then
    docker_args+=(-e "PRE_START=$CONF_PRE_START")
fi
```

### Pattern 3: Background Execution in Entrypoint
**What:** Execute PRE_START in background before main command
**When to use:** Running sidecars/services that don't block main process
**Example:**
```bash
# In entrypoint.sh - before exec gosu
if [[ -n "${PRE_START:-}" ]]; then
    # Execute in subshell, redirect output, background
    sh -c "$PRE_START" > /tmp/pre-start.log 2>&1 &
fi

# Main command (PID 1 after exec)
exec gosu "$USER_ID:$GROUP_ID" "$@"
```

### Pattern 4: Why `sh -c` Instead of Direct Execution
**What:** Wrap command in `sh -c` for safe execution
**When to use:** Executing user-provided command strings
**Example:**
```bash
# Good: sh -c handles complex commands safely
PRE_START="redis-server --daemonize yes"
sh -c "$PRE_START" &

# Bad: Direct execution can fail with arguments
$PRE_START &  # May interpret "redis-server --daemonize yes" as single command
```

The `sh -c` pattern is safer because:
1. It handles commands with arguments correctly
2. It supports shell features (pipes, redirects) if user needs them
3. It runs in a subshell, isolating execution

### Anti-Patterns to Avoid
- **Using `eval "$PRE_START"`:** While `eval` works, `sh -c` is preferred for command execution as it runs in a subshell with limited scope
- **Using nohup/disown:** Unnecessary because `exec` replaces the shell entirely - there's no parent shell to send SIGHUP
- **Logging to stdout directly:** Would interleave with main process output, polluting terminal
- **Waiting for background process:** `wait` would block the main command from starting
- **Using `&>` for redirection:** Not POSIX compatible, use `> file 2>&1` instead

## Don't Hand-Roll

Problems that look simple but have existing solutions:

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| Process supervision | Custom monitoring loop | External supervisord/s6 | Out of scope for simple pre-start |
| Health checking | Polling loop in entrypoint | Container health checks | Docker native feature |
| Service restart | Watchdog in entrypoint | systemd/supervisord | Containers are ephemeral |
| Complex logging | Custom log rotation | Mount log volume | Let host handle it |

**Key insight:** PRE_START is for simple sidecars/services, not a full init system. If users need complex service management, they should use supervisord or similar.

## Common Pitfalls

### Pitfall 1: Background Process Output Blocking Terminal
**What goes wrong:** PRE_START command writes to stdout, interfering with shell prompt
**Why it happens:** Background processes inherit parent's file descriptors
**How to avoid:** Always redirect: `sh -c "$cmd" > /tmp/pre-start.log 2>&1 &`
**Warning signs:** Random output appearing in terminal, broken prompts

### Pitfall 2: PRE_START Command Fails Silently
**What goes wrong:** User doesn't know PRE_START failed
**Why it happens:** Errors go to log file, user doesn't check it
**How to avoid:**
1. Log to well-known location (/tmp/pre-start.log)
2. Document in help output where to find logs
3. Optionally add verbose output when PRE_START is configured
**Warning signs:** Services not running despite config being set

### Pitfall 3: Thinking nohup/disown Are Required
**What goes wrong:** Adding unnecessary complexity
**Why it happens:** Confusion about when SIGHUP is sent
**How to avoid:** Understand that `exec` replaces the shell - there's no parent to send SIGHUP
**Warning signs:** Cargo-culting nohup into entrypoint scripts

### Pitfall 4: PRE_START Command With Quotes
**What goes wrong:** `PRE_START="cmd 'arg with spaces'"` doesn't parse correctly
**Why it happens:** Nested quotes in shell config are complex
**How to avoid:**
1. Document that simple commands work best
2. For complex commands, suggest wrapper scripts
3. `sh -c` handles most reasonable cases
**Warning signs:** Commands with quoted arguments not working as expected

### Pitfall 5: Process Not Running After Exec
**What goes wrong:** Background process dies when exec happens
**Why it happens:** Process started in foreground, or started after exec
**How to avoid:** Ensure background `&` is used, and command is before `exec gosu`
**Warning signs:** `ps aux` shows no PRE_START process

### Pitfall 6: Environment Variable Not Reaching Container
**What goes wrong:** PRE_START is set in run.conf but container doesn't see it
**Why it happens:** Forgetting to add `-e PRE_START=...` to docker_args
**How to avoid:** Follow the pattern in main() for passing CONF_* variables
**Warning signs:** `echo $PRE_START` in container is empty

## Code Examples

Verified patterns from official sources and existing codebase:

### Extending parse_run_conf Whitelist
```bash
# Source: Existing pattern in aishell, lines 406-407
# Simply extend the regex alternation

# Before (current):
readonly RUNCONF_ALLOWED_VARS="MOUNTS|ENV|PORTS|DOCKER_ARGS"

# After:
readonly RUNCONF_ALLOWED_VARS="MOUNTS|ENV|PORTS|DOCKER_ARGS|PRE_START"

# parse_run_conf will automatically:
# - Accept PRE_START=value lines
# - Set CONF_PRE_START variable
# - Strip quotes from value
```

### Passing PRE_START to Container
```bash
# Source: Pattern from existing main() docker_args handling
# Add after the DOCKER_ARGS handling in main()

# Pass PRE_START command to container if configured
if [[ -n "${CONF_PRE_START:-}" ]]; then
    verbose "  PRE_START: $CONF_PRE_START"
    docker_args+=(-e "PRE_START=$CONF_PRE_START")
fi
```

### Executing PRE_START in entrypoint.sh
```bash
# Source: Standard bash background execution pattern
# Add before the final exec gosu line in entrypoint.sh

# Execute pre-start command if specified
if [[ -n "${PRE_START:-}" ]]; then
    # Run in background, redirect output to log file
    sh -c "$PRE_START" > /tmp/pre-start.log 2>&1 &
fi

# Execute command as the user via gosu (existing line)
exec gosu "$USER_ID:$GROUP_ID" "$@"
```

### Complete entrypoint.sh Integration
```bash
#!/bin/bash
# entrypoint.sh - Dynamic user creation with gosu
# Creates a user at container startup that matches the host user's UID/GID

set -e

# ... existing user creation code ...

# Execute pre-start command if specified (PRE-01, PRE-02, PRE-03)
if [[ -n "${PRE_START:-}" ]]; then
    # Run in background, redirect all output to log file
    # Using sh -c ensures proper argument handling for complex commands
    sh -c "$PRE_START" > /tmp/pre-start.log 2>&1 &
fi

# Execute command as the user via gosu
exec gosu "$USER_ID:$GROUP_ID" "$@"
```

### Example run.conf Usage
```bash
# .aishell/run.conf - Example with PRE_START

# Start Redis in background before shell starts
PRE_START="redis-server --daemonize yes"

# Or start a simple HTTP server
PRE_START="python3 -m http.server 8000"

# Or run a setup script
PRE_START="/workspace/scripts/setup-sidecar.sh"
```

### Verbose Output Integration
```bash
# In aishell main(), for verbose mode feedback
if [[ -n "${CONF_PRE_START:-}" ]]; then
    verbose "  PRE_START: $CONF_PRE_START"
    docker_args+=(-e "PRE_START=$CONF_PRE_START")
fi
```

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| Multiple entrypoints | Single entrypoint with pre-start hook | Docker best practice | Simpler, more predictable |
| supervisord for sidecars | Background process with & | Container simplification | Less overhead, faster startup |
| nohup in containers | Just & with exec | Understanding of exec | Cleaner entrypoints |

**Deprecated/outdated:**
- **nohup in Docker entrypoints:** Unnecessary because `exec` replaces the shell entirely
- **disown in Docker entrypoints:** Same reason - no parent shell to remove job from
- **Complex init systems for simple sidecars:** Overkill for single background process

## Open Questions

Things that couldn't be fully resolved:

1. **Should PRE_START failure block container startup?**
   - What we know: Currently, failure just logs to /tmp/pre-start.log
   - What's unclear: Whether users want hard failure on PRE_START error
   - Recommendation: Keep non-blocking for v1.1. If PRE_START fails, user checks log. Add blocking mode (PRE_START_REQUIRED) in future if needed.

2. **Should logs go to docker logs?**
   - What we know: Could redirect to /proc/1/fd/1 to appear in `docker logs`
   - What's unclear: Whether this is more helpful than /tmp/pre-start.log
   - Recommendation: Use /tmp/pre-start.log for simplicity. Users can access via `cat /tmp/pre-start.log`. Adding /proc/1/fd/1 support would require running as same user as PID 1, which may not work with gosu privilege drop.

3. **Multiple PRE_START commands?**
   - What we know: Current design is single command
   - What's unclear: Whether users need multiple commands
   - Recommendation: Keep single command for v1.1. Users can create wrapper script for multiple commands. Could add PRE_START_1, PRE_START_2 pattern in future if needed.

## Sources

### Primary (HIGH confidence)
- [Docker Entrypoint Best Practices](https://www.docker.com/blog/docker-best-practices-choosing-between-run-cmd-and-entrypoint/) - exec form vs shell form, signal handling
- [DigitalOcean nohup Tutorial](https://www.digitalocean.com/community/tutorials/nohup-command-in-linux) - Output redirection patterns
- [Linux Job Control: disown and nohup](https://www.baeldung.com/linux/job-control-disown-nohup) - Background process patterns
- Existing codebase entrypoint.sh (lines 206-276 of aishell) - Proven patterns for gosu and exec

### Secondary (MEDIUM confidence)
- [Logging to Docker stdout from Background Process](https://dev.to/ara225/logging-to-a-docker-container-stdout-from-a-background-process-3dkg) - /proc/1/fd/1 pattern
- [Docker Container Lifecycle Hooks](https://docs.docker.com/compose/how-tos/lifecycle/) - Pre/post start patterns in Compose
- [Safely Using Bash eval](https://earthly.dev/blog/safely-using-bash-eval/) - Why sh -c is safer than eval

### Tertiary (LOW confidence)
- Community patterns for sidecar containers
- WebSearch results on bash background processes

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH - All bash builtins, well-documented patterns
- Architecture: HIGH - Simple extension of existing run.conf pattern
- Pitfalls: HIGH - Based on documented container entrypoint issues and bash background process gotchas
- Code examples: HIGH - Patterns adapted from existing codebase and official documentation

**Research date:** 2026-01-18
**Valid until:** 2026-02-18 (30 days - stable domain, bash/docker patterns don't change rapidly)
