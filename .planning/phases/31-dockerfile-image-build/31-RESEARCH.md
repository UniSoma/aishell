# Phase 31: Dockerfile & Image Build - Research

**Researched:** 2026-01-31
**Domain:** Docker image building, Debian package management, tmux installation
**Confidence:** HIGH

## Summary

This research covers adding tmux to the aishell base Docker image without increasing build time or introducing dependency conflicts. tmux is a terminal multiplexer that enables persistent sessions inside containers, allowing users to detach and reattach to running harness processes.

Key findings:
- Debian Bookworm provides tmux 3.3a-3 via standard apt repositories
- Package size is minimal: 444KB download, 1.1MB installed
- Dependencies (libc6, libevent-core-2.1-7, libtinfo6, libutempter0) are lightweight and likely already present
- Installation follows standard apt-get pattern already used in the Dockerfile
- Expected image size increase: 1-2MB (well under 5MB threshold)
- No dependency conflicts expected with existing packages

**Primary recommendation:** Add tmux to the existing RUN instruction that installs system packages in `templates.clj`, using `--no-install-recommends` flag to minimize dependencies. This is a one-line change to the package list with zero build time impact.

## Standard Stack

The established tools for this domain:

### Core
| Component | Version | Purpose | Why Standard |
|-----------|---------|---------|--------------|
| tmux | 3.3a-3 (Debian Bookworm) | Terminal multiplexer for persistent sessions | Official Debian package, stable, widely used |
| apt-get | System default | Debian package manager | Standard for Debian-based images |
| debian:bookworm-slim | Base image | Minimal Debian base | Already in use, tmux available in repos |

### Supporting
| Component | Version | Purpose | When to Use |
|-----------|---------|---------|-------------|
| libevent-core-2.1-7 | ≥2.1.8-stable | Async event library (tmux dependency) | Auto-installed with tmux |
| libtinfo6 | ≥6 | Terminal info library (tmux dependency) | Auto-installed with tmux |
| libutempter0 | ≥1.1.5 | utmp/wtmp updates (tmux dependency) | Auto-installed with tmux |

### Alternatives Considered
| Instead of | Could Use | Tradeoff |
|------------|-----------|----------|
| tmux | GNU Screen | tmux has better defaults, more active development, better split-pane support |
| apt-get install | Build from source | Source build adds complexity, build time, and dependencies (build-essential, libevent-dev, ncurses-dev) |
| Debian package | Backports version 3.5a-2 | Unnecessary complexity; 3.3a meets all requirements |

**Installation:**
```dockerfile
# Add to existing RUN instruction in templates.clj
RUN apt-get update && apt-get install -y --no-install-recommends \
    bash \
    ca-certificates \
    curl \
    file \
    git \
    htop \
    jq \
    less \
    ripgrep \
    sqlite3 \
    sudo \
    tmux \      # ADD THIS LINE
    tree \
    unzip \
    vim \
    watch \
    && rm -rf /var/lib/apt/lists/*
```

## Architecture Patterns

### Pattern 1: Single-Layer Package Installation
**What:** Install all system packages in one RUN instruction to minimize layers
**When to use:** All apt-get package installations in base image
**Example:**
```dockerfile
# Source: Docker best practices + existing templates.clj
# Install required packages in single layer
RUN apt-get update && apt-get install -y --no-install-recommends \
    package-a \
    package-b \
    tmux \
    package-z \
    && rm -rf /var/lib/apt/lists/*
```

**Why this works:**
- Combines `apt-get update` + `install` + cleanup in one layer
- Prevents Docker cache issues (stale package index)
- `--no-install-recommends` prevents unnecessary recommended packages
- `rm -rf /var/lib/apt/lists/*` removes apt cache (~40MB savings)
- Single layer minimizes final image size

### Pattern 2: Alphabetical Package Sorting
**What:** Keep package list alphabetically sorted for maintainability
**When to use:** All package lists in Dockerfile
**Example:**
```dockerfile
# Current pattern in templates.clj (lines 35-51)
RUN apt-get update && apt-get install -y --no-install-recommends \
    bash \
    ca-certificates \
    curl \
    file \
    git \
    htop \
    jq \
    less \
    ripgrep \
    sqlite3 \
    sudo \
    tmux \      # Alphabetically between sudo and tree
    tree \
    unzip \
    vim \
    watch \
    && rm -rf /var/lib/apt/lists/*
```

