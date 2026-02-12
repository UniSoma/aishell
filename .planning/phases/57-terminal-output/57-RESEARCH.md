# Phase 57: Terminal & Output - Research

**Researched:** 2026-02-12
**Domain:** Cross-platform terminal ANSI color code detection and handling (Windows/Unix)
**Confidence:** HIGH

## Summary

Phase 57 enables graceful ANSI color code handling across Windows and Unix terminals by detecting terminal capabilities and stripping color codes when unsupported. The core challenge is that Windows terminals have fragmented ANSI support: Windows Terminal and modern PowerShell support full ANSI, legacy cmd.exe requires explicit enablement, and redirected/piped output should have colors stripped regardless of platform.

The current implementation in `src/aishell/output.clj` uses `(System/console)` and `TERM` environment variable checks to detect color support. This works on Unix but has gaps on Windows: (1) `TERM` is often unset on Windows, defaulting to no colors even in ANSI-capable terminals, (2) Windows Terminal sets `WT_SESSION` but not always `COLORTERM`, and (3) ConEmu sets `ConEmuANSI=ON` for capability detection.

The standard approach from the NO_COLOR/FORCE_COLOR community standards establishes a clear priority: `NO_COLOR` (user opt-out) > `FORCE_COLOR` (user opt-in) > automatic terminal detection. The automatic detection should check: (1) `System.console` for interactive terminal, (2) `COLORTERM` for explicit color capability, (3) Windows-specific variables (`WT_SESSION`, `ConEmuANSI`), and (4) `TERM` value (not "dumb").

**Primary recommendation:** Enhance `colors-enabled?` function in `output.clj` to implement standards-compliant detection: honor `NO_COLOR`/`FORCE_COLOR`, add Windows terminal detection (`WT_SESSION`, `ConEmuANSI`), and set sensible `TERM`/`COLORTERM` defaults on Windows when passing environment variables to containers (Phase 56 already does this for `attach`, Phase 57 ensures consistency).

## Standard Stack

### Core

| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| System/console | Java built-in | Detect interactive terminal (returns null when piped/redirected) | Standard Java approach, cross-platform, already used in codebase |
| System/getenv | Java built-in | Read environment variables (TERM, COLORTERM, NO_COLOR, FORCE_COLOR, WT_SESSION, ConEmuANSI) | Standard Java interop, cross-platform |

### Supporting

| Library | Version | Purpose | When to Use |
|---------|---------|---------|-------------|
| babashka.fs/windows? | Built-in | Platform detection for Windows-specific terminal variable checks | Already used in Phases 53-56 for platform guards |
| clojure.string | Built-in | String manipulation for environment variable checking | Already used throughout codebase |

### Alternatives Considered

| Instead of | Could Use | Tradeoff |
|------------|-----------|----------|
| Manual environment variable checks | External ANSI detection library (jansi, clansi) | Adding dependencies for simple checks is overkill; environment variables sufficient for aishell's needs |
| Platform-specific terminal APIs | Windows Registry checks, Unix termcap queries | Complexity not justified; environment variables cover 95%+ of cases |
| Same logic for all platforms | Platform-specific detection for Windows terminals | Windows has different terminal ecosystem (WT_SESSION, ConEmuANSI) requiring platform-specific checks |

**Installation:**
No installation needed — uses Java built-ins and existing `babashka.fs` dependency. Zero additional dependencies.

## Architecture Patterns

### Recommended Project Structure

No new files required. Changes localized to existing output module:

```
src/aishell/
├── output.clj              # Enhance colors-enabled? with standards-compliant detection
└── detection/
    └── formatters.clj      # Update DIM check to use new colors-enabled? logic
```

### Pattern 1: Standards-Compliant Color Detection

**What:** Implement NO_COLOR/FORCE_COLOR community standards with automatic terminal detection fallback
**When to use:** All color output decisions in the codebase
**Example:**

