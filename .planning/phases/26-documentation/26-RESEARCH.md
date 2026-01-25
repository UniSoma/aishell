# Phase 26: Documentation - Research

**Researched:** 2026-01-25
**Domain:** README documentation, CLI tool documentation patterns, authentication documentation
**Confidence:** HIGH

## Summary

This phase focuses on documenting the new `aishell codex` and `aishell gemini` commands in the README, along with authentication methods and environment variables for all harnesses. The research covered CLI README documentation best practices, authentication flows for each harness, and the current codebase structure.

Key findings:
- Authentication presentation is nuanced: Claude Code and Codex both support interactive OAuth flows that work from containers (copy-paste URL or device code), while Gemini requires API keys for container use
- Environment variables are already implemented in the codebase (src/aishell/docker/run.clj) - documentation just needs to reflect them
- README already has established patterns (tables for env vars, code blocks for examples) that should be followed
- The existing README structure provides clear templates for adding new harness documentation

**Primary recommendation:** Add codex/gemini to existing command sections, document authentication with balanced presentation of OAuth vs API key options, and add environment variable documentation organized by harness.

## Standard Stack

No external libraries needed. This is pure documentation work.

### Core
| Component | Purpose | Why Standard |
|-----------|---------|--------------|
| README.md | Primary user documentation | Already exists, established format |
| Markdown | Documentation format | Standard for GitHub projects |

### Supporting
| Component | Purpose | When to Use |
|-----------|---------|-------------|
| Code blocks | Command examples | All usage examples |
| Tables | Environment variable listings | Structured data presentation |
| Inline code | Commands/flags within prose | All command references |

## Architecture Patterns

### Recommended README Structure

The existing README has these sections that need updates:

```
README.md
├── Features             # May mention harness count
├── Usage
│   ├── Build an image   # Add --with-codex, --with-gemini
│   ├── Run harnesses    # Add aishell codex, aishell gemini
│   └── ...
├── Environment Variables # Add harness-specific env vars
└── ...
```

### Pattern 1: Command Examples Consistency
**What:** Follow existing command example formatting
**When to use:** All new command examples
**Example:**
```markdown
### Run harnesses

\`\`\`bash
# Enter interactive shell
aishell

# Run Claude Code
aishell claude

# Run OpenCode
aishell opencode

# Run Codex CLI
aishell codex

# Run Gemini CLI
aishell gemini
\`\`\`
```

### Pattern 2: Environment Variable Table
**What:** Use existing table format for environment variables
**When to use:** All new environment variable documentation
**Example:**
```markdown
## Environment Variables

| Variable | Purpose | Harness |
|----------|---------|---------|
| `ANTHROPIC_API_KEY` | Anthropic API access | Claude Code |
| `CODEX_API_KEY` | OpenAI API access for Codex | Codex CLI |
| `GEMINI_API_KEY` | Google AI Studio API key | Gemini CLI |
```

### Pattern 3: Authentication Section
**What:** Balanced presentation of authentication options
**When to use:** Documenting auth for each harness
**Structure:**
```markdown
### Authentication

Each harness supports multiple authentication methods:

**Claude Code:**
- API key: Set `ANTHROPIC_API_KEY` environment variable
- OAuth: Run `aishell claude` and follow interactive login prompts

**Codex CLI:**
- API key: Set `CODEX_API_KEY` (non-interactive) or `OPENAI_API_KEY`
- OAuth: Run `aishell codex` and use `codex login --device-auth` for headless environments

**Gemini CLI:**
- API key: Set `GEMINI_API_KEY` or `GOOGLE_API_KEY`
- OAuth: Must authenticate on host first; credentials cached in `~/.gemini/`
```

### Anti-Patterns to Avoid
- **Favoring API keys over OAuth:** Both options are valid; users with existing accounts may prefer OAuth to avoid extra costs
- **Incomplete environment variable lists:** All passthrough variables should be documented
- **Inconsistent formatting:** Match existing README style exactly

## Don't Hand-Roll

This is documentation-only phase. No code to hand-roll.

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| Documentation format | Custom format | Existing README patterns | Consistency with existing docs |
| Auth documentation | Custom explanations | Official harness docs (referenced) | Accuracy and currency |

## Common Pitfalls

### Pitfall 1: Incomplete Auth Documentation
**What goes wrong:** Users can't authenticate successfully because documentation misses edge cases
**Why it happens:** Documentation doesn't cover container-specific authentication flows
**How to avoid:** Document both "auth on host, use in container" and "auth from within container" patterns for each harness
**Warning signs:** User confusion about which auth method to use in containers