### Pattern 3: Embedded Template Modification
**What:** Modify the base-dockerfile string constant in templates.clj
**When to use:** All Dockerfile changes for aishell base image
**Example:**
```clojure
;; In src/aishell/docker/templates.clj
(def base-dockerfile
  "# Aishell Base Image
...
# Install required packages in single layer
RUN apt-get update && apt-get install -y --no-install-recommends \\
    bash \\
    ...
    tmux \\
    tree \\
    ...
    && rm -rf /var/lib/apt/lists/*
...")
```

**Why this pattern:**
- Dockerfile is embedded as string constant (not external file)
- Backslash escaping required in multiline string
- Hash-based cache invalidation handles rebuilds automatically
- Single source of truth for all Docker builds

### Anti-Patterns to Avoid
- **Separate RUN for tmux:** Adds unnecessary layer, increases image size
- **apt-get update alone:** Causes cache busting issues (stale package index)
- **Missing --no-install-recommends:** Installs unnecessary recommended packages
- **Missing cleanup:** Leaves 40MB apt cache in image
- **Version pinning tmux:** Unnecessary for stable Debian package, adds maintenance burden

## Don't Hand-Roll

Problems that look simple but have existing solutions:

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| Package installation | Custom RUN per package | Extend existing RUN instruction | Minimizes layers, follows existing pattern |
| Dependency management | Manual apt-get install deps | Let apt-get resolve dependencies | tmux deps auto-installed correctly |
| Image size tracking | Manual comparison | Docker's `docker images` command | Built-in, accurate, includes all layers |
| Cache invalidation | Force rebuild always | Hash-based detection in build.clj | Already implemented, automatic |

**Key insight:** The Dockerfile template is already optimized for package installation. Adding tmux is a matter of inserting one line in the alphabetically-sorted package list. No new RUN instructions, no special handling, no custom logic needed.

## Common Pitfalls

### Pitfall 1: Separating apt-get update and install
**What goes wrong:** Docker caches the update layer separately, causing stale package index
**Why it happens:** Running `RUN apt-get update` in separate instruction from `RUN apt-get install tmux`
**How to avoid:** Always combine in single RUN: `RUN apt-get update && apt-get install -y tmux && rm ...`
**Warning signs:** Build succeeds but installs outdated tmux version, or build fails with "package not found"

### Pitfall 2: Missing --no-install-recommends flag
**What goes wrong:** Image size increases by 5-10MB+ due to recommended but unnecessary packages
**Why it happens:** apt-get installs recommended packages by default
**How to avoid:** Always use `apt-get install -y --no-install-recommends`
**Warning signs:** Image size increase >5MB, unexpected packages in container

### Pitfall 3: Forgetting apt cache cleanup
**What goes wrong:** Image size increases by ~40MB due to cached package lists
**Why it happens:** Not running `rm -rf /var/lib/apt/lists/*` in same RUN instruction
**How to avoid:** Always end package installation RUN with `&& rm -rf /var/lib/apt/lists/*`
**Warning signs:** Image size increase significantly larger than package installed size

### Pitfall 4: Adding tmux in separate RUN layer
**What goes wrong:** Creates additional layer, increases final image size
**Why it happens:** Thinking each package needs its own layer for "modularity"
**How to avoid:** Add to existing package installation RUN instruction
**Warning signs:** `docker history` shows new layer for tmux

### Pitfall 5: Not testing tmux version verification
**What goes wrong:** Image builds but `tmux -V` fails or reports wrong version
**Why it happens:** Assuming installation succeeded without verification
**How to avoid:** Test `docker run aishell:base tmux -V` after build (success criterion 2)
**Warning signs:** Container exists but tmux command not found or errors

### Pitfall 6: Dependency conflicts with existing packages
**What goes wrong:** apt-get fails due to conflicting package versions
**Why it happens:** tmux dependencies conflict with pinned versions of other packages
**How to avoid:** Review existing package list; libevent and ncurses deps are minimal and standard
**Warning signs:** apt-get error "conflicts with..." or "breaks..."

**Likelihood assessment:** Pitfall 6 is LOW risk because tmux dependencies (libc6, libevent-core, libtinfo6, libutempter0) are standard system libraries with no conflicts in Debian Bookworm. The existing Dockerfile doesn't pin specific versions of system libraries, only application packages (Node.js, Babashka, etc.).

