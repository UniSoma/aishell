# Current Implementation & Migration Path Research: Decoupling Harness Tools from Docker Extensions

## Research Parameters

**Topic**: Decoupling harness tool installation from heavy per-project Docker extensions in the "aishell" project.
**Perspective**: Current Implementation & Migration Path
**Focus**: Analyze existing aishell codebase to assess implementation complexity for each approach
**Date**: 2026-01-31
**Session**: 20260131-1807-decoupling-harness-tools-from-docker-extensions

## Key Findings

- **Approach A (Layer Inversion) requires extensive changes**: Template changes in 3 files (templates.clj, build.clj, extension.clj), new orchestration logic in run.clj, and modified cache invalidation throughout. Estimated 8-10 files modified with ~500 lines changed.
- **Approach B (Volume-Mounted Tools) has minimal code footprint**: Only requires changes to run.clj for volume mounting and a new build module for harness image construction. Extension logic remains untouched. Estimated 3-4 files modified with ~200 lines added.
- **Approach C (Hybrid) offers best balance for this codebase**: Foundation base split is straightforward (templates.clj), volume mount logic is isolated (run.clj), extension.clj needs minor label tracking updates. Estimated 4-5 files modified with ~300 lines changed.
- **Existing cache invalidation is highly coupled to base image ID**: Extension rebuild logic (extension.clj:91-95) uses `base-image-id-label` to track base changes. Any approach that splits the base image must update this tracking mechanism to avoid unnecessary rebuilds or stale dependencies.
- **The codebase already uses volume mounting patterns**: docker/run.clj (lines 132-147) demonstrates volume mounting for harness configs. Extending this pattern for tool binaries is architecturally consistent.

## Analysis

### Current Architecture: Build-Extension-Run Flow

The aishell codebase follows a three-layer architecture:

**Layer 1: Base Image Build** (`docker/build.clj`, `docker/templates.clj`)
- Dockerfile template is embedded as a string in `templates.clj` (line 7-149)
- Base image contains: Debian bookworm-slim + system tools + Node.js (multi-stage copy from node:24-bookworm-slim) + harness tools (npm packages: Claude, Codex, Gemini; native binaries: OpenCode)
- Cache invalidation: Dockerfile content hash stored in `dockerfile-hash-label` (build.clj:16)
- Harness version tracking: `version-changed?` function (build.clj:38-61) compares requested versions against state.edn
- **Critical rebuild trigger**: Any harness version change forces complete base rebuild with `--no-cache` (build.clj:153-154)

**Layer 2: Extension Image Build** (`docker/extension.clj`)
- Projects can provide `.aishell/Dockerfile` that starts `FROM aishell:base`
- Extension hash computed from Dockerfile content (extension.clj:56-65)
- Rebuild triggers: (1) base image ID changes (extension.clj:91-95), (2) extension Dockerfile content changes (extension.clj:99-102)
- Tag format: `aishell:ext-{12-char-hash}` where hash is SHA-256 of project directory path (extension.clj:46-54)
- **Problem**: Base image ID changes whenever harness tools are updated, invalidating ALL extension images across ALL projects

**Layer 3: Container Run** (`run.clj`, `docker/run.clj`)
- Image resolution: If `.aishell/Dockerfile` exists, use extension image; else use base (run.clj:41-57)
- Extension auto-build: `needs-extended-rebuild?` called before run, builds if needed (run.clj:48-54)
- Docker args constructed by `build-docker-args` (run.clj:158-165, docker/run.clj:254-277)
- Volume mounts: Project dir, harness configs (~/.claude, ~/.config/opencode, etc.), git identity via env vars
- Entrypoint: Dynamic user creation with gosu, auto-start in tmux session (templates.clj:151-246)

### Cache Invalidation Dependencies

The rebuild logic creates a dependency chain:

```
Harness version change → Base Dockerfile hash change → Base image rebuild → Base image ID change → All extension images invalidated
```

This is the core problem. Extension images track `base-image-id-label` (extension.clj:15, 91) to detect base changes. When a harness tool version bumps (e.g., Claude 2.0.22 → 2.0.23), the base image rebuilds with a new ID, and every project's extension becomes stale.

