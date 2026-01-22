# Phase 19: Core Detection Framework - Research

**Researched:** 2026-01-22
**Domain:** Babashka/Clojure file scanning, CLI patterns, terminal output formatting
**Confidence:** HIGH

## Summary

This phase establishes the core detection framework for warning users about sensitive files before container execution. The research focuses on four areas: (1) file system traversal using babashka.fs, (2) extensible detection architecture using Clojure multimethods, (3) terminal output formatting with severity-based colors, and (4) user confirmation prompts for high-severity warnings.

The existing codebase provides a solid foundation. The `aishell.validation` namespace already implements the advisory warning pattern with `warn-dangerous-args` and `warn-dangerous-mounts` - this phase extends that pattern to file content detection. The `aishell.output` namespace defines ANSI color codes (RED, YELLOW, CYAN) that can be extended for severity tiers.

**Primary recommendation:** Use `babashka.fs/glob` for file discovery with patterns, Clojure multimethods for extensible detector dispatch, extend `aishell.output` with severity-specific formatters, and implement y/n confirmation using `read-line` with `flush` for high-severity warnings.

## Standard Stack

This phase uses Babashka built-ins and existing project namespaces. No new dependencies required.

### Core

| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| babashka.fs | Built-in | File system traversal, glob patterns | Already used in project, wraps java.nio |
| clojure.core/defmulti | Built-in | Extensible detector dispatch | Clean way to add new detection types in later phases |
| read-line | Built-in | User confirmation input | Standard Clojure/Babashka for stdin |

### Supporting

| Library | Version | Purpose | When to Use |
|---------|---------|---------|-------------|
| clojure.string | Built-in | String manipulation | Truncation, formatting |
| babashka.process | Built-in | Shell command execution | `git check-ignore` for gitignore checking (Phase 23) |

### Alternatives Considered

| Instead of | Could Use | Tradeoff |
|------------|-----------|----------|
| defmulti dispatch | Protocols | Multimethods better for value-based dispatch (severity, pattern type) |
| fs/glob | fs/walk-file-tree | walk-file-tree faster for large dirs, but glob simpler for pattern matching |
| read-line | dialog CLI | read-line is built-in, dialog requires external dependency |

## Architecture Patterns

### Recommended Project Structure

```
src/aishell/
├── detection/
│   ├── core.clj          # scan-project, display-warnings, confirm-proceed
│   ├── patterns.clj      # Pattern definitions (placeholder, populated Phase 20+)
│   └── formatters.clj    # Severity-specific terminal output
├── cli.clj               # Add --unsafe flag handling
├── run.clj               # Hook detection before container start
└── output.clj            # Extended with severity colors
```

### Pattern 1: Multimethod-based Detection

**What:** Use `defmulti` with dispatch on finding type for extensible detection.

**When to use:** When you need to add new detection types without modifying existing code.

**Why:** Phases 20-22 will add environment files, SSH keys, cloud credentials, etc. Multimethods allow clean extension.

**Example:**
```clojure
;; Source: https://clojure.org/reference/multimethods

;; Dispatch on finding type
(defmulti format-finding :type)

(defmethod format-finding :env-file [{:keys [path reason]}]
  {:severity :medium
   :path path
   :reason reason})

(defmethod format-finding :private-key [{:keys [path]}]
  {:severity :high
   :path path
   :reason "Private key file"})

;; Default for unknown types
(defmethod format-finding :default [finding]
  {:severity :low
   :path (:path finding)
   :reason "Unknown sensitive file"})
```

### Pattern 2: Severity-based Display with Grouping

**What:** Group findings by severity, display high first with visual emphasis.

**When to use:** When presenting multiple warnings to users.

**Example:**
```clojure
;; Source: CONTEXT.md decisions

(def severity-order {:high 0 :medium 1 :low 2})

(defn group-by-severity [findings]
  (->> findings
       (group-by :severity)
       (sort-by (comp severity-order key))))

(defn display-warnings [findings]
  (println)
  (println (str BOLD "Sensitive files detected" NC))
  (println (str (apply str (repeat 40 "-"))))
  (doseq [[severity group] (group-by-severity findings)]
    (doseq [{:keys [path reason]} group]
      (println (format-finding-line severity path reason)))))
```

### Pattern 3: Confirmation Flow with Early Exit

