# Project Milestones: Agentic Harness Sandbox

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
