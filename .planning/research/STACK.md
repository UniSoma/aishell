# Technology Stack: tmux Plugin Support

**Project:** aishell v2.9.0 - tmux Opt-in & Plugin Support
**Researched:** 2026-02-01
**Overall confidence:** HIGH

## Executive Summary

This stack addition enables opt-in tmux plugin management in Docker containers using TPM (tmux plugin manager) with non-interactive installation. All plugins and session state install into the existing harness volume architecture (`aishell-harness-{hash}`) at `/tools/tmux/`, preserving the foundation/volume separation introduced in v2.8.0.

**No new runtime dependencies required.** tmux 3.3a-3 already installed in foundation image, git available, bash available. TPM and plugins are installed via git clone at volume population time.

## Current Stack (v2.8.0 Baseline)

Already validated and available:

| Component | Version | Location | Notes |
|-----------|---------|----------|-------|
| tmux | 3.3a-3 | Foundation image (apt) | Debian bookworm-slim package, already installed |
| git | 2.39+ | Foundation image (apt) | Required for TPM plugin cloning |
| bash | 5.2+ | Foundation image (apt) | Required for TPM scripts |
| Harness volume | - | `/tools` mount | Read-only mount, volume name `aishell-harness-{hash}` |
| Foundation image | - | `aishell:foundation` | Stable base, no harness tools |

## New Stack Additions (v2.9.0)

### Core: TPM (Tmux Plugin Manager)

| Technology | Version | Purpose | Installation Method |
|------------|---------|---------|---------------------|
| **TPM** | `master` (no releases) | tmux plugin manager | `git clone https://github.com/tmux-plugins/tpm` |

**Why master branch:** TPM has no versioned releases. The project is stable and maintained through commits on master. This is the standard installation method per official documentation.

**Installation location:** `/tools/tmux/plugins/tpm` (inside harness volume)

**Installation method:** Git clone during volume population, followed by non-interactive plugin installation via `~/.tmux/plugins/tpm/bin/install_plugins`

**Repository size:** ~200KB (lightweight, minimal overhead)

### Plugins: Recommended Defaults

| Plugin | Version | Purpose | Why Include |
|--------|---------|---------|-------------|
| **tmux-resurrect** | `master` (no releases) | Session save/restore | Core persistence feature, table stakes for tmux workflows |
| **tmux-continuum** | `master` (no releases) | Auto-save companion for resurrect | Removes manual save burden, auto-saves every 15 minutes |

**Note:** Both plugins follow the same master-branch model as TPM. No versioned releases exist.

**Installation location:** Each plugin clones to `/tools/tmux/plugins/{plugin-name}`

**Configuration:** User provides plugin list in `.aishell/config.yaml`:

```yaml
tmux:
  plugins:
    - tmux-plugins/tmux-resurrect
    - tmux-plugins/tmux-continuum
```

## Integration with Existing Architecture

### Harness Volume Structure

Current volume structure (v2.8.0):
```
/tools/
├── npm/           # npm-installed harnesses (claude, codex, gemini)
│   ├── bin/
│   └── lib/node_modules/
└── bin/           # curl-installed harnesses (opencode)
```

New structure (v2.9.0):
```
/tools/
├── npm/           # npm-installed harnesses (claude, codex, gemini)
│   ├── bin/
│   └── lib/node_modules/
├── bin/           # curl-installed harnesses (opencode)
└── tmux/          # NEW: tmux plugins and state
    ├── plugins/   # TPM and plugin repositories
    │   ├── tpm/
    │   ├── tmux-resurrect/
    │   └── tmux-continuum/
    └── resurrect/ # Session save files from tmux-resurrect
```

**Why `/tools/tmux/`:** Follows established pattern. Volume-mounted tools stay in `/tools`, foundation image stays clean, identical configs share volumes across projects.

**Volume mount strategy:**
- `/tools` mount changes from read-only to **read-write** (plugins need to save state)
- Alternative: Mount `/tools/tmux/resurrect` separately as writable, keep `/tools` read-only
- Recommendation: **Read-write `/tools` mount** (simpler, plugins may write to plugin dirs for caching)

