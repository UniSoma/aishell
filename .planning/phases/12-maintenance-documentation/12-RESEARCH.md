# Phase 12: Maintenance & Documentation - Research

**Researched:** 2026-01-19
**Domain:** Docker metadata, Bash heredoc hashing, documentation
**Confidence:** HIGH

## Summary

This phase focuses on three requirements: Dockerfile change detection (MAINT-01), documenting run.conf parsing limitations (DOC-01), and documenting safe.directory behavior (DOC-02). The research confirms that all three are straightforward to implement using existing patterns in the codebase.

For MAINT-01, the codebase already uses Docker labels to track base image IDs (line 1152: `--label "aishell.base.id=$base_id"`). The same pattern applies for Dockerfile hash detection: compute a hash of the embedded Dockerfile content at build time, store it as a Docker label, then compare against the current script's embedded Dockerfile hash at runtime.

For DOC-01 and DOC-02, the code already contains the complete information needed for documentation. The run.conf parser (lines 539-602) has explicit limitations: it only accepts `VAR=value` or `VAR="value"` format with no escape sequences. The safe.directory behavior (lines 378-384 in entrypoint.sh) adds entries to the container user's global gitconfig, which may be mounted from the host via `~/.gitconfig` or `~/.config/git/config`.

**Primary recommendation:** Add `aishell.dockerfile.hash` label at build time; compare current embedded Dockerfile hash with label value at runtime; document known limitations in README with clear examples of what works and what doesn't.

## Standard Stack

This phase uses only Bash built-ins and existing tools. No new dependencies required.

### Core

| Tool | Version | Purpose | Why Standard |
|------|---------|---------|--------------|
| sha256sum | GNU coreutils | Hash Dockerfile content | Already in use for project hash (line 460) |
| docker inspect | Docker CLI | Read image labels | Standard Docker command, already used (line 1119) |
| heredoc | Bash built-in | Embedded Dockerfile extraction | Already in use (lines 229-331) |

### Supporting

| Tool | Purpose | When to Use |
|------|---------|-------------|
| `--label` | Docker build arg | Embed metadata in image |
| `--format` | Docker inspect filter | Extract specific label values |
| cut | Extract hash portion | Truncate sha256 to readable length |

### Alternatives Considered

| Instead of | Could Use | Tradeoff |
|------------|-----------|----------|
| Docker label | File in image | Label is metadata, file pollutes image layers |
| sha256sum | md5sum | sha256 is standard in codebase, more collision-resistant |
| Embedded hash constant | Build-time computation | Embedded constant breaks DRY; computation is deterministic |

## Architecture Patterns

### Pattern 1: Embedded Content Hashing

**What:** Compute hash of heredoc content dynamically by extracting and hashing.

**When to use:** When you need to detect if embedded content has changed since build time.

**Why it works:** Heredocs are static within a script version. If the script changes, the heredoc changes, and the hash changes.

**Example:**
```bash
# Source: Existing pattern in codebase (line 460)

# Compute hash of embedded Dockerfile content
# Uses the same function that writes to disk, but captures output
get_dockerfile_hash() {
    # Create a deterministic hash of what write_dockerfile would produce
    # Method: redirect write_dockerfile output to sha256sum
    local temp_dir
    temp_dir=$(mktemp -d)
    write_dockerfile "$temp_dir"
    sha256sum "${temp_dir}/Dockerfile" | cut -c1-12
    rm -rf "$temp_dir"
}

# Alternative: extract heredoc content directly
# This is more complex but avoids temp file
get_dockerfile_hash_inline() {
    # Use sed to extract heredoc content between markers
    # Risk: fragile if markers change
    sed -n '/^write_dockerfile()/,/^DOCKERFILE_EOF$/p' "$0" | sha256sum | cut -c1-12
}
```

**Recommendation:** Use the temp-file approach. It's reliable, uses existing functions, and the temp file is cleaned up immediately.

### Pattern 2: Docker Label for Build Metadata

