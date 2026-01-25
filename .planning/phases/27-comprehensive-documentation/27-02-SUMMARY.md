---
phase: 27-comprehensive-documentation
plan: 02
subsystem: documentation
status: complete
tags: [documentation, harness-guide, authentication, setup, multi-provider]

requires:
  - 27-01: Environment variable documentation for reference
  - 26-01: README with authentication patterns

provides:
  - Per-harness setup and authentication guide
  - Harness comparison table
  - OAuth vs API key guidance
  - Container-specific authentication patterns

affects:
  - future-phases: Users can now set up any harness with detailed guidance

tech-stack:
  added: []
  patterns:
    - "Per-harness documentation structure"
    - "Authentication method comparison"
    - "Container auth nuances"

key-files:
  created:
    - docs/HARNESSES.md: "Comprehensive harness guide (614 lines)"
  modified: []

decisions:
  - id: harness-comparison-table
    choice: "Include comparison table at document start"
    rationale: "Users need quick reference to choose harness"
    impact: "Easier harness selection"
    alternatives: ["Comparison at end", "Separate comparison doc"]

metrics:
  tasks: 1
  commits: 1
  duration: "1m 45s"
  completed: 2026-01-25
---

# Phase 27 Plan 02: Harness Documentation Summary

**One-liner:** Comprehensive per-harness setup guide covering Claude, OpenCode, Codex, and Gemini with authentication methods and container patterns

## What Was Built

Created `docs/HARNESSES.md` with complete setup and usage documentation for all four AI harnesses supported by aishell.

**Key sections:**
- Harness comparison table (provider, auth, config, best use case)
- Per-harness guides (Claude Code, OpenCode, Codex CLI, Gemini CLI)
- Authentication quick reference (OAuth and API key methods)
- Multiple harness configuration
- Troubleshooting guide

**Each harness section includes:**
1. Overview (provider, models, capabilities)
2. Installation (build commands with version pinning)
3. Authentication (OAuth and API key methods)
4. Usage (basic commands, arguments, config)
5. Environment variables table
6. Configuration directories
7. Tips & best practices

**Documentation highlights:**
- 614 lines of comprehensive guidance
- OAuth vs API key presented equally (per decisions)
- Container-specific auth nuances documented
- Clear distinction between auth methods (e.g., Codex OPENAI_API_KEY vs CODEX_API_KEY)
- Gemini OAuth requirement (host auth first) prominently noted

## Tasks Completed

### Task 1: Create docs/HARNESSES.md ✓

**Commit:** `d3d041c`

Created comprehensive harness documentation with:
- Comparison table for quick harness selection
- Four complete harness sections (Claude, OpenCode, Codex, Gemini)
- Authentication guidance (OAuth and API key methods)
- Container-specific patterns
- Multiple harness setup section
- Authentication quick reference tables
- Troubleshooting section

**Files:**
- `docs/HARNESSES.md` (614 lines created)

**Verification passed:**
- File exists with 614+ lines (exceeds 200 minimum)
- All four harnesses documented
- Comparison table present
- 9 authentication sections found
- Installation, authentication, and usage covered per harness

## Decisions Made

### 1. Comparison Table Placement

**Decision:** Place comparison table at document start
**Rationale:** Users need quick reference to choose appropriate harness before diving into detailed setup
**Impact:** Improved user experience for harness selection
**Alternatives considered:**
- Comparison at end (after reading all details)
- Separate comparison document

### 2. Container Auth Patterns

**Decision:** Document container-specific authentication nuances in each harness section
**Rationale:** Authentication in containers differs from host (device codes, URL flows, host pre-auth)
**Impact:** Users understand container auth requirements upfront
**Examples documented:**
- Claude Code: Copy-paste URL flow works well
- Codex CLI: Device code with `--device-auth`
- Gemini CLI: Host authentication required first

### 3. OAuth and API Key Equality

**Decision:** Present OAuth and API key methods equally, without favoring one
**Rationale:** Both methods are valid; choice depends on user context (personal vs CI/CD)
**Impact:** Users make informed choice based on needs
**Alignment:** Follows v2.4.0 decision from earlier plans

## Deviations from Plan

None - plan executed exactly as written.

## Technical Implementation

### Documentation Structure

