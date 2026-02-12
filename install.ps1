# install.ps1 - Installer for aishell (Windows)
# Downloads and installs Babashka + aishell with checksum verification
#
# Usage: irm https://raw.githubusercontent.com/UniSoma/aishell/main/install.ps1 | iex
#
# Environment variables:
#   $env:VERSION     - Version to install (default: latest)
#   $env:INSTALL_DIR - Installation directory (default: $env:LOCALAPPDATA\Programs\aishell)

$ErrorActionPreference = "Stop"

# --- Configuration ---
$repoUrl = "https://github.com/UniSoma/aishell"
$version = if ($env:VERSION) { $env:VERSION } else { "latest" }
$installDir = if ($env:INSTALL_DIR) { $env:INSTALL_DIR } else { "$env:LOCALAPPDATA\Programs\aishell" }

# --- Output Functions ---
function Write-Info {
    param([string]$Message)
    Write-Host "==> " -ForegroundColor Blue -NoNewline
    Write-Host $Message -ForegroundColor White
}

function Write-Success {
    param([string]$Message)
    Write-Host "==> " -ForegroundColor Green -NoNewline
    Write-Host $Message
}

function Write-Error {
    param([string]$Message)
    Write-Host "Error: " -ForegroundColor Red -NoNewline
    Write-Host $Message
}

function Write-Warn {
    param([string]$Message)
    Write-Host "Warning: " -ForegroundColor Yellow -NoNewline
    Write-Host $Message
}

# --- Create Install Directory ---
Write-Info "Creating installation directory..."
New-Item -ItemType Directory -Force -Path $installDir | Out-Null

# --- Check for Babashka ---
if (-not (Get-Command bb -ErrorAction SilentlyContinue)) {
    Write-Info "Babashka not found. Installing..."

    # Check for scoop
    if (Get-Command scoop -ErrorAction SilentlyContinue) {
        Write-Info "Installing Babashka via Scoop..."
        scoop install babashka
        Write-Success "Babashka installed via Scoop"
    } else {
        Write-Info "Scoop not found. Installing Babashka directly from GitHub..."

        # Fetch latest release info from GitHub API
        try {
            $bbRelease = Invoke-RestMethod "https://api.github.com/repos/babashka/babashka/releases/latest"
            $bbVersion = $bbRelease.tag_name -replace '^v', ''
            $bbUrl = "https://github.com/babashka/babashka/releases/download/v${bbVersion}/babashka-${bbVersion}-windows-amd64.zip"
            $bbZip = "$env:TEMP\babashka.zip"

            Write-Info "Downloading Babashka v${bbVersion}..."
            Invoke-WebRequest -Uri $bbUrl -OutFile $bbZip

            Write-Info "Extracting Babashka to ${installDir}..."
            Expand-Archive -Path $bbZip -DestinationPath $installDir -Force

            # Cleanup
            Remove-Item $bbZip

            # Verify
            if (-not (Test-Path "$installDir\bb.exe")) {
                Write-Error "Babashka installation failed - bb.exe not found"
                exit 1
            }

            Write-Success "Babashka installed to $installDir\bb.exe"
        } catch {
            Write-Error "Failed to install Babashka: $_"
            exit 1
        }
    }
} else {
    Write-Success "Babashka found: $((Get-Command bb).Source)"
}

# --- Determine Download URL ---
$downloadUrl = if ($version -eq "latest") {
    "$repoUrl/releases/latest/download/aishell"
} else {
    "$repoUrl/releases/download/v${version}/aishell"
}
$checksumUrl = "${downloadUrl}.sha256"
$batUrl = if ($version -eq "latest") {
    "$repoUrl/releases/latest/download/aishell.bat"
} else {
    "$repoUrl/releases/download/v${version}/aishell.bat"
}

# --- Download aishell ---
Write-Info "Downloading aishell..."
try {
    Invoke-WebRequest -Uri $downloadUrl -OutFile "$installDir\aishell"
} catch {
    Write-Error "Failed to download aishell from ${downloadUrl}: $_"
    exit 1
}

# --- Download aishell.bat ---
Write-Info "Downloading aishell.bat..."
try {
    Invoke-WebRequest -Uri $batUrl -OutFile "$installDir\aishell.bat"
} catch {
    Write-Error "Failed to download aishell.bat from ${batUrl}: $_"
    Remove-Item "$installDir\aishell" -ErrorAction SilentlyContinue
    exit 1
}

# --- Download checksum ---
Write-Info "Downloading checksum..."
try {
    Invoke-WebRequest -Uri $checksumUrl -OutFile "$env:TEMP\aishell.sha256"
} catch {
    Write-Error "Failed to download checksum from ${checksumUrl}: $_"
    Remove-Item "$installDir\aishell" -ErrorAction SilentlyContinue
    Remove-Item "$installDir\aishell.bat" -ErrorAction SilentlyContinue
    exit 1
}

# --- Verify Checksum ---
Write-Info "Verifying checksum..."
$expectedHash = (Get-Content "$env:TEMP\aishell.sha256").Split(' ')[0].Trim()
$actualHash = (Get-FileHash "$installDir\aishell" -Algorithm SHA256).Hash.ToLower()

if ($actualHash -ne $expectedHash) {
    Write-Error "Checksum verification failed"
    Write-Host "  Expected: $expectedHash" -ForegroundColor Red
    Write-Host "  Got:      $actualHash" -ForegroundColor Red
    Remove-Item "$installDir\aishell" -ErrorAction SilentlyContinue
    Remove-Item "$installDir\aishell.bat" -ErrorAction SilentlyContinue
    Remove-Item "$env:TEMP\aishell.sha256" -ErrorAction SilentlyContinue
    exit 1
}

# --- Cleanup ---
Remove-Item "$env:TEMP\aishell.sha256" -ErrorAction SilentlyContinue

# --- PATH Management ---
$currentPath = [Environment]::GetEnvironmentVariable("Path", [System.EnvironmentVariableTarget]::User)
$pathNeedsUpdate = $false

if ($currentPath -notlike "*$installDir*") {
    Write-Info "Adding $installDir to user PATH..."
    $newPath = "$currentPath;$installDir"
    [Environment]::SetEnvironmentVariable("Path", $newPath, [System.EnvironmentVariableTarget]::User)
    $pathNeedsUpdate = $true
    Write-Success "PATH updated"
} else {
    Write-Success "$installDir already in PATH"
}

# --- Success Message ---
Write-Host ""
Write-Success "Done! Installed aishell to $installDir"
Write-Host ""

if ($pathNeedsUpdate) {
    Write-Warn "Restart your terminal for PATH changes to take effect."
    Write-Host ""
    Write-Host "Then run:"
    Write-Host "  aishell setup --with-claude    # Set up Docker image and select harnesses"
    Write-Host "  aishell claude                 # Run Claude Code"
} else {
    Write-Host "Quick start:"
    Write-Host "  aishell setup --with-claude    # Set up Docker image and select harnesses"
    Write-Host "  aishell claude                 # Run Claude Code"
}
Write-Host ""
