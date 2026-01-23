# Phase 20: Filename-based Detection - Research

**Researched:** 2026-01-23
**Domain:** File pattern matching for sensitive files (environment files, SSH keys, key containers)
**Confidence:** HIGH

## Summary

Phase 20 implements filename-based detection for sensitive files without inspecting content. This phase builds directly on Phase 19's detection framework by adding pattern matchers for three categories: (1) environment files (.env variants), (2) SSH key files (id_rsa, id_dsa, etc.), and (3) key container files (.p12, .pfx, .jks, .keystore, .ppk).

The landscape research confirms that existing tools (Gitleaks, TruffleHog) focus on content-based secret detection but don't warn about file presence. This phase fills that gap with simple filename/extension matching using babashka.fs glob patterns. The CONTEXT.md decisions establish clear constraints: scan recursively but exclude performance-heavy directories, use case-insensitive matching, group warnings to avoid spam (show ≤3 individual files, summarize if more), and include an audit command hint for truncated output.

**Primary recommendation:** Extend Phase 19's scan-project function with glob-based pattern matchers for each file category. Use babashka.fs/glob with :hidden true for dotfiles, implement case-insensitive matching via post-glob filtering (glob lacks native case-insensitive support), and apply the threshold-of-3 grouping strategy from CONTEXT.md to keep output compact.

## Standard Stack

This phase uses the existing Phase 19 infrastructure plus babashka.fs patterns. No new dependencies.

### Core

| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| babashka.fs/glob | Built-in | File pattern matching with ** recursion | Already used in Phase 19, handles hidden files |
| clojure.string | Built-in | Case-insensitive matching, path manipulation | Standard library for string operations |
| Phase 19 framework | Current | scan-project, display-warnings, severity system | Existing infrastructure ready for extension |

### Supporting

| Library | Version | Purpose | When to Use |
|---------|---------|---------|-------------|
| babashka.fs/match | Built-in | Alternative to glob if regex needed | Only if glob patterns insufficient |
| clojure.core/frequencies | Built-in | Count pattern matches for grouping | For threshold-of-3 summarization |

### Alternatives Considered

| Instead of | Could Use | Tradeoff |
|------------|-----------|----------|
| Glob post-filtering | fs/match with regex | match allows regex for case-insensitive but glob is simpler for ** patterns |
| Per-file warnings | Always summarize | CONTEXT.md decision: show ≤3 individual, 4+ summarized |
| Content inspection | Filename-only | Deferred to Phase 21 per roadmap |

## Architecture Patterns

### Recommended Extension Structure

```clojure
src/aishell/detection/
├── core.clj              # Extend scan-project with pattern calls
├── formatters.clj        # Already exists from Phase 19
└── patterns.clj          # NEW: Pattern definitions and matchers
```

### Pattern 1: Glob-based Filename Matchers

**What:** Use fs/glob with specific patterns for each file category, filter results case-insensitively.

**When to use:** For filename/extension detection without content inspection.

**Example:**
```clojure
;; Source: babashka.fs documentation + CONTEXT.md decisions

(ns aishell.detection.patterns
  (:require [babashka.fs :as fs]
            [clojure.string :as str]))

(defn- case-insensitive-name-match? [path pattern-lower]
  "Check if filename matches pattern (case-insensitive)"
  (str/includes? (str/lower-case (str (fs/file-name path))) pattern-lower))

(defn find-env-files [project-dir]
  "Find environment files: .env, .env.*, .envrc"
  (let [all-files (fs/glob project-dir "**" {:hidden true})]
    (->> all-files
         (filter #(or (case-insensitive-name-match? % ".env")
                      (case-insensitive-name-match? % ".envrc")))
         (map str))))

(defn find-ssh-keys [project-dir]
  "Find SSH key files: id_rsa, id_dsa, id_ed25519, id_ecdsa"
  (let [all-files (fs/glob project-dir "**" {:hidden true})
        key-names ["id_rsa" "id_dsa" "id_ed25519" "id_ecdsa"]]
    (->> all-files
         (filter (fn [p]
                   (let [name-lower (str/lower-case (str (fs/file-name p)))]
                     (some #(= name-lower %) key-names))))
         (map str))))

(defn find-key-containers [project-dir]
  "Find key container files: *.p12, *.pfx, *.jks, *.keystore, *.ppk"
  (concat
    (fs/glob project-dir "**/*.p12" {:hidden true})
    (fs/glob project-dir "**/*.pfx" {:hidden true})
    (fs/glob project-dir "**/*.jks" {:hidden true})
    (fs/glob project-dir "**/*.keystore" {:hidden true})
    (fs/glob project-dir "**/*.ppk" {:hidden true})))
```

