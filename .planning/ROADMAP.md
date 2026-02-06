# Roadmap: Agentic Harness Sandbox

## Milestones

- ✅ **v1.0 MVP** - Phases 1-8 (shipped 2026-01-18)
- ✅ **v1.1 Per-project Runtime Configuration** - Phases 9-10 (shipped 2026-01-19)
- ✅ **v1.2 Hardening & Edge Cases** - Phases 11-12 (shipped 2026-01-19)
- ✅ **v2.0 Babashka Rewrite** - Phases 13-18 (shipped 2026-01-21)
- ✅ **v2.3.0 Safe AI Context Protection** - Phases 18.1-23 (shipped 2026-01-24)
- ✅ **v2.4.0 Multi-Harness Support** - Phases 24-27 (shipped 2026-01-25)
- ✅ **v2.5.0 Optimization & Polish** - Phases 28-29 (shipped 2026-01-26)
- ✅ **v2.7.0 tmux Integration & Named Containers** - Phases 30-34 (shipped 2026-01-31)
- ✅ **v2.8.0 Decouple Harness Tools** - Phases 35-38 (shipped 2026-02-01)
- ✅ **v2.9.0 tmux Opt-in & Plugin Support** - Phases 39-43 (shipped 2026-02-03)
- ✅ **v2.10.0 Gitleaks Opt-in** - Phases 44-45 (shipped 2026-02-05)
- 🚧 **v3.0.0 Docker-native Attach** - Phases 46-52 (in progress)

## Phases

<details>
<summary>✅ v1.0 MVP (Phases 1-8) - SHIPPED 2026-01-18</summary>

### Phase 1: Foundation
**Goal**: Docker-based ephemeral sandbox infrastructure
**Plans**: 2 plans

Plans:
- [x] 01-01: Base Docker image with gosu and dynamic user creation
- [x] 01-02: Self-contained installer with heredoc-embedded build files

### Phase 2: Git Integration
**Goal**: Host git identity propagated into containers
**Plans**: 1 plan

Plans:
- [x] 02-01: Git user/email and safe.directory configuration

### Phase 3: Harness Installation
**Goal**: Claude Code and OpenCode installed and runnable
**Plans**: 3 plans

Plans:
- [x] 03-01: Claude Code npm installation with version pinning
- [x] 03-02: OpenCode npm installation with version pinning
- [x] 03-03: Harness config mounting from host

### Phase 4: CLI Commands
**Goal**: User-facing commands for shell and harness access
**Plans**: 2 plans

Plans:
- [x] 04-01: Shell access command (aishell)
- [x] 04-02: Direct harness commands (aishell claude, aishell opencode)

### Phase 5: Project Mounting
**Goal**: Projects accessible at same path inside container
**Plans**: 1 plan

Plans:
- [x] 05-01: Project volume mounting with path preservation

### Phase 6: Base Tools
**Goal**: Essential development tools in base image
**Plans**: 2 plans

Plans:
- [x] 06-01: Base tool installation (git, curl, vim, jq, ripgrep)
- [x] 06-02: Node.js installation via multi-stage build

### Phase 7: Extension Mechanism
**Goal**: Per-project customization without polluting base
**Plans**: 2 plans

Plans:
- [x] 07-01: .aishell/Dockerfile extension support
- [x] 07-02: Extension image caching and state tracking

### Phase 8: Build Workflow
**Goal**: Explicit build/update separation with state persistence
**Plans**: 3 plans

Plans:
- [x] 08-01: Build command with state file
- [x] 08-02: Update command for version refresh
- [x] 08-03: Version validation and security warnings

</details>

<details>
<summary>✅ v1.1 Per-project Runtime Configuration (Phases 9-10) - SHIPPED 2026-01-19</summary>

### Phase 9: Configuration Framework
**Goal**: Per-project runtime config via .aishell/run.conf
**Plans**: 2 plans

Plans:
- [x] 09-01: Whitelist-based config parser
- [x] 09-02: Variable support (MOUNTS, ENV, PORTS, DOCKER_ARGS)

