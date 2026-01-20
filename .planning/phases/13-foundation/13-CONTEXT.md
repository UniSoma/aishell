# Phase 13: Foundation - Context

**Gathered:** 2026-01-20
**Status:** Ready for planning

<domain>
## Phase Boundary

Establish project structure and core CLI (--version, --help, error handling) that all subsequent phases build on. This is the Babashka rewrite foundation — no Docker operations, no build/run commands yet.

</domain>

<decisions>
## Implementation Decisions

### Help output design
- Comprehensive help: commands, flags, examples, and usage patterns
- Inline examples per command (1-2 examples shown with each command)
- Colorful output when terminal supports it (commands highlighted, flags in different color)
- Command grouping: Claude's discretion based on command count and clarity

### Error message style
- Errors suggest fixes (e.g., "Did you mean: build?" or "Try: aishell --help")
- Red colored prefix for errors, yellow for warnings
- Verbosity: Claude decides appropriate level per error type
- Format style: Claude decides (structured prefix vs natural sentences)

### Version output format
- Output content: Claude decides (version only vs name+version vs extended info)
- Machine-readable format via `--version --json` flag
- Update checking is separate command (--version does not check for updates)

### Command structure
- Default (no args) enters container shell
- Short flag aliases for common flags: -h (--help), -v (--version)
- No explicit 'shell' subcommand — just run 'aishell' to enter shell
- Global flag position: Claude decides convention

### Project structure
- Source files in src/ directory (standard: src/aishell/core.clj, etc.)
- v1.x Bash code kept during parallel development
- Entry point named 'aishell' (same name as v1, will replace when ready)
- File organization (single vs multi-file): Claude decides based on complexity

### Claude's Discretion
- Help command grouping strategy
- Error message format style (structured vs natural)
- Error verbosity per error type
- Version output content level
- Global flag positioning convention
- Single file vs multi-file code organization

</decisions>

<specifics>
## Specific Ideas

No specific requirements — open to standard approaches for CLI tools.

</specifics>

<deferred>
## Deferred Ideas

None — discussion stayed within phase scope.

</deferred>

---

*Phase: 13-foundation*
*Context gathered: 2026-01-20*
