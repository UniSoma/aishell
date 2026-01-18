# Architecture Patterns

**Domain:** Docker-based sandbox CLI for agentic AI harnesses
**Researched:** 2026-01-17
**Confidence:** HIGH (based on Docker official patterns, multiple real implementations)

## Recommended Architecture

The architecture follows a three-layer pattern common to Docker-based CLI sandbox tools:

```
+------------------+     +-------------------+     +------------------+
|   CLI Wrapper    | --> |   Docker Image    | --> |   Entrypoint     |
|   (shell/bin)    |     |   (Dockerfile)    |     |   (shell script) |
+------------------+     +-------------------+     +------------------+
         |                        |                        |
         v                        v                        v
  Parse args, detect       Base tools +            Configure env,
  project, build if        harness deps           pass-through git,
  needed, docker run                              exec harness/shell
```

### Component Boundaries

| Component | Responsibility | Does NOT Handle |
|-----------|---------------|-----------------|
| **CLI Wrapper** | Parse arguments, detect project context, build extended images if needed, construct `docker run` command | Container internals, harness execution |
| **Base Docker Image** | Provide minimal runtime (git, curl, vim), harness dependencies (node for Claude Code), non-root user setup | Project-specific tools, harness config |
| **Entrypoint Script** | Configure runtime environment, pass-through git identity, mount point validation, exec to harness or shell | Building images, argument parsing |
| **Extension Mechanism** | Allow projects to add tools/deps via `Dockerfile.sandbox` that `FROM`s base image | Replacing core behavior |

### Data Flow

```
Host System                          Container
-----------                          ---------

1. User runs: sandbox claude
         |
         v
2. CLI detects:
   - Current directory ($PWD)
   - Git config (~/.gitconfig)
   - Harness config (~/.claude or ~/.config/opencode)
   - Dockerfile.sandbox (if exists)
         |
         v
3. CLI builds (if Dockerfile.sandbox exists):
   docker build -f Dockerfile.sandbox -t sandbox-extended .
         |
         v
4. CLI runs:
   docker run --rm -it \
     -v $PWD:/workspace \           -----> /workspace (project files)
     -v ~/.gitconfig:/etc/gitconfig -----> /etc/gitconfig (git identity)
     -v ~/.claude:/home/sandbox/.claude -> /home/sandbox/.claude (harness config)
     -e GIT_AUTHOR_NAME=... \       -----> Environment variables
     -e GIT_AUTHOR_EMAIL=... \
     [image] [entrypoint args]
         |
         v
5. Entrypoint:                       -----> Runs as PID 1
   - Validates mounts
   - Sets up shell environment
   - exec claude OR exec /bin/bash   -----> Replaces entrypoint with target
```

### Configuration Passthrough Matrix

| Configuration | Host Location | Container Location | Mount Type |
|---------------|---------------|-------------------|------------|
| Project files | `$PWD` | `/workspace` | Bind mount (rw) |
| Git config | `~/.gitconfig` | `/etc/gitconfig` or `/home/sandbox/.gitconfig` | Bind mount (ro) |
| Claude Code config | `~/.claude` | `/home/sandbox/.claude` | Bind mount (rw) |
| OpenCode config | `~/.config/opencode` | `/home/sandbox/.config/opencode` | Bind mount (rw) |
| SSH keys (optional) | `~/.ssh` | `/home/sandbox/.ssh` | Bind mount (ro) |
| Git credentials | `$SSH_AUTH_SOCK` | `/ssh.socket` + env var | Socket forward |

## Patterns to Follow

### Pattern 1: Single Entrypoint with Exec

**What:** Use a shell script as ENTRYPOINT that performs setup, then `exec` to the target command.

**When:** Always. This ensures proper signal handling and clean container shutdown.

**Example:**
```bash
#!/bin/bash
set -e

# Setup tasks
if [ -n "$GIT_USER_NAME" ]; then
    git config --global user.name "$GIT_USER_NAME"
fi

if [ -n "$GIT_USER_EMAIL" ]; then
    git config --global user.email "$GIT_USER_EMAIL"
fi

# Validate workspace mount
if [ ! -d /workspace ]; then
    echo "Error: /workspace not mounted" >&2
    exit 1
fi

cd /workspace

# Exec to target (becomes PID 1)
case "${1:-shell}" in
    claude)
        shift
        exec claude "$@"
        ;;
    opencode)
        shift
        exec opencode "$@"
        ;;
    shell|bash|"")
        exec /bin/bash
        ;;
    *)
        exec "$@"
        ;;
esac
```

### Pattern 2: Non-Root User with Matching UID

**What:** Create a non-root user in the container with UID matching host user for file permission compatibility.

**When:** Always for development tools that create/modify files in mounted volumes.

