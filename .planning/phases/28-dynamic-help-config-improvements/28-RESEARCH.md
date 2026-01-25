# Phase 28: Dynamic Help & Config Improvements - Research

**Researched:** 2026-01-25
**Domain:** Babashka CLI, EDN state management, YAML parsing, Docker conditional builds
**Confidence:** HIGH

## Summary

This research covers the technical foundations needed to implement Phase 28's requirements: dynamic help output based on installed harnesses, build-time Gitleaks exclusion via `--without-gitleaks` flag, and YAML list format support for `pre_start` configuration.

The project already uses Babashka with tools.cli for CLI argument parsing, EDN for state persistence, and clj-yaml for YAML configuration. All core technologies are in place. The key challenges are:

1. **Dynamic Help Generation**: Reading state.edn at runtime and filtering help output to show only installed harness commands
2. **Conditional Docker Build**: Adding `--without-gitleaks` flag and propagating it as a build arg to skip Gitleaks installation
3. **YAML List Format**: Handling both string and list formats for `pre_start`, joining lists with ` && ` separator

**Primary recommendation:** Extend existing CLI dispatch system to read state and conditionally include commands, add new build arg for Gitleaks control, and enhance config parsing to detect and join YAML list format for `pre_start`.

## Standard Stack (Already in Use)

The project already uses the appropriate technologies:

### Core Technologies

| Component | Current Version | Purpose | Status |
|-----------|----------------|---------|--------|
| Babashka | 1.12.214 | CLI runtime and scripting | In use |
| babashka.cli | Built-in | CLI dispatch and argument parsing | In use |
| clojure.edn | Built-in | EDN file reading/writing for state | In use |
| clj-yaml | Latest via deps | YAML config parsing | In use |
| Docker build args | Standard | Conditional image building | In use for harnesses |

### Supporting Tools

| Tool | Purpose | Current Usage |
|------|---------|---------------|
| cli/format-opts | Generate formatted option help text | Used in print-help and print-build-help |
| fs/exists? | Check file existence | Used throughout config/state loading |
| clojure.string/join | String manipulation | Available for list joining |

## Technical Implementation Patterns

### Pattern 1: Dynamic Help Based on State

**What:** Read ~/.aishell/state.edn at CLI startup and conditionally show harness commands based on what's installed

**Current state file schema (from state.clj):**
```clojure
{:with-claude true            ; boolean
 :with-opencode false         ; boolean
 :with-codex false            ; boolean
 :with-gemini false           ; boolean
 :claude-version "2.0.22"     ; string or nil
 :opencode-version nil        ; string or nil
 :codex-version "0.89.0"      ; string or nil
 :gemini-version nil          ; string or nil
 :with-gitleaks true          ; NEW: boolean (added in Phase 28)
 :image-tag "aishell:base"
 :build-time "2026-01-20..."
 :dockerfile-hash "abc123def456"}
```

**Implementation approach:**

```clojure
;; In cli.clj print-help function

(defn installed-harnesses
  "Return set of installed harness names based on state."
  []
  (if-let [state (state/read-state)]
    (cond-> #{}
      (:with-claude state) (conj "claude")
      (:with-opencode state) (conj "opencode")
      (:with-codex state) (conj "codex")
      (:with-gemini state) (conj "gemini"))
    ;; No state file = no build yet, show all commands
    #{"claude" "opencode" "codex" "gemini"}))

(defn print-help []
  (println (str output/BOLD "Usage:" output/NC " aishell [OPTIONS] COMMAND [ARGS...]"))
  (println)
  (println "Build and run ephemeral containers for AI harnesses.")
  (println)
  (println (str output/BOLD "Commands:" output/NC))
  (println (str "  " output/CYAN "build" output/NC "      Build the container image"))
  (println (str "  " output/CYAN "update" output/NC "     Rebuild with latest versions"))

  ;; Conditionally show harness commands based on installation
  (let [installed (installed-harnesses)]
    (when (contains? installed "claude")
      (println (str "  " output/CYAN "claude" output/NC "     Run Claude Code")))
    (when (contains? installed "opencode")
      (println (str "  " output/CYAN "opencode" output/NC "   Run OpenCode")))
    (when (contains? installed "codex")
      (println (str "  " output/CYAN "codex" output/NC "      Run Codex CLI")))
    (when (contains? installed "gemini")
      (println (str "  " output/CYAN "gemini" output/NC "     Run Gemini CLI"))))

  (println (str "  " output/CYAN "gitleaks" output/NC "   Run Gitleaks secret scanner"))
  (println (str "  " output/CYAN "(none)" output/NC "     Enter interactive shell"))
  ;; ... rest of help output
  )
```

