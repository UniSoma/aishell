# Requirements: Agentic Harness Sandbox

**Defined:** 2026-01-24
**Core Value:** Run agentic AI harnesses in isolated, reproducible environments without polluting the host system

## v2.4.0 Requirements

Requirements for multi-harness support milestone. Adds OpenAI Codex CLI and Google Gemini CLI.

### Codex CLI Integration

- [ ] **CODEX-01**: User can build image with OpenAI Codex CLI using `aishell build --with-codex`
- [ ] **CODEX-02**: User can pin Codex version at build time using `--with-codex=VERSION`
- [ ] **CODEX-03**: User can run Codex CLI directly with `aishell codex [args]`
- [ ] **CODEX-04**: Codex config directory (~/.codex/) is mounted from host to container
- [ ] **CODEX-05**: CODEX_API_KEY environment variable is passed through to container
- [ ] **CODEX-06**: User can configure default Codex args via config.yaml harness_args

### Gemini CLI Integration

- [ ] **GEMINI-01**: User can build image with Google Gemini CLI using `aishell build --with-gemini`
- [ ] **GEMINI-02**: User can pin Gemini version at build time using `--with-gemini=VERSION`
- [ ] **GEMINI-03**: User can run Gemini CLI directly with `aishell gemini [args]`
- [ ] **GEMINI-04**: Gemini config directory (~/.gemini/) is mounted from host to container
- [ ] **GEMINI-05**: GEMINI_API_KEY and GOOGLE_API_KEY environment variables are passed through
- [ ] **GEMINI-06**: User can configure default Gemini args via config.yaml harness_args
- [ ] **GEMINI-07**: Vertex AI credentials (GOOGLE_APPLICATION_CREDENTIALS) are passed through

### Build Infrastructure

- [ ] **BUILD-01**: Build state tracks codex and gemini installation status and versions
- [ ] **BUILD-02**: Version change detection triggers rebuild for codex/gemini
- [ ] **BUILD-03**: Build summary shows installed Codex and Gemini versions

### Documentation

- [ ] **DOCS-01**: README documents `aishell codex` and `aishell gemini` commands
- [ ] **DOCS-02**: README documents authentication methods (API key vs OAuth)
- [ ] **DOCS-03**: README documents environment variables for each harness

## Future Requirements

Deferred to future milestones.

### Session Management

- **SESSION-01**: User can persist Codex sessions across container restarts
- **SESSION-02**: User can persist Gemini chat history across container restarts

### Advanced Configuration

- **CONFIG-01**: User can configure network access policy for Codex sandbox
- **CONFIG-02**: Shell completions for codex and gemini commands

## Out of Scope

Explicitly excluded. Documented to prevent scope creep.

| Feature | Reason |
|---------|--------|
| OAuth authentication in container | Browser flow fails in headless; users authenticate on host |
| IDE integration (VS Code, etc.) | CLI-focused tool; IDE integration requires host installation |
| Codex Cloud task management | Network/account complexity beyond sandbox scope |
| Gemini MCP server management | Complex to sandbox; users configure on host |
| Session resumption in ephemeral containers | Conflicts with ephemeral design; requires persistent storage |
| Aider support | Deferred; can add in future milestone if requested |

## Traceability

Which phases cover which requirements. Updated during roadmap creation.

| Requirement | Phase | Status |
|-------------|-------|--------|
| CODEX-01 | Phase 24 | Pending |
| CODEX-02 | Phase 24 | Pending |
| CODEX-03 | Phase 25 | Pending |
| CODEX-04 | Phase 25 | Pending |
| CODEX-05 | Phase 25 | Pending |
| CODEX-06 | Phase 25 | Pending |
| GEMINI-01 | Phase 24 | Pending |
| GEMINI-02 | Phase 24 | Pending |
| GEMINI-03 | Phase 25 | Pending |
| GEMINI-04 | Phase 25 | Pending |
| GEMINI-05 | Phase 25 | Pending |
| GEMINI-06 | Phase 25 | Pending |
| GEMINI-07 | Phase 25 | Pending |
| BUILD-01 | Phase 24 | Pending |
| BUILD-02 | Phase 24 | Pending |
| BUILD-03 | Phase 24 | Pending |
| DOCS-01 | Phase 26 | Pending |
| DOCS-02 | Phase 26 | Pending |
| DOCS-03 | Phase 26 | Pending |

**Coverage:**
- v2.4.0 requirements: 19 total
- Mapped to phases: 19
- Unmapped: 0 ✓

---
*Requirements defined: 2026-01-24*
*Last updated: 2026-01-24 after roadmap creation*
