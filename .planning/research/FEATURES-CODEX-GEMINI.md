# Features Research: OpenAI Codex CLI and Google Gemini CLI

**Project:** aishell multi-harness support
**Milestone:** Adding Codex and Gemini CLI support to existing Claude Code and OpenCode
**Research Date:** 2026-01-24
**Confidence:** HIGH

## Executive Summary

OpenAI Codex CLI and Google Gemini CLI are both modern, agentic coding assistants with similar feature sets but distinct architectures and invocation patterns. Both support interactive REPL modes, non-interactive/headless execution for CI/CD, configuration management, and Model Context Protocol (MCP) integration.

**Key differences:**
- **Installation**: Both use npm (`@openai/codex` vs `@google/gemini-cli`)
- **Authentication**: Codex uses OAuth/API key via login command; Gemini supports multiple auth methods (Google account, API key, Vertex AI)
- **Models**: Codex offers GPT-5-Codex variants; Gemini offers Gemini 2.5/3.x variants
- **Commands**: Codex uses subcommands (`exec`, `resume`, `apply`); Gemini primarily uses slash commands in REPL

---

## OpenAI Codex CLI

### Primary Commands

Codex uses a subcommand-based CLI structure:

| Command | Alias | Purpose | Mode |
|---------|-------|---------|------|
| `codex` | - | Launch interactive terminal UI | Interactive |
| `codex exec` | `codex e` | Run non-interactively for CI/scripting | Non-interactive |
| `codex resume` | - | Continue previous sessions | Interactive |
| `codex fork` | - | Branch a session into new thread | Interactive |
| `codex apply` | `codex a` | Apply Cloud task diffs locally | Non-interactive |
| `codex login` | - | Authenticate via OAuth or API key | Configuration |
| `codex logout` | - | Remove credentials | Configuration |
| `codex completion` | - | Generate shell completions | Configuration |
| `codex cloud` | - | Manage Cloud tasks from terminal | Experimental |
| `codex mcp` | - | Manage MCP servers | Experimental |
| `codex sandbox` | - | Run commands in macOS/Linux sandboxes | Experimental |

### Interactive Slash Commands

Within the interactive TUI, users control Codex via slash commands:

| Command | Purpose |
|---------|---------|
| `/model` | Switch between GPT-5-Codex and GPT-5, adjust reasoning levels |
| `/review` | Launch code review against diffs without modifying working tree |
| `/diff` | Inspect Git diff within CLI |
| `/status` | Review active model, approval policy, token usage |
| `/compact` | Confirm conversation summarization |
| `/new` | Start fresh conversation in same session |
| `/approvals` | Switch approval modes (auto, read-only, full access) |
| `/quit` / `/exit` | Exit the CLI |

### Global Flags

Available across all subcommands:

| Flag | Short | Purpose |
|------|-------|---------|
| `--ask-for-approval` | `-a` | Controls approval timing: `untrusted \| on-failure \| on-request \| never` |
| `--cd` | `-C` | Sets working directory before processing |
| `--config` | `-c` | Overrides configuration values (`key=value`) |
| `--full-auto` | - | Low-friction preset: enables `on-request` approvals and `workspace-write` sandbox |
| `--image` | `-i` | Attaches image files (comma-separated or repeatable) |
| `--model` | `-m` | Overrides configured model |
| `--sandbox` | `-s` | Selects policy: `read-only \| workspace-write \| danger-full-access` |
| `--oss` | - | Uses local open-source provider (requires Ollama) |
| `--profile` | `-p` | Loads configuration profile |
| `--search` | - | Enables web search capability |

### Configuration Options

Configuration stored in `~/.codex/config.toml`:

- **Model selection**: Default model (e.g., `gpt-5-codex`, `gpt-5.2-codex`)
- **Approval policy**: When to pause for approval before executing commands
- **Sandbox mode**: Filesystem and network access controls
- **Features**: Optional capabilities via `[features]` table (e.g., `shell_snapshot`, `web_search_request`)
- **MCP servers**: Model Context Protocol server configurations
- **Reasoning effort**: Controls model reasoning depth
- **Shell environment**: Shell-specific policies

Command-line flags (`-c key=value`) override config file settings.

### Interactive vs Non-Interactive Modes

