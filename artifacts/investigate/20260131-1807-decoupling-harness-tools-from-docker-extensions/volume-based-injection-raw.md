# Volume-Based Tool Injection Patterns Research: Decoupling Harness Tool Installation From Docker Extensions

## Research Parameters

**Topic**: Decoupling harness tool installation from heavy per-project Docker extensions in the "aishell" project. Key harness tools are npm packages (claude-code, codex, gemini-cli) installed globally via npm, and native binaries (opencode, gitleaks, babashka). These currently live in the base image. When they update, 1GB+ extension images must rebuild. Hybrid install is acceptable: pre-cached volumes + startup linking with a few seconds delay OK. Need to evaluate how these tools can be volume-mounted or bind-mounted into containers at runtime without being baked into the image.

**Perspective**: Volume-Based Tool Injection Patterns

**Focus**: Runtime injection of tools via Docker volumes/bind-mounts

**Date**: 2026-01-31

**Session**: 20260131-1807-decoupling-harness-tools-from-docker-extensions

## Key Findings

- **Docker volumes can auto-populate from image content when first mounted to empty volumes, enabling pre-built tool distribution**: When a named volume is mounted to a directory containing files in the image, Docker copies the image directory's content into the volume on first run. This allows building a "tools image" with all harness tools installed, mounting a named volume at runtime to `/tools`, and having Docker populate the volume automatically. However, this only works for named volumes, not bind mounts, and requires the volume to be empty initially.

- **npm global packages require careful NODE_PATH and PATH configuration for volume-mount injection**: Global npm packages install to `{prefix}/lib/node_modules` (binaries in `{prefix}/bin`), where prefix defaults to `/usr/local`. To volume-mount npm packages, set a custom prefix like `/tools/npm` via `NPM_CONFIG_PREFIX`, install packages there, then mount that directory at runtime and set `PATH=/tools/npm/bin:$PATH` and `NODE_PATH=/tools/npm/lib/node_modules` in the target container. Node's module resolution algorithm checks `NODE_PATH` after local `node_modules` directories, ensuring proper precedence.

- **Native binaries mounted via volumes face shared library dependency challenges that require careful LD_LIBRARY_PATH management or static linking**: When mounting native binaries (gitleaks, babashka, opencode) from a volume, their shared library dependencies (.so files) must be available in the target container. Use `ldd /path/to/binary` to list dependencies, then either: (1) ensure the target container has compatible libraries installed, (2) mount the libraries alongside the binary and set `LD_LIBRARY_PATH`, or (3) use statically-linked binaries. Dynamic linking across different Linux distributions (e.g., Alpine vs Debian) commonly fails due to libc incompatibilities.

- **Sidecar container pattern with volumes-from or named volumes enables centralized tool management without runtime overhead**: Build a "tools container" that installs all harness tools, then use `docker run --volumes-from tools-container` or named volumes to share `/tools` directory with application containers. The tools container can be updated independently, and multiple application containers share the same tool installation. Docker Compose supports this via top-level named volumes. This pattern is widely used in Kubernetes (init containers, sidecar containers) for similar purposes.

- **Entrypoint scripts can create symlinks or update PATH at startup with minimal delay (1-3 seconds) for volume-mounted tools**: An entrypoint script can check for volume-mounted tools at `/tools`, create symlinks to `/usr/local/bin`, or prepend `/tools/bin` to PATH before executing the main command. This adds 1-3 seconds of startup time but avoids rebuilding images. Critical caveat: symlinks in the container do not work reliably with bind mounts when the mount target itself is a symlink; use direct PATH manipulation instead.

## Analysis

### The Core Challenge: Decoupling Tool Installation from Application Images

The aishell project faces a common Docker architectural challenge: **tools installed in the base image force cascading rebuilds of all extension layers when they update**. The current model:

```
debian:slim → system tools + Node.js + harness tools → per-project .aishell/Dockerfile
```

When harness tools (claude-code, gitleaks, etc.) update, the base image rebuilds, invalidating all extension layer caches. With 1GB+ extension images, this creates significant rebuild overhead.

Volume-based tool injection offers an alternative: **separate the tools from the image layers and mount them at runtime**. This section evaluates patterns for achieving this.

### Pattern 1: Named Volume Auto-Population from Tools Image

Docker's volume initialization behavior provides a foundation for runtime tool injection. According to the [Official Docker Volumes Documentation](https://docs.docker.com/engine/storage/volumes/), when you mount a named volume into a directory that contains files in the container image, Docker copies those files into the volume on first run:

