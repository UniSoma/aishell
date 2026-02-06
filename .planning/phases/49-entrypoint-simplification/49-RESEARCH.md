# Phase 49: Entrypoint Simplification - Research

**Researched:** 2026-02-06
**Domain:** Docker entrypoint script cleanup, bash conditional removal, gosu exec patterns
**Confidence:** HIGH

## Summary

Phase 49 removes all WITH_TMUX conditional logic from the container entrypoint script embedded in templates.clj. Phase 48 already removed WITH_TMUX from being passed by docker run, making all the tmux conditionals in the entrypoint dead code. This phase deletes approximately 100 lines of dead tmux plugin setup, config injection, and conditional startup logic, leaving only the simple `exec gosu` direct execution path.

The entrypoint script is currently 197 lines (lines 100-297 in templates.clj). After Phase 49, it will be approximately 95-100 lines - a 50% reduction. The remaining script handles user creation, PATH setup, git config, harness aliases, and direct shell execution via gosu.

**Primary recommendation:** Delete tmux-related blocks (lines 208-283), delete tmux startup path (lines 289-294), keep only the direct execution path (lines 296-297). This is pure deletion work with no new logic - the simplified entrypoint already exists at line 296.

## Standard Stack

Phase 49 uses existing project tools - no new libraries required.

### Core
| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| Babashka/SCI | 1.12.214 | Clojure interpreter | Project runtime (NOT JVM Clojure) |
| bash | Built-in | Entrypoint script language | Debian standard shell |
| gosu | 1.19 | User switching | Recommended by Docker for proper PID 1 signal handling |

### Supporting
None required - pure deletion work.

## Architecture Patterns

### Pattern 1: Gosu Exec Pattern (Already Implemented)

**What:** The entrypoint ends with `exec gosu "$USER_ID:$GROUP_ID" "$@"` which replaces the bash process with the user command, making the user's process a direct child of PID 1 (the init system).

**Current implementation:** Line 296 in templates.clj (inside the else block)
```bash
exec gosu "$USER_ID:$GROUP_ID" "$@"
```

**Why gosu:** gosu is a lightweight alternative to sudo designed for Docker containers. It:
1. Properly handles Unix signals (SIGTERM, SIGINT) by not creating a middle process
2. Doesn't need TTY allocation like sudo
3. Makes the target process a direct child of PID 1, ensuring clean shutdown
4. Official GitHub: https://github.com/tianon/gosu

**Exec semantics:** The `exec` bash builtin replaces the current process (the bash entrypoint) with the target command. This means:
- No bash process remains after exec
- Signals go directly to the user's command
- Exit code from user's command becomes container exit code
- Process tree shows: `PID 1 (docker-init) -> PID N (user command)`, no bash intermediary

**After Phase 49:** This pattern becomes the ONLY code path. No conditional logic remains.

### Pattern 2: Embedded Template String Editing

**What:** The entrypoint script lives as a multiline Clojure string in templates.clj (def entrypoint-script, lines 100-298).

**Editing approach:**
1. Read the entire templates.clj file
2. Identify line ranges to delete within the entrypoint-script string
3. Use Write tool to replace the entire file with deletions applied
4. Verify the string remains syntactically valid (proper closing quote, no escaping issues)

**Critical:** The entrypoint-script is a Clojure def with a bash script as its value. It uses double-quote escaping (`\"`) for bash strings. When deleting lines, ensure:
- No unbalanced quotes remain
- The closing `")` on line 298 stays intact
- Blank lines between sections are preserved for readability
- Bash variable expansions (`$VAR`, `${VAR}`) remain properly escaped

### Pattern 3: Dead Code Identification via Grep

**What:** After Phase 48, WITH_TMUX is never set by docker run. All conditionals checking WITH_TMUX are guaranteed dead code.

**Dead code blocks in entrypoint-script:**

1. **Lines 208-217:** Comment and plugin path bridging for tmux
   - Checks `[ "$WITH_TMUX" = "true" ]` - never true
   - Creates symlink from /tools/tmux/plugins to ~/.tmux/plugins

2. **Lines 219-264:** Config injection for tmux
   - Checks `[ "$WITH_TMUX" = "true" ]` - never true
   - Builds runtime tmux.conf with plugin declarations
   - Copies host config from /tmp/host-tmux.conf

