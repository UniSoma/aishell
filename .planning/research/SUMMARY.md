# Project Research Summary

**Project:** aishell v2.4.0 Multi-Harness Support
**Domain:** Adding OpenAI Codex CLI and Google Gemini CLI to Docker sandbox
**Researched:** 2026-01-24
**Confidence:** HIGH

## Executive Summary

Adding OpenAI Codex CLI and Google Gemini CLI to aishell is a **low-risk, mechanical integration** that follows established patterns. Both harnesses:
- Install via npm (like Claude Code)
- Use standard config directories (~/.codex/, ~/.gemini/)
- Support API key authentication (preferred for containers)
- Work with the existing Node.js 24 base image

**Estimated changes:** ~85 lines across 7 files, following exact patterns from Claude/OpenCode integration.

## Key Findings

### Stack

| Harness | Package | Node.js | Config Dir | API Key Env Var |
|---------|---------|---------|------------|-----------------|
| OpenAI Codex CLI | @openai/codex | 22+ (24 works) | ~/.codex/ | CODEX_API_KEY |
| Google Gemini CLI | @google/gemini-cli | 20+ (24 works) | ~/.gemini/ | GEMINI_API_KEY |

**No new system dependencies required.** Both use npm global install, matching Claude Code pattern exactly.

### Installation Commands

```dockerfile
# OpenAI Codex CLI
RUN npm install -g @openai/codex[@VERSION]

# Google Gemini CLI
RUN npm install -g @google/gemini-cli[@VERSION]
```

### Feature Table Stakes (MVP)

1. **Installation via npm** with version pinning (--with-codex=VERSION, --with-gemini=VERSION)
2. **Config directory mounting** (~/.codex/, ~/.gemini/ from host)
3. **Direct invocation**: `aishell codex [args]`, `aishell gemini [args]`
4. **API key passthrough** via environment variables
5. **Pass-through args** to harness (existing pattern)

### Watch Out For

| Pitfall | Risk | Prevention |
|---------|------|------------|
| OAuth browser flow in containers | HIGH | Recommend API key auth, not OAuth |
| Credential loss in ephemeral containers | HIGH | Mount config directories |
| TTY allocation missing | MEDIUM | Always use -it for harness commands |
| Network disabled by default (Codex) | MEDIUM | Document config override |
| Config paths differ from Claude/OpenCode | LOW | Update mount logic |

### Architecture Integration

All changes follow existing patterns. No new abstractions needed:

1. **templates.clj** - Add build args + npm install blocks
2. **build.clj** - Add to build-docker-args, version checks
3. **cli.clj** - Add build flags, dispatch cases
4. **run.clj** - Add harness verification, container commands
5. **docker/run.clj** - Add config mount paths
6. **config.clj** - Add to known-harnesses set

### Anti-Features (Avoid)

- OAuth flow in container (authenticate on host instead)
- Session persistence (conflicts with ephemeral model)
- Built-in sandboxing (container provides isolation)
- Cloud task management (network complexity)
- IDE integration (CLI-focused tool)

## Implications for Roadmap

### Recommended Phase Structure

1. **Phase 24: Dockerfile & Build Infrastructure**
   - Add WITH_CODEX, WITH_GEMINI build args
   - Add npm install blocks for both harnesses
   - Extend build-docker-args with new flags
   - Update state schema and version tracking

2. **Phase 25: CLI & Run Commands**
   - Add --with-codex, --with-gemini to build command
   - Add "codex" and "gemini" dispatch cases
   - Add harness verification checks
   - Add config directory mounts
   - Update help text

3. **Phase 26: Documentation & Config**
   - Document authentication methods (API key preferred)
   - Add environment variable examples
   - Update README with new commands
   - Add harness_args validation for new harnesses

### Deferred Work

- OAuth authentication flow documentation (users authenticate on host)
- Session resumption (requires persistent storage strategy)
- Shell completions generation
- Web search/network policy documentation

## Confidence Assessment

| Area | Confidence | Notes |
|------|------------|-------|
| Installation method | HIGH | Both use npm like Claude Code |
| Config directories | HIGH | Verified from official docs |
| Node.js compatibility | HIGH | 24 satisfies both (22+ and 20+) |
| Integration pattern | HIGH | Follows existing aishell patterns exactly |
| API key auth | HIGH | Documented as container-preferred method |
| TTY handling | MEDIUM | Known issues, but workarounds exist |

**Overall: HIGH confidence** - Mechanical integration following validated patterns.

## Sources

**Primary (HIGH confidence):**
- [OpenAI Codex CLI npm](https://www.npmjs.com/package/@openai/codex)
- [Codex CLI Documentation](https://developers.openai.com/codex/cli/)
- [Codex Config Reference](https://developers.openai.com/codex/config-reference/)
- [Google Gemini CLI npm](https://www.npmjs.com/package/@google/gemini-cli)
- [Gemini CLI Documentation](https://geminicli.com/docs/)
- [Gemini CLI GitHub](https://github.com/google-gemini/gemini-cli)

**Secondary (MEDIUM confidence):**
- GitHub issues for TTY/container edge cases
- Community Docker implementations
- XDG Base Directory Specification

---
*Research completed: 2026-01-24*
*Ready for requirements: yes*