**What:** Store build-time information as Docker image labels.

**When to use:** When you need to compare runtime state against build-time state.

**Why it works:** Labels are immutable after build, easily queried with `docker inspect`.

**Example:**
```bash
# Source: https://docs.docker.com/reference/dockerfile/#label
# Source: Existing pattern at line 1152

# At build time, add hash label
build_args+=(--label "aishell.dockerfile.hash=$dockerfile_hash")

# At runtime, check label
get_image_dockerfile_hash() {
    local image="$1"
    docker inspect --format='{{index .Config.Labels "aishell.dockerfile.hash"}}' "$image" 2>/dev/null
}
```

### Pattern 3: Warn-Only Update Check

**What:** Check for updates and warn, but don't block operation.

**When to use:** When the user should be informed but may have valid reasons to continue.

**Why it works:** Warnings inform without frustrating users who intentionally haven't updated.

**Example:**
```bash
# Source: Common CLI pattern (npm outdated, brew outdated, etc.)

check_dockerfile_update() {
    local image="$1"

    # Get hash from built image
    local built_hash
    built_hash=$(get_image_dockerfile_hash "$image")

    # Get current embedded hash
    local current_hash
    current_hash=$(get_dockerfile_hash)

    # Compare
    if [[ -n "$built_hash" ]] && [[ "$built_hash" != "$current_hash" ]]; then
        warn "Image was built with a different Dockerfile version"
        echo "  Built with:  $built_hash" >&2
        echo "  Current:     $current_hash" >&2
        echo "  Run 'aishell update' to rebuild with the current version." >&2
        echo "" >&2
    fi
}
```

### Anti-Patterns to Avoid

- **Embedding hash as constant:** Requires manual update when Dockerfile changes; easy to forget
- **Blocking on mismatch:** Users may intentionally use older images; warn only
- **Complex version comparison:** Simple hash comparison is sufficient; no need for semver

## Don't Hand-Roll

Problems with existing solutions:

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| Embed metadata in image | Custom file in /etc | Docker labels | Standard, doesn't pollute filesystem |
| Hash computation | Custom algorithm | sha256sum | Standard, reproducible, already in use |
| Version comparison | Semver parsing | Hash equality | Simpler, no false positives |

**Key insight:** Docker labels are the standard way to embed build metadata. The codebase already uses this pattern (line 1152). Extend it for Dockerfile hash.

## Common Pitfalls

### Pitfall 1: Hash Includes Variable Content

**What goes wrong:** Hash changes on every build due to timestamps or dynamic values.
**Why it happens:** If the Dockerfile contains build-time variables like `$BUILD_DATE`, hash varies.
**How to avoid:** Hash only the static template content, not the rendered output.
**Warning signs:** Hash mismatch warnings appear after every build.

**Mitigation in aishell:** The embedded Dockerfile uses `ARG` for dynamic values (lines 243-247). The heredoc content is static; only build args vary. Hash the heredoc source, not the rendered Dockerfile.

### Pitfall 2: Missing Label on Old Images

**What goes wrong:** Script crashes trying to read non-existent label from pre-update images.
**Why it happens:** Images built before the label feature don't have the label.
**How to avoid:** Handle empty/missing label gracefully; skip check, don't error.
**Warning signs:** "null" or empty string from docker inspect.

**Example:**
```bash
built_hash=$(get_image_dockerfile_hash "$image")
# If no hash (old image), skip check
[[ -z "$built_hash" ]] && return 0
```

### Pitfall 3: safe.directory Accumulation

**What goes wrong:** Repeated runs add duplicate entries to gitconfig.
**Why it happens:** `git config --add` always appends, doesn't check for duplicates.
**How to avoid:** Document as known behavior, not a bug; entries are harmless.
**Warning signs:** Large gitconfig files with repeated safe.directory lines.

