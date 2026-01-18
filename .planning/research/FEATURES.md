# Feature Landscape

**Domain:** Docker-based sandbox for agentic AI harnesses (Claude Code, OpenCode)
**Researched:** 2026-01-17
**Confidence:** HIGH (verified against Docker Sandboxes, Claude Code devcontainer, DevPod documentation)

## Table Stakes

Features users expect. Missing = product feels incomplete.

| Feature | Why Expected | Complexity | Notes |
|---------|--------------|------------|-------|
| Project directory mounting | Core functionality - AI agent needs code access | Low | Mount at identical absolute path for script/error message compatibility |
| Git user/email passthrough | Commits must be attributed correctly | Low | Auto-discover from host `~/.gitconfig` and inject into container |
| Ephemeral containers | Clean state prevents cross-project contamination | Low | Destroyed on exit by default |
| Single command entry | CLI simplicity is core value proposition | Low | `harness` drops to shell, `harness claude` runs agent directly |
| Basic CLI tools | Agents and developers need standard tooling | Low | git, curl, vim minimum; add ripgrep, jq, gh for modern workflows |
| Non-root user with sudo | Security + practical package installation | Low | Run as UID 1000 with passwordless sudo |
| Exit preserves host state | Changes sync back to host project | Low | Bind mount ensures real-time synchronization |

## Git Integration (Special Research)

Git integration is critical for AI coding agents that create commits, branches, and PRs.

### Required Features (Table Stakes)

| Feature | Why Required | Implementation | Complexity |
|---------|--------------|----------------|------------|
| Git config passthrough | User name/email for commits | Copy `~/.gitconfig` or inject `user.name`/`user.email` | Low |
| SSH agent forwarding | Push/pull via SSH | Forward `SSH_AUTH_SOCK` socket into container | Medium |
| HTTPS credential helper | Push/pull via HTTPS | Forward credential helper or mount credential cache | Medium |

### Optional Features (Differentiators)

| Feature | Value | Implementation | Complexity |
|---------|-------|----------------|------------|
| GPG signing passthrough | Signed commits from container | Mount GPG socket, install gnupg2, configure forwarding | High |
| Git credential manager integration | Seamless auth with GitHub/GitLab | Use git-credential-forwarder or native helper tunneling | Medium |
| GitHub CLI authentication | PR creation, issue management | Forward `gh` auth token or use credential helper | Low |

### Implementation Notes

**SSH Agent Forwarding:**
- Linux: Mount `$SSH_AUTH_SOCK` directly via bind mount
- macOS Docker Desktop: Use magic path `/run/host-services/ssh-auth.sock`
- Windows: Requires SSH agent service running in Administrator mode

**Git Credential Helpers:**
- VS Code devcontainers automatically forward credential helpers
- Standalone Docker requires explicit socket forwarding or credential injection
- DevPod provides automatic credential helper tunneling

**GPG Signing:**
- Most complex to implement correctly
- Requires gnupg2 in container, GPG agent socket forwarding
- Windows has additional path resolution issues
- Consider making this optional/advanced feature

## Mount Parameterization (Special Research)

Users need to mount additional host paths beyond the project directory.

### Use Cases

| Use Case | Typical Paths | Risk Level |
|----------|---------------|------------|
| Shared libraries | `~/.local/lib`, `/usr/local/lib` | Low |
| Package caches | `~/.npm`, `~/.cargo`, `~/.m2` | Low |
| Configuration | `~/.aws`, `~/.kube`, `~/.docker` | Medium (credentials) |
| Secrets | `~/.ssh`, `~/.gnupg` | High (sensitive) |
| Data directories | `/data`, `~/datasets` | Low |

### Implementation Options

| Approach | Pros | Cons | Recommendation |
|----------|------|------|----------------|
| CLI flags (`--mount`) | Explicit, one-off | Verbose for repeated use | Support for ad-hoc mounts |
| Config file (`.harness.yml`) | Project-specific, version-controlled | Requires file management | Primary configuration method |
| Environment variable | Quick override | Not persistent | Support as override |
| `Dockerfile.sandbox` extension | Full customization | Requires Docker knowledge | Already in scope |

### Recommended Approach

