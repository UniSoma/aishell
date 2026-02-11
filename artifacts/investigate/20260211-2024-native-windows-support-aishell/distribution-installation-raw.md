# Distribution & Installation Research: Native Windows Support for aishell

## Research Parameters

**Topic**: Native Windows support for aishell (Babashka Clojure CLI tool that manages Docker containers)

**Perspective**: Distribution & Installation

**Focus**: Windows installation strategy — replacing bash installer, PATH setup, release artifacts, PowerShell script or scoop/winget package

**Date**: 2026-02-11

**Session**: 20260211-2024-native-windows-support-aishell

---

## Key Findings

- **PowerShell installer script is technically viable** — PowerShell's built-in `Get-FileHash` cmdlet natively supports SHA256 verification, and installers can programmatically add directories to PATH using `[Environment]::SetEnvironmentVariable()` with administrator elevation. The pattern is well-established in production tools like PowerShell itself.

- **Scoop is the most pragmatic distribution channel for Windows Babashka tools** — Official Babashka tools (neil) follow the scoop pattern: download a script file + a .bat wrapper, manifest in Scoop's main bucket handles PATH automatically, and no special packaging or executable compilation required. This matches aishell's architecture perfectly.

- **.bat wrapper pattern is standard for Babashka scripts on Windows** — A simple 4-line batch file (`@echo off`, set arguments, construct path, invoke `bb`) enables direct command-line access to Babashka scripts without end users needing to know about the runtime. The neil project demonstrates this is production-ready.

- **Winget distribution is possible but involves higher friction** — Winget requires installers (MSIX, MSI, .exe) or package YAML manifests with strict requirements (silent install support, specific archive formats). Babashka scripts alone don't meet Winget's installer type requirements without packaging as .exe or creating custom installer logic.

- **Release artifacts should include .bat wrapper file** — Current release pipeline produces `dist/aishell` + `dist/aishell.sha256` for Unix. Windows distribution requires adding `dist/aishell.bat` as a release artifact alongside the script file, following the pattern established by neil.

---

## Analysis

### Current State: Unix-Only Distribution

The existing `install.sh` script handles bash environments cleanly:
- Downloads `aishell` script from GitHub releases
- Verifies SHA256 checksum against `.sha256` file
- Makes executable with `chmod +x`
- Installs to `~/.local/bin`
- Checks PATH and offers guidance if needed

Windows has no native equivalent: no bash, no chmod, and `chmod +x` is a POSIX concept. Even on Windows with WSL or Git Bash installed, the script depends on Unix tools and the POSIX filesystem model.

### Option 1: PowerShell Installer Script (`install.ps1`)

**Approach**: Create a Windows-native PowerShell installer script as the replacement for `install.sh`.

**Technical Feasibility**:

PowerShell provides native equivalents for all operations:

- **Download**: Use `Invoke-WebRequest` cmdlet (built-in since PowerShell 3.0)
- **Checksum verification**: Use `Get-FileHash -Algorithm SHA256` (built-in since PowerShell 4.0, Windows 2012R2+)
- **PATH modification**: Use `[Environment]::SetEnvironmentVariable()` with `[EnvironmentVariableTarget]::User` to add to user PATH programmatically. Requires administrator elevation for Machine scope.

