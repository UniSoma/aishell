# Architecture Integration: tmux Opt-In, Plugin Support, and Resurrect State

**Project:** aishell v2.9.0
**Researched:** 2026-02-01
**Confidence:** HIGH
**Builds on:** ARCHITECTURE-TMUX.md (2026-01-31 baseline research)

## Executive Summary

This document focuses on the v2.9.0 specific requirements: tmux opt-in flag, config mounting, plugin installation, and resurrect state persistence. The tmux foundation (installation, detached containers, attach command) is already designed in ARCHITECTURE-TMUX.md. This document addresses **how users configure and customize tmux** within the existing architecture.

**Key architectural decisions:**
1. **Opt-in pattern**: `--with-tmux` build flag (default: enabled based on existing behavior)
2. **Config mounting**: Auto-mount `~/.tmux.conf` when tmux enabled
3. **Plugin installation**: TPM + plugins installed into harness volume at `/tools/tmux/plugins/`
4. **Resurrect state**: Configurable mount of resurrect state directory

**Integration strategy:** Extend existing components (state.clj, config.clj, docker/volume.clj, docker/run.clj) following established patterns.

## Architecture Context

### Existing Foundation (from ARCHITECTURE-TMUX.md)

**Already implemented/designed:**
- tmux installed in foundation image (templates.clj)
- Containers run in detached mode with tmux wrapper
- `aishell attach` command for reconnecting
- Container naming: `aishell-{hash}` based on project path
- Entrypoint wraps harness commands in tmux: `exec gosu ... tmux new-session -A -s main "$@"`

**What v2.9.0 adds:**
- **User choice**: Opt-in/out via `--with-tmux` flag
- **User config**: Mount user's `~/.tmux.conf`
- **User plugins**: Install plugins from `config.yaml` into harness volume
- **State persistence**: Mount tmux-resurrect state directory

## Component Integration Analysis

### 1. State Management (`aishell.state`)

**Current schema (v2.8.0):**
```clojure
{:with-claude true
 :with-opencode false
 :with-gitleaks true
 :foundation-hash "abc123"
 :harness-volume-hash "def456"
 :harness-volume-name "aishell-harness-def456"}
```

**Extended schema (v2.9.0):**
```clojure
{:with-claude true
 :with-opencode false
 :with-gitleaks true
 :with-tmux true                        ; NEW: Default true (backward compat)
 :foundation-hash "abc123"
 :harness-volume-hash "def456"
 :harness-volume-name "aishell-harness-def456"}
```

**Integration points:**

| Function | Current | Modification |
|----------|---------|--------------|
| `read-state` | Returns map | No change (nil `:with-tmux` handled by consumers) |
| `write-state` | Writes map | No change (accepts new keys) |
| Schema docs (lines 25-40) | Documents boolean flags | Add `:with-tmux true ; boolean (whether tmux enabled)` |

**Backward compatibility:**

Missing `:with-tmux` key → defaults to `true` in consumer code:
```clojure
(get state :with-tmux true)  ; Default to true if missing
```

**Modified files:**
- `src/aishell/state.clj` - Update schema documentation (line 30)

**Build command impact:**
```bash
aishell build --with-claude --with-tmux     # Explicit enable (redundant, default)
aishell build --with-claude --without-tmux  # Explicit disable
aishell build --with-claude                 # Implicit enable (backward compat)
```

---

### 2. Configuration Schema (`aishell.config`)

**New config.yaml section:**
```yaml
# tmux configuration (optional, all fields optional)
tmux:
  # List of tmux plugins to install (github owner/repo format)
  # Installed into harness volume at /tools/tmux/plugins/
  plugins:
    - tmux-plugins/tmux-sensible
    - tmux-plugins/tmux-resurrect
    - tmux-plugins/tmux-continuum

  # Resurrect state persistence
  resurrect:
    # Enable mounting resurrect state from host (default: false)
    enabled: true

    # Host directory for resurrect state (default: ~/.local/share/tmux/resurrect)
    # Supports ~ and $HOME expansion
    state_dir: ~/.local/share/tmux/resurrect
```

**Integration points:**

| Function | Current | Modification |
|----------|---------|--------------|
| `known-keys` (line 11) | `#{:mounts :env :ports ...}` | Add `:tmux` |
| `validate-config` | Validates known keys | Add tmux-specific validation |
| `merge-configs` | Merges global + project | Extend for `:tmux` section |

