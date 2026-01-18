# Phase 2: Git Integration - Research

**Researched:** 2026-01-17
**Domain:** Git configuration in containers, identity propagation, safe directory handling
**Confidence:** HIGH

## Summary

This research covers the technical foundations needed to make git work seamlessly inside the aishell container. Users need to make commits inside the container with their host identity, and git must recognize the mounted project directory as safe despite ownership differences.

The core challenge has two parts: (1) propagating git identity from host to container, and (2) preventing "dubious ownership" errors when git encounters a repository owned by a different user (the host user vs container user). Both are solved with well-established patterns.

For identity, git respects environment variables (`GIT_AUTHOR_NAME`, `GIT_AUTHOR_EMAIL`, `GIT_COMMITTER_NAME`, `GIT_COMMITTER_EMAIL`) over config values. The CLI reads the host's effective git config (respecting local `.git/config` overrides) and passes them as environment variables to the container. For safe directory, the entrypoint configures `safe.directory` in the container's global `.gitconfig` before switching to the non-root user.

**Primary recommendation:** Read git identity on host using `git -C <project_dir> config user.name` and `git -C <project_dir> config user.email`, pass as environment variables, and configure `safe.directory` in the entrypoint after creating the user's home directory.

## Standard Stack

The established tools and patterns for this domain:

### Core

| Component | Version/Choice | Purpose | Why Standard |
|-----------|---------------|---------|--------------|
| `GIT_*` env vars | Git builtin | Override git identity | Take precedence over config, official Git mechanism |
| `safe.directory` | Git 2.35.2+ | Mark directories as trusted | Official fix for CVE-2022-24765, no workarounds needed |
| `git -C <path>` | Git builtin | Run git command in directory | Reads effective config (local > global) |
| `git config --global` | Git builtin | Write container-scoped config | Writes to `$HOME/.gitconfig` |

### Supporting

| Tool | Version | Purpose | When to Use |
|------|---------|---------|-------------|
| `command -v git` | Bash | Check if git is installed | Host git detection |
| `git config --get` | Git builtin | Read config value | Reading identity values |
| `git config --show-origin` | Git builtin | Show where config comes from | Debugging config sources |

### Alternatives Considered

| Instead of | Could Use | Tradeoff |
|------------|-----------|----------|
| Environment variables | Mount `~/.gitconfig` | More complex, need to handle XDG paths, may expose other settings |
| `git -C <path> config` | `git config --file <path>/.git/config` | Less robust, doesn't merge global+local |
| `safe.directory` per-path | `safe.directory = *` | Disables security check entirely, not recommended |
| Entrypoint config | Bashrc config | Runs too late, git commands in entrypoint would fail |

**Environment variables for git identity:**
```bash
# These are recognized by git commit and related commands
GIT_AUTHOR_NAME="John Doe"
GIT_AUTHOR_EMAIL="john@example.com"
GIT_COMMITTER_NAME="John Doe"
GIT_COMMITTER_EMAIL="john@example.com"
```

## Architecture Patterns

### Pattern 1: Read Effective Git Config from Project Directory

**What:** Use `git -C <path>` to read config with proper local > global precedence
**When to use:** Reading host git identity before container launch
**Why it works:** Respects `.git/config` overrides in the project directory
**Example:**

```bash
# Source: Official git-config documentation
# git -C runs the command as if started in <path>
# git config without --local reads from all scopes with proper precedence

project_dir="$(pwd)"

# Read effective user.name (local > global > system)
git_name=$(git -C "$project_dir" config user.name 2>/dev/null) || git_name=""
git_email=$(git -C "$project_dir" config user.email 2>/dev/null) || git_email=""

# Works even if project is not a git repo (falls back to global)
# Works if project has local overrides (uses local values)
```

### Pattern 2: Pass Git Identity via Environment Variables

**What:** Set `GIT_AUTHOR_*` and `GIT_COMMITTER_*` environment variables
**When to use:** Always when launching container
**Why it works:** Environment variables take precedence over config, per Git documentation
**Example:**

```bash
# Source: https://git-scm.com/book/en/v2/Git-Internals-Environment-Variables

docker run --rm -it \
    -e "GIT_AUTHOR_NAME=$git_name" \
    -e "GIT_AUTHOR_EMAIL=$git_email" \
    -e "GIT_COMMITTER_NAME=$git_name" \
    -e "GIT_COMMITTER_EMAIL=$git_email" \
    "$IMAGE_NAME"
```

