# Phase 38: Volume Cleanup & Documentation - Research

**Researched:** 2026-02-01
**Domain:** Docker volume management, CLI table formatting, confirmation prompts, version diff detection, documentation updates
**Confidence:** HIGH

## Summary

Phase 38 implements volume lifecycle management commands (list, prune) and comprehensive documentation updates for the v2.8.0 foundation/volume architecture. The phase also repurposes the `aishell update` command to focus on harness volume refresh with optional foundation rebuild.

The implementation centers on five components: (1) Redesigning `aishell update` to unconditionally repopulate volumes (delete-and-recreate strategy) with `--force` triggering both foundation rebuild and volume repopulation, (2) implementing volume list command showing active/orphaned status using table output (following `aishell ps` pattern), (3) implementing volume prune command with confirmation prompt (following interactive/non-interactive best practices), (4) adding version diff detection during npm package installation when feasible, and (5) updating all documentation to reflect the foundation/volume architecture changes in a single v2.8.0 changelog entry.

The approach leverages Docker's native volume inspection APIs, Clojure's `print-table` for formatted output, existing confirmation prompt patterns from detection framework, and npm CLI capabilities for version detection.

**Primary recommendation:** Use Docker volume ls/inspect with label filtering for orphan detection, implement table-based list output with `clojure.pprint/print-table`, add interactive confirmation prompts using existing `prompt-yn` pattern, attempt version diff display during npm installs, and create comprehensive v2.8.0 changelog entry covering phases 35-38.

## Standard Stack

### Core Technologies

| Technology | Version | Purpose | Why Standard |
|------------|---------|---------|--------------|
| Docker CLI | 1.9+ | Volume management and inspection | Native Docker volume operations with label support |
| `clojure.pprint/print-table` | Clojure 1.12+ | Formatted table output | Already used in `aishell ps`, built-in Clojure function |
| `docker system df -v` | Docker 1.9+ | Volume size calculation | Official Docker command for disk usage statistics |
| npm list/view | npm 7+ | Version detection | Standard npm CLI for package version queries |
| babashka.process | Latest | Shell command execution | Already used throughout aishell for Docker commands |

### Supporting Patterns

| Pattern | Purpose | When to Use |
|---------|---------|-------------|
| Delete-and-recreate volumes | Clean slate on update | Preventing stale files from old harness versions |
| Label-based filtering | Orphan detection | Filtering volumes by metadata without parsing names |
| Interactive confirmation | Safety for destructive operations | Volume prune (moderate danger level) |
| Table output with custom columns | Structured CLI output | Listing volumes with status/size/harnesses |
| Version diff display | User feedback during upgrades | npm install operations when version detectable |

### Volume Management Approach

Phase 38 uses **single-machine orphan detection** based on state.edn reference:

```clojure
;; Orphan detection logic
(defn volume-orphaned?
  [volume-name state]
  (let [current-volume-name (:harness-volume-name state)]
    ;; Volume is orphaned if not referenced by current state
    (and volume-name
         (str/starts-with? volume-name "aishell-harness-")
         (not= volume-name current-volume-name))))
```

**Why this works:** State file contains single harness-volume-name field. Any aishell-harness-* volume not matching this name is orphaned (old configuration, deleted state, or manual creation). No cross-project scanning needed.

