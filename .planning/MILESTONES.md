# Project Milestones: Agentic Harness Sandbox

## v2.5.0 Optimization & Polish (Shipped: 2026-01-26)

**Delivered:** Dynamic help output, conditional Gitleaks installation, pre-start list format, and `aishell exec` command for one-off container execution with TTY auto-detection.

**Phases completed:** 28-29 (4 plans total)

**Key accomplishments:**

- Dynamic help output showing only installed harness commands based on build state
- Conditional Gitleaks installation with `--without-gitleaks` build flag (~15MB savings)
- Pre-start YAML list format support (joined with `&&`, backwards compatible)
- One-off command execution via `aishell exec` with automatic TTY detection and piping support
- Comprehensive documentation updates across README, CONFIGURATION, and TROUBLESHOOTING
- Scope discipline: binary install approach investigated and abandoned (larger than npm)

**Stats:**

- 11 source files modified (27 total including planning)
- ~2,818 lines of Clojure (total codebase)
- 2 phases, 4 plans, ~10 tasks
- 2 days (2026-01-25 → 2026-01-26)

**Git range:** `feat(28-01)` → `docs(29)` (25 commits)

**What's next:** Future harnesses (Aider, Cursor), session persistence, security pattern audit, or shell completions

---

## v2.4.0 Multi-Harness Support (Shipped: 2026-01-25)

**Delivered:** OpenAI Codex CLI and Google Gemini CLI support with build flags, version pinning, config mounting, API key passthrough, and comprehensive documentation suite covering architecture, configuration, harnesses, troubleshooting, and development.

**Phases completed:** 24-27 (8 plans total)

**Key accomplishments:**

- Added OpenAI Codex CLI support with build flags (--with-codex), version pinning, and runtime integration
- Added Google Gemini CLI support with Vertex AI authentication and GCP credentials file mounting
- Implemented CLI dispatch for all four harnesses (Claude Code, OpenCode, Codex CLI, Gemini CLI)
- Created comprehensive documentation suite (5 docs, 3,660+ lines) covering architecture, configuration, harnesses, troubleshooting, and development
- Full harness comparison guide with authentication patterns for each provider
- Development guide with 7-step checklist for adding new harnesses

**Stats:**

- 53 files created/modified
- ~2,674 lines of Clojure (total codebase)
- 4 phases, 8 plans, ~17 tasks
- 1 day (2026-01-25)

**Git range:** `docs(24)` → `docs(27)` (40+ commits)

**What's next:** Future harnesses (Aider, Cursor), session persistence, or shell completions

---

## v2.3.0 Safe AI Context Protection (Shipped: 2026-01-24)

**Delivered:** Proactive sensitive file detection with severity tiers, Gitleaks integration for deep content scanning, and configurable pattern allowlists to protect users' secrets before AI agents access them.

**Phases completed:** 18.1-23 (11 plans total)

**Key accomplishments:**

- Core detection framework with high/medium/low severity tiers and advisory-only warnings
- Filename-based detection for 20+ sensitive file patterns (.env, SSH keys, cloud creds, package manager files)
- Gitleaks integration with `aishell gitleaks` command for deep content-based secret scanning
- Scan freshness tracking with 7-day staleness warnings before container launch
- Gitignore awareness highlighting high-severity files that may be accidentally committed
- Configurable custom patterns and allowlist for false positive suppression

**Stats:**

- 58 files created/modified
- ~2,565 lines of Clojure (total codebase)
- 6 phases, 11 plans, ~30 tasks
- 6 days from start to ship (2026-01-18 → 2026-01-24)

**Git range:** `feat(18.1-01)` → `docs(v2.1)` (79 commits)

**What's next:** v2.4 with audit command for CI/CD integration, strict blocking mode, or advanced secret patterns

---

## v2.0 Babashka Rewrite (Shipped: 2026-01-21)

**Delivered:** Complete CLI rewrite from Bash to Clojure Babashka with feature parity to v1.2, cross-platform support (Linux, macOS), and single-file uberscript distribution.

**Phases completed:** 13-18 (22 plans total)

**Key accomplishments:**

