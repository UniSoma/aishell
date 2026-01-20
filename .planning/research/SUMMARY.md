# Project Research Summary

**Project:** aishell CLI rewrite from Bash to Babashka
**Domain:** Cross-platform CLI tool with Docker integration
**Researched:** 2026-01-20
**Confidence:** HIGH

## Executive Summary

Rewriting aishell from 1,655 LOC Bash to Babashka is a well-supported migration path. Babashka provides all required functionality through built-in libraries: `babashka.cli` for argument parsing, `babashka.process` for Docker command execution, `babashka.fs` for cross-platform file operations, and native EDN/YAML/JSON parsing. **No external dependencies (pods) are required.** The estimated final implementation will be significantly simpler than Bash due to structured data handling, proper error management with try/catch, and elimination of shell quoting complexity.

The recommended approach is a phased rewrite starting with core CLI infrastructure (Phase 1), then Docker integration (Phase 2), followed by build commands (Phase 3), run commands (Phase 4), and finally cross-platform polish including Windows support (Phase 5). This order respects dependencies and delivers incremental value. The architecture separates concerns into clear namespaces: `aishell.cli`, `aishell.docker`, `aishell.build`, `aishell.run`, `aishell.config`, `aishell.state`, and `aishell.output`.

Key risks center on cross-platform compatibility, particularly Windows. The top pitfalls to address early are: (1) `babashka.process/shell` does NOT invoke an actual shell, so glob expansion and pipes fail without explicit handling; (2) environment variable case sensitivity on Windows; (3) UID/GID concepts don't exist on Windows, requiring conditional permission mapping; (4) SSH agent socket paths differ dramatically across platforms. These are all well-documented with clear mitigations, giving HIGH confidence in successful delivery.

## Key Findings

### Recommended Stack

Babashka v1.12.214 provides a complete replacement for the current Bash implementation. All functionality is available through built-in libraries with no external pods required.

**Core technologies:**
- **babashka.cli**: CLI argument parsing with subcommand dispatch, coercion, validation, help generation
- **babashka.process**: Docker command execution with captured/streaming output, cross-platform
- **babashka.fs**: File system operations, temp file management, path normalization (handles Windows backslashes)
- **clojure.edn + clj-yaml**: Configuration file parsing (EDN native, YAML built-in)
- **cheshire.core**: JSON handling for Docker inspect output parsing
- **Java interop**: SHA256 hashing via `MessageDigest`, shutdown hooks via `Runtime/addShutdownHook`

**Key version requirement:** Babashka >= 1.12.214 (released 2026-01-13)

### Expected Features

**Must have (table stakes):**
- Subcommand dispatch: `aishell build`, `aishell claude`, `aishell update`
- Spec-based CLI options with `:desc`, `:alias`, `:coerce`, `:default`
- Auto-generated help via `--help`/`-h`
- Clear error messages for invalid inputs
- Exit code handling (non-zero on errors)
- Cross-platform path handling

**Should have (differentiators from Bash):**
- EDN configuration files (simpler than shell `.conf` format)
- Structured data throughout (maps/vectors instead of arrays/variables)
- Proper error handling with try/catch and ex-info
- Type-safe configuration parsing and validation
- Superior testability (REPL-driven development, unit tests)

**Defer (v2+):**
- Spinner/progress indicators (requires async/threading)
- Full Windows support (functional but less tested)
- Migration tooling for existing `.conf` files

### Architecture Approach

The architecture separates concerns into distinct namespaces following standard Babashka patterns. The entry point (`aishell.core`) dispatches to command handlers, each namespace has clear responsibilities, and I/O is isolated at boundaries while keeping business logic pure.

**Major components:**
1. **aishell.cli** - Argument parsing, subcommand routing, spec definitions
2. **aishell.docker** - Docker CLI wrapper (build-image, run-container, image-exists?, inspect)
3. **aishell.build** - Build command logic, Dockerfile generation, image tag computation
4. **aishell.run** - Run/shell/exec logic, mount building, API env vars
5. **aishell.config** - run.conf parsing, validation, mount/env/port arg building
6. **aishell.state** - Build state persistence (EDN format, XDG paths)
7. **aishell.output** - Terminal output, colors, verbose mode

**Key pattern:** Pure functions compute what to do; impure functions at boundaries execute it.

### Critical Pitfalls

1. **shell doesn't invoke actual shell** - `babashka.process/shell` directly executes programs. Globs (`*.txt`), pipes (`|`), and environment variable expansion don't work. Use `babashka.fs/glob` for patterns, explicit shell invocation (`bash -c "..."`) when needed.

2. **Environment variable case sensitivity on Windows** - Setting `{"PATH" "..."}` won't update Windows' `Path` variable. Create platform-aware env key normalization in Phase 1.

3. **UID/GID concepts missing on Windows** - Docker permission mapping via `id -u`/`id -g` must be conditional. Docker Desktop for Windows handles permissions differently (no UID/GID needed).

4. **exec() only works in native images** - `babashka.process/exec` (process replacement) only works with the native bb binary, not JVM execution. Document this requirement clearly.

5. **SSH socket paths differ across platforms** - Linux uses `$SSH_AUTH_SOCK`, macOS Docker Desktop uses `/run/host-services/ssh-auth.sock`, Windows requires npiperelay. Build platform-specific detection.

## Implications for Roadmap

Based on research, suggested phase structure:

### Phase 1: Foundation
**Rationale:** Establishes project structure and core utilities that all subsequent phases depend on. Must address critical cross-platform pitfalls upfront.
**Delivers:** Basic CLI skeleton, `aishell --version`, `aishell --help`
**Addresses:** Table stakes (CLI options, help generation, error messages)
**Avoids:** Environment variable case sensitivity, path handling issues, shell expansion misunderstanding
**Exit criteria:** `./aishell --version` and `./aishell --help` work

### Phase 2: Docker Integration
**Rationale:** Docker is the core dependency. All commands need Docker operations (check, build, run, inspect).
**Delivers:** Docker wrapper module, pre-flight checks ("Docker not running" error)
**Uses:** babashka.process for shell execution
**Implements:** aishell.docker namespace
**Exit criteria:** `./aishell` shows appropriate "Docker not running" or "No build found" message

### Phase 3: Build Command
**Rationale:** Build must work before run. Creates images that run commands depend on.
**Delivers:** `aishell build --with-claude` creates Docker image, state persistence
**Uses:** babashka.fs for temp files, EDN for state
**Implements:** aishell.build, aishell.state namespaces
**Avoids:** Heredoc migration issues (use multi-line strings)
**Exit criteria:** Can build image, state file created in XDG location

### Phase 4: Run Commands
**Rationale:** Run commands are the primary user-facing feature. Depends on working build.
**Delivers:** `aishell claude`, `aishell opencode`, `aishell` (shell) launch containers
**Uses:** babashka.process for container execution with TTY
**Implements:** aishell.run, aishell.config namespaces
**Avoids:** SSH socket forwarding issues, TTY detection problems
**Exit criteria:** Full `aishell claude` flow works on Linux/macOS

### Phase 5: Cross-Platform Polish
**Rationale:** Windows support and edge cases after core functionality proven.
**Delivers:** Windows compatibility, update command, extension Dockerfile handling
**Addresses:** Windows-specific pitfalls (PATH case, Cygwin paths, USERPROFILE)
**Exit criteria:** Feature parity with v1.2 Bash implementation

### Phase 6: Testing and Distribution
**Rationale:** Solidify implementation before distribution.
**Delivers:** Test suite, uberscript for single-file distribution, documentation
**Exit criteria:** CI green, release artifact ready

### Phase Ordering Rationale

- **Dependencies flow downward:** CLI parsing -> Docker wrapper -> Build command -> Run commands
- **Risk mitigation early:** Phase 1 addresses the most critical cross-platform pitfalls before building on them
- **Incremental delivery:** Each phase produces testable artifacts
- **Windows deferred:** Core functionality on Linux/macOS first, Windows polish later (lower risk, well-documented path)

### Research Flags

Phases likely needing deeper research during planning:
- **Phase 5 (Windows):** Complex platform-specific behaviors, SSH agent forwarding on WSL, Cygwin path translation
- **Phase 4 (Run Commands):** TTY detection nuances, signal forwarding behavior

Phases with standard patterns (skip research-phase):
- **Phase 1 (Foundation):** Well-documented babashka.cli patterns, clear examples
- **Phase 2 (Docker Integration):** Simple shell wrapping, no complex API needed
- **Phase 3 (Build):** Straightforward file operations and process execution

## Confidence Assessment

| Area | Confidence | Notes |
|------|------------|-------|
| Stack | HIGH | Verified via Babashka book, v1.12.214 release notes, official repo docs |
| Features | HIGH | babashka.cli well-documented, reference projects (neil, bbin) demonstrate patterns |
| Architecture | HIGH | Standard Babashka project structure, aligns with official examples |
| Pitfalls | MEDIUM-HIGH | Core pitfalls well-documented; Windows edge cases less battle-tested |

**Overall confidence:** HIGH

### Gaps to Address

- **Windows testing:** Less community battle-testing than Linux/macOS. Plan explicit Windows testing in Phase 5.
- **Spinner/progress UI:** Not researched in depth. Defer to Phase 5 or post-MVP.
- **run.conf backward compatibility:** Decision needed: support old format, require migration, or auto-convert?
- **Uberscript vs bb installation:** Distribution strategy not finalized. Research during Phase 6 planning.

## Sources

### Primary (HIGH confidence)
- [Babashka Book](https://book.babashka.org/) - Official documentation, project structure, patterns
- [babashka/babashka GitHub](https://github.com/babashka/babashka) - v1.12.214 release notes, built-in libraries
- [babashka/process](https://github.com/babashka/process) - Process execution API, Windows tips
- [babashka/fs](https://github.com/babashka/fs/blob/master/API.md) - File system utilities, cross-platform handling
- [babashka/cli](https://github.com/babashka/cli) - CLI argument parsing, subcommand dispatch

### Secondary (MEDIUM confidence)
- [Bash and Babashka Equivalents Wiki](https://github.com/babashka/babashka/wiki/Bash-and-Babashka-equivalents) - Migration patterns
- [neil](https://github.com/babashka/neil), [bbin](https://github.com/babashka/bbin) - Reference CLI implementations
- [Blog: Converting from Bash to Babashka](https://blog.agical.se/en/posts/changing-my-mind--converting-a-script-from-bash-to-babashka/) - Migration experience

### Tertiary (LOW confidence)
- Docker Forum, WSL SSH forwarding guides - Platform-specific edge cases, needs validation during implementation

---
*Research completed: 2026-01-20*
*Ready for roadmap: yes*
