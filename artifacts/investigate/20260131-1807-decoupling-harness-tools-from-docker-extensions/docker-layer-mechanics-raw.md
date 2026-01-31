# Docker Layer Mechanics & BuildKit Features Research: Decoupling Harness Tool Installation From Docker Extensions

## Research Parameters

**Topic**: Decoupling harness tool installation from heavy per-project Docker extensions. Current model: debian:slim → system tools + Node.js + harness tools (npm packages like claude-code, codex, gemini-cli; native binaries like opencode, gitleaks, babashka) → per-project extension layer via .aishell/Dockerfile (FROM aishell:base). Extensions can be 1GB+. When harness tools update, the base image rebuilds, invalidating ALL extension layers.

**Perspective**: Docker Layer Mechanics & BuildKit Features

**Focus**: Docker/BuildKit capabilities for layer composition without rebuild cascades

**Date**: 2026-01-31

**Session**: 20260131-1807-decoupling-harness-tools-from-docker-extensions

## Key Findings

- **Multi-stage builds with COPY --from external images enable independent layer composition**: External images can be referenced without dependency on previous layers in the same stage. BuildKit treats external COPY --from statements as independent nodes in a dependency graph, allowing the base layer and extension layer to be built/cached separately. When harness tools update, only their layer rebuilds; downstream extension layers can reuse their previous state if the COPY source changes but the destination content semantically matches.

- **COPY --link flag (Dockerfile 1.4+, BuildKit 0.10+) creates truly decoupled layers that bypass cache invalidation**: The --link flag copies files into an independent layer that does not depend on parent layer state, allowing this layer to be reused even when previous commands change. This directly solves the cascade problem: if harness tools layer changes, extension layers built with COPY --link remain valid and don't require rebuild, making 2nd-day rebasing possible without local rebuilds.

- **BuildKit's concurrent dependency graph solver prevents cascade invalidation across independent stages**: BuildKit parses instructions into a Low-Level Build (LLB) dependency graph and identifies stages with no interdependencies for parallel execution. By structuring the Dockerfile so extension layers don't depend on harness tool internals (only on their output via COPY --from), you can ensure changes in harness tools don't invalidate downstream layers—the graph solver simply replaces the base layer without touching extension layers.

- **Cache mounts and BuildKit's remote cache enable efficient composition without full rebuilds**: BuildKit supports --mount=type=cache for persistent package caches across builds, and remote cache support allows reusing previously built layers from registries. This allows extension layers to be built against new base images without downloading the full base or re-executing expensive operations; only deltas are fetched and composed.

- **Image rebase capability (Dockerfile 1.4+ with --link) enables composing base and extension as independent artifacts**: With COPY --link, extension layer files become a snapshot independent of the base layer they sit on top of. This snapshot can be "rebased" onto a different base image remotely without local rebuilding. A new harness tools base image can be published, and existing extension images can be rebased on top in the registry without requiring per-project rebuilds.

## Analysis

### The Core Problem: Cascade Invalidation in Traditional Dockerfile Chains

The current architecture suffers from a fundamental Docker caching limitation: **layer dependency chains are linear and unidirectional downward**. When building:

```dockerfile
FROM debian:slim
# Layer A: system tools
RUN apt-get install ...
# Layer B: Node.js + npm packages (harness tools)
RUN apt-get install nodejs && npm install -g claude-code codex gemini-cli
# Layer C: per-project extension (from another Dockerfile)
COPY . /app
RUN npm install --production
```

If Layer B changes (harness tools update), the Docker cache invalidates Layer C even though the project code hasn't changed. According to the [Docker Build Cache Documentation](https://docs.docker.com/build/cache/invalidation/), "If no cached layer matches the instruction exactly, the cache is invalidated" and this applies to all subsequent instructions. This is the **cascade invalidation problem**.

### Solution 1: Multi-Stage Builds with External COPY --from

