# Phase 6: Final Enhancements - Research

**Researched:** 2026-01-18
**Domain:** Version pinning, bash PS1 customization, permission flag handling
**Confidence:** HIGH

## Summary

This research covers the four main areas for Phase 6: version pinning for Claude Code and OpenCode, image tagging strategy for caching, shell prompt optimization, and permission flag handling.

**Claude Code** supports specific version installation via `bash -s <version>` syntax with semantic version numbers (no "v" prefix). **OpenCode** supports version pinning via the `VERSION` environment variable, using GitHub releases URL pattern with "v" prefix. Both tools have clear, documented approaches for version specification.

For **shell prompt optimization**, Bash's `PROMPT_DIRTRIM` variable provides the cleanest solution for showing abbreviated paths while maintaining context. The `--dangerously-skip-permissions` flag is well-supported for containerized environments and is enabled by default in Docker's official sandbox mode.

**Primary recommendation:** Implement version pinning as new CLI flags (`--claude-version`, `--opencode-version`) that modify Dockerfile build-args and image tags, using PROMPT_DIRTRIM=2 for shell prompt, and default to `--dangerously-skip-permissions` with environment variable opt-out.

## Standard Stack

### Core Components

| Component | Implementation | Purpose | Source Confidence |
|-----------|----------------|---------|-------------------|
| Claude Code version | `bash -s <version>` | Install specific Claude Code version | HIGH - Official docs |
| OpenCode version | `VERSION=<version>` env var | Install specific OpenCode version | HIGH - GitHub installer |
| PS1 prompt trimming | `PROMPT_DIRTRIM=N` | Shorten directory path in prompt | HIGH - GNU Bash manual |
| Permission skip | `--dangerously-skip-permissions` | Bypass permission prompts | HIGH - Official docs |

### Version Format Reference

| Tool | Version Format | Example | URL Pattern |
|------|----------------|---------|-------------|
| Claude Code | Semantic (no prefix) | `2.0.22` | N/A (bootstrap script) |
| OpenCode | Semantic (v prefix) | `v1.1.25` | `github.com/.../releases/download/v{version}/...` |

## Architecture Patterns

### Pattern 1: Version Pinning Via Build Args

**What:** Pass version as Docker build-arg, bake into image tag
**When to use:** When caching images per-version is desired
**Source:** Community patterns (VishalJ99/claude-docker)

```bash
# Build-time version specification
docker build \
  --build-arg CLAUDE_VERSION=2.0.22 \
  --build-arg OPENCODE_VERSION=1.1.25 \
  -t aishell:claude-2.0.22 .
```

```dockerfile
# Dockerfile modification
ARG CLAUDE_VERSION=""
ARG OPENCODE_VERSION=""

RUN if [ "$WITH_CLAUDE" = "true" ]; then \
        if [ -n "$CLAUDE_VERSION" ]; then \
            curl -fsSL https://claude.ai/install.sh | bash -s "$CLAUDE_VERSION"; \
        else \
            curl -fsSL https://claude.ai/install.sh | bash; \
        fi && \
        cp /root/.local/bin/claude /usr/local/bin/claude; \
    fi

RUN if [ "$WITH_OPENCODE" = "true" ]; then \
        if [ -n "$OPENCODE_VERSION" ]; then \
            VERSION="$OPENCODE_VERSION" curl -fsSL https://opencode.ai/install | bash; \
        else \
            curl -fsSL https://opencode.ai/install | bash; \
        fi && \
        cp /root/.opencode/bin/opencode /usr/local/bin/opencode; \
    fi
```

### Pattern 2: Image Tagging Strategy

**What:** Include version in image tag for cache discrimination
**When to use:** When different versions need separate cache layers

```bash
# Tag format options (in order of preference)
aishell:claude-2.0.22              # Single harness with version
aishell:opencode-1.1.25            # Single harness with version
aishell:claude-2.0.22-opencode-1.1.25  # Both (verbose but explicit)
aishell:base                       # No harnesses (current)
aishell:ext-{hash}                 # Project extension (current)
```

**Recommendation:** Use harness name + version as suffix to base tag:
- `aishell:claude-2.0.22` when only Claude is installed
- `aishell:opencode-1.1.25` when only OpenCode is installed
- `aishell:claude-2.0.22-opencode-1.1.25` when both are installed
- `aishell:base` becomes `aishell:latest` for unversioned builds

### Pattern 3: PS1 Prompt Shortening

**What:** Use PROMPT_DIRTRIM to limit directory depth in prompt
**When to use:** Container environments with potentially deep paths

```bash
# bashrc.aishell modification
export PROMPT_DIRTRIM=2  # Show last 2 path components

# Result with PROMPT_DIRTRIM=2:
# Full path: /home/user/projects/harness/src/components
# Shows as:  .../src/components

# Current prompt format:
# [aishell] \w \$
# Will display: [aishell] .../src/components $
```