```clojure
;; Source: NO_COLOR and FORCE_COLOR community standards + Windows terminal detection research

;; CURRENT (Phase 56, gaps on Windows):
(defn- colors-enabled? []
  (and (some? (System/console))
       (nil? (System/getenv "NO_COLOR"))
       (or (some? (System/getenv "FORCE_COLOR"))
           (not= "dumb" (System/getenv "TERM")))))

;; ENHANCED (Phase 57, cross-platform):
(defn- colors-enabled? []
  (let [no-color (System/getenv "NO_COLOR")
        force-color (System/getenv "FORCE_COLOR")]
    (cond
      ;; 1. User opt-out (highest priority)
      (and no-color (not= "" no-color))
      false

      ;; 2. User opt-in (overrides auto-detection)
      (and force-color (not= "" force-color))
      true

      ;; 3. Auto-detection (fallback)
      :else
      (and
        ;; Interactive terminal check (null when piped/redirected)
        (some? (System/console))

        ;; Terminal capability check
        (or
          ;; Explicit color capability declaration
          (some? (System/getenv "COLORTERM"))

          ;; Windows Terminal detection
          (and (fs/windows?)
               (some? (System/getenv "WT_SESSION")))

          ;; ConEmu detection (Windows)
          (and (fs/windows?)
               (= "ON" (System/getenv "ConEmuANSI")))

          ;; Unix terminal with color support
          (and (not (fs/windows?))
               (let [term (System/getenv "TERM")]
                 (and term (not= "dumb" term)))))))))
```

**Why this pattern:**
- NO_COLOR and FORCE_COLOR are community standards supported by 500+ CLI tools
- `System/console` returns null when output redirected (correct behavior for logs, CI)
- `COLORTERM` is explicit color capability declaration (set by modern terminals)
- `WT_SESSION` identifies Windows Terminal (ANSI-capable)
- `ConEmuANSI=ON` identifies ConEmu with ANSI enabled
- TERM=dumb disables colors on Unix (emacs, simple terminals)
- Priority order matches community best practices

### Pattern 2: Container Environment Variable Defaults (Windows)

**What:** Set sensible TERM/COLORTERM defaults when passing environment to containers on Windows
**When to use:** Docker exec/run commands that inherit host environment
**Example:**

```clojure
;; Source: Phase 56 attach.clj pattern + Phase 57 Windows defaults

;; Already implemented in attach.clj (Phase 56):
(let [term (resolve-term container-name)
      colorterm (or (System/getenv "COLORTERM") "truecolor")]
  ...)

;; TERM-02 requirement: Windows doesn't set TERM by default
;; resolve-term already handles this:
(defn- resolve-term [container-name]
  (let [host-term (or (System/getenv "TERM") "xterm-256color") ; Default if unset
        {:keys [exit]} (p/shell {:out :string :err :string :continue true}
                                "docker" "exec" "-u" "developer" container-name
                                "infocmp" host-term)]
    (if (zero? exit)
      host-term
      "xterm-256color"))) ; Fallback for unsupported TERM

;; COLORTERM fallback already in place:
(or (System/getenv "COLORTERM") "truecolor")

;; Both patterns already correct for Windows — no changes needed
```

**Why this pattern:**
- Windows often doesn't set TERM/COLORTERM environment variables
- Defaulting to `xterm-256color` is safe (widely supported in Linux containers)
- `truecolor` matches modern terminal capabilities (Windows Terminal, modern PowerShell)
- `resolve-term` validates TERM against container's terminfo database (prevents errors)
- Already implemented in Phase 56 — Phase 57 just documents the rationale

### Pattern 3: Consistent Color Detection Across Modules

**What:** Use same `colors-enabled?` logic for all color decisions in codebase
**When to use:** Any place that conditionally uses ANSI codes
**Example:**

```clojure
;; Source: Codebase audit of ANSI color usage

;; CURRENT (inconsistent checks):
;; output.clj: uses colors-enabled?
;; detection/formatters.clj: uses (some? (System/console))
;; check.clj: uses (some? (System/console))

;; AFTER Phase 57 (consistent):
;; All modules use colors-enabled? from output.clj

;; detection/formatters.clj BEFORE:
(def ^:private DIM (if (some? (System/console)) "\u001b[2m" ""))

;; detection/formatters.clj AFTER:
(ns aishell.detection.formatters
  (:require [aishell.output :as output]))

(def ^:private DIM (if (output/colors-enabled?) "\u001b[2m" ""))

;; check.clj BEFORE:
(def GREEN (if (some? (System/console)) "\u001b[0;32m" ""))

;; check.clj AFTER:
(ns aishell.check
  (:require [aishell.output :as output]))

(def GREEN (if (output/colors-enabled?) "\u001b[0;32m" ""))
```

