# Phase 34: PS Command & Polish - Research

**Researched:** 2026-01-31
**Domain:** CLI listing command, Docker container discovery, tabular output formatting
**Confidence:** HIGH

## Summary

Phase 34 implements the `aishell ps` command to discover and list running containers for the current project. Building on Phase 30's container naming (project hash-based) and Phase 32's detached mode (named containers), this phase completes the multi-container workflow by providing visibility into what containers exist and their current state.

The core technical decision is **leveraging existing infrastructure**: Phase 30's `list-project-containers` function already exists and does 90% of the work. This phase primarily involves:
1. Creating a new CLI subcommand `ps` that calls the existing function
2. Formatting the output in a human-readable table
3. Handling edge cases (no containers, errors)

Key findings:
- **Docker query infrastructure exists**: `docker/naming.clj` already has `list-project-containers` function that filters by project hash
- **Simple table formatting**: `clojure.pprint/print-table` is built into Babashka, handles column alignment automatically
- **Time formatting**: Docker's `.CreatedAt` returns timestamps like "2026-01-31 10:15:05 +0000 UTC" - can use as-is or parse for relative time
- **Container name display**: Full container names are technical (aishell-a1b2c3d4-claude), should extract user-friendly portion
- **Status field**: Docker `.Status` shows "Up X minutes" or "Exited (N) X ago" - sufficient without parsing

**Primary recommendation:** Create minimal `ps` subcommand that formats output from existing `list-project-containers` function. Use `clojure.pprint/print-table` for basic formatting, extract short names from full container names, and show helpful message when no containers exist.

## Standard Stack

The established libraries/tools for this domain:

### Core
| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| `docker ps -a --filter` | Docker CLI | Query containers by name filter | Already used in `naming/list-project-containers` |
| `clojure.pprint/print-table` | Clojure 1.3+ | Format collections as tables | Built into Babashka, automatic column alignment |
| Phase 30 `naming/list-project-containers` | v2.6.0 | Query containers for project | Already implemented, tested, handles filtering |
| Phase 30 `naming/project-hash` | v2.6.0 | Generate 8-char project identifier | Used in container naming and filtering |
| `clojure.core/format` | Clojure core | Printf-style string formatting | Built-in, works for column width control |

### Supporting
| Library | Version | Purpose | When to Use |
|---------|---------|---------|-------------|
| `clojure.string` | Clojure core | String manipulation (split, trim) | Extract short name from full container name |
| `java.time.Instant` | JVM built-in | Parse Docker timestamps | If implementing relative time ("2 hours ago") |
| `java.time.Duration` | JVM built-in | Calculate time differences | If implementing relative time display |

### Alternatives Considered
| Instead of | Could Use | Tradeoff |
|------------|-----------|----------|
| `clojure.pprint/print-table` | Doric library | Doric requires external dependency, more features but overkill for simple table |
| As-is timestamps | Relative time ("2 hours ago") | Relative time more user-friendly but adds complexity; Docker format is clear |
| Full container names | Short names only | Short names cleaner but full names useful for Docker CLI copy-paste |
| Custom Docker query | Phase 30 function | Existing function already works, no need to rebuild |

**Installation:**
No additional dependencies required. All functionality available in current stack (Clojure core, Phase 30 utilities).

## Architecture Patterns

### Recommended Code Structure
```
src/aishell/
├── cli.clj              # Add ps subcommand to dispatch table
├── ps.clj               # NEW: PS command implementation (optional - can be inline in cli.clj)
├── docker/
│   ├── naming.clj       # Existing: list-project-containers function
│   └── ...
```

**Note:** Given simplicity, ps implementation could be inline in `cli.clj` rather than separate file.

