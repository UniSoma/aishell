# Phase 13: Foundation - Research

**Researched:** 2026-01-20
**Domain:** Babashka CLI Foundation (argument parsing, help generation, cross-platform paths)
**Confidence:** HIGH

## Summary

Phase 13 establishes the CLI foundation for the Babashka rewrite. The standard approach uses `babashka.cli` for argument parsing with subcommand dispatch, `babashka.fs` for cross-platform path handling, and ANSI escape codes for colored terminal output. All required functionality is available through Babashka's built-in libraries with no external dependencies (pods) needed.

The phase scope is deliberately narrow: `--version`, `--help`, invalid command errors, and cross-platform path utilities. Docker operations are not in scope (Phase 14). This foundation creates the project structure, CLI parsing infrastructure, and output utilities that all subsequent phases build upon.

**Primary recommendation:** Use `babashka.cli/dispatch` for command routing with per-command specs, `format-opts` for automatic help generation, and establish path/output utilities early that all modules can reuse.

## Standard Stack

The established libraries/tools for this domain:

### Core (Built into Babashka)

| Library | Purpose | Why Standard |
|---------|---------|--------------|
| `babashka.cli` | CLI argument parsing, subcommand dispatch, help generation | Built-in since bb 0.9.160, designed for Babashka CLIs |
| `babashka.fs` | Cross-platform file/path operations | Built-in, handles Linux/macOS differences |
| `clojure.string` | String manipulation | Built-in core library |
| `clojure.edn` | EDN parsing (for state files) | Built-in, native Clojure format |

### Supporting

| Library | Purpose | When to Use |
|---------|---------|-------------|
| `java.lang.System` | Environment variables, system properties | TTY detection, home directory fallback |

### Alternatives Considered

| Instead of | Could Use | Tradeoff |
|------------|-----------|----------|
| `babashka.cli` | `tools.cli` | tools.cli is more verbose, babashka.cli has better subcommand support |
| Manual ANSI codes | External color library | No color libraries built into Babashka, manual codes are sufficient |

**Installation:**
```bash
# No installation needed - all libraries built into Babashka
# Require Babashka >= 1.12.214 for latest features
```

## Architecture Patterns

### Recommended Project Structure
```
harness/
  aishell                  # Entry point script (shebang: #!/usr/bin/env bb)
  src/
    aishell/
      core.clj             # Main entry, -main, top-level dispatch
      cli.clj              # CLI specs, argument parsing, help formatting
      output.clj           # Color output, error/warn/verbose functions
      util.clj             # Path utilities, home directory, validation
```

### Pattern 1: Subcommand Dispatch Table

**What:** Central dispatch table routes commands to handlers with per-command specs
**When to use:** CLIs with multiple subcommands (build, update, claude, opencode)
**Example:**
```clojure
;; Source: https://github.com/babashka/cli
(require '[babashka.cli :as cli])

(def global-spec
  {:help    {:alias :h :coerce :boolean :desc "Show help"}
   :version {:alias :v :coerce :boolean :desc "Show version"}
   :verbose {:coerce :boolean :desc "Verbose output"}})

;; Empty :cmds matches when no subcommand given (default behavior)
;; Ordered by specificity - most specific first
(def dispatch-table
  [{:cmds ["build"]   :fn do-build   :spec build-spec}
   {:cmds ["update"]  :fn do-update  :spec build-spec}
   {:cmds ["claude"]  :fn do-claude  :spec run-spec}
   {:cmds ["opencode"] :fn do-opencode :spec run-spec}
   {:cmds []          :fn do-default :spec global-spec}])

(defn -main [& args]
  (cli/dispatch dispatch-table args {:coerce {:depth :long}}))
```

### Pattern 2: Option Spec with Description