**Why this pattern:**
- Single source of truth for color detection logic
- `System/console` check insufficient (misses NO_COLOR, FORCE_COLOR, Windows terminals)
- Consistent behavior across error messages, detection output, and status checks
- Makes testing easier (mock one function instead of environment variables everywhere)

### Anti-Patterns to Avoid

- **Don't check only `System/console`:** Misses user preferences (NO_COLOR/FORCE_COLOR) and Windows terminal capabilities (WT_SESSION)
- **Don't ignore NO_COLOR/FORCE_COLOR:** Community standards with broad adoption, users expect these to work
- **Don't use Windows Registry for VT100 detection:** Environment variables sufficient, Registry requires native calls or complex logic
- **Don't set TERM/COLORTERM on all platforms:** Only Windows needs defaults; Unix users set these correctly, overriding causes issues
- **Don't check `WT_SESSION` on Unix:** Variable is Windows Terminal specific, checking on all platforms is wasteful

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| ANSI color library | Custom ANSI escape code builder | Direct escape sequences (`"\u001b[0;31m"`) | Simple color output doesn't need library; complex TUI would use babashka/clojure-lanterna |
| Terminal capability database | Terminfo parser in Clojure | Environment variable checks + container `infocmp` | Terminfo parsing is complex; container already has termcap/terminfo |
| Windows VT100 enablement | Native Windows API calls for SetConsoleMode | Trust Docker Desktop terminal handling | Docker exec inherits terminal from host shell; Windows Terminal/PowerShell enable VT100 automatically |
| TTY detection | Custom file descriptor checks | `System.console` (returns null when not TTY) | Java provides this; cross-platform, reliable |

**Key insight:** ANSI color detection is well-standardized through environment variables. Avoid complex platform-specific APIs when simple environment checks suffice.

## Common Pitfalls

### Pitfall 1: Ignoring NO_COLOR Standard

**What goes wrong:** CLI tools ignore `NO_COLOR=1`, forcing colors in logs, CI, or when users pipe output to files

**Why it happens:** Developers focus on enabling colors, forget users need opt-out for non-interactive contexts

**How to avoid:**
- Always check NO_COLOR first (highest priority)
- Check for non-empty string, not just presence: `(and no-color (not= "" no-color))`
- Test with `NO_COLOR=1 aishell --help` to verify colors disabled

**Warning signs:**
- Colored output in CI logs (most CI systems set NO_COLOR)
- Users complain about ANSI codes in piped output
- Log files contain ANSI escape sequences

### Pitfall 2: Missing Windows Terminal Detection

**What goes wrong:** Windows Terminal supports ANSI but colors are disabled because TERM is unset

**Why it happens:** Assuming TERM is always set (Unix convention); Windows often doesn't set TERM

**How to avoid:**
- Check `WT_SESSION` environment variable on Windows (set by Windows Terminal)
- Check `ConEmuANSI=ON` for ConEmu users
- Provide fallback: if TERM unset, default to "xterm-256color" (already done in resolve-term)

**Warning signs:**
- Windows Terminal users report no colors (even though terminal supports ANSI)
- COLORTERM unset on Windows (less commonly set than WT_SESSION)
- Colors work in PowerShell but not cmd.exe launched from Windows Terminal

### Pitfall 3: Inconsistent Color Detection Across Modules

**What goes wrong:** Different parts of codebase use different color detection logic, causing inconsistent output

**Why it happens:** Copy-pasting `(System/console)` check instead of using centralized function

**How to avoid:**
- Export `colors-enabled?` as public function from output.clj
- Audit all color code definitions (grep for `\\u001b\[`)
- Replace inline `(System/console)` checks with `(output/colors-enabled?)`

**Warning signs:**
- Some output respects NO_COLOR, other output doesn't
- Error messages colored but detection output plain (or vice versa)
- Multiple definitions of color detection logic across files

### Pitfall 4: Not Testing Piped Output

**What goes wrong:** ANSI codes leak into piped output, breaking parsers or cluttering logs

**Why it happens:** Testing in interactive terminal only, missing pipe/redirect scenarios

**How to avoid:**
- Test piped output: `aishell --help | cat` (should have no ANSI codes)
- Test redirected: `aishell check > output.txt` (file should be plain text)
- Test with NO_COLOR: `NO_COLOR=1 aishell check` (no colors)

