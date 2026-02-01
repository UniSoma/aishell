# Domain Pitfalls: tmux Opt-in and Plugin Support

**Domain:** Adding opt-in tmux and plugin support to existing Docker container system
**Researched:** 2026-02-01
**Context:** Milestone v2.9.0 - Making tmux opt-in with --with-tmux flag and adding TPM plugin support

## Executive Summary

Making an always-on feature opt-in introduces **behavioral breaking changes** for existing users. Adding plugin support to Docker containers introduces **installation complexity** in non-interactive environments. The combination creates four critical risk areas:

1. **Opt-in migration** - Users who built workflows assuming tmux is always present will break
2. **Plugin installation failures** - TPM in non-interactive Docker environments has known failure modes
3. **Configuration conflicts** - Mounting host `~/.tmux.conf` that references missing plugins
4. **State persistence** - tmux-resurrect state across ephemeral containers requires volume architecture

The most dangerous pitfall is **silent behavioral change** - existing `aishell attach` commands assume tmux exists, and will fail cryptically when users rebuild without --with-tmux. The second most dangerous is **plugin installation race conditions** - TPM's git clone can fail in Docker builds due to network issues, leaving containers in broken state.

## Critical Pitfalls

Mistakes that cause rewrites or major issues.

### Pitfall 1: Opt-in Breaking Change for Existing Users

**What goes wrong:**

v2.7.0 made tmux always-on (all modes start inside tmux, `aishell attach` assumes tmux exists). v2.9.0 makes tmux opt-in with `--with-tmux` flag, defaulting to OFF. Users who:

1. Built containers with v2.7.0 (tmux always-on)
2. Integrated `aishell attach` into scripts/workflows
3. Upgrade to v2.9.0 and rebuild (defaults to no tmux)
4. Run `aishell attach` → fails with "tmux: command not found"

The failure is **silent** - the build succeeds, but runtime commands break. Users don't realize they need to add `--with-tmux` until their attach workflows fail.

**Why it happens:**

This is a classic backward-compatibility pitfall with opt-in feature flags. When you change a default-on feature to opt-in:

- Build system doesn't warn about behavioral change
- Existing scripts don't know to add the new flag
- State file (state.edn) from v2.7.0 doesn't track "tmux was installed" vs "user opted in"
- Rebuild with new version silently changes behavior

From feature flag research: "The recent switch to Hardware Revisions in ZMK firmware removed nice_nano_v2 as a standalone board name, which instantly broke thousands of existing user configs and GitHub Actions workflows that hardcode that board name."

**Consequences:**

- **Scripts break**: CI/CD pipelines using `aishell attach` fail after rebuild
- **User confusion**: "attach worked yesterday, now it doesn't"
- **Support burden**: Users file issues "attach command broken"
- **Trust erosion**: Upgrades break existing functionality without warning
- **Rollback pressure**: Users revert to v2.7.0 to restore functionality

**Prevention:**

**1. Migration detection with version-aware state:**

```clojure
;; In build.clj, after reading state.edn
(defn detect-tmux-migration [old-state new-args]
  (let [old-version (get old-state :version)
        new-has-tmux (:with-tmux new-args)
        tmux-was-default (and old-version
                              (version-gte old-version "2.7.0")
                              (version-lt old-version "2.9.0"))]
    (when (and tmux-was-default (not new-has-tmux))
      {:warning :tmux-opt-in-migration
       :message "Previous build (v2.7.0+) included tmux by default.
                 v2.9.0 requires --with-tmux flag to include tmux.
                 Add --with-tmux to maintain existing behavior."})))
```

**2. Explicit migration error in attach command:**

```clojure
;; In attach.clj, before docker exec
(defn validate-tmux-available [state container]
  (when-not (:with-tmux state)
    (output/error
      "Container was built without tmux (use --with-tmux flag).
       The 'attach' command requires tmux.

       To fix:
       1. Rebuild with: aishell build --with-tmux
       2. Or use: aishell exec bash (no tmux)")))
```