### Pitfall 2: API Key Bias
**What goes wrong:** Users think they must get API keys, missing free tier options
**Why it happens:** API key documentation is simpler to write than OAuth flow documentation
**How to avoid:** Present OAuth flow first for users with existing accounts, then API key as alternative
**Warning signs:** Documentation only mentions API keys

### Pitfall 3: Missing Harness-Specific Nuances
**What goes wrong:** Generic documentation that doesn't capture harness-specific behavior
**Why it happens:** Treating all harnesses as identical
**How to avoid:** Document specific differences:
  - Claude Code: Copy-paste URL OAuth works in container
  - Codex: `--device-auth` flag for headless, `CODEX_API_KEY` only works in `codex exec` mode
  - Gemini: No device code flow, requires API key or pre-existing auth for containers
**Warning signs:** Documentation says "all harnesses work the same way"

### Pitfall 4: Stale Environment Variable List
**What goes wrong:** Documentation doesn't match what the code actually passes through
**Why it happens:** Documentation written without referencing current codebase
**How to avoid:** Reference `src/aishell/docker/run.clj` api-key-vars list when documenting
**Warning signs:** Users report that documented env vars aren't being passed to container

### Pitfall 5: Missing Config Directory Information
**What goes wrong:** Users don't understand that auth credentials persist between container runs
**Why it happens:** Focus on env vars without explaining mounted config directories
**How to avoid:** Document that `~/.claude`, `~/.codex`, `~/.gemini` are mounted from host
**Warning signs:** Users think they need to re-authenticate every container run

## Code Examples

Since this is documentation, examples are README markdown snippets.

### Build Examples Update
```markdown
# Build with Codex CLI
aishell build --with-codex

# Build with Gemini CLI
aishell build --with-gemini

# Build with all harnesses
aishell build --with-claude --with-opencode --with-codex --with-gemini

# Build with specific versions
aishell build --with-codex=0.1.2025062501
```

### Run Examples Update
```markdown
# Run Codex CLI
aishell codex

# Run Gemini CLI
aishell gemini

# Pass arguments to harness
aishell codex --help
aishell gemini --help
```

### Authentication Documentation Template
```markdown
## Authentication

aishell mounts harness configuration directories from your host (`~/.claude`, `~/.codex`, `~/.gemini`), so authentication persists between container sessions.

### Claude Code

**Option 1: Interactive OAuth (recommended for existing accounts)**
Run `aishell claude` and follow the prompts. Claude Code displays a URL you can copy-paste into your browser, completing OAuth even from within the container.

**Option 2: API Key**
```bash
export ANTHROPIC_API_KEY="your-key-here"
aishell claude
```

### Codex CLI

**Option 1: Interactive OAuth**
Run `aishell codex` and select "Sign in with ChatGPT". In headless environments, use:
```bash
codex login --device-auth
```
This displays a code to enter at a URL in your browser.

**Option 2: API Key**
```bash
export OPENAI_API_KEY="your-key-here"  # For login
# or
export CODEX_API_KEY="your-key-here"   # Only works with `codex exec`, not interactive
aishell codex
```

### Gemini CLI

**Option 1: Authenticate on host first (recommended for existing accounts)**
```bash
# On your host machine (not in container)
gemini  # Select "Login with Google"

# Then run in container - credentials are mounted
aishell gemini
```

**Option 2: API Key**
```bash
export GEMINI_API_KEY="your-key-here"
# or
export GOOGLE_API_KEY="your-key-here"
aishell gemini
```

**Note:** Gemini CLI does not support device code flow for container authentication. Either authenticate on host first, or use an API key.
```

