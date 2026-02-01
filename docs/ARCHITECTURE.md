# aishell Architecture

This document describes the internal architecture of aishell, including the data flow from host machine through container execution, and the responsibilities of each code namespace.

**Last updated:** v2.8.0

---

## Table of Contents

- [System Overview](#system-overview)
- [Data Flow](#data-flow)
- [Namespace Responsibilities](#namespace-responsibilities)
- [Key Files](#key-files)
- [Extension System](#extension-system)

---

## System Overview

aishell orchestrates ephemeral Docker containers for AI harnesses. The system is split across two environments:

- **Host Machine:** CLI, configuration loading, Docker API interaction
- **Container:** Harness tools, project files, isolated execution environment

```mermaid
graph TB
    subgraph Host["Host Machine"]
        CLI[CLI Entry<br/>aishell.cli]
        State[State Manager<br/>aishell.state]
        Config[Config Loader<br/>aishell.config]
        Detection[Detection<br/>aishell.detection.*]
        Build[Image Builder<br/>aishell.docker.build]
    end

    subgraph Docker["Docker Container"]
        Entry[Entrypoint<br/>entrypoint.sh]
        Harness[AI Harness<br/>claude/opencode/codex/gemini]
        Project[Project Files<br/>mounted at same path]
        Tools[Dev Tools<br/>node, git, bb, etc.]
    end

    CLI --> State
    State --> Config
    Config --> Detection
    Detection --> Build
    Build --> Entry
    Entry --> Harness
    Harness --> Project
    Harness --> Tools
```

**Key architectural principles:**

1. **2-Tier Architecture:** Foundation image (stable system tools) + harness volume (updatable tools)
2. **Volume-Based Harness Tools:** npm packages and binaries mounted read-only at `/tools`
3. **Project Extension:** Optional per-project Dockerfile extends foundation image
4. **Configuration Merge:** Global and project configs combine with defined semantics
5. **Stateless Containers:** No data persists in container; all work is in mounted project dir
6. **Security Layers:** Detection (filename patterns) + Gitleaks (content scanning) before launch

---

## 2-Tier Architecture: Foundation + Harness Volume

aishell v2.8.0 separates the Docker image into two layers to optimize for harness updates.

### Foundation Image (`aishell:foundation`)

The foundation image contains stable system components that rarely change:

**Contents:**
- Debian bookworm-slim base
- Node.js 24 runtime
- Babashka CLI runtime
- System tools (git, curl, jq, ripgrep, vim, tmux, etc.)
- Gitleaks binary (optional, via `--without-gitleaks`)
- Gosu for user switching
- Entrypoint script and profile configuration

**Rebuild triggers:**
- Dockerfile template changes (detected via content hash)
- Explicit `aishell update --force`

**Why separate:** These components are stable across harness version updates.

### Harness Volume (`aishell-harness-{hash}`)

Harness tools are stored in Docker volumes and mounted into containers:

**Contents:**
- `/tools/npm` - npm global packages (@anthropic-ai/claude-code, @codex-ai/codex, @google/gemini-cli)
- `/tools/bin` - Go binaries (opencode)

**Volume naming:** `aishell-harness-{12-char-hash}` where hash is computed from:
- Enabled harnesses (which flags passed to build)
- Harness versions (pinned or 'latest')
- Alphabetically sorted for order-independence

**Volume sharing:** Projects with identical harness configurations share the same volume.

**Rebuild triggers:**
- `aishell update` (unconditional delete + recreate)
- Missing volume
- Hash mismatch (different harness config)

**Why separate:** Harness updates don't require multi-gigabyte foundation image rebuilds or Docker extension cache invalidation.

### Runtime Wiring

**Volume mount:**
```bash
-v aishell-harness-abc123:/tools:ro
```

Mounted read-only for security (harnesses can't modify installed tools).

**PATH setup:**
1. Entrypoint script sets `HARNESS_VOLUME` env var as signal
2. Checks directory existence: `if [ -d /tools/npm/bin ]`
3. Prepends to PATH: `/tools/npm/bin:/tools/bin:$PATH`
4. Sets NODE_PATH: `/tools/npm/lib/node_modules`

**Profile.d integration:**
`/etc/profile.d/aishell.sh` ensures tmux new-window sessions inherit PATH configuration.

### Migration from v2.7.0

**Image tag change:**
- Old: `aishell:base`
- New: `aishell:foundation`

Backward compatibility maintained via error detection in `.aishell/Dockerfile`:
```dockerfile
# Old (triggers error)
FROM aishell:base

# New (correct)
FROM aishell:foundation
```

**State schema evolution:**
- New fields: `foundation-hash`, `harness-volume-hash`, `harness-volume-name`
- Deprecated (but still written): `dockerfile-hash` → `foundation-hash`
- Additive migration: nil values for missing fields, no migration code needed

---

## Data Flow

### Build Phase

The build phase creates the foundation Docker image and populates the harness volume.

```
┌──────────────────┐
│ aishell build    │
│ --with-claude    │
└────────┬─────────┘
         │
         ▼
┌──────────────────────────────────────────────┐
│ aishell.docker.build/build-foundation-image  │
│ - Check cache (Dockerfile template hash)     │
│ - Write templates to temp dir                │
│ - Construct docker build command             │
└────────┬─────────────────────────────────────┘
         │
         ▼
┌──────────────────────────────────────────────┐
│ Docker Build (Foundation)                    │
│ - FROM debian:bookworm-slim                  │
│ - Install system packages                    │
│ - Install Node.js, Babashka, Gosu, Gitleaks  │
│ - Tag: aishell:foundation                    │
└────────┬─────────────────────────────────────┘
         │
         ▼
┌──────────────────────────────────────────────┐
│ aishell.docker.volume/compute-harness-hash   │
│ - Hash enabled harnesses + versions          │
│ - Generate volume name: aishell-harness-{hash}│
└────────┬─────────────────────────────────────┘
         │
         ▼
┌──────────────────────────────────────────────┐
│ aishell.docker.volume/populate-volume        │
│ - Create volume if missing                   │
│ - Run temporary container with --rm          │
│ - npm install to /tools/npm                  │
│ - Download binaries to /tools/bin            │
│ - Set world-readable permissions             │
└────────┬─────────────────────────────────────┘
         │
         ▼
┌──────────────────────────────────────────────┐
│ aishell.state/write-state                    │
│ - Store build flags                          │
│ - Store harness versions                     │
│ - Store foundation-hash                      │
│ - Store harness-volume-name                  │
│ - Location: ~/.aishell/state.edn             │
└──────────────────────────────────────────────┘
```

**Build artifacts:**

- Docker image: `aishell:foundation` (tagged locally)
- Docker volume: `aishell-harness-{hash}` (contains harness tools)
- State file: `~/.aishell/state.edn` (EDN format)
- Build labels: Metadata embedded in image and volume

### Run Phase

The run phase executes a harness (or shell) in a container with project files mounted.

```
┌──────────────────┐
│ aishell claude   │
│ --model sonnet   │
└────────┬─────────┘
         │
         ▼
┌──────────────────────────────────────────┐
│ aishell.run/run-container                │
│ 1. Check Docker available               │
│ 2. Read state (verify harness built)    │
│ 3. Resolve image (base or extended)     │
│ 4. Load config (merge global+project)   │
│ 5. Run detection checks (unless --unsafe)│
│ 6. Build docker run command             │
└────────┬─────────────────────────────────┘
         │
         ▼
┌──────────────────────────────────────────┐
│ Security Checks                          │
│ - aishell.detection.core (filename scan) │
│ - aishell.gitleaks.warnings (freshness)  │
│ - aishell.validation (dangerous args)    │
└────────┬─────────────────────────────────┘
         │
         ▼
┌──────────────────────────────────────────┐
│ Docker Run                               │
│ docker run --rm -it \                    │
│   -v /path/to/project:/path/to/project \ │
│   -v ~/.claude:/home/user/.claude \      │
│   -e GIT_AUTHOR_NAME=... \               │
│   aishell:base \                         │
│   claude --model sonnet                  │
└────────┬─────────────────────────────────┘
         │
         ▼
┌──────────────────────────────────────────┐
│ Container Execution                      │
│ entrypoint.sh:                           │
│ 1. Create user matching host UID/GID    │
│ 2. Setup home directory                 │
│ 3. Run pre_start command (if configured)│
│ 4. Validate TERM (fallback xterm-256color)│
│ 5. Switch to user (via gosu)            │
│ 6. Start tmux session 'main'            │
│ 7. Execute harness command in tmux      │
└──────────────────────────────────────────┘
```

**Data mounts:**

- Project directory → Mounted at same path as host (read-write)
- Harness config directories → Per-harness (e.g., `~/.claude`, `~/.codex`)
- Additional mounts from `config.yaml` (e.g., `~/.ssh`, `~/.gitconfig`)

### Config Merge Strategy

Configuration files are merged with defined semantics based on the `extends` key.

```
┌─────────────────────────────┐
│ ~/.aishell/config.yaml      │
│ (global defaults)           │
└──────────┬──────────────────┘
           │
           │ extends: global (default)
           │
           ▼
┌─────────────────────────────┐     ┌──────────────────────────┐
│ .aishell/config.yaml        │────▶│ Merged Config            │
│ (project overrides)         │     │ - Lists: concatenate     │
└─────────────────────────────┘     │ - Maps: shallow merge    │
                                    │ - Scalars: project wins  │
                                    └──────────────────────────┘

           extends: none
           │
           ▼
┌─────────────────────────────┐
│ .aishell/config.yaml        │
│ (fully replaces global)     │
└─────────────────────────────┘
```

For detailed merge behavior, see [Configuration Reference](./CONFIGURATION.md).

---

## Namespace Responsibilities

aishell is organized into focused namespaces, each handling a specific concern.

### Core Namespaces

| Namespace | Responsibility | Key Functions |
|-----------|----------------|---------------|
| `aishell.cli` | Command-line interface | Argument parsing, command dispatch, help text |
| `aishell.config` | Configuration loading | YAML parsing, global+project merge, validation |
| `aishell.run` | Container orchestration | Harness routing, security checks, `docker run` exec |
| `aishell.state` | Build state persistence | Read/write `~/.aishell/state.edn` |
| `aishell.output` | Terminal formatting | Colored output, error handling |
| `aishell.util` | Shared utilities | Path helpers, home directory resolution |
| `aishell.attach` | Attach command | Reconnect to running containers' tmux sessions |
| `aishell.check` | Pre-flight checks | Validate Docker, config, image state |
| `aishell.validation` | Config validation | Warn about dangerous docker_args/mounts |

### Docker Namespaces

| Namespace | Responsibility | Key Functions |
|-----------|----------------|---------------|
| `aishell.docker` | Docker API wrapper | Image existence checks, label reading |
| `aishell.docker.build` | Image building | Cache validation, build execution, progress display |
| `aishell.docker.templates` | Embedded build files | Dockerfile, entrypoint.sh, bashrc content |
| `aishell.docker.run` | Container execution | Construct `docker run` arguments |
| `aishell.docker.hash` | Cache invalidation | Compute Dockerfile content hash |
| `aishell.docker.spinner` | Build UI | Animated spinner during quiet builds |
| `aishell.docker.naming` | Container naming | Deterministic naming, Docker state queries, conflict detection |
| `aishell.docker.extension` | Project extensions | Build per-project extended images |
| `aishell.docker.volume` | Volume management | Harness volume creation, population, hash computation, listing, pruning |

### Security Namespaces

| Namespace | Responsibility | Key Functions |
|-----------|----------------|---------------|
| `aishell.detection.core` | Sensitive file detection | Scan project dir for .env, keys, credentials |
| `aishell.detection.patterns` | Detection rules | Default patterns for sensitive files |
| `aishell.detection.formatters` | Detection output | Format findings for user display |
| `aishell.detection.gitignore` | Gitignore integration | Skip gitignored files during scan |
| `aishell.gitleaks.scan_state` | Gitleaks state tracking | Timestamp management for scan freshness |
| `aishell.gitleaks.warnings` | Gitleaks freshness warnings | Advisory warnings when scans are stale |

**Security architecture:**

1. **Detection layer (aishell.detection.*):** Filename-based pattern matching
   - Fast, runs every time unless `--unsafe`
   - Configurable via `detection:` in config.yaml
   - Supports custom patterns and allowlists

2. **Gitleaks layer (aishell.gitleaks.*):** Content-based secret scanning
   - Slow, runs on-demand via `aishell gitleaks dir .`
   - Advisory freshness warnings (>7 days stale)
   - Never blocks execution (advisory only)

3. **Validation layer (aishell.validation):** Config safety checks
   - Warns about dangerous `docker_args` (--privileged, --network=host)
   - Warns about dangerous mounts (/etc, /var/run/docker.sock)

---

## Key Files

### Host Files

| Path | Purpose | Format | Persistence |
|------|---------|--------|-------------|
| `~/.aishell/state.edn` | Build state (harnesses, versions, hash) | EDN | Persistent |
| `~/.aishell/config.yaml` | Global config defaults | YAML | Persistent |
| `~/.aishell/gitleaks-scan.edn` | Per-project Gitleaks scan timestamps | EDN | Persistent |
| `.aishell/config.yaml` | Project-specific config | YAML | Persistent (in project) |
| `.aishell/Dockerfile` | Optional project extension | Dockerfile | Persistent (in project) |

### Container Files

| Path | Purpose | Generated From |
|------|---------|----------------|
| `/entrypoint.sh` | Container initialization script | `aishell.docker.templates/entrypoint-script` |
| `/etc/skel/.bashrc.aishell` | Shell customizations | `aishell.docker.templates/bashrc-content` |
| `$PWD` | Project directory (mounted at same path as host) | Host CWD |
| `/home/user` | Dynamic user home | Created by entrypoint.sh |

**State file schema (`~/.aishell/state.edn`):**

```clojure
{:with-claude true                       ; boolean: Claude Code enabled?
 :with-opencode false                    ; boolean: OpenCode enabled?
 :with-codex false                       ; boolean: Codex CLI enabled?
 :with-gemini false                      ; boolean: Gemini CLI enabled?
 :with-gitleaks true                     ; boolean: Gitleaks installed?
 :claude-version "2.0.22"                ; string or nil: pinned version
 :opencode-version nil                   ; string or nil: pinned version
 :codex-version "0.89.0"                 ; string or nil: pinned version
 :gemini-version nil                     ; string or nil: pinned version
 :image-tag "aishell:foundation"         ; string: Docker image tag
 :build-time "2026-02-01T12:00:00Z"      ; ISO-8601 timestamp
 :foundation-hash "abc123def"            ; 12-char SHA-256 hash (NEW in v2.8.0)
 :harness-volume-hash "def456ghi"        ; 12-char hash of harness config (NEW in v2.8.0)
 :harness-volume-name "aishell-harness-def456ghi" ; Volume name (NEW in v2.8.0)
 :dockerfile-hash "abc123def"}           ; DEPRECATED: use :foundation-hash (kept for backward compat)
```

---

## Extension System

Projects can extend the base image with custom Dockerfile layers.

**Extension flow:**

```
Base Image                Project Extension           Extended Image
(aishell:base)            (.aishell/Dockerfile)       (aishell:ext-abc123)
     │                           │                           │
     │                           │                           │
     ▼                           ▼                           ▼
┌─────────┐               ┌─────────────┐            ┌──────────────┐
│ Debian  │               │ FROM         │            │ aishell:base │
│ Node.js │               │ aishell:base │            │ +            │
│ Harness │    +          │              │    →       │ Custom Layers│
│ Gosu    │               │ RUN ...      │            │ (postgres,   │
│ Tools   │               │ COPY ...     │            │  python,     │
└─────────┘               └─────────────┘            │  etc.)       │
                                                      └──────────────┘
```

**Extension behavior:**

1. **Auto-build:** If `.aishell/Dockerfile` exists, extension builds automatically before run
2. **Cache:** Extended image tagged with hash (content-based), rebuilt only on Dockerfile changes
3. **Base dependency:** Extension requires base image to exist (`aishell build` must run first)
4. **Persistence:** Extended images persist locally, shared across runs

**Example project extension:**

```dockerfile
# .aishell/Dockerfile
FROM aishell:base

# Install PostgreSQL client
RUN apt-get update && apt-get install -y postgresql-client && rm -rf /var/lib/apt/lists/*

# Install Python tools
RUN apt-get update && apt-get install -y python3-pip && rm -rf /var/lib/apt/lists/*
```

For more details, see the Dockerfile extension section in the Configuration Reference.

---

## Architecture Decisions

**Why Babashka?**

- **Fast startup:** JVM-free Clojure interpreter (sub-10ms startup vs ~1s for JVM)
- **Single binary:** No dependency management, works in containers
- **Shell interop:** First-class support for calling Docker CLI

**Why gosu for user switching?**

- **Clean process tree:** Exec's the target command (no wrapper process)
- **No sudo overhead:** Designed for containers, lighter than sudo
- **UID/GID matching:** Ensures created files have correct ownership on host

**Why immutable base + extensions?**

- **Fast iteration:** Base image builds once, projects build extensions in seconds
- **Reproducibility:** Same base shared across all projects
- **Flexibility:** Projects can add languages, databases, or tools without rebuilding base

**Why two-layer security (detection + gitleaks)?**

- **Speed vs thoroughness:** Detection is instant, Gitleaks is comprehensive
- **Fail-fast:** Catch obvious mistakes before expensive builds
- **Non-blocking:** Advisory warnings never block power users