3. **Lines 266-283:** Resurrect configuration injection
   - Checks `[ "$WITH_TMUX" = "true" ] && [ "$RESURRECT_ENABLED" = "true" ]` - never true
   - Injects tmux-resurrect settings and auto-restore

4. **Lines 285-294:** Conditional startup comment and tmux path
   - Lines 285-288: Comment explaining the conditional (DELETE)
   - Lines 289-294: The if branch for WITH_TMUX=true (DELETE)
   - Lines 295-297: The else branch - KEEP but unwrap

**What remains:** Line 296 becomes the only execution path, no longer wrapped in else.

## Don't Hand-Roll

This phase is pure deletion - no custom solutions needed.

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| Verify entrypoint script is valid bash | Custom bash parser | Write to temp file, run `bash -n tempfile` | Bash's built-in syntax checker catches unbalanced quotes, invalid syntax |
| Test gosu execution | Custom container tests | Manual smoke test: build image, run container, check process tree with `ps aux` | Phase 49 doesn't change gosu logic (already works), just removes dead branches |

**Key insight:** The simplified entrypoint already exists (line 296). Phase 49 is subtractive - removing the conditional wrapper and the never-executed if-branch.

## Common Pitfalls

### Pitfall 1: Deleting User Creation or PATH Setup Logic

**What goes wrong:** Accidentally deleting lines before the tmux blocks (lines 100-207) that handle critical setup: user creation, git config, harness aliases, PATH configuration.

**Why it happens:** The entrypoint-script is long (197 lines). When finding tmux blocks to delete, it's easy to miscalculate line numbers or delete too much.

**How to avoid:**
1. Identify exact line ranges of tmux blocks BEFORE editing
2. Verify lines to keep: 100-207 (user setup, git config, aliases), 296 (exec gosu)
3. Delete only: 208-283 (tmux blocks), 285-294 (conditional wrapper)
4. Cross-check: preserved script should have user creation, PATH setup, git config, alias generation, and exec gosu

**Warning signs:**
- Container fails to start: "user does not exist" or "permission denied"
- Git operations fail: "dubious ownership" or "unsafe repository"
- Harness commands not found in PATH
- Entrypoint script shorter than 90 lines (likely deleted too much)

### Pitfall 2: Unbalanced Quotes After Deletion

**What goes wrong:** Deleting lines introduces unbalanced quotes in the Clojure string, causing SCI load failure.

**Why it happens:** The entrypoint-script string uses `\"` to escape bash double-quotes. Deleting lines with quotes can create imbalance if not careful.

**How to avoid:**
1. After editing, verify the string has matching opening quote (line 101: `"#!/bin/bash`) and closing quote (line N after deletions: `")`)
2. Count escaped quotes in kept lines - should be even number
3. Run `bb -e "(require 'aishell.docker.templates)"` to verify namespace loads
4. If load fails with "EOF while reading", check quote balance

**Warning signs:**
- Namespace load error: "EOF while reading string"
- Clojure syntax error mentioning unclosed string literal
- Build fails when trying to use templates/entrypoint-script

### Pitfall 3: Breaking Bash Syntax by Partial Deletion

**What goes wrong:** Deleting part of an if-block but leaving the if statement or leaving an orphaned else creates invalid bash.

**Why it happens:** The conditional startup section has nested structure: if block, comment, exec statement. Partial deletion breaks syntax.

**How to avoid:**
Delete the entire conditional structure atomically:
```bash
# DELETE lines 285-294 (comment + if block):
# Conditional startup: tmux session or direct shell
# When WITH_TMUX=true: starts tmux with runtime config and plugin support
# When WITH_TMUX is unset/false: direct shell execution without tmux
# Session named \"harness\" for consistency with project naming
if [ "$WITH_TMUX" = "true" ]; then
    if ! command -v tmux >/dev/null 2>&1; then
        echo "Error: tmux not found but --with-tmux was specified" >&2
        exit 1
    fi
    exec gosu "$USER_ID:$GROUP_ID" tmux -f "$RUNTIME_TMUX_CONF" new-session -A -s harness -c "$PWD" "$@"
else
    # KEEP line 296, but REMOVE the "else" keyword:
    exec gosu "$USER_ID:$GROUP_ID" "$@"
fi  # DELETE this closing fi
```

After deletion, line 296 should be a standalone statement, no conditional wrapper.

**Warning signs:**
- Container fails to start: "syntax error near unexpected token"
- Bash error: "else without if" or "fi without if"
- Entrypoint script validation fails: `bash -n entrypoint.sh` reports syntax error