### Pattern 2: Threshold-based Warning Grouping

**What:** Show files individually if ≤3 matches, summarize with count if >3.

**When to use:** Per CONTEXT.md decision to keep output compact without losing critical info.

**Example:**
```clojure
;; Source: CONTEXT.md decisions

(defn group-findings-by-pattern
  "Group findings by pattern type and apply threshold-of-3 summarization."
  [findings]
  (for [[type group] (group-by :type findings)
        :let [count (count group)]]
    (if (<= count 3)
      ;; Show individually
      group
      ;; Summarize
      [{:type type
        :severity (:severity (first group))
        :path nil  ;; No specific path for summary
        :reason (str count " files detected")
        :summary? true
        :paths (take 2 (map :path group))}])))  ;; Sample paths
```

### Pattern 3: Severity Assignment by File Type

**What:** Map file types to severity levels per requirements.

**When to use:** Convert raw matches to findings with correct severity.

**Example:**
```clojure
;; Source: REQUIREMENTS.md

(def severity-map
  {:env-file        :medium   ; ENVF-01, ENVF-02
   :env-template    :low      ; ENVF-03
   :ssh-key         :high     ; PKEY-02
   :key-container   :high     ; PKEY-03
   :pem-key         :medium}) ; PKEY-04

(defn classify-env-file [path]
  "Classify .env file as template or actual env file"
  (let [name-lower (str/lower-case (str (fs/file-name path)))]
    (if (or (str/includes? name-lower ".example")
            (str/includes? name-lower ".sample"))
      :env-template
      :env-file)))

(defn path->finding [path type]
  "Convert matched path to finding with severity"
  {:path (str path)
   :type type
   :severity (get severity-map type :low)
   :reason (type->reason type)})
```

### Pattern 4: Audit Command Hint for Truncated Output

**What:** When output is truncated, hint that audit command provides full detail.

**When to use:** Per CONTEXT.md when findings are summarized.

**Example:**
```clojure
;; Source: CONTEXT.md specific ideas

(defn display-with-audit-hint [findings]
  "Display findings with audit command hint if truncated"
  (let [truncated? (some :summary? findings)]
    (display-warnings findings)
    (when truncated?
      (binding [*out* *err*]
        (println (str output/DIM
                      "Run `aishell audit` for full untruncated output"
                      output/NC))))))
```

### Anti-Patterns to Avoid

- **Scanning node_modules or .git:** CONTEXT.md explicitly excludes these for performance
- **Content inspection in Phase 20:** Deferred to Phase 21 per roadmap
- **Blocking on medium/low findings:** Phase 19 decision - only high-severity prompts
- **Case-sensitive matching:** CONTEXT.md requires case-insensitive (.env, .ENV, .Env all match)
- **Showing all 50 .env files:** Use threshold-of-3 to keep output usable

## Don't Hand-Roll

Problems with existing solutions:

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| Case-insensitive glob | Custom glob implementation | Post-filter glob results | fs/glob doesn't support case-insensitive natively, filtering is simpler |
| File exclusion logic | Custom directory walker | Reuse Phase 19 excluded-dirs | Already implemented with common patterns |
| Pattern matching | Regex for all patterns | Glob for extensions, filter for exact names | Glob handles ** recursion cleanly |
| Warning truncation | Complex pagination | Threshold-of-3 with count | Simple, clear, meets CONTEXT.md requirement |

**Key insight:** babashka.fs/glob doesn't support case-insensitive matching natively (Java NIO limitation), so post-glob filtering with clojure.string/lower-case is the standard approach. Don't try to build a custom case-insensitive glob.

## Common Pitfalls

### Pitfall 1: Missing Hidden Files

**What goes wrong:** .env files not detected because glob doesn't check hidden files by default.
**Why it happens:** fs/glob requires explicit :hidden true option.
**How to avoid:** Always use `{:hidden true}` when scanning for dotfiles.
**Warning signs:** Detection works for env.production but not .env.

