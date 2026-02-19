# Agentic Harness Sandbox

## What This Is

A cross-platform Docker-based sandbox environment for running agentic AI harnesses (Claude Code, OpenCode, OpenAI Codex CLI, Google Gemini CLI, Pi) in isolated, ephemeral containers. Works on Linux, macOS, and Windows (cmd.exe/PowerShell with Docker Desktop). Users run `aishell build` once to create their environment, then `aishell` to enter a shell or `aishell claude`/`aishell opencode` to run harnesses directly. The container mounts projects at the exact host path, preserves git identity, and supports per-project customization via `.aishell/Dockerfile` and `.aishell/config.yaml`.

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

**v2.10.0 (2026-02-05) — Gitleaks Opt-in:**
- ✓ Gitleaks opt-in: `aishell build --with-gitleaks` enables Gitleaks (default = off) — v2.10.0
- ✓ Help visibility: `aishell gitleaks` hidden from `--help` when not installed — v2.10.0
- ✓ Pipeline gating: Gitleaks staleness warning skipped when not installed — v2.10.0
- ✓ Filename-based detection continues independently of Gitleaks — v2.10.0
- ✓ Documentation: All user-facing CLI changes reflected in docs/ — v2.10.0

**v2.9.0 (2026-02-03) — tmux Opt-in & Plugin Support:**
- ✓ tmux opt-in: `aishell build --with-tmux` makes tmux opt-in (default = no tmux) — v2.9.0
- ✓ tmux config mounting: User's `~/.tmux.conf` auto-mounted when tmux is active — v2.9.0
- ✓ tmux plugins via config.yaml: `tmux.plugins` list, plugins installed into harness volume — v2.9.0
- ✓ tmux-resurrect persistence: Session state persisted to host via volume mount — v2.9.0
- ✓ Documentation: All CLI changes reflected in docs/ — v2.9.0

**v2.8.0 (2026-02-01) — Decouple Harness Tools:**
- ✓ Foundation image split: Stable base without harness packages, tagged `aishell:foundation` — v2.8.0
- ✓ Volume-mounted harness tools: npm packages in Docker named volumes with content-hash naming — v2.8.0
- ✓ Lazy volume population: Auto-populated on first run if empty/stale — v2.8.0
- ✓ Per-project harness volumes: `aishell-harness-{hash}` shared across identical configs — v2.8.0
- ✓ Transparent build UX: `aishell build` handles foundation + volume transparently — v2.8.0
- ✓ Clean migration: `aishell:foundation` replaces `aishell:base` with clear error for legacy references — v2.8.0
- ✓ Cache invalidation: Extension tracking uses foundation image ID — v2.8.0
- ✓ Update command redesign: `aishell update` refreshes harness volume, `--force` rebuilds foundation — v2.8.0
- ✓ Volume management: `aishell volumes` list/prune with orphan detection and safety checks — v2.8.0

**v3.1.0 (2026-02-12) — Native Windows Support:**
- ✓ Platform detection — `babashka.fs/windows?` guards all Unix-specific code paths — v3.1.0
- ✓ Cross-platform paths — USERPROFILE/LOCALAPPDATA on Windows, HOME/XDG on Unix — v3.1.0
- ✓ Docker mount normalization — drive letter support, forward-slash conversion via fs/unixify — v3.1.0
- ✓ Windows UID/GID defaults — 1000/1000 without `id -u`/`id -g` — v3.1.0
- ✓ Process execution — p/process :inherit on Windows, p/exec on Unix — v3.1.0
- ✓ ANSI color detection — NO_COLOR/FORCE_COLOR standards, Windows Terminal/ConEmu support — v3.1.0
- ✓ .bat wrapper — neil-pattern launcher in GitHub Releases for cmd.exe/PowerShell — v3.1.0
- ✓ Documentation — Windows instructions across README, ARCHITECTURE, CONFIGURATION, TROUBLESHOOTING, DEVELOPMENT — v3.1.0

**v3.0.0 (2026-02-06) — Docker-native Attach:**
- ✓ tmux removed from foundation image — harnesses and shells run bare as container's main process — v3.0.0
- ✓ Attach simplified to `docker exec -it` — `aishell attach <name>` opens bash in running container — v3.0.0
- ✓ `--detach` flag removed — containers always run foreground-attached — v3.0.0
- ✓ `--with-tmux` build flag and all tmux-related state/config removed — v3.0.0
- ✓ tmux binary removed from foundation image — v3.0.0
- ✓ Entrypoint simplified — conditional tmux fork removed, direct `exec gosu` — v3.0.0
- ✓ All user-facing documentation updated for v3.0.0 changes — v3.0.0

**v3.5.0 (2026-02-18) — Pi Coding Agent Support:**
- ✓ Pi coding agent harness integration (`--with-pi`, `aishell pi`, version pinning) — v3.5.0
- ✓ fd pre-installed in foundation image (fd-find with fd symlink) — v3.5.0
- ✓ Pi-specific environment variable passthrough (PI_CODING_AGENT_DIR, PI_SKIP_VERSION_CHECK) — v3.5.0
- ✓ Pi config directory (~/.pi/) mounted from host — v3.5.0
- ✓ Documentation updates across all 6 user-facing docs — v3.5.0