**Precedence order (highest to lowest):**
1. `GIT_AUTHOR_NAME` / `GIT_AUTHOR_EMAIL` environment variables
2. `user.name` / `user.email` in local `.git/config`
3. `user.name` / `user.email` in global `~/.gitconfig`
4. `EMAIL` environment variable (fallback for email only)
5. System user/host names (ultimate fallback, usually wrong)

### Pattern 3: Configure safe.directory in Entrypoint

**What:** Add project path to git's `safe.directory` before running as user
**When to use:** After creating user home directory, before executing user command
**Why it works:** `safe.directory` is read from global config, must be set before git operations
**Example:**

```bash
# In entrypoint.sh, after mkdir -p "$HOME" and before exec gosu

# Configure git safe.directory to trust the mounted project path
# The working directory is passed via -w flag and is available as PWD
# We use git config --global because it writes to $HOME/.gitconfig
git config --global --add safe.directory "$PWD"
```

**Important timing notes:**
1. Home directory must exist before `git config --global` can write
2. Must run before switching to non-root user (need write access to `/root/.gitconfig` or user's `.gitconfig`)
3. Must run before any git commands that access the repository

### Pattern 4: Warn on Missing Git Identity

**What:** Show warning if git identity not found on host, but continue
**When to use:** Before launching container when git config is empty
**Example:**

```bash
# Per CONTEXT.md: "If git identity not found on host: warn and continue"
# Per CONTEXT.md: "Simple warning message, no instructions"
# Per CONTEXT.md: "Warning shows in normal mode (not just verbose)"

if [[ -z "$git_name" ]] || [[ -z "$git_email" ]]; then
    warn "Git identity not found on host"
fi
```

### Pattern 5: Handle Git Not Installed on Host

**What:** Skip git identity reading if git is not installed
**When to use:** Host detection phase
**Claude's Discretion per CONTEXT.md**
**Example:**

```bash
# Check if git is available on host
if command -v git &> /dev/null; then
    git_name=$(git -C "$project_dir" config user.name 2>/dev/null) || git_name=""
    git_email=$(git -C "$project_dir" config user.email 2>/dev/null) || git_email=""
else
    git_name=""
    git_email=""
    # Optionally warn, but git is installed in container so commits still work
fi
```

### Recommended Implementation Location

```
Host CLI (aishell):
  1. Check if git installed on host
  2. Read effective git identity from project directory
  3. Warn if identity not found
  4. Pass identity as environment variables to docker run

Container Entrypoint (entrypoint.sh):
  1. Create user with matching UID/GID (existing)
  2. Create home directory (existing)
  3. Configure safe.directory for project path (NEW)
  4. Switch to user and execute command (existing)
```

### Anti-Patterns to Avoid

- **Mounting `~/.gitconfig` from host:** Exposes other git settings, complex path handling (`~/.gitconfig` vs `$XDG_CONFIG_HOME/git/config`)
- **Using `safe.directory = *`:** Disables security check entirely, not recommended
- **Setting safe.directory in bashrc:** Too late for non-interactive git commands
- **Running git config as root for user's config:** Creates root-owned files in user home
- **Hardcoding safe.directory paths:** Container mounts project at variable paths

## Don't Hand-Roll

Problems that look simple but have existing solutions:

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| Git identity propagation | Custom config file merging | Environment variables | Git documents these as the primary override mechanism |
| Safe directory handling | Ownership manipulation | `safe.directory` config | Official fix for CVE-2022-24765, no performance cost |
| Reading effective config | Parsing config files | `git -C <path> config` | Handles all scopes and precedence correctly |
| Config file location | Checking multiple paths | `git config --global` | Git knows where to write based on `$HOME` |

**Key insight:** Git's environment variable mechanism was designed for exactly this use case - CI/CD pipelines, containers, and automated systems that need to override identity temporarily without modifying config files.

## Common Pitfalls

### Pitfall 1: Git Config Fails Without Home Directory
**What goes wrong:** `git config --global` fails with "could not lock config file"
**Why it happens:** Home directory doesn't exist when git tries to write
**How to avoid:** Create `$HOME` directory before running `git config --global`
**Warning signs:** `error: could not lock config file /home/user/.gitconfig: No such file or directory`

### Pitfall 2: Safe Directory Configured Too Late
**What goes wrong:** Git commands fail with "dubious ownership" despite safe.directory being set
**Why it happens:** safe.directory was configured in bashrc, but command ran before bashrc sourced
**How to avoid:** Configure safe.directory in entrypoint before exec gosu
**Warning signs:** First git command fails, subsequent ones work

### Pitfall 3: Using --local Flag Outside Git Repo
**What goes wrong:** `git config --local` fails with "not in a git directory"
**Why it happens:** Project directory is not a git repository
**How to avoid:** Use `git config` without `--local` to read effective config (falls back to global)
**Warning signs:** `fatal: --local can only be used inside a git repository`

### Pitfall 4: Environment Variables Not Set for Both Author and Committer
**What goes wrong:** Commits have correct author but wrong committer, or vice versa
**Why it happens:** Only set `GIT_AUTHOR_*` but forgot `GIT_COMMITTER_*`
**How to avoid:** Always set all four variables: `GIT_AUTHOR_NAME`, `GIT_AUTHOR_EMAIL`, `GIT_COMMITTER_NAME`, `GIT_COMMITTER_EMAIL`
**Warning signs:** `git log --format=fuller` shows different author and committer

### Pitfall 5: Empty Environment Variables Override Config
**What goes wrong:** Git uses empty string for name/email instead of config value
**Why it happens:** Passed empty environment variable (e.g., `-e GIT_AUTHOR_NAME=""`)
**How to avoid:** Only pass environment variable if value is non-empty
**Warning signs:** Commits with empty author name/email

### Pitfall 6: Forgetting that Git is Needed on Host
**What goes wrong:** Script crashes when trying to read git config on host without git
**Why it happens:** Assumed git is always installed
**How to avoid:** Check `command -v git` before calling git commands
**Warning signs:** `command not found: git`

## Code Examples

Verified patterns from official sources:

### Reading Git Identity on Host

```bash
# Source: git-config documentation
# Read effective git config respecting local > global precedence

read_git_identity() {
    local project_dir="$1"

    # Check if git is installed on host
    if ! command -v git &> /dev/null; then
        echo ""
        echo ""
        return 0
    fi

    # Read effective config (local overrides global)
    # git -C runs command as if started in <path>
    # Returns empty string if not set
    local name email
    name=$(git -C "$project_dir" config user.name 2>/dev/null) || name=""
    email=$(git -C "$project_dir" config user.email 2>/dev/null) || email=""

    echo "$name"
    echo "$email"
}
```

### Passing Git Identity to Container

```bash
# Source: Git Environment Variables documentation
# https://git-scm.com/book/en/v2/Git-Internals-Environment-Variables

launch_container() {
    local project_dir="$1"

    # Read git identity
    local identity
    identity=$(read_git_identity "$project_dir")
    local git_name git_email
    git_name=$(echo "$identity" | head -1)
    git_email=$(echo "$identity" | tail -1)

    # Warn if identity not found (per CONTEXT.md)
    if [[ -z "$git_name" ]] || [[ -z "$git_email" ]]; then
        warn "Git identity not found on host"
    fi

    # Build docker run command
    local -a docker_args=(
        --rm -it
        -v "$project_dir:$project_dir"
        -w "$project_dir"
        -e "LOCAL_UID=$(id -u)"
        -e "LOCAL_GID=$(id -g)"
        -e "TERM=${TERM:-xterm-256color}"
    )

    # Only pass git identity if both name and email are set
    if [[ -n "$git_name" ]] && [[ -n "$git_email" ]]; then
        docker_args+=(
            -e "GIT_AUTHOR_NAME=$git_name"
            -e "GIT_AUTHOR_EMAIL=$git_email"
            -e "GIT_COMMITTER_NAME=$git_name"
            -e "GIT_COMMITTER_EMAIL=$git_email"
        )
    fi

    exec docker run "${docker_args[@]}" "$IMAGE_NAME"
}
```

### Configuring safe.directory in Entrypoint

```bash
# In entrypoint.sh - after home directory creation, before exec gosu
# Source: git-config documentation for safe.directory

# ... existing user creation code ...

# Ensure home directory exists with correct ownership
mkdir -p "$HOME"
chown "$USER_ID:$GROUP_ID" "$HOME"

# Configure git safe.directory to trust the mounted project path
# PWD is set by docker run -w flag
# Must run before switching to non-root user
if [ -n "$PWD" ]; then
    git config --global --add safe.directory "$PWD"
fi

# ... rest of entrypoint ...

# Execute command as the user via gosu
exec gosu "$USER_ID:$GROUP_ID" "$@"
```

### Complete Entrypoint with Git Integration

```bash
#!/bin/bash
# entrypoint.sh - Dynamic user creation with gosu and git safe directory
# Creates a user at container startup that matches the host user's UID/GID

set -e

# Read UID/GID from environment variables
USER_ID=${LOCAL_UID:-1000}
GROUP_ID=${LOCAL_GID:-1000}
USERNAME=developer

# Create group if GID doesn't exist
if ! getent group "$GROUP_ID" > /dev/null 2>&1; then
    groupadd -g "$GROUP_ID" "$USERNAME" 2>/dev/null || true
fi

# Create user if UID doesn't exist
if ! getent passwd "$USER_ID" > /dev/null 2>&1; then
    useradd --shell /bin/bash \
        -u "$USER_ID" \
        -g "$GROUP_ID" \
        -o \
        -c "Container User" \
        -m "$USERNAME" 2>/dev/null || true
fi

# Get the username for this UID (might be different if user already existed)
ACTUAL_USER=$(getent passwd "$USER_ID" | cut -d: -f1)
export HOME=$(getent passwd "$USER_ID" | cut -d: -f6)

# Ensure home directory exists with correct ownership
mkdir -p "$HOME"
chown "$USER_ID:$GROUP_ID" "$HOME"

# Configure git safe.directory to trust the mounted project path (GIT-02)
# PWD is set by docker run -w flag
if [ -n "$PWD" ]; then
    git config --global --add safe.directory "$PWD"
fi

# Setup passwordless sudo for the user
echo "$ACTUAL_USER ALL=(ALL) NOPASSWD:ALL" > /etc/sudoers.d/developer
chmod 0440 /etc/sudoers.d/developer

# Source custom bashrc if starting bash
if [ "$1" = "/bin/bash" ] || [ "$1" = "bash" ]; then
    # Only add source line if not already present
    if ! grep -q "source /etc/bash.aishell" "$HOME/.bashrc" 2>/dev/null; then
        echo "source /etc/bash.aishell" >> "$HOME/.bashrc"
    fi
fi

# Execute command as the user via gosu
exec gosu "$USER_ID:$GROUP_ID" "$@"
```

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| Mounting ~/.gitconfig | Environment variables | Always better | Simpler, more portable, no path issues |
| Ignoring ownership errors | safe.directory config | Git 2.35.2 (Apr 2022) | Security fix for CVE-2022-24765 |
| chown on .git directory | safe.directory | Git 2.35.2 | Non-destructive, instant, no permission issues |
| Hardcoded identity in image | Runtime identity from host | Container best practice | Works for any user, any project |

**Deprecated/outdated:**
- **Recursive chown on .git:** Performance issue on large repos, may cause permission problems
- **safe.directory = \*:** Disables security check entirely, not recommended in shared environments
- **Relying on EMAIL env var:** Only sets email, doesn't set name, undocumented fallback

## Open Questions

Things that couldn't be fully resolved:

1. **Additional git config values to propagate**
   - What we know: CONTEXT.md says "start with name/email" (Claude's discretion for more)
   - What's unclear: Should we propagate `core.editor`, `core.autocrlf`, etc.?
   - Recommendation: For Phase 2, stick with name/email only. Add more if users request.

2. **Multiple safe.directory entries on repeated runs**
   - What we know: `git config --add` appends, doesn't replace
   - What's unclear: Does git deduplicate? Does multiple entries cause issues?
   - Recommendation: Not a problem - each container run is ephemeral, starts fresh.

3. **Submodule paths needing separate safe.directory**
   - What we know: Each submodule may need its own safe.directory entry
   - What's unclear: Should we auto-detect and add all submodule paths?
   - Recommendation: Out of scope for Phase 2. Document as known limitation.

## Sources

### Primary (HIGH confidence)
- [Git Environment Variables](https://git-scm.com/book/en/v2/Git-Internals-Environment-Variables) - Official docs on GIT_AUTHOR_*, GIT_COMMITTER_* precedence
- [git-config Documentation](https://git-scm.com/docs/git-config) - Official docs on safe.directory, config scopes
- [CVE-2022-24765](https://github.blog/2022-04-12-git-security-vulnerability-announced/) - GitHub blog on the security vulnerability that introduced safe.directory

### Secondary (MEDIUM confidence)
- [Avoiding Dubious Ownership in Dev Containers](https://www.kenmuse.com/blog/avoiding-dubious-ownership-in-dev-containers/) - Dev container patterns, verified with official docs
- [GitHub Actions Runner Issue #2033](https://github.com/actions/runner/issues/2033) - Container git safe.directory patterns

### Tertiary (LOW confidence)
- WebSearch results on Docker + git integration - Community patterns, corroborated with official docs

## Metadata

**Confidence breakdown:**
- Git environment variables: HIGH - Official Git documentation explicitly describes precedence
- safe.directory mechanism: HIGH - Official Git documentation, security advisory
- Implementation patterns: HIGH - Verified with local testing and official docs
- Edge cases: MEDIUM - Based on official docs plus community experience

**Research date:** 2026-01-17
**Valid until:** 2026-02-17 (30 days - stable domain, git safe.directory is well-established)
