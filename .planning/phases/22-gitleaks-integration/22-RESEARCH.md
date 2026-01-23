# Phase 22: Gitleaks Integration - Research

**Researched:** 2026-01-23
**Domain:** Gitleaks binary installation, command passthrough, state tracking, and freshness warnings
**Confidence:** HIGH

## Summary

Phase 22 integrates Gitleaks, a content-based secret scanner, into the aishell container. Users can run `aishell gitleaks` to perform deep scanning of git repositories for hardcoded secrets (passwords, API keys, tokens). The phase involves four main components: (1) installing the Gitleaks binary in the base container image, (2) creating a command passthrough mechanism similar to `aishell claude`, (3) tracking last-scan timestamps in XDG state directory keyed by project path, and (4) displaying freshness warnings when scans are stale (default 7 days).

Research confirms that Gitleaks is the industry-standard tool for secret detection with 89.9 benchmark score and high source reputation on Context7. The latest version is v8.30.0 (released November 2025). Installation is straightforward via GitHub releases binary download for Linux x64. Gitleaks supports two scanning modes: `gitleaks git` (scans commit history) and `gitleaks dir` (scans working tree without git context). The tool auto-detects `.gitleaks.toml` configuration files and supports baseline files (`.gitleaks-baseline.json`) for ignoring known findings.

The implementation follows established aishell patterns: Babashka scripting with EDN state persistence, XDG directory standards for personal state storage, and command dispatch architecture from cli.clj. The main challenge is implementing per-project state tracking, which requires keying scan timestamps by absolute project path (not hashed, for debuggability). Freshness warnings integrate into the existing detection framework display pattern.

**Primary recommendation:** Install Gitleaks binary in Dockerfile using GitHub releases download (v8.30.0 linux_x64.tar.gz). Add `gitleaks` command dispatch to cli.clj with full passthrough (like claude/opencode commands). Create new state module for scan timestamps using EDN file in XDG_STATE_HOME (`~/.local/state/aishell/gitleaks-scans.edn`) keyed by absolute project path. Display freshness warnings before container launch using existing output module patterns.

## Standard Stack

### Core

| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| Gitleaks | v8.30.0 | Content-based secret scanning | Industry standard (Context7 benchmark 89.9, high reputation) |
| babashka.fs | Built-in | File system operations, path manipulation | Already used throughout aishell |
| clojure.edn | Built-in | State persistence (read/write EDN) | Used in state.clj for build state |
| java.time.Instant | JDK built-in | ISO-8601 timestamp parsing and duration | Used in cli.clj for build timestamps |
| babashka.process | Built-in | Command execution (exec) | Used in run.clj for container execution |

### Supporting

| Library | Version | Purpose | When to Use |
|---------|---------|---------|-------------|
| java.time.Duration | JDK built-in | Calculate time between two Instants | For staleness calculation (days since scan) |
| aishell.output | Current | Warning display (YELLOW, NC colors) | For freshness warning messages |
| aishell.util | Current | XDG directory paths (state-dir function) | For locating XDG_STATE_HOME |
| aishell.config | Current | Load .aishell/config.yaml | For gitleaks_freshness_check toggle |

### Alternatives Considered

| Instead of | Could Use | Tradeoff |
|------------|-----------|----------|
| Binary download | Docker image (zricethezav/gitleaks) | Binary simpler - single executable, no container-in-container |
| Binary download | apt install gitleaks | GitHub releases more current, version pinning easier |
| EDN state file | SQLite database | EDN simpler for key-value, no SQL overhead |
| Absolute path keys | SHA-256 hash of path | Absolute paths debuggable, human-readable |
| XDG_STATE_HOME | Project .aishell/ directory | XDG correct - state is personal, not project config |

**Installation:**
```bash
# Dockerfile - after gosu installation
GITLEAKS_VERSION=8.30.0
curl -fsSL "https://github.com/gitleaks/gitleaks/releases/download/v${GITLEAKS_VERSION}/gitleaks_${GITLEAKS_VERSION}_linux_x64.tar.gz" \
  | tar -xz -C /usr/local/bin gitleaks
chmod +x /usr/local/bin/gitleaks
gitleaks version
```

## Architecture Patterns

### Recommended Project Structure

```
src/aishell/
├── cli.clj                    # ADD: gitleaks dispatch case
├── run.clj                    # MODIFY: Add freshness check before container launch
├── gitleaks/
│   ├── scan_state.clj        # NEW: Timestamp persistence (read/write/check-staleness)
│   └── warnings.clj          # NEW: Freshness warning display
├── config.clj                # MODIFY: Add :gitleaks_freshness_check to known-keys
└── util.clj                  # ALREADY HAS: state-dir function for XDG
```