**New validation function:**
```clojure
(defn validate-tmux-config
  "Validate tmux config structure. Warns on invalid format."
  [tmux-config source-path]
  (when tmux-config
    ;; Validate plugins is a list of strings
    (when-let [plugins (:plugins tmux-config)]
      (when-not (and (sequential? plugins)
                    (every? string? plugins))
        (output/warn (str "Invalid tmux.plugins in " source-path
                         ": expected list of strings"))))

    ;; Validate resurrect config
    (when-let [resurrect (:resurrect tmux-config)]
      (when-not (map? resurrect)
        (output/warn (str "Invalid tmux.resurrect in " source-path
                         ": expected map with 'enabled' and 'state_dir' keys"))))))
```

**Merge strategy for `:tmux` section:**

**Recommendation:** Scalar replacement (project tmux config fully replaces global)

**Rationale:**
- Plugin lists are user-specific preferences, not composable
- User wants either global plugins OR project plugins, rarely both
- Resurrect config is per-user environment, not additive

**Implementation:**
```clojure
;; In merge-configs function (around line 150)
(let [scalar-keys #{:pre_start :gitleaks_freshness_check :tmux}]  ; Add :tmux
  ...)
```

**Alternative (if list concat desired):**

Could use list concatenation merge strategy similar to `:mounts` and `:ports`. Requires discussion with users about expected behavior.

**Modified files:**
- `src/aishell/config.clj`:
  - Add `:tmux` to `known-keys` (line 11)
  - Add `validate-tmux-config` function
  - Call from `validate-config` (line 97)
  - Add `:tmux` to scalar merge keys (line 152)

**Config resolution:**
```clojure
;; Usage in docker/run or docker/volume:
(when-let [tmux-cfg (get config :tmux)]
  (let [plugins (get tmux-cfg :plugins)
        resurrect (get tmux-cfg :resurrect)]
    ...))
```

---

### 3. Docker Run Arguments (`aishell.docker/run`)

**Current mount construction pattern:**

From lines 32-55 in `docker/run.clj`:
```clojure
(defn- build-mount-args [mounts] ...)
(defn- build-env-args [env] ...)
(defn- build-port-args [ports] ...)
```

**New mount functions needed:**

```clojure
(defn- build-tmux-config-mount
  "Build mount args for ~/.tmux.conf if tmux enabled and file exists.
   Mounts read-only to prevent container from modifying host config.

   Returns ['-v' 'host-path:container-path:ro'] or empty vector."
  [state]
  (when (get state :with-tmux true)
    (let [home (util/get-home)
          host-path (str home "/.tmux.conf")
          container-path (str home "/.tmux.conf")]
      (when (fs/exists? host-path)
        ["-v" (str host-path ":" container-path ":ro")]))))

(defn- build-resurrect-state-mount
  "Build mount args for tmux-resurrect state directory.
   Requires both tmux enabled AND resurrect.enabled in config.
   Mounts read-write to allow resurrect to save state.

   Returns ['-v' 'host-dir:container-dir'] or empty vector."
  [state config]
  (when (and (get state :with-tmux true)
             (get-in config [:tmux :resurrect :enabled]))
    (let [state-dir (util/expand-path
                      (or (get-in config [:tmux :resurrect :state_dir])
                          "~/.local/share/tmux/resurrect"))
          container-dir state-dir]  ; Mount at same path
      (when (fs/exists? state-dir)
        ["-v" (str state-dir ":" container-dir)]))))
```

**Integration into `build-docker-args-internal`:**

Insert after harness config mounts (line 233):
```clojure
;; Existing: Harness config mounts (Claude, OpenCode, Codex, Gemini configs)
(into (build-harness-config-mounts))

;; NEW: tmux config mount
(into (or (build-tmux-config-mount state) []))

;; NEW: Resurrect state mount
(into (or (build-resurrect-state-mount state config) []))
```

**Why read-only for .tmux.conf:**
- Config is user-owned, should not be modified by container
- Prevents accidental corruption from plugin installers
- Matches pattern for other config mounts (e.g., gitconfig if mounted)

**Why read-write for resurrect state:**
- Plugin actively writes session snapshots
- Intended use case is persistence across container restarts
- Directory is plugin-specific, not general user config

