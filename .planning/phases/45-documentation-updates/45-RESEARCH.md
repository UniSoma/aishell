# Phase 45: Documentation Updates - Research

**Researched:** 2026-02-05
**Domain:** Technical documentation, Markdown, user-facing docs
**Confidence:** HIGH

## Summary

Phase 45 updates all user-facing documentation to reflect the Gitleaks opt-in default completed in Phase 44. The changes are straightforward: replace references to `--without-gitleaks` with `--with-gitleaks`, update explanations of default behavior from "installed by default" to "opt-in via flag", and clarify when Gitleaks commands/warnings apply.

The documentation changes span four files (README.md, CONFIGURATION.md, TROUBLESHOOTING.md, HARNESSES.md) plus ARCHITECTURE.md (internal reference doc). All changes are content updates to existing sections—no new documentation structure required.

**Primary recommendation:** Update all documentation files to reflect opt-in semantics, ensuring consistency with the actual implementation from Phase 44.

## Standard Stack

This is a documentation-only phase. No libraries or tools are required beyond text editing.

### Core

| Tool | Version | Purpose | Why Standard |
|------|---------|---------|--------------|
| Markdown | - | Documentation format | Universal standard for technical docs |
| Text editor | - | Content editing | Any editor works |

### Supporting

None required. This is pure content editing.

## Architecture Patterns

### Documentation Update Pattern

**Structure:**
```
docs/
├── README.md              # User-facing overview (root level)
├── CONFIGURATION.md       # Complete config reference
├── TROUBLESHOOTING.md     # Problem/solution guide
├── HARNESSES.md           # Harness-specific documentation
└── ARCHITECTURE.md        # Internal reference (tech stack)
```

**Update Strategy:**
1. Identify all references to old behavior (--without-gitleaks, default installation)
2. Update to new behavior (--with-gitleaks, opt-in installation)
3. Maintain consistency across all docs
4. Preserve existing structure and formatting

### Pattern 1: Flag Reference Updates

**What:** Replace all CLI flag references from negative to positive form
**When to use:** Anywhere the build flag is mentioned

**Example:**
```markdown
# Old (opt-out)
aishell setup --with-claude --without-gitleaks

# New (opt-in)
aishell setup --with-claude --with-gitleaks
```

### Pattern 2: Default Behavior Explanations

**What:** Update prose explanations of what happens by default
**When to use:** Sections explaining build behavior, image contents, or default state

**Example:**
```markdown
# Old
By default, aishell installs Gitleaks in the foundation image (~15MB)

# New
Gitleaks is opt-in. Use --with-gitleaks to install it during build.
```

### Pattern 3: Conditional Behavior Clarifications

**What:** Clarify when Gitleaks features are available (only when installed)
**When to use:** Sections about gitleaks commands, warnings, or freshness checks

**Example:**
```markdown
# Old
aishell gitleaks detect

# New
aishell gitleaks detect  # Only available when built with --with-gitleaks
```

### Anti-Patterns to Avoid

- **Incomplete updates:** Don't update some references but leave others with old behavior
- **Inconsistent terminology:** Use "opt-in" consistently, not a mix of "optional", "disabled by default", etc.
- **Removing all Gitleaks info:** Don't delete documentation—update it to reflect conditional availability

## Don't Hand-Roll

Not applicable—this is documentation editing, not code development.

## Common Pitfalls

### Pitfall 1: Missing References in Examples
**What goes wrong:** Code examples show the old flag but explanatory text is updated
**Why it happens:** Examples are often in code blocks, easy to miss during search
**How to avoid:** Use grep to find ALL occurrences, including in code blocks
**Warning signs:** Examples don't match the narrative

### Pitfall 2: Contradictory Statements
**What goes wrong:** One section says "installed by default", another says "opt-in"
**Why it happens:** Documentation files are updated independently without cross-checking
**How to avoid:** Read all four files after updates to verify consistency
**Warning signs:** User confusion, conflicting information

### Pitfall 3: Over-Correction
**What goes wrong:** Changing text that refers to the feature itself, not the flag
**Why it happens:** Search-and-replace without context
**How to avoid:** Review each change—some "Gitleaks" mentions don't need updating
**Warning signs:** Awkward phrasing like "opt-in Gitleaks" where "Gitleaks" alone is correct

