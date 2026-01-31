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

### Active

**v2.6.0 — tmux Integration & Named Containers:**
- [ ] tmux in base Docker image
- [ ] Harness commands auto-start inside named tmux session (`main`)
- [ ] Container naming: default = harness name, override with `--name`
- [ ] Docker container name = `aishell-{project-hash}-{name}` for cross-project isolation
- [ ] `aishell attach <name>` to connect to container's tmux session via docker exec
- [ ] `aishell attach <name> --session <session>` for specific tmux sessions
- [ ] `aishell ps` to list running containers for current project
- [ ] Conflict detection when starting container with name already in use
- [ ] Shell mode (`aishell`) — tmux available but no auto-start

### Out of Scope

- Persistent containers — ephemeral is the design choice (named containers added in v2.6.0 but still ephemeral)
- Windows host support — Docker on Windows is complex; deferred indefinitely
- GUI/desktop integration — CLI-focused tool
- SSH agent forwarding — deferred to future version
- GPG signing passthrough — deferred to future version
- Binary install for Claude/Codex — native binaries larger than npm packages (investigated v2.5.0)
- Conditional Node.js — abandoned with binary install (Node.js still needed for npm harnesses)

## Current State

**Shipped:** v2.5.0 on 2026-01-26
**Next:** v2.6.0 — tmux Integration & Named Containers

**Codebase:** ~2,818 LOC Clojure (Babashka)
**Tech stack:** Babashka, Docker, Debian bookworm-slim base, Node.js 24, Gitleaks v8.30.0
**Documentation:** 4,000+ lines across docs/ and README

**v2.5.0 accomplishments:**
- Dynamic help output showing only installed harness commands
- Conditional Gitleaks installation with `--without-gitleaks` flag
- Pre-start YAML list format support (backwards compatible)
- One-off command execution via `aishell exec` with TTY auto-detection
- 14 of 14 active v2.5.0 requirements satisfied (10 abandoned — binary install approach)

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
*Last updated: 2026-01-31 after v2.6.0 milestone start*
