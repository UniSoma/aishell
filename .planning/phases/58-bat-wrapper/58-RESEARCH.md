# Phase 58: .bat Wrapper - Research

**Researched:** 2026-02-12
**Domain:** Windows batch script wrappers for Babashka CLI tools
**Confidence:** HIGH

## Summary

This phase creates a Windows-native launcher script (aishell.bat) that enables Windows users to run aishell from PATH without the bb prefix. The research confirms that Windows .bat wrappers for Babashka tools follow an established 4-line pattern pioneered by the official neil tool. The wrapper is simple, robust, and cross-platform compatible with existing uberscript distribution.

**Key findings:**
- Neil tool demonstrates production-ready 4-line .bat wrapper pattern used across Babashka ecosystem
- Windows batch argument forwarding via %* preserves all arguments correctly
- %~dp0 variable provides script-adjacent path resolution, enabling wrapper to find uberscript
- Existing build-release.clj requires minimal changes (add .bat file generation)
- .bat wrapper works seamlessly with existing Linux uberscript (no platform-specific builds needed)

**Primary recommendation:** Follow neil's 4-line wrapper pattern exactly, generate aishell.bat during build-release.clj, and include in GitHub Releases alongside uberscript. No PowerShell, no Scoop manifest needed for Phase 58 — just the .bat file.

## Standard Stack

### Core
| Component | Version | Purpose | Why Standard |
|-----------|---------|---------|--------------|
| Windows CMD | Built-in | Batch script interpreter | Universal on all Windows versions, no dependencies |
| Babashka | 1.0+ | Clojure runtime | Already required by aishell, provides bb.exe on Windows |
| .bat extension | N/A | Windows executable wrapper | Standard Windows script format recognized in PATH |

### Supporting
| Component | Version | Purpose | When to Use |
|-----------|---------|---------|-------------|
| %~dp0 expansion | Built-in | Script directory detection | Finding uberscript in same directory as .bat |
| %* variable | Built-in | Argument forwarding | Passing all CLI args to bb |

### Alternatives Considered
| Instead of | Could Use | Tradeoff |
|------------|-----------|----------|
| .bat wrapper | .cmd wrapper | Identical functionality, .bat more common convention |
| Direct bb invocation | Scoop shim | Scoop adds value but out of scope for Phase 58 |
| Batch script | PowerShell script | .ps1 requires Set-ExecutionPolicy, .bat works universally |

**Installation:**
No additional dependencies required. Windows users must have Babashka installed (bb.exe in PATH).

## Architecture Patterns

### Recommended File Structure
```
dist/
├── aishell            # Uberscript (Unix shebang, works on all platforms)
├── aishell.bat        # Windows wrapper (NEW in Phase 58)
└── aishell.sha256     # Checksum for uberscript
```

### Pattern 1: 4-Line Batch Wrapper (Neil Pattern)
**What:** Minimal Windows batch script that invokes bb with script file and forwarded arguments
**When to use:** Standard wrapper for all Babashka CLI tools distributed as uberscripts
**Example:**
```batch
@echo off
set ARGS=%*
set SCRIPT=%~dp0aishell
bb -f %SCRIPT% %ARGS%
```

**Line-by-line breakdown:**
1. `@echo off` — Suppress command echo for clean console output
2. `set ARGS=%*` — Capture all command-line arguments into ARGS variable
3. `set SCRIPT=%~dp0aishell` — Build path to uberscript in same directory as .bat
4. `bb -f %SCRIPT% %ARGS%` — Invoke Babashka with script file and forwarded args

### Pattern 2: Build-Time .bat Generation
**What:** Generate aishell.bat during build-release.clj execution
**When to use:** Automated release builds ensuring .bat and uberscript stay synchronized
**Example:**
```clojure
;; In scripts/build-release.clj after uberscript creation
(defn create-bat-wrapper
  "Generate Windows .bat wrapper following neil pattern."
  [output-file]
  (let [bat-file (str output-file ".bat")
        bat-content "@echo off\nset ARGS=%*\nset SCRIPT=%~dp0aishell\nbb -f %SCRIPT% %ARGS%\n"]
    (spit bat-file bat-content)
    bat-file))

;; Call after uberscript build
(let [bat-file (create-bat-wrapper output-file)]
  (println (str "  Wrapper:  " bat-file)))
```