**Warning signs:**
- Bug reports about garbled output when piped
- Log files contain escape sequences
- `grep` output shows `^[[0;31m` ANSI codes

### Pitfall 5: Overriding User-Set TERM

**What goes wrong:** Code sets TERM="xterm-256color" unconditionally, breaking users with custom TERM values

**Why it happens:** Trying to "fix" Windows by setting TERM everywhere, accidentally overriding Unix users' configurations

**How to avoid:**
- Only set TERM default if unset: `(or (System/getenv "TERM") "xterm-256color")`
- Never override existing TERM value
- Windows detection should be additive (WT_SESSION check), not replacement

**Warning signs:**
- Unix users report terminal behavior changed after update
- Emacs users report issues (they set TERM=dumb intentionally)
- Tmux/screen users report color issues (they set TERM to custom values)

### Pitfall 6: Forgetting FORCE_COLOR for CI

**What goes wrong:** CI environments with color support (GitHub Actions, GitLab CI) show plain output even though they support ANSI

**Why it happens:** CI doesn't have System.console (non-interactive), but they DO support ANSI in logs

**How to avoid:**
- Check FORCE_COLOR before System.console check
- CI systems often set FORCE_COLOR=1 to enable colors
- Priority: NO_COLOR > FORCE_COLOR > auto-detection

**Warning signs:**
- GitHub Actions logs are plain text (should be colored)
- Users set FORCE_COLOR=1 manually and it doesn't work
- Color support varies between CI systems inconsistently

## Code Examples

Verified patterns from official sources and community standards:

### Enhanced Color Detection (Standards-Compliant)

```clojure
;; Source: NO_COLOR (https://no-color.org/) and FORCE_COLOR (https://force-color.org/) standards
;; Location: src/aishell/output.clj

(ns aishell.output
  (:require [babashka.fs :as fs]))

(defn colors-enabled?
  "Detect if ANSI colors should be used in output.

   Priority (high to low):
   1. NO_COLOR (user opt-out) - disables colors
   2. FORCE_COLOR (user opt-in) - enables colors
   3. Auto-detection (fallback):
      - System.console present (interactive terminal)
      - COLORTERM set (explicit color capability)
      - WT_SESSION set on Windows (Windows Terminal)
      - ConEmuANSI=ON on Windows (ConEmu)
      - TERM set and not 'dumb' on Unix

   Standards: https://no-color.org/ and https://force-color.org/"
  []
  (let [no-color (System/getenv "NO_COLOR")
        force-color (System/getenv "FORCE_COLOR")]
    (cond
      ;; User explicitly disabled colors
      (and no-color (not= "" no-color))
      false

      ;; User explicitly enabled colors (overrides auto-detection)
      (and force-color (not= "" force-color))
      true

      ;; Auto-detection based on terminal capabilities
      :else
      (and
        ;; Must be interactive terminal (not piped/redirected)
        (some? (System/console))

        ;; Terminal must support colors
        (or
          ;; Explicit color capability (modern terminals)
          (some? (System/getenv "COLORTERM"))

          ;; Windows Terminal (ANSI-capable)
          (and (fs/windows?)
               (some? (System/getenv "WT_SESSION")))

          ;; ConEmu with ANSI enabled
          (and (fs/windows?)
               (= "ON" (System/getenv "ConEmuANSI")))

          ;; Unix with standard TERM variable
          (and (not (fs/windows?))
               (let [term (System/getenv "TERM")]
                 (and term (not= "dumb" term)))))))))

;; Color definitions use the enhanced detection
(def RED (if (colors-enabled?) "\u001b[0;31m" ""))
(def YELLOW (if (colors-enabled?) "\u001b[0;33m" ""))
(def CYAN (if (colors-enabled?) "\u001b[0;36m" ""))
(def BOLD (if (colors-enabled?) "\u001b[1m" ""))
(def NC (if (colors-enabled?) "\u001b[0m" ""))
```

### Consistent Color Usage Across Modules