## Code Examples

Verified patterns from official sources:

### Adding tmux to Dockerfile
```dockerfile
# Source: Docker best practices + Debian official docs
# Location: src/aishell/docker/templates.clj, line 35-51

# Install required packages in single layer
RUN apt-get update && apt-get install -y --no-install-recommends \
    bash \
    ca-certificates \
    curl \
    file \
    git \
    htop \
    jq \
    less \
    ripgrep \
    sqlite3 \
    sudo \
    tmux \
    tree \
    unzip \
    vim \
    watch \
    && rm -rf /var/lib/apt/lists/*
```

### Testing tmux installation
```bash
# Source: Success criteria 2
# Verify tmux is installed and reports version
docker run aishell:base tmux -V
# Expected output: tmux 3.3a
```

### Checking image size increase
```bash
# Source: Success criteria 3
# Compare image sizes before and after
docker images aishell:base --format "{{.Size}}"
# Expected increase: 1-2MB (not >5MB)
```

### Build cache verification
```bash
# Source: Existing build.clj pattern
# Build should succeed without warnings
aishell build --verbose
# Look for: No dependency conflicts, no warnings
# Verify: Build completes successfully
```

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| Screen multiplexer | tmux multiplexer | ~2010 | Better defaults, active development, modern features |
| Build from source | Debian package | Always (stable distros) | Faster builds, security updates via apt |
| Multiple RUN per package | Single RUN all packages | Docker best practices 2015+ | Smaller images, better layer caching |
| Manual cleanup | Inline cleanup in RUN | Docker best practices 2016+ | Guarantees cleanup, smaller images |

**Deprecated/outdated:**
- GNU Screen: Still available but tmux is preferred for new projects
- Building tmux from source in containers: Adds unnecessary complexity and build time

## Open Questions

Things that couldn't be fully resolved:

1. **Exact image size increase**
   - What we know: Package is 444KB download, 1.1MB installed; dependencies likely already present
   - What's unclear: Whether dependencies are fully satisfied by existing packages
   - Recommendation: Build image and measure actual size increase (expect 1-2MB)

2. **Dependency overlap with existing packages**
   - What we know: libc6, libevent-core-2.1-7, libtinfo6, libutempter0 are tmux dependencies
   - What's unclear: Whether libevent-core is already installed (no libevent in current package list)
   - Recommendation: Let apt-get resolve dependencies; likely 300-500KB additional for libevent-core

3. **Build time impact**
   - What we know: Adding one package to existing RUN shouldn't increase build time
   - What's unclear: Network latency for downloading 444KB package
   - Recommendation: Negligible impact (<1 second); Docker layer cache handles rebuilds

## Sources

### Primary (HIGH confidence)
- [Debian tmux package details](https://packages.debian.org/bookworm/tmux) - Version, size, dependencies verified
- [Docker best practices - RUN instruction](https://docs.docker.com/build/building/best-practices/) - Layer optimization, apt-get patterns
- [Docker build cache optimization](https://docs.docker.com/build/cache/optimize/) - BuildKit cache mounts, layer caching
- [tmux GitHub Wiki - Installing](https://github.com/tmux/tmux/wiki/Installing) - Dependencies, requirements

### Secondary (MEDIUM confidence)
- [How to Reduce Docker Image Size (2026)](https://oneuptime.com/blog/post/2026-01-16-docker-reduce-image-size/view) - Best practices for minimizing image size
- [Docker Layer Caching Guide](https://depot.dev/blog/ultimate-guide-to-docker-build-cache) - BuildKit and cache strategies
- [Common Dockerfile Mistakes](https://www.atlassian.com/blog/developer/common-dockerfile-mistakes) - Anti-patterns to avoid
- [Installing system packages in Docker](https://pythonspeed.com/articles/system-packages-docker/) - Minimal bloat strategies

### Tertiary (LOW confidence)
- WebSearch results on tmux dependencies and disk space - Confirmed 50MB total requirement for Ubuntu (includes all deps)

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH - Official Debian package, verified version and size
- Architecture: HIGH - Extends proven existing patterns from templates.clj
- Pitfalls: HIGH - Based on Docker official docs and common apt-get issues

**Research date:** 2026-01-31
**Valid until:** 90 days (Debian stable packages change slowly)

---
*Phase: 31-dockerfile-image-build*
*Research completed: 2026-01-31*