**Alternatives considered:**
- `\W` (basename only): Too little context, loses parent directory info
- `PROMPT_DIRTRIM=1`: Shows only current dir, similar issue
- `PROMPT_DIRTRIM=3`: Good balance, shows grandparent/parent/current
- Custom function: Overkill for simple need

**Recommendation:** `PROMPT_DIRTRIM=2` - shows enough context while keeping prompt concise.

### Pattern 4: Permission Skip with Opt-Out

**What:** Default to `--dangerously-skip-permissions`, allow opt-out via env var
**When to use:** Container is the security sandbox, prompts are unnecessary

```bash
# In aishell script, claude command dispatch:
claude_args=()
if [[ "${AISHELL_SKIP_PERMISSIONS:-true}" != "false" ]]; then
    claude_args+=(--dangerously-skip-permissions)
fi
exec docker run ... "$IMAGE_TO_RUN" claude "${claude_args[@]}" "${HARNESS_ARGS[@]}"
```

**Environment variable pattern:**
- `AISHELL_SKIP_PERMISSIONS=true` (default): Add `--dangerously-skip-permissions`
- `AISHELL_SKIP_PERMISSIONS=false`: Do not add flag (user must approve actions)

### Anti-Patterns to Avoid

- **Building separate images for each version combination:** Use tag strategy instead
- **Version in Dockerfile hardcoded:** Makes it impossible to parameterize
- **PS1 with complex functions for path trimming:** PROMPT_DIRTRIM handles this natively
- **Requiring user to pass --dangerously-skip-permissions manually:** Container is sandbox

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| Path shortening in PS1 | Custom bash function | `PROMPT_DIRTRIM` | Native Bash 4+ feature |
| Version argument parsing | Complex regex | Pass through to installer | Installers validate |
| Docker layer caching | Custom cache invalidation | Docker build cache + tags | Docker handles this |

**Key insight:** Both Claude Code and OpenCode installers already handle version validation and installation; aishell only needs to pass the version through correctly.

## Common Pitfalls

### Pitfall 1: Version Format Mismatch

**What goes wrong:** Using wrong version format breaks installation
**Why it happens:** Claude uses no prefix (`2.0.22`), OpenCode uses v prefix internally but expects no prefix in VERSION env var
**How to avoid:**
- Claude: Pass version directly to `bash -s` (e.g., `bash -s 2.0.22`)
- OpenCode: Set `VERSION=1.1.25` (no v prefix), installer adds it for download URL
**Warning signs:** 404 errors during build, "version not found" messages

### Pitfall 2: Image Tag Collision

**What goes wrong:** Different version builds overwrite same tag
**Why it happens:** Using `aishell:base` regardless of version
**How to avoid:** Include version info in tag when version is specified
**Warning signs:** Running old code after rebuild, cache hits when expecting miss

### Pitfall 3: PROMPT_DIRTRIM Not Working

**What goes wrong:** Full path still shows despite PROMPT_DIRTRIM
**Why it happens:** Using `\W` instead of `\w`, or Bash version < 4
**How to avoid:** Ensure PS1 uses `\w` (lowercase), verify Bash 4+
**Warning signs:** PROMPT_DIRTRIM has no effect on displayed path

### Pitfall 4: Permission Skip Not Applied

**What goes wrong:** Claude still prompts for permissions
**Why it happens:** Flag not passed, or flag appears after other args incorrectly
**How to avoid:** Ensure `--dangerously-skip-permissions` is first arg or in correct position
**Warning signs:** Permission dialogs appear in what should be unattended mode

## Code Examples

### Claude Code Version Installation (Verified)

```bash
# Source: https://code.claude.com/docs/en/setup

# Install latest
curl -fsSL https://claude.ai/install.sh | bash

# Install specific version (no "v" prefix)
curl -fsSL https://claude.ai/install.sh | bash -s 2.0.22

# Install channel
curl -fsSL https://claude.ai/install.sh | bash -s stable
curl -fsSL https://claude.ai/install.sh | bash -s latest
```

### OpenCode Version Installation (Verified)

```bash
# Source: https://github.com/opencode-ai/opencode install script

# Install latest
curl -fsSL https://opencode.ai/install | bash

# Install specific version (no "v" prefix in env var)
VERSION=1.1.25 curl -fsSL https://opencode.ai/install | bash

# Custom install directory
OPENCODE_INSTALL_DIR=/usr/local/bin curl -fsSL https://opencode.ai/install | bash
```

### PS1 with PROMPT_DIRTRIM (Verified)

```bash
# Source: GNU Bash Manual, https://www.gnu.org/software/bash/manual/html_node/Controlling-the-Prompt.html

# Set in bashrc.aishell
export PROMPT_DIRTRIM=2
export PS1='\[\033[0;36m\][aishell]\[\033[0m\] \w \$ '

# Result examples:
# /home/user/projects/harness -> .../projects/harness
# /very/deep/nested/path/here -> .../path/here
```

