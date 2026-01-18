---
phase: 07-nodejs-and-clojure-tooling
verified: 2026-01-18T12:30:00Z
status: passed
score: 5/5 must-haves verified
---

# Phase 7: Node.js and Clojure Tooling Verification Report

**Phase Goal:** Add Node.js LTS and Babashka to the base image for enhanced scripting capabilities
**Verified:** 2026-01-18T12:30:00Z
**Status:** passed
**Re-verification:** No - initial verification

## Goal Achievement

### Observable Truths

| # | Truth | Status | Evidence |
|---|-------|--------|----------|
| 1 | User can run `node --version` and see Node.js 24.x | VERIFIED | Multi-stage copy from `node:24-bookworm-slim`, binary at `/usr/local/bin/node`, build-time verification via `node --version` |
| 2 | User can run `npm --version` and see npm version | VERIFIED | Symlink at `/usr/local/bin/npm` from copied node_modules, build-time verification via `npm --version` |
| 3 | User can run `bb --version` and see Babashka version | VERIFIED | Static binary downloaded to `/usr/local/bin/bb` (v1.12.214), build-time verification via `bb --version` |
| 4 | All tools work for non-root container user | VERIFIED | All binaries in `/usr/local/bin/` (world-executable), PATH includes this directory, gosu switches to user before exec |
| 5 | aishell contains Dockerfile heredoc with multi-stage Node.js copy and Babashka download | VERIFIED | `write_dockerfile()` function (lines 106-207) contains complete heredoc with multi-stage build |

**Score:** 5/5 truths verified

### Required Artifacts

| Artifact | Expected | Status | Details |
|----------|----------|--------|---------|
| `aishell` | Updated `write_dockerfile()` with Node.js and Babashka | VERIFIED | Contains multi-stage Dockerfile heredoc with all required installations |

### Key Implementation Details

#### Multi-stage Build Pattern (aishell lines 113-117)
```dockerfile
# Stage 1: Node.js source (for multi-stage copy)
FROM node:24-bookworm-slim AS node-source

# Stage 2: Main image
FROM debian:bookworm-slim
```

#### Node.js Installation (aishell lines 145-150)
```dockerfile
COPY --from=node-source /usr/local/bin/node /usr/local/bin/node
COPY --from=node-source /usr/local/lib/node_modules /usr/local/lib/node_modules
RUN ln -s /usr/local/lib/node_modules/npm/bin/npm-cli.js /usr/local/bin/npm \
    && ln -s /usr/local/lib/node_modules/npm/bin/npx-cli.js /usr/local/bin/npx \
    && node --version \
    && npm --version
```

#### Babashka Installation (aishell lines 125-127, 152-157)
```dockerfile
ARG BABASHKA_VERSION=1.12.214
...
RUN set -eux; \
    curl -fsSL "https://github.com/babashka/babashka/releases/download/v${BABASHKA_VERSION}/babashka-${BABASHKA_VERSION}-linux-amd64-static.tar.gz" \
    | tar -xz -C /usr/local/bin bb; \
    chmod +x /usr/local/bin/bb; \
    bb --version
```

#### Non-root User Access (aishell line 275)
```bash
[[ -d /usr/local/bin ]] && export PATH="/usr/local/bin:$PATH"
```

### Scope Change: bbin Removed

**Status:** Correctly removed per user feedback

bbin was in the original ROADMAP success criteria but was removed during implementation because:
- bbin requires Java runtime, which significantly increases image size
- User explicitly requested removal after initial implementation attempt
- Babashka (bb) alone provides sufficient Clojure scripting capability

This is documented in:
- `07-01-SUMMARY.md`: "Removed bbin after user feedback"
- `07-01-PLAN.md` key-decisions: "Removed bbin after user feedback (requires Java runtime)"

### Anti-Patterns Found

None. The implementation:
- Has no TODO/FIXME comments in the Node.js/Babashka sections
- Uses substantive implementation (not placeholders)
- Includes build-time verification (`node --version`, `npm --version`, `bb --version`)

### Human Verification Required

| # | Test | Expected | Why Human |
|---|------|----------|-----------|
| 1 | Run `aishell --rebuild` then `node --version` in container | Node.js 24.x version output | Requires actual Docker build and container execution |
| 2 | Run `npm --version` in container | npm version output (e.g., 10.x) | Requires actual container execution |
| 3 | Run `bb --version` in container | Babashka 1.12.214 output | Requires actual container execution |
| 4 | Run `npx --version` in container | npx version output | Verifies npx symlink works |
| 5 | Create a file with `node -e "require('fs').writeFileSync('test.txt', 'hello')"` | File created with correct ownership | Verifies non-root user can use Node.js |

## Summary

All 5 must-haves are verified at the code level:

1. **Node.js 24.x**: Multi-stage copy from official `node:24-bookworm-slim` image
2. **npm**: Symlinked from copied node_modules
3. **Babashka**: Static binary download with pinned version (1.12.214)
4. **Non-root access**: All tools in `/usr/local/bin/` which is in PATH after gosu switch
5. **Dockerfile heredoc**: Complete implementation in `write_dockerfile()` function

The implementation follows established patterns:
- Multi-stage builds for efficient binary copying (established in Phase 3)
- Static binary downloads for container compatibility
- Build-time verification of tool installation

**Phase 7 goal achieved.** Node.js LTS and Babashka are available in the base image for enhanced scripting capabilities.

---

*Verified: 2026-01-18T12:30:00Z*
*Verifier: Claude (gsd-verifier)*