**Interactive Mode** (`codex`):
- Full-screen terminal UI (TUI)
- Real-time collaboration with the AI
- Session history preserved locally
- Supports image pasting directly into composer
- Resume previous sessions with interactive picker

**Non-Interactive/Exec Mode** (`codex exec`):
- Designed for CI/CD pipelines and scripting
- Completes without human interaction
- Supports `--json` flag for newline-delimited JSON events
- Can validate output against JSON Schema (`--output-schema`)
- Session resumption supported (`codex exec resume --last`)

### Multi-Model Support

Codex supports multiple GPT-5 variant models:

| Model | Capability |
|-------|-----------|
| `gpt-5-codex` | Default on macOS/Linux for coding tasks |
| `gpt-5.2-codex` | Latest (Jan 2026): improved long-context, tool calling, factuality |
| `gpt-5.1-codex-max` | Handles millions of tokens via native compaction |
| `gpt-5-codex-mini` | Cost-effective, ~4x more usage, less capable |
| `gpt-5` | General-purpose model (default on Windows) |

Users can switch models mid-session with `/model` or specify at launch with `--model`.

### Use Cases

1. **Interactive Development**: Code generation, debugging, feature implementation
2. **Code Review**: `/review` command for reviewing diffs and finding issues
3. **CI/CD Integration**: `codex exec` for automated tasks, testing, code analysis
4. **Session Management**: Resume work across sessions, fork conversations
5. **Image-Based Prompts**: Attach screenshots/design specs for UI implementation
6. **Sandboxed Execution**: Run untrusted code in isolated environments

---

## Google Gemini CLI

### Primary Commands

Gemini CLI primarily uses an interactive REPL with slash commands, with minimal top-level commands:

**Top-Level Invocation**:
```bash
gemini                                    # Launch interactive REPL
gemini -p "prompt"                        # Non-interactive with prompt
gemini -p "prompt" --output-format json   # Headless mode with JSON output
```

**Command-Line Flags**:
- `--output-format json` - Structured JSON output with response, stats, metadata
- `--output-format stream-json` - Real-time newline-delimited JSON (JSONL)
- `-p` / `--prompt` - Non-interactive prompt input

### Interactive Slash Commands

The CLI is controlled via slash commands within the REPL:

**Session Management**:
| Command | Purpose |
|---------|---------|
| `/chat save <tag>` | Save current conversation with tag |
| `/chat resume <tag>` | Resume previous conversation |
| `/chat list` | List available saved conversations |
| `/chat share <file>` | Export conversation to Markdown/JSON |
| `/resume` | Interactive session browser |

**Configuration & Settings**:
| Command | Purpose |
|---------|---------|
| `/settings` | Modify CLI behavior and appearance (edits `.gemini/settings.json`) |
| `/model` | Choose Gemini model (auto, pro, flash routing) |
| `/theme` | Change visual theme |
| `/auth` | Modify authentication method |
| `/vim` | Toggle vim-mode editing |

**Tools & Context**:
| Command | Purpose |
|---------|---------|
| `/tools` | Display available tools |
| `/tools verbose` | Show detailed tool descriptions |
| `/memory add <text>` | Add text to AI's memory |
| `/memory show` | Display concatenated memory from GEMINI.md files |
| `/directory add <path>` | Add directory to workspace |
| `/mcp` | Manage MCP servers |
| `/mcp reload` | Restart MCP servers and re-discover tools |
| `/mcp auth` | Authenticate with OAuth-enabled MCP server |

**Utility**:
| Command | Purpose |
|---------|---------|
| `/help` or `/?` | Display help information |
| `/clear` | Clear terminal screen and session history |
| `/copy` | Copy last output to clipboard |
| `/stats` | Display token usage and session statistics |
| `/compress` | Summarize chat context to save tokens |
| `/init` | Generate tailored GEMINI.md files |
| `/bug <description>` | File GitHub issues |
| `/quit` or `/exit` | Exit CLI |

### Special Input Shortcuts

- **`@<path>`** - Inject file content into prompt (respects `.gitignore`)
- **`!<command>`** - Execute shell commands directly (sets `GEMINI_CLI=1` env var)
- **`!`** (standalone) - Toggle shell mode for continuous command execution
- **Ctrl+Z** - Undo
- **Ctrl+Shift+Z** - Redo
- **Ctrl+L** - Clear screen

### Configuration Options

Configuration managed via:

1. **Environment Variables** (loaded from `.env` or shell config):
   - `GEMINI_API_KEY` - API key for Gemini API
   - `GOOGLE_API_KEY` - Alternative API key (takes precedence)
   - `GOOGLE_APPLICATION_CREDENTIALS` - Path to service account JSON for Vertex AI

2. **Settings File** (`.gemini/settings.json`):
   - Edited via `/settings` command
   - Controls behavior, appearance, preview features

3. **GEMINI.md Files**:
   - Hierarchical memory/context loaded from project
   - Used for persistent instructions

### Interactive vs Non-Interactive Modes

**Interactive Mode** (default):
- REPL environment with full slash command support
- Session management and resumption
- Theme customization
- Real-time tool execution feedback

**Headless Mode**:
- Non-interactive execution for automation/CI/CD
- `--output-format json` for structured output
- `--output-format stream-json` for real-time events
- Programmatic via `-p` flag

**Output Formats**:
```bash
# Structured JSON
gemini -p "What is 2+2?" --output-format json
# Returns: {"response": "...", "stats": {...}, ...}

# Stream JSON (JSONL)
gemini -p "List files" --output-format stream-json
# Returns: newline-delimited JSON events

# Extract response with jq
gemini -p "Analyze code" --output-format json | jq -r '.response'
```

### Multi-Model Support

Gemini CLI supports multiple Gemini model variants:

| Model | Capability |
|-------|-----------|
| Auto (default) | Automatically routes to best Gemini 2.5/3.x model for task |
| `gemini-2.5-pro` | Production-ready, balanced capability |
| `gemini-2.5-flash` | Fast, lightweight for simple tasks |
| `gemini-3-pro-preview` | Latest, most capable (requires preview features enabled) |
| `gemini-3-flash-preview` | Fast variant of Gemini 3 (requires preview features enabled) |

**Model Routing**:
- **Auto**: Simple prompts → Gemini 2.5 Flash; Complex prompts → Gemini 3 Pro (if enabled) or Gemini 2.5 Pro
- **Pro**: Prioritizes most capable model (Gemini 3 Pro if enabled)
- **Flash**: Uses fastest model variant

**Enabling Gemini 3**:
1. Use `/settings`
2. Set "Preview Features" to `true`
3. Restart CLI
4. Use `/model` to select Gemini 3 variants

### Authentication Methods

Three authentication options:

1. **Google Account Login** (simplest):
   - Opens browser for OAuth flow
   - Grants free Gemini Code Assist license
   - Access to Gemini 2.5 Pro with 1M token context window

2. **API Key** (for individual developers):
   - Set `GEMINI_API_KEY` environment variable
   - Free tier: 60 requests/min, 1,000 requests/day
   - Obtain from Google AI Studio

3. **Vertex AI** (for Google Cloud):
   - Application Default Credentials (ADC)
   - Service Account JSON key
   - Set `GOOGLE_APPLICATION_CREDENTIALS` to JSON file path

### Use Cases

1. **Interactive Development**: Code generation, feature building, debugging
2. **Code Analysis**: Log analysis, code review, architecture exploration
3. **CI/CD Automation**: Headless mode for commit messages, reviews, testing
4. **Knowledge Management**: Memory system with GEMINI.md files
5. **Tool Extension**: MCP integration for custom tools
6. **Multi-File Workflows**: `@<path>` syntax for file injection

---

## Feature Categories

### Table Stakes (Must-Have for Basic Functionality)

These features are essential for `aishell codex` and `aishell gemini` to be usable:

| Feature | Codex | Gemini | aishell Implementation Priority |
|---------|-------|--------|--------------------------------|
| **Installation via npm** | ✅ `@openai/codex` | ✅ `@google/gemini-cli` | HIGH - Version pinning at build time |
| **Interactive mode** | ✅ `codex` | ✅ `gemini` | HIGH - Direct pass-through invocation |
| **Non-interactive mode** | ✅ `codex exec` | ✅ `gemini -p` | MEDIUM - For scripted workflows |
| **API key authentication** | ✅ via `codex login` | ✅ via `GEMINI_API_KEY` | HIGH - Must mount config or pass env |
| **Config directory** | `~/.codex/` | `.gemini/` | HIGH - Mount from host to container |
| **Git identity awareness** | ✅ Reads from repo | ✅ Reads from repo | HIGH - Already implemented in aishell |
| **Pass-through args** | ✅ All flags | ✅ All flags | HIGH - Already implemented in aishell |
| **Model selection** | `--model` flag | `/model` or env var | MEDIUM - User preference |