### Pattern 1: Command Passthrough (Similar to Claude/OpenCode)

**What:** Add gitleaks as a pass-through command that runs inside the container with all flags forwarded.

**When to use:** For tools that need full CLI flexibility and access to mounted project directory.

**Example:**
```clojure
;; Source: cli.clj dispatch function + CONTEXT.md passthrough requirement

;; In dispatch function, before standard dispatch
(case (first clean-args)
  "claude" (run/run-container "claude" (vec (rest clean-args)) {:unsafe unsafe?})
  "opencode" (run/run-container "opencode" (vec (rest clean-args)) {:unsafe unsafe?})
  "gitleaks" (run/run-container "gitleaks" (vec (rest clean-args)) {:unsafe unsafe? :skip-pre-start true})
  ;; Standard dispatch for other commands
  ...)

;; In run.clj container-cmd building
(case cmd
  "claude" (into ["claude" "--dangerously-skip-permissions"] merged-args)
  "opencode" (into ["opencode"] merged-args)
  "gitleaks" (into ["gitleaks"] harness-args)  ; No defaults merging - pure passthrough
  ["/bin/bash"])
```

**Note:** Gitleaks runs without pre_start hooks per success criteria. Pass `:skip-pre-start true` to run function.

### Pattern 2: Per-Project State Tracking with Absolute Paths

**What:** Store scan timestamps in XDG state directory, keyed by absolute project path.

**When to use:** For per-project metadata that is personal (not shared in repo) and needs to persist across invocations.

**Example:**
```clojure
;; Source: state.clj pattern + util.clj state-dir + CONTEXT.md state persistence

(ns aishell.gitleaks.scan-state
  (:require [babashka.fs :as fs]
            [clojure.edn :as edn]
            [aishell.util :as util]))

(defn scan-state-file
  "Path to gitleaks scan state: ~/.local/state/aishell/gitleaks-scans.edn
   File format: {absolute-path {:last-scan timestamp-string}}"
  []
  (str (fs/path (util/state-dir) "gitleaks-scans.edn")))

(defn read-scan-state
  "Read all scan timestamps. Returns map or empty map if file doesn't exist."
  []
  (let [path (scan-state-file)]
    (if (fs/exists? path)
      (edn/read-string (slurp path))
      {})))

(defn write-scan-timestamp
  "Record scan timestamp for project-dir (absolute path).
   Only called after successful scan completion."
  [project-dir]
  (let [abs-path (str (fs/absolutize project-dir))
        current-state (read-scan-state)
        new-state (assoc current-state abs-path {:last-scan (str (java.time.Instant/now))})]
    (util/ensure-dir (util/state-dir))
    (spit (scan-state-file) (pr-str new-state))))

(defn get-last-scan
  "Get last scan timestamp for project-dir. Returns Instant or nil."
  [project-dir]
  (let [abs-path (str (fs/absolutize project-dir))
        state (read-scan-state)
        timestamp-str (get-in state [abs-path :last-scan])]
    (when timestamp-str
      (java.time.Instant/parse timestamp-str))))
```

### Pattern 3: Staleness Calculation with Duration

**What:** Calculate days since last scan using java.time.Duration between Instants.

**When to use:** For time-based freshness checks and threshold warnings.

**Example:**
```clojure
;; Source: Java time API documentation + CONTEXT.md 7-day threshold

(defn days-since-scan
  "Calculate days since last scan. Returns Long or nil if never scanned."
  [project-dir]
  (when-let [last-scan (get-last-scan project-dir)]
    (let [now (java.time.Instant/now)
          duration (java.time.Duration/between last-scan now)]
      (.toDays duration))))

(defn stale?
  "Check if scan is stale (older than threshold days). Default threshold: 7."
  [project-dir & [threshold]]
  (let [threshold-days (or threshold 7)
        days (days-since-scan project-dir)]
    (cond
      (nil? days) true  ; Never scanned = stale
      (>= days threshold-days) true
      :else false)))
```

### Pattern 4: Freshness Warning Display

**What:** Display actionable warning before container launch when scan is stale.

**When to use:** On every command (shell/claude/opencode) to remind users to scan.