### Pattern 1: PS Command with Existing Infrastructure
**What:** List containers using Phase 30's existing query function
**When to use:** User runs `aishell ps` to discover running containers
**Example:**
```clojure
;; Source: Phase 30 naming.clj list-project-containers + clojure.pprint/print-table
(ns aishell.cli
  (:require [clojure.pprint :as pp]
            [clojure.string :as str]
            [aishell.docker.naming :as naming]
            [aishell.output :as output]))

(defn- extract-short-name
  "Extract user-friendly name from full container name.
   aishell-a1b2c3d4-claude -> claude
   aishell-a1b2c3d4-shell -> shell"
  [container-name]
  (last (str/split container-name #"-" 3)))

(defn- format-container-for-display
  "Transform container map for display.
   Adds :short-name field, keeps :status and :created."
  [container]
  {:name (:short-name container (extract-short-name (:name container)))
   :status (:status container)
   :created (:created container)})

(defn handle-ps
  "List running containers for current project."
  [{:keys [opts]}]
  (let [project-dir (System/getProperty "user.dir")
        containers (naming/list-project-containers project-dir)]
    (if (empty? containers)
      (println "No containers running for this project.\n\nTo start a container:\n  aishell <harness> --detach")
      (do
        (println "Containers for this project:\n")
        (pp/print-table [:name :status :created]
                        (map format-container-for-display containers))))))
```

### Pattern 2: Minimal Table Formatting with print-table
**What:** Use built-in `clojure.pprint/print-table` for automatic column formatting
**When to use:** Display tabular data without external dependencies
**Example:**
```clojure
;; Source: clojure.pprint documentation
(require '[clojure.pprint :as pp])

;; Specify column order explicitly
(pp/print-table [:name :status :created]
                [{:name "claude" :status "Up 2 hours" :created "2026-01-31 08:00:00 +0000 UTC"}
                 {:name "shell" :status "Up 5 minutes" :created "2026-01-31 10:00:00 +0000 UTC"}])

;; Output:
;; | :name  | :status      | :created                       |
;; |--------+--------------+--------------------------------|
;; | claude | Up 2 hours   | 2026-01-31 08:00:00 +0000 UTC |
;; | shell  | Up 5 minutes | 2026-01-31 10:00:00 +0000 UTC |
```

### Pattern 3: Short Name Extraction
**What:** Extract user-friendly portion from full container name
**When to use:** Display container names without project hash clutter
**Example:**
```clojure
;; Container naming format: aishell-{8-char-hash}-{name}
;; Example: aishell-a1b2c3d4-claude

(defn extract-short-name [full-name]
  ;; Split on hyphen, limit to 3 parts (aishell, hash, name)
  ;; Take last part (the user-provided name)
  (last (str/split full-name #"-" 3)))

;; Usage:
(extract-short-name "aishell-a1b2c3d4-claude")  ;; => "claude"
(extract-short-name "aishell-a1b2c3d4-shell")   ;; => "shell"
(extract-short-name "aishell-a1b2c3d4-my-experiment")  ;; => "my-experiment"
```

### Pattern 4: Empty State Handling
**What:** Show helpful message when no containers exist for project
**When to use:** User runs `aishell ps` in new/clean project
**Example:**
```clojure
(defn handle-ps [_]
  (let [containers (naming/list-project-containers (System/getProperty "user.dir"))]
    (if (empty? containers)
      ;; Helpful empty state
      (println "No containers running for this project.\n"
               "\nTo start a container:"
               "\n  aishell claude --detach"
               "\n  aishell shell --detach --name dev"
               "\n\nContainers are project-specific (based on directory).")
      ;; Show table
      (pp/print-table [:name :status :created]
                      (map format-container-for-display containers)))))
```

### Pattern 5: CLI Integration (Minimal)
**What:** Add ps as pass-through command in dispatch (no flags needed)
**When to use:** Wire ps command into CLI
**Example:**
```clojure
;; Source: Existing cli.clj dispatch pattern (check, exec commands)
;; Location: src/aishell/cli.clj

;; In dispatch function, add case:
(case (first args)
  "check" (check/run-check)
  "exec" (run/run-exec (vec (rest args)))
  "attach" (handle-attach (rest args))
  "ps" (handle-ps)  ;; NEW - no args needed
  ;; ... existing cases
  )
```