### Phase 10: Pre-start Commands
**Goal**: Background services before shell/harness
**Plans**: 2 plans

Plans:
- [x] 10-01: PRE_START variable implementation
- [x] 10-02: Sidecar service testing and validation

</details>

<details>
<summary>✅ v1.2 Hardening & Edge Cases (Phases 11-12) - SHIPPED 2026-01-19</summary>

### Phase 11: Input Validation
**Goal**: Defense against injection and edge cases
**Plans**: 2 plans

Plans:
- [x] 11-01: Version validation and HOME validation
- [x] 11-02: Security warnings for dangerous DOCKER_ARGS

### Phase 12: Cleanup & Signals
**Goal**: Consolidated cleanup and signal handling
**Plans**: 2 plans

Plans:
- [x] 12-01: Single EXIT handler with tracking arrays
- [x] 12-02: Docker --init for zombie handling

</details>

<details>
<summary>✅ v2.0 Babashka Rewrite (Phases 13-18) - SHIPPED 2026-01-21</summary>

### Phase 13: CLI Foundation
**Goal**: Babashka CLI with command routing
**Plans**: 3 plans

Plans:
- [x] 13-01: CLI namespace with command dispatch
- [x] 13-02: Uberscript compilation and distribution
- [x] 13-03: Pass-through args for harness commands

### Phase 14: State Management
**Goal**: EDN-based state persistence
**Plans**: 2 plans

Plans:
- [x] 14-01: State schema and read/write functions
- [x] 14-02: State migration from v1.2 shell format

### Phase 15: YAML Configuration
**Goal**: .aishell/config.yaml replacing run.conf
**Plans**: 3 plans

Plans:
- [x] 15-01: YAML config schema
- [x] 15-02: Config reading with merge strategy
- [x] 15-03: Config validation

### Phase 16: Docker Integration
**Goal**: Docker build and run in Babashka
**Plans**: 4 plans

Plans:
- [x] 16-01: Dockerfile generation
- [x] 16-02: Docker build command
- [x] 16-03: Docker run command with all flags
- [x] 16-04: Extension detection and caching

### Phase 17: Harness Commands
**Goal**: Shell and harness execution
**Plans**: 3 plans

Plans:
- [x] 17-01: Shell command
- [x] 17-02: Claude Code command
- [x] 17-03: OpenCode command

### Phase 18: Cross-platform Support
**Goal**: Linux and macOS compatibility
**Plans**: 3 plans

Plans:
- [x] 18-01: Platform detection
- [x] 18-02: Path normalization
- [x] 18-03: macOS Docker Desktop compatibility

</details>

<details>
<summary>✅ v2.3.0 Safe AI Context Protection (Phases 18.1-23) - SHIPPED 2026-01-24</summary>

### Phase 18.1: Detection Framework (INSERTED)
**Goal**: Severity-tiered sensitive file detection
**Plans**: 2 plans

Plans:
- [x] 18.1-01: Detection framework with high/medium/low severity
- [x] 18.1-02: Filename-based pattern detection

### Phase 19: Pre-container Checks
**Goal**: Fast filename-based detection before container starts
**Plans**: 2 plans

Plans:
- [x] 19-01: High-severity detection (credentials, SSH keys, secrets)
- [x] 19-02: Medium/low severity detection (package managers, app secrets)

### Phase 20: Gitleaks Integration
**Goal**: Deep content-based secret scanning
**Plans**: 3 plans

Plans:
- [x] 20-01: Gitleaks installation in Docker image
- [x] 20-02: aishell gitleaks command
- [x] 20-03: Scan result formatting and severity mapping

### Phase 21: Staleness Tracking
**Goal**: Nudge users to run Gitleaks periodically
**Plans**: 2 plans

Plans:
- [x] 21-01: XDG state directory for scan timestamps
- [x] 21-02: 7-day staleness warnings before container launch

### Phase 22: Gitignore Awareness
**Goal**: Extra warnings for high-severity files not in .gitignore
**Plans**: 1 plan

Plans:
- [x] 22-01: Gitignore parsing and risk annotation