### Pitfall 4: Breaking Existing Links
**What goes wrong:** Section headings change, breaking internal anchor links
**Why it happens:** Editing headings without checking for references
**How to avoid:** Only update content, not section structure/headings
**Warning signs:** Dead links in table of contents

## Code Examples

### Finding All References

Search patterns to find every mention that may need updating:

```bash
# Find flag references (should be --with-gitleaks now)
grep -rn "without-gitleaks" docs/ README.md

# Find default behavior explanations
grep -rn "by default.*Gitleaks\|Gitleaks.*default" docs/ README.md

# Find installation state references
grep -rn "installed.*Gitleaks\|Gitleaks.*installed" docs/ README.md

# Find conditional references (may need clarification)
grep -rn "aishell gitleaks\|gitleaks command" docs/ README.md
```

## State of the Art

| Approach | Status | When Changed | Impact |
|----------|--------|--------------|--------|
| Opt-out (--without-gitleaks) | Deprecated | Phase 44 (2026-02-05) | Flag no longer exists |
| Opt-in (--with-gitleaks) | Current | Phase 44 (2026-02-05) | Must be documented everywhere |
| Default: installed | Outdated | Phase 44 (2026-02-05) | Now default: NOT installed |
| Default: not installed | Current | Phase 44 (2026-02-05) | Must be documented everywhere |

**Deprecated/outdated:**
- `--without-gitleaks` flag: Replaced by opt-in design (absence of --with-gitleaks)
- "Gitleaks installed by default": Changed to opt-in via --with-gitleaks

## Documentation Locations

### Files Requiring Updates

Based on grep analysis, these files contain Gitleaks references:

| File | Lines | Update Type | Priority |
|------|-------|-------------|----------|
| **README.md** | ~112, 223, 301-308, 424 | Flag examples, feature description, default behavior | HIGH (user-facing) |
| **CONFIGURATION.md** | ~1169-1201 | Build flag documentation, image size impact | HIGH (reference) |
| **TROUBLESHOOTING.md** | ~738-771 | Gitleaks troubleshooting section | MEDIUM (conditional use) |
| **HARNESSES.md** | - | No direct Gitleaks mentions found | LOW (verify) |
| **ARCHITECTURE.md** | ~78, 509 | Internal state tracking, tech stack | LOW (internal) |

### Specific Sections to Update

**README.md:**
- Line 112: Features list ("Gitleaks integration")
- Line 223: Check command description (gitleaks scan freshness)
- Lines 299-310: "Gitleaks" section (usage, when available)
- Line 424: Foundation image contents

**CONFIGURATION.md:**
- Section "### --without-gitleaks" (rename to --with-gitleaks)
- Lines 1169-1201: Complete rewrite for opt-in semantics
- Line 1258: Update command description

