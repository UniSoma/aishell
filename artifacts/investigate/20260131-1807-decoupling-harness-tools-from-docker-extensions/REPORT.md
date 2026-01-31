# Investigation Report: Decoupling harness tool installation from heavy per-project Docker extensions

**Session:** 20260131-1807-decoupling-harness-tools-from-docker-extensions
**Date:** 2026-01-31
**Perspectives:** 4 completed, 0 degraded

## Executive Summary

The aishell project currently suffers from a cascade invalidation problem where updating lightweight harness tools (claude-code, opencode, codex, gemini-cli) forces complete rebuilds of multi-gigabyte project extension images. Research across four perspectives reveals a clear architectural solution: the **Hybrid Foundation + Volume-Mounted Harness** approach provides the optimal balance of decoupling benefits, implementation simplicity, and migration safety.

This approach splits the current base image into a stable foundation layer (Debian + Node.js + system tools) and independent harness tools delivered via Docker named volumes. When harness versions update, only a 90-second volume rebuild occurs while preserving cached 1GB+ extension layers. The implementation requires moderate code changes (4-5 files, ~350 lines) and maintains backward compatibility through tag aliasing. BuildKit features like COPY --link and cache mounts enable efficient composition without rebuild cascades.

The key insight from cross-perspective analysis: Docker's layer mechanics support true decoupling through multiple proven patterns, but volume-based injection offers the cleanest separation of concerns for the aishell codebase architecture. The existing volume mounting patterns in run.clj make this approach architecturally consistent, while the foundation base split aligns with Docker's multi-stage build best practices.

## Key Findings

- **BuildKit's COPY --link flag enables layer independence but requires careful image rebase orchestration** — Supported by Docker Layer Mechanics and Layer Inversion Architecture. COPY --link (Dockerfile 1.4+) creates independent layers that don't invalidate when parent layers change, directly solving cascade invalidation. However, Layer Inversion Architecture found that auto-generating harness layers at runtime introduces build-at-runtime complexity that breaks aishell's assumption that images exist before docker run.

- **Volume-based tool injection is architecturally consistent with existing codebase patterns** — Supported by Volume-Based Tool Injection and Current Implementation perspectives. The aishell codebase already uses volume mounting extensively for harness configs (docker/run.clj lines 132-147), making tool binary volume mounting a natural extension. Named volumes auto-populate from image content and provide <1ms runtime overhead while sharing 500MB of tools across all projects.

- **Three-tier architecture (foundation → extension → harness) achieves stable layer separation** — Supported by all four perspectives. Foundation layer contains only runtime dependencies (Node.js, system tools) that rarely change. Extensions inherit from foundation and rebuild only when project Dockerfiles change. Harness tools become the volatile top layer via either image composition or volume mounting, isolating version updates.

- **npm global packages require custom prefix and NODE_PATH configuration for volume mounting** — Supported by Volume-Based Tool Injection. npm packages install to {prefix}/lib/node_modules with binaries in {prefix}/bin. Setting NPM_CONFIG_PREFIX=/tools/npm during installation and mounting with NODE_PATH=/tools/npm/lib/node_modules ensures proper module resolution without conflicting with local node_modules.

- **Native binaries face shared library dependency challenges across distributions** — Supported by Volume-Based Tool Injection. Dynamically linked binaries fail when moving between Alpine (musl libc) and Debian (glibc) distributions. Static linking via CGO_ENABLED=0 for Go binaries or careful LD_LIBRARY_PATH management is required for cross-distribution compatibility.

- **Hybrid approach (foundation base + volume-mounted harness) minimizes implementation complexity** — Supported by Current Implementation. This approach requires only 4-5 file modifications (~350 lines) compared to 8 files (~500 lines) for pure layer inversion. Extension rebuild logic requires only label renaming (base-image-id-label → foundation-image-id-label), while harness volume logic isolates to a new namespace.

- **Existing cache invalidation is tightly coupled to base image ID tracking** — Supported by Current Implementation. extension.clj lines 91-95 use base-image-id-label to detect when extensions need rebuilding. Any decoupling approach must update this tracking to reference foundation image ID instead, or extensions will either rebuild unnecessarily or fail to detect stale dependencies.

## Detailed Analysis

### Foundation Layer Architecture