### Anti-Patterns to Avoid
- **External table library**: Doric/table libraries add dependencies; `print-table` sufficient for simple tables
- **Custom Docker queries**: Phase 30's `list-project-containers` already exists and works
- **Relative time formatting**: Adds complexity (parsing, edge cases); Docker's timestamps are clear
- **Complex filtering flags**: v1 should list all project containers; filtering can come later
- **JSON output mode**: Premature optimization; start with human-readable table

## Don't Hand-Roll

Problems that look simple but have existing solutions:

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| Querying Docker containers by project | Custom `docker ps` parsing | Phase 30 `naming/list-project-containers` | Already implemented, handles project hash filtering, returns structured data |
| Table formatting with columns | Manual padding/alignment | `clojure.pprint/print-table` | Built into Clojure, automatic column width calculation, clean output |
| Container name parsing | Regex or manual string splits | `clojure.string/split` with limit | Standard library, handles edge cases (names with hyphens) |
| Project hash calculation | Re-implement hash logic | Phase 30 `naming/project-hash` | Already exists, tested, deterministic |

**Key insight:** 90% of the work is already done. Phase 30 built the infrastructure, this phase is just a thin presentation layer.

## Common Pitfalls

### Pitfall 1: Reinventing Container Query Logic
**What goes wrong:** Writing custom Docker filtering logic instead of using existing function

**Why it happens:**
- Developer doesn't discover `list-project-containers` already exists in `naming.clj`
- Temptation to "improve" the existing function
- Not checking Phase 30 implementation first

**How to avoid:**
1. Read `src/aishell/docker/naming.clj` before starting
2. Use `list-project-containers` as-is (already returns structured data)
3. Focus implementation on presentation layer only

**Warning signs:**
- Writing new Docker CLI calls with `--filter` flags
- Parsing Docker output format strings
- Re-implementing project hash generation

**Prevention code:**
```clojure
;; WRONG: Re-implementing container query
(defn get-containers []
  (let [hash (compute-project-hash (System/getProperty "user.dir"))
        {:keys [out]} (p/shell {:out :string} "docker" "ps" "-a"
                               "--filter" (str "name=aishell-" hash)
                               "--format" "{{.Names}}\t{{.Status}}\t{{.CreatedAt}}")]
    ;; ... custom parsing logic ...
    ))

;; RIGHT: Use existing infrastructure
(defn get-containers []
  (naming/list-project-containers (System/getProperty "user.dir")))
```

**Source:** Phase 30 `naming.clj` lines 108-128

---

### Pitfall 2: Over-Engineering Time Display
**What goes wrong:** Implementing complex relative time formatting ("2 hours ago") when Docker timestamps are sufficient

**Why it happens:**
- Desire to match `docker ps` default output (which shows relative times)
- Assumption that users need "ago" format
- Not considering timezone/locale edge cases

**How to avoid:**
1. Use Docker's `.CreatedAt` timestamp as-is for v1
2. Full timestamps are more informative (exact time vs approximation)
3. Defer relative time to future enhancement if users request it

**Warning signs:**
- Parsing Docker timestamps into `java.time.Instant`
- Calculating durations between now and created time
- String formatting with "ago" suffix
- Timezone conversion logic

**Prevention code:**
```clojure
;; WRONG: Complex relative time
(defn format-created-time [timestamp-str]
  (let [created (java.time.Instant/parse timestamp-str)
        now (java.time.Instant/now)
        duration (java.time.Duration/between created now)
        hours (.toHours duration)]
    (if (< hours 24)
      (str hours " hours ago")
      (str (.toDays duration) " days ago"))))

;; RIGHT: Use Docker timestamp as-is
(defn format-container [container]
  {:name (extract-short-name (:name container))
   :status (:status container)
   :created (:created container)})  ;; Docker format: "2026-01-31 10:15:05 +0000 UTC"
```

**Source:** Docker returns full timestamps, keep simple

---