**Example:**
```clojure
;; Source: detection/core.clj display-warnings pattern + CONTEXT.md actionable message

(ns aishell.gitleaks.warnings
  (:require [aishell.output :as output]
            [aishell.gitleaks.scan-state :as scan-state]))

(defn display-freshness-warning
  "Display warning if gitleaks scan is stale (older than threshold).
   Called before container launch. Advisory only - does not block."
  [project-dir & [threshold]]
  (let [threshold-days (or threshold 7)]
    (when (scan-state/stale? project-dir threshold-days)
      (let [days (scan-state/days-since-scan project-dir)
            never-scanned? (nil? days)]
        (println)
        (binding [*out* *err*]
          (println (str output/YELLOW "Warning:" output/NC " Gitleaks scan is "
                       (if never-scanned?
                         "missing"
                         (str "stale (" days " days old)"))))
          (println (str "Run: " output/CYAN "aishell gitleaks" output/NC " to scan for secrets"))
          (println))))))
```

### Pattern 5: Config Toggle for Disabling Warnings

**What:** Allow users to disable freshness warnings via `.aishell/config.yaml`.

**When to use:** When users want to suppress advisory warnings (e.g., on known-safe projects).

**Example:**
```yaml
# .aishell/config.yaml
gitleaks_freshness_check: false  # Disable freshness warnings
```

```clojure
;; config.clj - add to known-keys
(def known-keys
  #{:mounts :env :ports :docker_args :pre_start :extends :harness_args
    :gitleaks_freshness_check})

;; warnings.clj - check config before displaying
(defn display-freshness-warning
  [project-dir config & [threshold]]
  (when (not= false (:gitleaks_freshness_check config))  ; Default: enabled
    ;; ... warning logic
    ))
```

### Anti-Patterns to Avoid

- **Running gitleaks outside container:** Gitleaks must run inside container per success criteria (consistent environment, no host pollution)
- **Blocking on stale scan:** Freshness warning is advisory only - never block container launch
- **Hashing project paths:** Use absolute paths as-is for debuggability (user can inspect state file)
- **Storing state in .aishell/:** State is personal metadata, belongs in XDG_STATE_HOME not project directory
- **Updating timestamp on failed scans:** Only update after successful completion (regardless of findings)

## Don't Hand-Roll

Problems that look simple but have existing solutions:

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| Secret detection | Custom regex patterns | Gitleaks | 100+ built-in rules, entropy detection, maintained ruleset |
| ISO-8601 parsing | String manipulation | java.time.Instant/parse | Handles timezones, leap seconds, edge cases |
| Duration calculation | Manual timestamp math | java.time.Duration/between | Handles DST, timezone offsets, overflow |
| XDG directory resolution | Hardcoded ~/.local/state | util/state-dir with XDG_STATE_HOME | Respects user overrides, cross-platform |

**Key insight:** Gitleaks handles all content-based detection complexity (entropy thresholds, base64 decoding, archive scanning). Aishell only orchestrates execution and tracks state.

## Common Pitfalls

### Pitfall 1: Pre-Start Hooks Running with Gitleaks

**What goes wrong:** User runs `aishell gitleaks`, pre_start hooks execute (npm install, etc.), slowing down quick scans.

**Why it happens:** Default run-container behavior executes PRE_START env var in entrypoint.sh.

**How to avoid:** Pass flag to skip pre_start execution for gitleaks command. Modify entrypoint.sh to check for SKIP_PRE_START env var or modify docker run args to unset PRE_START.

**Warning signs:** Gitleaks commands taking longer than expected, seeing pre_start output before scan.

### Pitfall 2: State File Corruption from Concurrent Writes

**What goes wrong:** Two terminal windows run `aishell gitleaks` simultaneously on different projects, corrupting the shared state file.

**Why it happens:** EDN spit/slurp is not atomic - read-modify-write race condition.

**How to avoid:**
- Option 1: Separate state files per project (e.g., `.aishell/.gitleaks-state.edn`) - violates XDG pattern
- Option 2: File locking with babashka.fs (if available)
- Option 3: Accept rare corruption risk (low probability, easy recovery - just re-scan)

**Recommendation:** Document as known limitation. Users can delete state file to reset. Most users won't run concurrent scans.

**Warning signs:** Missing timestamps after concurrent runs, EDN parse errors on state file read.

### Pitfall 3: Freshness Warning Fatigue

**What goes wrong:** Users see "scan is stale" on every command, ignore it completely (warning blindness).

**Why it happens:** 7-day threshold too aggressive for infrequently-modified projects.

**How to avoid:**
- Make threshold configurable via config.yaml (e.g., `gitleaks_freshness_days: 30`)
- Only warn once per day (track "last warned" in state)
- Adjust warning prominence (use info color instead of yellow)