According to the [PowerShell documentation for Get-FileHash](https://learn.microsoft.com/en-us/powershell/module/microsoft.powershell.utility/get-filehash?view=powershell-7.5), SHA256 is the default algorithm, and comparison is case-insensitive by default.

The pattern used in production by PowerShell itself is: `Invoke-Expression "& { $(Invoke-RestMethod 'https://aka.ms/install-powershell.ps1') } -daily"` — invoke a script hosted on GitHub, optionally piped from `curl.exe` or run directly.

**Implementation Pattern**:

```powershell
# 1. Create user bin directory if needed
New-Item -ItemType Directory -Force -Path $Env:HOMEDRIVE$Env:HOMEPATH\.local\bin

# 2. Download script and wrapper
Invoke-WebRequest -Uri "https://github.com/.../releases/latest/download/aishell" `
  -OutFile "$Env:HOMEDRIVE$Env:HOMEPATH\.local\bin\aishell"
Invoke-WebRequest -Uri "https://github.com/.../releases/latest/download/aishell.bat" `
  -OutFile "$Env:HOMEDRIVE$Env:HOMEPATH\.local\bin\aishell.bat"
Invoke-WebRequest -Uri "https://github.com/.../releases/latest/download/aishell.sha256" `
  -OutFile "$Env:TEMP\aishell.sha256"

# 3. Verify hash
$hash = (Get-FileHash -Path "$install_dir\aishell" -Algorithm SHA256).Hash
$expected = Get-Content "$Env:TEMP\aishell.sha256" | Select-Object -First 1
if ($hash -ne $expected) { throw "Hash mismatch" }

# 4. Add to PATH (requires elevation for User scope)
$current_path = [Environment]::GetEnvironmentVariable("PATH", "User")
if ($current_path -notlike "*\.local\bin*") {
  [Environment]::SetEnvironmentVariable(
    "PATH",
    "$current_path;$Env:HOMEDRIVE$Env:HOMEPATH\.local\bin",
    "User"
  )
}
```

**Advantages**:
- No additional tools required (PowerShell 5.0+ is Windows default)
- Can be invoked via `curl.exe | PowerShell` pattern
- Full programmatic PATH management
- Works on Windows 10+, Windows Server 2016+
- Familiar pattern to Windows administrators

**Disadvantages**:
- Requires PowerShell execution policy adjustment or script signing
- PATH update requires closing/reopening terminal to take effect
- Less discoverable than package managers (users must know the URL)
- Manual version tracking by end users

---

### Option 2: Scoop Package Manager (Recommended for Windows)

**Approach**: Create a Scoop manifest file and submit to the `babashka` bucket or main bucket.

**Technical Feasibility**:

Scoop is a mature command-line package manager for Windows, comparable to apt/brew. [Official Scoop documentation](https://github.com/ScoopInstaller/Scoop/wiki/App-Manifests) defines manifests as JSON files describing how to install applications.

The pattern is proven: both Babashka itself and tools built with Babashka (neil, bbin) are distributed via Scoop.

**Manifest Structure** (from [Scoop App Manifests](https://github.com/ScoopInstaller/Scoop/wiki/App-Manifests)):

```json
{
  "version": "3.0.0",
  "description": "Docker container shell manager built with Babashka",
  "homepage": "https://github.com/UniSoma/aishell",
  "license": "MIT",
  "url": "https://github.com/UniSoma/aishell/releases/download/v3.0.0/aishell",
  "hash": "sha256:{{computed-hash-here}}",
  "bin": [
    "aishell",
    {
      "name": "aishell",
      "path": "aishell.bat"
    }
  ],
  "depends": "babashka",
  "post_install": [
    "$script_path = Split-Path -Parent $profile",
    "Add-Content $script_path -Value '# aishell installed'"
  ]
}
```

Key manifest fields for CLI tools:
- **url**: Absolute URL to release artifact (can be a .zip or standalone file)
- **hash**: SHA256 of the downloaded file
- **bin**: Which files to expose as commands on PATH. Can specify both script and .bat wrapper.
- **depends**: Runtime dependencies (babashka must be installed first)
- **env_add_path**: Or use bin array with nested object to reference a .bat wrapper

**Distribution Channel**:

1. **Scoop main bucket** (most discoverable):
   - Add manifest to [github.com/ScoopInstaller/Scoop/bucket](https://github.com/ScoopInstaller/Scoop)
   - Used by `scoop install aishell` after `scoop update`
   - Requires submission and review similar to Winget

2. **Babashka community bucket**:
   - Add manifest to `babashka` bucket on Scoop
   - Users add bucket first: `scoop bucket add babashka`
   - Faster path for ecosystem tools

3. **Custom bucket** (quick start):
   - Create separate GitHub repo with manifest file
   - Users add and use: `scoop bucket add aishell https://github.com/.../aishell-bucket`

**Installation Experience**:

```powershell
# One-time setup
scoop bucket add babashka https://github.com/babashka/scoop-bucket

# Install
scoop install aishell
# Scoop automatically:
# - Downloads aishell + aishell.bat
# - Verifies checksums
# - Adds both to PATH
# - Verifies babashka dependency is installed

# Update
scoop update aishell
```

**Advantages**:
- No additional packaging required (script files work as-is)
- Scoop handles PATH automatically (no manual env var editing)
- Scoop handles dependency management (babashka as prerequisite)
- Works offline after initial bucket clone
- Familiar to Windows developers
- Support for auto-updates via `scoop update aishell`
- No installer restrictions (Scoop is portable)

**Disadvantages**:
- Requires Scoop installation first (though has 1M+ users)
- Manifest must be maintained in sync with releases
- Requires commitment to multi-release support

---

### Option 3: Chocolatey Package Manager

**Approach**: Package aishell as a Chocolatey package.

**Technical Feasibility**:

[Chocolatey](https://chocolatey.org/) is a mature Windows package manager using NuGet packages. However, it's less ideal for Babashka scripts than Scoop.

**Why less suitable**:
- Chocolatey natively works with .exe, .msi, .zip packages
- Babashka script distribution typically requires custom installation logic
- Chocolatey packages have higher friction: requires .nuspec file, NuGet package creation, more automation overhead
- Smaller ecosystem for Babashka tools (Scoop is more prevalent in Clojure/dev community)

**When to consider Chocolatey**:
- Organization already standardized on Chocolatey (common in enterprises)
- Need system-wide installation with admin management
- Target audience unfamiliar with Scoop

---

### Option 4: Winget (Windows Package Manager)

**Approach**: Submit manifest to [github.com/microsoft/winget-pkgs](https://github.com/microsoft/winget-pkgs) community repository.

**Technical Feasibility**:

Winget is Microsoft's official package manager, built into Windows 11. However, it has stricter requirements that conflict with Babashka script distribution.

**Blockers**:

According to [Winget submission requirements](https://learn.microsoft.com/en-us/windows/package-manager/package/manifest), installers must be:
- **Type**: MSIX, MSI, APPX, MSIXBundle, APPXBundle, or .exe
- **Behavior**: Support silent/quiet installation mode
- **Unsupported**: Script-based installers, fonts

A Babashka script is not an executable in the Winget sense. Options to work around this:

1. **Wrap script in .exe using GraalVM** (heavy):
   - Babashka compiles to native .exe via GraalVM
   - Adds build complexity and binary size
   - Enables Winget distribution but significant effort for marginal gain

2. **Create .exe installer** (moderate effort):
   - Use tool like NSIS (Nullsoft Installer) or Inno Setup
   - Creates Windows installer .exe
   - Satisfies Winget requirements
   - Adds release artifact complexity

3. **Skip Winget, use Scoop** (pragmatic):
   - Scoop has no such restrictions
   - Better suited to CLI tool distribution
   - Winget audience overlaps with Scoop audience

**Approval Timeline** (if pursuing):

According to [Winget submission discussions](https://github.com/microsoft/winget-pkgs/discussions/19502):
- Automated validation: 30-40 minutes to ~1 hour
- Manual review: hours to several days (depending on maintainer availability)
- Publishing pipeline: ~1 hour after approval

**Recommendation**: Skip Winget for now. If Windows adoption reaches critical mass, creating a native .exe via GraalVM compilation becomes justified.

---

### Option 5: PATH Setup Strategies

**Strategy A: Scoop-managed (recommended with Option 2)**

Scoop's `bin` manifest field automatically creates shims and adds the shim directory to PATH during installation. User sees:
```powershell
scoop install aishell
# aishell is immediately available in PowerShell/cmd/Windows Terminal
aishell --version
```

**Strategy B: Manual PowerShell-based (with Option 1)**

The PowerShell installer can modify PATH programmatically:

```powershell
# For current user (no elevation required)
[Environment]::SetEnvironmentVariable(
  "PATH",
  [Environment]::GetEnvironmentVariable("PATH", "User") + ";$HOME\.local\bin",
  "User"
)

# For system-wide (requires elevation)
[Environment]::SetEnvironmentVariable(
  "PATH",
  [Environment]::GetEnvironmentVariable("PATH", "Machine") + ";C:\Program Files\aishell",
  "Machine"
)
```

**Important**: PATH changes only take effect in new shell processes. Users must:
1. Close PowerShell/cmd window, or
2. Reload environment: `$Env:PATH = [Environment]::GetEnvironmentVariable("PATH")`

**Best Practice Location** (Windows conventions):

- **User-scoped tools** (preferred for CLI tools): `$Env:HOMEDRIVE$Env:HOMEPATH\.local\bin` (equivalent to `~/.local/bin` on Unix)
- **System-wide**: `C:\Program Files\aishell` (requires UAC, less common for CLI dev tools)

Per [Windows installation path conventions](https://learn.microsoft.com/en-us/answers/questions/3235887/what-is-localappdataprograms), user-scoped installation avoids admin requirements and is standard for dev tools.

---

### Option 6: Release Artifact Strategy

**Current Release Pipeline**:

The existing `build-release.clj` script produces:
- `dist/aishell` — Babashka script with shebang
- `dist/aishell.sha256` — SHA256 checksum file

**Recommended Additions for Windows**:

Add `dist/aishell.bat` as a release artifact:

```batch
@echo off
set ARGS=%*
set SCRIPT=%~dp0aishell
bb -f %SCRIPT% %ARGS%
```

This follows the pattern of other Babashka tools. The `.bat` wrapper:
- Intercepts command-line invocation (e.g., `aishell setup`)
- Passes arguments through to the Babashka script
- Requires `bb` (Babashka) to be on PATH (handled by Scoop's dependency management)

**Optional: install.ps1**

If pursuing PowerShell installer distribution:
- Add `install.ps1` to releases as alternative to Bash installer
- Or host it separately (doesn't need to be in release artifacts)

**Build Automation**:

Modify `build-release.clj` or `release.yml` to generate .bat wrapper:

```clojure
;; In build-release.clj
(spit (str output-file ".bat")
  "@echo off\nset ARGS=%*\nset SCRIPT=%~dp0aishell\nbb -f %SCRIPT% %ARGS%\n")
```

---

### Option 7: Self-Update Mechanism on Windows

**Current Unix Approach**:

On Unix systems, users run `aishell self-update` to fetch latest release. The script downloads from GitHub releases and replaces itself.

**Windows Challenges**:

1. **File locking**: Windows locks executables in use, preventing in-place overwrite
2. **Multiple artifacts**: Must update both script file and .bat wrapper
3. **Privilege boundaries**: Script in user's `.local\bin` vs. system-wide installation

**Recommended Approach**:

**Scoop-managed updates** (if distributed via Scoop):
```powershell
scoop update aishell
```
Scoop handles all complexity: downloads, verification, atomic replacement, cleanup.

**Manual PowerShell update** (if standalone installation):

Implement `aishell self-update` as:
1. Download latest `aishell` + `aishell.bat` to temp directory
2. Verify checksums
3. Close aishell process (if running)
4. Copy new files over current installation
5. Restart shell or reload PATH

This mirrors the Unix pattern but accounts for Windows process locking:

```powershell
# Inside aishell.clj or separate update command
(require '[babashka.process :as p])

(defn self-update []
  (let [install-dir (System.getenv "LOCAL_BIN")
        temp-dir (System.getenv "TEMP")
        version-check (p/shell {:out :string} "curl" "-s" "https://api.github.com/repos/UniSoma/aishell/releases/latest")]
    ;; Parse latest version
    ;; Download to temp
    ;; Verify checksums
    ;; Copy from temp to install-dir (Windows native copy, respects locking)
    ;; Cleanup temp
    ))
```

---

### Comparison: Installation Methods

| Method | Setup Friction | Discoverability | Maintenance | Windows-Native | Auto-Update | Recommended |
|--------|---|---|---|---|---|---|
| **Scoop (Option 2)** | Low (one-time bucket add) | Medium (need bucket link) | Medium (manifest per release) | Yes | Built-in ✓ | **YES** |
| **PowerShell installer (Option 1)** | Very low (one URL) | Low (must share URL) | Low (run script) | Yes | Manual | Secondary |
| **Chocolatey (Option 3)** | Low (pre-installed in some orgs) | High (discoverability) | High (NuGet packaging) | Partial | Built-in | Conditional |
| **Winget (Option 4)** | Very low (built-in) | Very high | Very high (native exe required) | Yes | Built-in | Future only |
| **Custom bucket** | Medium (clone bucket) | Very low | Medium | Yes | Variable | Development |

---

## Sources

### Primary Sources (Tier 1)

- [Microsoft Learn: Get-FileHash PowerShell Cmdlet](https://learn.microsoft.com/en-us/powershell/module/microsoft.powershell.utility/get-filehash?view=powershell-7.5)
- [Microsoft Learn: Create or Update PATH Environment Variable](https://learn.microsoft.com/en-us/powershell/dsc/reference/psdscresources/resources/environment/createpathvariable?view=dsc-2.0)
- [Winget Official Documentation: Package Manifest](https://learn.microsoft.com/en-us/windows/package-manager/package/manifest)
- [Winget Official Documentation: Submit to Repository](https://learn.microsoft.com/en-us/windows/package-manager/package/repository)
- [Babashka Official Book](https://book.babashka.org/)
- [Scoop Official Wiki: App Manifests](https://github.com/ScoopInstaller/Scoop/wiki/App-Manifests)
- [Scoop Official Wiki: Creating App Manifests](https://github.com/ScoopInstaller/Scoop/wiki/Creating-an-app-manifest)

### Expert Analysis (Tier 2)

- [Windows Installation Path Conventions: AppData vs Program Files](https://learn.microsoft.com/en-us/answers/questions/3235887/what-is-localappdataprograms)
- [Babashka/neil GitHub: Windows .bat Wrapper Implementation](https://github.com/babashka/neil/blob/main/neil.bat)
- [Babashka/bbin GitHub: Installation Documentation](https://github.com/babashka/bbin/blob/main/docs/installation.md)
- [TechTarget: Manage Windows PATH with PowerShell](https://www.techtarget.com/searchitoperations/answer/Manage-the-Windows-PATH-environment-variable-with-PowerShell)
- [Delft Stack: Set PATH Environment Variable in PowerShell](https://www.delftstack.com/howto/powershell/set-the-path-environment-variable-in-powershell/)
- [Thomas Maurer: Download Scripts from GitHub with PowerShell](https://www.thomasmaurer.ch/2021/07/powershell-download-script-or-file-from-github/)
- [PowerShell Examples: File Checksum Verification](https://kevinhakanson.com/2019-04-14-file-verification-of-a-sha-256-hash-using-powershell/)

### Metrics & Community (Tier 3)

- [GitHub: babashka/neil Repository](https://github.com/babashka/neil) (production Babashka tool using .bat pattern)
- [GitHub: babashka/bbin Repository](https://github.com/babashka/bbin) (production tool with multiple Windows installation paths)
- [GitHub: ScoopInstaller/Scoop Repository](https://github.com/ScoopInstaller/Scoop)
- [GitHub: microsoft/winget-pkgs Repository](https://github.com/microsoft/winget-pkgs)
- [GitHub Discussions: Winget Submission Timelines](https://github.com/microsoft/winget-pkgs/discussions/19502)
- [Scoop Concepts Documentation](https://scoop.netlify.app/concepts/)

---

## Confidence Assessment

**Overall Confidence**: High

**Factors Supporting High Confidence**:
- All technical foundations (PowerShell cmdlets, Scoop manifests) are well-documented in official sources
- Patterns are proven in production by Babashka ecosystem tools (neil, bbin)
- Current aishell architecture (single Babashka script) aligns well with Scoop distribution model
- Windows PATH and environment management documented thoroughly in Microsoft Learn
- Scoop and Winget submission processes well-established

**Areas of Slight Uncertainty**:
- Exact time to Winget approval varies significantly (hours to days); estimate based on community reports
- Self-update mechanism on Windows not yet implemented in aishell; design is sound but untested
- Exact friction of custom Scoop bucket creation (assumed medium, not directly verified)

**Gaps**:
- No investigation of Babashka's Windows startup behavior or performance characteristics at scale
- Limited research on how Chocolatey handles Babashka script dependencies
- No testing of PowerShell execution policy barriers in enterprise environments
- Didn't investigate CI/CD integration for multi-platform release artifacts