**Per-harness template:**
```markdown
## [Harness Name]
### Overview
### Installation
### Authentication
  - OAuth
  - API key
  - Vertex AI (if applicable)
### Usage
### Environment Variables
### Configuration Directory
### Tips & Best Practices
```

**Comparison dimensions:**
- Provider
- Auth methods
- Container auth approach
- Vertex AI support
- Config directory
- Best use case

### Key Documentation Patterns

**1. Version pinning examples:**
```bash
# Latest version
aishell build --with-claude

# Specific version
aishell build --with-claude=2.0.22
```

**2. API key usage:**
```bash
# Via export
export ANTHROPIC_API_KEY=sk-ant-...
aishell claude

# Via --env flag
aishell --env ANTHROPIC_API_KEY=sk-ant-... claude
```

**3. Container-specific auth:**
- Gemini: "IMPORTANT: OAuth requires host authentication first"
- Codex: "Device code flow recommended for containers"
- Claude: "OAuth works well in containers via copy-paste URL"

### Authentication Quick Reference

Created three quick reference tables:
1. OAuth authentication (method and container support per harness)
2. API key authentication (variable and source per harness)
3. Vertex AI authentication (OpenCode and Gemini only)

Provides rapid lookup for authentication setup.

## Testing/Verification

### Verification Performed

1. ✓ File exists with correct size (614 lines, 15K)
2. ✓ All four harnesses documented (Claude, OpenCode, Codex, Gemini)
3. ✓ Comparison table present with all expected columns
4. ✓ 9 authentication sections found (header + 4 harnesses × 2 sections each approx)
5. ✓ Installation, authentication, and usage covered for each harness

### Success Criteria Met

- [x] docs/HARNESSES.md exists with 200+ lines (614 actual)
- [x] Document contains comparison table
- [x] All four harnesses have dedicated sections
- [x] Each harness section covers: installation, authentication, usage
- [x] OAuth vs API key differences are clear for each harness
- [x] Container-specific authentication nuances are documented

## Next Phase Readiness

**Ready for:** Phase 27-03 (Configuration Documentation)

**Provides:**
- Harness setup reference
- Authentication method guidance
- Container auth patterns

**Enables:**
- Users can set up any harness
- Clear authentication troubleshooting
- Informed harness selection

**No blockers.**

## Artifacts

### Files Created

- `docs/HARNESSES.md` (614 lines)
  - Comprehensive harness guide
  - Comparison table
  - Per-harness setup/auth/usage
  - Quick reference tables

### Cross-References

**From HARNESSES.md:**
- References `ENVIRONMENT.md` for complete env var list
- References `CONFIGURATION.md` for config file details
- References `README.md` for main project docs

**To HARNESSES.md:**
- README.md should link to HARNESSES.md for harness setup
- ENVIRONMENT.md harness-specific variables reference this guide

## Performance

- **Tasks:** 1/1 completed
- **Commits:** 1 (d3d041c)
- **Duration:** 1m 45s
- **Lines documented:** 614

**Efficiency:** Single-task plan executed cleanly with comprehensive documentation coverage.

## Lessons Learned

### What Went Well

1. **Structured template approach:** Using consistent per-harness structure made documentation predictable and scannable
2. **Container-specific callouts:** Highlighting container auth differences (device codes, host pre-auth) addresses common pain points
3. **Quick reference tables:** Provide rapid lookup without reading full sections

### Documentation Patterns Established

1. **Harness guide structure:** Overview → Install → Auth → Usage → Env Vars → Config → Tips
2. **Auth method equality:** Present both OAuth and API key as equally valid
3. **Container nuances:** Always document container-specific behavior
4. **Quick references:** Provide summary tables for common lookups

### For Future Documentation

1. **Comparison tables:** Start complex guides with comparison tables for quick navigation
2. **Container focus:** Always document container-specific patterns (aishell's primary use case)
3. **Troubleshooting:** Include troubleshooting section for common issues
4. **Cross-references:** Link related docs (environment, config, README)

## Summary

Created comprehensive harness documentation covering all four supported AI harnesses (Claude Code, OpenCode, Codex CLI, Gemini CLI) with detailed setup, authentication, and usage guidance. Documentation includes comparison table for harness selection, per-harness sections following consistent structure, authentication quick reference tables, and container-specific patterns. All success criteria met with 614 lines of task-focused, setup-heavy documentation.

Ready for Phase 27-03 (Configuration Documentation).
