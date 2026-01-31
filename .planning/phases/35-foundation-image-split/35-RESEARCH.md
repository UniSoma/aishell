# Phase 35: Foundation Image Split - Research

**Researched:** 2026-01-31
**Domain:** Docker image architecture, multi-stage builds, cache invalidation
**Confidence:** HIGH

## Summary

Phase 35 splits the current monolithic `aishell:base` image into a stable foundation layer (`aishell:foundation`) containing only system dependencies (Debian, Node.js, system tools, babashka, gosu) while removing all harness tool installations (claude-code, opencode, codex, gemini-cli, gitleaks). This is the first step in a 4-phase milestone (v2.8.0) that eliminates cascade invalidation by decoupling volatile harness tools from stable system dependencies.

The research confirms Docker's cache invalidation works layer-by-layer: when a FROM image changes, all downstream layers rebuild. Currently, harness version updates trigger base image rebuilds (via ARG changes in build.clj), which invalidates all 1GB+ extension images. By removing harness installations from the foundation, only system dependency changes (Node.js upgrades, OS packages) will trigger foundation rebuilds—events occurring monthly vs weekly.

The migration strategy requires careful tag management. User `.aishell/Dockerfile` files contain `FROM aishell:base` references that must transition to `FROM aishell:foundation`. The recommended approach is a clean break: build foundation as `aishell:foundation` only (no `aishell:base` tag), detect legacy `FROM aishell:base` usage during extension builds, and provide clear error messages with migration instructions. This prevents silent breakage and ensures users understand the architectural change.

**Primary recommendation:** Remove harness installation blocks (lines 100-142 in templates.clj), change image tag to `aishell:foundation`, update cache tracking labels, and implement validation to detect and reject `FROM aishell:base` usage with actionable error messages.

## Standard Stack

The foundation image uses established Docker patterns and system dependencies:

### Core Components
| Component | Version | Purpose | Why Standard |
|-----------|---------|---------|--------------|
| debian:bookworm-slim | stable | Base OS layer | Minimal Debian with security updates, widely used for production containers |
| node:24-bookworm-slim | 24.x | Node.js runtime | Multi-stage COPY pattern from official image, provides npm for future volume setup |
| babashka | 1.12.214 | Clojure scripting | Static binary, minimal dependencies, already used for aishell CLI |
| gosu | 1.19 | Privilege dropping | Industry standard for user switching, safer than sudo/su |
| tmux | latest | Terminal multiplexer | Already integrated in Phase 33, enables persistent sessions |

### System Tools (No Changes)
Current system packages remain unchanged:
- Core: bash, ca-certificates, curl, git
- Development: vim, htop, tree, watch
- Utilities: jq, ripgrep, sqlite3, unzip, less, file

### Removed from Foundation (Moving to Phase 36 Volumes)
| Tool | Current Location | Future Location | Reason for Removal |
|------|------------------|-----------------|-------------------|
| claude-code | npm global in base | harness volume | Version updates weekly, triggers cascade |
| opencode | native binary in base | harness volume | Version updates, binary downloaded at build |
| codex | npm global in base | harness volume | Rarely updated but couples with npm ecosystem |
| gemini-cli | npm global in base | harness volume | Part of harness toolset, not system dependency |
| gitleaks | native binary in base | harness volume | Security tool updates independently |

**Installation (no changes to external interface):**
```bash
# Foundation builds internally during `aishell build`
# No user-facing changes to build command
aishell build --with-claude  # Still works, harness goes to volume in Phase 36
```

## Architecture Patterns

### Recommended Project Structure (No Changes)
Current structure remains unchanged. Foundation split is internal implementation detail:
```
.aishell/
├── config.yaml          # Project configuration (unchanged)
└── Dockerfile           # Extension Dockerfile (will need FROM update)
```

