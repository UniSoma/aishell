# Pitfalls Research: Multi-Harness Support (OpenAI Codex CLI & Google Gemini CLI)

**Domain:** Adding OpenAI Codex CLI and Google Gemini CLI to existing Docker-based aishell sandbox
**Researched:** 2026-01-24
**Confidence:** MEDIUM (WebSearch-based with official documentation verification)

## Executive Summary

Adding OpenAI Codex CLI and Google Gemini CLI to aishell presents container-specific challenges distinct from Claude Code and OpenCode. Key differences:

1. **Node.js version conflicts** - Codex requires Node 22, Gemini requires Node 20+, existing base has Node 24
2. **Configuration directory differences** - Codex uses `~/.codex/`, Gemini uses `~/.gemini/` (different from `~/.claude` and `~/.config/opencode`)
3. **Authentication complexity** - Codex prefers OAuth browser flow (problematic in headless containers), Gemini supports service accounts
4. **TTY requirements** - Both CLIs have known TTY/interactive mode issues in containerized environments
5. **Network sandboxing** - Codex blocks network by default, requires explicit configuration
6. **Workspace detection** - Codex has sophisticated project root detection that may behave unexpectedly in containers

Most critical: **Credential persistence in ephemeral containers** - both CLIs store auth tokens in their config directories, which will be lost on container restart unless properly mounted.

---

## OpenAI Codex CLI Pitfalls

### Pitfall 1: Node.js Version Incompatibility

**Risk:** Codex CLI requires Node.js >= 22 (recommended), but minimum Node.js 18. The base aishell image uses Node.js 24, which should work, but future Node.js versions may introduce breaking changes.

**Warning Signs:**
- Installation errors during `npm install -g @openai/codex`
- Runtime crashes with Node.js module errors
- CLI failing to start with obscure JavaScript errors

**Prevention:**
- Pin Node.js to version 22 or 24 explicitly in base Dockerfile
- Test Codex installation during Docker build, not first run
- Add version check in entrypoint: `node --version` vs Codex requirements

**Phase:** Foundation phase - must address during initial Dockerfile modification

