# Phase 38: Volume Cleanup & Documentation - Context

**Gathered:** 2026-02-01
**Status:** Ready for planning

<domain>
## Phase Boundary

Volume management commands (list, prune) and comprehensive documentation update for the v2.8.0 foundation/volume architecture. Also includes repurposing `aishell update` to focus on harness volume refresh instead of foundation rebuild.

</domain>

<decisions>
## Implementation Decisions

### Update command redesign
- `aishell update` repopulated harness volume unconditionally (always gets latest versions, even if hash unchanged)
- `aishell update --force` rebuilds foundation AND repopulates harness volume (both, always)
- `aishell update` does NOT accept harness selection flags — strictly refreshes what was last built via `aishell build`
- Volume repopulation uses delete-and-recreate strategy (clean slate, no stale files from old versions)
- Show version diff when detectable (e.g., "claude-code@2.1.0 -> 2.2.0") rather than just a spinner
- Hash in state.edn stays the same after update (config didn't change, volume name stays the same)

### Volume list & prune UX
- Command name: Claude's discretion based on overall CLI UX patterns in the codebase
- List output: table with status (volume name, harnesses included, size, active/orphaned status) — similar to `aishell ps`
- Prune confirmation: Claude's discretion on the safety vs convenience tradeoff
- Prune scope: volumes only — foundation image cleanup is a Docker concern, not aishell's
- In-use volumes: skip with warning ("Skipping aishell-harness-abc123 (in use by container X)")

### Orphan detection
- A volume is orphaned if it's not referenced by current state.edn (single-machine, simple)
- No cross-project scanning — scoped to the stored harness-volume-name in state

### No-harness edge case
- Claude's discretion on whether `aishell update` with no harnesses enabled errors or no-ops

### Claude's Discretion
- Volume management command naming (e.g., `aishell volumes` vs `aishell volume list/prune` vs `aishell clean`)
- Prune confirmation flow (show-then-confirm, --yes flag, etc.)
- Mermaid diagrams vs text-only in ARCHITECTURE.md
- Behavior of `aishell update` when no harnesses are enabled

</decisions>

<specifics>
## Specific Ideas

- The separation between `build` and `update` is: build selects what's present, update refreshes what's already selected
- Version diff output should show before/after when detectable during volume repopulation
- No migration docs needed — no real user base yet. A proper changelog entry for v2.8.0 is enough
- Single v2.8.0 changelog entry covering all phases 35-38 (foundation split, volume-based harnesses, update command, cleanup)

</specifics>

<deferred>
## Deferred Ideas

None — discussion stayed within phase scope

</deferred>

---

*Phase: 38-volume-cleanup-documentation*
*Context gathered: 2026-02-01*