**Example:**
```dockerfile
# In Dockerfile
ARG USER_UID=1000
ARG USER_GID=1000

RUN groupadd --gid $USER_GID sandbox \
    && useradd --uid $USER_UID --gid $USER_GID -m sandbox \
    && mkdir -p /home/sandbox/.config \
    && chown -R sandbox:sandbox /home/sandbox

USER sandbox
WORKDIR /workspace
```

**In CLI wrapper:**
```bash
# Pass host UID/GID at build time
docker build --build-arg USER_UID=$(id -u) --build-arg USER_GID=$(id -g) ...
```

### Pattern 3: Conditional Image Building

**What:** Check for `Dockerfile.sandbox` in project root; if present, build extended image; otherwise use base.

**When:** Supporting per-project customization.

**Example:**
```bash
#!/bin/bash

BASE_IMAGE="sandbox:latest"
PROJECT_DOCKERFILE="Dockerfile.sandbox"

if [ -f "$PROJECT_DOCKERFILE" ]; then
    # Hash project Dockerfile for cache-busting
    HASH=$(sha256sum "$PROJECT_DOCKERFILE" | cut -c1-12)
    PROJECT_IMAGE="sandbox-project:${HASH}"

    # Build only if image doesn't exist
    if ! docker image inspect "$PROJECT_IMAGE" &>/dev/null; then
        docker build -f "$PROJECT_DOCKERFILE" -t "$PROJECT_IMAGE" .
    fi

    IMAGE="$PROJECT_IMAGE"
else
    IMAGE="$BASE_IMAGE"
fi

docker run --rm -it ... "$IMAGE" ...
```

### Pattern 4: Path-Mirrored Mounting

**What:** Mount host directory at the same path inside container (or predictable path like `/workspace`).

**When:** For development tools where path consistency matters for error messages and hard-coded paths.

**Example:**
```bash
# Option A: Same path (Docker Sandboxes approach)
docker run -v "$PWD:$PWD" -w "$PWD" ...

# Option B: Predictable path (simpler, recommended)
docker run -v "$PWD:/workspace" -w /workspace ...
```

**Recommendation:** Use `/workspace` for simplicity. Same-path mounting is complex on macOS due to path differences.

### Pattern 5: Git Credential Passthrough via SSH Agent

**What:** Forward SSH agent socket into container for git operations requiring authentication.

**When:** Users need to push/pull from private repos inside container.

**Example:**
```bash
# Check if SSH agent is running
if [ -n "$SSH_AUTH_SOCK" ]; then
    SSH_ARGS="-v $SSH_AUTH_SOCK:/ssh.socket -e SSH_AUTH_SOCK=/ssh.socket"
else
    SSH_ARGS=""
fi

docker run $SSH_ARGS ...
```

## Anti-Patterns to Avoid

### Anti-Pattern 1: Shell Form ENTRYPOINT

**What:** Using `ENTRYPOINT command args` (shell form) instead of `ENTRYPOINT ["command", "args"]` (exec form).

**Why bad:** Shell form runs command under `/bin/sh -c`, breaking signal handling. Container cannot gracefully shutdown.

**Instead:**
```dockerfile
# Bad
ENTRYPOINT /entrypoint.sh

# Good
ENTRYPOINT ["/entrypoint.sh"]
```

### Anti-Pattern 2: Running as Root

**What:** Not creating a non-root user, running container processes as root.

**Why bad:** Files created in mounted volumes owned by root, causing permission issues on host. Security risk.

**Instead:** Create user with matching UID/GID (Pattern 2).

### Anti-Pattern 3: Hardcoded Harness Configs

**What:** Baking harness configuration (API keys, settings) into the Docker image.

**Why bad:** Secrets in image layers. Config changes require rebuild. Multi-user issues.

**Instead:** Mount config directories from host at runtime.

### Anti-Pattern 4: Fat Base Image

**What:** Including language runtimes (Python, Go, Rust) and development tools in base image.

**Why bad:** Large image size, slow pulls, most tools unused for any given project.

**Instead:** Minimal base + extension mechanism (Dockerfile.sandbox). Projects add what they need.

### Anti-Pattern 5: Persistent Named Containers

**What:** Using `docker run --name sandbox-session` without `--rm`, requiring manual cleanup.

**Why bad:** State accumulation, stale containers, complexity in "which container am I using?"

**Instead:** Ephemeral containers with `--rm`. Only mounted volumes persist.

## Suggested Build Order

Based on component dependencies and validation needs:

### Phase 1: Minimal Working Loop

Build order:
1. **Base Dockerfile** - Minimal image with bash, git, curl
2. **Entrypoint script** - Basic version that just exec's to bash
3. **CLI wrapper** - Minimal version that runs container with PWD mounted

**Validation:** Can enter container, see files, git works.

### Phase 2: Harness Integration

Build order:
1. **Extend Dockerfile** - Add Node.js, install Claude Code
2. **Extend entrypoint** - Handle `claude` command, pass arguments
3. **Extend CLI** - Parse subcommands (`sandbox claude`)
4. **Add config mounts** - Mount ~/.claude into container

