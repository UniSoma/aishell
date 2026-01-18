---
phase: 06-final-enhancements
plan: 01
subsystem: cli-version-pinning
tags: [version-pinning, docker, cli, reproducibility]
dependency_graph:
  requires: [05-01]
  provides: [versioned-harness-installation, version-aware-image-tags]
  affects: []
tech_stack:
  added: []
  patterns: [version-build-args, tag-based-caching]
key_files:
  created: []
  modified: [aishell]
decisions:
  - "Version format: no v-prefix (e.g., 2.0.22 not v2.0.22)"
  - "Tag format: aishell:claude-X.Y.Z or aishell:opencode-X.Y.Z"
  - "Combined versions: aishell:claude-X.Y.Z-opencode-X.Y.Z"
metrics:
  duration: 2 min
  completed: 2026-01-18
---

# Phase 6 Plan 1: Version Pinning Summary

Version flags for reproducible harness builds, with version-aware image tagging for cache efficiency.

## Objectives Achieved

- VERSION-01: `--claude-version=X.Y.Z` flag accepted and used in build
- VERSION-02: `--opencode-version=X.Y.Z` flag accepted and used in build
- VERSION-03: Without flags, latest version installed (default behavior preserved)
- VERSION-04: Versioned builds use distinct image tags (aishell:claude-X.Y.Z)

## Implementation Details

### CLI Flags Added

```bash
--claude-version=X.Y.Z   # Install specific Claude Code version
--opencode-version=X.Y.Z # Install specific OpenCode version
```

### Version Installation Patterns

**Claude Code:**
```bash
# In Dockerfile, using bash -s syntax
curl -fsSL https://claude.ai/install.sh | bash -s "$CLAUDE_VERSION"
```

**OpenCode:**
```bash
# In Dockerfile, using VERSION env var
VERSION="$OPENCODE_VERSION" curl -fsSL https://opencode.ai/install | bash
```

### Image Tagging Strategy

| Scenario | Tag |
|----------|-----|
| Claude only (versioned) | `aishell:claude-2.0.22` |
| OpenCode only (versioned) | `aishell:opencode-1.1.25` |
| Both (versioned) | `aishell:claude-2.0.22-opencode-1.1.25` |
| No versions specified | `aishell:base` |

### Key Function: compute_image_tag()

```bash
compute_image_tag() {
    local suffix=""
    if [[ -n "$CLAUDE_VERSION" ]]; then
        suffix="${suffix:+$suffix-}claude-$CLAUDE_VERSION"
    fi
    if [[ -n "$OPENCODE_VERSION" ]]; then
        suffix="${suffix:+$suffix-}opencode-$OPENCODE_VERSION"
    fi
    if [[ -n "$suffix" ]]; then
        echo "aishell:$suffix"
    else
        echo "$BASE_IMAGE_NAME"
    fi
}
```

## Decisions Made

1. **Version format without v-prefix:** Both tools expect semantic versions without "v" prefix (e.g., `2.0.22` not `v2.0.22`)

2. **Tag-based caching:** Different versions create different tags, enabling Docker to cache each version separately

3. **BASE_IMAGE_NAME update:** After ensure_image(), BASE_IMAGE_NAME is updated to the computed tag for extension builds to use correctly

## Usage Examples

```bash
# Install specific Claude Code version
aishell --with-claude --claude-version=2.0.22

# Install specific OpenCode version
aishell --with-opencode --opencode-version=1.1.25

# Both with specific versions
aishell --with-claude --claude-version=2.0.22 --with-opencode --opencode-version=1.1.25

# Update to specific version
aishell update --with-claude --claude-version=2.0.22
```

## Deviations from Plan

None - plan executed exactly as written.

## Commits

| Hash | Description |
|------|-------------|
| c5a60fb | feat(06-01): add version CLI flags and parsing |
| af2927c | feat(06-01): add version support to Dockerfile heredoc |
| 9c77300 | feat(06-01): add version-aware image tagging |

## Next Phase Readiness

Plan 06-01 complete. Remaining items from Phase 6 roadmap:
- Shell prompt optimization (PROMPT_DIRTRIM=2) - documented in RESEARCH.md, not part of this plan
- Permission skip default (--dangerously-skip-permissions) - documented in RESEARCH.md, not part of this plan

These are captured as pending todos in STATE.md and can be addressed in future iterations.
