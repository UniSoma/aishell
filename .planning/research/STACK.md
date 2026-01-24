# Stack Research: Multi-Harness Support

**Project:** aishell - Multi-Harness Support (v2.4.0)
**Researched:** 2026-01-24
**Focus:** OpenAI Codex CLI and Google Gemini CLI integration

## Executive Summary

Both OpenAI Codex CLI and Google Gemini CLI are distributed via npm and follow similar patterns to the existing Claude Code installation. **Critical finding:** OpenAI Codex CLI requires Node.js 22+ (higher than current base image Node.js 24), while Google Gemini CLI requires Node.js 20+. The existing Node.js 24 base satisfies both requirements.

**Integration complexity:** LOW - Both harnesses follow npm global install patterns similar to Claude Code, with straightforward config directory mounting and environment variable passthrough.

## OpenAI Codex CLI

### Installation

**Package:** `@openai/codex`
**Method:** npm global install
**Current version:** 0.89.0 (released 2026-01-22)

```dockerfile
# In Dockerfile with build arg pattern (matches existing Claude Code pattern)
ARG WITH_CODEX=false
ARG CODEX_VERSION=""

RUN if [ "$WITH_CODEX" = "true" ]; then \
        if [ -n "$CODEX_VERSION" ]; then \
            npm install -g @openai/codex@"$CODEX_VERSION"; \
        else \
            npm install -g @openai/codex; \
        fi \
    fi
```

**Alternative installation methods:**
- Homebrew: `brew install --cask codex` (macOS only, not relevant for container)
- Standalone binaries available for macOS (ARM64), Windows (ARM64, x86-64), Linux (ARM64)

### Config Location

**Primary config directory:** `~/.codex/`
**Config file:** `~/.codex/config.toml`
**Auth storage:** `~/.codex/auth.json` (default) or OS keyring

**Environment variable override:**
- `CODEX_HOME` - overrides default `~/.codex/` location

**Mount pattern for aishell:**
```yaml
# In config.yaml volumes section
- ~/.codex:/home/developer/.codex
```

### Environment Variables

**Authentication (multiple methods):**

1. **ChatGPT Login** (primary method):
   - No environment variable needed
   - Browser-based OAuth flow
   - Credentials cached in `~/.codex/auth.json` or system keyring

2. **API Key Authentication:**
   - `CODEX_API_KEY` - for non-interactive use (CI/CD)
   - Can also configure via `config.toml` with `env_key` pointing to custom env var name
   - Example: `env_key = "OPENAI_API_KEY"` (then set OPENAI_API_KEY in environment)

**Configuration:**
- `CODEX_HOME` - config directory location (optional, defaults to `~/.codex`)

**Passthrough for aishell:**
```yaml
# In config.yaml environment section
environment:
  passthrough:
    - CODEX_API_KEY
    - OPENAI_API_KEY  # if using custom env_key config
```

### Dependencies

**Node.js:** Version 22 or higher (REQUIRED)
**Platform:** Linux (amd64, arm64), macOS, Windows
**System libraries:** Standard glibc (Debian bookworm-slim compatible)

**Current aishell base:** Node.js 24 ✓ (satisfies requirement)

### Runtime Notes

**Binary location:** `/usr/local/bin/codex` (npm global install)
**Command invocation:** `codex` (interactive) or `codex <args>` (with commands)
**Launch command for aishell:** `aishell codex` → execs `codex` in container

**Authentication flow:**
- First run prompts for ChatGPT login or API key
- Opens browser for OAuth (may need device code flow in headless environments)
- Credentials cached in mounted `~/.codex/` directory

**Model Context Protocol (MCP):** Supported via `config.toml` configuration

## Google Gemini CLI

### Installation

**Package:** `@google/gemini-cli`
**Method:** npm global install
**Current version:** 0.25.2 (released 2026-01-23)

```dockerfile
# In Dockerfile with build arg pattern
ARG WITH_GEMINI=false
ARG GEMINI_VERSION=""

RUN if [ "$WITH_GEMINI" = "true" ]; then \
        if [ -n "$GEMINI_VERSION" ]; then \
            npm install -g @google/gemini-cli@"$GEMINI_VERSION"; \
        else \
            npm install -g @google/gemini-cli; \
        fi \
    fi
```

