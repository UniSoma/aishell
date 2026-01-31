# Project Research Summary

**Project:** aishell (Docker sandbox for AI coding harnesses)
**Milestone:** v2.6.0 — tmux Integration & Named Containers
**Domain:** Docker-based development environment with session management
**Researched:** 2026-01-31
**Confidence:** HIGH

## Executive Summary

The tmux and named containers integration represents a controlled architectural shift from fully ephemeral (--rm, anonymous) containers to persistent named containers with session management. Research confirms this is a straightforward addition to the existing Docker-based sandbox: tmux from Debian bookworm-slim (3.3a-3), project-hash-based container naming for isolation, and docker exec-based attach workflows. The core value proposition is observability - developers can "peek in" on running AI agents without disrupting them.

The recommended approach preserves existing ephemeral behavior for shell/exec modes while introducing detached mode for harness commands. Harnesses auto-start inside tmux session "main", containers are named `aishell-{project-hash}-{name}` for multi-instance support, and users attach via `docker exec` (not `docker attach`) to avoid PID 1 complications. The architecture builds cleanly on existing Babashka CLI infrastructure with no fundamental changes to the entrypoint, gosu user-switching, or pre-start hooks.

Critical risks center on three areas: (1) signal handling when tmux interacts with PID 1, requiring trap handlers to ensure graceful shutdown, (2) socket permissions when mixing root entrypoint with gosu user switching, requiring tmux to start AFTER gosu, and (3) --rm flag conflicts with named containers, requiring pre-flight conflict detection. All three are well-documented issues with known mitigations.

## Key Findings

### Recommended Stack

The stack additions are minimal and low-risk. tmux 3.3a-3 from Debian bookworm provides all required features (named sessions, attach/detach, multiple windows) with no backports or custom builds needed. Container naming uses SHA-256 hash (8 chars) of project directory for deterministic, collision-resistant names. Docker labels (`aishell.project-hash`, `aishell.project-dir`, `aishell.container-name`) enable efficient filtering for the `ps` command. The attach workflow uses `docker exec -it` rather than `docker attach` because tmux is not PID 1 - the process tree is tini → entrypoint → gosu → tmux.

**Core technologies:**
- **tmux 3.3a-3** (Debian bookworm): Terminal multiplexer, adds ~1MB to image, native package with no dependency issues
- **docker exec + tmux attach**: Spawns new tmux client process, avoids PID 1 complications, enables multiple concurrent attachments
- **SHA-256 project hashing**: 8-char deterministic names, 0.02% collision chance at 100 projects, enables cross-project isolation
- **Docker labels**: Metadata storage for `ps` filtering, indexed by Docker daemon for fast queries

**What NOT to add:**
- tmux plugins (tpm, resurrect, continuum) - conflicts with ephemeral container model
- Custom tmux.conf in image - users should mount their own
- User-provided container names - hash-based ensures consistency, prevents collisions

### Expected Features

Research identified a clear split between table-stakes features (basic tmux-in-Docker workflows), differentiators (aishell-specific UX improvements), and anti-features (complexity to avoid). The foundation layer (tmux in base image, named containers with project isolation, conflict detection) is non-negotiable - missing any of these makes the feature feel incomplete. The integration layer (auto-start harness in tmux, attach command, ps command) provides the value proposition. Multiple containers per project is enabled by the naming scheme but requires documentation for discovery.

**Must have (table stakes):**
- tmux installed in base image - core requirement for any tmux workflow
- Named tmux sessions - default session names (0, 1, 2) unusable for multi-agent workflows
- Named Docker containers - anonymous containers unusable with multiple agents
- Project-based naming with hash prefix - prevents cross-project collisions
- Container conflict detection - prevents "name already in use" errors
- Attach to named session - must reconnect to existing sessions by name
- Container listing by project - users need to see their running containers

**Should have (competitive):**
- Auto-start harness in tmux - observability without extra steps (medium complexity, high value)
- `aishell attach <name>` CLI - simpler than `docker exec` + `tmux attach` manual workflow
- `aishell ps` for current project - scoped listing more useful than global `docker ps`
- Default session name = "main" - convention over configuration
- Graceful handling of shell mode - doesn't force tmux on users who just want bash

