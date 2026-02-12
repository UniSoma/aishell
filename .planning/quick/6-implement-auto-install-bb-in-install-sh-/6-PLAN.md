---
phase: quick-6
plan: 01
type: execute
wave: 1
depends_on: []
files_modified:
  - install.sh
  - install.ps1
  - README.md
autonomous: true

must_haves:
  truths:
    - "install.sh auto-installs Babashka when bb is not found (no manual step)"
    - "install.sh skips Babashka install when bb is already present"
    - "install.ps1 provides a one-liner Windows install experience (bb + aishell + PATH)"
    - "README.md Quick Start reflects simplified install for both platforms"
  artifacts:
    - path: "install.sh"
      provides: "Auto-install Babashka via official installer with --dir flag"
      contains: "install_babashka"
    - path: "install.ps1"
      provides: "Full Windows installer (Babashka + aishell + aishell.bat + PATH)"
      min_lines: 80
    - path: "README.md"
      provides: "Updated Quick Start sections for both platforms"
      contains: "install.ps1"
  key_links:
    - from: "install.sh"
      to: "https://raw.githubusercontent.com/babashka/babashka/master/install"
      via: "curl/wget pipe to bash with --dir flag"
      pattern: "babashka.*install.*--dir"
    - from: "install.ps1"
      to: "https://github.com/babashka/babashka/releases"
      via: "Invoke-WebRequest to download bb.exe from GitHub releases"
      pattern: "babashka.*releases"
---

<objective>
Add automatic Babashka installation to install.sh and create a PowerShell install.ps1 for Windows, then update README.md to reflect the simplified install experience.

Purpose: Eliminate the manual "install Babashka first" prerequisite. Users should run one command and get a working aishell.
Output: Updated install.sh, new install.ps1, updated README.md
</objective>

<execution_context>
@/home/jonasrodrigues/.claude/get-shit-done/workflows/execute-plan.md
@/home/jonasrodrigues/.claude/get-shit-done/templates/summary.md
</execution_context>

<context>
@install.sh
@dist/aishell.bat
@README.md
</context>

<tasks>

<task type="auto">
  <name>Task 1: Add auto-install Babashka to install.sh</name>
  <files>install.sh</files>
  <action>
Replace the Babashka check block (lines 52-56) with an auto-install flow:

1. Add an `install_babashka()` function BEFORE `install_aishell()` that:
   - Takes `$install_dir` and `$downloader` as parameters
   - Downloads the official Babashka installer script from `https://raw.githubusercontent.com/babashka/babashka/master/install`
   - Runs it with `bash /tmp/bb-install --dir "$install_dir"` (installs bb to same dir as aishell, avoids sudo)
   - Uses the already-detected `$downloader` (curl/wget) to fetch the installer
   - Cleans up the downloaded installer script
   - Verifies bb is now available at `$install_dir/bb`

2. In `install_aishell()`, move the download tool detection (lines 59-67) BEFORE the Babashka check so we can use curl/wget in the bb installer.

3. Replace the Babashka check (lines 52-56) with:
   ```bash
   if ! command -v bb &>/dev/null; then
       info "Babashka not found. Installing..."
       install_babashka "$install_dir" "$downloader"
       success "Babashka installed to ${install_dir}/bb"
   else
       success "Babashka found: $(command -v bb)"
   fi
   ```

4. Keep idempotency: if `bb` is already on PATH, skip install entirely.

Key details:
- The official babashka install script URL is: `https://raw.githubusercontent.com/babashka/babashka/master/install`
- The `--dir` flag installs to a user-writable location, avoiding sudo
- Download the installer to `/tmp/bb-install`, run it, then `rm -f /tmp/bb-install`
- If the babashka install fails, `error` and `exit 1`
  </action>
  <verify>
Run `bash -n install.sh` to check for syntax errors. Read through the script to confirm:
- Download tool detection happens before bb check
- install_babashka function exists and uses --dir flag
- Idempotent: existing bb on PATH skips install
- Cleanup of /tmp/bb-install happens
  </verify>
  <done>
install.sh auto-installs Babashka to $INSTALL_DIR when bb is not found, skips when bb exists, uses no sudo, and cleans up temp files.
  </done>
</task>

<task type="auto">
  <name>Task 2: Create install.ps1 for Windows</name>
  <files>install.ps1</files>
  <action>
Create `install.ps1` in the repo root that provides a complete Windows install experience. The script should:

1. Configuration section:
   - `$repoUrl = "https://github.com/UniSoma/aishell"`
   - `$version` from `$env:VERSION` or "latest"
   - `$installDir` from `$env:INSTALL_DIR` or `"$env:LOCALAPPDATA\Programs\aishell"`

2. Output functions: `Write-Info`, `Write-Success`, `Write-Error`, `Write-Warn` with colored output matching install.sh style.

3. Create install directory: `New-Item -ItemType Directory -Force -Path $installDir`

