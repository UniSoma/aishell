# Agentic Harness Sandbox

## What This Is

A Docker-based sandbox environment for running agentic AI harnesses (Claude Code, OpenCode, OpenAI Codex CLI, Google Gemini CLI) in isolated, ephemeral containers. Users run `aishell build` once to create their environment, then `aishell` to enter a shell or `aishell claude`/`aishell opencode` to run harnesses directly. The container mounts projects at the exact host path, preserves git identity, and supports per-project customization via `.aishell/Dockerfile` and `.aishell/config.yaml`.

## Core Value

Run agentic AI harnesses in isolated, reproducible environments without polluting the host system.

## Requirements

### Validated

**v1.0 (2026-01-18):**
- Shell access: User can enter a shell in the sandbox from any project root
- Claude Code: User can run Claude Code directly with `aishell claude`
- OpenCode: User can run OpenCode directly with `aishell opencode`
- Project mount: Project folder is mounted and accessible inside container
- Git identity: Git user/email from host is available inside container
- Harness configs: Harness configurations from host are available inside container
- Ephemeral: Container is ephemeral (destroyed on exit, only mounted files persist)
- Base tools: Base image includes essential tools (git, curl, vim, jq, ripgrep, Node.js, Babashka)
- Extension: Projects can extend the environment via .aishell/Dockerfile
- Installation: Tool installable via curl|bash one-liner
- Version pinning: Version pinning for harnesses (--claude-version, --opencode-version)
- Build workflow: Explicit build/update workflow with state persistence

**v1.1 (2026-01-19):**
- Volume mounts: Projects can define additional volume mounts via config
- Environment: Projects can define additional environment variables (passthrough or literal)
- Port mapping: Projects can expose port mappings from container to host
- Docker args: Projects can specify extra docker run arguments
- Pre-start: Projects can define a pre-start command for sidecar/background services

**v1.2 (2026-01-19):**
- IP binding: Port mapping supports IP binding (e.g., 127.0.0.1:8080:80)
- Version validation: Version strings validated before use in npm/curl commands
- Cleanup: Temp file cleanup consolidated to single handler
- Signal handling: Signal handling during build phase (clean Ctrl+C)
- HOME validation: HOME directory validated before use
- Zombie handling: `--init` flag added for zombie process handling
- Security warnings: Dangerous DOCKER_ARGS patterns warned/documented
- Update awareness: Update check warns when embedded Dockerfile changed since build
- Config docs: run.conf parsing limits documented (no escaped quotes)

**v2.0 (2026-01-21) - Babashka Rewrite:**
- CLI rewrite: Full CLI rewritten in Clojure Babashka with feature parity to v1.2
- Cross-platform: Works on Linux and macOS (x86_64, aarch64)
- YAML config: Projects use `.aishell/config.yaml` instead of run.conf
- Uberscript: Single-file distribution as Babashka uberscript
- State persistence: Build state in EDN format at ~/.aishell/state.edn
- Pass-through args: Harness commands pass arguments directly (e.g., `aishell claude --help`)

**v2.3.0 (2026-01-24) - Safe AI Context Protection:**
- ✓ Severity-tiered detection framework: High/medium/low severity warnings for different finding types
- ✓ Filename-based detection (pre-container): .env files, SSH keys, key containers, cloud creds, package manager files, app secrets
- ✓ Gitleaks integration: `aishell gitleaks` command for deep content-based secret scanning inside container
- ✓ Scan freshness warning: Alerts on claude/opencode/shell if gitleaks hasn't been run recently (7 days)
- ✓ Context awareness: Extra warning when high-severity files NOT in .gitignore ("risk: may be committed")
- ✓ Configuration: Additive custom filename patterns, allowlist for false positives

**v2.4.0 (2026-01-25) - Multi-Harness Support:**
- ✓ OpenAI Codex CLI: User can run OpenAI Codex CLI with `aishell codex`
- ✓ Google Gemini CLI: User can run Google Gemini CLI with `aishell gemini`
- ✓ Harness-specific config mounting: Each harness's config directory mounted appropriately
- ✓ Version pinning: Support --with-codex=VERSION and --with-gemini=VERSION flags at build time
- ✓ Comprehensive documentation: Architecture, configuration, harnesses, troubleshooting, development guides

**v2.5.0 (2026-01-26) - Optimization & Polish:**
- ✓ Dynamic help: Show only installed harness commands in help output — v2.5.0
- ✓ Gitleaks opt-out: --without-gitleaks build flag (~15MB savings) — v2.5.0
- ✓ Pre-start list format: YAML list for pre_start joined with && — v2.5.0
- ✓ One-off execution: `aishell exec` command with TTY auto-detection — v2.5.0
- ✓ Gitleaks state tracking: :with-gitleaks in state.edn — v2.5.0
- ✓ Documentation: README, CONFIGURATION, TROUBLESHOOTING updated for v2.5.0 — v2.5.0

