# aishell Troubleshooting

This guide helps diagnose and resolve common issues with aishell.

**How to use this guide:** Find the symptom you're experiencing, then follow the resolution steps.

**Last updated:** v2.8.0

---

## Table of Contents

- [Quick Diagnostics](#quick-diagnostics)
- [Build Issues](#build-issues)
- [Container Issues](#container-issues)
- [Volume Issues](#volume-issues)
- [Authentication Issues](#authentication-issues)
- [Sensitive File Detection](#sensitive-file-detection)
- [Detached Mode & Attach Issues](#detached-mode--attach-issues)
- [Exec Command Issues](#exec-command-issues)
- [Network Issues](#network-issues)
- [Getting Help](#getting-help)

---

## Quick Diagnostics

Before troubleshooting, gather diagnostic information:

```bash
# Check aishell version
aishell --version

# Check Docker is running
docker info

# Check for existing images
docker images | grep aishell

# View current state
cat ~/.aishell/state.edn

# View project config (if in project directory)
cat .aishell/config.yaml
```

---

## Build Issues

### Symptom: "Docker not found" or "Cannot connect to Docker daemon"

**Cause:** Docker is not installed or the Docker daemon is not running.

**Resolution:**

1. **Install Docker** if not present:
   - Linux: https://docs.docker.com/engine/install/
   - macOS: Install Docker Desktop

2. **Start Docker daemon:**
   ```bash
   # Linux (systemd)
   sudo systemctl start docker
   sudo systemctl enable docker  # Start on boot

   # macOS
   # Open Docker Desktop application
   ```

3. **Verify Docker is running:**
   ```bash
   docker info
   ```

### Symptom: "Permission denied" when running build

**Cause:** Your user doesn't have permission to access the Docker socket.

**Resolution:**

**Option 1: Add user to docker group (recommended)**
```bash
# Add your user to docker group
sudo usermod -aG docker $USER

# Log out and back in for group membership to take effect
# Or start a new shell session:
newgrp docker

# Verify you can run docker without sudo
docker ps
```

**Option 2: Use sudo**
```bash
sudo aishell build --with-claude
```

**Note:** After adding yourself to the docker group, you may need to reboot for the change to fully take effect on some systems.

### Symptom: "Image build fails" with Docker syntax errors

**Cause:** Custom `.aishell/Dockerfile` has invalid syntax or references missing resources.

**Resolution:**

1. **Check Dockerfile syntax:**
   ```bash
   cat .aishell/Dockerfile
   ```

2. **Common issues:**
   - Missing `FROM aishell:base` as first instruction
   - Typos in RUN commands
   - Missing backslashes in multi-line commands
   - Incorrect package names

3. **Test manually:**
   ```bash
   # Build just the base image first
   aishell build --with-claude

   # Then try your custom Dockerfile
   docker build -f .aishell/Dockerfile -t test .
   ```

4. **Simplify to isolate problem:**
   Temporarily rename `.aishell/Dockerfile` and rebuild to verify base image works.

### Symptom: "Version not found" for harness

**Cause:** Specified harness version doesn't exist in npm registry.

**Resolution:**

1. **Check available versions:**
   ```bash
   # For Claude Code
   npm view @anthropic-ai/claude-code versions

   # For Codex CLI
   npm view @openai/codex versions

   # For Gemini CLI
   npm view @google/gemini-cli versions
   ```

2. **Use exact version from npm:**
   ```bash
   aishell build --with-claude=2.0.22
   ```

3. **Omit version to use latest:**
   ```bash
   aishell build --with-claude
   ```

---

## Container Issues

### Symptom: "Container exits immediately" after launch

**Cause:** Pre-start command fails or is misconfigured.

**Resolution:**

1. **Check pre_start command in config.yaml:**
   ```yaml
   pre_start: "redis-server --daemonize yes"
   ```

2. **Common issues:**
   - Command not found (service not installed in image)
   - Command doesn't daemonize (runs in foreground and blocks)
   - Command fails silently

3. **Debug the pre_start command:**
   ```bash
   # Enter shell to test command manually
   aishell

   # Run the pre_start command directly
   redis-server --daemonize yes

   # Check if it succeeded
   ps aux | grep redis
   ```

4. **View container logs:**
   ```bash
   # Find container ID
   docker ps -a | grep aishell

   # View logs
   docker logs <container-id>
   ```

5. **Temporarily disable pre_start:**
   Comment out the pre_start line in `.aishell/config.yaml` to verify it's the cause.

### Symptom: "File not found" errors inside container

**Cause:** Expected files aren't mounted or mount paths are incorrect.

**Resolution:**

1. **Verify mount configuration in `.aishell/config.yaml`:**
   ```yaml
   mounts:
     - /absolute/path/to/data
     - source: $HOME/.secrets
       target: /secrets
   ```

2. **Common issues:**
   - Relative paths in source (must be absolute)
   - Typo in source path
   - Source path doesn't exist on host
   - Environment variables not expanded ($HOME)

3. **Check mounts from inside container:**
   ```bash
   aishell
   mount | grep /home
   ls -la /path/you/expect
   ```

4. **Verify source exists on host:**
   ```bash
   ls -la ~/.secrets  # Before entering container
   ```

5. **Check syntax:**
   - Short form: `- /path/to/mount` (mounted at same path in container)
   - Long form: `source:` and `target:` on separate lines

### Symptom: "Permission denied" on mounted files

**Cause:** UID/GID mismatch between host user and container user.

**Resolution:**

1. **Check file ownership on host:**
   ```bash
   ls -ln /path/to/mounted/file
   # Note the UID and GID (numeric IDs)
   ```

2. **Check user inside container:**
   ```bash
   aishell
   id
   # Compare UID/GID with host
   ```

3. **If UIDs differ, options:**

   **Option A: Change file permissions on host**
   ```bash
   # Make files group-writable
   chmod g+w /path/to/mounted/file

   # Add container user to same group (if UIDs match)
   # This is automatic in aishell - container runs as your UID
   ```

   **Option B: Use bind mount with permissions**
   In `.aishell/Dockerfile`:
   ```dockerfile
   FROM aishell:base
   USER root
   RUN chown -R dev:dev /path/in/container
   USER dev
   ```

4. **Verify aishell runs as your UID:**
   aishell automatically runs containers with your host UID/GID to avoid permission issues.

### Symptom: "git safe.directory" warnings

**Cause:** Git security feature (CVE-2022-24765) prevents operations on directories owned by different users.

**Why this happens:**
- Inside the container, mounted directories appear owned by a different user
- Git requires explicit trust via `safe.directory` configuration

**Resolution:**

**aishell handles this automatically.** The entrypoint script adds your project to `safe.directory` in the container's gitconfig.

If you still see warnings:

1. **Check if you're mounting host gitconfig:**
   ```yaml
   # In .aishell/config.yaml
   mounts:
     - source: ~/.gitconfig
       target: /home/dev/.gitconfig
   ```

   If you mount host gitconfig, the safe.directory entry will be written to your **host** file.

2. **To avoid modifying host gitconfig:**
   Don't mount `~/.gitconfig`. The container creates its own gitconfig (discarded when container exits).

3. **Manual override (if needed):**
   ```bash
   aishell
   git config --global --add safe.directory /your/project/path
   ```

**Note:** This is a security feature, not a bug. The warning is expected and handled by aishell's entrypoint.

---

## Volume Issues

### Symptom: "Harness command not found" inside container

**Cause:** Harness volume is missing or not populated.

**Resolution:**

1. **Check if volume exists:**
   ```bash
   aishell volumes
   ```

2. **If no volumes or volume missing:**
   ```bash
   # Rebuild to create and populate volume
   aishell build --with-claude
   ```

3. **If volume exists but harness not working:**
   ```bash
   # Delete and repopulate volume
   aishell update
   ```

4. **Verify PATH inside container:**
   ```bash
   aishell
   echo $PATH
   # Should include /tools/npm/bin and /tools/bin

   ls -la /tools/npm/bin
   # Should show harness binaries (claude, codex, gemini)
   ```

5. **Check volume mount:**
   ```bash
   # From inside container
   mount | grep /tools
   # Should show: aishell-harness-{hash} on /tools type ext4 (ro,...)
   ```

**If harness still not found after rebuild:**
Check build output for npm installation errors.

### Symptom: "Volume disk usage growing" - many orphaned volumes

**Cause:** Changing harness configuration creates new volumes, old ones remain.

**Resolution:**

1. **List volumes:**
   ```bash
   aishell volumes
   # Shows active vs orphaned volumes
   ```

2. **Prune orphaned volumes:**
   ```bash
   aishell volumes prune
   # Confirms before deletion

   # Or auto-confirm:
   aishell volumes prune --yes
   ```

3. **Verify disk space recovered:**
   ```bash
   docker system df
   ```

**When volumes become orphaned:**
- Changed harness selection (`--with-claude` → `--with-opencode`)
- Changed harness versions (version pin updated)
- Rebuilt with different harness combination

**Safe to prune:**
Only removes volumes not referenced by current state. Active volume is never deleted.

### Symptom: "Update seems to hang" during volume population

**Cause:** Volume population downloads npm packages, may be slow on first run or slow network.

**Resolution:**

1. **Check if actually hung or just slow:**
   ```bash
   # In another terminal, check Docker container activity
   docker ps
   # Should show a temporary container running
   ```

2. **Use --verbose to see progress:**
   ```bash
   aishell update --verbose
   # Shows npm install output in real-time
   ```

3. **Network issues:**
   - Check internet connection
   - npm registry may be slow/unavailable
   - Try again later

4. **Abort and retry:**
   ```bash
   # Ctrl+C to abort
   # Try again with verbose output
   aishell update --verbose
   ```

**Expected duration:**
- First population: 30-60 seconds (downloads npm packages)
- Subsequent updates: 20-40 seconds (npm cache helps)
- Slow on: slow network, large harness packages

### Symptom: "tmux new-window missing tools" (harness commands not in PATH)

**Cause:** Fixed in v2.8.0 via `/etc/profile.d/aishell.sh`. If still occurring, foundation image needs rebuild.

**Resolution:**

1. **Rebuild foundation image:**
   ```bash
   aishell update --force
   ```

2. **Verify profile.d script exists:**
   ```bash
   aishell
   cat /etc/profile.d/aishell.sh
   # Should exist and set PATH
   ```

3. **Test in tmux new-window:**
   ```bash
   aishell
   # Inside container in main tmux session:
   tmux new-window
   # In new window:
   echo $PATH
   # Should include /tools/npm/bin
   which claude
   # Should find /tools/npm/bin/claude
   ```

**If still broken:**
File a bug report with tmux version and SHELL variable.

### Symptom: "Legacy FROM aishell:base error" in custom Dockerfile

**Cause:** v2.8.0 renamed `aishell:base` to `aishell:foundation`. Custom `.aishell/Dockerfile` needs update.

**Resolution:**

1. **Edit `.aishell/Dockerfile`:**
   ```dockerfile
   # Change this:
   FROM aishell:base

   # To this:
   FROM aishell:foundation
   ```

2. **Rebuild:**
   ```bash
   aishell build --with-claude
   ```

**Migration detection:**
aishell detects legacy `FROM aishell:base` and shows clear error message with migration instructions.

**Backward compatibility:**
Only the tag name changed. Functionality identical.

---

## Authentication Issues

### Symptom: "Claude: API key not working" or "Anthropic API error"

**Cause:** `ANTHROPIC_API_KEY` environment variable is not set, incorrect, or expired.

**Resolution:**

1. **Check if variable is set on host:**
   ```bash
   echo $ANTHROPIC_API_KEY
   ```

2. **If empty, set it:**
   ```bash
   export ANTHROPIC_API_KEY="sk-ant-..."
   ```

3. **Verify key is valid:**
   ```bash
   curl https://api.anthropic.com/v1/messages \
     -H "x-api-key: $ANTHROPIC_API_KEY" \
     -H "anthropic-version: 2023-06-01" \
     -H "content-type: application/json" \
     -d '{"model":"claude-3-5-sonnet-20241022","max_tokens":10,"messages":[{"role":"user","content":"Hi"}]}'
   ```

4. **If using OAuth instead:**
   ```bash
   # Don't set ANTHROPIC_API_KEY
   # Just run claude and follow OAuth prompts
   aishell claude
   ```

5. **Check credentials persist:**
   ```bash
   ls -la ~/.claude/
   # Should see config/credentials files
   ```

### Symptom: "Codex: Login fails" or "Device code expired"

**Cause:** OAuth device code flow has issues or API key not set.

**Resolution:**

**Option 1: Use device code auth (recommended for containers)**
```bash
aishell codex
# Select "Sign in with ChatGPT"
# Choose "Use device code" when prompted

# Or directly:
codex login --device-auth
```

This displays a code and URL. Open the URL in your browser, enter the code.

**Option 2: Use API key**
```bash
export OPENAI_API_KEY="sk-..."
aishell codex
```

**Common issues:**
- Device code expires after 15 minutes - be quick
- Network issues can interrupt OAuth flow
- `CODEX_API_KEY` only works with `codex exec`, not interactive mode

**Verify authentication:**
```bash
aishell codex
codex whoami
```

### Symptom: "Gemini: OAuth fails in container"

**Cause:** Gemini CLI doesn't support device code flow for headless authentication.

**Resolution:**

**Option 1: Authenticate on host first (recommended)**
```bash
# On your host machine (outside container)
gemini  # Select "Login with Google"

# Credentials are stored in ~/.gemini
# aishell mounts this automatically
aishell gemini
```

**Option 2: Use API key**
```bash
export GEMINI_API_KEY="..."
# or
export GOOGLE_API_KEY="..."

aishell gemini
```

**Verify credentials mounted:**
```bash
aishell
ls -la ~/.gemini/
```

### Symptom: "Credentials not persisting" between sessions

**Cause:** Config directories aren't being mounted or are getting wiped.

**Resolution:**

1. **Check which directories should exist:**
   - Claude Code: `~/.claude`
   - Codex CLI: `~/.codex`
   - Gemini CLI: `~/.gemini`
   - OpenCode: `~/.config/opencode`, `~/.local/share/opencode`

2. **Verify directories exist on host:**
   ```bash
   ls -la ~/.claude
   ls -la ~/.codex
   ls -la ~/.gemini
   ```

3. **Check they're being mounted:**
   ```bash
   aishell
   mount | grep -E '\.claude|\.codex|\.gemini'
   ```

4. **If not mounted, check for conflicts:**
   Custom mounts in `.aishell/config.yaml` may be overriding defaults.

5. **Verify permissions:**
   ```bash
   ls -la ~/.claude
   # Should be writable by your user
   ```

### Symptom: "Permission denied (publickey)" when using git over SSH

**Cause:** SSH keys are not mounted in the container. By default, aishell does NOT mount `~/.ssh`.

**Resolution:**

**Recommended: Use HTTPS instead of SSH for git operations:**

```bash
# Instead of SSH remotes
git@github.com:user/repo.git

# Use HTTPS with token
https://github.com/user/repo.git
```

Set up token authentication:
```yaml
# In .aishell/config.yaml
env:
  GH_TOKEN:           # GitHub - passthrough from host
  GITLAB_TOKEN:       # GitLab - passthrough from host
```

**Alternative: Mount SSH keys (with security considerations):**

⚠️ **Security warning:** Mounting SSH keys gives AI harnesses access to your private keys. They can execute arbitrary code and could use your keys to authenticate anywhere.

If you understand the risks and still need SSH:

```yaml
# In .aishell/config.yaml
mounts:
  # Safer: mount only a specific deploy key
  - ~/.ssh/id_ed25519_deploy:~/.ssh/id_ed25519:ro
  - ~/.ssh/known_hosts:~/.ssh/known_hosts:ro

  # Less safe: mount entire .ssh directory
  # - ~/.ssh:~/.ssh:ro
```

See [CONFIGURATION.md - SSH Keys Security Note](CONFIGURATION.md#mounts) for detailed guidance.

---

## Sensitive File Detection

### Symptom: "False positive on test file" in detection warnings

**Cause:** Test fixtures or example files match sensitive file patterns.

**Resolution:**

Add to `.aishell/config.yaml` allowlist:

```yaml
detection:
  allowlist:
    - path: "test/fixtures/fake-key.pem"
      reason: "Test fixture, not a real key"

    - path: "examples/.env.example"
      reason: "Template file, no real credentials"
```

**Pattern matching:**
- Exact path: `test/fixtures/fake-key.pem`
- Glob pattern: `test/**/*.pem`
- Directory: `test/fixtures/`

After adding to allowlist:
```bash
aishell claude  # No warning for allowlisted files
```

### Symptom: "Custom pattern not matching" files I want to detect

**Cause:** Incorrect glob syntax or pattern not being applied.

**Resolution:**

1. **Add custom pattern in `.aishell/config.yaml`:**
   ```yaml
   detection:
     custom_patterns:
       "*.secret": high
       "internal-*.json":
         severity: medium
   ```

2. **Glob syntax:**
   - `*` matches any characters in filename
   - `**` matches directories recursively
   - `?` matches single character
   - `[abc]` matches any character in set

3. **Verify pattern syntax:**
   ```bash
   # Test with find
   find . -name "*.secret"
   ```

4. **Check config is loaded:**
   ```bash
   cat .aishell/config.yaml
   ```

5. **Severity levels:** `high`, `medium`, `low`

### Symptom: "Gitleaks scan takes too long" on large repositories

**Cause:** Gitleaks is scanning large binary files or generated directories.

**Resolution:**

1. **Exclude directories via `.gitleaksignore`:**
   Create `.gitleaksignore` in project root:
   ```
   node_modules/
   vendor/
   dist/
   build/
   *.min.js
   ```

2. **Use `.gitignore`:**
   Gitleaks respects `.gitignore` by default.

3. **Run with `--no-git` for faster scan:**
   ```bash
   aishell gitleaks detect --no-git
   ```

4. **Scan specific paths:**
   ```bash
   aishell gitleaks detect --source src/
   ```

5. **Disable freshness warnings:**
   In `.aishell/config.yaml`:
   ```yaml
   detection:
     gitleaks_freshness_check: false
   ```

---

## Detached Mode & Attach Issues

### Symptom: "Container name already in use" when starting detached

**Cause:** A container with the same name is already running.

**Resolution:**

1. **Check running containers:**
   ```bash
   aishell ps
   ```

2. **Attach to existing container:**
   ```bash
   aishell attach --name claude
   ```

3. **Stop and restart:**
   ```bash
   docker stop aishell-<hash>-claude
   aishell claude --detach
   ```

**Note:** If the container is stopped (not running), aishell auto-removes it and starts a new one.

### Symptom: "attach: container not found" or "container is not running"

**Cause:** The container has exited or the name is wrong.

**Resolution:**

1. **List project containers:**
   ```bash
   aishell ps
   ```

2. **Check Docker directly:**
   ```bash
   docker ps -a | grep aishell
   ```

3. **Common issues:**
   - Container stopped (exited): Restart with `aishell claude --detach`
   - Wrong name: Use `aishell ps` to see actual container names
   - Wrong project directory: Container names are project-scoped

### Symptom: "tmux: open terminal failed: not a terminal"

**Cause:** Running `aishell attach` from a non-interactive terminal (e.g., script, pipe, CI).

**Resolution:**

The attach command requires an interactive terminal. Run from a terminal emulator, not from scripts or pipes.

### Symptom: tmux fails with "open terminal failed: missing or unsuitable terminal"

**Cause:** Your terminal's `TERM` value (e.g., `xterm-ghostty`) doesn't have a terminfo entry in the container.

**Resolution:**

aishell automatically validates TERM and falls back to `xterm-256color` for unsupported values. If you still see this:

1. **Rebuild the image** to get the TERM validation fix:
   ```bash
   aishell update
   ```

2. **Manual override:**
   ```bash
   TERM=xterm-256color aishell claude
   ```

### Symptom: "aishell ps" shows no containers

**Cause:** No containers running for the current project, or you're in a different directory.

**Resolution:**

1. **Verify you're in the right project directory:**
   Container names are scoped by project path hash. Running `aishell ps` from a different directory shows different containers.

2. **Check Docker directly:**
   ```bash
   docker ps | grep aishell
   ```

3. **Start a container:**
   ```bash
   aishell claude --detach
   aishell ps
   ```

---

## Exec Command Issues

### "the input device is not a TTY"

**Symptom:** Running `aishell exec` in a script or CI produces this Docker error.

**Cause:** This should not happen - aishell auto-detects TTY and omits the `-t` flag when stdin is not a terminal.

**Resolution:**
1. Ensure you're using the latest version of aishell
2. If issue persists, report it as a bug

### Piped input not working

**Symptom:** `echo "test" | aishell exec cat` produces no output.

**Cause:** TTY detection may be incorrectly identifying stdin as a terminal.

**Solution:**
1. Verify the pipe is correctly formed
2. Try: `echo "test" | aishell exec sh -c 'cat'`

### Exit code not propagated

**Symptom:** `aishell exec false; echo $?` returns 0 instead of 1.

**Cause:** Exit code propagation issue.

**Solution:**
1. Check that you're using the command correctly: `aishell exec <command>`
2. Verify with: `aishell exec sh -c 'exit 42'; echo $?` (should show 42)

### Command not found in container

**Symptom:** `aishell exec mycommand` fails with "command not found".

**Cause:** The command is not installed in the base image or your project extension.

**Solution:**
1. Use full path: `aishell exec /usr/bin/mycommand`
2. Add the tool to your `.aishell/Dockerfile` extension
3. Run interactive shell to debug: `aishell` then check `which mycommand`

---

## Network Issues

### Symptom: "Port not accessible" from host

**Cause:** Port forwarding is misconfigured or not specified.

**Resolution:**

1. **Add port forwarding in `.aishell/config.yaml`:**
   ```yaml
   ports:
     - "3000:3000"      # host:container
     - "8080:80"
   ```

2. **Verify format:**
   - `"HOST_PORT:CONTAINER_PORT"`
   - Both must be strings (quoted)
   - Multiple ports as list items

3. **Check port is listening in container:**
   ```bash
   aishell
   netstat -tlnp | grep 3000
   ```

4. **Check from host:**
   ```bash
   curl http://localhost:3000
   ```

5. **Conflicts:**
   Ensure host port isn't already in use:
   ```bash
   sudo netstat -tlnp | grep 3000
   ```

### Symptom: "Cannot reach external services" from container

**Cause:** Docker network configuration or DNS issues.

**Resolution:**

1. **Test basic connectivity:**
   ```bash
   aishell
   ping 8.8.8.8         # Test IP connectivity
   ping google.com      # Test DNS resolution
   curl https://api.anthropic.com/v1/health
   ```

2. **If IP works but DNS doesn't:**
   Docker DNS issue. Restart Docker daemon:
   ```bash
   sudo systemctl restart docker
   ```

3. **Check Docker network mode:**
   ```bash
   docker inspect <container-id> | grep NetworkMode
   ```

4. **Corporate firewall/proxy:**
   Set proxy in `.aishell/config.yaml`:
   ```yaml
   env:
     HTTP_PROXY: http://proxy.company.com:8080
     HTTPS_PROXY: http://proxy.company.com:8080
     NO_PROXY: localhost,127.0.0.1
   ```

5. **VPN conflicts:**
   Some VPNs break Docker networking. Disconnect VPN temporarily to test.

---

## Getting Help

If you've tried the above and are still stuck:

### Before Reporting a Bug

Gather this information:

```bash
# aishell version
aishell --version

# Docker version
docker version

# State file
cat ~/.aishell/state.edn

# Config (if relevant)
cat .aishell/config.yaml

# Recent container logs
docker ps -a | grep aishell
docker logs <container-id>
```

### Where to Get Help

- **GitHub Issues:** https://github.com/UniSoma/aishell/issues
- **Bug Report Template:**
  - aishell version
  - Docker version
  - Operating system
  - Command you ran
  - Expected behavior
  - Actual behavior (include error output)
  - Contents of state.edn
  - Contents of config.yaml (if relevant)

### Common Mistake Checklist

Before filing an issue, verify:

- [ ] Docker daemon is running
- [ ] Your user is in the docker group (or using sudo)
- [ ] You've built an image with `aishell build --with-<harness>`
- [ ] Config files use absolute paths, not relative
- [ ] Environment variables are set on host before running aishell
- [ ] You're in the correct directory (if using project-specific config)
- [ ] Config YAML syntax is valid (use `yamllint` or online validator)

---

**Quick Tips:**

- **"Try turning it off and on again"** - Rebuild the image: `aishell update`
- **"Is it plugged in?"** - Check Docker is running: `docker info`
- **"Clear the cache"** - Remove old images: `docker image prune`
- **"Read the error message"** - Error output often contains the solution
