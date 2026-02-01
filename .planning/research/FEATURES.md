# Feature Landscape: tmux Opt-In and Plugin Support

**Domain:** Docker-based AI coding agent sandbox with optional tmux integration
**Milestone:** v2.9.0 - tmux opt-in and plugin support
**Researched:** 2026-02-01
**Confidence:** HIGH (ecosystem patterns mature, Docker integration well-documented)

## Executive Summary

The v2.9.0 milestone shifts tmux from always-on to opt-in, with plugin management and configuration mounting. This represents a maturity phase: the foundation (tmux-in-Docker, attach workflows, named containers) exists in v2.7-2.8, and now we're adding power-user features (plugins, persistence, configuration).

The feature set divides into four categories:

1. **Opt-in mechanism** (build-time flag, default behavior)
2. **Configuration mounting** (user's ~/.tmux.conf into container)
3. **Plugin management** (declarative plugin list in config.yaml)
4. **Session persistence** (tmux-resurrect state saved to host)

Key insight from research: **tmux plugin ecosystem uses .tmux.conf for configuration, NOT YAML**. The config.yaml approach for aishell must bridge this gap - declare plugins in YAML, generate .tmux.conf snippets, install via TPM.

## Table Stakes

Features users expect from opt-in tmux with plugin support. Missing these means the feature feels incomplete.

| Feature | Why Expected | Complexity | Dependencies |
|---------|--------------|------------|--------------|
| `--with-tmux` build flag | Opt-in is the commitment; flag is how users signal it | Low | Build command parsing |
| Default = no tmux | Opt-in means OFF by default; reverses v2.7-2.8 behavior | Low | Build templates, entrypoint logic |
| Mount user's ~/.tmux.conf | Users have existing configs, expect them to work | Medium | Volume mount, user switching (gosu) |
| tmux installed in base image | Can't use plugins without tmux itself | Low | Already exists in foundation |
| TPM (Tmux Plugin Manager) installed | De facto standard for plugin management | Low | Clone TPM during build |
| Declarative plugin list | Users shouldn't manually edit container .tmux.conf | Medium | Config YAML schema, validation |
| Plugin installation during build | Pre-installed plugins, not runtime downloads | Medium | Dockerfile template, TPM bin/install_plugins |
| tmux-resurrect plugin support | Core persistence plugin users expect | Low | Included in plugin list options |
| Session state volume mount | Resurrect state must survive container lifecycle | Medium | Harness volume architecture |
| Graceful no-tmux mode | When --with-tmux not passed, no tmux references | Low | Conditional entrypoint logic |
| Config validation | Catch YAML typos before build | Low | Schema validation |
| Plugin name validation | Verify plugin syntax before TPM install | Low | Regex check for GitHub shorthand |

## Differentiators

Features that set aishell apart from manual tmux+Docker+plugin workflows. Not expected, but highly valued.

| Feature | Value Proposition | Complexity | Dependencies |
|---------|-------------------|------------|--------------|
| YAML-based plugin config | Declarative > imperative; fits aishell config pattern | Medium | Transform YAML → .tmux.conf |
| Plugin installation at build time | Faster container start (no runtime cloning) | Medium | Dockerfile RUN step with TPM |
| Automatic TPM initialization | Users don't need to know TPM exists | Low | .tmux.conf generation includes TPM init |
| Resurrect state in harness volume | Reuses existing volume architecture, not new mount | Medium | Harness volume at /tools, resurrect subdir |
| Plugin defaults for AI workflows | Pre-vetted plugins (sensible, yank, resurrect) | Low | Documentation, examples |
| Plugin version pinning | Reproducibility across builds | Medium | TPM branch/tag syntax support |
| Hybrid config: user + aishell | User's .tmux.conf + aishell-generated plugin config | High | Two-stage config merge, source order |
| Config.yaml plugin options | Plugin-specific settings (resurrect save-dir) | Medium | YAML schema extension, variable substitution |
| Incremental plugin updates | `aishell update` refreshes plugins, not just tools | Medium | Volume update command, TPM update script |
| Plugin troubleshooting command | `aishell tmux-debug` shows plugin status | Low | New subcommand, TPM diagnostic queries |

## Anti-Features

Features to explicitly NOT build. Common mistakes or unnecessary complexity.

| Anti-Feature | Why Avoid | What to Do Instead |
|--------------|-----------|-------------------|
| Runtime plugin installation | Slow, network-dependent, violates reproducibility | Install at build time via TPM bin/install_plugins |
| GUI plugin manager | CLI tool, not IDE; users comfortable with YAML | Declarative config.yaml list |
| Plugin marketplace/discovery | Scope creep; users know what plugins they want | Document popular plugins, link to TPM ecosystem |
| Automatic plugin updates | Version drift breaks reproducibility | Explicit version pinning, manual update via `aishell build` |
| Session resurrection across container restarts | Violates ephemeral design; users expect fresh state | Resurrect state for detach/attach within container lifetime |
| Plugin conflict resolution | Complex dependency management; trust TPM | Validate plugin names, let TPM handle install order |
| Tmux theme/status bar customization | Subjective; users have strong preferences | Mount user's .tmux.conf with full control |
| Session template management (tmuxp) | Separate tool with different goals | Focus on plugin management, not session layout |
| Nested tmux support | Confusing; containers already isolated | One tmux server per container, document SSH forwarding if needed |
| Plugin metrics/telemetry | Privacy concern; no value for sandbox use case | No tracking, no phone-home |
| Automatic .tmux.conf generation | Overwrites user configs; dangerous | Merge strategy: user config + plugin append |
| Global plugin cache | Complexity; containers should be independent | Each build installs plugins to image |

## Feature Dependencies

```
Foundation (already exists in v2.7-2.8):
├─ tmux installed in foundation image
├─ Named containers with project isolation
├─ Attach command and workflow
└─ Harness volume architecture at /tools

Opt-in mechanism (v2.9.0 Phase 1):
├─ --with-tmux build flag
├─ Build config stores tmux enabled/disabled
├─ Conditional entrypoint (tmux session OR direct exec)
└─ attach subcommand registration conditional on flag

Configuration mounting (v2.9.0 Phase 2):
├─ Volume mount ~/.tmux.conf → /home/dev/.tmux.conf
├─ Ownership fix (gosu user, not root)
└─ Conflict handling: user config + aishell plugins

Plugin management (v2.9.0 Phase 3):
├─ TPM installed to /home/dev/.tmux/plugins/tpm
├─ config.yaml schema: tmux.plugins list
├─ Dockerfile RUN: install plugins via TPM
├─ .tmux.conf generation: plugin declarations + TPM init
└─ Plugin validation: GitHub shorthand syntax check

Session persistence (v2.9.0 Phase 4):
├─ tmux-resurrect in default plugin list
├─ Resurrect save-dir: /tools/tmux-resurrect (harness volume)
├─ .tmux.conf: set @resurrect-dir
└─ Volume mount persists across container detach/reattach
```

## Detailed Feature Specifications

### Feature 1: --with-tmux Build Flag (Opt-In Mechanism)

**What:** `aishell build --with-tmux` enables tmux session management. Without flag, no tmux is started.

**Why:** Opt-in respects user preferences. Some users don't want tmux overhead or prefer their own workflow.

**User experience:**
```bash
# No tmux (default)
aishell build --with-claude
aishell claude  # → runs claude directly, no tmux session

# With tmux
aishell build --with-claude --with-tmux
aishell claude  # → starts tmux session "main", claude runs inside
aishell attach claude  # → attach to tmux session
```

**Edge cases:**
- `--with-tmux` without harnesses: Allowed (user may want tmux for shell mode)
- Changing tmux preference requires rebuild (not update): Document clearly
- attach subcommand unavailable without --with-tmux: Help text guidance

**Complexity:** Low (flag parsing, conditional logic)

---

### Feature 2: Mount User's ~/.tmux.conf

**What:** When --with-tmux enabled, mount host's ~/.tmux.conf to container at /home/dev/.tmux.conf.

**Why:** Users have existing tmux configurations (key bindings, colors, settings) and expect them to work.

**Conflict handling with plugin config:**

**Two-stage config (RECOMMENDED)**
1. User's ~/.tmux.conf mounted → /home/dev/.tmux.conf
2. aishell generates /home/dev/.tmux/aishell-plugins.conf (plugin declarations)
3. User's config sources aishell plugins at end:
   ```bash
   # Appended to mounted .tmux.conf via entrypoint
   source-file ~/.tmux/aishell-plugins.conf
   ```

**Edge cases:**
- User has no ~/.tmux.conf: Skip mount, use defaults
- User's config has errors: tmux fails to start (document troubleshooting)
- User declares TPM in their config: aishell still installs TPM, may conflict (document that aishell manages plugins)

**Complexity:** Medium (mount logic, conflict handling)

---

### Feature 3: Declarative Plugin List in config.yaml

**What:** Users declare tmux plugins in .aishell/config.yaml, aishell installs them at build time.

**Why:** Declarative > imperative. Fits aishell's config pattern. Easier than editing .tmux.conf in container.

**YAML schema:**
```yaml
tmux:
  plugins:
    - tmux-plugins/tmux-sensible
    - tmux-plugins/tmux-yank
    - tmux-plugins/tmux-resurrect
    - tmux-plugins/tmux-continuum
    # Version pinning via branch/tag
    - tmux-plugins/tmux-pain-control#v2.0.1
    # Full git URLs supported
    - git@github.com:user/custom-plugin
```

**Transformation to .tmux.conf:**

aishell generates /home/dev/.tmux/aishell-plugins.conf:
```bash
# Generated by aishell from config.yaml
set -g @plugin 'tmux-plugins/tpm'
set -g @plugin 'tmux-plugins/tmux-sensible'
set -g @plugin 'tmux-plugins/tmux-yank'
set -g @plugin 'tmux-plugins/tmux-resurrect'

# Initialize TPM (must be at end)
run '~/.tmux/plugins/tpm/tpm'
```

**Build-time installation:**

Dockerfile template (when --with-tmux enabled):
```dockerfile
FROM aishell:foundation

# Install TPM
RUN git clone https://github.com/tmux-plugins/tpm /home/dev/.tmux/plugins/tpm

# Copy generated plugin config
COPY .aishell/aishell-plugins.conf /home/dev/.tmux/aishell-plugins.conf

# Install plugins non-interactively
ENV TMUX_PLUGIN_MANAGER_PATH=/home/dev/.tmux/plugins
RUN /home/dev/.tmux/plugins/tpm/bin/install_plugins

# Ensure ownership
RUN chown -R dev:dev /home/dev/.tmux
```

**Complexity:** Medium (YAML schema, config generation, build integration)

---

### Feature 4: Plugin-Specific Configuration

**What:** Allow users to configure plugin settings in config.yaml, not just list plugins.

**YAML schema extension:**
```yaml
tmux:
  plugins:
    - tmux-plugins/tmux-sensible
    - tmux-plugins/tmux-resurrect
    - tmux-plugins/tmux-continuum

  # Plugin options
  plugin_options:
    resurrect:
      save_dir: /tools/tmux-resurrect  # Save to harness volume
      capture_pane_contents: true
    continuum:
      auto_save_interval: 15  # Minutes
      auto_restore: true
```

**Transformation:**

Generated .tmux.conf includes options:
```bash
set -g @plugin 'tmux-plugins/tmux-resurrect'
set -g @plugin 'tmux-plugins/tmux-continuum'

# Plugin options from config.yaml
set -g @resurrect-dir '/tools/tmux-resurrect'
set -g @resurrect-capture-pane-contents 'on'
set -g @continuum-save-interval '15'
set -g @continuum-restore 'on'

run '~/.tmux/plugins/tpm/tpm'
```

**Complexity:** Medium (schema extension, option mapping, validation)

---

### Feature 5: tmux-resurrect Session Persistence

**What:** tmux-resurrect plugin saves session state (windows, panes, programs) to harness volume, persisting across detach/reattach.

**Important distinction:**

Persistence scope is **within container lifetime**, not across container restarts. tmux-resurrect saves state so users can:
1. Detach from tmux session (Ctrl-B D)
2. Reattach later with `aishell attach`
3. Session restored with windows/panes intact

But when container stops (docker stop), session state is lost. This is consistent with ephemeral design.

**Configuration:**

Save directory set to harness volume:
```yaml
tmux:
  plugins:
    - tmux-plugins/tmux-resurrect
  plugin_options:
    resurrect:
      save_dir: /tools/tmux-resurrect
```

**Harness volume integration:**

The harness volume (/tools) already persists across container launches for same project:
```
/tools/
  claude/       # Claude Code binary
  opencode/     # OpenCode installation
  tmux-resurrect/  # Session state files
    last          # Symlink to most recent save
    tmux_resurrect_20260201T120000.txt
```

**User workflow:**
```bash
# Start container with work
aishell claude
# ... work in tmux, create windows/panes ...
# Save session: prefix + Ctrl-S (resurrect default)
# Detach from tmux: Ctrl-B D

# Later, reattach
aishell attach claude
# Restore session: prefix + Ctrl-R (resurrect default)
# → Windows and panes restored
```

**Complexity:** Medium (volume integration, plugin config, documentation)

---

## MVP Recommendation

### Phase 1: Opt-In Mechanism (Must Have)
1. `--with-tmux` build flag (default OFF)
2. Build config stores tmux enabled/disabled
3. Conditional entrypoint (tmux OR direct exec)
4. attach subcommand registration conditional

**Rationale:** Foundation for all tmux features. Reverses v2.7-2.8 always-on behavior to opt-in.

### Phase 2: Configuration Mounting (Should Have)
5. Mount user's ~/.tmux.conf (read-only)
6. Two-stage config (user + aishell plugins)
7. Config validation (exists, readable, ownership)

**Rationale:** Users expect their tmux configs to work. Critical for adoption.

### Phase 3: Plugin Management (Should Have)
8. config.yaml schema: tmux.plugins list
9. TPM installation during build
10. Plugin config generation (.tmux/aishell-plugins.conf)
11. Build-time plugin installation (bin/install_plugins)
12. Plugin validation (GitHub shorthand syntax)

**Rationale:** Core value of v2.9.0. Declarative plugins fit aishell pattern.

### Phase 4: Session Persistence (Could Have)
13. tmux-resurrect in default plugin list
14. Resurrect save-dir: /tools/tmux-resurrect
15. Plugin options in config.yaml (resurrect save_dir)
16. Documentation: detach/reattach workflow

**Rationale:** Power user feature. Adds persistence without violating ephemeral design.

### Defer to Post-MVP
- Plugin version pinning (nice-to-have, TPM supports it easily)
- Incremental plugin updates (optimization, rebuild is acceptable)
- tmux-continuum auto-save/restore (advanced, resurrect sufficient)
- Plugin troubleshooting command (debugging tool, manual inspection works)

## Integration with Existing Features

| Existing Feature | Integration Point | Changes Required |
|------------------|-------------------|------------------|
| Build command | Add --with-tmux flag | Flag parsing, build config storage |
| Build config (.build-state.edn) | Store tmux-enabled boolean | Schema extension |
| Dockerfile template | Conditional TPM install, plugin config | Template branching |
| Entrypoint | Conditional tmux session OR direct exec | Logic fork based on TMUX_ENABLED |
| Volume mounts | Add ~/.tmux.conf mount | Run.clj volume list |
| Harness volume | Add tmux-resurrect subdir | No changes (volume auto-expands) |
| config.yaml schema | Add tmux section (plugins, plugin_options) | Schema validation extension |
| attach subcommand | Conditional registration | CLI router checks build config |

**Critical consideration:** --with-tmux is a build-time decision, not runtime. Changing requires rebuild (not update). Document clearly.

## Complexity Assessment

| Feature Category | Implementation Risk | Testing Complexity | Documentation Need |
|------------------|--------------------|--------------------|-------------------|
| Opt-in flag | Low | Low | Medium (migration guide) |
| Config mounting | Medium (gosu permissions) | Medium (user config variations) | Low |
| Plugin list YAML | Low (schema extension) | Medium (validation cases) | High (examples) |
| Plugin config generation | Medium (templating) | Medium (escaping, quoting) | Medium |
| TPM installation | Low (well-documented) | Low (TPM handles it) | Low |
| Plugin options | Medium (mapping YAML→tmux) | High (plugin-specific) | High (plugin docs) |
| Resurrect persistence | Medium (volume path config) | Medium (state file handling) | High (workflow explanation) |

**Overall complexity:** MEDIUM-HIGH. Individual features are low-medium, but the combination (opt-in, config merge, plugin management, persistence) requires careful coordination.

## Migration Path

**For existing users (v2.7-2.8 with always-on tmux):**

v2.9.0 is a BREAKING CHANGE. tmux becomes opt-in, default OFF.

Migration steps:
1. Add `--with-tmux` flag to build commands
   ```bash
   # OLD: aishell build --with-claude
   # NEW: aishell build --with-claude --with-tmux
   ```
2. Update scripts/automation (CI/CD)
3. Document in changelog and migration guide

**Why breaking change is justified:**
- Opt-in better aligns with Unix philosophy (do one thing)
- Users who don't need tmux shouldn't pay the cost
- Foundation (v2.7-2.8) validated tmux-in-Docker works; now making it optional

## Sources

### Tmux Plugin Manager (TPM)
- [GitHub - tmux-plugins/tpm: Tmux Plugin Manager](https://github.com/tmux-plugins/tpm)
- [TIL - Two Tmux Plugin Manager features](https://qmacro.org/blog/posts/2023/11/10/til-two-tmux-plugin-manager-features/)
- [Tmux/plugins/tpm - Gentoo wiki](https://wiki.gentoo.org/wiki/Tmux/plugins/tpm)
- [How to use tmux package manager (TPM)?](https://tmuxai.dev/tmux-package-manager/)
- [Managing Tmux Plugins with Tmux Plugin Manager | FOSS Linux](https://www.fosslinux.com/106799/managing-tmux-plugins-with-tmux-plugin-manager.htm)

### TPM Docker Integration
- [Automatic installation issue and workaround · Issue #105 · tmux-plugins/tpm](https://github.com/tmux-plugins/tpm/issues/105)
- [tpm/docs/automatic_tpm_installation.md at master · tmux-plugins/tpm](https://github.com/tmux-plugins/tpm/blob/master/docs/automatic_tpm_installation.md)
- [How To Install Plugins on TMUX — TPM (TMUX Plugin Manager) | by pachoyan | Medium](https://medium.com/@pachoyan/how-to-install-plugins-on-tmux-tpm-tmux-plugin-manager-5d5b8bd33817)

### tmux-resurrect Session Persistence
- [GitHub - tmux-plugins/tmux-resurrect: Persists tmux environment across system restarts](https://github.com/tmux-plugins/tmux-resurrect)
- [Save and Restore Tmux Sessions across Reboots with Tmux Resurrect — Nick Janetakis](https://nickjanetakis.com/blog/save-and-restore-tmux-sessions-across-reboots-with-tmux-resurrect)
- [Save And Restore Tmux Environments Across Reboots In Linux - OSTechNix](https://ostechnix.com/save-and-restore-tmux-environment/)
- [tmux-resurrect/docs/save_dir.md at master · tmux-plugins/tmux-resurrect](https://github.com/tmux-plugins/tmux-resurrect/blob/master/docs/save_dir.md)

### Configuration Mounting
- [Put All of Your Tmux Configs and Plugins in a .config/tmux Directory — Nick Janetakis](https://nickjanetakis.com/blog/put-all-of-your-tmux-configs-and-plugins-in-a-config-tmux-directory)
- [tmux-config/Dockerfile at master · samoshkin/tmux-config](https://github.com/samoshkin/tmux-config/blob/master/Dockerfile)
- [tmux in demonized docker container · GitHub](https://gist.github.com/ptrj/b54b8cdd34f632028e669a1e71bc03b8)
- [Tmux and Screen | Intuitive documentation for using Docker containerization](https://dockerdocs.org/multiplexers/)

### Opt-In Best Practices
- [Feature Flags Best Practices](https://www.cloudbees.com/blog/feature-flag-best-practices)
- [Everything You Need to Know About Feature Flags: The Essential Guide 2026](https://www.apwide.com/what-are-feature-flags-guide/)
- [The 12 Commandments Of Feature Flags In 2025](https://octopus.com/devops/feature-flags/feature-flag-best-practices/)
- [Features - The Cargo Book](https://doc.rust-lang.org/cargo/reference/features.html)

### Session Persistence Best Practices
- [Using tmux for persistent shell sessions — DKRZ Documentation](https://docs.dkrz.de/blog/2022/tmux.html)
- [Using tmux to create persistent server sessions — The Princeton Handbook for Reproducible Neuroimaging](https://brainhack-princeton.github.io/handbook/content_pages/hack_pages/tmux.html)
- [Using TMUX for persistent sessions - Runpod Documentation](https://docs.runpod.io/tips-and-tricks/tmux)
- [How to use tmux in 2026](https://www.hostinger.com/tutorials/how-to-use-tmux)

All research conducted 2026-02-01, sources verified for currency and relevance.