### Pitfall 3: Full Container Name Display Clutter
**What goes wrong:** Showing full container names (aishell-a1b2c3d4-claude) makes output hard to read

**Why it happens:**
- Directly using `:name` field from `list-project-containers`
- Not considering user experience in table output
- Thinking users need full name for Docker CLI

**How to avoid:**
1. Extract short name for primary display column
2. Optionally show full name in verbose mode or on error
3. Keep table focused on user-friendly names

**Warning signs:**
- Table showing "aishell-a1b2c3d4-claude" instead of "claude"
- Users have to mentally parse hash from every row
- Confusion about which container is which

**Prevention code:**
```clojure
;; Extract short name from container name
(defn extract-short-name [full-name]
  (last (str/split full-name #"-" 3)))

;; Use in display
(defn format-container [container]
  {:name (extract-short-name (:name container))  ;; "claude" instead of "aishell-hash-claude"
   :status (:status container)
   :created (:created container)})
```

**Source:** UX consideration - users named containers, show those names

---

### Pitfall 4: Missing Empty State Guidance
**What goes wrong:** Empty output when no containers exist, user doesn't know what to do

**Why it happens:**
- Treating empty container list as edge case
- Not providing next steps
- Assuming users remember how to start containers

**How to avoid:**
1. Detect empty container list explicitly
2. Show helpful message with examples
3. Explain that containers are project-specific (directory-based)

**Warning signs:**
- Silent empty output (just blank screen)
- Error message instead of guidance
- User has to `--help` to figure out next steps

**Prevention code:**
```clojure
;; WRONG: Silent empty state
(defn handle-ps [_]
  (let [containers (naming/list-project-containers (System/getProperty "user.dir"))]
    (pp/print-table [:name :status :created] (map format-container containers))))

;; RIGHT: Helpful empty state
(defn handle-ps [_]
  (let [containers (naming/list-project-containers (System/getProperty "user.dir"))]
    (if (empty? containers)
      (println "No containers running for this project.\n"
               "\nTo start a container:"
               "\n  aishell claude --detach"
               "\n  aishell shell --detach --name dev"
               "\n\nContainers are project-specific (based on directory).")
      (pp/print-table [:name :status :created] (map format-container containers)))))
```

**Source:** UX best practice - empty states should guide users

---

### Pitfall 5: Ignoring Stopped Containers
**What goes wrong:** `list-project-containers` queries `docker ps -a` (includes stopped), but user expects only running

**Why it happens:**
- Phase 30 function uses `-a` flag (all containers)
- Not filtering by running state in ps command
- Requirements unclear about stopped vs running

**How to avoid:**
1. Check requirements (DISC-01 says "running containers")
2. Filter containers by status or modify query
3. Or show both with status column making it clear

**Warning signs:**
- Stopped containers appearing in ps output
- Confusion about why "Exited" containers show up
- Users asking how to remove stopped containers

**Prevention approach:**
```clojure
;; Option 1: Filter in ps command
(defn handle-ps [_]
  (let [all-containers (naming/list-project-containers (System/getProperty "user.dir"))
        running (filter #(str/starts-with? (:status %) "Up") all-containers)]
    ;; ... display running only
    ))

;; Option 2: Show all with clear status column (status makes state obvious)
;; "Up 2 hours" vs "Exited (0) 5 minutes ago" - user can see difference
```

**Note:** Requirements say "running containers" but showing all with status column may be more useful. Status column makes running vs stopped obvious.

**Source:** DISC-01 requirement clarity needed

## Code Examples

Verified patterns from official sources and existing codebase:

### Complete PS Command Implementation (Minimal)
```clojure
;; Source: Phase 30 naming.clj + clojure.pprint/print-table
;; MODIFIED FILE: src/aishell/cli.clj (add handle-ps function)

(require '[clojure.pprint :as pp]
         '[clojure.string :as str]
         '[aishell.docker.naming :as naming])

(defn- extract-short-name
  "Extract user-friendly name from full container name.
   aishell-{hash}-{name} -> {name}"
  [container-name]
  (last (str/split container-name #"-" 3)))

(defn- format-container
  "Transform container map for display.
   Extracts short name, keeps status and created timestamp."
  [container]
  {:name (extract-short-name (:name container))
   :status (:status container)
   :created (:created container)})

(defn handle-ps
  "List containers for current project.
   Shows container name, status (Up/Exited), and creation time."
  [_]
  (let [project-dir (System/getProperty "user.dir")
        containers (naming/list-project-containers project-dir)]
    (if (empty? containers)
      ;; Empty state with guidance
      (println "No containers found for this project.\n"
               "\nTo start a container:"
               "\n  aishell claude --detach"
               "\n  aishell shell --detach --name dev"
               "\n\nContainers are project-specific (based on directory).")
      ;; Display table
      (do
        (println "Containers for this project:\n")
        (pp/print-table [:name :status :created]
                        (map format-container containers))
        (println "\nTo attach: aishell attach --name <name>")
        (println "To stop: docker stop <name>")))))
```

### CLI Dispatch Integration
```clojure
;; Source: Existing cli.clj dispatch pattern (check, exec, attach)
;; MODIFIED FILE: src/aishell/cli.clj

;; Add ps case to dispatch function
(defn dispatch [args]
  ;; ... existing flag extraction ...
  (case (first clean-args)
    "check" (check/run-check)
    "exec" (run/run-exec (vec (rest clean-args)))
    "attach" (handle-attach (rest clean-args))
    "ps" (handle-ps nil)  ;; NEW - no args needed
    "claude" (run/run-container "claude" ...)
    ;; ... rest of dispatch
    ))

;; Add to help text (print-help function)
(defn print-help []
  (println (str output/BOLD "Commands:" output/NC))
  (println (str "  " output/CYAN "build" output/NC "      Build the container image"))
  (println (str "  " output/CYAN "check" output/NC "      Validate setup and configuration"))
  (println (str "  " output/CYAN "ps" output/NC "         List project containers"))  ;; NEW
  (println (str "  " output/CYAN "attach" output/NC "     Attach to running container"))
  ;; ... existing commands
  )
```

### Help Text for PS Command
```clojure
;; Optional: Add --help support for ps (if implementing help)
(defn print-ps-help []
  (println (str output/BOLD "Usage:" output/NC " aishell ps"))
  (println)
  (println "List containers for the current project.")
  (println)
  (println "Shows container name, status (Up/Exited), and creation time.")
  (println "Containers are project-specific based on the current directory.")
  (println)
  (println (str output/BOLD "Examples:" output/NC))
  (println (str "  " output/CYAN "aishell ps" output/NC))
  (println (str "      List all containers for this project"))
  (println)
  (println (str output/BOLD "See also:" output/NC))
  (println "  aishell attach --name <name>  - Attach to a container")
  (println "  docker stop <container>       - Stop a container"))
```

### Alternative: Filter to Running Only
```clojure
;; If requirements strictly mean "running only" (not stopped)
(defn handle-ps [_]
  (let [project-dir (System/getProperty "user.dir")
        all-containers (naming/list-project-containers project-dir)
        ;; Filter to running containers only (status starts with "Up")
        running (filter #(str/starts-with? (:status %) "Up") all-containers)]
    (if (empty? running)
      (println "No running containers for this project.\n"
               "\nTo start: aishell <harness> --detach"
               "\nStopped containers: docker ps -a --filter name=aishell")
      (do
        (println "Running containers for this project:\n")
        (pp/print-table [:name :status :created]
                        (map format-container running))))))
```

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| No container discovery | `aishell ps` command | Phase 34 (v2.6.0) | Users can see what containers exist |
| Manual `docker ps` filtering | Project-scoped listing | Phase 30/34 | Automatic project isolation |
| Full container names everywhere | Short name extraction | Phase 34 (v2.6.0) | Cleaner UX, less cognitive load |
| Custom table formatting | `clojure.pprint/print-table` | Standard practice | Built-in, no dependencies |