**Modified files:**
- `src/aishell/docker/run.clj`:
  - Add `build-tmux-config-mount` function (after line 166)
  - Add `build-resurrect-state-mount` function (after tmux-config)
  - Update `build-docker-args-internal` to include both mounts (after line 233)
  - Update function signature to accept `state` parameter (line 202)

**Signature change impact:**

`build-docker-args-internal` needs `state` parameter:
```clojure
;; Current signature:
(defn- build-docker-args-internal
  [{:keys [project-dir image-tag config git-identity ...]}]
  ...)

;; New signature:
(defn- build-docker-args-internal
  [{:keys [project-dir image-tag config git-identity state ...]}]  ; Add state
  ...)
```

All callers (`build-docker-args`, `build-docker-args-for-exec`) must pass `state`.

---

### 4. Plugin Installation (`aishell.docker/volume`)

**Current volume population:**

From `docker/volume.clj` lines 274-329:
- Creates temporary container with harness volume mounted
- Runs `build-install-commands` to install npm packages and OpenCode binary
- Sets world-readable permissions with `chmod -R a+rX /tools`

**Plugin installation strategy:**

Add TPM (Tmux Plugin Manager) installation + plugin cloning to volume population.

**New functions:**

```clojure
(defn- build-tpm-install-command
  "Build shell command for installing TPM and tmux plugins.

   Process:
   1. Clone TPM to /tools/tmux/plugins/tpm
   2. Generate temporary .tmux.conf with plugin declarations
   3. Run TPM's bin/install_plugins script
   4. Clean up temporary config

   Returns: Shell command string or nil if no plugins configured"
  [config]
  (when-let [plugins (get-in config [:tmux :plugins])]
    (when (seq plugins)
      (let [;; Generate plugin declarations for temp config
            plugin-lines (str/join "\\n"
                           (map #(str "set -g @plugin '" % "'") plugins))
            tmp-conf "/tmp/tmux-plugins.conf"]
        (str
          ;; 1. Create plugin directory
          "mkdir -p /tools/tmux/plugins && "

          ;; 2. Clone TPM (depth 1 for speed)
          "git clone --depth 1 https://github.com/tmux-plugins/tpm "
          "/tools/tmux/plugins/tpm && "

          ;; 3. Generate temp config with plugin list + TPM initialization
          "printf '" plugin-lines "\\n"
          "run /tools/tmux/plugins/tpm/tpm' > " tmp-conf " && "

          ;; 4. Set TMUX_PLUGIN_MANAGER_PATH and run installer
          "TMUX_PLUGIN_MANAGER_PATH=/tools/tmux/plugins "
          "/tools/tmux/plugins/tpm/bin/install_plugins " tmp-conf " && "

          ;; 5. Cleanup temp config
          "rm " tmp-conf)))))

(defn- should-install-plugins?
  "Check if tmux plugins should be installed.
   Requires: tmux enabled in state AND plugins configured in config"
  [state config]
  (and (get state :with-tmux true)
       (seq (get-in config [:tmux :plugins]))))
```

**Extended `build-install-commands`:**

Current signature (line 212):
```clojure
(defn build-install-commands [state] ...)
```

New signature:
```clojure
(defn build-install-commands
  "Build shell command string for installing harness tools AND tmux plugins.

   Extended from v2.8.0 to include optional tmux plugin installation."
  [state config]  ; Add config parameter
  (let [;; Existing harness installation (npm + opencode)
        packages (keep ...)  ; existing code
        npm-install (when (seq packages) ...)  ; existing code
        opencode-install (build-opencode-install-command state)

        ;; NEW: tmux plugin installation
        tpm-install (when (should-install-plugins? state config)
                      (build-tpm-install-command config))]

    ;; Build complete command string, concatenating all non-nil commands
    (str "export NPM_CONFIG_PREFIX=/tools/npm"
         (when npm-install (str " && " npm-install))
         (when opencode-install (str " && " opencode-install))
         (when tpm-install (str " && " tpm-install))
         " && chmod -R a+rX /tools")))
```

**Updated `populate-volume` signature:**

Current signature (line 299):
```clojure
(defn populate-volume [volume-name state & [opts]] ...)
```

New signature:
```clojure
(defn populate-volume
  "Install harness tools AND tmux plugins into Docker volume.

   Extended from v2.8.0 to support tmux plugin installation."
  [volume-name state config & [opts]]  ; Add config parameter
  (let [install-commands (build-install-commands state config)  ; Pass config
        verbose? (:verbose opts)
        cmd ["docker" "run" "--rm"
             "-v" (str volume-name ":/tools")
             "--entrypoint" ""
             build/foundation-image-tag
             "sh" "-c" install-commands]]
    ...))
```