**What:** Declarative spec defines options with descriptions, aliases, coercion
**When to use:** Every command needs documented options
**Example:**
```clojure
;; Source: https://github.com/babashka/cli
(def build-spec
  {:with-claude     {:coerce :boolean :desc "Include Claude Code"}
   :with-opencode   {:coerce :boolean :desc "Include OpenCode"}
   :claude-version  {:coerce :string :desc "Claude Code version (e.g., 2.0.22)"}
   :opencode-version {:coerce :string :desc "OpenCode version (e.g., 1.1.25)"}
   :no-cache        {:coerce :boolean :desc "Force fresh build"}
   :verbose         {:alias :v :coerce :boolean :desc "Show detailed output"}
   :help            {:alias :h :coerce :boolean :desc "Show help"}})
```

### Pattern 3: Help Generation with format-opts

**What:** Auto-generate help text from spec definitions
**When to use:** Generating --help output
**Example:**
```clojure
;; Source: https://github.com/babashka/cli
(require '[babashka.cli :as cli])

(defn print-help []
  (println "Usage: aishell [OPTIONS] COMMAND [ARGS...]")
  (println)
  (println "Commands:")
  (println "  build      Build the container image")
  (println "  update     Rebuild with latest versions")
  (println "  claude     Run Claude Code")
  (println "  opencode   Run OpenCode")
  (println "  (none)     Enter interactive shell")
  (println)
  (println "Global Options:")
  (println (cli/format-opts {:spec global-spec
                             :order [:help :version :verbose]}))
  (println)
  (println "Examples:")
  (println "  aishell build --with-claude     Build with Claude Code")
  (println "  aishell claude                  Run Claude Code")
  (println "  aishell                         Enter shell"))
```

### Pattern 4: Error Handler for Invalid Commands

**What:** Custom error-fn handles unknown commands with suggestions
**When to use:** User enters invalid command
**Example:**
```clojure
;; Source: https://github.com/babashka/cli (error-fn pattern)
(require '[clojure.string :as str])

(def known-commands #{"build" "update" "claude" "opencode"})

(defn suggest-command [input]
  ;; Simple Levenshtein distance not built-in, use prefix match
  (first (filter #(str/starts-with? % input) known-commands)))

(defn handle-error [{:keys [type cause msg option]}]
  (case cause
    :restrict  ;; Unknown option
    (do
      (println (str "Error: Unknown option: " option))
      (println "Try: aishell --help")
      (System/exit 1))

    ;; Default
    (do
      (println (str "Error: " msg))
      (System/exit 1))))

;; In dispatch call:
(cli/dispatch table args {:error-fn handle-error})
```

### Pattern 5: Unknown Command Handling

**What:** Catch-all handler for unrecognized commands
**When to use:** User types `aishell foo` (invalid command)
**Example:**
```clojure
;; The empty :cmds entry catches everything not matched
(def dispatch-table
  [;; ... specific commands ...
   {:cmds []
    :fn (fn [{:keys [opts args]}]
          (cond
            (:version opts) (print-version)
            (:help opts) (print-help)
            (seq args) (error-unknown-command (first args))
            :else (do-shell opts)))}])

(defn error-unknown-command [cmd]
  (binding [*out* *err*]
    (println (str "Error: Unknown command: " cmd))
    (when-let [suggestion (suggest-command cmd)]
      (println (str "Did you mean: " suggestion "?")))
    (println "Try: aishell --help"))
  (System/exit 1))
```

### Anti-Patterns to Avoid

- **Nested case statements for dispatch:** Use dispatch table instead of nested case/cond
- **String building for help text:** Use format-opts from spec definitions
- **Hardcoded option lists:** Define once in spec, reference everywhere
- **Ignoring error-fn:** Always provide error handling for invalid input

## Don't Hand-Roll

