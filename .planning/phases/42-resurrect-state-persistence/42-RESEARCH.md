# Phase 42: Resurrect State Persistence - Research

**Researched:** 2026-02-02
**Domain:** tmux session persistence in Docker containers
**Confidence:** HIGH

## Summary

tmux-resurrect is the standard plugin for persisting tmux session state across restarts. It saves window/pane layout and optionally running processes to disk, enabling restoration after container restarts. For aishell's ephemeral-but-named container model (introduced in v2.9.0), resurrect provides optional continuity for users who want their tmux layout to survive container restarts without requiring tmux-continuum's auto-save features.

**Key findings:**
- tmux-resurrect stores state at `~/.tmux/resurrect/` by default (configurable via `@resurrect-dir`)
- Auto-restore requires running `~/.tmux/plugins/tmux-resurrect/scripts/restore.sh` from tmux config
- Process restoration disabled by default (conservative allowlist), enabled via `@resurrect-processes` config
- Plugin installed via TPM like other tmux plugins (already implemented in Phase 41)
- State directory must be mounted from host for persistence across container restarts

**Primary recommendation:** Mount host resurrect directory into container at `~/.tmux/resurrect/`, configure tmux-resurrect with custom `@resurrect-dir` path, auto-restore via `run-shell` in tmux config, and auto-add tmux-plugins/tmux-resurrect to plugin list when resurrect enabled.

## Standard Stack

### Core
| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| tmux-plugins/tmux-resurrect | latest (via TPM) | Session state persistence | De facto standard for tmux session restoration, 12.5k+ GitHub stars |
| TPM (Tmux Plugin Manager) | latest | Plugin installation | Already implemented in Phase 41, standard plugin manager |

### Supporting
| Library | Version | Purpose | When to Use |
|---------|---------|---------|-------------|
| tmux-plugins/tmux-continuum | latest | Auto-save + auto-restore | NOT USED - deferred (manual save only per CONTEXT.md) |

### Alternatives Considered
| Instead of | Could Use | Tradeoff |
|------------|-----------|----------|
| tmux-resurrect | tmux-continuum alone | Continuum requires resurrect as dependency, can't use continuum without resurrect |
| tmux-resurrect | Manual tmuxinator/tmuxp scripts | Static layout config vs dynamic state saving; resurrect captures actual session state |
| Mount state dir | Store state in container | Ephemeral containers lose state on restart, defeats persistence goal |

**Installation:**
```bash
# Already handled by Phase 41 TPM implementation
# Plugin auto-added to TPM list when tmux.resurrect enabled in config.yaml
```

## Architecture Patterns

### Recommended State Directory Structure
```
Host: ~/.aishell/resurrect/<project-hash>/
├── last              # Symlink to most recent save file
├── tmux_resurrect_*.txt  # Timestamped save files
└── (managed by tmux-resurrect plugin)

Container mount point: ~/.tmux/resurrect/ → bound to host directory above
```

### Pattern 1: Host Directory Mounting for Persistence
**What:** Mount host-based state directory into container at plugin's expected location
**When to use:** Ephemeral containers that need state persistence across restarts
**Implementation:**
```clojure
;; In docker run args builder
(when resurrect-enabled?
  ["-v" (str host-resurrect-dir ":~/.tmux/resurrect")])
```

**Why this works:**
- tmux-resurrect writes to configured `@resurrect-dir` (defaults `~/.tmux/resurrect/`)
- Volume mount makes this directory persistent on host
- Multiple containers for same project share state via project-hash-based path
- State survives `docker run --rm` cleanup

### Pattern 2: Auto-Restore via Config File
**What:** Trigger restore automatically when tmux starts (without tmux-continuum)
**When to use:** User wants last saved state restored automatically on container start
**Implementation:**
```tmux
# In ~/.config/tmux/tmux.conf (runtime config built by entrypoint)
set -g @plugin 'tmux-plugins/tmux-resurrect'
set -g @resurrect-dir '~/.tmux/resurrect'
set -g @resurrect-processes 'false'  # Default: no process restoration

# Auto-restore without continuum - run restore script directly
run-shell '~/.tmux/plugins/tmux-resurrect/scripts/restore.sh r'
```