### Config Mounting: Host tmux.conf

User's `~/.tmux.conf` must be mounted into container for TPM to discover plugin configuration.

**Mount strategy:**
```bash
docker run \
  -v ~/.tmux.conf:/home/developer/.tmux.conf:ro \
  ...
```

**When to mount:** Only when `--with-tmux` flag present in build state OR when tmux plugins configured in config.yaml

**Fallback behavior:** If `~/.tmux.conf` doesn't exist on host, skip mount (no error). TPM won't run, default tmux behavior.

**Why read-only:** Config file should not be modified by container. User edits on host.

## Non-Interactive Installation Workflow

TPM provides `bin/install_plugins` script for automated plugin installation. This is the official method for Docker/CI environments.

### Installation Steps (during volume population)

1. **Clone TPM** to `/tools/tmux/plugins/tpm`:
   ```bash
   git clone --depth 1 https://github.com/tmux-plugins/tpm /tools/tmux/plugins/tpm
   ```

2. **Create tmux.conf with plugin declarations** (temporary file for installation):
   ```bash
   cat > /tmp/tmux-install.conf <<'EOF'
   set -g @plugin 'tmux-plugins/tpm'
   set -g @plugin 'tmux-plugins/tmux-resurrect'
   set -g @plugin 'tmux-plugins/tmux-continuum'

   # TPM initialization (must be last line)
   run '/tools/tmux/plugins/tpm/tpm'
   EOF
   ```

3. **Run non-interactive installer**:
   ```bash
   TMUX_PLUGIN_MANAGER_PATH=/tools/tmux/plugins \
   /tools/tmux/plugins/tpm/bin/install_plugins
   ```

**Key environment variables:**
- `TMUX_PLUGIN_MANAGER_PATH`: Overrides default `~/.tmux/plugins/` location
- Must point to `/tools/tmux/plugins` to install into volume

**Installation dependencies:**
- tmux binary must be in `$PATH` (checked by installer script)
- git must be available (for cloning plugin repos)
- bash must be available (TPM scripts are bash)

**Installation validation:**
- Check for existence of `/tools/tmux/plugins/tmux-resurrect/resurrect.tmux`
- Check for existence of `/tools/tmux/plugins/tmux-continuum/continuum.tmux`

### Volume Population Logic

Add to existing harness volume population (in `src/aishell/docker/volume.clj`):

```clojure
;; After npm/bin population, if tmux plugins configured:
(when (seq (:plugins (:tmux config)))
  ;; Clone TPM
  (shell "git clone --depth 1 https://github.com/tmux-plugins/tpm /tools/tmux/plugins/tpm")

  ;; Generate temporary tmux.conf with plugin list
  (spit "/tmp/tmux-install.conf" (generate-plugin-config (:plugins (:tmux config))))

  ;; Run non-interactive installer
  (shell {:env {"TMUX_PLUGIN_MANAGER_PATH" "/tools/tmux/plugins"}}
         "/tools/tmux/plugins/tpm/bin/install_plugins"))
```

## Session Persistence: tmux-resurrect

### Save File Location

**Default:** `~/.tmux/resurrect/` (or `~/.local/share/tmux/resurrect` if XDG_DATA_HOME set)

**Override location:** Mount to volume for persistence across container restarts:
```
set -g @resurrect-dir '/tools/tmux/resurrect'
```

**Volume integration:** Map `/tools/tmux/resurrect` on volume to this location. Resurrect files persist across container recreation.

**File format:**
- `tmux_resurrect_YYYYMMDDTHHMMSS.txt` - timestamped save files
- `last` - symlink to most recent save

**Configuration in user's ~/.tmux.conf:**
```tmux
set -g @resurrect-dir '/tools/tmux/resurrect'
```

**Why volume-based persistence:** Session state survives container destruction. When user recreates container with same project hash, session restores automatically.

### Auto-save: tmux-continuum

**Default interval:** 15 minutes

**Configuration:** Auto-save enabled by default once plugin installed. No explicit config needed.