### Pitfall 4: Missing TERM Validation Block

**What goes wrong:** Accidentally deleting lines 207-211 (TERM validation) which prevent terminal errors.

**Why it happens:** Lines 207-211 reference tmux in the comment ("prevents tmux failure") but serve a general purpose (TERM validation).

**Lines to KEEP (207-216):**
```bash
# Validate TERM has terminfo entry; fallback to xterm-256color if missing
# Prevents tmux failure with custom terminals (e.g., xterm-ghostty from Ghostty)
if command -v infocmp >/dev/null 2>&1 && ! infocmp "$TERM" >/dev/null 2>&1; then
    export TERM=xterm-256color
fi

# Set UTF-8 locale before tmux starts so tmux enables Unicode mode
# Without this, tmux defaults to ASCII and Unicode characters render as dashes
export LANG=C.UTF-8
export LC_ALL=C.UTF-8
```

**Why keep:** Even without tmux, TERM validation prevents terminal compatibility issues. The UTF-8 locale is needed for shell Unicode support. Update comments to remove tmux references, but KEEP the logic.

**How to avoid:**
1. Read comments carefully - "prevents tmux failure" doesn't mean this is tmux-specific
2. TERM and locale setup are general container environment concerns
3. Only delete blocks that are EXCLUSIVELY tmux-related (plugin setup, config injection, tmux exec)

**Warning signs:**
- Terminal rendering issues in container shell
- Unicode characters display as ???? or dashes
- Error: "TERM environment variable not set"

## Code Examples

Verified deletion pattern from codebase analysis:

### Current Entrypoint Structure (lines 100-297)

```bash
#!/bin/bash
# entrypoint.sh - Dynamic user creation with gosu
# ... (lines 100-207: user creation, git config, aliases, PATH)

# Validate TERM has terminfo entry; fallback to xterm-256color if missing
# Prevents tmux failure with custom terminals (e.g., xterm-ghostty from Ghostty)
if command -v infocmp >/dev/null 2>&1 && ! infocmp "$TERM" >/dev/null 2>&1; then
    export TERM=xterm-256color
fi

# Set UTF-8 locale before tmux starts so tmux enables Unicode mode
# Without this, tmux defaults to ASCII and Unicode characters render as dashes
export LANG=C.UTF-8
export LC_ALL=C.UTF-8

# Plugin path bridging: symlink volume plugins to tmux's expected path
# DELETE lines 219-225
if [ "$WITH_TMUX" = "true" ] && [ -d "/tools/tmux/plugins" ]; then
    mkdir -p "$HOME/.tmux"
    ln -sfn /tools/tmux/plugins "$HOME/.tmux/plugins"
    if [ ! -d "$HOME/.tmux/plugins/tpm" ]; then
        echo "Warning: TPM not found at ~/.tmux/plugins/tpm" >&2
    fi
fi

# Config injection: build runtime tmux config with plugin declarations.
# DELETE lines 227-264
if [ "$WITH_TMUX" = "true" ]; then
    RUNTIME_TMUX_CONF="$HOME/.config/tmux/tmux.conf"
    TPM_RUN_LINE="run '~/.tmux/plugins/tpm/tpm'"
    # ... (tmux config building logic)
fi

# Resurrect configuration: inject tmux-resurrect settings into runtime config
# DELETE lines 266-283
if [ "$WITH_TMUX" = "true" ] && [ "$RESURRECT_ENABLED" = "true" ]; then
    echo "" >> "$RUNTIME_TMUX_CONF"
    echo "# tmux-resurrect configuration (auto-added by aishell)" >> "$RUNTIME_TMUX_CONF"
    # ... (resurrect settings injection)
fi

# Conditional startup: tmux session or direct shell
# DELETE lines 285-294 (comment + if block)
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

### After Phase 49 (simplified)

```bash
#!/bin/bash
# entrypoint.sh - Dynamic user creation with gosu
# ... (lines 100-207: user creation, git config, aliases, PATH - UNCHANGED)

# Validate TERM has terminfo entry; fallback to xterm-256color if missing
if command -v infocmp >/dev/null 2>&1 && ! infocmp "$TERM" >/dev/null 2>&1; then
    export TERM=xterm-256color
fi

# Set UTF-8 locale for proper Unicode rendering
export LANG=C.UTF-8
export LC_ALL=C.UTF-8