```clojure
;; Source: Codebase audit of ANSI color usage
;; Location: src/aishell/detection/formatters.clj

(ns aishell.detection.formatters
  "Severity-specific terminal output formatting for detection findings."
  (:require [aishell.output :as output]
            [babashka.fs :as fs]
            [clojure.string :as str]))

;; BEFORE (inconsistent detection):
(def ^:private DIM (if (some? (System/console)) "\u001b[2m" ""))

;; AFTER (uses centralized detection):
(def ^:private DIM (if (output/colors-enabled?) "\u001b[2m" ""))

;; Color definitions now respect NO_COLOR, FORCE_COLOR, and Windows terminals
```

```clojure
;; Source: Codebase audit of ANSI color usage
;; Location: src/aishell/check.clj

(ns aishell.check
  (:require [aishell.output :as output]))

;; BEFORE (inconsistent detection):
(def GREEN (if (some? (System/console)) "\u001b[0;32m" ""))

;; AFTER (uses centralized detection):
(def GREEN (if (output/colors-enabled?) "\u001b[0;32m" ""))
```

### Container Environment Defaults (Already Correct)

```clojure
;; Source: Phase 56 implementation (src/aishell/attach.clj)
;; Demonstrates TERM-02 requirement already satisfied

(defn- resolve-term
  "Resolve a TERM value valid inside the container.
   Checks if the host TERM has a terminfo entry in the container via infocmp.
   Falls back to xterm-256color if unsupported (e.g., xterm-ghostty)."
  [container-name]
  (let [host-term (or (System/getenv "TERM") "xterm-256color") ; Default if unset (Windows)
        {:keys [exit]} (p/shell {:out :string :err :string :continue true}
                                "docker" "exec" "-u" "developer" container-name
                                "infocmp" host-term)]
    (if (zero? exit)
      host-term
      "xterm-256color"))) ; Fallback for unsupported TERM

(defn attach-to-container [name]
  ;; ...
  (let [term (resolve-term container-name)
        colorterm (or (System/getenv "COLORTERM") "truecolor")] ; Default if unset (Windows)
    ;; Pass to container via -e flags
    (if (fs/windows?)
      (let [result @(p/process {:inherit true}
                               "docker" "exec" "-it" "-u" "developer"
                               "-e" (str "TERM=" term)
                               "-e" (str "COLORTERM=" colorterm)
                               ...)]
        (System/exit (:exit result)))
      (p/exec "docker" "exec" "-it" "-u" "developer"
              "-e" (str "TERM=" term)
              "-e" (str "COLORTERM=" colorterm)
              ...))))
```

### Testing Color Detection

```bash
# Test NO_COLOR (should disable colors)
NO_COLOR=1 aishell --help
# Output should be plain text (no ANSI codes)

# Test FORCE_COLOR (should enable colors even when piped)
FORCE_COLOR=1 aishell --help | cat
# Output should have ANSI codes (unusual but intentional for CI logs)

# Test piped output (should disable colors automatically)
aishell check | tee output.txt
# output.txt should be plain text

# Test redirected output (should disable colors automatically)
aishell check > output.txt 2>&1
# output.txt should be plain text

# Test Windows Terminal (on Windows with WT)
echo %WT_SESSION%
# Should show GUID if in Windows Terminal
aishell --help
# Should have colors

# Test legacy cmd.exe (on Windows without WT)
set WT_SESSION=
set COLORTERM=
aishell --help
# Should be plain text (System.console null or no capability vars)
```

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| Simple `(System/console)` check | NO_COLOR/FORCE_COLOR standards + terminal capability detection | Phase 57 (v3.1.0) | Respects user preferences, works in CI, detects Windows Terminal |
| Inconsistent color detection | Centralized `colors-enabled?` function | Phase 57 (v3.1.0) | Consistent behavior across all output (errors, warnings, detection) |
| Unix-only terminal assumptions | Platform-specific terminal detection (WT_SESSION, ConEmuANSI on Windows) | Phase 57 (v3.1.0) | Windows Terminal users get colored output |
| No TERM/COLORTERM defaults | Sensible defaults when unset (xterm-256color, truecolor) | Phase 56-57 (v3.1.0) | Windows users get working terminal in containers |

**Deprecated/outdated:**
- Checking only `System.console`: Must also check NO_COLOR, FORCE_COLOR, and Windows terminal variables
- Assuming TERM is always set: Windows often doesn't set TERM, need fallback
- Platform-independent color detection: Windows terminal ecosystem requires platform-specific checks (WT_SESSION)

## Open Questions