**Caller impact:**

All calls to `populate-volume` must pass `config`:
- In `cli.clj` update command
- In `run.clj` container launch flow

**Volume structure after plugin installation:**

```
/tools/
├── npm/                          # Existing: npm global packages
│   ├── bin/
│   │   ├── claude
│   │   ├── codex
│   │   └── gemini
│   └── lib/node_modules/
├── bin/                          # Existing: Go binaries
│   └── opencode
└── tmux/                         # NEW: tmux plugins
    └── plugins/
        ├── tpm/                  # Plugin manager
        │   ├── bin/install_plugins
        │   ├── tpm
        │   └── ...
        ├── tmux-sensible/
        ├── tmux-resurrect/
        └── tmux-continuum/
```

**Why install in harness volume, not in foundation image:**

| Consideration | Harness Volume | Foundation Image |
|---------------|----------------|------------------|
| Update frequency | Per-project config change | Dockerfile rebuild |
| User customization | Per-project via config.yaml | Global only |
| Cache efficiency | Volume repopulation (~30s) | Image rebuild (~5min) |
| Sharing | Shared across projects with same config | Shared globally |

**Recommendation:** Harness volume (implemented above)

**Modified files:**
- `src/aishell/docker/volume.clj`:
  - Add `build-tpm-install-command` function
  - Add `should-install-plugins?` predicate
  - Update `build-install-commands` to accept config, add TPM install
  - Update `populate-volume` to accept config, pass through
  - Update all callers of `populate-volume`

---

### 5. Entrypoint Modifications (`aishell.docker/templates`)

**Current entrypoint final lines (lines 203-208):**
```bash
# Execute command as the user via gosu, auto-start in tmux session
exec gosu "$USER_ID:$GROUP_ID" tmux new-session -A -s main -c "$PWD" "$@"
```

**Required changes:**

1. **Conditional tmux execution** (when `--without-tmux` used)
2. **TPM initialization** (symlink volume plugins to user home)
3. **Plugin availability** (ensure tmux can find and load plugins)

**New entrypoint logic:**

```bash
# NEW: Check if TPM plugins installed in harness volume
if [ -d "/tools/tmux/plugins/tpm" ] && [ "$DISABLE_TMUX" != "1" ]; then
    # Symlink volume plugins to user home for tmux to find
    mkdir -p "$HOME/.tmux"
    if [ ! -e "$HOME/.tmux/plugins" ]; then
        ln -s /tools/tmux/plugins "$HOME/.tmux/plugins"
    fi

    # If user has .tmux.conf mounted, ensure it sources TPM
    # TPM auto-installs plugins when tmux starts if 'run .../tpm' present
    if [ -f "$HOME/.tmux.conf" ]; then
        # Check if TPM run command already present
        if ! grep -q "run.*tmux/plugins/tpm/tpm" "$HOME/.tmux.conf"; then
            # Append TPM initialization (non-destructive)
            echo "" >> "$HOME/.tmux.conf"
            echo "# TPM initialization (added by aishell entrypoint)" >> "$HOME/.tmux.conf"
            echo "run '/tools/tmux/plugins/tpm/tpm'" >> "$HOME/.tmux.conf"
        fi
    fi
fi

# Execute command - tmux wrapper or direct
if [ "$DISABLE_TMUX" = "1" ]; then
    # Direct execution without tmux wrapper
    exec gosu "$USER_ID:$GROUP_ID" "$@"
else
    # Execute in tmux session (existing behavior)
    exec gosu "$USER_ID:$GROUP_ID" tmux new-session -A -s main -c "$PWD" "$@"
fi
```

**Environment variable signal:**

`DISABLE_TMUX=1` passed via docker run when `--without-tmux` flag used.

**Why symlink instead of copy:**

| Approach | Pros | Cons |
|----------|------|------|
| Symlink | Instant, no disk usage, single source | Requires valid volume mount |
| Copy | Works without volume | Slow, duplicates data, stale on update |

**Why append to .tmux.conf instead of separate file:**

TPM's `run` command must be in .tmux.conf (or file sourced by it). Users mount their existing .tmux.conf, so we ensure it has TPM initialization.

