# Phase 7: Node.js and Clojure Tooling - Research

**Researched:** 2026-01-18
**Domain:** Developer tooling (Node.js, Babashka, bbin) on Debian bookworm-slim
**Confidence:** HIGH

## Summary

This phase adds Node.js LTS, Babashka (bb), and bbin to the aishell base image. The research identifies the cleanest installation methods for Docker containers with dynamically created users.

**Key findings:**
1. **Node.js**: Use multi-stage build to copy from official `node:24-bookworm-slim` image. This avoids NodeSource repository issues and provides exact version control.
2. **Babashka**: Download static binary directly from GitHub releases to `/usr/local/bin/bb`. Version 1.12.214 is current LTS.
3. **bbin**: Download script to `/usr/local/bin/bbin` at build time. Requires Babashka 0.9.162+. Version 0.2.5 is current.
4. **User compatibility**: All tools must be installed in system paths (`/usr/local/bin`) since the user is created dynamically at container startup.

**Primary recommendation:** Use multi-stage Docker build for Node.js and direct binary downloads for Babashka/bbin. Install to `/usr/local/bin` for system-wide availability before user creation.

## Standard Stack

The established tools for this domain:

### Core
| Tool | Version | Purpose | Why Standard |
|------|---------|---------|--------------|
| Node.js | 24.x LTS (Krypton) | JavaScript runtime | Current LTS, supported until April 2028 |
| npm | bundled with Node | Package manager | Ships with Node.js, no separate install |
| Babashka (bb) | 1.12.214 | Fast Clojure scripting | Native binary, instant startup, batteries included |
| bbin | 0.2.5 | Babashka script installer | Official tool for installing bb scripts |

### Version Details
| Tool | Current Version | Release Date | End of Support |
|------|-----------------|--------------|----------------|
| Node.js 24 LTS | 24.13.0 | Jan 13, 2026 | April 2028 |
| Node.js 22 LTS | 22.22.0 | Jan 12, 2026 | April 2027 |
| Babashka | 1.12.214 | Jan 13, 2026 | Rolling |
| bbin | 0.2.5 | Recent | Rolling |

### Alternatives Considered
| Instead of | Could Use | Tradeoff |
|------------|-----------|----------|
| Multi-stage Node copy | NodeSource apt repo | NodeSource has issues with Node 24 on Debian; less version control |
| Multi-stage Node copy | fnm/nvm | Adds complexity; user-level install conflicts with dynamic user |
| Direct binary download (bb) | Docker COPY --from | Official bb image uses Alpine base; static binary simpler |
| Direct script download (bbin) | Homebrew | Homebrew adds ~500MB; unnecessary for single script |

**Installation approach:**
```dockerfile
# Node.js: Multi-stage copy from official image
FROM node:24-bookworm-slim AS node-source
FROM debian:bookworm-slim
COPY --from=node-source /usr/local/bin/node /usr/local/bin/
COPY --from=node-source /usr/local/lib/node_modules /usr/local/lib/node_modules
RUN ln -s /usr/local/lib/node_modules/npm/bin/npm-cli.js /usr/local/bin/npm \
    && ln -s /usr/local/lib/node_modules/npm/bin/npx-cli.js /usr/local/bin/npx

# Babashka: Direct binary download
RUN curl -fsSL https://github.com/babashka/babashka/releases/download/v1.12.214/babashka-1.12.214-linux-amd64-static.tar.gz \
    | tar -xz -C /usr/local/bin bb

# bbin: Direct script download
RUN curl -fsSL -o /usr/local/bin/bbin https://raw.githubusercontent.com/babashka/bbin/v0.2.5/bbin \
    && chmod +x /usr/local/bin/bbin
```

## Architecture Patterns

### Recommended Installation Location
```
/usr/local/bin/
├── node         # Node.js binary (copied from official image)
├── npm          # Symlink to npm-cli.js
├── npx          # Symlink to npx-cli.js
├── bb           # Babashka binary (downloaded)
└── bbin         # bbin script (downloaded)

/usr/local/lib/
└── node_modules/
    └── npm/     # npm package (copied from official image)
```

### Pattern 1: Multi-Stage Copy for Node.js
**What:** Copy Node.js binaries from official Docker image instead of using package managers
**When to use:** When you need specific Node.js versions in a different base image
**Why:** Avoids repository issues, exact version control, faster builds, smaller attack surface

```dockerfile
# Source: https://gist.github.com/BretFisher/da34530726ff8076b83b583e527e91ed
FROM node:24-bookworm-slim AS node
FROM debian:bookworm-slim

# Copy Node.js runtime
COPY --from=node /usr/local/bin/node /usr/local/bin/node
COPY --from=node /usr/local/lib/node_modules /usr/local/lib/node_modules

# Create npm/npx symlinks
RUN ln -s /usr/local/lib/node_modules/npm/bin/npm-cli.js /usr/local/bin/npm \
    && ln -s /usr/local/lib/node_modules/npm/bin/npx-cli.js /usr/local/bin/npx
```