**What:** High-severity warnings require y/n confirmation; medium/low proceed automatically.

**When to use:** Per CONTEXT.md decision - balance user awareness with workflow friction.

**Example:**
```clojure
;; Source: Babashka book + CONTEXT.md decisions

(defn prompt-yn [message]
  (print (str message " (y/n): "))
  (flush)  ; Critical: ensure prompt displays before read-line
  (let [response (read-line)]
    (= (clojure.string/lower-case (or response "")) "y")))

(defn confirm-if-needed [findings]
  (let [has-high? (some #(= :high (:severity %)) findings)]
    (if has-high?
      (if (prompt-yn "High-severity findings detected. Proceed anyway?")
        true
        (do (println "Aborted by user.")
            (System/exit 0)))
      true)))  ; Auto-proceed for medium/low only
```

### Pattern 4: --unsafe Flag for CI/Automation

**What:** Flag to skip all detection warnings.

**When to use:** CI pipelines or automation that knows the risks.

**Example:**
```clojure
;; In cli.clj

(def run-spec
  {:unsafe {:coerce :boolean :desc "Skip sensitive file warnings"}})

;; In run.clj

(defn run-container [cmd harness-args opts]
  ;; ... existing code ...
  (when-not (:unsafe opts)
    (let [findings (detection/scan-project project-dir)]
      (when (seq findings)
        (detection/display-warnings findings)
        (detection/confirm-if-needed findings))))
  ;; ... proceed to container ...
  )
```

### Anti-Patterns to Avoid

- **Blocking by default:** Per CONTEXT.md, never block - user always can proceed
- **Scanning home directory:** Only scan project dir and explicit mounts
- **Hard-coded patterns in core:** Use multimethods for extensibility
- **Silent on no findings:** Correct behavior - no noise when no warnings

## Don't Hand-Roll

Problems with existing solutions:

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| File glob matching | Custom regex walking | `babashka.fs/glob` | Handles edge cases, follows symlinks optionally |
| Gitignore checking | Parse .gitignore manually | `git check-ignore -q` | Git's parser handles all edge cases, nested .gitignore files |
| ANSI color stripping | Manual escape removal | Check `NO_COLOR` env, conditional output | Standard env var for color control |
| Terminal width detection | Hardcode 80 cols | Check if needed; start simple | Truncation can be fixed-width initially |

**Key insight:** `git check-ignore` is the authoritative way to check if a file is ignored. It handles global gitignore, nested .gitignore files, and all gitignore syntax edge cases. Don't try to parse .gitignore yourself.

## Common Pitfalls

### Pitfall 1: Scanning Too Many Files

**What goes wrong:** Detection takes seconds on large repos, annoying users.
**Why it happens:** Recursive glob on repos with node_modules, .git, build artifacts.
**How to avoid:** Exclude common large directories (.git, node_modules, vendor, target, build).
**Warning signs:** Spinner runs for more than 1 second on typical projects.

**Mitigation:**
```clojure
(def excluded-dirs #{".git" "node_modules" "vendor" "target" "build" "dist" "__pycache__"})

(defn should-scan? [path]
  (not (some #(clojure.string/includes? (str path) (str "/" % "/")) excluded-dirs)))
```

### Pitfall 2: read-line Without flush

**What goes wrong:** Prompt doesn't display, user sees blank line waiting for input.
**Why it happens:** stdout is line-buffered; `print` without newline doesn't flush.
**How to avoid:** Always call `(flush)` after `print` and before `read-line`.
**Warning signs:** Interactive prompt appears "stuck" or shows prompt after input.

### Pitfall 3: Blocking in Non-TTY Contexts

**What goes wrong:** Script hangs waiting for input when run in CI.
**Why it happens:** `read-line` blocks even when stdin is not a terminal.
**How to avoid:** Check if running interactively; auto-proceed or require --unsafe in non-TTY.
**Warning signs:** CI builds hang indefinitely.

**Detection:**
```clojure
(defn interactive? []
  (some? (System/console)))
```

### Pitfall 4: ANSI Codes in Non-Color Terminals

**What goes wrong:** Output shows raw escape codes like `[0;31m`.
**Why it happens:** Not checking NO_COLOR env var or terminal capability.
**How to avoid:** Existing `colors-enabled?` function in output.clj handles this.
**Warning signs:** Garbled output in logs or redirected output.