**Mitigation:**
```clojure
;; WRONG: Misses .env files
(fs/glob project-dir "**")

;; RIGHT: Catches hidden files
(fs/glob project-dir "**" {:hidden true})
```

### Pitfall 2: Case-Sensitive Matching Misses Variants

**What goes wrong:** Detects .env but not .ENV or .Env on case-insensitive filesystems.
**Why it happens:** fs/glob patterns are case-sensitive by Java NIO design.
**How to avoid:** Post-filter results with clojure.string/lower-case comparison.
**Warning signs:** Detection inconsistent across platforms (Linux vs macOS).

### Pitfall 3: Flooding User with 100+ Individual Warnings

**What goes wrong:** Project has 50 .env.local files (monorepo), output is unusable.
**Why it happens:** Showing every match without grouping.
**How to avoid:** Implement threshold-of-3 from CONTEXT.md: individual if ≤3, summarized if >3.
**Warning signs:** Terminal scrollback filled with repetitive warnings.

### Pitfall 4: Excluding Too Many Directories

**What goes wrong:** User has secrets in build/credentials.json, not detected.
**Why it happens:** Over-aggressive exclusion list.
**How to avoid:** CONTEXT.MD decision: exclude common dep dirs (node_modules, vendor, .git) but not all build outputs.
**Warning signs:** Known sensitive files in project not detected.

**Balanced exclusion list:**
```clojure
;; Phase 19 excluded-dirs is already well-balanced:
#{".git" "node_modules" "vendor" "target" "build" "dist"
  "__pycache__" ".venv" "venv" ".bundle"}

;; Don't add more without evidence of performance issues
```

### Pitfall 5: Symlink Loops

**What goes wrong:** Scan hangs or runs forever following circular symlinks.
**Why it happens:** fs/glob follows symlinks when directory contains circular references.
**How to avoid:** CONTEXT.md decision: Claude's discretion on symlinks, likely skip (Phase 19 default :follow-links false).
**Warning signs:** Scan takes >5 seconds on small projects.

**Safe default:**
```clojure
;; Phase 19 infrastructure already safe - :follow-links defaults to false
(fs/glob project-dir "**" {:hidden true})  ;; Does NOT follow symlinks
```

## Code Examples

### Complete Pattern Detector Structure

```clojure
;; Source: Phase 19 research + CONTEXT.md decisions

(ns aishell.detection.patterns
  "Pattern definitions and matching functions for sensitive files."
  (:require [babashka.fs :as fs]
            [clojure.string :as str]))

(defn- in-excluded-dir?
  "Reuse Phase 19 exclusion logic"
  [path excluded-dirs]
  (let [path-str (str path)]
    (some #(str/includes? path-str (str "/" % "/")) excluded-dirs)))

(defn- case-insensitive-basename-match? [path pattern-lower]
  "Check if filename matches pattern (case-insensitive)"
  (str/includes? (str/lower-case (str (fs/file-name path))) pattern-lower))

(defn detect-env-files
  "Detect .env files (medium severity) and templates (low severity).
   Returns vector of findings."
  [project-dir excluded-dirs]
  (let [all-files (fs/glob project-dir "**" {:hidden true})
        filtered (remove #(in-excluded-dir? % excluded-dirs) all-files)
        env-files (filter #(let [name (str/lower-case (str (fs/file-name %)))]
                             (or (= name ".env")
                                 (str/starts-with? name ".env.")
                                 (= name ".envrc")))
                          filtered)]
    (for [path env-files]
      (let [name-lower (str/lower-case (str (fs/file-name path)))
            is-template? (or (str/includes? name-lower "example")
                             (str/includes? name-lower "sample"))]
        {:path (str path)
         :type (if is-template? :env-template :env-file)
         :severity (if is-template? :low :medium)
         :reason (if is-template?
                   "Environment template file"
                   "Environment configuration file")}))))

(defn detect-ssh-keys
  "Detect SSH key files by name (high severity).
   Returns vector of findings."
  [project-dir excluded-dirs]
  (let [all-files (fs/glob project-dir "**" {:hidden true})
        filtered (remove #(in-excluded-dir? % excluded-dirs) all-files)
        key-names ["id_rsa" "id_dsa" "id_ed25519" "id_ecdsa"]
        ssh-keys (filter (fn [p]
                           (let [name-lower (str/lower-case (str (fs/file-name p)))]
                             (some #(= name-lower %) key-names)))
                         filtered)]
    (for [path ssh-keys]
      {:path (str path)
       :type :ssh-key
       :severity :high
       :reason "SSH private key file"})))

(defn detect-key-containers
  "Detect key container files by extension (high severity).
   Returns vector of findings."
  [project-dir excluded-dirs]
  (let [extensions [".p12" ".pfx" ".jks" ".keystore" ".ppk"]
        all-matches (mapcat (fn [ext]
                              (fs/glob project-dir (str "**/*" ext) {:hidden true}))
                            extensions)
        filtered (remove #(in-excluded-dir? % excluded-dirs) all-matches)]
    (for [path filtered]
      {:path (str path)
       :type :key-container
       :severity :high
       :reason "Key container file (PKCS12/JKS/PuTTY)"})))

(defn detect-pem-key-files
  "Detect .pem and .key files (medium severity - may be certs).
   Returns vector of findings."
  [project-dir excluded-dirs]
  (let [pem-files (fs/glob project-dir "**/*.pem" {:hidden true})
        key-files (fs/glob project-dir "**/*.key" {:hidden true})
        all-matches (concat pem-files key-files)
        filtered (remove #(in-excluded-dir? % excluded-dirs) all-matches)]
    (for [path filtered]
      {:path (str path)
       :type :pem-key
       :severity :medium
       :reason "PEM/key file (may contain private key or certificate)"})))
```

