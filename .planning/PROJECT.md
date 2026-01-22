# Agentic Harness Sandbox

## What This Is

A Docker-based sandbox environment for running agentic AI harnesses (Claude Code, OpenCode) in isolated, ephemeral containers. Users run `aishell build` once to create their environment, then `aishell` to enter a shell or `aishell claude`/`aishell opencode` to run harnesses directly. The container mounts projects at the exact host path, preserves git identity, and supports per-project customization via `.aishell/Dockerfile` and `.aishell/config.yaml`.

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

### Active

**v2.1 Safe AI Context Protection:**
- Severity-tiered detection framework: High/medium/low severity warnings for different finding types
- Environment file detection: .env, .env.*, .envrc with severity-appropriate warnings
- Private key detection: Content-aware (BEGIN PRIVATE KEY markers) + filename patterns (id_rsa, *.p12, *.pfx)
- Cloud credential detection: GCP service accounts, application_default_credentials.json, terraform.tfstate
- Package manager credentials: .npmrc (with authToken check), .pypirc, .netrc, .docker/config.json
- Application secrets: Rails master.key, database credentials, generic secret patterns
- Context awareness: Extra warning when sensitive files NOT in .gitignore
- Configuration: Additive custom patterns, allowlist for false positives, safe YAML parsing

### Out of Scope

- Persistent containers / named sessions — ephemeral is the design choice
- Windows host support — Docker on Windows is complex; deferred indefinitely
- GUI/desktop integration — CLI-focused tool
- SSH agent forwarding — deferred to future version
- GPG signing passthrough — deferred to future version

## Current State

**Shipped:** v2.0 on 2026-01-21

**Codebase:** ~1,650 LOC Clojure (Babashka)
**Tech stack:** Babashka, Docker, Debian bookworm-slim base, Node.js 24

**v2.0 accomplishments:**
- Complete CLI rewrite from 1,655 LOC Bash to ~1,650 LOC Clojure
- Cross-platform support: Linux and macOS (x86_64, aarch64)
- YAML config format replacing shell-style run.conf
- Single-file uberscript distribution (60KB)
- curl|bash installer with checksum verification
- All 33 requirements shipped (100% coverage)
- Clean architecture with 15 namespaces

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

## Current Milestone: v2.1 Safe AI Context Protection

**Goal:** Proactively warn users about sensitive data in their project directory before AI agents access it.

**Scope (27 requirements across 7 categories):**
- **Detection Framework:** Severity tiers (high/medium/low), advisory-only warnings
- **Environment Files:** .env, .env.*, .envrc detection with template file awareness
- **Private Keys:** Content-aware (BEGIN PRIVATE KEY) + filename patterns (id_rsa, *.p12, *.pfx, *.jks)
- **Cloud Credentials:** GCP service accounts, ADC, terraform.tfstate, kubeconfig
- **Package Managers:** .npmrc (authToken check), .pypirc, .netrc, .docker/config.json
- **Application Secrets:** Rails master.key, database credentials, generic secret.*/vault.* patterns
- **Context & Config:** Git-ignore awareness, additive custom patterns, allowlist for false positives

**Research basis:** Deep dive on "Safe AI Sandboxing" (2026-01-21) identified context boundary protection as highest-impact opportunity.

---
*Last updated: 2026-01-22 after v2.1 milestone start*