**Warning signs:** User feedback about annoying warnings, requests to disable.

### Pitfall 4: Gitleaks Binary Architecture Mismatch

**What goes wrong:** Download linux_x64 binary but user runs aishell on ARM architecture (Raspberry Pi, M1 Mac with Docker on Linux).

**Why it happens:** Hardcoded architecture in download URL.

**How to avoid:** Detect architecture in Dockerfile using `dpkgArch` pattern (already used for gosu installation).

**Warning signs:** "cannot execute binary file: Exec format error" when running gitleaks.

### Pitfall 5: Default to Full History Scan

**What goes wrong:** User runs `aishell gitleaks` expecting working tree scan, gets 2-hour git history scan on large repo.

**Why it happens:** Gitleaks defaults to `git` command (full history) not `dir` command (working tree).

**How to avoid:** Default to `gitleaks dir .` (working tree), require explicit `--history` flag for git history scan. Document in help output.

**Warning signs:** Slow scan times on first run, user confusion about what's being scanned.

## Code Examples

Verified patterns from official sources:

### Gitleaks Installation in Dockerfile

```dockerfile
# Source: https://github.com/gitleaks/gitleaks/releases
# Pattern: Similar to gosu installation in templates.clj

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

### Gitleaks Working Tree Scan (Default Behavior)

```bash
# Source: Context7 /gitleaks/gitleaks - dir command
# Default: scan current directory (working tree only)
gitleaks dir --verbose

# Explicit project path
gitleaks dir /path/to/project --verbose

# With JSON report
gitleaks dir --report-path findings.json --report-format json

# With custom config (auto-detected if .gitleaks.toml exists)
gitleaks dir --config .gitleaks.toml --verbose
```

### Gitleaks Git History Scan (Opt-in with --history flag)

```bash
# Source: Context7 /gitleaks/gitleaks - git command
# Scan full commit history
gitleaks git --verbose

# Scan specific commit range
gitleaks git --log-opts="--all abc123..def456"

# With baseline to ignore known findings
gitleaks git --baseline-path .gitleaks-baseline.json --report-path new-findings.json
```

### State Persistence Pattern

```clojure
;; Source: state.clj existing pattern

(ns aishell.gitleaks.scan-state
  (:require [babashka.fs :as fs]
            [clojure.edn :as edn]
            [aishell.util :as util]))

(defn scan-state-file []
  (str (fs/path (util/state-dir) "gitleaks-scans.edn")))

(defn write-scan-timestamp [project-dir]
  (let [abs-path (str (fs/absolutize project-dir))
        state (read-scan-state)
        new-state (assoc state abs-path {:last-scan (str (java.time.Instant/now))})]
    (util/ensure-dir (util/state-dir))
    (spit (scan-state-file) (pr-str new-state))))

(defn read-scan-state []
  (let [path (scan-state-file)]
    (if (fs/exists? path)
      (edn/read-string (slurp path))
      {})))
```

### Skip Pre-Start Hooks

```clojure
;; Source: CONTEXT.md requirement + entrypoint.sh PRE_START logic

;; Option 1: Unset PRE_START in docker run args
(defn build-docker-args-for-gitleaks
  [base-docker-args]
  ;; Add -e PRE_START= to unset the env var
  (concat base-docker-args ["-e" "PRE_START="]))

