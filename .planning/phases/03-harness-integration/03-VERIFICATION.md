---
phase: 03-harness-integration
verified: 2026-01-17T20:45:00Z
status: passed
score: 6/6 must-haves verified
---

# Phase 3: Harness Integration Verification Report

**Phase Goal:** Users can run Claude Code and OpenCode harnesses with their configurations mounted
**Verified:** 2026-01-17T20:45:00Z
**Status:** passed
**Re-verification:** No - initial verification

## Goal Achievement

### Observable Truths

| # | Truth | Status | Evidence |
|---|-------|--------|----------|
| 1 | Running `aishell claude` launches Claude Code inside container | VERIFIED | aishell:360-361 dispatches `claude` command via `exec docker run ... claude "${HARNESS_ARGS[@]}"` |
| 2 | Running `aishell opencode` launches OpenCode inside container | VERIFIED | aishell:363-364 dispatches `opencode` command via `exec docker run ... opencode "${HARNESS_ARGS[@]}"` |
| 3 | Running `aishell` (no args) enters shell in container | VERIFIED | aishell:369-370 dispatches to `/bin/bash` when no HARNESS_CMD |
| 4 | Claude Code can read existing config from mounted ~/.claude | VERIFIED | aishell:100-101 mounts `$HOME/.claude` and `$HOME/.claude.json` when they exist |
| 5 | OpenCode can read existing config from mounted ~/.config/opencode | VERIFIED | aishell:104 mounts `$HOME/.config/opencode` when it exists |
| 6 | API keys (ANTHROPIC_API_KEY etc.) passed to container | VERIFIED | aishell:114-131 passes 13 API key vars when set, plus DISABLE_AUTOUPDATER=1 |

**Score:** 6/6 truths verified

### Required Artifacts

| Artifact | Expected | Status | Details |
|----------|----------|--------|---------|
| `Dockerfile` | Conditional harness installation with build args | VERIFIED | 63 lines, contains ARG WITH_CLAUDE=false, ARG WITH_OPENCODE=false, conditional RUN blocks with curl installers |
| `aishell` | Subcommand parsing, config mounting, env passthrough | VERIFIED | 375 lines, contains build_config_mounts(), build_api_env(), case dispatch for claude/opencode/update |
| `entrypoint.sh` | Harness command dispatch, PATH setup | VERIFIED | 63 lines, PATH includes /usr/local/bin, exec gosu passes command to user |

### Key Link Verification

| From | To | Via | Status | Details |
|------|-----|-----|--------|---------|
| Dockerfile ARG WITH_CLAUDE | curl claude.ai/install.sh | conditional RUN | WIRED | Line 39: `if [ "$WITH_CLAUDE" = "true" ]` triggers installation |
| Dockerfile ARG WITH_OPENCODE | curl opencode.ai/install | conditional RUN | WIRED | Line 49: `if [ "$WITH_OPENCODE" = "true" ]` triggers installation |
| aishell subcommand parsing | docker run CMD | exec docker run | WIRED | Lines 360-370: case dispatch builds docker_args and execs with harness command |
| aishell config mounting | container filesystem | -v mount flags | WIRED | Lines 342-346: build_config_mounts() output added to docker_args |
| aishell build flags | Dockerfile build args | --build-arg | WIRED | Lines 248, 273: WITH_CLAUDE/WITH_OPENCODE passed to docker build |
| entrypoint.sh | user execution | exec gosu | WIRED | Line 63: `exec gosu "$USER_ID:$GROUP_ID" "$@"` runs command as user |

### Requirements Coverage

| Requirement | Status | Evidence |
|-------------|--------|----------|
| HARNESS-01: `aishell claude` runs Claude Code | SATISFIED | aishell dispatch case + Dockerfile installation |
| HARNESS-02: `aishell opencode` runs OpenCode | SATISFIED | aishell dispatch case + Dockerfile installation |
| HARNESS-03: `aishell` enters shell | SATISFIED | aishell default case dispatches to /bin/bash |
| HARNESS-04: Claude config mounted | SATISFIED | build_config_mounts() checks for ~/.claude, ~/.claude.json |
| HARNESS-05: OpenCode config mounted | SATISFIED | build_config_mounts() checks for ~/.config/opencode |
| HARNESS-06: Claude Code installed | SATISFIED | Dockerfile WITH_CLAUDE=true installs via native installer |
| HARNESS-07: OpenCode installed | SATISFIED | Dockerfile WITH_OPENCODE=true installs via native installer |

### Anti-Patterns Found

| File | Line | Pattern | Severity | Impact |
|------|------|---------|----------|--------|
| (none) | - | - | - | No anti-patterns found |

**Anti-pattern scan:** Searched for TODO, FIXME, placeholder, "not implemented" - none found in aishell, Dockerfile, entrypoint.sh.

### Human Verification Completed

Per 03-03-SUMMARY.md, human verification was completed with all HARNESS requirements passing:

| Requirement | Test | Result |
|-------------|------|--------|
| HARNESS-01 | `./aishell claude` launches Claude Code | Pass |
| HARNESS-02 | `./aishell opencode` launches OpenCode | Pass |
| HARNESS-03 | `./aishell` enters interactive shell | Pass |
| HARNESS-04 | ~/.claude mounted in container | Pass |
| HARNESS-05 | ~/.config/opencode mounted in container | Pass |
| HARNESS-06 | Claude Code installed (v2.1.12) | Pass |
| HARNESS-07 | OpenCode installed (v1.1.25) | Pass |

Issues found and fixed during human verification:
1. Harness binary access: Symlinks to /root/ were inaccessible after privilege drop - fixed by copying binaries to /usr/local/bin/
2. Config mount flags: Output format caused parsing issues - fixed by outputting complete flags on single lines
3. Home directory mismatch: Container HOME was /home/developer but mounts used host path - fixed by passing LOCAL_HOME

### Verification Summary

**All must-haves verified.**

The phase goal "Users can run Claude Code and OpenCode harnesses with their configurations mounted" has been achieved:

1. **Dockerfile** contains conditional harness installation via WITH_CLAUDE and WITH_OPENCODE build args. Binaries are copied to /usr/local/bin/ for PATH accessibility after privilege drop.

2. **aishell** script:
   - Parses subcommands (claude, opencode, update) and build flags (--with-claude, --with-opencode)
   - Builds config mounts conditionally based on existence of ~/.claude, ~/.config/opencode
   - Passes API keys (13 common providers) when set in host environment
   - Always sets DISABLE_AUTOUPDATER=1 for ephemeral container use
   - Dispatches to appropriate docker run command with all args

3. **entrypoint.sh** sets up PATH to include /usr/local/bin and executes commands as the user via gosu.

4. **Human verification** confirmed all 7 HARNESS requirements work end-to-end.

---

*Verified: 2026-01-17T20:45:00Z*
*Verifier: Claude (gsd-verifier)*
