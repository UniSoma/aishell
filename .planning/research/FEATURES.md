# Features Research: Babashka CLI Patterns

**Domain:** CLI tool patterns for Babashka (Clojure) rewrite of aishell
**Researched:** 2026-01-20
**Mode:** Ecosystem research for Babashka CLI best practices
**Confidence:** HIGH (verified against official babashka.cli documentation, Babashka book, reference implementations)

## Summary

Babashka provides a mature CLI development ecosystem with `babashka.cli` as the primary argument parsing library (built-in since v0.9.160). The library follows an "open world assumption" where extra arguments don't break parsing, and validation is deferred to the application layer. Key patterns include: spec-based option definitions with coercion, subcommand dispatch, metadata-driven CLI generation, and integration with `babashka.process` for shell operations. The ecosystem emphasizes minimal boilerplate, leveraging Clojure's data-oriented design for configuration (EDN files instead of shell config formats).

For the aishell rewrite, Babashka enables significant improvements over Bash: type-safe configuration parsing, proper error handling with exceptions, structured data throughout (maps instead of arrays/variables), cross-platform path handling via `babashka.fs`, and superior testability.

## Table Stakes (Must Have)

Features users expect from a well-designed Babashka CLI tool.

| Feature | Description | Complexity | Notes |
|---------|-------------|------------|-------|
| Subcommand dispatch | `aishell build`, `aishell claude`, `aishell update` routing | LOW | Use `babashka.cli/dispatch` - handles shared options between subcommands |
| Spec-based options | Declarative option definitions with `:desc`, `:alias`, `:coerce`, `:default` | LOW | Standard pattern in babashka.cli |
| Auto-coercion | Automatic boolean/number/keyword conversion from strings | LOW | Built-in since v0.3.35, reduces boilerplate |
| Help generation | `--help` and `-h` produce formatted usage text | LOW | Use `babashka.cli/format-opts` with spec |
| Error messages | Clear errors for invalid options, missing requirements | LOW | Use `:error-fn` in parse-opts for custom handling |
| Version flag | `--version` displays tool version | LOW | Simple check before dispatch |
| Main entry pattern | Safe to load at REPL and invoke from CLI | LOW | Check `(= *file* (System/getProperty "babashka.file"))` |
| bb.edn configuration | Project-level deps, paths, min-version | LOW | Standard for distributable Babashka tools |
| Exit code handling | Non-zero exit on errors | LOW | `(System/exit 1)` or let exception propagate |

## Differentiators (Babashka Advantages)

Features where Babashka significantly improves over Bash implementation.

| Feature | Bash Limitation | Babashka Advantage | Complexity |
|---------|-----------------|-------------------|------------|
| EDN configuration files | Bash: Custom parsing for `.conf` files, fragile quoting, no nested structures | Clojure: Native EDN support via `clojure.edn/read-string`, validated with spec/malli, nested maps natural | LOW |
| Structured data throughout | Bash: Arrays with index math, associative arrays awkward, no nested structures | Clojure: Maps and vectors, destructuring, threading macros | LOW |
| Type coercion | Bash: Manual string-to-number, boolean as string comparison | babashka.cli: Declarative `:coerce :long`, `:boolean`, `:keyword`, auto-coercion | LOW |
| Error handling | Bash: `set -e` fragile, trap complexity, error messages ad-hoc | Clojure: try/catch, ex-info with structured data, stack traces | LOW |
| Cross-platform paths | Bash: Hardcoded `/`, `$HOME` assumptions, no Windows | babashka.fs: `fs/path`, `fs/home`, works on Windows/macOS/Linux | MED |
| Temporary files | Bash: `mktemp`, manual cleanup with traps | `fs/with-temp-dir` auto-cleanup, `fs/create-temp-file` | LOW |
| Process output capture | Bash: Subshells `$()`, exit code in `$?`, stderr handling awkward | babashka.process: `:out :string`, `:err :string`, structured return | LOW |
| Testing | Bash: No standard test framework, integration tests only | Clojure: REPL-driven development, unit tests with clojure.test | MED |
| Heredocs/templates | Bash: Heredocs with quoting complexity, variable expansion tricky | Clojure: Multiline strings, `str`, Selmer templates if needed | LOW |
| Config validation | Bash: Manual checks with if/case | Spec/malli schemas, validate at parse time | MED |
| Subcommand parsing | Bash: `case` statements, manual shift, no shared options | `babashka.cli/dispatch` with `:global-opts`, automatic routing | LOW |
| Verbose mode | Bash: Global variable, manual echo conditionals | Binding/dynamic vars, structured logging | LOW |