### Pitfall 5: Truncation Loses Context

**What goes wrong:** User sees "...and 50 more" but can't tell what's important.
**Why it happens:** Naive truncation just cuts off the list.
**How to avoid:** Truncate within severity groups, show counts per severity.
**Warning signs:** High-severity items hidden in "more" message.

**Better approach:**
```clojure
(defn truncate-findings [findings max-per-severity]
  (let [grouped (group-by :severity findings)]
    (reduce-kv
      (fn [acc severity items]
        (let [shown (take max-per-severity items)
              hidden (- (count items) (count shown))]
          (concat acc shown
                  (when (pos? hidden)
                    [{:type :truncation
                      :severity severity
                      :count hidden}]))))
      []
      grouped)))
```

## Code Examples

### Detection Core Structure

```clojure
;; Source: Project structure analysis + Clojure best practices

(ns aishell.detection.core
  (:require [babashka.fs :as fs]
            [aishell.output :as output]
            [aishell.detection.patterns :as patterns]))

(def excluded-dirs
  "Directories to skip during scanning for performance."
  #{".git" "node_modules" "vendor" "target" "build" "dist"
    "__pycache__" ".venv" "venv" ".bundle"})

(defn scan-project
  "Scan project directory for sensitive files.
   Returns vector of findings: [{:path :type :severity :reason}]"
  [project-dir]
  (let [all-files (fs/glob project-dir "**" {:hidden true})
        filtered (remove #(some (fn [ex] (clojure.string/includes? (str %) (str "/" ex "/")))
                                excluded-dirs)
                         all-files)]
    ;; Phase 20+ will add actual pattern matching here
    ;; For Phase 19, return empty - framework only
    []))
```

### Severity Color Formatting

```clojure
;; Source: CONTEXT.md decisions + existing output.clj patterns

(ns aishell.detection.formatters
  (:require [aishell.output :as output]))

;; Extended ANSI codes for severity display
(def DIM (if (output/colors-enabled?) "\u001b[2m" ""))
(def BLUE (if (output/colors-enabled?) "\u001b[0;34m" ""))

(def severity-config
  {:high   {:emoji "HIGH"   :color output/RED    :bold? true}
   :medium {:emoji "MEDIUM" :color output/YELLOW :bold? false}
   :low    {:emoji "LOW"    :color DIM           :bold? false}})

(defn format-severity-label [severity]
  (let [{:keys [emoji color bold?]} (severity-config severity)]
    (str (when bold? output/BOLD)
         color
         emoji
         output/NC)))

(defn format-finding-line [{:keys [severity path reason]}]
  (str "  "
       (format-severity-label severity)
       " "
       path
       (when reason (str " - " reason))))
```

### Warning Display Block

```clojure
;; Source: Follows existing validation.clj warn-dangerous-* pattern

(defn display-warnings
  "Display warnings block to stderr, grouped by severity."
  [findings]
  (when (seq findings)
    (let [grouped (group-by :severity findings)
          sorted-groups (sort-by #(get {:high 0 :medium 1 :low 2} (key %) 3) grouped)]
      (println)  ; Blank line before warning block
      (binding [*out* *err*]
        (println (str output/BOLD "Sensitive files detected in project directory" output/NC))
        (println (apply str (repeat 50 "-")))
        (doseq [[severity items] sorted-groups]
          (doseq [finding items]
            (println (format-finding-line finding))))
        (println)
        (println "AI tools will have access to these files inside the container.")
        (println)))))
```

### User Confirmation Prompt

```clojure
;; Source: Babashka book, CONTEXT.md decisions

(defn prompt-yn
  "Prompt user for y/n confirmation. Returns true if user enters 'y'."
  [message]
  (print (str message " (y/n): "))
  (flush)
  (let [response (clojure.string/lower-case (or (read-line) ""))]
    (= response "y")))

(defn confirm-if-needed
  "Request confirmation for high-severity findings.
   Returns true to proceed, false or exits for abort.
   Medium/low findings auto-proceed."
  [findings]
  (let [high-count (count (filter #(= :high (:severity %)) findings))]
    (cond
      ;; No high-severity: auto-proceed
      (zero? high-count)
      true

      ;; Non-interactive (CI): require --unsafe
      (not (System/console))
      (do
        (binding [*out* *err*]
          (println)
          (println (str output/RED "Error:" output/NC
                        " High-severity findings in non-interactive mode."))
          (println "Use --unsafe flag to proceed in CI/automation."))
        (System/exit 1))

      ;; Interactive: prompt
      :else
      (if (prompt-yn (str output/YELLOW "Proceed with " high-count
                          " high-severity finding(s)?" output/NC))
        true
        (do
          (println "Aborted.")
          (System/exit 0))))))
```