**v3.8.0 (2026-02-19) — OpenSpec Support & Global Base Image Customization:**
- ✓ OpenSpec opt-in via `--with-openspec` build flag with version pinning — v3.8.0
- ✓ OpenSpec npm package installed in harness volume with state tracking — v3.8.0
- ✓ Three-tier image chain: `aishell:foundation` -> `aishell:base` -> `aishell:ext-{hash}` — v3.8.0
- ✓ Global Dockerfile customization via `~/.aishell/Dockerfile` — v3.8.0
- ✓ Label-based base image staleness detection with cascade rebuild triggers — v3.8.0
- ✓ `aishell info` command for image stack summary — v3.8.0
- ✓ Documentation updates across all 6 user-facing docs — v3.8.0

### Active

(None — planning next milestone)

### Out of Scope

- Persistent containers — ephemeral is the design choice (named containers in v2.7.0 are still ephemeral with --rm)
- tmux inside containers — window management belongs on the host, not in the container (removed in v3.0.0)
- Detached/background mode — always-interactive simplifies the model; use host tools for backgrounding (removed in v3.0.0)
- Native Windows containers — Linux containers via Docker Desktop are supported; actual Windows container images deferred indefinitely
- Windows installer/Scoop package — users install babashka and docker manually; distribution deferred
- GUI/desktop integration — CLI-focused tool
- SSH agent forwarding — deferred to future version
- GPG signing passthrough — deferred to future version
- Binary install for Claude/Codex — native binaries larger than npm packages (investigated v2.5.0)
- Conditional Node.js — abandoned with binary install (Node.js still needed for npm harnesses)

## Current State

**Shipped:** v3.8.0 OpenSpec Support & Global Base Image Customization (2026-02-19)
**Current:** Planning next milestone

**Codebase:** ~5,063 LOC Clojure (Babashka)
**Tech stack:** Babashka, Docker, Debian bookworm-slim base, Node.js 24, Gitleaks v8.30.0 (opt-in)
**Platforms:** Linux, macOS, Windows (cmd.exe/PowerShell with Docker Desktop WSL2)
**Harnesses:** Claude Code, OpenCode, Codex CLI, Gemini CLI, Pi
**Additional tools:** OpenSpec (opt-in)
**Image architecture:** Three-tier (foundation -> base -> extension)
**Documentation:** 5,000+ lines across docs/ and README

## Milestone Conventions

- **DOCS-01 requirement**: Every milestone MUST include a cross-cutting requirement `DOCS-01: All user-facing CLI changes reflected in docs/` covering README.md, docs/ARCHITECTURE.md, docs/CONFIGURATION.md, docs/HARNESSES.md, docs/TROUBLESHOOTING.md, and docs/DEVELOPMENT.md. This ensures documentation is auditable and never forgotten. (Established after v2.7.0 where docs were missed until milestone completion.)

## Constraints

- **Docker dependency**: Requires Docker installed on host
- **Babashka dependency**: Requires Babashka installed (not bundled)
- **Windows**: Requires Docker Desktop with WSL2 backend for Linux containers

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

**v2.9.0 (tmux Opt-in & Plugin Support):**

| Decision | Rationale | Outcome |
|----------|-----------|---------|
| --with-tmux opt-in (default = no tmux) | Users who don't need tmux shouldn't pay for it | Good |
| Plugin format: owner/repo with warning-only validation | Consistent with existing validation framework | Good |
| TPM + plugins in harness volume (not foundation) | Version drift, rebuild speed; matches tool separation architecture | Good |
| Volume hash includes tmux state | Proper invalidation when tmux config changes | Good |
| Session name "harness" (was "main") | Project naming consistency across all namespaces | Good |
| WITH_TMUX as env var (not mounted state.edn) | Simpler pattern, consistent with existing env var approach | Good |
| Runtime config at ~/.tmux.conf.runtime | Discoverable location, not hidden in /tmp | Good |
| resurrect: true → sensible defaults | Enabled with process restoration off; safe starting point | Good |
| Per-project resurrect state directory | Isolation between projects; follows per-project pattern | Good |
| Schema-based migration detection | Reliable without version comparison; state shape tells the story | Good |
| One-time migration warning with marker file | Inform once, don't nag; marker prevents repeats | Good |
| Auto-inject resurrect plugin (no manual declaration) | UX improvement; deduplicate if user also declares it | Good |

**v2.10.0 (Gitleaks Opt-in):**

| Decision | Rationale | Outcome |
|----------|-----------|---------|
| Positive --with-* flag pattern (no --without-*) | Consistency across all build options | Good |
| Opt-in default (nil -> false) | Users who don't need Gitleaks shouldn't have it installed | Good |
| Gate staleness warnings on build state | Prevents confusing warnings for users without Gitleaks | Good |
| Filename detection independent of Gitleaks | Lightweight Babashka-side checks valuable on their own | Good |

