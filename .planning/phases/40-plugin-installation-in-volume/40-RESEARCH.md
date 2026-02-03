# Phase 40: Plugin Installation in Volume - Research

**Researched:** 2026-02-02
**Domain:** tmux plugin manager (TPM), non-interactive plugin installation, volume population
**Confidence:** HIGH

## Summary

Phase 40 adds tmux plugin installation capability to the harness volume population process. The research confirms that TPM (Tmux Plugin Manager) is the standard tool for managing tmux plugins and provides explicit support for non-interactive installation via the `bin/install_plugins` script.

TPM uses a simple `owner/repo` format for plugin declarations, matching GitHub's repository naming convention. The standard installation process involves: (1) git clone TPM to a plugins directory, (2) configure plugins in tmux.conf, (3) run the install_plugins script to fetch all declared plugins.

The key technical insight is that while TPM was originally designed for interactive use, it includes dedicated scripts specifically for automated/containerized environments. The `bin/install_plugins` script can run without an active tmux session, making it ideal for Docker volume population during `aishell build`.

**Primary recommendation:** Use TPM's `bin/install_plugins` script during harness volume population, with plugin format validation at config parse time to catch errors before build starts.

## Standard Stack

The established libraries/tools for tmux plugin management:

### Core
| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| TPM (tmux-plugins/tpm) | latest (git) | Tmux plugin manager | Official plugin manager from tmux-plugins org, most widely adopted |
| git | 2.x | Clone TPM and plugins | Required by TPM for repository operations |
| tmux | 3.1+ | Terminal multiplexer | TPM supports XDG paths since tmux 3.1 |

### Supporting
| Library | Version | Purpose | When to Use |
|---------|---------|---------|-------------|
| bash | 4.x+ | TPM scripts runtime | Required - TPM install scripts are written in bash |

### Alternatives Considered
| Instead of | Could Use | Tradeoff |
|------------|-----------|----------|
| TPM | tmux-plug (tplug) | tplug designed for non-tmux environments, but TPM is standard and has `bin/install_plugins` |
| TPM | Manual git clones | No dependency management, no update mechanism, reinventing the wheel |

**Installation:**
```bash
# TPM installation
git clone https://github.com/tmux-plugins/tpm ~/.tmux/plugins/tpm

# Or for XDG path (tmux 3.1+)
git clone https://github.com/tmux-plugins/tpm ~/.config/tmux/plugins/tpm
```

**Plugin installation (non-interactive):**
```bash
# After TPM is cloned and tmux.conf configured
~/.tmux/plugins/tpm/bin/install_plugins
# Or for XDG path
~/.config/tmux/plugins/tpm/bin/install_plugins
```

## Architecture Patterns

### Recommended Directory Structure (Standard Path)
```
/tools/tmux/
├── plugins/
│   ├── tpm/                    # TPM itself (cloned from GitHub)
│   │   ├── bin/
│   │   │   └── install_plugins # Non-interactive installation script
│   │   ├── scripts/
│   │   └── tpm                 # Main TPM script
│   ├── tmux-sensible/          # Example plugin (installed by TPM)
│   └── [other-plugins]/        # Additional plugins installed by TPM
```

### Pattern 1: Volume-Based Plugin Installation

**What:** Install TPM and plugins into harness volume during `aishell build`, separate from foundation image

**When to use:** When you want plugins available in container but not baked into image