4. Babashka check and install:
   ```powershell
   if (-not (Get-Command bb -ErrorAction SilentlyContinue)) {
       Write-Info "Babashka not found. Installing..."
       # Check for scoop
       if (Get-Command scoop -ErrorAction SilentlyContinue) {
           scoop install babashka
       } else {
           # Direct download: fetch latest bb release from GitHub
           # URL: https://github.com/babashka/babashka/releases/latest/download/babashka-{version}-windows-amd64.zip
           # Since we don't know the version from "latest", use GitHub API to resolve:
           $bbRelease = Invoke-RestMethod "https://api.github.com/repos/babashka/babashka/releases/latest"
           $bbVersion = $bbRelease.tag_name -replace '^v', ''
           $bbUrl = "https://github.com/babashka/babashka/releases/download/v${bbVersion}/babashka-${bbVersion}-windows-amd64.zip"
           $bbZip = "$env:TEMP\babashka.zip"
           Invoke-WebRequest -Uri $bbUrl -OutFile $bbZip
           Expand-Archive -Path $bbZip -DestinationPath $installDir -Force
           Remove-Item $bbZip
           # Verify
           if (-not (Test-Path "$installDir\bb.exe")) {
               Write-Error "Babashka installation failed"
               exit 1
           }
           Write-Success "Babashka installed to $installDir\bb.exe"
       }
   } else {
       Write-Success "Babashka found: $((Get-Command bb).Source)"
   }
   ```

5. Download aishell + aishell.bat:
   - Resolve download URL (latest or specific version, same logic as install.sh)
   - Download aishell script to `$installDir\aishell`
   - Download aishell.bat to `$installDir\aishell.bat`
   - Download checksum file

6. Verify SHA256 checksum:
   ```powershell
   $expected = (Get-Content "$env:TEMP\aishell.sha256").Split(' ')[0]
   $actual = (Get-FileHash "$installDir\aishell" -Algorithm SHA256).Hash.ToLower()
   if ($actual -ne $expected) { Write-Error "Checksum mismatch"; exit 1 }
   ```

7. PATH management:
   - Check if `$installDir` is already in user PATH
   - If not, add it and inform user to restart terminal
   - Use `[Environment]::SetEnvironmentVariable` for persistent user PATH

8. Cleanup temp files and print success + quick start instructions.

Script should be runnable via: `irm https://raw.githubusercontent.com/UniSoma/aishell/main/install.ps1 | iex`

Add a comment header explaining usage, matching install.sh style.
  </action>
  <verify>
Read through install.ps1 to confirm:
- Babashka auto-install works via scoop (if available) or direct download
- aishell + aishell.bat downloaded with checksum verification
- PATH is updated persistently for current user
- Idempotent (skips bb if present, skips PATH if already set)
- Can be piped from web (`irm ... | iex`)
  </verify>
  <done>
install.ps1 exists and provides a complete one-command Windows install: Babashka + aishell + aishell.bat + PATH configuration.
  </done>
</task>

<task type="auto">
  <name>Task 3: Update README.md Quick Start and Prerequisites</name>
  <files>README.md</files>
  <action>
Update README.md to reflect the new auto-install experience:

1. Unix Quick Start (lines 57-68): Keep the same curl one-liner. Add a note below:
   ```markdown
   > Babashka is installed automatically if not already present.
   ```

2. Windows Quick Start (lines 70-90): Replace the 5-step manual process with:
   ```markdown
   ### Windows (PowerShell)

   ```powershell
   # 1. Install
   irm https://raw.githubusercontent.com/UniSoma/aishell/main/install.ps1 | iex

   # 2. Restart terminal, then build foundation image and select harnesses (one-time)
   aishell setup --with-claude

   # 3. Run
   aishell claude
   ```
   ```
   Add the same note: `> Babashka is installed automatically if not already present.`

3. Prerequisites section (inside the `<details>` block, lines 92-134):
   - Update Requirements to note Babashka is auto-installed:
     - **Linux/macOS:** Docker Engine (Babashka installed automatically)
     - **Windows:** Docker Desktop with WSL2 backend enabled (Babashka installed automatically)
   - Remove or simplify the standalone Babashka install instructions since it is now automatic
   - Keep the Docker install links as-is
   - Keep the PATH configuration info as-is

Do NOT change any other sections of the README.
  </action>
  <verify>
Read README.md and confirm:
- Unix Quick Start still uses curl pipe to bash
- Windows Quick Start uses `irm ... | iex` one-liner
- Both mention auto-install of Babashka
- Prerequisites updated to reflect auto-install
- No other sections accidentally modified
  </verify>
  <done>
README.md reflects the simplified install experience for both platforms with auto-Babashka-install messaging.
  </done>
</task>

</tasks>

<verification>
- `bash -n install.sh` passes (no syntax errors)
- install.sh contains install_babashka function with --dir flag
- install.ps1 exists with Babashka + aishell download + checksum + PATH
- README.md Windows Quick Start is now a one-liner (irm | iex)
- README.md Unix Quick Start unchanged except for auto-install note
</verification>

<success_criteria>
- install.sh auto-installs Babashka when missing, skips when present, no sudo needed
- install.ps1 provides complete Windows install in one command
- README.md Quick Start reflects the new simplified experience on both platforms
- All scripts are idempotent (safe to run multiple times)
</success_criteria>

<output>
After completion, create `.planning/quick/6-implement-auto-install-bb-in-install-sh-/6-SUMMARY.md`
</output>
