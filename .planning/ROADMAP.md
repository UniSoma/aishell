# Roadmap: Agentic Harness Sandbox v2.5.0

**Milestone:** v2.5.0 Optimization & Polish
**Phases:** 28-29 (2 phases)
**Requirements:** 14

## Phase 28: Dynamic Help & Config Improvements

**Goal:** Users can customize build behavior and see context-aware help output.

**Dependencies:** None (builds on v2.4.0 stable base)

**Plans:** 2 plans

Plans:
- [x] 28-01-PLAN.md — Pre-start list format + Gitleaks build flag infrastructure
- [x] 28-02-PLAN.md — Dynamic help output + Documentation updates

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

## Phase 29: Exec Command

**Goal:** Users can run one-off commands in the container without entering interactive shell.

**Dependencies:** Phase 28 (stable base)

**Plans:** 2 plans

Plans:
- [ ] 29-01-PLAN.md — Core exec infrastructure (TTY detection, run-exec, CLI dispatch)
- [ ] 29-02-PLAN.md — Documentation (README, TROUBLESHOOTING)

**Requirements:**
- CLI-04: New `aishell exec <command>` subcommand runs command in container
- CLI-05: Exec command uses all standard mounts/env from config
- CLI-06: Exec command auto-detects TTY (allocate when stdin is terminal)
- CLI-07: Exec command requires prior build (clear error if image missing)
- DOC-05: README.md updated for `aishell exec` command usage
- DOC-07: TROUBLESHOOTING.md updated for common exec issues

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
| 28 | Dynamic Help & Config | 8 | ✓ Complete |
| 29 | Exec Command | 6 | Pending |

---
*Roadmap created: 2026-01-25*