# Execute command as developer user
exec gosu "$USER_ID:$GROUP_ID" "$@"
```

**Lines deleted:** 208-283 (tmux setup blocks), 285-294 (conditional wrapper)
**Lines kept:** 100-207 (user/env setup), updated 207-216 (TERM/locale), 296 (exec gosu)
**Result:** ~95 line entrypoint, single code path, no conditionals

### Exact Line Ranges for Deletion

**DELETE Block 1 (lines 218-225):** Plugin path bridging
```bash
# Plugin path bridging: symlink volume plugins to tmux's expected path
if [ "$WITH_TMUX" = "true" ] && [ -d "/tools/tmux/plugins" ]; then
    mkdir -p "$HOME/.tmux"
    ln -sfn /tools/tmux/plugins "$HOME/.tmux/plugins"
    if [ ! -d "$HOME/.tmux/plugins/tpm" ]; then
        echo "Warning: TPM not found at ~/.tmux/plugins/tpm" >&2
    fi
fi
```

**DELETE Block 2 (lines 227-264):** Config injection
```bash
# Config injection: build runtime tmux config with plugin declarations.
# Host config is staged at /tmp/host-tmux.conf (not at its original path)
# so it doesn't shadow the XDG location that TPM reads for plugin discovery.
# The runtime config is written to the XDG path so both tmux and TPM read it.
if [ "$WITH_TMUX" = "true" ]; then
    RUNTIME_TMUX_CONF="$HOME/.config/tmux/tmux.conf"
    TPM_RUN_LINE="run '~/.tmux/plugins/tpm/tpm'"

    mkdir -p "$HOME/.config/tmux"

    # Copy host config from staging path (mounted at /tmp/host-tmux.conf)
    if [ -f /tmp/host-tmux.conf ]; then
        cp /tmp/host-tmux.conf "$RUNTIME_TMUX_CONF"
    else
        : > "$RUNTIME_TMUX_CONF"
    fi

    # Inject plugin declarations from volume manifest
    if [ -f "/tools/tmux/plugins.conf" ]; then
        if ! grep -qF "@plugin" "$RUNTIME_TMUX_CONF" 2>/dev/null; then
            echo "" >> "$RUNTIME_TMUX_CONF"
            echo "# Plugin declarations (from harness volume)" >> "$RUNTIME_TMUX_CONF"
            cat /tools/tmux/plugins.conf >> "$RUNTIME_TMUX_CONF"
        fi
    fi

    # Set plugin manager path in tmux config
    if ! grep -qF "TMUX_PLUGIN_MANAGER_PATH" "$RUNTIME_TMUX_CONF" 2>/dev/null; then
        echo "set-environment -g TMUX_PLUGIN_MANAGER_PATH \"$HOME/.tmux/plugins\"" >> "$RUNTIME_TMUX_CONF"
    fi

    # Append TPM initialization
    if ! grep -qF "$TPM_RUN_LINE" "$RUNTIME_TMUX_CONF" 2>/dev/null; then
        echo "" >> "$RUNTIME_TMUX_CONF"
        echo "# TPM initialization (auto-added by aishell)" >> "$RUNTIME_TMUX_CONF"
        echo "$TPM_RUN_LINE" >> "$RUNTIME_TMUX_CONF"
    fi
fi
```

**DELETE Block 3 (lines 266-283):** Resurrect configuration
```bash
# Resurrect configuration: inject tmux-resurrect settings into runtime config
if [ "$WITH_TMUX" = "true" ] && [ "$RESURRECT_ENABLED" = "true" ]; then
    # Set resurrect save directory (matches volume mount point)
    echo "" >> "$RUNTIME_TMUX_CONF"
    echo "# tmux-resurrect configuration (auto-added by aishell)" >> "$RUNTIME_TMUX_CONF"
    echo "set -g @resurrect-dir '~/.tmux/resurrect'" >> "$RUNTIME_TMUX_CONF"

    # Configure process restoration
    if [ "$RESURRECT_RESTORE_PROCESSES" = "true" ]; then
        echo "set -g @resurrect-processes ':all:'" >> "$RUNTIME_TMUX_CONF"
    else
        echo "set -g @resurrect-processes 'false'" >> "$RUNTIME_TMUX_CONF"
    fi

    # Auto-restore: run resurrect restore script on tmux start
    # This restores the last saved session if state exists, no-ops if no state
    echo "run-shell '~/.tmux/plugins/tmux-resurrect/scripts/restore.sh r'" >> "$RUNTIME_TMUX_CONF"