**Risk mitigation:**

Grep check prevents duplicate injection across container restarts.

**Plugin loading sequence:**

1. Container starts
2. Entrypoint creates symlink: `~/.tmux/plugins` → `/tools/tmux/plugins`
3. Entrypoint appends `run '.../tpm/tpm'` to .tmux.conf (if not present)
4. tmux starts
5. tmux sources .tmux.conf
6. TPM's `run` command executes, loads plugins from `~/.tmux/plugins/`

**Modified files:**
- `src/aishell/docker/templates.clj`:
  - Update `entrypoint-script` string (lines 104-209)
  - Add TPM symlink logic (before final exec)
  - Add conditional tmux execution (replace current exec line)

**No Dockerfile changes needed:** tmux already installed (per ARCHITECTURE-TMUX.md)

---

### 6. CLI Command Updates (`aishell.cli`)

**Build command flags:**

```clojure
;; Current build flags (example):
--with-claude              Install Claude Code
--without-gitleaks        Skip Gitleaks installation

;; NEW flags:
--with-tmux               Enable tmux integration (default: enabled)
--without-tmux            Disable tmux integration
```

**Flag parsing:**

```clojure
;; In handle-build function:
(let [opts (cli/parse-opts args build-spec)
      with-tmux? (not (:without-tmux opts))  ; Default true
      ...]
  ;; Pass to build logic
  (build/build-foundation-image {...})
  (state/write-state (assoc state :with-tmux with-tmux?)))
```

**Runtime behavior:**

`--without-tmux` at build time affects:
1. State written to `state.edn`
2. Entrypoint behavior (`DISABLE_TMUX` env var)
3. Attach command validation (error if tmux disabled)

**No runtime flag needed:** tmux enabled/disabled is build-time decision, stored in state.

**Modified files:**
- `src/aishell/cli.clj`:
  - Add `--with-tmux` / `--without-tmux` to build-spec
  - Update help text to document flags
  - Pass flag value through to build and state write

---

### 7. Attach Command Compatibility (`aishell.attach`)

**Current attach.clj implementation:**

Already exists (from ARCHITECTURE-TMUX.md), performs:
1. TTY validation
2. Container existence check
3. Container running check
4. tmux session existence check
5. `docker exec -it ... tmux attach-session`

**Required addition:**

Validate tmux is available before attempting attach.

**New validation function:**

```clojure
(defn- validate-tmux-available!
  "Validate that tmux is enabled for this project.
   Exits with error if container was built with --without-tmux."
  [state]
  (when-not (get state :with-tmux true)
    (output/error (str "Container was built without tmux support.\n\n"
                      "The 'attach' command requires tmux.\n"
                      "Rebuild with: aishell build --with-claude"))))
```

**Integration into attach flow:**

```clojure
(defn attach-to-session
  [name session]
  (let [project-dir (System/getProperty "user.dir")
        container-name (naming/container-name project-dir name)
        state (state/read-state)]  ; NEW: Read state

    ;; Run all validations
    (validate-tty!)
    (validate-tmux-available! state)  ; NEW: Check tmux enabled
    (validate-container-state! container-name name)
    (validate-session-exists! container-name session name)

    ;; ... existing attach logic ...
    ))
```

**Error messaging:**

```
$ aishell attach
Error: Container was built without tmux support.

The 'attach' command requires tmux.
Rebuild with: aishell build --with-claude
```

**Modified files:**
- `src/aishell/attach.clj`:
  - Add `validate-tmux-available!` function
  - Add state read to `attach-to-session`
  - Call validation before container checks

---

## Data Flow: Plugin Installation