**Deprecated/outdated:**
- Manual Docker commands: `docker ps --filter name=aishell-$(hash)` replaced by `aishell ps`
- Full container name display: Extract short names for readability
- Custom table libraries: Built-in `print-table` sufficient for simple tables

## Open Questions

1. **Running vs All Containers**
   - What we know: DISC-01 says "running containers", but Phase 30 function queries all (`docker ps -a`)
   - What's unclear: Should stopped containers appear in output?
   - Recommendation: Show all containers with status column (makes state obvious). Filtering to running-only loses visibility into stopped containers that might need cleanup.

2. **Table Column Headers**
   - What we know: Requirements specify name, status, created
   - What's unclear: Should headers be uppercase (NAME, STATUS) or title case (Name, Status)?
   - Recommendation: Use title case (Name, Status, Created) - friendlier than all-caps. `print-table` uses keyword as-is, control with custom formatting if needed.

3. **Full Name Display Option**
   - What we know: Short names cleaner for display
   - What's unclear: Should there be verbose mode showing full container names?
   - Recommendation: Not for v1. Users can use `docker ps` if they need full names. Keep ps output focused and simple.

4. **Additional Helpful Commands in Output**
   - What we know: Empty state shows examples
   - What's unclear: Should non-empty output show next-step commands (attach, stop)?
   - Recommendation: Yes - add footer with "To attach: aishell attach --name <name>" and "To stop: docker stop <name>" for discoverability.

## Sources

### Primary (HIGH confidence)
- **Existing codebase**:
  - Phase 30 `src/aishell/docker/naming.clj` (L108-128) - `list-project-containers` function
  - Phase 30 `src/aishell/docker/naming.clj` (L11-21) - `project-hash` function
  - Existing `src/aishell/cli.clj` - CLI dispatch pattern (check, exec, attach commands)
- **Clojure documentation**:
  - [clojure.pprint/print-table](https://clojuredocs.org/clojure.pprint/print-table) - Built-in table formatting
  - [clojure.string/split](https://clojuredocs.org/clojure.string/split) - String manipulation
- **Requirements**: `.planning/REQUIREMENTS.md` (DISC-01, DISC-02) - Functional requirements

### Secondary (MEDIUM confidence)
- **Docker documentation**:
  - [docker container ls](https://docs.docker.com/reference/cli/docker/container/ls/) - Docker ps format options
  - Docker ps format fields: `.Names`, `.Status`, `.CreatedAt`
- **Babashka documentation**:
  - [Babashka book](https://book.babashka.org/) - Clojure scripting patterns
  - [Print tabular data with Doric](https://github.com/babashka/babashka/blob/master/doc/projects.md) - Alternative table library (not used)

### Tertiary (LOW confidence)
- **Community patterns**:
  - [GitHub - joegallo/doric](https://github.com/joegallo/doric) - Table formatting library (reference only)
  - [GitHub - cldwalker/table](https://github.com/cldwalker/table) - Alternative table library (not used)

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH - All components already exist (Phase 30 function, `print-table` built-in)
- Architecture: HIGH - Minimal new code, leverages existing infrastructure completely
- Pitfalls: MEDIUM - Mostly UX considerations (empty state, name display) rather than technical risks
- Implementation: HIGH - Straightforward presentation layer, no Docker/infrastructure changes

**Research date:** 2026-01-31
**Valid until:** 90 days (stable domain - Docker CLI and Clojure core rarely change)

**Critical dependencies for planning:**
- Phase 30 provides: `list-project-containers`, `project-hash`, container name format
- Clojure built-in: `print-table` for formatting, `clojure.string` for parsing
- Current codebase: CLI dispatch pattern, output color utilities

**Implementation complexity:** VERY LOW
- Reuses 100% existing container query infrastructure
- New code: ~40 lines (format function + dispatch case + help text)
- No new dependencies, no Docker changes, no image rebuild
- Simplest phase in v2.6.0 roadmap

---
*Phase: 34-ps-command-and-polish*
*Research completed: 2026-01-31*