**3. State file migration:**

```clojure
;; In state.clj, when reading v2.7.0 state
(defn migrate-state [old-state]
  (cond-> old-state
    ;; If state from v2.7.0-v2.8.x, assume tmux was present
    (and (version-gte (:version old-state) "2.7.0")
         (version-lt (:version old-state) "2.9.0"))
    (assoc :with-tmux true  ; Preserve old behavior
           :migrated-from-always-on true)))  ; Mark as migrated
```

**4. Documentation and changelog:**

```markdown
## BREAKING CHANGE: tmux now opt-in

v2.7.0-v2.8.x included tmux by default. v2.9.0 requires explicit opt-in.

**Migration:** Add `--with-tmux` flag to `aishell build`:

    aishell build --with-tmux

**Affects:** Users who rely on `aishell attach` or tmux in container.

**Why:** Reduces base image size for users who don't need tmux.
```

**Detection:**

- **Build warning not shown** when upgrading from v2.7.0/v2.8.0
- **attach command fails** with "tmux: command not found" after rebuild
- **CI/CD pipelines fail** after updating aishell version
- **State file missing :with-tmux key** after rebuild from v2.7.x
- **User confusion**: "It worked before the upgrade"

**Sources:**
- [Feature Flag Best Practices](https://frontegg.com/blog/feature-flag-best-practices)
- [Backwards Compatibility and Breaking Changes](https://github.com/kedro-org/kedro/wiki/Backwards-compatibility-and-breaking-changes)
- [Feature Request: Restore nice_nano_v2 as alias](https://github.com/zmkfirmware/zmk/issues/3157)

---

### Pitfall 2: TPM Plugin Installation Failures in Non-Interactive Docker Builds

**What goes wrong:**

TPM (Tmux Plugin Manager) is designed for interactive tmux sessions where users press `Prefix + I` to install plugins. In Docker container builds or startup scripts, TPM's `bin/install_plugins` must run non-interactively, which has several failure modes:

1. **Network failures during git clone** - GitHub.com unreachable, timeout, SSL verification errors
2. **Empty git clone** - Git succeeds but produces empty directory (race condition)
3. **Plugin script failures** - Install scripts expect interactive shell, fail in Dockerfile RUN
4. **Silent failures** - TPM returns success even when plugins didn't install

From WebSearch: "Users have reported that cloning git repos via HTTPS doesn't work on some systems, resulting in 'download fail' errors when trying to install plugins. Setting `export GIT_SSL_NO_VERIFY=true` has been tried but doesn't always work. One user found the fix was changing `clone 'https://git::@github.com/$plugin'` to `clone 'https://github.com/$plugin'`."

**Why it happens:**

TPM was designed for interactive use. The automatic installation snippet from official docs:

```bash
if "test ! -d ~/.tmux/plugins/tpm" \
 "run 'git clone https://github.com/tmux-plugins/tpm ~/.tmux/plugins/tpm && ~/.tmux/plugins/tpm/bin/install_plugins'"
```

This runs **during tmux startup**, which in aishell means during `docker run` entrypoint. At that moment:

- Network may not be stable (container just starting)
- Git config may not be set up (SSH keys not mounted yet)
- HTTPS certificate verification may fail (CA certs not installed)
- Git credentials not available (plugin repos may be private)

**Consequences:**

- **Container starts but plugins missing** - tmux.conf references plugins that don't exist
- **tmux errors on every command** - "unknown command" or "plugin not found"
- **Silent degradation** - Container works but features missing (e.g., resurrect not saving)
- **Network-dependent builds** - Builds succeed in CI but fail in restricted networks
- **Race conditions** - Sometimes works, sometimes doesn't (depends on GitHub responsiveness)

**Prevention:**

**1. Pre-install plugins in harness volume, not at runtime:**

```clojure
;; In volume.clj, when populating harness volume
(defn install-tmux-plugins [volume-name plugins]
  (let [plugin-dir "/tmp/tmux-plugins"
        cmds (concat
              [(str "mkdir -p " plugin-dir "/tpm")]
              [(str "git clone --depth 1 https://github.com/tmux-plugins/tpm "
                    plugin-dir "/tpm")]
              ;; For each plugin, clone into volume
              (for [plugin plugins]
                (str "git clone --depth 1 https://github.com/" plugin " "
                     plugin-dir "/" (plugin-name plugin))))]
    ;; Run in temporary container with volume mount
    (docker/run-with-volume volume-name plugin-dir cmds)))
```

**2. Network retry with timeout:**

```bash
# In plugin installation script
retry_git_clone() {
  local url=$1
  local dest=$2
  local attempts=3
  local timeout=30

  for i in $(seq 1 $attempts); do
    if timeout $timeout git clone --depth 1 "$url" "$dest" 2>/tmp/git-error; then
      return 0
    fi
    echo "Git clone attempt $i failed, retrying..." >&2
    sleep 2
  done

  echo "ERROR: Failed to clone $url after $attempts attempts:" >&2
  cat /tmp/git-error >&2
  return 1
}
```

**3. Validate plugin installation:**

```bash
# After TPM install_plugins
for plugin in $(tmux show-options -g | grep '@plugin' | cut -d"'" -f2); do
  plugin_name=$(basename "$plugin")
  if [ ! -d "$HOME/.tmux/plugins/$plugin_name" ]; then
    echo "ERROR: Plugin $plugin_name failed to install" >&2
    exit 1
  fi
done
```

**4. Fallback to basic tmux if plugins fail:**

```bash
# In entrypoint.sh, before starting tmux
if [ -f "$HOME/.tmux.conf" ] && grep -q "set -g @plugin" "$HOME/.tmux.conf"; then
  # Plugins declared, verify TPM is available
  if [ ! -x "$HOME/.tmux/plugins/tpm/tpm" ]; then
    echo "WARN: tmux.conf has plugins but TPM not installed, using fallback config"
    mv "$HOME/.tmux.conf" "$HOME/.tmux.conf.with-plugins"
    # Use minimal config without plugins
    echo "set -g mouse on" > "$HOME/.tmux.conf"
  fi
fi
```

**5. Install plugins during volume population, not container startup:**

Since aishell v2.8.0 uses volume-mounted harness tools, plugins should go in the harness volume:

```clojure
;; In volume.clj populate-volume
(when (:with-tmux config)
  (let [plugins (get-in config [:tmux :plugins] [])]
    (install-tpm-and-plugins volume-name plugins)))
```

This moves network operations to `aishell build`/`aishell update`, not container startup, giving better error visibility.

**Detection:**

- **tmux starts with errors**: "unknown command: @plugin" or similar
- **Plugin features don't work**: resurrect doesn't save, theme doesn't apply
- **Empty plugin directories**: `ls ~/.tmux/plugins/` shows directories but they're empty
- **Git errors in logs**: "fatal: unable to access 'https://github.com/...'"
- **Inconsistent behavior**: Works sometimes, fails other times (network race)
- **Docker build succeeds but runtime broken**: No error during build, errors in container

**Sources:**
- [Unable to install plugins](https://github.com/tmux-plugins/tpm/issues/195)
- [Plugin download failed](https://github.com/tmux-plugins/tpm/issues/62)
- [FIX Proposal on random plugin download fail](https://github.com/tmux-plugins/tpm/issues/229)
- [Automatic TPM Installation docs](https://github.com/tmux-plugins/tpm/blob/master/docs/automatic_tpm_installation.md)

---

### Pitfall 3: Host tmux.conf Plugin Path Conflicts

**What goes wrong:**

User's host `~/.tmux.conf` references plugins at `~/.tmux/plugins/...`. When mounted into the container, the config expects plugins to exist at that exact path. But in aishell v2.9.0:

- Plugins are installed in harness volume at `/tools/tmux/plugins/` (or similar)
- Container's `~/.tmux.conf` (mounted from host) points to `~/.tmux/plugins/`
- Plugins don't exist where config expects them
- tmux errors: "~/.tmux/plugins/tmux-resurrect/resurrect.tmux: no such file"

This also happens when host config uses XDG paths (`~/.config/tmux/plugins/`) but container uses traditional paths, or vice versa.

**Why it happens:**

From WebSearch: "If TPM finds your Tmux config in `~/.config/tmux/tmux.conf`, it will install plugins into `~/.config/tmux/plugins` without any additional configuration."

The issue: host and container may have different conventions:

- **Host**: `~/.config/tmux/tmux.conf` → plugins in `~/.config/tmux/plugins/`
- **Container**: `~/.tmux.conf` (mounted) → expects plugins in `~/.tmux/plugins/`
- **Harness volume**: Plugins actually at `/tools/tmux/plugins/`

When config is mounted from host but plugins are installed in container, the paths don't align.

**Consequences:**

- **tmux fails to start**: "error in configuration file"
- **Plugins don't load**: Config runs but `run-shell '~/.tmux/plugins/...'` fails
- **User confusion**: "My config works on host, why not in container?"
- **Silent feature loss**: Config loads but plugin features missing
- **Inconsistent state**: Some plugins work (bundled) others don't (path mismatch)

**Prevention:**

**1. Normalize plugin path with environment variable:**

```bash
# In entrypoint.sh, before starting tmux
export TMUX_PLUGIN_MANAGER_PATH="/tools/tmux/plugins"

# In mounted ~/.tmux.conf, user references:
set -g @plugin 'tmux-plugins/tmux-resurrect'
# TPM automatically uses $TMUX_PLUGIN_MANAGER_PATH if set
```

**2. Symlink host plugin path to volume path:**

```bash
# In entrypoint.sh
if [ -f "$HOME/.tmux.conf" ]; then
  # Detect if user has plugins
  if grep -q "set -g @plugin" "$HOME/.tmux.conf"; then
    # Create symlink so host paths work
    mkdir -p "$HOME/.tmux"
    ln -sf /tools/tmux/plugins "$HOME/.tmux/plugins"

    # Also handle XDG path
    mkdir -p "$HOME/.config/tmux"
    ln -sf /tools/tmux/plugins "$HOME/.config/tmux/plugins"
  fi
fi
```

**3. Rewrite plugin paths in mounted config:**

```bash
# In entrypoint.sh, create container-specific tmux.conf
if [ -f "$HOME/.tmux.conf.host" ]; then
  # Rewrite plugin paths to point to volume mount
  sed "s|~/.tmux/plugins|/tools/tmux/plugins|g" \
      "$HOME/.tmux.conf.host" > "$HOME/.tmux.conf"
fi
```

**4. Document plugin path configuration:**

```markdown
## Plugin Paths

When using host `~/.tmux.conf`, plugins must be installed in the container.
aishell installs plugins to `/tools/tmux/plugins/`.

**Option 1:** Use environment variable (recommended)
```bash
# In ~/.tmux.conf
set-environment -g TMUX_PLUGIN_MANAGER_PATH "/tools/tmux/plugins"
```

**Option 2:** Explicit paths in config
```bash
run-shell '/tools/tmux/plugins/tpm/tpm'
```

**Option 3:** Let aishell create symlinks (automatic)
```

**5. Validate plugin paths before tmux starts:**

```bash
# In entrypoint.sh
if [ -f "$HOME/.tmux.conf" ]; then
  # Extract plugin paths from config
  plugin_paths=$(grep -oP "run-shell ['\"]?\K[^'\"]*(?=/?tpm/?)" "$HOME/.tmux.conf")

  for path in $plugin_paths; do
    if [ ! -e "$path" ]; then
      echo "WARN: tmux.conf references plugin path $path which doesn't exist"
      echo "      Plugins are installed at /tools/tmux/plugins/"
    fi
  done
fi
```

**Detection:**

- **tmux error on startup**: "~/.tmux/plugins/tpm/tpm: no such file or directory"
- **Plugins declared but don't work**: Config has `@plugin` lines but features missing
- **Path mismatch in logs**: "run-shell" errors referencing non-existent paths
- **Works on host, fails in container**: Same config file, different behavior
- **Symlinks missing**: `ls -la ~/.tmux/plugins` shows no symlink to volume

**Sources:**
- [Put All of Your Tmux Configs in .config/tmux Directory](https://nickjanetakis.com/blog/put-all-of-your-tmux-configs-and-plugins-in-a-config-tmux-directory)
- [Tmux plugins generate incorrect path in tmux.conf (NixOS)](https://discourse.nixos.org/t/tmux-plugins-fetched-from-github-generate-incorrect-path-in-config-tmux-tmux-conf/25996)

---

### Pitfall 4: tmux-resurrect State Corruption Across Container Restarts

**What goes wrong:**

tmux-resurrect saves session state to `~/.local/share/tmux/resurrect/` (or `~/.tmux/resurrect/` in older versions). In ephemeral Docker containers:

1. **First run**: tmux-resurrect saves state to container filesystem (not volume)
2. **Container stops**: State file remains in stopped container
3. **Container removed** (--rm flag): State lost
4. **Next run**: New container, no state, "nothing to restore"

Even when state IS persisted to a volume, corruption can occur from:

- **Concurrent writes**: Multiple tmux sessions writing to same state file
- **Partial writes**: Container killed mid-save (SIGKILL)
- **Permission errors**: State file owned by wrong user after gosu
- **Path changes**: Host path mounted at different location in new container

From WebSearch: "There is a known bug where re-saving a session restored by tmux-resurrect corrupts the resurrect session file and makes programs not launch. The corruption involves programs like htop and zsh getting swapped in the saved state file."

Also: "Using external volumes with spaces in the name (like 'El Gato') breaks the restore function with errors indicating it can't find files in the path."

**Why it happens:**

aishell containers are ephemeral (--rm flag). The container filesystem is destroyed on exit. tmux-resurrect saves state to `$XDG_DATA_HOME/tmux/resurrect/` (which is `~/.local/share/tmux/resurrect/` by default, per 2025 XDG_STATE_HOME proposal).

If `~/.local/share` is NOT mounted as a volume, state is lost when container is removed.

If it IS mounted, additional issues:

- **UID/GID mismatch**: File written as developer user in container, but UID changes across rebuilds
- **Path encoding**: Resurrect saves working directory paths, which may not exist in new container
- **Process restoration**: Resurrect tries to restart processes that don't exist in container

**Consequences:**

- **State lost on restart**: "I saved my session but it's gone"
- **Restore fails silently**: tmux starts but no windows/panes restored
- **Corrupted state file**: Programs launch in wrong panes
- **Permission denied errors**: Can't write to state file on save
- **Process spawn failures**: Resurrect tries to run commands that don't exist
- **User confusion**: "tmux-resurrect works on host, not in container"

**Prevention:**

**1. Mount resurrect state directory to host:**

```yaml
# In .aishell/config.yaml
volumes:
  - ~/.local/share/tmux/resurrect:/home/developer/.local/share/tmux/resurrect
```

**2. Set explicit state directory in tmux.conf:**

```bash
# In ~/.tmux.conf or container's /etc/tmux.conf
set -g @resurrect-dir '~/.local/share/tmux/resurrect'
```

**3. Use project-specific state directory:**

```bash
# In entrypoint.sh
export TMUX_RESURRECT_DIR="$PWD/.tmux-state"
mkdir -p "$TMUX_RESURRECT_DIR"

# In tmux.conf
set -g @resurrect-dir "$PWD/.tmux-state"
```

This keeps state inside the mounted project directory, ensuring it persists.

**4. Disable process restoration (only save layout):**

```bash
# In tmux.conf - don't try to restore programs
set -g @resurrect-processes 'false'
```

This avoids issues where resurrect tries to restart programs that don't exist in container.

**5. Validate state file permissions:**

```bash
# In entrypoint.sh, after gosu but before tmux
if [ -d "$HOME/.local/share/tmux/resurrect" ]; then
  chown -R "$USER_ID:$GROUP_ID" "$HOME/.local/share/tmux/resurrect"
  chmod -R u+rw "$HOME/.local/share/tmux/resurrect"
fi
```

**6. Document resurrect limitations in containers:**

```markdown
## tmux-resurrect in Containers

tmux-resurrect saves session state, but containers are ephemeral.

**To persist state across container runs:**

1. Mount resurrect directory:
   ```yaml
   volumes:
     - ~/.local/share/tmux/resurrect:/home/developer/.local/share/tmux/resurrect
   ```

2. Or use project-local state:
   ```yaml
   volumes:
     - ./.tmux-state:/home/developer/.local/share/tmux/resurrect
   ```

**Limitations:**
- Process restoration may fail (programs not available in container)
- Working directory paths must exist in container
- State is container-specific (don't share across projects)
```

**7. Graceful degradation when state is corrupt:**

```bash
# In entrypoint.sh, before starting tmux
RESURRECT_LAST="$HOME/.local/share/tmux/resurrect/last"
if [ -f "$RESURRECT_LAST" ]; then
  # Validate state file is readable
  if ! tmux-resurrect-restore-check "$RESURRECT_LAST" 2>/dev/null; then
    echo "WARN: tmux-resurrect state appears corrupt, backing up and starting fresh"
    mv "$RESURRECT_LAST" "${RESURRECT_LAST}.corrupt.$(date +%s)"
  fi
fi
```

**Detection:**

- **Restore doesn't work**: tmux starts but no windows/panes restored
- **Permission denied on save**: "cannot write to ~/.local/share/tmux/resurrect/last"
- **State file exists but empty**: `cat resurrect/last` shows nothing
- **Corruption errors**: Programs launch in wrong panes after restore
- **Process spawn failures**: "command not found" when restoring session
- **State lost after container removal**: Works during container lifetime, gone after restart

**Sources:**
- [Bug: Re-saving corrupts resurrect session file](https://github.com/tmux-plugins/tmux-resurrect/issues/392)
- [Path space breaks resurrect](https://github.com/tmux-plugins/tmux-resurrect/issues/548)
- [Use $XDG_STATE_HOME for resurrect files](https://github.com/tmux-plugins/tmux-resurrect/issues/542)
- [Restore doesn't open docker container](https://github.com/tmux-plugins/tmux-resurrect/issues/402)

---

## Moderate Pitfalls

Mistakes that cause delays or technical debt.

### Pitfall 5: Plugin Installation Order Dependencies

**What goes wrong:**

Some tmux plugins depend on others being loaded first. For example:

- **tmux-continuum** depends on **tmux-resurrect**
- **tmux-powerline** depends on **tmux-colors-solarized**
- Custom themes may depend on specific plugins

If plugins are installed out of order, or if `~/.tmux.conf` loads them in wrong order, features break:

- Continuum can't find resurrect's save command
- Themes fail to apply colors
- Key bindings conflict (later plugin overwrites earlier one)

**Why it happens:**

TPM loads plugins in the order they appear in `~/.tmux.conf`:

```bash
set -g @plugin 'tmux-plugins/tmux-resurrect'
set -g @plugin 'tmux-plugins/tmux-continuum'  # Must come AFTER resurrect
```

When aishell installs plugins via config.yaml, the installation order may not match the load order.

**Prevention:**

1. **Document plugin order in config.yaml**
2. **Validate plugin dependencies** during config parsing
3. **Auto-generate tmux.conf plugin declarations** in correct order if user doesn't have one

**Detection:**

- Plugin features don't work despite being installed
- Continuum doesn't auto-save (resurrect works manually)
- Key bindings missing or wrong
- Errors in tmux logs about undefined options

---

### Pitfall 6: Volume-Mounted Plugins vs Host-Mounted Config Version Mismatch

**What goes wrong:**

User's host `~/.tmux.conf` expects plugin version X. aishell installs plugin version Y in harness volume (latest from git). Features break because option names changed between versions.

**Prevention:**

1. **Pin plugin versions in config.yaml**: `tmux-plugins/tmux-resurrect@v4.0.0`
2. **Clone specific tags**: `git clone --depth 1 --branch v4.0.0`
3. **Document version compatibility**

**Detection:**

- Config has plugin options that tmux complains about
- "unknown option: @resurrect-strategy-vim"

---

### Pitfall 7: Plugin Config Not Applied (Source Order)

**What goes wrong:**

User sets plugin options in `~/.tmux.conf` AFTER the plugin is loaded. Plugins read their options when loaded, so late-set options are ignored.

**Prevention:**

Document correct order:

```bash
# Correct order in ~/.tmux.conf:
set -g @plugin 'tmux-plugins/tmux-resurrect'
set -g @resurrect-dir '~/.tmux-state'  # Set BEFORE run tpm
run '/tools/tmux/plugins/tpm/tpm'
```

**Detection:**

- Plugin options don't take effect
- Default behavior despite custom config

---

### Pitfall 8: TPM Auto-Install Race Condition

**What goes wrong:**

TPM auto-install snippet runs during tmux startup. If user immediately attaches, TPM may still be installing in the background.

**Prevention:**

Install plugins during volume population (`aishell build`/`aishell update`), not container startup.

**Detection:**

- Plugins appear/disappear during session
- "unknown command" errors that resolve after few seconds

---

## Minor Pitfalls

Mistakes that cause annoyance but are fixable.

### Pitfall 9: Plugin Config File Locations

Different plugins store data in different locations. If not mounted to volumes, data is lost on container restart.

**Prevention:** Mount entire `~/.local/share/tmux/` to persist all plugin data.

---

### Pitfall 10: tmux Version in Container vs Plugin Requirements

Some plugins require tmux >= 3.0. If container has older version, plugins fail to load.

**Prevention:** Document tmux version in foundation image and validate plugin compatibility.

---

### Pitfall 11: TPM $HOME Path Hardcoding

TPM scripts hardcode `$HOME` for plugin paths. If entrypoint changes `$HOME` after TPM installation, TPM can't find plugins.

**Prevention:** Ensure `$HOME` is consistent in entrypoint before any TPM operations.

---

### Pitfall 12: Conflicting Plugin Key Bindings

Multiple plugins bind the same keys. Later plugin overwrites earlier one.

**Prevention:** Document known key conflicts and rebinding strategies.

---

## Phase-Specific Warnings

| Phase Topic | Likely Pitfall | Mitigation |
|-------------|---------------|------------|
| Build opt-in flag | Pitfall 1 (Opt-in breaking change) | State migration, version detection, attach validation |
| Plugin installation | Pitfall 2 (TPM non-interactive failures) | Install in volume population, retry logic, validation |
| Config mounting | Pitfall 3 (Plugin path conflicts) | Symlinks or TMUX_PLUGIN_MANAGER_PATH, path normalization |
| Resurrect integration | Pitfall 4 (State corruption) | Mount resurrect dir to host, disable process restore |
| Plugin dependencies | Pitfall 5 (Load order) | Validate dependencies, document order requirements |
| Version pinning | Pitfall 6 (Version mismatch) | Support @version syntax in config.yaml |
| Config generation | Pitfall 7 (Source order) | Generate correct order if user has no tmux.conf |
| Build vs runtime | Pitfall 8 (Auto-install race) | Install during volume population, not runtime |

## Integration-Specific Concerns

### Opt-in + Volume Architecture Interaction

The combination of opt-in tmux and volume-mounted harness tools creates a dependency. If `--with-tmux` but no harnesses, where do plugins go?

**Solution:** Create dedicated tmux volume, or put tmux tools in harness volume unconditionally when `--with-tmux` is set.

### State Migration from v2.7.0 Always-On

Users upgrading from v2.7.0/v2.8.0 (tmux always-on) to v2.9.0 (opt-in) need clear migration path:

1. **Detect old version** in state.edn
2. **Assume tmux was present** if version is v2.7.0-v2.8.x
3. **Set :with-tmux true** in migrated state
4. **Warn user** about behavioral change
5. **Document** in changelog and migration guide

### Plugin Installation Timing

Three options for when to install plugins:

1. **During foundation build**: Slow, couples plugins to image
2. **During volume population**: Fast, separated from image (RECOMMENDED)
3. **During container startup**: Network-dependent, race conditions

v2.9.0 should use option 2 (volume population) for consistency with harness tools.

## Testing Checklist for Implementation

**Opt-in migration:**
- [ ] v2.7.0/v2.8.0 state migrates to v2.9.0 with :with-tmux true
- [ ] New builds default to :with-tmux false
- [ ] `aishell attach` errors gracefully when tmux not installed
- [ ] Warning shown when rebuilding from always-on version without --with-tmux

**Plugin installation:**
- [ ] Plugins install successfully in disconnected network (cached)
- [ ] Plugin installation errors are visible during build, not runtime
- [ ] Failed plugin install prevents build (doesn't create broken container)
- [ ] Retry logic handles transient network failures

**Config mounting:**
- [ ] Host `~/.tmux.conf` with plugins works in container
- [ ] Plugin path symlinks created correctly
- [ ] TMUX_PLUGIN_MANAGER_PATH set correctly
- [ ] Config errors are clear (plugin not found, wrong path)

**Resurrect state:**
- [ ] State persists across container restarts when volume mounted
- [ ] State directory has correct permissions after gosu
- [ ] Corrupted state files handled gracefully (backup and start fresh)
- [ ] Process restoration disabled by default (layout-only)

**Version compatibility:**
- [ ] tmux version in container documented
- [ ] Plugin version pinning works (@version syntax)
- [ ] Incompatible plugins warn during build

## Sources Summary

**Opt-in Breaking Changes:**
- [Feature Flag Best Practices](https://frontegg.com/blog/feature-flag-best-practices)
- [Backwards Compatibility and Breaking Changes](https://github.com/kedro-org/kedro/wiki/Backwards-compatibility-and-breaking-changes)
- [Feature Request: Restore nice_nano_v2 as alias](https://github.com/zmkfirmware/zmk/issues/3157)

**TPM Installation:**
- [Unable to install plugins](https://github.com/tmux-plugins/tpm/issues/195)
- [Plugin download failed](https://github.com/tmux-plugins/tpm/issues/62)
- [Automatic TPM Installation docs](https://github.com/tmux-plugins/tpm/blob/master/docs/automatic_tpm_installation.md)
- [TPM GitHub README](https://github.com/tmux-plugins/tpm)

**Plugin Paths:**
- [Put Tmux Configs in .config/tmux Directory](https://nickjanetakis.com/blog/put-all-of-your-tmux-configs-and-plugins-in-a-config-tmux-directory)
- [Tmux plugins incorrect path in NixOS](https://discourse.nixos.org/t/tmux-plugins-fetched-from-github-generate-incorrect-path-in-config-tmux-tmux-conf/25996)

**tmux-resurrect:**
- [Bug: Re-saving corrupts resurrect session file](https://github.com/tmux-plugins/tmux-resurrect/issues/392)
- [Path space breaks resurrect](https://github.com/tmux-plugins/tmux-resurrect/issues/548)
- [Use $XDG_STATE_HOME for resurrect files](https://github.com/tmux-plugins/tmux-resurrect/issues/542)
- [Restore doesn't open docker container](https://github.com/tmux-plugins/tmux-resurrect/issues/402)

**Docker Volumes:**
- [Permission denied when mounting volume](https://labex.io/tutorials/docker-how-to-resolve-permission-denied-error-when-mounting-volume-in-docker-417724)
- [Docker Files and Volumes: Permission Denied](https://mydeveloperplanet.com/2022/10/19/docker-files-and-volumes-permission-denied/)

All research conducted 2026-02-01, sources verified for currency and relevance.