**Defer (v2+):**
- Per-harness session customization (claude/opencode instead of "main") - nice polish, not blocking
- `aishell attach <name> --session <session>` - specific session targeting, advanced feature
- Session discovery on attach error - show `tmux ls` output if session not found
- Visual indicators in ps output (which containers have active sessions) - high complexity, low ROI
- Auto-cleanup command (`aishell clean`) - nice-to-have for stopped container cleanup

**Anti-features (DO NOT BUILD):**
- Persistent tmux sessions across container restarts - violates ephemeral design principle
- Automatic tmux session recording/logging - privacy concern
- Built-in tmux configuration management - scope creep
- Tmux plugin installation in base image - version drift, slow builds
- Session sharing between containers - complex, unclear use case
- Background harness execution - defeats observability purpose

### Architecture Approach

The integration involves 6 modified components and 2 new namespaces, totaling ~270 lines of code. The architectural pattern is "mode detection" - a single `run-container` function handles both ephemeral (shell/exec) and detached (harness) modes based on command detection. Shell mode bypasses tmux entirely (preserves current UX), harness mode wraps the command in `tmux new-session -s main -d`. The entrypoint remains unchanged - tmux is invoked via command arguments, not as ENTRYPOINT or CMD, preserving existing user creation and pre-start hook logic.

Container lifecycle changes from `docker run --rm` (auto-remove on exit) to `docker run --name X --rm` (named but still ephemeral). Conflict detection checks `docker ps` before launch to prevent "name already in use" errors. The attach command uses `docker exec -it` (not `docker attach`) because tmux is not PID 1 - it spawns a new tmux client process that can detach without killing the container. The ps command filters containers via `docker ps --filter label=aishell.project-hash=<hash>`.

**Major components:**
1. **templates.clj** — Add tmux to Dockerfile RUN command (trivial change, +1 line)
2. **docker/run.clj** — Add `compute-container-name` (hash from project path) and `build-docker-args-detached` (--name, -d flags)
3. **run.clj** — Add mode detection logic (ephemeral vs detached), conflict detection, tmux command wrapping (~80 lines)
4. **cli.clj** — Add `attach` and `ps` command dispatch (+20 lines)
5. **docker.clj** — Add `container-running?`, `container-exists?`, `list-aishell-containers` utility functions (+40 lines)
6. **attach.clj (new)** — Attach command handler, computes container name and runs `docker exec -it ... tmux attach` (~40 lines)
7. **ps.clj (new)** — List command handler, queries Docker and formats output (~30 lines)

**Key architectural patterns:**
- **Deterministic naming**: Same project → same container name (hash-based, no user input)
- **Mode detection**: Single run-container function handles both ephemeral and detached via conditional
- **tmux as wrapper, not entrypoint**: Command wrapped in tmux, preserving entrypoint logic
- **Conflict detection at launch**: Pre-flight check before `docker run`, helpful error messages

### Critical Pitfalls