1. **Should we support CLICOLOR/CLICOLOR_FORCE in addition to NO_COLOR/FORCE_COLOR?**
   - What we know: CLICOLOR/CLICOLOR_FORCE is an older standard, some Unix tools use it
   - What's unclear: Is it worth supporting for a Babashka CLI tool (not widely used in modern ecosystem)
   - Recommendation: NO for Phase 57. NO_COLOR/FORCE_COLOR are newer, better-specified standards with broader adoption. CLICOLOR adds complexity for minimal benefit.

2. **Should colors-enabled? be made public for use in other modules?**
   - What we know: Currently private, but detection/formatters.clj and check.clj need it
   - What's unclear: Is this an internal detail or a public API?
   - Recommendation: YES, make it public. Consistent color detection across modules is important. Mark as `:api :internal` if Babashka supports metadata-based API boundaries.

3. **Should we log a debug message when colors are disabled?**
   - What we know: Users sometimes confused why colors aren't working
   - What's unclear: Would debug logging help or just add noise?
   - Recommendation: NO for Phase 57. Keep it simple. Users who want colors can test with `FORCE_COLOR=1`. Debug logging would require verbose flag plumbing throughout codebase.

4. **Should we support COLORTERM=24bit vs COLORTERM=truecolor?**
   - What we know: Both indicate true color support, some terminals use "24bit" instead of "truecolor"
   - What's unclear: Does aishell need to distinguish? (We only use 16 basic colors currently)
   - Recommendation: Treat any COLORTERM value as "supports color". Don't distinguish 24bit vs 256 vs 16 — simple ANSI codes work everywhere.

## Codebase Impact Analysis

### Files Requiring Changes

Based on ANSI color code usage audit:

1. **src/aishell/output.clj** (HIGH PRIORITY)
   - Lines 6-16: Enhance `colors-enabled?` with NO_COLOR, FORCE_COLOR, Windows terminal detection
   - Make `colors-enabled?` public (remove `defn-` private marker)
   - Add docstring explaining detection logic and standards

2. **src/aishell/detection/formatters.clj** (MEDIUM PRIORITY)
   - Line 8: Change `(some? (System/console))` to `(output/colors-enabled?)`
   - Add `aishell.output` to namespace requires

3. **src/aishell/check.clj** (MEDIUM PRIORITY)
   - Find GREEN color definition: Change `(some? (System/console))` to `(output/colors-enabled?)`
   - Add `aishell.output` to namespace requires

Total: 3 files, straightforward enhancements to existing patterns

### Files NOT Requiring Changes

Based on analysis:

1. **src/aishell/attach.clj**
   - Already handles TERM/COLORTERM defaults correctly (Phase 56)
   - `resolve-term` provides fallback for unset TERM
   - COLORTERM defaults to "truecolor" if unset
   - TERM-02 requirement already satisfied

2. **src/aishell/docker/run.clj**
   - COLORTERM default at line 287: `(or (System/getenv "COLORTERM") "truecolor")`
   - Already correct for Windows — no changes needed

3. **src/aishell/docker/spinner.clj**
   - Uses `(System/console)` for spinner TTY check (line 14)
   - This is CORRECT: spinners need TTY, not color support
   - Don't change to `colors-enabled?` — spinner logic is different from color logic

### Testing Scope

**Manual testing required on both platforms:**

**Unix (Linux/macOS):**
- Test NO_COLOR=1 disables colors: `NO_COLOR=1 aishell --help`
- Test FORCE_COLOR=1 enables colors when piped: `FORCE_COLOR=1 aishell check | cat`
- Test piped output is plain: `aishell check | cat` (no ANSI codes)
- Test redirected output is plain: `aishell check > out.txt` (file is plain text)
- Test TERM=dumb disables colors: `TERM=dumb aishell --help`
- Test normal interactive has colors: `aishell --help` (colored)

**Windows:**
- Test Windows Terminal: `aishell --help` in Windows Terminal (should have colors)
- Test WT_SESSION detection: `echo %WT_SESSION%` shows GUID, colors enabled
- Test cmd.exe: `aishell --help` in plain cmd.exe (should be plain if WT_SESSION unset)
- Test PowerShell: `aishell --help` in PowerShell (depends on COLORTERM/WT_SESSION)
- Test NO_COLOR on Windows: `set NO_COLOR=1 && aishell --help` (plain)
- Test FORCE_COLOR on Windows: `set FORCE_COLOR=1 && aishell check` (colored)