### Pattern 3: Cross-Platform Uberscript Compatibility
**What:** Same uberscript works on Unix (via shebang) and Windows (via .bat wrapper)
**When to use:** All platforms share identical binary, reducing distribution complexity
**How it works:**
- Unix/macOS: `#!/usr/bin/env bb` shebang enables direct execution (`./aishell`)
- Windows: .bat wrapper invokes `bb -f aishell` explicitly (shebang ignored)
- GitHub Releases: Upload both files, users download what they need

### Pattern 4: PATH Installation Instructions
**What:** Users add dist/ directory to Windows PATH environment variable
**When to use:** Manual installation without Scoop/package managers
**Example:**
```powershell
# PowerShell (Administrator)
$path = [Environment]::GetEnvironmentVariable("Path", "User")
$newPath = "$path;C:\Users\username\bin"
[Environment]::SetEnvironmentVariable("Path", $newPath, "User")
```

Or via System Properties GUI:
1. Settings → System → About → Advanced system settings
2. Environment Variables → User variables → Path → Edit
3. Add directory containing aishell.bat

### Anti-Patterns to Avoid
- **Using %1 %2 %3 ... instead of %*:** Limits to 9 arguments, breaks with spaces
- **Quoting %SCRIPT% path:** Unnecessary and breaks if path contains quotes
- **Adding .exe extension:** bb.exe is found via PATH, explicit extension not needed
- **Embedding full bb.exe path:** Fragile across installations, rely on PATH lookup

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| Custom argument parsing | Manual %1 %2 logic | %* variable | Handles arbitrary arg count, preserves spaces |
| Script path resolution | Hardcoded paths | %~dp0 expansion | Works regardless of install location |
| Line ending conversion | sed/dos2unix | Windows-native CRLF | .bat expects CRLF, uberscript LF is fine |
| bb.exe detection | where.exe checks | Direct bb invocation | Windows PATH handles lookup, fails fast if missing |

**Key insight:** Windows batch scripting has robust built-in primitives for common CLI wrapper patterns. The 4-line neil pattern is battle-tested and requires zero external dependencies or platform detection.

## Common Pitfalls

### Pitfall 1: Argument Quoting and %* Limitations
**What goes wrong:** Arguments with spaces or special characters don't forward correctly
**Why it happens:** %* expands to unquoted argument list, CMD word-splitting occurs
**How to avoid:** Rely on CMD's built-in quoting preservation — %* maintains original quotes from caller
**Warning signs:** Commands like `aishell shell "my project"` fail, only see "my"

**Example:**
```batch
# BAD - Manual argument reconstruction loses quotes
bb -f %SCRIPT% %1 %2 %3

# GOOD - %* preserves original quoting
bb -f %SCRIPT% %ARGS%
```

**Note:** Modern Windows (Windows 10+) preserves quoted arguments correctly through %*. Legacy edge cases (CMD /C invocation) are rare in PATH-based CLI tools.

### Pitfall 2: CRLF vs LF Line Endings
**What goes wrong:** .bat file has LF line endings, Windows CMD fails to parse
**Why it happens:** Git autocrlf=input converts to LF, Windows batch requires CRLF
**How to avoid:** Generate .bat at build time with explicit \r\n, or add .gitattributes rule
**Warning signs:** "The syntax of the command is incorrect" errors on Windows

**Solution:**
```
# .gitattributes
*.bat text eol=crlf
```

Or in build script:
```clojure
;; Ensure Windows line endings
(spit bat-file bat-content)  ; Babashka uses platform line endings
;; Or explicit:
(spit bat-file (str/replace bat-content "\n" "\r\n"))
```

