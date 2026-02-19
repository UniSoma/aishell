# Phase 66: Global Base Image Customization - Context

**Gathered:** 2026-02-18
**Status:** Ready for planning

<domain>
## Phase Boundary

Introduce `aishell:base` as an intermediate image layer between `aishell:foundation` and project extensions, enabling advanced users to globally customize their base image via `~/.aishell/Dockerfile`. Three-tier chain: `aishell:foundation` -> `aishell:base` -> `aishell:ext-{hash}`.

</domain>

<decisions>
## Implementation Decisions

### Scaffolding & authoring
- No CLI scaffold command — users create `~/.aishell/Dockerfile` manually
- Fixed path only: always `~/.aishell/Dockerfile`, not configurable
- Trust the user on FROM line — document that it should be `FROM aishell:foundation` but don't validate/enforce
- Documentation should include 2-3 common use case examples (extra system packages, shell config, dev tools)

### Build integration
- Lazy build on first run — base image is built when a container run detects `~/.aishell/Dockerfile`, not during `aishell setup`
- Auto-cascade rebuilds — base image change invalidates all project extension images; they rebuild next time each project runs
- Tag alias when no Dockerfile — `aishell:base` always exists; when no `~/.aishell/Dockerfile`, it's a Docker tag alias for `aishell:foundation`
- Accept both FROM lines — project Dockerfiles can use `FROM aishell:foundation` or `FROM aishell:base`, no forced migration, new docs recommend `aishell:base`
- Note: existing `validate-base-tag` in extension.clj (which rejects `FROM aishell:base`) must be removed/reversed

### Status & visibility
- `aishell check` always shows base image status — "Base image: custom (~/.aishell/Dockerfile)" or "Base image: default (foundation alias)"
- Spinner during build — "Building global base image..." spinner, same pattern as extension builds
- One-line confirmation after build — e.g., checkmark "Global base image built"
- Hard-stop on build failure — user explicitly created the Dockerfile, don't silently skip it; show Docker build output for debugging

### Reset & removal
- Delete file to reset — user deletes `~/.aishell/Dockerfile`, next run re-tags foundation as `aishell:base`; no CLI command needed
- Auto-cleanup old base image — when Dockerfile is removed, clean up the old custom base image and re-tag foundation
- `aishell setup --force` rebuilds base — consistent behavior, --force rebuilds everything (foundation + base + ext)
- `aishell volumes prune` includes orphaned base images — one-stop cleanup command

### Claude's Discretion
- Docker label tracking strategy for base image (following existing extension pattern)
- Content hash algorithm for change detection
- Exact wording of check output and build messages
- Order of operations during the lazy build path

</decisions>

<specifics>
## Specific Ideas

- Build pattern should match existing extension image workflow (lazy detect, auto-rebuild, spinner)
- `aishell:base` always exists as a tag — either custom-built or aliased to foundation
- No new CLI subcommands — the entire feature is driven by file presence (`~/.aishell/Dockerfile`)

</specifics>

<deferred>
## Deferred Ideas

None — discussion stayed within phase scope

</deferred>

---

*Phase: 66-global-base-image-customization*
*Context gathered: 2026-02-18*