**Edge cases:**
- ConEmu: Set ConEmuANSI=ON, verify colors enabled
- Git Bash on Windows: Check TERM variable, verify Unix detection path works
- Emacs shell: TERM=dumb should disable colors
- CI environment: FORCE_COLOR=1 should enable colors even without TTY

**Regression testing (existing behavior):**
- Error messages still colored in interactive terminal
- Warning messages still colored in interactive terminal
- Detection output formatting unchanged
- Check command output formatting unchanged

## Sources

### Primary (HIGH confidence)

- **NO_COLOR standard**: [https://no-color.org/](https://no-color.org/) — Community standard for disabling ANSI color output
- **FORCE_COLOR standard**: [https://force-color.org/](https://force-color.org/) — Community standard for forcing ANSI color output
- **Java Console documentation**: [Console (Java SE 8)](https://docs.oracle.com/javase/8/docs/api/java/io/Console.html) — System.console() behavior and null return conditions
- **Existing codebase**:
  - `src/aishell/output.clj` (lines 6-16) — Current colors-enabled? implementation
  - `src/aishell/attach.clj` (lines 14, 63) — TERM/COLORTERM defaults for containers
  - `src/aishell/detection/formatters.clj` (line 8) — DIM color definition with System.console check

### Secondary (MEDIUM confidence)

- **Windows Terminal documentation**:
  - [GitHub Issue #13006](https://github.com/microsoft/terminal/issues/13006) — WT_SESSION environment variable discussion
  - [Detecting Windows Terminal with PowerShell](https://mikefrobbins.com/2024/05/16/detecting-windows-terminal-with-powershell/) — WT_SESSION usage pattern
- **ConEmu documentation**:
  - [ConEmu ANSI X3.64 Support](https://conemu.github.io/en/AnsiEscapeCodes.html) — ConEmuANSI environment variable
- **Windows ANSI support**:
  - [Console Virtual Terminal Sequences](https://learn.microsoft.com/en-us/windows/console/console-virtual-terminal-sequences) — Windows 10+ VT100 support
  - [SS64: ANSI colors in Windows CMD](https://ss64.com/nt/syntax-ansi.html) — Windows ANSI availability and enablement
- **Terminal color standards**:
  - [Standard for ANSI Colors in Terminals](http://bixense.com/clicolors/) — CLICOLOR/CLICOLOR_FORCE discussion
  - [So you want to render colors in your terminal](https://marvinh.dev/blog/terminal-colors/) — COLORTERM and TERM detection best practices

### Tertiary (LOW confidence)

- WebSearch: "ANSI color detection best practices 2026" — General patterns, verified against official docs
- WebSearch: "Windows Terminal WT_SESSION ANSI support 2026" — Confirmed WT_SESSION usage
- WebSearch: "Java System.console null detection 2026" — Confirmed pipe/redirect behavior

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH — Uses Java built-ins and existing babashka.fs, no new dependencies
- Architecture: HIGH — Based on community standards (NO_COLOR, FORCE_COLOR) and official Windows documentation
- Pitfalls: HIGH — Based on common CLI tool mistakes and community standard documents
- Windows testing: MEDIUM — No actual Windows testing performed (static analysis only)

**Research date:** 2026-02-12
**Valid until:** 2026-03-12 (30 days - standards are stable, Windows Terminal behavior unlikely to change)

**Phase-specific notes:**
- Phase 57 builds on Phase 56 (process execution with :inherit) and Phase 53 (platform detection)
- TERM-02 requirement (sensible defaults) already satisfied by Phase 56 — Phase 57 documents the pattern
- TERM-01 requirement (auto-detection and stripping) is the main work for Phase 57
- Simple phase: ~3 files need changes, pattern is well-established from community standards

**Codebase context:**
- Babashka project (SCI interpreter), not JVM Clojure
- System/console returns null when stdout/stdin/stderr redirected (pipes, files, CI)
- NO_COLOR and FORCE_COLOR are informal standards with 500+ CLI tool adoption
- Windows Terminal sets WT_SESSION, ConEmu sets ConEmuANSI=ON, both are ANSI-capable
- Current code already handles TERM/COLORTERM defaults for containers (Phase 56)
