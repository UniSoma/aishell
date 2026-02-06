# Phase 52: Documentation Update - Research

**Researched:** 2026-02-06
**Domain:** Technical documentation maintenance (Markdown)
**Confidence:** HIGH

## Summary

Phase 52 updates all user-facing documentation to reflect v3.0.0 CLI changes. The research identified exactly what changed in Phases 46-51, where those changes are documented, and what needs updating.

**What changed in v3.0.0:**
- tmux completely removed from containers (Phases 46-49)
- Attach command simplified from tmux session management to direct docker exec bash (Phase 50)
- --detach flag removed; containers always run foreground-attached (Phase 51)
- CLI semantics simplified: no detached mode, no session management (Phase 51)

**Where documentation exists:**
- README.md (Quick start, usage, features)
- docs/ARCHITECTURE.md (System design, data flow, tmux architecture section)
- docs/CONFIGURATION.md (Config reference, tmux: section)
- docs/HARNESSES.md (Harness guide, detached mode section)
- docs/TROUBLESHOOTING.md (Common issues, tmux issues section)
- docs/DEVELOPMENT.md (Adding harnesses, entrypoint internals)

**Primary recommendation:** Update each doc file by searching for tmux, detach, and attach references, then revising to reflect the always-attached, no-tmux model.

## Standard Stack

Documentation is Markdown only. No libraries or tools needed beyond standard text editing.

### Core
| Tool | Version | Purpose | Why Standard |
|------|---------|---------|--------------|
| Markdown | N/A | Documentation format | Universal, simple, readable |
| Text editor | N/A | Editing | Any editor works |

### Supporting
None needed — this is pure documentation maintenance.

### Alternatives Considered
None — Markdown is the established format for this project.

**Installation:**
None required — files already exist.

## Architecture Patterns

### Documentation Structure
The project follows standard open-source documentation patterns:

```
aishell/
├── README.md              # User-facing entry point
└── docs/
    ├── ARCHITECTURE.md    # Technical design
    ├── CONFIGURATION.md   # Config reference
    ├── HARNESSES.md       # Harness-specific guide
    ├── TROUBLESHOOTING.md # Common issues
    └── DEVELOPMENT.md     # Contributor guide
```

### Pattern 1: README.md Structure
**What:** User entry point covering quick start, features, usage examples
**When to update:** Any CLI change, major feature addition/removal
**Sections affected by v3.0.0:**
- "Detached mode & multi-container workflow" (lines 175-202 in current version)
- "Features" list mentions --detach and tmux
- Usage examples showing --detach flag
- Environment variables section (may reference tmux-related vars)

### Pattern 2: ARCHITECTURE.md Technical Details
**What:** Internal design, data flow, architectural decisions
**When to update:** Infrastructure changes, component removal/addition
**Sections affected by v3.0.0:**
- "tmux Architecture" section (lines 172-257) — entire section obsolete
- "Data Flow" may reference tmux conditionals
- "Namespace Responsibilities" may list tmux-related namespaces
- "Architecture Decisions" at end discusses tmux rationale

### Pattern 3: CONFIGURATION.md Reference
**What:** Complete config.yaml reference with examples
**When to update:** Config schema changes
**Sections affected by v3.0.0:**
- "tmux" section (lines 836-947) — entire section obsolete
- "Setup Options" mentions --with-tmux flag
- Examples showing tmux: plugins and resurrect config

### Pattern 4: HARNESSES.md Usage Guide
**What:** Per-harness setup and usage instructions
**When to update:** Harness execution changes, workflow changes
**Sections affected by v3.0.0:**
- "Detached Mode & tmux" section (lines 515-610) — needs complete rewrite
- References to tmux sessions throughout
- Attach command examples need updating

### Pattern 5: TROUBLESHOOTING.md Issue Solutions
**What:** Common problems and fixes
**When to update:** Features removed, error messages changed
**Sections affected by v3.0.0:**
- "tmux Issues" section (lines 901-1059) — entire section obsolete
- "Detached Mode & Attach Issues" section needs revision
- References to tmux session errors throughout