```yaml
# .harness.yml (project root)
mounts:
  - source: ~/.npm
    target: /home/user/.npm
    readonly: false
  - source: ~/.aws
    target: /home/user/.aws
    readonly: true  # credentials should be read-only
```

**CLI override:** `harness --mount ~/.cargo:/home/user/.cargo claude`

### Security Considerations

| Path Type | Default Behavior | Rationale |
|-----------|------------------|-----------|
| Sensitive paths (`~/.ssh`, `~/.gnupg`) | Require explicit opt-in | Prevent accidental credential exposure |
| System paths (`/etc`, `/var`) | Block entirely | No legitimate use case, high risk |
| Home directory (`~`) | Block full mount | Too broad, encourage specific paths |
| Project subdirectories | Allow | Normal development workflow |

## Network Access (Special Research)

Network configuration balances agent functionality with security isolation.

### Options Comparison

| Mode | Description | Use Cases | Security |
|------|-------------|-----------|----------|
| **Full access** (default bridge) | Container can reach any network | General development, API calls | Low isolation |
| **Host-only** | Access host network only | Local services, databases | Medium isolation |
| **Allowlist** | Whitelist specific domains | Production-like constraints | High isolation |
| **No network** | Complete isolation | Offline development, max security | Maximum isolation |

### Recommended Default: Allowlist Mode

Based on Claude Code devcontainer patterns:

**Default allowlist:**
- npm/yarn/pnpm registries (package installation)
- GitHub/GitLab/Bitbucket (git operations)
- AI provider APIs (Anthropic, OpenAI)
- DNS resolution

**Blocked by default:**
- Arbitrary outbound connections
- Local network scanning
- Cloud metadata endpoints (169.254.169.254)

### Implementation

```bash
# Firewall script approach (Claude Code devcontainer pattern)
iptables -A OUTPUT -d api.anthropic.com -j ACCEPT
iptables -A OUTPUT -d github.com -j ACCEPT
iptables -A OUTPUT -d registry.npmjs.org -j ACCEPT
# ... other allowed hosts
iptables -A OUTPUT -j DROP  # Default deny
```

### Configuration Options

| Level | Approach | Target User |
|-------|----------|-------------|
| Preset: `--network full` | Full access | Trusted environments |
| Preset: `--network restricted` | Allowlist (default) | Standard development |
| Preset: `--network none` | No network | Maximum isolation |
| Custom: `--allow-host api.example.com` | Add to allowlist | Custom API access |

## Differentiators

Features that set product apart. Not expected, but valued.

| Feature | Value Proposition | Complexity | Notes |
|---------|-------------------|------------|-------|
| Multiple harness support | One tool for Claude Code, OpenCode, future agents | Medium | Harness detection/configuration system |
| Project-specific extensions (`Dockerfile.sandbox`) | Custom dependencies per project | Medium | Already in user requirements |
| Persistent config volumes | Preserve Claude/OpenCode settings across sessions | Low | Named volumes for `~/.claude`, `~/.opencode` |
| Session persistence option | Continue previous session | Medium | Named containers, `--persist` flag |
| Pre-configured harness templates | Quick start with recommended settings | Low | `docker/sandbox-templates` pattern |
| Network allowlist customization | Project-specific network policies | Medium | Config file or CLI flags |
| Cache mount optimization | Faster subsequent runs | Low | Mount npm/cargo/pip caches |
| Host toolchain access | Use host-installed tools if needed | High | Complex, potential security implications |
| Resource limits | CPU/memory constraints | Low | `--cpus`, `--memory` flags |
| Background mode | Run agent headless | Low | `--detach` with log access |

## Anti-Features

Features to explicitly NOT build. Common mistakes in this domain.

| Anti-Feature | Why Avoid | What to Do Instead |
|--------------|-----------|-------------------|
| Full home directory mount | Exposes all user data to agent | Mount specific paths only |
| Docker socket passthrough (default) | Container escape risk, security vulnerability | Require explicit opt-in with warnings |
| Privileged mode | Defeats isolation purpose | Use minimal capabilities |
| Root user default | Security anti-pattern | Non-root with sudo access |
| Automatic credential discovery | Silent exposure of sensitive data | Explicit credential configuration |
| Complex multi-container orchestration | Scope creep, maintenance burden | Single container simplicity |
| GUI/desktop integration | Out of scope for CLI tool | Focus on terminal workflows |
| Built-in editor | Redundant with vim, users have preferences | Include vim, let users add more |
| Cloud deployment features | Different product category | Focus on local development |
| Persistent containers by default | Clean state is the value proposition | Ephemeral default, opt-in persistence |
| MCP server hosting | Scope creep into orchestration | Let users configure if needed |
| Automatic updates inside container | Reproducibility issues | Version-pinned images |