### Differentiators (Advanced Features to Consider)

Features that enhance functionality but aren't critical for MVP:

| Feature | Codex | Gemini | aishell Consideration |
|---------|-------|--------|----------------------|
| **JSON output mode** | ✅ `--json` | ✅ `--output-format json` | LOW - Use case unclear in sandbox |
| **Session resumption** | ✅ `codex resume` | ✅ `/chat resume` | LOW - Requires persistent state |
| **Image input** | ✅ `--image` flag | ✅ Paste in REPL | LOW - Hard to support in container |
| **Web search** | ✅ `--search` flag | ✅ Built-in tool | LOW - Network access implications |
| **MCP servers** | ✅ Config in TOML | ✅ `/mcp` commands | LOW - Complex to sandbox |
| **Sandboxing** | ✅ Built-in | ✅ Experimental | N/A - aishell provides this layer |
| **Shell completions** | ✅ `codex completion` | ✅ Via settings | MEDIUM - Nice UX improvement |
| **Version pinning** | ✅ npm versioning | ✅ npm versioning | HIGH - Core aishell feature |

### Anti-Features (What NOT to Implement)

Features to explicitly avoid or defer:

| Anti-Feature | Rationale |
|--------------|-----------|
| **Cloud/Remote task management** | `codex cloud` and similar features require network access and cloud accounts; aishell focuses on local, sandboxed execution |
| **OAuth flow in container** | Browser-based OAuth is problematic in containers; users should authenticate on host, mount credentials |
| **Network access by default** | Both CLIs have web search/fetch capabilities; aishell should restrict network unless explicitly enabled |
| **Direct MCP server management** | Too complex for initial release; users can configure on host, mount config |
| **Session persistence** | Saving/resuming sessions requires persistent storage; conflicts with ephemeral container model |
| **IDE integration** | Both have IDE extensions; aishell is CLI-focused |
| **Native sandboxing** | Both have built-in sandboxing (`codex sandbox`, Gemini experimental); redundant with container isolation |

---

## Sandboxed Execution Implications

### Critical Considerations for aishell

1. **Configuration Mounting**:
   - Codex: Mount `~/.codex/config.toml` and auth tokens
   - Gemini: Mount `.gemini/settings.json` or pass environment variables
   - Both: Respect user's existing configuration on host

2. **Authentication Strategy**:
   - **Codex**: Users authenticate on host with `codex login`, aishell mounts `~/.codex/` directory
   - **Gemini**: Support both API key (via environment variable) and mounted credentials

3. **Git Identity**:
   - Both CLIs read git config for identity
   - aishell already propagates git identity - no changes needed

4. **Network Access**:
   - Both CLIs make API calls to their respective backends
   - Container must allow outbound HTTPS to:
     - `api.openai.com` (Codex)
     - `generativelanguage.googleapis.com` (Gemini API)
     - `aiplatform.googleapis.com` (Gemini Vertex AI)
   - Optional: Allow web search (requires broader network access)

5. **Working Directory**:
   - Both CLIs operate on current working directory
   - aishell must mount project directory as working dir
   - Both respect `--cd` / `-C` flag for custom paths

6. **Ephemeral Execution**:
   - Interactive sessions work fine (container runs until exit)
   - Session resumption requires persistent storage (defer for now)
   - Exec mode is naturally ephemeral (perfect fit)

---

## Invocation Pattern Mapping

### Current aishell Pattern (Claude Code, OpenCode)

```bash
aishell claude [args]
# Runs: docker run ... claude [args]

aishell opencode [args]
# Runs: docker run ... opencode [args]
```

### Proposed Pattern for Codex and Gemini

```bash
aishell codex [args]
# Runs: docker run ... codex [args]
# Examples:
#   aishell codex                      # Interactive TUI
#   aishell codex exec "fix bug"       # Non-interactive
#   aishell codex --model gpt-5-codex  # Model override
#   aishell codex resume               # Resume session

aishell gemini [args]
# Runs: docker run ... gemini [args]
# Examples:
#   aishell gemini                          # Interactive REPL
#   aishell gemini -p "analyze code"        # Non-interactive
#   aishell gemini --output-format json     # JSON output
```

