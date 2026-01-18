# Project Milestones: Agentic Harness Sandbox

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