### Files Requiring Modification by Approach

#### Approach A: Layer Inversion (Foundation → Extension → Harness)

**Conceptual model**: Foundation image (Debian + system tools + Node.js) → Extension image (FROM foundation + project tools) → Auto-generated harness layer (FROM extension + harness tools)

**Files to modify**:
1. **`docker/templates.clj`**: Split `base-dockerfile` into `foundation-dockerfile` (lines 8-76 minus harness installs) and create harness template (lines 96-138 extracted). ~150 lines changed.
2. **`docker/build.clj`**: New `build-foundation-image` function, extract harness installation to separate builder. Update cache logic to track foundation hash separately from harness hash. ~200 lines changed.
3. **`docker/extension.clj`**: Change `FROM aishell:base` to `FROM aishell:foundation`. Update rebuild tracking to use foundation image ID. ~50 lines changed.
4. **`run.clj`**: New orchestration logic for harness layer generation. After extension resolution (line 44-56), auto-build harness layer on top. Image tag becomes `aishell:harness-{ext-tag}-{harness-hash}`. ~100 lines added.
5. **`docker/hash.clj`**: Add harness version hash computation (combine all enabled harness flags + versions). ~20 lines added.
6. **`cli.clj`**: Update `handle-build` to build foundation instead of base. ~10 lines changed.
7. **`state.clj`**: Add foundation-image-id to state schema. ~5 lines changed.
8. **`check.clj`**: Update image existence checks to verify foundation + harness layer. ~30 lines changed.

**Total estimate**: 8 files modified, ~500-600 lines changed/added.

**Complexity**: High. Requires complete rearchitecture of the build pipeline. The harness layer auto-generation is complex because it must happen transparently at runtime, but Docker doesn't support "build on run" natively — you'd need to detect staleness, run `docker build`, then continue to `docker run`. This breaks the current model where `run.clj` assumes images are ready after `resolve-image-tag` returns.

**Breakage risk**: Extension Dockerfiles currently say `FROM aishell:base`. Changing this to `FROM aishell:foundation` breaks existing user configs unless migration logic is added.

#### Approach B: Volume-Mounted Tools

**Conceptual model**: Base image (Debian + system tools + Node.js + project-specific tools via extension) + separate harness image (FROM scratch or alpine with just binaries) → Extract harness binaries to named volume → Mount volume at runtime

**Files to modify**:
1. **`docker/templates.clj`**: Remove harness installation RUN commands (lines 96-138), keep Node.js. Create new `harness-dockerfile` template that installs tools into /opt/harness. ~100 lines changed.
2. **`docker/build.clj`**: Keep existing base build logic (it becomes "foundation-like" naturally by removing harness installs). Add new `build-harness-volume` function to build harness image and extract to named volume. ~150 lines added.
3. **`docker/run.clj`**: Add volume mount for harness tools. Modify `build-docker-args` to include `-v aishell-harness:/opt/harness:ro` and update PATH: `-e PATH=/opt/harness/bin:$PATH`. ~30 lines added.
4. **`run.clj`**: Add pre-flight check before container run: ensure harness volume exists and is up-to-date (compare volume creation timestamp or label hash against desired harness versions). ~50 lines added.
5. **`cli.clj`**: Update `handle-build` to call harness volume builder. ~20 lines changed.
6. **`state.clj`**: Add harness-volume-hash to state schema. ~5 lines changed.

**Total estimate**: 4 files modified (templates, build, run, docker/run), 2 new namespaces optional (docker/harness-volume.clj), ~300-350 lines added.

**Complexity**: Moderate. Volume extraction is non-standard Docker workflow. Need to:
- Build harness image with binaries at known paths (/opt/harness/bin/{claude,opencode,codex,gemini})
- Create named volume (`docker volume create aishell-harness`)
- Extract binaries: `docker run --rm -v aishell-harness:/dst harness-img cp -r /opt/harness/* /dst/`
- Mount volume at runtime: `-v aishell-harness:/opt/harness:ro`

