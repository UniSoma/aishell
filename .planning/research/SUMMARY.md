# Project Research Summary

**Project:** aishell v2.9.0 - tmux Opt-in & Plugin Support
**Domain:** Docker-based sandbox environment enhancement
**Researched:** 2026-02-01
**Confidence:** HIGH

## Executive Summary

The v2.9.0 milestone extends an existing Docker-based AI coding sandbox to make tmux opt-in and add plugin support with configuration mounting. Research shows this is fundamentally about configuration management and volume architecture, not complex new infrastructure. The foundation already exists (tmux installed, detached containers, attach command) - this milestone adds user control and customization.

The recommended approach leverages the existing harness volume pattern introduced in v2.8.0. Install TPM (Tmux Plugin Manager) and plugins into `/tools/tmux/plugins/` during volume population, mount user's `~/.tmux.conf` for personalization, and optionally persist tmux-resurrect session state to host. This separates stable foundation (tmux binary) from user-configurable tools (plugins), following the established architecture pattern. No new runtime dependencies needed - tmux 3.3a-3, git, and bash already exist in the foundation image.

The critical risk is breaking change migration. Users who built containers in v2.7.0-v2.8.0 (tmux always-on) will encounter silent behavioral changes when rebuilding with v2.9.0 (tmux opt-in, default disabled). State file migration, version detection, and clear attach command validation are essential to prevent cryptic failures. Secondary risks include TPM installation failures in non-interactive Docker environments (mitigated by installing during volume population with retry logic) and plugin path conflicts between host config and container environment (mitigated by symlinking `/tools/tmux/plugins` to `~/.tmux/plugins`).

## Key Findings

### Recommended Stack

v2.9.0 adds plugin management capabilities without new runtime dependencies. All required components already exist in the v2.8.0 foundation image.

**Core technologies:**
- **TPM (Tmux Plugin Manager)**: Plugin installation orchestrator - de facto standard in tmux ecosystem, no versioned releases (master branch stable), ~200KB overhead
- **tmux-resurrect**: Session persistence plugin - saves window/pane layout to volume, enables detach/reattach workflows within container lifetime
- **tmux-continuum**: Auto-save companion - eliminates manual save burden, triggers resurrect save every 15 minutes

**Installation strategy:**
- TPM and plugins installed via `git clone --depth 1` during harness volume population
- Location: `/tools/tmux/plugins/` (follows existing harness volume pattern)
- Non-interactive installation via TPM's `bin/install_plugins` script (official Docker/CI method)
- No version pinning (master branch convention), updates via `aishell update`

**Volume architecture integration:**
```
/tools/
├── npm/           # Existing: npm-installed harnesses
├── bin/           # Existing: curl-installed binaries
└── tmux/          # NEW: Plugin ecosystem
    ├── plugins/   # TPM + user-configured plugins
    └── resurrect/ # Optional: Session state files
```

Mount strategy: `/tools` becomes read-write (was read-only) to allow plugin state persistence. Alternative: separate writable mount for `/tools/tmux/resurrect` while keeping `/tools` read-only, but adds complexity without benefit.

### Expected Features