### Pattern 2: Direct Binary Download for Babashka
**What:** Download pre-compiled static binary directly from GitHub releases
**When to use:** For native tools that provide pre-built binaries
**Why:** No build tools needed, reproducible, small footprint

```dockerfile
# Source: https://github.com/babashka/babashka
ARG BABASHKA_VERSION=1.12.214
RUN set -eux; \
    curl -fsSL "https://github.com/babashka/babashka/releases/download/v${BABASHKA_VERSION}/babashka-${BABASHKA_VERSION}-linux-amd64-static.tar.gz" \
    | tar -xz -C /usr/local/bin bb; \
    chmod +x /usr/local/bin/bb; \
    bb --version
```

### Pattern 3: Script Download for bbin
**What:** Download bbin script and make executable
**When to use:** For script-based tools distributed as single files
**Why:** Minimal, no dependencies beyond bb

```dockerfile
# Source: https://github.com/babashka/bbin
ARG BBIN_VERSION=0.2.5
RUN curl -fsSL -o /usr/local/bin/bbin \
    "https://raw.githubusercontent.com/babashka/bbin/v${BBIN_VERSION}/bbin" \
    && chmod +x /usr/local/bin/bbin
```

### Anti-Patterns to Avoid
- **User-level installation paths:** Don't install to `~/.local/bin` or `~/.npm-global` in Dockerfile since user doesn't exist at build time
- **nvm/fnm in containers:** Adds complexity for single Node.js version; better for local development
- **NodeSource for Node 24:** Repository has known issues as of Jan 2026; use official image instead
- **Dynamic binary fetch without version pinning:** Always pin versions for reproducibility

## Don't Hand-Roll

Problems that look simple but have existing solutions:

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| Node.js installation | apt repo setup | Multi-stage COPY from node:24 | Cleaner, fewer layers, exact version |
| npm symlinks | Manual path management | Official image paths | Already solved correctly |
| Babashka compilation | GraalVM build | Pre-built static binary | Binary is 22MB, no JVM needed |
| Script installation | Custom installer | bbin | Official tool, handles all sources |

**Key insight:** All three tools provide official installation methods optimized for their use cases. Node.js official images handle the complexity of npm symlinks. Babashka provides static binaries that work without glibc dependencies. bbin is a single script file.

## Common Pitfalls

### Pitfall 1: User Path vs System Path
**What goes wrong:** Installing to `~/.local/bin` during Docker build, but user created at runtime
**Why it happens:** Following local development patterns in Docker context
**How to avoid:** Always install to `/usr/local/bin` for system-wide availability
**Warning signs:** Commands work as root but fail for runtime user

### Pitfall 2: NodeSource Repository Issues
**What goes wrong:** `apt-get update` fails with "Release file not found" for Node 24
**Why it happens:** NodeSource repository for Node 24 has known issues on Debian bookworm
**How to avoid:** Use multi-stage build from official Node.js Docker image
**Warning signs:** Installation works in CI but fails with new Node versions

### Pitfall 3: Dynamic vs Static Babashka Binary
**What goes wrong:** Babashka fails with "GLIBC not found" errors
**Why it happens:** Using dynamic binary on different glibc version
**How to avoid:** Always use `-static` binary variant for containers
**Warning signs:** Works locally, fails in container

### Pitfall 4: Missing npm Symlinks
**What goes wrong:** `npm` command not found after copying Node.js
**Why it happens:** npm is in node_modules, not directly in bin
**How to avoid:** Create symlinks for npm-cli.js and npx-cli.js
**Warning signs:** `node --version` works but `npm --version` fails

### Pitfall 5: bbin Without Babashka
**What goes wrong:** `bbin: command not found` or script errors
**Why it happens:** bbin is a Babashka script, requires bb on PATH
**How to avoid:** Install Babashka before bbin, ensure bb is in PATH
**Warning signs:** bbin download succeeds but execution fails

## Code Examples

Verified patterns from official sources:

### Complete Dockerfile Additions
```dockerfile
# Source: Combined from official docs and verified patterns

# Build arg for version pinning
ARG NODE_VERSION=24
ARG BABASHKA_VERSION=1.12.214
ARG BBIN_VERSION=0.2.5

# Multi-stage: Get Node.js from official image
FROM node:${NODE_VERSION}-bookworm-slim AS node-source

# Main image continues from existing base
FROM debian:bookworm-slim

# ... existing apt-get install and gosu setup ...

# Install Node.js via multi-stage copy
COPY --from=node-source /usr/local/bin/node /usr/local/bin/node
COPY --from=node-source /usr/local/lib/node_modules /usr/local/lib/node_modules
RUN ln -s /usr/local/lib/node_modules/npm/bin/npm-cli.js /usr/local/bin/npm \
    && ln -s /usr/local/lib/node_modules/npm/bin/npx-cli.js /usr/local/bin/npx \
    && node --version \
    && npm --version

# Install Babashka (static binary for glibc independence)
RUN set -eux; \
    curl -fsSL "https://github.com/babashka/babashka/releases/download/v${BABASHKA_VERSION}/babashka-${BABASHKA_VERSION}-linux-amd64-static.tar.gz" \
    | tar -xz -C /usr/local/bin bb; \
    chmod +x /usr/local/bin/bb; \
    bb --version

# Install bbin (Babashka script installer)
RUN curl -fsSL -o /usr/local/bin/bbin \
    "https://raw.githubusercontent.com/babashka/bbin/v${BBIN_VERSION}/bbin" \
    && chmod +x /usr/local/bin/bbin \
    && bbin version

# ... rest of Dockerfile ...
```