**v2.7.0 (2026-01-31) — tmux Integration & Named Containers:**
- ✓ tmux in base Docker image — v2.7.0
- ✓ Harness commands auto-start inside named tmux session (`main`) — v2.7.0 (all modes, including shell)
- ✓ Container naming: default = harness name, override with `--name` — v2.7.0
- ✓ Docker container name = `aishell-{project-hash}-{name}` for cross-project isolation — v2.7.0
- ✓ `aishell attach --name <name>` to connect to container's tmux session via docker exec — v2.7.0
- ✓ `aishell attach --name <name> --session <session>` for specific tmux sessions — v2.7.0
- ✓ `aishell ps` to list running containers for current project — v2.7.0
- ✓ Conflict detection when starting container with name already in use — v2.7.0
- ✓ TERM validation with xterm-256color fallback for unsupported terminals — v2.7.0
- ✓ Detached mode (`--detach`/`-d`) for background container execution — v2.7.0

### Active

**v2.8.0 — Decouple Harness Tools from Docker Extensions:**
- [ ] Foundation image split: Stable base (Debian + Node.js + system tools) without harness packages
- [ ] Volume-mounted harness tools: npm packages installed into Docker named volume, mounted at runtime
- [ ] Lazy volume population: Harness volume auto-populated on first run if empty/stale
- [ ] Per-project harness volumes: Content-hash named volumes (aishell-harness-{hash}) for different harness combos
- [ ] Transparent build UX: `aishell build` handles both foundation + volume, harness volume auto-rebuilds when stale
- [ ] Clean migration: `aishell:foundation` tag replaces `aishell:base`, clear error for old FROM references
- [ ] Cache invalidation update: Extension tracking references foundation image ID, not base image ID

### Out of Scope

- Persistent containers — ephemeral is the design choice (named containers in v2.7.0 are still ephemeral with --rm)
- Persistent tmux sessions across container restarts — violates ephemeral design principle
- tmux plugin installation in base image — version drift, slow builds
- Windows host support — Docker on Windows is complex; deferred indefinitely
- GUI/desktop integration — CLI-focused tool
- SSH agent forwarding — deferred to future version
- GPG signing passthrough — deferred to future version
- Binary install for Claude/Codex — native binaries larger than npm packages (investigated v2.5.0)
- Conditional Node.js — abandoned with binary install (Node.js still needed for npm harnesses)

## Current Milestone: v2.8.0 Decouple Harness Tools

**Goal:** Eliminate cascade invalidation by splitting the monolithic base image into a stable foundation layer and volume-mounted harness tools, so harness version updates no longer force multi-gigabyte extension rebuilds.

**Target features:**
- Foundation image without harness tools (stable, rarely rebuilt)
- Harness tools in Docker named volumes (rebuilt in ~90s, not minutes)
- Lazy volume population on first container run
- Per-project volumes keyed by harness combination hash
- Transparent UX — `aishell build` and `aishell <harness>` work the same
- Clean break from `aishell:base` to `aishell:foundation`

## Current State

**Shipped:** v2.7.0 on 2026-01-31
**Next:** v2.8.0 — Decouple Harness Tools

**Codebase:** ~3,457 LOC Clojure (Babashka)
**Tech stack:** Babashka, Docker, Debian bookworm-slim base, Node.js 24, Gitleaks v8.30.0, tmux
**Documentation:** 4,000+ lines across docs/ and README

**v2.7.0 accomplishments:**
- Deterministic container naming with 8-char SHA-256 project hashing
- tmux auto-start in all container modes with TERM validation
- Detached mode for background execution with conflict detection
- Attach command for reconnecting to running containers
- PS command for project-scoped container discovery
- 19 of 19 v2.7.0 requirements satisfied (2 overridden by user decision)

## Milestone Conventions

- **DOCS-01 requirement**: Every milestone MUST include a cross-cutting requirement `DOCS-01: All user-facing CLI changes reflected in docs/` covering README.md, docs/ARCHITECTURE.md, docs/CONFIGURATION.md, docs/HARNESSES.md, docs/TROUBLESHOOTING.md, and docs/DEVELOPMENT.md. This ensures documentation is auditable and never forgotten. (Established after v2.7.0 where docs were missed until milestone completion.)

## Constraints

- **Docker dependency**: Requires Docker installed on host
- **Babashka dependency**: Requires Babashka installed (not bundled)
- **No Windows**: Docker on Windows is complex; deferred indefinitely

## Key Decisions

**v1.0-v1.2 (Bash):**