### Pitfall 3: %~dp0 Trailing Backslash
**What goes wrong:** %~dp0 includes trailing backslash, concatenation creates double backslash
**Why it happens:** %~dp0 always ends with \ (e.g., "C:\Users\bin\")
**How to avoid:** Concatenate filename directly: %~dp0aishell (no separator needed)
**Warning signs:** bb -f fails with "file not found" despite correct path

**Example:**
```batch
# BAD - Creates C:\Users\bin\\aishell (double backslash works but ugly)
set SCRIPT=%~dp0\aishell

# GOOD - Creates C:\Users\bin\aishell (clean path)
set SCRIPT=%~dp0aishell
```

### Pitfall 4: Missing bb.exe in PATH
**What goes wrong:** .bat fails with "'bb' is not recognized as internal or external command"
**Why it happens:** Babashka not installed or not in system PATH
**How to avoid:** Clear error message, document bb requirement in installation instructions
**Warning signs:** Works for developer but not end users

**Mitigation:**
```batch
@echo off
where bb >nul 2>nul
if %errorlevel% neq 0 (
    echo Error: Babashka not found. Install: https://babashka.org
    exit /b 1
)
set ARGS=%*
set SCRIPT=%~dp0aishell
bb -f %SCRIPT% %ARGS%
```

**Note:** This adds complexity. For Phase 58, keep minimal 4-line wrapper and rely on documentation.

### Pitfall 5: Case-Sensitivity in Filenames
**What goes wrong:** .bat references "Aishell" but file is "aishell" (or vice versa)
**Why it happens:** Windows filesystem is case-insensitive but case-preserving
**How to avoid:** Use consistent lowercase naming: aishell, aishell.bat, aishell.sha256
**Warning signs:** Works on developer machine, fails on some Windows configurations

**Best practice:** All artifacts lowercase (current codebase already follows this).

### Pitfall 6: Uberscript Extension Assumptions
**What goes wrong:** .bat looks for "aishell.clj" but uberscript has no extension
**Why it happens:** Babashka scripts traditionally use .clj/.bb extensions
**How to avoid:** Neil pattern uses no extension for uberscript (cross-platform consistency)
**Warning signs:** Works on Unix (./aishell) but .bat can't find script

**Correct structure:**
```
dist/aishell       ← No extension (Unix shebang execution)
dist/aishell.bat   ← Windows wrapper (invokes bb -f aishell)
```

## Code Examples

Verified patterns from official sources:

### Complete aishell.bat Wrapper
```batch
@echo off
set ARGS=%*
set SCRIPT=%~dp0aishell
bb -f %SCRIPT% %ARGS%
```
Source: [neil.bat](https://github.com/babashka/neil/blob/main/neil.bat) - Official Babashka neil tool

### Integration into build-release.clj
```clojure
#!/usr/bin/env bb

(ns build-release
  (:require [babashka.fs :as fs]
            [babashka.process :as p]
            [clojure.string :as str]))

(def output-dir "dist")
(def output-file (str output-dir "/aishell"))
(def bat-file (str output-file ".bat"))

(defn compute-sha256
  "Compute SHA-256 hash of file, returning 64-character hex string."
  [file-path]
  (let [md (java.security.MessageDigest/getInstance "SHA-256")
        bytes (.digest md (fs/read-all-bytes file-path))]
    (apply str (map #(format "%02x" (bit-and % 0xff)) bytes))))

(defn create-bat-wrapper
  "Generate Windows .bat wrapper following neil pattern.
   Uses Windows line endings (CRLF) for proper CMD parsing."
  [script-name]
  (let [bat-content (str "@echo off\r\n"
                         "set ARGS=%*\r\n"
                         "set SCRIPT=%~dp0" script-name "\r\n"
                         "bb -f %SCRIPT% %ARGS%\r\n")]
    (spit bat-file bat-content)))

(defn main []
  (println "Building aishell uberscript...")

  ;; Create output directory
  (fs/create-dirs output-dir)

  ;; Remove existing artifacts (bb uberscript refuses to overwrite)
  (fs/delete-if-exists output-file)
  (fs/delete-if-exists bat-file)

  ;; Build uberscript with main namespace
  (p/shell "bb" "uberscript" output-file "-m" "aishell.core")

  ;; Add shebang for Unix direct execution
  (let [content (slurp output-file)]
    (spit output-file (str "#!/usr/bin/env bb\n" content)))

  ;; Make executable (Unix only, no-op on Windows)
  (when-not (fs/windows?)
    (p/shell "chmod" "+x" output-file))

  ;; Generate Windows .bat wrapper
  (create-bat-wrapper "aishell")

  ;; Generate checksum for uberscript only (.bat doesn't need checksum)
  (let [hash (compute-sha256 output-file)
        checksum-file (str output-file ".sha256")
        checksum-content (str hash "  aishell\n")]
    (spit checksum-file checksum-content))

  ;; Print completion message
  (println)
  (println "Build complete!")
  (println (str "  Binary:   " output-file))
  (println (str "  Wrapper:  " bat-file))
  (println (str "  Checksum: " output-file ".sha256"))
  (println)
  (print (slurp (str output-file ".sha256"))))

(main)
```

### Updated install.sh (Add Windows Instructions)
```bash
#!/bin/bash
# install.sh - Installer for aishell (Unix/macOS)
# For Windows: Download manually from GitHub Releases

set -euo pipefail

install_aishell() {
    # ... existing Unix installation logic ...

    # Post-install message
    success "Installed aishell to ${install_dir}/aishell"

    if [[ ":$PATH:" != *":$install_dir:"* ]]; then
        echo ""
        echo "Note: ${install_dir} is not in your PATH."
        echo "Add to your shell profile (~/.bashrc, ~/.zshrc, or ~/.profile):"
        echo ""
        echo "  export PATH=\"${install_dir}:\$PATH\""
        echo ""
    fi
}

# Detect Windows/WSL
if [[ -f /proc/version ]] && grep -qi microsoft /proc/version; then
    echo "Detected WSL environment."
    echo "This installer works in WSL. For native Windows (cmd/PowerShell):"
    echo "  1. Download aishell.bat and aishell from GitHub Releases"
    echo "  2. Place both files in the same directory (e.g., C:\\Users\\username\\bin)"
    echo "  3. Add that directory to your Windows PATH"
    echo ""
    read -p "Continue with WSL installation? [y/N] " -n 1 -r
    echo
    if [[ ! $REPLY =~ ^[Yy]$ ]]; then
        exit 0
    fi
fi

install_aishell "$@"
```

### GitHub Release Upload (Both Files)
```yaml
# .github/workflows/release.yml
- name: Upload aishell binary
  uses: actions/upload-release-asset@v1
  with:
    upload_url: ${{ steps.create_release.outputs.upload_url }}
    asset_path: ./dist/aishell
    asset_name: aishell
    asset_content_type: application/octet-stream

- name: Upload Windows wrapper
  uses: actions/upload-release-asset@v1
  with:
    upload_url: ${{ steps.create_release.outputs.upload_url }}
    asset_path: ./dist/aishell.bat
    asset_name: aishell.bat
    asset_content_type: application/octet-stream

- name: Upload checksum
  uses: actions/upload-release-asset@v1
  with:
    upload_url: ${{ steps.create_release.outputs.upload_url }}
    asset_path: ./dist/aishell.sha256
    asset_name: aishell.sha256
    asset_content_type: text/plain
```

### Windows Installation Instructions (README)
```markdown
## Installation

### Windows (cmd.exe / PowerShell)

1. Download from [GitHub Releases](https://github.com/UniSoma/aishell/releases/latest):
   - `aishell` (Babashka uberscript)
   - `aishell.bat` (Windows wrapper)

2. Place both files in the same directory (e.g., `C:\Users\username\bin`)

3. Add directory to PATH:
   - Settings → System → About → Advanced system settings
   - Environment Variables → User variables → Path → Edit
   - Add new entry: `C:\Users\username\bin`

4. Verify installation:
   ```cmd
   aishell --version
   ```

**Requirements:**
- [Babashka](https://babashka.org) installed (bb.exe in PATH)
- [Docker Desktop](https://docker.com) with WSL2 backend

### Unix/macOS (Linux, macOS, WSL)

```bash
curl -fsSL https://raw.githubusercontent.com/UniSoma/aishell/main/install.sh | bash
```

Or download manually:
```bash
# Download binary
curl -fsSL https://github.com/UniSoma/aishell/releases/latest/download/aishell -o ~/.local/bin/aishell

# Verify checksum (optional)
curl -fsSL https://github.com/UniSoma/aishell/releases/latest/download/aishell.sha256 | sha256sum -c

# Make executable
chmod +x ~/.local/bin/aishell
```
```

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| PowerShell wrappers (.ps1) | Batch wrappers (.bat) | Ongoing (2020+) | .bat works universally without Set-ExecutionPolicy |
| Scoop shims | Manual .bat | Babashka ecosystem | Scoop adds value but requires bucket submission |
| Platform-specific builds | Universal uberscript + wrapper | Babashka design | Single binary works on all platforms via wrapper/shebang |
| Manual PATH editing | Scoop automatic PATH | Mature package managers | Phase 58 uses manual, Scoop deferred to future |

**Deprecated/outdated:**
- **PowerShell-only wrappers:** .ps1 requires ExecutionPolicy changes, .bat works out-of-box
- **Compiled .exe wrappers:** GraalVM native-image unnecessary for Babashka scripts
- **Custom installer scripts:** Scoop/Chocolatey standard for Windows package distribution

## Open Questions

1. **Checksum for .bat file?**
   - What we know: .bat is generated at build time, could have separate checksum
   - What's unclear: Whether users validate .bat integrity or trust GitHub Releases
   - Recommendation: Skip .bat checksum in Phase 58 — .bat is simple and auditable (4 lines), focus on uberscript checksum

2. **CRLF line endings — generate at build or commit?**
   - What we know: .bat requires CRLF, Git can autocrlf or .gitattributes handle it
   - What's unclear: Whether to commit .bat to repo or generate at build time only
   - Recommendation: Generate at build time (not committed), include in releases only — avoids line ending configuration issues

3. **bb.exe vs bb invocation in wrapper?**
   - What we know: Windows PATH lookup finds bb.exe automatically
   - What's unclear: Whether explicit .exe extension improves reliability
   - Recommendation: Use `bb` (no .exe) matching neil pattern — Windows PATH handles extension automatically

4. **.bat in repository or build-only?**
   - What we know: Neil commits .bat to repo, some projects build-generate only
   - What's unclear: Whether committing .bat improves developer UX or adds line-ending maintenance burden
   - Recommendation: Build-time generation (not committed) — avoids line ending conflicts, .bat is trivial to regenerate

## Sources

### Primary (HIGH confidence)
- [neil.bat](https://github.com/babashka/neil/blob/main/neil.bat) - Official Babashka neil tool wrapper (4-line reference implementation)
- [Babashka Windows Binary Support](https://github.com/babashka/babashka/issues/138) - Official Windows compatibility discussion
- [Babashka Book](https://book.babashka.org/) - Official Babashka documentation confirming Windows support
- [Windows CMD Parameters/Arguments](https://ss64.com/nt/syntax-args.html) - SS64 authoritative reference for %*, %~dp0, argument parsing
- [Batch files - Command line parameters](https://www.robvanderwoude.com/parameters.php) - Rob van der Woude's comprehensive batch scripting reference

### Secondary (MEDIUM confidence)
- [Windows Batch File Best Practices](https://www.robvanderwoude.com/battech_bestpractices.php) - Community-validated best practices (@echo off, argument quoting)
- [GitHub - trgwii/Batch-Style-Guide](https://github.com/trgwii/Batch-Style-Guide) - Community style guide for batch scripting conventions
- [Scoop App Manifests](https://github.com/ScoopInstaller/Scoop/wiki/App-Manifests) - Scoop packaging format (future enhancement context)
- [babashka/bbin Installation](https://github.com/babashka/bbin/blob/main/docs/installation.md) - Another Babashka tool using similar wrapper patterns

### Tertiary (LOW confidence)
- [Codebase: scripts/build-release.clj](file:///home/jonasrodrigues/projects/harness/scripts/build-release.clj) - Current build script structure (basis for integration)
- [Codebase: .planning/phases/18-distribution/18-RESEARCH.md](file:///home/jonasrodrigues/projects/harness/.planning/phases/18-distribution/18-RESEARCH.md) - Original distribution research (background context)
- [Investigation: Windows Support Report](file:///home/jonasrodrigues/projects/harness/artifacts/investigate/20260211-2024-native-windows-support-aishell/REPORT.md) - Comprehensive Windows compatibility analysis

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH - Neil wrapper is production-proven pattern across Babashka ecosystem
- Architecture: HIGH - 4-line pattern verified in official neil tool, %* and %~dp0 documented in authoritative sources
- Pitfalls: HIGH - CRLF, argument quoting, path resolution all documented in Rob van der Woude and SS64 references

**Research date:** 2026-02-12
**Valid until:** 180 days (stable Windows CMD primitives, slow-moving conventions)