Problems that look simple but have existing solutions:

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| Argument parsing | Manual string parsing | `babashka.cli/parse-opts` | Handles edge cases (quotes, =, booleans) |
| Subcommand routing | Nested conditionals | `babashka.cli/dispatch` | Built for this exact pattern |
| Help text generation | Hardcoded strings | `babashka.cli/format-opts` | Keeps help in sync with spec |
| Path joining | String concatenation | `babashka.fs/path` | Cross-platform separator handling |
| Home directory | `$HOME` only | `babashka.fs/home` | Handles Windows USERPROFILE |
| Executable finding | `which` via shell | `babashka.fs/which` | Cross-platform, no subprocess |
| Tilde expansion | Regex replace | `babashka.fs/expand-home` | Correct edge cases |

**Key insight:** Babashka's built-in libraries cover all CLI needs. External dependencies are unnecessary for Phase 13.

## Common Pitfalls

### Pitfall 1: Global Flag Position Confusion

**What goes wrong:** User expects `aishell --verbose build` but flags after subcommand work differently
**Why it happens:** babashka.cli processes flags in position order
**How to avoid:** Use global spec with `{:cmds []}` entry; document flag order
**Warning signs:** Flags ignored when placed before subcommand

```clojure
;; Fix: Global spec with empty cmds catches flags before subcommand
(def table
  [{:cmds [] :spec global-spec}  ;; Catches --verbose before subcommand
   {:cmds ["build"] :fn do-build :spec build-spec}])
```

### Pitfall 2: Boolean Flag Requires Explicit Coercion

**What goes wrong:** `--verbose` returns string `"true"` instead of boolean `true`
**Why it happens:** Default coercion is string
**How to avoid:** Always specify `:coerce :boolean` for flags
**Warning signs:** Truthy string comparisons fail

```clojure
;; Wrong
{:verbose {:desc "Verbose output"}}  ;; Returns "true" string

;; Correct
{:verbose {:coerce :boolean :desc "Verbose output"}}  ;; Returns true boolean
```

### Pitfall 3: Missing Alias Short Forms

**What goes wrong:** `-h` doesn't work, only `--help`
**Why it happens:** Aliases must be explicitly defined
**How to avoid:** Define `:alias` for common short forms
**Warning signs:** Users report short flags not working

```clojure
;; Required for short forms
{:help {:alias :h :coerce :boolean :desc "Show help"}
 :version {:alias :v :coerce :boolean :desc "Show version"}}
```

### Pitfall 4: Path Separator Issues on macOS vs Linux

**What goes wrong:** Paths work on Linux but fail on macOS (or vice versa)
**Why it happens:** Hardcoded path separators or string concatenation
**How to avoid:** Always use `babashka.fs/path` for path construction
**Warning signs:** "File not found" errors on one platform only

```clojure
;; Wrong
(str home "/.aishell/config.yaml")

;; Correct
(str (fs/path home ".aishell" "config.yaml"))
```

### Pitfall 5: Home Directory Fallback Chain

**What goes wrong:** `$HOME` not set in some environments (containers, cron)
**Why it happens:** Assuming HOME always exists
**How to avoid:** Use fallback chain: HOME -> user.home property
**Warning signs:** Failures in Docker containers or CI environments

```clojure
;; Robust home directory resolution
(defn get-home []
  (or (System/getenv "HOME")
      (System/getProperty "user.home")))

;; Or simply use babashka.fs/home which handles this
(require '[babashka.fs :as fs])
(fs/home)
```

### Pitfall 6: Color Output Without TTY Check

**What goes wrong:** ANSI codes appear in logs/pipes as garbage characters
**Why it happens:** Not checking if stdout is a terminal
**How to avoid:** Check `(System/console)` and `NO_COLOR` env var
**Warning signs:** CI logs show `[0;31m` garbage

```clojure
(def colors-enabled?
  (and (some? (System/console))           ;; Is TTY?
       (nil? (System/getenv "NO_COLOR"))  ;; NO_COLOR not set
       (not= "dumb" (System/getenv "TERM")))) ;; Not dumb terminal
```

## Code Examples

Verified patterns from official sources:

### Entry Point (aishell.core)
```clojure
;; Source: Pattern from neil (https://github.com/babashka/neil)
(ns aishell.core
  (:require [aishell.cli :as cli]
            [aishell.output :as output]))

(def version "2.0.0")

(defn print-version []
  (println (str "aishell " version)))

(defn print-version-json []
  (println (str "{\"name\":\"aishell\",\"version\":\"" version "\"}")))

(defn -main [& args]
  (try
    (cli/dispatch args)
    (catch clojure.lang.ExceptionInfo e
      (output/error (ex-message e)))
    (catch Exception e
      (output/error (str "Unexpected error: " (.getMessage e))))))

;; Entry point for script execution
(when (= *file* (System/getProperty "babashka.file"))
  (apply -main *command-line-args*))
```

### CLI Dispatch (aishell.cli)
```clojure
;; Source: https://github.com/babashka/cli dispatch pattern
(ns aishell.cli
  (:require [babashka.cli :as cli]
            [aishell.core :as core]
            [aishell.output :as output]))

(def global-spec
  {:help    {:alias :h :coerce :boolean :desc "Show help"}
   :version {:alias :v :coerce :boolean :desc "Show version"}
   :json    {:coerce :boolean :desc "Output in JSON format"}})

(defn print-help []
  (println "Usage: aishell [OPTIONS] COMMAND [ARGS...]")
  (println)
  (println "Build and run ephemeral containers for AI harnesses.")
  (println)
  (println "Commands:")
  (println "  build      Build the container image")
  (println "  update     Rebuild with latest versions")
  (println "  claude     Run Claude Code")
  (println "  opencode   Run OpenCode")
  (println "  (none)     Enter interactive shell")
  (println)
  (println "Global Options:")
  (println (cli/format-opts {:spec global-spec
                             :order [:help :version]}))
  (println)
  (println "Examples:")
  (println "  aishell build --with-claude     Build with Claude Code")
  (println "  aishell claude                  Run Claude Code")
  (println "  aishell                         Enter shell"))

(defn handle-default [{:keys [opts args]}]
  (cond
    (:version opts)
    (if (:json opts)
      (core/print-version-json)
      (core/print-version))

    (:help opts)
    (print-help)

    (seq args)
    (output/error-unknown-command (first args))

    :else
    ;; Default: enter shell (Phase 16)
    (output/error "No image built. Run: aishell build --with-claude")))

(def dispatch-table
  [{:cmds [] :spec global-spec :fn handle-default}])

(defn dispatch [args]
  (cli/dispatch dispatch-table args))
```

### Color Output (aishell.output)
```clojure
;; Source: ANSI escape code standard, NO_COLOR convention
(ns aishell.output
  (:require [clojure.string :as str]))

(def ^:dynamic *verbose* false)

(defn- colors-enabled? []
  (and (some? (System/console))
       (nil? (System/getenv "NO_COLOR"))
       (or (some? (System/getenv "FORCE_COLOR"))
           (not= "dumb" (System/getenv "TERM")))))

(def RED (if (colors-enabled?) "\u001b[0;31m" ""))
(def YELLOW (if (colors-enabled?) "\u001b[0;33m" ""))
(def CYAN (if (colors-enabled?) "\u001b[0;36m" ""))
(def BOLD (if (colors-enabled?) "\u001b[1m" ""))
(def NC (if (colors-enabled?) "\u001b[0m" ""))  ;; No Color / Reset

(defn error
  "Print error message to stderr and exit with code 1"
  [msg]
  (binding [*out* *err*]
    (println (str RED "Error:" NC " " msg)))
  (System/exit 1))

(defn warn
  "Print warning message to stderr"
  [msg]
  (binding [*out* *err*]
    (println (str YELLOW "Warning:" NC " " msg))))

(defn verbose
  "Print message to stderr if verbose mode enabled"
  [msg]
  (when *verbose*
    (binding [*out* *err*]
      (println msg))))

(defn error-unknown-command
  "Print error for unknown command with suggestion"
  [cmd]
  (binding [*out* *err*]
    (println (str RED "Error:" NC " Unknown command: " cmd))
    (println (str "Try: " CYAN "aishell --help" NC)))
  (System/exit 1))
```

