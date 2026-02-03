# Phase 41: TPM Initialization in Entrypoint - Research

**Researched:** 2026-02-02
**Domain:** Docker entrypoint scripting, tmux conditional startup, runtime plugin path bridging, config injection
**Confidence:** HIGH

## Summary

Phase 41 bridges the gap between volume-installed tmux plugins (Phase 40) and a working tmux session at runtime. The core challenge is making plugins discoverable to tmux while respecting user configuration and supporting both tmux-enabled and shell-only modes.

The research confirms that Docker entrypoint scripts are the standard location for runtime initialization tasks including: dynamic user creation (via gosu), environment setup, and conditional service startup. The existing aishell entrypoint already implements sophisticated user creation and PATH configuration, providing a solid foundation for adding tmux initialization logic.

Three key technical patterns emerge: (1) Symlink-based path bridging using `ln -sfn` for idempotent directory symlinks, (2) Config injection via copying read-only mounted files to writable locations and appending initialization commands, (3) Conditional execution blocks guarded by environment variables or state flags. The current entrypoint hardcodes tmux startup at line 208, which must be made conditional based on the :with-tmux state flag.

**Primary recommendation:** Extend the existing entrypoint.sh script with three distinct sections: (1) Plugin path bridging (symlink /tools/tmux/plugins to ~/.tmux/plugins), (2) Config injection (copy user's .tmux.conf, append TPM run command), (3) Conditional tmux startup (replace hardcoded tmux exec with conditional block that checks :with-tmux flag).

## Standard Stack

The established tools and patterns for Docker entrypoint scripting with tmux integration:

### Core
| Tool | Version | Purpose | Why Standard |
|------|---------|---------|--------------|
| bash | 4.x+ | Entrypoint scripting | Universal shell, signal handling, exec support |
| gosu | 1.19 | User switching | Proper signal forwarding, avoids sudo TTY issues |
| tmux | 3.1+ | Terminal multiplexer | Already installed in foundation image, XDG path support |
| ln (coreutils) | 8.x+ | Symlink creation | Standard POSIX tool for path bridging |

### Supporting
| Tool | Version | Purpose | When to Use |
|------|---------|---------|-------------|
| grep | 3.x+ | Config line checking | Verify TPM run command not already in config |
| cp | 8.x+ | Config file copying | Copy read-only mounted config to writable location |
| test/[ ] | builtin | File/directory checking | Conditional execution guards |

### Alternatives Considered
| Instead of | Could Use | Tradeoff |
|------------|-----------|----------|
| Symlink | TMUX_PLUGIN_MANAGER_PATH env var only | Env var works for TPM but not all plugins; symlink is universal |
| Symlink | Bind mount in docker run | Requires docker run changes; entrypoint is cleaner separation |
| Copy + append config | Modify mounted file | Read-only mount is design decision (Phase 39); can't modify |
| bash script | Shell-agnostic (sh/dash) | bash already required by TPM, gosu pattern uses bash features |

**Note on dependencies:**
All core tools are already present in aishell foundation image (installed in templates.clj). No additional package installation needed.

## Architecture Patterns

### Recommended Entrypoint Structure
```
entrypoint.sh flow:
1. Dynamic user creation (existing - lines 112-157)
2. Git safe.directory config (existing - lines 147-153)
3. Sudo setup (existing - lines 155-157)
4. Bashrc injection (existing - lines 159-165)
5. PATH configuration (existing - lines 167-181)
6. PRE_START execution (existing - lines 183-190)
7. TERM/locale validation (existing - lines 192-201)
8. [NEW] Plugin path bridging (symlink ~/.tmux/plugins)
9. [NEW] Config injection (copy .tmux.conf, append TPM run)
10. [NEW] Conditional tmux/shell startup (replace line 208)
```

### Pattern 1: Idempotent Plugin Path Bridging

**What:** Create symlink from /tools/tmux/plugins to ~/.tmux/plugins, overriding any existing symlink or directory

**When to use:** Every container startup when :with-tmux is enabled

**Example:**
```bash
# Source: Based on ln idempotent patterns and existing entrypoint PATH setup
# Runs AFTER user creation (needs $HOME), BEFORE tmux startup

# Create plugin path symlink if tmux is enabled
# Override any existing ~/.tmux/plugins - volume is authoritative
if [ -d "/tools/tmux/plugins" ]; then
    mkdir -p "$HOME/.tmux"
    ln -sfn /tools/tmux/plugins "$HOME/.tmux/plugins"

    # Sanity check: warn if symlink is broken but continue
    if [ ! -d "$HOME/.tmux/plugins/tpm" ]; then
        echo "Warning: TPM not found at ~/.tmux/plugins/tpm" >&2
    fi
fi
```

**Why this pattern:**
- `ln -sfn` is idempotent: `-f` removes existing symlink, `-n` prevents directory traversal
- Runs every startup (not just first) to ensure consistency if volume changes
- Volume-managed plugins are authoritative inside container (design principle from CONTEXT.md)
- Sanity check provides early warning without blocking startup (warning-only philosophy)

### Pattern 2: Config Injection with TPM Initialization

**What:** Copy read-only mounted ~/.tmux.conf to writable location, append TPM run command if not already present

**When to use:** When :with-tmux is enabled AND user has ~/.tmux.conf mounted

**Example:**
```bash
# Source: Based on grep-check-before-append pattern and existing bashrc injection logic
# Runs AFTER plugin symlink, BEFORE tmux startup

RUNTIME_TMUX_CONF="$HOME/.tmux.conf.runtime"
TPM_RUN_LINE="run '~/.tmux/plugins/tpm/tpm'"

# If user's .tmux.conf is mounted, copy to writable location
if [ -f "$HOME/.tmux.conf" ]; then
    cp "$HOME/.tmux.conf" "$RUNTIME_TMUX_CONF"

    # Append TPM run command if not already present
    if ! grep -qF "$TPM_RUN_LINE" "$RUNTIME_TMUX_CONF" 2>/dev/null; then
        echo "" >> "$RUNTIME_TMUX_CONF"
        echo "# TPM initialization (auto-added by aishell)" >> "$RUNTIME_TMUX_CONF"
        echo "$TPM_RUN_LINE" >> "$RUNTIME_TMUX_CONF"
    fi
else
    # No user config - create minimal config with just TPM initialization
    cat > "$RUNTIME_TMUX_CONF" <<'EOF'
# Minimal tmux config (auto-generated by aishell)
# TPM initialization
run '~/.tmux/plugins/tpm/tpm'
EOF
fi

# Set TMUX_PLUGIN_MANAGER_PATH for consistency
export TMUX_PLUGIN_MANAGER_PATH="$HOME/.tmux/plugins"
```

**Why this pattern:**
- Respects read-only mount decision (Phase 39) - copy instead of modifying
- grep -qF check prevents duplicate TPM run lines on restart
- Fallback to minimal config ensures TPM works even without user config
- Runtime config at $HOME/.tmux.conf.runtime avoids conflicting with mount
- TMUX_PLUGIN_MANAGER_PATH env var ensures plugins load from correct path

### Pattern 3: Conditional tmux Startup

**What:** Replace hardcoded tmux exec with conditional block that checks :with-tmux state flag

**When to use:** Final exec at end of entrypoint - either tmux session or direct shell

**Example:**
```bash
# Source: Based on existing entrypoint exec pattern and state.edn schema
# Reads state.edn from known location (same as CLI commands)

STATE_FILE="$HOME/.aishell/state.edn"
WITH_TMUX=false

# Read :with-tmux flag from state.edn if file exists
if [ -f "$STATE_FILE" ]; then
    # Simple grep for :with-tmux true pattern (EDN parsing in bash)
    if grep -q ':with-tmux true' "$STATE_FILE" 2>/dev/null; then
        WITH_TMUX=true
    fi
fi

# Execute command with or without tmux based on state
if [ "$WITH_TMUX" = "true" ]; then
    # tmux mode: use runtime config, start session named 'harness'
    exec gosu "$USER_ID:$GROUP_ID" tmux -f "$RUNTIME_TMUX_CONF" new-session -A -s harness -c "$PWD" "$@"
else
    # Shell mode: direct exec without tmux
    exec gosu "$USER_ID:$GROUP_ID" "$@"
fi
```

**Why this pattern:**
- State flag source of truth is state.edn (matches CLI behavior)
- Simple grep pattern avoids EDN parser dependency in bash
- Fixed session name 'harness' (design decision from CONTEXT.md)
- `-f` flag specifies runtime config (with TPM initialization)
- Shell mode preserves existing behavior for users without tmux
- `exec` ensures proper signal handling (PID 1 considerations)

### Pattern 4: Error Handling Philosophy

**What:** Warning-only approach for plugin issues, hard fail only for explicit tmux failures

**When to use:** Throughout entrypoint tmux initialization sections

**Example:**
```bash
# Sanity checks with warnings (don't block startup)
if [ ! -d "$HOME/.tmux/plugins/tpm" ]; then
    echo "Warning: TPM not found at ~/.tmux/plugins/tpm" >&2
    echo "Plugins may not be available. Check plugin installation." >&2
fi

# tmux startup failure is hard fail (user explicitly asked for tmux)
if [ "$WITH_TMUX" = "true" ]; then
    if ! command -v tmux >/dev/null 2>&1; then
        echo "Error: tmux not found but --with-tmux was specified" >&2
        exit 1
    fi
    exec gosu "$USER_ID:$GROUP_ID" tmux -f "$RUNTIME_TMUX_CONF" new-session -A -s harness -c "$PWD" "$@"
fi
```

**Why this pattern:**
- Consistent with Phase 40 warning-only validation approach
- Plugins are nice-to-have enhancement, not critical for operation
- tmux failure when explicitly requested should error (user expectation)
- All warnings to stderr only (ephemeral container, no persistent logs)

### Anti-Patterns to Avoid

- **Hardcoded tmux startup**: Current entrypoint line 208 always starts tmux. Must be conditional on :with-tmux flag or shell mode breaks.

- **Modifying read-only mounted config**: Phase 39 decision to mount ~/.tmux.conf read-only prevents container modification. Must copy to writable location.

- **Assuming ~/.tmux/plugins exists**: Don't mkdir -p and populate in entrypoint. Plugins are installed at build time (Phase 40), entrypoint only bridges paths.

- **Using -b flag in generated run command**: `run -b` breaks TPM in some scenarios. Use `run` without `-b` for reliability.

- **Complex EDN parsing in bash**: Don't try to parse state.edn with regex beyond simple key-value grep. Keep bash logic simple.

- **Failing on missing plugins**: If TPM directory is missing, warn but continue. Partial functionality better than blocking container startup.

## Don't Hand-Roll

Problems that look simple but have existing solutions:

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| EDN parsing in bash | Custom regex parser | Simple grep for :with-tmux flag | Full EDN parsing is complex; grep covers this specific need |
| Signal handling in shell exec | Custom trap handlers | bash exec with gosu | gosu already handles signals correctly, exec replaces process |
| File existence checking | Custom test logic | bash [ -f ] and [ -d ] operators | POSIX standard, reliable, universally available |
| Directory symlink creation | rm -rf && ln -s | ln -sfn | Idempotent single command, handles edge cases |

**Key insight:** The entrypoint script is critical infrastructure that runs on every container start. Prefer battle-tested POSIX patterns over clever solutions. Bash builtins (test, exec, []) and coreutils (ln, cp, grep) have decades of edge case handling.

## Common Pitfalls

### Pitfall 1: Symlink Without -n Flag Creates Nested Directories

**What goes wrong:** Using `ln -sf source target` when target is already a directory symlink creates a symlink *inside* the target directory instead of replacing it.

**Why it happens:** Without `-n` flag, ln dereferences directory symlinks and creates links inside them.

**How to avoid:** Always use `ln -sfn` when symlinking directories:
```bash
# WRONG: Second run creates /tools/tmux/plugins/plugins
ln -sf /tools/tmux/plugins ~/.tmux/plugins
ln -sf /tools/tmux/plugins ~/.tmux/plugins  # Creates nested symlink!

# CORRECT: Idempotent - replaces symlink every time
ln -sfn /tools/tmux/plugins ~/.tmux/plugins
ln -sfn /tools/tmux/plugins ~/.tmux/plugins  # Works correctly
```

**Warning signs:**
- Plugins work first run but fail on second container start
- Path becomes `~/.tmux/plugins/plugins/tpm` instead of `~/.tmux/plugins/tpm`
- "No such file or directory" errors for plugin files

**Relevance to aishell:** HIGH - entrypoint runs every container start, must be idempotent.

### Pitfall 2: Config Injection Without Checking for Duplicates

**What goes wrong:** Appending TPM run command without checking if already present creates duplicate initialization lines, causing tmux to load plugins multiple times.

**Why it happens:** Container restarts re-run entrypoint, appending to the same config file each time.

**How to avoid:** Use grep to check before appending:
```bash
# Check if line already exists
if ! grep -qF "run '~/.tmux/plugins/tpm/tpm'" "$CONFIG_FILE"; then
    echo "run '~/.tmux/plugins/tpm/tpm'" >> "$CONFIG_FILE"
fi
```

**Warning signs:**
- Config file grows on each container restart
- Duplicate error messages from TPM
- Slow tmux startup as plugins initialize multiple times

**Relevance to aishell:** MEDIUM - Current design copies config to .runtime on each start, so duplicates won't accumulate across restarts. But still good practice.

### Pitfall 3: Reading State Before User Creation

**What goes wrong:** Trying to read ~/.aishell/state.edn before HOME is set or user is created results in wrong path or permission errors.

**Why it happens:** Entrypoint runs as root initially, $HOME points to /root, not /home/developer.

**How to avoid:** Read state AFTER user creation and HOME setup (after line 137 in current entrypoint):
```bash
# After line 137: export HOME=${LOCAL_HOME:-...}
STATE_FILE="$HOME/.aishell/state.edn"
```

**Warning signs:**
- State file not found even though it exists
- Reading /root/.aishell/state.edn instead of /home/developer/.aishell/state.edn
- Permission denied errors

**Relevance to aishell:** HIGH - Current entrypoint sets HOME at line 137. State reading must come after.

### Pitfall 4: Using Relative Paths for Symlinks

**What goes wrong:** Using relative paths in symlinks breaks when current directory changes.

**Why it happens:** Symlinks store the exact string provided. Relative paths are resolved at *use time*, not creation time.

**How to avoid:** Always use absolute paths for symlinks in entrypoint scripts:
```bash
# WRONG: Breaks if PWD changes
ln -sfn ../../tools/tmux/plugins "$HOME/.tmux/plugins"

# CORRECT: Always works regardless of PWD
ln -sfn /tools/tmux/plugins "$HOME/.tmux/plugins"
```

**Warning signs:**
- Symlink appears correct with `ls -l` but files not accessible
- Errors change based on which directory you're in
- "Too many levels of symbolic links" errors

**Relevance to aishell:** LOW - All paths in aishell are absolute. But good reminder for maintenance.

### Pitfall 5: Not Handling Missing tmux.conf Gracefully

**What goes wrong:** Assuming user's ~/.tmux.conf always exists and failing when it doesn't.

**Why it happens:** Phase 39 mounts ~/.tmux.conf conditionally - only if file exists on host AND tmux is enabled AND not explicitly in config mounts.

**How to avoid:** Check file existence and provide fallback:
```bash
if [ -f "$HOME/.tmux.conf" ]; then
    # User has config - copy and extend it
    cp "$HOME/.tmux.conf" "$RUNTIME_TMUX_CONF"
else
    # No user config - create minimal config
    echo "run '~/.tmux/plugins/tpm/tpm'" > "$RUNTIME_TMUX_CONF"
fi
```

**Warning signs:**
- "No such file or directory" errors when copying config
- tmux starts but plugins don't load
- Different behavior for users with vs without .tmux.conf

**Relevance to aishell:** CRITICAL - Not all users have ~/.tmux.conf. Must handle both cases.

### Pitfall 6: Exec Without gosu Runs as Root

**What goes wrong:** Forgetting `gosu "$USER_ID:$GROUP_ID"` in final exec means command runs as root, breaking permissions on created files.

**Why it happens:** Entrypoint runs as root (required for user creation). Must explicitly switch to user before exec.

**How to avoid:** Always wrap final exec with gosu:
```bash
# WRONG: Runs tmux as root
exec tmux new-session -A -s harness "$@"

# CORRECT: Runs tmux as developer user
exec gosu "$USER_ID:$GROUP_ID" tmux new-session -A -s harness "$@"
```

**Warning signs:**
- Files created in container owned by root
- Permission denied errors when user tries to modify files
- Git operations fail with "dubious ownership" errors

**Relevance to aishell:** CRITICAL - Current entrypoint line 208 correctly uses gosu. Must preserve in conditional version.

## Code Examples

Verified patterns from official sources and existing aishell codebase:

### Plugin Path Bridging
```bash
# Source: Idempotent bash patterns + existing entrypoint PATH setup
# Location: After user creation (line 141+), before tmux startup

# Bridge plugin path from volume to user's home
# Only runs when tmux is enabled and plugins exist in volume
if [ -d "/tools/tmux/plugins" ]; then
    # Ensure ~/.tmux directory exists
    mkdir -p "$HOME/.tmux"

    # Create symlink (idempotent - replaces existing)
    # -s: symbolic link
    # -f: force (remove existing file/symlink)
    # -n: no-dereference (don't traverse directory symlinks)
    ln -sfn /tools/tmux/plugins "$HOME/.tmux/plugins"

    # Sanity check: warn if TPM not found but continue
    if [ ! -d "$HOME/.tmux/plugins/tpm" ]; then
        echo "Warning: TPM not found at ~/.tmux/plugins/tpm" >&2
        echo "Plugins may not be available. Check 'aishell build --with-tmux' completed successfully." >&2
    fi
fi
```

### Config Injection with TPM Initialization
```bash
# Source: grep-before-append pattern + existing bashrc injection
# Location: After plugin bridging, before tmux startup

RUNTIME_TMUX_CONF="$HOME/.tmux.conf.runtime"
TPM_RUN_LINE="run '~/.tmux/plugins/tpm/tpm'"

# Copy user's config if mounted, otherwise create minimal config
if [ -f "$HOME/.tmux.conf" ]; then
    # User has custom config - copy to writable location
    cp "$HOME/.tmux.conf" "$RUNTIME_TMUX_CONF"

    # Append TPM run command if not already present
    # -q: quiet mode (no output)
    # -F: fixed string (not regex)
    if ! grep -qF "$TPM_RUN_LINE" "$RUNTIME_TMUX_CONF" 2>/dev/null; then
        {
            echo ""
            echo "# TPM initialization (auto-added by aishell)"
            echo "$TPM_RUN_LINE"
        } >> "$RUNTIME_TMUX_CONF"
    fi
else
    # No user config - create minimal config with just TPM
    cat > "$RUNTIME_TMUX_CONF" <<'EOF'
# Minimal tmux configuration (auto-generated by aishell)
# User's ~/.tmux.conf not found - using defaults

# TPM initialization
run '~/.tmux/plugins/tpm/tpm'
EOF
fi

# Set plugin path for consistency (tmux and plugins read this)
export TMUX_PLUGIN_MANAGER_PATH="$HOME/.tmux/plugins"
```

### Conditional tmux Startup
```bash
# Source: Existing entrypoint exec pattern + state.edn reading
# Location: End of entrypoint (replaces line 208)

STATE_FILE="$HOME/.aishell/state.edn"
WITH_TMUX=false

# Read :with-tmux flag from state.edn if exists
# Simple grep pattern for EDN key-value (don't need full parser)
if [ -f "$STATE_FILE" ]; then
    if grep -q ':with-tmux true' "$STATE_FILE" 2>/dev/null; then
        WITH_TMUX=true
    fi
fi

# Conditional startup based on :with-tmux flag
if [ "$WITH_TMUX" = "true" ]; then
    # tmux mode: verify tmux is available
    if ! command -v tmux >/dev/null 2>&1; then
        echo "Error: tmux not found but --with-tmux was specified" >&2
        echo "This indicates a problem with the foundation image." >&2
        exit 1
    fi

    # Start tmux with runtime config and fixed session name
    # -f: config file
    # -A: attach if exists, create if not (idempotent)
    # -s: session name (fixed: 'harness')
    # -c: start directory (set by docker run -w)
    exec gosu "$USER_ID:$GROUP_ID" tmux -f "$RUNTIME_TMUX_CONF" new-session -A -s harness -c "$PWD" "$@"
else
    # Shell mode: direct exec without tmux
    # Preserves original behavior for users without --with-tmux
    exec gosu "$USER_ID:$GROUP_ID" "$@"
fi
```

### Full Entrypoint Integration
```bash
# Source: Existing templates.clj entrypoint-script + new sections
# Shows where new sections fit in existing flow

#!/bin/bash
set -e

# ... [EXISTING: Lines 112-201: user creation, git config, sudo, bashrc, PATH, PRE_START, TERM/locale] ...

# [NEW SECTION 1: Plugin path bridging]
if [ -d "/tools/tmux/plugins" ]; then
    mkdir -p "$HOME/.tmux"
    ln -sfn /tools/tmux/plugins "$HOME/.tmux/plugins"
    if [ ! -d "$HOME/.tmux/plugins/tpm" ]; then
        echo "Warning: TPM not found at ~/.tmux/plugins/tpm" >&2
    fi
fi

# [NEW SECTION 2: Config injection]
RUNTIME_TMUX_CONF="$HOME/.tmux.conf.runtime"
TPM_RUN_LINE="run '~/.tmux/plugins/tpm/tpm'"

if [ -f "$HOME/.tmux.conf" ]; then
    cp "$HOME/.tmux.conf" "$RUNTIME_TMUX_CONF"
    if ! grep -qF "$TPM_RUN_LINE" "$RUNTIME_TMUX_CONF" 2>/dev/null; then
        {
            echo ""
            echo "# TPM initialization (auto-added by aishell)"
            echo "$TPM_RUN_LINE"
        } >> "$RUNTIME_TMUX_CONF"
    fi
else
    cat > "$RUNTIME_TMUX_CONF" <<'EOF'
# Minimal tmux configuration (auto-generated by aishell)
run '~/.tmux/plugins/tpm/tpm'
EOF
fi

export TMUX_PLUGIN_MANAGER_PATH="$HOME/.tmux/plugins"

# [NEW SECTION 3: Conditional startup - REPLACES line 208]
STATE_FILE="$HOME/.aishell/state.edn"
WITH_TMUX=false

if [ -f "$STATE_FILE" ]; then
    if grep -q ':with-tmux true' "$STATE_FILE" 2>/dev/null; then
        WITH_TMUX=true
    fi
fi

if [ "$WITH_TMUX" = "true" ]; then
    if ! command -v tmux >/dev/null 2>&1; then
        echo "Error: tmux not found but --with-tmux was specified" >&2
        exit 1
    fi
    exec gosu "$USER_ID:$GROUP_ID" tmux -f "$RUNTIME_TMUX_CONF" new-session -A -s harness -c "$PWD" "$@"
else
    exec gosu "$USER_ID:$GROUP_ID" "$@"
fi
```

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| Hardcoded tmux in entrypoint | Conditional tmux via state flag | Phase 41 | Enables shell-only mode, matches --with-tmux opt-in design |
| Direct ~/.tmux.conf usage | Runtime copy with TPM injection | Phase 41 | Respects read-only mount, ensures TPM initialization |
| Plugins in user's dotfiles | Volume-based plugin installation | Phase 40 | Enables plugin sharing, build-time installation |
| Manual plugin path setup | Automatic symlink in entrypoint | Phase 41 | Zero user configuration, volume-managed plugins work automatically |

**Deprecated/outdated:**
- **Always-on tmux**: Pre-v2.9.0 aishell hardcoded tmux startup. Phase 39 made tmux opt-in with --with-tmux flag.
- **Writable config mounts**: Phase 39 decision to mount ~/.tmux.conf read-only prevents container modification. Must copy for modification.
- **Session name "main"**: Current entrypoint uses session name "main". Phase 41 changes to "harness" for consistency with project naming.

## Open Questions

Things that couldn't be fully resolved:

1. **Should state.edn be bind mounted for runtime access?**
   - What we know: State file is at ~/.aishell/state.edn on host. Entrypoint needs to read :with-tmux flag. Currently not mounted.
   - What's unclear: Should state.edn be automatically mounted like .tmux.conf? Or pass flag via environment variable?
   - Recommendation: Mount state.edn read-only in docker run (similar to .tmux.conf). More reliable than env var which can get out of sync. Alternative: Add -e WITH_TMUX=true to docker run args based on state flag. Env var is simpler and less fragile. **Recommend env var approach** for Phase 41.

2. **What if user has both .tmux.conf and .config/tmux/tmux.conf?**
   - What we know: tmux 3.1+ supports XDG paths (~/.config/tmux/tmux.conf) as alternative to ~/.tmux.conf. Both may exist.
   - What's unclear: Which takes precedence? Should entrypoint check both locations?
   - Recommendation: Phase 39 only mounts ~/.tmux.conf (not XDG path). Keep Phase 41 simple - only check ~/.tmux.conf. XDG support can be added later if users request it. **Defer XDG path handling** to future enhancement.

3. **Should entrypoint validate plugin installation before starting tmux?**
   - What we know: Sanity check warns if TPM directory missing. Could do deeper validation (check each plugin directory exists).
   - What's unclear: Does deeper validation add value or just complexity? Plugins load lazily via TPM anyway.
   - Recommendation: Stick with simple TPM directory check. Warning-only approach (from CONTEXT.md) means deeper validation has limited benefit. User will see plugin errors when tmux starts. **Keep validation minimal** for Phase 41.

4. **Should runtime config location be ~/.tmux.conf.runtime or /tmp/tmux.conf?**
   - What we know: Need writable location for config copy. Both ~/.tmux.conf.runtime (in HOME) and /tmp/tmux.conf work.
   - What's unclear: Which is cleaner? /tmp is ephemeral and auto-cleaned. ~/.tmux.conf.runtime is more discoverable for debugging.
   - Recommendation: Use ~/.tmux.conf.runtime - keeps all tmux config in one place, easier to debug, doesn't pollute /tmp. Container is ephemeral anyway so cleanup doesn't matter. **Use $HOME/.tmux.conf.runtime**.

## Sources

### Primary (HIGH confidence)
- [GitHub - tmux-plugins/tpm: Tmux Plugin Manager](https://github.com/tmux-plugins/tpm) - TPM initialization and run command
- [Docker Best Practices: Choosing Between RUN, CMD, and ENTRYPOINT](https://www.docker.com/blog/docker-best-practices-choosing-between-run-cmd-and-entrypoint/) - Exec form, signal handling
- [How to write idempotent Bash scripts](https://arslan.io/2019/07/03/how-to-write-idempotent-bash-scripts/) - ln -sfn, mkdir -p, grep-before-append patterns
- [GitHub - tianon/gosu: Simple Go-based setuid+setgid+setgroups+exec](https://github.com/tianon/gosu) - gosu usage and signal handling
- [Ln Command in Linux (Create Symbolic Links) | Linuxize](https://linuxize.com/post/how-to-create-symbolic-links-in-linux-using-the-ln-command/) - ln -sfn for directory symlinks
- Existing aishell codebase: src/aishell/docker/templates.clj (entrypoint-script, lines 104-209) - Current entrypoint implementation

### Secondary (MEDIUM confidence)
- [Docker ENTRYPOINT Explained: Usage, Syntax & Best Practices | DataCamp](https://www.datacamp.com/tutorial/docker-entrypoint) - Entrypoint vs CMD, best practices
- [tmux in demonized docker container - GitHub Gist](https://gist.github.com/ptrj/b54b8cdd34f632028e669a1e71bc03b8) - tmux in Docker patterns
- [Taisun-Docker/tmux entrypoint.sh](https://github.com/Taisun-Docker/tmux/blob/master/entrypoint.sh) - Example tmux session management in entrypoint
- [File Editing: Appending a Non-Existent Line to a File | Baeldung](https://www.baeldung.com/linux/appending-non-existent-line-to-file) - grep-before-append pattern
- [User Switching Tool in Containers: gosu | DOCSAID](https://docsaid.org/en/blog/gosu-usage/) - gosu best practices and dynamic user creation

### Tertiary (LOW confidence)
- Various blog posts and tutorials on Docker entrypoint scripts
- Community discussions on tmux startup patterns

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH - bash, gosu, tmux already in foundation image, coreutils are POSIX standard
- Plugin bridging: HIGH - ln -sfn pattern is well-documented and idempotent
- Config injection: HIGH - grep-before-append is standard pattern, matches existing bashrc injection
- Conditional startup: HIGH - Based on existing entrypoint exec pattern, simple state flag check
- Error handling: HIGH - Consistent with Phase 40 warning-only validation and design decisions

**Research date:** 2026-02-02
**Valid until:** 30 days (2026-03-04) - bash patterns are stable, tmux configuration is stable