;; Option 2: Set SKIP_PRE_START flag (requires entrypoint.sh modification)
;; In entrypoint.sh, check: if [[ -z "${SKIP_PRE_START:-}" && -n "${PRE_START:-}" ]]; then
```

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| truffleHog | Gitleaks v8+ | 2021-2022 | Gitleaks faster, better regex engine, active maintenance |
| git-secrets | Gitleaks | 2019-2020 | Gitleaks has entropy detection, more rules, JSON output |
| Manual regex patterns | Gitleaks built-in rules | Ongoing | 100+ maintained rules vs custom patterns |
| `gitleaks --repo-path` | `gitleaks dir` / `gitleaks git` | v8.0.0 (2022) | Clearer command separation (filesystem vs git) |
| Sync scanning | Async with progress | v8.8+ | Better UX for large repos |

**Deprecated/outdated:**
- `gitleaks --repo-path`: Use `gitleaks git /path` or `gitleaks dir /path` (v8.0+)
- `--no-git` flag: Use `gitleaks dir` command instead (clearer intent)
- Configuration v1 format: Use v2 TOML format with `[[rules]]` arrays

**Current best practices (2026):**
- Default to `gitleaks dir` for working tree scans (fast, most common use case)
- Use `gitleaks git` for pre-commit/pre-push hooks (scans staged/recent commits)
- Auto-detect `.gitleaks.toml` instead of requiring --config flag
- Support `.gitleaks-baseline.json` for incremental scanning (ignore known findings)
- Use JSON output format for programmatic parsing, SARIF for GitHub integration

## Open Questions

Things that couldn't be fully resolved:

1. **Gitleaks default command behavior**
   - What we know: Gitleaks has `git` and `dir` commands. Success criteria says "one-shot scan of project directory"
   - What's unclear: Should default be `gitleaks dir .` (working tree) or `gitleaks git` (history)? Context says "default to working tree scan, --history flag for full git history"
   - Recommendation: Implement smart default - if user passes no subcommand, run `dir .`. If user passes explicit `git`, run that. Document flag mapping.

2. **Concurrent state file access**
   - What we know: EDN spit/slurp is not atomic. Babashka may or may not have file locking.
   - What's unclear: Should we implement locking, per-project state files, or accept rare corruption?
   - Recommendation: Start with simple approach (shared state file). Document as known limitation. Add locking in future if users report issues.

3. **Freshness warning threshold configurability**
   - What we know: Default 7 days per context. Config can disable entirely.
   - What's unclear: Should threshold be configurable (e.g., `gitleaks_freshness_days: 30`) or just on/off toggle?
   - Recommendation: Start with on/off toggle only. Add threshold config if users request it.

4. **Exit code behavior when secrets found**
   - What we know: Gitleaks exits 1 when secrets found, 0 when clean
   - What's unclear: Should aishell gitleaks preserve this (fail CI on findings) or always exit 0 (warnings only)?
   - Recommendation: Pure passthrough - preserve gitleaks exit codes. Users can check $? for automation. Document in help.

5. **Output format and reporting**
   - What we know: Gitleaks supports JSON, SARIF, and terminal output. Context says "Claude's discretion"
   - What's unclear: Should we default to JSON and summarize, or stream terminal output directly?
   - Recommendation: Stream terminal output directly (simpler, less processing). Users can add `--report-path findings.json` if they want files.

## Sources

### Primary (HIGH confidence)

- [Context7 /gitleaks/gitleaks](https://context7.com/gitleaks/gitleaks) - Command syntax, configuration, baseline support
- [GitHub gitleaks/gitleaks releases](https://github.com/gitleaks/gitleaks/releases) - v8.30.0 binary download URLs and formats
- [Gitleaks Official Documentation](https://gitleaks.io/) - Installation methods and Docker usage
- [Oracle Java Tutorials - Duration](https://docs.oracle.com/javase/tutorial/datetime/iso/period.html) - java.time.Duration API for timestamp math
- [XDG Base Directory Specification](https://specifications.freedesktop.org/basedir/latest/) - XDG_STATE_HOME standards

### Secondary (MEDIUM confidence)

- [Lindevs - Install Gitleaks on Ubuntu](https://lindevs.com/install-gitleaks-on-ubuntu) - Installation methods for Debian/Ubuntu
- [GitHub opendevsecops/docker-gitleaks](https://github.com/opendevsecops/docker-gitleaks) - Docker container patterns for Gitleaks
- [Medium - Mastering Time in Java](https://medium.com/@rihab.beji099/mastering-time-in-java-unlocking-the-power-of-duration-period-and-iso-8601-5443954d11bd) - ISO-8601 and Duration examples
- [dbt Freshness Monitoring](https://www.getgalaxy.io/learn/glossary/dbt-freshness-monitoring-data-staleness) - Staleness warning patterns and thresholds

### Tertiary (LOW confidence)

- [GeeksforGeeks - What is Gitleaks](https://www.geeksforgeeks.org/linux-unix/gitleaks/) - General overview
- [Flow CLI Documentation](https://data-wise.github.io/flow-cli/) - SHA-256 hashing for state tracking pattern

## Metadata

**Confidence breakdown:**
- Gitleaks installation: HIGH - Official releases page provides exact download URLs and version
- Command passthrough: HIGH - Existing pattern in cli.clj for claude/opencode
- State tracking: HIGH - Existing pattern in state.clj, XDG standards documented
- Timestamp calculation: HIGH - Java time API well-documented, tested in many applications
- Freshness warnings: MEDIUM - Pattern extrapolated from detection framework, not verified end-to-end
- Concurrent access handling: LOW - Babashka file locking capabilities unclear

**Research date:** 2026-01-23
**Valid until:** 2026-02-23 (30 days - Gitleaks is stable tool, changes infrequent)