**Current code (line 383):** Uses `--add` which appends. This is acceptable because:
1. Git handles duplicates correctly (later entries don't break anything)
2. Each project path is only added once per container run
3. Alternative (check before add) adds complexity for no functional benefit

### Pitfall 4: Documenting Implementation Details

**What goes wrong:** Documentation becomes outdated when code changes.
**Why it happens:** Documenting "how it works" instead of "how to use it".
**How to avoid:** Document behavior and limitations, not implementation.
**Warning signs:** README mentions line numbers or internal function names.

## Code Examples

### MAINT-01: Dockerfile Hash Detection

```bash
# Source: Docker label best practices
# Source: Existing codebase pattern (line 460, 1152)

# --- Dockerfile Hash Detection ---
# Compute hash of embedded Dockerfile content
get_dockerfile_hash() {
    local temp_dir
    temp_dir=$(mktemp -d)
    write_dockerfile "$temp_dir"
    local hash
    hash=$(sha256sum "${temp_dir}/Dockerfile" | cut -c1-12)
    rm -rf "$temp_dir"
    echo "$hash"
}

# Read hash from built image label
get_image_dockerfile_hash() {
    local image="$1"
    docker inspect --format='{{index .Config.Labels "aishell.dockerfile.hash"}}' "$image" 2>/dev/null || true
}

# Check if Dockerfile has changed since build
check_dockerfile_changed() {
    local image="$1"

    local built_hash
    built_hash=$(get_image_dockerfile_hash "$image")

    # Skip check for old images without hash label
    [[ -z "$built_hash" ]] && return 0

    local current_hash
    current_hash=$(get_dockerfile_hash)

    if [[ "$built_hash" != "$current_hash" ]]; then
        warn "Image was built with a different Dockerfile version"
        echo "  Built with:  $built_hash" >&2
        echo "  Current:     $current_hash" >&2
        echo "  Run 'aishell update' to rebuild with the current version." >&2
        echo "" >&2
    fi
}

# In do_build(), add the label:
do_build() {
    # ... existing code ...

    # Compute Dockerfile hash for tracking
    local dockerfile_hash
    dockerfile_hash=$(get_dockerfile_hash)

    # Add to build args
    build_args+=(--label "aishell.dockerfile.hash=$dockerfile_hash")

    # ... rest of build ...
}

# In main(), before running container:
main() {
    # ... after verify_build_exists ...

    # Check for Dockerfile updates (MAINT-01)
    check_dockerfile_changed "$IMAGE_TO_RUN"

    # ... rest of main ...
}
```

### DOC-01: run.conf Parsing Limitations (README Section)

```markdown
### run.conf Limitations

The `.aishell/run.conf` file uses a simplified parsing format:

**Supported syntax:**
- `VAR=value` - Unquoted value (no spaces allowed)
- `VAR="value with spaces"` - Double-quoted value
- `VAR='value with spaces'` - Single-quoted value
- `# comment` - Comments on their own line

**Not supported:**
- Escaped quotes: `VAR="value with \"quotes\""` will fail
- Multi-line values: Each assignment must be on one line
- Shell expansion: `$VAR` and `$(command)` are not expanded
- Continuation lines: No backslash line continuation

**Examples that work:**
```bash
MOUNTS="$HOME/.ssh $HOME/.config/git"
ENV="EDITOR=vim DEBUG_MODE=1"
PORTS="3000:3000 8080:80"
DOCKER_ARGS="--memory=4g --cpus=2"
PRE_START="redis-server --daemonize yes"
```

**Examples that don't work:**
```bash
# WRONG: Escaped quotes
MOUNTS="/path/with \"spaces\""

# WRONG: Multi-line value
DOCKER_ARGS="--memory=4g \
  --cpus=2"

# WRONG: Shell command substitution
ENV="BUILD_DATE=$(date)"
```

**Workaround for complex values:**
Use `DOCKER_ARGS` to pass environment variables that need special characters:
```bash
DOCKER_ARGS="-e COMPLEX_VAR=value-with-special-chars"
```
```

### DOC-02: safe.directory Behavior (README Section)

```markdown
### Git safe.directory Configuration

When you run a container, aishell configures git to trust the mounted project directory by adding it to `safe.directory` in the container's gitconfig.

**What happens:**
1. The entrypoint runs `git config --global --add safe.directory /your/project/path`
2. This writes to `~/.gitconfig` inside the container

**Host gitconfig impact:**
If you mount your host's `~/.gitconfig` or `~/.config/git/config` into the container (via `MOUNTS` in run.conf), the safe.directory entry will be added to your **host's** gitconfig file.

**Why this happens:**
- Git requires safe.directory for directories owned by different users
- Inside the container, the mounted project is owned by root (from container's perspective)
- This is a security feature (CVE-2022-24765), not a bug

**What you'll see in ~/.gitconfig:**
```ini
[safe]
    directory = /home/user/projects/myproject
    directory = /home/user/projects/another
```

**Is this a problem?**
- The entries are harmless; they just mark directories as trusted
- Entries may accumulate over time (git handles duplicates correctly)
- You can safely remove old entries manually if desired

**To avoid modifying host gitconfig:**
Don't mount your host gitconfig into the container. The container creates its own gitconfig that is discarded when the container exits.
```

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| No version tracking | Docker labels for metadata | Docker 1.6 (2015) | Standard metadata storage |
| Custom version files | OCI image labels | OCI spec 2017 | Industry standard |
| Blocking on outdated | Warn-only patterns | Modern CLI design | Better UX |

**Deprecated/outdated:**
- `/etc/version` files in containers: Use labels instead
- Blocking updates: Warn and let user decide
- Complex version comparison: Simple hash equality is sufficient

## Open Questions

1. **Hash Stability Across Releases**
   - What we know: Heredoc content is stable within a script version
   - What's unclear: Should we version the hash algorithm itself?
   - Recommendation: Simple 12-char sha256 truncation is sufficient; no versioning needed

2. **Extension Dockerfile Hashing**
   - What we know: User's `.aishell/Dockerfile` extends base image
   - What's unclear: Should we track extension Dockerfile hash separately?
   - Recommendation: Out of scope for MAINT-01; base image hash is sufficient for update detection

3. **safe.directory Cleanup**
   - What we know: Entries accumulate in gitconfig
   - What's unclear: Should aishell provide a cleanup command?
   - Recommendation: Document as known behavior; manual cleanup is simple enough

## Sources

### Primary (HIGH confidence)
- [Docker LABEL Documentation](https://docs.docker.com/reference/dockerfile/#label) - Official label syntax
- [Docker inspect Documentation](https://docs.docker.com/reference/cli/docker/inspect/) - Querying image metadata
- [Git safe.directory Documentation](https://git-scm.com/docs/git-config#Documentation/git-config.txt-safedirectory) - Official git-config docs
- [CVE-2022-24765](https://github.blog/2022-04-12-git-security-vulnerability-announced/) - Security context for safe.directory

### Secondary (MEDIUM confidence)
- [OCI Image Annotation Spec](https://github.com/opencontainers/image-spec/blob/main/annotations.md) - Standard label keys
- Existing codebase patterns (lines 460, 1152) - Proven hash and label patterns

### Tertiary (LOW confidence)
- [Docker Best Practices: Tags and Labels](https://www.docker.com/blog/docker-best-practices-using-tags-and-labels-to-manage-docker-image-sprawl/) - Community patterns

## Metadata

**Confidence breakdown:**
- Dockerfile hashing: HIGH - Uses existing codebase patterns, standard sha256sum
- Docker labels: HIGH - Official Docker feature, already in use in codebase
- run.conf documentation: HIGH - Based on direct code analysis (lines 539-602)
- safe.directory documentation: HIGH - Based on direct code analysis (lines 378-384)

**Research date:** 2026-01-19
**Valid until:** 90 days (stable domain, established patterns)
