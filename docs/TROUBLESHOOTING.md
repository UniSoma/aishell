# aishell Troubleshooting

Find the symptom you are experiencing, then follow the resolution steps.

**Last updated:** v3.1.0

---

## Table of Contents

- [Quick Diagnostics](#quick-diagnostics)
- [Setup Issues](#setup-issues)
- [Container Issues](#container-issues)
- [Volume Issues](#volume-issues)
- [Authentication Issues](#authentication-issues)
- [Sensitive File Detection](#sensitive-file-detection)
- [Attach Issues](#attach-issues)
- [Exec Command Issues](#exec-command-issues)
- [Network Issues](#network-issues)
- [Windows-Specific Issues](#windows-specific-issues)
- [Getting Help](#getting-help)

---

## Quick Diagnostics

Before troubleshooting, gather this diagnostic information:

**Unix/macOS/Linux:**
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

**Windows (PowerShell):**
```powershell
# Check aishell version
aishell --version

# Check Docker is running
docker info

# Check for existing images
docker images | Select-String "aishell"

# View current state
Get-Content "$env:LOCALAPPDATA\aishell\state.edn"

# View project config (if in project directory)
Get-Content .aishell\config.yaml
```

---

## Setup Issues

### Symptom: "Docker not found" or "Cannot connect to Docker daemon"

**Cause:** Docker is not installed, or the Docker daemon is not running.

**Resolution:**

1. **Install Docker** if not present:
   - Linux: https://docs.docker.com/engine/install/
   - macOS: Install Docker Desktop
   - Windows: Install Docker Desktop from https://www.docker.com/products/docker-desktop/

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

### Symptom: "Permission denied" when running setup

**Cause:** Your user lacks permission to access the Docker socket.

**Resolution:**

**Option 1: Add user to docker group (recommended)**
```bash
# Add your user to docker group
sudo usermod -aG docker $USER

# Log out and back in for the group change to take effect,
# or start a new shell session:
newgrp docker

# Verify you can run docker without sudo
docker ps
```

**Option 2: Use sudo**
```bash
sudo aishell setup --with-claude
```

**Note:** Some systems require a reboot before the docker group change takes effect.

### Symptom: "Image setup fails" with Docker syntax errors

**Cause:** Custom `.aishell/Dockerfile` has invalid syntax or references a missing resource.

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
   aishell setup --with-claude

   # Then try your custom Dockerfile
   docker build -f .aishell/Dockerfile -t test .
   ```

4. **Isolate the problem:**
   Temporarily rename `.aishell/Dockerfile` and rebuild to verify the base image works.

### Symptom: "Version not found" for harness

**Cause:** The specified harness version does not exist in the npm registry.

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
   aishell setup --with-claude=2.0.22
   ```

3. **Omit version to use latest:**
   ```bash
   aishell setup --with-claude
   ```

---

## Container Issues

### Symptom: "Container exits immediately" after launch

**Cause:** The pre-start command fails or is misconfigured.

**Resolution:**

1. **Check pre_start command in config.yaml:**
   ```yaml
   pre_start: "redis-server --daemonize yes"
   ```

2. **Common issues:**
   - Command not found (service not installed in the image)
   - Command runs in foreground and blocks instead of daemonizing
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

**Cause:** Expected files are not mounted, or the mount paths are wrong.

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
   - Source path does not exist on host
   - Environment variables not expanded (`$HOME`)

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

**Cause:** Git's security feature (CVE-2022-24765) blocks operations on directories owned by a different user. Inside the container, mounted directories appear owned by another user, so Git requires explicit trust via `safe.directory`.

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

   Mounting host gitconfig causes the safe.directory entry to be written to your **host** file.

2. **To avoid modifying host gitconfig:**
   Omit the `~/.gitconfig` mount. The container creates its own gitconfig, discarded when the container exits.

3. **Manual override (if needed):**
   ```bash
   aishell
   git config --global --add safe.directory /your/project/path
   ```

**Note:** This is a security feature, not a bug. The aishell entrypoint handles this warning automatically.

---

## Volume Issues

### Symptom: "Harness command not found" inside container

**Cause:** The harness volume is missing or empty.

**Resolution:**

1. **Check if volume exists:**
   ```bash
   aishell volumes
   ```

2. **If the volume is missing:**
   ```bash
   # Rebuild to create and populate volume
   aishell setup --with-claude
   ```

3. **If the volume exists but the harness still fails:**
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

**If the harness is still missing after a rebuild:**
Check the build output for npm installation errors.

### Symptom: "Volume disk usage growing" - many orphaned volumes

**Cause:** Changing harness configuration creates new volumes; old ones remain.

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

**Volumes become orphaned when you:**
- Change harness selection (`--with-claude` to `--with-opencode`)
- Change harness versions (update a version pin)
- Rebuild with a different harness combination

**Safe to prune:**
Pruning removes only volumes not referenced by current state. The active volume is never deleted.

### Symptom: "Update seems to hang" during volume population

**Cause:** Volume population downloads npm packages and may be slow on the first run or on a slow network.

**Resolution:**

1. **Check whether it is hung or just slow:**
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
   - Check your internet connection
   - The npm registry may be slow or unavailable
   - Retry later

4. **Abort and retry:**
   ```bash
   # Ctrl+C to abort
   # Try again with verbose output
   aishell update --verbose
   ```

**Expected duration:**
- First population: 30-60 seconds (downloads npm packages)
- Subsequent updates: 20-40 seconds (npm cache helps)
- Slower on slow networks or with large harness packages

### Symptom: "Legacy FROM aishell:base error" in custom Dockerfile

**Cause:** v2.8.0 renamed `aishell:base` to `aishell:foundation`. Update your custom `.aishell/Dockerfile`.

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
   aishell setup --with-claude
   ```

**Migration detection:**
aishell detects `FROM aishell:base` and shows an error with migration instructions.

**Backward compatibility:**
Only the tag name changed. Functionality is identical.

---

## Authentication Issues

### Symptom: "Claude: API key not working" or "Anthropic API error"

**Cause:** `ANTHROPIC_API_KEY` is unset, incorrect, or expired.

**Resolution:**

1. **Check whether the variable is set on the host:**
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

**Cause:** The OAuth device code flow failed, or the API key is not set.

**Resolution:**

**Option 1: Use device code auth (recommended for containers)**
```bash
aishell codex
# Select "Sign in with ChatGPT"
# Choose "Use device code" when prompted

# Or directly:
codex login --device-auth
```

This displays a code and a URL. Open the URL in your browser and enter the code.

**Option 2: Use API key**
```bash
export OPENAI_API_KEY="sk-..."
aishell codex
```

**Common issues:**
- The device code expires after 15 minutes
- Network interruptions can break the OAuth flow
- `CODEX_API_KEY` only works with `codex exec`, not interactive mode

**Verify authentication:**
```bash
aishell codex
codex whoami
```

### Symptom: "Gemini: OAuth fails in container"

**Cause:** Gemini CLI lacks device code flow for headless authentication.

**Resolution:**

**Option 1: Authenticate on host first (recommended)**
```bash
# On your host machine (outside container)
gemini  # Select "Login with Google"

# Credentials stored in ~/.gemini
# aishell mounts this directory automatically
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

**Cause:** Config directories are not mounted, or they are wiped between sessions.

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

3. **Check they are mounted:**
   ```bash
   aishell
   mount | grep -E '\.claude|\.codex|\.gemini'
   ```

4. **If not mounted, check for conflicts:**
   Custom mounts in `.aishell/config.yaml` may override the defaults.

5. **Verify permissions:**
   ```bash
   ls -la ~/.claude
   # Should be writable by your user
   ```

### Symptom: "Permission denied (publickey)" when using git over SSH

**Cause:** SSH keys are not mounted in the container. By default, aishell does not mount `~/.ssh`.

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

**Security warning:** Mounting SSH keys gives AI harnesses access to your private keys. They can execute arbitrary code and could use your keys to authenticate anywhere.

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

**Cause:** Test fixtures or example files match the sensitive file patterns.

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

**Cause:** Incorrect glob syntax, or the pattern is not applied.

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

4. **Verify config is loaded:**
   ```bash
   cat .aishell/config.yaml
   ```

5. **Severity levels:** `high`, `medium`, `low`

### Symptom: "gitleaks" command not found or missing from `aishell --help`

**Cause:** Gitleaks is opt-in and was not installed during build. By default, aishell does NOT install Gitleaks.

**Resolution:**

1. **Rebuild with Gitleaks:**
   ```bash
   aishell setup --with-claude --with-gitleaks
   ```

2. **Verify installation:**
   ```bash
   aishell
   gitleaks version
   # Should show: gitleaks version
   ```

3. **Check build state:**
   ```bash
   cat ~/.aishell/state.edn
   # Should show :with-gitleaks true
   ```

4. **Alternative:** If you don't need deep content scanning, aishell's built-in filename-based sensitive file detection works without Gitleaks. No action needed.

### Symptom: "Gitleaks scan takes too long" on large repositories

**Note:** This section applies only when Gitleaks is installed (built with `--with-gitleaks`). If you don't have Gitleaks installed, see the section above.

**Cause:** Gitleaks scans large binary files or generated directories.

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

## Attach Issues

### Symptom: "Container name already in use" when starting container

**Cause:** A container with that name is already running.

**Resolution:**

1. **Check running containers:**
   ```bash
   aishell ps
   ```

2. **Attach to existing container:**
   ```bash
   aishell attach claude
   ```

3. **Stop and restart:**
   ```bash
   docker stop aishell-<hash>-claude
   aishell claude
   ```

**Note:** If the container is stopped, aishell removes it and starts a new one.

### Symptom: "attach: container not found" or "container is not running"

**Cause:** The container has exited, or the name is wrong.

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
   - Container exited: restart with `aishell claude`
   - Wrong name: run `aishell ps` to see actual container names
   - Wrong directory: container names are project-scoped

### Symptom: "aishell ps" shows no containers

**Cause:** No containers are running for the current project, or you are in a different directory.

**Resolution:**

1. **Verify you are in the correct project directory:**
   Container names are scoped by project path hash. Running `aishell ps` from a different directory shows different containers.

2. **Check Docker directly:**
   ```bash
   docker ps | grep aishell
   ```

3. **Start a container:**
   ```bash
   aishell claude --name background-task
   aishell ps
   ```

---

## Exec Command Issues

### "the input device is not a TTY"

**Symptom:** `aishell exec` in a script or CI produces this Docker error.

**Cause:** This should not happen. aishell detects TTY and omits the `-t` flag when stdin is not a terminal.

**Resolution:**
1. Ensure you are using the latest version of aishell
2. If issue persists, report it as a bug

### Piped input not working

**Symptom:** `echo "test" | aishell exec cat` produces no output.

**Cause:** TTY detection may incorrectly identify stdin as a terminal.

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

**Cause:** The command is not installed in the base image or in your project extension.

**Solution:**
1. Use full path: `aishell exec /usr/bin/mycommand`
2. Add the tool to your `.aishell/Dockerfile` extension
3. Run interactive shell to debug: `aishell` then check `which mycommand`

---

## Network Issues

### Symptom: "Port not accessible" from host

**Cause:** Port forwarding is misconfigured or unspecified.

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
   Ensure the host port is not already in use:
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
   Some VPNs break Docker networking. Disconnect the VPN temporarily to test.

---

## Windows-Specific Issues

### Symptom: "Docker daemon not running" on Windows

**Cause:** Docker Desktop not started or WSL2 backend not enabled.

**Resolution:**

1. **Verify Docker Desktop is running:**
   - Check system tray for Docker Desktop icon
   - Open Docker Desktop if not running

2. **Verify WSL2 backend enabled:**
   ```powershell
   docker info | Select-String "OSType"
   # Should show: OSType: linux
   ```

3. **Enable WSL2 backend if needed:**
   - Open Docker Desktop → Settings → General
   - Check "Use the WSL2 based engine"
   - Click "Apply & Restart"

4. **Verify WSL2 installation:**
   ```powershell
   wsl --list --verbose
   # Should show WSL2 distribution
   ```

   If no WSL2 installed:
   ```powershell
   wsl --install
   # Restart computer after installation
   ```

### Symptom: VSCode Dev Container can't find Docker on WSL2 (no Docker Desktop)

**Cause:** Docker is installed natively inside WSL2 (e.g. `apt install docker.io`) instead of through Docker Desktop. VSCode's Dev Containers extension defaults to looking for Docker on the Windows side, but there is no Docker Desktop exposing it.

**Resolution:**

1. **Add this to VSCode settings (`settings.json`):**
   ```json
   {
     "dev.containers.executeInWSL": true
   }
   ```

   This tells the Dev Containers extension to run Docker commands through WSL2 instead of looking for Docker on Windows.

2. **Verify Docker is accessible from WSL2:**
   ```bash
   # Inside WSL2
   docker info
   ```

**Note:** This only affects users running a standalone Docker Engine inside WSL2. If you have Docker Desktop installed, it exposes Docker to both Windows and WSL2 automatically — no extra configuration needed.

### Symptom: "No such file or directory" when mounting Windows path

**Cause:** Path format not compatible with Docker on Windows.

**Resolution:**

1. **Use forward slashes in config:**
   ```yaml
   # Incorrect - backslashes not supported
   mounts:
     - C:\Users\name\.ssh

   # Correct - forward slashes
   mounts:
     - C:/Users/name/.ssh

   # Better - use tilde expansion (works on all platforms)
   mounts:
     - ~/.ssh
   ```

2. **Avoid mixed separators:**
   ```yaml
   # Incorrect
   mounts:
     - C:/Users\name/project

   # Correct
   mounts:
     - C:/Users/name/project
   ```

3. **Test with absolute path:**
   ```powershell
   # Verify path exists
   Test-Path "C:/Users/name/.ssh"
   ```

4. **Use tilde expansion for portability:**
   aishell automatically expands `~` to `C:/Users/username` on Windows.

### Symptom: "aishell.bat not recognized" in cmd.exe

**Cause:** aishell.bat not in PATH or has incorrect line endings.

**Resolution:**

1. **Verify PATH includes aishell directory:**
   ```cmd
   echo %PATH%
   # Should include directory containing aishell.bat
   ```

2. **Test direct invocation:**
   ```cmd
   C:\path\to\aishell.bat --version
   ```

3. **Verify file has CRLF line endings:**
   aishell.bat requires Windows-style CRLF line endings. If you see "command not recognized" despite being in PATH:
   ```powershell
   # Re-download from GitHub Release (ensures correct line endings)
   Invoke-WebRequest -Uri https://github.com/UniSoma/aishell/releases/latest/download/aishell.bat -OutFile aishell.bat
   ```

4. **Add to PATH permanently:**
   ```powershell
   $installDir = "C:\path\to\aishell"
   [Environment]::SetEnvironmentVariable("Path", $env:Path + ";$installDir", [System.EnvironmentVariableTarget]::User)
   # Restart terminal after PATH update
   ```

### Symptom: Colors not displaying correctly in PowerShell/cmd.exe

**Cause:** Terminal doesn't support ANSI colors or NO_COLOR environment variable set.

**Resolution:**

1. **Use Windows Terminal (recommended):**
   - Install from Microsoft Store
   - Full ANSI color support built-in

2. **Force color output:**
   ```powershell
   $env:FORCE_COLOR = "1"
   aishell claude
   ```

3. **Disable color output:**
   ```powershell
   $env:NO_COLOR = "1"
   aishell claude
   ```

4. **Legacy cmd.exe limitations:**
   cmd.exe has limited ANSI support. Use PowerShell 7+ or Windows Terminal for best experience.

**Environment variable priority:**
- `NO_COLOR=1` disables colors (highest priority)
- `FORCE_COLOR=1` enables colors even if terminal not detected
- Auto-detection (default)

### Symptom: Permission errors when building custom .aishell/Dockerfile on Windows

**Cause:** File permissions not set correctly or Docker buildx path mapping issue.

**Resolution:**

1. **Use WSL2 filesystem for project (recommended):**
   Move project to WSL2 filesystem for better Docker performance:
   ```powershell
   # From PowerShell
   wsl
   # From WSL2
   cd ~
   git clone <your-repo>
   cd <your-repo>
   bb -m aishell.core setup --with-claude
   ```

2. **Check Docker Desktop file sharing settings:**
   - Open Docker Desktop → Settings → Resources → File Sharing
   - Ensure drive containing project is shared
   - Add drive if missing, click "Apply & Restart"

3. **Verify Dockerfile exists:**
   ```powershell
   Test-Path .aishell\Dockerfile
   # Should return True
   ```

4. **Check Dockerfile syntax:**
   ```bash
   # Inside container or WSL2
   cat .aishell/Dockerfile
   # Verify first line: FROM aishell:foundation
   ```

---

## Getting Help

If none of the above resolves your issue:

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
- [ ] You built an image with `aishell setup --with-<harness>`
- [ ] Config files use absolute paths, not relative
- [ ] Environment variables are set on the host before running aishell
- [ ] You are in the correct directory (if using project-specific config)
- [ ] Config YAML syntax is valid (use `yamllint` or online validator)

---

**Quick Tips:**

- **Rebuild the image:** `aishell update`
- **Check Docker is running:** `docker info`
- **Remove old images:** `docker image prune`
- **Read the error message** -- it often contains the solution