```
User creates .aishell/config.yaml:
tmux:
  plugins:
    - tmux-plugins/tmux-resurrect
        ↓
User runs: aishell build --with-claude
        ↓
┌─────────────────────────────────────┐
│ 1. Build foundation image           │
│    (tmux already installed)         │
└──────────┬──────────────────────────┘
           ↓
┌─────────────────────────────────────┐
│ 2. Compute harness volume hash      │
│    (unchanged: based on harnesses)  │
└──────────┬──────────────────────────┘
           ↓
┌─────────────────────────────────────┐
│ 3. Load config.yaml                 │
│    Parse tmux.plugins section       │
└──────────┬──────────────────────────┘
           ↓
┌─────────────────────────────────────┐
│ 4. Populate harness volume          │
│    docker run --rm                  │
│      -v aishell-harness-X:/tools    │
│      aishell:foundation             │
│      sh -c "..."                    │
└──────────┬──────────────────────────┘
           ↓
┌─────────────────────────────────────┐
│ Inside temporary container:         │
│                                     │
│ export NPM_CONFIG_PREFIX=/tools/npm │
│ npm install -g @anthropic-ai/...    │
│                                     │
│ mkdir -p /tools/tmux/plugins        │
│ git clone .../tpm /tools/tmux/plugins/tpm │
│                                     │
│ printf 'set -g @plugin ...' > /tmp/tmux.conf │
│ TMUX_PLUGIN_MANAGER_PATH=/tools/tmux/plugins \ │
│   /tools/tmux/plugins/tpm/bin/install_plugins │
│                                     │
│ chmod -R a+rX /tools                │
└──────────┬──────────────────────────┘
           ↓
┌─────────────────────────────────────┐
│ Volume now contains:                │
│ /tools/npm/...                      │
│ /tools/bin/opencode                 │
│ /tools/tmux/plugins/tpm             │
│ /tools/tmux/plugins/tmux-resurrect  │
└─────────────────────────────────────┘
```

## Data Flow: Runtime with Plugins

```
User runs: aishell claude
        ↓
┌─────────────────────────────────────┐
│ 1. Read state.edn                   │
│    :with-tmux true                  │
└──────────┬──────────────────────────┘
           ↓
┌─────────────────────────────────────┐
│ 2. Load config.yaml                 │
│    tmux.resurrect.enabled: true     │
└──────────┬──────────────────────────┘
           ↓
┌─────────────────────────────────────┐
│ 3. Build docker run command         │
│    -v ~/.tmux.conf:~/.tmux.conf:ro  │
│    -v ~/.local/share/tmux/resurrect:... │
│    -v aishell-harness-X:/tools      │
└──────────┬──────────────────────────┘
           ↓
┌─────────────────────────────────────┐
│ 4. Container starts                 │
│    entrypoint.sh executes           │
└──────────┬──────────────────────────┘
           ↓
┌─────────────────────────────────────┐
│ 5. Entrypoint logic                 │
│                                     │
│ if [ -d /tools/tmux/plugins/tpm ]; then │
│   ln -s /tools/tmux/plugins \       │
│         $HOME/.tmux/plugins         │
│                                     │
│   echo "run '.../tpm/tpm'" >> \     │
│        $HOME/.tmux.conf             │
│ fi                                  │
│                                     │
│ exec gosu ... tmux new-session ... claude │
└──────────┬──────────────────────────┘
           ↓
┌─────────────────────────────────────┐
│ 6. tmux starts                      │
│    - Sources ~/.tmux.conf (mounted) │
│    - Runs TPM (appended to config)  │
│    - TPM loads plugins from ~/.tmux/plugins/ │
│    - Plugins execute (resurrect active) │
└─────────────────────────────────────┘
```

## Component Modification Summary

### Files to Modify

| File | Changes | Lines Changed | Complexity |
|------|---------|---------------|------------|
| `src/aishell/state.clj` | Add `:with-tmux` to schema docs | ~1 | Low |
| `src/aishell/config.clj` | Add `:tmux` validation, merge strategy | ~30 | Medium |
| `src/aishell/docker/run.clj` | Add tmux config mount, resurrect mount | ~40 | Medium |
| `src/aishell/docker/volume.clj` | Add TPM install, update signatures | ~80 | High |
| `src/aishell/docker/templates.clj` | Add TPM symlink, conditional tmux | ~30 | High |
| `src/aishell/cli.clj` | Add `--with/without-tmux` flags | ~10 | Low |
| `src/aishell/attach.clj` | Add tmux availability check | ~15 | Low |

**Total:** ~206 lines changed/added

**Risk areas:**
- TPM installation shell command generation (complex bash, network dependent)
- Entrypoint .tmux.conf injection (must not corrupt user config)
- Volume signature changes (all callers must be updated)

---

## Build Order Recommendation

### Phase 1: State and Config Schema
**Goal:** Establish data layer

**Changes:**
- `state.clj`: Add `:with-tmux` to schema docs
- `config.clj`: Add `:tmux` validation, merge strategy
- `cli.clj`: Add `--with/without-tmux` flags