**TROUBLESHOOTING.md:**
- Lines 738-771: Add note that troubleshooting applies only when Gitleaks installed
- Add section on "Gitleaks command not found" (user didn't use --with-gitleaks)

**HARNESSES.md:**
- Verify no Gitleaks-specific content (grep found no matches)
- If mentioned, clarify it's opt-in

**ARCHITECTURE.md:**
- Line 78: Update to reflect opt-in nature
- Line 509: Verify state tracking documentation is accurate

## Required Changes by File

### README.md Updates

1. **Features section (line 112):**
   ```markdown
   # Current
   - **Gitleaks integration** - Deep content-based secret scanning with `aishell gitleaks`

   # Updated
   - **Gitleaks integration** - Opt-in deep content-based secret scanning with `aishell gitleaks` (requires `--with-gitleaks` flag)
   ```

2. **Check command (line 223):**
   ```markdown
   # Current
   Checks Docker availability, build state, image existence, configuration validity, mount paths, sensitive files, and gitleaks scan freshness.

   # Updated
   Checks Docker availability, build state, image existence, configuration validity, mount paths, sensitive files, and gitleaks scan freshness (when installed).
   ```

3. **Gitleaks section (lines 299-310):**
   ```markdown
   # Current
   ### Gitleaks

   Use `aishell gitleaks` for content-based secret detection inside the container:

   # Updated
   ### Gitleaks (Optional)

   When built with `--with-gitleaks`, use `aishell gitleaks` for content-based secret detection:

   ```bash
   # Enable Gitleaks at build time
   aishell setup --with-claude --with-gitleaks

   # Then use gitleaks commands
   aishell gitleaks detect
   aishell gitleaks detect --verbose --no-git
   ```
   ```

4. **Foundation image contents (line 424):**
   ```markdown
   # Current
   **Security tools:**
   - Gitleaks v8.30.0 (secret scanning)

   # Updated
   **Security tools:**
   - Gitleaks v8.30.0 (optional, via `--with-gitleaks` flag)
   ```

### CONFIGURATION.md Updates

1. **Rename section:** "### --without-gitleaks" → "### --with-gitleaks"

2. **Rewrite section (lines 1169-1201):**
   ```markdown
   ### --with-gitleaks

   **Purpose:** Install Gitleaks during build for content-based secret scanning.

   **Usage:**
   ```bash
   aishell setup --with-claude --with-gitleaks
   ```

   **Behavior:**
   - By default, aishell does NOT install Gitleaks
   - `--with-gitleaks` enables Gitleaks installation (~15MB)
   - Build state records installation status in `~/.aishell/state.edn`
   - `aishell --help` shows the `gitleaks` command only when installed

   **State tracking:**
   ```bash
   # Check what was installed
   cat ~/.aishell/state.edn
   # Shows :with-gitleaks true or false
   ```

   **When to use:**
   - **Content scanning:** You want deep secret detection beyond filename patterns
   - **Pre-commit safety:** Run scans before AI agents see your code
   - **Compliance:** Your workflow requires secret scanning

   **Image size impact:**
   - Without Gitleaks: ~265MB
   - With Gitleaks: ~280MB
   - Cost: ~15MB

   **Note:** `aishell gitleaks` only works when built with `--with-gitleaks`. Without it, the command is not available.
   ```

3. **Update command description (line 1258):**
   ```markdown
   # Current
   - Gitleaks installation status

   # Updated
   - Gitleaks installation status (`--with-gitleaks` flag)
   ```

### TROUBLESHOOTING.md Updates

1. **Add new section after "Sensitive File Detection":**
   ```markdown
   ### Symptom: "gitleaks: command not found" or "No gitleaks command in help"

   **Cause:** Gitleaks was not installed during build. It is opt-in via `--with-gitleaks` flag.

   **Resolution:**

   1. **Rebuild with Gitleaks:**
      ```bash
      aishell setup --with-claude --with-gitleaks
      ```

   2. **Verify Gitleaks is installed:**
      ```bash
      aishell
      gitleaks version
      # Should show: v8.30.0
      ```

   3. **Check state file:**
      ```bash
      cat ~/.aishell/state.edn
      # Should show: :with-gitleaks true
      ```

   4. **Alternative: Use filename-based detection only:**
      If you don't need deep content scanning, aishell's built-in filename-based detection works without Gitleaks.
   ```

2. **Update existing Gitleaks section (lines 738-771):**
   Add at the top:
   ```markdown
   **Note:** These issues apply only when Gitleaks is installed (`--with-gitleaks` flag). If you don't have Gitleaks, see "gitleaks: command not found" above.
   ```

### HARNESSES.md Updates

Verify no updates needed (grep found no matches). If any Gitleaks references exist:
- Clarify that it's an optional build-time feature
- Link to CONFIGURATION.md for setup instructions

### ARCHITECTURE.md Updates

1. **Line 78:**
   ```markdown
   # Current
   - Gitleaks binary (optional, via `--without-gitleaks`)

   # Updated
   - Gitleaks binary (opt-in, via `--with-gitleaks`)
   ```

2. **Line 509:**
   ```markdown
   # Current
    :with-gitleaks true                     ; boolean: Gitleaks installed?

   # Updated (likely already correct, verify)
    :with-gitleaks false                    ; boolean: Gitleaks installed? (default false, opt-in)
   ```

## Open Questions

None. The changes are well-defined by the Phase 44 implementation.

## Sources

### Primary (HIGH confidence)
- Phase 44 verification report (44-VERIFICATION.md) - Implementation details
- Phase 44 summary (44-01-SUMMARY.md) - Completed changes
- Current codebase (src/aishell/cli.clj, templates.clj, state.clj) - Actual implementation
- Existing documentation (README.md, CONFIGURATION.md, etc.) - Current state

### Secondary (MEDIUM confidence)
- None required

### Tertiary (LOW confidence)
- None required

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH - Plain Markdown editing, no tools required
- Architecture: HIGH - Straightforward content updates to existing files
- Pitfalls: HIGH - Common documentation pitfalls are well-understood

**Research date:** 2026-02-05
**Valid until:** 90 days (stable—documentation patterns don't change frequently)