### Phase 23: Configuration
**Goal**: Custom patterns and allowlist for false positives
**Plans**: 1 plan

Plans:
- [x] 23-01: Custom patterns and allowlist in config.yaml

</details>

<details>
<summary>✅ v2.4.0 Multi-Harness Support (Phases 24-27) - SHIPPED 2026-01-25</summary>

### Phase 24: OpenAI Codex CLI
**Goal**: User can run OpenAI Codex CLI with aishell codex
**Plans**: 2 plans

Plans:
- [x] 24-01: Codex installation with version pinning
- [x] 24-02: Codex command with config mounting

### Phase 25: Google Gemini CLI
**Goal**: User can run Google Gemini CLI with aishell gemini
**Plans**: 2 plans

Plans:
- [x] 25-01: Gemini installation with version pinning
- [x] 25-02: Gemini command with GCP credentials mounting

### Phase 26: CLI Dispatch
**Goal**: Unified harness dispatch with dynamic help
**Plans**: 2 plans

Plans:
- [x] 26-01: Harness command routing
- [x] 26-02: Dynamic help based on installed harnesses

### Phase 27: Documentation Suite
**Goal**: Comprehensive docs for all harnesses
**Plans**: 2 plans

Plans:
- [x] 27-01: Core docs (ARCHITECTURE, CONFIGURATION, HARNESSES)
- [x] 27-02: Support docs (TROUBLESHOOTING, DEVELOPMENT)

</details>

<details>
<summary>✅ v2.5.0 Optimization & Polish (Phases 28-29) - SHIPPED 2026-01-26</summary>

### Phase 28: Build Optimization
**Goal**: Smaller images and faster builds
**Plans**: 2 plans

Plans:
- [x] 28-01: --without-gitleaks flag (~15MB savings)
- [x] 28-02: Dynamic help showing only installed harnesses

### Phase 29: Execution Features
**Goal**: Pre-start list format and one-off execution
**Plans**: 2 plans

Plans:
- [x] 29-01: Pre-start YAML list format (joined with &&)
- [x] 29-02: aishell exec command with TTY auto-detection

</details>

<details>
<summary>✅ v2.7.0 tmux Integration & Named Containers (Phases 30-34) - SHIPPED 2026-01-31</summary>

### Phase 30: Container Naming
**Goal**: Deterministic named containers with project-hash isolation
**Plans**: 2 plans

Plans:
- [x] 30-01: Project hash calculation (8-char SHA-256)
- [x] 30-02: Container naming pattern (aishell-{hash}-{name})

### Phase 31: tmux Integration
**Goal**: tmux auto-start in all container modes
**Plans**: 2 plans

Plans:
- [x] 31-01: tmux installation in base image
- [x] 31-02: TERM validation with xterm-256color fallback

### Phase 32: Detached Mode
**Goal**: Background container execution
**Plans**: 1 plan

Plans:
- [x] 32-01: --detach flag with --rm for auto-cleanup

### Phase 33: Attach Command
**Goal**: Connect to running container's tmux session
**Plans**: 2 plans

Plans:
- [x] 33-01: aishell attach with three-layer validation
- [x] 33-02: --session flag for specific tmux sessions

### Phase 34: PS Command
**Goal**: Project-scoped container discovery
**Plans**: 1 plan

Plans:
- [x] 34-01: aishell ps with table output

</details>

<details>
<summary>✅ v2.8.0 Decouple Harness Tools (Phases 35-38) - SHIPPED 2026-02-01</summary>

### Phase 35: Foundation Split
**Goal**: Stable base image without harness packages
**Plans**: 3 plans

Plans:
- [x] 35-01: Foundation image without npm packages
- [x] 35-02: Volume-based harness installation
- [x] 35-03: Content-hash volume naming

### Phase 36: Volume Population
**Goal**: Lazy harness installation in Docker volumes
**Plans**: 3 plans

Plans:
- [x] 36-01: Volume creation and staleness detection
- [x] 36-02: npm package installation into volume
- [x] 36-03: OpenCode binary installation into volume

### Phase 37: Volume Lifecycle
**Goal**: Update and cleanup commands
**Plans**: 3 plans