**Validation:** `sandbox claude` launches Claude Code with project context.

### Phase 3: Git Identity & Credentials

Build order:
1. **Git config passthrough** - Mount .gitconfig, pass env vars
2. **SSH agent forwarding** - Conditional socket mounting
3. **Entrypoint git setup** - Configure git identity on startup

**Validation:** Git commits inside container show correct author. Can push to private repos.

### Phase 4: Extension Mechanism

Build order:
1. **Dockerfile.sandbox detection** - CLI checks for project Dockerfile
2. **Conditional build logic** - Build extended image when found
3. **Image caching** - Hash-based naming to avoid rebuilds

**Validation:** Project with Dockerfile.sandbox gets custom tools available.

### Phase 5: Second Harness (OpenCode)

Build order:
1. **OpenCode installation** - Determine deps, add to Dockerfile
2. **Config mount** - Add ~/.config/opencode mounting
3. **Entrypoint handling** - Add opencode case

**Validation:** `sandbox opencode` launches OpenCode.

## File/Directory Structure

Recommended project structure for the sandbox tool:

```
harness/                          # Repository root
|-- bin/
|   +-- sandbox                   # CLI wrapper script (main entry point)
|
|-- docker/
|   |-- Dockerfile                # Base image definition
|   |-- entrypoint.sh             # Container entrypoint script
|   +-- .dockerignore             # Exclude from build context
|
|-- install.sh                    # Installation script (copies bin/sandbox to PATH)
|-- uninstall.sh                  # Removal script
|-- README.md                     # User documentation
|-- LICENSE                       # License file
|
+-- .planning/                    # GSD planning artifacts
    |-- PROJECT.md
    |-- research/
    +-- roadmap/
```

### Key Files Explained

| File | Purpose |
|------|---------|
| `bin/sandbox` | Main CLI. Parses args, detects context, runs docker. ~100-200 lines of bash. |
| `docker/Dockerfile` | Base image. FROM debian/ubuntu, install minimal tools + harness deps. |
| `docker/entrypoint.sh` | Container startup. Configure env, validate mounts, exec to target. |
| `install.sh` | Installation. Build image, copy CLI to /usr/local/bin or ~/.local/bin. |

### User's Project Structure (After Adoption)

```
user-project/                     # Any user project
|-- src/
|-- package.json
|-- ...
+-- Dockerfile.sandbox            # Optional: extends base image for this project
```

**Dockerfile.sandbox example:**
```dockerfile
FROM sandbox:latest

# Add project-specific tools
RUN apt-get update && apt-get install -y python3 python3-pip
RUN pip3 install pytest black

# Add project-specific harness config (optional)
COPY .claude-settings.json /home/sandbox/.claude/settings.json
```

## Scalability Considerations

| Concern | Single User | Team (10 users) | Large Team (100+ users) |
|---------|-------------|-----------------|------------------------|
| Image distribution | Local build | Docker registry / Dockerfile | Private registry + CI |
| Config management | Host mount | Host mount | Centralized config server? |
| Extension mechanism | Dockerfile.sandbox | Same | Same + shared base images |
| Build time | Acceptable | Cache base image | Pre-built base in registry |

**For v1:** Focus on single-user local build. Team distribution is v2 concern.

## Technology Choices Implied

Based on architecture patterns:

| Component | Choice | Rationale |
|-----------|--------|-----------|
| CLI language | Bash | Universal availability, simple Docker invocation |
| Base image | Debian slim or Ubuntu | Package availability, Claude Code compatibility |
| Node.js version | 20 LTS | Required for Claude Code, LTS stability |
| Container runtime | Docker | Standard, wide compatibility |
| Shell in container | Bash | Better UX than sh, reasonable size |

## Sources

Architecture patterns derived from:

- [Docker Sandboxes Official Documentation](https://docs.docker.com/ai/sandboxes) - HIGH confidence
- [Docker Best Practices: ENTRYPOINT](https://www.docker.com/blog/docker-best-practices-choosing-between-run-cmd-and-entrypoint/) - HIGH confidence
- [ClaudeBox Project](https://github.com/RchGrav/claudebox) - MEDIUM confidence (third-party implementation)
- [Claude Code DevContainer Docs](https://code.claude.com/docs/en/devcontainer) - HIGH confidence
- [VS Code Sharing Git Credentials](https://code.visualstudio.com/remote/advancedcontainers/sharing-git-credentials) - HIGH confidence
- [Docker Volumes Documentation](https://docs.docker.com/engine/storage/volumes) - HIGH confidence
- [Docker Bind Mounts Documentation](https://docs.docker.com/engine/storage/bind-mounts/) - HIGH confidence
- [Multiple Dockerfiles Guide](https://www.divio.com/blog/guide-using-multiple-dockerfiles/) - MEDIUM confidence