**Confidence:** HIGH ([npm package documentation](https://www.npmjs.com/package/@openai/codex), [official installation guides](https://itecsonline.com/post/how-to-codex-cli-linux))

---

### Pitfall 2: Authentication Token Loss in Ephemeral Containers

**Risk:** Codex stores auth credentials in `~/.codex/auth.json` (plaintext) or OS keyring. In ephemeral containers without volume mount, users must re-authenticate every container launch.

**Warning Signs:**
- Users report needing to login every time they run `aishell codex`
- "Not authenticated" errors despite previous successful login
- Browser OAuth flow triggers on every invocation

**Prevention:**
- Mount `~/.codex/` directory from host to container (similar to `~/.claude/` mount)
- Document that API key mode (`OPENAI_API_KEY` env var) is preferred for containers
- Add entrypoint check: if `~/.codex/auth.json` missing AND no `OPENAI_API_KEY`, warn user
- Consider adding `cli_auth_credentials_store: file` to default config to avoid keyring issues

**Phase:** Harness integration phase - implement alongside Codex installation

**Confidence:** HIGH ([official auth documentation](https://developers.openai.com/codex/auth/), [containerization examples](https://github.com/Diatonic-AI/codex-cli-docker-mcp))

---

### Pitfall 3: OAuth Browser Flow Failure in Headless Containers

**Risk:** Codex default authentication uses OAuth browser flow, which fails in headless/SSH environments. Known GitHub issues report blocking and panicking in non-TTY environments.

**Warning Signs:**
- Codex hangs on "Waiting for authentication"
- Error: "browser required for authentication"
- Timeout errors during initial setup

**Prevention:**
- Recommend API key authentication for container use: `export OPENAI_API_KEY=sk-...`
- Document `codex auth login --api-key` workflow for initial setup
- Add troubleshooting note: browser flow requires X11 forwarding or running on host first
- Consider detecting headless environment and printing API key setup instructions

**Phase:** Documentation phase - must be clearly explained to users

**Confidence:** MEDIUM ([GitHub issue #2798](https://github.com/openai/codex/issues/2798), [GitHub issue #3820](https://github.com/openai/codex/issues/3820))

---

### Pitfall 4: TTY Echo Breakage After Interactive Commands

**Risk:** Running commands with interactive prompts (e.g., `sudo`) leaves terminal in corrupted TTY state - no echo, unresponsive to Ctrl-C.

**Warning Signs:**
- Terminal input disappears after Codex runs sudo
- ESC key unresponsive
- Must kill container to recover

**Prevention:**
- Configure `AISHELL_SKIP_PERMISSIONS` (existing env var) to avoid sudo prompts
- Document known issue with interactive prompts in containers
- Recommend non-interactive alternatives (e.g., pre-install packages in Dockerfile)
- Consider adding TTY reset command to container cleanup

**Phase:** Runtime configuration - handle during command execution logic

**Confidence:** HIGH ([GitHub issue #3646](https://github.com/openai/codex/issues/3646))

---

### Pitfall 5: Network Access Disabled by Default

**Risk:** Codex runs in sandbox mode with network blocked by default. Installation commands (npm install, apt-get, curl) fail silently or with unclear errors.

**Warning Signs:**
- Package installation fails inside Codex session
- "Network unreachable" errors
- Commands work in regular shell but fail in Codex

**Prevention:**
- Add default config at `~/.codex/config.toml`:
  ```toml
  [sandbox_workspace_write]
  network_access = true
  ```
- Document CLI override: `codex -c 'sandbox_workspace_write.network_access=true' "command"`
- For full network access: `codex --sandbox danger-full-access`
- Add warning during Codex launch if network access is disabled

**Phase:** Configuration phase - set sensible defaults for containerized use

**Confidence:** HIGH ([official security docs](https://developers.openai.com/codex/security/), [network config guide](https://smartscope.blog/en/generative-ai/chatgpt/codex-network-restrictions-solution/))

---

### Pitfall 6: Config Directory Location Differs from Other Harnesses

**Risk:** Codex uses `~/.codex/` while Claude uses `~/.claude/` and OpenCode uses `~/.config/opencode/`. Mounting wrong directory or forgetting to mount `~/.codex/` breaks persistence.

**Warning Signs:**
- Settings don't persist between sessions
- Codex regenerates default config on every launch
- Custom AGENTS.md files not recognized

**Prevention:**
- Update Docker mount logic to include `~/.codex/` when `WITH_CODEX=true`
- Create `~/.codex/` directory on host if missing (avoid permission errors)
- Document config location prominently in README
- Add validation in entrypoint: check if `~/.codex/` is mounted and writable

**Phase:** Foundation phase - implement during initial Dockerfile/entrypoint work

**Confidence:** HIGH ([official config docs](https://github.com/openai/codex/blob/main/docs/config.md), [config reference](https://developers.openai.com/codex/config-reference/))

---

### Pitfall 7: Workspace Detection Mismatch in Containers

**Risk:** Codex uses `--cd` flag and project root markers (.git, config files) to determine workspace. In containers with mounted paths, it may detect wrong root or fail to find project files.

**Warning Signs:**
- Codex operates on wrong directory
- "Cannot find project root" errors
- AGENTS.md files in subdirectories not loaded

**Prevention:**
- Always launch Codex with explicit `--cd $PWD` to set working directory
- Ensure git repository is properly mounted (required for root detection)
- Test multi-project scenarios with `--add-dir` if needed
- Document that Codex requires git root to be within container filesystem

**Phase:** Harness integration - add to Codex launch wrapper

**Confidence:** MEDIUM ([workspace detection docs](https://developers.openai.com/codex/cli/features/))

---

### Pitfall 8: Headless/Non-Interactive Mode Missing

**Risk:** Codex CLI lacks stable headless mode. It either panics or blocks in non-TTY environments, making CI/automation difficult.

**Warning Signs:**
- Codex hangs indefinitely in scripts
- Panic errors in cron jobs or CI pipelines
- No output when run without TTY

**Prevention:**
- Use `codex exec` (or `codex e`) for non-interactive runs
- Add `--json` flag for machine-readable output
- Consider detecting TTY availability in wrapper script
- Document that full interactive mode requires TTY allocation

**Phase:** CLI wrapper implementation - handle TTY detection

**Confidence:** HIGH ([GitHub issue #4219](https://github.com/openai/codex/issues/4219))

---

## Google Gemini CLI Pitfalls

### Pitfall 1: Node.js Version Requirements Different from Codex

**Risk:** Gemini CLI requires Node.js >= 20 (some sources say >= 18). If pinning to Node 22 for Codex, Gemini should work, but version conflicts may arise with other tools.

**Warning Signs:**
- Installation fails with "unsupported engine" error
- Runtime errors about missing Node.js features
- npm warns about incompatible versions

**Prevention:**
- Use Node.js 22 or 24 as baseline (satisfies both Codex and Gemini)
- Add npm engine check during installation
- Test both CLIs work on same Node version during build

**Phase:** Foundation phase - coordinate with Codex Node.js decision

**Confidence:** HIGH ([npm package](https://www.npmjs.com/package/@google/gemini-cli), [installation guides](https://geminicli.com/docs/get-started/installation/))

---

### Pitfall 2: Configuration Directory Different from Codex and Claude

**Risk:** Gemini uses `~/.gemini/` for config and credentials. Missing mount loses all settings, chat history, and authentication tokens.

**Warning Signs:**
- Gemini asks for authentication on every container launch
- Settings.json changes don't persist
- Chat history disappears between sessions

**Prevention:**
- Mount `~/.gemini/` directory from host (pattern: `-v "$HOME/.gemini:/home/developer/.gemini"`)
- Create directory on host if missing: `mkdir -p ~/.gemini`
- Handle permissions correctly (match container user UID/GID)
- Document mount requirement in README

**Phase:** Harness integration - add mount logic

**Confidence:** HIGH ([config docs](https://github.com/google-gemini/gemini-cli/blob/main/docs/get-started/configuration.md), [Docker examples](https://hub.docker.com/r/tgagor/gemini-cli))

---

### Pitfall 3: Service Account Authentication Requires Multiple Environment Variables

**Risk:** Gemini requires GOOGLE_APPLICATION_CREDENTIALS (path to JSON), GOOGLE_CLOUD_PROJECT (project ID), and GOOGLE_CLOUD_LOCATION. Missing any one causes cryptic authentication failures.

**Warning Signs:**
- "CREDENTIALS_MISSING" error despite valid service account
- "Project ID not specified" errors
- "Location required" warnings

**Prevention:**
- Document all three required environment variables together
- Validate in entrypoint: check if service account auth is attempted, ensure all vars are set
- Provide clear error messages for each missing variable
- Example in README:
  ```bash
  export GOOGLE_APPLICATION_CREDENTIALS="/path/to/keyfile.json"
  export GOOGLE_CLOUD_PROJECT="my-project-id"
  export GOOGLE_CLOUD_LOCATION="us-central1"
  ```
- For Docker: mount JSON file and pass all three env vars

**Phase:** Documentation and configuration - critical for Vertex AI users

**Confidence:** HIGH ([auth docs](https://github.com/google-gemini/gemini-cli/blob/main/docs/get-started/authentication.md), [GitHub issue #15823](https://github.com/google-gemini/gemini-cli/issues/15823))

---

### Pitfall 4: API Key Conflicts Between Authentication Methods

**Risk:** If GEMINI_API_KEY or GOOGLE_API_KEY is set, service account authentication silently fails. Users trying to switch auth methods get confused.

**Warning Signs:**
- Service account auth doesn't work despite correct configuration
- Errors about "API key invalid" when trying to use service account
- Silent fallback to wrong authentication method

**Prevention:**
- Add validation: if using service account, ensure API key env vars are unset
- Provide clear error message: "Cannot use both API key and service account. Unset GEMINI_API_KEY and GOOGLE_API_KEY"
- Document auth method precedence in README
- Consider startup check that warns about conflicting auth config

**Phase:** Configuration validation - add to entrypoint checks

**Confidence:** HIGH ([auth documentation](https://github.com/google-gemini/gemini-cli/blob/main/docs/get-started/authentication.md))

---

### Pitfall 5: TTY Freeze with Experimental Features

**Risk:** The `--experimental-acp` flag causes terminal to freeze completely within 2 seconds in TTY mode. Even Ctrl-C doesn't work.

**Warning Signs:**
- Terminal becomes unresponsive after launching Gemini
- Must kill container from another session
- Happens specifically with certain flags

**Prevention:**
- Document experimental flag issues in troubleshooting section
- Avoid enabling experimental features by default
- If freeze occurs, users should use `docker stop <container>` to recover
- Monitor Gemini CLI releases for fix

**Phase:** Documentation - warn users about experimental features

**Confidence:** MEDIUM ([GitHub PR #10089](https://github.com/google-gemini/gemini-cli/pull/10089))

---

### Pitfall 6: Docker Build Failure - Missing Pre-Built Artifacts

**Risk:** Official Dockerfile assumes `.tgz` artifacts exist (pre-built on host). Fresh clone and build fails with "no such file or directory".

**Warning Signs:**
- Docker build fails at COPY step
- Error: "packages/cli/dist/google-gemini-cli-*.tgz not found"
- Dockerfile works for maintainers but not for users

**Prevention:**
- Install via npm instead of building from source: `npm install -g @google/gemini-cli`
- Don't use official Dockerfile directly - it's for development, not deployment
- Document npm installation as primary method
- If building from source, add build step before Docker build

**Phase:** Foundation phase - choose installation method

**Confidence:** HIGH ([GitHub issue #15859](https://github.com/google-gemini/gemini-cli/issues/15859))

---

### Pitfall 7: Authentication Gets Stuck in "Waiting for auth" Loop

**Risk:** Gemini CLI freezes at "⠼ Waiting for auth... (Press ESC to cancel)" in containers, even with valid credentials. Works with `--debug` flag.

**Warning Signs:**
- Spinner runs indefinitely
- ESC doesn't cancel
- Same credentials work outside container

**Prevention:**
- Recommend API key auth for containers instead of OAuth
- If OAuth required, ensure proper TTY allocation (`docker run -it`)
- Add `--debug` flag to container launch for troubleshooting
- Document known issue and workaround in README

**Phase:** Troubleshooting documentation

**Confidence:** MEDIUM ([GitHub issue #1919](https://github.com/google-gemini/gemini-cli/issues/1919))

---

### Pitfall 8: Connection Failure with VS Code Dev Containers

**Risk:** Gemini CLI can't discover or connect to VS Code when running inside dev containers (different from aishell's approach, but relevant for users).

**Warning Signs:**
- "/ide install" works but connection fails
- "Cannot connect to VS Code" errors
- Works on host but not in container

**Prevention:**
- Document known limitation with VS Code integration in containers
- For aishell use case (CLI-only), this may not be relevant
- If users need IDE integration, recommend running Gemini on host, not in container

**Phase:** Documentation - set user expectations

**Confidence:** MEDIUM ([GitHub issue #10475](https://github.com/google-gemini/gemini-cli/issues/10475), [GitHub issue #6297](https://github.com/google-gemini/gemini-cli/issues/6297))

---

### Pitfall 9: SSL/TLS Certificate Errors on Corporate Networks

**Risk:** On networks with SSL inspection (corporate proxies), Gemini fails with "UNABLE_TO_GET_ISSUER_CERT_LOCALLY" error.

**Warning Signs:**
- Certificate validation errors
- Works on home network, fails at office
- Other HTTPS tools work, but Gemini doesn't

**Prevention:**
- Document environment variable workaround: `export NODE_EXTRA_CA_CERTS=/path/to/corporate-ca.pem`
- Mount corporate CA certificates into container
- Add troubleshooting section for corporate environments
- Consider allowing custom CA bundle in config.yaml

**Phase:** Documentation and configuration

**Confidence:** HIGH ([troubleshooting docs](https://github.com/google-gemini/gemini-cli/blob/main/docs/troubleshooting.md))

---

## Common Pitfalls (Both Codex and Gemini)

### Pitfall 1: Credential Volume Mount Permission Errors

**Risk:** When mounting `~/.codex/` or `~/.gemini/`, container user UID/GID mismatch causes permission denied errors.

**Warning Signs:**
- "Permission denied" when writing to config directory
- Cannot save settings or auth tokens
- Works initially, then breaks after container restart

**Prevention:**
- Use gosu to match container user to host user (aishell already does this)
- Ensure mounted directories have correct ownership: `chown -R $USER_ID:$GROUP_ID`
- Create directories on host before mounting if they don't exist
- Test write permissions in entrypoint before launching harness

**Phase:** Entrypoint enhancement - extend existing user matching logic

**Confidence:** HIGH (standard Docker volume permission issue, [Docker examples](https://hub.docker.com/r/tgagor/gemini-cli))

---

### Pitfall 2: Concurrent Config Directory Modification

**Risk:** If user runs multiple containers (or host + container) simultaneously, both write to same config files, causing corruption.

**Warning Signs:**
- Settings randomly reset
- Auth tokens disappear
- Config files contain merge conflicts or garbled data

**Prevention:**
- Document: don't run multiple aishell instances on same project simultaneously
- Consider adding lock file mechanism for config directories
- Warn users about concurrent access in README
- For multi-user scenarios, recommend separate config directories per container

**Phase:** Documentation - set clear usage expectations

**Confidence:** MEDIUM (inferred from config architecture)

---

### Pitfall 3: Environment Variable Passthrough Inconsistencies

**Risk:** API keys and config passed via environment variables may not reach CLI if Docker run command doesn't include `-e` flags.

**Warning Signs:**
- "API key not found" despite export on host
- Environment variables visible on host but not in container
- Configuration works outside Docker, fails inside

**Prevention:**
- Document all required environment variables with `-e` flag examples
- Extend aishell config.yaml to support env var passthrough for Codex/Gemini
- Add entrypoint validation: print warning if expected env vars are missing
- Support .env file loading (both CLIs support this natively)

**Phase:** Runtime configuration - extend existing env var handling

**Confidence:** HIGH (standard Docker environment variable challenge)

---

### Pitfall 4: TTY Allocation Missing for Interactive Mode

**Risk:** Running container without `-it` flags causes TTY-dependent features to fail or behave unexpectedly.

**Warning Signs:**
- Spinners don't render
- Interactive prompts hang
- CLI complains about "not a TTY"

**Prevention:**
- Ensure aishell wrapper always uses `-it` for harness commands
- Document when `-it` is required vs optional
- Add detection: if not TTY, suggest adding `--tty` flag
- Test both TTY and non-TTY modes during integration

**Phase:** CLI wrapper - ensure proper Docker flags

**Confidence:** HIGH ([TTY issues](https://github.com/google-gemini/gemini-cli/issues/1919), [Codex issues](https://github.com/openai/codex/issues/4219))

---

### Pitfall 5: Incomplete npm Global Installation in Multi-Stage Builds

**Risk:** If using multi-stage Dockerfile, npm global packages installed in one stage may not be available in final stage.

**Warning Signs:**
- "command not found: codex" or "command not found: gemini"
- npm install succeeds but binary not in PATH
- Works in one build, breaks in another

**Prevention:**
- Install CLIs in final stage, not intermediate stages
- Verify installation with version check: `codex --version && gemini --version`
- Add PATH validation in entrypoint
- Test that both CLIs are runnable before committing Docker image

**Phase:** Foundation phase - Dockerfile structure

**Confidence:** HIGH (standard multi-stage build pitfall)

---

### Pitfall 6: Version Pinning Neglected

**Risk:** Installing latest versions without pinning means container rebuilds may break due to incompatible CLI updates.

**Warning Signs:**
- Build succeeds, but harness behaves differently
- New CLI version introduces breaking changes
- Container built last month doesn't match today's build

**Prevention:**
- Support version pinning like existing harnesses: `--with-codex=0.87.0`
- Document how to pin versions in .aishell config
- Default to latest, but make pinning easy
- Test version pinning logic during implementation

**Phase:** Foundation phase - implement alongside installation

**Confidence:** HIGH (aishell already does this for Claude/OpenCode)

---

### Pitfall 7: Missing Debug/Troubleshooting Flags

**Risk:** When issues occur, users have no way to get diagnostic information from CLIs running in containers.

**Warning Signs:**
- "It doesn't work" reports with no details
- Can't reproduce issues
- No logs to analyze

**Prevention:**
- Document debug flags: `codex --json`, `gemini --debug`
- Add `aishell codex --debug` wrapper that enables verbose output
- Preserve CLI output in container logs
- Provide troubleshooting guide with diagnostic commands

**Phase:** Documentation and CLI wrapper

**Confidence:** HIGH ([debug documentation](https://github.com/google-gemini/gemini-cli/blob/main/docs/troubleshooting.md))

---

## Phase Mapping Recommendations

| Phase | Priority Pitfalls |
|-------|------------------|
| **Foundation (Dockerfile/Entrypoint)** | Node.js versions, config directory mounts, version pinning, installation validation |
| **Harness Integration** | Authentication setup, credential persistence, TTY allocation, workspace detection |
| **Runtime Configuration** | Network access defaults, environment variable passthrough, permission handling |
| **Documentation** | OAuth vs API key auth, troubleshooting guides, corporate network setup, experimental flags |
| **Testing** | Concurrent access, multi-harness scenarios, TTY vs non-TTY modes |

---

## Sources

**OpenAI Codex CLI:**
- [OpenAI Codex CLI npm package](https://www.npmjs.com/package/@openai/codex)
- [OpenAI Codex official documentation](https://developers.openai.com/codex/cli/)
- [Codex authentication guide](https://developers.openai.com/codex/auth/)
- [Codex configuration reference](https://developers.openai.com/codex/config-reference/)
- [Codex security documentation](https://developers.openai.com/codex/security/)
- [GitHub: openai/codex repository](https://github.com/openai/codex)
- [GitHub: openai/codex-universal Docker base image](https://github.com/openai/codex-universal)
- [GitHub issue #4219: Headless mode request](https://github.com/openai/codex/issues/4219)
- [GitHub issue #3646: TTY echo breakage](https://github.com/openai/codex/issues/3646)
- [GitHub issue #2798: Remote OAuth sign-in](https://github.com/openai/codex/issues/2798)
- [GitHub issue #3820: Headless authentication](https://github.com/openai/codex/issues/3820)
- [Codex network restrictions guide](https://smartscope.blog/en/generative-ai/chatgpt/codex-network-restrictions-solution/)
- [Codex Docker MCP example](https://github.com/Diatonic-AI/codex-cli-docker-mcp)

**Google Gemini CLI:**
- [Google Gemini CLI npm package](https://www.npmjs.com/package/@google/gemini-cli)
- [GitHub: google-gemini/gemini-cli repository](https://github.com/google-gemini/gemini-cli)
- [Gemini CLI authentication documentation](https://github.com/google-gemini/gemini-cli/blob/main/docs/get-started/authentication.md)
- [Gemini CLI configuration documentation](https://github.com/google-gemini/gemini-cli/blob/main/docs/get-started/configuration.md)
- [Gemini CLI troubleshooting guide](https://github.com/google-gemini/gemini-cli/blob/main/docs/troubleshooting.md)
- [Gemini CLI official website](https://geminicli.com/)
- [Docker Hub: tgagor/gemini-cli](https://hub.docker.com/r/tgagor/gemini-cli)
- [GitHub: tgagor/docker-gemini-cli](https://github.com/tgagor/docker-gemini-cli)
- [GitHub issue #15823: Vertex AI authentication bug](https://github.com/google-gemini/gemini-cli/issues/15823)
- [GitHub issue #10475: VS Code dev container connection](https://github.com/google-gemini/gemini-cli/issues/10475)
- [GitHub issue #1919: Docker container usage](https://github.com/google-gemini/gemini-cli/issues/1919)
- [GitHub issue #15859: Dockerfile build failure](https://github.com/google-gemini/gemini-cli/issues/15859)
- [GitHub PR #10089: Experimental ACP TTY fix](https://github.com/google-gemini/gemini-cli/pull/10089)
- [Medium: How to Set Up Gemini CLI in Docker Container](https://medium.com/@johnnyorellana32/how-to-set-up-gemini-cli-in-a-docker-container-4ce16b611d16)

**Confidence Note:** Most findings are MEDIUM-HIGH confidence, based on official documentation, GitHub issues, and community Docker implementations. Some edge cases (experimental flags, concurrent access) are inferred from architecture and marked MEDIUM confidence pending validation.