**Alternative installation methods:**
- Homebrew: `brew install gemini-cli`
- Anaconda/Conda (for restricted environments)
- Google Cloud Shell (pre-installed, not relevant for aishell)
- npx: `npx @google/gemini-cli` (one-off execution)

### Config Location

**Primary config directory:** `~/.gemini/`
**User settings:** `~/.gemini/settings.json`
**Project settings:** `.gemini/settings.json` (in project root, optional)

**Hierarchy:** Project settings → User settings → System defaults

**System defaults (informational only, not user-editable):**
- Linux: `/etc/gemini-cli/system-defaults.json`
- macOS: `/Library/Application Support/GeminiCli/system-defaults.json`
- Windows: `C:\ProgramData\gemini-cli\system-defaults.json`

**Mount pattern for aishell:**
```yaml
# In config.yaml volumes section
- ~/.gemini:/home/developer/.gemini
```

**Note on .env files:**
Gemini CLI automatically loads `.env` files from current directory upward. This interacts with aishell's project mount - `.env` files in project root will be detected.

### Environment Variables

**Authentication (multiple methods):**

1. **Gemini API Key** (simplest):
   - `GEMINI_API_KEY` - authentication credential
   - Obtain from Google AI Studio

2. **Google Cloud API Key** (alternative):
   - `GOOGLE_API_KEY` - for Vertex AI access
   - Takes precedence over GEMINI_API_KEY if both set

3. **Application Default Credentials (ADC)**:
   - `GOOGLE_APPLICATION_CREDENTIALS` - path to credentials JSON file
   - `GOOGLE_CLOUD_PROJECT` - GCP project identifier
   - Note: Must unset GEMINI_API_KEY and GOOGLE_API_KEY to use ADC

4. **Gemini Code Assist License** (OAuth):
   - Login with personal Google account
   - No environment variable needed
   - Grants access to Gemini 2.5 Pro (1M token context)
   - Free tier: 60 req/min, 1000 req/day

**Configuration:**
- `GEMINI_MODEL` - default model to use (optional)
- `GOOGLE_GENAI_USE_VERTEXAI` - set to "true" for Vertex AI mode

**Passthrough for aishell:**
```yaml
# In config.yaml environment section
environment:
  passthrough:
    - GEMINI_API_KEY
    - GOOGLE_API_KEY
    - GOOGLE_APPLICATION_CREDENTIALS
    - GOOGLE_CLOUD_PROJECT
    - GOOGLE_CLOUD_LOCATION  # for Vertex AI
    - GEMINI_MODEL
```

### Dependencies

**Node.js:** Version 20 or higher (REQUIRED)
**Platform:** Linux, macOS, Windows
**System libraries:** Standard glibc (Debian bookworm-slim compatible)

**Current aishell base:** Node.js 24 ✓ (satisfies requirement)

### Runtime Notes

**Binary location:** `/usr/local/bin/gemini` (npm global install)
**Command invocation:** `gemini` (interactive) or `gemini <args>`
**Launch command for aishell:** `aishell gemini` → execs `gemini` in container

**Authentication flow:**
- Multiple methods supported (API key is simplest for container use)
- API key can be set via environment variable or settings.json
- OAuth login available for Code Assist license (browser-based)

**Model Context Protocol (MCP):** Supported with custom integrations

**Special features:**
- Built-in Google Search grounding
- File operations tools
- Shell command execution
- Web fetching capabilities

## Integration with Aishell Patterns

### Dockerfile Changes

Both harnesses integrate cleanly with the existing Dockerfile pattern:

```dockerfile
# Add to existing build args section
ARG WITH_CODEX=false
ARG WITH_GEMINI=false
ARG CODEX_VERSION=""
ARG GEMINI_VERSION=""

# Add after existing Claude Code / OpenCode installation blocks
# Install OpenAI Codex CLI if requested (npm global)
RUN if [ "$WITH_CODEX" = "true" ]; then \
        if [ -n "$CODEX_VERSION" ]; then \
            npm install -g @openai/codex@"$CODEX_VERSION"; \
        else \
            npm install -g @openai/codex; \
        fi \
    fi

# Install Google Gemini CLI if requested (npm global)
RUN if [ "$WITH_GEMINI" = "true" ]; then \
        if [ -n "$GEMINI_VERSION" ]; then \
            npm install -g @google/gemini-cli@"$GEMINI_VERSION"; \
        else \
            npm install -g @google/gemini-cli; \
        fi \
    fi
```

**No additional system packages required** - both use existing Node.js 24.

### Config Mounting

Extend the existing config mount pattern in `docker/run.clj`:

```clojure
;; Existing: Claude Code ~/.claude, OpenCode ~/.config/opencode
;; Add: Codex ~/.codex, Gemini ~/.gemini

(defn config-mounts []
  (let [home (System/getenv "HOME")]
    ["-v" (str home "/.claude:/home/developer/.claude")
     "-v" (str home "/.config/opencode:/home/developer/.config/opencode")
     "-v" (str home "/.codex:/home/developer/.codex")
     "-v" (str home "/.gemini:/home/developer/.gemini")]))
```

**Directory creation:** No pre-creation needed - harnesses create config dirs on first run.

### Build Command Flags

Add new version flags to `build` command:

```bash
aishell build --codex-version 0.89.0 --gemini-version 0.25.2
```

**State persistence:** Extend `state.edn` to track codex/gemini versions:

```clojure
{:claude-version "1.2.3"
 :opencode-version "1.1.25"
 :codex-version "0.89.0"     ;; NEW
 :gemini-version "0.25.2"    ;; NEW
 :dockerfile-hash "abc123..."}
```

### Launch Commands

Add new harness launch commands:

```bash
aishell codex      # Launches codex in container
aishell gemini     # Launches gemini in container
```

**Command routing:** Extend `core.clj` pre-dispatch logic:

```clojure
(case (first args)
  "claude" (run-harness "claude" (rest args))
  "opencode" (run-harness "opencode" (rest args))
  "codex" (run-harness "codex" (rest args))      ;; NEW
  "gemini" (run-harness "gemini" (rest args))    ;; NEW
  ;; ... fall through to CLI parsing
  )
```

### Environment Variable Passthrough

Both harnesses need API key passthrough. Current aishell pattern:

```yaml
# .aishell/config.yaml
environment:
  passthrough:
    - ANTHROPIC_API_KEY
    - OPENAI_API_KEY  # OpenCode uses this
    - CODEX_API_KEY   # NEW - for Codex
    - GEMINI_API_KEY  # NEW - for Gemini
    - GOOGLE_API_KEY  # NEW - alternative for Gemini
```

**Validation consideration:** Don't fail if API keys are missing (user may use OAuth login instead).

## Comparison Matrix

| Aspect | Claude Code | OpenCode | OpenAI Codex CLI | Google Gemini CLI |
|--------|-------------|----------|------------------|-------------------|
| **Install method** | npm global | curl binary | npm global | npm global |
| **Package name** | @anthropic-ai/claude-code | n/a (binary) | @openai/codex | @google/gemini-cli |
| **Config dir** | ~/.claude | ~/.config/opencode | ~/.codex | ~/.gemini |
| **Config format** | JSON | TOML | TOML | JSON |
| **Node.js version** | 18+ | n/a | 22+ | 20+ |
| **Auth env var** | ANTHROPIC_API_KEY | OPENAI_API_KEY | CODEX_API_KEY | GEMINI_API_KEY |
| **OAuth support** | Yes | No | Yes (ChatGPT) | Yes (Google) |
| **MCP support** | Yes | Unknown | Yes | Yes |
| **Current version** | (varies) | 1.1.25 | 0.89.0 | 0.25.2 |

## Dependencies Summary

### No New System Packages Required

Both harnesses install via npm and run on Node.js, which is already present in the base image.

**Current base image:**
- Node.js 24.x (satisfies Codex's Node 22+ and Gemini's Node 20+ requirements)
- npm (bundled with Node.js)
- Debian bookworm-slim with glibc (compatible with both harnesses)

