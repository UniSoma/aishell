# Phase 63: Core OpenSpec Integration - Context

**Gathered:** 2026-02-18
**Status:** Ready for planning

<domain>
## Phase Boundary

Add OpenSpec as an opt-in build-time tool available inside containers, following the established `--with-*` / harness volume npm install pattern. OpenSpec is NOT a harness — no `aishell openspec` subcommand, no config mounts, no API key passthrough. Users opt in with `--with-openspec` at build time and access it via the `openspec` command inside the container.

</domain>

<decisions>
## Implementation Decisions

### Default version strategy
- Bare `--with-openspec` (no version) resolves to npm `@latest` at install time
- `--with-openspec=1.2.3` pins that exact version
- Follow the same versioning behavior as existing harnesses: no version locking, `aishell update` re-resolves latest for unpinned, re-installs same for pinned
- If npm resolution fails (registry down, network issue), the build fails — do not continue without OpenSpec when user explicitly requested it

### User discovery & feedback
- No shell banner or welcome message when entering container — user typed `--with-openspec`, they know it's there
- Minimal build output: same level as other harness tools (no version display during build)
- `aishell status` (or equivalent) shows OpenSpec enabled status and version alongside other `--with-*` tools
- `aishell update` output lists OpenSpec when re-installing/updating it

### Error experience
- Standard shell "command not found" when running `openspec` in a container not built with `--with-openspec` — no custom error message
- Invalid version handling: same behavior as existing harnesses (semver validation, fail at install time)
- If OpenSpec install fails during `aishell update`, the entire update fails — no partial updates
- Version format validated using same semver validation pattern as existing `--with-*` tools

### OpenSpec scope & commands
- Full OpenSpec CLI installed as-is from npm — no curated subset, whatever the package ships
- No host config file mounts — OpenSpec runs with what's in the container
- Installed into the shared `/tools` volume alongside other npm-installed tools (like Claude Code)

### Claude's Discretion
- Exact npm package name and install command structure
- State tracking implementation details (state.edn keys)
- Hash computation integration for volume invalidation
- Build flag parsing implementation

</decisions>

<specifics>
## Specific Ideas

- Follow existing harness patterns exactly — OpenSpec should be indistinguishable in workflow from how Claude Code or OpenCode are handled
- "Same as harnesses" was the guiding principle for most decisions — consistency over novelty

</specifics>

<deferred>
## Deferred Ideas

None — discussion stayed within phase scope

</deferred>

---

*Phase: 63-core-openspec-integration*
*Context gathered: 2026-02-18*
