# Phase 44: Flip Gitleaks Default - Research

**Researched:** 2026-02-05
**Domain:** Docker ARG defaults, Babashka CLI flag patterns, Clojure boolean coercion
**Confidence:** HIGH

## Summary

This research covers the technical implementation required to flip Gitleaks from opt-out (default installed with `--without-gitleaks` to skip) to opt-in (default not installed with `--with-gitleaks` to include). The project currently uses Docker ARG with default value `WITH_GITLEAKS=true` and CLI flag `--without-gitleaks` that inverts to `:with-gitleaks false` in state.

**Current behavior (v2.5.0-v2.9.0):**
- Default: Gitleaks installed (ARG WITH_GITLEAKS=true)
- Opt-out: `aishell setup --without-gitleaks` → WITH_GITLEAKS=false → no Gitleaks in image
- State tracks: `:with-gitleaks true/false` (positive tracking)

**Target behavior (v2.10.0):**
- Default: Gitleaks NOT installed (ARG WITH_GITLEAKS=false)
- Opt-in: `aishell setup --with-gitleaks` → WITH_GITLEAKS=true → Gitleaks in image
- State tracks: `:with-gitleaks true/false` (positive tracking unchanged)

The flip is straightforward: change the ARG default value in Dockerfile, swap the CLI flag from `--without-gitleaks` (negative) to `--with-gitleaks` (positive), and invert the default value calculation. All downstream code (help visibility, warning suppression, filename detection) already checks `:with-gitleaks` state field and will work without modification.

**Primary recommendation:** Change ARG default to false, replace `--without-gitleaks` with `--with-gitleaks`, default `:with-gitleaks` to false when flag absent. No changes needed to run pipeline, help system, or detection frameworks.

## Standard Stack

All technologies already in use. No new dependencies required.

### Core (Existing)
| Component | Version | Purpose | Current Usage |
|-----------|---------|---------|---------------|
| Docker ARG | Standard | Build-time conditional compilation | Used for WITH_GITLEAKS, harness versions |
| Babashka CLI | Built-in | Flag parsing with boolean coercion | Used throughout cli.clj |
| Clojure EDN | Built-in | State persistence | Used in state.clj for ~/.aishell/state.edn |

### Supporting (Existing)
| Tool | Purpose | Current Usage |
|------|---------|---------------|
| babashka.cli/parse-opts | Parse CLI options with coercion | cli.clj dispatch |
| Docker --build-arg | Pass build arguments at build time | build.clj |
| State read/write | Persist build configuration | state.clj, cli.clj |

**No installation needed:** Everything is already in place from v2.5.0 implementation of `--without-gitleaks`.

## Architecture Patterns

### Pattern 1: Docker ARG Default Value Inversion

**What:** Change ARG default in Dockerfile from `true` to `false`, making Gitleaks opt-in

**Current implementation (templates.clj):**
```dockerfile
# Install Gitleaks for secret scanning (conditional)
ARG WITH_GITLEAKS=true  # <-- DEFAULT IS TRUE (opt-out model)
ARG GITLEAKS_VERSION=8.30.0
RUN if [ "$WITH_GITLEAKS" = "true" ]; then \
        # ... installation logic
    fi
```

**Target implementation:**
```dockerfile
# Install Gitleaks for secret scanning (conditional, opt-in)
ARG WITH_GITLEAKS=false  # <-- DEFAULT IS FALSE (opt-in model)
ARG GITLEAKS_VERSION=8.30.0
RUN if [ "$WITH_GITLEAKS" = "true" ]; then \
        # ... installation logic (unchanged)
    fi
```