**v3.0.0 (Docker-native Attach):**

| Decision | Rationale | Outcome |
|----------|-----------|---------|
| Remove tmux entirely (not just opt-in) | Window management belongs on host; tmux added complexity, code, and image size | Good |
| Positional argument for attach (`aishell attach <name>`) | Simpler UX than `--name` flag; breaking change justified by major version | Good |
| Remove --detach flag entirely | Always-foreground model simplifies execution paths; host tools handle backgrounding | Good |
| Rename skip-tmux to skip-interactive | Parameter now controls all interactive features (harness aliases), not just tmux | Good |
| Single entrypoint execution path | No conditional branching; direct exec gosu for every container mode | Good |
| State schema bump to v3.0.0 | Major version reflects removal of :with-tmux, :tmux-plugins, :resurrect-config keys | Good |
| Pure deletion refactor approach | 7 phases each focused on removing one layer; no new code, only deletions | Good |

**v3.1.0 (Native Windows Support):**

| Decision | Rationale | Outcome |
|----------|-----------|---------|
| babashka.fs/windows? for all platform detection | Built-in predicate, consistent single pattern across codebase | Good |
| USERPROFILE/LOCALAPPDATA on Windows, HOME/XDG on Unix | Standard OS conventions for each platform | Good |
| UID/GID 1000 defaults on Windows | Standard first non-root user in Debian/Ubuntu containers | Good |
| p/process :inherit on Windows, p/exec on Unix | Windows p/exec is no-op; :inherit provides full I/O inheritance | Good |
| System/exit for exit code propagation on Windows | p/process returns control to Babashka, need explicit exit | Good |
| NO_COLOR > FORCE_COLOR > auto-detection priority | Community standards compliance (no-color.org) | Good |
| neil-pattern .bat wrapper (4-line minimal) | Proven pattern, CRLF endings, minimal maintenance | Good |
| Side-by-side platform examples in docs | Reduces doc sprawl, keeps related info together | Good |
| Source-only mounts map to /home/developer on Windows | No direct path equivalent; consistent container-side location | Good |

**v3.5.0 (Pi Coding Agent Support):**

| Decision | Rationale | Outcome |
|----------|-----------|---------|
| Pi follows exact Codex/Gemini pattern | Consistency across harness integrations; proven pattern | Good |
| fd-find with fd symlink in foundation | Debian packages as fdfind but pi expects fd; symlink bridges the gap | Good |
| PI_CODING_AGENT_DIR/PI_SKIP_VERSION_CHECK as passthrough env vars | Only forwarded when set on host; matches existing env var pattern | Good |
| No special Pi auth troubleshooting | Pi has straightforward config-based auth; no complex OAuth flows | Good |

**v2.8.0 (Decouple Harness Tools):**

| Decision | Rationale | Outcome |
|----------|-----------|---------|
| 2-tier architecture: foundation image + harness volume | Simpler than 3-image approach, volume mounts are fast | Good |
| Volume-based injection over COPY --link layer inversion | Simpler for local dev workflow, no Docker buildx dependency | Good |
| Clean break from aishell:base to aishell:foundation | Backward compat alias adds maintenance burden | Good |
| Content-hash volume naming (aishell-harness-{hash}) | Identical configs share volumes across projects | Good |
| Lazy volume population on first run | Avoids unnecessary work during build; auto-recovers | Good |
| Gitleaks in foundation (not harness volume) | Security infrastructure belongs in stable layer | Good |
| Read-only volume mount at /tools | Prevents accidental modification of shared tools | Good |
| Update command: volume refresh by default, --force for foundation | Most common use case is harness update, not system update | Good |
| OpenCode binary via curl/tar (not npm) | Go binary, not npm package; established /tools/bin pattern | Good |
| Profile.d script for login shell environment | Fixes tmux new-window PATH loss; POSIX compatible | Good |
| Unconditional delete + recreate for volume update | Simpler than staleness check; guarantees clean slate | Good |

**v3.8.0 (OpenSpec Support & Global Base Image Customization):**

| Decision | Rationale | Outcome |
|----------|-----------|---------|
| OpenSpec is NOT a harness | No dispatch entry, no config mounts, no API key passthrough; just an npm tool | Good |
| Non-harness tools use harness-keys/harness-npm-packages pattern | Consistent registration without dispatch table entry | Good |
| Three-tier image chain (foundation -> base -> extension) | Global customization layer without breaking existing extension mechanism | Good |
| Tag-alias when no global Dockerfile | Zero overhead when feature unused; `docker tag` is instant | Good |
| Label-based staleness for base images | Same proven pattern as extension images; consistent codebase | Good |
| Hard-stop on base build failure | User explicitly created Dockerfile, so failures should be visible | Good |
| Base tag passed as parameter to extension | Avoids circular dependency between base.clj and extension.clj | Good |
| Extension rebuild tracks base image ID (not foundation) | Three-tier chain: base change cascades to extensions automatically | Good |

---
*Last updated: 2026-02-19 after v3.8.0 milestone*
