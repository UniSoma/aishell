# Phase 4: Project Customization - Research

**Researched:** 2026-01-17
**Domain:** Docker image extension mechanism for project-specific customization
**Confidence:** HIGH

## Summary

Phase 4 implements project-specific image extension via `.aishell/Dockerfile`. Projects can extend the base `aishell:base` image with additional dependencies (Node.js, Python, Rust, etc.) using standard Dockerfile syntax. The CLI detects this file, builds an extended image, and runs the container using it instead of the base image.

The implementation leverages Docker's native caching mechanism rather than custom cache management. When a user runs `aishell`, the CLI always runs `docker build` and lets Docker determine whether to rebuild layers. Base image change detection uses Docker labels to track the parent image ID, triggering extended image rebuild when the base changes.

**Primary recommendation:** Use Docker labels to store base image ID in extended images. On each run, compare the stored label against current base image ID. If different, pass `--no-cache` to force rebuild of extended image against new base.

## Standard Stack

This phase uses Docker CLI features already available in the project. No new dependencies required.

### Core

| Component | Version | Purpose | Why Standard |
|-----------|---------|---------|--------------|
| docker build | 20+ | Build extended images | Native Docker feature, no alternative needed |
| docker inspect | 20+ | Get image IDs and labels | Standard Docker introspection |
| LABEL instruction | - | Store metadata in images | Docker-native metadata mechanism |
| --build-arg | - | Pass arguments to child Dockerfile | Standard Docker pattern |

### Supporting

| Tool | Version | Purpose | When to Use |
|------|---------|---------|-------------|
| sha256sum | coreutils | Hash Dockerfile content (optional) | If content-based tagging needed |
| --iidfile | Docker 17.06+ | Write image ID to file after build | Alternative to parsing build output |

### Alternatives Considered

| Instead of | Could Use | Tradeoff |
|------------|-----------|----------|
| Docker labels for tracking | File-based cache (e.g., `.aishell/.cache`) | Labels are introspectable, self-contained; files can get stale |
| Always `docker build` | Check Dockerfile mtime | Docker's layer cache is smarter than mtime; simpler code |
| Per-project image tags | Single `aishell:extended` tag | Per-project enables multiple projects with different extensions |

## Architecture Patterns

### Recommended Project Structure

User's project with extension:

```
user-project/
├── .aishell/
│   └── Dockerfile       # Extends aishell:base
├── src/
└── package.json
```

The `.aishell/Dockerfile` example:

```dockerfile
FROM aishell:base

# Project-specific dependencies
RUN apt-get update && apt-get install -y python3 python3-pip
RUN pip3 install pytest black
```

### Pattern 1: Base Image Reference by Name

**What:** Extended Dockerfile uses `FROM aishell:base` to reference the local base image.
**When:** Always. This is the user-facing convention.
**Why:** Docker checks local cache first before attempting registry pull. Since `aishell:base` is a local-only image without registry prefix, Docker will find and use the local image.

```dockerfile
# .aishell/Dockerfile
FROM aishell:base

# Add project-specific tools
RUN apt-get update && apt-get install -y nodejs npm
```