**Breakage risk**: Low for extensions (they don't change). Medium for harness tool paths if extraction fails or PATH isn't set correctly. OpenCode installs to `/root/.opencode/bin/opencode` and is copied to `/usr/local/bin` (templates.clj:116) — this path handling needs careful mapping to /opt/harness structure.

#### Approach C: Hybrid (Foundation Base + Volume-Mounted Harness)

**Conceptual model**: Foundation base image (Debian + system tools + Node.js, versioned rarely) → Extension image (FROM foundation + project tools) → Harness tools in named volume (updated independently)

**Files to modify**:
1. **`docker/templates.clj`**: Rename `base-dockerfile` to `foundation-dockerfile`, remove harness installs (lines 96-138). Create `harness-dockerfile` template. ~100 lines changed.
2. **`docker/build.clj`**: Rename `build-base-image` to `build-foundation-image`, remove harness version tracking from `version-changed?`. Add `build-harness-volume` function (similar to Approach B). ~180 lines changed.
3. **`docker/extension.clj`**: Update `FROM aishell:base` references to `FROM aishell:foundation` in comments/docs. Change `base-image-id-label` to `foundation-image-id-label` for clarity. No logic changes. ~20 lines changed (mostly renames).
4. **`docker/run.clj`**: Add harness volume mount and PATH update (same as Approach B). ~30 lines added.
5. **`run.clj`**: Add harness volume staleness check (compare harness-volume-hash in state against current config). ~50 lines added.
6. **`cli.clj`**: Update `handle-build` to build foundation + harness volume. ~25 lines changed.
7. **`state.clj`**: Rename base-image-id to foundation-image-id, add harness-volume-hash. ~10 lines changed.

**Total estimate**: 5 files modified, ~350-400 lines changed.

**Complexity**: Moderate. Combines the clean separation of Approach A (foundation vs harness) with the implementation simplicity of Approach B (volume mounting). The foundation image becomes stable (only rebuilds when system dependencies change), extension rebuild logic stays mostly intact (just rename base → foundation), and harness updates don't cascade.

**Breakage risk**: Low. Extensions need `FROM aishell:foundation` instead of `FROM aishell:base`, but this can be handled with a migration warning + auto-detection (check if .aishell/Dockerfile exists and contains `FROM aishell:base`, print migration instructions).

### Specific Code Impact Analysis

#### Cache Invalidation Logic (Critical for All Approaches)

Current logic in `extension.clj`:

```clojure
(let [stored-base-id (docker/get-image-label extended-tag base-image-id-label)
      current-base-id (get-base-image-id base-tag)]
  (cond
    ;; Base image changed
    (not= stored-base-id current-base-id)
    true
    ...))
```

**Approach A**: Must change `base-tag` to `foundation-tag` and track harness layer separately. Extension rebuild should only trigger on foundation ID change OR extension Dockerfile change, NOT on harness changes (harness layer is auto-generated on top).

**Approach B**: No changes needed — base image ID stops changing when harness tools are removed from it.

**Approach C**: Rename `base-tag` → `foundation-tag`, rename label. Logic remains identical. Extensions rebuild only when foundation or project Dockerfile changes.

#### Runtime Image Resolution (run.clj:41-57)

Current logic:

```clojure
(defn- resolve-image-tag [base-tag project-dir force?]
  (if-let [_dockerfile (ext/project-dockerfile project-dir)]
    ;; Project has extension
    (let [extended-tag (ext/compute-extended-tag project-dir)]
      (when (ext/needs-extended-rebuild? extended-tag base-tag project-dir)
        (ext/build-extended-image {...}))
      extended-tag)
    ;; No extension, use base
    base-tag))
```

**Approach A**: Add harness layer generation after extension resolution:
```clojure
(let [base-or-ext-tag (if-let ... extended-tag ... foundation-tag)
      harness-hash (compute-harness-hash state)
      harness-tag (str "aishell:harness-" harness-hash "-" base-or-ext-tag)]
  (when (needs-harness-rebuild? harness-tag state)
    (build-harness-layer base-or-ext-tag harness-hash state))
  harness-tag)
```
This is complex and breaks the assumption that images exist before run.

**Approach B & C**: No changes to core resolution logic. Add separate harness volume check before docker run:
```clojure
(let [image-tag (resolve-image-tag foundation-tag project-dir false)]
  (ensure-harness-volume-ready! state) ;; New pre-flight check
  ...)
```
This is much cleaner — volume check is orthogonal to image resolution.

#### Build Command Orchestration (cli.clj:145-192)

Current logic:
- Call `build-base-image` with harness flags
- Write state with all harness versions + base image ID + Dockerfile hash

**Approach A**:
- Call `build-foundation-image` (no harness flags)
- Write state with foundation image ID
- Harness layer deferred to runtime (run.clj)

**Approach B & C**:
- Call `build-foundation-image` (no harness flags)
- Call `build-harness-volume` with harness flags
- Write state with foundation image ID + harness-volume-hash

Approach B/C is simpler because all build work happens at build time, not deferred to runtime.

#### Volume Mounting Pattern (docker/run.clj:132-147)

The codebase already has extensive volume mounting logic for harness configs:

```clojure
(defn- build-harness-config-mounts []
  (let [home (util/get-home)
        config-paths [[(str home "/.claude") (str home "/.claude")]
                      [(str home "/.config/opencode") (str home "/.config/opencode")]
                      ...]]
    (->> config-paths
         (filter (fn [[src _]] (fs/exists? src)))
         (mapcat (fn [[src dst]] ["-v" (str src ":" dst)])))))
```

**Approach B & C** extend this pattern with:

```clojure
(defn- build-harness-tools-mount []
  ["-v" "aishell-harness:/opt/harness:ro"
   "-e" "PATH=/opt/harness/bin:$PATH"])
```

This is architecturally consistent with existing code — volume mounts are already the preferred mechanism for injecting host resources.

### Dockerfile Template Modification (templates.clj)

Current harness installation (lines 96-138):

```dockerfile
# Install Claude Code if requested (npm global)
RUN if [ "$WITH_CLAUDE" = "true" ]; then \
        if [ -n "$CLAUDE_VERSION" ]; then \
            npm install -g @anthropic-ai/claude-code@"$CLAUDE_VERSION"; \
        else \
            npm install -g @anthropic-ai/claude-code; \
        fi \
    fi

# Install OpenCode if requested (native binary)
RUN if [ "$WITH_OPENCODE" = "true" ]; then \
        if [ -n "$OPENCODE_VERSION" ]; then \
            VERSION="$OPENCODE_VERSION" curl -fsSL https://opencode.ai/install | bash; \
        else \
            curl -fsSL https://opencode.ai/install | bash; \
        fi && \
        cp /root/.opencode/bin/opencode /usr/local/bin/opencode && \
        chmod +x /usr/local/bin/opencode; \
    fi
```

**Approach A**: These RUN commands move to a separate harness layer Dockerfile that uses `FROM {extension-or-foundation}` as input. Problem: Need to parameterize the FROM line at runtime based on extension tag. Dockerfile doesn't support dynamic FROM (you'd need `docker build --build-arg BASE_IMAGE=...`).