Research identified 13 documented pitfalls, 5 critical (cause rewrites), 4 moderate (cause delays), 4 minor (annoyance). The most dangerous involve signal handling (containers won't stop gracefully), socket permissions (attach fails with "permission denied"), and TTY allocation (intermittent failures in CI/SSH). All have known mitigations from the Docker + tmux ecosystem.

1. **tmux Signal Handling with PID 1** — When tmux runs as PID 1, it ignores SIGTERM unless explicitly handled. Result: `docker stop` waits 10s then SIGKILLs. **Prevention:** Use trap handlers in entrypoint BEFORE exec-ing tmux, or rely on existing --init flag (tini as PID 1). Test: `docker stop` should complete in <3s with exit code 0/143, not 137.

2. **tmux Socket Permissions After User Switching** — If tmux starts as root (before gosu), socket owned by root, attach fails with "permission denied". If started as dev user but socket directory doesn't exist, same failure. **Prevention:** Start tmux AFTER gosu, verify socket ownership in entrypoint, use consistent UID/GID in attach command.

3. **TTY Allocation for docker exec + tmux attach** — `docker exec -it` TTY allocation can fail with "not a tty" when wrapped in `sh -c` or in non-interactive environments. **Prevention:** Always use `-it` flag, don't wrap in `sh -c` for simple commands, set TERM=screen-256color, validate TTY before attach with `tty -s`.

4. **Named Container + --rm Flag Conflict** — `--rm` removes container on clean exit, but if Docker daemon interrupted or container killed, stopped container persists with name taken. Next run fails with "name already in use". **Prevention:** Pre-flight check for name collision, offer to remove stopped container, or drop --rm for named containers and add cleanup command.

5. **tmux Session Persistence Expectations** — Users expect tmux sessions to persist across container restarts (like host tmux), but Docker containers are ephemeral - sessions die with container. Causes confusion about "attach" semantics. **Prevention:** Document ephemeral session model clearly, clarify named containers != persistent sessions, fail gracefully on attach to stopped container.

**Additional moderate risks:**
- TERM variable mismatch (broken colors, pane errors) - set TERM=screen-256color in tmux.conf and docker exec
- tmux socket location varies with TMPDIR/TMUX_TMPDIR - explicitly set TMUX_TMPDIR=/tmp in entrypoint
- Zombie processes with tmux + --init - existing --init flag handles most cases, avoid --enable-utempter when building tmux
- Container name length limits (63-char DNS limit) - truncate project hash to 8 chars, validate total name length

## Implications for Roadmap

Based on combined research, the implementation should follow a 5-phase structure that builds foundation utilities first, then adds core functionality, then polish. This order minimizes risk by validating Docker query functions and hash generation before modifying container launch logic. Each phase has clear dependencies and acceptance criteria.

### Phase 1: Container Utilities & Naming
**Rationale:** Foundation for all other phases - other components depend on hash generation and Docker queries. Low risk, easy to test in isolation.

**Delivers:**
- `docker.clj`: Add `container-running?`, `container-exists?`, `list-aishell-containers` functions
- `docker/run.clj`: Add `compute-container-name` function (SHA-256 hash, 8 chars)
- Unit tests for hash generation (deterministic, collision-resistant)
- Integration tests for Docker ps queries

**Addresses:** Foundation requirement from FEATURES.md (project-based naming with hash prefix)

**Avoids:** Pitfall #9 (container name length limits) via truncation to 8 chars

**Research flag:** Standard Docker API patterns, no phase research needed

---

### Phase 2: Dockerfile & Image Build
**Rationale:** Needed before any tmux-specific testing. Trivial change (single RUN line), but blocks phases 3-5.

**Delivers:**
- `templates.clj`: Add tmux to package list in Dockerfile
- Build verification: `docker run aishell:base tmux -V`
- Image size check (expect +1-2MB)

**Addresses:** Core requirement from STACK.md (tmux 3.3a-3 from Debian bookworm)

**Avoids:** Pitfall #8 (zombie processes) by using native Debian package (no --enable-utempter flag)

**Research flag:** Standard package installation, no phase research needed

---

### Phase 3: Detached Mode & Conflict Detection
**Rationale:** Core functionality - enables named containers and tmux-wrapped execution. Medium complexity, requires careful mode detection logic.

**Delivers:**
- `docker/run.clj`: Add `build-docker-args-detached` (--name, -d, -it flags, Docker labels)
- `run.clj`: Add mode detection (ephemeral vs detached), conflict detection, tmux command wrapping
- `run.clj`: Pre-flight container name collision check
- Integration tests: Launch detached container, verify running, manual `docker exec` attach

**Addresses:**
- Must-have from FEATURES.md: Named containers, conflict detection, auto-start harness in tmux
- Differentiator: Auto-start provides observability without extra steps

**Avoids:**
- Pitfall #1 (signal handling) via existing --init flag (tini as PID 1)
- Pitfall #2 (socket permissions) by starting tmux AFTER gosu in entrypoint
- Pitfall #4 (--rm conflict) via pre-flight collision check

**Research flag:** Needs phase research for entrypoint signal handling if existing --init doesn't work

**Testing criteria:**
- `aishell claude` starts container in background
- `docker ps` shows named container (aishell-{hash}-claude)
- Attempting to start duplicate name shows conflict error
- Container stops gracefully in <3s with `docker stop`

---

### Phase 4: Attach Command
**Rationale:** User-facing convenience - abstracts `docker exec` complexity. Low risk, depends on phase 3.

**Delivers:**
- `attach.clj`: New namespace, attach command handler
- `cli.clj`: Add `attach` command dispatch
- Error handling: Container not found, container stopped, tmux session not found
- Integration tests: Start container, attach, detach (Ctrl+B D), reattach

**Addresses:**
- Must-have from FEATURES.md: Attach to named session
- Differentiator: `aishell attach <name>` simpler than `docker exec` manual workflow

**Avoids:**
- Pitfall #3 (TTY allocation) via `-it` flag, TERM variable, tty validation
- Pitfall #5 (persistence expectations) via clear error messages for stopped containers

**Research flag:** Standard Docker exec patterns, no phase research needed

**Testing criteria:**
- `aishell attach claude` enters tmux session
- Detach with Ctrl+B D leaves container running
- Attach to stopped container shows helpful error
- Attach in non-TTY environment fails gracefully

---

### Phase 5: PS Command & Polish
**Rationale:** Nice-to-have for discovery. Low risk, standalone feature.

**Delivers:**
- `ps.clj`: New namespace, list command handler
- `cli.clj`: Add `ps` command dispatch
- Table-formatted output: NAME, STATUS, CREATED
- Filter by project hash (uses labels from phase 3)

**Addresses:**
- Must-have from FEATURES.md: Container listing by project
- Differentiator: Scoped listing more useful than global `docker ps`

**Avoids:** No specific pitfalls

**Research flag:** Standard Docker ps filtering, no phase research needed

**Testing criteria:**
- `aishell ps` with no containers shows "No containers running"
- `aishell ps` with multiple containers shows formatted table
- Only shows containers for current project (hash-based filtering)

---

### Phase Ordering Rationale

1. **Foundation first (Phase 1)**: Utilities used by all other phases, easy to test in isolation, de-risks hash generation and Docker queries.

2. **Image before runtime (Phase 2)**: Can't test tmux functionality without tmux installed, trivial change minimizes phase 2 scope.

3. **Core before convenience (Phase 3 → 4 → 5)**: Detached mode is the architectural change, attach/ps are UX layers on top. Enables testing with manual `docker exec` if attach command fails.

4. **Dependencies respected**: Phase 4 depends on Phase 3 (no attach without containers), Phase 5 depends on Phase 3 (labels for filtering).

5. **Risk mitigation**: Each phase has clear acceptance criteria and rollback plan. Mode detection in Phase 3 is isolated to `run.clj`, doesn't affect existing shell/exec workflows.

### Research Flags

**Phases needing phase research during planning:**
- **Phase 3 (Detached Mode)**: If entrypoint signal handling proves complex, may need research on tmux + --init + gosu interaction patterns. Monitor exit codes during testing - if containers consistently exit with 137 (SIGKILL), research trap handler patterns.

**Phases with standard patterns (skip phase research):**
- **Phase 1**: Docker API queries and SHA-256 hashing are well-documented.
- **Phase 2**: Debian package installation is straightforward.
- **Phase 4**: Docker exec patterns are standard.
- **Phase 5**: Docker ps filtering by label is documented.

**Validation during implementation:**
- Phase 3 acceptance: `docker stop` completes in <3s with exit code 0/143 (not 137)
- Phase 3 acceptance: Socket permissions allow attach (test with `ls -la /tmp/tmux-*`)
- Phase 4 acceptance: Attach works in SSH session, fails gracefully in CI (non-TTY)

## Confidence Assessment

| Area | Confidence | Notes |
|------|------------|-------|
| Stack | HIGH | tmux 3.3a-3 from Debian bookworm is stable, well-documented; docker exec patterns verified across sources |
| Features | MEDIUM | AI-specific workflows are emerging (2026 content), but core tmux+Docker patterns are established |
| Architecture | HIGH | Existing aishell codebase well-structured for this change; mode detection pattern is common in multi-mode CLIs |
| Pitfalls | HIGH | All 5 critical pitfalls have documented mitigations; signal handling and socket permissions are well-covered |

**Overall confidence:** HIGH

The research converged on consistent recommendations across all four areas. Stack choice (native Debian tmux, docker exec attach pattern) is validated by 10+ sources. Architecture builds cleanly on existing Babashka infrastructure with no fundamental changes to entrypoint or lifecycle. Pitfalls are well-documented with known mitigations that have been tested in similar contexts (tmux in daemon containers, Docker + gosu user switching).

### Gaps to Address

1. **Signal handling validation**: While the existing --init flag (tini as PID 1) should handle signals correctly, this needs testing. The entrypoint currently uses `exec gosu` which replaces the shell process - need to verify signals propagate through tini → gosu → tmux. If `docker stop` takes 10s and exits with code 137, add trap handlers before exec.

2. **--rm flag decision**: Research shows --rm + --name can conflict when cleanup fails. Three options: (a) keep --rm, add pre-flight collision check and helpful error, (b) drop --rm for named containers, add `aishell clean` command, (c) use --rm but auto-remove conflicts. Recommend (a) for MVP - lowest complexity, preserves auto-cleanup in success case.

3. **Per-harness session naming**: Research suggests using harness name as session name (claude/opencode/codex/gemini) instead of generic "main". Deferred to v2+ as polish, but worth validating if users expect this. If multiple harnesses run in same container (unlikely with current design), session name collisions would occur.

4. **Container name to project path mapping**: `aishell ps` can show container names but not which project directories they belong to (hash is one-way). Future enhancement: store project-dir as Docker label, query with `--format {{.Label "aishell.project-dir"}}`. Not blocking for MVP - users can navigate to project and run `aishell attach`.

## Sources

### Primary (HIGH confidence)
- [Debian Packages - tmux in bookworm](https://packages.debian.org/bookworm/tmux) — Version verification, dependency checking
- [Docker Exec vs Attach](https://www.baeldung.com/ops/docker-exec-attach-difference) — Authoritative guide on PID 1 vs exec processes
- [Docker Object Labels Documentation](https://docs.docker.com/engine/manage-resources/labels/) — Metadata storage for ps filtering
- [tmux Manual Page](https://man7.org/linux/man-pages/man1/tmux.1.html) — Command reference, session management
- [Docker --rm Best Practices](https://thelinuxcode.com/docker-run-rm-an-expert-teachers-guide-to-effectively-using-and-best-practices/) — Lifecycle and cleanup patterns

### Secondary (MEDIUM confidence)
- [Building Software Faster with LLMs: Part 2 - Ergonomics and Observability](https://blog.laurentcharignon.com/post/2025-09-30-llm-workflow-part2-ergonomics/) — AI coding agent workflows in 2026
- [Forking subagents in an AI coding session with tmux - Kaushik Gopal](https://kau.sh/blog/agent-forking/) — Multi-agent tmux patterns
- [Docker Container Naming Best Practices](https://devtodevops.com/blog/docker-container-naming-convention/) — Naming conventions, DNS limits
- [PID 1 Signal Handling in Docker](https://petermalmgren.com/signal-handling-docker/) — Signal propagation with --init
- [tmux in demonized docker container](https://gist.github.com/ptrj/b54b8cdd34f632028e669a1e71bc03b8) — Community patterns for tmux as daemon

### Tertiary (LOW confidence, needs validation)
- [Docker Signal Demo with tmux](https://github.com/DrPsychick/docker-signal-demo) — Example trap handler implementations
- [User Switching Tool in Containers: gosu](https://docsaid.org/en/blog/gosu-usage/) — gosu + socket permissions interaction
- [tmux socket permission issues](https://github.com/tmux/tmux/issues/2110) — GitHub issues discussing socket ownership

---
*Research completed: 2026-01-31*
*Ready for roadmap: YES*
*Estimated implementation: ~270 LOC across 7 files, 5 phases, LOW-MEDIUM complexity*