**Must have (table stakes):**
- `--with-tmux` build flag for opt-in (default: enabled based on v2.7-2.8 behavior)
- Mount user's `~/.tmux.conf` into container (users expect existing configs to work)
- TPM installation in harness volume (de facto standard for plugin management)
- Declarative plugin list in `config.yaml` (fits aishell's configuration pattern)
- Plugin installation at build time (not runtime - reproducibility and speed)
- tmux-resurrect plugin support (core persistence feature users expect)
- Graceful no-tmux mode (when flag not passed, no tmux references)
- Config validation (catch YAML errors before build)

**Should have (differentiators):**
- YAML-based plugin config (declarative > imperative, unique to aishell)
- Automatic TPM initialization (users don't need to know TPM exists)
- Resurrect state in harness volume (reuses existing volume architecture)
- Hybrid config strategy (user's `.tmux.conf` + aishell-generated plugin config)
- Plugin version pinning (reproducibility, TPM supports `@tag` syntax)
- Incremental plugin updates (`aishell update` refreshes plugins)

**Defer (v2+):**
- Plugin marketplace/discovery (users know what plugins they want)
- Automatic plugin updates (breaks reproducibility)
- Plugin conflict resolution (trust TPM's installation order)
- tmux theme customization (subjective, users handle via mounted config)
- Session resurrection across container stops (violates ephemeral design)

### Architecture Approach

v2.9.0 extends seven existing components following established patterns. The architecture leverages content-addressable volumes (plugins share volumes when config identical), separates foundation (stable tmux binary) from harness tools (user-configurable plugins), and maintains backward compatibility through state migration.

**Major components:**
1. **State management** (`state.clj`) - Add `:with-tmux` boolean to schema (default true for backward compat), no migration logic needed (nil defaults to true)
2. **Configuration schema** (`config.clj`) - New `tmux:` section with plugin list and resurrect config, scalar replacement merge strategy (project fully replaces global)
3. **Volume population** (`docker/volume.clj`) - Extend `build-install-commands` to clone TPM and install plugins via non-interactive script, update signatures to accept config parameter
4. **Docker run arguments** (`docker/run.clj`) - Add two mount builders: `build-tmux-config-mount` (read-only ~/.tmux.conf) and `build-resurrect-state-mount` (read-write state directory)
5. **Entrypoint logic** (`docker/templates.clj`) - Symlink `/tools/tmux/plugins` to `~/.tmux/plugins`, conditionally append TPM initialization to mounted config, conditional tmux wrapper based on `DISABLE_TMUX` env var
6. **CLI flag parsing** (`cli.clj`) - Add `--with-tmux`/`--without-tmux` flags to build command
7. **Attach validation** (`attach.clj`) - Check `:with-tmux` in state before attempting attach, error with rebuild guidance if disabled

**Integration strategy:** Volume-based plugin installation (build-time, not runtime), symlink-based plugin discovery (volume plugins available at standard path), append-based config augmentation (preserve user config, add TPM initialization), environment variable signaling (`DISABLE_TMUX` controls entrypoint behavior).

### Critical Pitfalls

1. **Opt-in breaking change for existing users** - v2.7-2.8 users who rebuild without `--with-tmux` will encounter silent `aishell attach` failures. Prevention: state file migration (preserve `:with-tmux true` when upgrading from v2.7-2.8), explicit attach validation (error with rebuild instructions), changelog warnings.

2. **TPM plugin installation failures in non-interactive Docker builds** - Git clone network errors, empty repositories, silent failures. Prevention: install during volume population (not container startup), retry logic with timeout, validate plugin directories exist post-install, fallback to basic tmux if plugins missing.

3. **Host tmux.conf plugin path conflicts** - User's config references `~/.tmux/plugins/` but plugins installed at `/tools/tmux/plugins/`. Prevention: symlink volume path to standard home path, set `TMUX_PLUGIN_MANAGER_PATH` environment variable, document path configuration options.

4. **tmux-resurrect state corruption across container restarts** - State saved to ephemeral container filesystem, permission errors after gosu, corrupted save files. Prevention: mount resurrect directory to host, disable process restoration (layout-only saves), validate permissions in entrypoint, graceful degradation when state corrupt.

5. **Plugin installation order dependencies** - tmux-continuum requires tmux-resurrect loaded first, incorrect order breaks auto-save. Prevention: document plugin order requirements, generate `.tmux.conf` declarations in config.yaml order, validate known dependencies during config parsing.

## Implications for Roadmap

Based on research, suggested phase structure follows dependency order and risk profile. Early phases establish data layer and low-risk mounting, middle phases tackle complex plugin installation and entrypoint logic, final phases add convenience features.

### Phase 1: State Schema & Config Mounting
**Rationale:** Foundation for all tmux features. Low risk, no external dependencies. Establishes opt-in flag and configuration mounting before adding complex plugin logic.
**Delivers:** `--with-tmux` flag parsed, `:with-tmux` stored in state, user's `~/.tmux.conf` mounted read-only into container
**Addresses:** Table stakes features (opt-in flag, config mounting)
**Avoids:** Pitfall #1 (breaking change) via state schema extension

### Phase 2: Plugin Installation in Volume
**Rationale:** Core value proposition. Highest complexity (shell command generation, network operations, TPM integration). Isolated from runtime concerns - failures visible during build, not container startup.
**Delivers:** TPM + plugins installed to `/tools/tmux/plugins/` during harness volume population, validation of plugin installation success
**Uses:** Git (from foundation image), TPM's `bin/install_plugins` script
**Addresses:** Table stakes features (TPM installation, declarative plugin list, build-time installation)
**Avoids:** Pitfall #2 (installation failures) via build-time installation with retry logic

### Phase 3: TPM Initialization in Entrypoint
**Rationale:** Makes installed plugins discoverable to tmux. Requires completed Phase 2 (plugins must exist before symlinking). High risk (file manipulation) but testable in isolation.
**Delivers:** Symlink `/tools/tmux/plugins` → `~/.tmux/plugins`, append TPM initialization to mounted config if not present, conditional tmux execution based on opt-in flag
**Implements:** Entrypoint logic modifications (symlink creation, config augmentation)
**Avoids:** Pitfall #3 (path conflicts) via symlink strategy

### Phase 4: Resurrect State Persistence
**Rationale:** Power user feature, depends on Phase 3 (resurrect plugin must be loaded). Medium risk (persistence verification) but optional functionality.
**Delivers:** Optional mount of resurrect state directory to host, resurrect config options in `config.yaml`, documentation of detach/reattach workflow
**Addresses:** Should-have features (resurrect state in harness volume)
**Avoids:** Pitfall #4 (state corruption) via explicit host mount

### Phase 5: Validation & Migration
**Rationale:** User experience polish, ensures graceful failures. Can be implemented in parallel with earlier phases but tested after integration.
**Delivers:** Attach command validation (errors when tmux disabled), state migration detection (warns when upgrading from always-on versions), comprehensive error messages with rebuild guidance
**Addresses:** Table stakes features (graceful no-tmux mode)
**Avoids:** Pitfall #1 (breaking change) via explicit migration detection and validation

### Phase Ordering Rationale

- **Phase 1 before all others:** State schema and config mounting are prerequisites for plugin installation and runtime behavior. No dependencies, can proceed immediately.
- **Phase 2 before Phase 3:** Plugins must be installed before entrypoint can symlink them. Volume population is testable in isolation (verify directory contents).
- **Phase 3 before Phase 4:** TPM must be initialized for resurrect plugin to function. Entrypoint symlink makes plugins discoverable at standard path.
- **Phase 4 after Phase 3:** Resurrect is one plugin among many, requires TPM infrastructure working. Optional feature, can be deferred if needed.
- **Phase 5 in parallel with others:** Validation logic has no dependencies (reads state, checks conditions). Can be implemented and tested independently.

**Dependency grouping:** Phases 1-2 are infrastructure (data layer + installation), Phase 3 is integration (making installed plugins available), Phases 4-5 are polish (optional features + user experience). Sequential phases have hard dependencies (can't symlink before installing), parallel phases are independent (validation doesn't depend on plugins).

**Pitfall avoidance:** Plugin installation at build time (Phase 2) prevents runtime network failures. Symlink strategy (Phase 3) solves path conflicts. State migration (Phase 5) addresses breaking changes. Architecture naturally mitigates risks through phase ordering.

### Research Flags

Phases with standard patterns (skip research-phase):
- **Phase 1:** Config mounting and flag parsing are well-established aishell patterns (see `docker/run.clj` examples for mount builders)
- **Phase 2:** TPM documentation is comprehensive, `bin/install_plugins` script is official non-interactive method
- **Phase 3:** Bash symlinking and config file manipulation are standard operations
- **Phase 5:** State migration follows existing state.clj patterns

No phases need deeper research. All implementation patterns are documented in official sources (TPM repository, tmux-resurrect docs) and existing aishell codebase provides templates for config handling, volume population, and Docker integration.

## Confidence Assessment

| Area | Confidence | Notes |
|------|------------|-------|
| Stack | HIGH | TPM, tmux-resurrect, and tmux-continuum are official projects with comprehensive docs. Installation methods verified via official "automatic installation" guides. No new runtime dependencies beyond what's in foundation image. |
| Features | HIGH | Feature landscape research covered TPM ecosystem patterns, Docker config mounting practices, and opt-in flag best practices. Table stakes features identified through cross-referencing multiple Docker+tmux integration examples. |
| Architecture | HIGH | Extends existing v2.8.0 harness volume pattern. All integration points clearly defined with file/line references. Volume population, config mounting, and entrypoint logic follow established aishell patterns. |
| Pitfalls | HIGH | Critical pitfalls sourced from GitHub issues (TPM installation failures, resurrect corruption) and backward compatibility research (opt-in breaking changes). Prevention strategies verified via official TPM troubleshooting docs and Docker volume best practices. |

**Overall confidence:** HIGH

Research conducted using official documentation (TPM/resurrect/continuum repositories), Docker best practices guides, and backward compatibility case studies. All critical integration points have existing aishell code patterns to follow. TPM's non-interactive installation method is officially documented for Docker/CI use cases. Pitfall prevention strategies are sourced from upstream issue trackers and resolution documentation.

### Gaps to Address

- **Plugin version pinning syntax:** Research confirms TPM supports `@branch` and `@tag` syntax but didn't verify exact implementation. Address during Phase 2 by testing `git clone --branch` with various formats or defer to post-MVP.
- **Resurrect state corruption detection:** Research identified corruption issues but didn't find official detection tools. Address during Phase 4 by implementing simple validation (file exists, readable, non-zero size) or defer corruption handling to post-MVP.
- **Multiple plugin list merge strategies:** Research didn't find clear user preference for scalar replacement vs list concatenation. Address during Phase 1 by documenting merge behavior in config.yaml and considering alternative based on early feedback.

None of these gaps block implementation. All have reasonable defaults (no version pinning for MVP, basic existence validation, scalar replacement merge) with clear paths to refinement based on usage.

## Sources

### Primary (HIGH confidence)
- [TPM Official Repository](https://github.com/tmux-plugins/tpm) - Installation paths, non-interactive methods, official documentation
- [TPM Automatic Installation](https://github.com/tmux-plugins/tpm/blob/master/docs/automatic_tpm_installation.md) - Docker/CI installation patterns
- [TPM Command-Line Management](https://github.com/tmux-plugins/tpm/blob/master/docs/managing_plugins_via_cmd_line.md) - `bin/install_plugins` usage
- [tmux-resurrect Official Repository](https://github.com/tmux-plugins/tmux-resurrect) - Save directory configuration
- [tmux-resurrect Save Directory Docs](https://github.com/tmux-plugins/tmux-resurrect/blob/master/docs/save_dir.md) - Default paths and configuration
- [tmux-continuum Official Repository](https://github.com/tmux-plugins/tmux-continuum) - Auto-save intervals, resurrect integration
- [Debian Packages: tmux](https://packages.debian.org/bookworm/tmux) - Version verification for foundation image

### Secondary (MEDIUM confidence)
- [Feature Flag Best Practices](https://frontegg.com/blog/feature-flag-best-practices) - Opt-in pattern validation
- [Backwards Compatibility and Breaking Changes](https://github.com/kedro-org/kedro/wiki/Backwards-compatibility-and-breaking-changes) - Migration strategies
- [Unable to install plugins](https://github.com/tmux-plugins/tpm/issues/195) - Network failure troubleshooting
- [Bug: Re-saving corrupts resurrect session file](https://github.com/tmux-plugins/tmux-resurrect/issues/392) - Corruption prevention
- [Put Tmux Configs in .config/tmux Directory](https://nickjanetakis.com/blog/put-all-of-your-tmux-configs-and-plugins-in-a-config-tmux-directory) - XDG path conventions
- [ArcoLinux tmux Plugin Manager Guide](https://arcolinux.com/everything-you-need-to-know-about-tmux-plugins-manager/) - Installation patterns
- [Lorenzo Bettini: Installing TPM with Chezmoi](https://www.lorenzobettini.it/2025/04/installing-the-tmux-plugin-manager-tpm-with-chezmoi/) - Non-interactive installation in automated environments

### Tertiary (LOW confidence)
- Various Docker volume permission guides - General best practices, not tmux-specific
- Feature flag blog posts - General principles, applied to aishell context

---
*Research completed: 2026-02-01*
*Ready for roadmap: yes*