Plans:
- [x] 37-01: aishell update refreshes harness volume
- [x] 37-02: aishell volumes list/prune
- [x] 37-03: Orphan detection and safety checks

### Phase 38: Migration
**Goal**: Clean migration from aishell:base
**Plans**: 2 plans

Plans:
- [x] 38-01: Error messages for legacy references
- [x] 38-02: Documentation updates for v2.8.0

</details>

<details>
<summary>✅ v2.9.0 tmux Opt-in & Plugin Support (Phases 39-43) - SHIPPED 2026-02-03</summary>

### Phase 39: tmux Opt-in
**Goal**: tmux made opt-in with --with-tmux build flag
**Plans**: 3 plans

Plans:
- [x] 39-01: --with-tmux build flag and state tracking
- [x] 39-02: Conditional tmux startup in entrypoint
- [x] 39-03: Attach validation for no-tmux mode

### Phase 40: Config Mounting
**Goal**: User's ~/.tmux.conf mounted into containers
**Plans**: 2 plans

Plans:
- [x] 40-01: tmux config mount builder
- [x] 40-02: Graceful handling of missing config

### Phase 41: Plugin Management
**Goal**: TPM + plugins in harness volume
**Plans**: 3 plans

Plans:
- [x] 41-01: config.yaml tmux section with plugin list
- [x] 41-02: TPM installation during volume population
- [x] 41-03: Plugin installation via non-interactive script

### Phase 42: Plugin Runtime
**Goal**: Installed plugins available to tmux
**Plans**: 2 plans

Plans:
- [x] 42-01: Symlink /tools/tmux/plugins to ~/.tmux/plugins
- [x] 42-02: Runtime config generation with TPM initialization

### Phase 43: Session Persistence
**Goal**: tmux-resurrect state persisted to host
**Plans**: 2 plans

Plans:
- [x] 43-01: Resurrect state mount to host volume
- [x] 43-02: Auto-injection of resurrect plugin

</details>

<details>
<summary>✅ v2.10.0 Gitleaks Opt-in (Phases 44-45) - SHIPPED 2026-02-05</summary>

### Phase 44: Gitleaks Opt-in
**Goal**: Gitleaks flipped from opt-out to opt-in
**Plans**: 2 plans

Plans:
- [x] 44-01: Flip flag to --with-gitleaks (opt-in)
- [x] 44-02: Gate staleness warnings on build state

### Phase 45: Documentation
**Goal**: All user-facing CLI changes reflected in docs
**Plans**: 1 plan

Plans:
- [x] 45-01: Update README, CONFIGURATION, TROUBLESHOOTING, ARCHITECTURE

</details>

### 🚧 v3.0.0 Docker-native Attach (In Progress)

**Milestone Goal:** Remove tmux from containers entirely, simplify attach to docker exec, adjust CLI semantics so window management belongs on the host.

#### Phase 46: Foundation Image Cleanup
**Goal**: tmux binary removed from foundation image
**Depends on**: Nothing (starts new milestone)
**Requirements**: TMUX-01
**Success Criteria** (what must be TRUE):
  1. Foundation image builds without tmux package
  2. Foundation image size reduced by tmux removal
  3. `tmux` command unavailable inside containers
**Plans**: 1 plan

Plans:
- [ ] 46-01-PLAN.md — Remove tmux installation from Dockerfile template and verify image build

#### Phase 47: State & Config Schema Cleanup
**Goal**: tmux flags and options removed from aishell's configuration surface
**Depends on**: Phase 46
**Requirements**: TMUX-02, TMUX-03, TMUX-08
**Success Criteria** (what must be TRUE):
  1. `--with-tmux` flag removed from build command
  2. `:with-tmux` and `:tmux-plugins` absent from state schema
  3. `tmux:` section absent from config.yaml schema
  4. Build command help output shows no tmux options
**Plans**: TBD

Plans:
- [ ] 47-01: TBD