**Auto-restore:** Requires explicit opt-in in user's `~/.tmux.conf`:
```tmux
set -g @continuum-restore 'on'
```

**Integration:** Works seamlessly with resurrect. Continuum triggers resurrect's save mechanism periodically, then resurrect restores from saved state on tmux server start.

**Why recommended:** Eliminates manual save workflow (prefix + Ctrl+s). Users get automatic session persistence without thinking about it.

## PATH and Environment

### Plugin Discovery

TPM discovers plugins via `~/.tmux.conf` configuration. Standard format:

```tmux
set -g @plugin 'tmux-plugins/tmux-resurrect'
set -g @plugin 'tmux-plugins/tmux-continuum'

# Initialize TPM (keep at bottom of tmux.conf)
run '/tools/tmux/plugins/tpm/tpm'
```

**Critical:** Final `run` line must point to `/tools/tmux/plugins/tpm/tpm` when using volume-based installation (not default `~/.tmux/plugins/tpm/tpm`).

**User guidance:** Documentation must instruct users to either:
1. Use volume-based path in their global `~/.tmux.conf` (works everywhere, not just aishell)
2. Create aishell-specific override (complex, not recommended)

**Recommendation:** Document volume-based path as standard. Works in aishell containers, and non-aishell environments ignore missing path gracefully.

### Runtime Environment Variables

No additional environment variables needed at runtime. TPM uses standard tmux configuration mechanism.

**At volume population time only:**
- `TMUX_PLUGIN_MANAGER_PATH=/tools/tmux/plugins` (for non-interactive install)

## What NOT to Add

| Rejected Approach | Why Not |
|-------------------|---------|
| Install TPM in foundation image | Version drift, slower builds, violates foundation/volume separation |
| Per-project tmux.conf | Complexity, user's global config is sufficient, override mechanism unclear |
| Versioned plugin installations | Plugins don't publish versions, master branch is standard |
| Package manager (apt) for TPM | Not available in Debian repos, git clone is official method |
| Custom plugin installation scripts | TPM's `bin/install_plugins` is the official non-interactive method |
| Read-only volume for tmux plugins | Plugins need write access for state (resurrect saves, potential caching) |
| XDG_DATA_HOME override | Unnecessary complexity, explicit `@resurrect-dir` is clearer |

## Build Flag Integration

### --with-tmux Flag

**Current behavior (v2.7.0):** tmux always enabled, auto-start in all modes

**New behavior (v2.9.0):** tmux opt-in via `--with-tmux` build flag

**Impact on stack:**
- Flag adds `:with-tmux true` to state.edn
- Volume population skips tmux plugin installation if flag absent
- Config mounting skips `~/.tmux.conf` if flag absent
- Entrypoint skips `tmux new-session -A` wrapper if flag absent

**Default:** tmux DISABLED (breaking change from v2.7.0, but v2.9.0 is explicit about opt-in)

**Validation:** `aishell build --with-tmux` required before tmux features work

## Configuration Schema Addition

New top-level key in `.aishell/config.yaml`:

```yaml
tmux:
  plugins:
    - tmux-plugins/tmux-resurrect
    - tmux-plugins/tmux-continuum
    # Add more plugins as needed
```

**Format:** List of GitHub repository paths in `owner/repo` format

**Processing:** During volume population, iterate list and add to generated tmux.conf, then run installer

**Validation:**
- Each entry must match pattern `[a-zA-Z0-9_-]+/[a-zA-Z0-9_-]+`
- Cloning uses `https://github.com/{entry}` (no auth, public repos only)

## Version Pinning Strategy

**TPM and plugins: No versioning**

All tmux plugins (TPM, resurrect, continuum) follow master-branch development model with no versioned releases. This is by design and standard in the tmux plugin ecosystem.

**Installation approach:**
- `git clone --depth 1` to get latest master
- No git tag or SHA pinning
- Updates via `aishell update` (recreates volume, re-clones master)

**Risk mitigation:**
- Shallow clone (`--depth 1`) minimizes data transfer
- Volume content-hash means identical configs share volumes (no duplicate installs)
- Breaking changes rare (plugins are stable, minimal API surface)

