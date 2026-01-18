# Phase 8: Explicit Build/Update Commands - Research

**Researched:** 2026-01-18
**Domain:** Shell script state persistence, CLI subcommand patterns, XDG standard compliance
**Confidence:** HIGH

## Summary

This phase separates build and run concerns in the aishell CLI tool. The implementation requires:
1. A state persistence mechanism to store build flags per-project
2. Subcommand restructuring to add explicit `build` and `update` commands
3. Clear error messaging when commands are run without required prerequisites

The research confirms that XDG_STATE_HOME (`~/.local/state`) is the correct location for build state files. For state file format, shell variables (KEY=VALUE) are recommended over JSON due to native bash support without external dependencies, though jq is already available in the image. Project identification via path hashing (sha256sum truncated to 12 characters) is an established pattern already in use by the codebase.

**Primary recommendation:** Use shell variable format for state files with strict sourcing validation, implement subcommands via case-based dispatch (existing pattern), and provide actionable error messages with specific corrective commands.

## Standard Stack

The established libraries/tools for this domain:

### Core
| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| bash | 5.x | Script runtime | POSIX shell standard, already in use |
| sha256sum | coreutils | Project path hashing | Cryptographic uniqueness, already in use |
| mkdir -p | coreutils | Directory creation | Standard Unix, handles missing parents |

### Supporting
| Library | Version | Purpose | When to Use |
|---------|---------|---------|-------------|
| jq | 1.6 | JSON processing | Only if JSON state format chosen |
| realpath | coreutils | Canonical path resolution | When normalizing project paths |
| date | coreutils | Timestamp generation | For build metadata |

### Alternatives Considered
| Instead of | Could Use | Tradeoff |
|------------|-----------|----------|
| Shell variable state | JSON state | JSON requires jq dependency (already installed), but shell vars are simpler and native |
| sha256sum hash | md5sum hash | sha256 is more collision-resistant, already used in codebase |
| realpath | readlink -f | realpath is more portable across Linux/BSD |

**Installation:**
```bash
# All tools already available in Debian bookworm-slim
# jq is already installed in the Dockerfile
apt-get install -y coreutils
```

## Architecture Patterns

### Recommended State Directory Structure
```
~/.local/state/aishell/
├── builds/
│   ├── {hash12}.state      # Per-project build state
│   └── {hash12}.state
└── (future expansion space)
```

### Pattern 1: State File Format (Shell Variables)
**What:** Store build flags as sourceable shell variables
**When to use:** Simple key-value state without nested structures
**Example:**
```bash
# Source: https://flokoe.github.io/bash-hackers-wiki/howto/conffile/
# State file: ~/.local/state/aishell/builds/{hash12}.state

# aishell build state - DO NOT EDIT MANUALLY
# Project: /home/user/project
# Built: 2026-01-18T10:30:00Z
BUILD_WITH_CLAUDE=true
BUILD_WITH_OPENCODE=false
BUILD_CLAUDE_VERSION=""
BUILD_OPENCODE_VERSION=""
BUILD_IMAGE_TAG="aishell:claude-2.0.22"
BUILD_TIMESTAMP="2026-01-18T10:30:00Z"
```

### Pattern 2: Secure State File Sourcing
**What:** Validate state file before sourcing to prevent code injection
**When to use:** Always when sourcing external files
**Example:**
```bash
# Source: Apple Developer Shell Script Security
load_state_file() {
    local state_file="$1"

    [[ ! -f "$state_file" ]] && return 1

    # Validate file contains only allowed patterns:
    # - Comments (lines starting with #)
    # - Variable assignments (NAME=value or NAME="value")
    # - Empty lines
    if grep -qvE '^(#.*|[A-Z_]+="?[^"]*"?|[[:space:]]*)$' "$state_file"; then
        error "State file contains invalid content: $state_file"
        return 1
    fi

    # shellcheck source=/dev/null
    source "$state_file"
}
```