**Approach B & C**: Extract these to a standalone harness Dockerfile:

```dockerfile
FROM node:24-bookworm-slim
ARG WITH_CLAUDE=false
ARG CLAUDE_VERSION=""
# ... same RUN commands but install to /opt/harness/bin ...
RUN if [ "$WITH_CLAUDE" = "true" ]; then \
        npm config set prefix /opt/harness && \
        npm install -g @anthropic-ai/claude-code@"$CLAUDE_VERSION"; \
    fi
```

Then extract binaries to volume. This is cleaner because harness image doesn't depend on foundation/extension at all — it's built once and shared.

### Migration Path Considerations

#### Approach A Migration:
1. Run `aishell build --with-claude` → builds new foundation image
2. User has existing `.aishell/Dockerfile` with `FROM aishell:base` → fails because aishell:base doesn't exist
3. Migration path: detect old Dockerfiles, print error with fix instructions ("Change FROM aishell:base to FROM aishell:foundation")
4. User updates Dockerfile → next run rebuilds extension from foundation → harness layer auto-generates

**Pain point**: Breaks existing user Dockerfiles. Requires manual intervention.

#### Approach B/C Migration:
1. Run `aishell build --with-claude` → builds foundation image (no tag name change) + harness volume
2. User has existing `.aishell/Dockerfile` with `FROM aishell:base` → still works because foundation is tagged as aishell:base (or we keep both tags)
3. Migration path: seamless. Extension sees new base (without harness tools), rebuilds once to update base image ID label, then stable.

