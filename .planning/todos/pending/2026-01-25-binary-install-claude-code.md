---
created: 2026-01-25T16:42
title: Binary install Claude Code with version pinning
area: docker
files:
  - src/aishell/docker/templates.clj:92-100
  - src/aishell/docker/build.clj
---

## Problem

The base Docker image is ~579MB, with Node.js being the largest contributor (~200-250MB). Currently, Claude Code is installed via npm which requires the full Node.js runtime:

```dockerfile
# Current approach (requires Node.js)
COPY --from=node-source /usr/local/bin/node /usr/local/bin/node
COPY --from=node-source /usr/local/lib/node_modules /usr/local/lib/node_modules
RUN npm install -g @anthropic-ai/claude-code@"$CLAUDE_VERSION"
```

Claude Code now offers a native binary installer that doesn't require Node.js. The npm installation method is deprecated per official docs.

## Solution

Replace npm installation with native binary installer in `templates.clj`:

```dockerfile
# Native binary (no Node.js required)
RUN if [ "$WITH_CLAUDE" = "true" ]; then \
        if [ -n "$CLAUDE_VERSION" ]; then \
            curl -fsSL https://claude.ai/install.sh | bash -s "$CLAUDE_VERSION"; \
        else \
            curl -fsSL https://claude.ai/install.sh | bash; \
        fi; \
        # Copy to /usr/local/bin for PATH accessibility
        cp ~/.local/bin/claude /usr/local/bin/claude; \
    fi
```

**Considerations:**
1. Make Node.js conditional — only install if `WITH_CODEX=true` or `WITH_GEMINI=true`
2. Add `DISABLE_AUTOUPDATER=1` to entrypoint (auto-updates don't make sense in containers)
3. Test the native installer in container context (known bug: may hang showing "Cleaning up old npm installations...")
4. Update cache invalidation logic if install method changes hash

**Expected impact:** ~200-250MB reduction when only Claude Code is needed.

**Sources:**
- https://code.claude.com/docs/en/setup (official docs - native installer recommended)
- https://github.com/anthropics/claude-code/issues/19985 (request to change Dockerfile to native)
- https://github.com/anthropics/claude-code/issues/5209 (Linux hang bug to watch for)