**Example:**
```clojure
;; Source: Based on existing volume.clj populate-volume pattern
(defn install-tmux-plugins
  "Install TPM and plugins into harness volume via temporary container.

   Arguments:
   - volume-name: Volume name string
   - config: Config map with :tmux {:plugins [...]} section

   Returns: {:success true} on success"
  [volume-name config]
  (let [plugins (get-in config [:tmux :plugins])
        install-command (build-tpm-install-command plugins)
        cmd ["docker" "run" "--rm"
             "-v" (str volume-name ":/tools")
             "--entrypoint" ""
             foundation-image-tag
             "sh" "-c" install-command]]
    ;; Run installation...
    ))

(defn build-tpm-install-command
  "Build shell command for installing TPM and plugins.

   Returns shell command string that:
   1. Creates plugin directory
   2. Clones TPM repository
   3. Generates minimal tmux.conf with plugin declarations
   4. Runs bin/install_plugins to fetch all plugins
   5. Sets world-readable permissions"
  [plugins]
  (let [plugin-lines (str/join "\\n"
                       (map #(str "set -g @plugin '" % "'") plugins))]
    (str "mkdir -p /tools/tmux/plugins && "
         "git clone --depth 1 https://github.com/tmux-plugins/tpm /tools/tmux/plugins/tpm && "
         "printf '%s\\n' '" plugin-lines "' > /tmp/tmux.conf && "
         "TMUX_PLUGIN_MANAGER_PATH=/tools/tmux/plugins /tools/tmux/plugins/tpm/bin/install_plugins && "
         "chmod -R a+rX /tools/tmux")))
```

### Pattern 2: Config-Time Plugin Validation

**What:** Validate plugin format at config parse time, not at build time

**When to use:** Always - fail fast before Docker operations start

**Example:**
```clojure
;; Source: Based on existing config.clj validation patterns
(def plugin-format-pattern
  "Regex pattern for GitHub owner/repo format.
   GitHub usernames: alphanumeric, hyphens, max 39 chars, no leading/trailing hyphens.
   Repo names: alphanumeric, hyphens, underscores, dots, max 100 chars."
  #"^[a-zA-Z0-9]([a-zA-Z0-9-]{0,37}[a-zA-Z0-9])?/[a-zA-Z0-9._-]{1,100}$")

(defn validate-plugin-format
  "Validate single plugin string matches owner/repo format.
   Returns nil if valid, error message if invalid."
  [plugin]
  (when-not (re-matches plugin-format-pattern plugin)
    (str "Invalid plugin format: '" plugin "' - expected 'owner/repo'")))

(defn validate-tmux-plugins
  "Validate tmux plugins list in config.
   Warns about invalid formats."
  [plugins source-path]
  (when (seq plugins)
    (doseq [plugin plugins]
      (when-not (string? plugin)
        (output/warn (str "Invalid plugin in " source-path ": expected string, got " (type plugin))))
      (when-let [error (validate-plugin-format plugin)]
        (output/warn (str error " in " source-path))))))
```

### Pattern 3: Integration with Existing Volume Population

**What:** Extend existing `populate-volume` to include tmux plugin installation when `:with-tmux` is enabled

**When to use:** During `aishell build` and `aishell update` operations

**Example:**
```clojure
;; Source: Extends existing volume.clj populate-volume pattern
(defn populate-volume
  "Install harness tools and tmux plugins into Docker volume.

   Process:
   1. Install harness tools (npm packages, OpenCode binary)
   2. If :with-tmux enabled, install TPM and plugins
   3. Set world-readable permissions"
  [volume-name state config & [opts]]
  (let [harness-install (build-install-commands state)
        tmux-install (when (:with-tmux state)
                       (build-tpm-install-command
                         (get-in config [:tmux :plugins])))
        full-command (str harness-install
                         (when tmux-install (str " && " tmux-install)))]
    ;; Execute full command in temporary container...
    ))
```

### Anti-Patterns to Avoid

- **Installing plugins at runtime**: TPM plugins should be installed during `aishell build`, not when container starts. Runtime installation breaks volume sharing and slows startup.

- **Using `run -b` flag in generated tmux.conf**: The `-b` flag (background execution) prevents TPM scripts from working outside tmux. Use `run` without `-b` for non-interactive environments.

- **Skipping format validation**: Don't rely on git clone to validate plugin format. Validate at config parse time to provide clear error messages before Docker operations start.

- **Creating tmux server for installation**: Early TPM versions required `tmux new-session -d` before running install scripts. Modern TPM's `bin/install_plugins` works without a tmux server.

## Don't Hand-Roll