### CLI Flag Integration

```clojure
;; Source: Existing cli.clj patterns

;; Add to dispatch function in cli.clj
(defn dispatch [args]
  (let [;; Parse --unsafe from args before pass-through
        unsafe? (some #{"--unsafe"} args)
        clean-args (remove #{"--unsafe"} args)]
    (case (first clean-args)
      "claude" (run/run-container "claude" (vec (rest clean-args)) {:unsafe unsafe?})
      "opencode" (run/run-container "opencode" (vec (rest clean-args)) {:unsafe unsafe?})
      ;; ... existing dispatch
      )))
```

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| Manual file scanning | fs/glob with patterns | babashka.fs 0.4+ | Simpler, handles edge cases |
| Switch/case detection | Multimethods | Clojure 1.0+ | Extensible without modification |
| Custom gitignore parsing | git check-ignore | Git 1.8.2+ (2013) | Authoritative, handles all edge cases |
| Hardcoded color codes | NO_COLOR env check | De facto standard 2019+ | Accessibility, CI compatibility |

**Deprecated/outdated:**
- File.listFiles(): Use java.nio via babashka.fs for better symlink handling
- Protocols for value dispatch: Multimethods are cleaner for severity/type dispatch
- Custom .gitignore parsing: Shell out to `git check-ignore` instead

## Open Questions

1. **Symlink Handling**
   - What we know: fs/glob has `:follow-links` option, defaults to false
   - What's unclear: Should we follow symlinks into sensitive directories?
   - Recommendation: Don't follow by default (security principle of least exposure)

2. **Large File Handling**
   - What we know: Phase 21 will inspect file contents for private keys
   - What's unclear: Size limit for content inspection?
   - Recommendation: Skip files >1MB for content inspection; filename-only for large files

3. **Concurrent Mount Scanning**
   - What we know: Phase 23 adds mount source tracking
   - What's unclear: Should mounts be scanned in parallel?
   - Recommendation: Defer to Phase 23; sequential is fine for initial implementation

4. **Spinner vs Progress Bar**
   - What we know: CONTEXT.md mentions "spinner/dots indicator while scanning"
   - What's unclear: Should we show file count progress?
   - Recommendation: Use existing spinner pattern from docker.spinner namespace

## Sources

### Primary (HIGH confidence)
- [babashka/fs GitHub](https://github.com/babashka/fs) - Official file system library documentation
- [babashka/fs API.md](https://github.com/babashka/fs/blob/master/API.md) - Complete glob/match API
- [Clojure Multimethods Reference](https://clojure.org/reference/multimethods) - Official multimethod documentation
- [git check-ignore Documentation](https://git-scm.com/docs/git-check-ignore) - Official git command for gitignore checking

### Secondary (MEDIUM confidence)
- [Babashka Book](https://book.babashka.org/) - stdin/stdout handling patterns
- [ANSI Escape Codes Reference](https://gist.github.com/fnky/458719343aabd01cfb17a3a4f7296797) - Color code reference
- [NO_COLOR Standard](https://no-color.org/) - Environment variable for disabling colors
- [Clojure for the Brave and True - Multimethods](https://www.braveclojure.com/multimethods-records-protocols/) - Multimethod patterns

### Tertiary (LOW confidence)
- Community discussions on Babashka Slack for interactive prompts
- Blog posts on file scanning performance

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH - Using built-in Babashka libraries, already proven in project
- Architecture (multimethods): HIGH - Standard Clojure pattern, official docs
- Architecture (display): HIGH - Extends existing validation.clj patterns
- User confirmation: MEDIUM - read-line works but has nREPL caveats
- Pitfalls: HIGH - Based on actual project patterns and common issues

**Research date:** 2026-01-22
**Valid until:** 90 days (stable Babashka/Clojure patterns)
