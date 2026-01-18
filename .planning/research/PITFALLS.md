# Domain Pitfalls: Docker Sandbox for Agentic AI Harnesses

**Domain:** Docker-based sandbox environment for AI coding assistants (Claude Code, OpenCode)
**Researched:** 2026-01-17
**Confidence:** HIGH (verified through multiple authoritative sources)

---

## Critical Pitfalls

Mistakes that cause rewrites, data loss, or security vulnerabilities.

---

### Pitfall 1: UID/GID Mismatch — Files Unreadable or Root-Owned on Host

**What goes wrong:** Files created inside the container are owned by root (UID 0) or a mismatched UID, making them unreadable/uneditable on the host without sudo. Projects become polluted with root-owned files that break normal workflows.

**Why it happens:** Docker containers default to running as root. The Linux kernel only recognizes UID/GID numbers — a user with UID 1000 in the container is numerically identical to UID 1000 on the host. When container runs as root but host user is UID 1000, all created files are owned by root from the host's perspective.

**Consequences:**
- `git status` shows permission errors
- IDE cannot save files
- User must repeatedly `sudo chown -R` their project
- Build artifacts locked to root
- Frustrating, unusable DX

**Warning signs:**
- `ls -la` in project shows `root:root` ownership on new files
- Permission denied errors when editing container-created files
- `git diff` shows ownership changes

**Prevention:**
1. **Run container with matching UID/GID** (recommended):
   ```bash
   docker run --user $(id -u):$(id -g) -v "$PWD:/workspace" image
   ```
2. **Docker Compose with dynamic UID/GID**:
   ```yaml
   services:
     sandbox:
       user: "${UID:-1000}:${GID:-1000}"
   ```
3. **Create matching user in Dockerfile** with build args:
   ```dockerfile
   ARG HOST_UID=1000
   ARG HOST_GID=1000
   RUN addgroup -g ${HOST_GID} devgroup && \
       adduser -D -u ${HOST_UID} -G devgroup devuser
   USER devuser
   ```
4. **Never use `chmod 777`** — this is a security anti-pattern masquerading as a fix

**Detection:** Add validation in entrypoint script that checks if container user matches mount ownership.

**Phase to address:** Phase 1 (Core Container) — This is foundational; get it wrong and nothing works.