Problems that look simple but have existing solutions:

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| Plugin dependency management | Custom clone scripts | TPM's bin/install_plugins | Handles dependencies, updates, error handling, parallel fetching |
| Plugin format parsing | String splitting | TPM's plugin_functions.sh | Supports multiple formats (owner/repo, owner/repo#branch, full git URLs) |
| Plugin update mechanism | Manual git pull scripts | TPM's update bindings/scripts | Handles version conflicts, rollback, per-plugin updates |
| Plugin directory management | mkdir -p loops | TPM's directory logic | Handles XDG paths, custom TMUX_PLUGIN_MANAGER_PATH, permissions |

**Key insight:** TPM is battle-tested across thousands of dotfile repositories and containerized environments. Its non-interactive installation script (`bin/install_plugins`) is specifically designed for Docker/CI/CD use cases and handles edge cases that aren't obvious (permission issues, partial installs, network failures).

## Common Pitfalls

### Pitfall 1: Forgetting TMUX_PLUGIN_MANAGER_PATH

**What goes wrong:** TPM's `bin/install_plugins` script looks for tmux.conf in standard locations (~/.tmux.conf or ~/.config/tmux/tmux.conf). If config is elsewhere or if you're using a custom plugin path, plugins get installed to the wrong location.

**Why it happens:** TPM determines plugin directory from config file location unless explicitly overridden with `TMUX_PLUGIN_MANAGER_PATH` environment variable.

**How to avoid:** When installing plugins to non-standard paths (like `/tools/tmux/plugins`), explicitly set `TMUX_PLUGIN_MANAGER_PATH` before running install script:

```bash
TMUX_PLUGIN_MANAGER_PATH=/tools/tmux/plugins /tools/tmux/plugins/tpm/bin/install_plugins
```

**Warning signs:** Plugins get cloned to `~/.tmux/plugins/` inside container instead of `/tools/tmux/plugins/`

### Pitfall 2: Windows Line Endings in Cloned Repositories

**What goes wrong:** On Windows/Cygwin systems with `core.autocrlf=true`, TPM scripts get cloned with Windows line endings (CRLF), causing bash execution failures.

**Why it happens:** Git's autocrlf setting converts line endings during clone, but bash scripts require Unix line endings (LF).

**How to avoid:**
- Run git config with `core.autocrlf=false` before cloning TPM
- Or configure git to not transform line endings: `git config --system core.autocrlf false`

**Warning signs:** Error messages like "'~/.tmux/plugins/tpm/tpm' returned 2" or "unexpected end of file"

**Relevance to aishell:** LOW - aishell uses Linux-based foundation image (Debian), so Windows line ending issues don't apply. Git clones inside container use Linux defaults.

### Pitfall 3: Missing Git in Container

**What goes wrong:** TPM's install_plugins script fails silently or with cryptic errors when git isn't available in the container.

**Why it happens:** TPM relies on git for all plugin operations (clone, update, etc). If git is missing from PATH or not installed, TPM can't function.

**How to avoid:** Ensure git is installed in foundation image before TPM operations. Verify with `command -v git` in install script.

**Warning signs:**
- Empty plugin directories after installation
- "command not found: git" errors in container logs
- TPM install script completes but no plugins appear

**Relevance to aishell:** Git is already installed in aishell foundation image (required for harness operations), so this pitfall is already avoided.

### Pitfall 4: Incorrect Plugin Format Causes Silent Failures

**What goes wrong:** Invalid plugin formats (missing owner, wrong separator, typos) cause TPM to skip plugins silently or produce unhelpful git errors during installation.

**Why it happens:** TPM's plugin parsing is permissive - it attempts to process any string. Git clone failures may scroll past in build logs.

**How to avoid:** Validate plugin format at config parse time using regex pattern. Fail fast with clear error message before Docker operations.

**Warning signs:**
- Build succeeds but plugins don't appear in container
- Git errors like "fatal: repository 'owner-repo' does not exist" in verbose output
- Some plugins install, others don't

### Pitfall 5: Forgetting to Set World-Readable Permissions

**What goes wrong:** Plugins installed by root during `docker run` have restrictive permissions (700/600), preventing non-root container user from accessing plugin files.

**Why it happens:** Docker runs as root by default. Git clone creates files with owner-only permissions.

**How to avoid:** Run `chmod -R a+rX /tools/tmux` after plugin installation to make directories traversable and files readable by all users.