**Key considerations:**
- If state.edn doesn't exist (no build yet), show all commands (helps discoverability)
- Gitleaks always shown (even if --without-gitleaks used, command still works via external install)
- State reading is fast (small EDN file, local filesystem)
- No caching needed - help invocation is terminal operation

**Source:** Current implementation in cli.clj (lines 71-94), state.clj (lines 14-20)

### Pattern 2: Conditional Docker Build with --without-gitleaks

**What:** Add `--without-gitleaks` flag to build command that propagates as `WITH_GITLEAKS` build arg, defaulting to true for backwards compatibility

**Current Gitleaks installation (from templates.clj lines 77-90):**
```dockerfile
# Install Gitleaks for secret scanning
ARG GITLEAKS_VERSION=8.30.0
RUN set -eux; \
    dpkgArch="$(dpkg --print-architecture)"; \
    case "${dpkgArch##*-}" in \
        amd64) glArch='x64' ;; \
        arm64) glArch='arm64' ;; \
        armhf) glArch='armv7' ;; \
        *) echo "unsupported architecture: $dpkgArch"; exit 1 ;; \
    esac; \
    curl -fsSL "https://github.com/gitleaks/gitleaks/releases/download/v${GITLEAKS_VERSION}/gitleaks_${GITLEAKS_VERSION}_linux_${glArch}.tar.gz" \
    | tar -xz -C /usr/local/bin gitleaks; \
    chmod +x /usr/local/bin/gitleaks; \
    gitleaks version
```

**Conditional installation pattern:**
```dockerfile
# Install Gitleaks for secret scanning (conditional)
ARG WITH_GITLEAKS=true
ARG GITLEAKS_VERSION=8.30.0
RUN if [ "$WITH_GITLEAKS" = "true" ]; then \
        set -eux; \
        dpkgArch="$(dpkg --print-architecture)"; \
        case "${dpkgArch##*-}" in \
            amd64) glArch='x64' ;; \
            arm64) glArch='arm64' ;; \
            armhf) glArch='armv7' ;; \
            *) echo "unsupported architecture: $dpkgArch"; exit 1 ;; \
        esac; \
        curl -fsSL "https://github.com/gitleaks/gitleaks/releases/download/v${GITLEAKS_VERSION}/gitleaks_${GITLEAKS_VERSION}_linux_${glArch}.tar.gz" \
        | tar -xz -C /usr/local/bin gitleaks; \
        chmod +x /usr/local/bin/gitleaks; \
        gitleaks version; \
    fi
```

**CLI changes required:**