### Pattern 3: Subcommand Dispatch
**What:** Route subcommands via case statement before option parsing
**When to use:** CLIs with distinct operation modes
**Example:**
```bash
# Source: https://clig.dev/
main() {
    local command="${1:-}"

    case "$command" in
        build)
            shift
            do_build "$@"
            ;;
        update)
            shift
            do_update "$@"
            ;;
        claude|opencode)
            # Run harness (existing behavior)
            shift
            run_harness "$command" "$@"
            ;;
        ""|--help|-h)
            # No command = shell, or help requested
            if [[ "$command" == "--help" ]] || [[ "$command" == "-h" ]]; then
                usage
                exit 0
            fi
            run_shell "$@"
            ;;
        -*)
            # Flag without command - could be global option or error
            parse_global_options "$@"
            ;;
        *)
            error "Unknown command: $command. Run 'aishell --help' for usage."
            ;;
    esac
}
```

### Pattern 4: Project Identification via Path Hash
**What:** Generate unique identifier from canonical project path
**When to use:** Per-project state storage keyed by location
**Example:**
```bash
# Already in codebase - verify canonical path first
get_project_hash() {
    local project_dir="$1"
    local canonical_path

    # Resolve to canonical path (handles symlinks, .., etc)
    canonical_path=$(realpath "$project_dir" 2>/dev/null) || canonical_path="$project_dir"

    # Hash and truncate to 12 chars (matches existing pattern)
    echo -n "$canonical_path" | sha256sum | cut -c1-12
}
```

### Anti-Patterns to Avoid
- **Catch-all subcommand:** Never allow omitting a frequently-used subcommand - prevents future command expansion
- **Ambiguous command names:** Avoid similarly-named commands like "update" and "upgrade" - choose distinct terms
- **Implicit state mutation:** Always inform users when state changes (build flags saved, image built, etc.)
- **Sourcing untrusted files:** Never source state files without validation - they execute as bash code

## Don't Hand-Roll

Problems that look simple but have existing solutions:

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| JSON state parsing | grep/sed JSON parsing | jq | Edge cases with escaping, nested values |
| Path canonicalization | Manual .. resolution | realpath | Symlink resolution, edge cases |
| Unique identifiers | Random strings | sha256sum of path | Deterministic, collision-resistant |
| Option parsing | Manual flag loops | Case-based dispatch | Already working, getopts adds complexity |
| Temp file handling | Manual cleanup | mktemp + trap | Cleanup on all exit paths |

**Key insight:** The codebase already has working patterns for most needs. The main addition is state file management, which should follow established shell configuration patterns.

## Common Pitfalls

### Pitfall 1: State File Race Conditions
**What goes wrong:** Two aishell processes write to the same state file simultaneously
**Why it happens:** Parallel builds of same project, or user runs build while another is in progress
**How to avoid:** Write to temp file, then atomic mv; check for running builds via lockfile
**Warning signs:** Corrupted state files, missing flags after concurrent operations

### Pitfall 2: Symlink Confusion in Project Paths
**What goes wrong:** Same project accessed via different paths (symlink vs real path) gets different state files
**Why it happens:** User cd's into project via symlink one time, real path another time
**How to avoid:** Always canonicalize path with `realpath` before hashing
**Warning signs:** "No previous build found" when user knows they built before

### Pitfall 3: State File Security - Code Injection
**What goes wrong:** Malicious state file executes arbitrary code when sourced
**Why it happens:** Source command executes entire file as bash script
**How to avoid:** Validate state file contents before sourcing; only allow known patterns
**Warning signs:** Unexpected behavior after switching projects, errors mentioning command not found

### Pitfall 4: XDG_STATE_HOME Override Ignored
**What goes wrong:** State stored in wrong location when user has custom XDG_STATE_HOME
**Why it happens:** Hardcoding `~/.local/state` instead of checking environment variable
**How to avoid:** Use `${XDG_STATE_HOME:-$HOME/.local/state}` pattern
**Warning signs:** Works on dev machine, fails on user's system with custom XDG setup

### Pitfall 5: Unhelpful Error Messages
**What goes wrong:** User sees "build required" but doesn't know what command to run
**Why it happens:** Error message states problem but doesn't provide solution
**How to avoid:** Every error should include the corrective command
**Warning signs:** User frustration, repeated questions about "what do I do now?"