### Pattern 1: Multi-Stage Build for Node.js
**What:** Copy Node.js from official image rather than installing via apt
**When to use:** Always, already implemented in current codebase
**Example:**
```dockerfile
# Stage 1: Node.js source
FROM node:24-bookworm-slim AS node-source

# Stage 2: Foundation
FROM debian:bookworm-slim
COPY --from=node-source /usr/local/bin/node /usr/local/bin/node
COPY --from=node-source /usr/local/lib/node_modules /usr/local/lib/node_modules
RUN ln -s /usr/local/lib/node_modules/npm/bin/npm-cli.js /usr/local/bin/npm
```
**Source:** [Docker Multi-Stage Builds](https://docs.docker.com/build/building/multi-stage/)

This pattern is already implemented in templates.clj lines 12-60 and requires no changes.

### Pattern 2: Foundation-Only Image Build
**What:** Remove conditional harness installation from Dockerfile template
**When to use:** Phase 35 implementation
**Example (diff from current templates.clj):**
```dockerfile
# REMOVE these ARG declarations (lines 18-27):
# ARG WITH_CLAUDE=false
# ARG WITH_OPENCODE=false
# ...

# REMOVE harness installation blocks (lines 100-142):
# RUN if [ "$WITH_CLAUDE" = "true" ]; then ...
# RUN if [ "$WITH_OPENCODE" = "true" ]; then ...
# ...
```

**Result:** Foundation Dockerfile becomes static (no build-time variability), enabling consistent cache hits.

### Pattern 3: Tag Migration with Validation
**What:** Change image tag from `aishell:base` to `aishell:foundation`, detect legacy usage
**When to use:** Phase 35 during extension builds
**Example validation (pseudo-code for extension.clj):**
```clojure
(defn validate-dockerfile-base-tag
  "Scan project Dockerfile for legacy FROM aishell:base usage"
  [dockerfile-path]
  (when (project-dockerfile project-dir)
    (let [content (slurp dockerfile-path)]
      (when (re-find #"FROM\s+aishell:base" content)
        (output/error
          "Your .aishell/Dockerfile uses 'FROM aishell:base' which is deprecated.

Update to: FROM aishell:foundation

Reason: Harness tools moved to volumes in v2.8.0 to prevent cascade rebuilds.
See: https://github.com/yourorg/harness/blob/main/MIGRATION.md")))))
```

**Source:** Docker community best practice for breaking changes with clear migration paths.

### Anti-Patterns to Avoid

- **Aliasing `aishell:base` to `aishell:foundation` for backward compatibility:** Silent migration hides architectural change from users. Extensions using `FROM aishell:base` would work but miss the semantic shift. Explicit error messages force awareness and prevent future confusion.

- **Keeping harness ARGs "for future flexibility":** Unused ARGs in Dockerfile increase complexity and confuse cache behavior. Foundation should be minimal and static.

- **Installing Node.js via apt-get instead of multi-stage COPY:** apt-get node.js is outdated in Debian bookworm-slim (v18.x vs 24.x). Multi-stage pattern ensures latest stable Node.js version.

## Don't Hand-Roll

Problems that have existing Docker/build solutions:

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| Base image version tracking | Custom hash computation of Dockerfile | Docker image labels with `aishell.dockerfile.hash` | Already implemented in build.clj:16-36, proven pattern |
| Extension invalidation detection | File timestamp comparison | Docker image ID labels via `docker inspect --format={{.Id}}` | Already implemented in extension.clj:30-44, reliable |
| Multi-stage builds | Custom build scripts with multiple docker build calls | Native Dockerfile multi-stage with `FROM ... AS stage-name` | Official Docker feature, optimized by BuildKit |
| Tag validation during build | Runtime checks in entrypoint | Build-time validation in extension build orchestration | Fail fast at build time, not container runtime |

**Key insight:** Docker's native layer caching and image inspection APIs provide robust cache invalidation without custom diffing logic. The existing `aishell.dockerfile.hash` label pattern should be preserved and extended to `aishell.foundation.hash` for semantic clarity.

## Common Pitfalls

### Pitfall 1: Not Removing Build Args Causes Silent Cache Misses
**What goes wrong:** If harness ARGs remain in foundation Dockerfile (even unused), Docker considers them in cache key computation. Builds appear to hit cache but actually rebuild layers unnecessarily.

**Why it happens:** BuildKit includes all ARG values in cache key, even if ARGs aren't referenced in RUN commands. This is documented behavior to prevent cache poisoning.

**How to avoid:** Remove ALL harness-related ARGs from foundation template. Foundation should have zero build-time variability except BABASHKA_VERSION and GITLEAKS_VERSION (which remain for system tools).

**Warning signs:**
- `docker build` output shows "CACHED" but build takes >10 seconds
- Identical foundation builds produce different image IDs
- `docker history aishell:foundation` shows layers with different creation timestamps

**Source:** [Docker Build Cache Invalidation](https://docs.docker.com/build/cache/invalidation/)

### Pitfall 2: Extension Dockerfile References Harness Binaries During Build
**What goes wrong:** User's `.aishell/Dockerfile` might contain `RUN claude --version` or similar checks. After Phase 35, these fail because harness tools aren't in foundation.

**Why it happens:** Users may have added harness binary checks for validation during extension builds (e.g., "verify claude is installed"). When harness moves to volumes (Phase 36), these binaries aren't available at build time.

**How to avoid:**
1. Scan project Dockerfiles during extension build for harness binary references (grep for `claude|opencode|codex|gemini|gitleaks`)
2. Provide clear error before build starts: "Your Dockerfile references '{binary}' but harness tools are now volume-mounted at runtime. Remove build-time checks or install tools directly in extension."
3. Document in MIGRATION.md that harness tools are runtime-only after v2.8.0

**Warning signs:**
- Extension builds fail with "command not found" errors
- RUN commands that worked in v2.7.0 fail in v2.8.0

### Pitfall 3: Changing Tag Name Breaks Existing Extension Cache
**What goes wrong:** When tag changes from `aishell:base` to `aishell:foundation`, existing extensions have `base-image-id-label` pointing to old tag. Cache logic in extension.clj:91-95 fails to find base image, triggers unnecessary rebuilds.

**Why it happens:** extension.clj tracks base image ID via label. Label references `aishell:base` image ID. When tag changes to `aishell:foundation`, `get-base-image-id` returns nil for old label value.

**How to avoid:** Phase 35 should only change the tag, NOT the label names. Leave `base-image-id-label` as-is. Phase 37 will migrate labels systematically as part of state migration. Decoupling tag rename (Phase 35) from label migration (Phase 37) prevents cascading issues.

**Warning signs:**
- All extensions rebuild on first v2.8.0 run (expected for Phase 37, NOT Phase 35)
- Error messages: "Image not found: aishell:base"

### Pitfall 4: Foundation Dockerfile Still Contains Entrypoint/CMD That Expect Harness Binaries
**What goes wrong:** If foundation template contains harness-specific entrypoint logic (e.g., "check if claude binary exists"), foundation build succeeds but runtime fails.

**Why it happens:** Entrypoint script (templates.clj:155-250) currently has no harness-specific logic, but future changes might introduce coupling.

**How to avoid:** Audit entrypoint script to ensure it only handles:
- Dynamic user creation (LOCAL_UID/GID/HOME)
- Git identity configuration
- tmux session setup
- PATH configuration (generic, not harness-specific)

**Warning signs:**
- Foundation builds succeed but containers fail to start
- Entrypoint errors about missing commands

## Code Examples

Verified patterns from existing codebase and Docker documentation:

### Multi-Stage Node.js Copy (Already Implemented)
```clojure
;; In templates.clj
(def base-dockerfile
  "# Stage 1: Node.js source
FROM node:24-bookworm-slim AS node-source

# Stage 2: Foundation
FROM debian:bookworm-slim

# Copy Node.js from official image
COPY --from=node-source /usr/local/bin/node /usr/local/bin/node
COPY --from=node-source /usr/local/lib/node_modules /usr/local/lib/node_modules
RUN ln -s /usr/local/lib/node_modules/npm/bin/npm-cli.js /usr/local/bin/npm \\
    && ln -s /usr/local/lib/node_modules/npm/bin/npx-cli.js /usr/local/bin/npx \\
    && node --version \\
    && npm --version")
```
**Source:** templates.clj:12-60 (existing implementation, no changes needed)

### Image Tag Change in build.clj
```clojure
;; In build.clj
;; BEFORE (v2.7.0):
(def base-image-tag "aishell:base")

;; AFTER (Phase 35):
(def foundation-image-tag "aishell:foundation")
(def base-image-tag foundation-image-tag) ;; Alias during transition

;; Update build function
(defn build-foundation-image
  "Build the foundation Docker image (system dependencies only).

   Options:
   - :with-gitleaks - Include Gitleaks (default true, system tool not harness)
   - :force - Bypass cache check
   - :verbose - Show full build output
   - :quiet - Suppress all output except errors"
  [{:keys [force verbose quiet with-gitleaks] :as opts}]
  ;; Remove harness-related opts processing
  ;; Foundation build has no harness variability
  ...)
```
**Source:** build.clj:18-210 (modified for Phase 35)

### Extension Dockerfile Validation
```clojure
;; In extension.clj (new function for Phase 35)
(defn validate-base-tag
  "Check if project Dockerfile uses deprecated 'FROM aishell:base'.
   Error with migration instructions if found."
  [project-dir]
  (when-let [dockerfile-path (project-dockerfile project-dir)]
    (let [content (slurp dockerfile-path)]
      (when (re-find #"FROM\s+aishell:base\b" content)
        (output/error
          "Legacy base tag detected in .aishell/Dockerfile

Found: FROM aishell:base
Expected: FROM aishell:foundation

Why: v2.8.0 splits base image into foundation (system deps) + harness volumes
     to prevent cascade rebuilds when harness tools update.

Fix: Update .aishell/Dockerfile:
  FROM aishell:foundation

See: MIGRATION.md for full v2.8.0 upgrade guide")))))
```
**Source:** New implementation for Phase 35 based on validation pattern

## State of the Art

| Old Approach (v2.7.0) | Current Approach (Phase 35) | When Changed | Impact |
|------------|------------------|--------------|--------|
| Monolithic base with harness tools | Foundation layer (system deps only) | Phase 35 (v2.8.0) | Decouples system deps from volatile tools |
| Single tag `aishell:base` | Tag `aishell:foundation` | Phase 35 | Semantic clarity: foundation = stable base |
| Harness ARGs control installation | No harness ARGs in foundation | Phase 35 | Static Dockerfile enables consistent cache |
| base-image-tag references monolith | foundation-image-tag references stable layer | Phase 35 | Prepares for volume-based harness (Phase 36) |

**Deprecated/outdated:**
- **`aishell:base` tag:** Replaced by `aishell:foundation` in Phase 35. User Dockerfiles using `FROM aishell:base` will fail with clear error message and migration instructions.
- **Harness build ARGs in foundation template:** `WITH_CLAUDE`, `WITH_OPENCODE`, `WITH_CODEX`, `WITH_GEMINI` removed. Harness installation moves to volume population in Phase 36.

## Open Questions

### 1. Should gitleaks remain in foundation or move to harness volume?
**What we know:**
- Gitleaks is a native Go binary (static-linked, minimal dependencies)
- Currently installed conditionally via `WITH_GITLEAKS` ARG (default true)
- Used for security scanning (detection/scan commands)
- Updates independently of other harness tools

**What's unclear:**
- Is gitleaks semantically a "system tool" (like git) or "harness tool" (like claude)?
- Does gitleaks update frequency justify volume-based installation?

**Recommendation:** Keep gitleaks in foundation for Phase 35. Rationale:
1. Security scanning is infrastructure concern, not AI harness concern
2. Static binary has no npm/node dependencies, fits system tool category
3. Updates less frequently than npm-based harness tools
4. Can revisit in Phase 36 if volume pattern proves superior

### 2. How should Phase 35 handle existing state files from v2.7.0?
**What we know:**
- state.edn contains `:image-tag "aishell:base"`
- Phase 35 changes tag to `aishell:foundation`
- State migration is formally addressed in Phase 37

**What's unclear:**
- Should Phase 35 perform partial state migration (just tag name)?
- Or should it leave state unchanged and wait for Phase 37?

**Recommendation:** Leave state migration to Phase 37. Phase 35 should:
1. Build foundation as `aishell:foundation` (new tag)
2. Keep existing state.edn with `:image-tag "aishell:base"` (stale but non-blocking)
3. Phase 36 and 37 will handle state migration comprehensively
4. Avoid partial migrations that create intermediate invalid states

### 3. Should extension.clj base-image-id-label change in Phase 35 or Phase 37?
**What we know:**
- extension.clj:15 defines `base-image-id-label "aishell.base.id"`
- This label tracks which base image an extension was built from
- Phase 35 changes tag to foundation
- Phase 37 focuses on state schema migration

**What's unclear:**
- Is renaming `aishell.base.id` to `aishell.foundation.id` a Phase 35 or Phase 37 concern?
- Will keeping old label name during tag transition cause issues?

**Recommendation:** Keep label name unchanged in Phase 35. Rename in Phase 37. Rationale:
1. Label is internal tracking mechanism, doesn't need semantic alignment with tag
2. Changing both tag AND label in Phase 35 increases risk
3. Phase 37 migration can handle label rename alongside state migration
4. Principle: minimize scope of changes per phase

## Sources

### Primary (HIGH confidence)
- [Docker Build Cache Invalidation](https://docs.docker.com/build/cache/invalidation/) - Official cache behavior documentation
- [Docker Multi-Stage Builds](https://docs.docker.com/build/building/multi-stage/) - Official multi-stage pattern docs
- [Docker Volumes Documentation](https://docs.docker.com/engine/storage/volumes/) - Volume auto-population behavior (for Phase 36 context)
- [Docker Image Tag CLI Reference](https://docs.docker.com/reference/cli/docker/image/tag/) - Tag naming rules and syntax
- src/aishell/docker/build.clj - Current build implementation with ARG-based harness installation
- src/aishell/docker/extension.clj - Extension cache invalidation logic
- src/aishell/docker/templates.clj - Dockerfile templates with harness installation blocks
- src/aishell/state.clj - State persistence schema

### Secondary (MEDIUM confidence)
- [Docker Best Practices: Using Tags and Labels](https://www.docker.com/blog/docker-best-practices-using-tags-and-labels-to-manage-docker-image-sprawl/) - Tag naming conventions and label usage patterns
- [Azure Container Registry: Image Tag Best Practices](https://learn.microsoft.com/en-us/azure/container-registry/container-registry-image-tag-version) - Versioning and stability guidance
- [Investigation Report: Decoupling Harness Tools](./../../artifacts/investigate/20260131-1807-decoupling-harness-tools-from-docker-extensions/REPORT.md) - Multi-perspective analysis of architecture options
- [Volume-Based Injection Research](./../../artifacts/investigate/20260131-1807-decoupling-harness-tools-from-docker-extensions/volume-based-injection-raw.md) - npm prefix patterns and volume mounting

### Tertiary (LOW confidence)
- [Docker Image Naming Convention Best Practices](https://climbtheladder.com/10-docker-image-naming-convention-best-practices/) - Community conventions for tag naming
- [Using Semver for Docker Image Tags](https://medium.com/@mccode/using-semantic-versioning-for-docker-image-tags-dfde8be06699) - Semantic versioning applied to containers

## Metadata

**Confidence breakdown:**
- Dockerfile template changes (removing harness blocks): HIGH - Direct code modification, low risk
- Tag rename from `aishell:base` to `aishell:foundation`: HIGH - Standard Docker operation, well-documented
- Extension validation logic: MEDIUM - New validation code, requires testing with user Dockerfiles
- Cache invalidation behavior: HIGH - Based on official Docker docs and existing implementation

**Research date:** 2026-01-31
**Valid until:** 2026-03-31 (60 days - Docker core features stable, foundation architecture is long-term design)

**Key technical constraints:**
- Phase 35 is foundation-only (no volume implementation yet)
- Must maintain compatibility with existing extension.clj cache logic
- State migration deferred to Phase 37 (principle: minimize change scope)
- Foundation must provide Node.js runtime for Phase 36 volume npm installs
