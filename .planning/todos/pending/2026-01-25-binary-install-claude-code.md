---
created: 2026-01-25T16:42
updated: 2026-01-25T17:15
title: Binary install all harnesses, conditional Node.js for Gemini only
area: docker
files:
  - src/aishell/docker/templates.clj:52-134
  - src/aishell/docker/build.clj
---

## Problem

The base Docker image is ~579MB, with Node.js being the largest contributor (~200-250MB). Currently:

- **Claude Code**: npm install (deprecated, native binary available)
- **Codex CLI**: npm install (Rust binary available, ~20MB vs ~100MB npm)
- **Gemini CLI**: npm install (**only option** - no native binary exists)
- **OpenCode**: Already using native binary (correct)

Node.js is always included even when only Claude/OpenCode/Codex are requested.

## Research Summary (2026-01-25)

| Harness | Binary Install? | Version Pinning | Binary Size |
|---------|----------------|-----------------|-------------|
| Claude Code | Yes (native) | `bash -s VERSION` | ~50MB |
| OpenCode | Yes (Go) | `VERSION=x.x.x` | ~32MB |
| Codex CLI | Yes (Rust) | GitHub releases | ~20MB |
| Gemini CLI | **No** | npm only | ~12MB (needs Node.js) |

## Solution

1. **Make Node.js conditional** — only copy from `node-source` if `WITH_GEMINI=true`

2. **Claude Code** — switch to native installer:
   ```dockerfile
   RUN if [ "$WITH_CLAUDE" = "true" ]; then \
           curl -fsSL https://claude.ai/install.sh | bash -s ${CLAUDE_VERSION:-}; \
           cp ~/.local/bin/claude /usr/local/bin/claude; \
       fi
   ```

3. **Codex CLI** — switch to binary download:
   ```dockerfile
   RUN if [ "$WITH_CODEX" = "true" ]; then \
           CODEX_VER=${CODEX_VERSION:-latest}; \
           curl -fsSL "https://github.com/openai/codex/releases/download/v${CODEX_VER}/codex-x86_64-unknown-linux-musl.tar.gz" \
           | tar -xz -C /usr/local/bin; \
       fi
   ```

4. **Gemini CLI** — keep npm (no alternative):
   ```dockerfile
   # Only install Node.js if Gemini is requested
   RUN if [ "$WITH_GEMINI" = "true" ]; then \
           npm install -g @google/gemini-cli${GEMINI_VERSION:+@$GEMINI_VERSION}; \
       fi
   ```

5. **Add `DISABLE_AUTOUPDATER=1`** to entrypoint for Claude Code (auto-updates don't make sense in containers)

**Expected image sizes:**
- Claude only: ~300MB (down from ~579MB)
- Claude + OpenCode + Codex: ~350MB
- With Gemini: ~500MB (Node.js required)

**Considerations:**
- Test Claude native installer in container (known hang bug on "Cleaning up old npm installations...")
- Codex binary URL needs architecture detection (amd64/arm64)
- Update cache invalidation hash logic in build.clj

**Sources:**
- https://code.claude.com/docs/en/setup (Claude native installer)
- https://github.com/openai/codex/releases (Codex binaries)
- https://github.com/google-gemini/gemini-cli/issues/14106 (Gemini native requested but not available)