**Source:** [Docker ARG documentation](https://docs.docker.com/build/building/variables/)

**Key insight:** The RUN condition doesn't change. We're only changing what happens when no `--build-arg WITH_GITLEAKS=...` is passed.

### Pattern 2: Positive Flag Replacement

**What:** Replace negative flag (`--without-gitleaks`) with positive flag (`--with-gitleaks`)

**Current implementation (cli.clj setup-spec):**
```clojure
(def setup-spec
  {:with-claude   {:desc "Include Claude Code (optional: =VERSION)"}
   :with-opencode {:desc "Include OpenCode (optional: =VERSION)"}
   :with-codex    {:desc "Include Codex CLI (optional: =VERSION)"}
   :with-gemini   {:desc "Include Gemini CLI (optional: =VERSION)"}
   :without-gitleaks {:coerce :boolean :desc "Skip Gitleaks installation"}  ; <-- NEGATIVE
   :with-tmux     {:coerce :boolean :desc "Enable tmux multiplexer"}
   ;; ...
   })
```

**Target implementation:**
```clojure
(def setup-spec
  {:with-claude   {:desc "Include Claude Code (optional: =VERSION)"}
   :with-opencode {:desc "Include OpenCode (optional: =VERSION)"}
   :with-codex    {:desc "Include Codex CLI (optional: =VERSION)"}
   :with-gemini   {:desc "Include Gemini CLI (optional: =VERSION)"}
   :with-gitleaks {:coerce :boolean :desc "Include Gitleaks secret scanner"}  ; <-- POSITIVE
   :with-tmux     {:coerce :boolean :desc "Enable tmux multiplexer"}
   ;; ...
   })
```

**Flag behavior (babashka.cli):**
- No flag present → `(:with-gitleaks opts)` returns `nil`
- Flag present without value → `(:with-gitleaks opts)` returns `true`
- Flag with explicit value → `(:with-gitleaks opts)` returns coerced boolean

**Source:** [Babashka CLI boolean flags](https://github.com/babashka/cli/blob/main/README.md#boolean-flags-and-auto-coercion)

### Pattern 3: Default Value Flip in CLI Handler

**What:** Change default value when flag is absent from `true` to `false`

**Current implementation (cli.clj handle-setup):**
```clojure
(defn handle-setup [{:keys [opts]}]
  ;; ...
  (let [;; ... harness configs ...
        with-gitleaks (not (:without-gitleaks opts))  ; <-- INVERT NEGATIVE FLAG, DEFAULT TRUE
        ;; ...
        ])
  ;; Pass to build and state
  )
```

**Target implementation:**
```clojure
(defn handle-setup [{:keys [opts]}]
  ;; ...
  (let [;; ... harness configs ...
        with-gitleaks (boolean (:with-gitleaks opts))  ; <-- READ POSITIVE FLAG, DEFAULT FALSE
        ;; ...
        ])
  ;; Pass to build and state (unchanged)
  )
```

**Key insight:**
- `(not (:without-gitleaks opts))` returns `true` when flag absent (nil → true)
- `(boolean (:with-gitleaks opts))` returns `false` when flag absent (nil → false)

### Pattern 4: Unchanged State Tracking

**What:** State field `:with-gitleaks` remains boolean with positive semantics

**Current schema (state.clj, unchanged):**
```clojure
{:with-claude true            ; boolean
 :with-opencode false         ; boolean
 :with-gitleaks true          ; boolean (whether Gitleaks installed)  <-- POSITIVE FIELD
 :with-tmux false             ; boolean
 ;; ...
 }
```

**Behavior:**
- `:with-gitleaks true` → Gitleaks installed → show in help, skip freshness warning
- `:with-gitleaks false` → Gitleaks NOT installed → hide from help, skip freshness warning

**Downstream consumers (no changes needed):**
1. **Help visibility** (`cli.clj` line 118): `(when (contains? installed "gitleaks") ...)`
   - Checks state, not default assumption
2. **Freshness warning** (`run.clj` line 190-191): `(when-not (= cmd "gitleaks") ...)`
   - Warning only displays if Gitleaks command runs; command only dispatches if installed
3. **Filename detection** (`run.clj` lines 176-187): Independent of Gitleaks installation
   - Runs unconditionally, as specified in PIPE-02

## Don't Hand-Roll

No custom solutions needed. All mechanisms already exist.

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| CLI flag parsing | Custom string parsing | babashka.cli with :coerce :boolean | Handles presence/absence, true/false, yes/no automatically |
| Boolean default logic | Complex if/else chains | Simple `(boolean (:flag opts))` | nil coerces to false, true stays true |
| State persistence | Custom serialization | EDN read/write in state.clj | Already handles all data types, atomic writes |
| Docker conditional builds | Multiple Dockerfiles | ARG + shell condition | Standard pattern, cache-friendly |

**Key insight:** The v2.5.0 implementation of `--without-gitleaks` already built all necessary infrastructure. This phase is a value flip, not a new feature.

## Common Pitfalls

### Pitfall 1: Forgetting to Update Help Examples

**What goes wrong:** Help text shows outdated flag (`--without-gitleaks`)

**Why it happens:** Multiple help functions (print-help, print-setup-help) have hardcoded examples

**How to avoid:**
- Search for all occurrences of "without-gitleaks" in cli.clj
- Replace with "with-gitleaks" in both spec and help examples
- Check both command help (`aishell --help`) and setup help (`aishell setup --help`)

**Warning signs:** User runs `aishell setup --help` and sees `--without-gitleaks`

**Locations to check:**
- `print-setup-help` function: Example lines with `--without-gitleaks`
- `setup-spec` `:order` vector: Ensure `--with-gitleaks` in right position
- Any inline documentation comments

### Pitfall 2: State File Migration Assumption

**What goes wrong:** Assuming old state files need migration code

**Why it happens:** Thinking `:with-gitleaks false` in old state is wrong

**How to avoid:** Recognize that no migration is needed:
- Old state with `:with-gitleaks true` → still means "Gitleaks installed" → correct
- Old state with `:with-gitleaks false` → still means "not installed" → correct
- The state field semantics never changed, only the default

**Warning signs:** Adding migration code that "fixes" old state files

**Correct approach:** Leave state.clj untouched except for docstring updates

### Pitfall 3: Breaking Filename Detection

**What goes wrong:** Assuming filename detection depends on Gitleaks installation

**Why it happens:** Conflating two independent systems (filename patterns vs. Gitleaks binary)

**How to avoid:** Verify that filename detection (detection/core.clj) runs unconditionally
- Check run.clj lines 176-187: Detection runs before Gitleaks freshness warning
- Check PIPE-02 requirement: "Filename-based detection continues to work (independent of Gitleaks)"

**Warning signs:** Tests show filename detection stopping when `--with-gitleaks` absent

**Correct behavior:** Filename detection always runs, regardless of `:with-gitleaks` value

### Pitfall 4: Help Visibility Edge Cases

**What goes wrong:** Gitleaks command shows in help when not installed, or vice versa

**Why it happens:** `installed-harnesses` function has fallback behavior for missing state

**How to avoid:**
- When no state file exists: `installed-harnesses` returns all commands for discoverability
- When state exists: Returns only installed harnesses
- This is correct and intentional (see Phase 28 decision: "Show all harnesses when no state file")

**Warning signs:** User builds with `--with-gitleaks`, but help doesn't show `gitleaks` command

**Verification:**
```clojure
;; In cli.clj lines 80-93
(defn installed-harnesses []
  (if-let [state (state/read-state)]
    (cond-> #{}
      (:with-gitleaks state true) (conj "gitleaks"))  ; <-- Note: Uses default `true`
    #{"claude" "opencode" "codex" "gemini" "gitleaks"}))
```

**Fix:** The `true` default in line 91 should remain — it's for the `get` function default when key missing from old states, not for nil state.

## Code Examples

Verified patterns for the flip:

### Example 1: Dockerfile ARG Default Change

**Location:** `src/aishell/docker/templates.clj`

```clojure
;; BEFORE (v2.9.0):
;; Install Gitleaks for secret scanning (conditional)
ARG WITH_GITLEAKS=true
ARG GITLEAKS_VERSION=8.30.0

;; AFTER (v2.10.0):
;; Install Gitleaks for secret scanning (conditional, opt-in)
ARG WITH_GITLEAKS=false
ARG GITLEAKS_VERSION=8.30.0
```

**Source:** [Docker ARG documentation](https://docs.docker.com/build/building/variables/)

### Example 2: CLI Flag Replacement

**Location:** `src/aishell/cli.clj`

```clojure
;; BEFORE (v2.9.0):
(def setup-spec
  {:without-gitleaks {:coerce :boolean :desc "Skip Gitleaks installation"}})

;; AFTER (v2.10.0):
(def setup-spec
  {:with-gitleaks {:coerce :boolean :desc "Include Gitleaks secret scanner"}})
```

**Source:** [Babashka CLI spec format](https://github.com/babashka/cli/blob/main/README.md)

### Example 3: Default Value Logic Flip

**Location:** `src/aishell/cli.clj` handle-setup function

```clojure
;; BEFORE (v2.9.0):
(let [with-gitleaks (not (:without-gitleaks opts))  ; nil → true (default installed)
      ;; ...
      ])

;; AFTER (v2.10.0):
(let [with-gitleaks (boolean (:with-gitleaks opts))  ; nil → false (default not installed)
      ;; ...
      ])
```

**Source:** Existing codebase pattern (cli.clj lines 164-165)

### Example 4: Help Example Update

**Location:** `src/aishell/cli.clj` print-setup-help function

```clojure
;; BEFORE (v2.9.0):
(println (str "  " output/CYAN "aishell setup --without-gitleaks" output/NC "   Skip Gitleaks"))

;; AFTER (v2.10.0):
(println (str "  " output/CYAN "aishell setup --with-gitleaks" output/NC "      Include Gitleaks scanner"))
```

**Source:** Existing help pattern (cli.clj lines 145-152)

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| Gitleaks always installed | Gitleaks opt-out (`--without-gitleaks`) | v2.5.0 (2026-01-26) | Users who don't need scanning save ~15MB |
| Opt-out model | Opt-in model (`--with-gitleaks`) | v2.10.0 (this phase) | Consistent `--with-*` pattern, simpler mental model |

**Rationale for flip (from artifacts/think/via-negativa.md):**
> "Currently the mental model is inconsistent: harnesses are opt-in, but Gitleaks is opt-out. Impact: consistent `--with-*` pattern, simpler setup command."

**Established patterns:**
- All harnesses: `--with-claude`, `--with-opencode`, `--with-codex`, `--with-gemini` (opt-in)
- tmux: `--with-tmux` (opt-in)
- Gitleaks: Currently `--without-gitleaks` (opt-out) → changing to `--with-gitleaks` (opt-in)

## Open Questions

None. The implementation is straightforward:

1. **Question:** Do we need to handle old state files that have `:with-gitleaks true`?
   - **Answer:** No. Old state files remain valid. `:with-gitleaks true` still means "installed," which is correct.

2. **Question:** Will filename detection break when Gitleaks not installed?
   - **Answer:** No. Filename detection (PIPE-02) is independent and runs unconditionally.

3. **Question:** How does help visibility work when no state file exists?
   - **Answer:** Shows all commands for discoverability (existing behavior from v2.5.0, validated in Phase 28).

4. **Question:** Do we need new validation logic for the flag?
   - **Answer:** No. Boolean coercion is built into babashka.cli, works identically to other `--with-*` flags.

## Implementation Checklist

Concrete steps for the planner:

- [ ] Change `ARG WITH_GITLEAKS=true` to `ARG WITH_GITLEAKS=false` in templates.clj
- [ ] Replace `:without-gitleaks` with `:with-gitleaks` in setup-spec (cli.clj)
- [ ] Change `(not (:without-gitleaks opts))` to `(boolean (:with-gitleaks opts))` in handle-setup
- [ ] Update all help examples from `--without-gitleaks` to `--with-gitleaks`
- [ ] Update `:order` vector in print-setup-help to position flag correctly
- [ ] Update state.clj docstring to clarify opt-in semantics
- [ ] Verify no other files reference "without-gitleaks" string

## Sources

### Primary (HIGH confidence)
- [Docker ARG variables documentation](https://docs.docker.com/build/building/variables/) - ARG default value behavior
- [Babashka CLI README](https://github.com/babashka/cli/blob/main/README.md) - Boolean flag parsing and coercion
- Existing codebase (v2.5.0-v2.9.0) - Current `--without-gitleaks` implementation in cli.clj, templates.clj, build.clj, state.clj

### Secondary (MEDIUM confidence)
- artifacts/think/via-negativa.md - Rationale for flip ("consistent `--with-*` pattern")
- .planning/phases/28-dynamic-help-config-improvements/ - Phase 28 implementation of opt-out model

### Tertiary (LOW confidence)
- None needed

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH - All technologies already in use, no new dependencies
- Architecture: HIGH - Patterns verified in existing v2.5.0 implementation
- Pitfalls: HIGH - Based on real implementation code and migration patterns

**Research date:** 2026-02-05
**Valid until:** 2026-03-05 (30 days for stable Docker/Babashka features)

**Verification notes:**
- Examined current implementation in 6 source files (cli.clj, state.clj, templates.clj, build.clj, run.clj, gitleaks/warnings.clj)
- Confirmed no migration logic needed (state semantics unchanged)
- Verified filename detection independence (PIPE-02 requirement)
- Checked help visibility logic (installed-harnesses function, lines 80-93)
