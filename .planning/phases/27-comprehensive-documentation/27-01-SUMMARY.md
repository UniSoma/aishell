---
phase: 27-comprehensive-documentation
plan: 01
subsystem: documentation
status: complete
tags: [documentation, architecture, configuration, mermaid, reference]

requires:
  - 26-01: Existing documentation patterns and structure

provides:
  - System architecture documentation with Mermaid diagrams
  - Complete configuration reference with merge strategy
  - Namespace responsibilities mapping
  - Key files and state schema documentation

affects:
  - 27-02: Provides architecture context for harness documentation
  - future-phases: Developers can understand system internals

tech-stack:
  added: []
  patterns:
    - "Mermaid diagrams for architecture visualization"
    - "Comprehensive reference documentation"
    - "Annotated configuration examples"

key-files:
  created:
    - docs/ARCHITECTURE.md: "System architecture with data flow and namespace mapping (369 lines)"
    - docs/CONFIGURATION.md: "Complete configuration reference with examples (907 lines)"
  modified: []

decisions:
  - id: mermaid-for-diagrams
    choice: "Use Mermaid.js for architecture diagrams"
    rationale: "GitHub renders Mermaid natively, no external tools needed"
    impact: "Diagrams are maintainable in markdown"
    alternatives: ["ASCII art", "PNG images", "External diagram tools"]

  - id: separate-architecture-and-config
    choice: "Split architecture and configuration into separate docs"
    rationale: "Different audiences: developers vs users"
    impact: "Easier to navigate and maintain"
    alternatives: ["Single comprehensive doc", "Split by namespace"]

  - id: merge-strategy-tables
    choice: "Document merge strategy with comparison tables"
    rationale: "Visual tables clarify complex merge behavior"
    impact: "Users can understand extends: global vs none"
    alternatives: ["Prose only", "Code examples only"]

metrics:
  tasks: 2
  commits: 2
  duration: "4m 33s"
  completed: 2026-01-25
---

# Phase 27 Plan 01: Foundation Documentation Summary

**One-liner:** System architecture with Mermaid diagrams and complete configuration reference covering merge strategies, all options, and common patterns

---

## What Was Built

Created the foundational documentation for aishell's internal architecture and configuration system:

### docs/ARCHITECTURE.md (369 lines)

**System Overview:**
- Mermaid diagram showing Host Machine → Docker Container data flow
- Key architectural principles (immutable base, config merge, security layers)

**Data Flow Documentation:**
- Build phase: CLI → Docker build → State persistence
- Run phase: Security checks → Docker run → Container execution
- Config merge: Global + Project → Merged configuration

**Namespace Responsibilities:**
- Core namespaces: cli, config, run, state, output, util, validation
- Docker namespaces: docker, build, templates, run, hash, spinner, extension
- Security namespaces: detection.*, gitleaks.*

**Key Files Reference:**
- Host files: state.edn, config.yaml, gitleaks-scan.edn
- Container files: entrypoint.sh, bashrc.aishell, /project mount
- State schema documentation with EDN example

**Extension System:**
- Project Dockerfile extension flow
- Auto-build behavior and caching
- Base dependency requirements

**Architecture Decisions:**
- Why Babashka (fast startup, single binary, shell interop)
- Why gosu (clean process tree, no sudo overhead)
- Why immutable base + extensions (fast iteration, reproducibility)
- Why two-layer security (speed vs thoroughness)

### docs/CONFIGURATION.md (907 lines)

**Configuration Files:**
- Global vs project config comparison table
- Loading behavior and fallback rules

**Config Inheritance:**
- `extends: global` vs `extends: none` syntax
- Merge strategy table by data type (lists, maps, map-of-lists, scalars, custom)
- When to use each strategy

**Full Annotated Example:**
- Complete config.yaml with all options
- Inline comments explaining each section
- Both formats for multi-format options (env, docker_args)

**Configuration Options Reference:**
Each option documented with:
- Purpose and type
- Syntax/formats
- Examples
- Notes and caveats
- Merge behavior
- Common use cases

Options covered:
- `extends` - Config inheritance
- `mounts` - Directory mounting (source-only, source:dest, read-only)
- `env` - Environment variables (map and array formats)
- `ports` - Port mapping (simple, IP binding, protocol)
- `docker_args` - Additional Docker flags (with security warnings)
- `pre_start` - Background command execution
- `harness_args` - Per-harness default arguments
- `gitleaks_freshness_check` - Scan freshness warnings
- `detection` - Sensitive file detection (enabled, custom_patterns, allowlist)

**Common Patterns:**
- Database credentials mounting
- Port exposure for web servers
- Custom Docker resource limits
- Per-harness model configuration
- Service startup with pre_start
- Project-specific sensitive file patterns
- Isolated project config (extends: none)
- Multi-service development environment

---

## Deviations from Plan

None - plan executed exactly as written.

---

## Testing

### Verification Performed

1. **File existence:**
   ```
   ✓ docs/ARCHITECTURE.md exists (369 lines > 100 min)
   ✓ docs/CONFIGURATION.md exists (907 lines > 150 min)
   ```

2. **Mermaid syntax:**
   ```
   ✓ docs/ARCHITECTURE.md contains valid ```mermaid block
   ✓ Diagram shows Host → Docker data flow
   ```

3. **Merge strategy documentation:**
   ```
   ✓ docs/CONFIGURATION.md explains extends: global vs none
   ✓ Merge strategy table documents all data types
   ✓ Full annotated config example present
   ```

4. **Namespace references:**
   ```
   ✓ docs/ARCHITECTURE.md documents aishell.cli
   ✓ docs/ARCHITECTURE.md documents aishell.config
   ✓ docs/ARCHITECTURE.md documents aishell.run
   ✓ docs/ARCHITECTURE.md documents aishell.docker.*
   ```

---

## Key Insights

1. **Mermaid renders natively on GitHub:**
   - No need for external image generation
   - Diagrams are version-controlled as text
   - Easy to update alongside code changes

2. **Configuration merge is complex:**
   - Different types merge differently (lists, maps, scalars)
   - `detection` config has custom merge logic
   - Tables and examples are essential for understanding

3. **Documentation serves different audiences:**
   - ARCHITECTURE.md → developers, contributors
   - CONFIGURATION.md → users, project setup
   - Clear separation improves navigation

4. **Annotated examples are powerful:**
   - Full config.yaml example shows all options
   - Inline comments provide context
   - Users can copy-paste and modify

---

## Next Phase Readiness

**Ready for:** Phase 27 Plan 03 (if exists) or phase completion

**Provides:**
- Architecture foundation for understanding system internals
- Configuration reference for project setup
- Examples and patterns for common use cases

**No blockers or concerns.**

---

## Files Changed

### Created

- `docs/ARCHITECTURE.md` (369 lines)
  - System overview with Mermaid diagrams
  - Data flow documentation
  - Namespace responsibilities
  - Key files reference
  - Extension system documentation

- `docs/CONFIGURATION.md` (907 lines)
  - Configuration files reference
  - Config inheritance and merge strategy
  - Full annotated example
  - Complete options reference
  - Common configuration patterns

### Modified

None