## Anti-Features (Don't Do This)

Patterns to deliberately avoid in Babashka CLI development.

| Pattern | Why to Avoid | What to Do Instead |
|---------|--------------|-------------------|
| tools.cli instead of babashka.cli | More verbose, not integrated with bb tasks, different idioms | Use babashka.cli - it's built-in and designed for bb |
| Shelling out for everything | Loses Babashka's advantages, slower, platform-dependent | Use babashka.fs, babashka.process structured APIs |
| Global mutable state | Hard to test, REPL-unfriendly | Use function parameters, return values, or dynamic bindings |
| Manual argument parsing | Reinventing the wheel, error-prone | Use babashka.cli spec-based parsing |
| Complex pods for simple tasks | Pods add startup overhead, distribution complexity | Use built-in libraries first (fs, process, http-client) |
| JVM-only libraries | Won't work in Babashka, GraalVM constraints | Check babashka toolbox for compatible libs |
| Long-running CPU tasks | Babashka uses SCI interpreter, slower than JVM | Shell out to Java for intensive work, or accept bb is for scripts |
| #() reader macros in bb.edn | Not valid EDN syntax | Use `(fn [x] ...)` form instead |
| `:require` in bb.edn tasks | Syntax errors, confusing | Use `:requires` key at task level |
| Relative paths without resolution | Breaks when invoked from different directories | Use `(fs/absolutize path)` or `*file*` for script-relative |

## Feature Mapping: Bash to Babashka

Existing aishell features and their Babashka equivalents.

| Bash Feature | Babashka Equivalent | Notes |
|--------------|---------------------|-------|
| `case "$1" in build)` | `(cli/dispatch {:cmds {...}})` | Declarative subcommand routing |
| `shift; parse args` | `(cli/parse-args args {:spec ...})` | Returns `{:args [...] :opts {...}}` |
| `$HOME/.local/state` | `(fs/path (fs/home) ".local" "state")` | Cross-platform |
| `mktemp -d` | `(fs/create-temp-dir)` or `(fs/with-temp-dir ...)` | Auto-cleanup with `with-temp-dir` |
| `docker build ...` | `(shell "docker" "build" ...)` or `(process ...)` | Use shell for inherited stdout |
| Heredoc Dockerfile | Multiline string in code | `(def dockerfile "FROM...` |
| `source "$config_file"` | `(edn/read-string (slurp config))` | Structured config, validated |
| `echo "$var"` | `(println var)` | Or use `prn` for data |
| `set -e` | Try/catch at top level | Or let exceptions propagate |
| `trap cleanup EXIT` | `try/finally` or shutdown hooks | More explicit control |
| `${var:-default}` | `(get opts :var "default")` | Or use `:default` in spec |
| `readonly VAR=...` | `(def ^:const VAR ...)` | Immutable by default anyway |

## Reference Projects

Notable Babashka CLI tools demonstrating best practices.