| Decision | Rationale | Outcome |
|----------|-----------|---------|
| Ephemeral containers | Simpler model, avoid state management | Good |
| Dockerfile-based extension | Per-project customization without polluting base | Good |
| Command pattern: `aishell` shell, `aishell claude` direct | Intuitive UX | Good |
| Debian over Alpine | glibc compatibility for harness binaries | Good |
| gosu for user switching | Proper PID 1 handling, signal forwarding | Good |
| Dynamic user creation | UID/GID matching without pre-configured users | Good |
| Heredoc embedding | Self-contained distribution, no separate files | Good |
| Explicit build command | Clear separation of build vs run | Good |
| State file for flags | Persistent config without JSON complexity | Good |
| Multi-stage build | Node.js binaries without full runtime overhead | Good |
| Shell-style config (run.conf) | Native to Bash, no parser dependencies | Good |
| Whitelist-based config parsing | Prevents injection attacks, explicit allowed vars | Good |
| printf for docker flags | Avoids echo -e flag interpretation issues | Good |
| Docker --init for zombie reaping | Simpler than custom PID 1 handling, built into Docker | Good |
| Warn-only for version mismatch | Don't block users who intentionally use older images | Good |

**v2.0 (Babashka):**

| Decision | Rationale | Outcome |
|----------|-----------|---------|
| Babashka over Bash | Cross-platform, better data structures, YAML native | Good |
| YAML config over run.conf | Better structure, native clj-yaml support | Good |
| EDN state over shell format | Native Clojure data, no parsing complexity | Good |
| Uberscript distribution | Single file, no deps beyond Babashka | Good |
| Static requires for uberscript | Enables bb uberscript namespace detection | Good |
| Pre-dispatch command interception | Pass-through args bypass CLI parsing | Good |
| Advisory security warnings | Never block, just inform | Good |
| requiring-resolve for state | Avoids circular dependency in build.clj | Good |

**v2.3.0 (Safe AI Context Protection):**

| Decision | Rationale | Outcome |
|----------|-----------|---------|
| Content detection delegated to Gitleaks | Better coverage than reimplementing subset of patterns | Good |
| Filename detection pre-container | Fast checks before container starts, no container overhead | Good |
| defmulti for format-finding | Extensible pattern formatters without modifying core | Good |
| High-severity requires confirmation | Ensures user acknowledges serious risks in interactive mode | Good |
| 7-day staleness threshold | Balances nudging without annoyance | Good |
| Custom patterns extend defaults | Additive config prevents accidental reduction of coverage | Good |
| Allowlist requires path+reason | Forces documentation of why false positive was suppressed | Good |
| XDG state directory for scan timestamps | Follows spec, separates state from config | Good |

**v2.5.0 (Optimization & Polish):**

| Decision | Rationale | Outcome |
|----------|-----------|---------|
| Pre-start list normalization at YAML load time | Single point of normalization ensures consistent behavior | Good |
| --without-gitleaks (opt-out) flag naming | Maintains backwards compatibility — default includes Gitleaks | Good |
| Positive state tracking (:with-gitleaks) | State reflects what is installed, not what is excluded | Good |
| Show all harnesses when no state file | Aids discoverability for new users who haven't built yet | Good |
| Always show gitleaks in help | May be installed on host, command works via host mounting | Good |
| Abandon binary install approach | Native Claude binary (213MB) larger than npm package; no net savings | Good |
| System/console for TTY detection | Already used in existing codebase, portable, simple | Good |
| Skip detection/pre_start for exec | Fast path for one-off commands; users can run gitleaks separately | Good |
| Always include -i flag for exec | Without -i, piped input fails silently | Good |
| p/shell with :inherit for exec | Need to capture exit code; p/exec replaces process | Good |

**v2.7.0 (tmux Integration & Named Containers):**

| Decision | Rationale | Outcome |
|----------|-----------|---------|
| 8-char SHA-256 hash for container names | 2^32 space, <0.02% collision at 100 projects | Good |
| All modes auto-start inside tmux | Consistency across shell/harness/detached modes (user override) | Good |
| gosu before tmux in exec chain | User-owned socket, avoids permission errors | Good |
| infocmp for TERM validation | Defensive check without extra terminfo packages | Good |
| xterm-256color fallback | Universally available in Debian, preserves color support | Good |
| -d short form for --detach | No conflicts with any harness flags | Good |
| --rm + --detach | Modern Docker auto-cleanup on stop | Good |
| p/exec for attach terminal takeover | Full TTY control without shell wrapper | Good |
| clojure.pprint/print-table for ps | Standard library, no dependencies | Good |
| Three-layer attach validation | TTY → container → session prevents confusing Docker errors | Good |

**v2.4.0 (Multi-Harness Support):**

| Decision | Rationale | Outcome |
|----------|-----------|---------|
| Codex/Gemini follow npm global install pattern | Consistent with Claude Code install approach | Good |
| ARG naming: WITH_{HARNESS} and {HARNESS}_VERSION | Consistent with existing pattern | Good |
| Simple pass-through for Codex/Gemini | No special flags needed (unlike Claude's --dangerously-skip-permissions) | Good |
| OAuth and API key presented equally | Both methods valid; user chooses based on context | Good |
| Mermaid.js for architecture diagrams | GitHub renders natively, no external tools | Good |
| Troubleshooting organized by symptom | Users search for symptoms, not components | Good |
| 7-step harness checklist in dev guide | Explicit pattern makes contributions straightforward | Good |

---
*Last updated: 2026-01-31 after v2.8.0 milestone start*