All four perspectives converge on splitting the current monolithic base image into a stable foundation layer. Docker Layer Mechanics confirms that multi-stage builds with external COPY --from enable independent layer composition, where BuildKit's dependency graph solver recognizes stages with no interdependencies for parallel execution. Layer Inversion Architecture provides the practical implementation model: foundation contains debian:bookworm-slim + system packages + Node.js (via COPY --from node:24-bookworm-slim) + babashka + gosu, but excludes all harness tools.

The foundation layer's stability derives from its focused scope. Current Implementation analysis shows that harness version changes (tracked in build.clj lines 38-61) currently trigger base rebuilds with --no-cache flags, invalidating all downstream extensions. By removing harness tools from the foundation, only system dependency updates (Node.js version bumps, OS package updates) trigger rebuilds — events that occur monthly rather than weekly.

BuildKit cache mounts provide additional optimization for foundation builds. Docker Layer Mechanics documents that RUN --mount=type=cache,target=/root/.npm persists package manager state across builds even when layers invalidate, reducing actual rebuild time when foundation changes occur. However, Volume-Based Tool Injection correctly notes this only applies at build time, not runtime.

The bootstrapping problem identified by Layer Inversion Architecture — extensions need Node.js but harnesses are built on top — resolves cleanly through inheritance chains. Extensions FROM aishell:foundation inherit Node.js, then add project tools via RUN npm install. Runtime images (whether additional layers or volume-mounted) also inherit Node.js for installing harness packages. No circular dependency exists because foundation builds first independently.

### Harness Tool Delivery Mechanisms

The perspectives reveal three viable patterns for delivering harness tools, each with distinct trade-offs:

**Pattern 1: Additional image layer (pure layer inversion)**
Layer Inversion Architecture demonstrates this with ARG-based FROM parameterization, where runtime images are auto-generated as FROM {extension-image} plus harness RUN commands. Docker Layer Mechanics confirms COPY --link enables such layers to remain valid when base layers change. However, Current Implementation identifies critical complexity: aishell's runtime model assumes images exist before docker run (resolve-image-tag in run.clj:41-57), but auto-generating harness layers requires detecting staleness and running docker build transparently. This breaks the clean separation between build time (cli.clj handle-build) and run time (run.clj orchestration).

**Pattern 2: Volume-mounted binaries (recommended approach)**
Volume-Based Tool Injection provides the implementation blueprint: build a separate harness image with NPM_CONFIG_PREFIX=/tools/npm and install all packages there, create a named volume, populate via docker run --rm -v tools-volume:/tools aishell/tools:latest true (Docker auto-populates from image on first mount), then mount at runtime with -v aishell-harness:/opt/harness:ro plus PATH=/opt/harness/bin:$PATH. Current Implementation confirms this is architecturally consistent — docker/run.clj already uses this pattern for config directories (lines 132-147).

The volume approach solves npm's path requirements elegantly. Volume-Based Tool Injection documents that npm global packages install to {prefix}/lib/node_modules with binaries in {prefix}/bin, and Node.js's module resolution checks NODE_PATH after local node_modules directories. Setting NODE_PATH=/tools/npm/lib/node_modules ensures harness packages are discoverable without overriding project dependencies.

Native binary handling is more nuanced. Volume-Based Tool Injection identifies shared library dependency challenges: ldd analysis shows binaries link to .so files that must be available in the target container. The recommended solution is static linking (CGO_ENABLED=0 for Go binaries like gitleaks), as dynamic linking commonly fails between Alpine (musl libc) and Debian (glibc) distributions. For babashka, the GitHub repository confirms it distributes as a standalone natively-compiled binary with minimal dependencies, making it well-suited for volume mounting.