#### Phase 48: Docker Run Arguments Cleanup
**Goal**: All tmux-related mounts and environment variables removed from container runtime
**Depends on**: Phase 47
**Requirements**: TMUX-04, TMUX-05, TMUX-06, TMUX-07, TMUX-09
**Success Criteria** (what must be TRUE):
  1. `WITH_TMUX` env var not passed to containers
  2. tmux config not mounted (no ~/.tmux.conf mount)
  3. TPM and plugins not installed in harness volume
  4. Volume hash calculation excludes tmux state
  5. Resurrect mounts removed from docker run
**Plans**: TBD

Plans:
- [ ] 48-01: TBD

#### Phase 49: Entrypoint Simplification
**Goal**: Entrypoint becomes simple exec gosu without tmux conditional logic
**Depends on**: Phase 48
**Requirements**: TMUX-10
**Success Criteria** (what must be TRUE):
  1. Entrypoint script has single code path (no WITH_TMUX conditional)
  2. Harness commands execute directly via gosu (no tmux wrapper)
  3. Shell sessions execute directly via gosu (no tmux session creation)
  4. Container process tree shows harness/shell as PID 1 child (no tmux intermediary)
**Plans**: TBD

Plans:
- [ ] 49-01: TBD

#### Phase 50: Attach Command Rewrite
**Goal**: Attach simplified to docker exec -it bash
**Depends on**: Phase 49
**Requirements**: ATTCH-01, ATTCH-02, ATTCH-03, ATTCH-04
**Success Criteria** (what must be TRUE):
  1. `aishell attach <name>` runs docker exec -it with bash
  2. `--session` and `--shell` flags removed from attach
  3. Attach validates TTY availability before exec
  4. Attach validates container exists and is running
  5. Attach takes single positional argument (container name)
**Plans**: TBD

Plans:
- [ ] 50-01: TBD

#### Phase 51: CLI Semantics Update
**Goal**: Default container naming and detach flag removal
**Depends on**: Phase 50
**Requirements**: CLI-01, CLI-02, CLI-03, CLI-04, CLI-05, CLI-06
**Success Criteria** (what must be TRUE):
  1. `aishell` (no args) creates container named `shell`
  2. `aishell claude` creates container named `claude`
  3. `aishell --name foo` creates container named `foo` running bash
  4. `aishell claude --name bar` creates container named `bar` running Claude Code
  5. Creation commands error if named container already running
  6. `--detach`/`-d` flag removed from CLI (no detached mode)
**Plans**: TBD

Plans:
- [ ] 51-01: TBD

#### Phase 52: Documentation Update
**Goal**: All v3.0.0 CLI changes reflected across documentation
**Depends on**: Phase 51
**Requirements**: DOCS-01
**Success Criteria** (what must be TRUE):
  1. README reflects docker exec attach semantics and new container naming
  2. ARCHITECTURE explains removal of tmux and always-attached model
  3. CONFIGURATION shows no tmux section or flags
  4. HARNESSES documents that harnesses run bare (no tmux)
  5. TROUBLESHOOTING removes tmux-related issues
  6. DEVELOPMENT reflects simplified entrypoint without tmux
**Plans**: TBD

Plans:
- [ ] 52-01: TBD

## Progress

**Execution Order:**
Phases execute in numeric order: 46 → 47 → 48 → 49 → 50 → 51 → 52

| Phase | Milestone | Plans Complete | Status | Completed |
|-------|-----------|----------------|--------|-----------|
| 1-45 | v1.0-v2.10.0 | Complete | Shipped | See milestones |
| 46. Foundation Image Cleanup | v3.0.0 | 0/1 | Not started | - |
| 47. State & Config Schema Cleanup | v3.0.0 | 0/1 | Not started | - |
| 48. Docker Run Arguments Cleanup | v3.0.0 | 0/1 | Not started | - |
| 49. Entrypoint Simplification | v3.0.0 | 0/1 | Not started | - |
| 50. Attach Command Rewrite | v3.0.0 | 0/1 | Not started | - |
| 51. CLI Semantics Update | v3.0.0 | 0/1 | Not started | - |
| 52. Documentation Update | v3.0.0 | 0/1 | Not started | - |