| Project | What to Learn | Link |
|---------|---------------|------|
| **neil** | Complex CLI with subcommands, deps.edn manipulation, well-structured | [babashka/neil](https://github.com/babashka/neil) |
| **bbin** | Package manager CLI, installation/distribution patterns | [babashka/bbin](https://github.com/babashka/bbin) |
| **http-server** | Simple CLI with options, process handling | [babashka/http-server](https://github.com/babashka/http-server) |
| **quickblog** | File processing, configuration, templating | [borkdude/quickblog](https://github.com/borkdude/quickblog) |
| **babashka/cli examples** | Official examples of dispatch, parsing | [babashka/cli](https://github.com/babashka/cli) |

## Recommended Patterns for aishell Rewrite

### 1. Configuration File Format

**Current (Bash):** `.aishell/run.conf` - custom shell-style parsing
```bash
MOUNTS="$HOME/.ssh $HOME/.config/git"
ENV="EDITOR DEBUG_MODE=1"
```

**Recommended (Babashka):** `.aishell/config.edn` - native EDN
```clojure
{:mounts [{:source "~/.ssh" :target "~/.ssh"}
          {:source "~/.config/git" :target "~/.config/git"}]
 :env {:EDITOR "vim" :DEBUG_MODE "1"}
 :ports [[3000 3000] [8080 80]]}
```

**Advantages:**
- Native parsing with `(edn/read-string (slurp path))`
- Validation with spec/malli
- Nested structures natural
- Comments with `;`
- No quoting complexity

### 2. Subcommand Structure

```clojure
(def dispatch-table
  [{:cmds ["build"]   :fn build-cmd  :spec build-spec}
   {:cmds ["update"]  :fn update-cmd :spec update-spec}
   {:cmds ["claude"]  :fn claude-cmd :spec harness-spec}
   {:cmds ["opencode"] :fn opencode-cmd :spec harness-spec}
   {:cmds []          :fn shell-cmd  :spec shell-spec}])

(defn -main [& args]
  (cli/dispatch dispatch-table args {:error-fn handle-error}))
```

### 3. State Management

```clojure
;; State file as EDN
(defn read-state [project-dir]
  (let [state-file (state-file-path project-dir)]
    (when (fs/exists? state-file)
      (edn/read-string (slurp state-file)))))

(defn write-state [project-dir state]
  (let [state-file (state-file-path project-dir)]
    (fs/create-dirs (fs/parent state-file))
    (spit state-file (pr-str state))))
```

### 4. Docker Integration

```clojure
(require '[babashka.process :refer [shell process check]])

;; For operations where user sees output
(defn docker-build [opts]
  (shell {:dir build-dir}
         "docker" "build"
         (when (:no-cache opts) "--no-cache")
         "-t" (:tag opts)
         "."))

;; For operations where we need to capture output
(defn docker-inspect [image]
  (-> (process ["docker" "inspect" image] {:out :string})
      check
      :out
      (json/parse-string true)))
```

### 5. Cross-Platform Paths

```clojure
(require '[babashka.fs :as fs])

(defn state-dir []
  (or (System/getenv "XDG_STATE_HOME")
      (fs/path (fs/home) ".local" "state")))

(defn config-path [project-dir]
  (fs/path project-dir ".aishell" "config.edn"))
```

## CLI Option Spec Example

```clojure
(def build-spec
  {:with-claude    {:coerce :boolean
                    :alias :c
                    :desc "Include Claude Code in image"}
   :with-opencode  {:coerce :boolean
                    :alias :o
                    :desc "Include OpenCode in image"}
   :claude-version {:coerce :string
                    :desc "Specific Claude Code version (e.g., 2.0.22)"}
   :opencode-version {:coerce :string
                      :desc "Specific OpenCode version (e.g., 1.1.25)"}
   :no-cache       {:coerce :boolean
                    :desc "Force rebuild without Docker cache"}
   :verbose        {:coerce :boolean
                    :alias :v
                    :desc "Show detailed build output"}
   :help           {:coerce :boolean
                    :alias :h
                    :desc "Show this help"}})
```

## Complexity Assessment for Rewrite

| Component | Complexity | Notes |
|-----------|------------|-------|
| CLI dispatch/parsing | LOW | babashka.cli handles well |
| Config file migration (shell -> EDN) | LOW | Cleaner in EDN |
| State file format | LOW | Already structured, easy in EDN |
| Docker process invocation | LOW | babashka.process direct replacement |
| Heredoc embedding (Dockerfile, entrypoint) | LOW | Multiline strings work |
| Hash computation | LOW | `(-> (slurp file) .getBytes java.security.MessageDigest ...)` |
| Path handling | MED | Need careful fs/path usage for cross-platform |
| Cleanup/resource management | LOW | try/finally simpler than bash traps |
| Color output | LOW | ANSI escapes same, or use library |
| Spinner/progress | MED | Need async or threading |

## Sources

### HIGH Confidence (Official Documentation)
- [Babashka CLI GitHub](https://github.com/babashka/cli) - Official argument parsing library
- [Babashka Book](https://book.babashka.org/) - Comprehensive Babashka documentation
- [babashka.cli API](https://github.com/babashka/cli/blob/main/API.md) - Complete API reference
- [babashka/process](https://github.com/babashka/process) - Process execution library
- [babashka/fs](https://github.com/babashka/fs) - File system utilities

### MEDIUM Confidence (Verified Blog/Tutorial Sources)
- [Babashka CLI Blog Post](https://blog.michielborkent.nl/babashka-cli.html) - Author's introduction
- [Babashka Tasks + CLI](https://blog.michielborkent.nl/babashka-tasks-meets-babashka-cli.html) - Integration patterns
- [Martin Klepsch CLI Scripts](https://martinklepsch.org/posts/one-shot-babashka-cli-scripts/) - One-shot script patterns
- [How to Do Things with Babashka](https://presumably.de/how-to-do-things-with-babashka.html) - Practical patterns

### LOW Confidence (Community Examples)
- [bbin Scripts and Projects](https://github.com/babashka/bbin/wiki/Scripts-and-Projects) - Ecosystem examples
- [Brave Clojure Babooka](https://www.braveclojure.com/quests/babooka/) - Tutorial content