### Verification Commands
```bash
# Verify Node.js
node --version    # Should output: v24.x.x
npm --version     # Should output: 10.x.x (bundled with Node 24)
npx --version     # Should output: 10.x.x

# Verify Babashka
bb --version      # Should output: babashka v1.12.214

# Verify bbin
bbin version      # Should output: bbin 0.2.5

# Test as non-root user (after gosu switch)
# All commands should produce same output
```

### bbin Usage Example
```bash
# Install a Babashka script globally
bbin install io.github.babashka/neil

# Install from URL
bbin install https://example.com/script.clj

# List installed scripts
bbin ls
```

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| NodeSource apt repo | Multi-stage copy from official image | 2025 (Node 24 issues) | More reliable, fewer dependencies |
| nvm in Dockerfile | Direct Node.js copy | 2024 | Simpler, faster builds |
| Babashka via Homebrew | Direct binary download | 2023 | No Homebrew dependency |
| bbin to ~/.babashka | bbin to ~/.local/bin | bbin 0.2.0 (2024) | XDG compliance |

**Deprecated/outdated:**
- **NodeSource for Node 24**: Has repository issues on Debian bookworm; use official Docker image
- **Babashka dynamic binary**: Use static binary for container compatibility
- **bbin ~/.babashka/bbin/bin**: Old default path; now uses ~/.local/bin

## Open Questions

Things that couldn't be fully resolved:

1. **Corepack for alternative package managers**
   - What we know: Node.js 24 includes Corepack for yarn/pnpm
   - What's unclear: Whether to enable by default in container
   - Recommendation: Don't enable; users can enable via project extension if needed

2. **ARM64/aarch64 support**
   - What we know: All tools have ARM64 binaries
   - What's unclear: Whether to support multi-arch in aishell
   - Recommendation: Defer to future phase; focus on amd64 for now

3. **bbin without releases page**
   - What we know: bbin uses git tags (v0.2.5) but no GitHub releases
   - What's unclear: How to verify downloads/checksums
   - Recommendation: Use raw.githubusercontent.com URL with version tag; acceptable risk

## Requirements Definition

Phase 7 introduces new requirements (DEV-01, DEV-02, DEV-03). Based on research, these should be:

### DEV-01: Node.js LTS Available
**Requirement:** Node.js LTS is available in the container
**Verification:** `node --version` outputs version number, `npm --version` outputs version number
**Implementation:** Multi-stage copy from official node:24-bookworm-slim image

### DEV-02: Babashka Available
**Requirement:** Babashka (bb) is available in the container for Clojure scripting
**Verification:** `bb --version` outputs version number
**Implementation:** Direct download of static binary from GitHub releases

### DEV-03: bbin Available
**Requirement:** bbin is available in the container for installing Babashka scripts
**Verification:** `bbin version` outputs version number
**Implementation:** Direct download of bbin script from GitHub repository

## Sources

### Primary (HIGH confidence)
- [Node.js Official Releases](https://nodejs.org/en/about/previous-releases) - LTS version information
- [Node.js Docker Hub](https://hub.docker.com/_/node) - Official image tags
- [Babashka GitHub Releases](https://github.com/babashka/babashka/releases) - v1.12.214 binary URLs
- [bbin GitHub](https://github.com/babashka/bbin) - Installation documentation
- [bbin Changelog](https://github.com/babashka/bbin/blob/main/CHANGELOG.md) - v0.2.5 release notes
- [Docker Multi-stage Builds](https://docs.docker.com/build/building/multi-stage/) - Official documentation
- [Bret Fisher Node.js Multi-stage Gist](https://gist.github.com/BretFisher/da34530726ff8076b83b583e527e91ed) - Verified pattern

### Secondary (MEDIUM confidence)
- [NodeSource GitHub Issue #1864](https://github.com/nodesource/distributions/issues/1864) - Node 24 repo issues
- [Docker Node Best Practices](https://github.com/nodejs/docker-node/blob/main/docs/BestPractices.md) - npm global config
- [Babashka Install Script](https://raw.githubusercontent.com/babashka/babashka/master/install) - Installation options

### Tertiary (LOW confidence)
- Community blog posts about fnm in Docker (considered but not recommended)

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH - Official sources, verified downloads, version numbers confirmed
- Architecture: HIGH - Patterns from official documentation, verified with existing aishell structure
- Pitfalls: MEDIUM - Based on known issues and common patterns, some from community reports

**Research date:** 2026-01-18
**Valid until:** 2026-02-18 (30 days - stable tooling, Node.js LTS cycle)