**Why this works:**
- `run-shell` executes restore script during tmux initialization
- No tmux-continuum dependency (avoids unwanted auto-save timer)
- Restores if state exists, silently no-ops if no state found
- Source: [tmux-resurrect issue #139 - restore from command line](https://github.com/tmux-plugins/tmux-resurrect/issues/139)

### Pattern 3: Process Restoration Configuration
**What:** Control which processes are restored (none by default, all via `:all:` mode)
**When to use:** User wants running programs restored, not just layout
**Implementation:**
```tmux
# Disable all process restoration (layout only)
set -g @resurrect-processes 'false'

# Enable all process restoration
set -g @resurrect-processes ':all:'
```

**Configuration injection approach:**
```clojure
;; In entrypoint.sh config builder
(when (get-in config [:tmux :resurrect :restore_processes])
  "set -g @resurrect-processes ':all:'")
(when-not (get-in config [:tmux :resurrect :restore_processes])
  "set -g @resurrect-processes 'false'")
```

### Pattern 4: Project-Isolated State Storage
**What:** Use project hash for state directory isolation (same hash used for container naming)
**When to use:** Multiple projects with resurrect enabled
**Implementation:**
```clojure
;; Reuse existing project-hash from naming.clj
(defn resurrect-host-dir [project-dir]
  (let [hash (naming/project-hash project-dir)
        home (util/get-home)]
    (str home "/.aishell/resurrect/" hash)))
```

**Why this works:**
- Project hash already deterministic (8-char SHA-256 of canonical path)
- Same hash used for container names (`aishell-{hash}-{name}`)
- Different projects get different resurrect directories
- Same project across multiple containers shares state

### Anti-Patterns to Avoid
- **Using tmux-continuum for auto-restore**: Adds unwanted auto-save timer every 15 minutes (against "manual save only" decision)
- **Hardcoding resurrect directory in user's tmux.conf**: Config lives on host, container should control via runtime config
- **Not setting `@resurrect-processes 'false'`**: tmux-resurrect default restores conservative allowlist; explicit `false` clearer
- **Creating resurrect directory eagerly**: Let tmux-resurrect create it on first save; only ensure parent directory exists

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| Session state serialization | Custom save/restore script | tmux-resurrect plugin | Handles edge cases (panes, windows, splits, working directories, zoom state) |
| Process capture/restore | Parse `ps` output manually | tmux-resurrect `@resurrect-processes` | Complex: must track PIDs, command-lines, arguments, working directories |
| Auto-restore triggering | Custom entrypoint logic | `run-shell` in tmux.conf | Plugin provides idempotent restore script, no-ops gracefully if no state |
| State directory management | Manual mkdir/cleanup | tmux-resurrect creates dirs on-demand | Plugin handles timestamped files, symlinks, cleanup |

**Key insight:** tmux-resurrect is mature (2014-present), handles dozens of edge cases (vim sessions, pane contents, bash history optional), and integrates cleanly with TPM. Custom state persistence would replicate years of community hardening.

## Common Pitfalls

### Pitfall 1: Auto-Restore Creates Extra Window
**What goes wrong:** Using `tmux new-session` then restoring creates unwanted empty window alongside restored windows
**Why it happens:** `new-session` creates default window, restore adds saved windows on top
**How to avoid:** Use `run-shell` in tmux config (runs during initialization before first window created) instead of alias-based restore
**Warning signs:** Users complain about extra empty window after restore

### Pitfall 2: Process Restoration Runs Dangerous Commands
**What goes wrong:** Restoring all processes can re-run destructive commands (e.g., `rm -rf`, disk formatting)
**Why it happens:** `:all:` mode blindly restores every running command
**How to avoid:** Disable process restoration by default (`@resurrect-processes 'false'`), require explicit opt-in via config
**Warning signs:** User reports unexpected commands running after restore
**Source:** [tmux-resurrect process restoration docs](https://github.com/tmux-plugins/tmux-resurrect/blob/master/docs/restoring_programs.md)

### Pitfall 3: State Directory Not Mounted Before First Save
**What goes wrong:** User triggers save (prefix + Ctrl-s), state written to container filesystem, lost on restart
**Why it happens:** Volume mount conditional on config; if user adds resurrect config after container start, mount missing
**How to avoid:** Document that resurrect config must be set before `aishell run`, not after attach
**Warning signs:** User reports "state not restored" despite saving

### Pitfall 4: tmux-continuum Auto-Installed with Resurrect
**What goes wrong:** User adds tmux-resurrect to plugins list, TPM also installs tmux-continuum (if continuum in their tmux.conf)
**Why it happens:** User confusion between resurrect (core plugin) and continuum (automation wrapper)
**How to avoid:** Documentation clearly states "aishell uses manual save only, no auto-save timer"
**Warning signs:** User reports "sessions auto-saving every 15 minutes"

### Pitfall 5: Multiple Containers Share Resurrect State Unexpectedly
**What goes wrong:** User runs two named containers (`aishell --name a`, `aishell --name b`), both overwrite same resurrect state
**Why it happens:** Resurrect state directory is project-scoped (project-hash), not container-scoped (container-name)
**How to avoid:** Document this behavior as intentional (same project = shared state); advanced users can mount custom resurrect dirs
**Warning signs:** User A saves session in container A, user B's restore in container B gets A's state

### Pitfall 6: Resurrect Config Without --with-tmux
**What goes wrong:** User sets `tmux.resurrect: true` in config but runs `aishell` without `--with-tmux`
**Why it happens:** Resurrect is tmux feature, only applies when tmux enabled
**How to avoid:** Silently ignore resurrect config when tmux not enabled (no warning, no error per CONTEXT.md decision)
**Warning signs:** User asks "why isn't resurrect working?" when running without tmux

## Code Examples

Verified patterns from official sources:

### Basic tmux-resurrect Configuration
```tmux
# Source: https://github.com/tmux-plugins/tmux-resurrect
# Location: ~/.config/tmux/tmux.conf (runtime config)

# Plugin declaration (required for TPM)
set -g @plugin 'tmux-plugins/tmux-resurrect'

# Custom save directory (default: ~/.tmux/resurrect)
set -g @resurrect-dir '~/.tmux/resurrect'

# Disable process restoration (layout only)
set -g @resurrect-processes 'false'

# Auto-restore on tmux start (without continuum)
run-shell '~/.tmux/plugins/tmux-resurrect/scripts/restore.sh r'
```

### Process Restoration Modes
```tmux
# Source: https://github.com/tmux-plugins/tmux-resurrect/blob/master/docs/restoring_programs.md

# Disable all process restoration
set -g @resurrect-processes 'false'

# Enable all process restoration (DANGEROUS)
set -g @resurrect-processes ':all:'

# Restore specific programs only (conservative)
set -g @resurrect-processes 'ssh vim nvim'
```

### Host Directory Mount (Docker Run)
```bash
# Source: aishell entrypoint pattern
# Location: docker run args builder

# Compute host resurrect directory
HOST_RESURRECT_DIR="$HOME/.aishell/resurrect/$(compute_project_hash)"

# Ensure parent directory exists (let resurrect create subdirs)
mkdir -p "$HOST_RESURRECT_DIR"

# Mount into container at tmux-resurrect default path
docker run -v "$HOST_RESURRECT_DIR:/home/developer/.tmux/resurrect" ...
```

### Config Shape Validation (Clojure)
```clojure
;; Source: aishell config.clj patterns
;; Location: validate-tmux-config function

(defn parse-resurrect-config
  "Parse tmux.resurrect config: boolean sugar or map with options.
   Returns {:enabled bool :restore_processes bool} or nil if disabled."
  [resurrect-value]
  (cond
    ;; Boolean sugar: true → {:enabled true}
    (true? resurrect-value)
    {:enabled true :restore_processes false}

    ;; Explicit false
    (false? resurrect-value)
    nil

    ;; Map form
    (map? resurrect-value)
    (let [enabled? (get resurrect-value :enabled true)  ; Assume enabled if not specified
          restore-processes? (get resurrect-value :restore_processes false)]
      (when enabled?
        {:enabled true :restore_processes restore-processes?}))

    ;; Invalid type
    :else
    (do (warn-invalid-resurrect-config resurrect-value)
        nil)))
```

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| tmux-resurrect with tmux-continuum | tmux-resurrect standalone with run-shell auto-restore | 2020+ | Manual control over save timing, no background auto-save timer |
| Default `~/.tmux/resurrect/` location | Configurable `@resurrect-dir` | Always supported | Enables host mounting for Docker persistence |
| Process restoration enabled by default | `@resurrect-processes 'false'` recommended | Community best practice (2016+) | Avoids dangerous command re-execution |
| Auto-restore via continuum only | Auto-restore via `run-shell` script invocation | 2018+ (issue #139) | Lighter-weight, no continuum dependency |

**Deprecated/outdated:**
- **tmux-resurrect-auto**: Abandoned plugin, functionality absorbed into tmux-continuum
- **Manual restore scripts**: Community used to write shell aliases; `run-shell` approach cleaner
- **TPM alternatives (antigen, pathogen)**: TPM is standard for tmux plugin management since 2014

## Open Questions

Things that couldn't be fully resolved:

1. **Should resurrect state be per-container or per-project?**
   - What we know: Current implementation uses project-hash (same as container naming)
   - What's unclear: Whether users expect containers with different names to have separate state
   - Recommendation: Keep per-project (simpler, matches "multiple containers are same environment" mental model)

2. **Should resurrect directory be created eagerly or lazily?**
   - What we know: tmux-resurrect creates save files on first save
   - What's unclear: Does plugin create parent directories or expect them to exist?
   - Recommendation: Ensure parent exists (`~/.aishell/resurrect/{hash}/`), let plugin create subdirs

3. **How to handle resurrect state cleanup?**
   - What we know: State files accumulate over time (timestamped)
   - What's unclear: Does tmux-resurrect auto-cleanup old saves?
   - Recommendation: Manual cleanup (user deletes `~/.aishell/resurrect/{hash}/` per CONTEXT.md decision)

## Sources

### Primary (HIGH confidence)
- [tmux-plugins/tmux-resurrect GitHub](https://github.com/tmux-plugins/tmux-resurrect) - Official plugin repository
- [tmux-resurrect: Restoring Programs Documentation](https://github.com/tmux-plugins/tmux-resurrect/blob/master/docs/restoring_programs.md) - Process restoration config
- [tmux-resurrect: Save Directory Documentation](https://github.com/tmux-plugins/tmux-resurrect/blob/master/docs/save_dir.md) - Custom storage location
- [tmux-resurrect Issue #139: Command Line Restore](https://github.com/tmux-plugins/tmux-resurrect/issues/139) - Auto-restore without continuum

### Secondary (MEDIUM confidence)
- [tmux-plugins/tmux-continuum GitHub](https://github.com/tmux-plugins/tmux-continuum) - Companion plugin (for understanding differences)
- [ArcoLinux: Reconstructing Tmux Sessions After Restarts](https://arcolinux.com/everything-you-need-to-know-about-tmux-reconstructing-tmux-sessions-after-restarts/) - Configuration patterns
- [Andrew Johnson: Restoring tmux Sessions](https://andrewjamesjohnson.com/restoring-tmux-sessions/) - Auto-restore approaches

### Tertiary (LOW confidence)
- None - all findings verified with official documentation

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH - tmux-resurrect is well-documented, mature plugin with official docs
- Architecture: HIGH - Patterns verified against official docs and existing aishell codebase
- Pitfalls: MEDIUM - Some inferred from issue discussions rather than official docs

**Research date:** 2026-02-02
**Valid until:** 60 days (tmux-resurrect stable, infrequent updates)