### Pattern 6: DEVELOPMENT.md Contributor Guide
**What:** How to extend aishell
**When to update:** Core infrastructure changes
**Sections affected by v3.0.0:**
- References to entrypoint tmux conditionals
- Build flow explanations may mention tmux volume population

### Anti-Patterns to Avoid
- **Half-removing features:** Don't leave tmux references in some docs but not others
- **Stale examples:** Don't keep examples showing removed flags
- **Contradictory information:** Don't say "no tmux" in README but show tmux config in CONFIGURATION
- **Orphaned sections:** Don't leave section headers with no content after removal

## Don't Hand-Roll

This is documentation maintenance — no custom tooling needed.

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| Finding references | Custom grep scripts | Standard grep/ripgrep | Already installed, well-understood |
| Validating Markdown | Custom parser | Visual inspection, markdown linters | Markdown is simple enough |
| Tracking changes | Spreadsheet of diffs | Git diffs, atomic commits | Version control is built-in |

**Key insight:** Documentation updates are manual by nature. Don't over-engineer — search, read, update, commit.

## Common Pitfalls

### Pitfall 1: Incomplete Search Coverage
**What goes wrong:** Searching only for "tmux" misses "detach", "attach --session", etc.
**Why it happens:** Changed features have multiple surface areas
**How to avoid:** Search for all related terms (tmux, detach, attach, session, --with-tmux, resurrect, TPM, plugins)
**Warning signs:** CI/CD documentation mentions tmux but README doesn't

### Pitfall 2: Leaving Obsolete Sections
**What goes wrong:** Section headers remain with "(removed in v3.0.0)" notes
**Why it happens:** Reluctance to delete large doc sections
**How to avoid:** Fully remove obsolete sections instead of deprecation notices
**Warning signs:** Table of contents links to empty sections