### Environment Variables Table Template
```markdown
## Environment Variables

aishell automatically passes these environment variables to containers when set on your host:

### Harness-Specific Keys

| Variable | Purpose | Notes |
|----------|---------|-------|
| `ANTHROPIC_API_KEY` | Claude Code API access | Required for API key auth |
| `OPENAI_API_KEY` | Codex login, OpenCode | Used by multiple harnesses |
| `CODEX_API_KEY` | Codex CLI API access | Only works with `codex exec` mode |
| `GEMINI_API_KEY` | Gemini CLI API access | From Google AI Studio |
| `GOOGLE_API_KEY` | Gemini/Vertex AI access | Alternative to GEMINI_API_KEY |

### Cloud Provider Credentials

| Variable | Purpose | Notes |
|----------|---------|-------|
| `GOOGLE_APPLICATION_CREDENTIALS` | Vertex AI service account | Path to JSON key file (file is mounted) |
| `GOOGLE_CLOUD_PROJECT` | GCP project ID | Required for Vertex AI |
| `GOOGLE_CLOUD_LOCATION` | GCP region | Required for Vertex AI |
| `AWS_ACCESS_KEY_ID` | AWS access | For harnesses using AWS |
| `AWS_SECRET_ACCESS_KEY` | AWS secret | For harnesses using AWS |
| `AWS_REGION` | AWS region | For harnesses using AWS |
| `AWS_PROFILE` | AWS profile | Named profile support |
| `AZURE_OPENAI_API_KEY` | Azure OpenAI | For Azure-hosted models |
| `AZURE_OPENAI_ENDPOINT` | Azure endpoint | For Azure-hosted models |

### Other

| Variable | Purpose | Notes |
|----------|---------|-------|
| `GITHUB_TOKEN` | GitHub API access | For GitHub operations |
| `GROQ_API_KEY` | Groq API access | For Groq-hosted models |
| `AISHELL_SKIP_PERMISSIONS` | Claude permissions | Set to `false` to enable prompts |
```

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| API key only docs | Balanced OAuth + API key | Phase 26 | Users with accounts can use free tiers |
| Generic auth docs | Harness-specific auth flows | Phase 26 | Accurate per-harness guidance |
| Simple env var list | Categorized env var table | Phase 26 | Easier to find relevant variables |

**Deprecated/outdated:**
- Single auth approach per harness: All harnesses now support multiple auth methods
- Environment-variable-only documentation: Config directory mounting is equally important

## Open Questions

### 1. OpenCode Authentication
- What we know: OpenCode uses `~/.config/opencode` and `~/.local/share/opencode`
- What's unclear: Exact environment variables OpenCode supports (not researched in Phase 25)
- Recommendation: Include mount paths but note OpenCode may have its own documentation for auth

### 2. Config.yaml harness_args Examples
- What we know: config.yaml supports harness_args section per CONTEXT.md
- What's unclear: Whether to add examples in README or defer to separate config documentation
- Recommendation: Claude's discretion per CONTEXT.md

### 3. Gemini Device Code Flow
- What we know: Currently not supported (GitHub issue #1696)
- What's unclear: Whether this will be added soon
- Recommendation: Document current state (no device code), note that auth-on-host pattern works

## Authentication Flow Summary

| Harness | OAuth in Container | Device Code | API Key Env Var | Config Directory |
|---------|-------------------|-------------|-----------------|------------------|
| Claude Code | Yes (copy-paste URL) | N/A | ANTHROPIC_API_KEY | ~/.claude |
| Codex CLI | Via device auth | Yes (`--device-auth`) | CODEX_API_KEY (exec only), OPENAI_API_KEY | ~/.codex |
| Gemini CLI | No | No | GEMINI_API_KEY, GOOGLE_API_KEY | ~/.gemini |
| OpenCode | Unknown | Unknown | OPENAI_API_KEY | ~/.config/opencode, ~/.local/share/opencode |

## Sources

### Primary (HIGH confidence)
- [OpenAI Codex Authentication](https://developers.openai.com/codex/auth/) - Device code auth, auth.json, ChatGPT login flow
- [OpenAI Codex CLI Reference](https://developers.openai.com/codex/cli/reference/) - Command-line options
- [Gemini CLI Authentication](https://geminicli.com/docs/get-started/authentication/) - API key, OAuth, Vertex AI options
- [Gemini CLI Headless Mode](https://geminicli.com/docs/cli/headless/) - Headless usage requirements
- Existing codebase: src/aishell/docker/run.clj (lines 149-166) - Implemented env var list
- Existing codebase: src/aishell/cli.clj - Current command structure
- Existing README.md - Current documentation patterns

### Secondary (MEDIUM confidence)
- [GitHub - Codex Device Auth Issues](https://github.com/openai/codex/issues/3820) - Device code flow details
- [GitHub - Gemini Headless Auth Issue](https://github.com/google-gemini/gemini-cli/issues/1696) - Confirmation no device code flow
- [Make a README](https://www.makeareadme.com/) - README best practices

### Tertiary (LOW confidence)
- WebSearch results on container authentication patterns - General guidance only

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH - Documentation only, no libraries needed
- Architecture: HIGH - Following existing README patterns exactly
- Pitfalls: HIGH - Based on verified authentication documentation
- Code examples: HIGH - Based on existing codebase and official docs

**Research date:** 2026-01-25
**Valid until:** 30 days (documentation patterns stable, auth flows may update)

---
*Phase: 26-documentation*
*Research completed: 2026-01-25*