- Complete CLI rewrite from 1,655 LOC Bash to ~1,650 LOC Clojure Babashka
- Cross-platform support for Linux and macOS (x86_64, aarch64)
- YAML config format (.aishell/config.yaml) replacing shell-style run.conf
- Single-file uberscript distribution (60KB) with curl|bash installer
- EDN state persistence at ~/.aishell/state.edn
- Pass-through args for harness commands (e.g., `aishell claude --help`)

**Stats:**

- 15 source files created
- ~1,650 lines of Clojure
- 6 phases, 22 plans, ~60 tasks
- 2 days from start to ship (2026-01-20 → 2026-01-21)

**Git range:** `feat(13-01)` → `feat(18-03)` (70 commits)

**What's next:** TBD - project complete or v2.1 with Windows support, SSH agent forwarding, GPG signing

---

## v1.2 Hardening & Edge Cases (Shipped: 2026-01-19)

**Delivered:** Input validation hardening, consolidated cleanup infrastructure, security warnings, and documentation for edge cases and known limitations.

**Phases completed:** 11-12 (4 plans total)

**Key accomplishments:**

- Consolidated trap/cleanup infrastructure with single EXIT handler and tracking arrays
- Input validation with semver regex + shell metachar blocklist (defense in depth)
- Port mapping IP binding support (127.0.0.1:8080:80 format)
- Zombie process handling via `--init` flag for proper PID 1 reaping
- Security warnings for dangerous DOCKER_ARGS (--privileged, docker.sock)
- Dockerfile hash detection with runtime mismatch warnings
- Documented run.conf limitations and safe.directory behavior

**Stats:**

- 1 file modified (aishell)
- 1,655 lines of Bash (+194 from v1.1)
- 2 phases, 4 plans, ~8 tasks
- Same day (2026-01-19)

**Git range:** `feat(11-01)` → `test(11-03)` (27 commits)

**What's next:** v2.0 (SSH agent forwarding, GPG signing, macOS support)

---

## v1.1 Per-project Runtime Configuration (Shipped: 2026-01-19)

**Delivered:** Per-project runtime configuration via `.aishell/run.conf` for custom mounts, environment variables, ports, docker args, and pre-start commands.

**Phases completed:** 9-10 (4 plans total)

**Key accomplishments:**

- Whitelist-based config file parser with security-focused validation
- MOUNTS variable with $HOME expansion and source:destination format support
- ENV passthrough (VAR) and literal (VAR=value) syntax
- PORTS variable for container-to-host port exposure
- DOCKER_ARGS for arbitrary docker run flags
- PRE_START for background sidecar services before shell/harness

**Stats:**

- 1 file modified (aishell)
- 1,461 lines of Bash (+145 from v1.0)
- 2 phases, 4 plans, ~12 tasks
- 1 day from start to ship (2026-01-18 → 2026-01-19)

**Git range:** `feat(09-01)` → `test(10)` (33 commits)

**What's next:** v1.2 (SSH agent forwarding, GPG signing, macOS support) or v2.0 with advanced features

---

## v1.0 MVP (Shipped: 2026-01-18)

**Delivered:** Docker-based ephemeral sandbox for running Claude Code and OpenCode in isolated containers with correct permissions, git integration, and per-project customization.

**Phases completed:** 1-8 (16 plans total)

**Key accomplishments:**

- Docker-based ephemeral sandbox with correct UID/GID ownership and project mounted at same path as host
- Git identity propagation (user.name/email from host) and safe.directory configuration
- Claude Code and OpenCode harnesses installed, configured, and runnable with config mounting
- Per-project customization via .aishell/Dockerfile extension mechanism
- Self-contained distribution (curl|bash installer) with heredoc-embedded build files
- Explicit build/update workflow with state persistence and version pinning support

**Stats:**

- 70 files created/modified
- 1,316 lines of Bash (aishell + install.sh)
- 8 phases, 16 plans, ~80 tasks
- 2 days from start to ship (2026-01-17 → 2026-01-18)

**Git range:** `feat(01-01)` → `feat(08-03)` (99 commits)

**What's next:** v1.1 enhancements (SSH agent forwarding, macOS support, credential helpers) or v2.0 with advanced features

---