fi
```

**DELETE Block 4 (lines 285-294):** Conditional startup wrapper
```bash
# Conditional startup: tmux session or direct shell
# When WITH_TMUX=true: starts tmux with runtime config and plugin support
# When WITH_TMUX is unset/false: direct shell execution without tmux
# Session named \"harness\" for consistency with project naming
if [ "$WITH_TMUX" = "true" ]; then
    if ! command -v tmux >/dev/null 2>&1; then
        echo "Error: tmux not found but --with-tmux was specified" >&2
        exit 1
    fi
    exec gosu "$USER_ID:$GROUP_ID" tmux -f "$RUNTIME_TMUX_CONF\" new-session -A -s harness -c \"$PWD\" \"$@\"
else
```

**KEEP and unwrap (line 296):**
```bash
    exec gosu "$USER_ID:$GROUP_ID" "$@"
fi  # DELETE this closing fi
```

Becomes:
```bash
# Execute command as developer user
exec gosu "$USER_ID:$GROUP_ID" "$@"
```

### Comment Updates for Kept Lines

**Lines 207-211 (TERM validation):**
BEFORE:
```bash
# Validate TERM has terminfo entry; fallback to xterm-256color if missing
# Prevents tmux failure with custom terminals (e.g., xterm-ghostty from Ghostty)
```

AFTER:
```bash
# Validate TERM has terminfo entry; fallback to xterm-256color if missing
```

**Lines 213-216 (UTF-8 locale):**
BEFORE:
```bash
# Set UTF-8 locale before tmux starts so tmux enables Unicode mode
# Without this, tmux defaults to ASCII and Unicode characters render as dashes
```

AFTER:
```bash
# Set UTF-8 locale for proper Unicode rendering
```

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| Conditional tmux startup in entrypoint | Direct gosu exec (no conditional) | Phase 49 (v3.0.0) | Simpler entrypoint, faster container startup, clearer process tree |
| WITH_TMUX env var controls container mode | No env var needed (always direct exec) | Phase 48-49 (v3.0.0) | Eliminates configuration state, reduces docker run args |
| Entrypoint installs tmux plugins and config | No plugin management in entrypoint | Phase 49 (v3.0.0) | Faster startup, no filesystem writes during entrypoint |
| Complex 197-line entrypoint | Simple ~95-line entrypoint | Phase 49 (v3.0.0) | Easier to understand, maintain, and debug |

**Deprecated/outdated:**
- WITH_TMUX environment variable: Removed in Phase 48, conditionals removed in Phase 49
- Tmux plugin path bridging: Removed in Phase 49, no plugins in container
- Tmux config injection at runtime: Removed in Phase 49, no tmux config needed
- Resurrect configuration injection: Removed in Phase 49, no session persistence
- Conditional entrypoint paths: Removed in Phase 49, single exec path only

## Open Questions

None - Phase 49 is pure deletion of dead code. The simplified entrypoint already exists and works (line 296 has been the fallback path since project inception).

## Sources

### Primary (HIGH confidence)
- Project codebase analysis: src/aishell/docker/templates.clj (entrypoint-script lines 100-297)
- Phase 48 verification: .planning/phases/48-docker-run-arguments-cleanup/48-VERIFICATION.md (confirmed WITH_TMUX no longer passed)
- Phase 46 research: .planning/phases/46-foundation-image-cleanup/46-RESEARCH.md (tmux binary removal, entrypoint cleanup deferred to Phase 49)
- Requirements: .planning/REQUIREMENTS.md (TMUX-10: entrypoint simplified, direct exec gosu)
- Roadmap: .planning/ROADMAP.md (Phase 49 success criteria)

### Secondary (MEDIUM confidence)
- gosu documentation: https://github.com/tianon/gosu (exec pattern, signal handling)
- Bash exec builtin: man page (process replacement semantics)

### Tertiary (LOW confidence)
None - all findings verified with codebase inspection.

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH - No new tools, uses existing bash/gosu/Babashka
- Architecture: HIGH - Gosu exec pattern already implemented (line 296), just needs unwrapping
- Pitfalls: HIGH - Based on actual entrypoint structure analysis, clear line ranges identified

**Research date:** 2026-02-06
**Valid until:** 90 days (stable project, no external dependencies, pure deletion work)