**Warning signs:**
- "Permission denied" errors when tmux tries to source plugins
- User can list plugin directories but can't read plugin files
- Plugins work when running container as root but fail for normal user

**Relevance to aishell:** CRITICAL - aishell containers run as non-root user (via gosu). Without chmod, plugins will be inaccessible.

### Pitfall 6: Installing Plugins Without Testing Plugin List First

**What goes wrong:** User declares 10 plugins, one has a typo. Build takes 2 minutes installing valid plugins, then fails on the typo. User fixes typo, re-runs build, wastes another 2 minutes re-installing the 9 valid plugins.

**Why it happens:** No pre-flight validation of plugin existence before starting installation.

**How to avoid:** Two-stage validation:
1. Format validation at config parse (covered in pitfall 4)
2. Optional: Add `--dry-run` flag to test plugin list without installing

For MVP (Phase 40): Format validation is sufficient. Dry-run can be deferred to future enhancement.

**Warning signs:**
- Repeated build failures after fixing individual plugin typos
- Long build times for debugging simple config errors

## Code Examples

Verified patterns from official sources:

### Non-Interactive TPM Installation (Docker/CI/CD)
```bash
# Source: https://github.com/tmux-plugins/tpm/issues/6
# Create plugin directory structure
mkdir -p /tools/tmux/plugins

# Clone TPM repository (--depth 1 for faster clone)
git clone --depth 1 https://github.com/tmux-plugins/tpm /tools/tmux/plugins/tpm

# Generate temporary tmux.conf with plugin declarations
# (TPM's install_plugins script reads this to know what to install)
cat > /tmp/tmux.conf <<'EOF'
set -g @plugin 'tmux-plugins/tmux-sensible'
set -g @plugin 'tmux-plugins/tmux-resurrect'
EOF

# Set plugin directory and run installation
# NOTE: Set TMUX_PLUGIN_MANAGER_PATH when using non-standard path
TMUX_PLUGIN_MANAGER_PATH=/tools/tmux/plugins /tools/tmux/plugins/tpm/bin/install_plugins

# Make plugins readable by non-root users
chmod -R a+rX /tools/tmux
```

### Plugin Format Validation (Clojure)
```clojure
;; Source: GitHub naming rules + Clojure regex patterns
;; GitHub usernames: alphanumeric + hyphens, 1-39 chars, no leading/trailing hyphens
;; Repository names: alphanumeric + hyphens/underscores/dots, 1-100 chars

(def plugin-format-pattern
  #"^[a-zA-Z0-9]([a-zA-Z0-9-]{0,37}[a-zA-Z0-9])?/[a-zA-Z0-9._-]{1,100}$")

(defn validate-plugin-format
  "Validate plugin matches owner/repo format.
   Returns nil if valid, error message string if invalid."
  [plugin]
  (cond
    (not (string? plugin))
    "Plugin must be a string"

    (not (re-matches plugin-format-pattern plugin))
    (str "Invalid format: '" plugin "' - expected 'owner/repo'")

    :else nil))

;; Usage examples:
(validate-plugin-format "tmux-plugins/tmux-sensible")  ; => nil (valid)
(validate-plugin-format "invalid")                     ; => "Invalid format: 'invalid' - expected 'owner/repo'"
(validate-plugin-format "-bad/repo")                   ; => "Invalid format: '-bad/repo' - expected 'owner/repo'"
```

### Config Validation Integration
```clojure
;; Source: Based on existing config.clj validation patterns
;; Integrate into existing validate-tmux-config function

(defn validate-tmux-config
  "Validate tmux config structure. Warns on invalid format."
  [tmux-config source-path]
  (when tmux-config
    ;; Existing validation: ensure tmux-config is a map
    (when-not (map? tmux-config)
      (output/warn (str "Invalid tmux section in " source-path
                       ": expected map, got " (type tmux-config))))

    ;; NEW: Validate plugins list
    (when-let [plugins (:plugins tmux-config)]
      (when-not (sequential? plugins)
        (output/warn (str "Invalid tmux.plugins in " source-path
                         ": expected list, got " (type plugins))))
      (doseq [plugin plugins]
        (when-let [error (validate-plugin-format plugin)]
          (output/warn (str "Invalid plugin in " source-path ": " error))))))
  tmux-config)
```