**Source:** Docker volume label filtering - [Docker volume ls documentation](https://docs.docker.com/reference/cli/docker/volume/ls/)

## Architecture Patterns

### Pattern 1: Update Command Redesign - Unconditional Volume Repopulation

**What:** `aishell update` always repopulates harness volume (delete-and-recreate), `--force` rebuilds foundation AND repopulates volume

**When to use:** Ensuring users get latest harness versions without cache/staleness issues

**Implementation:**

```clojure
;; cli.clj - Updated handle-update function
(defn handle-update
  "Update command: repopulate harness volume unconditionally.
   With --force: rebuild foundation image AND repopulate volume.
   Without --force: repopulate volume only (foundation unchanged)."
  [{:keys [opts]}]
  (let [state (state/read-state)]
    (when-not state
      (output/error "No previous build found. Run: aishell build"))

    ;; Step 1: Rebuild foundation if --force
    (when (:force opts)
      (println "Rebuilding foundation image...")
      (build/build-foundation-image
        {:with-gitleaks (:with-gitleaks state true)
         :verbose (:verbose opts)
         :force true}))

    ;; Step 2: Always repopulate volume (delete + recreate)
    (let [volume-name (:harness-volume-name state)]
      (when volume-name
        (println "Repopulating harness volume...")
        ;; Delete existing volume
        (vol/remove-volume volume-name)
        ;; Recreate with same hash
        (vol/create-volume volume-name
          {"aishell.harness.hash" (:harness-volume-hash state)
           "aishell.harness.version" "2.8.0"})
        ;; Populate with latest versions
        (vol/populate-volume volume-name state {:verbose (:verbose opts)})))))
```

**Why unconditional repopulation:** Hash-based staleness detection only catches config changes, not upstream version updates. Users expect `aishell update` to get latest versions even if configuration is unchanged. Delete-and-recreate ensures no stale files from previous installs.

**Source pattern:** Similar to `npm update` behavior - always fetches latest within semver range

### Pattern 2: Volume List with Table Output

**What:** Display volumes in table format showing name, harnesses, size, and orphaned status

**When to use:** `aishell volumes list` (or `aishell volumes` without subcommand)

**Implementation:**

```clojure
;; docker/volume.clj - List volumes with metadata
(defn list-volumes
  "List all aishell harness volumes with metadata.
   Returns vector of maps suitable for print-table."
  [state]
  (let [current-volume (:harness-volume-name state)
        ;; Get all aishell-harness-* volumes
        volumes (p/shell {:out :string}
                         "docker" "volume" "ls"
                         "--filter" "name=aishell-harness-"
                         "--format" "{{.Name}}")
        volume-names (str/split-lines (:out volumes))

        ;; Get size info from docker system df -v
        df-output (p/shell {:out :string}
                           "docker" "system" "df" "-v")

        ;; Parse and format each volume
        (mapv (fn [vol-name]
                (let [hash (vol/get-volume-label vol-name "aishell.harness.hash")
                      orphaned? (not= vol-name current-volume)
                      ;; Parse size from df output (complex parsing)
                      size (parse-volume-size df-output vol-name)]
                  {:NAME vol-name
                   :HARNESSES (harnesses-from-hash vol-name)  ; Requires label or inspection
                   :SIZE size
                   :STATUS (if orphaned? "orphaned" "active")}))
              volume-names)]
    volumes))

;; cli.clj - Table display (following aishell ps pattern)
(defn handle-volumes-list [_]
  (let [state (state/read-state)
        volumes (vol/list-volumes state)]
    (if (empty? volumes)
      (println "No harness volumes found.")
      (do
        (println "Harness volumes:\n")
        (pp/print-table [:NAME :STATUS :SIZE :HARNESSES] volumes)
        (println "\nTo remove orphaned volumes: aishell volumes prune")))))
```

**Table output pattern:** Uses `clojure.pprint/print-table` with explicit column order, matching `aishell ps` implementation. Column names are uppercase keywords for consistent header display.

**Source:** [clojure.pprint/print-table documentation](https://clojuredocs.org/clojure.pprint/print-table) - custom column ordering with `(print-table [:COL1 :COL2] rows)`

### Pattern 3: Volume Size Detection

**What:** Get volume disk usage using `docker system df -v` command

**When to use:** Displaying volume sizes in list output

**Implementation:**

```clojure
;; docker/volume.clj - Parse volume size from docker system df
(defn get-volume-size
  "Get size of Docker volume using docker system df -v.
   Returns formatted size string (e.g., '245.3MB') or 'N/A' on failure."
  [volume-name]
  (try
    (let [{:keys [exit out]} (p/shell {:continue true
                                       :out :string
                                       :err :string}
                                      "docker" "system" "df" "-v"
                                      "--format" "{{.Name}}\t{{.Size}}")]
      (if (zero? exit)
        ;; Parse output: find line starting with volume-name
        (if-let [line (->> (str/split-lines out)
                          (filter #(str/starts-with? % volume-name))
                          first)]
          (second (str/split line #"\t"))
          "N/A")
        "N/A"))
    (catch Exception _
      "N/A")))
```

**Why docker system df:** Docker volume inspect doesn't provide size information directly. The `docker system df -v` command shows detailed volume information including disk usage. Alternative is `du -sh /var/lib/docker/volumes/<name>` but requires root access.

**Source:** [Docker system df documentation](https://docs.docker.com/reference/cli/docker/system/df/) - verbose flag shows per-volume size information

### Pattern 4: Volume Prune with Confirmation

**What:** Remove orphaned volumes with interactive confirmation or `--yes` flag bypass

**When to use:** `aishell volumes prune` command for cleanup

**Implementation:**

```clojure
;; cli.clj - Volume prune with confirmation
(defn handle-volumes-prune
  [{:keys [opts]}]
  (let [state (state/read-state)
        current-volume (:harness-volume-name state)
        all-volumes (list-volume-names)
        orphaned (filter #(and (str/starts-with? % "aishell-harness-")
                              (not= % current-volume))
                        all-volumes)]
    (if (empty? orphaned)
      (println "No orphaned volumes to prune.")
      (do
        ;; Show what will be removed
        (println "The following volumes will be removed:")
        (doseq [vol orphaned]
          (println (str "  - " vol)))
        (println)

        ;; Confirm or bypass with --yes
        (if (or (:yes opts)
                (not (interactive?))
                (prompt-yn "Remove these volumes?"))
          (do
            (doseq [vol orphaned]
              ;; Check if volume is in use
              (if (volume-in-use? vol)
                (println (str "Skipping " vol " (in use by container)"))
                (do
                  (vol/remove-volume vol)
                  (println (str "Removed " vol)))))
            (println "Prune complete."))
          (println "Prune cancelled."))))))

;; Reuse existing prompt-yn from detection.core
(defn- prompt-yn
  "Prompt user for y/n confirmation. Returns true if user enters 'y'."
  [message]
  (print (str message " (y/n): "))
  (flush)
  (let [response (str/lower-case (or (read-line) ""))]
    (= response "y")))
```

**Confirmation UX:** Following CLI best practices - show what will be deleted, prompt for confirmation in interactive mode, allow `--yes` flag bypass for scripts. Skip volumes in use with warning (prevents data loss).

**Source:** [CLI confirmation prompt best practices](https://clig.dev/) - confirm before destructive operations, provide --yes flag for automation

### Pattern 5: Version Diff Detection During Install

**What:** Show version changes during npm install when detectable (e.g., "claude-code@2.1.0 → 2.2.0")

**When to use:** Volume population during `aishell update` command

**Implementation:**

```clojure
;; docker/volume.clj - Detect and display version changes
(defn detect-package-version
  "Get currently installed version of npm package.
   Returns version string or nil if not installed."
  [package-name volume-name]
  (try
    (let [cmd ["docker" "run" "--rm"
               "-v" (str volume-name ":/tools")
               build/foundation-image-tag
               "sh" "-c"
               (str "export NPM_CONFIG_PREFIX=/tools/npm && "
                    "npm list -g " package-name " --depth=0 --json 2>/dev/null")]
          {:keys [exit out]} (p/shell {:continue true
                                       :out :string
                                       :err :string}
                                      cmd)]
      (when (zero? exit)
        (let [data (json/parse-string out true)
              deps (get-in data [:dependencies (keyword package-name)])]
          (:version deps))))
    (catch Exception _
      nil)))

(defn populate-volume-with-diff
  "Populate volume showing version diffs when detectable.
   Displays 'package@old → new' for upgrades."
  [volume-name state opts]
  (let [packages (extract-npm-packages state)
        ;; Get current versions before deletion (if volume exists)
        old-versions (when (volume-exists? volume-name)
                      (into {} (map (fn [pkg]
                                     [pkg (detect-package-version pkg volume-name)])
                                   (map first packages))))]

    ;; Delete and recreate volume
    (vol/remove-volume volume-name)
    (vol/create-volume volume-name {...})

    ;; Install with version tracking
    (println "Installing harness tools:")
    (doseq [[pkg version] packages]
      (let [old-ver (get old-versions pkg)
            display (if (and old-ver (not= old-ver version))
                     (str pkg "@" old-ver " → " version)
                     (str pkg "@" (or version "latest")))]
        (println (str "  " display))))

    ;; Run npm install
    (vol/populate-volume volume-name state opts)))
```

**Feasibility:** Version detection requires running container with mounted volume BEFORE deletion, adding complexity. Simpler approach: show target versions from state, skip old version detection (less useful feedback but simpler implementation).

**Simplified alternative:** Just show "Installing claude-code@2.2.0" without diff (clearer, no container overhead)

**Source:** [npm list command documentation](https://docs.npmjs.com/cli/v9/commands/npm-ls/) - JSON output format for programmatic parsing

## Don't Hand-Roll

Problems that look simple but have existing solutions:

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| Volume size calculation | Custom du parsing | `docker system df -v` | Official Docker command with reliable output format |
| Table formatting | Manual column padding | `clojure.pprint/print-table` | Built-in function, handles alignment and headers |
| Volume filtering | Name parsing | Docker label filtering | Metadata-based filtering more reliable than name conventions |
| Confirmation prompts | Custom input validation | Existing `prompt-yn` pattern | Already tested in detection framework |
| Version diff detection | Complex npm parsing | Show target versions only | Old version detection adds complexity with minimal user value |

**Key insight:** Docker provides native volume management APIs (ls, inspect, df, prune). Use these instead of parsing names or directly accessing /var/lib/docker/volumes. Label-based metadata is the correct approach for volume classification.

## Common Pitfalls

### Pitfall 1: Volume In-Use Detection

**What goes wrong:** Attempting to remove volumes currently mounted by running containers causes errors

**Why it happens:** Docker won't remove in-use volumes, but error messages aren't user-friendly

**How to avoid:** Check container status before volume removal

```clojure
(defn volume-in-use?
  "Check if volume is mounted by any container."
  [volume-name]
  (try
    (let [{:keys [exit out]} (p/shell {:continue true
                                       :out :string}
                                      "docker" "ps" "-a"
                                      "--filter" (str "volume=" volume-name)
                                      "--format" "{{.Names}}")]
      (and (zero? exit) (not (str/blank? out))))
    (catch Exception _
      false)))

;; Usage in prune
(if (volume-in-use? vol)
  (println (str "Skipping " vol " (in use by container " container-name ")"))
  (vol/remove-volume vol))
```

**Warning signs:** "Error response from daemon: remove volume in use" messages during prune

### Pitfall 2: Hash-Only Volume Names Lack Context

**What goes wrong:** Volume list shows "aishell-harness-abc123def456" with no indication what's inside

**Why it happens:** Volume name is hash-based for determinism, but hash alone isn't human-readable

**How to avoid:** Store harness metadata in volume labels, display in list output

```clojure
;; During volume creation, add harness metadata
(vol/create-volume volume-name
  {"aishell.harness.hash" hash
   "aishell.harness.version" "2.8.0"
   "aishell.harnesses" (pr-str [:claude :codex])})  ; NEW: Store harness list

;; During list, retrieve and display
(defn harnesses-from-labels
  [volume-name]
  (when-let [harnesses-str (vol/get-volume-label volume-name "aishell.harnesses")]
    (try
      (edn/read-string harnesses-str)
      (catch Exception _ []))))
```

**Warning signs:** Users can't tell which volumes contain which harnesses without manual inspection

### Pitfall 3: Update Command Confusion - What Gets Updated?

**What goes wrong:** Users expect `aishell update` to accept harness selection flags like `--with-claude`, causing confusion when it doesn't

**Why it happens:** Separation between build (what's present) and update (refresh what's present) isn't immediately clear

**How to avoid:** Clear help text and error messages explaining the separation

```clojure
;; Help text for update command
(defn print-update-help []
  (println (str output/BOLD "Usage:" output/NC " aishell update [OPTIONS]"))
  (println)
  (println "Refresh harness tools to latest versions using existing configuration.")
  (println)
  (println (str output/BOLD "Options:" output/NC))
  (println "  --force          Also rebuild foundation image with --no-cache")
  (println "  --verbose, -v    Show full build and install output")
  (println)
  (println (str output/BOLD "Notes:" output/NC))
  (println "  - Refreshes harnesses from last 'aishell build' command")
  (println "  - To change which harnesses are installed: use 'aishell build'")
  (println "  - Volume is deleted and recreated for clean slate")
  (println)
  (println (str output/BOLD "Examples:" output/NC))
  (println (str "  " output/CYAN "aishell update" output/NC "           Refresh harness volumes"))
  (println (str "  " output/CYAN "aishell update --force" output/NC "  Also rebuild foundation image")))
```

**Warning signs:** GitHub issues asking "why doesn't `aishell update --with-opencode` work?"

### Pitfall 4: Volume Prune Safety - Default to Safe

**What goes wrong:** Accidentally pruning active volume when state file is missing or corrupt

**Why it happens:** Orphan detection relies on state.edn correctness

**How to avoid:** Conservative orphan detection - only prune if clearly orphaned

```clojure
(defn safe-to-prune?
  "Conservative check: only prune if clearly orphaned.
   Returns false if state is missing or ambiguous."
  [volume-name state]
  (and state  ; State file must exist
       (:harness-volume-name state)  ; State must have volume reference
       (str/starts-with? volume-name "aishell-harness-")  ; Must be harness volume
       (not= volume-name (:harness-volume-name state))  ; Must not be current
       (not (volume-in-use? volume-name))))  ; Must not be mounted
```

**Warning signs:** Prune removes volume user is actively using because state file was accidentally deleted

## Code Examples

Verified patterns from official sources and existing codebase:

### Volume List Command (Following aishell ps Pattern)

```clojure
;; cli.clj - List volumes with table output
(defn handle-volumes-list
  "List harness volumes with status and metadata."
  [_]
  (let [state (state/read-state)
        volumes (vol/list-volumes state)]
    (if (empty? volumes)
      (println "No harness volumes found.\n\nVolumes are created automatically when running harnesses after 'aishell build'.")
      (do
        (println "Harness volumes:\n")
        (pp/print-table [:NAME :STATUS :SIZE :HARNESSES] volumes)
        (println "\nTo remove orphaned volumes: aishell volumes prune")))))

;; docker/volume.clj - List implementation
(defn list-volumes
  "List all aishell harness volumes.
   Returns vector of maps with :NAME, :STATUS, :SIZE, :HARNESSES keys."
  [state]
  (let [current-volume (:harness-volume-name state)
        {:keys [out]} (p/shell {:out :string}
                              "docker" "volume" "ls"
                              "--filter" "name=aishell-harness-"
                              "--format" "{{.Name}}")
        volume-names (remove str/blank? (str/split-lines out))]
    (mapv (fn [vol-name]
            {:NAME vol-name
             :STATUS (if (= vol-name current-volume) "active" "orphaned")
             :SIZE (get-volume-size vol-name)
             :HARNESSES (harnesses-from-labels vol-name)})
          volume-names)))
```

**Source:** Existing `aishell ps` command in cli.clj (lines 304-314)

### Volume Prune with Confirmation

```clojure
;; cli.clj - Prune orphaned volumes
(defn handle-volumes-prune
  "Remove orphaned harness volumes with confirmation."
  [{:keys [opts]}]
  (let [state (state/read-state)
        volumes (vol/list-volumes state)
        orphaned (filter #(= "orphaned" (:STATUS %)) volumes)]
    (if (empty? orphaned)
      (println "No orphaned volumes to prune.")
      (do
        (println "The following volumes will be removed:")
        (doseq [vol orphaned]
          (println (str "  - " (:NAME vol) " (" (:HARNESSES vol) ")")))
        (println)

        (if (or (:yes opts)
                (and (interactive?) (prompt-yn "Remove these volumes?")))
          (do
            (doseq [vol orphaned]
              (let [vol-name (:NAME vol)]
                (if (volume-in-use? vol-name)
                  (println (str "Skipping " vol-name " (in use)"))
                  (do
                    (vol/remove-volume vol-name)
                    (println (str "Removed " vol-name))))))
            (println "\nPrune complete."))
          (println "Prune cancelled."))))))

;; Reuse confirmation pattern from detection/core.clj
(defn- interactive?
  "Check if running in an interactive terminal."
  []
  (some? (System/console)))

(defn- prompt-yn
  "Prompt for y/n confirmation."
  [message]
  (print (str message " (y/n): "))
  (flush)
  (= "y" (str/lower-case (or (read-line) ""))))
```

**Source:** detection/core.clj (lines 138-149) - existing confirmation pattern

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| `aishell update` rebuilds foundation image | `aishell update` repopulates volume only, `--force` rebuilds both | Phase 38 (v2.8.0) | Faster updates (skip foundation rebuild if not needed) |
| No volume management commands | `aishell volumes list/prune` for lifecycle management | Phase 38 (v2.8.0) | Users can inspect and clean up orphaned volumes |
| Single-image architecture | Foundation + volume architecture | Phase 35-37 (v2.8.0) | Faster harness updates, better disk usage |
| Version detection during install | Show target versions only | Phase 38 (decision) | Simpler implementation, less container overhead |

**Deprecated/outdated:**

- `aishell update` without state file (now errors with "Run: aishell build" message)
- Manual Docker volume pruning (replaced with `aishell volumes prune`)
- Hash-based staleness detection alone (update now always repopulates for freshness)

## Open Questions

Things that couldn't be fully resolved:

1. **Volume Harnesses Label Storage**
   - What we know: Labels support string values, can store EDN/JSON
   - What's unclear: Whether to store harness list as label for display or compute from hash
   - Recommendation: Store harness list as label during creation, simplifies list display

2. **Command Naming Convention**
   - What we know: Existing commands are simple verbs (build, update, check, ps, attach)
   - What's unclear: Whether to use `aishell volume` or `aishell volumes` (singular vs plural)
   - Recommendation: Use `aishell volumes` (plural) to match Docker's `docker volume` convention

3. **Version Diff Implementation Complexity**
   - What we know: Version detection requires running container before deletion
   - What's unclear: Whether user value justifies implementation complexity
   - Recommendation: Skip version diff, show target versions only (simpler, clearer)

4. **No-Harness Update Edge Case**
   - What we know: Update without harnesses enabled is valid (foundation-only setup)
   - What's unclear: Should it error, warn, or silently succeed?
   - Recommendation: Silent success - volume operations are optional, focus on foundation rebuild with `--force`

## Sources

### Primary (HIGH confidence)

- [Docker volume ls documentation](https://docs.docker.com/reference/cli/docker/volume/ls/) - Label filtering and format options
- [Docker system df documentation](https://docs.docker.com/reference/cli/docker/system/df/) - Volume size detection
- [Docker volume prune documentation](https://docs.docker.com/reference/cli/docker/volume/prune/) - Prune confirmation and --force flag
- [clojure.pprint/print-table documentation](https://clojuredocs.org/clojure.pprint/print-table) - Custom column table formatting
- [babashka.cli documentation](https://github.com/babashka/cli) - Subcommand dispatch patterns
- Existing codebase patterns:
  - `/home/jonasrodrigues/projects/harness/src/aishell/cli.clj` (lines 304-314) - `aishell ps` table output pattern
  - `/home/jonasrodrigues/projects/harness/src/aishell/detection/core.clj` (lines 138-149) - confirmation prompt pattern
  - `/home/jonasrodrigues/projects/harness/src/aishell/docker/volume.clj` - volume operations API

### Secondary (MEDIUM confidence)

- [CLI Guidelines (clig.dev)](https://clig.dev/) - Confirmation prompt best practices
- [npm list documentation](https://docs.npmjs.com/cli/v9/commands/npm-ls/) - Version detection via JSON output
- [Medium article on CLI version update warnings](https://medium.com/trabe/improve-the-ux-of-cli-tools-with-version-update-warnings-23eb8fcb474a) - UX patterns for version updates

### Tertiary (LOW confidence)

- Web search results on volume size detection methods (multiple approaches exist)
- Community practices for CLI confirmation prompts (varying conventions)

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH - Docker volume APIs well-documented, Clojure print-table built-in, existing patterns proven
- Architecture: HIGH - Patterns follow existing codebase conventions (ps command, prompt-yn function)
- Pitfalls: HIGH - Based on Docker volume behavior documentation and common CLI UX mistakes
- Version diff detection: MEDIUM - Technically feasible but complexity/value tradeoff unclear

**Research date:** 2026-02-01
**Valid until:** 30 days (Docker APIs stable, Clojure built-ins stable, CLI patterns established)
