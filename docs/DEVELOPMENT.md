# aishell Development Guide

This guide is for developers who want to extend aishell with new features or harnesses.

**Target audience:** Developers adding new AI harness integrations or core aishell features.

**Last updated:** v2.4.0

---

## Table of Contents

- [Development Setup](#development-setup)
- [Project Structure](#project-structure)
- [Adding a New Harness](#adding-a-new-harness)
- [Testing Locally](#testing-locally)
- [Code Style](#code-style)
- [Submitting Changes](#submitting-changes)

---

## Development Setup

### Prerequisites

- Linux or macOS
- Docker Engine
- [Babashka](https://babashka.org) 1.0+

### Clone and Run from Source

```bash
# Clone the repository
git clone https://github.com/UniSoma/aishell.git
cd aishell

# Run directly from source
bb -m aishell.core build --with-claude

# Run commands
bb -m aishell.core claude
bb -m aishell.core --help
```

The `bb -m aishell.core` command runs the main namespace without installation.

### Symlink for Development

```bash
# Create symlink to development version
ln -sf $(pwd)/aishell ~/.local/bin/aishell-dev

# Test it
aishell-dev --version
```

---

## Project Structure

Brief overview (see [ARCHITECTURE.md](ARCHITECTURE.md) for full details):

```
aishell/
├── src/aishell/
│   ├── core.clj              # Main entry point (-main function)
│   ├── cli.clj               # Command parsing and dispatch
│   ├── run.clj               # Container lifecycle orchestration
│   ├── config.clj            # Configuration loading and merging
│   ├── state.clj             # State persistence (state.edn)
│   ├── docker/
│   │   ├── build.clj         # Image building orchestration
│   │   ├── run.clj           # Docker run argument construction
│   │   ├── templates.clj     # Dockerfile generation
│   │   ├── extension.clj     # Project-level Dockerfile handling
│   │   ├── hash.clj          # Dockerfile content hashing
│   │   └── spinner.clj       # Build progress UI
│   ├── detection/            # Sensitive file detection
│   │   ├── core.clj          # Detection orchestration
│   │   ├── patterns.clj      # Pattern definitions
│   │   ├── formatters.clj    # Output formatting
│   │   └── gitignore.clj     # Gitignore parsing
│   ├── gitleaks/
│   │   ├── scan_state.clj    # Scan freshness tracking
│   │   └── warnings.clj      # Gitleaks reminder warnings
│   ├── validation.clj        # Argument validation
│   ├── output.clj            # Terminal output utilities
│   └── util.clj              # Shared utilities
├── entrypoint.sh             # Container entrypoint script
├── aishell                   # Shell script wrapper (calls bb)
└── install.sh                # Installation script
```

**Namespace responsibilities:**
- **cli.clj:** Parse commands, validate args, dispatch to handlers
- **docker/build.clj:** Orchestrate Docker build, manage state
- **docker/templates.clj:** Generate Dockerfile from template
- **docker/run.clj:** Construct docker run command with mounts, env vars
- **config.clj:** Load and merge YAML configs (global + project)
- **run.clj:** High-level container lifecycle (detection, pre-start, exec)

---

## Adding a New Harness

Follow this checklist to integrate a new AI harness (e.g., `cursor`, `aider`, etc.).

### Step 1: Dockerfile Template

**File:** `src/aishell/docker/templates.clj`

Add harness installation to the Dockerfile template:

**a. Add build arguments:**
```dockerfile
ARG WITH_CURSOR=false
ARG CURSOR_VERSION=""
```

Pattern: `WITH_{HARNESS}` (uppercase) and `{HARNESS}_VERSION`

**b. Add installation block:**
```dockerfile
# Install Cursor if requested (npm global or curl install)
RUN if [ "$WITH_CURSOR" = "true" ]; then \
        if [ -n "$CURSOR_VERSION" ]; then \
            npm install -g @cursor/cursor-cli@"$CURSOR_VERSION"; \
        else \
            npm install -g @cursor/cursor-cli; \
        fi; \
    fi
```

**Patterns for different installers:**

**npm global (most common):**
```dockerfile
RUN if [ "$WITH_CURSOR" = "true" ]; then \
        if [ -n "$CURSOR_VERSION" ]; then \
            npm install -g @cursor/cursor-cli@"$CURSOR_VERSION"; \
        else \
            npm install -g @cursor/cursor-cli; \
        fi; \
    fi
```

**curl install script (like OpenCode):**
```dockerfile
RUN if [ "$WITH_CURSOR" = "true" ]; then \
        if [ -n "$CURSOR_VERSION" ]; then \
            VERSION="$CURSOR_VERSION" curl -fsSL https://cursor.sh/install | bash; \
        else \
            curl -fsSL https://cursor.sh/install | bash; \
        fi; \
    fi
```

**Check where binary is installed:**
- npm global: `/usr/local/bin/{harness}`
- Custom installer: May install to `/usr/local/bin` or `$HOME/.local/bin`

**Checklist:**
- [ ] Added `ARG WITH_CURSOR=false`
- [ ] Added `ARG CURSOR_VERSION=""`
- [ ] Added `RUN if [ "$WITH_CURSOR" = "true" ]` block
- [ ] Installation works without version (latest)
- [ ] Installation works with version (`CURSOR_VERSION="1.2.3"`)
- [ ] Binary location is in `$PATH`

---

### Step 2: Build Flags

**File:** `src/aishell/docker/build.clj`

**a. Add to `build-docker-args` function:**

Find the function that constructs Docker build args:
```clojure
(defn- build-docker-args
  [{:keys [with-claude with-opencode with-codex with-gemini
           claude-version opencode-version codex-version gemini-version]} dockerfile-hash]
  (cond-> []
    with-claude (conj "--build-arg" "WITH_CLAUDE=true")
    with-opencode (conj "--build-arg" "WITH_OPENCODE=true")
    with-codex (conj "--build-arg" "WITH_CODEX=true")
    with-gemini (conj "--build-arg" "WITH_GEMINI=true")
    ;; ADD YOUR HARNESS HERE:
    with-cursor (conj "--build-arg" "WITH_CURSOR=true")
    ;; ...
```

Add to the function signature and the cond-> threading macro.

**b. Add to `needs-rebuild?` function:**

Ensure version changes trigger rebuild:
```clojure
(and (:with-cursor opts)
     (not= (:cursor-version opts) (:cursor-version state)))
```

**c. Add to build output:**

Find the section that prints installed harnesses after build:
```clojure
(when (:with-cursor opts)
  (println (format-harness-line "Cursor" (:cursor-version opts))))
```

**Checklist:**
- [ ] Added `:with-cursor` and `:cursor-version` to function signature
- [ ] Added to `build-docker-args` cond-> block
- [ ] Added version check to `needs-rebuild?`
- [ ] Added to build success output

---

### Step 3: CLI Flags

**File:** `src/aishell/cli.clj`

**a. Add to `build-spec`:**
```clojure
(def build-spec
  {:with-claude   {:desc "Include Claude Code (optional: =VERSION)"}
   :with-opencode {:desc "Include OpenCode (optional: =VERSION)"}
   :with-codex    {:desc "Include Codex CLI (optional: =VERSION)"}
   :with-gemini   {:desc "Include Gemini CLI (optional: =VERSION)"}
   ;; ADD YOUR HARNESS HERE:
   :with-cursor   {:desc "Include Cursor (optional: =VERSION)"}
   ;; ...
```

**b. Add to `handle-build` function:**

Parse the flag and validate version:
```clojure
(defn handle-build [{:keys [opts]}]
  (if (:help opts)
    (print-build-help)
    (let [cursor-config (parse-with-flag (:with-cursor opts))
          _ (validate-version (:version cursor-config) "Cursor")
          ;; ...
```

Include in `build-base-image` call:
```clojure
(build/build-base-image
  {:with-cursor (:enabled? cursor-config)
   :cursor-version (:version cursor-config)
   ;; ...
```

**c. Add to `handle-update` function:**

Preserve configuration on update:
```clojure
(when (:with-cursor state)
  (println (str "  Cursor: " (or (:cursor-version state) "latest"))))
```

**d. Add to state persistence:**

Both in `handle-build` and `handle-update`:
```clojure
(state/write-state
  {:with-cursor (:enabled? cursor-config)
   :cursor-version (:version cursor-config)
   ;; ...
```

**e. Add to help text:**
```clojure
(defn print-help []
  ;; ...
  (println (str "  " output/CYAN "cursor" output/NC "     Run Cursor"))
  ;; ...
```

**f. Add to command dispatch:**
```clojure
(case (first clean-args)
  "claude" (run/run-container "claude" (vec (rest clean-args)) {:unsafe unsafe?})
  ;; ...
  "cursor" (run/run-container "cursor" (vec (rest clean-args)) {:unsafe unsafe?})
  ;; ...
```

**Checklist:**
- [ ] Added `:with-cursor` to `build-spec`
- [ ] Added flag parsing in `handle-build`
- [ ] Added version validation
- [ ] Added to build invocation
- [ ] Added to state persistence
- [ ] Added to `handle-update` output
- [ ] Added to help text
- [ ] Added to command dispatch

---

### Step 4: Config Mounts

**File:** `src/aishell/docker/run.clj`

Add config directory mount to `harness-config-mounts` function:

```clojure
(defn harness-config-mounts
  "Standard mounts for harness configuration directories.
   Only mounts directories that exist on host."
  []
  (let [home (util/get-home)
        config-paths [[(str home "/.claude") (str home "/.claude")]
                      [(str home "/.claude.json") (str home "/.claude.json")]
                      [(str home "/.config/opencode") (str home "/.config/opencode")]
                      [(str home "/.local/share/opencode") (str home "/.local/share/opencode")]
                      [(str home "/.codex") (str home "/.codex")]
                      [(str home "/.gemini") (str home "/.gemini")]
                      ;; ADD YOUR HARNESS HERE:
                      [(str home "/.cursor") (str home "/.cursor")]]]
    ;; ...
```

**Pattern:** `[source-path target-path]` tuple in `config-paths` vector.

**Common config locations:**
- `~/.{harness}` - Most CLI tools
- `~/.config/{harness}` - XDG Base Directory compliant
- `~/.local/share/{harness}` - XDG data directory

**Test the mount:**
```bash
# Authenticate on host
cursor login

# Check credentials exist
ls -la ~/.cursor

# Run in container
aishell cursor

# Verify mounted
aishell
ls -la ~/.cursor
```

**Checklist:**
- [ ] Added `[source, target]` tuple to `config-paths`
- [ ] Used correct config directory path for the harness
- [ ] Verified directory exists after authentication
- [ ] Tested credentials persist between container sessions

---

### Step 5: Environment Variables

**File:** `src/aishell/docker/run.clj`

Add API key environment variable to `env-passthrough-keys`:

```clojure
(def env-passthrough-keys
  "Environment variables to pass from host to container when set."
  [;; Harness-specific keys
   "ANTHROPIC_API_KEY"
   "OPENAI_API_KEY"
   "CODEX_API_KEY"
   "GEMINI_API_KEY"
   "GOOGLE_API_KEY"
   ;; ADD YOUR HARNESS HERE:
   "CURSOR_API_KEY"
   ;; ...
```

**Pattern:** Add to the vector with appropriate comment grouping.

**Common naming conventions:**
- `{HARNESS}_API_KEY` - Most common
- `{PROVIDER}_API_KEY` - Provider-specific (OPENAI_API_KEY, etc.)

**Checklist:**
- [ ] Added env var to `env-passthrough-keys`
- [ ] Used correct variable name from harness docs
- [ ] Added to appropriate comment group
- [ ] Tested passthrough works (set on host, verify in container)

---

### Step 6: State Schema

**File:** `src/aishell/state.clj`

The state file tracks which harnesses are installed and their versions.

**Add to state writes:**

In `cli.clj` `handle-build` and `handle-update`:
```clojure
(state/write-state
  {:with-cursor (:enabled? cursor-config)
   :cursor-version (:version cursor-config)
   ;; ...
```

**State schema:**
```edn
{:with-claude true
 :claude-version "2.0.22"
 :with-cursor true
 :cursor-version "1.0.5"
 :image-tag "aishell:base"
 :build-time "2026-01-25T12:00:00Z"
 :dockerfile-hash "abc123..."}
```

**Checklist:**
- [ ] Added `:with-{harness}` boolean
- [ ] Added `:{harness}-version` string
- [ ] State persists after build
- [ ] State used in `handle-update` to preserve config

---

### Step 7: Documentation

Update documentation to include the new harness:

**a. README.md:**
```markdown
# Build with Cursor
aishell build --with-cursor

# Build with specific version
aishell build --with-cursor=1.0.5
```

**b. docs/HARNESSES.md:**

Add new section following existing pattern:
- Overview
- Installation
- Authentication (OAuth, API Key, both)
- Container-specific notes
- Usage examples
- Troubleshooting

**c. docs/TROUBLESHOOTING.md:**

Add authentication troubleshooting section if the harness has specific auth quirks.

**Checklist:**
- [ ] Added to README.md usage examples
- [ ] Added section to docs/HARNESSES.md
- [ ] Added auth instructions
- [ ] Added comparison table entry
- [ ] Added troubleshooting if needed

---

## Testing Locally

### Test Full Integration

```bash
# 1. Build with new harness
bb -m aishell.core build --with-cursor

# 2. Verify build state
cat ~/.aishell/state.edn
# Should show :with-cursor true

# 3. Test harness runs
bb -m aishell.core cursor --help

# 4. Test config mounting (after authenticating on host)
cursor login  # On host
bb -m aishell.core cursor
# Inside container, verify:
ls ~/.cursor
```

### Test Version Pinning

```bash
# Build with specific version
bb -m aishell.core build --with-cursor=1.0.5

# Verify version in state
cat ~/.aishell/state.edn

# Verify correct version installed in container
bb -m aishell.core cursor --version
```

### Test Update Preservation

```bash
# Build with version
bb -m aishell.core build --with-cursor=1.0.5

# Update (should preserve harness and version)
bb -m aishell.core update

# Verify version preserved
cat ~/.aishell/state.edn
```

### Test Config Directory Mounting

```bash
# 1. Authenticate on host first
cursor login

# 2. Create test credential file
echo "test" > ~/.cursor/test-file

# 3. Run in container
bb -m aishell.core cursor

# 4. Inside container, verify mount
ls -la ~/.cursor/test-file
cat ~/.cursor/test-file  # Should output "test"
```

### Test Environment Variable Passthrough

```bash
# Set API key on host
export CURSOR_API_KEY="test-key-123"

# Run container in shell mode
bb -m aishell.core

# Inside container, verify
echo $CURSOR_API_KEY  # Should output "test-key-123"
```

---

## Code Style

### Clojure Conventions

- **Use kebab-case** for function and variable names
- **Add docstrings** to public functions
- **Threading macros** for readability (→, →>, cond→)
- **Let bindings** for intermediate values
- **Destructuring** in function signatures where appropriate

**Example:**
```clojure
(defn build-cursor-args
  "Construct Docker build args for Cursor harness.
   Returns vector of [\"--build-arg\" \"KEY=VALUE\" ...] strings."
  [{:keys [with-cursor cursor-version]}]
  (cond-> []
    with-cursor (conj "--build-arg" "WITH_CURSOR=true")
    cursor-version (conj "--build-arg" (str "CURSOR_VERSION=" cursor-version))))
```

### Follow Existing Patterns

When adding a new harness, mirror the structure of existing harnesses:

1. **Look at Claude Code integration** as the reference implementation
2. **Copy the pattern** for Codex or Gemini (both use npm global install)
3. **Keep structure parallel** - if Claude has X, your harness should have X

### Error Handling

Use `output/error` for user-facing errors (exits with message):
```clojure
(when (not (docker/image-exists? image-tag))
  (output/error "No image found. Run: aishell build --with-cursor"))
```

Use try/catch for recoverable errors:
```clojure
(try
  (p/shell "cursor" "--version")
  (catch Exception e
    (println "Warning: Could not verify cursor installation")))
```

---

## Submitting Changes

### Before Submitting

- [ ] Code follows existing patterns (compare to claude, codex, gemini)
- [ ] Docstrings added to new functions
- [ ] Tested locally (build, run, config mount, env passthrough)
- [ ] Documentation updated (README, HARNESSES, TROUBLESHOOTING)
- [ ] No breaking changes to existing harnesses
- [ ] State schema backwards compatible

### Pull Request Process

```bash
# 1. Fork the repository on GitHub

# 2. Clone your fork
git clone https://github.com/YOUR_USERNAME/aishell.git
cd aishell

# 3. Create feature branch
git checkout -b add-cursor-harness

# 4. Make changes and commit
git add .
git commit -m "feat: add Cursor harness support"

# 5. Push to your fork
git push origin add-cursor-harness

# 6. Create PR on GitHub
```

### PR Description Template

```markdown
## Summary
Add support for [Harness Name] integration.

## Changes
- Added Dockerfile template for [harness]
- Added CLI flags: --with-[harness], --[harness]-version
- Added config directory mounting (~/.harness)
- Added environment variable passthrough ([HARNESS]_API_KEY)
- Updated documentation (README, HARNESSES, TROUBLESHOOTING)

## Testing
- [ ] Built image: `aishell build --with-[harness]`
- [ ] Ran harness: `aishell [harness] --help`
- [ ] Verified config mounting persists credentials
- [ ] Verified environment variable passthrough
- [ ] Tested version pinning

## Documentation
- [ ] Updated README.md with examples
- [ ] Added section to docs/HARNESSES.md
- [ ] Added troubleshooting if needed
```

---

## Common Patterns

### Adding Optional Dependencies

For harnesses requiring system packages:

```dockerfile
# In templates.clj, add to base RUN block or create conditional:
RUN if [ "$WITH_CURSOR" = "true" ]; then \
        apt-get update && \
        apt-get install -y libfoo-dev && \
        rm -rf /var/lib/apt/lists/*; \
    fi
```

### Handling Multiple Config Locations

Some tools use multiple directories:

```clojure
(defn harness-config-mounts []
  (let [home (util/get-home)
        config-paths [[home "/.cursor") (str home "/.cursor")]
                      [home "/.config/cursor") (str home "/.config/cursor")]
                      [home "/.local/share/cursor") (str home "/.local/share/cursor")]]]
    ;; ...
```

### Version Syntax Variations

**npm packages:** `1.2.3` (standard semver)
**Custom installers:** May use `v1.2.3` prefix or `VERSION=1.2.3` env var

Check harness documentation for correct version syntax.

---

## Questions?

- Review existing harness integrations (claude, codex, gemini)
- Check [ARCHITECTURE.md](ARCHITECTURE.md) for system design
- Ask in GitHub Discussions or Issues