### Pitfall 6: Update Losing Existing Flags
**What goes wrong:** `aishell update --with-opencode` replaces all flags instead of merging
**Why it happens:** Update command overwrites state instead of merging new flags with existing
**How to avoid:** Load existing state first, merge new flags, then save combined state
**Warning signs:** "Where did my Claude installation go?" after running update

## Code Examples

Verified patterns from official sources and existing codebase:

### State Directory Setup
```bash
# Source: XDG Base Directory Specification
setup_state_dir() {
    local state_base="${XDG_STATE_HOME:-$HOME/.local/state}"
    local state_dir="$state_base/aishell/builds"

    mkdir -p "$state_dir"
    echo "$state_dir"
}
```

### Write State File (Atomic)
```bash
# Source: Shell script best practices
write_state_file() {
    local state_file="$1"
    local project_path="$2"
    local with_claude="$3"
    local with_opencode="$4"
    local claude_version="$5"
    local opencode_version="$6"
    local image_tag="$7"

    local tmp_file
    tmp_file=$(mktemp)

    cat > "$tmp_file" << EOF
# aishell build state - DO NOT EDIT MANUALLY
# Project: $project_path
# Built: $(date -Iseconds)
BUILD_WITH_CLAUDE=$with_claude
BUILD_WITH_OPENCODE=$with_opencode
BUILD_CLAUDE_VERSION="$claude_version"
BUILD_OPENCODE_VERSION="$opencode_version"
BUILD_IMAGE_TAG="$image_tag"
BUILD_TIMESTAMP="$(date -Iseconds)"
EOF

    # Atomic move
    mv "$tmp_file" "$state_file"
}
```

### Read State File (Safe)
```bash
# Source: Bash Hackers Wiki
read_state_file() {
    local state_file="$1"

    # Initialize defaults
    BUILD_WITH_CLAUDE=false
    BUILD_WITH_OPENCODE=false
    BUILD_CLAUDE_VERSION=""
    BUILD_OPENCODE_VERSION=""
    BUILD_IMAGE_TAG=""
    BUILD_TIMESTAMP=""

    [[ ! -f "$state_file" ]] && return 1

    # Validate: only comments, empty lines, and VAR=value allowed
    if grep -qvE '^(#.*|BUILD_[A-Z_]+="?[^"]*"?|[[:space:]]*)$' "$state_file"; then
        warn "State file appears corrupted: $state_file"
        return 1
    fi

    # Safe to source
    # shellcheck source=/dev/null
    source "$state_file"
    return 0
}
```

### Error Message with Action
```bash
# Source: https://clig.dev/ and https://medium.com/@czhoudev/error-handling-in-cli-tools
error_no_build() {
    local project_hash="$1"
    echo -e "${RED}Error:${NC} No previous build found for this project." >&2
    echo "" >&2
    echo "To build with Claude Code:" >&2
    echo "    aishell build --with-claude" >&2
    echo "" >&2
    echo "To build with OpenCode:" >&2
    echo "    aishell build --with-opencode" >&2
    echo "" >&2
    echo "To build with both:" >&2
    echo "    aishell build --with-claude --with-opencode" >&2
    exit 1
}

error_missing_harness() {
    local harness="$1"
    local flag="--with-$harness"
    echo -e "${RED}Error:${NC} $harness was not included in the build." >&2
    echo "" >&2
    echo "Current build does not include $harness." >&2
    echo "" >&2
    echo "To add $harness to the build:" >&2
    echo "    aishell update $flag" >&2
    echo "" >&2
    echo "Or rebuild with $harness:" >&2
    echo "    aishell build $flag" >&2
    exit 1
}
```

### Flag Merging for Update
```bash
# Source: CLI design best practices
merge_update_flags() {
    # Load existing state
    local state_file="$1"
    shift

    read_state_file "$state_file" || {
        error_no_build
        return 1
    }

    # Parse new flags - they ADD to existing, don't replace
    while [[ $# -gt 0 ]]; do
        case "$1" in
            --with-claude)
                BUILD_WITH_CLAUDE=true
                shift
                ;;
            --with-opencode)
                BUILD_WITH_OPENCODE=true
                shift
                ;;
            --claude-version=*)
                BUILD_CLAUDE_VERSION="${1#--claude-version=}"
                BUILD_WITH_CLAUDE=true
                shift
                ;;
            --opencode-version=*)
                BUILD_OPENCODE_VERSION="${1#--opencode-version=}"
                BUILD_WITH_OPENCODE=true
                shift
                ;;
            *)
                shift
                ;;
        esac
    done

    # Now BUILD_* vars contain merged state
}
```

