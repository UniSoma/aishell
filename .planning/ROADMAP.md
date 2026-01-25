# Roadmap: Agentic Harness Sandbox v2.5.0

**Milestone:** v2.5.0 Optimization & Polish
**Phases:** 28-30 (3 phases)
**Requirements:** 24

## Phase 28: Dynamic Help & Config Improvements

**Goal:** Users can customize build behavior and see context-aware help output.

**Dependencies:** None (builds on v2.4.0 stable base)

**Requirements:**
- CLI-01: Help output shows only installed harness commands (reads build state)
- CLI-02: Build command accepts `--without-gitleaks` flag to skip Gitleaks installation
- CLI-03: Gitleaks installation state tracked in ~/.aishell/state.edn
- CFG-01: Pre-start config accepts YAML list format
- CFG-02: List items joined with ` && ` to form single command
- CFG-03: String format remains supported (backwards compatible)
- DOC-01: CONFIGURATION.md updated for pre_start list format with examples
- DOC-02: CONFIGURATION.md updated for --without-gitleaks build flag

**Success Criteria:**
1. User runs `aishell --help` and sees only harnesses they have installed (e.g., no `gemini` if WITH_GEMINI was false)
2. User runs `aishell build --without-gitleaks` and resulting image does not contain Gitleaks binary
3. User defines pre_start as a YAML list in config.yaml; commands execute joined by ` && `
4. User defines pre_start as a string; behavior unchanged from v2.4.0
5. User can check ~/.aishell/state.edn to see which optional components (Gitleaks, Codex, Gemini) are installed

---

## Phase 29: Binary Install & Conditional Node.js

**Goal:** Reduce image size and build time by using native installers and including Node.js only when needed.

**Dependencies:** Phase 28 (state tracking infrastructure)

**Requirements:**
- DKR-01: Claude Code installed via native installer (not npm)
- DKR-02: Codex CLI installed via binary download (not npm)
- DKR-03: Node.js only included when WITH_GEMINI=true
- DKR-04: Multi-stage build updated for conditional Node.js copy
- DKR-05: Architecture detection for Codex binary (amd64/arm64)
- DKR-06: Claude auto-updater disabled in container (DISABLE_AUTOUPDATER=1)
- DKR-07: Version pinning works for native installers
- DOC-03: HARNESSES.md updated for native installer methods (Claude, Codex)
- DOC-04: ARCHITECTURE.md updated for conditional Node.js build flow
- DOC-06: DEVELOPMENT.md updated for binary install pattern in harness checklist

**Success Criteria:**
1. User builds image without Gemini; resulting image has no Node.js runtime (`node --version` fails)
2. User builds image with Gemini; Node.js is available for Gemini CLI
3. User runs `aishell claude` and Claude Code works without npm being present in PATH
4. User builds on arm64 machine; Codex binary is correct architecture
5. User specifies `--claude-version=X.Y.Z`; native installer respects the version

---

## Phase 30: Exec Command

**Goal:** Users can run one-off commands in the container without entering interactive shell.

**Dependencies:** Phase 29 (binary install complete for all harnesses)

**Requirements:**
- CLI-04: New `aishell exec <command>` subcommand runs command in container
- CLI-05: Exec command uses all standard mounts/env from config
- CLI-06: Exec command auto-detects TTY (allocate when stdin is terminal)
- CLI-07: Exec command requires prior build (clear error if image missing)
- DOC-05: README.md updated for `aishell exec` command usage
- DOC-07: TROUBLESHOOTING.md updated for common exec/binary install issues

**Success Criteria:**
1. User runs `aishell exec ls -la` and sees container's directory listing
2. User runs `aishell exec` from script (non-TTY); command executes without TTY allocation
3. User runs `aishell exec` before any build; receives clear error message with instructions
4. User's config.yaml mounts and environment variables apply to exec command
5. User can pipe input/output: `echo "test" | aishell exec cat` works correctly

---

## Progress

| Phase | Name | Requirements | Status |
|-------|------|--------------|--------|
| 28 | Dynamic Help & Config | 8 | Pending |
| 29 | Binary Install & Conditional Node.js | 10 | Pending |
| 30 | Exec Command | 6 | Pending |

---
*Roadmap created: 2026-01-25*