### Pitfall 3: Breaking Internal Links
**What goes wrong:** Removing sections breaks [anchor links](#section-name) elsewhere
**Why it happens:** Not checking for cross-references
**How to avoid:** Search for removed section names before deleting
**Warning signs:** Links to #tmux-architecture, #detached-mode-attach-issues

### Pitfall 4: Inconsistent Terminology
**What goes wrong:** Some docs say "no tmux", others say "tmux removed", others say "always-attached"
**Why it happens:** Multiple contributors, no style guide
**How to avoid:** Pick standard phrasing: "containers always run foreground-attached", "no tmux in v3.0.0"
**Warning signs:** Confusion in issues/discussions about whether tmux exists

### Pitfall 5: Stale Version References
**What goes wrong:** "Last updated: v2.9.0" header on substantially changed docs
**Why it happens:** Forgetting to update metadata
**How to avoid:** Update "Last updated" line in every modified doc
**Warning signs:** Docs marked v2.9.0 but mention v3.0.0 changes

## Code Examples

Documentation updates are prose, not code. No code examples needed.

## State of the Art

| Old Approach (v2.9.0) | Current Approach (v3.0.0) | When Changed | Impact |
|----------------------|---------------------------|--------------|--------|
| tmux optional via --with-tmux | No tmux at all | v3.0.0 | Entire tmux sections obsolete |
| Detached mode via --detach | No detached mode | v3.0.0 | CLI examples need updating |
| attach --name X --session Y | attach X | v3.0.0 | Attach examples need updating |
| aishell X --detach | aishell X (always foreground) | v3.0.0 | Usage examples need updating |

**Deprecated/outdated:**
- --with-tmux flag: Removed in Phase 47
- --detach/-d flag: Removed in Phase 51
- attach --session flag: Removed in Phase 50
- attach --shell flag: Removed in Phase 50
- tmux: config section: Removed in Phase 47
- resurrect: config: Removed in Phase 47

## Search Targets

Terms to search for in each doc file (grep -n):

**Direct references:**
- `tmux` (case-insensitive)
- `--with-tmux`
- `WITH_TMUX`
- `:with-tmux`
- `--detach`
- `-d` (may have false positives)
- `--session`
- `--shell` (in context of attach)
- `resurrect`
- `TPM` (Tmux Plugin Manager)

**Indirect references:**
- `attach --name` (old syntax)
- `Ctrl+B D` (tmux detach)
- `session` (when discussing tmux)
- `plugin` (when discussing tmux plugins)
- `detached mode`
- `background mode`

**CLI command examples:**
- `aishell claude --detach`
- `aishell attach --name`
- `aishell setup --with-tmux`

## File-by-File Update Strategy

### README.md
**Lines to check:** 114-119 (features), 175-202 (detached mode)
**Updates needed:**
- Remove "Detached mode" from features list
- Remove "tmux integration" from features list
- Remove entire "Detached mode & multi-container workflow" section
- Update "Features" → "Power user" section (remove attach/detach)
- Check for --detach in usage examples
- Verify no tmux references in Quick Start

### docs/ARCHITECTURE.md
**Lines to check:** 62 (principles), 172-257 (tmux architecture), 575-598 (decisions)
**Updates needed:**
- Remove "tmux Opt-in" from architectural principles
- Remove entire "## tmux Architecture" section (85 lines)
- Remove "## Migration" → "From v2.8.0 to v2.9.0" tmux behavior subsection
- Update "Architecture Decisions" to note tmux removal
- Update namespace table to remove tmux-related namespaces (if any)

### docs/CONFIGURATION.md
**Lines to check:** 836-947 (tmux section), 1128-1169 (--with-tmux setup)
**Updates needed:**
- Remove entire "### tmux" section (111 lines)
- Remove "### --with-tmux" setup section (41 lines)
- Check annotated example for tmux references
- Remove tmux from any merge behavior examples

### docs/HARNESSES.md
**Lines to check:** 515-610 (detached mode section)
**Updates needed:**
- Remove or rewrite entire "## Detached Mode & tmux" section
- Update "Container Naming" section to remove tmux session references
- Check for --detach in usage examples
- Remove tmux plugins configuration examples
- Update troubleshooting references to attach

### docs/TROUBLESHOOTING.md
**Lines to check:** 803-1059 (detached/attach/tmux issues)
**Updates needed:**
- Remove entire "## tmux Issues" section (lines 901-1059, 158 lines)
- Update "## Detached Mode & Attach Issues" to reflect docker exec attach
- Remove migration warning references
- Update attach error messages to match Phase 50 implementation
- Check quick diagnostics for tmux references

### docs/DEVELOPMENT.md
**Lines to check:** References to entrypoint, volume population
**Updates needed:**
- Update "## Build Flow and Volume Population Internals" to remove TPM
- Update entrypoint flow description (remove tmux conditionals)
- Remove tmux from state schema example
- Check for --with-tmux in examples

## Update Principles

**Consistency:**
- Use "containers always run foreground-attached" consistently
- Use "docker exec bash" to describe attach
- Say "v3.0.0 removed tmux" when explaining removals

**Completeness:**
- Don't leave "see tmux section below" dangling references
- Update table of contents if sections removed
- Update cross-references between docs

**Clarity:**
- Explain what replaced removed features (e.g., "attach now uses docker exec")
- Don't assume users know what changed
- Be explicit: "This feature was removed in v3.0.0"

## Open Questions

None — all information needed to update docs is available from:
- Phase 46-51 SUMMARY.md files (what changed)
- Current documentation files (what to update)
- REQUIREMENTS.md (what the new behavior is)

## Sources

### Primary (HIGH confidence)
- .planning/phases/46-51 SUMMARY.md files - What actually changed
- .planning/REQUIREMENTS.md - v3.0.0 requirements
- .planning/STATE.md - Prior decisions
- Current README.md and docs/*.md files - What needs updating

### Secondary (MEDIUM confidence)
- Phase PLAN.md files - Implementation details

### Tertiary (LOW confidence)
None — all information is internal project documentation

## Metadata

**Confidence breakdown:**
- File locations: HIGH - docs are standardized Markdown
- Changes needed: HIGH - phases 46-51 clearly documented
- Search terms: HIGH - specific keywords identified

**Research date:** 2026-02-06
**Valid until:** 2026-02-13 (documentation research doesn't age quickly)