```clojure
;; In cli.clj build-spec (line 62)
(def build-spec
  {:with-claude   {:desc "Include Claude Code (optional: =VERSION)"}
   :with-opencode {:desc "Include OpenCode (optional: =VERSION)"}
   :with-codex    {:desc "Include Codex CLI (optional: =VERSION)"}
   :with-gemini   {:desc "Include Gemini CLI (optional: =VERSION)"}
   :without-gitleaks {:coerce :boolean :desc "Skip Gitleaks installation"}  ; NEW
   :force         {:coerce :boolean :desc "Force rebuild (bypass Docker cache)"}
   :verbose       {:alias :v :coerce :boolean :desc "Show full Docker build output"}
   :help          {:alias :h :coerce :boolean :desc "Show build help"}})

;; In cli.clj handle-build (line 113)
(defn handle-build [{:keys [opts]}]
  (if (:help opts)
    (print-build-help)
    (let [;; Parse flags
          claude-config (parse-with-flag (:with-claude opts))
          opencode-config (parse-with-flag (:with-opencode opts))
          codex-config (parse-with-flag (:with-codex opts))
          gemini-config (parse-with-flag (:with-gemini opts))
          with-gitleaks (not (:without-gitleaks opts))  ; NEW: invert flag

          ;; ... validation ...

          ;; Build with options
          result (build/build-base-image
                   {:with-claude (:enabled? claude-config)
                    :with-opencode (:enabled? opencode-config)
                    :with-codex (:enabled? codex-config)
                    :with-gemini (:enabled? gemini-config)
                    :with-gitleaks with-gitleaks  ; NEW
                    ;; ... rest of options
                    })]

      ;; Persist state with gitleaks flag
      (state/write-state
        {:with-claude (:enabled? claude-config)
         ;; ... other flags ...
         :with-gitleaks with-gitleaks  ; NEW
         ;; ... rest of state
         }))))

;; In docker/build.clj build-docker-args (line 68)
(defn- build-docker-args
  [{:keys [with-claude with-opencode with-codex with-gemini with-gitleaks  ; NEW
           claude-version opencode-version codex-version gemini-version]} dockerfile-hash]
  (cond-> []
    with-claude (conj "--build-arg" "WITH_CLAUDE=true")
    with-opencode (conj "--build-arg" "WITH_OPENCODE=true")
    with-codex (conj "--build-arg" "WITH_CODEX=true")
    with-gemini (conj "--build-arg" "WITH_GEMINI=true")
    with-gitleaks (conj "--build-arg" "WITH_GITLEAKS=true")  ; NEW
    (not with-gitleaks) (conj "--build-arg" "WITH_GITLEAKS=false")  ; NEW: explicit false
    ;; ... version args ...
    true (conj "--label" (str dockerfile-hash-label "=" dockerfile-hash))))
```

**Cache invalidation considerations:**
- Docker ARG with conditional RUN forces rebuild when ARG changes (cannot be cached across different WITH_GITLEAKS values)
- This is acceptable: users who exclude Gitleaks are making an intentional choice, rebuild cost is reasonable
- Force flag (--force) provides explicit rebuild control if needed