**Testing:**
- Parse config.yaml with tmux section
- Validate plugin list format
- Test build with `--without-tmux` flag

**Dependencies:** None
**Risk:** Low

---

### Phase 2: Config Mounting
**Goal:** Mount user's .tmux.conf

**Changes:**
- `docker/run.clj`: Add `build-tmux-config-mount` function
- Integrate into `build-docker-args-internal`

**Testing:**
- Create ~/.tmux.conf with test content
- `aishell claude`
- Verify file mounted in container: `docker exec ... cat ~/.tmux.conf`

**Dependencies:** Phase 1 (state schema)
**Risk:** Low

---

### Phase 3: Plugin Installation
**Goal:** Install TPM + plugins into harness volume

**Changes:**
- `docker/volume.clj`: Add TPM install functions
- Update `build-install-commands` signature
- Update `populate-volume` signature
- Update all callers

**Testing:**
- Add plugins to config.yaml
- `aishell update` (repopulate volume)
- Verify plugins in volume: `docker run -v ... ls /tools/tmux/plugins/`

**Dependencies:** Phase 1 (config schema)
**Risk:** High (shell command complexity, network)

---

### Phase 4: TPM Initialization in Entrypoint
**Goal:** Make plugins available to tmux

**Changes:**
- `templates.clj`: Add symlink logic
- Add .tmux.conf injection logic

**Testing:**
- `aishell claude`
- Verify symlink: `docker exec ... ls -la ~/.tmux/plugins`
- Verify TPM run appended: `docker exec ... grep tpm ~/.tmux.conf`
- Verify plugins loaded: `docker exec ... tmux show-environment`

**Dependencies:** Phase 3 (plugins installed)
**Risk:** High (file manipulation, config injection)

---

### Phase 5: Resurrect State Mounting
**Goal:** Persist tmux sessions across restarts

**Changes:**
- `docker/run.clj`: Add `build-resurrect-state-mount` function
- Integrate into `build-docker-args-internal`

**Testing:**
- Enable resurrect in config.yaml
- `aishell claude`, create tmux windows
- Save session (resurrect plugin: `prefix + Ctrl-s`)
- Exit container, restart
- Verify session restored

**Dependencies:** Phase 4 (plugins working)
**Risk:** Medium (persistence verification)

---

### Phase 6: Attach Command Validation
**Goal:** Graceful error when tmux disabled

**Changes:**
- `attach.clj`: Add `validate-tmux-available!`

**Testing:**
- `aishell build --with-claude --without-tmux`
- `aishell claude` (direct exec, no tmux)
- `aishell attach` → Error with rebuild guidance

**Dependencies:** Phase 1 (state schema)
**Risk:** Low

---

### Phase 7: Integration Testing
**Goal:** End-to-end workflows

**Test scenarios:**
1. Fresh build with plugins → verify loaded
2. Config change (add plugin) → update → verify new plugin
3. Resurrect save/restore cycle
4. `--without-tmux` build → direct exec → attach fails
5. Mount collision handling (user's .tmux.conf already has TPM)

**Dependencies:** All previous phases
**Risk:** N/A (testing)

---

## State.edn Schema Evolution

**Before (v2.8.0):**
```clojure
{:with-claude true
 :with-gitleaks true
 :foundation-hash "abc123"
 :harness-volume-hash "def456"}
```

**After (v2.9.0):**
```clojure
{:with-claude true
 :with-gitleaks true
 :with-tmux true                    ; NEW
 :foundation-hash "abc123"
 :harness-volume-hash "def456"}
```

**Migration:** None required (nil → defaults to `true`)

---

## Config.yaml Schema Additions

**New section:**
```yaml
tmux:
  plugins:
    - tmux-plugins/tmux-sensible
    - tmux-plugins/tmux-resurrect

  resurrect:
    enabled: true
    state_dir: ~/.local/share/tmux/resurrect
```

**Validation:**
- `tmux`: optional map
- `tmux.plugins`: optional list of strings
- `tmux.resurrect`: optional map
- `tmux.resurrect.enabled`: optional boolean (default: false)
- `tmux.resurrect.state_dir`: optional string (default: ~/.local/share/tmux/resurrect)

**Merge strategy:** Scalar replacement (project tmux section replaces global entirely)

---

## Open Questions for Roadmap

### Q1: Plugin version pinning?

**Current design:** TPM clones HEAD of master/main

**Options:**
- A) Always use latest (current design)
- B) Support `owner/repo@branch` syntax
- C) Support `owner/repo@commit-sha` syntax