**Sources:**
- [Docker Mount Permissions Guide](https://eastondev.com/blog/en/posts/dev/20251217-docker-mount-permissions-guide/)
- [Docker Volumes Permission Denied](https://mydeveloperplanet.com/2022/10/19/docker-files-and-volumes-permission-denied/)
- [7 Docker Volume Ownership Fixes](https://medium.com/@Modexa/7-docker-volume-ownership-fixes-uid-gid-the-python-way-23b59e703a83)

---

### Pitfall 2: SSH Agent Forwarding Fails — Git Push/Pull Broken

**What goes wrong:** Users cannot push/pull from private repositories. SSH operations fail with "Permission denied (publickey)" or "Could not open a connection to your authentication agent."

**Why it happens:** SSH agent socket isn't forwarded into container, or socket permissions don't allow container user to access it. The `SSH_AUTH_SOCK` environment variable points to a non-existent or inaccessible path inside the container.

**Consequences:**
- Cannot clone private repos
- Cannot push commits
- Users must copy private keys into container (security risk)
- Workflow broken for any private repo work

**Warning signs:**
- `ssh-add -l` inside container returns "Could not open a connection to your authentication agent"
- `ssh -T git@github.com` fails with permission denied
- Git operations prompt for password despite using SSH URLs

**Prevention:**
1. **Mount SSH agent socket**:
   ```bash
   docker run -v "$SSH_AUTH_SOCK:/ssh-agent" -e SSH_AUTH_SOCK=/ssh-agent image
   ```
2. **Handle macOS Docker Desktop** (socket path differs):
   ```bash
   # macOS uses a different path
   docker run -v /run/host-services/ssh-auth.sock:/ssh-agent \
              -e SSH_AUTH_SOCK=/ssh-agent image
   ```
3. **Verify in entrypoint**:
   ```bash
   if [ -n "$SSH_AUTH_SOCK" ] && [ ! -S "$SSH_AUTH_SOCK" ]; then
     echo "Warning: SSH agent socket not accessible"
   fi
   ```
4. **Test connectivity in container**: `ssh -T git@github.com`

**Detection:** Entrypoint should test SSH agent availability and warn if not working.

**Phase to address:** Phase 2 (Git Integration) — Core to the "git working seamlessly" requirement.

**Sources:**
- [VS Code: Sharing Git Credentials](https://code.visualstudio.com/remote/advancedcontainers/sharing-git-credentials)
- [Docker SSH Agent Forwarding Gist](https://gist.github.com/d11wtq/8699521)

---

### Pitfall 3: GPG Signing Fails Inside Container

**What goes wrong:** Git commits fail with "gpg failed to sign the data" or "fatal: failed to write commit object." Users who sign commits cannot use the sandbox.

**Why it happens:** GPG agent forwarding is complex — more complex than SSH. TTY handling (`updatestartuptty`) for local agents conflicts with forwarded agents. Windows users face additional complexity where GPG keys set in Git Bash aren't accessible from containers.

**Consequences:**
- Users with commit signing requirements cannot use sandbox
- CI/CD pipelines that verify signatures break
- Users may disable signing, weakening security posture

**Warning signs:**
- `git commit` fails with GPG errors
- `gpg --list-keys` shows no keys inside container
- Works on host but fails in container

**Prevention:**
1. **For basic use: mount .gnupg directory** (simple but less secure):
   ```bash
   docker run -v "$HOME/.gnupg:/home/user/.gnupg:ro" image
   ```
2. **For agent forwarding** (complex, platform-dependent):
   - Linux: Forward `$GNUPGHOME/S.gpg-agent` socket
   - macOS: GPG agent forwarding has known issues with Docker
   - Windows: Must configure GPG in Windows GUI, not Git Bash
3. **Document as optional feature** with clear setup instructions
4. **Provide graceful fallback**: Detect if GPG unavailable and warn instead of fail

**Detection:** Check `gpg --list-secret-keys` in entrypoint; warn if empty but user has signing configured.

**Phase to address:** Phase 2 (Git Integration) — Mark as "advanced feature" with known limitations documented.

**Sources:**
- [VS Code DevContainer GPG Issues](https://github.com/microsoft/vscode-remote-release/issues/11379)
- [GPG Agent Forward Docker Mac Issue](https://github.com/docker/for-mac/issues/5297)

---

### Pitfall 4: Mounting ~/.claude Exposes API Keys and Secrets

**What goes wrong:** Mounting the entire `~/.claude` directory gives the container (and any code running in it) access to API keys, authentication tokens, and other sensitive credentials.

**Why it happens:** Convenience wins over security — mounting the whole config directory is simpler than selectively mounting only safe files. The `~/.claude/claude.json` file may contain sensitive secrets.

**Consequences:**
- Malicious or buggy code in project can exfiltrate API keys
- Prompt injection attacks can access credentials
- If container is compromised, attacker has API access
- Shared systems expose credentials to other users

**Warning signs:**
- Config directory mounted without `:ro` flag
- No file permission restrictions on sensitive files
- API keys visible in `docker inspect` output

**Prevention:**
1. **Mount read-only**: `-v "$HOME/.claude:/home/user/.claude:ro"`
2. **Mount only specific files needed**, not entire directory
3. **Use Docker secrets for sensitive data** instead of bind mounts:
   ```yaml
   secrets:
     claude_api_key:
       file: ~/.claude/api_key
   ```
4. **Set restrictive permissions** on host: `chmod 600 ~/.claude/claude.json`
5. **Use `CLAUDE_CONFIG_DIR` environment variable** to control which config is used
6. **Consider credential proxying** — container requests credentials from host process

**Detection:** Warn in documentation; optionally scan mounted configs for obvious secrets.

**Phase to address:** Phase 1 (Core Container) — Security-critical from day one.

**Sources:**
- [Claude Code Security Docs](https://code.claude.com/docs/en/security)
- [Claude Code Automatically Loads .env Secrets](https://www.knostic.ai/blog/claude-loads-secrets-without-permission)
- [Securely Using SSH Keys in Docker](https://www.fastruby.io/blog/docker/docker-ssh-keys.html)

---

### Pitfall 5: macOS Volume Mount Performance Degrades DX

**What goes wrong:** File operations become painfully slow — 3-6x slower than native. npm install, file watchers, and IDE operations become frustrating. The "quick sandbox" becomes a productivity killer.

**Why it happens:** Unlike Linux (where bind mounts have minimal overhead), macOS Docker requires translation between host and container filesystems. Every file access crosses the VM boundary through VirtioFS (current) or osxfs (legacy).

**Consequences:**
- `npm install` takes minutes instead of seconds
- File watchers (webpack, vite) have noticeable lag
- IDE responsiveness suffers
- Users abandon sandbox due to poor DX

**Warning signs:**
- Operations noticeably slower than host
- High CPU usage in Docker Desktop
- File watcher delays > 1 second

**Prevention:**
1. **Use volumes for heavy directories** (node_modules, vendor, etc.):
   ```yaml
   volumes:
     - .:/workspace
     - node_modules:/workspace/node_modules  # Named volume, not bind mount
   ```
2. **Consider consistency flags** (traded write consistency for speed):
   ```bash
   -v "$PWD:/workspace:delegated"  # Deprecated but still works
   ```
3. **Document OrbStack as alternative** — significantly faster than Docker Desktop
4. **Hybrid approach**: Bind mount source, volume mount dependencies
5. **For heavy workloads, suggest Lima or cloud dev environments**

**Detection:** Benchmark file operations in entrypoint; warn if abnormally slow.

**Phase to address:** Phase 3 (Polish/Performance) — Works without this, but DX suffers.

**Sources:**
- [Docker on MacOS is still slow? (2025)](https://www.paolomainardi.com/posts/docker-performance-macos-2025/)
- [Docker on MacOS and How to Fix It](https://www.cncf.io/blog/2023/02/02/docker-on-macos-is-slow-and-how-to-fix-it/)
- [Docker Performance Tuning Docs](https://docker-docs.uclv.cu/docker-for-mac/osxfs-caching/)

---

## Moderate Pitfalls

Mistakes that cause delays, confusion, or technical debt.

---

### Pitfall 6: Git Identity Not Set — Commits Fail

**What goes wrong:** Git commits fail with "Please tell me who you are" error. User must manually configure git inside every container session.

**Why it happens:** Container doesn't have access to host's `.gitconfig`. Git looks for identity in global config which doesn't exist in fresh container.

**Prevention:**
1. **Mount .gitconfig read-only**:
   ```bash
   -v "$HOME/.gitconfig:/home/user/.gitconfig:ro"
   ```
2. **Copy git identity in entrypoint** from environment variables:
   ```bash
   if [ -n "$GIT_AUTHOR_NAME" ]; then
     git config --global user.name "$GIT_AUTHOR_NAME"
   fi
   ```
3. **Passthrough GIT_AUTHOR_* and GIT_COMMITTER_* environment variables**

**Phase to address:** Phase 2 (Git Integration)

**Sources:**
- [VS Code: Sharing Git Credentials](https://code.visualstudio.com/remote/advancedcontainers/sharing-git-credentials)
- [.gitconfig inside container issue](https://github.com/microsoft/vscode-remote-release/issues/5218)

---

### Pitfall 7: "Dubious Ownership" Git Errors

**What goes wrong:** Git refuses to operate with "fatal: detected dubious ownership in repository." Security feature blocks normal workflow.

**Why it happens:** CVE-2022-24765 mitigation. Git now checks if repo owner matches current user. With bind mounts, container user often differs from the UID that owns the files on host.

**Prevention:**
1. **Add safe.directory in entrypoint**:
   ```bash
   git config --global --add safe.directory /workspace
   ```
2. **Match container user to host user UID** (solves root cause)
3. **Use `postStartCommand` in devcontainer.json** if using VS Code

**Phase to address:** Phase 2 (Git Integration) — Common friction point.

**Sources:**
- [Avoiding Dubious Ownership in Dev Containers](https://www.kenmuse.com/blog/avoiding-dubious-ownership-in-dev-containers/)
- [Git safe.directory in Docker Containers](https://github.com/actions/runner/issues/2033)

---

### Pitfall 8: Entrypoint Breaks Signal Handling — Container Won't Stop

**What goes wrong:** `docker stop` takes 10 seconds (timeout) instead of stopping gracefully. Container doesn't respond to Ctrl+C. Data may be lost due to ungraceful shutdown.

**Why it happens:** Using shell form instead of exec form in ENTRYPOINT. Shell becomes PID 1 and doesn't forward SIGTERM to the actual process. Or entrypoint script doesn't use `exec` to hand off to main process.

**Prevention:**
1. **Use exec form for ENTRYPOINT**:
   ```dockerfile
   ENTRYPOINT ["/entrypoint.sh"]
   CMD ["bash"]
   ```
2. **Use `exec` in shell scripts**:
   ```bash
   #!/bin/bash
   # Setup code here...
   exec "$@"  # Replace shell with CMD
   ```
3. **Or use init process**:
   ```bash
   docker run --init image  # Uses tini
   ```

**Phase to address:** Phase 1 (Core Container) — Affects basic usability.

**Sources:**
- [PID 1 Signal Handling in Docker](https://petermalmgren.com/signal-handling-docker/)
- [Docker Signals Article](https://hynek.me/articles/docker-signals/)
- [Docker ENTRYPOINT Best Practices](https://www.docker.com/blog/docker-best-practices-choosing-between-run-cmd-and-entrypoint/)

---

### Pitfall 9: Bind Mount Obscures Container Files

**What goes wrong:** Files that exist in the image at the mount point become invisible. Generated files, default configs, or bootstrap data disappear when bind mount is applied.

**Why it happens:** Bind mounts overlay the container directory. Pre-existing container files are "obscured" — they still exist but are inaccessible while the mount is in place.

**Prevention:**
1. **Generate/copy files in entrypoint** (after mounts are in place)
2. **Use volumes for container-managed directories** (node_modules, etc.)
3. **Document clearly**: "Files in X location will be hidden by your project mount"
4. **Bootstrap pattern**: Copy defaults from image to mount if not present

**Phase to address:** Phase 1 (Core Container)

**Sources:**
- [Docker Bind Mounts Documentation](https://docs.docker.com/engine/storage/bind-mounts/)
- [Docker bind mount deleting container files](https://forums.docker.com/t/docker-bind-mount-is-deleting-containers-files/135541)

---

### Pitfall 10: TTY Allocation Errors in Non-Interactive Contexts

**What goes wrong:** "The input device is not a TTY" error when running in scripts, CI, or piped commands. Container fails to start or behaves unexpectedly.

**Why it happens:** Using `-t` (allocate TTY) when no TTY is available (CI/CD, cron, piped input). The container expects an interactive terminal that doesn't exist.

**Prevention:**
1. **Detect TTY availability**:
   ```bash
   if [ -t 0 ]; then
     docker run -it image
   else
     docker run -i image
   fi
   ```
2. **Make `-t` optional** in wrapper script
3. **Document interactive vs non-interactive usage**

**Phase to address:** Phase 1 (Core Container) — Wrapper script must handle this.

**Sources:**
- [Docker TTY Error Fix](https://devops-daily.com/posts/docker-tty-error-fix)
- [Interactive and TTY Options in Docker](https://www.baeldung.com/linux/docker-run-interactive-tty-options)

---

### Pitfall 11: Container Timezone Mismatch Confuses Users

**What goes wrong:** Timestamps in logs, file modifications, and git commits show wrong times. UTC default doesn't match user's local time.

**Why it happens:** Containers default to UTC. No timezone data packages installed in minimal images. TZ environment variable alone isn't sufficient without tzdata.

**Prevention:**
1. **Pass TZ environment variable**: `-e TZ=$(cat /etc/timezone 2>/dev/null || echo UTC)`
2. **Mount host timezone files** (Linux only):
   ```bash
   -v /etc/localtime:/etc/localtime:ro
   -v /etc/timezone:/etc/timezone:ro
   ```
3. **Install tzdata in Dockerfile** if setting TZ via ENV
4. **Document that timezone inherits from host** or is configurable

**Phase to address:** Phase 3 (Polish) — Nice to have, not critical.

**Sources:**
- [Fix Docker Time and Timezone Problems](https://hoa.ro/blog/2020-12-08-draft-docker-time-timezone/)
- [How to Handle Timezones in Docker](https://www.howtogeek.com/devops/how-to-handle-timezones-in-docker-containers/)

---

### Pitfall 12: DNS Resolution Fails for localhost or Custom Domains

**What goes wrong:** Container can't resolve `host.docker.internal` or custom DNS entries. Services on host unreachable. Corporate VPN DNS doesn't work.

**Why it happens:** Docker's DNS handling differs by platform. Custom DNS in daemon.json can break `host.docker.internal`. Systems using 127.0.0.1 as resolver (Ubuntu with dnsmasq) cause Docker to fall back to 8.8.8.8.

**Prevention:**
1. **Use `host.docker.internal`** for host connectivity (not localhost)
2. **For Linux, add host entry** if `host.docker.internal` doesn't resolve:
   ```bash
   --add-host host.docker.internal:host-gateway
   ```
3. **Document network requirements** for different platforms
4. **Test DNS in entrypoint** if critical services depend on it

**Phase to address:** Phase 3 (Polish) — Edge case but frustrating when hit.

**Sources:**
- [Support host.docker.internal on Linux](https://github.com/docker/for-linux/issues/264)
- [Solving DNS Resolution Issues](https://www.magetop.com/blog/solving-dns-resolution-issues-inside-docker-containers/)

---

### Pitfall 13: Resource Limits Cause Claude Code to Fail

**What goes wrong:** Claude Code hangs, crashes, or performs poorly. Container appears to work but AI assistant doesn't function correctly.

**Why it happens:** Default Docker memory limits (2GB) are insufficient for Claude Code. Memory-intensive operations fail silently or OOM.

**Prevention:**
1. **Set adequate memory limits**: At least 4GB, recommend 8GB+
   ```bash
   docker run --memory=8g image
   ```
2. **Document minimum resource requirements** clearly
3. **Add memory check to entrypoint** with warning if below threshold

**Phase to address:** Phase 1 (Core Container) — Essential for core functionality.

**Sources:**
- [Running Claude Code in Devcontainers](https://www.solberg.is/claude-devcontainer)
- [Claude Code Docker Guide](https://smartscope.blog/en/generative-ai/claude/claude-code-docker-guide/)

---

## Minor Pitfalls

Mistakes that cause annoyance but are easily fixable.

---

### Pitfall 14: Dockerfile Layer Caching Defeated by Poor Instruction Order

**What goes wrong:** Every change causes full rebuild. Build times frustrate development iteration.

**Why it happens:** COPY . before RUN npm install means any source change invalidates dependency install layer. ARG values that change frequently placed too early.

**Prevention:**
1. **Order instructions from least to most frequently changed**:
   ```dockerfile
   COPY package*.json ./
   RUN npm install
   COPY . .  # Source code last
   ```
2. **Use .dockerignore** to exclude node_modules, .git, etc.
3. **Place frequently-changing ARGs near the end**

**Phase to address:** Phase 1 (Core Container) — Build efficiency.

**Sources:**
- [Docker Build Cache Documentation](https://docs.docker.com/build/cache/)
- [Ultimate Guide to Docker Build Cache](https://depot.dev/blog/ultimate-guide-to-docker-build-cache)

---

### Pitfall 15: Environment Variables Leak Secrets in Logs/Inspect

**What goes wrong:** API keys or tokens visible in `docker inspect`, process listings, or debug logs. Secrets exposed unintentionally.

**Why it happens:** Passing secrets via `-e SECRET=value` makes them visible to anyone who can inspect the container or view ps output.

**Prevention:**
1. **Use Docker secrets** for sensitive values (file-mounted, not env vars)
2. **Use env file** instead of CLI args: `--env-file .env`
3. **Never log environment in entrypoint** for debugging
4. **For Claude API key**: Use CLAUDE_CONFIG_DIR pointing to mounted secrets file

**Phase to address:** Phase 1 (Core Container) — Security hygiene.

**Sources:**
- [Docker Secrets Documentation](https://docs.docker.com/engine/swarm/secrets/)
- [4 Ways to Securely Store Secrets in Docker](https://blog.gitguardian.com/how-to-handle-secrets-in-docker/)

---

### Pitfall 16: Dockerfile.sandbox Not Found or Not Applied

**What goes wrong:** User creates `Dockerfile.sandbox` for per-project extension but it has no effect. Custom setup ignored.

**Why it happens:** File not in expected location, not correctly named, or build process doesn't look for it.

**Prevention:**
1. **Document exact filename and location** expected
2. **Provide clear error message** if file exists but can't be processed
3. **Log when Dockerfile.sandbox is detected and applied**
4. **Validate Dockerfile syntax** before attempting build

**Phase to address:** Phase 2 or 3 (Per-project extensions)

---

## Phase-Specific Warnings

| Phase Topic | Likely Pitfall | Mitigation |
|-------------|----------------|------------|
| Core Container | UID/GID mismatch | Run container with --user matching host |
| Core Container | Signal handling | Use exec form, test docker stop |
| Core Container | Memory limits | Default to 4GB+, document requirements |
| Git Integration | SSH agent not forwarded | Platform-specific socket mounting |
| Git Integration | GPG signing fails | Document as optional, graceful fallback |
| Git Integration | Dubious ownership | Add safe.directory automatically |
| macOS Support | Volume performance | Use named volumes for node_modules |
| Security | Config/secret exposure | Mount read-only, use secrets not env vars |
| DX Polish | Timezone mismatch | Pass TZ from host |
| DX Polish | TTY errors in scripts | Detect TTY availability |

---

## Summary: Top 5 Pitfalls to Solve First

1. **UID/GID Mismatch** — Without this, files are unusable. Solve in Phase 1.
2. **SSH Agent Forwarding** — Without this, git push/pull fails. Solve in Phase 2.
3. **Signal Handling** — Without this, containers won't stop cleanly. Solve in Phase 1.
4. **Config Security** — Without this, API keys at risk. Solve in Phase 1.
5. **macOS Performance** — Without this, Mac users have poor DX. Document in Phase 1, optimize in Phase 3.

---

## Sources Summary

### Official Documentation
- [Docker Bind Mounts](https://docs.docker.com/engine/storage/bind-mounts/)
- [Docker Build Cache](https://docs.docker.com/build/cache/)
- [Docker Secrets](https://docs.docker.com/engine/swarm/secrets/)
- [Docker Security](https://docs.docker.com/engine/security/)

### Claude-Specific
- [Claude Code Security](https://code.claude.com/docs/en/security)
- [Claude Code DevContainer Docs](https://code.claude.com/docs/en/devcontainer)
- [Claude Code in Devcontainers (Solberg)](https://www.solberg.is/claude-devcontainer)

### Community Resources
- [VS Code Remote Containers](https://code.visualstudio.com/remote/advancedcontainers/sharing-git-credentials)
- [Docker macOS Performance 2025](https://www.paolomainardi.com/posts/docker-performance-macos-2025/)
- [PID 1 Signal Handling](https://petermalmgren.com/signal-handling-docker/)
- [Docker Mount Permissions Guide](https://eastondev.com/blog/en/posts/dev/20251217-docker-mount-permissions-guide/)
