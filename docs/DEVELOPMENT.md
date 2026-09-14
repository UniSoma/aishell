# aishell Development Guide

This guide helps developers extend aishell with new harnesses or core features.

**Last updated:** v3.8.0

---

## Table of Contents

- [Development Setup](#development-setup)
- [Project Structure](#project-structure)
- [Adding a New Harness](#adding-a-new-harness)
- [Testing Locally](#testing-locally)
- [Testing on Windows](#testing-on-windows)
- [Code Style](#code-style)
- [Submitting Changes](#submitting-changes)

---

## Development Setup

### Prerequisites

- Linux, macOS, or Windows 10/11
- Docker Engine (Linux/macOS) or Docker Desktop with WSL2 backend (Windows)
- [Babashka](https://babashka.org) 1.0+ (via Scoop on Windows, or manual binary)

### Clone and Run from Source

**All platforms (Unix/macOS/Linux/Windows):**

```bash
# Clone the repository
git clone https://github.com/UniSoma/aishell.git
cd aishell

# Run directly from source
bb -m aishell.core setup --with-claude

# Run commands
bb -m aishell.core claude
bb -m aishell.core --help
```

The `bb -m aishell.core` command runs the main namespace without installing. This works identically on all platforms - no platform-specific source changes needed.

### Symlink for Development

```bash
# Create symlink to development version
ln -sf $(pwd)/aishell ~/.local/bin/aishell-dev

# Test it
aishell-dev --version
```

---

## Project Structure

Overview (see [ARCHITECTURE.md](ARCHITECTURE.md) for full details):

```
aishell/
├── src/aishell/
│   ├── core.clj              # Main entry point (-main function)
│   ├── cli.clj               # Command parsing and dispatch
│   ├── run.clj               # Container lifecycle orchestration
│   ├── config.clj            # Configuration loading and merging
│   ├── state.clj             # State persistence (state.edn)
│   ├── docker/
│   │   ├── base.clj          # Global base image customization (three-tier chain)
│   │   ├── build.clj         # Foundation image build orchestration
│   │   ├── run.clj           # Docker run argument construction
│   │   ├── templates.clj     # Dockerfile template generation
│   │   ├── extension.clj     # Project-level Dockerfile handling
│   │   ├── hash.clj          # Dockerfile content hashing
│   │   ├── naming.clj        # Container naming and Docker state queries
│   │   ├── volume.clj        # Harness volume management (v2.8.0+)
│   │   └── spinner.clj       # Build progress UI
│   ├── detection/            # Sensitive file detection
│   │   ├── core.clj          # Detection orchestration
│   │   ├── patterns.clj      # Pattern definitions
│   │   ├── formatters.clj    # Output formatting
│   │   └── gitignore.clj     # Gitignore parsing
│   ├── gitleaks/
│   │   ├── scan_state.clj    # Scan freshness tracking
│   │   └── warnings.clj      # Gitleaks reminder warnings
│   ├── attach.clj            # Attach command (reconnect to containers)
│   ├── check.clj             # Pre-flight validation command
│   ├── info.clj              # Image stack info display
│   ├── validation.clj        # Argument validation
│   ├── migration.clj         # Version migration warnings (v2.9.0+)
│   ├── output.clj            # Terminal output utilities
│   └── util.clj              # Shared utilities
├── entrypoint.sh             # Container entrypoint script
├── aishell                   # Shell script wrapper (calls bb)
└── install.sh                # Installation script
```

**Namespace responsibilities:**
- **cli.clj:** Parse commands, validate args, dispatch to handlers
- **docker/base.clj:** Global base image customization -- detect `~/.aishell/Dockerfile`, build `aishell:base`, manage tag alias. Key functions: `ensure-base-image`, `global-dockerfile-exists?`, `needs-base-rebuild?`, `build-base-image`, `tag-foundation-as-base`
- **docker/build.clj:** Orchestrate foundation image build, manage state
- **docker/templates.clj:** Generate Dockerfile template from harness config
- **docker/run.clj:** Construct docker run command with mounts, env vars, volume
- **docker/volume.clj:** Harness volume hashing, creation, population, listing, pruning
- **info.clj:** Display image stack info (foundation contents, base/extension status, harnesses)
- **config.clj:** Load and merge YAML configs (global + project)
- **run.clj:** High-level container lifecycle (detection, pre-start, exec)
- **state.clj:** Read/write EDN state (foundation-hash, harness-volume-name, etc.)
- **migration.clj:** Version migration warnings and markers

---

## Build Flow and Volume Population Internals

### Foundation Image Build Flow

1. **Check cache:** `docker/hash/compute-hash` computes the SHA-256 of the Dockerfile template
2. **Compare hash:** If foundation-hash matches state, skip rebuild (unless `--force`)
3. **Generate Dockerfile:** `docker/templates/base-dockerfile` produces the template
4. **Write to temp dir:** `/tmp/aishell-build-{uuid}/Dockerfile`
5. **Docker build:** `docker build --tag aishell:foundation`
6. **Label image:** Embed the foundation hash in image metadata

### Volume Hash Computation

**Source:** `docker/volume/compute-harness-hash`

**Inputs:**
```clojure
{:with-claude true
 :claude-version "2.0.22"
 :with-opencode true
 :opencode-version nil  ; treated as "latest"
 :with-codex false
 :with-gemini false
 :with-pi false}
```

**Algorithm:**
1. Filter to enabled harnesses only
2. Normalize nil versions to `"latest"`
3. Create sorted map: `{"claude" "2.0.22", "opencode" "latest"}`
4. EDN serialize: `"{\"claude\" \"2.0.22\", \"opencode\" \"latest\"}"`
5. SHA-256 hash, take first 12 chars

**Result:** `aishell-harness-abc123def456`

**Volume sharing:** Identical configs produce identical hashes, so they share one volume.

### Volume Population

**Source:** `docker/volume/populate-volume`

**Flow:**
1. **Create volume:** `docker volume create aishell-harness-{hash} --label aishell.harness.hash={hash}`
2. **Start temporary container:**
   ```bash
   docker run --rm \
     -v aishell-harness-{hash}:/tools \
     aishell:foundation \
     sh -c "npm install commands..."
   ```
3. **npm installation:**
   - Sets `NPM_CONFIG_PREFIX=/tools/npm`
   - Runs `npm install -g @anthropic-ai/claude-code@{version}`
   - Repeats for each enabled npm harness (claude, codex, copilot, gemini, pi)
4. **Binary download (OpenCode):**
   - Downloads `curl -L https://github.com/anomalyco/opencode/releases/.../opencode-linux-x64.tar.gz`
   - Extracts to `/tools/bin`
5. **Set permissions:** `chmod -R a+rX /tools` (world-readable)
6. **Container exits:** Volume is populated; Docker auto-removes the temporary container

**On failure:**
- The system deletes the volume (rollback)
- Prints an error message
- Exits with non-zero status

### State Schema v2.8.0

**File:** `~/.aishell/state.edn`

**New fields:**
```clojure
{:foundation-hash "abc123def"                    ; Dockerfile template hash
 :harness-volume-hash "def456ghi"                ; Harness config hash
 :harness-volume-name "aishell-harness-def456ghi" ; Volume name
 :dockerfile-hash "abc123def"}                   ; DEPRECATED: alias for foundation-hash
```

**Backward compatibility:**
- Old aishell versions ignore unknown keys because EDN is schemaless
- New aishell writes both `:dockerfile-hash` and `:foundation-hash`
- Migration is additive: missing fields default to nil, with no migration code

**State writes:**
- `cli/handle-setup`: After foundation build + volume population
- `cli/handle-update`: Updates build-time (and foundation-hash if `--force`)

### Extension Cache Invalidation

**Source:** `docker/extension/build-project-extension`

**Three-tier model (current):**
- Extensions track the base image ID via `aishell.base.id` label
- When `aishell:base` changes (global Dockerfile modified or foundation updated), extensions auto-rebuild
- The `docker/base.clj` module handles the base layer; callers pass `base/base-image-tag` to extension functions to avoid circular dependencies

**Previous behavior (v2.8.0):**
- Used the foundation image ID directly to invalidate cache
- Foundation image label: `aishell.foundation.id={image-id}`

**Lazy build pattern:**
- `ensure-base-image` is called at multiple entry points (run-container, run-exec, resolve-image-tag) for defense-in-depth
- The base image builds lazily on first container run, not during `aishell setup`
- This pattern ensures the base image exists before any extension build or container launch

---

## Adding a New Harness

Every harness-dependent behavior derives from the harness registry in
`src/aishell/harness.clj`: setup flags, `--reuse-config`, summaries, help,
`check`, `info`, `update`, the harness-volume hash and install commands,
config mounts, environment passthrough, shell aliases and launch argv. Adding
a harness means adding one descriptor there, then proving it through the
existing contract tests. No harness-specific branch belongs in `cli.clj`,
`run.clj` or `volume.clj`; the Copilot integration (`:copilot`) is the
reference for an npm harness added this way.

### Step 1: Descriptor

Append a descriptor to `registry` in display order. The schema comment at the
top of the namespace documents every key; the required ones are `:id`,
`:label`, `:subcommand`, `:state-key` (`:with-<id>`), `:version-key`
(`:<id>-version`), the four capability booleans (`:interactive?`,
`:pre-start?`, `:accepts-config-defaults?`, `:volume-participant?`) and
`:install`.

```clojure
{:id :cursor
 :label "Cursor CLI"
 :subcommand "cursor"
 :state-key :with-cursor
 :version-key :cursor-version
 :interactive? true
 :pre-start? true
 :accepts-config-defaults? true
 :volume-participant? true
 :alias {:always? true}
 :install {:kind :npm :package "@cursor/cli"}
 :config-paths [{:path [".cursor"] :type :dir}]
 :env-passthrough ["CURSOR_API_KEY"]
 :runtime-env {"CURSOR_AUTO_UPDATE" "false"}}
```

Optional keys, each read by exactly one derivation:

| Key | Effect |
|-----|--------|
| `:fixed-args` | argv flags inserted before user args on every launch |
| `:skip-permissions-flag` | flag added when skip-permissions is in effect |
| `:alias` | emit a shell alias in the sandbox; `{:always? false}` means only when it has args |
| `:setup-flag-desc` | help text for `--with-<id>` when "Include <label>" is not enough |
| `:config-paths` | home-relative dirs/files created on the host and mounted into the container |
| `:credentials-file-env` | env var naming a host credentials file to mount read-only |
| `:env-passthrough` | host env vars forwarded when set, in declared order |
| `:runtime-env` | fixed env entries applied when enabled; they override config `env` and lose only to `docker_args` ([ADR 0008](adr/0008-harness-owned-runtime-environment-precedence.md)) |

Keep `:env-passthrough` to variables the harness itself defines. Broad
credentials such as `GITHUB_TOKEN` stay an explicit opt-in through the
`env` config key. A variable aishell decides the value of belongs in
`:runtime-env` instead — a passthrough only works when the user already set it
on the host, which is the wrong shape for policy. Add an update knob only when
the harness's own documentation names it. The behavioral runtime-env tests in
`run_test.clj` iterate every descriptor carrying `:runtime-env`; the literal
inventory beside them is one more expectation to extend, like the tables below.

Install kinds: `:npm` (`{:kind :npm :package "..."}`, pinned by semver
through `--with-<id>=VERSION`), `:binary-tarball` (see OpenCode) and
`:image-baked` (see gitleaks). A new kind needs a branch in
`volume/build-install-commands`.

### Step 2: Contract tests

The registry tests are table-driven over `harness/registry`, so most of them
fail until the new descriptor is complete. Extend the literal expectations in:

- `test/aishell/harness_test.clj`: descriptor count and order, labels,
  install kinds, capabilities, config paths, passthrough, bare launch argv
- `test/aishell/cli_test.clj`: setup flag, empty and explicit setup state,
  `--reuse-config`, help ordering, subcommand pass-through
- `test/aishell/docker/volume_test.clj`: install commands and the hash.
  Do not edit `harness-hash-matches-the-pre-registry-derivation`; add an
  opt-in hash test like `copilot-hash-is-opt-in` so existing users' volume
  hashes are proven unchanged
- `test/aishell/docker/run_test.clj`: config mounts, `harness-api-keys`,
  runtime env, aliases. Rebind `run/host-env` to control which passthrough
  variables look set
- `test/aishell/check_test.clj`, `info_test.clj`, `output_test.clj`: the
  status and summary output that names each harness

### Step 3: Documentation

Update `README.md`, `docs/HARNESSES.md` (section plus the comparison table),
`docs/CONFIGURATION.md`, `llm.txt`, the `CONTEXT.md` glossary and
`CHANGELOG.md`. Add a `docs/TROUBLESHOOTING.md` entry if the harness has
authentication quirks. Use "Harness" for the concept; the glossary avoids
"agent", "tool" and "AI".

### Checklist

- [ ] Descriptor added in display order; `clj-kondo --lint src test` clean
- [ ] `bb test` passes with the new harness in every table-driven expectation
- [ ] Existing harness-volume hashes unchanged when the harness is disabled
- [ ] No harness-specific branch outside `harness.clj`
- [ ] Documentation and changelog updated

---

## Testing Locally

### Test Full Integration

```bash
# 1. Build with new harness
bb -m aishell.core setup --with-cursor

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
bb -m aishell.core setup --with-cursor=1.0.5

# Verify version in state
cat ~/.aishell/state.edn

# Verify correct version installed in container
bb -m aishell.core cursor --version
```

### Test Update Preservation

```bash
# Build with version
bb -m aishell.core setup --with-cursor=1.0.5

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

## Testing on Windows

### Prerequisites for Windows Development

- Windows 10/11 with WSL2 enabled
- Docker Desktop with WSL2 backend enabled
- Babashka 1.0+ installed (via Scoop or manual binary)
- PowerShell 7+ recommended (better cross-platform compatibility than PowerShell 5.1)

### Platform-Specific Test Scenarios

When testing Windows support or adding features that may affect cross-platform behavior, verify these five scenarios:

#### 1. Path Handling

**What to test:** Windows path normalization in Docker commands

**How to test:**
```powershell
# Test container startup with Windows paths
bb -m aishell.core claude

# Inside container, verify Unix-style paths
# (Windows paths should be normalized to forward slashes)

# Test config mount with Windows-style path
# Create .aishell/config.yaml with:
#   mounts:
#     - C:/test-data:/data

bb -m aishell.core claude
# Inside container: ls /data (should show C:/test-data contents)
```

**Why it matters:** Docker Desktop accepts Windows paths with forward slashes. aishell normalizes paths using `fs/unixify` at Docker boundaries. This test ensures path handling works correctly.

#### 2. State Directory

**What to test:** Windows uses LOCALAPPDATA for state files

**How to test:**
```powershell
# Verify state directory location on Windows
echo $env:LOCALAPPDATA\aishell

# Run aishell to create state file
bb -m aishell.core setup --with-claude

# Check state file exists
Get-ChildItem "$env:LOCALAPPDATA\aishell"
# Should show state.edn

# Verify content
Get-Content "$env:LOCALAPPDATA\aishell\state.edn"
```

**Why it matters:** Windows follows platform conventions (`%LOCALAPPDATA%\aishell` instead of `~/.local/state/aishell`). This test ensures state persistence works on Windows.

#### 3. Process Execution

**What to test:** Attach uses `p/process` (not `p/exec`) on Windows

**How to test:**
```powershell
# Start harness in background
bb -m aishell.core claude

# In another terminal, test attach
bb -m aishell.core attach claude
# Should attach successfully without crashing

# Verify I/O inheritance works
# Type commands in attached session, verify they execute
```

**Why it matters:** Windows doesn't support `p/exec` process replacement. aishell uses `p/process {:inherit true}` on Windows and `p/exec` on Unix. This test ensures attach works cross-platform.

#### 4. ANSI Color Output

**What to test:** Color detection and environment variable overrides

**How to test:**
```powershell
# Test color auto-detection in Windows Terminal
bb -m aishell.core --help
# Should show colored output (cyan, green, red)

# Test NO_COLOR override
$env:NO_COLOR = "1"
bb -m aishell.core --help
# Should show no colors

# Test FORCE_COLOR override
Remove-Item Env:\NO_COLOR
$env:FORCE_COLOR = "1"
bb -m aishell.core --help
# Should show colors even if terminal not detected
```

**Why it matters:** Windows has varied terminal support (cmd.exe, PowerShell 5.1, PowerShell 7+, Windows Terminal). Priority: `NO_COLOR` > `FORCE_COLOR` > auto-detection.

### Cross-Platform Development Patterns

When contributing features that touch platform-specific code, follow these patterns:

**1. Use `babashka.fs/windows?` for platform detection**

Don't use manual OS checks:
```clojure
;; ✗ Bad - manual OS detection
(if (str/includes? (System/getProperty "os.name") "Windows")
  ...)

;; ✓ Good - use babashka.fs
(if (fs/windows?)
  ...)
```

**2. Use `fs/path` for path construction**

Don't use string concatenation with `/`:
```clojure
;; ✗ Bad - hardcoded separator
(str home "/" ".aishell")

;; ✓ Good - use fs/path
(fs/path home ".aishell")
```

**3. Use `fs/unixify` at Docker mount boundaries**

Normalize Windows paths to forward slashes for Docker:
```clojure
;; When constructing Docker run commands
(str (fs/unixify source-path) ":" target-path)
```

**4. Guard `p/exec` calls with platform check**

Use `p/process {:inherit true}` on Windows:
```clojure
(if (fs/windows?)
  ;; Windows - use p/process with I/O inheritance
  (let [proc (p/process {:inherit true} cmd)]
    (System/exit (p/exit-code proc)))
  ;; Unix - use p/exec for clean process replacement
  (p/exec cmd))
```

**5. Guard `chmod` calls with platform check**

Windows doesn't have `chmod`:
```clojure
(when-not (fs/windows?)
  (fs/set-posix-file-permissions path "rwxr-xr-x"))
```

**Reference:** See [ARCHITECTURE.md](ARCHITECTURE.md) for full cross-platform architecture patterns and design rationale.

### Testing Without Windows Machine

**Limitations:** Simulating Windows path behavior on Unix/macOS is limited. Real Windows testing recommended for significant path/process changes.

**What you can test on Unix/macOS:**
- Forward-slash normalization (Docker Desktop accepts on Windows)
- Mount path format (Docker Desktop converts Windows paths)

**What you cannot test on Unix/macOS:**
- Actual Windows syscalls (CreateProcess, etc.)
- Windows filesystem semantics (case-insensitivity, path length limits)
- cmd.exe/PowerShell interaction
- CRLF line ending behavior
- ANSI color support in various Windows terminals

**CI/CD:** aishell GitHub Actions test on Ubuntu, macOS, and Windows runners. Windows CI uses Docker Desktop with WSL2 backend.

---

## Code Style

### Clojure Conventions

- Use kebab-case for function and variable names
- Add docstrings to public functions
- Prefer threading macros for readability (→, →>, cond→)
- Use let bindings for intermediate values
- Destructure function signatures where appropriate

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

When adding a new harness, add a descriptor to the registry and let the
existing derivations pick it up; see [Adding a New Harness](#adding-a-new-harness).
The Copilot descriptor is the reference for an npm harness.

### Error Handling

Use `output/error` for user-facing errors (exits with message):
```clojure
(when (not (docker/image-exists? image-tag))
  (output/error "No image found. Run: aishell setup --with-cursor"))
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

- [ ] Code follows existing patterns (compare to the claude, codex, copilot, gemini and pi descriptors)
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
- [ ] Built image: `aishell setup --with-[harness]`
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

Harnesses install into the shared harness volume, not the image. A harness
that needs a system package the foundation image lacks needs that package
added to the foundation Dockerfile in `src/aishell/docker/templates.clj`,
which bumps the image for every user; prefer harnesses whose npm package or
tarball is self-contained.

### Handling Multiple Config Locations

Some harnesses keep state in more than one place. List each one in the
descriptor's `:config-paths`; the mount pipeline creates the host path when
absent and mounts it at the matching container home path:

```clojure
:config-paths [{:path [".cursor"] :type :dir}
               {:path [".config" "cursor"] :type :dir}
               {:path [".cursor.json"] :type :file}]
```

### Version Syntax Variations

**npm packages:** `1.2.3` (standard semver)
**Custom installers:** May use `v1.2.3` prefix or `VERSION=1.2.3` env var

Check each harness's documentation for the correct version syntax.

---

## Questions?

- Review the existing descriptors in `src/aishell/harness.clj`
- See [ARCHITECTURE.md](ARCHITECTURE.md) for system design
- Ask in GitHub Discussions or Issues