### Permission Skip Default (Verified)

```bash
# Source: https://docs.docker.com/ai/sandboxes/claude-code/
# "enabled by default in sandboxes"

# aishell script pattern for claude command:
case "$HARNESS_CMD" in
    claude)
        local claude_args=()
        # Default: skip permissions (container is sandbox)
        if [[ "${AISHELL_SKIP_PERMISSIONS:-true}" != "false" ]]; then
            claude_args+=(--dangerously-skip-permissions)
        fi
        exec docker run "${docker_args[@]}" "$IMAGE_TO_RUN" claude "${claude_args[@]}" "${HARNESS_ARGS[@]}"
        ;;
esac
```

## State of the Art

| Area | Old Approach | Current Approach | When Changed | Impact |
|------|--------------|------------------|--------------|--------|
| Claude installation | npm global package | Native binary installer | May 2025 | Faster, no Node.js dependency |
| OpenCode repo | opencode-ai/opencode | anomalyco/opencode | Sept 2025 | Old repo archived |
| Docker permission skip | Manual flag required | Default in sandboxes | Late 2025 | Better container UX |

**Deprecated/outdated:**
- `npm install -g @anthropic-ai/claude-code`: Use native installer instead
- `opencode-ai/opencode`: Archived Sept 2025, use `anomalyco/opencode` or current installer

**Important note on OpenCode:** The installer at `https://opencode.ai/install` redirects to `https://raw.githubusercontent.com/anomalyco/opencode/refs/heads/dev/install`, confirming anomalyco is the current active repository.

## Open Questions

1. **OpenCode installer URL stability**
   - What we know: Current installer redirects from opencode.ai to GitHub raw
   - What's unclear: Whether opencode.ai URL will remain stable
   - Recommendation: Use opencode.ai URL (stable public interface), let it redirect

2. **Claude Code version availability**
   - What we know: Versions passed to `bash -s` work via bootstrap
   - What's unclear: Minimum version available, how long versions stay available
   - Recommendation: Document that users should specify recent versions; add error handling for invalid versions

3. **Image tag length limits**
   - What we know: Docker tags have 128 char limit
   - What's unclear: Whether combined version tags could exceed limit
   - Recommendation: Monitor tag lengths; current format (`aishell:claude-X.Y.Z-opencode-X.Y.Z`) is ~45 chars, well under limit

## Sources

### Primary (HIGH confidence)
- [Claude Code Setup Docs](https://code.claude.com/docs/en/setup) - Version installation syntax
- [Claude Code Settings Docs](https://code.claude.com/docs/en/settings) - Permission configuration
- [Docker Claude Code Sandbox](https://docs.docker.com/ai/sandboxes/claude-code/) - Default sandbox behavior
- [GNU Bash Manual](https://www.gnu.org/software/bash/manual/html_node/Controlling-the-Prompt.html) - PROMPT_DIRTRIM
- [anomalyco/opencode releases](https://github.com/anomalyco/opencode/releases) - Current OpenCode versions
- OpenCode installer script (via WebFetch redirect) - VERSION env var handling

### Secondary (MEDIUM confidence)
- [Claude Code devcontainer docs](https://code.claude.com/docs/en/devcontainer) - Container permission patterns
- [npm @anthropic-ai/claude-code](https://www.npmjs.com/package/@anthropic-ai/claude-code) - Referenced for context
- [VishalJ99/claude-docker](https://github.com/VishalJ99/claude-docker) - Community version pinning pattern

### Tertiary (LOW confidence)
- WebSearch results on Docker image tagging strategies - General patterns, not tool-specific

## Metadata

**Confidence breakdown:**
- Version pinning (Claude): HIGH - Official documentation verified
- Version pinning (OpenCode): HIGH - Installer script verified via WebFetch
- PS1 PROMPT_DIRTRIM: HIGH - GNU Bash manual reference
- Permission skip flag: HIGH - Official Docker docs confirm default behavior
- Image tagging strategy: MEDIUM - Community patterns, no official guidance

**Research date:** 2026-01-18
**Valid until:** 2026-02-18 (30 days - these tools are actively developed, verify before major changes)

## Implementation Checklist

Based on research, the planner should address:

1. **CLI flags for version**: `--claude-version=X.Y.Z` and `--opencode-version=X.Y.Z`
2. **Build-arg propagation**: Pass versions as Docker build args
3. **Dockerfile modification**: Handle conditional version installation
4. **Image tag generation**: Include version in tag when specified
5. **bashrc.aishell update**: Add `PROMPT_DIRTRIM=2`
6. **Permission skip default**: Add `--dangerously-skip-permissions` with opt-out
7. **Environment variable**: `AISHELL_SKIP_PERMISSIONS` for opt-out