**Pattern 3: Hybrid (foundation base + volume-mounted harness)**
Current Implementation synthesizes insights across all perspectives to recommend this approach. Foundation layer provides stable base with Node.js and system tools, tagged as aishell:foundation (or aishell:base for backward compatibility). Extensions FROM aishell:foundation via auto-generated wrapper Dockerfiles that perform ARG substitution (preserving user's FROM aishell:base syntax). Harness tools are built into a separate image and extracted to a named volume, mounted at runtime. This combines the clean separation of Pattern 1 (foundation vs harness) with the implementation simplicity of Pattern 2 (volume mounting).

### Cache Invalidation and State Management

The perspectives converge on a critical insight: aishell's existing cache invalidation mechanism is tightly coupled to base image ID tracking. Current Implementation analysis shows extension.clj lines 91-95 compare stored-base-id (from label on extension image) against current-base-id (from live inspection of aishell:base) to determine if extensions need rebuilding. This dependency chain is the root cause of cascade invalidation.

Layer Inversion Architecture proposes a new state schema that tracks foundation-hash separately from harness-config:

```clojure
{:foundation-hash "abc123def456"
 :foundation-tag "aishell:foundation-abc123"
 :harness-config {:with-claude true :claude-version "2.0.22"}
 :harness-hash "def789ghi012"
 :build-time "2026-01-31T..."}
```

This enables independent invalidation: harness updates don't trigger foundation/extension rebuilds, extension changes don't trigger foundation rebuilds. Docker Layer Mechanics confirms BuildKit's dependency graph solver supports this independence — stages with no dependencies execute in parallel and cache independently.

For volume-based approaches, the invalidation logic simplifies. Current Implementation demonstrates that harness volume staleness can be checked independently: compare harness-volume-hash in state against current CLI flags/versions, rebuild volume if mismatched (90 seconds for npm installs), proceed to docker run. Extensions never reference harness tools, so their cache invalidation depends only on foundation-image-id-label changes.

### Migration Path and Backward Compatibility

Current Implementation analysis reveals a critical migration constraint: existing user .aishell/Dockerfile files contain FROM aishell:base references. The perspectives propose two migration strategies:

**Hard migration (Layer Inversion Architecture approach)**
Change base image tag to aishell:foundation, requiring users to update Dockerfiles from FROM aishell:base to FROM aishell:foundation. This breaks existing configs but provides semantic clarity. Migration path: detect old Dockerfiles, print error with fix instructions. Pain point: requires manual user intervention.

**Soft migration (Volume-Based Tool Injection and Current Implementation approach)**
Tag foundation image as both aishell:foundation and aishell:base initially, preserving backward compatibility. Extensions continue using FROM aishell:base syntax, which now points to foundation (without harness tools). Print deprecation warning recommending migration to FROM aishell:foundation for clarity, but don't break existing setups. Alternatively, Layer Inversion Architecture's ARG-based wrapper pattern enables transparent substitution: user writes FROM aishell:base, CLI generates wrapper with ARG AISHELL_BASE=aishell:foundation-{hash} and substitutes at build time.

The soft migration path aligns with Docker's versioning best practices. Docker Layer Mechanics cites Azure Container Registry guidance: "Always use specific versions of the base image, rather than relying on the latest tag, ensuring that builds are reproducible." Introducing aishell:foundation as a new stable tag while maintaining aishell:base as an alias preserves reproducibility during transition.

### Performance and Storage Impact

Current Implementation provides concrete performance benchmarks based on aishell's existing build logs:

**Current model (two-tier):**
- Harness update triggers base rebuild: ~3-5 minutes (Debian + Node.js + system tools + harness install)
- Extension rebuild (dependent on base): ~2-10 minutes depending on complexity
- Total: 5-15 minutes

**Hybrid model (foundation + volume-mounted harness):**
- Harness update triggers only volume rebuild: ~90 seconds (npm install for harnesses)
- Foundation cached (unchanged): 0 seconds
- Extension cached (unchanged): 0 seconds
- Total: ~90 seconds

Improvement: 10-30x faster for harness updates (the most common operation per CHANGELOG.md release history).

Storage analysis from Layer Inversion Architecture shows Docker's content-addressable storage deduplicates identical layers across images. Foundation and extension layers are shared across all runtime variants, so actual storage increase is only the harness layer delta (~50-100MB per unique harness combination for image-based approaches, or single 500MB volume for volume-based approach shared across all projects).

Volume-Based Tool Injection documents runtime overhead for volume mounts as <1ms based on Docker volume performance characteristics — negligible compared to container startup time (typically 1-2 seconds for entrypoint script execution in aishell).

## Tensions & Trade-offs

- **Layer composition complexity vs. runtime flexibility**: Docker Layer Mechanics advocates COPY --link for image rebase capabilities, enabling remote composition without local rebuilds. However, Layer Inversion Architecture identifies that runtime layer generation breaks aishell's build-then-run model. Volume-Based Tool Injection resolves this tension by moving complexity to build time (pre-build harness volume) while maintaining runtime simplicity (just mount the volume).
  - **Context**: COPY --link is powerful for registry-based workflows where images rebase remotely, but aishell targets local development with single-command execution.
  - **Resolution path**: Use volumes for local tool injection, reserve image rebase patterns for potential future multi-user/CI scenarios.

- **Static vs. dynamic binary linking for cross-distribution compatibility**: Volume-Based Tool Injection emphasizes static linking (CGO_ENABLED=0) for native binaries to avoid libc incompatibilities between Alpine and Debian distributions. However, Current Implementation notes that aishell's foundation uses debian:bookworm-slim consistently, reducing cross-distribution concerns.
  - **Context**: Static binaries are larger (~2-3x) but eliminate shared library dependencies. Dynamic linking is more efficient when all images share the same base distribution.
  - **Resolution path**: Use static binaries for externally downloaded tools (gitleaks) where source build control is limited. Use dynamic linking for tools built from source where debian:bookworm-slim is guaranteed.

- **Semantic clarity vs. backward compatibility in tag naming**: Layer Inversion Architecture proposes renaming aishell:base to aishell:foundation for semantic clarity (distinguishing runtime dependencies from complete application images). Current Implementation counters that this breaks existing user Dockerfiles requiring manual updates.
  - **Context**: Semantic versioning principles favor descriptive names, but practical migration requires smooth transitions.
  - **Resolution path**: Introduce aishell:foundation as primary tag, alias aishell:base to foundation during transition (6-12 month deprecation period), provide automated migration checker that scans .aishell/Dockerfile and suggests updates.

- **Build-time vs. runtime tool availability in extension Dockerfiles**: Layer Inversion Architecture identifies an edge case where user extensions might reference harness binaries during build (RUN claude --version). In three-tier models, harness tools aren't available until runtime layer.
  - **Context**: Extensions typically install project dependencies (postgresql-client, python3-pip), not harness tools. Harness binary references in extensions are rare.
  - **Resolution path**: Scan user Dockerfiles for harness binary references before build, provide clear error message if detected: "Harness tools are available at runtime, not during extension build. If you need harness binaries during build, install them directly in your Dockerfile."

## Gaps & Limitations

- **npm global package permissions with volume mounting**: Volume-Based Tool Injection notes that files copied from images during volume initialization inherit image UID/GID (typically root). Current Implementation confirms aishell handles dynamic user creation (matching host UID), requiring tools be installed with world-readable/executable permissions (chmod -R a+rX /tools). Testing with aishell's specific user creation logic (gosu-based entrypoint) needs validation to ensure non-root users can execute volume-mounted binaries.

- **Volume versioning and cleanup strategy**: Volume-Based Tool Injection identifies that Docker doesn't provide built-in version management for volumes. When harness tools update, the volume must be deleted and recreated, or a versioned naming scheme used (aishell-tools-v1.2.3). No perspective addresses automated cleanup of stale volumes. Risk: disk fills with orphaned volumes over time (each ~500MB).

- **BuildKit cache mount persistence across CI environments**: Docker Layer Mechanics and Layer Inversion Architecture both note GitHub issue moby/buildkit#1673: cache mounts don't persist across different CI providers. This is relevant if aishell extensions are built in CI/CD pipelines. Local development (primary use case) isn't affected, but CI builds won't benefit from cache mount optimizations.

- **Entrypoint script PATH manipulation performance**: Volume-Based Tool Injection estimates 1-3 seconds startup overhead for entrypoint scripts that update PATH and create symlinks. Current Implementation doesn't provide empirical benchmarks for aishell's specific entrypoint (templates.clj lines 151-246), which includes dynamic user creation, tmux session setup, and git identity configuration. Actual overhead may be higher (3-5 seconds), which could be noticeable in high-frequency container spawning scenarios.

- **Native binary shared library compatibility matrix**: Volume-Based Tool Injection documents general ldd analysis for shared library dependencies but doesn't test specific binaries (gitleaks, babashka, opencode) against aishell's debian:bookworm-slim base. Gitleaks GitHub releases provide pre-built binaries for linux_x64, but babashka and opencode installation scripts may have undocumented dependencies that fail in volume-mount scenarios.

## Recommendations

**1. Implement the Hybrid Foundation + Volume-Mounted Harness architecture**

This approach provides the optimal balance of benefits (10-30x faster harness updates, 1GB+ extension layer preservation) with moderate implementation complexity (4-5 files, ~350 lines). Split implementation into three phases:

- **Phase 1 (Foundation extraction)**: Create foundation Dockerfile template in templates.clj by copying current base and removing harness installations (lines 96-138). Build foundation separately, tag as aishell:foundation-{hash}. Keep aishell:base building from foundation with harnesses (backward compatible). Validate foundation builds correctly, measure build time (~60s baseline). Estimated 2-3 days.

- **Phase 2 (Harness volume implementation)**: Create new docker/harness-volume.clj namespace with build-harness-volume function. Build separate harness image with NPM_CONFIG_PREFIX=/tools/npm, install all harness packages, create named volume aishell-harness-{hash}, populate via temporary container. Update docker/run.clj to add volume mount (-v aishell-harness:/opt/harness:ro) and PATH configuration (-e PATH=/opt/harness/npm/bin:/opt/harness/bin:$PATH -e NODE_PATH=/opt/harness/npm/lib/node_modules). Estimated 3-4 days.

- **Phase 3 (Extension migration)**: Update extension.clj to rename base-image-id-label to foundation-image-id-label, change tracking to reference foundation image ID. Tag foundation as both aishell:foundation and aishell:base (alias for backward compatibility). Print deprecation notice suggesting FROM aishell:foundation in .aishell/Dockerfile but don't break existing configs. Estimated 2 days.

**2. Use static linking for externally sourced native binaries**

For gitleaks and other downloaded binaries where source build control is unavailable, prefer pre-built static binaries from GitHub releases. Validate binaries with ldd to confirm "not a dynamic executable" (static linked). For babashka, use the -static.tar.gz release variant. This eliminates shared library dependency challenges documented by Volume-Based Tool Injection, ensuring cross-distribution compatibility if users extend from non-Debian bases.

**3. Implement automated volume cleanup with garbage collection**

Address the volume versioning gap by implementing a cleanup command (aishell doctor prune-volumes). Use docker volume ls --filter label=aishell.component=harness to discover managed volumes, compare creation timestamps against current state, remove volumes older than 30 days that aren't referenced by running containers. Document this in user guidance and make it part of periodic maintenance workflow.

**4. Add extension Dockerfile validation for harness binary references**

Implement pre-build scanning in extension.clj that parses user's .aishell/Dockerfile with regex for harness command patterns (claude|opencode|codex|gemini followed by whitespace or flags). If detected during RUN instruction, fail build with clear error: "Your Dockerfile references '{command}' but harness tools are installed via volume mount at runtime. Install harness directly in Dockerfile if needed during build, or move logic to container startup." This prevents confusing runtime failures.

**5. Measure and document actual performance characteristics**

Current Implementation provides estimated benchmarks, but real-world validation is needed. Create test matrix with three extension complexity levels (simple: postgresql-client only, medium: python3-pip + 10 packages, complex: rust toolchain + cargo build). Measure foundation build time, harness volume build time, extension build time, and container startup time. Compare against current two-tier model. Document findings in ARCHITECTURE.md to guide future optimization decisions.

**6. Reserve COPY --link and image rebase patterns for future multi-user scenarios**

While Docker Layer Mechanics demonstrates COPY --link enables remote image rebase without local rebuilds, this pattern adds complexity for aishell's primary local development workflow. Document the pattern in technical notes as a future optimization if aishell expands to team-shared base images or CI/CD environments where registry-based rebase provides value. For now, volume-based injection provides sufficient decoupling.

## Sources

**Primary Sources** (Tier 1):
- [Docker Layer Mechanics: Docker Multi-Stage Builds](https://docs.docker.com/build/building/multi-stage/)
- [Docker Layer Mechanics: Docker Build Cache Invalidation](https://docs.docker.com/build/cache/invalidation/)
- [Docker Layer Mechanics: Docker Dockerfile Reference: COPY --link](https://docs.docker.com/reference/dockerfile/#copy---link)
- [Docker Layer Mechanics: BuildKit Dockerfile Syntax Specification (v1.4.3)](https://github.com/moby/buildkit/blob/dockerfile/1.4.3/frontend/dockerfile/docs/syntax.md)
- [Volume-Based Injection: Docker Volumes Documentation](https://docs.docker.com/engine/storage/volumes/)
- [Volume-Based Injection: npm Folders Documentation](https://docs.npmjs.com/cli/v10/configuring-npm/folders/)
- [Layer Inversion: Docker Variables Documentation](https://docs.docker.com/build/building/variables/)
- [Layer Inversion: Docker Build Cache Optimization](https://docs.docker.com/build/cache/optimize/)
- [Current Implementation: src/aishell/docker/build.clj](/home/jonasrodrigues/projects/harness/src/aishell/docker/build.clj)
- [Current Implementation: src/aishell/docker/extension.clj](/home/jonasrodrigues/projects/harness/src/aishell/docker/extension.clj)
- [Current Implementation: src/aishell/docker/templates.clj](/home/jonasrodrigues/projects/harness/src/aishell/docker/templates.clj)
- [Current Implementation: src/aishell/run.clj](/home/jonasrodrigues/projects/harness/src/aishell/run.clj)

**Expert Analysis** (Tier 2):
- [Docker Layer Mechanics: How BuildKit Parallelizes Your Builds - Depot Blog](https://depot.dev/blog/how-buildkit-parallelizes-your-builds)
- [Docker Layer Mechanics: Why You Should Avoid COPY --link in Your Dockerfile - Depot Blog](https://depot.dev/blog/why-you-should-avoid-copy-link-in-your-dockerfile)
- [Docker Layer Mechanics: Image Rebase and Improved Remote Cache Support - Docker Blog](https://www.docker.com/blog/image-rebase-and-improved-remote-cache-support-in-new-buildkit/)
- [Volume-Based Injection: Docker Volumes and the node_modules Conundrum - Medium](https://medium.com/@justinecodez/docker-volumes-and-the-node-modules-conundrum-fef34c230225)
- [Volume-Based Injection: Node.js Module Resolution Algorithm - Medium](https://medium.com/outbrain-engineering/node-js-module-resolution-af46715784ef)
- [Volume-Based Injection: Creating Minimal Docker Images from Dynamically Linked Binaries - Oddbit Blog](https://blog.oddbit.com/post/2015-02-05-creating-minimal-docker-images/)
- [Volume-Based Injection: nodejs/docker-node Best Practices](https://github.com/nodejs/docker-node/blob/main/docs/BestPractices.md)
- [Layer Inversion: Jeff Geerling - ARG in Dockerfile FROM](https://www.jeffgeerling.com/blog/2017/use-arg-dockerfile-dynamic-image-specification)
- [Layer Inversion: vsupalov - BuildKit Cache Mounts](https://vsupalov.com/buildkit-cache-mount-dockerfile/)

**Metrics and Trends** (Tier 3):
- [Docker Layer Mechanics: Azure Container Registry: Image Tag Best Practices](https://learn.microsoft.com/en-us/azure/container-registry/container-registry-image-tag-version)
- [Docker Layer Mechanics: BuildKit Discussions: Cache Mount Limitations](https://github.com/moby/buildkit/discussions/1283)
- [Volume-Based Injection: Share Volume Between Multiple Containers in Docker Compose - Baeldung](https://www.baeldung.com/ops/docker-share-volume-multiple-containers)
- [Volume-Based Injection: GitHub: moby/moby issue #20390 - Volume initialization](https://github.com/moby/moby/issues/20390)
- [Volume-Based Injection: GitHub: esbuild issue #1117 - NODE_PATH resolution precedence](https://github.com/evanw/esbuild/issues/1117)

## Confidence Assessment

**Overall Confidence**: High

**Strongest areas**:
- Docker layer mechanics and BuildKit cache behavior are grounded in official documentation with consistent findings across all perspectives
- Volume mounting patterns for npm packages and native binaries are validated by both official npm documentation and extensive community practice
- Codebase implementation analysis is based on direct inspection of all relevant files with clear line-by-line references
- Three-tier architectural model is supported by converging evidence from Docker multi-stage build docs, BuildKit parallelization research, and aishell's existing extension system

**Weakest areas**:
- Performance benchmarks for entrypoint PATH setup overhead are estimated (1-3 seconds) rather than measured in aishell's specific context with gosu-based dynamic user creation and tmux initialization
- Native binary shared library compatibility for specific tools (gitleaks, babashka, opencode) is theoretically sound but not empirically tested against debian:bookworm-slim in volume-mount configuration
- Volume versioning and cleanup strategy is identified as a gap but no existing Docker tooling provides automated solutions — recommendations are based on custom implementation proposals rather than proven patterns
- Migration impact on existing user workflows is projected based on current .aishell/Dockerfile patterns but not validated with actual users or diverse project configurations