### Build Preview Before Starting
```bash
# Source: CLI UX patterns (git status pattern)
show_build_preview() {
    local with_claude="$1"
    local with_opencode="$2"
    local claude_version="$3"
    local opencode_version="$4"
    local is_update="$5"

    local action="Building"
    [[ "$is_update" == true ]] && action="Updating"

    echo "$action image with:"

    if [[ "$with_claude" == true ]]; then
        if [[ -n "$claude_version" ]]; then
            echo "  - Claude Code v$claude_version"
        else
            echo "  - Claude Code (latest)"
        fi
    fi

    if [[ "$with_opencode" == true ]]; then
        if [[ -n "$opencode_version" ]]; then
            echo "  - OpenCode v$opencode_version"
        else
            echo "  - OpenCode (latest)"
        fi
    fi

    if [[ "$with_claude" != true ]] && [[ "$with_opencode" != true ]]; then
        echo "  - Base image only (shell access)"
    fi

    echo ""
}
```

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| Auto-build on first run | Explicit build command | Phase 8 | Clearer user intent, no surprise builds |
| No state persistence | XDG_STATE_HOME state files | Phase 8 | Enables update command, tracks build config |
| Implicit harness detection | Explicit requirement errors | Phase 8 | Clear errors when harness not built |

**Deprecated/outdated:**
- Auto-rebuild when --with-* flags passed: Will be replaced by explicit build command
- Implicit image selection: Will require prior build

## Open Questions

Things that couldn't be fully resolved:

1. **Exit code strategy**
   - What we know: Non-zero on failure is standard; distinct codes help scripting
   - What's unclear: Whether distinct codes (e.g., 2=no build, 3=missing harness) add value vs simple 0/1
   - Recommendation: Start with 0/1, add distinct codes if users request them for scripting

2. **Stale state detection**
   - What we know: State file could become stale if image is manually deleted
   - What's unclear: Whether to validate image existence on every run vs trust state
   - Recommendation: Trust state by default; add --verify flag if users report issues

3. **Concurrent build protection**
   - What we know: Two builds to same project could corrupt state
   - What's unclear: Whether lockfile adds more complexity than it solves
   - Recommendation: Defer lockfile until users report actual concurrent build issues

## Sources

### Primary (HIGH confidence)
- [XDG Base Directory Specification](https://specifications.freedesktop.org/basedir/latest/) - State directory standard
- [Command Line Interface Guidelines](https://clig.dev/) - Subcommand patterns, error handling, state management
- [Bash Hackers Wiki - Config Files](https://flokoe.github.io/bash-hackers-wiki/howto/conffile/) - Secure state file sourcing

### Secondary (MEDIUM confidence)
- [Apple Developer - Shell Script Security](https://developer.apple.com/library/archive/documentation/OpenSource/Conceptual/ShellScripting/ShellScriptSecurity/ShellScriptSecurity.html) - Security considerations for sourcing files
- [Baeldung - SHA-256 from Command Line](https://www.baeldung.com/linux/sha-256-from-command-line) - Path hashing patterns
- [Debian Package - jq](https://packages.debian.org/bookworm/jq) - jq 1.6 in bookworm

### Tertiary (LOW confidence)
- Medium articles on CLI error handling patterns - General UX guidance
- Stack Overflow discussions on bash subcommand patterns - Community patterns

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH - All tools already available, XDG spec well-documented
- Architecture: HIGH - Patterns from official CLI guidelines and existing codebase
- Pitfalls: MEDIUM - Based on general shell scripting experience, not specific failures

**Research date:** 2026-01-18
**Valid until:** 30 days (stable domain, shell patterns don't change rapidly)
