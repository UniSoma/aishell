# Phase 23: Context & Configuration - Research

**Researched:** 2026-01-24
**Domain:** YAML configuration, gitignore detection, glob pattern matching
**Confidence:** HIGH

## Summary

This phase adds configuration and context awareness to the existing filename detection system (phases 19-21). The core technical challenges involve:

1. **Safe YAML parsing** - The project already uses clj-yaml 1.0.29, which provides robust security controls against arbitrary object instantiation via YAML tags
2. **Gitignore detection** - Git's `check-ignore` command provides authoritative gitignore status with simple exit-code-based programmatic usage
3. **Glob pattern matching** - babashka.fs 0.5.30 is already available with comprehensive glob support for custom patterns and allowlisting

The standard approach is to extend the existing config.clj merge strategy (already handling global/project merge) with new configuration keys for detection patterns and allowlists. Gitignore status requires shelling out to `git check-ignore`, which is the recommended approach over hand-rolling gitignore pattern parsing.

**Primary recommendation:** Use git check-ignore for gitignore detection (don't parse .gitignore), leverage existing config merge infrastructure, and ensure safe YAML parsing (clj-yaml defaults are already safe).

## Standard Stack

The established libraries/tools for this domain:

### Core
| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| clj-commons/clj-yaml | 1.0.29 | YAML parsing/generation | Official Clojure YAML library, built into babashka, safe defaults |
| babashka/fs | 0.5.30 | Glob pattern matching | Built into babashka, supports full glob syntax with options |
| git check-ignore | git 2.x | Gitignore status checking | Authoritative source, respects all gitignore precedence rules |

### Supporting
| Library | Version | Purpose | When to Use |
|---------|---------|---------|-------------|
| babashka.process | 0.6.25 | Shell command execution | For invoking `git check-ignore` |

### Alternatives Considered
| Instead of | Could Use | Tradeoff |
|------------|-----------|----------|
| git check-ignore | Manual .gitignore parsing | Manual parsing misses precedence rules, global ignores, .git/info/exclude - always prefer git check-ignore |
| babashka.fs glob | Custom pattern matching | Glob syntax is standard and well-tested - custom implementation adds complexity |

**Installation:**
All dependencies already present in babashka 1.12.214 (no additional installation needed).

## Architecture Patterns

### Recommended Configuration Structure

```yaml
# .aishell/config.yaml or ~/.aishell/config.yaml

# Global kill switch
detection:
  enabled: true  # false to disable all filename detection

  # Custom patterns (additive to defaults)
  custom_patterns:
    "*.api-key":
      severity: high
      description: "API key files"  # optional but recommended
    "**/credentials/*.json":
      severity: high
      description: "Credential JSON files"
    "test-secrets.*":
      severity: low
      description: "Test secret files"

  # Allowlist (exact paths or globs)
  allowlist:
    - path: "config/secrets.example.json"
      reason: "Example file with fake credentials"
    - path: "**/test/fixtures/*.pem"
      reason: "Test fixtures with dummy certificates"
```

### Pattern 1: Configuration Key Design
**What:** Namespace detection settings under `:detection` key in config
**When to use:** Keeps detection-related config grouped, avoids collisions with existing keys
**Example:**
```clojure
;; config.clj - extend known-keys
(def known-keys
  #{:mounts :env :ports :docker_args :pre_start :extends :harness_args
    :gitleaks_freshness_check
    :detection})  ; new key

;; Merge strategy: detection is a map that shallow-merges (project overrides global)
(def map-keys #{:env :detection})
```

### Pattern 2: Safe YAML Parsing (Default Behavior)
**What:** Use clj-yaml's default safe mode (no :unsafe flag)
**When to use:** Always - user config files are untrusted input
**Example:**
```clojure
;; Source: https://cljdoc.org/d/clj-commons/clj-yaml/1.0.28/api/clj-yaml.core
(yaml/parse-string (slurp config-path))
;; NO :unsafe flag - defaults to safe mode
;; Throws on unknown tags (prevents Java object instantiation)
```

### Pattern 3: Gitignore Status Checking
**What:** Shell out to `git check-ignore -q` for authoritative status
**When to use:** Before displaying warnings for high-severity files
**Example:**
```clojure
;; Source: https://git-scm.com/docs/git-check-ignore
(require '[babashka.process :as p])

(defn gitignored?
  "Check if file is in gitignore. Returns true if ignored, false if not.
   Returns nil if not in a git repo or path doesn't exist."
  [project-dir file-path]
  (try
    (let [result (p/shell {:dir project-dir
                          :out :string
                          :err :string
                          :continue true}
                         "git" "check-ignore" "-q" file-path)]
      (zero? (:exit result)))  ; exit 0 = ignored
    (catch Exception _e
      nil)))  ; not a git repo or other error
```

### Pattern 4: Glob Pattern Matching for Custom Patterns
**What:** Use babashka.fs/glob or fs/match for testing files against patterns
**When to use:** Matching custom patterns and allowlist entries
**Example:**
```clojure
;; Source: https://github.com/babashka/fs
(require '[babashka.fs :as fs])

;; For exact patterns
(fs/match path "glob:*.api-key")

;; For multi-file search
(fs/glob project-dir "**/*.api-key" {:hidden true})

;; Allowlist matching (check if path matches any allowlist glob)
(defn allowlisted?
  [path allowlist-patterns]
  (some #(fs/match path (str "glob:" %)) allowlist-patterns))
```

### Pattern 5: Additive Pattern Merging
**What:** Merge custom patterns with defaults, never replace
**When to use:** Loading custom patterns from config
**Example:**
```clojure
(defn merge-custom-patterns
  "Merge custom patterns from config with default patterns.
   Custom patterns are additive - they extend, never replace."
  [default-patterns custom-patterns-config]
  (reduce
    (fn [acc [pattern-str {:keys [severity description]}]]
      (conj acc {:pattern pattern-str
                 :severity (keyword severity)
                 :description (or description "Custom pattern")
                 :custom? true}))
    default-patterns
    custom-patterns-config))
```

### Anti-Patterns to Avoid
- **Parsing .gitignore manually:** Git's precedence rules are complex (local, global, .git/info/exclude, per-directory). Always use `git check-ignore`.
- **Using :unsafe in yaml/parse-string:** Opens YAML tag-based RCE vulnerability. Never use for user config.
- **Replacing default patterns:** Custom patterns should extend, not replace - users expect SSH keys etc. to still be detected.
- **Allowlist without audit trail:** Require a `reason` field - creates accountability and helps future maintainers understand exceptions.

## Don't Hand-Roll

Problems that look simple but have existing solutions:

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| Gitignore parsing | Custom .gitignore parser | `git check-ignore` | Complex precedence rules, multiple ignore sources, negation patterns, per-directory rules |
| Glob matching | Regex-based filename matching | babashka.fs/glob or fs/match | Handles `**` recursion, character classes, escaping, hidden files, follows platform conventions |
| YAML tag filtering | Custom tag validator | clj-yaml default mode | Already prevents unsafe tags, handles edge cases, battle-tested |
| Pattern matching optimization | Cache compiled regexes | Use fs/match per-file | Glob matching is already fast, premature optimization adds complexity |

**Key insight:** Gitignore semantics are deceptively complex. A file might be ignored via global config, .git/info/exclude, or per-directory rules. The only authoritative answer is `git check-ignore`.

## Common Pitfalls

### Pitfall 1: Glob Pattern Case Sensitivity
**What goes wrong:** Glob patterns are case-sensitive by default, users expect case-insensitive matching on case-insensitive filesystems (Windows, macOS default)
**Why it happens:** Java NIO glob is case-sensitive, pattern `*.PEM` won't match `file.pem`
**How to avoid:** Document that patterns are case-sensitive, or normalize patterns and filenames to lowercase for comparison
**Warning signs:** User reports "pattern doesn't match" on Windows/macOS when case differs

### Pitfall 2: Hidden File Matching
**What goes wrong:** Globs don't match dotfiles unless pattern starts with `.`
**Why it happens:** Standard glob behavior (POSIX convention)
**How to avoid:** When using babashka.fs/glob, always pass `:hidden true` option for detection use case. For user patterns, document that `.` prefix is required.
**Warning signs:** User patterns like `*secret*` don't match `.secrets` file

### Pitfall 3: Git Check-Ignore in Non-Git Directories
**What goes wrong:** `git check-ignore` fails when project isn't a git repo, code crashes
**Why it happens:** Git commands require .git directory
**How to avoid:** Catch exceptions, treat non-git-repos as "all files unprotected" (matches user decision: "if no .gitignore, treat all as unprotected")
**Warning signs:** Errors when running in non-git directories

### Pitfall 4: Path vs Filename Confusion in Allowlist
**What goes wrong:** User specifies filename in allowlist, expects it to match anywhere in tree, but exact path matching requires full path
**Why it happens:** Ambiguity between "match this filename anywhere" vs "match this exact path"
**How to avoid:** Support both - exact paths (relative to project root) and glob patterns. Document with examples.
**Warning signs:** User says "allowlist isn't working" when they specified `secrets.json` but file is at `config/secrets.json`

### Pitfall 5: Severity Validation
**What goes wrong:** User specifies invalid severity in custom patterns (typo like "hgh" or "critical"), pattern is ignored silently or crashes
**Why it happens:** No validation of severity values
**How to avoid:** Validate severity is one of `high`, `medium`, `low` when parsing config. Warn and skip invalid patterns.
**Warning signs:** Custom patterns mysteriously don't appear in output

### Pitfall 6: YAML Merge Key Confusion
**What goes wrong:** User tries to use YAML anchors/merge keys (`<<:`) expecting them to work across files, but they're file-scoped
**Why it happens:** YAML anchors only work within a single document
**How to avoid:** Document that global/project merge happens at config level, not YAML level. Use `extends: global` for file merging.
**Warning signs:** User reports "YAML anchor doesn't work" when defined in global, used in project config

### Pitfall 7: Relative Path Resolution
**What goes wrong:** Allowlist paths are ambiguous - relative to what? Project root? Config file location?
**Why it happens:** No clear specification of path resolution base
**How to avoid:** Document that all paths in allowlist are relative to project root (not config file location). Convert to absolute paths internally for matching.
**Warning signs:** Allowlist works in project config but not global config (or vice versa)

## Code Examples

Verified patterns from official sources:

### Safe YAML Parsing with Validation
```clojure
;; Source: https://cljdoc.org/d/clj-commons/clj-yaml/1.0.28/api/clj-yaml.core
(require '[clj-yaml.core :as yaml])

(defn parse-detection-config
  "Parse detection section from config. Returns nil on error."
  [config-map]
  (when-let [detection (:detection config-map)]
    (when-not (map? detection)
      (throw (ex-info "detection must be a map" {:got (type detection)})))

    ;; Validate enabled flag
    (when (contains? detection :enabled)
      (when-not (boolean? (:enabled detection))
        (throw (ex-info "detection.enabled must be true/false"
                       {:got (:enabled detection)}))))

    ;; Validate custom patterns
    (when-let [patterns (:custom_patterns detection)]
      (doseq [[pattern opts] patterns]
        (when-not (map? opts)
          (throw (ex-info "Pattern options must be a map"
                         {:pattern pattern :got (type opts)})))
        (when-let [sev (:severity opts)]
          (when-not (#{:high :medium :low "high" "medium" "low"} sev)
            (throw (ex-info "Invalid severity"
                           {:pattern pattern :severity sev}))))))

    detection))
```

### Git Check-Ignore with Proper Error Handling
```clojure
;; Source: https://git-scm.com/docs/git-check-ignore
(require '[babashka.process :as p])

(defn git-check-ignore
  "Check if file is gitignored. Returns:
   - true: file is ignored
   - false: file is NOT ignored
   - nil: not a git repo or error"
  [project-dir file-path]
  (try
    (let [result (p/shell {:dir project-dir
                          :out :string
                          :err :string
                          :continue true}
                         "git" "check-ignore" "-q"
                         (str file-path))]
      (case (:exit result)
        0 true    ; exit 0 = file is ignored
        1 false   ; exit 1 = file is NOT ignored
        nil))     ; other exit = error
    (catch Exception _e
      nil)))      ; not a git repo or other error

(defn warn-if-unprotected
  "Append '(risk: may be committed)' to high-severity findings
   that are NOT in .gitignore."
  [finding project-dir]
  (if (= :high (:severity finding))
    (let [ignored? (git-check-ignore project-dir (:path finding))]
      (if (false? ignored?)  ; explicitly false (not nil)
        (update finding :reason str " (risk: may be committed)")
        finding))
    finding))
```

### Glob Pattern Matching for Custom Patterns
```clojure
;; Source: https://github.com/babashka/fs
(require '[babashka.fs :as fs])

(defn detect-custom-pattern
  "Detect files matching custom glob pattern.
   Returns vector of findings."
  [project-dir pattern-str severity description excluded-dirs]
  (let [matches (fs/glob project-dir pattern-str {:hidden true})
        filtered (remove #(in-excluded-dir? % excluded-dirs) matches)]
    (for [path filtered]
      {:path (str path)
       :type :custom-pattern
       :severity severity
       :reason (or description "Custom pattern match")
       :pattern pattern-str})))

(defn file-allowlisted?
  "Check if file path matches any allowlist pattern.
   Supports both exact paths and glob patterns."
  [file-path allowlist-entries project-dir]
  (let [;; Normalize to absolute path for comparison
        abs-path (fs/absolutize file-path)
        project-abs (fs/absolutize project-dir)
        rel-path (str (fs/relativize project-abs abs-path))]
    (some
      (fn [{:keys [path]}]
        (or
          ;; Exact path match (relative to project root)
          (= path rel-path)
          ;; Glob pattern match
          (fs/match rel-path (str "glob:" path))))
      allowlist-entries)))
```

### Configuration Merge Strategy Extension
```clojure
;; Extend existing config.clj merge strategy

;; Add :detection to known-keys
(def known-keys
  #{:mounts :env :ports :docker_args :pre_start :extends :harness_args
    :gitleaks_freshness_check
    :detection})

;; Add :detection to map-keys (shallow merge strategy)
(def map-keys #{:env :detection})

;; Detection-specific merge happens automatically via merge-configs
;; Project detection.custom_patterns overrides global patterns of same name
;; Project detection.allowlist merges with global allowlist (both apply)

(defn merge-detection
  "Custom merge for detection config.
   - enabled: project wins (scalar)
   - custom_patterns: project overrides global (per-pattern, map merge)
   - allowlist: concatenate (both apply, list merge)"
  [global-detection project-detection]
  (let [enabled (if (contains? project-detection :enabled)
                 (:enabled project-detection)
                 (:enabled global-detection))
        patterns (merge (:custom_patterns global-detection)
                       (:custom_patterns project-detection))
        allowlist (vec (concat (:allowlist global-detection [])
                              (:allowlist project-detection [])))]
    (cond-> {}
      (some? enabled) (assoc :enabled enabled)
      (seq patterns) (assoc :custom_patterns patterns)
      (seq allowlist) (assoc :allowlist allowlist))))
```

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| Manual .gitignore parsing | git check-ignore command | Always standard | Authoritative, handles all edge cases |
| Custom glob implementation | java.nio.file.PathMatcher (via fs/glob) | Java 7+ (2011) | Standard, cross-platform, well-tested |
| :unsafe YAML by default | Safe mode by default | clj-yaml 1.x | Prevents YAML deserialization attacks |
| String severity values | Keyword severity | Phase 19 | Type-safe, easier to work with |

**Deprecated/outdated:**
- Manual gitignore parsing libraries (e.g., various language-specific parsers) - always prefer git check-ignore
- YAML libraries without tag safety controls - clj-yaml's safe defaults are required
- Single-level config (no global/project) - already implemented in phase 9

## Open Questions

Things that couldn't be fully resolved:

1. **Performance of git check-ignore at scale**
   - What we know: git check-ignore is fast for single files, unknown for thousands of files
   - What's unclear: Whether to batch calls (multiple files per invocation) or call once per file
   - Recommendation: Start with one-call-per-file (simpler), optimize if performance issue emerges. Only check high-severity files (decision from CONTEXT.md) which limits scale.

2. **Allowlist path normalization on Windows**
   - What we know: Windows uses backslashes, POSIX uses forward slashes
   - What's unclear: Whether babashka.fs handles path separator normalization in fs/match
   - Recommendation: Test on Windows, normalize separators if needed. Document that allowlist paths should use forward slashes (POSIX style) for cross-platform configs.

3. **Custom pattern description field optionality**
   - What we know: User decided description is optional (from CONTEXT.md "Claude's Discretion")
   - What's unclear: What default to use when omitted
   - Recommendation: Use "Custom pattern match" as generic default, show pattern string in output for clarity.

## Sources

### Primary (HIGH confidence)
- [clj-commons/clj-yaml API documentation](https://cljdoc.org/d/clj-commons/clj-yaml/1.0.28/api/clj-yaml.core) - Safe YAML parsing options and security controls
- [git-check-ignore official documentation](https://git-scm.com/docs/git-check-ignore) - Exit status and usage for gitignore checking
- [babashka.fs GitHub repository](https://github.com/babashka/fs) - Glob pattern matching and file system utilities
- [babashka.fs API documentation](https://github.com/babashka/fs/blob/master/API.md) - Detailed glob options
- Project source code (config.clj, detection/patterns.clj) - Current implementation patterns

### Secondary (MEDIUM confidence)
- [How gitignore works - GitIgnore.pro](https://gitignore.pro/guides/how-does-gitignore-work) - Gitignore pattern matching semantics
- [Fast String Matching with Globs - CodeProject](https://www.codeproject.com/Articles/5163931/Fast-String-Matching-with-Wildcards-Globs-and-Giti) - Glob algorithm complexity and pitfalls
- [VS Code glob patterns documentation](https://code.visualstudio.com/docs/editor/glob-patterns) - Common glob gotchas
- [Babashka book](https://book.babashka.org/) - Built-in library overview

### Tertiary (LOW confidence)
- WebSearch results for YAML security best practices - General security guidance
- WebSearch results for configuration merging strategies - Architecture patterns

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH - All libraries already in use (clj-yaml 1.0.29, babashka.fs 0.5.30, git check-ignore), verified versions
- Architecture: HIGH - Extends existing config.clj patterns, git check-ignore is authoritative, glob matching is standard
- Pitfalls: MEDIUM - Based on general glob/git knowledge and official docs, not phase-specific experience

**Research date:** 2026-01-24
**Valid until:** 2026-03-24 (60 days - stable domain, infrequent changes)