### Threshold-based Grouping Implementation

```clojure
;; Source: CONTEXT.MD threshold-of-3 decision

(defn group-findings
  "Group findings by type and apply threshold-of-3 summarization.
   Returns seq of findings (individual or summary)."
  [findings]
  (let [by-type (group-by :type findings)]
    (mapcat
      (fn [[type group]]
        (if (<= (count group) 3)
          ;; Show individually
          group
          ;; Summarize with sample paths
          [{:type type
            :severity (:severity (first group))
            :path nil
            :reason (str (count group) " files detected")
            :summary? true
            :sample-paths (take 2 (map :path group))}]))
      by-type)))
```

### Integration into Phase 19 scan-project

```clojure
;; Source: Phase 19 core.clj + Phase 20 patterns

(ns aishell.detection.core
  (:require [babashka.fs :as fs]
            [aishell.detection.formatters :as formatters]
            [aishell.detection.patterns :as patterns]))

(defn scan-project
  "Scan project directory for sensitive files.
   Returns vector of findings: [{:path :type :severity :reason}]"
  [project-dir]
  ;; Phase 19 TODO: Phase 20 adds filename pattern detection
  (let [all-findings (concat
                       (patterns/detect-env-files project-dir excluded-dirs)
                       (patterns/detect-ssh-keys project-dir excluded-dirs)
                       (patterns/detect-key-containers project-dir excluded-dirs)
                       (patterns/detect-pem-key-files project-dir excluded-dirs))]
    ;; Apply grouping for output
    (patterns/group-findings all-findings)))
```

### Enhanced Formatter for Summary Findings

```clojure
;; Source: Phase 19 formatters.clj extension

(ns aishell.detection.formatters
  (:require [aishell.output :as output]))

;; ... existing severity-config and format-severity-label ...

(defmethod format-finding :default
  [{:keys [severity path reason summary? sample-paths]}]
  (if summary?
    ;; Summary format: "  HIGH 15 files detected (e.g., .env, .env.local)"
    (str "  "
         (format-severity-label severity)
         " "
         reason
         (when (seq sample-paths)
           (str " (e.g., " (clojure.string/join ", " (map fs/file-name sample-paths)) ")")))
    ;; Individual format: "  HIGH path - reason"
    (str "  "
         (format-severity-label severity)
         " "
         path
         (when reason (str " - " reason)))))
```

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| Content-only scanning | Filename + content detection | 2026 (this project) | Warns about file presence before content inspection |
| Case-sensitive globs | Post-filter with lower-case | babashka.fs design | Cross-platform consistency (.env = .ENV) |
| Show all matches | Threshold-based grouping | UX research 2020s+ | Prevents warning fatigue |
| Hardcoded patterns | Multimethod dispatch | Phase 19 design | Extensible for Phase 21+ content detection |