**No changes needed to:**
- Babashka installation
- System packages (curl, git, vim, etc.)
- gosu or entrypoint.sh
- bashrc configuration

### Version Compatibility Matrix

| Component | Required By | Current | Status |
|-----------|-------------|---------|--------|
| Node.js 20+ | Gemini CLI | 24.x | ✓ Pass |
| Node.js 22+ | Codex CLI | 24.x | ✓ Pass |
| npm | Both | Bundled | ✓ Pass |
| glibc | Both | Debian bookworm | ✓ Pass |

## Implementation Checklist

- [ ] Add WITH_CODEX, WITH_GEMINI, CODEX_VERSION, GEMINI_VERSION build args to Dockerfile
- [ ] Add npm install blocks for @openai/codex and @google/gemini-cli
- [ ] Add config directory mounts for ~/.codex and ~/.gemini
- [ ] Add --codex-version and --gemini-version flags to build command
- [ ] Extend state.edn schema with :codex-version and :gemini-version
- [ ] Add "codex" and "gemini" commands to CLI dispatcher
- [ ] Document environment variables (CODEX_API_KEY, GEMINI_API_KEY, etc.) in README
- [ ] Add passthrough examples to config.yaml template
- [ ] Update security detection if needed (API key patterns already covered)

## Risk Assessment

**Integration risk:** LOW

Both harnesses follow patterns nearly identical to existing Claude Code integration:
- npm global install (same as Claude Code)
- Config directory in user home (same pattern as ~/.claude)
- Environment variable passthrough (same pattern as ANTHROPIC_API_KEY)
- No special system dependencies beyond Node.js

**Differences from existing harnesses:**
1. Codex uses TOML config (like OpenCode) instead of JSON (like Claude)
2. Gemini supports multiple auth methods (API key, OAuth, ADC)
3. Both support MCP (like Claude Code)

**No breaking changes** - existing harnesses continue to work unchanged.

## Sources

### OpenAI Codex CLI

- [OpenAI Codex CLI Documentation](https://developers.openai.com/codex/cli/)
- [OpenAI Codex GitHub Repository](https://github.com/openai/codex)
- [OpenAI Codex CLI Releases](https://github.com/openai/codex/releases)
- [OpenAI Codex Authentication](https://developers.openai.com/codex/auth/)
- [OpenAI Codex Configuration Reference](https://developers.openai.com/codex/config-reference/)
- [@openai/codex npm package](https://www.npmjs.com/package/@openai/codex)
- [Node.js 22+ requirement discussion](https://github.com/openai/codex/issues/164)

### Google Gemini CLI

- [Google Gemini CLI GitHub Repository](https://github.com/google-gemini/gemini-cli)
- [Gemini CLI Official Documentation](https://geminicli.com/docs/)
- [Gemini CLI Installation Guide](https://geminicli.com/docs/get-started/installation/)
- [Gemini CLI Configuration](https://geminicli.com/docs/get-started/configuration/)
- [Gemini CLI Authentication Setup](https://geminicli.com/docs/get-started/authentication/)
- [Gemini CLI Releases](https://github.com/google-gemini/gemini-cli/releases)
- [Google Developers - Gemini Code Assist CLI](https://developers.google.com/gemini-code-assist/docs/gemini-cli)
- [Google Codelabs - Hands-on with Gemini CLI](https://codelabs.developers.google.com/gemini-cli-hands-on)

### Cross-Reference

- [SmartScope OpenAI Codex CLI Guide](https://smartscope.blog/en/generative-ai/chatgpt/openai-codex-cli-comprehensive-guide/) (2025-12 update)
- [DeployHQ - Getting Started with OpenAI Codex CLI](https://www.deployhq.com/blog/getting-started-with-openai-codex-cli-ai-powered-code-generation-from-your-terminal)
- [Google Blog - Introducing Gemini CLI](https://blog.google/technology/developers/introducing-gemini-cli-open-source-ai-agent/)

**Confidence Level:** HIGH - All critical details verified from official documentation and GitHub repositories (Context7 not available for these tools, but official sources are authoritative and current as of January 2026).