> "If you start a container which creates a new volume, and the container has files or directories in the directory to be mounted such as /app/, Docker copies the directory's contents into the volume."

This enables a **tools image pattern**:

```dockerfile
# tools.Dockerfile
FROM debian:slim
RUN apt-get update && apt-get install -y nodejs npm
RUN npm config set prefix /tools/npm
RUN npm install -g claude-code codex gemini-cli
RUN curl -L https://github.com/gitleaks/gitleaks/releases/download/v8.18.0/gitleaks_8.18.0_linux_x64.tar.gz | tar -xz -C /tools/bin
# ... install other tools to /tools/
```

**Runtime injection**:

```bash
# Create named volume on first run (auto-populated from image /tools)
docker run --rm -v tools-volume:/tools aishell/tools:latest true

# Use in application container
docker run -v tools-volume:/tools \
  -e PATH=/tools/npm/bin:/tools/bin:$PATH \
  -e NODE_PATH=/tools/npm/lib/node_modules \
  my-app:latest
```

**Key gotchas identified from research**:

1. **Auto-population only works for named volumes, NOT bind mounts**: According to [Docker Community Forums discussion on volume initialization](https://forums.docker.com/t/volume-overwrites-the-directory-inside-the-docker-container/137031), "If the container's base image contains data at the specified mount point, that existing data is copied into the new volume upon volume initialization. (Note that this does not apply when mounting a host directory.)" Bind mounts do not trigger initialization.

2. **Volume must be empty initially**: If the named volume already exists with content, Docker skips initialization. This was confirmed in [GitHub issue on volume initialization](https://github.com/moby/moby/issues/20390).

3. **Subsequent updates require volume replacement**: To update tools, you must delete the old volume and recreate it, or use a versioned volume naming scheme (e.g., `tools-v1.2.3`).

### Pattern 2: npm Global Package Volume Mounting

npm global packages present specific challenges for volume-based injection due to their installation structure and Node.js's module resolution algorithm.

**Installation structure**: According to [npm folder documentation](https://docs.npmjs.com/cli/v10/configuring-npm/folders/), global packages install to `{prefix}/lib/node_modules/` with executable wrappers in `{prefix}/bin/`. The default prefix is `/usr/local`, which would require mounting the entire `/usr/local` tree.

**Solution: Custom npm prefix**

The [nodejs/docker-node best practices](https://github.com/nodejs/docker-node/blob/main/docs/BestPractices.md) recommend setting a custom prefix for global packages in Docker:

```dockerfile
ENV NPM_CONFIG_PREFIX=/tools/npm
ENV PATH=/tools/npm/bin:$PATH
RUN npm install -g claude-code codex gemini-cli
```

This installs packages to `/tools/npm/lib/node_modules/` with binaries in `/tools/npm/bin/`.

**Runtime mounting**:

```bash
docker run -v tools-npm:/tools/npm \
  -e PATH=/tools/npm/bin:$PATH \
  -e NODE_PATH=/tools/npm/lib/node_modules \
  my-app:latest
```

**Critical gotcha: node_modules resolution precedence**

Node.js's module resolution algorithm was extensively documented in research results. According to [Medium article on Node.js module resolution](https://medium.com/outbrain-engineering/node-js-module-resolution-af46715784ef), Node searches for modules in this order:

1. Local `node_modules` directory (current directory)
2. Parent directory `node_modules`, recursively up to root
3. Directories in `NODE_PATH` environment variable
4. Global installation paths

An important finding from [esbuild GitHub issue #1117](https://github.com/evanw/esbuild/issues/1117) notes that "node_modules folders in the same directory and in parent directories should have precedence over modules directly in NODE_PATH." This means setting `NODE_PATH=/tools/npm/lib/node_modules` is safe—it won't override local project dependencies.

**Volume mount vs. bind mount tradeoff for node_modules**:

The [Docker volumes and node_modules conundrum article](https://medium.com/@justinecodez/docker-volumes-and-the-node-modules-conundrum-fef34c230225) explains a related issue: when you bind-mount a project directory into a container, if the host lacks `node_modules`, the mount "effectively erases the container's node_modules." The standard solution is an anonymous volume:

```yaml
volumes:
  - .:/app
  - /app/node_modules  # Anonymous volume prevents bind mount from clobbering
```

For global tool injection, this isn't an issue because we're mounting a dedicated tools directory, not the application directory.

### Pattern 3: Native Binary Volume Mounting with Shared Library Dependencies

Native binaries (gitleaks, babashka, opencode) are simpler than npm packages in structure but face shared library dependency challenges.

**The shared library challenge**:

Native binaries dynamically link to system libraries (.so files). According to [blog post on creating minimal Docker images from dynamically linked binaries](https://blog.oddbit.com/post/2015-02-05-creating-minimal-docker-images/), you can use `ldd` to list dependencies:

```bash
$ ldd /tools/bin/gitleaks
        linux-vdso.so.1 (0x00007ffc3d5e8000)
        libpthread.so.0 => /lib/x86_64-linux-gnu/libpthread.so.0
        libc.so.6 => /lib/x86_64-linux-gnu/libc.so.6
        /lib64/ld-linux-x86-64.so.2
```

**Three approaches to handle this**:

1. **Static linking (recommended for cross-distro compatibility)**: Build binaries with static linking. Go binaries can be built with `CGO_ENABLED=0` to produce fully static binaries. According to [GitHub gist on binary dependencies](https://gist.github.com/bcardiff/85ae47e66ff0df35a78697508fcb49af), this is the simplest approach for minimal Docker images.

2. **Ensure target container has compatible libraries**: If both the tools image and target image use the same base (e.g., `debian:bookworm-slim`), shared libraries should be compatible. Mount only the binaries:

```bash
docker run -v tools-bin:/tools/bin -e PATH=/tools/bin:$PATH my-app:latest
```

3. **Mount libraries alongside binaries and set LD_LIBRARY_PATH**: Extract all dependencies and mount them:

```bash
# In tools image build
RUN ldd /tools/bin/gitleaks | tr -s '[:blank:]' '\n' | grep '^/' | \
  xargs -I % sh -c 'mkdir -p /tools/lib/$(dirname %); cp % /tools/lib%;'

# Runtime
docker run -v tools:/tools -e PATH=/tools/bin:$PATH \
  -e LD_LIBRARY_PATH=/tools/lib:$LD_LIBRARY_PATH my-app:latest
```

**Critical gotcha: LD_LIBRARY_PATH pitfalls**

According to [HPC DTU article on LD_LIBRARY_PATH](https://www.hpc.dtu.dk/?page_id=1180), "A realistic answer is 'the less you use LD_LIBRARY_PATH, the better off you will be.'" The recommended approach for custom library locations is to add paths to `/etc/ld.so.conf.d/` and run `ldconfig`, but this requires modifying the target container image.

For babashka specifically, the [babashka GitHub repository](https://github.com/babashka/babashka) notes it's distributed as a "standalone, natively-compiled binary that executes directly" with minimal dependencies, making it a good candidate for volume mounting.

**Cross-distribution compatibility**: According to community discussions on [shared library dependencies in Docker](https://gist.github.com/bcardiff/85ae47e66ff0df35a78697508fcb49af), dynamically linked binaries commonly fail when moving between Alpine (musl libc) and Debian (glibc) distributions. For maximum compatibility across potential extension images, static binaries are strongly preferred.

### Pattern 4: Sidecar Container Pattern with --volumes-from

An alternative to mounting volumes directly is the **data container pattern** or **sidecar pattern**, where a dedicated container holds the tools and shares them via volumes.

**Historical context**: According to [Medium article on Docker data containers](https://medium.com/@rasheedamir/docker-good-bye-data-only-container-pattern-a28f90493a5a), the "data-only-container" pattern was widely used before Docker 1.9. With modern Docker (1.9+), named volumes are preferred, but `--volumes-from` still has value for tool sharing.

**Implementation**:

```bash
# Create tools container (runs once, exits)
docker create --name tools-container \
  -v /tools \
  aishell/tools:latest

# Use tools in application containers
docker run --volumes-from tools-container my-app:latest
```

According to [Docker official volumes documentation](https://docs.docker.com/engine/storage/volumes/), `--volumes-from` "pulls in all the volume definitions attached to a container." This is particularly useful in backup scenarios, but also for shared tooling.

**Modern alternative with named volumes in Docker Compose**:

According to [Baeldung guide on sharing volumes in Docker Compose](https://www.baeldung.com/ops/docker-share-volume-multiple-containers), the preferred approach is top-level named volumes:

```yaml
services:
  tools:
    image: aishell/tools:latest
    volumes:
      - tools-volume:/tools

  app:
    image: my-app:latest
    volumes:
      - tools-volume:/tools
    environment:
      - PATH=/tools/npm/bin:/tools/bin:$PATH
      - NODE_PATH=/tools/npm/lib/node_modules

volumes:
  tools-volume:
```

**Kubernetes equivalent**: The research on [sidecar containers in Kubernetes](https://kubernetes.io/docs/concepts/workloads/pods/sidecar-containers/) shows this pattern is formalized in K8s. According to the official docs, "Sidecar containers run alongside app containers in the same Pod, using the same network namespace and shared volumes." The pattern directly applies to Docker multi-container setups.

**Benefit**: Tool updates are centralized. Update the `aishell/tools` image, recreate the tools container/volume, and all dependent containers use the new version on next start.

**Gotcha**: `--volumes-from` shares ALL volumes from the source container, not just specific paths. For fine-grained control, use explicit named volume mounts instead.

### Pattern 5: Entrypoint Script with Startup Initialization

For maximum flexibility with minimal image changes, an entrypoint script can set up tool access at container startup.

**Basic pattern**:

```bash
#!/bin/bash
# entrypoint.sh

# Check if tools volume is mounted
if [ -d "/tools/npm/bin" ]; then
  export PATH="/tools/npm/bin:$PATH"
  export NODE_PATH="/tools/npm/lib/node_modules"
fi

if [ -d "/tools/bin" ]; then
  export PATH="/tools/bin:$PATH"
fi

# Optionally create symlinks for discoverability
if [ -d "/tools/bin" ]; then
  for binary in /tools/bin/*; do
    ln -sf "$binary" /usr/local/bin/$(basename "$binary")
  done
fi

# Execute main command
exec "$@"
```

According to [Medium article on Docker entrypoint scripts](https://medium.com/@leonardo5621_66451/learn-how-to-use-entrypoint-scripts-in-docker-images-fede010f172d), entrypoint scripts are commonly used for "conditional startup logic, waiting for dependent services, handling setup tasks."

**Performance overhead**: The script adds 1-3 seconds of startup time for symlink creation and PATH updates. For interactive development workflows, this is acceptable. For high-frequency container spawning (CI/CD), it may be noticeable but is still preferable to full image rebuilds.

**Critical gotcha: symlink issues with bind mounts**

According to [GitHub issue on ENTRYPOINT symlink behavior](https://github.com/docker/cli/issues/337), "ENTRYPOINT does not work with symlinks" in certain contexts. More importantly, [GitHub issue on bind-mounting to symlink mount points](https://github.com/moby/moby/issues/17944) notes that "when bind-mounting a file to a location which is a symlink in the container, that symlink isn't replaced with the bind-mounted binary, but the target of the symlink gets replaced."

**Recommendation**: Use direct PATH manipulation rather than symlinks when working with bind mounts:

```bash
export PATH="/tools/npm/bin:/tools/bin:$PATH"
```

This is more reliable than symlinks and avoids the bind-mount interaction issue.

**Volume mount timing**: According to [Docker community forum on entrypoint execution order](https://forums.docker.com/t/container-initialisation-the-correct-way/147013), "Mounts are performed prior to executing anything inside the container." This means the entrypoint script can reliably check for mounted volumes and act accordingly.

### Pattern 6: BuildKit Cache Mounts (Build-Time Only, Not Runtime)

BuildKit's `RUN --mount=type=cache` feature was extensively discussed in research but is **not applicable for runtime tool injection**. According to [GitHub issue on cache mount runtime availability](https://github.com/moby/buildkit/issues/2147), "When building an image with cache mounts, when you try to execute commands in the container, the cache is not there—node_modules exists, but is empty."

Cache mounts are only available during build, not in the final image or at runtime. They are useful for **speeding up tool installation** during image builds but do not support runtime volume-based injection.

**Correct use case** (build optimization, not runtime injection):

```dockerfile
RUN --mount=type=cache,target=/root/.npm \
  npm install -g claude-code codex gemini-cli
```

This caches npm downloads across builds, reducing rebuild time when package versions change. However, the installed packages still end up in the image layer, not in a runtime-mountable volume.

### Architectural Recommendation for aishell

Based on the research findings, the recommended pattern for aishell is a **hybrid approach**:

**1. Pre-built tools image with custom npm prefix**:

```dockerfile
# aishell-tools.Dockerfile
FROM debian:bookworm-slim
RUN apt-get update && apt-get install -y nodejs npm curl tar

# npm packages to /tools/npm
ENV NPM_CONFIG_PREFIX=/tools/npm
RUN npm install -g claude-code codex gemini-cli

# Native binaries to /tools/bin (static-linked where possible)
RUN curl -L https://github.com/gitleaks/gitleaks/releases/download/v8.18.0/gitleaks_8.18.0_linux_x64.tar.gz | \
    tar -xz -C /tools/bin
# ... babashka, opencode

# Gemini CLI requires Node.js runtime (no native binary available)
# Already in /tools/npm/bin via npm global install
```

**2. Named volume for tools (auto-populated on first run)**:

```bash
# Initialize tools volume
docker run --rm -v aishell-tools:/tools aishell/tools:latest true
```

**3. Runtime volume mount with entrypoint PATH setup**:

```bash
# In aishell run script
docker run -v aishell-tools:/tools \
  --entrypoint /entrypoint-with-tools.sh \
  my-project-extension:latest
```

**Entrypoint**:

```bash
#!/bin/bash
export PATH="/tools/npm/bin:/tools/bin:$PATH"
export NODE_PATH="/tools/npm/lib/node_modules"
exec "$@"
```

**4. Version management with named volume versioning**:

```bash
# Update tools
docker build -t aishell/tools:v1.2.3 -f aishell-tools.Dockerfile .
docker tag aishell/tools:v1.2.3 aishell/tools:latest

# Create new versioned volume
docker run --rm -v aishell-tools-v1.2.3:/tools aishell/tools:v1.2.3 true

# Update aishell script to use versioned volume
docker run -v aishell-tools-v1.2.3:/tools ...
```

**Benefits of this approach**:

- Extension images (`my-project-extension:latest`) do not depend on tools being in the base image
- Tools can be updated independently by rebuilding `aishell/tools` and recreating the volume
- No cascade invalidation of extension image layers
- Startup overhead is minimal (1-2 seconds for PATH setup)
- Compatible with existing `.aishell/Dockerfile` extension pattern

**Tradeoffs**:

- Requires managing a separate tools image and volume
- First-run initialization step (negligible: `docker run ... true`)
- Tools are not discoverable via `docker inspect` of the application image (they're in a volume)
- Debugging requires checking volume contents (`docker run --rm -v aishell-tools:/tools alpine ls -R /tools`)

### Permission Considerations

The research identified permission mismatches as a common gotcha with bind mounts. According to [Docker community forum on bind mount permissions](https://forums.docker.com/t/bind-mount-permissions/146262), "bind mounts can cause permission issues due to UID/GID mismatches between host and container."

For **named volumes** (recommended pattern), this is less of an issue because Docker manages ownership. However, files copied from the image during volume initialization inherit the image's UID/GID.

The aishell project already handles dynamic user creation (based on existing code context), which ensures the container user matches the host user. When tools are installed in the image as root and copied to a named volume, the volume files are owned by root. The container user (matching host UID/GID) needs read+execute permissions.

**Solution**: Ensure tools are installed with world-readable/executable permissions in the tools image:

```dockerfile
RUN chmod -R a+rX /tools
```

This allows any user (including the dynamically created container user) to read and execute tools.

## Sources

**Primary Sources** (Tier 1):

- [Docker Volumes Documentation](https://docs.docker.com/engine/storage/volumes/)
- [Docker Bind Mounts Documentation](https://docs.docker.com/engine/storage/bind-mounts/)
- [Docker Multi-Stage Builds Documentation](https://docs.docker.com/build/building/multi-stage/)
- [Docker Build Cache Optimization](https://docs.docker.com/build/cache/optimize/)
- [npm Folders Documentation](https://docs.npmjs.com/cli/v10/configuring-npm/folders/)
- [Kubernetes Sidecar Containers](https://kubernetes.io/docs/concepts/workloads/pods/sidecar-containers/)

**Expert Analysis** (Tier 2):

- [Docker Volumes and the node_modules Conundrum - Medium](https://medium.com/@justinecodez/docker-volumes-and-the-node-modules-conundrum-fef34c230225)
- [Node.js Module Resolution Algorithm - Medium](https://medium.com/outbrain-engineering/node-js-module-resolution-af46715784ef)
- [Creating Minimal Docker Images from Dynamically Linked Binaries - Oddbit Blog](https://blog.oddbit.com/post/2015-02-05-creating-minimal-docker-images/)
- [Shared Libs: Linking, LD_LIBRARY_PATH, rpath, & ldconfig - CircuitLabs](https://circuitlabs.net/shared-libs-linking-ld_library_path-rpath-ldconfig/)
- [How to Share Data Between Docker Containers - DigitalOcean](https://www.digitalocean.com/community/tutorials/how-to-share-data-between-docker-containers)
- [Docker: Good-bye Data-Only-Container Pattern - Medium](https://medium.com/@rasheedamir/docker-good-bye-data-only-container-pattern-a28f90493a5a)
- [nodejs/docker-node Best Practices](https://github.com/nodejs/docker-node/blob/main/docs/BestPractices.md)
- [Understanding Docker Volumes - Earthly Blog](https://earthly.dev/blog/docker-volumes/)
- [Learn How to Use Entrypoint Scripts in Docker Images - Medium](https://medium.com/@leonardo5621_66451/learn-how-to-use-entrypoint-scripts-in-docker-images-fede010f172d)

**Metrics and Trends** (Tier 3):

- [Share Volume Between Multiple Containers in Docker Compose - Baeldung](https://www.baeldung.com/ops/docker-share-volume-multiple-containers)
- [Kubernetes Sidecar Container - Best Practices - Spacelift](https://spacelift.io/blog/kubernetes-sidecar-container)
- [Docker Community Forum: Volume Initialization](https://forums.docker.com/t/volume-overwrites-the-directory-inside-the-docker-container/137031)
- [Docker Community Forum: Bind Mount Permissions](https://forums.docker.com/t/bind-mount-permissions/146262)
- [GitHub: moby/moby issue #20390 - Volume initialization](https://github.com/moby/moby/issues/20390)
- [GitHub: moby/buildkit issue #2147 - Cache mount runtime availability](https://github.com/moby/buildkit/issues/2147)
- [GitHub: docker/cli issue #337 - ENTRYPOINT symlink behavior](https://github.com/docker/cli/issues/337)
- [GitHub: moby/moby issue #17944 - Bind-mounting to symlink](https://github.com/moby/moby/issues/17944)
- [GitHub: esbuild issue #1117 - NODE_PATH resolution precedence](https://github.com/evanw/esbuild/issues/1117)
- [GitHub: babashka repository](https://github.com/babashka/babashka)
- [GitHub: List binary dependencies gist](https://gist.github.com/bcardiff/85ae47e66ff0df35a78697508fcb49af)

## Confidence Assessment

**Overall Confidence**: High

**Factors**:

- Docker volume auto-population behavior is documented in official Docker documentation and confirmed across multiple community sources
- npm global package installation structure and NODE_PATH resolution are well-documented in npm official docs and Node.js module resolution specifications
- Shared library dependency challenges with `ldd` and `LD_LIBRARY_PATH` are extensively covered in Linux system administration documentation and Docker community best practices
- Sidecar pattern and `--volumes-from` behavior are documented in official Docker docs and widely discussed in Kubernetes documentation
- Entrypoint script patterns are standard practice with consistent behavior across Docker versions
- Permission handling with named volumes vs bind mounts is well-understood and documented

**Gaps**:

- Real-world performance benchmarks for entrypoint PATH setup overhead with 10+ tools are not documented; 1-3 second estimate is based on typical script execution time but not empirically measured for this specific use case
- Cross-distribution compatibility of specific binaries (gitleaks, babashka, opencode) between aishell's current base (assumed Debian bookworm-slim based on codebase context) and various potential extension base images is assumed but not explicitly tested
- Volume initialization behavior with very large tool sets (1GB+ of npm packages) may have performance implications not covered in documentation
- Named volume versioning and cleanup strategy (handling old volumes when tools update) is a procedural gap—Docker doesn't provide built-in version management for volumes
- BuildKit cache mount persistence across different build environments (local vs CI) remains an open issue per GitHub discussions, though this doesn't affect the recommended runtime volume approach

**Strengths**:

- Architectural recommendation is grounded in multiple converging official sources (Docker docs, npm docs, Kubernetes patterns)
- All gotchas identified are supported by specific GitHub issues or community forum discussions with reproducible examples
- The hybrid approach (named volumes + entrypoint PATH setup) combines proven patterns from multiple domains (npm packaging, Docker volume management, container initialization)
- Permission considerations align with aishell's existing dynamic user creation pattern (confirmed via codebase context)

**Recommendation**: The volume-based injection pattern is viable and well-supported for the aishell use case, with the caveat that initial setup requires creating and managing versioned named volumes alongside the tools image.