### Path Utilities (aishell.util)
```clojure
;; Source: https://github.com/babashka/fs/blob/master/API.md
(ns aishell.util
  (:require [babashka.fs :as fs]
            [clojure.string :as str]))

(defn get-home
  "Get user home directory (cross-platform)"
  []
  (str (fs/home)))

(defn expand-path
  "Expand ~ and environment variables in path"
  [path]
  (-> path
      (str/replace #"^\~" (get-home))
      (str/replace #"\$HOME" (get-home))
      (str/replace #"\$\{HOME\}" (get-home))))

(defn config-dir
  "Get aishell config directory path"
  []
  (str (fs/path (get-home) ".aishell")))

(defn state-dir
  "Get XDG state directory for aishell"
  []
  (let [xdg-state (or (System/getenv "XDG_STATE_HOME")
                      (str (fs/path (get-home) ".local" "state")))]
    (str (fs/path xdg-state "aishell"))))

(defn ensure-dir
  "Create directory if it doesn't exist"
  [dir]
  (when-not (fs/exists? dir)
    (fs/create-dirs dir))
  dir)
```

## State of the Art

| Old Approach (Bash) | Current Approach (Babashka) | When Changed | Impact |
|--------------------|-----------------------------|--------------|--------|
| case/esac dispatch | `cli/dispatch` table | Babashka adoption | Cleaner, extensible |
| Manual --help string | `cli/format-opts` from spec | cli 0.3+ | Help stays in sync with code |
| `$HOME` hardcoded | `fs/home` function | Always available | Cross-platform |
| `tput colors` for TTY | `System/console` check | Babashka pattern | No subprocess needed |
| `getopts` | `cli/parse-opts` | Babashka adoption | Better validation, coercion |

**Deprecated/outdated:**
- `tools.cli` for Babashka: Still works but `babashka.cli` has better subcommand support
- Manual argument parsing: Built-in cli library handles edge cases

## Open Questions

Things that couldn't be fully resolved:

1. **Help output formatting for commands with many options**
   - What we know: format-opts generates basic help; neil uses custom formatting
   - What's unclear: Best approach for grouping options by category
   - Recommendation: Start with format-opts, customize if needed in later phases

2. **Version output content**
   - What we know: User decided on `--version --json` for machine-readable
   - What's unclear: What non-JSON version should show (just version vs name+version)
   - Recommendation: Use "aishell 2.0.0" format (consistent with most CLI tools)

## Sources

### Primary (HIGH confidence)
- [babashka/cli GitHub](https://github.com/babashka/cli) - dispatch, parse-opts, format-opts
- [babashka/cli API.md](https://github.com/babashka/cli/blob/main/API.md) - Full API documentation
- [babashka/fs API.md](https://github.com/babashka/fs/blob/master/API.md) - path, home, expand-home, which
- [neil source](https://github.com/babashka/neil/blob/main/src/babashka/neil.clj) - Real-world CLI pattern

### Secondary (MEDIUM confidence)
- [Babashka Book](https://book.babashka.org/) - Project structure, patterns
- [ANSI escape codes](https://gist.github.com/fnky/458719343aabd01cfb17a3a4f7296797) - Color codes reference

### Tertiary (LOW confidence)
- NO_COLOR convention - Community standard, not formally specified

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH - All built-in Babashka libraries, well-documented
- Architecture: HIGH - Pattern matches neil, bbin, other production CLIs
- Pitfalls: HIGH - Documented in official repos and existing project research
- Code examples: HIGH - Verified against official documentation

**Research date:** 2026-01-20
**Valid until:** 2026-03-20 (Babashka ecosystem is stable)

---
*Phase: 13-foundation*
*Research completed: 2026-01-20*