The foundation of decoupling is [multi-stage builds](https://docs.docker.com/build/building/multi-stage/) with external image references. Instead of a linear chain, structure layers as independent stages:

```dockerfile
# Stage 1: harness-base (maintained separately as aishell:base)
FROM debian:slim AS harness-base
RUN apt-get install nodejs
RUN npm install -g claude-code codex gemini-cli

# Stage 2: extension (in per-project Dockerfile)
FROM aishell:base  # or use COPY --from=harness-base externally
COPY . /app
RUN npm install
```

According to [Docker Multi-Stage Build Documentation](https://docs.docker.com/build/building/multi-stage/), "You can reference stages either by number or by name using the AS clause" and critically, "you can copy from a separate image, either using the local image name, a tag available locally or on a Docker registry."

This enables **BuildKit dependency graph independence**: The external image (aishell:base) is treated as an external input, not a parent layer. When building the per-project Dockerfile:

1. The base image is pulled
2. Files are copied from it
3. The extension layer is built
4. Changes to aishell:base don't invalidate the extension layer's cache because the extension stage doesn't depend on the base image's internal instructions—it only depends on the file content it copies

According to [BuildKit parallelization research](https://depot.dev/blog/how-buildkit-parallelizes-your-builds), BuildKit uses a "Directed Acyclic Graph (DAG) solver" that "recognizes stages with no dependencies on each other" and executes them concurrently. More importantly, it only executes stages needed for the final target, and **skips unused stages**. This means independent stages don't interfere with each other's caching.

### Solution 2: COPY --link for True Layer Independence (Recommended for 2nd-Day Rebasing)

The most powerful feature for solving this problem is [COPY --link](https://docs.docker.com/reference/dockerfile/#copy---link), introduced in Dockerfile 1.4 with BuildKit 0.10+. This flag fundamentally changes how Docker treats copied files.

According to the [Docker Dockerfile 1.4 specification](https://github.com/moby/buildkit/blob/dockerfile/1.4.3/frontend/dockerfile/docs/syntax.md), COPY --link creates files in an independent layer with the following key property:

> "Files are copied to a new location and turned into an independent layer, removing the dependency from the destination directory so we don't need to wait for previous commands to finish before completing the COPY command, and we also do not need to invalidate our build cache for the current command when previous commands on the same Dockerfile stage change."

This directly solves the cascade problem. Consider:

```dockerfile
# syntax=docker/dockerfile:1.4
FROM debian:slim
RUN apt-get install nodejs
COPY --link npm-packages /usr/local/lib/node_modules/  # Harness tools layer
COPY --link . /app/  # Extension layer
```

If only the npm-packages snapshot changes (harness tools update), the second COPY --link for the extension doesn't invalidate because:

1. The harness tools COPY is in its own independent layer
2. The extension COPY doesn't read from or depend on the state of previous layers
3. The cache for the extension COPY can be reused if the source content hasn't changed

According to research on [COPY --link performance and semantics](https://depot.dev/blog/why-you-should-avoid-copy-link-in-your-dockerfile), the trade-off is that COPY --link cannot read symlinks or follow paths from the parent layer, but for artifact copying in multi-stage builds, this is acceptable.

The critical benefit for your use case is **2nd-day rebasing**: The [Docker Blog on Image Rebase](https://www.docker.com/blog/image-rebase-and-improved-remote-cache-support-in-new-buildkit/) explains that with COPY --link, "if the application binary hasn't changed, you can rebase that layer on top of the new base image, happening completely remotely without any local layers." This means:

- Harness tools are published as aishell:base (with all COPY --link instructions)
- Extension layers are built and cached with references to aishell:base
- When aishell:base updates, extension layers can be rebased in the registry without local rebuilding
- BuildKit automatically composes the new base with the existing extension snapshot

### Solution 3: BuildKit Cache Mounts for Persistent Package State

For scenarios where the harness tools are installed via package managers (npm, pip, apt), [BuildKit cache mounts](https://docs.docker.com/build/cache/optimize/) provide another decoupling mechanism.

According to the [Docker Cache Optimization Guide](https://docs.docker.com/build/cache/optimize/):

> "The cache is cumulative across builds, so you can read and write to the cache multiple times. Files written to a cache mount are preserved across builds, even if the layer containing the mount operation is invalidated."

Cache mounts can be used with RUN instructions:

```dockerfile
RUN --mount=type=cache,target=/root/.npm \
  npm install -g claude-code codex gemini-cli
```

The critical property: "You only download new or changed packages. Any unchanged packages are reused from the cache mount" even if the layer is invalidated. This means:

1. If harness tools version pinning changes, npm must re-resolve, but cached downloads are reused
2. If extension dependencies change, the harness tools cache mounts remain intact
3. The cache persists across rebuild cycles, reducing the actual rebuild time

However, cache mounts have limitations noted in [BuildKit discussions](https://github.com/moby/buildkit/discussions/1283): "BuildKit doesn't support saving or loading cache mounts" across different CI providers or persistent storage. This works well within a single build environment but requires persistent BuildKit daemon state.

### Solution 4: BuildKit's Concurrent Dependency Graph Solver

The fundamental architecture that enables all these features is BuildKit's dependency graph. According to [BuildKit Documentation](https://docs.docker.com/build/buildkit/):

> "BuildKit features a new fully concurrent build graph solver that can run build steps in parallel when possible and optimize out commands that don't have an impact on the final result."

The solver parses the Dockerfile into Low-Level Build (LLB) format, a "content-addressable dependency graph that defines the dependencies for processes running part of your build." The key insight for decoupling:

**BuildKit only executes stages that the final target depends on, and skips unused stages.**

This means if you structure your Dockerfile as:

```dockerfile
FROM debian:slim AS system-base
# system tools

FROM system-base AS harness-tools
# npm install harness tools

FROM external-image:latest AS extension-base
# per-project extensions (FROM external image, not FROM harness-tools)

FROM extension-base
# final image
```

BuildKit's solver recognizes that the extension-base stage doesn't depend on harness-tools, even though both depend on system-base. This independence means:

1. Changes to harness-tools stage don't invalidate extension-base
2. Both can be cached independently
3. If extension-base uses COPY --from=external-image, it's entirely decoupled

### Solution 5: Structured Versioning with External Base Images

Rather than a single Dockerfile chain, split into versioned published images:

**Harness Base Image** (`aishell:base` or `aishell:v1.2.3`):
- System tools
- Node.js + npm packages
- Native binaries (gitleaks, babashka, opencode)
- Published to registry with semantic versioning
- Uses COPY --link for all artifact placement

**Per-Project Extension Image** (`my-project:dev`, built locally or in CI):
- FROM aishell:v1.2.3 (or :latest-stable)
- COPY --from=aishell:v1.2.3 or just uses FROM directly
- Project-specific dependencies
- Built and cached independently

**Benefit**: When aishell:base updates to v1.2.4, per-project images can:

1. Stay on v1.2.3 if desired (no forced update)
2. Upgrade to v1.2.4 on next build (new FROM reference)
3. Optionally use image rebase to move existing layers onto v1.2.4 in the registry without local rebuild

According to [Docker Image Versioning Best Practices](https://learn.microsoft.com/en-us/azure/container-registry/container-registry-image-tag-version), "Always use specific versions of the base image, rather than relying on the latest tag, ensuring that builds are reproducible." This principle ensures your extension layers always reference a specific, stable harness base version, preventing unexpected cascades.

## Sources

### Primary Sources (Tier 1: Official Documentation)

- [Docker Multi-Stage Builds](https://docs.docker.com/build/building/multi-stage/)
- [Docker Build Cache Invalidation](https://docs.docker.com/build/cache/invalidation/)
- [Docker Build Cache Optimization](https://docs.docker.com/build/cache/optimize/)
- [Docker BuildKit Documentation](https://docs.docker.com/build/buildkit/)
- [Docker Dockerfile Reference: COPY --link](https://docs.docker.com/reference/dockerfile/#copy---link)
- [Docker Build Secrets](https://docs.docker.com/build/building/secrets/)
- [BuildKit Dockerfile Syntax Specification (v1.4.3)](https://github.com/moby/buildkit/blob/dockerfile/1.4.3/frontend/dockerfile/docs/syntax.md)

### Expert Analysis (Tier 2: Technical Community Sources)

- [How BuildKit Parallelizes Your Builds - Depot Blog](https://depot.dev/blog/how-buildkit-parallelizes-your-builds)
- [Docker Multi-Stage Builds Explained - Depot Blog](https://depot.dev/blog/docker-multistage-builds)
- [The Ultimate Guide to Docker Build Cache - Depot Blog](https://depot.dev/blog/ultimate-guide-to-docker-build-cache)
- [Why You Should Avoid COPY --link in Your Dockerfile - Depot Blog](https://depot.dev/blog/why-you-should-avoid-copy-link-in-your-dockerfile)
- [Image Rebase and Improved Remote Cache Support - Docker Blog](https://www.docker.com/blog/image-rebase-and-improved-remote-cache-support-in-new-buildkit/)
- [Advanced Dockerfiles: Faster Builds Using BuildKit and Multistage Builds - Docker Blog](https://www.docker.com/blog/advanced-dockerfiles-faster-builds-and-smaller-images-using-buildkit-and-multistage-builds/)

### Comparative & Community Sources (Tier 3: Ecosystem Research)

- [Azure Container Registry: Image Tag Best Practices](https://learn.microsoft.com/en-us/azure/container-registry/container-registry-image-tag-version)
- [BuildKit Discussions: Cache Mount Limitations](https://github.com/moby/buildkit/discussions/1283)
- [Docker Community Forum: COPY --from External Images](https://forums.docker.com/t/copy-entire-layer-from-previous-build-stage-in-multi-stage-build/39835)

## Confidence Assessment

**Overall Confidence**: High

**Factors**:
- All primary findings are grounded in official Docker documentation (Tier 1 sources)
- COPY --link behavior is specified in the official Dockerfile syntax and BuildKit source
- Multi-stage build and cache invalidation semantics are well-documented and stable features
- Layer dependency graph mechanics are explained consistently across Docker blog posts and third-party analysis
- BuildKit parallelization behavior is documented and observable in practice

**Gaps**:
- Real-world performance metrics for large extension layers (1GB+) with COPY --link are not extensively documented; Depot's blog notes COPY --link can increase build times for some workloads
- Image rebase feature is documented but deep implementation details in remote cache are not fully public
- Cache mount persistence across CI providers is an open issue (issue #1673 on moby/buildkit) with no finalized solution
- No direct benchmarks comparing cascade invalidation cost vs. COPY --link overhead for the specific harness tools + extension layer use case

**Recommendation for Next Research**: Practical validation of COPY --link performance with a test extension layer (~500MB-1GB) would provide confidence on whether the decoupling benefit outweighs any build time cost.
