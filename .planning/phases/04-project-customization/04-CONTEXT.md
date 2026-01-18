# Phase 4: Project Customization - Context

**Gathered:** 2026-01-17
**Status:** Ready for planning

<domain>
## Phase Boundary

Projects can extend the base environment with additional dependencies via `.aishell/Dockerfile`. The tool detects this file, builds an extended image, and caches it. This does NOT include: multiple profiles, advanced configuration files, or registry publishing.

</domain>

<decisions>
## Implementation Decisions

### Extension Mechanism
- Explicit `FROM aishell:base` — user writes standard Dockerfile syntax
- Base image is local only (built on first run, no registry needed)
- Build arguments pass through: `aishell --build-arg NODE_VERSION=20`
- Extended image inherits harness configuration from base (whatever Claude/OpenCode base was built with)
- If harness flags change (e.g., `--with-opencode` on a base without it), rebuild extended image against new base automatically
- Build context is project directory — `COPY package.json .` works

### Detection & Build UX
- Auto-build with status when `.aishell/Dockerfile` is detected (spinner: "Building project image...")
- Spinner by default, `--verbose` flag shows streaming Docker build output
- Silent when using cached image (no "Using cached..." message)
- On build failure: show error output and exit with non-zero status (no fallback offer)

### Cache Behavior
- Let Docker decide rebuilds — always run `docker build`, Docker's layer cache handles efficiency
- `--rebuild` flag forces `docker build --no-cache`
- Auto-detect base image changes: track base image ID, rebuild extended if base changed
- Image cleanup: Claude's discretion (likely no automatic cleanup, leave to user)

### Naming & Conventions
- File location: `.aishell/Dockerfile` (hidden directory, keeps project root clean)
- Single file per project — no profiles (`.aishell/Dockerfile.dev`, etc.)
- Other config files in `.aishell/`: Claude's discretion (may defer to future)
- Init command to generate starter template: Claude's discretion (may defer to future)

### Claude's Discretion
- Extended image naming/tagging scheme (project-based, hash-based, or single tag)
- Whether to include `.aishell/.env` support in this phase or defer
- Whether to include `aishell init` command or just document format
- Image cleanup strategy (none, warn on old, auto-cleanup)

</decisions>

<specifics>
## Specific Ideas

No specific requirements — open to standard approaches. User wants the Docker-native feel (explicit FROM, standard syntax).

</specifics>

<deferred>
## Deferred Ideas

None — discussion stayed within phase scope.

</deferred>

---

*Phase: 04-project-customization*
*Context gathered: 2026-01-17*