### Version Pinning

Following existing aishell pattern:

```bash
# At aishell build time:
docker build --build-arg CODEX_VERSION=0.86.0
docker build --build-arg GEMINI_VERSION=latest

# Installed in container:
RUN npm install -g @openai/codex@${CODEX_VERSION}
RUN npm install -g @google/gemini-cli@${GEMINI_VERSION}
```

---

## Recommended Implementation Priorities

### Phase 1: Core Functionality (MVP)

1. **Installation**:
   - Add build args for `CODEX_VERSION` and `GEMINI_VERSION`
   - Install via npm in Dockerfile
   - Default to `latest` unless specified

2. **Configuration Mounting**:
   - Mount `~/.codex/` from host (if exists)
   - Pass `GEMINI_API_KEY` environment variable (if set)
   - Support `.gemini/` directory mounting

3. **Direct Invocation**:
   - `aishell codex [args]` → `codex [args]`
   - `aishell gemini [args]` → `gemini [args]`
   - Full argument pass-through (already implemented)

4. **Documentation**:
   - Add to README: authentication setup on host
   - Document environment variable options
   - Provide example workflows

### Phase 2: Enhanced Features (Post-MVP)

1. **Shell Completions**:
   - Generate completions in container
   - Provide instructions for host installation

2. **Model Presets**:
   - Environment variables for default models
   - `--codex-model`, `--gemini-model` flags for aishell

3. **Network Configuration**:
   - Optional `--enable-web-search` flag
   - Explicit network policy documentation

4. **Session Management** (if persistent storage added):
   - Mount session directories
   - Document limitations vs native usage

---

## Sources

**OpenAI Codex CLI:**
- [Codex CLI Documentation](https://developers.openai.com/codex/cli/)
- [Command Line Reference](https://developers.openai.com/codex/cli/reference/)
- [Codex Features](https://developers.openai.com/codex/cli/features/)
- [Slash Commands](https://developers.openai.com/codex/cli/slash-commands/)
- [Non-Interactive Mode](https://developers.openai.com/codex/noninteractive/)
- [Configuration Reference](https://developers.openai.com/codex/config-reference/)
- [Codex Models](https://developers.openai.com/codex/models/)
- [GitHub Repository](https://github.com/openai/codex)
- [@openai/codex on npm](https://www.npmjs.com/package/@openai/codex)
- [GPT-5.2-Codex Announcement](https://openai.com/index/introducing-gpt-5-2-codex/)

**Google Gemini CLI:**
- [Gemini CLI Documentation](https://geminicli.com/docs/)
- [CLI Commands Reference](https://geminicli.com/docs/cli/commands/)
- [Headless Mode](https://geminicli.com/docs/cli/headless/)
- [Authentication Setup](https://geminicli.com/docs/get-started/authentication/)
- [Installation Guide](https://geminicli.com/docs/get-started/installation/)
- [Model Selection](https://geminicli.com/docs/cli/model/)
- [Gemini 3 Features](https://geminicli.com/docs/get-started/gemini-3/)
- [GitHub Repository](https://github.com/google-gemini/gemini-cli)
- [@google/gemini-cli on npm](https://www.npmjs.com/package/@google/gemini-cli)
- [Google Cloud Documentation](https://cloud.google.com/gemini/docs/codeassist/gemini-cli)
- [Google for Developers](https://developers.google.com/gemini-code-assist/docs/gemini-cli)

**General:**
- [Hands-on with Gemini CLI (Google Codelabs)](https://codelabs.developers.google.com/gemini-cli-hands-on)
- [How Google SREs Use Gemini CLI](https://cloud.google.com/blog/topics/developers-practitioners/how-google-sres-use-gemini-cli-to-solve-real-world-outages)

---

## Confidence Assessment

| Area | Confidence | Notes |
|------|------------|-------|
| Command structure | HIGH | Verified from official docs |
| Configuration options | HIGH | Documented in official references |
| Authentication | HIGH | Multiple authoritative sources |
| Model support | HIGH | Confirmed from changelogs and model docs |
| Non-interactive modes | HIGH | Verified from official documentation |
| Sandboxing implications | MEDIUM | Some inference based on container patterns |
| Version availability | HIGH | Confirmed from npm and release notes |

**Overall confidence: HIGH** - All features verified from official documentation sources.
