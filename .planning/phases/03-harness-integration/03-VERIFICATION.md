---
phase: 03-harness-integration
verified: 2026-01-17T21:15:00Z
status: human_needed
score: 6/6 must-haves verified (code)
re_verification:
  previous_status: passed
  previous_score: 6/6
  gaps_closed:
    - "OpenCode XDG directory creation (entrypoint.sh lines 39-41)"
  gaps_remaining: []
  regressions: []
human_verification:
  - test: "OpenCode starts without permission errors"
    expected: "Run `./aishell opencode` and see OpenCode interface (no EACCES error)"
    why_human: "Requires running container and verifying runtime behavior"
  - test: "Claude Code warning about ~/.local/bin gone"
    expected: "Run `./aishell claude` and see no warning about installMethod native and ~/.local/bin"
    why_human: "Requires running container and verifying console output"
---

# Phase 3: Harness Integration Verification Report

**Phase Goal:** Users can run Claude Code and OpenCode harnesses with their configurations mounted
**Verified:** 2026-01-17T21:15:00Z
**Status:** human_needed
**Re-verification:** Yes - after gap closure (03-04 XDG directory fix)

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

**Score:** 6/6 truths verified (code-level)

### Required Artifacts

| Artifact | Expected | Status | Details |
|----------|----------|--------|---------|
| `Dockerfile` | Conditional harness installation with build args | VERIFIED | 63 lines, contains ARG WITH_CLAUDE=false, ARG WITH_OPENCODE=false, conditional RUN blocks with curl installers |
| `aishell` | Subcommand parsing, config mounting, env passthrough | VERIFIED | 376 lines, contains build_config_mounts(), build_api_env(), case dispatch for claude/opencode/update |
| `entrypoint.sh` | Harness command dispatch, PATH setup, XDG directories | VERIFIED | 67 lines, creates ~/.local/{state,share,bin}, PATH includes /usr/local/bin, exec gosu passes command to user |

### Key Link Verification

| From | To | Via | Status | Details |
|------|-----|-----|--------|---------|
| Dockerfile ARG WITH_CLAUDE | curl claude.ai/install.sh | conditional RUN | WIRED | Line 39: `if [ "$WITH_CLAUDE" = "true" ]` triggers installation |
| Dockerfile ARG WITH_OPENCODE | curl opencode.ai/install | conditional RUN | WIRED | Line 49: `if [ "$WITH_OPENCODE" = "true" ]` triggers installation |
| aishell subcommand parsing | docker run CMD | exec docker run | WIRED | Lines 360-370: case dispatch builds docker_args and execs with harness command |
| aishell config mounting | container filesystem | -v mount flags | WIRED | Lines 342-346: build_config_mounts() output added to docker_args |
| aishell build flags | Dockerfile build args | --build-arg | WIRED | Lines 248, 273: WITH_CLAUDE/WITH_OPENCODE passed to docker build |
| entrypoint.sh | user execution | exec gosu | WIRED | Line 67: `exec gosu "$USER_ID:$GROUP_ID" "$@"` runs command as user |
| entrypoint.sh | XDG directories | mkdir/chown | WIRED | Lines 39-41: creates ~/.local/{state,share,bin} with correct ownership |

### Gap Closure: XDG Directory Fix (03-04)

**Original Issue (from 03-UAT.md):**
- Test 4 (OpenCode API Connectivity) failed with: `EACCES: permission denied, mkdir '/home/jonasrodrigues/.local/state'`
- Root cause: entrypoint.sh created `$HOME` but not XDG standard subdirectories
- OpenCode expects to write to `~/.local/state` (XDG_STATE_HOME default)

**Fix Applied (commit d0849ae):**

```bash
# entrypoint.sh lines 39-41
# Create XDG standard directories (apps expect these to be writable)
mkdir -p "$HOME/.local/state" "$HOME/.local/share" "$HOME/.local/bin"
chown -R "$USER_ID:$GROUP_ID" "$HOME/.local"
```

**Fix Verification:**

| Check | Status | Evidence |
|-------|--------|----------|
| Code exists | VERIFIED | entrypoint.sh lines 39-41 |
| Creates ~/.local/state | VERIFIED | `mkdir -p "$HOME/.local/state"` |
| Creates ~/.local/share | VERIFIED | `mkdir -p "$HOME/.local/share"` |
| Creates ~/.local/bin | VERIFIED | `mkdir -p "$HOME/.local/bin"` |
| Sets correct ownership | VERIFIED | `chown -R "$USER_ID:$GROUP_ID" "$HOME/.local"` |
| Runs before gosu switch | VERIFIED | Lines 39-41 precede line 67 (exec gosu) |
| Committed | VERIFIED | Commit d0849ae |

**Conclusion:** The code fix is correctly implemented. Runtime verification required to confirm OpenCode starts without permission errors.

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

### Human Verification Required

The XDG directory fix (03-04) has been code-verified but requires runtime verification.

#### 1. OpenCode Starts Without Permission Errors

**Test:** Run `./aishell --with-opencode && ./aishell opencode`
**Expected:** OpenCode starts and shows interface (no EACCES permission error)
**Why human:** Requires running the container and observing runtime behavior; the fix creates directories at container startup which cannot be verified without actually running the container

#### 2. Claude Code Warning Resolved

**Test:** Run `./aishell --with-claude && ./aishell claude`
**Expected:** Claude Code starts without warning about installMethod native and ~/.local/bin not existing
**Why human:** Requires observing console output at runtime

### Verification Summary

**Code-level verification: PASSED**

All phase 3 artifacts are:
- Present (existence verified)
- Substantive (adequate length, no stubs)
- Wired (properly connected)

The XDG directory fix (03-04) is correctly implemented:
- Creates ~/.local/state (fixes OpenCode EACCES)
- Creates ~/.local/share (common app data)
- Creates ~/.local/bin (fixes Claude Code warning)
- Sets correct ownership before privilege drop

**Runtime verification: PENDING**

Human must verify that:
1. OpenCode starts without EACCES permission errors
2. Claude Code warning about ~/.local/bin is resolved

---

*Verified: 2026-01-17T21:15:00Z*
*Verifier: Claude (gsd-verifier)*
*Re-verification after: 03-04 gap closure*