**Source:** Docker Best Practices guide ([Docker ARG guide](https://www.docker.com/blog/docker-best-practices-using-arg-and-env-in-your-dockerfiles/)), current templates.clj implementation

### Pattern 3: YAML List Format for pre_start

**What:** Support both string and list formats for `pre_start` config, joining list items with ` && ` separator

**Current behavior (string only):**
```yaml
pre_start: "echo 'hello' && redis-server --daemonize yes"
```

**New behavior (list support):**
```yaml
pre_start:
  - "echo 'Starting services...'"
  - "redis-server --daemonize yes"
  - "sleep 2"

# Equivalent to: "echo 'Starting services...' && redis-server --daemonize yes && sleep 2"
```

**Implementation in config.clj:**

```clojure
;; Add normalization function
(defn normalize-pre-start
  "Normalize pre_start: string passes through, list joins with ' && '.
   Returns string or nil."
  [pre-start-value]
  (cond
    (nil? pre-start-value) nil
    (string? pre-start-value) pre-start-value
    (sequential? pre-start-value) (clojure.string/join " && " pre-start-value)
    :else nil))  ; Invalid type, treat as nil

;; Modify load-yaml-config to normalize pre_start
(defn load-yaml-config
  "Load and parse YAML config from path. Returns parsed config or nil on error."
  [path]
  (when (fs/exists? path)
    (try
      (-> (slurp path)
          yaml/parse-string
          (validate-config path)
          ;; Normalize pre_start after validation
          (update :pre_start normalize-pre-start))
      (catch Exception e
        (output/error (str "Invalid YAML in " path ": " (.getMessage e)))))))
```

**YAML parsing behavior (from clj-yaml):**
- YAML lists parse to Clojure sequences (lazy seqs or vectors depending on context)
- `clojure.string/join` works with any sequential collection
- Empty list becomes empty string after join, treated as nil by conditional in entrypoint.sh

**Backwards compatibility:**
- Existing string format continues to work unchanged
- Merge strategy for scalar `pre_start` remains: project replaces global
- No breaking changes to existing configs

**Security considerations:**
- Individual list items are NOT shell-escaped (user responsible for proper quoting)
- `&&` separator means command failure stops execution (fail-fast behavior)
- This matches Docker Compose behavior for command arrays joined for shell execution

**Source:** Current config.clj implementation (lines 194-240), clj-yaml documentation ([clj-yaml user guide](https://github.com/clj-commons/clj-yaml/blob/master/doc/01-user-guide.adoc)), Docker Compose command format guide ([TestDriven.io Docker tips](https://testdriven.io/tips/9c233ead-5f34-4b95-b416-84f80f3ef5e7/))

## Architecture Considerations

### State File Schema Extension

The state.edn schema needs one new field:

```clojure
{;; Existing fields
 :with-claude true
 :with-opencode false
 :with-codex false
 :with-gemini false
 :claude-version "2.0.22"
 :opencode-version nil
 :codex-version "0.89.0"
 :gemini-version nil

 ;; NEW field for Phase 28
 :with-gitleaks true          ; boolean, tracks --without-gitleaks build flag

 ;; Existing metadata
 :image-tag "aishell:base"
 :build-time "2026-01-20T15:30:00Z"
 :dockerfile-hash "abc123def456"}
```

**Migration strategy:**
- Existing state files without `:with-gitleaks` key: treat as `true` (Gitleaks was installed in v2.4.0)
- First build after Phase 28 writes new schema with `:with-gitleaks` field
- No data migration script needed (use `(get state :with-gitleaks true)` pattern)

### Config Loading Flow

Pre_start normalization happens during YAML parsing (early in load chain):

```
1. load-yaml-config (config.clj)
   - slurp file
   - yaml/parse-string  → returns map with :pre_start as string or sequence
   - validate-config    → validates known keys, returns config unchanged
   - normalize-pre-start → converts list to string with && separator
   - returns normalized config

2. merge-configs (if applicable)
   - receives already-normalized pre_start values (strings)
   - scalar merge strategy: project replaces global
   - no special handling needed

3. Runtime usage (run.clj)
   - passes pre_start string to docker run as PRE_START env var
   - entrypoint.sh receives joined string
   - executes via sh -c (handles && correctly)
```

**Benefits of early normalization:**
- Config merge logic unchanged (still sees scalars)
- Runtime code unchanged (still receives strings)
- Validation can check for valid list items (non-empty strings)

### Help Output Performance

Reading state.edn on every `--help` invocation:

**Performance analysis:**
- state.edn size: ~500 bytes (small EDN map)
- File read + EDN parse: <1ms on typical system
- Help output is terminal operation (no repeated calls)
- No caching needed

**Alternative considered:** Cache state in memory during CLI session
- **Rejected:** CLI is stateless script (new process per invocation)
- No persistent process to cache in
- File read overhead is negligible

## Domain Pitfalls

### Pitfall 1: State File Missing After Build

**What goes wrong:** User runs `aishell build`, build succeeds, but state.edn not written due to error in state persistence code. Help output shows no commands.

**Why it happens:** State write happens after build completes. If write fails (disk full, permissions), build succeeds but state is lost.

**Prevention:**
- Validate state write success in handle-build
- If state write fails, show warning but don't fail build (user can still use container)
- Document state.edn location in help output if commands not shown

**Detection:** Help output shows build/update/gitleaks commands but no harness commands

### Pitfall 2: YAML List with Empty Items

**What goes wrong:** User defines `pre_start: ["echo hi", "", "echo bye"]`. Joining produces `"echo hi &&  && echo bye"` which fails with syntax error.

**Why it happens:** clojure.string/join doesn't filter empty items

**Prevention:**
```clojure
(defn normalize-pre-start
  [pre-start-value]
  (cond
    (nil? pre-start-value) nil
    (string? pre-start-value) pre-start-value
    (sequential? pre-start-value)
    (let [filtered (filter (comp not empty?) pre-start-value)]
      (when (seq filtered)
        (clojure.string/join " && " filtered)))
    :else nil))
```

**Detection:** Container fails to start with "sh: syntax error" in /tmp/pre-start.log

### Pitfall 3: State File Tracks Wrong Gitleaks Status

**What goes wrong:** User builds with `--without-gitleaks`, state.edn shows `:with-gitleaks false`, but `aishell gitleaks` command still works (using external Gitleaks installation or fallback).

**Why it happens:** `:with-gitleaks` tracks build-time installation, not runtime availability

**Prevention:**
- Document that `:with-gitleaks` means "installed in container"
- Gitleaks command can still work if binary is on host or mounted
- Help text should still show `gitleaks` command regardless of build flag

**Detection:** State shows `:with-gitleaks false` but `aishell gitleaks dir .` works

### Pitfall 4: Docker Build Cache with Changing ARG

**What goes wrong:** User builds with Gitleaks, then rebuilds with `--without-gitleaks`. Docker doesn't use cache for RUN layer, rebuilds from that point.

**Why it happens:** ARG changes invalidate cache for all subsequent layers

**Prevention:**
- This is expected Docker behavior, not a bug
- Document that changing optional components requires rebuild
- Gitleaks installation is late in Dockerfile (after base tools), so cache loss is minimal

**Detection:** User notices rebuild takes longer than expected when toggling flags

## Documentation Requirements

### DOC-01: CONFIGURATION.md Update for pre_start List Format

Add to pre_start section (after line 120 in current CONFIGURATION.md):

```markdown
### pre_start

Command(s) to run before the shell starts. Runs in background, output goes to `/tmp/pre-start.log`.

**Formats:**

**String format** (original):
```yaml
pre_start: "echo 'Container started' && redis-server --daemonize yes"
```

**List format** (v2.5+):
```yaml
pre_start:
  - "echo 'Starting services...'"
  - "redis-server --daemonize yes"
  - "sleep 2"
  - "echo 'Services ready'"
```

**Behavior:**
- **String:** Executed as-is via `sh -c`
- **List:** Items joined with ` && ` separator, then executed via `sh -c`
- **Empty list:** No pre-start command runs
- **Merge strategy:** Project config replaces global (not concatenated)

**Command chaining:**
- `&&` separator means commands run sequentially
- If any command fails (non-zero exit), execution stops
- All commands run as container user (not root)

**Use cases:**
- Start background services (Redis, PostgreSQL)
- Run initialization scripts
- Wait for dependencies
- Set up runtime environment

**Important notes:**
- Commands run asynchronously (don't block container startup)
- Use `sleep` if subsequent commands depend on previous ones
- Check `/tmp/pre-start.log` if services don't start
- Use `sudo` in individual commands if root access needed
```

### DOC-02: CONFIGURATION.md Update for --without-gitleaks

Add to build command documentation (new section after Phase 28):

```markdown
## Build Command Reference

### Optional Component Flags

Control which tools are installed in the container image:

| Flag | Default | Description |
|------|---------|-------------|
| `--with-claude[=VERSION]` | false | Install Claude Code (npm global) |
| `--with-opencode[=VERSION]` | false | Install OpenCode (native binary) |
| `--with-codex[=VERSION]` | false | Install Codex CLI (npm global) |
| `--with-gemini[=VERSION]` | false | Install Gemini CLI (npm global) |
| `--without-gitleaks` | false | Skip Gitleaks secret scanner installation |

**Gitleaks installation:**
- By default, Gitleaks is installed in the container
- Use `--without-gitleaks` to skip installation (reduces image size by ~15MB)
- You can still use `aishell gitleaks` if Gitleaks is installed on the host
- Build state tracks installation status in `~/.aishell/state.edn`

**Examples:**
```bash
# Build with Gitleaks (default)
aishell build --with-claude

# Build without Gitleaks
aishell build --with-claude --without-gitleaks

# Check what was installed
cat ~/.aishell/state.edn
# Shows :with-gitleaks true or false
```

**Image size impact:**
- With Gitleaks: ~280MB
- Without Gitleaks: ~265MB
- Savings: ~15MB (Gitleaks binary is ~15MB compressed)
```

## Testing Strategy

### Unit Tests (if test suite exists)

```clojure
;; Test dynamic help filtering
(deftest test-installed-harnesses
  (testing "returns installed harnesses from state"
    (with-redefs [state/read-state (fn [] {:with-claude true
                                            :with-codex true
                                            :with-opencode false
                                            :with-gemini false})]
      (is (= #{"claude" "codex"} (installed-harnesses)))))

  (testing "returns all harnesses when no state"
    (with-redefs [state/read-state (fn [] nil)]
      (is (= #{"claude" "opencode" "codex" "gemini"} (installed-harnesses))))))

;; Test pre_start normalization
(deftest test-normalize-pre-start
  (testing "string passes through"
    (is (= "echo hi" (normalize-pre-start "echo hi"))))

  (testing "list joins with &&"
    (is (= "echo hi && echo bye"
           (normalize-pre-start ["echo hi" "echo bye"]))))

  (testing "filters empty items"
    (is (= "echo hi && echo bye"
           (normalize-pre-start ["echo hi" "" "echo bye"]))))

  (testing "empty list returns nil"
    (is (nil? (normalize-pre-start []))))

  (testing "nil returns nil"
    (is (nil? (normalize-pre-start nil)))))
```

### Integration Tests

**Test 1: Help output reflects build state**
```bash
# Build with only Claude
aishell build --with-claude

# Check help output
aishell --help | grep -E "(claude|codex|opencode|gemini)"
# Should show only "claude" command

# Rebuild with Codex too
aishell build --with-claude --with-codex

# Check help output again
aishell --help | grep -E "(claude|codex|opencode|gemini)"
# Should show "claude" and "codex" commands
```

**Test 2: Gitleaks installation control**
```bash
# Build without Gitleaks
aishell build --with-claude --without-gitleaks

# Check image size (should be smaller)
docker images aishell:base --format "{{.Size}}"

# Check state file
cat ~/.aishell/state.edn | grep with-gitleaks
# Should show :with-gitleaks false

# Verify Gitleaks not in container
docker run --rm aishell:base which gitleaks
# Should exit with error (not found)
```

**Test 3: Pre_start list format**
```bash
# Create test config with list
cat > .aishell/config.yaml <<EOF
pre_start:
  - "echo 'Step 1' >> /tmp/test.log"
  - "echo 'Step 2' >> /tmp/test.log"
  - "echo 'Step 3' >> /tmp/test.log"
EOF

# Run container
aishell

# Check execution order
cat /tmp/pre-start.log
# Should show Step 1, Step 2, Step 3 in order

# Check that failure stops execution
cat > .aishell/config.yaml <<EOF
pre_start:
  - "echo 'Before fail'"
  - "false"
  - "echo 'After fail (should not run)'"
EOF

aishell
cat /tmp/pre-start.log
# Should show only "Before fail", not "After fail"
```

## Confidence Assessment

| Area | Confidence | Rationale |
|------|-----------|-----------|
| Dynamic Help | HIGH | Existing state.clj and cli.clj provide all needed infrastructure. Pattern is straightforward conditional output. |
| --without-gitleaks Flag | HIGH | Docker ARG conditional RUN is standard pattern. Existing code uses similar pattern for harness installation. State persistence already in place. |
| Pre_start List Format | HIGH | YAML parsing via clj-yaml handles lists natively. String join is trivial. Backwards compatibility ensured by type checking. |
| State Schema Extension | HIGH | EDN format allows easy schema evolution. Nil-safe reading pattern (get with default) handles missing keys. |
| Documentation | MEDIUM | Requires clear examples and behavior description. Testing with users recommended to ensure clarity. |

## Open Questions

1. **Help output when state.edn is corrupted or invalid:**
   - Current behavior: state/read-state returns nil on parse error
   - Proposed: Treat as "no state" and show all commands
   - Consideration: Should we warn about corrupted state?

2. **Pre_start list items with special characters:**
   - List items are not shell-escaped (user responsible)
   - Should we document quoting requirements clearly?
   - Alternative: Add shell-escape function (but breaks power-user use cases)

3. **Gitleaks version pinning when excluded:**
   - If user builds with `--without-gitleaks`, GITLEAKS_VERSION arg is unused
   - Should we remove GITLEAKS_VERSION from Dockerfile if WITH_GITLEAKS=false?
   - Recommendation: Keep it (no harm, simplifies conditional logic)

## Implementation Priority

Based on complexity and dependencies:

1. **Pre_start list format** (CFG-01, CFG-02, CFG-03)
   - Lowest risk, isolated change in config.clj
   - No dependencies on other work
   - Test independently first

2. **--without-gitleaks flag** (CLI-02, CLI-03)
   - Medium complexity, touches multiple files
   - Extends existing pattern (WITH_CLAUDE, etc.)
   - Requires state schema extension

3. **Dynamic help output** (CLI-01)
   - Depends on state schema having :with-gitleaks field
   - Implement after item 2 completes
   - Final integration point for all changes

4. **Documentation updates** (DOC-01, DOC-02)
   - After all implementation complete
   - Include examples from integration testing

## Sources and References

**Babashka CLI:**
- [Babashka CLI GitHub](https://github.com/babashka/cli) - CLI dispatch and format-opts usage
- [Babashka CLI API Documentation](https://github.com/babashka/cli/blob/main/API.md) - format-opts customization

**EDN State Management:**
- [clojure.edn API](https://clojure.github.io/clojure/clojure.edn-api.html) - EDN reading and writing
- [ClojureDocs edn namespace](https://clojuredocs.org/clojure.edn) - Examples and patterns

**YAML Parsing:**
- [clj-yaml GitHub](https://github.com/clj-commons/clj-yaml) - YAML parsing library
- [clj-yaml User Guide](https://github.com/clj-commons/clj-yaml/blob/master/doc/01-user-guide.adoc) - Array and list handling

**Docker Build:**
- [Docker ARG Best Practices](https://www.docker.com/blog/docker-best-practices-using-arg-and-env-in-your-dockerfiles/) - ARG usage and conditional builds
- [Docker Build Variables Guide](https://docs.docker.com/build/building/variables/) - Official ARG documentation
- [Docker Conditional Installation Guide](https://www.golinuxcloud.com/condition-in-dockerfile/) - Conditional RUN patterns

**Shell Command Execution:**
- [TestDriven.io Docker CMD Tips](https://testdriven.io/tips/9c233ead-5f34-4b95-b416-84f80f3ef5e7/) - Array vs string command format
- [Docker CMD vs ENTRYPOINT Best Practices](https://www.docker.com/blog/docker-best-practices-choosing-between-run-cmd-and-entrypoint/) - Command execution patterns

**Current Implementation References:**
- Project files: cli.clj, config.clj, state.clj, docker/build.clj, docker/templates.clj
- Configuration: .aishell/config.yaml, docs/CONFIGURATION.md
- Phase 22 research: .planning/phases/22-gitleaks-integration/22-RESEARCH.md (Gitleaks installation patterns)