## Feature Dependencies

```
Core (must implement first):
  Project mounting ─────────────────────┐
  Git config passthrough ───────────────┼─> Basic functional sandbox
  Ephemeral containers ─────────────────┤
  Single command entry ─────────────────┘

Git Integration (second priority):
  SSH agent forwarding ─────────────────┐
  HTTPS credential helper ──────────────┼─> Full git workflow support
  (depends on: Core)                    │
                                        │
  GPG signing passthrough ──────────────┘
  (optional, high complexity)

Configuration (third priority):
  Mount parameterization ───────────────┐
  Network allowlist ────────────────────┼─> Customizable sandbox
  Config file support ──────────────────┘
  (depends on: Core)

Extensions (fourth priority):
  Dockerfile.sandbox ───────────────────┐
  Persistent config volumes ────────────┼─> Project customization
  Cache mount optimization ─────────────┘
  (depends on: Configuration)
```

## MVP Recommendation

For MVP, prioritize:

1. **Project mounting with path preservation** - Core value proposition
2. **Git user/email passthrough** - Commits work correctly
3. **SSH agent forwarding** - Push/pull via SSH works
4. **Ephemeral containers** - Clean state guarantee
5. **Single command entry** - `harness`, `harness claude`, `harness opencode`
6. **Basic CLI tools** - git, curl, vim, ripgrep, jq

Defer to post-MVP:
- GPG signing: High complexity, niche use case
- Network allowlists: Default full access is acceptable for local dev
- Mount parameterization config file: CLI flags sufficient initially
- Session persistence: Ephemeral is the core value
- Background mode: Interactive is primary use case

## Complexity Assessment

| Feature Category | Estimated Effort | Risk |
|------------------|------------------|------|
| Core sandbox (mount, git config, ephemeral) | 1-2 days | Low |
| SSH agent forwarding | 1 day | Medium (platform differences) |
| HTTPS credential helpers | 1 day | Medium (helper variety) |
| CLI structure | 1 day | Low |
| Base image with tools | 0.5 days | Low |
| Mount parameterization (CLI) | 0.5 days | Low |
| Config file support | 1 day | Low |
| Network isolation | 1-2 days | Medium (iptables complexity) |
| Dockerfile.sandbox extension | 0.5 days | Low |
| GPG signing | 2-3 days | High (cross-platform issues) |

## Sources

### HIGH Confidence (Official Documentation)
- [Docker Sandboxes Documentation](https://docs.docker.com/ai/sandboxes) - Official Docker approach
- [Claude Code Devcontainer Documentation](https://code.claude.com/docs/en/devcontainer) - Anthropic's reference implementation
- [VS Code Sharing Git Credentials](https://code.visualstudio.com/remote/advancedcontainers/sharing-git-credentials) - Git integration patterns
- [Dev Container JSON Reference](https://containers.dev/implementors/json_reference/) - Mount configuration spec
- [Docker Bind Mounts Documentation](https://docs.docker.com/engine/storage/bind-mounts/) - Mount security best practices
- [Anthropic Devcontainer Features](https://github.com/anthropics/devcontainer-features) - Official Claude Code feature

### MEDIUM Confidence (Verified Community Sources)
- [DevPod Credentials Documentation](https://devpod.sh/docs/developing-in-workspaces/credentials) - Credential forwarding patterns
- [Docker Security Best Practices](https://docs.docker.com/engine/security/) - Container isolation guidance
- [Docker Enhanced Container Isolation](https://docs.docker.com/enterprise/security/hardened-desktop/enhanced-container-isolation/) - Advanced security features

### LOW Confidence (Community Patterns, Needs Validation)
- [Agent Infra Sandbox](https://github.com/agent-infra/sandbox) - All-in-one sandbox reference
- Various Medium articles on AI agent sandboxing - Community patterns