**Recommendation:** A for MVP, consider B/C in future
**Reasoning:** TPM's default behavior, simplest implementation

---

### Q2: Plugin list merge strategy?

**Current design:** Scalar replacement (project fully replaces global)

**Alternative:** List concatenation (global + project plugins)

**Recommendation:** Scalar replacement for MVP
**Reasoning:** Plugin lists are typically user-specific, not composable

---

### Q3: Resurrect auto-save integration?

**Use case:** Auto-save sessions periodically without manual `prefix + Ctrl-s`

**Implementation:** tmux-continuum plugin (auto-save every 15 minutes)

**Question:** Should config.yaml have `resurrect.auto_save: true` sugar for adding continuum?

**Recommendation:** No, users add continuum to plugin list explicitly
**Reasoning:** Explicit is better than implicit, users understand plugin list

---

### Q4: Multiple resurrect state directories?

**Use case:** User wants per-project resurrect state

**Options:**
- A) Single global state_dir (current design)
- B) Auto-namespace by project hash
- C) Allow per-project state_dir in config.yaml

**Recommendation:** A (single global) for MVP
**Reasoning:** Resurrect typically user-preference, not project-specific

---

## Risks and Mitigations

### Risk 1: TPM installation network failure

**Probability:** Medium (git clone over network)
**Impact:** High (plugins unavailable)

**Mitigations:**
- Wrap git clone in error handling
- Log full error output for debugging
- Don't fail entire volume population (partial success)
- Document that plugins require network access

---

### Risk 2: .tmux.conf injection corrupts user config

**Probability:** Low (grep check prevents duplicates)
**Impact:** Medium (tmux fails to start)

**Mitigations:**
- Only append if TPM run not already present
- Add comment marker for identification
- Append to end (doesn't break syntax)
- Test with various .tmux.conf formats

---

### Risk 3: Plugin installation slowness

**Probability:** High (git clones multiple repos)
**Impact:** Low (one-time per volume creation)

**Mitigations:**
- Use `--depth 1` for shallow clones
- Show progress during population
- Cache plugin repos in volume (persist across builds)

---

### Risk 4: Resurrect state directory permissions

**Probability:** Low (user's home directory)
**Impact:** Low (warning shown, no failure)

**Mitigations:**
- Check directory exists before mounting
- Warn if missing (don't fail)
- Document expected permissions in error

---

## Alternative Architectures Considered

### Alternative 1: Plugins in foundation image

**Approach:** Install plugins during `docker build` of foundation image

**Rejected because:**
- Plugins are user-specific, not foundation infrastructure
- Would require foundation rebuild for plugin changes
- Breaks cache efficiency
- Can't vary plugins per-project

---

### Alternative 2: Separate plugin volume

**Approach:** Create `aishell-tmux-{hash}` volume separate from harness volume

**Rejected because:**
- Unnecessary complexity (two volumes to manage)
- /tools is already the pattern for mounted tools
- No isolation benefit (plugins are read-only)

---

### Alternative 3: Runtime plugin installation

**Approach:** Entrypoint detects missing plugins, installs on-demand

**Rejected because:**
- Slow container startup (git clones at runtime)
- Network dependency at runtime
- Harder to troubleshoot failures
- Inconsistent state across restarts

---

## Sources

Research sources for architectural decisions:

- [TPM Installation Directory](https://github.com/tmux-plugins/tpm/blob/master/docs/changing_plugins_install_dir.md)
- [TPM Automatic Installation](https://github.com/tmux-plugins/tpm/blob/master/docs/automatic_tpm_installation.md)
- [tmux-resurrect Repository](https://github.com/tmux-plugins/tmux-resurrect)
- [tmux-resurrect XDG State Home](https://github.com/tmux-plugins/tmux-resurrect/issues/542)
- [XDG Base Directory Specification](https://specifications.freedesktop.org/basedir-spec/basedir-spec-latest.html)
- [Docker Configuration in tmux](https://nickjanetakis.com/blog/put-all-of-your-tmux-configs-and-plugins-in-a-config-tmux-directory)
- [tmux Plugin Manager TIL](https://qmacro.org/blog/posts/2023/11/10/til-two-tmux-plugin-manager-features/)