**Future consideration:** If version pinning becomes necessary, support `owner/repo@tag` format in config.yaml

## Installation Size Impact

| Component | Size | Impact |
|-----------|------|--------|
| TPM | ~200KB | Negligible |
| tmux-resurrect | ~100KB | Negligible |
| tmux-continuum | ~50KB | Negligible |
| **Total overhead** | **~350KB** | Minimal (0.35MB added to harness volume) |

Compared to harness tools (Claude Code ~50MB npm package), plugin overhead is negligible.

## Compatibility Matrix

| Component | Minimum Version | Validated Version | Notes |
|-----------|----------------|-------------------|-------|
| tmux | 1.9 | 3.3a-3 (Debian bookworm) | TPM requires 1.9+, resurrect works with 1.9+ |
| git | 1.7+ | 2.39+ (Debian bookworm) | Required for git clone operations |
| bash | 3.0+ | 5.2+ (Debian bookworm) | TPM scripts require bash |

**All requirements satisfied by existing foundation image.** No new apt packages needed.

## Testing Validation Points

Before milestone completion, validate:

1. **Non-interactive install works:** `bin/install_plugins` succeeds without tmux running
2. **Plugins load:** After container start, `tmux list-keys` shows resurrect/continuum bindings
3. **Save/restore works:** Create windows, save (manual or auto), destroy container, recreate, verify restore
4. **Volume persistence:** Resurrect files persist in `/tools/tmux/resurrect` across container restarts
5. **Config mounting:** User's `~/.tmux.conf` visible inside container, changes reflected on reload
6. **No TPM when tmux disabled:** Without `--with-tmux` flag, no plugin installation attempted
7. **Plugin-less operation:** `--with-tmux` without plugins in config.yaml works (bare tmux, no TPM)

## Documentation Requirements

Users need to know:

1. **How to enable tmux:** `aishell build --with-tmux`
2. **How to configure plugins:** Add `tmux.plugins` list to `.aishell/config.yaml`
3. **How to configure resurrect save location:** Set `@resurrect-dir '/tools/tmux/resurrect'` in `~/.tmux.conf`
4. **How to enable auto-restore:** Set `@continuum-restore 'on'` in `~/.tmux.conf`
5. **TPM initialization path:** Use `run '/tools/tmux/plugins/tpm/tpm'` in `~/.tmux.conf` (not default path)
6. **Update workflow:** `aishell update` refreshes plugins to latest master
7. **Manual plugin management:** How to add/remove plugins (edit config.yaml, run update)

## Sources

**HIGH Confidence (Official Documentation):**
- [TPM Official Repository](https://github.com/tmux-plugins/tpm) - Installation paths, non-interactive methods
- [tmux-resurrect Official Repository](https://github.com/tmux-plugins/tmux-resurrect) - Save directory configuration
- [tmux-resurrect Save Directory Docs](https://github.com/tmux-plugins/tmux-resurrect/blob/master/docs/save_dir.md) - Default `~/.tmux/resurrect/` location
- [TPM Command-Line Management](https://github.com/tmux-plugins/tpm/blob/master/docs/managing_plugins_via_cmd_line.md) - `bin/install_plugins` usage
- [tmux-continuum Official Repository](https://github.com/tmux-plugins/tmux-continuum) - 15-minute auto-save interval, resurrect integration
- [Debian Packages: tmux in bookworm](https://packages.debian.org/bookworm/tmux) - Version 3.3a-3 confirmed

**MEDIUM Confidence (Community Documentation):**
- [ArcoLinux tmux Plugin Manager Guide](https://arcolinux.com/everything-you-need-to-know-about-tmux-plugins-manager/) - Installation patterns, plugin directory structure
- [Lorenzo Bettini: Installing TPM with Chezmoi](https://www.lorenzobettini.it/2025/04/installing-the-tmux-plugin-manager-tpm-with-chezmoi/) - Non-interactive installation in automated environments

**Verified:** All critical claims cross-referenced with official repositories. Version numbers confirmed via Debian package tracker. Non-interactive installation method confirmed via official TPM documentation.