### TPM Installation Shell Command Builder
```clojure
;; Source: Pattern from existing volume.clj build-install-commands
(defn build-tpm-install-command
  "Build shell command for installing TPM and plugins.

   Arguments:
   - plugins: Vector of plugin strings in 'owner/repo' format

   Returns: Shell command string or nil if no plugins"
  [plugins]
  (when (seq plugins)
    (let [;; Build plugin declaration lines for tmux.conf
          plugin-lines (str/join "\\n"
                        (map #(str "set -g @plugin '" % "'") plugins))
          ;; Construct multi-command pipeline
          commands ["mkdir -p /tools/tmux/plugins"
                   "git clone --depth 1 https://github.com/tmux-plugins/tpm /tools/tmux/plugins/tpm"
                   (str "printf '%s\\n' '" plugin-lines "' > /tmp/tmux.conf")
                   "TMUX_PLUGIN_MANAGER_PATH=/tools/tmux/plugins /tools/tmux/plugins/tpm/bin/install_plugins"
                   "chmod -R a+rX /tools/tmux"]]
      (str/join " && " commands))))

;; Example output:
;; "mkdir -p /tools/tmux/plugins && git clone --depth 1 https://github.com/tmux-plugins/tpm /tools/tmux/plugins/tpm && printf '%s\n' 'set -g @plugin 'tmux-plugins/tmux-sensible'' > /tmp/tmux.conf && TMUX_PLUGIN_MANAGER_PATH=/tools/tmux/plugins /tools/tmux/plugins/tpm/bin/install_plugins && chmod -R a+rX /tools/tmux"
```

### Integration with Existing Volume Population
```clojure
;; Source: Extends existing volume.clj populate-volume function
(defn populate-volume
  "Install harness tools and tmux plugins into Docker volume.

   Modified to include optional tmux plugin installation."
  [volume-name state config & [opts]]
  (let [;; Existing harness installation command
        harness-install (build-install-commands state)

        ;; NEW: Build tmux plugin installation command if enabled
        tmux-install (when (:with-tmux state)
                       (build-tpm-install-command
                         (get-in config [:tmux :plugins])))

        ;; Combine commands
        full-command (str harness-install
                         (when tmux-install
                           (str " && " tmux-install)))

        ;; Rest of populate-volume logic unchanged...
        verbose? (:verbose opts)
        cmd ["docker" "run" "--rm"
             "-v" (str volume-name ":/tools")
             "--entrypoint" ""
             build/foundation-image-tag
             "sh" "-c" full-command]]
    ;; Execute as before...
    ))
```

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| Interactive plugin install (prefix + I) | Non-interactive bin/install_plugins | ~2016 | Enabled Docker/CI/CD plugin installation |
| ~/.tmux/ path only | XDG path support (~/.config/tmux/) | tmux 3.1 (2020) | Modern config organization, TPM auto-detects |
| Manual tmux server start before install | Direct bin/install_plugins execution | TPM ~2017 | Simplified automated installation |
| run -b flag in tmux.conf | run without -b for automation | Ongoing | -b flag breaks non-interactive scripts |

**Deprecated/outdated:**
- **Creating detached tmux session before install**: Early workaround for TPM limitations. Modern `bin/install_plugins` doesn't require tmux server.
- **Using `scripts/install_plugins.sh`**: Some old tutorials reference `scripts/install_plugins.sh`. Correct path is `bin/install_plugins`.
- **Setting TPM path in .tmux.conf**: Old approach used `set-environment -g TMUX_PLUGIN_MANAGER_PATH`. For automation, pass as env var to script: `TMUX_PLUGIN_MANAGER_PATH=/path bin/install_plugins`

## Open Questions

Things that couldn't be fully resolved:

1. **Should aishell validate plugin existence before installation?**
   - What we know: Format validation catches syntax errors. Git clone will fail if plugin doesn't exist, but only after TPM is cloned and installation starts.
   - What's unclear: Is pre-flight validation worth the complexity? Would require GitHub API calls or git ls-remote checks.
   - Recommendation: Phase 40 MVP does format validation only. Plugin existence validation can be deferred as enhancement. Most users will discover missing plugins quickly when build fails, and verbose mode shows git errors clearly.

2. **Should plugin installation have timeout?**
   - What we know: Large plugins or slow networks could cause long installs. TPM doesn't have built-in timeout mechanism.
   - What's unclear: What's a reasonable timeout for plugin installation? Should timeout be per-plugin or total?
   - Recommendation: Use Docker's default timeout initially (no explicit timeout). Users can ctrl-C if build hangs. Monitor for issues in real-world usage before adding complexity.

3. **How to handle plugin installation failures gracefully?**
   - What we know: TPM's install_plugins continues after individual plugin failures, installing what it can. Exit code reflects if any plugin failed.
   - What's unclear: Should aishell fail build on any plugin failure, or allow partial installation?
   - Recommendation: Fail build on any plugin installation failure (non-zero exit from install_plugins). Partial installations lead to confusing runtime behavior. User can remove problematic plugin from config and retry.

## Sources

### Primary (HIGH confidence)
- [GitHub - tmux-plugins/tpm: Tmux Plugin Manager](https://github.com/tmux-plugins/tpm) - Official TPM repository, installation instructions
- [tpm/docs/automatic_tpm_installation.md](https://github.com/tmux-plugins/tpm/blob/master/docs/automatic_tpm_installation.md) - Official automated installation guide
- [Run Install from command line? · Issue #6 · tmux-plugins/tpm](https://github.com/tmux-plugins/tpm/issues/6) - Official confirmation of bin/install_plugins for non-interactive use
- [Are TPM bin/ scripts supposed to work outside of tmux? · Issue #151 · tmux-plugins/tpm](https://github.com/tmux-plugins/tpm/issues/151) - TMUX_PLUGIN_MANAGER_PATH requirements, -b flag issues
- [tpm/docs/tpm_not_working.md](https://github.com/tmux-plugins/tpm/blob/master/docs/tpm_not_working.md) - Official troubleshooting guide
- [tpm/docs/changing_plugins_install_dir.md](https://github.com/tmux-plugins/tpm/blob/master/docs/changing_plugins_install_dir.md) - Official guide for TMUX_PLUGIN_MANAGER_PATH

### Secondary (MEDIUM confidence)
- [GitHub - shinnn/github-username-regex](https://github.com/shinnn/github-username-regex) - GitHub username validation rules
- [GitHub username considerations documentation](https://docs.github.com/en/enterprise-cloud@latest/admin/managing-iam/iam-configuration-reference/username-considerations-for-external-authentication) - Official GitHub naming rules
- [XDG and Tmux Plugins](https://alecthegeek.gitlab.io/blog/2025/08/xdg-and-tmux-plugins/) - XDG path behavior with TPM
- [Put All of Your Tmux Configs in .config/tmux Directory](https://nickjanetakis.com/blog/put-all-of-your-tmux-configs-and-plugins-in-a-config-tmux-directory) - Modern tmux/TPM directory structure

### Tertiary (LOW confidence)
- [Best practices for getting code into container](https://forums.docker.com/t/best-practices-for-getting-code-into-a-container-git-clone-vs-copy-vs-data-container/4077) - Docker community discussion on git clone in containers
- [Cloning Code In Containers](https://dzone.com/articles/clone-code-into-containers-how) - General best practices for git in Docker

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH - TPM is official and widely adopted, bin/install_plugins documented for automation
- Architecture: HIGH - Verified patterns from official TPM docs and existing aishell volume.clj
- Plugin format validation: HIGH - GitHub naming rules are documented, regex pattern is standard
- Pitfalls: HIGH - Based on official troubleshooting docs and verified GitHub issues
- Integration approach: HIGH - Follows existing aishell patterns (volume.clj, config.clj)

**Research date:** 2026-02-02
**Valid until:** 30 days (2026-03-04) - TPM is stable, but git/Docker best practices may evolve