**Pain point**: None if we keep aishell:base tag pointing to foundation. Or, print deprecation warning: "Note: Base image no longer includes harness tools (now in volume). Update FROM aishell:base → FROM aishell:foundation in .aishell/Dockerfile for clarity."

### Code Quality & Maintainability

**Approach A**: Introduces build-at-runtime complexity. The harness layer generation logic would be scattered across build.clj (template generation), run.clj (orchestration), and hash.clj (version hashing). Testing becomes harder because you need to test runtime image builds.

**Approach B**: Volume extraction logic is isolated to a new `docker/harness-volume.clj` namespace. Clear separation of concerns. Testing is straightforward: verify volume contains expected binaries at expected paths.

**Approach C**: Same as Approach B for harness volume logic, plus clean rename of base → foundation (better semantic clarity). Testing is same as Approach B.

### Performance Impact

**Build time**:
- Approach A: Foundation build ~60s (Debian + Node.js), extension build ~30s (project tools), harness layer ~90s (npm installs) = 180s total, but harness layer deferred to first run → slower first launch
- Approach B/C: Foundation build ~60s, harness volume build ~90s (one-time), extension build ~30s = 180s total at build time, but subsequent builds with harness version changes only rebuild harness volume (~90s) without touching foundation or extensions

**Runtime overhead**:
- Approach A: No overhead (tools in image layers)
- Approach B/C: Named volume mount adds <1ms overhead (Docker volume performance is near-native)

**Disk usage**:
- Approach A: Each harness layer creates a new image tag (aishell:harness-{hash}-{ext}). If user experiments with multiple harness combinations, disk fills with image layers.
- Approach B/C: Single named volume shared across all projects (~500MB for all harness tools). More efficient.

## Sources

**Primary Sources** (Tier 1 - Codebase Analysis):
- [src/aishell/docker/build.clj](/home/jonasrodrigues/projects/harness/src/aishell/docker/build.clj) - Base image build logic and cache invalidation
- [src/aishell/docker/extension.clj](/home/jonasrodrigues/projects/harness/src/aishell/docker/extension.clj) - Extension image rebuild tracking
- [src/aishell/docker/templates.clj](/home/jonasrodrigues/projects/harness/src/aishell/docker/templates.clj) - Embedded Dockerfile templates with harness installation
- [src/aishell/run.clj](/home/jonasrodrigues/projects/harness/src/aishell/run.clj) - Container orchestration and image resolution
- [src/aishell/docker/run.clj](/home/jonasrodrigues/projects/harness/src/aishell/docker/run.clj) - Docker run argument construction and volume mounting
- [src/aishell/state.clj](/home/jonasrodrigues/projects/harness/src/aishell/state.clj) - State persistence schema
- [src/aishell/cli.clj](/home/jonasrodrigues/projects/harness/src/aishell/cli.clj) - Build command orchestration

## Confidence Assessment

**Overall Confidence**: High

**Factors**:
- Complete codebase access allowed direct analysis of all relevant files
- Implementation patterns are consistent throughout (volume mounts, hash-based cache invalidation, state persistence)
- Existing extension rebuild logic (extension.clj:67-105) provides clear model for how cache invalidation must work in any new approach
- Volume mounting for configs (docker/run.clj:132-147) demonstrates the pattern already exists and works

**Gaps**:
- No performance benchmarks for volume mount overhead vs. image layers (assumed negligible based on Docker volume performance characteristics, but not measured in this codebase)
- Migration testing scenarios not validated (e.g., what happens to running containers when harness volume is updated — do they see new tools immediately or need restart?)
- OpenCode native binary path handling in volume extraction needs verification (currently installs to /root/.opencode, copies to /usr/local/bin — mapping to /opt/harness/bin may require testing)