**Source:** [Docker Base Images Documentation](https://docs.docker.com/build/building/base-images/)

### Pattern 2: Build Context is Project Directory

**What:** Set build context to project root, not `.aishell/` directory.
**When:** Always when building extended images.
**Why:** Users may want to `COPY` files from their project (e.g., `COPY requirements.txt .`).

```bash
# In aishell CLI
docker build -f "$PROJECT_DIR/.aishell/Dockerfile" -t "$IMAGE_TAG" "$PROJECT_DIR"
```

The `-f` flag specifies the Dockerfile location while the positional argument (last) sets the build context. This allows Dockerfile to reference any file within the project.

**Source:** [Docker Build Context Documentation](https://docs.docker.com/build/concepts/context/)

### Pattern 3: Base Image ID Tracking via Labels

**What:** Store base image ID as a label in extended image. Check on each run.
**When:** Always when building extended images.
**Why:** Detects base image changes (e.g., `--with-opencode` added) and forces extended rebuild.

```bash
# Get current base image ID
BASE_ID=$(docker inspect --format='{{.Id}}' aishell:base)

# Build extended image with label
docker build \
    --label "aishell.base.id=$BASE_ID" \
    -f "$PROJECT_DIR/.aishell/Dockerfile" \
    -t "$EXTENDED_TAG" \
    "$PROJECT_DIR"

# On subsequent runs, check if base changed
STORED_ID=$(docker inspect --format='{{index .Config.Labels "aishell.base.id"}}' "$EXTENDED_TAG" 2>/dev/null)
if [[ "$BASE_ID" != "$STORED_ID" ]]; then
    # Force rebuild
    docker build --no-cache --label "aishell.base.id=$BASE_ID" ...
fi
```

**Source:** [Docker Object Labels Documentation](https://docs.docker.com/engine/manage-resources/labels/)

### Pattern 4: Project-Based Image Naming

**What:** Name extended images based on project directory path for isolation.
**When:** Always for extended images.
**Why:** Allows multiple projects with different extensions to coexist without conflict.

Naming scheme options:

1. **Hash-based (recommended):** `aishell:ext-{hash}` where hash is derived from project path
   ```bash
   # Generate consistent hash from absolute project path
   HASH=$(echo -n "$PROJECT_DIR" | sha256sum | cut -c1-12)
   EXTENDED_TAG="aishell:ext-$HASH"
   ```

2. **Path-based:** Sanitized project name
   ```bash
   # Sanitize project directory name
   PROJECT_NAME=$(basename "$PROJECT_DIR" | tr '[:upper:]' '[:lower:]' | tr -c '[:alnum:]' '-')
   EXTENDED_TAG="aishell:$PROJECT_NAME"
   ```

Hash-based is more robust (handles path changes, special characters) but less human-readable. Path-based is more readable but may conflict if two projects share a name.

**Recommendation:** Use hash-based for robustness. The tag is internal; users don't interact with it directly.

### Pattern 5: Build Output Handling

**What:** Capture build output for error reporting while showing spinner.
**When:** Default mode (non-verbose).
**Why:** Users need error details on failure, but clean UX on success.

```bash
build_extended_image() {
    local dockerfile="$1"
    local project_dir="$2"
    local image_tag="$3"
    local build_log

    build_log=$(mktemp)
    trap "rm -f '$build_log'" RETURN

    start_spinner "Building project image"

    if docker build \
        -f "$dockerfile" \
        -t "$image_tag" \
        --label "aishell.base.id=$BASE_ID" \
        "$project_dir" > "$build_log" 2>&1; then
        stop_spinner
        return 0
    else
        stop_spinner
        error "Build failed. Output:"
        cat "$build_log" >&2
        return 1
    fi
}
```

**Verbose mode:** Pass through directly with `--progress=plain` for full streaming output.

**Source:** [Docker Community Forums - Capture Build Output](https://forums.docker.com/t/capture-ouput-of-docker-build-into-a-log-file/123178)

### Anti-Patterns to Avoid

- **Using mtime for cache decisions:** Docker's layer cache is content-aware; mtime-based decisions lead to unnecessary rebuilds or missed updates.

- **Hardcoding base image tag in extended Dockerfile:** Users write `FROM aishell:base`, but the CLI should not modify their Dockerfile. Instead, if base tagging ever changes, document the expected convention.

- **Building from `.aishell/` context:** Restricts user's ability to COPY project files. Always use project root as context.

- **Single `aishell:extended` tag:** Conflicts between projects with different extensions. Use per-project naming.

## Don't Hand-Roll

Problems that look simple but have existing solutions:

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| Cache invalidation | Custom mtime/checksum tracking | Docker layer cache | Docker handles this properly, including content-based invalidation |
| Base image change detection | File-based state tracking | Docker labels + inspect | Labels are introspectable, travel with image, self-documenting |
| Build context handling | Symlinks or file copying | `docker build -f ... context` | Native Docker feature, handles all edge cases |
| Progress indication | Custom build progress parsing | Spinner + capture output | Build output is complex (BuildKit format varies); just hide or show all |

**Key insight:** Docker already solved caching. The CONTEXT.md decision to "let Docker decide rebuilds" is correct. Our job is detection (does extended Dockerfile exist?) and triggering (when to pass `--no-cache` for base image changes).

## Common Pitfalls

### Pitfall 1: BuildKit Parent Information

**What goes wrong:** Attempting to get parent image from child using `docker inspect --format='{{.Parent}}'`.
**Why it happens:** BuildKit (default since Docker 23) does not populate the `.Parent` field.
**How to avoid:** Don't rely on `.Parent`. Use labels to explicitly track base image ID.
**Warning signs:** Empty parent field in inspect output.

**Source:** [Baeldung - Docker Dependent Child Images](https://www.baeldung.com/ops/docker-dependent-child-images)

### Pitfall 2: COPY Outside Build Context

**What goes wrong:** User writes `COPY ../some-file .` in their Dockerfile.
**Why it happens:** Docker build context limits what can be COPYed.
**How to avoid:** Document that build context is project root; files outside project cannot be copied.
**Warning signs:** "COPY failed: Forbidden path outside the build context" error.

**Source:** [Docker Build Context Documentation](https://docs.docker.com/build/concepts/context/)

### Pitfall 3: Registry Pull Attempts

**What goes wrong:** Docker tries to pull `aishell:base` from Docker Hub.
**Why it happens:** Base image doesn't exist locally (first run without prior `aishell` build).
**How to avoid:** Ensure base image exists before attempting extended build. The CLI should auto-build base if missing (already implemented in Phase 1).
**Warning signs:** "pull access denied" or "manifest unknown" errors.

### Pitfall 4: ARG/ENV Inheritance

**What goes wrong:** Build arguments from base image not available in extended image.
**Why it happens:** ARG values do not persist beyond build stage; ENV does.
**How to avoid:** If extended images need to know build-time choices (e.g., which harnesses installed), use labels or ENV in base, not ARG.
**Warning signs:** Empty variable values in extended image.

**Source:** [Docker Variables Documentation](https://docs.docker.com/build/building/variables/)

### Pitfall 5: Stale Extended Images

**What goes wrong:** User changes `.aishell/Dockerfile` but image doesn't update.
**Why it happens:** Docker's layer cache considers file content via checksum, but if user changes a URL or version string in a RUN command, cache may hit unexpectedly.
**How to avoid:** Document `--rebuild` flag for forcing clean build. Trust Docker cache by default.
**Warning signs:** Old versions of tools despite Dockerfile changes.

## Code Examples

### Example 1: Detect Extension Dockerfile

```bash
# Check for project extension
extension_dockerfile() {
    local project_dir="$1"
    local dockerfile="$project_dir/.aishell/Dockerfile"

    if [[ -f "$dockerfile" ]]; then
        echo "$dockerfile"
        return 0
    fi

    return 1
}
```

### Example 2: Get Base Image ID

```bash
# Get current base image ID (full sha256)
get_base_image_id() {
    docker inspect --format='{{.Id}}' aishell:base 2>/dev/null
}
```

### Example 3: Check If Extended Needs Rebuild

```bash
# Returns 0 if rebuild needed, 1 if cached
needs_extended_rebuild() {
    local extended_tag="$1"
    local current_base_id="$2"

    # Extended image doesn't exist
    if ! docker image inspect "$extended_tag" >/dev/null 2>&1; then
        return 0
    fi

    # Check stored base ID against current
    local stored_base_id
    stored_base_id=$(docker inspect --format='{{index .Config.Labels "aishell.base.id"}}' "$extended_tag" 2>/dev/null)

    if [[ "$stored_base_id" != "$current_base_id" ]]; then
        return 0  # Base changed, need rebuild
    fi

    return 1  # Use cache
}
```

### Example 4: Build Extended Image

```bash
build_extended_image() {
    local dockerfile="$1"
    local project_dir="$2"
    local image_tag="$3"
    local base_id="$4"
    local force_rebuild="$5"

    local -a build_args=(
        -f "$dockerfile"
        -t "$image_tag"
        --label "aishell.base.id=$base_id"
    )

    [[ "$force_rebuild" == true ]] && build_args+=(--no-cache)

    if [[ "$VERBOSE" == true ]]; then
        docker build "${build_args[@]}" --progress=plain "$project_dir"
    else
        local build_log
        build_log=$(mktemp)
        trap "rm -f '$build_log'" RETURN

        start_spinner "Building project image"
        if docker build "${build_args[@]}" "$project_dir" > "$build_log" 2>&1; then
            stop_spinner
        else
            stop_spinner
            error "Build failed:"
            cat "$build_log" >&2
            return 1
        fi
    fi
}
```

### Example 5: Full Extension Flow

```bash
# Integrated into main execution flow
ensure_image_with_extension() {
    local project_dir="$1"

    # First ensure base image exists
    ensure_image  # Existing function from Phase 1

    # Check for extension
    local dockerfile
    dockerfile=$(extension_dockerfile "$project_dir") || {
        # No extension, use base
        IMAGE_TO_RUN="aishell:base"
        return 0
    }

    # Calculate extended tag
    local hash
    hash=$(echo -n "$project_dir" | sha256sum | cut -c1-12)
    local extended_tag="aishell:ext-$hash"

    # Get current base ID
    local base_id
    base_id=$(get_base_image_id)

    # Determine if rebuild needed
    local force_rebuild=false
    if needs_extended_rebuild "$extended_tag" "$base_id"; then
        force_rebuild=true
    fi

    # User requested --rebuild
    [[ "$FORCE_REBUILD" == true ]] && force_rebuild=true

    # Build (Docker cache handles efficiency if force_rebuild=false)
    build_extended_image "$dockerfile" "$project_dir" "$extended_tag" "$base_id" "$force_rebuild"

    IMAGE_TO_RUN="$extended_tag"
}
```

### Example 6: Pass Build Args to Extended Build

```bash
# Pass --build-arg from CLI to extended build
# Per CONTEXT.md: aishell --build-arg NODE_VERSION=20

build_extended_with_args() {
    local -a build_args=("${BASE_BUILD_ARGS[@]}")  # From CLI parsing

    for arg in "${BUILD_ARGS[@]}"; do
        build_args+=(--build-arg "$arg")
    done

    docker build "${build_args[@]}" ...
}
```

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| docker build (legacy) | docker build (BuildKit) | Docker 23 (2023) | BuildKit is default; .Parent field not populated |
| --progress=auto | --progress=plain for verbose | BuildKit | Need explicit plain for readable streaming output |
| Parse build output for ID | --iidfile | Docker 17.06 | Reliable image ID capture without output parsing |

**Current as of 2026:** BuildKit is the default builder. All patterns in this research account for BuildKit behavior.

## Open Questions

### 1. Build Argument Passthrough Schema

**What we know:** CONTEXT.md specifies `aishell --build-arg NODE_VERSION=20` passthrough.
**What's unclear:** How to distinguish aishell's own build args (e.g., `--with-claude`) from passthrough args for extended Dockerfile.
**Recommendation:** Use `--build-arg` prefix for passthrough, existing `--with-*` flags for aishell features. Document that `--build-arg` only affects extended builds, not base.

### 2. Init Command Deferral

**What we know:** CONTEXT.md marks "init command to generate starter template" as Claude's discretion.
**What's unclear:** Whether to include in Phase 4 or defer.
**Recommendation:** Defer to future phase. Document Dockerfile format in README. Users can create manually or copy from examples.

### 3. .aishell/.env Support

**What we know:** CONTEXT.md marks this as Claude's discretion.
**What's unclear:** Whether project-specific environment variables should be supported.
**Recommendation:** Defer. This is additional scope. Current env var passthrough (API keys) covers main use case.

### 4. Image Cleanup

**What we know:** CONTEXT.md marks cleanup strategy as Claude's discretion.
**What's unclear:** Whether to warn about old images, auto-cleanup, or leave to user.
**Recommendation:** Do nothing (leave to user). Extended images are small (layer on top of base). Users can `docker image prune` if needed. Document in help/README.

## Sources

### Primary (HIGH confidence)

- [Docker Base Images Documentation](https://docs.docker.com/build/building/base-images/) - Local image resolution behavior
- [Docker Build Context Documentation](https://docs.docker.com/build/concepts/context/) - Build context and -f flag usage
- [Docker Object Labels Documentation](https://docs.docker.com/engine/manage-resources/labels/) - Label metadata patterns
- [Docker Build Cache Invalidation](https://docs.docker.com/build/cache/invalidation/) - Cache behavior and FROM instruction
- [Docker Variables Documentation](https://docs.docker.com/build/building/variables/) - ARG inheritance in multi-stage builds
- [Docker Image Inspect Reference](https://docs.docker.com/reference/cli/docker/image/inspect/) - Format options for metadata extraction

### Secondary (MEDIUM confidence)

- [Docker Image Digests Documentation](https://docs.docker.com/dhi/core-concepts/digests/) - Image ID vs RepoDigests distinction
- [Docker Image Naming Best Practices](https://dev.to/kalkwst/docker-image-naming-and-tagging-1pg9) - Tagging conventions and patterns
- [Baeldung - Docker Dependent Child Images](https://www.baeldung.com/ops/docker-dependent-child-images) - BuildKit .Parent limitation

### Tertiary (LOW confidence)

- Community patterns from Docker Forums - Build output capture patterns

## Metadata

**Confidence breakdown:**
- Extension mechanism: HIGH - Uses documented Docker features (FROM, -f, labels)
- Base change detection: HIGH - Docker inspect and labels are stable APIs
- Build output handling: MEDIUM - BuildKit progress format varies; simple capture is reliable
- Image naming scheme: MEDIUM - Hash-based is robust; edge cases may exist

**Research date:** 2026-01-17
**Valid until:** 60 days (Docker features are stable; core patterns unlikely to change)