**Deprecated/outdated:**
- Java File.listFiles() recursion: Use babashka.fs/glob with ** patterns
- Custom directory exclusion: Reuse Phase 19's excluded-dirs set
- Regex-based filename matching: Use glob for extensions, filter for exact names (simpler)

## Open Questions

1. **Exact Threshold for Truncation**
   - What we know: CONTEXT.md says ≤3 individual, >3 summarized
   - What's unclear: Should summary show sample paths? How many samples?
   - Recommendation: Show count + 2 sample paths (Claude's discretion per CONTEXT.md)

2. **PEM File Ambiguity**
   - What we know: .pem can be certificates (public) or private keys
   - What's unclear: Should we always warn or only after content check?
   - Recommendation: Warn at medium severity (Phase 20), upgrade to high if content matches in Phase 21

3. **.env.example in Root vs Nested**
   - What we know: Root .env.example is a template (low severity)
   - What's unclear: What about nested-service/.env.example in monorepos?
   - Recommendation: All .env.example are low severity regardless of location (consistent rule)

4. **Audit Command Scope**
   - What we know: CONTEXT.md suggests audit command for full output
   - What's unclear: Is this Phase 20 or a separate phase?
   - Recommendation: Deferred per CONTEXT.md - implement after Phase 20 as quick task or Phase 23.5

## Sources

### Primary (HIGH confidence)
- [babashka/fs GitHub API.md](https://github.com/babashka/fs/blob/master/API.md) - Official glob and match API documentation
- [babashka/fs glob function](/babashka/fs Context7 query) - glob options, hidden files, recursive patterns
- Phase 19 research and implementation - Detection framework, excluded-dirs, severity system
- [REQUIREMENTS.md](/home/jonasrodrigues/projects/harness/.planning/REQUIREMENTS.md) - ENVF-01/02/03, PKEY-02/03/04 requirements
- [20-CONTEXT.md](/home/jonasrodrigues/projects/harness/.planning/phases/20-filename-detection/20-CONTEXT.md) - Threshold-of-3, case-insensitive, scan scope decisions

### Secondary (MEDIUM confidence)
- [GitGuardian: Secure Your Secrets with .env](https://blog.gitguardian.com/secure-your-secrets-with-env/) - Environment file security best practices
- [Medium: Exposing Sensitive Data from .env](https://medium.com/@360Security/exposing-sensitive-data-from-env-1b0104b2cf65) - .env exposure risks
- [Vite: Env Variables and Modes](https://vite.dev/guide/env-and-mode) - .env variant purposes (.env.local, .env.production)
- [Aayu Technologies: Different Keystore Types](https://aayutechnologies.com/blog/different-types-of-keystores/) - .p12, .pfx, .jks, .keystore formats
- [PKCS 12 - Wikipedia](https://en.wikipedia.org/wiki/PKCS_12) - .p12/.pfx format definition
- [Gitleaks GitHub Config](https://github.com/gitleaks/gitleaks/blob/master/config/gitleaks.toml) - Pattern reference for SSH key detection
- [GitHub Docs: Excluding folders from secret scanning](https://docs.github.com/en/code-security/secret-scanning/using-advanced-secret-scanning-and-push-protection-features/excluding-folders-and-files-from-secret-scanning) - Directory exclusion best practices

### Tertiary (LOW confidence)
- [Sensitive File Detection Tools Landscape](/home/jonasrodrigues/projects/harness/artifacts/research/2026-01-23-sensitive-file-detection-tools-landscape.md) - Custom research confirming filename detection gap

## Metadata

**Confidence breakdown:**
- Standard stack (babashka.fs): HIGH - Official documentation, already used in Phase 19
- Architecture (threshold-of-3): HIGH - CONTEXT.md explicit decision
- Patterns (.env variants): HIGH - Verified with Vite, Next.js, Expo docs
- Patterns (SSH keys): HIGH - Standard filenames (id_rsa, id_dsa, id_ed25519, id_ecdsa)
- Patterns (key containers): HIGH - Industry standard extensions (.p12, .pfx, .jks, .keystore, .ppk)
- Case-insensitive approach: MEDIUM - Post-filter workaround due to fs/glob limitation
- Pitfalls: HIGH - Based on Phase 19 implementation experience and babashka.fs docs

**Research date:** 2026-01-23
**Valid until:** 90 days (stable Babashka patterns, CONTEXT.md decisions unlikely to change)
